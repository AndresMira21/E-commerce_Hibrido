package com.ecommerce.orderprocessor.publisher;

import com.ecommerce.orderprocessor.command.ProcessOrderCommand;
import com.ecommerce.orderprocessor.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class RabbitTaskPublisher implements OrderTaskPublisher {
    private static final Logger log = LoggerFactory.getLogger(RabbitTaskPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public RabbitTaskPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void publish(ProcessOrderCommand command) {
        CorrelationData correlation = new CorrelationData(command.orderId() + ":" + UUID.randomUUID());
        try {
            rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_TASKS_EXCHANGE, "", command, correlation);
            var confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ rechazo o devolvio el comando order_id=" + command.orderId());
            }
            log.info("order_id={} publicacion confirmada exchange=order_tasks", command.orderId());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicacion interrumpida order_id=" + command.orderId(), exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("RabbitMQ no confirmo order_id=" + command.orderId(), exception);
        }
    }
}
