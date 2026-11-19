package com.ecommerce.inventoryworker.repository;

import com.ecommerce.inventoryworker.entity.InventoryItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    Optional<InventoryItem> findByProductName(String productName);
}
