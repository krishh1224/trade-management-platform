package com.team5.auth;

import com.team5.domain.exception.AccountNotActiveException;
import org.springframework.stereotype.Component;

@Component
public class AccountAccessAuthorizer {

    public void assertAuthorised(Long authenticatedAccountId, Long addressedAccountId) {
        if (authenticatedAccountId == null || addressedAccountId == null) {
            throw new AccountNotActiveException();
        }
        if (!authenticatedAccountId.equals(addressedAccountId)) {
            throw new AccountNotActiveException();
        }
    }
}