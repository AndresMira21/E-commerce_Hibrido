package com.ecommerce.billingworker.publisher;

import com.ecommerce.billingworker.event.PaymentEvent;

public interface PaymentEventPublisher {
    void publish(PaymentEvent event);
}
