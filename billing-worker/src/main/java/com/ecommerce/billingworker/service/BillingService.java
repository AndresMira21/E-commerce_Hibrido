package com.ecommerce.billingworker.service;

import com.ecommerce.billingworker.command.OrderTaskCommand;
import com.ecommerce.billingworker.payment.PaymentResult;
import com.ecommerce.billingworker.payment.PaymentResult.Status;
import com.ecommerce.billingworker.strategy.DescuentoStrategySelector;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);
    private static final BigDecimal LIMITE_APROBACION = new BigDecimal("1000000.00");
    private final DescuentoStrategySelector strategySelector;

    public BillingService(DescuentoStrategySelector strategySelector) {
        this.strategySelector = strategySelector;
    }

    public PaymentResult processOrder(OrderTaskCommand command) {
        log.info("Orden recibida para facturacion: id={}, producto={}, cantidad={}, monto={}",
                command.orderId(), command.producto(), command.cantidad(), command.monto());
        // El umbral se evalua con el monto recibido, antes de redondearlo a centavos.
        BigDecimal descuento = strategySelector.seleccionar(command.monto())
                .calcularDescuento(command.monto()).setScale(2, RoundingMode.HALF_UP);
        BigDecimal montoOriginal = command.monto().setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalFinal = montoOriginal.subtract(descuento).setScale(2, RoundingMode.HALF_UP);
        Status status = totalFinal.compareTo(LIMITE_APROBACION) <= 0
                ? Status.PAYMENT_SUCCESS : Status.PAYMENT_FAILED;
        return new PaymentResult(command.orderId(), status, montoOriginal, descuento, totalFinal);
    }
}
