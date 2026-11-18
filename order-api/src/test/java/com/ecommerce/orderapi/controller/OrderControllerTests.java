package com.ecommerce.orderapi.controller;

import com.ecommerce.orderapi.event.NewOrderEvent;
import com.ecommerce.orderapi.publisher.KafkaOrderPublisher;
import com.ecommerce.orderapi.service.OrderService;
import com.ecommerce.orderapi.service.OrdenPersistence;
import com.ecommerce.orderapi.repository.OrdenRepository;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class OrderControllerTests {
    private static final String JSON = """
            {"cliente":"Ana","correo":"ana@example.com","item":"Libro","cantidad":2,"monto":120.50}
            """;
    @Mock private KafkaOrderPublisher publisher;
    @Mock private OrdenRepository repository;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        lenient().when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(repository.findById(any())).thenAnswer(invocation -> java.util.Optional.of(
                com.ecommerce.orderapi.domain.Orden.builder()
                        .identificador(new com.ecommerce.orderapi.domain.IdentificadorOrden(invocation.getArgument(0)))
                        .cliente("Ana").correo("ana@example.com")
                        .detalle(new com.ecommerce.orderapi.domain.DetalleItem("Libro",2))
                        .dinero(new com.ecommerce.orderapi.domain.Dinero(new BigDecimal("120.50"))).build()));
        OrderService service = new OrderService(publisher, new TaskExecutorAdapter(new SyncTaskExecutor()),
                new OrdenPersistence(repository));
        mvc = MockMvcBuilders.standaloneSetup(new OrderController(service))
                .setControllerAdvice(new OrderExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/orders", "/order"})
    void validOrderGeneratesUuidAndReturns202AfterConfirmation(String path) throws Exception {
        when(publisher.publish(any())).thenReturn(CompletableFuture.completedFuture(null));
        MvcResult pending = mvc.perform(post(path).contentType("application/json").content(JSON))
                .andExpect(request().asyncStarted()).andReturn();
        ArgumentCaptor<NewOrderEvent> captor = ArgumentCaptor.forClass(NewOrderEvent.class);
        verify(publisher).publish(captor.capture());
        NewOrderEvent event = captor.getValue();
        assertThat(UUID.fromString(event.orderId()).toString()).isEqualTo(event.orderId());
        assertThat(event).isEqualTo(new NewOrderEvent(event.orderId(), "Ana", "ana@example.com",
                "Libro", 2, new BigDecimal("120.50")));
        mvc.perform(asyncDispatch(pending)).andExpect(status().isAccepted())
                .andExpect(jsonPath("order_id").value(event.orderId()))
                .andExpect(jsonPath("status").value("ACCEPTED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"cliente\":\" \"}",
            "{\"correo\":\"invalido\"}",
            "{\"cantidad\":0}",
            "{\"cantidad\":-1}",
            "{\"monto\":0}",
            "{\"monto\":-1}"
    })
    void invalidFieldReturns400WithoutPublishing(String patch) throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var input = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(JSON);
        input.setAll((com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(patch));
        mvc.perform(post("/api/orders").contentType("application/json").content(input.toString()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error").value("Solicitud invalida"))
                .andExpect(jsonPath("fields").isMap());
        verifyNoInteractions(publisher);
    }

    @Test
    void databaseFailureReturns503AndDoesNotPublish() throws Exception {
        when(repository.saveAndFlush(any())).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("DB privada"));
        MvcResult pending = mvc.perform(post("/order").contentType("application/json").content(JSON))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("error").exists());
        verifyNoInteractions(publisher);
    }

    @Test
    void invalidAliasDoesNotPersistOrPublish() throws Exception {
        mvc.perform(post("/order").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository, publisher);
    }

    @Test
    void malformedJsonReturns400WithoutPublishing() throws Exception {
        mvc.perform(post("/api/orders").contentType("application/json").content("{bad"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("error").exists());
        verifyNoInteractions(publisher);
    }

    @Test
    void kafkaFailureReturns503WithoutInternalDetails() throws Exception {
        when(publisher.publish(any())).thenReturn(CompletableFuture.failedFuture(
                new IllegalStateException("password=confidencial broker:9092")));
        MvcResult pending = mvc.perform(post("/api/orders").contentType("application/json").content(JSON))
                .andExpect(request().asyncStarted()).andReturn();
        mvc.perform(asyncDispatch(pending)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("error").value("No se pudo confirmar la orden. Intenta mas tarde"))
                .andExpect(jsonPath("stackTrace").doesNotExist())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("confidencial"))));
    }

    @Test
    void pendingKafkaDoesNotCompleteHttpResponsePrematurely() throws Exception {
        CompletableFuture<SendResult<String, String>> kafka = new CompletableFuture<>();
        when(publisher.publish(any())).thenReturn(kafka);
        MvcResult pending = mvc.perform(post("/api/orders").contentType("application/json").content(JSON))
                .andExpect(request().asyncStarted()).andReturn();
        assertThat(kafka.isDone()).isFalse();
        assertThat(pending.getRequest().isAsyncStarted()).isTrue();
        assertThat(pending.getResponse().getContentAsString()).isEmpty();
        assertThat(pending.getResponse().getStatus()).isNotEqualTo(202);
        kafka.complete(null);
        mvc.perform(asyncDispatch(pending)).andExpect(status().isAccepted());
    }
}
