package com.ecommerce.inventoryworker.publisher;

import com.ecommerce.inventoryworker.event.InventoryEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.support.serializer.JsonSerializer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaInventoryEventPublisherTests {

    @Mock
    private KafkaTemplate<String, InventoryEvent> template;
    @Mock
    private CompletableFuture<SendResult<String, InventoryEvent>> future;

    private final UUID id = UUID.fromString("a1b2c3d4-1234-5678-9012-abcdefabcdef");

    @ParameterizedTest
    @ValueSource(strings = {"STOCK_RESERVED", "OUT_OF_STOCK"})
    void sendsStatusToCorrectTopicAndKeyAndWaitsForConfirmation(String status) throws Exception {
        InventoryEvent event = event(status);
        when(template.send("inventory_events", id.toString(), event)).thenReturn(future);

        new KafkaInventoryEventPublisher(template, "inventory_events").publish(event);

        verify(template).send("inventory_events", id.toString(), event);
        verify(future).get(10, TimeUnit.SECONDS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"STOCK_RESERVED", "OUT_OF_STOCK"})
    void serializesExactlyDashboardFieldsWithoutJavaTypeHeaders(String status) throws Exception {
        java.util.Properties properties = new java.util.Properties();
        try (var input = getClass().getResourceAsStream("/application.properties")) {
            properties.load(input);
        }
        String addHeaders = properties.getProperty("spring.kafka.producer.properties[spring.json.add.type.headers]");
        assertThat(addHeaders).isEqualTo("false");
        assertThat(properties.getProperty("spring.kafka.producer.value-serializer"))
                .isEqualTo(JsonSerializer.class.getName());
        try (JsonSerializer<InventoryEvent> serializer = new JsonSerializer<>()) {
            serializer.configure(Map.of(JsonSerializer.ADD_TYPE_INFO_HEADERS, addHeaders), false);
            RecordHeaders headers = new RecordHeaders();
            byte[] json = serializer.serialize("inventory_events", headers, event(status));
            ObjectMapper mapper = new ObjectMapper();
            assertThat(mapper.readTree(json)).isEqualTo(mapper.readTree(
                    "{\"order_id\":\"" + id + "\",\"status\":\"" + status + "\"}"));
            assertThat(headers.toArray()).isEmpty();
        }
    }

    @Test
    void publishesToConfiguredTopic() throws Exception {
        InventoryEvent event = event("STOCK_RESERVED");
        when(template.send("custom_inventory", id.toString(), event)).thenReturn(future);
        new KafkaInventoryEventPublisher(template, "custom_inventory").publish(event);
        verify(template).send("custom_inventory", id.toString(), event);
        verify(future).get(10, TimeUnit.SECONDS);
    }

    @Test
    void propagatesBrokerFailure() throws Exception {
        InventoryEvent event = event("STOCK_RESERVED");
        when(template.send("inventory_events", id.toString(), event)).thenReturn(future);
        ExecutionException failure = new ExecutionException(new IllegalStateException("broker caido"));
        when(future.get(10, TimeUnit.SECONDS)).thenThrow(failure);

        assertThatThrownBy(() -> new KafkaInventoryEventPublisher(template, "inventory_events").publish(event))
                .isInstanceOf(IllegalStateException.class).hasCause(failure);
    }

    @Test
    void propagatesTimeoutWithoutWaitingIndefinitely() throws Exception {
        InventoryEvent event = event("STOCK_RESERVED");
        when(template.send("inventory_events", id.toString(), event)).thenReturn(future);
        when(future.get(10, TimeUnit.SECONDS)).thenThrow(new TimeoutException("sin confirmacion"));

        assertThatThrownBy(() -> new KafkaInventoryEventPublisher(template, "inventory_events").publish(event))
                .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(TimeoutException.class);
    }

    @Test
    void preservesInterruptFlagAndPropagatesFailure() throws Exception {
        InventoryEvent event = event("STOCK_RESERVED");
        when(template.send("inventory_events", id.toString(), event)).thenReturn(future);
        when(future.get(10, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        try {
            assertThatThrownBy(() -> new KafkaInventoryEventPublisher(template, "inventory_events").publish(event))
                    .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(InterruptedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void propagatesImmediateSendFailure() {
        InventoryEvent event = event("STOCK_RESERVED");
        IllegalStateException failure = new IllegalStateException("send fallo");
        when(template.send("inventory_events", id.toString(), event)).thenThrow(failure);
        assertThatThrownBy(() -> new KafkaInventoryEventPublisher(template, "inventory_events").publish(event)).isSameAs(failure);
    }

    private InventoryEvent event(String status) {
        return new InventoryEvent(id, status);
    }
}
