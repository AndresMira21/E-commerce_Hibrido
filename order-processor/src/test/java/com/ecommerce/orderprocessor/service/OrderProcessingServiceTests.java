package com.ecommerce.orderprocessor.service;

import com.ecommerce.orderprocessor.command.ProcessOrderCommand;
import com.ecommerce.orderprocessor.event.NewOrderEvent;
import com.ecommerce.orderprocessor.publisher.RabbitTaskPublisher;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class OrderProcessingServiceTests {

    @Mock
    private RabbitTaskPublisher publisher;

    @Test
    void buildsAndPublishesCommandWithAllOrderData() {
        NewOrderEvent order = new NewOrderEvent("ORD-123", "Alejandra", "alejandra@example.com",
                "Teclado", 2, new BigDecimal("150000.75"));

        new OrderProcessingService(publisher).process(order);

        ArgumentCaptor<ProcessOrderCommand> captor = ArgumentCaptor.forClass(ProcessOrderCommand.class);
        verify(publisher).publish(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new ProcessOrderCommand("ORD-123", "Alejandra",
                "alejandra@example.com", "Teclado", 2, new BigDecimal("150000.75")));
        assertThat(captor.getValue().accion()).isEqualTo("PROCESS_ORDER");
        verifyNoMoreInteractions(publisher);
    }
}
