package com.ecommerce.hibrido.notification.config;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationRetryConfigTests {
    public interface Invocation { void invoke(Object channel, Message message); }
    private final NotificationRetryConfig config=new NotificationRetryConfig();
    private final Message message=new Message(new byte[0]);
    @ParameterizedTest @ValueSource(strings={"Kafka","PostgreSQL","OptimisticLock"})
    void transientErrorsStopAfterThreeAttempts(String failure) {
        MessageListener target=mock(MessageListener.class);
        doThrow(new IllegalStateException(failure)).when(target).onMessage(message);
        List<Long> pauses=new ArrayList<>();
        assertThatThrownBy(() -> advised(target,pauses).onMessage(message))
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(target,times(3)).onMessage(message);
        assertThat(pauses).containsExactly(200L,400L);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void permanentErrorIsNotRetriedEvenWhenWrapped(boolean wrapped) {
        MessageListener target=mock(MessageListener.class);
        RuntimeException failure=new IllegalArgumentException("invalid");
        doThrow(wrapped ? new RuntimeException(failure) : failure).when(target).onMessage(message);
        List<Long> pauses=new ArrayList<>();
        assertThatThrownBy(() -> advised(target,pauses).onMessage(message))
                .hasCauseInstanceOf(AmqpRejectAndDontRequeueException.class);
        verify(target).onMessage(message);
        assertThat(pauses).isEmpty();
    }
    @Test void temporaryFailureCanSucceedWithoutRejecting() {
        MessageListener target=mock(MessageListener.class);
        doThrow(new IllegalStateException("Kafka")).doNothing().when(target).onMessage(message);
        List<Long> pauses=new ArrayList<>();
        advised(target,pauses).onMessage(message);
        verify(target,times(2)).onMessage(message);
        assertThat(pauses).containsExactly(200L);
    }
    @Test void declaresOnlyCompatibleDurableDlq() {
        var topology=config.notificationDeadLetterTopology();
        assertThat(topology.getDeclarables()).hasSize(3);
        assertThat(topology.getDeclarablesByType(DirectExchange.class)).singleElement().satisfies(e -> {
            assertThat(e.getName()).isEqualTo("notification_dlx"); assertThat(e.isDurable()).isTrue();
        });
        assertThat(topology.getDeclarablesByType(Queue.class)).singleElement().satisfies(q -> {
            assertThat(q.getName()).isEqualTo("notification_dead_queue"); assertThat(q.isDurable()).isTrue();
            assertThat(q.getArguments()).isEmpty();
        });
        assertThat(topology.getDeclarablesByType(Binding.class)).singleElement().satisfies(b -> {
            assertThat(b.getExchange()).isEqualTo("notification_dlx");
            assertThat(b.getDestination()).isEqualTo("notification_dead_queue");
            assertThat(b.getRoutingKey()).isEqualTo("notification.failed");
        });
    }
    @Test void containerDisablesUnlimitedRequeue() {
        var configurer=mock(SimpleRabbitListenerContainerFactoryConfigurer.class);
        var connection=mock(ConnectionFactory.class);
        var advice=config.notificationRetryInterceptor(config.notificationRetryTemplate());
        var factory=config.notificationRabbitListenerContainerFactory(configurer,connection,advice);
        verify(configurer).configure(factory,connection);
        var container=factory.createListenerContainer();
        assertThat(org.springframework.test.util.ReflectionTestUtils.getField(container,"defaultRequeueRejected")).isEqualTo(false);
        assertThat((Object[])org.springframework.test.util.ReflectionTestUtils.getField(container,"adviceChain")).containsExactly(advice);
    }
    private MessageListener advised(MessageListener target,List<Long> pauses) {
        var template=config.notificationRetryTemplate();
        var backOff=new ExponentialBackOffPolicy();
        backOff.setInitialInterval(200); backOff.setMultiplier(2); backOff.setMaxInterval(400);
        backOff.setSleeper(pauses::add); template.setBackOffPolicy(backOff);
        ProxyFactory proxy=new ProxyFactory();
        proxy.setInterfaces(Invocation.class);
        proxy.setTarget((Invocation)(channel,msg) -> target.onMessage(msg));
        proxy.addAdvice(config.notificationRetryInterceptor(template));
        Invocation invocation=(Invocation)proxy.getProxy();
        return msg -> invocation.invoke(null,msg);
    }
}
