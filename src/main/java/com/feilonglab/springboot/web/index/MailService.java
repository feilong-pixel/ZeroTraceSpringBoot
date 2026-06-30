package com.feilonglab.springboot.web.index;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.feilonglab.smtp.basic.SmtpClient;
import com.feilonglab.smtp.unified.MailRequest;
import com.feilonglab.springboot.enums.SmtpDebugFlag;
import com.feilonglab.springboot.util.MessageUtils;

/**
 * 邮件处理业务服务类。
 * 提供即时邮件发送的校验和 SMTP 处理服务，以及加载 SMTP 服务器的配置状态。
 */
@Service
public class MailService {

    /** 日志记录器 */
    private static final Logger logger = LoggerFactory.getLogger(MailService.class);

    /**
     * 获取当前的 SMTP 客户端配置以供前端视图呈现。
     *
     * @return 包含配置映射的 Map 集合
     */
    public Map<String, Object> getSmtpConfig() {
        Map<String, Object> config = new HashMap<>();
        // 尝试创建一个 SmtpClient 实例以获取当前的 SMTP 配置
        try (SmtpClient client = new SmtpClient()) {
            config.put("smtpHost", client.getHost());
            config.put("smtpPort", client.getPort());
            config.put("smtpUsername", client.getUsername());
            config.put("smtpSenderName", client.getSenderName());
            config.put("smtpSenderEmail", client.getSenderEmail());
            config.put("debugFlag", client.getDebugFlag());
        } catch (Exception e) {

            logger.error(MessageUtils.getMessage("mail.immediate.config.error"), e);
            // 如果无法获取配置，则返回默认的未配置状态
            String notConfigured = MessageUtils.getMessage("mail.config.not.configured");
            config.put("smtpHost", notConfigured);
            config.put("smtpPort", 0);
            config.put("smtpUsername", notConfigured);
            config.put("smtpSenderName", notConfigured);
            config.put("smtpSenderEmail", notConfigured);
            config.put("debugFlag", SmtpDebugFlag.REAL_CONNECTION.getValue());
        }
        return config;
    }

    /**
     * 执行单封即时邮件发送业务。
     *
     * @param mailRequest 邮件发送请求对象
     * @return 包含 success, message 和 HTTP 辅助状态 status 字段的返回映射
     */
    public Map<String, Object> sendImmediateMail(MailRequest mailRequest) {
        Map<String, Object> result = new HashMap<>();

        // 参数非空及合法性校验
        // 邮件收件人不能为空
        if (mailRequest.ToMailAddress == null || mailRequest.ToMailAddress.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", MessageUtils.getMessage("mail.validate.to.empty"));
            result.put("status", 400); // 400 Bad Request
            return result;
        }
        // 邮件主题不能为空
        if (mailRequest.subject == null || mailRequest.subject.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", MessageUtils.getMessage("mail.validate.subject.empty"));
            result.put("status", 400);
            return result;
        }
        // 邮件内容不能为空
        if (mailRequest.textContent == null || mailRequest.textContent.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", MessageUtils.getMessage("mail.validate.content.empty"));
            result.put("status", 400);
            return result;
        }

        // 调用 SMTP 客户端开启通道并发送邮件
        try (SmtpClient client = new SmtpClient()) {
            // 打开 SMTP 客户端连接
            client.open();

            // 调用邮件发送客户端进行投递，演示模式下会将发送详情输出至日志
            client.sendMail(mailRequest);

            // 投递成功，返回成功信息
            result.put("success", true);
            if (SmtpDebugFlag.SIMULATION.getValue().equals(client.getDebugFlag())) {
                result.put("message", MessageUtils.getMessage("mail.immediate.success.simulation"));
            } else {
                result.put("message", MessageUtils.getMessage("mail.immediate.success.normal"));
            }
            result.put("status", 200); // 200 OK
            return result;
        } catch (Exception e) {

            // 捕获异常并记录错误日志
            logger.error(MessageUtils.getMessage("mail.immediate.send.error"), e);

            // 返回失败信息，包含异常原因
            result.put("success", false);
            result.put("message", MessageUtils.getMessage("mail.immediate.failed.prefix", e.getMessage()));
            result.put("status", 500); // 500 Internal Server Error
            return result;
        }
    }
}
