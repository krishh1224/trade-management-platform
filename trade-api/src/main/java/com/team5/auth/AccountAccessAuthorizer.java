package com.team5.auth;

import com.team5.exception.AccountAccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class AccountAccessAuthorizer {

    public void assertAuthorised(Long authenticatedAccountId, Long addressedAccountId) {
        if (authenticatedAccountId == null || !authenticatedAccountId.equals(addressedAccountId)) {
            throw new AccountAccessDeniedException();
        }
    }
}
