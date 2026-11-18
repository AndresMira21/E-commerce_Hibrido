package com.ecommerce.hibrido.notification.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationEventTest {
    @Test
    void aceptaEventoValidoYRechazaIdentificadorOEstadoAusente() {
        // Arrange
        var id = UUID.randomUUID();

        // Act / Assert
        assertDoesNotThrow(() -> new NotificationEvent(id, "EMAIL_SENT"));
        assertThrows(IllegalArgumentException.class, () -> new NotificationEvent(null, "EMAIL_SENT"));
        assertThrows(IllegalArgumentException.class, () -> new NotificationEvent(id, " "));
        assertThrows(IllegalArgumentException.class, () -> new NotificationEvent(id, "SENT"));
    }
}
