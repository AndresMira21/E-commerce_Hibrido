package com.ecommerce.orderprocessor.service;

import com.ecommerce.orderprocessor.event.NewOrderEvent;
import com.ecommerce.orderprocessor.command.ProcessOrderCommand;
import com.ecommerce.orderprocessor.publisher.RabbitTaskPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderProcessingService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingService.class);
    private final RabbitTaskPublisher publisher;

    public OrderProcessingService(RabbitTaskPublisher publisher) {
        this.publisher = publisher;
    }

    public void process(NewOrderEvent order) {
        log.info("Orden recibida: id={}, producto={}, cantidad={}, monto={}",
                order.orderId(), order.producto(), order.cantidad(), order.monto());
        publisher.publish(new ProcessOrderCommand(order.orderId(), order.cliente(), order.correo(),
                order.producto(), order.cantidad(), order.monto()));
    }
}
