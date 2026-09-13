package com.team5.exception;

public class UnauthorisedException extends DomainException {

    public UnauthorisedException() {
        super("AUTH-401", "Unauthorised");
    }
}