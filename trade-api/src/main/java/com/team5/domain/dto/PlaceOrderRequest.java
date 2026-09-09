package com.team5.domain.dto;

import com.team5.domain.enums.OrderSide;
import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public class PlaceOrderRequest {
    @NotBlank 
    private String accountKey;
    @NotBlank
     private String symbol;
    @NotNull
     private OrderSide side;
    @Positive
    private int quantity;
    @NotNull
    @Positive
    private BigDecimal price;
    @NotBlank 
    private String idempotencyKey;

    public PlaceOrderRequest(String accountKey, String symbol, OrderSide side, int quantity, BigDecimal price, String idempotencyKey) {
        this.accountKey = accountKey;
        this.symbol = symbol;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.idempotencyKey = idempotencyKey;
    }

    public String getAccountKey() { return accountKey; }
    public String getSymbol() { return symbol; }
    public OrderSide getSide() { return side; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
