package com.ecommerce.hibrido.analytics.consumer;

import com.ecommerce.hibrido.analytics.model.OrderReceivedEvent;
import com.ecommerce.hibrido.analytics.service.OrderAnalytics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderReceivedListenerTest {
    @Mock OrderAnalytics analytics;
    @InjectMocks OrderReceivedListener listener;

    @Test
    void delegaElEventoRecibido() {
        var event = new OrderReceivedEvent(UUID.randomUUID(), "Miranda", "m@example.com", "Libro", 1, BigDecimal.TEN);

        listener.onOrderReceived(event);

        verify(analytics).record(event);
    }
}
