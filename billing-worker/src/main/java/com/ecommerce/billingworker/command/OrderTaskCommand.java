package com.ecommerce.billingworker.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderTaskCommand(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("accion") String accion,
        @JsonProperty("cliente") String cliente,
        @JsonProperty("correo") String correo,
        @JsonProperty("item") String producto,
        @JsonProperty("cantidad") Integer cantidad,
        @JsonProperty("monto") BigDecimal monto) {

    public OrderTaskCommand {
        if (orderId == null) {
            throw new IllegalArgumentException("El identificador de la orden es obligatorio");
        }
        if (!"PROCESS_ORDER".equals(accion)) {
            throw new IllegalArgumentException("La accion debe ser PROCESS_ORDER");
        }
        if (producto == null || producto.isBlank()) {
            throw new IllegalArgumentException("El producto no puede estar vacio");
        }
        if (cantidad == null || cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        if (monto == null || monto.signum() < 0) {
            throw new IllegalArgumentException("El monto es obligatorio y no puede ser negativo");
        }
    }
}
