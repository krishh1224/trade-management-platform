package com.team5.auth;

import com.team5.exception.UnauthorisedException;
import jakarta.servlet.http.HttpServletRequest;

public final class RequestAuthContext {

    public static final String AUTHENTICATED_ACCOUNT_ID = "authenticatedAccountId";

    private RequestAuthContext() {
    }

    public static Long requireAuthenticatedAccountId(HttpServletRequest request) {
        Object value = request.getAttribute(AUTHENTICATED_ACCOUNT_ID);
        if (value instanceof Long accountId) {
            return accountId;
        }
        throw new UnauthorisedException();
    }
}