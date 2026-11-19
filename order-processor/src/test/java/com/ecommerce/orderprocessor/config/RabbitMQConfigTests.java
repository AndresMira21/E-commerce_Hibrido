package com.ecommerce.orderprocessor.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class RabbitMQConfigTests {

    @Test
    void declaresDurableFanoutWithThreeQueuesAndBindings() {
        Declarables topology = new RabbitMQConfig().orderTasksTopology();

        assertThat(topology.getDeclarablesByType(FanoutExchange.class)).singleElement()
                .satisfies(exchange -> {
                    assertThat(exchange.getName()).isEqualTo("order_tasks");
                    assertThat(exchange.isDurable()).isTrue();
                });
        assertThat(topology.getDeclarablesByType(Queue.class)).extracting(Queue::getName)
                .containsExactlyInAnyOrder("billing_queue", "inventory_queue", "notification_queue");
        assertThat(topology.getDeclarablesByType(Queue.class)).allSatisfy(queue ->
                assertThat(queue.isDurable()).isTrue());
        assertThat(topology.getDeclarablesByType(Binding.class)).hasSize(3)
                .allSatisfy(binding -> {
                    assertThat(binding.getExchange()).isEqualTo("order_tasks");
                    assertThat(binding.getRoutingKey()).isEmpty();
                }).extracting(Binding::getDestination)
                .containsExactlyInAnyOrder("billing_queue", "inventory_queue", "notification_queue");
    }
}
