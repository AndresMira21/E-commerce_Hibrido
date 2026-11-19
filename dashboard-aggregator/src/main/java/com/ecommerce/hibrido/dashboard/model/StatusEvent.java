package com.ecommerce.hibrido.dashboard.model;

import java.util.UUID;
import java.util.Set;

public record StatusEvent(UUID order_id, String status) {
    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "PAYMENT_SUCCESS", "PAYMENT_FAILED", "STOCK_RESERVED", "OUT_OF_STOCK",
            "STOCK_OUT_OF_STOCK", "EMAIL_SENT");

    public StatusEvent {
        if (order_id == null) throw new IllegalArgumentException("order_id es obligatorio");
        if (status == null || !ALLOWED_STATUSES.contains(status)) {
            throw new IllegalArgumentException("status no corresponde a un evento conocido");
        }
    }
}
