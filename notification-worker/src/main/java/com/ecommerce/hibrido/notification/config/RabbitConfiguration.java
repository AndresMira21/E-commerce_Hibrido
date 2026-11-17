package com.ecommerce.hibrido.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.beans.factory.annotation.Value;

@Configuration
public class RabbitConfiguration {
    @Bean
    FanoutExchange orderTasksExchange(@Value("${app.rabbitmq.exchange}") String exchange) {
        return new FanoutExchange(exchange, true, false);
    }

    @Bean
    Queue notificationQueue(@Value("${app.rabbitmq.queue}") String queue) {
        return QueueBuilder.durable(queue).build();
    }

    @Bean
    Binding notificationBinding(Queue notificationQueue, FanoutExchange orderTasksExchange) {
        return BindingBuilder.bind(notificationQueue).to(orderTasksExchange);
    }

    @Bean
    Jackson2JsonMessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        var factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        var retry = new RetryTemplate();
        retry.setRetryPolicy(new SimpleRetryPolicy(3));
        var backoff = new ExponentialBackOffPolicy();
        backoff.setInitialInterval(500L);
        backoff.setMultiplier(2.0);
        backoff.setMaxInterval(3000L);
        retry.setBackOffPolicy(backoff);
        factory.setRetryTemplate(retry);
        return factory;
    }
}
