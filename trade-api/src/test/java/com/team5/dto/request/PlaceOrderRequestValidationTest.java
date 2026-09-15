package com.team5.dto.request;

import com.team5.domain.enums.OrderSide;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

public class PlaceOrderRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    public static void setUpValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private static PlaceOrderRequest validRequest() {
        PlaceOrderRequest req = new PlaceOrderRequest();
        req.setAccountId(123L);
        req.setSymbol("AAPL");
        req.setSide(OrderSide.BUY);
        req.setQuantity(10);
        req.setPrice(new BigDecimal("150.25"));
        req.setIdempotencyKey("idemp-key-1");
        return req;
    }

    private static Set<ConstraintViolation<PlaceOrderRequest>> validate(PlaceOrderRequest req) {
        return validator.validate(req);
    }

    @Test
    public void validRequest_passesValidation() {
        assertTrue(validate(validRequest()).isEmpty(), "Valid request should have no violations");
    }

    @Test
    public void nullAccountId_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setAccountId(null);
        assertFalse(validate(req).isEmpty(), "Null account id should be rejected");
    }

    @Test
    public void zeroAccountId_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setAccountId(0L);
        assertFalse(validate(req).isEmpty(), "Account id below 1 should be rejected");
    }

    @Test
    public void nullSymbol_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setSymbol(null);
        assertFalse(validate(req).isEmpty(), "Null symbol should be rejected");
    }

    @Test
    public void blankSymbol_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setSymbol("   ");
        assertFalse(validate(req).isEmpty(), "Blank symbol should be rejected");
    }

    @Test
    public void oversizedSymbol_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setSymbol("A".repeat(21));
        assertFalse(validate(req).isEmpty(), "Symbol longer than 20 characters should be rejected");
    }

    @Test
    public void nullSide_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setSide(null);
        assertFalse(validate(req).isEmpty(), "Null side should be rejected");
    }

    @Test
    public void nullQuantity_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setQuantity(null);
        assertFalse(validate(req).isEmpty(), "Null quantity should be rejected");
    }

    @Test
    public void zeroQuantity_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setQuantity(0);
        assertFalse(validate(req).isEmpty(), "Zero quantity should be rejected");
    }

    @Test
    public void negativeQuantity_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setQuantity(-1);
        assertFalse(validate(req).isEmpty(), "Negative quantity should be rejected");
    }

    @Test
    public void positiveQuantity_isAccepted() {
        PlaceOrderRequest req = validRequest();
        req.setQuantity(1);
        assertTrue(validate(req).isEmpty(), "Positive quantity should be accepted");
    }

    @Test
    public void nullPrice_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setPrice(null);
        assertFalse(validate(req).isEmpty(), "Null price should be rejected");
    }

    @Test
    public void zeroPrice_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setPrice(BigDecimal.ZERO);
        assertFalse(validate(req).isEmpty(), "Zero price should be rejected");
    }

    @Test
    public void negativePrice_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setPrice(new BigDecimal("-0.01"));
        assertFalse(validate(req).isEmpty(), "Negative price should be rejected");
    }

    @Test
    public void slightlyPositivePrice_isAccepted() {
        PlaceOrderRequest req = validRequest();
        req.setPrice(new BigDecimal("0.01"));
        assertTrue(validate(req).isEmpty(), "Slightly positive price should be accepted");
    }

    @Test
    public void priceWithTooManyDecimals_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setPrice(new BigDecimal("150.255"));
        assertFalse(validate(req).isEmpty(), "Price with more than 2 decimal places should be rejected");
    }

    @Test
    public void nullIdempotencyKey_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setIdempotencyKey(null);
        assertFalse(validate(req).isEmpty(), "Null idempotency key should be rejected");
    }

    @Test
    public void shortIdempotencyKey_isRejected() {
        PlaceOrderRequest req = validRequest();
        req.setIdempotencyKey("short");
        assertFalse(validate(req).isEmpty(), "Idempotency key shorter than 8 characters should be rejected");
    }
}
