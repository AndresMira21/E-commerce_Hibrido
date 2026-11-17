package com.ecommerce.hibrido.dashboard.model;

import java.util.UUID;

public record StatusEvent(UUID order_id, String status) {
    public StatusEvent {
        if (order_id == null) throw new IllegalArgumentException("order_id es obligatorio");
        if (status == null || status.isBlank()) throw new IllegalArgumentException("status es obligatorio");
    }
}
