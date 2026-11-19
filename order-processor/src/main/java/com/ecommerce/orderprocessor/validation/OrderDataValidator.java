package com.ecommerce.orderprocessor.validation;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/** Validaciones compartidas por el evento de entrada y el comando de salida. */
public final class OrderDataValidator {
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private OrderDataValidator() { }

    public static void validate(String orderId, String cliente, String correo, String producto,
                                int cantidad, BigDecimal monto) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("El identificador de la orden es obligatorio");
        }
        if (cliente == null || cliente.isBlank()) {
            throw new IllegalArgumentException("El cliente es obligatorio");
        }
        if (producto == null || producto.isBlank()) {
            throw new IllegalArgumentException("El producto es obligatorio");
        }
        if (correo == null || !EMAIL.matcher(correo).matches()) {
            throw new IllegalArgumentException("El correo debe tener un formato valido y dominio con punto");
        }
        if (cantidad <= 0) {
            throw new IllegalArgumentException("La cantidad debe ser mayor que cero");
        }
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser obligatorio y mayor que cero");
        }
    }
}
