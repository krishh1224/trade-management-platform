package com.neueda.learning.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

class SecretsValidatorTest {

    private SecretsValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SecretsValidator();
        // Valid defaults for every field; each test overrides only the one it exercises.
        ReflectionTestUtils.setField(validator, "jwtSecret", "a".repeat(32));
        ReflectionTestUtils.setField(validator, "datasourceUrl", "jdbc:postgresql://localhost:5432/trade_management");
        ReflectionTestUtils.setField(validator, "datasourceUsername", "postgres");
        ReflectionTestUtils.setField(validator, "datasourcePassword", "postgres");
        ReflectionTestUtils.setField(validator, "kafkaBootstrapServers", "localhost:8085");
    }

    @Test
    void allValidConfigurationPassesWithoutThrowing() {
        assertDoesNotThrow(() -> validator.run(new DefaultApplicationArguments()));
    }

    @Test
    void blankJwtSecretThrows() {
        ReflectionTestUtils.setField(validator, "jwtSecret", "  ");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
        assertTrue(exception.getMessage().contains("JWT_SECRET environment variable is not set"));
    }

    @Test
    void tooShortJwtSecretThrows() {
        ReflectionTestUtils.setField(validator, "jwtSecret", "short");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
        assertTrue(exception.getMessage().contains("JWT_SECRET is too short"));
    }

    @Test
    void blankDatasourceUrlThrows() {
        ReflectionTestUtils.setField(validator, "datasourceUrl", "");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
        assertTrue(exception.getMessage().contains("SPRING_DATASOURCE_URL is not set"));
    }

    @Test
    void blankDatasourceUsernameThrows() {
        ReflectionTestUtils.setField(validator, "datasourceUsername", "");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
        assertTrue(exception.getMessage().contains("SPRING_DATASOURCE_USERNAME is not set"));
    }

    @Test
    void blankDatasourcePasswordThrows() {
        ReflectionTestUtils.setField(validator, "datasourcePassword", "");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
        assertTrue(exception.getMessage().contains("SPRING_DATASOURCE_PASSWORD is not set"));
    }

    @Test
    void blankKafkaBootstrapServersThrows() {
        ReflectionTestUtils.setField(validator, "kafkaBootstrapServers", "");

        IllegalStateException exception = assertThrows(
            IllegalStateException.class, () -> validator.run(new DefaultApplicationArguments()));
        assertTrue(exception.getMessage().contains("KAFKA_BOOTSTRAP_SERVERS environment variable is not set"));
    }
}
