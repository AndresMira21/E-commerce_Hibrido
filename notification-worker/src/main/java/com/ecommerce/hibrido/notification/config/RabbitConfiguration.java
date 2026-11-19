package com.ecommerce.hibrido.notification.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
        var converter = new Jackson2JsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
