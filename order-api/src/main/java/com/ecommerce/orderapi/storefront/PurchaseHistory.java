package com.ecommerce.orderapi.storefront;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("storefront")
@RequestMapping("/api/me/purchases")
class PurchaseHistory {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    PurchaseHistory(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @GetMapping
    ResponseEntity<List<Purchase>> list(@AuthenticationPrincipal AccountController.Customer customer,
                                         @RequestParam(defaultValue = "0") int offset) {
        if (offset < 0 || offset > 100_000) return ResponseEntity.badRequest().build();
        var ids = jdbc.sql("""
                SELECT id FROM orders.purchases WHERE customer_id=:owner
                ORDER BY created_at DESC,id DESC LIMIT 25 OFFSET :offset
                """).param("owner", customer.id()).param("offset", offset).query(UUID.class).list();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ids.stream().map(id -> find(customer.id(), id)).flatMap(java.util.Optional::stream).toList());
    }

    @GetMapping("/{id}")
    ResponseEntity<Purchase> detail(@AuthenticationPrincipal AccountController.Customer customer, @PathVariable UUID id) {
        return find(customer.id(), id).map(purchase -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(purchase))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private java.util.Optional<Purchase> find(UUID owner, UUID id) {
        var header = jdbc.sql("""
                SELECT id, created_at, delivery, payment_method, subtotal, discount, total
                FROM orders.purchases WHERE id=:id AND customer_id=:owner
                """).param("id", id).param("owner", owner)
                .query((row, index) -> new Header(row.getObject("id", UUID.class),
                        row.getObject("created_at", OffsetDateTime.class), delivery(row.getString("delivery")),
                        row.getString("payment_method"), row.getBigDecimal("subtotal"), row.getBigDecimal("discount"), row.getBigDecimal("total"))).optional();
        if (header.isEmpty()) return java.util.Optional.empty();
        var lines = jdbc.sql("""
                SELECT l.order_id,l.sku,l.product_name,l.unit_price,l.discount,o.cantidad,o.monto,b.status AS publication,
                    p.status AS payment,s.status AS stock,n.status AS notification
                FROM orders.purchase_lines l
                JOIN orders.purchases h ON h.id=l.purchase_id
                JOIN orders.ordenes o ON o.order_id=l.order_id
                JOIN orders.purchase_outbox b ON b.order_id=l.order_id
                LEFT JOIN orders.purchase_results p ON p.order_id=l.order_id AND p.worker='payment'
                LEFT JOIN orders.purchase_results s ON s.order_id=l.order_id AND s.worker='stock'
                LEFT JOIN orders.purchase_results n ON n.order_id=l.order_id AND n.worker='notification'
                WHERE h.id=:id AND h.customer_id=:owner ORDER BY l.sku
                """).param("id", id).param("owner", owner)
                .query((row, index) -> new Line(row.getObject("order_id", UUID.class), row.getString("sku"),
                        row.getString("product_name"), row.getBigDecimal("unit_price"), row.getBigDecimal("discount"),
                        row.getInt("cantidad"), row.getBigDecimal("monto"), row.getString("publication"),
                        row.getString("payment"), row.getString("stock"), row.getString("notification"))).list();
        var data = header.get();
        return java.util.Optional.of(new Purchase(data.id(), data.date(), data.delivery(), data.payment(),
                data.subtotal(), data.discount(), data.total(), lines));
    }

    private JsonNode delivery(String json) {
        try { return mapper.readTree(json); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalStateException(invalid); }
    }

    private record Header(UUID id, OffsetDateTime date, JsonNode delivery, String payment,
                          BigDecimal subtotal, BigDecimal discount, BigDecimal total) {}
    record Purchase(UUID id, OffsetDateTime date, JsonNode delivery, String payment,
                    BigDecimal subtotal, BigDecimal discount, BigDecimal total, List<Line> lines) {}
    record Line(UUID orderId, String sku, String name, BigDecimal unitPrice, BigDecimal discount,
                int quantity, BigDecimal subtotal, String publication, String payment, String stock, String notification) {}
}
