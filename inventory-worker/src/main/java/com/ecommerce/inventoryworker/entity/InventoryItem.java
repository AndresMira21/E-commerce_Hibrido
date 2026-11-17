package com.ecommerce.inventoryworker.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "inventory_items")
@Check(constraints = "available_quantity >= 0")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_name", nullable = false, unique = true)
    private String productName;

    @Column(name = "available_quantity", nullable = false)
    private Integer availableQuantity;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected InventoryItem() {
        // Requerido por JPA.
    }

    public InventoryItem(String productName, Integer availableQuantity) {
        this.productName = productName;
        this.availableQuantity = availableQuantity;
        validate();
    }

    @PrePersist
    @PreUpdate
    private void validate() {
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("El nombre del producto es obligatorio");
        }
        if (availableQuantity == null || availableQuantity < 0) {
            throw new IllegalArgumentException("La cantidad disponible es obligatoria y no puede ser negativa");
        }
    }

    public void reserve(int quantity) {
        if (quantity <= 0 || quantity > availableQuantity) {
            throw new IllegalArgumentException("La reserva debe ser positiva y no superar el stock disponible");
        }
        availableQuantity -= quantity;
    }

    public Long getId() { return id; }
    public String getProductName() { return productName; }
    public Integer getAvailableQuantity() { return availableQuantity; }
    public Long getVersion() { return version; }
}
