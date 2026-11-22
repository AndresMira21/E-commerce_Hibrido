package com.ecommerce.hibrido.dashboard.consumer;

import com.ecommerce.hibrido.dashboard.model.StatusEvent;
import com.ecommerce.hibrido.dashboard.service.OrderStatusService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

@Component
public class OrderStatusListener {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderStatusListener.class);
    private final ObjectMapper json = new ObjectMapper();
    private final OrderStatusService statusService;

    public OrderStatusListener(OrderStatusService statusService) {
        this.statusService = statusService;
    }

    @KafkaListener(topics = "new_orders", properties = {
            "value.deserializer=org.apache.kafka.common.serialization.StringDeserializer"
    })
    public void onOrderReceived(ConsumerRecord<String, String> record) {
        UUID id;
        try {
            id = UUID.fromString(json.readTree(record.value()).path("order_id").asText());
            if (record.key() != null && !id.toString().equals(record.key())) throw new IllegalArgumentException("Invalid key");
        } catch (Exception invalid) {
            log.warn("Orden no legible en new_orders, particion {} offset {}", record.partition(), record.offset());
            return;
        }
        statusService.orderReceived(id);
    }

    @KafkaListener(topics = "${app.kafka.billing-topic}")
    public void onBillingEvent(StatusEvent event) {
        statusService.updatePayment(event);
    }

    @KafkaListener(topics = "${app.kafka.inventory-topic}")
    public void onInventoryEvent(StatusEvent event) {
        statusService.updateStock(event);
    }

    @KafkaListener(topics = "${app.kafka.notification-topic}")
    public void onNotificationEvent(StatusEvent event) {
        statusService.updateEmail(event);
    }
}
