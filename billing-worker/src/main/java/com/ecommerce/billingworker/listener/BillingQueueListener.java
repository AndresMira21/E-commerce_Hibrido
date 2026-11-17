package com.ecommerce.billingworker.listener;

import com.ecommerce.billingworker.command.OrderTaskCommand;
import com.ecommerce.billingworker.service.BillingService;
import com.ecommerce.billingworker.event.PaymentEvent;
import com.ecommerce.billingworker.payment.PaymentResult;
import com.ecommerce.billingworker.publisher.PaymentEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class BillingQueueListener {

    private final ObjectMapper objectMapper;
    private final BillingService billingService;
    private final PaymentEventPublisher publisher;

    public BillingQueueListener(ObjectMapper objectMapper, BillingService billingService,
                                PaymentEventPublisher publisher) {
        this.objectMapper = objectMapper;
        this.billingService = billingService;
        this.publisher = publisher;
    }

    @RabbitListener(queues = "billing_queue")
    public void onMessage(Message message) throws IOException {
        // Solo se interpreta el cuerpo JSON, sin depender del encabezado Java __TypeId__.
        OrderTaskCommand command = objectMapper.readValue(message.getBody(), OrderTaskCommand.class);
        PaymentResult result = billingService.processOrder(command);
        publisher.publish(new PaymentEvent(result.orderId(), result.status().name()));
    }
}
