package com.team5.domain.dto;

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

    @Test
    public void validRequest_passesValidation() {
        PlaceOrderRequest req = new PlaceOrderRequest("ACC123", "AAPL", OrderSide.BUY, new BigDecimal("10.5"), new BigDecimal("150.25"), "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty(), "Valid request should have no violations");
    }

    @Test
    public void nullAccountKey_isRejected() {
        PlaceOrderRequest req = new PlaceOrderRequest(null, "AAPL", OrderSide.BUY, new BigDecimal("10.5"), new BigDecimal("150.25"), "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty(), "Null account key should be rejected");
    }

    @Test
    public void nullSymbol_isRejected() {
        PlaceOrderRequest req = new PlaceOrderRequest("ACC123", null, OrderSide.BUY, new BigDecimal("10.5"), new BigDecimal("150.25"), "idemp-key-1");
        Set<ConstraintViolation<PlaceOrderRequest>> violations = validator.validate(req);
        assertFalse(violations.isEmpty(), "Null symbol should be rejected");
    }
}
