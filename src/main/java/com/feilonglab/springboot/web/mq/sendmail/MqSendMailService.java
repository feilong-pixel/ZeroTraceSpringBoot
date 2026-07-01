package com.feilonglab.springboot.web.mq.sendmail;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import com.feilonglab.smtp.mq.SmtpClient;
import com.feilonglab.smtp.unified.MailRequest;
import com.feilonglab.springboot.enums.MailStatus;
import com.feilonglab.springboot.model.dao.MailInfoDao;
import com.feilonglab.springboot.model.dao.MailSendLogDao;
import com.feilonglab.springboot.model.entity.MailInfo;
import com.feilonglab.springboot.model.entity.MailSendLog;
import com.feilonglab.springboot.util.MessageUtils;

/**
 * 邮件发送 MQ 业务服务类。
 * 当收到发送请求时先持久化邮件元数据，然后再通过消息队列异步处理发送并应用 ZeroTrace 可靠重试设计。
 */
@Service
@PropertySource("classpath:mq.properties")
public class MqSendMailService {

    /** 邮件队列名称 */
    @Value("${mail.queue:mail.queue}")
    private String queueName;

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(MqSendMailService.class);

    /** 邮件信息数据访问对象 */
    @Autowired
    private MailInfoDao mailInfoDao;

    /** 邮件发送日志数据访问对象 */
    @Autowired
    private MailSendLogDao mailSendLogDao;

    /** JmsTemplate */
    @Autowired
    private JmsTemplate jmsTemplate;

    /** 自我注入，用于事务传播切换 */
    @Autowired
    @Lazy
    private MqSendMailService self;

    /** SMTP 客户端工厂 */
    @Autowired
    private ObjectFactory<SmtpClient> smtpClientFactory;

    /**
     * 接收邮件发送请求，先写入数据库并向队列推送消息。
     *
     * @param request 邮件发送请求
     * @return 持久化后的邮件ID
     */
    @Transactional
    public Long sendMailAsync(MailRequest request) {
        // 1. 构建 MailInfo 并写入数据库
        MailInfo mailInfo = new MailInfo();
        // 设置邮件信息
        mailInfo.setStatus(MailStatus.PENDING.getValue()); // 未发送
        // 初始化重试次数为 0
        mailInfo.setRetryCount(0);
        mailInfo.setToName(request.toName);
        mailInfo.setToEmail(request.ToMailAddress);
        mailInfo.setSubject(request.subject);
        mailInfo.setContent(request.textContent);

        mailInfoDao.insert(mailInfo);
        Long mailId = mailInfo.getMailId();

        // 2. 发送 mailId 到 JMS 队列
        logger.info("邮件已持久化，ID: {}, 准备发布至 JMS 队列.", mailId);
        jmsTemplate.convertAndSend(queueName, mailId);

        return mailId;
    }

    /**
     * 监听邮件队列进行异步邮件投递与可靠性落盘。
     *
     * @param mailId 邮件ID
     */
    @JmsListener(destination = "${mail.queue:mail.queue}", containerFactory = "jmsListenerContainerFactory")
    public void consumeMailMessage(Long mailId) {
        logger.info("从 MQ 队列接收到邮件发送任务，邮件 ID: {}", mailId);

        // 1. 读取数据库中的邮件信息
        MailInfo mail = mailInfoDao.selectById(mailId);
        if (mail == null) {
            logger.warn("未找到对应的邮件信息，邮件 ID: {}", mailId);
            return;
        }

        // 2. 只有未发送（0）或者发送失败（9）的邮件才允许消费投递
        if (MailStatus.SUCCESS.getValue() == mail.getStatus()) {
            logger.info("邮件 ID: {} 已发送成功过，不再重复发送.", mailId);
            return;
        }

        boolean sendSuccess = false;
        String errorReason = null;

        // 3. 执行 SMTP 发送
        try (SmtpClient client = smtpClientFactory.getObject()) {
            // 打开 SMTP 客户端连接
            client.open();

            // 调用邮件发送客户端进行投递，演示模式下会将发送详情输出至日志
            client.sendMail(mail.getToName(), mail.getToEmail(), mail.getSubject(), mail.getContent());

            // 投递成功，更新邮件状态为成功并记录发送时间
            sendSuccess = true;

            logger.info(MessageUtils.getMessage("mail.send.success", mail.getMailId()));
        } catch (Exception e) {

            // 投递失败，记录错误原因
            sendSuccess = false;
            // 捕获异常并封装错误原因，限制长度以防止日志过长
            errorReason = e.getMessage();
            if (errorReason != null && errorReason.length() > 500) {
                errorReason = errorReason.substring(0, 500); // 截断过长错误原因
            }
            logger.error(MessageUtils.getMessage("mail.send.failure", mail.getMailId(), errorReason));
        }

        // 4. 数据更新落盘与日志记录（使用 REQUIRES_NEW 事务以防止在当前非事务上下文中或抛出异常时回滚）
        if (sendSuccess) {
            self.saveSuccess(mail);
        } else {
            self.saveFailure(mail, errorReason);
        }
    }

    /**
     * 邮件发送成功后，在一个独立的新事务中更新邮件状态并记录成功日志。
     * 必须是 package-private 或 public，以便 Spring AOP 代理有效。
     *
     * @param mail 成功发送的邮件实体
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveSuccess(MailInfo mail) {
        try {
            // 计算当前尝试次数
            int attempt = mail.getRetryCount() + 1;
            LocalDateTime processedAt = LocalDateTime.now();

            // 更新邮件状态为成功（1）并记录发送时间
            mail.setRetryCount(attempt);
            mail.setStatus(MailStatus.SUCCESS.getValue());
            mail.setSentAt(processedAt);

            // 使用乐观锁更新数据库，确保不会覆盖其他线程的修改
            int updated = mailInfoDao.update(mail);
            if (updated == 0) {
                logger.error(MessageUtils.getMessage("mail.db.optimistic.lock", mail.getMailId()));
                return;
            }

            // 记录发送成功日志
            MailSendLog sendLog = new MailSendLog();
            sendLog.setMailId(mail.getMailId());
            sendLog.setSentAt(processedAt);
            sendLog.setStatus(MailStatus.SUCCESS.getValue());
            sendLog.setAttempt(attempt);
            sendLog.setErrorReason(null);

            // 插入发送日志
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
     * 邮件发送失败后，在一个独立的新事务中更新邮件状态为失败（9）并记录错误日志。
     * 必须是 package-private 或 public，以便 Spring AOP 代理有效。
     *
     * @param mail        发送失败的邮件实体
     * @param errorReason 失败原因
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFailure(MailInfo mail, String errorReason) {
        try {
            // 计算当前尝试次数
            int attempt = mail.getRetryCount() + 1;
            LocalDateTime processedAt = LocalDateTime.now();

            // 更新邮件状态为失败（9）并记录发送时间
            mail.setRetryCount(attempt);
            mail.setStatus(MailStatus.FAILED.getValue());
            mail.setSentAt(null);

            // 使用乐观锁更新数据库，确保不会覆盖其他线程的修改
            int updated = mailInfoDao.update(mail);
            if (updated == 0) {
                logger.error(MessageUtils.getMessage("mail.db.optimistic.lock", mail.getMailId()));
                return;
            }

            // 记录发送失败日志
            MailSendLog sendLog = new MailSendLog();
            sendLog.setMailId(mail.getMailId());
            sendLog.setSentAt(processedAt);
            sendLog.setStatus(MailStatus.FAILED.getValue());
            sendLog.setAttempt(attempt);
            sendLog.setErrorReason(errorReason);

            // 插入发送日志
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
