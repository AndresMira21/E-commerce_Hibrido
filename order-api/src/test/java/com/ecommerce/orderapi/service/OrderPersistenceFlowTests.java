package com.ecommerce.orderapi.service;

import com.ecommerce.orderapi.domain.*;
import com.ecommerce.orderapi.dto.CreateOrderRequest;
import com.ecommerce.orderapi.event.NewOrderEvent;
import com.ecommerce.orderapi.publisher.OrderEventPublisher;
import com.ecommerce.orderapi.repository.OrdenRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.kafka.support.SendResult;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class OrderPersistenceFlowTests {
    @Mock OrdenRepository repository;
    @Mock OrderEventPublisher publisher;
    private OrderService service;
    private Orden stored;
    private final CreateOrderRequest request=new CreateOrderRequest("Alejandra","alejandra@example.com","Libro",1,new BigDecimal("15000"));
    @BeforeEach void setUp() {
        lenient().when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
            stored=invocation.getArgument(0); return stored;
        });
        lenient().when(repository.findById(any())).thenAnswer(invocation -> Optional.of(stored));
        service=new OrderService(publisher,new TaskExecutorAdapter(new SyncTaskExecutor()),new OrdenPersistence(repository));
    }
    @Test void guardaAntesDePublicarYAuditaConfirmacion() {
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        var result=service.createOrder(request).join();
        var sequence=inOrder(repository,publisher);
        sequence.verify(repository).saveAndFlush(any());
        sequence.verify(publisher).publish(new NewOrderEvent(result.orderId(),"Alejandra","alejandra@example.com","Libro",1,new BigDecimal("15000")));
        sequence.verify(repository).findById(stored.identificador().valor());
        sequence.verify(repository).saveAndFlush(stored);
        assertThat(stored.getEstado()).isEqualTo(EstadoOrden.PUBLICADA);
    }
    @Test void errorBaseDeDatosNoPublica() {
        when(repository.saveAndFlush(any())).thenThrow(new DataAccessResourceFailureException("DB"));
        assertThatThrownBy(() -> service.createOrder(request).join()).hasCauseInstanceOf(OrderPublicationException.class);
        verifyNoInteractions(publisher);
    }
    @Test void kafkaFallaYConservaOrdenYAuditoriaSinReintentar() {
        when(publisher.publish(any())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Kafka")));
        assertThatThrownBy(() -> service.createOrder(request).join()).hasCauseInstanceOf(OrderPublicationException.class);
        assertThat(stored.getEstado()).isEqualTo(EstadoOrden.PUBLICACION_INCIERTA);
        assertThat(stored.getCambios()).hasSize(2);
        verify(publisher).publish(any());
        verify(repository,never()).delete(any());
    }
    @Test void pendienteNoAceptaAntesDeConfirmar() {
        CompletableFuture<SendResult<String,String>> kafka=new CompletableFuture<>();
        when(publisher.publish(any())).thenReturn(kafka);
        var result=service.createOrder(request);
        assertThat(result.isDone()).isFalse();
        assertThat(stored.getEstado()).isEqualTo(EstadoOrden.PENDIENTE_PUBLICACION);
        kafka.complete(null);
        assertThat(result.join().status()).isEqualTo("ACCEPTED");
    }
    @Test void falloAuditoriaNoInventaExitoTrasConfirmacionKafka() {
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        doThrow(new DataAccessResourceFailureException("DB")).when(repository).findById(any());
        assertThatThrownBy(() -> service.createOrder(request).join()).hasCauseInstanceOf(OrderPublicationException.class);
        assertThat(stored.getEstado()).isEqualTo(EstadoOrden.PENDIENTE_PUBLICACION);
    }
    @Test void commitSeConfirmaAntesDeInvocarBroker() {
        // Usa interceptor transaccional real con gestor simulado, sin PostgreSQL.
        PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
        TransactionStatus transaction=mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(transaction);
        ProxyFactory proxy=new ProxyFactory(new OrdenPersistence(repository));
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        var transactionalService=new OrderService(publisher,new TaskExecutorAdapter(new SyncTaskExecutor()),
                (OrdenPersistence) proxy.getProxy());
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        transactionalService.createOrder(request).join();
        var sequence=inOrder(repository,manager,publisher);
        sequence.verify(repository).saveAndFlush(any());
        sequence.verify(manager).commit(transaction);
        sequence.verify(publisher).publish(any());
    }
    @Test void falloDeCommitNoInvocaKafkaAunqueSaveAndFlushTermine() {
        PlatformTransactionManager manager=mock(PlatformTransactionManager.class);
        TransactionStatus transaction=mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(transaction);
        doThrow(new DataAccessResourceFailureException("Commit no confirmado")).when(manager).commit(transaction);
        ProxyFactory factory=new ProxyFactory(new OrdenPersistence(repository));
        factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        OrderService transactionalService=new OrderService(publisher,
                new TaskExecutorAdapter(new SyncTaskExecutor()),(OrdenPersistence)factory.getProxy());

        assertThatThrownBy(() -> transactionalService.createOrder(request).join())
                .hasCauseInstanceOf(OrderPublicationException.class);
        verify(repository).saveAndFlush(any());
        verifyNoInteractions(publisher);
    }
}
