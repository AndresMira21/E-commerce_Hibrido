package com.ecommerce.billingworker.publisher;

import com.ecommerce.billingworker.event.PaymentEvent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaPaymentEventPublisher implements PaymentEventPublisher {
    private final KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    public KafkaPaymentEventPublisher(KafkaTemplate<String, PaymentEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(PaymentEvent event) {
        try {
            kafkaTemplate.send("billing_events", event.orderId().toString(), event)
                    .get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicacion del pago interrumpida", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("No se confirmo el evento de pago en Kafka", exception);
        }
    }
}
