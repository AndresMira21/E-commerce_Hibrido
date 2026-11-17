package com.ecommerce.hibrido.notification.service;

import com.ecommerce.hibrido.notification.model.NotificationEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class KafkaNotificationEventPublisher implements NotificationEventPublisher {
    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;
    private final String topic;

    public KafkaNotificationEventPublisher(KafkaTemplate<String, NotificationEvent> kafkaTemplate,
                                           @Value("${app.kafka.notification-topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(NotificationEvent event) {
        try {
            kafkaTemplate.send(topic, event.order_id().toString(), event).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrumpido al publicar el evento de notificación", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("No se pudo publicar el evento de notificación", exception);
        }
    }
}
