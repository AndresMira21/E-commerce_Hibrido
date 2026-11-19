package com.ecommerce.orderapi.domain;
import java.util.UUID;
public record IdentificadorOrden(UUID valor) {
    public IdentificadorOrden {
        if (valor == null) throw new IllegalArgumentException("El identificador es obligatorio");
    }
    public static IdentificadorOrden nuevo() { return new IdentificadorOrden(UUID.randomUUID()); }
    @Override public String toString() { return valor.toString(); }
}
