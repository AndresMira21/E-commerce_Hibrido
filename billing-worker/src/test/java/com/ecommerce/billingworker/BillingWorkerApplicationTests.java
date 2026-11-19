package com.ecommerce.billingworker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import com.ecommerce.billingworker.event.PaymentEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        // Valida el arranque sin crear conexiones ni infraestructura de mensajeria.
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class BillingWorkerApplicationTests {
    @MockitoBean(name = "billingRabbitListenerContainerFactory")
    private org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory;
    @MockitoBean
    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsWithoutBrokers() {
        assertThat(context.getBean(BillingWorkerApplication.class)).isNotNull();
        assertThat(context.getEnvironment().getProperty("spring.application.name"))
                .isEqualTo("billing-worker");
    }
}
