package com.ecommerce.inventoryworker.service;

import com.ecommerce.inventoryworker.entity.ReservationStatus;
import java.util.UUID;

public record InventoryResult(UUID orderId, ReservationStatus status, String productName, Integer quantity) {
}
