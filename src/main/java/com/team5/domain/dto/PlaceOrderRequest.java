package com.team5.domain.dto;

import com.team5.domain.enums.OrderSide;
import java.math.BigDecimal;

public class PlaceOrderRequest {
    private String accountKey;
    private String symbol;
    private OrderSide side;
    private BigDecimal quantity;
    private BigDecimal price;
    private String idempotencyKey;

    public PlaceOrderRequest(String accountKey, String symbol, OrderSide side, BigDecimal quantity, BigDecimal price, String idempotencyKey) {
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
    public BigDecimal getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
