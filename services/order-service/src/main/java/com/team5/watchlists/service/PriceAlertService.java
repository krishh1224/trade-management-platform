package com.team5.watchlists.service;

import com.team5.watchlists.dto.CreatePriceAlertRequest;
import com.team5.watchlists.dto.PriceAlertListResponse;
import com.team5.watchlists.dto.PriceAlertResponse;

// The customer's side of price alerts. Every method checks the token's account against the account
// addressed (ACC-403) before reading or writing anything.
public interface PriceAlertService {

    PriceAlertListResponse listAlerts(long authenticatedAccountId, long accountId);

    PriceAlertResponse createAlert(long authenticatedAccountId, long accountId, CreatePriceAlertRequest request);

    PriceAlertResponse getAlert(long authenticatedAccountId, long accountId, long alertId);

    void deleteAlert(long authenticatedAccountId, long accountId, long alertId);
}
