package com.team5.notifications.service;

import com.team5.auth.AccountAccessAuthorizer;
import com.team5.notifications.delivery.PushEndpointPolicy;
import com.team5.notifications.exception.InvalidPushSubscriptionException;
import com.team5.notifications.mapper.PushSubscriptionMapper;
import com.team5.notifications.web.PushConfigResponse;
import com.team5.notifications.web.PushSubscriptionRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PushSubscriptionService {

    private final PushSubscriptionMapper subscriptionMapper;
    private final AccountAccessAuthorizer accountAccessAuthorizer;
    private final boolean pushEnabled;
    private final String publicKey;

    public PushSubscriptionService(
        PushSubscriptionMapper subscriptionMapper,
        AccountAccessAuthorizer accountAccessAuthorizer,
        @Value("${notifications.push.enabled:false}") boolean pushEnabled,
        @Value("${notifications.push.vapid-public-key:}") String publicKey
    ) {
        this.subscriptionMapper = subscriptionMapper;
        this.accountAccessAuthorizer = accountAccessAuthorizer;
        this.pushEnabled = pushEnabled;
        this.publicKey = publicKey;
    }

    public PushConfigResponse config(long authenticatedAccountId, long accountId) {
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, accountId);
        boolean enabled = pushEnabled && publicKey != null && !publicKey.isBlank();
        return new PushConfigResponse(enabled, enabled ? publicKey : null);
    }

    public void subscribe(long authenticatedAccountId, long accountId, PushSubscriptionRequest request) {
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, accountId);
        // the endpoint comes from the customer's browser and the platform will POST to it: allowlist only
        if (!PushEndpointPolicy.isAllowedEndpoint(request.endpoint())
            || !PushEndpointPolicy.isValidKeys(request.keys().p256dh(), request.keys().auth())) {
            throw new InvalidPushSubscriptionException();
        }
        subscriptionMapper.upsert(accountId, request.endpoint(), request.keys().p256dh(), request.keys().auth());
    }

    public void unsubscribe(long authenticatedAccountId, long accountId, String endpoint) {
        accountAccessAuthorizer.assertAuthorised(authenticatedAccountId, accountId);
        subscriptionMapper.deleteForAccount(accountId, endpoint);
    }
}
