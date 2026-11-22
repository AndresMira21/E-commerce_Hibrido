package com.ecommerce.inventoryworker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import org.springframework.data.domain.Persistable;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "stock_reservations")
@Check(constraints = "quantity > 0")
public class StockReservation implements Persistable<UUID> {

    // Un UUID asignado no significa que la fila ya exista: save debe insertar, no fusionar.
    @Transient
    private boolean newReservation = true;

    @Override
    public UUID getId() { return orderId; }

    @Override
    public boolean isNew() { return newReservation; }

    @PostLoad
    @PostPersist
    private void markPersisted() { newReservation = false; }

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "product_name", nullable = false, updatable = false)
    private String productName;

    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StockReservation() {
    }

    public StockReservation(UUID orderId, String productName, Integer quantity, ReservationStatus status) {
        this.orderId = orderId;
        this.productName = productName;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = Instant.now();
        validate();
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (orderId == null) {
            throw new IllegalArgumentException("El identificador de la orden es obligatorio");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("La cantidad de la reserva debe ser mayor que cero");
        }
        if (status == null) {
            throw new IllegalArgumentException("El estado de la reserva es obligatorio");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("La fecha de la reserva es obligatoria");
        }
    }

    public UUID getOrderId() { return orderId; }
    public String getProductName() { return productName; }
    public Integer getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
