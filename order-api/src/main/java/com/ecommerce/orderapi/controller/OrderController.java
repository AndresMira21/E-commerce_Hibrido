package com.ecommerce.orderapi.controller;

import com.ecommerce.orderapi.dto.CreateOrderRequest;
import com.ecommerce.orderapi.dto.CreateOrderResponse;
import com.ecommerce.orderapi.service.OrderService;
import jakarta.validation.Valid;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/orders", "/order"})
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) { this.service = service; }

    @PostMapping
    public CompletableFuture<ResponseEntity<CreateOrderResponse>> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
        return service.createOrder(request).thenApply(result -> ResponseEntity.accepted().body(result));
    }
}
