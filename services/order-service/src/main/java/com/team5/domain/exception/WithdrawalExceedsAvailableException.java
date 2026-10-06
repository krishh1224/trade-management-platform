package com.team5.domain.exception;

public class WithdrawalExceedsAvailableException extends DomainException {

    public WithdrawalExceedsAvailableException() {
        super("CSH-400", "Withdrawal exceeds available funds");
    }
}
