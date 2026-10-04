package com.team5.advisor.web;

import com.team5.advisor.chat.AdvisorException;
import com.team5.advisor.web.ChatDtos.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/** Every failure leaves as the contract's ErrorResponse; no stack traces or internal details. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AdvisorException.class)
    ResponseEntity<ErrorResponse> advisor(AdvisorException e) {
        return ResponseEntity.status(e.getStatus()).body(new ErrorResponse(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HandlerMethodValidationException.class,
        HttpMessageNotReadableException.class,
        HttpMediaTypeNotSupportedException.class
    })
    ResponseEntity<ErrorResponse> invalid(Exception e) {
        AdvisorException invalid = AdvisorException.invalidInput();
        return ResponseEntity.status(invalid.getStatus())
            .body(new ErrorResponse(invalid.getErrorCode(), invalid.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception e) {
        if (e instanceof org.springframework.web.ErrorResponse webError) {
            // Spring's own web errors (unknown path, wrong method) keep their status, with no body.
            return ResponseEntity.status(webError.getStatusCode()).build();
        }
        log.error("Unexpected advisor failure", e);
        AdvisorException unavailable = AdvisorException.unavailable();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse(unavailable.getErrorCode(), unavailable.getMessage()));
    }
}
