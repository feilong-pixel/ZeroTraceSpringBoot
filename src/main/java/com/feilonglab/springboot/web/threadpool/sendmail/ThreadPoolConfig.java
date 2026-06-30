package com.feilonglab.springboot.web.threadpool.sendmail;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 场景 3：专属邮件发送本地线程池配置类。
 * 定义专用于异步、并发邮件发送的 ThreadPoolTaskExecutor，以提供高吞吐的本地处理能力。
 */
@Configuration
@EnableAsync
public class ThreadPoolConfig {

    /** 核心线程数，从配置文件加载，默认 2 */
    @Value("${mail.threadpool.core-size:2}")
    private int coreSize;

    /** 最大线程数，从配置文件加载，默认 5 */
    @Value("${mail.threadpool.max-size:5}")
    private int maxSize;

    /** 阻塞队列容量，从配置文件加载，默认 10。小的容量便于展示溢出拒绝策略 */
    @Value("${mail.threadpool.queue-capacity:10}")
    private int queueCapacity;

    /**
     * 定义邮件发送线程池 Bean。
     * 配置 AbortPolicy 溢出策略，当池满且队列满时，将抛出 RejectedExecutionException。
     *
     * @return 线程池执行器实例
     */
    @Bean(name = "mailThreadPoolExecutor")
    public Executor mailThreadPoolExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(coreSize);
        executor.setMaxPoolSize(maxSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("mail-async-");
        
        // 设置溢出拒绝策略：抛出异常以示对比（可在控制层捕获并表现出差异性）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        
        executor.initialize();
        return executor;
    }
}
