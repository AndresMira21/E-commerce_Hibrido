package com.ecommerce.orderapi.controller;

import com.ecommerce.orderapi.service.OrderPublicationException;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class OrderExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> validation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new TreeMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("error", "Solicitud invalida", "fields", fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<?> malformedJson() {
        return ResponseEntity.badRequest().body(Map.of("error", "El cuerpo JSON no es valido"));
    }

    @ExceptionHandler(OrderPublicationException.class)
    public ResponseEntity<?> publicationFailed() {
        return ResponseEntity.status(503).body(Map.of("error", "No se pudo confirmar la orden. Intenta mas tarde"));
    }
}
