package com.ecommerce.inventoryworker.listener;

import com.ecommerce.inventoryworker.command.OrderTaskCommand;
import com.ecommerce.inventoryworker.entity.ReservationStatus;
import com.ecommerce.inventoryworker.service.InventoryResult;
import com.ecommerce.inventoryworker.service.InventoryService;
import com.ecommerce.inventoryworker.event.InventoryEvent;
import com.ecommerce.inventoryworker.publisher.InventoryEventPublisher;
import com.ecommerce.inventoryworker.entity.StockReservation;
import com.ecommerce.inventoryworker.repository.InventoryItemRepository;
import com.ecommerce.inventoryworker.repository.StockReservationRepository;
import java.util.Optional;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.OptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryQueueListenerTests {

    private static final UUID ID = UUID.fromString("a1b2c3d4-1234-5678-9012-abcdefabcdef");
    private static final String JSON = """
            {"order_id":"a1b2c3d4-1234-5678-9012-abcdefabcdef","accion":"PROCESS_ORDER",
             "cliente":"Alejandra","correo":"alejandra@example.com","item":"Teclado",
             "cantidad":2,"monto":150000.75}
            """;
    @Mock private InventoryService service;
    @Mock private InventoryEventPublisher publisher;
    private InventoryQueueListener listener;

    @BeforeEach
    void setUp() { listener = new InventoryQueueListener(new ObjectMapper(), service, publisher); }

    @Test
    void mapsAllAgreedJsonFields() throws Exception {
        OrderTaskCommand command = new ObjectMapper().readValue(JSON, OrderTaskCommand.class);
        assertThat(command).isEqualTo(new OrderTaskCommand(ID, "PROCESS_ORDER", "Alejandra",
                "alejandra@example.com", "Teclado", 2, new BigDecimal("150000.75")));
    }

    @ParameterizedTest
    @EnumSource(ReservationStatus.class)
    void deliversCorrectArgumentsAndHandlesBothResultsIgnoringForeignType(ReservationStatus status) {
        when(service.reserveStock(ID, "Teclado", 2)).thenReturn(new InventoryResult(ID, status, "Teclado", 2));
        listener.onMessage(message(JSON));
        verify(service).reserveStock(ID, "Teclado", 2);
        var order = inOrder(service, publisher);
        order.verify(service).reserveStock(ID, "Teclado", 2);
        order.verify(publisher).publish(new InventoryEvent(ID, status.name()));
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "0", "null"})
    void rejectsInvalidQuantities(String quantity) {
        assertInvalid(JSON.replace("\"cantidad\":2", "\"cantidad\":" + quantity));
    }

    @Test
    void rejectsIncorrectAction() { assertInvalid(JSON.replace("PROCESS_ORDER", "OTHER")); }

    @ParameterizedTest
    @ValueSource(strings = {"{bad-json", "null", "[]"})
    void rejectsInvalidJson(String json) { assertInvalid(json); }

    @Test
    void rejectsNullId() { assertInvalid(JSON.replace("\"" + ID + "\"", "null")); }

    @Test
    void rejectsInvalidUuid() { assertInvalid(JSON.replace(ID.toString(), "ORD-123")); }

    @ParameterizedTest
    @ValueSource(strings = {"\"\"", "\"  \"", "null"})
    void rejectsMissingProduct(String product) { assertInvalid(JSON.replace("\"Teclado\"", product)); }

    @ParameterizedTest
    @ValueSource(strings = {"-0.01", "null"})
    void rejectsInvalidAmount(String amount) { assertInvalid(JSON.replace("150000.75", amount)); }

    @Test
    void rejectsInconsistentDuplicateWithoutRequeue() {
        IllegalArgumentException failure = new IllegalArgumentException("Orden inconsistente");
        when(service.reserveStock(ID, "Teclado", 2)).thenThrow(failure);
        assertThatThrownBy(() -> listener.onMessage(message(JSON)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class).hasCause(failure);
    }

    @Test
    void propagatesDatabaseFailureUnchanged() {
        var failure = new DataAccessResourceFailureException("PostgreSQL no disponible");
        when(service.reserveStock(ID, "Teclado", 2)).thenThrow(failure);
        assertThatThrownBy(() -> listener.onMessage(message(JSON))).isSameAs(failure);
    }

    @Test
    void propagatesOptimisticConflictUnchanged() {
        var failure = new OptimisticLockingFailureException("Version concurrente");
        when(service.reserveStock(ID, "Teclado", 2)).thenThrow(failure);
        assertThatThrownBy(() -> listener.onMessage(message(JSON))).isSameAs(failure);
    }

    private void assertInvalid(String json) {
        assertThatThrownBy(() -> listener.onMessage(message(json)))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        verifyNoInteractions(service);
        verifyNoInteractions(publisher);
    }

    @Test
    void propagatesKafkaFailureAfterReservationWithoutCompensation() {
        when(service.reserveStock(ID, "Teclado", 2))
                .thenReturn(new InventoryResult(ID, ReservationStatus.STOCK_RESERVED, "Teclado", 2));
        var failure = new IllegalStateException("Kafka no disponible");
        doThrow(failure).when(publisher).publish(any());
        assertThatThrownBy(() -> listener.onMessage(message(JSON))).isSameAs(failure);
        verify(service).reserveStock(ID, "Teclado", 2);
        verifyNoMoreInteractions(service);
    }

    @ParameterizedTest
    @EnumSource(ReservationStatus.class)
    void republishesStoredOrderAfterFailedPublicationWithoutTouchingStock(ReservationStatus status) {
        InventoryItemRepository items = mock(InventoryItemRepository.class);
        StockReservationRepository reservations = mock(StockReservationRepository.class);
        when(reservations.findByOrderId(ID))
                .thenReturn(Optional.of(new StockReservation(ID, "Teclado", 2, status)));
        InventoryQueueListener duplicateListener = new InventoryQueueListener(new ObjectMapper(),
                new InventoryService(items, reservations), publisher);
        var failure = new IllegalStateException("Kafka no disponible");
        doThrow(failure).doNothing().when(publisher).publish(any());

        assertThatThrownBy(() -> duplicateListener.onMessage(message(JSON))).isSameAs(failure);
        duplicateListener.onMessage(message(JSON));

        verify(publisher, times(2)).publish(new InventoryEvent(ID, status.name()));
        verifyNoInteractions(items);
        verify(reservations, times(2)).findByOrderId(ID);
        verifyNoMoreInteractions(reservations);
    }

    private Message message(String json) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType("application/json");
        properties.setHeader("__TypeId__", "com.ecommerce.orderprocessor.command.ProcessOrderCommand");
        return new Message(json.getBytes(StandardCharsets.UTF_8), properties);
    }
}
