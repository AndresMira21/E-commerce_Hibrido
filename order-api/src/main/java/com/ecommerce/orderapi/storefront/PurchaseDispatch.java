package com.ecommerce.orderapi.storefront;

import com.ecommerce.orderapi.event.NewOrderEvent;
import com.ecommerce.orderapi.publisher.OrderEventPublisher;
import com.ecommerce.orderapi.repository.OrdenRepository;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration
@EnableScheduling
@Profile("storefront")
@ConditionalOnProperty(name = "storefront.dispatch.enabled", havingValue = "true")
class PurchaseDispatch {
    private static final Logger log = LoggerFactory.getLogger(PurchaseDispatch.class);
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final OrdenRepository orders;
    private final OrderEventPublisher publisher;

    PurchaseDispatch(JdbcClient jdbc, TransactionTemplate transactions, OrdenRepository orders, OrderEventPublisher publisher) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.orders = orders;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelay = 750)
    void dispatch() {
        jdbc.sql("""
                UPDATE orders.purchase_outbox SET status='UNCERTAIN', changed_at=now()
                WHERE status='SENDING' AND changed_at < now() - interval '2 minutes'
                """).update();
        var id = transactions.execute(status -> jdbc.sql("""
                UPDATE orders.purchase_outbox SET status='SENDING', changed_at=now()
                WHERE order_id=(SELECT order_id FROM orders.purchase_outbox WHERE status='PENDING'
                                ORDER BY changed_at,order_id LIMIT 1 FOR UPDATE SKIP LOCKED)
                RETURNING order_id
                """).query(UUID.class).optional());
        if (id == null || id.isEmpty()) return;
        var orderId = id.get();
        boolean confirmed;
        try {
            var order = orders.findById(orderId).orElseThrow();
            publisher.publish(new NewOrderEvent(orderId.toString(), order.getCliente(), order.getCorreo(),
                    order.detalle().producto(), order.detalle().cantidad(), order.dinero().valor())).get(20, TimeUnit.SECONDS);
            confirmed = true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception failure) {
            log.warn("Resultado de publicacion incierto para orden {}", orderId);
            confirmed = false;
        }
        var delivered = confirmed;
        transactions.executeWithoutResult(status -> {
            var updated = jdbc.sql("""
                    UPDATE orders.purchase_outbox SET status=:result, changed_at=now()
                    WHERE order_id=:id AND status IN ('SENDING','UNCERTAIN')
                    """).param("result", delivered ? "SENT" : "UNCERTAIN").param("id", orderId).update();
            if (updated > 0) {
                var order = orders.findById(orderId).orElseThrow();
                if (delivered) order.confirmarPublicacion(); else order.registrarPublicacionIncierta();
            }
        });
    }
}
