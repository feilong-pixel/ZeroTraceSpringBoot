package com.feilonglab.springboot.web.scheduler.sendmail;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import com.feilonglab.springboot.model.dao.MailInfoDao;

/**
 * 场景 4：定时调度与重试补偿控制层。 提供手动触发重试以及查看当前定时任务状态的 RESTful Web 接口。
 */
@RestController
@RequestMapping("/scheduler")
public class SchedulerSendMailController {

    /** 定时任务服务 */
    @Autowired
    private SchedulerSendMailService schedulerSendMailService;

    /** 邮件信息数据访问对象 */
    @Autowired
    private MailInfoDao mailInfoDao;

    /** 消息源 */
    @Autowired
    private MessageSource messageSource;

    /**
     * 手动触发一次定时补偿和发送循环。
     *
     * @return 触发结果与成功投递/重试的邮件数量
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerPoll() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 调用服务层方法执行定时轮询和发送逻辑
            int count = schedulerSendMailService.pollAndSendPendingMails();
            result.put("success", true);
            String message = messageSource.getMessage("ui.scheduler.trigger.success", new Object[] { count },
                    LocaleContextHolder.getLocale());
            result.put("message", message);
            result.put("successCount", count);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            // 捕获异常并返回错误信息，避免手动触发失败导致服务中断
            result.put("success", false);
            String message = messageSource.getMessage("ui.scheduler.trigger.error", new Object[] { e.getMessage() },
                    LocaleContextHolder.getLocale());
            result.put("message", message);
            return ResponseEntity.internalServerError().body(result);
        }
    }

    /**
     * 查询定时轮询器的运行状态。 返回是否启用、轮询间隔以及当前待发送和需补偿的邮件总数。
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
            String message = messageSource.getMessage("ui.scheduler.status.error", new Object[] { e.getMessage() },
                    LocaleContextHolder.getLocale());
            result.put("message", message);
            return ResponseEntity.internalServerError().body(result);
        }
    }
}
