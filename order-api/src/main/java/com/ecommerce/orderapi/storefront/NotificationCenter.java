package com.ecommerce.orderapi.storefront;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("storefront")
@RequestMapping("/api/me/notifications")
class NotificationCenter {
    private final JdbcClient jdbc;
    private final ApplicationEventPublisher events;

    NotificationCenter(JdbcClient jdbc, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.events = events;
    }

    @GetMapping
    public ResponseEntity<Inbox> inbox(@AuthenticationPrincipal AccountController.Customer customer,
                                        @RequestParam(defaultValue = "0") int offset) {
        if (offset < 0) return ResponseEntity.badRequest().build();
        var snapshot = jdbc.sql("""
                SELECT n.id,n.purchase_id,n.title,n.description,n.created_at,n.read_at
                FROM orders.customer_notifications n JOIN orders.purchases p ON p.id=n.purchase_id
                WHERE p.customer_id=:owner AND n.event_key='notification' AND n.deleted_at IS NULL
                ORDER BY n.created_at DESC,n.id DESC LIMIT 26 OFFSET :offset
                """).param("owner", customer.id()).param("offset", offset).query((row, index) -> new Notice(
                        row.getObject("id", UUID.class), row.getObject("purchase_id", UUID.class), row.getString("title"),
                        row.getString("description"), row.getObject("created_at", OffsetDateTime.class),
                        row.getObject("read_at", OffsetDateTime.class))).list();
        var unread = jdbc.sql("""
                SELECT count(*) FROM orders.customer_notifications n JOIN orders.purchases p ON p.id=n.purchase_id
                WHERE p.customer_id=:owner AND n.event_key='notification' AND n.deleted_at IS NULL AND n.read_at IS NULL
                """).param("owner", customer.id()).query(Long.class).single();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                new Inbox(snapshot.stream().limit(25).toList(), unread, snapshot.size() > 25));
    }

    @PostMapping("/{id}/read")
    @Transactional
    public ResponseEntity<Void> read(@AuthenticationPrincipal AccountController.Customer customer, @PathVariable UUID id) {
        var found = jdbc.sql("""
                UPDATE orders.customer_notifications n SET read_at=COALESCE(n.read_at, now())
                FROM orders.purchases p WHERE p.id=n.purchase_id AND p.customer_id=:owner AND n.id=:id
                    AND n.event_key='notification' AND n.deleted_at IS NULL
                """).param("owner", customer.id()).param("id", id).update();
        if (found == 0) return ResponseEntity.notFound().build();
        events.publishEvent(new PurchaseSocket.Update(customer.id(), null));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @Transactional
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal AccountController.Customer customer) {
        var changed = jdbc.sql("""
                UPDATE orders.customer_notifications n SET read_at=now()
                FROM orders.purchases p WHERE p.id=n.purchase_id AND p.customer_id=:owner AND n.read_at IS NULL
                    AND n.event_key='notification' AND n.deleted_at IS NULL
                """).param("owner", customer.id()).update();
        if (changed > 0) events.publishEvent(new PurchaseSocket.Update(customer.id(), null));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/delete")
    @Transactional
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AccountController.Customer customer, @PathVariable UUID id) {
        var changed = jdbc.sql("""
                UPDATE orders.customer_notifications n SET deleted_at=now()
                FROM orders.purchases p WHERE p.id=n.purchase_id AND p.customer_id=:owner AND n.id=:id
                    AND n.event_key='notification' AND n.deleted_at IS NULL
                """).param("owner", customer.id()).param("id", id).update();
        if (changed == 0) return ResponseEntity.notFound().build();
        events.publishEvent(new PurchaseSocket.Update(customer.id(), null));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/delete-all")
    @Transactional
    public ResponseEntity<Void> deleteAll(@AuthenticationPrincipal AccountController.Customer customer) {
        var changed = jdbc.sql("""
                UPDATE orders.customer_notifications n SET deleted_at=now()
                FROM orders.purchases p WHERE p.id=n.purchase_id AND p.customer_id=:owner
                    AND n.event_key='notification' AND n.deleted_at IS NULL
                """).param("owner", customer.id()).update();
        if (changed > 0) events.publishEvent(new PurchaseSocket.Update(customer.id(), null));
        return ResponseEntity.noContent().build();
    }

    record Inbox(List<Notice> items, long unread, boolean hasMore) {}
    record Notice(UUID id, UUID purchaseId, String title, String description, OffsetDateTime createdAt, OffsetDateTime readAt) {}
}
