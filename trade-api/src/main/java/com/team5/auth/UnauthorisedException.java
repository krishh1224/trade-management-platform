package com.team5.auth;

import com.team5.domain.exception.DomainException;

public class UnauthorisedException extends DomainException {

    public UnauthorisedException() {
        super("AUTH-401", "Unauthorised");
    }
}