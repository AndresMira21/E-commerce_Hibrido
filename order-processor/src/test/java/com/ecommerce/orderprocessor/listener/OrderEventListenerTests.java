package com.ecommerce.orderprocessor.listener;

import com.ecommerce.orderprocessor.event.NewOrderEvent;
import com.ecommerce.orderprocessor.service.OrderProcessingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class OrderEventListenerTests {

    @Mock
    private OrderProcessingService service;

    private OrderEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new OrderEventListener(new ObjectMapper(), service);
    }

    @Test
    void parsesJsonAndDeliversOrderToService() throws JsonProcessingException {
        String message = """
                {
                  "order_id": "ORD-123",
                  "cliente": "Alejandra",
                  "correo": "alejandra@example.com",
                  "item": "Teclado",
                  "cantidad": 2,
                  "monto": 150000.75
                }
                """;

        listener.onMessage(message);

        verify(service).process(new NewOrderEvent("ORD-123", "Alejandra",
                "alejandra@example.com", "Teclado", 2, new BigDecimal("150000.75")));
        verifyNoMoreInteractions(service);
    }

    @Test
    void rejectsMalformedJsonWithoutCallingService() {
        assertThatThrownBy(() -> listener.onMessage("{invalid-json"))
                .isInstanceOf(JsonProcessingException.class);
        verifyNoInteractions(service);
    }

    @Test
    void rejectsNonNumericAmountWithoutCallingService() {
        assertThatThrownBy(() -> listener.onMessage("{\"monto\":\"invalid\"}"))
                .isInstanceOf(JsonProcessingException.class);
        verifyNoInteractions(service);
    }
}
