package com.ecommerce.hibrido.analytics.service;

import com.ecommerce.hibrido.analytics.model.AnalyticsSnapshot;
import com.ecommerce.hibrido.analytics.model.OrderReceivedEvent;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class OrderAnalytics {
    private final Map<UUID, BigDecimal> observedOrders = new HashMap<>();

    public synchronized boolean record(OrderReceivedEvent event) {
        return observedOrders.putIfAbsent(event.order_id(), event.monto()) == null;
    }

    public synchronized AnalyticsSnapshot snapshot() {
        var total = observedOrders.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        var count = observedOrders.size();
        var average = count == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        return new AnalyticsSnapshot(count, total, average);
    }
}
