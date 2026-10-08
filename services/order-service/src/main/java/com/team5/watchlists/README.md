# Watchlists and price alerts

A customer keeps named watchlists of instruments, sees the latest market price beside each one, and
can ask to be told when a watched instrument's price crosses a level. Watching needs no position and no
trade. The module lives in the Trade REST API beside orders, portfolio and notifications, with its own
package, routes, tables and Kafka consumer group.

- API contract: [`contracts/watchlists-api.yaml`](../../../../../../../../contracts/watchlists-api.yaml)
- Tables: `watchlists` ([V7](../../../../../../../../databases/postgresql/migrations/V7_add_watchlists.sql)),
  `watchlist_groups` ([V8](../../../../../../../../databases/postgresql/migrations/V8_watchlist_groups.sql)),
  `price_alerts` and `watchlist_quotes` ([V16](../../../../../../../../databases/postgresql/migrations/V16_price_alerts.sql),
  one active alert per level: [V17](../../../../../../../../databases/postgresql/migrations/V17_price_alerts_unique_active.sql))

## How it works

```
market-data ──► MarketDataQuoteListener               (consumer group "watchlists-market-data")
  (QUOTE, keyed      │  parse the platform envelope; price = payload.price, else bid/ask midpoint
   by symbol)        │  PriceAlertEvaluator.evaluate               ── one transaction ──
                     │     watchlist_quotes: first price for the symbol?  → store it, done (SEEDED)
                     │     lock the symbol's row; quote not newer?        → ignore it (STALE)
                     │     store the new price
                     │     price_alerts: ACTIVE, this symbol, threshold between old and new price
                     │        (partial index on (symbol, threshold) WHERE status = 'ACTIVE')
                     │     for each one CrossingRule says was crossed:
                     │        UPDATE ... SET status = 'TRIGGERED' WHERE status = 'ACTIVE'   (once)
                     │        AlertDelivery.deliver(PriceAlert)  ──► notifications module records it
                     │  transaction commits
                     ▼
                   ack.acknowledge()                     ← the offset is committed here

notifications dispatcher (every 5 s) ──► resolves the channel from Customer Preferences ──► sends
```

The listener never calls an email, SMS or push provider and never looks up a customer's channel or
contact details: `AlertDelivery.deliver` inserts a `QUEUED` notification and returns, and the
notifications module's dispatcher decides how it goes out. A slow provider cannot stall a
`market-data` partition.

## Package layout

| Package | Contents |
|---|---|
| `web` | `WatchlistController` (`/api/v1/watchlists`), `PriceAlertController` (`/api/v1/accounts/{id}/price-alerts`) |
| `service` | `WatchlistServiceImpl`, `PriceAlertServiceImpl`, `PriceAlertEvaluator`, `WatchlistAccessGuard` |
| `consumer` | `WatchlistConsumerConfig` (own group, manual ack, DLT) and `MarketDataQuoteListener` |
| `domain` | `CrossingRule`, `PriceAlertStatus`, the row and quote types |
| `mapper` | `WatchlistMapper`, `PriceAlertMapper`, `WatchlistQuoteMapper` (XML in `resources/mapper`) |
| `dto` | Request and response bodies |
| `exception` | `WTL-404` / `WTL-409` exceptions, the alert limit (`VAL-422`), `InvalidQuoteException` (to DLT) |

What it uses from other modules, all in-process Java and none of it their tables:

| From | What | Why |
|---|---|---|
| accounts (`com.team5.service.AccountService`) | `getAccount` | The account exists (`ACC-404`), is the caller's and is `ACTIVE` (`ACC-403`) before anything is written |
| notifications (`com.team5.notifications.api`) | `AlertDelivery`, `PriceAlert`, `AlertDirection` | Handing over a triggered alert |
| platform (`com.team5.kafka`) | `KafkaEnvelope`, `QuotePayload` | The existing `market-data` message format |

## Routes

All behind the bearer token. The token's `accountId` claim must equal the account addressed: the path
`{id}`, the body `accountId`, or the owner of the entry, group or alert named by id. A mismatch is
`ACC-403` (logged by `WatchlistAccessGuard`) and nothing is read or written; an id no account has is
`WTL-404`.

