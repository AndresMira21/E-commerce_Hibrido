package com.ecommerce.orderapi.service;

import com.ecommerce.orderapi.dto.CreateOrderRequest;
import com.ecommerce.orderapi.dto.CreateOrderResponse;
import com.ecommerce.orderapi.event.NewOrderEvent;
import com.ecommerce.orderapi.publisher.KafkaOrderPublisher;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final KafkaOrderPublisher publisher;
    private final AsyncTaskExecutor executor;

    public OrderService(KafkaOrderPublisher publisher,
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor) {
        this.publisher = publisher;
        this.executor = executor;
    }

    public CompletableFuture<CreateOrderResponse> createOrder(CreateOrderRequest request) {
        String id = UUID.randomUUID().toString();
        NewOrderEvent event = new NewOrderEvent(id, request.cliente(), request.correo(), request.item(),
                request.cantidad(), request.monto());
        // send() puede esperar metadatos; se ejecuta fuera del hilo HTTP.
        return CompletableFuture.supplyAsync(() -> publisher.publish(event), executor)
                .thenCompose(future -> future)
                .handle((confirmation, failure) -> {
                    if (failure != null) {
                        throw new OrderPublicationException(failure);
                    }
                    return new CreateOrderResponse(id, "ACCEPTED");
                });
    }
}
