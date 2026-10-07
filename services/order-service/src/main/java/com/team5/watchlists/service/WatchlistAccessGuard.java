package com.team5.watchlists.service;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.domain.exception.AccountAccessDeniedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Compares the token's account claim with the account that owns what is being addressed: the
 * account in the path or body, or the owner of a watchlist entry, group or alert named by id. A
 * mismatch is the platform's ACC-403 and is logged, since a customer probing another customer's
 * watchlist is what the security review looks for.
 */
@Component
public class WatchlistAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(WatchlistAccessGuard.class);

    private final AccountAccessAuthorizer authorizer;

    public WatchlistAccessGuard(AccountAccessAuthorizer authorizer) {
        this.authorizer = authorizer;
    }

    public void assertSameAccount(long authenticatedAccountId, Long addressedAccountId) {
        try {
            authorizer.assertAuthorised(authenticatedAccountId, addressedAccountId);
        } catch (AccountAccessDeniedException e) {
            log.warn("Watchlist access denied: token account {} addressed account {}",
                authenticatedAccountId, addressedAccountId);
            throw e;
        }
    }
}
