package com.ecommerce.hibrido.analytics.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderReceivedEventTest {
    @Test
    void aceptaEventoValido() {
        // Arrange
        var id = UUID.randomUUID();

        // Act / Assert
        assertDoesNotThrow(() -> validEvent(id));
    }

    @Test
    void rechazaCamposObligatoriosInvalidos() {
        // Arrange
        var id = UUID.randomUUID();

        // Act / Assert
        assertThrows(IllegalArgumentException.class, () -> new OrderReceivedEvent(null, "Cliente", "c@example.com", "Libro", 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderReceivedEvent(id, " ", "c@example.com", "Libro", 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderReceivedEvent(id, "Cliente", " ", "Libro", 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderReceivedEvent(id, "Cliente", "correo-invalido", "Libro", 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderReceivedEvent(id, "Cliente", "c@example.com", " ", 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderReceivedEvent(id, "Cliente", "c@example.com", "Libro", 0, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderReceivedEvent(id, "Cliente", "c@example.com", "Libro", 1, null));
        assertThrows(IllegalArgumentException.class, () -> new OrderReceivedEvent(id, "Cliente", "c@example.com", "Libro", 1, new BigDecimal("-1")));
    }

    private OrderReceivedEvent validEvent(UUID id) {
        return new OrderReceivedEvent(id, "Cliente", "c@example.com", "Libro", 1, BigDecimal.ONE);
    }
}
