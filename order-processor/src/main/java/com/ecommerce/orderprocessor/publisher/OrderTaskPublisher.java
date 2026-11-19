package com.ecommerce.orderprocessor.publisher;

import com.ecommerce.orderprocessor.command.ProcessOrderCommand;

public interface OrderTaskPublisher {
    void publish(ProcessOrderCommand command);
}
