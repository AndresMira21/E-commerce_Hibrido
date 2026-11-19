package com.ecommerce.inventoryworker.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record InventoryEvent(@JsonProperty("order_id") UUID orderId, String status) {
}
