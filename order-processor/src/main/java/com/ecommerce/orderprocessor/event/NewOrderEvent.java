package com.ecommerce.orderprocessor.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record NewOrderEvent(
        @JsonProperty("order_id") String orderId,
        @JsonProperty("cliente") String cliente,
        @JsonProperty("correo") String correo,
        @JsonProperty("item") String producto,
        @JsonProperty("cantidad") int cantidad,
        @JsonProperty("monto") BigDecimal monto) {
}
