package com.ecommerce.hibrido.notification.service;
import com.ecommerce.hibrido.notification.model.NotificationEvent;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class KafkaNotificationEventPublisherTest {
    @Test void confirmaEmailSentEnTopicYClaveCorrectos() {
        // Arrange
        KafkaTemplate<String,NotificationEvent> template=mock(KafkaTemplate.class);
        var event=new NotificationEvent(UUID.randomUUID(),"EMAIL_SENT");
        when(template.send("notification_events",event.order_id().toString(),event))
                .thenReturn(CompletableFuture.completedFuture(null));
        // Act
        new KafkaNotificationEventPublisher(template,"notification_events").publish(event);
        // Assert
        verify(template).send("notification_events",event.order_id().toString(),event);
    }
    @Test void fallaSinConvertirErrorKafkaEnNotificacionExitosa() {
        KafkaTemplate<String,NotificationEvent> template=mock(KafkaTemplate.class);
        var event=new NotificationEvent(UUID.randomUUID(),"EMAIL_SENT");
        when(template.send("notification_events",event.order_id().toString(),event))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka")));
        assertThatThrownBy(() -> new KafkaNotificationEventPublisher(template,"notification_events").publish(event))
                .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(ExecutionException.class);
    }
    @Test void servicioPropagaFalloYNoDevuelveEventoComoExito() {
        var publisher=mock(NotificationEventPublisher.class);
        var command=new com.ecommerce.hibrido.notification.model.OrderCommand(UUID.randomUUID(),"PROCESS_ORDER","Libro",1,java.math.BigDecimal.TEN);
        var failure=new IllegalStateException("Kafka");
        doThrow(failure).when(publisher).publish(any());
        assertThatThrownBy(() -> new NotificationService(publisher).notifyOrderReceived(command)).isSameAs(failure);
        verify(publisher).publish(new NotificationEvent(command.order_id(),"EMAIL_SENT"));
    }
}
