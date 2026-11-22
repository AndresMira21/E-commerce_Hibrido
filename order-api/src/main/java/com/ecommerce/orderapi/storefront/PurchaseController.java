package com.ecommerce.orderapi.storefront;

import com.ecommerce.orderapi.domain.*;
import com.ecommerce.orderapi.repository.OrdenRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("storefront")
@RequestMapping("/api/me/purchases")
class PurchaseController {
    private final JdbcClient jdbc;
    private final ObjectMapper mapper;
    private final CatalogController catalog;
    private final OrdenRepository orders;
    private final ApplicationEventPublisher events;

    PurchaseController(JdbcClient jdbc, ObjectMapper mapper, CatalogController catalog, OrdenRepository orders,
                       ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.catalog = catalog;
        this.orders = orders;
        this.events = events;
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Receipt> create(@AuthenticationPrincipal AccountController.Customer customer,
                                          @RequestHeader("Idempotency-Key") UUID key,
                                          @Valid @RequestBody Checkout input) {
        var canonical = input.quote().items().stream().sorted(Comparator.comparing(CatalogController.QuoteItem::sku)).toList();
        var fingerprint = fingerprint(new Checkout(new CatalogController.QuoteRequest(canonical), input.delivery(),
                input.payment(), input.expectedTotal().stripTrailingZeros()));
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(:scope, 0))")
                .param("scope", customer.id() + "/" + key).query((row, index) -> true).single();
        var existing = jdbc.sql("SELECT id, request_hash, total FROM orders.purchases WHERE customer_id = :owner AND idempotency_key = :key")
                .param("owner", customer.id()).param("key", key)
                .query((row, index) -> new Saved(row.getObject("id", UUID.class), row.getString("request_hash"), row.getBigDecimal("total"))).optional();
        if (existing.isPresent()) {
            var purchase = existing.get();
            if (!purchase.hash().equals(fingerprint)) {
                throw new CatalogController.QuoteRejected(HttpStatus.CONFLICT, "La clave de compra ya se utilizo con otros datos.");
            }
            return receipt(purchase.id(), purchase.total());
        }
        var quote = catalog.calculate(input.quote());
        if (quote.total().compareTo(input.expectedTotal()) != 0) {
            throw new CatalogController.QuoteRejected(HttpStatus.CONFLICT, "La cotizacion cambio. Revisa el nuevo total antes de confirmar.");
        }
        var purchaseId = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO orders.purchases (id,customer_id,idempotency_key,request_hash,delivery,payment_method,subtotal,discount,total)
                VALUES (:id,:owner,:key,:hash,CAST(:delivery AS jsonb),:method,:subtotal,:discount,:total)
                """)
                .param("id", purchaseId).param("owner", customer.id()).param("key", key)
                .param("hash", fingerprint).param("delivery", json(input.delivery())).param("method", input.payment())
                .param("subtotal", quote.subtotal()).param("discount", quote.discount()).param("total", quote.total()).update();
        for (var line : quote.lines()) {
            var id = IdentificadorOrden.nuevo();
            var order = Orden.builder().identificador(id).cliente(customer.name()).correo(customer.email())
                    .detalle(new DetalleItem(line.sku(), line.quantity())).dinero(new Dinero(line.subtotal())).build();
            orders.saveAndFlush(order);
            jdbc.sql("""
                    INSERT INTO orders.purchase_lines (order_id,purchase_id,sku,product_name,unit_price,discount)
                    VALUES (:order,:purchase,:sku,:name,:price,:discount)
                    """)
                    .param("order", id.valor()).param("purchase", purchaseId).param("sku", line.sku())
                    .param("name", line.name()).param("price", line.unitPrice()).param("discount", line.discount()).update();
            jdbc.sql("INSERT INTO orders.purchase_outbox (order_id) VALUES (:order)").param("order", id.valor()).update();
        }
        events.publishEvent(new PurchaseSocket.Update(customer.id(), purchaseId));
        return receipt(purchaseId, quote.total());
    }

    @ExceptionHandler(CatalogController.QuoteRejected.class)
    ResponseEntity<?> rejected(CatalogController.QuoteRejected error) {
        return ResponseEntity.status(error.status).body(Map.of("error", error.getMessage()));
    }

    private ResponseEntity<Receipt> receipt(UUID id, BigDecimal total) {
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(new Receipt(id, total));
    }

    private String fingerprint(Checkout input) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json(input).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalStateException(failure); }
    }

    record Checkout(@Valid @NotNull CatalogController.QuoteRequest quote,
                    @Valid @NotNull Delivery delivery,
                    @NotNull @Pattern(regexp = "tarjeta|contraentrega") String payment,
                    @NotNull @Positive BigDecimal expectedTotal) {}
    record Delivery(@NotBlank @Size(max = 100) String recipient,
                    @NotBlank @Size(max = 20) @Pattern(regexp = "\\+?[0-9 ()-]{7,20}") String phone,
                    @NotBlank @Size(max = 60) String department,
                    @NotBlank @Size(max = 80) String city,
                    @NotBlank @Size(max = 140) String address,
                    @NotBlank @Size(max = 80) String neighborhood,
                    @Size(max = 240) String notes) {
        @AssertTrue(message = "El telefono debe tener de 7 a 15 digitos")
        public boolean isPhoneValid() {
            return phone != null && phone.replaceAll("\\D", "").matches("[0-9]{7,15}");
        }
    }
    record Receipt(UUID id, BigDecimal total) {}
    private record Saved(UUID id, String hash, BigDecimal total) {}
}
