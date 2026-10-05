package com.team5.advisor.auth;

import org.springframework.security.oauth2.jwt.Jwt;

/** The JWT `accountId` claim is the only source of the account the advisor may read. */
public final class AccountClaims {

    public static final String ACCOUNT_ID = "accountId";

    private AccountClaims() {
    }

    static boolean isValidAccountId(Object value) {
        return (value instanceof Long longValue && longValue > 0)
            || (value instanceof Integer intValue && intValue > 0);
    }

    /** The token's account id; tokens without a valid one never pass the decoder's validators. */
    public static long accountId(Jwt jwt) {
        Object value = jwt.getClaim(ACCOUNT_ID);
        if (!isValidAccountId(value)) {
            throw new IllegalStateException("Authenticated token has no valid accountId claim");
        }
        return ((Number) value).longValue();
    }
}
