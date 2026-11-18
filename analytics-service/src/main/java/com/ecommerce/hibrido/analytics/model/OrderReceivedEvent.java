package com.ecommerce.hibrido.analytics.model;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderReceivedEvent(UUID order_id, String cliente, String correo, String item,
                                Integer cantidad, BigDecimal monto) {
    public OrderReceivedEvent {
        if (order_id == null) throw new IllegalArgumentException("order_id es obligatorio");
        if (cliente == null || cliente.isBlank()) throw new IllegalArgumentException("cliente es obligatorio");
        if (correo == null || !correo.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("correo debe tener formato válido");
        }
        if (item == null || item.isBlank()) throw new IllegalArgumentException("item es obligatorio");
        if (cantidad == null || cantidad <= 0) throw new IllegalArgumentException("cantidad debe ser positiva");
        if (monto == null || monto.signum() < 0) throw new IllegalArgumentException("monto debe ser no negativo");
    }
}
