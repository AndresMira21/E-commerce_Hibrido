package com.ecommerce.orderapi.storefront;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@Profile("storefront")
@RequestMapping("/api/catalog")
class CatalogController {
    private static final BigDecimal DISCOUNT_THRESHOLD = new BigDecimal("100000");
    private static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.10");
    private final List<Product> products;
    private final InventoryReadModel inventory;

    CatalogController(ObjectMapper mapper, InventoryReadModel inventory) throws IOException {
        this.inventory = inventory;
        try (var stream = new ClassPathResource("storefront/catalog.json").getInputStream()) {
            products = List.copyOf(mapper.readValue(stream, new TypeReference<List<Product>>() {}));
        }
    }

    @GetMapping("/featured")
    List<Product> featured() {
        return List.of(products.get(0), products.get(1), products.get(5),
                products.get(3), products.get(4), products.get(14));
    }

    @GetMapping("/products")
    ResponseEntity<List<Listing>> products() {
        var stock = inventory.quantities();
        var listings = products.stream().map(product -> {
            var available = stock.map(counts -> counts.getOrDefault(product.id(), 0)).orElse(null);
            var state = available == null ? "UNKNOWN" : available == 0 ? "OUT_OF_STOCK" :
                    available <= 5 ? "LOW_STOCK" : "IN_STOCK";
            return new Listing(product.id(), product.category(), product.name(), product.price(),
                    product.description(), state, available);
        }).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(listings);
    }

    @PostMapping("/quote")
    ResponseEntity<?> quote(@Valid @RequestBody QuoteRequest request) {
        try {
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(calculate(request));
        } catch (QuoteRejected rejection) {
            return ResponseEntity.status(rejection.status).cacheControl(CacheControl.noStore())
                    .body(Map.of("error", rejection.getMessage()));
        }
    }

    Quote calculate(QuoteRequest request) {
        var items = new java.util.TreeMap<String, Integer>();
        for (var line : request.items()) {
            if (items.putIfAbsent(line.sku(), line.quantity()) != null) {
                throw new QuoteRejected(HttpStatus.BAD_REQUEST, "Cada SKU debe aparecer una sola vez.");
            }
        }
        var lines = new ArrayList<QuoteLine>();
        for (var entry : items.entrySet()) {
            var product = products.stream().filter(candidate -> candidate.id().equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> new QuoteRejected(HttpStatus.BAD_REQUEST, "Hay un producto que no pertenece al catálogo."));
            var subtotal = BigDecimal.valueOf(product.price()).multiply(BigDecimal.valueOf(entry.getValue()));
            lines.add(new QuoteLine(product.id(), product.name(), entry.getValue(), product.price(), subtotal, discount(subtotal)));
        }
        var stock = inventory.quantities().orElseThrow(() ->
                new QuoteRejected(HttpStatus.SERVICE_UNAVAILABLE, "No se pudo verificar el inventario. Intenta nuevamente."));
        for (var line : lines) {
            if (stock.getOrDefault(line.sku(), 0) < line.quantity()) {
                throw new QuoteRejected(HttpStatus.CONFLICT, "No hay existencias suficientes de " + line.name() + ".");
            }
        }
        var subtotal = lines.stream().map(QuoteLine::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        var discount = lines.stream().map(QuoteLine::discount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Quote(List.copyOf(lines), subtotal, discount, subtotal.subtract(discount));
    }

    static BigDecimal discount(BigDecimal orderAmount) {
        return orderAmount.compareTo(DISCOUNT_THRESHOLD) < 0 ? BigDecimal.ZERO :
                orderAmount.multiply(DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    record Product(String id, String category, String name, long price, String description) {}
    record Listing(String id, String category, String name, long price, String description, String availability, Integer availableQuantity) {}
    record QuoteRequest(@NotNull @Size(min = 1, max = 20) List<@NotNull @Valid QuoteItem> items) {}
    record QuoteItem(@NotBlank @Size(max = 40) String sku, @Min(1) @Max(99) int quantity) {}
    record QuoteLine(String sku, String name, int quantity, long unitPrice, BigDecimal subtotal, BigDecimal discount) {}
    record Quote(List<QuoteLine> lines, BigDecimal subtotal, BigDecimal discount, BigDecimal total) {}

    static class QuoteRejected extends RuntimeException {
        final HttpStatus status;
        QuoteRejected(HttpStatus status, String message) { super(message); this.status = status; }
    }
}
