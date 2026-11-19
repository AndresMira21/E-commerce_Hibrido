package com.ecommerce.orderapi.publisher;
import com.ecommerce.orderapi.event.NewOrderEvent;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.support.SendResult;
public interface OrderEventPublisher {
    CompletableFuture<SendResult<String,String>> publish(NewOrderEvent event);
}
