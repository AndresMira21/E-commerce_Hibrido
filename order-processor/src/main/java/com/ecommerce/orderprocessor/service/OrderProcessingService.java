package com.ecommerce.orderprocessor.service;

import com.ecommerce.orderprocessor.event.NewOrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderProcessingService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingService.class);

    public void process(NewOrderEvent order) {
        log.info("Orden recibida: id={}, producto={}, cantidad={}, monto={}",
                order.orderId(), order.producto(), order.cantidad(), order.monto());
    }
}
