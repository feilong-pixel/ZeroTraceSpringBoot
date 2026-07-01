package com.feilonglab.springboot.web.batch.sendmail;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.feilonglab.smtp.basic.SmtpClient;
import com.feilonglab.springboot.enums.MailStatus;
import com.feilonglab.springboot.model.dao.MailInfoDao;
import com.feilonglab.springboot.model.dao.MailSendLogDao;
import com.feilonglab.springboot.model.entity.MailInfo;
import com.feilonglab.springboot.model.entity.MailSendLog;
import com.feilonglab.springboot.util.MessageUtils;

/**
 * 邮件发送业务服务类。
 * 主要负责调度和流转邮件的发送流程。协调 {@link MailInfoDao} 和 {@link MailSendLogDao}，
 * 将待发送邮件的数据读取、SMTP 邮件发送以及单封事务状态落盘逻辑有机地结合起来。
 */
@Service
public class SendMailService {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(SendMailService.class);

    /** 邮件信息数据访问对象 */
    @Autowired
    private MailInfoDao mailInfoDao;

    /** 邮件发送日志数据访问对象 */
    @Autowired
    private MailSendLogDao mailSendLogDao;

    // 自我注入（Self-Injection）当前 Bean 的代理对象。
    // 用于在类内部调用带有 @Transactional 注解的方法，避免 Spring AOP
    // 自调用（Self-Invocation）导致声明式事务失效的问题。
    @Autowired
    @Lazy
    private SendMailService self;

    /**
     * 批量邮件发送的主流程函数。
     * 负责检索待发送邮件。若存在待处理数据，则开启并复用单一 SMTP 客户端连接执行循环发送；
     * 若无数据，则立刻结束，从而规避空打 SMTP 连接带来的网络性能与握手开销。
     *
     * @return 本次成功发送 of 邮件总数
     */
    public int processBatchSend() {
        // 1. 从数据库中查询状态为未发送（0）或者已发送失败（9）且尚未达到最大重试上限（3次）的所有邮件列表
        List<MailInfo> unsentMails = mailInfoDao.selectUnsentOrFailed(3);

        // 2. 若当前没有待发送邮件，则直接提前退出，规避空连 SMTP 服务的开销
        if (unsentMails == null || unsentMails.isEmpty()) {
            logger.info(MessageUtils.getMessage("mail.batch.empty"));
            return 0;
        }

        logger.info(MessageUtils.getMessage("mail.batch.start", unsentMails.size()));
        int successCount = 0;

        // 3. 在有数据的前提下，统一建立一次 SMTP 连接，并在后续的邮件循环发送中复用该 client，以提高发送效能
        try (SmtpClient client = new SmtpClient()) {
            client.open();
            for (MailInfo mail : unsentMails) {
                // 4. 对待发邮件进行遍历，调用非事务性的单封邮件发送子函数进行分发
                boolean isSent = sendSingleMail(client, mail);
                if (isSent) {
                    successCount++;
                }
            }
        } catch (Exception e) {
            logger.error(MessageUtils.getMessage("mail.batch.error"), e);
        }

        return successCount;
    }

    /**
     * 单封邮件的发送逻辑包装函数。
     * 负责具体的邮件投递尝试。根据邮件投递结果（成功或失败），
     * 路由调用各自的数据库状态落盘及日志记录的事务性方法。
     *
     * @param client 已经开启并保持连接状态 of SMTP 客户端实例
     * @param mail   需要发送 of 邮件实体信息
     * @return 发送是否成功（成功返回 true，失败返回 false）
     */
    private boolean sendSingleMail(SmtpClient client, MailInfo mail) {
        boolean sendSuccess = false;
        String errorReason = null;

        try {
            // 1. 复用已经处于连接状态 of 客户端实例进行邮件投递
            client.sendMail(mail.getToName(), mail.getToEmail(), mail.getSubject(), mail.getContent());
            sendSuccess = true;
            logger.info(MessageUtils.getMessage("mail.send.success", mail.getMailId()));
        } catch (Exception e) {
            sendSuccess = false;
            errorReason = e.getMessage();
            if (errorReason != null && errorReason.length() > 500) {
                errorReason = errorReason.substring(0, 500); // 截断过长错误原因，防止超出数据库字段长度限制
            }
            logger.error(MessageUtils.getMessage("mail.send.failure", mail.getMailId(), errorReason));
        }

        // 2. 发送尝试结束后，调用专门 of 数据做成与落盘函数（通过代理对象 self 调用以保证 @Transactional 起效）
        if (sendSuccess) {
            self.saveSuccess(mail);
        } else {
            self.saveFailure(mail, errorReason);
        }

        return sendSuccess;
    }

