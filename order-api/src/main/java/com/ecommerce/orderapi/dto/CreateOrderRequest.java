package com.ecommerce.orderapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotBlank String cliente,
        @NotBlank @Email String correo,
        @NotBlank String item,
        @Positive int cantidad,
        @NotNull @Positive BigDecimal monto) {
}
