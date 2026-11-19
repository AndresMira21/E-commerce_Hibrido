package com.ecommerce.orderprocessor.listener;

import com.ecommerce.orderprocessor.event.NewOrderEvent;
import com.ecommerce.orderprocessor.service.OrderProcessingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final ObjectMapper objectMapper;
    private final OrderProcessingService orderProcessingService;

    public OrderEventListener(ObjectMapper objectMapper, OrderProcessingService orderProcessingService) {
        this.objectMapper = objectMapper;
        this.orderProcessingService = orderProcessingService;
    }

    @KafkaListener(id = "orderProcessor", idIsGroup = false, topics = "new_orders")
    public void onMessage(String message) throws JsonProcessingException {
        NewOrderEvent order = objectMapper.readValue(message, NewOrderEvent.class);
        orderProcessingService.process(order);
    }
}
