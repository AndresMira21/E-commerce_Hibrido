package com.ecommerce.hibrido.dashboard.service;

import com.ecommerce.hibrido.dashboard.model.OrderStatus;
import com.ecommerce.hibrido.dashboard.model.StatusEvent;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderStatusServiceTest {
    @Mock SimpMessagingTemplate messagingTemplate;
    @InjectMocks OrderStatusService service;

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"pse","pes","spe","sep","eps","esp"})
    void conservaCamposEnLasSeisPermutacionesConEstadosFallidos(String sequence) {
        // Arrange
        UUID id=UUID.randomUUID();
        OrderStatus result=OrderStatus.pending(id);
        // Act
        for(char field:sequence.toCharArray()) {
            result=switch(field) {
                case 'p' -> service.updatePayment(new StatusEvent(id,"PAYMENT_FAILED"));
                case 's' -> service.updateStock(new StatusEvent(id,"OUT_OF_STOCK"));
                default -> service.updateEmail(new StatusEvent(id,"EMAIL_SENT"));
            };
        }
        // Assert
        assertEquals(new OrderStatus(id,"PAYMENT_FAILED","OUT_OF_STOCK","EMAIL_SENT"),result);
        verify(messagingTemplate,org.mockito.Mockito.times(3)).convertAndSend(eq("/topic/orders"),org.mockito.ArgumentMatchers.any(Object.class));
    }

    @Test void mantieneAisladasDosOrdenes() {
        UUID first=UUID.randomUUID(), second=UUID.randomUUID();
        service.updatePayment(new StatusEvent(first,"PAYMENT_SUCCESS"));
        service.updateStock(new StatusEvent(second,"OUT_OF_STOCK"));
        assertEquals(2,service.allStatuses().size());
        assertEquals("PENDING",service.allStatuses().stream().filter(s -> s.order_id().equals(second)).findFirst().orElseThrow().payment());
    }

    @Test
    void agregaEventosEnCualquierOrdenYPublicaCadaActualizacion() {
        // Arrange
        var orderId = UUID.randomUUID();

        // Act
        var stock = service.updateStock(new StatusEvent(orderId, "STOCK_RESERVED"));
        var email = service.updateEmail(new StatusEvent(orderId, "EMAIL_SENT"));
        var payment = service.updatePayment(new StatusEvent(orderId, "PAYMENT_SUCCESS"));

        // Assert
        assertEquals("PENDING", stock.payment());
        assertEquals("PENDING", stock.email());
        assertEquals("STOCK_RESERVED", email.stock());
        assertEquals("PAYMENT_SUCCESS", payment.payment());
        assertEquals("STOCK_RESERVED", payment.stock());
        assertEquals("EMAIL_SENT", payment.email());
        var published = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate, org.mockito.Mockito.times(3))
                .convertAndSend(eq("/topic/orders"), published.capture());
        assertEquals(List.of(stock, email, payment), published.getAllValues().stream().map(OrderStatus.class::cast).toList());
    }

    @Test
    void repiteEventoSinDuplicarOrdenNiPerderActualizacion() {
        // Arrange
        var event = new StatusEvent(UUID.randomUUID(), "PAYMENT_FAILED");

        // Act
        service.updatePayment(event);
        var repeated = service.updatePayment(event);
        var all = service.allStatuses();

        // Assert
        assertEquals(1, all.size());
        assertEquals("PAYMENT_FAILED", repeated.payment());
    }
}
