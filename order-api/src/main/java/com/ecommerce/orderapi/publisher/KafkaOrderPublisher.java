package com.ecommerce.orderapi.publisher;

import com.ecommerce.orderapi.event.NewOrderEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
public class KafkaOrderPublisher implements OrderEventPublisher {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaOrderPublisher(ObjectMapper objectMapper, KafkaTemplate<String, String> kafkaTemplate) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
    }

    public CompletableFuture<SendResult<String, String>> publish(NewOrderEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            // Se devuelve la confirmacion real de Kafka; no se anticipa el exito.
            return kafkaTemplate.send("new_orders", event.orderId(), json);
        } catch (JsonProcessingException | RuntimeException exception) {
            // Mantiene un unico contrato asincrono incluso si el fallo ocurre antes del envio.
            return CompletableFuture.failedFuture(exception);
        }
    }
}
