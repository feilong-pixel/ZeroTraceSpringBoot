package com.feilonglab.springboot.web.batch.sendmail;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.feilonglab.springboot.util.MessageUtils;

/**
 * 邮件发送批处理 Controller。
 * 提供触发邮件批量发送作业的 Web 接口映射（GET/POST /batch/sendmail）。
 */
@RestController
@RequestMapping("/batch")
public class SendMailController {

    /** 邮件批量发送服务组件 */
    @Autowired
    private SendMailService sendMailService;

    /**
     * 响应 GET /batch/sendmail 请求。
     * 调用邮件发送服务触发批量发送。
     *
     * @return 包含执行成功标志与状态文本的响应实体 Map
     */
    @GetMapping("/sendmail")
    public ResponseEntity<Map<String, Object>> sendMailGet() {
        return executeSend();
    }

    /**
     * 响应 POST /batch/sendmail 请求。
     * 调用邮件发送服务触发批量发送。
     *
     * @return 包含执行成功标志与状态文本的响应实体 Map
     */
    @PostMapping("/sendmail")
    public ResponseEntity<Map<String, Object>> sendMailPost() {
        return executeSend();
    }

    /**
     * 内部通用执行邮件批量投递方法。
     * 统一处理 GET 与 POST 请求的分发逻辑，并对可能出现的异常进行捕获与国际化日志封装。
     *
     * @return 响应包装实体 Map
     */
    private ResponseEntity<Map<String, Object>> executeSend() {
        Map<String, Object> result = new HashMap<>();
        try {
            int successCount = sendMailService.processBatchSend();
            result.put("success", true);
            result.put("message", MessageUtils.getMessage("controller.batch.success"));
            result.put("successCount", successCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", MessageUtils.getMessage("controller.batch.error", e.getMessage()));
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
