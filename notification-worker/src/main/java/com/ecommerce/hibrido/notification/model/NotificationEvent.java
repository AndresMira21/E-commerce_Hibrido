package com.ecommerce.hibrido.notification.model;

import java.util.UUID;

public record NotificationEvent(UUID order_id, String status) {
    public NotificationEvent {
        if (order_id == null) throw new IllegalArgumentException("order_id es obligatorio");
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status es obligatorio");
    }
}
