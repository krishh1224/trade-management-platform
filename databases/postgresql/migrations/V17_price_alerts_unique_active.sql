-- One ACTIVE price alert per account, symbol, direction and threshold (Watchlists module).
--
-- Two identical active alerts would fire on the same quote and send the customer the same message
-- twice, so the second is refused (WTL-409 "Price alert already exists"). The rule is per symbol, not
-- per watchlist entry: the same instrument in two watchlists still gets one alert at a given level.
-- Triggered alerts are outside the index, so the level can be watched again once an alert has fired.
--
-- Databases that ran V16 before this rule may already hold duplicates. They are all still ACTIVE, so
-- none has fired or produced a notification; the oldest of each set is kept and the copies removed,
-- otherwise the unique index below could not be built.

DELETE FROM price_alerts duplicate
USING price_alerts kept
WHERE duplicate.status = 'ACTIVE'
  AND kept.status = 'ACTIVE'
  AND duplicate.account_id = kept.account_id
  AND duplicate.symbol = kept.symbol
  AND duplicate.direction = kept.direction
  AND duplicate.threshold = kept.threshold
  AND duplicate.alert_id > kept.alert_id;

CREATE UNIQUE INDEX IF NOT EXISTS uq_price_alerts_active_level
    ON price_alerts (account_id, symbol, direction, threshold)
    WHERE status = 'ACTIVE';
