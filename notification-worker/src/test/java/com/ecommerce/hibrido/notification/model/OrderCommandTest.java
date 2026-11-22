package com.ecommerce.hibrido.notification.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderCommandTest {
    @Test
    void aceptaComandoValido() {
        assertDoesNotThrow(() -> validCommand(UUID.randomUUID()));
    }

    @Test
    void rechazaCamposObligatoriosInvalidos() {
        var id = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> new OrderCommand(null, "PROCESS_ORDER", "Libro", 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderCommand(id, " ", "Libro", 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderCommand(id, "UNKNOWN_ACTION", "Libro", 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderCommand(id, "PROCESS_ORDER", " ", 1, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderCommand(id, "PROCESS_ORDER", "Libro", null, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderCommand(id, "PROCESS_ORDER", "Libro", 0, BigDecimal.ONE));
        assertThrows(IllegalArgumentException.class, () -> new OrderCommand(id, "PROCESS_ORDER", "Libro", 1, null));
        assertThrows(IllegalArgumentException.class, () -> new OrderCommand(id, "PROCESS_ORDER", "Libro", 1, new BigDecimal("-1")));
    }

    private OrderCommand validCommand(UUID id) {
        return new OrderCommand(id, "PROCESS_ORDER", "Libro", 2, new BigDecimal("30000"));
    }
}
