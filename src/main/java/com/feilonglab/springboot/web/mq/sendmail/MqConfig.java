package com.feilonglab.springboot.web.mq.sendmail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.config.JmsListenerContainerFactory;

import jakarta.jms.ConnectionFactory;

/**
 * Embedded ActiveMQ Artemis / JMS 消息队列配置类。
 * 定义邮件发送场景所使用的监听器容器工厂，配置并发消费者数量。
 */
@Configuration
@PropertySource("classpath:mq.properties")
public class MqConfig {

    /**
     * JMS listener 并发消费者配置（最小并发-最大并发数，例如 "1-5"）。
     * 由 Spring JMS 自动按需在这两者之间动态调配消费线程。
     */
    @Value("${mail.listener.concurrency:1-5}")
    private String concurrency;

    /**
     * 为 `@JmsListener` 提供自定义的容器工厂，以便配置并发消费者数量（最小/最大线程数）。
     * 
     * @param connectionFactory JMS 连接工厂 (由 Spring Boot Artemis 自动配置并注入)
     * @return JmsListenerContainerFactory Bean
     */
    @Bean
    public JmsListenerContainerFactory<?> jmsListenerContainerFactory(ConnectionFactory connectionFactory) {
        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrency(concurrency);
        return factory;
    }
}
