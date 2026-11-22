package com.ecommerce.orderprocessor.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class KafkaFailureConfigTests {
    @Test void transientFailureInvokesRecoveryOnlyAfterThreeAttempts() {
        var recoverer=mock(org.springframework.kafka.listener.ConsumerRecordRecoverer.class);
        var handler=new KafkaFailureConfig().orderProcessorErrorHandler(recoverer);
        handler.setSeekAfterError(false);
        var record=new ConsumerRecord<>("new_orders",0,7,"id","{}");
        var consumer=mock(org.apache.kafka.clients.consumer.Consumer.class);
        var container=mock(MessageListenerContainer.class);
        var failure=new IllegalStateException("RabbitMQ");
        handler.handleOne(failure,record,consumer,container);
        handler.handleOne(failure,record,consumer,container);
        verifyNoInteractions(recoverer);
        handler.handleOne(failure,record,consumer,container);
        verify(recoverer).accept(record,failure);
    }

    @Test void invalidEventGoesDirectlyToRecoveryWithoutBrokerRetries() {
        var recoverer=mock(org.springframework.kafka.listener.ConsumerRecordRecoverer.class);
        var handler=new KafkaFailureConfig().orderProcessorErrorHandler(recoverer);
        handler.setSeekAfterError(false);
        var record=new ConsumerRecord<>("new_orders",0,8,"id","{}");
        var failure=new IllegalArgumentException("invalid");
        handler.handleOne(failure,record,mock(org.apache.kafka.clients.consumer.Consumer.class),mock(MessageListenerContainer.class));
        verify(recoverer).accept(record,failure);
    }
    @Test void exhaustedDeliveryStopsContainerAndThrowsInsteadOfAcknowledging() {
        var registry=mock(KafkaListenerEndpointRegistry.class);
        var container=mock(MessageListenerContainer.class);
        when(registry.getListenerContainer("orderProcessor")).thenReturn(container);
        var recoverer=new KafkaFailureConfig().orderProcessorRecoverer(registry);
        assertThatThrownBy(() -> recoverer.accept(new ConsumerRecord<>("new_orders",0,5,"id","{}"),
                new IllegalStateException("RabbitMQ"))).isInstanceOf(org.springframework.kafka.KafkaException.class);
        verify(container).stop(any(Runnable.class));
        assertThat(new KafkaFailureConfig().orderProcessorErrorHandler(recoverer).isAckAfterHandle()).isFalse();
    }
}
