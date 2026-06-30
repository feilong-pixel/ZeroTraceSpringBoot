package com.feilonglab.springboot.web.threadpool.sendmail;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.feilonglab.smtp.unified.MailRequest;
import com.feilonglab.springboot.model.entity.MailInfo;

/**
 * 场景 3：邮件发送线程池并发发送模式控制层。
 * 暴露 Web API 接口，用于接收批量邮件发送请求并将其分配到本地的并发线程池。
 */
@RestController
@RequestMapping("/threadpool")
public class ThreadPoolSendMailController {

    @Autowired
    private ThreadPoolSendMailService threadPoolSendMailService;

    /**
     * 接收批量邮件发送请求，将它们持久化到数据库，并提交给线程池异步执行。
     * 支持捕获本地线程池排队溢出的 RejectedExecutionException，以便与 MQ 的溢出拒绝逻辑进行对比。
     *
     * @param mailRequests 邮件发送请求列表
     * @return 包含提交结果的响应实体
     */
    @PostMapping("/sendmail")
    public ResponseEntity<Map<String, Object>> sendMailAsync(@RequestBody List<MailRequest> mailRequests) {
        Map<String, Object> result = new HashMap<>();

        if (mailRequests == null || mailRequests.isEmpty()) {
            result.put("success", false);
            result.put("message", "请求的邮件列表不能为空。");
            return ResponseEntity.badRequest().body(result);
        }

        // 1. 参数校验
        for (MailRequest request : mailRequests) {
            if (request.ToMailAddress == null || request.ToMailAddress.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "收件人邮箱地址不能为空。");
                return ResponseEntity.badRequest().body(result);
            }
            if (request.subject == null || request.subject.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "邮件主题不能为空。");
                return ResponseEntity.badRequest().body(result);
            }
            if (request.textContent == null || request.textContent.trim().isEmpty()) {
                result.put("success", false);
                result.put("message", "邮件正文不能为空。");
                return ResponseEntity.badRequest().body(result);
            }
        }

        List<Long> successMailIds = new ArrayList<>();
        int rejectedCount = 0;
        boolean overflowOccurred = false;
        String overflowErrorMsg = null;

        // 2. 依次持久化并向线程池提交任务
        for (MailRequest request : mailRequests) {
            // 在独立新事务中持久化 MailInfo，以便即便发生拒绝，邮件信息也能入库记录
            MailInfo mailInfo = threadPoolSendMailService.persistMail(request);
            Long mailId = mailInfo.getMailId();

            if (overflowOccurred) {
                // 如果先前已有任务被线程池拒绝，为防止请求堆积，后续任务直接快速失败拒绝
                rejectedCount++;
                threadPoolSendMailService.saveFailure(mailInfo, "本地线程池已发生溢出，任务被直接拒绝提交");
                continue;
            }

            try {
                // 提交给线程池异步并发发送
                threadPoolSendMailService.sendMailAsync(mailId);
                successMailIds.add(mailId);
            } catch (RejectedExecutionException e) {
                overflowOccurred = true;
                overflowErrorMsg = e.getMessage();
                rejectedCount++;
                // 提交被线程池拒绝，更新该邮件状态为失败并写入失败日志
                threadPoolSendMailService.saveFailure(mailInfo, "本地线程池队列满被拒绝: " + e.getMessage());
            }
        }

        // 3. 构建响应结果
        if (overflowOccurred) {
            result.put("success", false);
            result.put("message", "提交任务时发生本地线程池队列溢出。已成功提交数: " 
                       + successMailIds.size() + ", 被拒绝/丢弃数: " + rejectedCount + ". 错误原因: " + overflowErrorMsg);
            result.put("successMailIds", successMailIds);
            result.put("rejectedCount", rejectedCount);
            // 线程池队列溢出属于系统负载问题，返回 INTERNAL_SERVER_ERROR (500)
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
        } else {
            result.put("success", true);
            result.put("message", "所有邮件已成功提交到本地线程池进行并发投递。共计: " + successMailIds.size() + " 封。");
            result.put("successMailIds", successMailIds);
            return ResponseEntity.ok(result);
        }
    }
}
