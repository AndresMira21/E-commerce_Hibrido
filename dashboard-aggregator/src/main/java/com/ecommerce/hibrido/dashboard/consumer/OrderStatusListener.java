package com.ecommerce.hibrido.dashboard.consumer;

import com.ecommerce.hibrido.dashboard.model.StatusEvent;
import com.ecommerce.hibrido.dashboard.service.OrderStatusService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderStatusListener {
    private final OrderStatusService statusService;

    public OrderStatusListener(OrderStatusService statusService) {
        this.statusService = statusService;
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
