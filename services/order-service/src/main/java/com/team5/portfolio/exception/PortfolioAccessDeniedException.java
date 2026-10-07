package com.team5.portfolio.exception;

import com.team5.domain.exception.DomainException;

// The portfolio contract words a token/account mismatch as "Account not accessible".
public class PortfolioAccessDeniedException extends DomainException {

    public PortfolioAccessDeniedException() {
        super("ACC-403", "Account not accessible");
    }
}
