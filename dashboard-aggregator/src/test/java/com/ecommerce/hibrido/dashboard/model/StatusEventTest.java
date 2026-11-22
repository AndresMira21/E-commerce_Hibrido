package com.ecommerce.hibrido.dashboard.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StatusEventTest {
    @Test
    void aceptaEventoValidoYRechazaIdentificadorOEstadoAusente() {
        var id = UUID.randomUUID();

        assertDoesNotThrow(() -> new StatusEvent(id, "OUT_OF_STOCK"));
        assertThrows(IllegalArgumentException.class, () -> new StatusEvent(null, "OUT_OF_STOCK"));
        assertThrows(IllegalArgumentException.class, () -> new StatusEvent(id, " "));
        assertThrows(IllegalArgumentException.class, () -> new StatusEvent(id, "UNKNOWN_STATUS"));
    }
}
