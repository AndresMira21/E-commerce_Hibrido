package com.ecommerce.billingworker.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class DescuentoPorMonto implements DescuentoStrategy {

    public static final BigDecimal MONTO_MINIMO = new BigDecimal("100000");
    private static final BigDecimal PORCENTAJE = new BigDecimal("0.10");

    @Override
    public BigDecimal calcularDescuento(BigDecimal montoOriginal) {
        if (montoOriginal.compareTo(MONTO_MINIMO) < 0) {
            return new BigDecimal("0.00");
        }
        return montoOriginal.multiply(PORCENTAJE).setScale(2, RoundingMode.HALF_UP);
    }
}
