package com.ecommerce.billingworker.listener;

import com.ecommerce.billingworker.command.OrderTaskCommand;
import com.ecommerce.billingworker.service.BillingService;
import com.ecommerce.billingworker.event.PaymentEvent;
import com.ecommerce.billingworker.payment.PaymentResult;
import com.ecommerce.billingworker.publisher.PaymentEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BillingQueueListener {
    private static final Logger log = LoggerFactory.getLogger(BillingQueueListener.class);

    private final ObjectMapper objectMapper;
    private final BillingService billingService;
    private final PaymentEventPublisher publisher;

    public BillingQueueListener(ObjectMapper objectMapper, BillingService billingService,
                                PaymentEventPublisher publisher) {
        this.objectMapper = objectMapper;
        this.billingService = billingService;
        this.publisher = publisher;
    }

    @RabbitListener(queues = "billing_queue", containerFactory = "billingRabbitListenerContainerFactory")
    public void onMessage(Message message) throws IOException {
        // Solo se interpreta el cuerpo JSON, sin depender del encabezado Java __TypeId__.
        OrderTaskCommand command;
        try {
            command = objectMapper.readValue(message.getBody(), OrderTaskCommand.class);
            if (command == null) throw new IllegalArgumentException("Comando obligatorio");
        } catch (IOException | IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Comando de facturacion invalido", exception);
        }
        PaymentResult result = billingService.processOrder(command);
        publisher.publish(new PaymentEvent(result.orderId(), result.status().name()));
        log.info("order_id={} billing_events confirmado status={}", result.orderId(), result.status());
    }
}
