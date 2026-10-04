package com.team5.dto.response;

import java.time.LocalDate;

public record ClientProfileResponse(
    Long accountId,
    String name,
    String email,
    String phone,
    LocalDate dob
) {}
