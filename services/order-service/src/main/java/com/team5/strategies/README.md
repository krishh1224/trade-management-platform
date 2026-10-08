# Automated strategy execution

A customer configures a rule once and the platform trades it for them. The module evaluates every
enabled strategy against the real-time price stream (`market-data`, S7) and, when a rule fires, places
the order through the Trade REST API's own route, `POST /api/v1/orders` (S6). Nobody confirms it. A
defect here does not draw a wrong number on a screen; it spends a customer's money. So every control
below is checked again on every order, at the moment of the decision, and most are backed by the
database as well.

> In production this would sit behind suitability and risk controls (appropriateness for the
> customer's risk profile, firm-wide exposure, market-hours and price-collar checks). Those are out of
> scope here; the per-strategy limits below are the floor, not the whole of it.

- API contract: [`contracts/strategies-api.yaml`](../../../../../../../../contracts/strategies-api.yaml)
- Tables: `strategies`, `strategy_executions`, `strategy_quotes`
  ([V18](../../../../../../../../databases/postgresql/migrations/V18_strategies.sql))
- Executor (S7) change: `SymbolProvider` also polls the symbols of enabled strategies

## How it works

```
market-data ──► StrategyMarketDataListener            (consumer group "strategies-market-data")
  (QUOTE, keyed      │  parse the platform envelope; skip stale=true quotes
   by symbol)        │  StrategyQuoteRecorder.record          ── own transaction ──
                     │     older than the newest stored?  → STALE, nothing evaluated
                     │     same as the newest stored?     → REDELIVERED, evaluate again
                     │     otherwise append to strategy_quotes (keep 250 per symbol) → NEW
                     │  older than max-quote-age (5 min)? → kept for history, nothing traded
                     │  StrategyEngine: ENABLED strategies on the symbol, recent prices
                     │     for each: StrategySignals.evaluate  (pure: MovingAverageCrossover / BollingerBands)
                     │        fired? → StrategyExecutor.execute       ── one transaction per strategy ──
                     │                   SELECT strategy FOR UPDATE   (a disable now waits)
                     │                   acted on this quote already?        → nothing
                     │                   not ENABLED any more?               → SKIPPED_DISABLED
                     │                   BUY over remaining spend?           → SKIPPED_SPEND_LIMIT
                     │                   BUY over max position (+ pending)?  → SKIPPED_POSITION_LIMIT
                     │                   SELL with nothing left to sell?     → SKIPPED_NO_HOLDING
                     │                   TradeApiOrderClient ──HTTP──► POST /api/v1/orders
                     │                        Bearer <60 s delegated token>     (validation, accountId
                     │                                                          check, idempotency)
                     │                   PLACED → spent += value, failure run = 0
                     │                   FAILED → failure run + 1, HALTED at the limit
                     │                   strategy_executions row
                     │                 commit: the strategy row is released
                     ▼
                   ack.acknowledge()                     ← the offset is committed here
```

## Package layout

| Package | Contents |
|---|---|
| `web` | `StrategyController` (`/api/v1/accounts/{id}/strategies`) |
| `service` | `StrategyService`: create, list, enable, disable, delete, executions |
| `consumer` | `StrategyConsumerConfig` (own group, manual ack, DLT) and `StrategyMarketDataListener` |
| `engine` | `StrategyEngine` (one cycle), `StrategyExecutor` (the controls), `TradeApiOrderClient` (the HTTP call), `StrategyTokenIssuer` (the delegated token), `StrategyQuoteRecorder` |
| `indicators` | `MovingAverageCrossover`, `BollingerBands`, `StrategySignals`: pure functions of a price series |
| `domain`, `dto`, `mapper`, `exception`, `config` | Rows, bodies, MyBatis mappers (XML in `resources/mapper`), `STR-404` / `VAL-422`, `StrategyProperties` |

What it uses from the rest of the service:

| From | What | Why |
|---|---|---|
| Trade REST API (`POST /api/v1/orders`) | over HTTP, never in-process | Every order |
| auth (`com.team5.auth`) | `StrategyTokenProperties`, `AccountAccessAuthorizer`; `SecurityConfig` verifies strategy tokens | Identity |
| accounts (`AccountService.getAccount`) | account exists and is `ACTIVE` | Create and enable |
| platform (`com.team5.kafka`) | `KafkaEnvelope`, `QuotePayload` | The `market-data` format |

## Rules

Both are **crossings**, like price alerts: a rule fires when its condition becomes true, not on every
quote while it stays true. Windows count quotes, not time.

| Type | BUY | SELL | Needs |
|---|---|---|---|
| `MA_CROSSOVER` | `SMA(short)` was at or below `SMA(long)` on the previous price and is above it now (golden cross) | the mirror (death cross) | `longWindow + 1` prices |
| `BOLLINGER_BANDS` | the previous price was at or above its lower band and the current price is below its lower band | the mirror on the upper band | `bandWindow + 2` prices |

A price's band is the mean of the `bandWindow` prices **before** it, plus or minus `bandWidth`
population standard deviations of those prices (decision 8).

## Routes

All behind a **customer** token whose `accountId` equals the path `{id}`; a strategy's own token is
`403 ACC-403` on every one of them.

| Method | Path | Result |
|---|---|---|
| GET / POST | `/api/v1/accounts/{id}/strategies` | List (with `maxStrategies`) / create (ENABLED at once) |
| GET / DELETE | `/api/v1/accounts/{id}/strategies/{strategyId}` | Read / delete |
| POST | `/api/v1/accounts/{id}/strategies/{strategyId}/disable` | Off, effective before the call returns |
| POST | `/api/v1/accounts/{id}/strategies/{strategyId}/enable` | On again (also un-halts, clearing the failure run) |
| GET | `/api/v1/accounts/{id}/strategies/{strategyId}/executions` | The last 50 firings and what happened |
| GET | `/api/v1/accounts/{id}/strategies/{strategyId}/chart` | The stored prices, the rule's lines and where it fired (decision 15) |

There is no route that runs a strategy or places its order.

## Tables

- **Owned and written:** `strategies`, `strategy_executions`, `strategy_quotes`.
- **Read only:** `instruments` (tradable?), `portfolio`, `portfolio_holdings` and `orders` (position and
  pending orders; finding the order behind an idempotency key) — all in `StrategyReadMapper`, all `SELECT`.
- **Never written:** `orders`. An order only ever comes into being through `POST /api/v1/orders`.

## Configuration

| Property | Default | |
|---|---|---|
| `auth.strategy-token.shared-secret` | `STRATEGY_TOKEN_SECRET`; a dev value in the `local` profile | At least 32 bytes, must differ from `JWT_SECRET`. Unset: no strategy order can be placed |
| `auth.strategy-token.ttl` | `60s` | At most `5m`; also the longest lifetime the Trade API accepts |
| `strategies.consumer.group-id` / `auto-startup` | `strategies-market-data` / `true` | `false` in tests |
| `strategies.max-per-account` | `10` | Any status |
| `strategies.price-tolerance` | `0.01` | BUY limit = ask × 1.01, SELL limit = bid × 0.99 |
| `strategies.trade-api-base-url` | `http://localhost:${server.port}` | This service's own HTTP port |
| `strategies.connect-timeout` / `read-timeout` | `2s` / `5s` | The longest a disable can wait on a decision |
| `strategies.decision-delay` | `0s` (`STRATEGIES_DECISION_DELAY`) | Demonstration only, see below |
| `strategies.max-quote-age` | `5m` | Older quotes (a backlog after downtime) are stored, never traded on |

## Decision log

1. **Identity: a delegated, single-order token, not the customer's.** A strategy runs while its
   customer is signed out, so the obvious options are all wrong:
   - *Their access token* expires in 15 minutes, and the strategy runs for weeks.
   - *Their refresh token* is a full customer credential. Holding one lets a background job do anything
     the customer can (withdraw cash, change preferences, cancel orders), and rotating it races the
     customer's own sessions.
   - *A service-wide "automation" token* with no account, or one that can name any account, defeats
     the route's only authorisation rule: that the token's `accountId` matches the order's.
   - *Calling `OrderService` in-process* skips the route's validation, the `accountId` check and the
     controller's after-commit publish. That bypass is the one the brief rules out.

   Instead, every order gets a token minted for it alone. Issuer `strategy-automation`, `sub`
   `strategy:{id}`, `accountId` = the strategy's owner, `scope` `orders:place`, a random `jti`, and a
   lifetime of 60 s. It is signed with `STRATEGY_TOKEN_SECRET`, which is never the customer key; startup
   refuses to run if the two are equal. **Consent** comes from creating the strategy: the customer
   does that with their own token, and the account id is copied from that token onto the strategy.
   Nothing a quote carries can change which account a strategy trades.
   `SecurityConfig` picks the verifier by issuer (`JwtIssuerAuthenticationManagerResolver`). It
   accepts this issuer only on `POST /api/v1/orders`, and only with that scope, a `strategy:` subject
   and an expiry no further away than the ttl. Every other route answers `403 ACC-403`. The token
   cannot read the account, cancel an order, or re-enable or re-limit its own strategy. The route then
   treats it like any customer: same validation, same `accountId` check, same idempotency key, same
   after-commit `ORDER_PLACED`. A leaked token is good for one minute, on one route, for one account.
   *Not chosen:* asking the auth service to mint it. That would put a second service on the critical
   path of every automated order, and auth's verifier is strict about its claim set (`auth-token.service.ts`).
   Minting it here, with a separate key the Trade API alone verifies, keeps the blast radius the same.
2. **Over HTTP, though it is the same process.** `TradeApiOrderClient` calls
   `http://localhost:${server.port}/api/v1/orders`. A loopback call costs a few milliseconds per
   order, and orders come at most once per strategy per quote. In exchange, a strategy order is
   indistinguishable from a customer's at the route, and no change to the order path can forget it.
3. **Every decision is taken under the strategy's row lock.** `StrategyExecutor` begins with
   `SELECT ... FOR UPDATE` on the strategy and re-reads its status there. It never relies on the list
   the engine loaded at the start of the cycle. The customer's disable is an `UPDATE` of the same row,
   so either it commits first and the decision sees `DISABLED` and places nothing, or the decision
   holds the lock and the disable waits for that one order (at most the 5 s read timeout). **When
   disable returns, no further order from that strategy can start**, including later in the same
   cycle. `StrategyMapperIntegrationTest.disableDuringAnOrderDecision_waitsForIt_andNoOrderStartsAfterItReturns`
   shows this on Postgres. The lock is per strategy, so one strategy's slow order holds up no other
   strategy's switch, and the order route itself never touches `strategies`.
4. **Spend is counted at the worst price, before it can happen.** A BUY's value is
   `quantity × limit price`. The executor may fill at a better ask but never a worse one. It must fit
   in `maxSpend - spent`, and the `CHECK (spent <= max_spend)` constraint refuses an update that does
   not, should the check above ever be bypassed. When the Trade API gives no answer (timeout, 5xx), the
   order may exist, so its value stays counted: the strategy may under-spend but never over-spend.
5. **Position counts what is on its way.** `maxPosition` is compared with holdings + unexecuted
   (`NEW`) BUYs + this order. Without the pending BUYs, two quotes a second apart could each pass the
   check before the executor fills either. A SELL is never more than holdings − unexecuted SELLs, and
   one with nothing left is not sent: the Trade API does not check holdings itself (the executor
   rejects later), so this is the only place an impossible SELL is stopped.
6. **Halting: failures in a row, not in total.** A `FAILED` outcome is a refused order (any 4xx other
   than our own duplicate) or no answer. It extends the run, and the run reaching
   `maxConsecutiveFailures` (default 3) moves the strategy to `HALTED` with the reason, in the same
   statement. A placed order clears the run. Limit skips are the controls working, not failures, and
   do not count. Enabling a halted strategy is a customer decision and clears the run. A missing
   `STRATEGY_TOKEN_SECRET` fails every order, so a misconfigured deployment halts its strategies
   rather than retrying forever.
7. **One action per strategy per quote.** The idempotency key is `strat-{strategyId}-{quoteEventId}`,
   and `strategy_executions` is `UNIQUE (strategy_id, quote_event_id)`, checked under the row lock.
   A redelivered quote is evaluated again but changes nothing. If our transaction rolled back after the
   Trade API had committed the order, the retry gets `ORD-409`. The executor then looks the key up in
   `orders` and records the existing order as placed, so an order is never sent twice and never
   lost from the strategy's accounting. An `ORD-409` with no order behind the key is a failure.
8. **Bollinger bands exclude the price being tested.** A single value can be at most `√(window−1)`
   deviations from a mean it is part of. With the price in its own window, a 20-quote, 5-deviation
   band could never be crossed. Measuring each price against the band of the prices before it keeps
   `bandWidth` meaning what a customer expects.
9. **Old prices are never traded on.** A quote older than the newest one consumed for its symbol
   (a replay from an earlier offset) is `STALE` and not evaluated. Quotes flagged `stale` by the
   poller are neither stored nor evaluated. After downtime the consumer group resumes from its last
   committed offset and reads the backlog in order. Every quote in it is stored, so the history has
   no gaps, but a quote older than `strategies.max-quote-age` (5 minutes) when it is consumed is not
   evaluated: a crossover in yesterday's prices must not place an order at today's. A brand-new
   consumer group starts at the **latest** offset (the watchlists group starts at the earliest).
10. **The limit price leaves room for the executor's later quote.** The executor fills a BUY only if
    the limit is at or above the ask *it* fetches when it executes, a few seconds later. A limit at
    our ask would often miss. `price-tolerance` (1%) sets BUYs above the ask and SELLs below the bid.
    The cost of that headroom is counted in decision 4.
11. **Price history is the module's own.** `strategy_quotes` keeps the last 250 prices per symbol
    (the longest rule needs 202), so indicators survive a restart and do not depend on the watchlists
    module's single last price.
12. **The executor polls strategy symbols.** It used to poll only held or watched symbols, so a
    strategy on anything else would never see a quote. `SymbolProvider` now adds the symbols of
    `ENABLED` strategies. If that query fails (a database without V18), it logs and polls the rest,
    so the poller never stops.
13. **A demonstration delay, off by default.** In production the time between a rule firing and the
    decision is milliseconds, too short to show a disable landing mid-cycle by hand.
    `strategies.decision-delay` (e.g. `STRATEGIES_DECISION_DELAY=15s`) pauses *before* the decision
    takes the lock. A disable in that window is seen by the decision, which records `SKIPPED_DISABLED`
    and places nothing. It is bounded at 1 minute and is zero unless set.
14. **No new error codes but one.** `STR-404` (Strategy not found) follows `WTL-404` / `NTF-404`;
    every other refusal reuses `ACC-403`, `ACC-404`, `INS-404` or `VAL-422` with its own message,
    e.g. `Strategy limit reached`.

15. **The chart is computed by the rule's own code.** `GET .../chart` returns the stored prices with
    the lines the rule compares at each one (`IndicatorSeries`, built on the same `Indicators` the
    rules use) and a marker on each quote the rule fired on. The browser draws; it never computes, so
    a crossing on the chart is a crossing the rule saw
    (`IndicatorSeriesTest.theAveragesOnTheChartCrossExactlyWhereTheRuleFires`). Points are evenly
    spaced because the rule counts price updates, not time, and only the 250 stored quotes are drawn.

## Demonstrating a disable mid-cycle

1. Start the Trade API with `STRATEGIES_DECISION_DELAY=15s` (and the executor with a short
   `market-data.poll-interval-seconds`).
2. Create a strategy that will fire on the next quote, e.g. a `BOLLINGER_BANDS` with a narrow
   `bandWidth`, or watch the log for `Strategy N (...) fired BUY ...` and `deciding in 15000 ms`.
3. Disable it on the Strategies page (or `POST .../disable`) inside those 15 seconds.
4. The log shows `Strategy N fired BUY but is DISABLED now; no order`. The Strategies page shows the
   firing as `SKIPPED_DISABLED`. The order history has no new order.

## Tests

| Requirement | Test |
|---|---|
| Strategy triggers on a crossover condition | `MovingAverageCrossoverTest.shortAverageCrossingAboveTheLong_firesBuy` / `...BelowTheLong_firesSell`, `BollingerBandsTest.priceCrossing*`, `StrategyEngineTest.quoteCompletingACrossover_triggersTheStrategy` |
| No action when conditions are not met | `MovingAverageCrossoverTest.shortAverageStaying*_firesNothing`, `averagesMeetingWithoutCrossing_firesNothing`, `tooFewPrices_*`, `BollingerBandsTest.priceInsideTheBands_firesNothing`, `StrategyEngineTest.quoteWithoutACrossover_takesNoAction`, `notEnoughHistoryYet_takesNoAction` |
| Order placed through the Trade API on trigger | `StrategyOrderThroughTradeApiTest.triggeredStrategy_placesItsOrderThroughPostApiV1Orders` (executor → HTTP → real `SecurityConfig` → real `OrderController`), `TradeApiOrderClientTest.postsToTheTradeApisOrderRoute_withAShortLivedDelegatedToken`, `StrategyExecutorTest.firedBuy_placesTheOrderThroughTheTradeApi_forTheStrategysAccount` |
| Fires once, not on every quote | `MovingAverageCrossoverTest.aTrendAfterTheCross_firesOnceNotOnEveryQuote`, `BollingerBandsTest.priceStayingBelowTheBand_doesNotFireAgain` |
| Disabled mid-cycle places nothing | `StrategyExecutorTest.strategyDisabledAfterTheCycleLoadedIt_placesNothing`, `StrategyMapperIntegrationTest.disableDuringAnOrderDecision_waitsForIt_andNoOrderStartsAfterItReturns` |
| Max spend | `StrategyExecutorTest.buyOverTheRemainingSpend_placesNothing`, `buyExactlyUsingUpTheSpend_isPlaced`, `noAnswerFromTheTradeApi_...CountsTheValueAgainstTheLimit`, `StrategyMapperIntegrationTest.spendingPastTheCap_isRefusedByTheDatabaseToo` |
| Max position | `StrategyExecutorTest.buyPastTheMaxPosition_countingPendingBuys_placesNothing`, `sell_neverMoreThanIsLeftToSell`, `sellWithNothingHeld_placesNothing`, `StrategyMapperIntegrationTest.position_isHoldingsPlusUnexecutedOrders` |
| Stop after repeated failures | `StrategyExecutorTest.failureReachingTheLimit_haltsTheStrategy`, `rejectedOrder_isAFailure_thatDoesNotHaltBeforeTheLimit`, `StrategyMapperIntegrationTest.failuresInARow_haltTheStrategyAtItsLimit`, `anOrderPlaced_clearsTheFailureRun` |
| Strategy token confined to placing orders | `StrategyOrderThroughTradeApiTest.strategyToken_isRefusedOnEveryRouteButPlacingAnOrder`, `strategyClaims_signedWithTheCustomerKey_areRejected`, `customerIssuer_signedWithTheStrategyKey_isRejected`, `strategyTokenWithoutTheOrdersScope_isRejected`, `strategyTokenLivingLongerThanItsTtl_isRejected`, `SecurityConfigTest.strategyKeyEqualToTheCustomerKeyFailsStartup` |
| Never twice for one quote | `StrategyExecutorTest.quoteAlreadyActedOn_placesNothingTwice`, `duplicateKey_*`, `StrategyMapperIntegrationTest.executionsAreUniquePerStrategyAndQuote` |
| market-data only, own consumer group; S7 wire format | `StrategyMarketDataListenerTest` |
| Backlog after downtime is not traded on | `StrategyEngineTest.quoteFromABacklogAfterDowntime_isStoredButNotTradedOn` |
| The chart agrees with the decisions | `IndicatorSeriesTest`, `StrategyChartServiceTest`, `strategy-chart.component.spec.ts` |
| Executor polls strategy symbols | `SymbolProviderTest.testStrategySymbols_ArePolledAndDeduplicated`, `testStrategySymbolsUnavailable_StillPollsTheRest` (executor-service) |

`StrategyMapperIntegrationTest` needs the PostgreSQL from `docker-compose.yml`, like the other mapper
tests. It builds its tables from `V18_strategies.sql` in a throwaway schema.

## Known limits

- **Outcomes after placement are not fed back.** A placed order that the executor later `REJECTED`
  (insufficient cash, non-marketable at its own quote) counted as placed and spent. Feeding
  `trade-events` back into the failure run is the natural next step.
- **No notification on halt.** A halted strategy shows `HALTED` and its reason in the UI and the log;
  handing it to the notifications module is not done yet.
- **Limits cannot be edited.** Delete and recreate; `spent` starts again at zero for the new strategy.
- Windows count quotes, so a strategy's horizon depends on the executor's poll interval.
- One consumer instance per partition is assumed for per-symbol order; the strategy row lock keeps
  decisions correct even if two instances briefly share a symbol during a rebalance.
