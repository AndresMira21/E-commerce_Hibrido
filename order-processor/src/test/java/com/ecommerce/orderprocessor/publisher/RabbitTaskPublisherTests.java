package com.ecommerce.orderprocessor.publisher;

import com.ecommerce.orderprocessor.command.ProcessOrderCommand;
import com.ecommerce.orderprocessor.config.RabbitMQConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class RabbitTaskPublisherTests {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Test
    void publishesToOrderTasksWithEmptyRoutingKey() {
        ProcessOrderCommand command = command();

        new RabbitTaskPublisher(rabbitTemplate).publish(command);

        verify(rabbitTemplate).convertAndSend("order_tasks", "", command);
        verifyNoMoreInteractions(rabbitTemplate);
    }

    @Test
    void configuredConverterSerializesAllAgreedJsonFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        Message message = new RabbitMQConfig().rabbitMessageConverter(mapper)
                .toMessage(command(), new MessageProperties());

        JsonNode json = mapper.readTree(message.getBody());
        assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(json.size()).isEqualTo(7);
        assertThat(json.path("order_id").asText()).isEqualTo("ORD-123");
        assertThat(json.path("accion").asText()).isEqualTo("PROCESS_ORDER");
        assertThat(json.path("cliente").asText()).isEqualTo("Alejandra");
        assertThat(json.path("correo").asText()).isEqualTo("alejandra@example.com");
        assertThat(json.path("item").asText()).isEqualTo("Teclado");
        assertThat(json.path("cantidad").asInt()).isEqualTo(2);
        assertThat(json.path("monto").isNumber()).isTrue();
        assertThat(json.path("monto").decimalValue()).isEqualByComparingTo("150000.75");
    }

    private ProcessOrderCommand command() {
        return new ProcessOrderCommand("ORD-123", "Alejandra", "alejandra@example.com",
                "Teclado", 2, new BigDecimal("150000.75"));
    }
}
