package com.ecommerce.orderapi.storefront;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@Profile("storefront")
class CustomerNotices {
    private final JdbcClient jdbc;

    CustomerNotices(JdbcClient jdbc) { this.jdbc = jdbc; }

    void record(UUID purchase) {
        jdbc.sql("""
                INSERT INTO orders.customer_notifications(id,purchase_id,event_key,title,description)
                VALUES (:id,:purchase,'notification','Aviso de compra registrado',
                        'Puedes consultar el estado de tu compra en Mis pedidos.')
                ON CONFLICT (purchase_id,event_key) DO NOTHING
                """).param("id", UUID.randomUUID()).param("purchase", purchase)
                .update();
    }
}
