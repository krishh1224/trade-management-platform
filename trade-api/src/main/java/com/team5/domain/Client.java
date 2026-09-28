package com.team5.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Client {

    private final long id;

    @NotNull
    private final Long accId;

    @NotBlank
    private final String name;

    @NotBlank
    private final String email;

    @NotBlank
    private final String phone;

    @NotNull
    private final LocalDate dob;

    private final LocalDateTime createdAt;

    private final LocalDateTime updatedAt;

    public Client(long id, Long accId, String name, String email, String phone,
                  LocalDate dob, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.accId = accId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.dob = dob;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() { return id; }
    public Long getAccId() { return accId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public LocalDate getDob() { return dob; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "Client{id=" + id + ", accId=" + accId + ", name='" + name + "', email='" + email + "'}";
    }
}