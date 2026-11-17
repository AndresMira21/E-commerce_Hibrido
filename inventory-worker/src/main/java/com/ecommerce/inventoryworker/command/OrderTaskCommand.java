package com.ecommerce.inventoryworker.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.UUID;

public record OrderTaskCommand(
        @JsonProperty("order_id") UUID orderId,
        @JsonProperty("accion") String accion,
        @JsonProperty("cliente") String cliente,
        @JsonProperty("correo") String correo,
        @JsonProperty("item") String productName,
        @JsonProperty("cantidad") Integer quantity,
        @JsonProperty("monto") BigDecimal monto) {

    public OrderTaskCommand {
        if (orderId == null) {
            throw new IllegalArgumentException("El identificador de la orden es obligatorio");
        }
        if (!"PROCESS_ORDER".equals(accion)) {
            throw new IllegalArgumentException("La accion debe ser PROCESS_ORDER");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("El producto es obligatorio");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        if (monto == null || monto.signum() < 0) {
            throw new IllegalArgumentException("El monto es obligatorio y no puede ser negativo");
        }
    }
}
