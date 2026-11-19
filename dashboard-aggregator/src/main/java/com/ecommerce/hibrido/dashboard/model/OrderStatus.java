package com.ecommerce.hibrido.dashboard.model;

import java.util.UUID;

public record OrderStatus(UUID order_id, String payment, String stock, String email) {
    public static OrderStatus pending(UUID orderId) {
        return new OrderStatus(orderId, "PENDING", "PENDING", "PENDING");
    }
}
