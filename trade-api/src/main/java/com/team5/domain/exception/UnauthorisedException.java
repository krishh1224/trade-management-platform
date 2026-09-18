package com.team5.domain.exception;

public class UnauthorisedException extends DomainException {

    public UnauthorisedException() {
        super("AUTH-401", "Unauthorised");
    }
}