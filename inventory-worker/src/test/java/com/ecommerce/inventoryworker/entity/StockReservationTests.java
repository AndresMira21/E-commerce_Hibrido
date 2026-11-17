package com.ecommerce.inventoryworker.entity;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockReservationTests {

    private final UUID orderId = UUID.fromString("a1b2c3d4-1234-5678-9012-abcdefabcdef");

    @ParameterizedTest
    @EnumSource(ReservationStatus.class)
    void createsReservationWithOrderDataAndTimestamp(ReservationStatus status) {
        Instant before = Instant.now();
        StockReservation reservation = new StockReservation(orderId, "Teclado", 1, status);
        Instant after = Instant.now();

        assertThat(reservation.getOrderId()).isEqualTo(orderId);
        assertThat(reservation.getProductName()).isEqualTo("Teclado");
        assertThat(reservation.getQuantity()).isEqualTo(1);
        assertThat(reservation.getStatus()).isEqualTo(status);
        assertThat(reservation.getCreatedAt()).isBetween(before, after);
    }

    @Test
    void rejectsNullOrderId() {
        assertThatThrownBy(() -> new StockReservation(null, "Teclado", 1, ReservationStatus.STOCK_RESERVED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingProduct(String product) {
        assertThatThrownBy(() -> new StockReservation(orderId, product, 1, ReservationStatus.STOCK_RESERVED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 0})
    void rejectsInvalidQuantity(Integer quantity) {
        assertThatThrownBy(() -> new StockReservation(orderId, "Teclado", quantity, ReservationStatus.STOCK_RESERVED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullStatus() {
        assertThatThrownBy(() -> new StockReservation(orderId, "Teclado", 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
