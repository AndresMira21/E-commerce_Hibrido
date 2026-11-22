package com.ecommerce.orderapi.storefront;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.BeforeEach;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("storefront")
@EnabledIfEnvironmentVariable(named = "STOREFRONT_POSTGRES_TEST", matches = "true")
class StorefrontIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired CustomerNotices notices;
    @Autowired org.springframework.context.ApplicationEventPublisher events;
    @Autowired org.springframework.transaction.support.TransactionTemplate transactions;
    @MockitoBean InventoryReadModel inventory;

    @BeforeEach
    void storefrontInventory() {
        org.mockito.Mockito.when(inventory.quantities()).thenReturn(Optional.of(Map.of("nexo", 4, "trazo", 3)));
    }

    @Test
    void purchaseUsesServerPricesAndOnlyTheOwnerCanReplayOrReadIt() throws Exception {
        var owner = registerBuyer();
        var other = registerBuyer();
        var key = UUID.randomUUID().toString();
        var input = """
                {"quote":{"items":[{"sku":"nexo","quantity":2},{"sku":"trazo","quantity":1}]},
                "expectedTotal":809730,"payment":"contraentrega",
                "delivery":{"recipient":"Cliente de prueba","phone":"3000000000","department":"Cundinamarca",
                "city":"Bogota","address":"Calle de prueba 10","neighborhood":"Pruebas"}}
                """;
        var quoteToken = csrf(owner);
        mvc.perform(post("/api/me/purchases").session(owner).header(quoteToken.header(), quoteToken.value())
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON)
                        .content(input.replace("809730", "1"))).andExpect(status().isConflict());
        var first = mvc.perform(post("/api/me/purchases").session(owner).header(quoteToken.header(), quoteToken.value())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isAccepted()).andReturn();
        var id = json.readTree(first.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(post("/api/me/purchases").session(owner).header(quoteToken.header(), quoteToken.value())
                        .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/me/purchases").session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].lines", hasSize(2)));
        mvc.perform(get("/api/me/purchases/" + id).session(other)).andExpect(status().isNotFound());
        mvc.perform(get("/api/me/purchases/" + id)).andExpect(status().isUnauthorized());

        var lineIds = jdbc.sql("SELECT order_id FROM orders.purchase_lines WHERE purchase_id=:id ORDER BY sku")
                .param("id", UUID.fromString(id)).query(UUID.class).list();
        var projection = new PurchaseResults(jdbc, json, events, notices);
        for (int index = 0; index < lineIds.size(); index++) {
            var line = lineIds.get(index);
            receive(projection, "billing_events", line, index == 0 ? "PAYMENT_SUCCESS" : "PAYMENT_FAILED");
            receive(projection, "inventory_events", line, index == 0 ? "STOCK_RESERVED" : "OUT_OF_STOCK");
            mvc.perform(get("/api/me/notifications").session(owner)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.items", hasSize(index)));
            receive(projection, "notification_events", line, "EMAIL_SENT");
            receive(projection, "notification_events", line, "EMAIL_SENT");
        }
        var inbox = mvc.perform(get("/api/me/notifications").session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1)).andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].purchaseId").value(id)).andReturn();
        var noticeId = json.readTree(inbox.getResponse().getContentAsString()).at("/items/0/id").asText();
        var otherToken = csrf(other);
        mvc.perform(get("/api/me/notifications").session(other)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0));
        mvc.perform(post("/api/me/notifications/" + noticeId + "/delete").session(other)
                        .header(otherToken.header(), otherToken.value())).andExpect(status().isNotFound());
        mvc.perform(post("/api/me/notifications/delete-all").session(other)
                        .header(otherToken.header(), otherToken.value())).andExpect(status().isNoContent());
        mvc.perform(post("/api/me/notifications/" + noticeId + "/delete").session(owner))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/me/notifications/read-all").session(owner).header(quoteToken.header(), quoteToken.value()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/notifications").session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0)).andExpect(jsonPath("$.items", hasSize(1)));
        mvc.perform(post("/api/me/notifications/" + noticeId + "/delete").session(owner)
                        .header(quoteToken.header(), quoteToken.value())).andExpect(status().isNoContent());
        receive(projection, "notification_events", lineIds.get(0), "EMAIL_SENT");
        var second = mvc.perform(post("/api/me/purchases").session(owner).header(quoteToken.header(), quoteToken.value())
                        .header("Idempotency-Key", UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isAccepted()).andReturn();
        var secondId = UUID.fromString(json.readTree(second.getResponse().getContentAsString()).path("id").asText());
        var secondLine = jdbc.sql("SELECT order_id FROM orders.purchase_lines WHERE purchase_id=:id LIMIT 1")
                .param("id", secondId).query(UUID.class).single();
        receive(projection, "notification_events", secondLine, "EMAIL_SENT");
        mvc.perform(get("/api/me/notifications").session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1)).andExpect(jsonPath("$.items[0].purchaseId").value(secondId.toString()));
        mvc.perform(post("/api/me/notifications/delete-all").session(owner).header(quoteToken.header(), quoteToken.value()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me/notifications").session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0)).andExpect(jsonPath("$.items", hasSize(0)));
        assertThat(jdbc.sql("SELECT count(*) FROM orders.customer_notifications WHERE purchase_id IN (:first,:second) AND deleted_at IS NOT NULL")
                .param("first", UUID.fromString(id)).param("second", secondId).query(Long.class).single()).isEqualTo(2);
        mvc.perform(get("/api/me/purchases/" + id).session(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[?(@.stock == 'STOCK_RESERVED')]", hasSize(1)))
                .andExpect(jsonPath("$.lines[?(@.payment == 'PAYMENT_FAILED')]", hasSize(1)));
    }

    private void receive(PurchaseResults projection, String topic, UUID order, String status) {
        var payload = "{\"order_id\":\"" + order + "\",\"status\":\"" + status + "\"}";
        transactions.executeWithoutResult(transaction -> projection.receive(
                new org.apache.kafka.clients.consumer.ConsumerRecord<>(topic, 0, 0, order.toString(), payload)));
    }

    private MockHttpSession registerBuyer() throws Exception {
        var guest = new MockHttpSession();
        var token = csrf(guest);
        var input = json.writeValueAsString(Map.of("name", "Cliente de prueba", "email", UUID.randomUUID() + "@example.test",
                "password", "abc123", "confirmation", "abc123"));
        return (MockHttpSession) mvc.perform(post("/api/auth/register").session(guest).header(token.header(), token.value())
                        .contentType(MediaType.APPLICATION_JSON).content(input))
                .andExpect(status().isCreated()).andReturn().getRequest().getSession(false);
    }

    @Test
    void visitorCanOnlyReadThePublicSelection() throws Exception {
        mvc.perform(get("/api/catalog/featured")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(6)));
        mvc.perform(get("/api/catalog/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/assets/products/halo.jpg")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/operaciones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/ws")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register")).andExpect(status().isForbidden());
    }

    @Test
    void academicPasswordBoundaryAlsoAppliesToLoginAndKeepsTheBcryptByteLimit() throws Exception {
        var email = UUID.randomUUID() + "@example.test";
        var anonymous = new MockHttpSession();
        var token = csrf(anonymous);
        for (String rejected : new String[] {"abc12", "ü".repeat(37)}) {
            var body = json.writeValueAsString(Map.of("name", "Cliente de prueba", "email", email,
                    "password", rejected, "confirmation", rejected));
            mvc.perform(post("/api/auth/register").session(anonymous).header(token.header(), token.value())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        var mismatch = json.writeValueAsString(Map.of("name", "Cliente de prueba", "email", email,
                "password", "abc123", "confirmation", "abc124"));
        mvc.perform(post("/api/auth/register").session(anonymous).header(token.header(), token.value())
                        .contentType(MediaType.APPLICATION_JSON).content(mismatch))
                .andExpect(status().isBadRequest());
        var body = json.writeValueAsString(Map.of("name", "Cliente de prueba", "email", email,
                "password", "abc123", "confirmation", "abc123"));
        var registered = mvc.perform(post("/api/auth/register").session(anonymous).header(token.header(), token.value())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        var customer = (MockHttpSession) registered.getRequest().getSession(false);
        mvc.perform(get("/api/auth/session").session(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));
        var loginSession = new MockHttpSession();
        var loginToken = csrf(loginSession);
        var login = json.writeValueAsString(Map.of("email", email, "password", "abc123"));
        mvc.perform(post("/api/auth/login").session(loginSession).header(loginToken.header(), loginToken.value())
                        .contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void registrationPersistsHashAndRotatesSessionThenLoginAndLogoutEnforceAccess() throws Exception {
        var email = UUID.randomUUID() + "@example.test";
        var password = "Local-Test-Password-123";
        var registration = json.writeValueAsString(Map.of("name", "Cliente de prueba", "email", email.toUpperCase(java.util.Locale.ROOT),
                "password", password, "confirmation", password));
        var anonymous = new MockHttpSession();
        var token = csrf(anonymous);
        var registered = mvc.perform(post("/api/auth/register").session(anonymous).header(token.header(), token.value())
                        .contentType(MediaType.APPLICATION_JSON).content(registration))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.email").value(email)).andReturn();
        var customer = (MockHttpSession) registered.getRequest().getSession(false);
        assertThat(anonymous.isInvalid()).isTrue();
        assertThat(customer).isNotNull().isNotSameAs(anonymous);
        var hash = jdbc.sql("SELECT password_hash FROM orders.customer_accounts WHERE email = :email")
                .param("email", email).query(String.class).single();
        assertThat(hash).startsWith("$2a$").doesNotContain(password);

        mvc.perform(get("/api/auth/session").session(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));
        mvc.perform(get("/api/catalog/products").session(customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)));
        mvc.perform(get("/api/me/orders/another-order").session(customer)).andExpect(status().isForbidden());
        mvc.perform(get("/operaciones").session(customer)).andExpect(status().isForbidden());
        mvc.perform(get("/ws").session(customer)).andExpect(status().isForbidden());
        token = csrf(customer);
        for (String route : new String[] {"/api/orders", "/order", "/api/me/orders"}) {
            mvc.perform(post(route).session(customer).header(token.header(), token.value()))
                    .andExpect(status().isForbidden());
        }

        mvc.perform(post("/api/auth/logout").session(customer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/logout").session(customer).header(token.header(), token.value()))
                .andExpect(status().isNoContent());
        assertThat(customer.isInvalid()).isTrue();
        mvc.perform(get("/api/catalog/products")).andExpect(status().isUnauthorized());

        var next = new MockHttpSession();
        token = csrf(next);
        mvc.perform(post("/api/auth/register").session(next).header(token.header(), token.value())
                        .contentType(MediaType.APPLICATION_JSON).content(registration)).andExpect(status().isConflict());
        var badLogin = json.writeValueAsString(Map.of("email", email, "password", "wrong-password-123"));
        mvc.perform(post("/api/auth/login").session(next).header(token.header(), token.value())
                        .contentType(MediaType.APPLICATION_JSON).content(badLogin)).andExpect(status().isUnauthorized());
        var login = json.writeValueAsString(Map.of("email", email, "password", password));
        var signedIn = mvc.perform(post("/api/auth/login").session(next).header(token.header(), token.value())
                        .contentType(MediaType.APPLICATION_JSON).content(login)).andExpect(status().isOk()).andReturn();
        mvc.perform(get("/api/catalog/products").session((MockHttpSession) signedIn.getRequest().getSession(false)))
                .andExpect(status().isOk());
    }

    private Token csrf(HttpSession session) throws Exception {
        var response = mvc.perform(get("/api/auth/csrf").session((MockHttpSession) session))
                .andExpect(status().isOk()).andReturn().getResponse();
        var token = json.readTree(response.getContentAsString());
        return new Token(token.get("headerName").asText(), token.get("token").asText());
    }

    private record Token(String header, String value) {}
}
