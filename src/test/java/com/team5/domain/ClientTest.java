package com.team5.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ClientTest {

    @Test
    void shouldCreateClientWithValidValues() {
        Client client = createClient();

        assertEquals(1L, client.getId());
        assertEquals("John Doe", client.getName());
        assertEquals("john@example.com", client.getEmail());
    }

    @Test
    void shouldRejectBlankName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Client(
                        1L,
                        " ",
                        "john@example.com",
                        "9999999999",
                        LocalDate.of(2000, 1, 1),
                        null,
                        null
                )
        );
    }

    @Test
    void shouldRejectBlankEmail() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Client(
                        1L,
                        "John Doe",
                        " ",
                        "9999999999",
                        LocalDate.of(2000, 1, 1),
                        null,
                        null
                )
        );
    }

    @Test
    void shouldRejectBlankPhone() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Client(
                        1L,
                        "John Doe",
                        "john@example.com",
                        " ",
                        LocalDate.of(2000, 1, 1),
                        null,
                        null
                )
        );
    }

    @Test
    void shouldRejectNullDateOfBirth() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Client(
                        1L,
                        "John Doe",
                        "john@example.com",
                        "9999999999",
                        null,
                        null,
                        null
                )
        );
    }

    private Client createClient() {
        return new Client(
                1L,
                "John Doe",
                "john@example.com",
                "9999999999",
                LocalDate.of(2000, 1, 1),
                null,
                null
        );
    }
}