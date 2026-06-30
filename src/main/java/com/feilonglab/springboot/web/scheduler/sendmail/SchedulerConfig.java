package com.feilonglab.springboot.web.scheduler.sendmail;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 场景 4：定时调度配置类。
 * 开启 Spring 任务调度器，用于定时扫描数据库并补偿重试发送。
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
    // 采用 Spring Boot 默认自动配置的任务调度器即可，此处仅用于启用 @EnableScheduling。
}
