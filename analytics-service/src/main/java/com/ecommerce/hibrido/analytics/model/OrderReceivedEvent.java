package com.ecommerce.hibrido.analytics.model;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderReceivedEvent(UUID order_id, String cliente, String correo, String item,
                                Integer cantidad, BigDecimal monto) {
    public OrderReceivedEvent {
        if (order_id == null) throw new IllegalArgumentException("order_id es obligatorio");
        if (monto == null || monto.signum() < 0) throw new IllegalArgumentException("monto debe ser no negativo");
    }
}
