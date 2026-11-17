package com.ecommerce.inventoryworker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import com.ecommerce.inventoryworker.repository.InventoryItemRepository;
import com.ecommerce.inventoryworker.repository.StockReservationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.ecommerce.inventoryworker.event.InventoryEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        // Valida el arranque sin brokers, DataSource ni infraestructura JPA.
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration"
})
class InventoryWorkerApplicationTests {

    @MockitoBean(name = "inventoryRabbitListenerContainerFactory")
    private SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory;

    @MockitoBean
    private KafkaTemplate<String, InventoryEvent> kafkaTemplate;

    @MockitoBean
    private InventoryItemRepository items;

    @MockitoBean
    private StockReservationRepository reservations;

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoadsWithoutExternalServices() {
        assertThat(context.getBean(InventoryWorkerApplication.class)).isNotNull();
        assertThat(context.getEnvironment().getProperty("spring.application.name"))
                .isEqualTo("inventory-worker");
    }
}
