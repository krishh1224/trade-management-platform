package com.team5.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Client {

    private final long id;

    private final String name;

    private final String email;

    private final String phone;

    private final LocalDate dob;

    private final LocalDateTime createdAt;

    private final LocalDateTime updatedAt;

    public Client(long id, String name, String email, String phone,
                  LocalDate dob, LocalDateTime createdAt, LocalDateTime updatedAt) {
        if (name == null || name.isBlank())  throw new IllegalArgumentException("Client name must not be blank.");
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Client email must not be blank.");
        if (phone == null || phone.isBlank()) throw new IllegalArgumentException("Client phone must not be blank.");
        if (dob == null) throw new IllegalArgumentException("Client dob must not be null.");
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.dob = dob;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public LocalDate getDob() { return dob; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "Client{id=" + id + ", name='" + name + "', email='" + email + "'}";
    }
}