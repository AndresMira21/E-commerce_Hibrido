package com.ecommerce.billingworker.strategy;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class DescuentoStrategySelector {

    private final SinDescuento sinDescuento;
    private final DescuentoPorMonto descuentoPorMonto;

    public DescuentoStrategySelector(SinDescuento sinDescuento, DescuentoPorMonto descuentoPorMonto) {
        this.sinDescuento = sinDescuento;
        this.descuentoPorMonto = descuentoPorMonto;
    }

    public DescuentoStrategy seleccionar(BigDecimal montoOriginal) {
        return montoOriginal.compareTo(DescuentoPorMonto.MONTO_MINIMO) >= 0
                ? descuentoPorMonto : sinDescuento;
    }
}
