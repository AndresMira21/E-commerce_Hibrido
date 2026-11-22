package com.ecommerce.orderapi.storefront;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CatalogQuoteTests {
    private final InventoryReadModel inventory = mock(InventoryReadModel.class);

    @Test
    void pricesComeFromTheCatalogAndTheThresholdIsPerLine() throws Exception {
        var catalog = new CatalogController(new ObjectMapper(), inventory);
        when(inventory.quantities()).thenReturn(Optional.of(Map.of("nexo", 3, "clave", 3)));
        var quote = catalog.calculate(new CatalogController.QuoteRequest(List.of(
                new CatalogController.QuoteItem("nexo", 2), new CatalogController.QuoteItem("clave", 1))));
        assertThat(quote.subtotal()).isEqualByComparingTo("624700");
        assertThat(quote.discount()).isEqualByComparingTo("57980");
        assertThat(quote.total()).isEqualByComparingTo("566720");
        assertThat(CatalogController.discount(new BigDecimal("99999.99"))).isZero();
        assertThat(CatalogController.discount(new BigDecimal("100000"))).isEqualByComparingTo("10000");
    }

    @Test
    void discountsAtTheThresholdAndKeepsDifferentProductsSeparate() throws Exception {
        var mapper = mock(ObjectMapper.class);
        when(mapper.readValue(any(InputStream.class),
                ArgumentMatchers.<TypeReference<List<CatalogController.Product>>>any())).thenReturn(List.of(
                new CatalogController.Product("a", "Audio", "A", 80000, ""),
                new CatalogController.Product("b", "Audio", "B", 80000, ""),
                new CatalogController.Product("c", "Audio", "C", 100000, ""),
                new CatalogController.Product("d", "Audio", "D", 150000, "")));
        when(inventory.quantities()).thenReturn(Optional.of(Map.of("a", 2, "b", 1, "c", 1, "d", 1)));
        var catalog = new CatalogController(mapper, inventory);
        var first = new CatalogController.QuoteItem("a", 1);

        assertQuote(catalog, "80000", "0", "80000", first);
        assertQuote(catalog, "100000", "10000", "90000", new CatalogController.QuoteItem("c", 1));
        assertQuote(catalog, "150000", "15000", "135000", new CatalogController.QuoteItem("d", 1));
        assertQuote(catalog, "160000", "16000", "144000", new CatalogController.QuoteItem("a", 2));
        assertQuote(catalog, "160000", "0", "160000", first, new CatalogController.QuoteItem("b", 1));
    }

    private static void assertQuote(CatalogController catalog, String subtotal, String discount, String total,
                                    CatalogController.QuoteItem... items) {
        var quote = catalog.calculate(new CatalogController.QuoteRequest(List.of(items)));
        assertThat(quote.subtotal()).isEqualByComparingTo(subtotal);
        assertThat(quote.discount()).isEqualByComparingTo(discount);
        assertThat(quote.total()).isEqualByComparingTo(total);
    }

    @Test
    void reportsVerifiedStockAndUnknownAvailabilitySeparately() throws Exception {
        var catalog = new CatalogController(new ObjectMapper(), inventory);
        when(inventory.quantities()).thenReturn(Optional.of(Map.of("nexo", 16, "clave", 1, "halo", 0)));
        var products = catalog.products().getBody();
        assertThat(products.stream().filter(p -> p.id().equals("nexo")).findFirst().orElseThrow())
                .satisfies(p -> { assertThat(p.availability()).isEqualTo("IN_STOCK"); assertThat(p.availableQuantity()).isEqualTo(16); });
        assertThat(products.stream().filter(p -> p.id().equals("clave")).findFirst().orElseThrow().availability())
                .isEqualTo("LOW_STOCK");
        assertThat(products.stream().filter(p -> p.id().equals("halo")).findFirst().orElseThrow().availability())
                .isEqualTo("OUT_OF_STOCK");
        when(inventory.quantities()).thenReturn(Optional.empty());
        assertThat(catalog.products().getBody()).allSatisfy(p -> {
            assertThat(p.availability()).isEqualTo("UNKNOWN");
            assertThat(p.availableQuantity()).isNull();
        });
    }

    @Test
    void missingAndUnreachableStockGiveDistinctOutcomes() throws Exception {
        var catalog = new CatalogController(new ObjectMapper(), inventory);
        var request = new CatalogController.QuoteRequest(List.of(new CatalogController.QuoteItem("nexo", 2)));
        when(inventory.quantities()).thenReturn(Optional.of(Map.of("nexo", 1)));
        assertThatThrownBy(() -> catalog.calculate(request)).isInstanceOfSatisfying(
                CatalogController.QuoteRejected.class, failure -> assertThat(failure.status.value()).isEqualTo(409));
        when(inventory.quantities()).thenReturn(Optional.empty());
        assertThatThrownBy(() -> catalog.calculate(request)).isInstanceOfSatisfying(
                CatalogController.QuoteRejected.class, failure -> assertThat(failure.status.value()).isEqualTo(503));
    }
}
