package com.ecommerce.billingworker.listener;

import com.ecommerce.billingworker.command.OrderTaskCommand;
import com.ecommerce.billingworker.service.BillingService;
import com.ecommerce.billingworker.event.PaymentEvent;
import com.ecommerce.billingworker.payment.PaymentResult;
import com.ecommerce.billingworker.payment.PaymentResult.Status;
import com.ecommerce.billingworker.publisher.PaymentEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class BillingQueueListenerTests {

    private static final String VALID_JSON = """
            {"order_id":"550e8400-e29b-41d4-a716-446655440000",
             "accion":"PROCESS_ORDER", "cliente":"Alejandra", "correo":"alejandra@example.com",
             "item":"Teclado", "cantidad":2, "monto":150000.75}
            """;

    @Mock
    private BillingService service;
    @Mock
    private PaymentEventPublisher publisher;

    private BillingQueueListener listener;

    @BeforeEach
    void setUp() {
        listener = new BillingQueueListener(new ObjectMapper(), service, publisher);
        lenient().when(service.processOrder(any())).thenAnswer(invocation -> {
            OrderTaskCommand command = invocation.getArgument(0);
            return new PaymentResult(command.orderId(), Status.PAYMENT_SUCCESS,
                    command.monto(), BigDecimal.ZERO, command.monto());
        });
    }

    @Test
    void convertsJsonAndDeliversCommandIgnoringForeignJavaTypeHeader() throws Exception {
        listener.onMessage(message(VALID_JSON));

        verify(service).processOrder(new OrderTaskCommand(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), "PROCESS_ORDER",
                "Alejandra", "alejandra@example.com", "Teclado", 2, new BigDecimal("150000.75")));
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "null"})
    void rejectsInvalidQuantity(String quantity) {
        assertRejected(VALID_JSON.replace("\"cantidad\":2", "\"cantidad\":" + quantity));
    }

    @Test
    void rejectsIncorrectAction() {
        assertRejected(VALID_JSON.replace("PROCESS_ORDER", "CANCEL_ORDER"));
    }

    @Test
    void rejectsMalformedJson() {
        assertRejected("{invalid-json");
    }

    @Test
    void rejectsMissingIdentifier() {
        assertRejected(VALID_JSON.replace("\"550e8400-e29b-41d4-a716-446655440000\"", "null"));
    }

    @Test
    void rejectsInvalidUuid() {
        assertRejected(VALID_JSON.replace("550e8400-e29b-41d4-a716-446655440000", "ORD-123"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"   \"", "null"})
    void rejectsEmptyProduct(String product) {
        assertRejected(VALID_JSON.replace("\"Teclado\"", product));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "null"})
    void rejectsInvalidAmount(String amount) {
        assertRejected(VALID_JSON.replace("150000.75", amount));
    }

    @Test
    void acceptsZeroAmount() throws Exception {
        listener.onMessage(message(VALID_JSON.replace("150000.75", "0")));

        verify(service).processOrder(new OrderTaskCommand(
                UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), "PROCESS_ORDER",
                "Alejandra", "alejandra@example.com", "Teclado", 2, BigDecimal.ZERO));
    }

    private void assertRejected(String json) {
        assertThatThrownBy(() -> listener.onMessage(message(json)))
                .isInstanceOf(JsonProcessingException.class);
        verifyNoInteractions(service);
        verifyNoInteractions(publisher);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PAYMENT_SUCCESS", "PAYMENT_FAILED"})
    void publishesReturnedPaymentStatus(String status) throws Exception {
        UUID id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        doReturn(new PaymentResult(id, Status.valueOf(status),
                BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.TEN)).when(service).processOrder(any());

        listener.onMessage(message(VALID_JSON));

        verify(publisher).publish(new PaymentEvent(id, status));
    }

    @Test
    void propagatesPublicationFailureToRabbitListener() {
        IllegalStateException failure = new IllegalStateException("Kafka no disponible");
        doThrow(failure).when(publisher).publish(any());

        assertThatThrownBy(() -> listener.onMessage(message(VALID_JSON))).isSameAs(failure);
    }

    private Message message(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setHeader("__TypeId__", "com.ecommerce.orderprocessor.command.ProcessOrderCommand");
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }
}
