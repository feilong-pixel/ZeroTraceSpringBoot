package com.feilonglab.springboot.web.index;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.feilonglab.smtp.unified.MailRequest;

/**
 * 邮件发送控制层。
 * 协调 MailService 进行 SMTP 配置加载和单封即时邮件发送的请求映射。
 */
@Controller
public class MailController {

    @Autowired
    private MailService mailService;

    /**
     * 渲染首页，绑定当前 SMTP 配置项到视图层。
     */
    @GetMapping("/")
    public String index(Model model) {
        // 获取当前 SMTP 配置并添加到模型中，以便在视图层显示
        Map<String, Object> config = mailService.getSmtpConfig();
        // 将配置项添加到模型中，供前端页面渲染
        model.addAllAttributes(config);
        return "index";
    }

    /**
     * 接收即时发送单封邮件请求，返回响应状态。
     */
    @PostMapping("/api/send")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendMail(@RequestBody MailRequest mailRequest) {
        // 调用 MailService 处理邮件发送请求
        Map<String, Object> result = mailService.sendImmediateMail(mailRequest);
        // 根据处理结果返回不同的 HTTP 响应状态
        int status = (int) result.get("status");
        result.remove("status"); // 移除内部辅助状态字段以防泄露
        
        // 返回响应实体，包含处理结果和相应的 HTTP 状态码
        if (status == 400) {
            return ResponseEntity.badRequest().body(result);
        } else if (status == 500) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        }
        return ResponseEntity.ok(result);
    }
}
