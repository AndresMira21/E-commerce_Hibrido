package com.ecommerce.hibrido.notification.config;
import com.ecommerce.hibrido.notification.model.OrderCommand;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import static org.assertj.core.api.Assertions.*;

class NotificationConversionTest {
    @Test void aceptaContratoFanoutConHeaderJavaDeOtroServicio() {
        String json="""
                {"order_id":"a1b2c3d4-1234-5678-9012-abcdefabcdef","accion":"PROCESS_ORDER",
                 "cliente":"Alejandra","correo":"aleja@example.com","item":"Libro","cantidad":1,"monto":15000}
                """;
        var properties=new MessageProperties();
        properties.setContentType("application/json");
        properties.setHeader("__TypeId__","com.ecommerce.orderprocessor.command.ProcessOrderCommand");
        properties.setInferredArgumentType(OrderCommand.class);
        Object command=new RabbitConfiguration().jacksonMessageConverter()
                .fromMessage(new Message(json.getBytes(StandardCharsets.UTF_8),properties));
        assertThat(command).isInstanceOf(OrderCommand.class);
        assertThat(((OrderCommand)command).item()).isEqualTo("Libro");
    }
}
