package com.feilonglab.springboot.web.mq.sendmail;

import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

/**
 * RabbitMQ / AMQP 消息队列配置类。 定义邮件发送场景所使用的交换机、队列和绑定规则。
 */
@Configuration
@PropertySource("classpath:mq.properties")
public class MqConfig {

    /**
     * 邮件队列名称，从配置文件中读取，默认为 "mail.queue"。
     */
    @Value("${mail.queue:mail.queue}")
    private String queueName;

    /**
     * 邮件交换机名称，从配置文件中读取，默认为 "mail.exchange"。
     */
    @Value("${mail.exchange:mail.exchange}")
    private String exchangeName;

    /**
     * 邮件路由键，从配置文件中读取，默认为 "mail.routing.key"。
     */
    @Value("${mail.routing.key:mail.routing.key}")
    private String routingKey;

    /**
     * 邮件队列最大长度，从配置文件中读取，默认为 0（表示不限制）。
     */
    @Value("${mail.queue.max-length:0}")
    private Integer maxLength;

    /**
     * 邮件队列溢出策略，从配置文件中读取，默认为空字符串（表示不设置）。
     */
    @Value("${mail.queue.overflow:}")
    private String overflow;

    /**
     * Rabbit listener 并发消费者（最小线程数）
     */
    @Value("${mail.listener.concurrentConsumers:1}")
    private int concurrentConsumers;

    /**
     * Rabbit listener 最大并发消费者（最大线程数）
     */
    @Value("${mail.listener.maxConcurrentConsumers:5}")
    private int maxConcurrentConsumers;

    /**
     * 定义持久化的邮件队列。
     *
     * @return 队列 Bean
     */
    @Bean
    public Queue mailQueue() {
        // 创建一个持久化的队列，并设置最大长度和溢出策略
        Map<String, Object> args = new HashMap<>();
        // 如果配置了最大长度，则设置队列的最大长度属性
        if (maxLength != null && maxLength > 0) {
            args.put("x-max-length", maxLength);
        }
        // 如果配置了溢出策略，则设置队列的溢出策略属性
        if (overflow != null && !overflow.trim().isEmpty()) {
            args.put("x-overflow", overflow);
        }
        return new Queue(queueName, true, false, false, args); // durable = true
    }

    /**
     * 定义主题交换机。
     *
     * @return 交换机 Bean
     */
    @Bean
    public TopicExchange mailExchange() {
        // 创建一个持久化的主题交换机
        return new TopicExchange(exchangeName);
    }

    /**
     * 为 `@RabbitListener` 提供自定义的容器工厂，以便配置并发消费者数量（最小/最大线程数）。
     * 
     * @param connectionFactory RabbitMQ 连接工厂
     * @return SimpleRabbitListenerContainerFactory Bean
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        // 创建一个自定义的 SimpleRabbitListenerContainerFactory 实例
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();

        // 设置连接工厂
        factory.setConnectionFactory(connectionFactory);
        // 设置并发消费者数量（最小线程数）
        factory.setConcurrentConsumers(concurrentConsumers);
        // 设置最大并发消费者数量（最大线程数）
        factory.setMaxConcurrentConsumers(maxConcurrentConsumers);
        return factory;
    }

    /**
     * 绑定队列到交换机，并指定路由键。
     *
     * @param mailQueue    定义的邮件队列 Bean
     * @param mailExchange 定义的邮件交换机 Bean
     * @return 绑定对象 Bean
     */
    @Bean
    public Binding mailBinding(Queue mailQueue, TopicExchange mailExchange) {
        // 使用 BindingBuilder 将队列绑定到交换机，并指定路由键
        return BindingBuilder.bind(mailQueue).to(mailExchange).with(routingKey);
    }
}
