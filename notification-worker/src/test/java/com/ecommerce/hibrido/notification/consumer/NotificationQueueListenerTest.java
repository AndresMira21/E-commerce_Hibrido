package com.ecommerce.hibrido.notification.consumer;

import com.ecommerce.hibrido.notification.model.OrderCommand;
import com.ecommerce.hibrido.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationQueueListenerTest {
    @Mock NotificationService service;
    @InjectMocks NotificationQueueListener listener;

    @Test
    void delegaElComandoAlServicio() {
        // Arrange
        var command = new OrderCommand(UUID.randomUUID(), "PROCESS_ORDER", "Libro", 1, BigDecimal.TEN);

        // Act
        listener.onMessage(command);

        // Assert
        verify(service).notifyOrderReceived(command);
    }
}
