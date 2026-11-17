package com.ecommerce.orderapi.publisher;

import com.ecommerce.orderapi.event.NewOrderEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class KafkaOrderPublisherTests {

    @Mock private KafkaTemplate<String, String> template;
    @Mock private SendResult<String, String> confirmation;
    private final ObjectMapper mapper = new ObjectMapper();
    private final NewOrderEvent event = new NewOrderEvent("a1b2c3d4-1234-5678-9012-abcdefabcdef",
            "Alejandra", "alejandra@example.com", "Libro", 2, new BigDecimal("150000.75"));
    private KafkaOrderPublisher publisher;

    @BeforeEach
    void setUp() { publisher = new KafkaOrderPublisher(mapper, template); }

    @Test
    void sendsExactJsonContractToNewOrdersWithOrderIdKey() throws Exception {
        when(template.send(eq("new_orders"), eq(event.orderId()), anyString()))
                .thenReturn(new CompletableFuture<>());
        publisher.publish(event);
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(template).send(eq("new_orders"), eq(event.orderId()), json.capture());
        assertThat(mapper.readTree(json.getValue())).isEqualTo(mapper.readTree("""
                {"order_id":"a1b2c3d4-1234-5678-9012-abcdefabcdef","cliente":"Alejandra",
                 "correo":"alejandra@example.com","item":"Libro","cantidad":2,"monto":150000.75}
                """));
        verifyNoMoreInteractions(template);
    }

    @Test
    void completesOnlyAfterKafkaConfirmation() {
        CompletableFuture<SendResult<String, String>> pending = new CompletableFuture<>();
        when(template.send(eq("new_orders"), eq(event.orderId()), anyString())).thenReturn(pending);
        var result = publisher.publish(event);
        assertThat(result).isSameAs(pending);
        assertThat(result.isDone()).isFalse();
        pending.complete(confirmation);
        assertThat(result.join()).isSameAs(confirmation);
    }

    @Test
    void exposesAsynchronousKafkaFailure() {
        CompletableFuture<SendResult<String, String>> pending = new CompletableFuture<>();
        when(template.send(eq("new_orders"), eq(event.orderId()), anyString())).thenReturn(pending);
        var result = publisher.publish(event);
        var failure = new IllegalStateException("Kafka rechazo el envio");
        pending.completeExceptionally(failure);
        assertThatThrownBy(result::join).hasCause(failure);
    }

    @Test
    void exposesImmediateSendFailureThroughFuture() {
        var failure = new IllegalStateException("Kafka no disponible");
        when(template.send(eq("new_orders"), eq(event.orderId()), anyString())).thenThrow(failure);
        assertThatThrownBy(() -> publisher.publish(event).join()).hasCause(failure);
    }

    @Test
    void serializationFailureDoesNotSendMessage() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        var failure = new JsonProcessingException("No se pudo serializar") { };
        when(failingMapper.writeValueAsString(event)).thenThrow(failure);
        var failingPublisher = new KafkaOrderPublisher(failingMapper, template);
        assertThatThrownBy(() -> failingPublisher.publish(event).join()).hasCause(failure);
        verifyNoInteractions(template);
    }
}