    /**
     * 邮件发送成功后 of 数据库更新与日志记录处理（事务方法）。
     * 开启一个独立 of 新事务（Propagation.REQUIRES_NEW），以确保该邮件 of 成功状态即使在后续处理中发生任何回滚，也能即时持久化到数据库。
     * 如果落盘过程中发生任何数据库异常，内部捕获并直接记录日志，同时安全标记本事务回滚，不对外抛出异常以防中断外部 of 发送循环。
     *
     * @param mail 成功发送 of 邮件实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveSuccess(MailInfo mail) {
        try {
            // 计算当前尝试次数
            int attempt = mail.getRetryCount() + 1;
            LocalDateTime processedAt = LocalDateTime.now();

            // 1. 设置发送成功 of 状态属性，包括递增重试次数、标记成功状态以及记录发送成功时间
            mail.setRetryCount(attempt);
            mail.setStatus(MailStatus.SUCCESS.getValue()); // 发送成功
            mail.setSentAt(processedAt);

            // 使用乐观锁更新数据库，确保在高并发场景下不会覆盖其他线程的更新
            int updated = mailInfoDao.update(mail);
            if (updated == 0) {
                logger.error(MessageUtils.getMessage("mail.db.optimistic.lock", mail.getMailId()));
                return;
            }

            // 2. 组装并向数据库中插入本次 of 邮件发送成功历史日志
            MailSendLog sendLog = new MailSendLog();
            sendLog.setMailId(mail.getMailId());
            sendLog.setSentAt(processedAt);
            sendLog.setStatus(MailStatus.SUCCESS.getValue());
            sendLog.setAttempt(attempt);
            sendLog.setErrorReason(null);

            // 3. 将成功日志插入数据库
            mailSendLogDao.insert(sendLog);
        } catch (Exception e) {
            logger.error(MessageUtils.getMessage("mail.db.save.success.error", mail.getMailId()), e);
            try {
                // 标记当前新事务回滚，确保不污染数据库一致性
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            } catch (Exception ex) {
                logger.error(MessageUtils.getMessage("mail.tx.rollback.failed"), ex);
            }
        }
    }

    /**
     * 邮件发送失败后 of 数据库更新与日志记录处理（事务方法）。
     * 开启一个独立 of 新事务（Propagation.REQUIRES_NEW），以便在单封邮件投递失败时，立即更新数据库状态和重试计数。
     * 如果更新过程中抛出异常，内部捕获并直接记录日志，同时安全标记本事务回滚，不对外抛出异常以防中断外部发送循环。
     *
     * @param mail        发送失败 of 邮件实体
     * @param errorReason 具体 of 发送错误/异常原因描述
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void saveFailure(MailInfo mail, String errorReason) {
        try {
            // 计算当前尝试次数
            int attempt = mail.getRetryCount() + 1;
            LocalDateTime processedAt = LocalDateTime.now();

            // 1. 设置发送失败 of 状态属性，包括递增重试次数、标记失败状态、并将发送时间置空
            mail.setRetryCount(attempt);
            mail.setStatus(MailStatus.FAILED.getValue()); // 发送失败
            mail.setSentAt(null);

            // 使用乐观锁更新数据库，确保在高并发场景下不会覆盖其他线程的更新
            int updated = mailInfoDao.update(mail);
            if (updated == 0) {
                logger.error(MessageUtils.getMessage("mail.db.optimistic.lock", mail.getMailId()));
                return;
            }

            // 2. 组装并向数据库中插入本次 of 邮件发送失败历史日志，记录下具体错误堆栈或报错原因
            MailSendLog sendLog = new MailSendLog();
            sendLog.setMailId(mail.getMailId());
            sendLog.setSentAt(processedAt);
            sendLog.setStatus(MailStatus.FAILED.getValue());
            sendLog.setAttempt(attempt);
            sendLog.setErrorReason(errorReason);

            // 3. 将失败日志插入数据库
            mailSendLogDao.insert(sendLog);
        } catch (Exception e) {
            logger.error(MessageUtils.getMessage("mail.db.save.failure.error", mail.getMailId()), e);
            try {
                // 标记当前新事务回滚，确保不污染数据库一致性
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            } catch (Exception ex) {
                logger.error(MessageUtils.getMessage("mail.tx.rollback.failed"), ex);
            }
        }
    }
}
