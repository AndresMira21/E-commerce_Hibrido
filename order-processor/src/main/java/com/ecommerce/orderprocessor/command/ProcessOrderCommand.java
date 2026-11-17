package com.ecommerce.orderprocessor.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record ProcessOrderCommand(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("cliente") String cliente,
        @JsonProperty("correo") String correo,
        @JsonProperty("item") String producto,
        @JsonProperty("cantidad") int cantidad,
        @JsonProperty("monto") BigDecimal monto) {

    @JsonProperty("accion")
    public String accion() {
        return "PROCESS_ORDER";
    }
}
