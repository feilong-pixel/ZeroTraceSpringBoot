package com.feilonglab.springboot.web.scheduler.sendmail;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.feilonglab.springboot.model.dao.MailInfoDao;

/**
 * 场景 4：定时调度与重试补偿控制层。
 * 提供手动触发重试以及查看当前定时任务状态的 RESTful Web 接口。
 */
@RestController
@RequestMapping("/scheduler")
public class SchedulerSendMailController {

    @Autowired
    private SchedulerSendMailService schedulerSendMailService;

    @Autowired
    private MailInfoDao mailInfoDao;

    /**
     * 手动触发一次定时补偿和发送循环。
     *
     * @return 触发结果与成功投递/重试的邮件数量
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerPoll() {
        Map<String, Object> result = new HashMap<>();
        try {
            int count = schedulerSendMailService.pollAndSendPendingMails();
            result.put("success", true);
            result.put("message", "定时重试任务手动触发成功，本次成功投递/补偿邮件数: " + count + " 封。");
            result.put("successCount", count);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "手动触发执行定时发送任务时发生异常: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 查询定时轮询器的运行状态。
     * 返回是否启用、轮询间隔以及当前待发送和需补偿的邮件总数。
     *
     * @return 包含调度器状态属性的响应集合
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean enabled = schedulerSendMailService.isSchedulerEnabled();
            String cron = schedulerSendMailService.getCronExpression();
            
            // 查询当前等待发送（重试小于3次）的邮件数
            int pendingOrFailedCount = mailInfoDao.selectUnsentOrFailed(3).size();

            result.put("success", true);
            result.put("enabled", enabled);
            result.put("cron", cron);
            result.put("pendingOrFailedCount", pendingOrFailedCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询定时调度状态发生异常: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
