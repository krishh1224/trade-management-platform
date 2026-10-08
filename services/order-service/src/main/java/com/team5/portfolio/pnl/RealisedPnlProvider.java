package com.team5.portfolio.pnl;

import java.math.BigDecimal;

/**
 * Lifetime realised profit and loss for an account, booked at sale and expressed in the account's
 * base currency. The P&L module owns the implementation; the portfolio summary only passes the
 * figure through and never derives it from current prices.
 */
public interface RealisedPnlProvider {

    BigDecimal lifetimeRealised(long accountId, String baseCurrency);
}
