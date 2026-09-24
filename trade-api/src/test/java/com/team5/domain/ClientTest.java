package com.team5.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ClientTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void shouldCreateClientWithValidValues() {
        Client client = createClient();

        assertEquals(1L, client.getId());
        assertEquals("John Doe", client.getName());
        assertEquals("john@example.com", client.getEmail());

        Set<ConstraintViolation<Client>> violations = validator.validate(client);

        assertTrue(violations.isEmpty());
    }

    @Test
    void shouldRejectBlankName() {
        Client client = new Client(
                1L,
                101L,
                " ",
                "john@example.com",
                "9999999999",
                LocalDate.of(2000, 1, 1),
                null,
                null
        );

        Set<ConstraintViolation<Client>> violations = validator.validate(client);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("name"))
        );
    }

    @Test
    void shouldRejectBlankEmail() {
        Client client = new Client(
                1L,
                101L,
                "John Doe",
                " ",
                "9999999999",
                LocalDate.of(2000, 1, 1),
                null,
                null
        );

        Set<ConstraintViolation<Client>> violations = validator.validate(client);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("email"))
        );
    }

    @Test
    void shouldRejectBlankPhone() {
        Client client = new Client(
                1L,
                101L,
                "John Doe",
                "john@example.com",
                " ",
                LocalDate.of(2000, 1, 1),
                null,
                null
        );

        Set<ConstraintViolation<Client>> violations = validator.validate(client);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("phone"))
        );
    }

    @Test
    void shouldRejectNullDateOfBirth() {
        Client client = new Client(
                1L,
                101L,
                "John Doe",
                "john@example.com",
                "9999999999",
                null,
                null,
                null
        );

        Set<ConstraintViolation<Client>> violations = validator.validate(client);

        assertTrue(
                violations.stream()
                        .anyMatch(v -> v.getPropertyPath().toString().equals("dob"))
        );
    }

    private Client createClient() {
        return new Client(
                1L,
                101L,
                "John Doe",
                "john@example.com",
                "9999999999",
                LocalDate.of(2000, 1, 1),
                null,
                null
        );
    }
}