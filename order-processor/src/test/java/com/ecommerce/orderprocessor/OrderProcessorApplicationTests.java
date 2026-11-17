package com.ecommerce.orderprocessor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        // Esta prueba valida el arranque sin crear infraestructura de mensajeria.
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class OrderProcessorApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        assertThat(context.getBean(OrderProcessorApplication.class)).isNotNull();
        assertThat(context.getEnvironment().getProperty("spring.application.name"))
                .isEqualTo("order-processor");
    }
}
