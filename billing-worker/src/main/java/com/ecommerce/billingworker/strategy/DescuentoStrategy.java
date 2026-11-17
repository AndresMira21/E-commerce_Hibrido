package com.ecommerce.billingworker.strategy;

import java.math.BigDecimal;

public interface DescuentoStrategy {
    BigDecimal calcularDescuento(BigDecimal montoOriginal);
}
