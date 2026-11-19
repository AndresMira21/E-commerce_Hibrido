package com.ecommerce.inventoryworker.service;

import com.ecommerce.inventoryworker.entity.InventoryItem;
import com.ecommerce.inventoryworker.entity.ReservationStatus;
import com.ecommerce.inventoryworker.entity.StockReservation;
import com.ecommerce.inventoryworker.repository.InventoryItemRepository;
import com.ecommerce.inventoryworker.repository.StockReservationRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryItemRepository items;
    private final StockReservationRepository reservations;

    public InventoryService(InventoryItemRepository items, StockReservationRepository reservations) {
        this.items = items;
        this.reservations = reservations;
    }

    @Transactional
    public InventoryResult reserveStock(UUID orderId, String productName, int quantity) {
        if (orderId == null || productName == null || productName.isBlank() || quantity <= 0) {
            throw new IllegalArgumentException("La orden requiere identificador, producto y cantidad positiva");
        }
        Optional<StockReservation> previous = reservations.findByOrderId(orderId);
        if (previous.isPresent()) {
            StockReservation reservation = previous.get();
            if (!reservation.getProductName().equals(productName) || reservation.getQuantity() != quantity) {
                throw new IllegalArgumentException("La orden ya fue registrada con otro producto o cantidad");
            }
            return result(reservation);
        }

        Optional<InventoryItem> item = items.findByProductName(productName);
        ReservationStatus status = ReservationStatus.OUT_OF_STOCK;
        if (item.isPresent() && item.get().getAvailableQuantity() >= quantity) {
            InventoryItem inventoryItem = item.get();
            inventoryItem.reserve(quantity);
            // Entidad administrada por JPA: save conserva el control de @Version.
            items.save(inventoryItem);
            status = ReservationStatus.STOCK_RESERVED;
        }
        StockReservation reservation = new StockReservation(orderId, productName, quantity, status);
        // Fuerza INSERT/flush dentro de esta transaccion; cualquier conflicto se propaga.
        reservations.saveAndFlush(reservation);
        return result(reservation);
    }

    private InventoryResult result(StockReservation reservation) {
        return new InventoryResult(reservation.getOrderId(), reservation.getStatus(),
                reservation.getProductName(), reservation.getQuantity());
    }
}
