package com.feilonglab.springboot.web.threadpool.sendmail;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.feilonglab.smtp.threadpool.SmtpClient;
import com.feilonglab.smtp.unified.MailRequest;
import com.feilonglab.springboot.enums.MailStatus;
import com.feilonglab.springboot.model.dao.MailInfoDao;
import com.feilonglab.springboot.model.dao.MailSendLogDao;
import com.feilonglab.springboot.model.entity.MailInfo;
import com.feilonglab.springboot.model.entity.MailSendLog;
import com.feilonglab.springboot.util.MessageUtils;

/**
 * 场景 3：邮件发送线程池异步业务层服务。 负责将多封邮件先做持久化落盘，再通过本地线程池执行并发发送，保障发送过程不阻塞主线程。
 */
@Service
public class ThreadPoolSendMailService {

    /** 日志 */
    private static final Logger logger = LoggerFactory.getLogger(ThreadPoolSendMailService.class);

    /** 注入邮件信息数据访问对象 */
    @Autowired
    private MailInfoDao mailInfoDao;

    /** 注入邮件发送日志数据访问对象 */
    @Autowired
    private MailSendLogDao mailSendLogDao;

    /** 注入 SmtpClient 工厂 */
    @Autowired
    private ObjectFactory<SmtpClient> smtpClientFactory;

    /** 自我注入，用于事务传播与 @Async 代理调用 */
    @Autowired
    @Lazy
    private ThreadPoolSendMailService self;

    /**
     * 持久化单封邮件，并返回其持久化对象。 为保证任务提交的及时性，单封邮件以独立事务入库。
     *
     * @param request 邮件发送请求
     * @return 持久化的 MailInfo 实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public MailInfo persistMail(MailRequest request) {
        MailInfo mailInfo = new MailInfo();
        mailInfo.setStatus(MailStatus.PENDING.getValue()); // 初始状态为 0 (未发送)
        mailInfo.setRetryCount(0);
        mailInfo.setToName(request.toName);
        mailInfo.setToEmail(request.ToMailAddress);
        mailInfo.setSubject(request.subject);
        mailInfo.setContent(request.textContent);

        mailInfoDao.insert(mailInfo);
        return mailInfo;
    }

    /**
     * 触发异步的发送任务。 此方法会被代理包装，使用指定的 mailThreadPoolExecutor 异步执行。
     *
     * @param mailId 邮件唯一ID
     */
    @Async("mailThreadPoolExecutor")
    public void sendMailAsync(Long mailId) {
        logger.info("线程池子线程 [ID: {}] 开始异步执行邮件发送任务, 邮件 ID: {}", Thread.currentThread().getId(), mailId);

        // 1. 读取邮件信息
        MailInfo mail = mailInfoDao.selectById(mailId);
        if (mail == null) {
            logger.warn("未找到对应的邮件信息，邮件 ID: {}", mailId);
            return;
        }

        // 2. 防重发送检查
        if (MailStatus.SUCCESS.getValue() == mail.getStatus()) {
            logger.info("邮件 ID: {} 已成功发送，跳过重复处理.", mailId);
            return;
        }

        boolean sendSuccess = false;
        String errorReason = null;

        // 3. 调用 SmtpClient 发送邮件
        try (SmtpClient client = smtpClientFactory.getObject()) {
            client.open();
            client.sendMail(mail.getToName(), mail.getToEmail(), mail.getSubject(), mail.getContent());
            sendSuccess = true;
            logger.info(MessageUtils.getMessage("mail.send.success", mail.getMailId()));
        } catch (Exception e) {
            sendSuccess = false;
            errorReason = e.getMessage();
            if (errorReason != null && errorReason.length() > 500) {
                errorReason = errorReason.substring(0, 500); // 截断异常信息
            }
            logger.error(MessageUtils.getMessage("mail.send.failure", mail.getMailId(), errorReason));
        }

        // 4. 新事务落盘
        if (sendSuccess) {
            self.saveSuccess(mail);
        } else {
            self.saveFailure(mail, errorReason);
        }
    }

    /**
     * 发送成功落盘（独立事务）。
     *
     * @param mail 邮件实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSuccess(MailInfo mail) {
        try {
            int attempt = mail.getRetryCount() + 1;
            LocalDateTime processedAt = LocalDateTime.now();

            mail.setRetryCount(attempt);
            mail.setStatus(MailStatus.SUCCESS.getValue());
            mail.setSentAt(processedAt);

            int updated = mailInfoDao.update(mail);
            if (updated == 0) {
                logger.error(MessageUtils.getMessage("mail.db.optimistic.lock", mail.getMailId()));
                return;
            }

            MailSendLog sendLog = new MailSendLog();
            sendLog.setMailId(mail.getMailId());
            sendLog.setSentAt(processedAt);
            sendLog.setStatus(MailStatus.SUCCESS.getValue());
            sendLog.setAttempt(attempt);
            sendLog.setErrorReason(null);

            mailSendLogDao.insert(sendLog);
        } catch (Exception e) {
            logger.error(MessageUtils.getMessage("mail.db.save.success.error", mail.getMailId()), e);
            try {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            } catch (Exception ex) {
                logger.error(MessageUtils.getMessage("mail.tx.rollback.failed"), ex);
            }
        }
    }

    /**
     * 发送失败落盘（独立事务）。
     *
     * @param mail        邮件实体
     * @param errorReason 失败原因
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailure(MailInfo mail, String errorReason) {
        try {
            int attempt = mail.getRetryCount() + 1;
            LocalDateTime processedAt = LocalDateTime.now();

            // 更新邮件状态
            mail.setRetryCount(attempt);
            mail.setStatus(MailStatus.FAILED.getValue());
            mail.setSentAt(null);

            int updated = mailInfoDao.update(mail);
            if (updated == 0) {
                logger.error(MessageUtils.getMessage("mail.db.optimistic.lock", mail.getMailId()));
                return;
            }

            // 持久化发送日志
            MailSendLog sendLog = new MailSendLog();
            sendLog.setMailId(mail.getMailId());
            sendLog.setSentAt(processedAt);
            sendLog.setStatus(MailStatus.FAILED.getValue());
            sendLog.setAttempt(attempt);
            sendLog.setErrorReason(errorReason);

            mailSendLogDao.insert(sendLog);
        } catch (Exception e) {
            logger.error(MessageUtils.getMessage("mail.db.save.failure.error", mail.getMailId()), e);
            try {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            } catch (Exception ex) {
                logger.error(MessageUtils.getMessage("mail.tx.rollback.failed"), ex);
            }
        }
    }
}
