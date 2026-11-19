package com.ecommerce.inventoryworker.publisher;

import com.ecommerce.inventoryworker.event.InventoryEvent;

public interface InventoryEventPublisher {
    void publish(InventoryEvent event);
}
