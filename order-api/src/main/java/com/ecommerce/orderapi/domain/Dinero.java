package com.ecommerce.orderapi.domain;
import java.math.BigDecimal;
public record Dinero(BigDecimal valor) {
    public Dinero {
        if (valor == null || valor.signum() <= 0) throw new IllegalArgumentException("El monto debe ser positivo");
    }
}
