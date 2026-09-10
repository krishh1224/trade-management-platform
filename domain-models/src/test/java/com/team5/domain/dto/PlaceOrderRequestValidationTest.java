package com.team5.domain.dto;

import com.team5.domain.enums.OrderSide;
import com.team5.dto.PlaceOrderRequest;
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

    @Test
    public void validRequest_passesValidation() {
        PlaceOrderRequest req = new PlaceOrderRequest("ACC123", "AAPL", OrderSide.BUY, 10, new BigDecimal("150.25"), "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty(), "Valid request should have no violations");
    }

    @Test
    public void nullAccountKey_isRejected() {
        PlaceOrderRequest req = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, 10, new BigDecimal("150.25"), "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty(), "Null account key should be rejected");
    }

    @Test
    public void nullSymbol_isRejected() {
        PlaceOrderRequest req = new PlaceOrderRequest("ACC123", null, OrderSide.BUY, 10, new BigDecimal("150.25"), "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty(), "Null symbol should be rejected");
    }

    @Test
    public void nullSide_isRejected() {
        PlaceOrderRequest req = new PlaceOrderRequest("ACC123", "AAPL", null, 10, new BigDecimal("150.25"), "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty(), "Null side should be rejected");
    }

    @Test
    public void zeroQuantity_isRejected() {
        PlaceOrderRequest req = new PlaceOrderRequest("ACC123", "AAPL", OrderSide.BUY, 0, new BigDecimal("150.25"), "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty(), "Zero quantity should be rejected");
    }

    @Test
    public void negativeQuantity_isRejected() {
        PlaceOrderRequest req = new PlaceOrderRequest("ACC123", "AAPL", OrderSide.BUY, -1, new BigDecimal("150.25"), "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty(), "Negative quantity should be rejected");
    }

    @Test
    public void positiveQuantity_isAccepted() {
        PlaceOrderRequest req = new PlaceOrderRequest("ACC123", "AAPL", OrderSide.BUY, 1, new BigDecimal("150.25"), "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty(), "Positive quantity should be accepted");
    }

    @Test
    public void nullPrice_isRejected() {
        PlaceOrderRequest req = new PlaceOrderRequest("ACC123", "AAPL", OrderSide.BUY, 10, null, "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty(), "Null price should be rejected");
    }

    @Test
    public void zeroPrice_isRejected() {
        PlaceOrderRequest req = new PlaceOrderRequest("ACC123", "AAPL", OrderSide.BUY, 10, BigDecimal.ZERO, "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty(), "Zero price should be rejected");
    }

    @Test
    public void negativePrice_isRejected() {
        PlaceOrderRequest req = new PlaceOrderRequest("ACC123", "AAPL", OrderSide.BUY, 10, new BigDecimal("-0.01"), "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty(), "Negative price should be rejected");
    }

    @Test
    public void slightlyPositivePrice_isAccepted() {
        PlaceOrderRequest req = new PlaceOrderRequest("ACC123", "AAPL", OrderSide.BUY, 10, new BigDecimal("0.01"), "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty(), "Slightly positive price should be accepted");
    }

    @Test
    public void nullIdempotencyKey_isRejected() {
        PlaceOrderRequest req = new PlaceOrderRequest("ACC123", "AAPL", OrderSide.BUY, 10, new BigDecimal("150.25"), null);
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty(), "Null idempotency key should be rejected");
    }
}