| Method | Path | Result |
|---|---|---|
| POST | `/api/v1/watchlists` | Add an instrument (to the first watchlist unless `watchlistGroupId` is given) |
| GET | `/api/v1/watchlists?groupId=` | The token account's entries with `lastPrice` and `changePct` |
| GET / POST | `/api/v1/watchlists/groups` | List / create named watchlists |
| DELETE | `/api/v1/watchlists/groups/{watchlistGroupId}` | Delete a custom watchlist with its entries and their alerts; `Favourite` is `WTL-409` |
| DELETE | `/api/v1/watchlists/{watchlistId}` | Remove an entry and its alerts |
| GET / POST | `/api/v1/accounts/{id}/price-alerts` | List alerts with `maxActiveAlerts` and `activeAlerts` / create one |
| GET / DELETE | `/api/v1/accounts/{id}/price-alerts/{alertId}` | Read / delete one alert |

There is no route that triggers an alert or sends a notification.

## Crossing, defined

With `previous` the last price this module consumed for the symbol and `current` the new quote:

| Direction | Crossed when |
|---|---|
| `ABOVE` | `previous < threshold` and `current >= threshold` |
| `BELOW` | `previous > threshold` and `current <= threshold` |

`CrossingRule` is the single implementation; the SQL only narrows the candidates to thresholds between
the two prices. A price that stays beyond the threshold is not a crossing. Alerts are **one-shot**: the
first crossing makes the alert `TRIGGERED` with `triggeredAt` and `triggerPrice`, and it is never
evaluated again. The customer sees `ACTIVE` / `TRIGGERED` on the Alerts page, and the message in the
notifications inbox and on their alert channel.

## In the Angular app

- **Watchlist** (`/watchlist`): watchlists, instruments and their live price in the instrument's
  currency (₹ / $), a badge with each instrument's active alert count, and an **Alert** button that
  opens the Alerts page with that instrument chosen (`/alerts?watchlistId=`).
- **Alerts** (`/alerts`): create an alert on any watched instrument (each symbol offered once,
  whichever watchlists hold it), the "n of 20 active" count, and every alert filtered by All / Active /
  Triggered, with its level, the current price, when and at what price it fired, and Delete.

