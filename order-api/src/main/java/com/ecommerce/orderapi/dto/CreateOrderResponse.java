package com.ecommerce.orderapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CreateOrderResponse(@JsonProperty("order_id") String orderId, String status) {}
