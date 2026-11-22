package com.ecommerce.hibrido.analytics.service;

import com.ecommerce.hibrido.analytics.model.OrderReceivedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderAnalyticsTest {
    @Test void conservaPrecisionDecimalYRedondeaSoloElPromedio() {
        var analytics=new OrderAnalytics();
        var id=UUID.randomUUID();
        analytics.record(event(id,"0.10"));
        analytics.record(event(UUID.randomUUID(),"0.21"));
        analytics.record(event(id,"99999"));
        var snapshot=analytics.snapshot();
        assertEquals(2,snapshot.total_orders());
        assertEquals(new BigDecimal("0.31"),snapshot.total_amount());
        assertEquals(new BigDecimal("0.16"),snapshot.average_amount());
    }
    @Test
    void acumulaTotalPromedioYDescartaDuplicados() {
        var analytics = new OrderAnalytics();
        var first = event(UUID.randomUUID(), "30000");
        var second = event(UUID.randomUUID(), "10000");
        var repeatedIdWithDifferentAmount = event(first.order_id(), "99999");

        var firstInserted = analytics.record(first);
        var duplicateInserted = analytics.record(repeatedIdWithDifferentAmount);
        analytics.record(second);
        var result = analytics.snapshot();

        assertTrue(firstInserted);
        assertFalse(duplicateInserted);
        assertEquals(2, result.total_orders());
        assertEquals(new BigDecimal("40000"), result.total_amount());
        assertEquals(new BigDecimal("20000.00"), result.average_amount());
    }

    @Test
    void devuelveMetricasEnCeroSinOrdenes() {
        var analytics = new OrderAnalytics();

        var result = analytics.snapshot();

        assertEquals(0, result.total_orders());
        assertEquals(BigDecimal.ZERO, result.total_amount());
        assertEquals(BigDecimal.ZERO, result.average_amount());
    }

    private OrderReceivedEvent event(UUID id, String amount) {
        return new OrderReceivedEvent(id, "Miranda", "m@example.com", "Libro", 1, new BigDecimal(amount));
    }
}