Every price the module returns carries `currency` (the instrument's `instruments.currency`), so the UI
shows the right symbol without guessing from the ticker.

## Tables

- **Owned and written:** `watchlists`, `watchlist_groups`, `price_alerts`, `watchlist_quotes`. Each
  mapper writes only these, enforced by `PriceAlertMapperIntegrationTest.theModulesMappersWriteOnlyItsOwnTables`.
- **Read only:** `instruments` (symbol and name), `market_quote_snapshots` (the executor's snapshot,
  as a price fallback and for `changePct`).
- **Never touched:** `orders`, `portfolio_holdings`, `notifications`, `customer_preferences`, `clients`.

## Configuration

| Property | Default | |
|---|---|---|
| `watchlists.consumer.group-id` | `watchlists-market-data` | Unique to this module |
| `watchlists.consumer.auto-startup` | `true` | `false` in tests (`src/test/resources/config/application.properties`) |
| `watchlists.consumer.retry-interval-ms` / `max-retries` | `1000` / `3` | Then `market-data.DLT` |
| `watchlists.alerts.max-active-per-account` | `20` (`PriceAlertServiceImpl.DEFAULT_MAX_ACTIVE_ALERTS`) | Must be at least 1 |

## Decision log

1. **One-shot alerts.** A crossing triggers the alert once and retires it. Re-arming would need a
   "has the price come back?" state per alert and a new idempotency key per firing; a customer who wants
   the level again creates a new alert, which keeps the history of what fired visible.
2. **"Previous price" is per symbol, not per alert, and stored.** `watchlist_quotes` holds the last price
   consumed per symbol, so evaluating a quote is one row lock, one indexed range query and one update,
   however many alerts exist, and it survives a restart. The consequence: an alert created while the
   price is already beyond its threshold does not fire until the price goes back and crosses it. That is
   the definition of crossing, and it is stated in the contract and the UI.
3. **Replays and redeliveries cannot invent crossings.** A quote whose `eventTime` is not later than the
   stored one is ignored. Without this, re-reading the topic from an earlier offset would measure old
   prices against today's and fire alerts for moves that never happened.
4. **Exactly one hand-off per alert.** Three layers: the `ACTIVE`-only `UPDATE` (a second evaluation
   updates 0 rows and delivers nothing), the per-alert `delivery_id` handed to notifications as its
   idempotency key (`UNIQUE (event_id)` there), and the single transaction around the quote.
5. **If notifications refuses the hand-off, the quote is retried, not dropped.** The exception rolls back
   the alert's state and the stored price together, and the listener does not acknowledge. The
   container retries 3 times, 1 s apart (the notifications consumer's policy), then parks the quote on
   `market-data.DLT`. Because the stored price did not move, the next quote for the symbol still sees
   the crossing if the price is still beyond the threshold, so the alert is late rather than lost. A
   message that can never be a quote (no symbol, no price, no time, bad JSON) goes to the DLT at once
   with an `x-failure-reason` header.
6. **Channel resolution stays in notifications.** `PriceAlert` carries structured fields only; the
   notifications module writes the text and looks up Customer Preferences at send time. This module
   never reads `customer_preferences` or contact details.
7. **An alert hangs off a watchlist entry.** The executor only polls symbols someone holds or watches, so
   an alert on an unwatched symbol would never see a quote. Requiring the entry, and cascading its
   removal to its alerts, keeps every alert on a symbol that is being quoted.
8. **The cap counts `ACTIVE` alerts only (20 by default).** Active alerts are what each quote evaluates,
   so they are what needs bounding; triggered ones cost nothing until the customer deletes them. Two
   concurrent creates are serialised by a transaction-scoped advisory lock per account
   (`pg_advisory_xact_lock`), which touches no row the order path uses; a row lock on `accounts` would
   have contended with trading.
9. **One active alert per level.** An account cannot hold two `ACTIVE` alerts on the same symbol,
   direction and threshold (`1500` = `1500.00`): both would fire on the same quote and the customer
   would get the same message twice. The rule is per symbol rather than per watchlist entry, so the
   same instrument in two watchlists does not get round it. A triggered alert leaves the rule, so the
   level can be set again after it fires. It is checked under the same per-account lock as the cap and
   backed by the partial unique index `uq_price_alerts_active_level` (V17), which first removes any
   duplicates an earlier V16 database holds (all still `ACTIVE`, so nothing they would have sent is
   lost). Refused with `WTL-409` "Price alert already exists".
10. **Favourite cannot be deleted; custom watchlists can.** `Favourite` is where every add without a
    group lands and what the page opens on, so deleting it would only have it recreated empty on the
    next read. Any other watchlist can be deleted; its entries and their alerts go with it through the
    existing `ON DELETE CASCADE`s, and notifications already sent stay in the inbox. Refused with
    `WTL-409` "The Favourite watchlist cannot be deleted" (matched case-insensitively, as names are).
11. **No new error codes.** The catalogue rule is to reuse codes where one fits. The cap is `VAL-422`
   with the message `Price alert limit reached`, as `ACC-403` already carries different messages in the
   trade and portfolio contracts; clients branch on the code, and the UI knows the limit from
   `maxActiveAlerts`. A missing alert is `WTL-404` ("Price alert not found").
12. **Another account's resource is `ACC-403`, not `WTL-404`.** The story asks that a token addressing
    another account's resource gets the platform's `ACC-403`. Before this change, a watchlist entry or
    group belonging to someone else answered `WTL-404`; ownership is now looked up first and compared.
    This confirms that an id exists, which is acceptable here: entry and group ids are random 63-bit
    numbers, and nothing about the resource is returned.
13. **The live price is the module's own.** `lastPrice` on a watchlist entry is the price the module
    last consumed from `market-data`, which is also the price alerts were last evaluated against, so the
    screen and the alerts agree. The executor's `market_quote_snapshots` row is a read-only fallback until
    the first quote for a symbol arrives, and the only source of `changePct` (the `market-data` payload
    the executor publishes carries bid and ask only).
14. **Price = bid/ask midpoint when the quote has no `price`.** The executor publishes only `bid` and
    `ask`; the midpoint to 4 places, half-up, is exactly what it stores as its own snapshot.
15. **Moved into `com.team5.watchlists`.** The Sprint 6 watchlist classes lived in the shared
    `controller` / `service` / `mapper` / `dto` packages and read accounts and instruments through the
    order module's mappers. They were moved here unchanged apart from three fixes: accounts go through
    `AccountService`, instruments through this module's own read-only query, and ownership mismatches
    are `ACC-403`.
16. **Own contract file.** `trade-api.yaml` keeps its Sprint 6 `Watchlist` section unchanged (the
    generated Angular client depends on it). `watchlists-api.yaml` documents the whole module as built,
    in the same way `notifications-api.yaml` and `portfolio-api.yaml` do for theirs.
17. **Alerts have their own page.** Creating, filtering and deleting alerts lives on `/alerts`; the
    Watchlist page only shows how many alerts an instrument has and links there. Triggered alerts are
    not capped and accumulate, so they need the room and the filter the Watchlist page could not give.

## Tests

| Requirement | Test |
|---|---|
| Watchlist created and scoped to the token's account | `WatchlistAuthorizationTest.createdWatchlistAndEntry_areScopedToTheTokenAccount`, `WatchlistServiceImplTest` |
| Watching needs no position or trade | `WatchlistServiceImplTest.testAddToWatchlist_NeedsNoPositionOrOrder` |
| Instrument added and live price shown | `WatchlistAuthorizationTest.addedInstrument_isListedWithItsLivePrice`, `PriceAlertMapperIntegrationTest.watchlistShowsTheConsumedPrice_andTheExecutorSnapshotUntilThereIsOne`, `watchlist.component.spec.ts` |
| A quote short of the threshold does not trigger | `PriceAlertEvaluatorTest.quoteShortOfTheThreshold_doesNotTrigger`, `CrossingRuleTest` |
| Crossing ABOVE triggers | `PriceAlertEvaluatorTest.quoteCrossingAbove_triggersAndIsDeliveredThroughNotifications` |
| Crossing BELOW triggers | `PriceAlertEvaluatorTest.quoteCrossingBelow_triggers` |
| Delivered through notifications, not logged | `PriceAlertEvaluatorTest.quoteCrossingAbove_triggersAndIsDeliveredThroughNotifications` (real notifications service and dispatcher), `PriceAlertMapperIntegrationTest.crossingQuote_triggersTheAlertAndRecordsAPriceAlertNotification` (row in `notifications` on Postgres) |
| No firing on every quote beyond the threshold | `PriceAlertEvaluatorTest.priceStayingBeyondTheThreshold_doesNotFireOnEveryQuote` |
| Replay / redelivery sends nothing twice | `PriceAlertEvaluatorTest.redeliveredOrReplayedQuote_changesNothingAndSendsNothingTwice`, `MarketDataQuoteListenerTest.replayedQuote_isAcknowledgedAndSendsNothingTwice` |
| The same active alert cannot be created twice | `PriceAlertServiceImplTest.createAlert_sameLevelTwice_isRefusedAndNothingIsWritten` (and the other `createAlert_*Duplicate*` cases), `PriceAlertMapperIntegrationTest.sameActiveLevel_isFoundAndRefusedByTheDatabase`, `v17_keepsTheOldestOfDuplicatesAlreadyStored` |
| Custom watchlists can be deleted, Favourite cannot | `WatchlistServiceImplTest.testDeleteWatchlistGroup_*`, `WatchlistAuthorizationTest.delete*Watchlist*`, `PriceAlertMapperIntegrationTest.deletingAWatchlistGroup_removesItsEntriesAndTheirAlerts` |
| Cap per account | `PriceAlertServiceImplTest.createAlert_beyondTheCap_isRejectedAndNothingIsWritten`, `PriceAlertControllerTest.createPriceAlert_atTheCap_422AndNothingWritten` |
| Cross-account access is `ACC-403` | `PriceAlertControllerTest.*_403*`, `WatchlistAuthorizationTest.*_403*` |
| market-data only, own consumer group | `MarketDataQuoteListenerTest.listenerReadsMarketDataOnly_inItsOwnConsumerGroup`, `noListenerInTheModuleSubscribesToOrdersOrTradeEvents` |
| Consumer offsets and failures | `MarketDataQuoteListenerTest` (executor wire format, ack after commit, no ack on failure, DLT reasons) |
| Failed hand-off is retried, not lost | `PriceAlertMapperIntegrationTest.notificationsFailing_rollsTheQuoteBack_soTheCrossingIsFoundOnRetry` |
| Per-quote lookup uses the symbol index | `PriceAlertMapperIntegrationTest.perQuoteLookup_canUseThePartialIndexOnSymbol` |

`PriceAlertMapperIntegrationTest` needs the PostgreSQL from `docker-compose.yml`, like the other mapper
tests; it builds its tables from the migration files in a throwaway schema.

## Known limits

- One consumer instance per partition is assumed by the per-symbol ordering; during a rebalance two
  instances can briefly share a symbol, which the row lock on `watchlist_quotes` serialises.
- `changePct` comes from the executor's snapshot, so it can be a poll behind `lastPrice`.
- A triggered alert cannot be re-armed; create a new one.
