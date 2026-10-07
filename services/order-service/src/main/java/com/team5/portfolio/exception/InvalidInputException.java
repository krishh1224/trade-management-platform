package com.team5.portfolio.exception;

import com.team5.domain.exception.DomainException;

/** The request failed field validation, for example {@code from} later than {@code to}. */
public class InvalidInputException extends DomainException {

    public InvalidInputException() {
        super("VAL-422", "Invalid input");
    }
}
