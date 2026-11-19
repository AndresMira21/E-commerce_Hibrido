package com.ecommerce.hibrido.notification.model;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCommand(UUID order_id, String accion, String item, Integer cantidad, BigDecimal monto) {
    public OrderCommand {
        if (order_id == null) throw new IllegalArgumentException("order_id es obligatorio");
        if (!"PROCESS_ORDER".equals(accion)) throw new IllegalArgumentException("accion debe ser PROCESS_ORDER");
        if (item == null || item.isBlank()) throw new IllegalArgumentException("item es obligatorio");
        if (cantidad == null || cantidad <= 0) throw new IllegalArgumentException("cantidad debe ser positiva");
        if (monto == null || monto.signum() < 0) throw new IllegalArgumentException("monto debe ser no negativo");
    }
}
