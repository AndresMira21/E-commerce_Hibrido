package com.ecommerce.orderapi.storefront;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("storefront")
class InventoryReadModel {
    private static final Logger log = LoggerFactory.getLogger(InventoryReadModel.class);
    private final String url;
    private final Properties credentials = new Properties();

    InventoryReadModel(@Value("${storefront.inventory.url}") String url,
                       @Value("${STOREFRONT_INVENTORY_USERNAME:}") String user,
                       @Value("${STOREFRONT_INVENTORY_PASSWORD:}") String password) {
        this.url = url;
        credentials.setProperty("user", user);
        credentials.setProperty("password", password);
        credentials.setProperty("connectTimeout", "2");
        credentials.setProperty("socketTimeout", "3");
    }

    Optional<Map<String, Integer>> quantities() {
        if (url.isBlank() || credentials.getProperty("user").isBlank()) return Optional.empty();
        try (var connection = DriverManager.getConnection(url, credentials)) {
            connection.setReadOnly(true);
            try (var statement = connection.prepareStatement(
                    "SELECT product_name, available_quantity FROM public.inventory_items");
                 var rows = statement.executeQuery()) {
                var counts = new HashMap<String, Integer>();
                while (rows.next()) counts.put(rows.getString(1), rows.getInt(2));
                return Optional.of(Map.copyOf(counts));
            }
        } catch (SQLException unavailable) {
            log.warn("No fue posible consultar inventario (SQLState={})", unavailable.getSQLState());
            return Optional.empty();
        }
    }
}
