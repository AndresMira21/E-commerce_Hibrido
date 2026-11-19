package com.ecommerce.hibrido.dashboard.consumer;

import com.ecommerce.hibrido.dashboard.model.StatusEvent;
import com.ecommerce.hibrido.dashboard.service.OrderStatusService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderStatusListenerTest {
    @Mock OrderStatusService service;
    @InjectMocks OrderStatusListener listener;

    @Test
    void distribuyeCadaTipoDeEventoAlCampoCorrespondiente() {
        // Arrange
        var event = new StatusEvent(UUID.randomUUID(), "STOCK_RESERVED");

        // Act
        listener.onInventoryEvent(event);

        // Assert
        verify(service).updateStock(event);
    }
}
