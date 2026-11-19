package com.ecommerce.billingworker.service;

import com.ecommerce.billingworker.command.OrderTaskCommand;
import com.ecommerce.billingworker.payment.PaymentResult;
import com.ecommerce.billingworker.payment.PaymentResult.Status;
import com.ecommerce.billingworker.strategy.DescuentoPorMonto;
import com.ecommerce.billingworker.strategy.DescuentoStrategy;
import com.ecommerce.billingworker.strategy.DescuentoStrategySelector;
import com.ecommerce.billingworker.strategy.SinDescuento;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingServiceTests {

    @Mock
    private DescuentoStrategySelector selector;
    @Mock
    private DescuentoStrategy strategy;

    @Test
    void delegatesDiscountCalculationToSelectedStrategy() {
        OrderTaskCommand command = command("200000");
        when(selector.seleccionar(command.monto())).thenReturn(strategy);
        when(strategy.calcularDescuento(command.monto())).thenReturn(new BigDecimal("20000.00"));

        PaymentResult result = new BillingService(selector).processOrder(command);

        verify(selector).seleccionar(command.monto());
        verify(strategy).calcularDescuento(command.monto());
        assertThat(result).isEqualTo(new PaymentResult(command.orderId(), Status.PAYMENT_SUCCESS,
                new BigDecimal("200000.00"), new BigDecimal("20000.00"), new BigDecimal("180000.00")));
    }

    @ParameterizedTest
    @CsvSource({
            "0,0.00,0.00,0.00,PAYMENT_SUCCESS",
            "99999.99,99999.99,0.00,99999.99,PAYMENT_SUCCESS",
            "100000,100000.00,10000.00,90000.00,PAYMENT_SUCCESS",
            "200000,200000.00,20000.00,180000.00,PAYMENT_SUCCESS",
            "1000000,1000000.00,100000.00,900000.00,PAYMENT_SUCCESS",
            "1111111.10,1111111.10,111111.11,999999.99,PAYMENT_SUCCESS",
            "1111111.11,1111111.11,111111.11,1000000.00,PAYMENT_SUCCESS",
            "1111111.12,1111111.12,111111.11,1000000.01,PAYMENT_FAILED",
            "2000000,2000000.00,200000.00,1800000.00,PAYMENT_FAILED",
            "99999.999,100000.00,0.00,100000.00,PAYMENT_SUCCESS",
            "100000.05,100000.05,10000.01,90000.04,PAYMENT_SUCCESS",
            "100000.055,100000.06,10000.01,90000.05,PAYMENT_SUCCESS"
    })
    void simulatesPaymentWithExactMonetaryResults(String amount, String original, String discount,
                                                 String total, Status status) {
        BillingService service = new BillingService(new DescuentoStrategySelector(
                new SinDescuento(), new DescuentoPorMonto()));
        OrderTaskCommand command = command(amount);

        PaymentResult result = service.processOrder(command);

        assertThat(result).isEqualTo(new PaymentResult(command.orderId(), status,
                new BigDecimal(original), new BigDecimal(discount), new BigDecimal(total)));
    }

    private OrderTaskCommand command(String amount) {
        return new OrderTaskCommand(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                "PROCESS_ORDER", "Alejandra", "alejandra@example.com", "Teclado", 2,
                new BigDecimal(amount));
    }
}
