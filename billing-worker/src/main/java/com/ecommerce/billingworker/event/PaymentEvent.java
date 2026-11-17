package com.ecommerce.billingworker.event;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record PaymentEvent(@JsonProperty("order_id") UUID orderId, String status) {
}
