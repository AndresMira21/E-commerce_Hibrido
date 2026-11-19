package com.ecommerce.orderprocessor.validation;

import com.ecommerce.orderprocessor.command.ProcessOrderCommand;
import com.ecommerce.orderprocessor.event.NewOrderEvent;
import com.ecommerce.orderprocessor.listener.OrderEventListener;
import com.ecommerce.orderprocessor.publisher.OrderTaskPublisher;
import com.ecommerce.orderprocessor.service.OrderProcessingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderContractValidationTests {
    @Mock private OrderTaskPublisher publisher;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String VALID_JSON = """
            {"order_id":"ORD-123","cliente":"Alejandra","correo":"aleja@example.com",
             "item":"Libro","cantidad":1,"monto":15000.50}
            """;

    static Stream<Arguments> invalidFields() {
        return Stream.of(
                Arguments.of("order_id", "null"), Arguments.of("order_id", "\"\""),
                Arguments.of("order_id", "\"  \""),
                Arguments.of("cliente", "null"), Arguments.of("cliente", "\"\""),
                Arguments.of("cliente", "\"  \""),
                Arguments.of("item", "null"), Arguments.of("item", "\"\""),
                Arguments.of("item", "\"  \""),
                Arguments.of("correo", "null"), Arguments.of("correo", "\"\""),
                Arguments.of("correo", "\"aleja@localhost\""),
                Arguments.of("correo", "\"correo-invalido\""),
                Arguments.of("correo", "\"aleja @example.com\""),
                Arguments.of("cantidad", "0"), Arguments.of("cantidad", "-1"),
                Arguments.of("monto", "null"), Arguments.of("monto", "0"),
                Arguments.of("monto", "-0.01"));
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void rejectsInvalidFieldsInBothCompactConstructors(String field, String value) throws Exception {
        // Arrange
        ObjectNode json = (ObjectNode) mapper.readTree(VALID_JSON);
        json.set(field, mapper.readTree(value));
        String id = json.path("order_id").isNull() ? null : json.path("order_id").asText();
        String cliente = json.path("cliente").isNull() ? null : json.path("cliente").asText();
        String correo = json.path("correo").isNull() ? null : json.path("correo").asText();
        String producto = json.path("item").isNull() ? null : json.path("item").asText();
        int cantidad = json.path("cantidad").asInt();
        BigDecimal monto = json.path("monto").isNull() ? null : json.path("monto").decimalValue();

        // Act / Assert: la validacion ocurre tambien al construir fuera de Jackson.
        assertThatThrownBy(() -> new NewOrderEvent(id, cliente, correo, producto, cantidad, monto))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProcessOrderCommand(id, cliente, correo, producto, cantidad, monto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("invalidFields")
    void invalidKafkaPayloadNeverReachesRabbitPublisher(String field, String value) throws Exception {
        // Arrange: listener y servicio reales, publicador abstraido mediante DIP.
        ObjectNode json = (ObjectNode) mapper.readTree(VALID_JSON);
        json.set(field, mapper.readTree(value));
        OrderEventListener listener = new OrderEventListener(mapper, new OrderProcessingService(publisher));

        // Act / Assert
        assertThatThrownBy(() -> listener.onMessage(json.toString()))
                .isInstanceOf(JsonProcessingException.class).hasRootCauseInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(publisher);
    }

    @Test
    void validStringIdentifierAndExactJsonContractRemainCompatible() throws Exception {
        // Arrange
        NewOrderEvent event = mapper.readValue(VALID_JSON, NewOrderEvent.class);
        OrderProcessingService service = new OrderProcessingService(publisher);

        // Act
        service.process(event);

        // Assert
        ProcessOrderCommand command = new ProcessOrderCommand("ORD-123", "Alejandra", "aleja@example.com",
                "Libro", 1, new BigDecimal("15000.50"));
        verify(publisher).publish(command);
        verifyNoMoreInteractions(publisher);
        assertThat(mapper.readTree(mapper.writeValueAsString(event))).isEqualTo(mapper.readTree(VALID_JSON));
        ObjectNode expected = (ObjectNode) mapper.readTree(VALID_JSON);
        expected.put("accion", "PROCESS_ORDER");
        assertThat(mapper.readTree(mapper.writeValueAsString(command))).isEqualTo(expected);
    }
}
