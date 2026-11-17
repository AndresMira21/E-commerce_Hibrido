package com.ecommerce.inventoryworker.service;

import com.ecommerce.inventoryworker.entity.InventoryItem;
import com.ecommerce.inventoryworker.entity.ReservationStatus;
import com.ecommerce.inventoryworker.entity.StockReservation;
import com.ecommerce.inventoryworker.repository.InventoryItemRepository;
import com.ecommerce.inventoryworker.repository.StockReservationRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTests {
    @Mock private InventoryItemRepository items;
    @Mock private StockReservationRepository reservations;
    private InventoryService service;
    private final UUID id = UUID.fromString("a1b2c3d4-1234-5678-9012-abcdefabcdef");

    @BeforeEach
    void setUp() { service = new InventoryService(items, reservations); }

    @Test
    void reservesAvailableStockAndInsertsResult() {
        InventoryItem item = new InventoryItem("Teclado", 5);
        when(items.findByProductName("Teclado")).thenReturn(Optional.of(item));
        InventoryResult result = service.reserveStock(id, "Teclado", 2);
        assertThat(item.getAvailableQuantity()).isEqualTo(3);
        assertThat(result).isEqualTo(new InventoryResult(id, ReservationStatus.STOCK_RESERVED, "Teclado", 2));
        verify(items).save(item);
        ArgumentCaptor<StockReservation> captor = ArgumentCaptor.forClass(StockReservation.class);
        verify(reservations).saveAndFlush(captor.capture());
        assertThat(captor.getValue().isNew()).isTrue();
        assertThat(captor.getValue().getId()).isEqualTo(id);
        assertThat(captor.getValue().getStatus()).isEqualTo(ReservationStatus.STOCK_RESERVED);
    }

    @Test
    void insufficientStockDoesNotChangeInventory() {
        InventoryItem item = new InventoryItem("Teclado", 1);
        when(items.findByProductName("Teclado")).thenReturn(Optional.of(item));
        assertThat(service.reserveStock(id, "Teclado", 2).status()).isEqualTo(ReservationStatus.OUT_OF_STOCK);
        assertThat(item.getAvailableQuantity()).isEqualTo(1);
        verify(items, never()).save(any());
        verify(reservations).saveAndFlush(any());
    }

    @Test
    void missingProductRegistersOutOfStock() {
        assertThat(service.reserveStock(id, "Teclado", 1).status()).isEqualTo(ReservationStatus.OUT_OF_STOCK);
        verify(items, never()).save(any());
        verify(reservations).saveAndFlush(any());
    }

    @ParameterizedTest
    @EnumSource(ReservationStatus.class)
    void duplicateReturnsStoredResultWithoutTouchingInventory(ReservationStatus status) {
        when(reservations.findByOrderId(id)).thenReturn(Optional.of(new StockReservation(id, "Teclado", 2, status)));
        assertThat(service.reserveStock(id, "Teclado", 2)).isEqualTo(new InventoryResult(id, status, "Teclado", 2));
        verifyNoInteractions(items);
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void duplicateWithDifferentProductIsRejected() {
        when(reservations.findByOrderId(id)).thenReturn(Optional.of(new StockReservation(id, "Mouse", 2, ReservationStatus.STOCK_RESERVED)));
        assertThatThrownBy(() -> service.reserveStock(id, "Teclado", 2)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(items);
        verify(reservations, never()).saveAndFlush(any());
    }

    @Test
    void duplicateWithDifferentQuantityIsRejected() {
        when(reservations.findByOrderId(id)).thenReturn(Optional.of(new StockReservation(id, "Teclado", 1, ReservationStatus.STOCK_RESERVED)));
        assertThatThrownBy(() -> service.reserveStock(id, "Teclado", 2)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(items);
    }

    @Test
    void distinctOrdersCanExhaustStockButNeverMakeItNegative() {
        InventoryItem item = new InventoryItem("Teclado", 4);
        when(items.findByProductName("Teclado")).thenReturn(Optional.of(item));
        assertThat(service.reserveStock(id, "Teclado", 2).status()).isEqualTo(ReservationStatus.STOCK_RESERVED);
        assertThat(service.reserveStock(UUID.randomUUID(), "Teclado", 2).status()).isEqualTo(ReservationStatus.STOCK_RESERVED);
        assertThat(service.reserveStock(UUID.randomUUID(), "Teclado", 1).status()).isEqualTo(ReservationStatus.OUT_OF_STOCK);
        assertThat(item.getAvailableQuantity()).isZero();
        verify(items, times(2)).save(item);
        verify(reservations, times(3)).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, Integer.MIN_VALUE})
    void rejectsInvalidQuantitiesBeforeRepositories(int quantity) {
        assertThatThrownBy(() -> service.reserveStock(id, "Teclado", quantity)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(items, reservations);
    }

    @Test
    void rejectsMissingIdAndProduct() {
        assertThatThrownBy(() -> service.reserveStock(null, "Teclado", 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reserveStock(id, null, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.reserveStock(id, "  ", 1)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(items, reservations);
    }

    @Test
    void transactionCommitsSuccessfulOperation() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(transaction);
        transactionalProxy(manager).reserveStock(id, "Teclado", 1);
        verify(manager).commit(transaction);
        verify(manager, never()).rollback(any());
    }

    @Test
    void optimisticConflictPropagatesAndRollsBackTransaction() {
        assertTransactionalFailure(new OptimisticLockingFailureException("version concurrente"));
    }

    @Test
    void concurrentDuplicateInsertPropagatesAndRollsBackTransaction() {
        assertTransactionalFailure(new DataIntegrityViolationException("order_id duplicado"));
    }

    private void assertTransactionalFailure(RuntimeException failure) {
        InventoryItem item = new InventoryItem("Teclado", 4);
        when(items.findByProductName("Teclado")).thenReturn(Optional.of(item));
        when(reservations.saveAndFlush(any())).thenThrow(failure);
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        when(manager.getTransaction(any())).thenReturn(transaction);
        assertThatThrownBy(() -> transactionalProxy(manager).reserveStock(id, "Teclado", 1)).isSameAs(failure);
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }

    private InventoryService transactionalProxy(PlatformTransactionManager manager) {
        ProxyFactory factory = new ProxyFactory(service);
        factory.addAdvice(new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource()));
        return (InventoryService) factory.getProxy();
    }
}
