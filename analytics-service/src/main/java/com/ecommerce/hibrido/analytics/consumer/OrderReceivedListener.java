package com.ecommerce.hibrido.analytics.consumer;

import com.ecommerce.hibrido.analytics.model.OrderReceivedEvent;
import com.ecommerce.hibrido.analytics.service.OrderAnalytics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderReceivedListener {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderReceivedListener.class);
    private final OrderAnalytics analytics;

    public OrderReceivedListener(OrderAnalytics analytics) {
        this.analytics = analytics;
    }

    @KafkaListener(topics = "${app.kafka.orders-topic}")
    public void onOrderReceived(OrderReceivedEvent event) {
        boolean inserted = analytics.record(event);
        log.info("order_id={} new_orders recibido analytics_insertado={}", event.order_id(), inserted);
    }
}
