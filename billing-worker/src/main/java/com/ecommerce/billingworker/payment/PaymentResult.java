package com.ecommerce.billingworker.payment;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentResult(UUID orderId, Status status, BigDecimal montoOriginal,
                            BigDecimal descuento, BigDecimal totalFinal) {
    public enum Status {
        PAYMENT_SUCCESS,
        PAYMENT_FAILED
    }
}
