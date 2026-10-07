package com.team5.watchlists.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

public class CreatePriceAlertRequest {

    // the watchlist entry the alert hangs off; the browser sends it as a string (63-bit id)
    @NotNull
    @Min(1)
    private Long watchlistId;

    // a String, not the enum, so an unknown value is VAL-422 like every other field
    @NotNull
    @Pattern(regexp = "ABOVE|BELOW")
    private String direction;

    @NotNull
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 15, fraction = 4)
    private BigDecimal threshold;

    public Long getWatchlistId() {
        return watchlistId;
    }

    public void setWatchlistId(Long watchlistId) {
        this.watchlistId = watchlistId;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }
}
