package com.ecommerce.orderapi.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class CreateOrderRequestTests {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() { factory.close(); }

    @Test
    void acceptsValidOrder() {
        var request = new CreateOrderRequest("Alejandra", "alejandra@example.com", "Libro", 2,
                new BigDecimal("150000.75"));
        assertThat(validator.validate(request)).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingCustomer(String cliente) {
        assertInvalid(new CreateOrderRequest(cliente, "a@example.com", "Libro", 1, BigDecimal.TEN), "cliente");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "sin-correo", "a@", "a b@example.com"})
    void rejectsInvalidEmail(String correo) {
        assertInvalid(new CreateOrderRequest("Ana", correo, "Libro", 1, BigDecimal.TEN), "correo");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void rejectsMissingItem(String item) {
        assertInvalid(new CreateOrderRequest("Ana", "a@example.com", item, 1, BigDecimal.TEN), "item");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveQuantity(int cantidad) {
        assertInvalid(new CreateOrderRequest("Ana", "a@example.com", "Libro", cantidad, BigDecimal.TEN), "cantidad");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-0.01"})
    void rejectsMissingOrNonPositiveAmount(String monto) {
        assertInvalid(new CreateOrderRequest("Ana", "a@example.com", "Libro", 1,
                monto == null ? null : new BigDecimal(monto)), "monto");
    }

    @Test
    void acceptsPositiveFractionalAmount() {
        assertThat(validator.validate(new CreateOrderRequest("Ana", "a@example.com", "Libro", 1,
                new BigDecimal("0.01")))).isEmpty();
    }

    private void assertInvalid(CreateOrderRequest request, String field) {
        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsOnly(field);
    }
}
