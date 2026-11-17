package com.ecommerce.hibrido.notification.service;

import com.ecommerce.hibrido.notification.model.OrderCommand;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock NotificationEventPublisher publisher;
    @InjectMocks NotificationService service;

    @Test
    void procesaPedidoYPublicaEventoEmailSent() {
        // Arrange
        var orderId = UUID.randomUUID();
        var command = new OrderCommand(orderId, "PROCESS_ORDER", "Libro", 2, new BigDecimal("30000"));

        // Act
        var result = service.notifyOrderReceived(command);

        // Assert
        assertEquals(orderId, result.order_id());
        assertEquals("EMAIL_SENT", result.status());
        verify(publisher).publish(result);
    }
}
