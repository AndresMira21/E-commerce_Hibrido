package com.ecommerce.inventoryworker.config;

import com.ecommerce.inventoryworker.entity.ReservationStatus;
import com.ecommerce.inventoryworker.entity.StockReservation;
import com.ecommerce.inventoryworker.event.InventoryEvent;
import com.ecommerce.inventoryworker.listener.InventoryQueueListener;
import com.ecommerce.inventoryworker.publisher.InventoryEventPublisher;
import com.ecommerce.inventoryworker.repository.InventoryItemRepository;
import com.ecommerce.inventoryworker.repository.StockReservationRepository;
import com.ecommerce.inventoryworker.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.support.RetryTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryRabbitConfigTests {

    public interface ContainerInvocation {
        void invoke(Object channel, Message message);
    }

    private final InventoryRabbitConfig config = new InventoryRabbitConfig();
    private final Message message = new Message("{}".getBytes(StandardCharsets.UTF_8));

    @Test
    void declaresOnlyDurableDeadLetterDestinationAndBinding() {
        Declarables topology = config.inventoryDeadLetterTopology();
        assertThat(topology.getDeclarables()).hasSize(3);
        assertThat(topology.getDeclarablesByType(DirectExchange.class)).singleElement().satisfies(exchange -> {
            assertThat(exchange.getName()).isEqualTo("inventory_dlx");
            assertThat(exchange.isDurable()).isTrue();
            assertThat(exchange.isAutoDelete()).isFalse();
        });
        assertThat(topology.getDeclarablesByType(Queue.class)).singleElement().satisfies(queue -> {
            assertThat(queue.getName()).isEqualTo("inventory_dead_queue");
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.getArguments()).isEmpty();
        });
        assertThat(topology.getDeclarablesByType(Binding.class)).singleElement().satisfies(binding -> {
            assertThat(binding.getExchange()).isEqualTo("inventory_dlx");
            assertThat(binding.getDestination()).isEqualTo("inventory_dead_queue");
            assertThat(binding.getRoutingKey()).isEqualTo("inventory.failed");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"database", "optimistic", "kafka"})
    void transientFailureHasThreeTotalAttemptsAndProgressivePauses(String type) {
        MessageListener target = mock(MessageListener.class);
        RuntimeException failure = switch (type) {
            case "database" -> new DataAccessResourceFailureException("postgres");
            case "optimistic" -> new OptimisticLockingFailureException("version");
            default -> new IllegalStateException("Kafka fallo");
        };
        doThrow(failure).when(target).onMessage(message);
        List<Long> pauses = new ArrayList<>();
        MessageListener listener = advised(target, pauses);

        assertThatThrownBy(() -> listener.onMessage(message))
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(target, times(3)).onMessage(message);
        assertThat(pauses).containsExactly(200L, 400L);
    }

    @Test
    void succeedsOnThirdAttemptWithoutRecovery() {
        MessageListener target = mock(MessageListener.class);
        doThrow(new IllegalStateException("Kafka")).doThrow(new IllegalStateException("Kafka"))
                .doNothing().when(target).onMessage(message);
        List<Long> pauses = new ArrayList<>();
        advised(target, pauses).onMessage(message);
        verify(target, times(3)).onMessage(message);
        assertThat(pauses).containsExactly(200L, 400L);
    }

    @Test
    void wrappedPermanentErrorHasOnlyOneAttempt() {
        MessageListener target = mock(MessageListener.class);
        doThrow(new RuntimeException("wrapper", new AmqpRejectAndDontRequeueException("invalido")))
                .when(target).onMessage(message);
        List<Long> pauses = new ArrayList<>();
        assertThatThrownBy(() -> advised(target, pauses).onMessage(message))
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(target).onMessage(message);
        assertThat(pauses).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{invalid", "{}", "null"})
    void actualInvalidJsonIsRejectedWithoutRetry(String json) {
        InventoryService service = mock(InventoryService.class);
        InventoryEventPublisher publisher = mock(InventoryEventPublisher.class);
        InventoryQueueListener target = new InventoryQueueListener(new ObjectMapper(), service, publisher);
        List<Long> pauses = new ArrayList<>();
        assertThatThrownBy(() -> advised(target::onMessage, pauses)
                .onMessage(new Message(json.getBytes(StandardCharsets.UTF_8))))
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(pauses).isEmpty();
        verifyNoInteractions(service, publisher);
    }

    @Test
    void kafkaRetryReusesStoredReservationWithoutStockChanges() {
        UUID id = UUID.fromString("a1b2c3d4-1234-5678-9012-abcdefabcdef");
        InventoryItemRepository items = mock(InventoryItemRepository.class);
        StockReservationRepository reservations = mock(StockReservationRepository.class);
        when(reservations.findByOrderId(id)).thenReturn(Optional.of(
                new StockReservation(id, "Teclado", 2, ReservationStatus.STOCK_RESERVED)));
        InventoryEventPublisher publisher = mock(InventoryEventPublisher.class);
        doThrow(new IllegalStateException("Kafka")).doNothing().when(publisher).publish(any());
        InventoryQueueListener target = new InventoryQueueListener(new ObjectMapper(),
                new InventoryService(items, reservations), publisher);
        String json = "{\"order_id\":\"" + id + "\",\"accion\":\"PROCESS_ORDER\","
                + "\"item\":\"Teclado\",\"cantidad\":2,\"monto\":10}";
        List<Long> pauses = new ArrayList<>();
        advised(target::onMessage, pauses).onMessage(new Message(json.getBytes(StandardCharsets.UTF_8)));
        verify(publisher, times(2)).publish(new InventoryEvent(id, "STOCK_RESERVED"));
        verify(reservations, times(2)).findByOrderId(id);
        verifyNoMoreInteractions(reservations);
        verifyNoInteractions(items);
        assertThat(pauses).containsExactly(200L);
    }

    @Test
    void factoryUsesConfiguredConnectionAndRetryAdviceWithoutRequeue() {
        var configurer = mock(SimpleRabbitListenerContainerFactoryConfigurer.class);
        var connectionFactory = mock(ConnectionFactory.class);
        var interceptor = config.inventoryRetryInterceptor(config.inventoryRetryTemplate());
        SimpleRabbitListenerContainerFactory factory = config.inventoryRabbitListenerContainerFactory(
                configurer, connectionFactory, interceptor);
        verify(configurer).configure(factory, connectionFactory);
        var container = factory.createListenerContainer();
        // Inspeccion local: no se inicia el contenedor ni se conecta al broker.
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(container, "defaultRequeueRejected"))
                .isEqualTo(false);
        assertThat((Object[]) org.springframework.test.util.ReflectionTestUtils.getField(container, "adviceChain"))
                .containsExactly(interceptor);
    }

    private MessageListener advised(MessageListener target, List<Long> pauses) {
        RetryTemplate template = config.inventoryRetryTemplate();
        // Mismos intervalos de produccion, pero el Sleeper registra sin esperar.
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(200);
        backOff.setMultiplier(2);
        backOff.setMaxInterval(400);
        backOff.setSleeper(pauses::add);
        template.setBackOffPolicy(backOff);
        ProxyFactory factory = new ProxyFactory();
        factory.setInterfaces(ContainerInvocation.class);
        factory.setTarget((ContainerInvocation) (channel, message) -> target.onMessage(message));
        factory.addAdvice(config.inventoryRetryInterceptor(template));
        ContainerInvocation invocation = (ContainerInvocation) factory.getProxy();
        return message -> invocation.invoke(null, message);
    }
}
