package com.ecommerce.orderapi.domain;
public record DetalleItem(String producto, int cantidad) {
    public DetalleItem {
        if (producto == null || producto.isBlank() || producto.length() > 255)
            throw new IllegalArgumentException("El producto es obligatorio y debe tener hasta 255 caracteres");
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser positiva");
    }
}
