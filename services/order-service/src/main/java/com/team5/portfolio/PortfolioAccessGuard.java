package com.team5.portfolio;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.exception.AccountAccessDeniedException;
import com.team5.portfolio.exception.PortfolioAccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Compares the token's account claim with the account in the path. Every portfolio route calls this
 * before it reads anything, and a mismatch is logged: a customer probing another customer's
 * portfolio is exactly what the security review looks for.
 */
@Component
public class PortfolioAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(PortfolioAccessGuard.class);

    private final AccountAccessAuthorizer authorizer;

    public PortfolioAccessGuard(AccountAccessAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    public void assertSameAccount(long authenticatedAccountId, long accountId) {
        try {
            authorizer.assertAuthorised(authenticatedAccountId, accountId);
        } catch (AccountAccessDeniedException e) {
            log.warn("Portfolio access denied: token account {} requested account {}",
                authenticatedAccountId, accountId);
            throw new PortfolioAccessDeniedException();
        }
    }
}
