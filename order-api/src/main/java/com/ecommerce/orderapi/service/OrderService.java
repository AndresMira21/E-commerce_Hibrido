package com.ecommerce.orderapi.service;

import com.ecommerce.orderapi.dto.CreateOrderRequest;
import com.ecommerce.orderapi.dto.CreateOrderResponse;
import com.ecommerce.orderapi.event.NewOrderEvent;
import com.ecommerce.orderapi.publisher.OrderEventPublisher;
import com.ecommerce.orderapi.domain.*;
import java.util.concurrent.CompletableFuture;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
    private final OrderEventPublisher publisher;
    private final AsyncTaskExecutor executor;
    private final OrdenPersistence persistence;

    public OrderService(OrderEventPublisher publisher,
            @Qualifier("applicationTaskExecutor") AsyncTaskExecutor executor, OrdenPersistence persistence) {
        this.publisher = publisher;
        this.executor = executor;
        this.persistence = persistence;
    }

    public CompletableFuture<CreateOrderResponse> createOrder(CreateOrderRequest request) {
        Orden orden = Orden.builder().identificador(IdentificadorOrden.nuevo())
                .cliente(request.cliente()).correo(request.correo())
                .detalle(new DetalleItem(request.item(), request.cantidad()))
                .dinero(new Dinero(request.monto())).build();
        String id = orden.identificador().toString();
        // La llamada al bean separado retorna solo despues del commit.
        return CompletableFuture.supplyAsync(() -> persistence.guardarNueva(orden), executor)
                .thenCompose(saved -> {
                    NewOrderEvent event = new NewOrderEvent(id, saved.getCliente(), saved.getCorreo(),
                            saved.detalle().producto(), saved.detalle().cantidad(), saved.dinero().valor());
                    CompletableFuture<?> envio;
                    try { envio = publisher.publish(event); }
                    catch (RuntimeException failure) { envio = CompletableFuture.failedFuture(failure); }
                    return envio.handleAsync((confirmation, failure) -> {
                        // Otra transaccion audita el resultado, nunca compensa ni borra la orden.
                        persistence.registrarResultado(orden.identificador().valor(), failure == null);
                        if (failure != null) throw new OrderPublicationException(failure);
                        return new CreateOrderResponse(id, "ACCEPTED");
                    }, executor);
                }).handle((result, failure) -> {
                    if (failure != null) throw new OrderPublicationException(failure);
                    return result;
                });
    }
}
