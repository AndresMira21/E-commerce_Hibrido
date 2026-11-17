package com.ecommerce.hibrido.dashboard.service;

import com.ecommerce.hibrido.dashboard.model.StatusEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderStatusServiceTest {
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks OrderStatusService service;

    @Test
    void agregaEventosEnCualquierOrdenYPublicaCadaActualizacion() {
        // Arrange
        var orderId = UUID.randomUUID();

        // Act
        var stock = service.updateStock(new StatusEvent(orderId, "STOCK_RESERVED"));
        var email = service.updateEmail(new StatusEvent(orderId, "EMAIL_SENT"));
        var payment = service.updatePayment(new StatusEvent(orderId, "PAYMENT_SUCCESS"));

        // Assert
        assertEquals("PENDING", stock.payment());
        assertEquals("PENDING", stock.email());
        assertEquals("STOCK_RESERVED", email.stock());
        assertEquals("PAYMENT_SUCCESS", payment.payment());
        assertEquals("STOCK_RESERVED", payment.stock());
        assertEquals("EMAIL_SENT", payment.email());
        verify(messagingTemplate, org.mockito.Mockito.times(3)).convertAndSend(eq("/topic/orders"), org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test
    void repiteEventoSinDuplicarOrdenNiPerderActualizacion() {
        // Arrange
        var event = new StatusEvent(UUID.randomUUID(), "PAYMENT_FAILED");

        // Act
        service.updatePayment(event);
        var repeated = service.updatePayment(event);
        var all = service.allStatuses();

        // Assert
        assertEquals(1, all.size());
        assertEquals("PAYMENT_FAILED", repeated.payment());
    }
}
