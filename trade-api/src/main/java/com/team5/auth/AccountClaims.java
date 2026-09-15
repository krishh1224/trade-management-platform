package com.team5.auth;

import org.springframework.security.oauth2.jwt.Jwt;

public final class AccountClaims {

    public static final String ACCOUNT_ID = "accountId";

    private AccountClaims() {
    }

    // Nimbus parses JSON integers as Long and decimals as Double; only whole, positive ids are accepted.
    static boolean isValidAccountId(Object value) {
        return (value instanceof Long longValue && longValue > 0)
            || (value instanceof Integer intValue && intValue > 0);
    }

    static Long accountIdOrNull(Jwt jwt) {
        Object value = jwt.getClaim(ACCOUNT_ID);
        return isValidAccountId(value) ? ((Number) value).longValue() : null;
    }
}
