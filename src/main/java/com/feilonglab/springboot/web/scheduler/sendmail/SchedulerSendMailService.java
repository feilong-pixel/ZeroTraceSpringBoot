package com.feilonglab.springboot.web.scheduler.sendmail;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.feilonglab.smtp.scheduler.SmtpClient;
import com.feilonglab.springboot.enums.MailStatus;
import com.feilonglab.springboot.model.dao.MailInfoDao;
import com.feilonglab.springboot.model.dao.MailSendLogDao;
import com.feilonglab.springboot.model.entity.MailInfo;
import com.feilonglab.springboot.model.entity.MailSendLog;
import com.feilonglab.springboot.util.MessageUtils;

/**
 * 场景 4：定时调度与重试补偿业务服务。 核心功能是定期捞取发送失败（小于 3 次）或遗漏未发的文件，并独立对每封邮件起短事务，复用 SMTP
 * 客户端进行投递。
 */
@Service
public class SchedulerSendMailService {

    /** 日志 */
    private static final Logger logger = LoggerFactory.getLogger(SchedulerSendMailService.class);

    /** 邮件信息数据访问对象 */
    @Autowired
    private MailInfoDao mailInfoDao;

    /** 邮件发送日志数据访问对象 */
    @Autowired
    private MailSendLogDao mailSendLogDao;

    /** SMTP 客户端对象工厂 */
    @Autowired
    private ObjectFactory<SmtpClient> smtpClientFactory;

    /** 自我注入，用于事务传播代理调用 */
    @Autowired
    @Lazy
    private SchedulerSendMailService self;

    /** 是否启用定时器功能 */
    @Value("${mail.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    /** 定时任务 Cron 表达式。默认：周一至周五，每天 8 点到 23 点之间，每 10 分钟执行一次 */
    @Value("${mail.scheduler.cron:0 */10 8-23 * * MON-FRI}")
    private String cronExpression;

    /**
     * 定时轮询的入口任务。 配置为 fixedDelay，即在上一次轮询结束后等待指定毫秒数再开始下一次，避免多任务重叠冲突。
     */
    @Scheduled(cron = "${mail.scheduler.cron:0 */10 8-23 * * MON-FRI}")
    public void scheduledPollAndSend() {
        if (!schedulerEnabled) {
            logger.debug(MessageUtils.getMessage("mail.scheduler.disabled"));
            return;
        }

        logger.info(MessageUtils.getMessage("mail.scheduler.start"));
        // 执行扫描和发送
        int successCount = pollAndSendPendingMails();
        logger.info(MessageUtils.getMessage("mail.scheduler.complete", successCount));
    }

    /**
     * 执行扫描和发送，此方法也可被控制器手动触发。 捞取 status 为 0 (未发送) 或 9 (发送失败且重试次数小于 3) 的邮件。
     *
     * @return 本次发送成功的邮件数量
     */
    public int pollAndSendPendingMails() {
        // 1. 查询所有未发送或发送失败，且重试次数少于 3 次的邮件列表
        List<MailInfo> unsentMails = mailInfoDao.selectUnsentOrFailed(3);

        // 没有待发送的邮件
        if (unsentMails == null || unsentMails.isEmpty()) {
            logger.debug(MessageUtils.getMessage("mail.scheduler.no.pending"));
            return 0;
        }

        logger.info(MessageUtils.getMessage("mail.scheduler.scanned.count", unsentMails.size()));
        int successCount = 0;

        // 2. 统一开启并复用单一的 SMTP 客户端连接，避免频繁握手
        try (SmtpClient client = smtpClientFactory.getObject()) {
            client.open();
            for (MailInfo mail : unsentMails) {
                boolean isSent = sendSingleMail(client, mail);
                if (isSent) {
                    successCount++;
                }
            }
        } catch (Exception e) {
            logger.error(MessageUtils.getMessage("mail.scheduler.error"), e);
        }

        return successCount;
    }

    /**
     * 发送单封邮件。
     *
     * @param client 已经开启连接的 SMTP 客户端
     * @param mail   待发送的邮件对象
     * @return 是否发送成功
     */
    private boolean sendSingleMail(SmtpClient client, MailInfo mail) {
        boolean sendSuccess = false;
        String errorReason = null;

        try {
            // 发送邮件
            client.sendMail(mail.getToName(), mail.getToEmail(), mail.getSubject(), mail.getContent());
            sendSuccess = true;
            logger.info(MessageUtils.getMessage("mail.send.success", mail.getMailId()));
        } catch (Exception e) {
            sendSuccess = false;
            errorReason = e.getMessage();
            // 截断过长错误原因
            if (errorReason != null && errorReason.length() > 500) {
                errorReason = errorReason.substring(0, 500);
            }
            logger.error(MessageUtils.getMessage("mail.send.failure", mail.getMailId(), errorReason));
        }

        // 调用带新事务的保存方法
        if (sendSuccess) {
            self.saveSuccess(mail);
        } else {
            self.saveFailure(mail, errorReason);
        }

        return sendSuccess;
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

            // 更新邮件状态为成功
            mail.setRetryCount(attempt);
            mail.setStatus(MailStatus.SUCCESS.getValue());
            mail.setSentAt(processedAt);

            int updated = mailInfoDao.update(mail);
            if (updated == 0) {
                logger.error(MessageUtils.getMessage("mail.db.optimistic.lock", mail.getMailId()));
                return;
            }

            // 插入邮件发送日志
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

            // 更新邮件状态为失败
            mail.setRetryCount(attempt);
            mail.setStatus(MailStatus.FAILED.getValue());
            mail.setSentAt(null);

            int updated = mailInfoDao.update(mail);
            if (updated == 0) {
                logger.error(MessageUtils.getMessage("mail.db.optimistic.lock", mail.getMailId()));
                return;
            }

            // 插入邮件发送日志
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

    /**
     * 定时任务是否启用
     * 
     * @return 定时任务是否启用
     */
    public boolean isSchedulerEnabled() {
        return schedulerEnabled;
    }

    /**
     * 获取 Cron 表达式
     * 
     * @return Cron 表达式
     */
    public String getCronExpression() {
        return cronExpression;
    }
}
