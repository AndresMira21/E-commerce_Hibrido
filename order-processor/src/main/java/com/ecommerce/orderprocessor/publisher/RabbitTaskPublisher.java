package com.ecommerce.orderprocessor.publisher;

import com.ecommerce.orderprocessor.command.ProcessOrderCommand;
import com.ecommerce.orderprocessor.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitTaskPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitTaskPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(ProcessOrderCommand command) {
        rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_TASKS_EXCHANGE, "", command);
    }
}
