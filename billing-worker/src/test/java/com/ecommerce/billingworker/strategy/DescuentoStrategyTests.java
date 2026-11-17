package com.ecommerce.billingworker.strategy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class DescuentoStrategyTests {

    @Test
    void sinDescuentoReturnsZero() {
        assertThat(new SinDescuento().calcularDescuento(new BigDecimal("200000")))
                .isEqualTo(new BigDecimal("0.00"));
    }

    @ParameterizedTest
    @CsvSource({"99999.99,0.00", "100000,10000.00", "100000.05,10000.01", "200000,20000.00"})
    void amountDiscountHonorsThresholdAndHalfUp(String amount, String expected) {
        assertThat(new DescuentoPorMonto().calcularDescuento(new BigDecimal(amount)))
                .isEqualTo(new BigDecimal(expected));
    }

    @ParameterizedTest
    @CsvSource({"0,false", "99999.999,false", "100000,true", "100000.01,true"})
    void selectsStrategyUsingOriginalAmount(String amount, boolean discounted) {
        SinDescuento none = new SinDescuento();
        DescuentoPorMonto discount = new DescuentoPorMonto();
        DescuentoStrategySelector selector = new DescuentoStrategySelector(none, discount);
        assertThat(selector.seleccionar(new BigDecimal(amount))).isSameAs(discounted ? discount : none);
    }
}
