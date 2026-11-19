package com.ecommerce.orderprocessor.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.KafkaException;
import org.springframework.util.backoff.FixedBackOff;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
public class KafkaFailureConfig {
    private static final Logger log = LoggerFactory.getLogger(KafkaFailureConfig.class);

    @Bean
    public ConsumerRecordRecoverer orderProcessorRecoverer(KafkaListenerEndpointRegistry registry) {
        return (record, failure) -> {
            log.error("new_orders fallo topic={} partition={} offset={} tipo={}; detener y revisar antes de reiniciar",
                    record.topic(), record.partition(), record.offset(), failure.getClass().getSimpleName());
            var container = registry.getListenerContainer("orderProcessor");
            if (container != null) container.stop(() -> { });
            // Nunca convertir en exito ni avanzar el offset del registro fallido.
            throw new KafkaException("Order Processor detenido: entrega RabbitMQ no confirmada", failure);
        };
    }

    @Bean
    public DefaultErrorHandler orderProcessorErrorHandler(ConsumerRecordRecoverer orderProcessorRecoverer) {
        DefaultErrorHandler handler = new DefaultErrorHandler(orderProcessorRecoverer, new FixedBackOff(500, 2));
        handler.addNotRetryableExceptions(JsonProcessingException.class, IllegalArgumentException.class);
        handler.setAckAfterHandle(false);
        handler.setCommitRecovered(false);
        handler.setRetryListeners((record, exception, attempt) -> log.warn(
                "new_orders intento={} partition={} offset={} tipo={}", attempt,
                record.partition(), record.offset(), exception.getClass().getSimpleName()));
        return handler;
    }
}
