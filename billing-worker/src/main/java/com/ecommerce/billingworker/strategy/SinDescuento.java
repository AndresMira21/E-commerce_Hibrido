package com.ecommerce.billingworker.strategy;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class SinDescuento implements DescuentoStrategy {
    @Override
    public BigDecimal calcularDescuento(BigDecimal montoOriginal) {
        return new BigDecimal("0.00");
    }
}
