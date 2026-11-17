package com.ecommerce.orderapi.service;

public class OrderPublicationException extends RuntimeException {
    public OrderPublicationException(Throwable cause) {
        super("No fue posible confirmar la publicacion de la orden", cause);
    }
}
