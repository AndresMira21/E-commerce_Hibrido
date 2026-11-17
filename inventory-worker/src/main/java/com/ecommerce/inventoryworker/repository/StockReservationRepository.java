package com.ecommerce.inventoryworker.repository;

import com.ecommerce.inventoryworker.entity.StockReservation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockReservationRepository extends JpaRepository<StockReservation, UUID> {
    Optional<StockReservation> findByOrderId(UUID orderId);
}
