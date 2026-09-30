package com.team5.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class AddToWatchlistRequest {

    @NotNull
    @Min(1)
    private Long accountId;

    @NotBlank
    @Size(min = 1, max = 20)
    private String symbol;

    public AddToWatchlistRequest() {
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
}
