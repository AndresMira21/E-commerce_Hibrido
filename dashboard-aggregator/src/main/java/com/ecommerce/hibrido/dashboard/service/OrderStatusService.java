package com.ecommerce.hibrido.dashboard.service;

import com.ecommerce.hibrido.dashboard.model.OrderStatus;
import com.ecommerce.hibrido.dashboard.model.StatusEvent;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class OrderStatusService {
    private final ConcurrentMap<UUID, OrderStatus> statuses = new ConcurrentHashMap<>();
    private final SimpMessagingTemplate messagingTemplate;

    public OrderStatusService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public OrderStatus updatePayment(StatusEvent event) {
        return update(event, (current, status) -> new OrderStatus(current.order_id(), status, current.stock(), current.email()));
    }

    public OrderStatus updateStock(StatusEvent event) {
        return update(event, (current, status) -> new OrderStatus(current.order_id(), current.payment(), status, current.email()));
    }

    public OrderStatus updateEmail(StatusEvent event) {
        return update(event, (current, status) -> new OrderStatus(current.order_id(), current.payment(), current.stock(), status));
    }

    public synchronized List<OrderStatus> allStatuses() {
        return statuses.values().stream().sorted(Comparator.comparing(s -> s.order_id().toString())).toList();
    }

    private synchronized OrderStatus update(StatusEvent event, FieldUpdater updater) {
        var updated = statuses.compute(event.order_id(), (id, current) ->
                updater.apply(current == null ? OrderStatus.pending(id) : current, event.status()));
        messagingTemplate.convertAndSend("/topic/orders", updated);
        return updated;
    }

    @FunctionalInterface
    private interface FieldUpdater {
        OrderStatus apply(OrderStatus current, String value);
    }
}
