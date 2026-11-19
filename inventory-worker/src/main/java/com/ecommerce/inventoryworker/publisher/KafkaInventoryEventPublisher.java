package com.ecommerce.inventoryworker.publisher;

import com.ecommerce.inventoryworker.event.InventoryEvent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaInventoryEventPublisher implements InventoryEventPublisher {

    private final KafkaTemplate<String, InventoryEvent> kafkaTemplate;
    private final String topic;

    public KafkaInventoryEventPublisher(KafkaTemplate<String, InventoryEvent> kafkaTemplate,
            @Value("${app.kafka.inventory-topic:inventory_events}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    @Override
    public void publish(InventoryEvent event) {
        try {
            kafkaTemplate.send(topic, event.orderId().toString(), event).get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicacion de inventario interrumpida", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("No se confirmo el evento de inventario en Kafka", exception);
        }
    }
}
