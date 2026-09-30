package com.team5.domain.exception;

import com.team5.dto.response.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final Set<String> NOT_FOUND_CODES = Set.of("ACC-404", "INS-404", "WTL-404");

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(DomainException exception) {
        HttpStatus status = mapHttpStatus(exception.getCode());
        log.warn("Domain rule rejected the request: code={}, message={}",
            exception.getCode(), exception.getMessage());
        return ResponseEntity.status(status)
            .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException exception) {
        log.warn("Order lookup failed: code={}, message={}", exception.getCode(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse(exception.getCode(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        log.warn("Request body failed validation: {}", exception.getMessage());
        return ResponseEntity.unprocessableEntity()
            .body(new ErrorResponse("VAL-422", "Invalid input"));
    }

    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleConstraintViolation(Exception exception) {
        log.warn("Request parameter failed validation: {}", exception.getMessage());
        return ResponseEntity.unprocessableEntity()
            .body(new ErrorResponse("VAL-422", "Invalid input"));
    }

    private HttpStatus mapHttpStatus(String errorCode) {
        if ("AUTH-401".equals(errorCode)) {
            return HttpStatus.UNAUTHORIZED;
        }
        if ("ACC-403".equals(errorCode)) {
            return HttpStatus.FORBIDDEN;
        }
        if (NOT_FOUND_CODES.contains(errorCode)) {
            return HttpStatus.NOT_FOUND;
        }
        if ("ORD-400".equals(errorCode)) {
            return HttpStatus.BAD_REQUEST;
        }
        if ("ORD-409".equals(errorCode)) {
            return HttpStatus.CONFLICT;
        }
        if ("WTL-409".equals(errorCode)) {
            return HttpStatus.CONFLICT;
        }
        log.error("Unmapped domain error code reached ApiExceptionHandler: {}", errorCode);
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}