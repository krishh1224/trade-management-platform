package com.team5.domain.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.team5.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void unauthorisedExceptionMapsTo401() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(new UnauthorisedException());

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("AUTH-401", response.getBody().errorCode());
    }

    @Test
    void accountNotActiveExceptionMapsTo403() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(new AccountNotActiveException());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("ACC-403", response.getBody().errorCode());
    }

    @Test
    void accountAccessDeniedExceptionMapsTo403() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(new AccountAccessDeniedException());

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("ACC-403", response.getBody().errorCode());
    }

    @Test
    void accountNotFoundExceptionMapsTo404() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(new AccountNotFoundException());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("ACC-404", response.getBody().errorCode());
    }

    @Test
    void instrumentNotFoundExceptionMapsTo404() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(new InstrumentNotFoundException());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("INS-404", response.getBody().errorCode());
    }

    @Test
    void insufficientFundsExceptionMapsTo400() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(new InsufficientFundsException());

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("ORD-400", response.getBody().errorCode());
    }

    @Test
    void insufficientHoldingsExceptionMapsTo409() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(new InsufficientHoldingsException());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("ORD-409", response.getBody().errorCode());
    }

    @Test
    void duplicateOrderExceptionMapsTo409() {
        ResponseEntity<ErrorResponse> response = handler.handleDomainException(new DuplicateOrderException());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("ORD-409", response.getBody().errorCode());
    }

    @Test
    void unmappedDomainErrorCodeFallsBackTo422() {
        DomainException unmapped = new DomainException("XYZ-999", "Something unexpected") {
        };

        ResponseEntity<ErrorResponse> response = handler.handleDomainException(unmapped);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("XYZ-999", response.getBody().errorCode());
    }

    @Test
    void orderNotFoundExceptionAlwaysMapsTo404RegardlessOfItsCode() {
        ResponseEntity<ErrorResponse> response = handler.handleOrderNotFound(new OrderNotFoundException());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("ORD-409", response.getBody().errorCode());
        assertEquals("Order not found", response.getBody().message());
    }

    @Test
    void constraintViolationExceptionMapsTo422() {
        ResponseEntity<ErrorResponse> response =
            handler.handleConstraintViolation(new ConstraintViolationException(Collections.emptySet()));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("VAL-422", response.getBody().errorCode());
    }

    @Test
    void methodArgumentTypeMismatchExceptionMapsTo422() {
        MethodArgumentTypeMismatchException exception =
            new MethodArgumentTypeMismatchException("abc", Long.class, "id", null, null);

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(exception);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertEquals("VAL-422", response.getBody().errorCode());
    }
}
