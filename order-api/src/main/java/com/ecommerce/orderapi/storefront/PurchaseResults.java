package com.ecommerce.orderapi.storefront;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("storefront")
@ConditionalOnProperty(name = "storefront.dispatch.enabled", havingValue = "true")
class PurchaseResults {
    private static final Logger log = LoggerFactory.getLogger(PurchaseResults.class);
    private static final Map<String, Worker> WORKERS = Map.of(
            "billing_events", new Worker("payment", Set.of("PAYMENT_SUCCESS", "PAYMENT_FAILED")),
            "inventory_events", new Worker("stock", Set.of("STOCK_RESERVED", "OUT_OF_STOCK", "STOCK_OUT_OF_STOCK")),
            "notification_events", new Worker("notification", Set.of("EMAIL_SENT")));
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final ApplicationEventPublisher events;
    private final CustomerNotices notices;

    PurchaseResults(JdbcClient jdbc, ObjectMapper mapper, ApplicationEventPublisher events, CustomerNotices notices) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.events = events;
        this.notices = notices;
    }

    @KafkaListener(topics = {"billing_events", "inventory_events", "notification_events"},
            groupId = "${STOREFRONT_RESULT_GROUP:storefront-purchase-results}",
            properties = {"auto.offset.reset=earliest", "enable.auto.commit=false"})
    @Transactional
    public void receive(ConsumerRecord<String, String> record) {
        UUID id;
        String value;
        var worker = WORKERS.get(record.topic());
        try {
            var json = mapper.readTree(record.value());
            id = UUID.fromString(json.path("order_id").asText());
            value = json.path("status").asText();
            if (!worker.statuses().contains(value) || (record.key() != null && !id.toString().equals(record.key()))) {
                throw new IllegalArgumentException("Invalid status event");
            }
        } catch (Exception invalid) {
            log.warn("Evento de resultados invalido en {} offset {}", record.topic(), record.offset());
            return;
        }

        var purchase = jdbc.sql("""
                SELECT p.customer_id,p.id FROM orders.purchase_lines l
                JOIN orders.purchases p ON p.id=l.purchase_id WHERE l.order_id=:orderId FOR UPDATE OF p
                """).param("orderId", id).query((row, index) -> new PurchaseSocket.Update(
                        row.getObject("customer_id", UUID.class), row.getObject("id", UUID.class))).optional();
        if (purchase.isEmpty()) return;
        var changed = jdbc.sql("""
                INSERT INTO orders.purchase_results (order_id,worker,status) VALUES (:id,:worker,:status)
                ON CONFLICT(order_id,worker) DO NOTHING
                """).param("id", id).param("worker", worker.name()).param("status", value).update();
        if (changed == 0) {
            var previous = jdbc.sql("SELECT status FROM orders.purchase_results WHERE order_id=:id AND worker=:worker")
                    .param("id", id).param("worker", worker.name()).query(String.class).single();
            if (!previous.equals(value)) log.warn("Resultados contradictorios para orden {} etapa {}", id, worker.name());
            return;
        }
        jdbc.sql("UPDATE orders.purchase_outbox SET status='SENT',changed_at=now() WHERE order_id=:id AND status<>'SENT'")
                .param("id", id).update();
        var reconciled = jdbc.sql("""
                UPDATE orders.ordenes SET estado='PUBLICADA', actualizada_en=now(), version=version+1
                WHERE order_id=:id AND estado<>'PUBLICADA'
                """).param("id", id).update();
        if (reconciled > 0) {
            jdbc.sql("""
                    INSERT INTO orders.orden_cambios (order_id,secuencia,estado,fecha,motivo)
                    SELECT :id, COALESCE(MAX(secuencia)+1,0),'PUBLICADA',now(),'Resultado del worker confirma el procesamiento'
                    FROM orders.orden_cambios WHERE order_id=:id
                    """).param("id", id).update();
        }
        var update = purchase.get();
        if (worker.name().equals("notification")) notices.record(update.purchaseId());
        events.publishEvent(update);
    }

    private record Worker(String name, Set<String> statuses) {}
}
