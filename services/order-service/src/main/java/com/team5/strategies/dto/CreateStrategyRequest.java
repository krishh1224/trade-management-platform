package com.team5.strategies.dto;

import com.team5.strategies.domain.StrategyType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

// The parameters that belong together for each type (windows, band width) are checked in the service,
// which says which rule failed.
public class CreateStrategyRequest {

    @NotBlank
    @Size(max = 60)
    private String name;

    @NotBlank
    @Size(min = 1, max = 20)
    private String symbol;

    @NotNull
    private StrategyType type;

    // MA_CROSSOVER
    @Min(2)
    @Max(199)
    private Integer shortWindow;

    @Min(3)
    @Max(200)
    private Integer longWindow;

    // BOLLINGER_BANDS
    @Min(2)
    @Max(200)
    private Integer bandWindow;

    @DecimalMin(value = "0.0", inclusive = false)
    @DecimalMax("5.0")
    @Digits(integer = 1, fraction = 3)
    private BigDecimal bandWidth;

    // shares per order
    @NotNull
    @Min(1)
    @Max(100000)
    private Integer quantity;

    // the most this strategy may ever spend on BUY orders, at their limit price
    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 15, fraction = 2)
    private BigDecimal maxSpend;

    // the most of this symbol the account may hold, counting pending BUYs, after a strategy BUY
    @NotNull
    @Min(1)
    @Max(10000000)
    private Long maxPosition;

    // failed orders in a row before the strategy is halted; 3 when absent
    @Min(1)
    @Max(10)
    private Integer maxConsecutiveFailures;

    public CreateStrategyRequest() {
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public StrategyType getType() { return type; }
    public void setType(StrategyType type) { this.type = type; }

    public Integer getShortWindow() { return shortWindow; }
    public void setShortWindow(Integer shortWindow) { this.shortWindow = shortWindow; }

    public Integer getLongWindow() { return longWindow; }
    public void setLongWindow(Integer longWindow) { this.longWindow = longWindow; }

    public Integer getBandWindow() { return bandWindow; }
    public void setBandWindow(Integer bandWindow) { this.bandWindow = bandWindow; }

    public BigDecimal getBandWidth() { return bandWidth; }
    public void setBandWidth(BigDecimal bandWidth) { this.bandWidth = bandWidth; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public BigDecimal getMaxSpend() { return maxSpend; }
    public void setMaxSpend(BigDecimal maxSpend) { this.maxSpend = maxSpend; }

    public Long getMaxPosition() { return maxPosition; }
    public void setMaxPosition(Long maxPosition) { this.maxPosition = maxPosition; }

    public Integer getMaxConsecutiveFailures() { return maxConsecutiveFailures; }
    public void setMaxConsecutiveFailures(Integer maxConsecutiveFailures) { this.maxConsecutiveFailures = maxConsecutiveFailures; }
}
