package com.ecommerce.inventoryworker.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryItemTests {

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 6, Integer.MAX_VALUE})
    void invalidReservationLeavesQuantityUnchanged(int quantity) {
        InventoryItem item = new InventoryItem("Teclado", 5);
        assertThatThrownBy(() -> item.reserve(quantity)).isInstanceOf(IllegalArgumentException.class);
        assertThat(item.getAvailableQuantity()).isEqualTo(5);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 100})
    void createsItemWithoutAssigningDatabaseManagedFields(int quantity) {
        InventoryItem item = new InventoryItem("Teclado", quantity);
        assertThat(item.getProductName()).isEqualTo("Teclado");
        assertThat(item.getAvailableQuantity()).isEqualTo(quantity);
        assertThat(item.getId()).isNull();
        assertThat(item.getVersion()).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingProduct(String product) {
        assertThatThrownBy(() -> new InventoryItem(product, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, -100})
    void rejectsInvalidQuantity(Integer quantity) {
        assertThatThrownBy(() -> new InventoryItem("Teclado", quantity))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
