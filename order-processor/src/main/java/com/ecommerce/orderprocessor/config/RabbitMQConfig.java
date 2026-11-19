package com.ecommerce.orderprocessor.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String ORDER_TASKS_EXCHANGE = "order_tasks";

    @Bean
    public Declarables orderTasksTopology() {
        FanoutExchange exchange = new FanoutExchange(ORDER_TASKS_EXCHANGE, true, false);
        Queue billing = new Queue("billing_queue", true);
        Queue inventory = new Queue("inventory_queue", true);
        Queue notification = new Queue("notification_queue", true);
        return new Declarables(exchange, billing, inventory, notification,
                BindingBuilder.bind(billing).to(exchange),
                BindingBuilder.bind(inventory).to(exchange),
                BindingBuilder.bind(notification).to(exchange));
    }

    @Bean
    public Jackson2JsonMessageConverter rabbitMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
