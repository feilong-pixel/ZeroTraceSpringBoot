package com.feilonglab.springboot.web.mq.sendmail;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.feilonglab.smtp.unified.MailRequest;
import com.feilonglab.springboot.util.MessageUtils;

/**
 * 邮件发送 MQ 模式控制层。 暴露 Web API 接口，用于接收邮件请求并将其推入消息队列进行异步可靠发送。
 */
@RestController
@RequestMapping("/mq")
public class MqSendMailController {

    /** 注入邮件发送服务 */
    @Autowired
    private MqSendMailService mqSendMailService;

    /**
     * 接收邮件发送请求，将其持久化并发布到 MQ。
     *
     * @param mailRequest 邮件发送请求对象
     * @return 包含执行成功标志、消息描述与持久化邮件 ID 的响应实体
     */
    @PostMapping("/sendmail")
    public ResponseEntity<Map<String, Object>> sendMailAsync(@RequestBody MailRequest mailRequest) {
        Map<String, Object> result = new HashMap<>();

        // 1. 参数非空及合法性校验
        // 检查收件人邮箱地址是否为空
        if (mailRequest.ToMailAddress == null || mailRequest.ToMailAddress.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", MessageUtils.getMessage("mail.validate.to.empty"));
            return ResponseEntity.badRequest().body(result);
        }
        // 检查邮件主题是否为空
        if (mailRequest.subject == null || mailRequest.subject.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", MessageUtils.getMessage("mail.validate.subject.empty"));
            return ResponseEntity.badRequest().body(result);
        }
        // 检查邮件正文内容是否为空
        if (mailRequest.textContent == null || mailRequest.textContent.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", MessageUtils.getMessage("mail.validate.content.empty"));
            return ResponseEntity.badRequest().body(result);
        }

        try {
            // 2. 调用业务层以异步消息队列方式发送邮件
            Long mailId = mqSendMailService.sendMailAsync(mailRequest);
            // 返回成功响应，包含邮件 ID
            result.put("success", true);
            result.put("message", "邮件已成功加入消息队列进行异步投递，邮件 ID: " + mailId);
            result.put("mailId", mailId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // 3. 捕获异常并返回错误响应
            result.put("success", false);
            result.put("message", "推入消息队列时发生异常: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
    }
}
