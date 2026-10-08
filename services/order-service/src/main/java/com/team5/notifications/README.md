# Customer notifications

Turns order outcomes on `trade-events` (`ORDER_FILLED`, `ORDER_REJECTED`, `ORDER_CANCELLED`) and
price alerts raised by watchlists into a message delivered on the channel the customer holds in
Customer Preferences, and keeps that history readable by the owning account only.

- API contract: [`contracts/notifications-api.yaml`](../../../../../../../../contracts/notifications-api.yaml)
- Table: [`databases/postgresql/migrations/V13_notifications.sql`](../../../../../../../../databases/postgresql/migrations/V13_notifications.sql)

## How it works

```
trade-events ──► TradeEventNotificationListener      (consumer group "notifications")
                   │  parse envelope (executor values are double-encoded JSON; unwrapped once)
                   │  NotificationService.recordTradeEvent
                   │     INSERT ... ON CONFLICT (event_id) DO NOTHING   → status QUEUED
                   │  transaction commits
                   ▼
                 ack.acknowledge()                    ← the offset is committed here

watchlists ────► AlertDelivery.deliver(PriceAlert)    (in-process Java call, same INSERT)

every 5 s  ────► NotificationDispatcher
                   │  for each QUEUED / HELD row:
                   │     CustomerPreferencesLookup.findAlertChannel(accountId)   ← on every send
                   │       empty  → HELD     (looked at again next pass)
                   │       NONE   → SKIPPED  (channel NONE recorded, nothing outbound)
                   │       X      → ChannelSender[X].send()
                   │                  ok   → SENT,   channel X, sent_at
                   │                  fail → FAILED, channel X, last_error
                   ▼
                 DeliveryLedger: writes status, channel, sent_at / last_error onto the row
```

The Kafka offset says what has been **read**; the `status` column says what has **reached the
customer**. They are separate on purpose: the listener never calls a provider, so an email outage
delays the dispatcher loop but never stalls a `trade-events` partition.

## Package layout

| Package | Contents |
|---|---|
| `api` | `AlertDelivery` and `PriceAlert`: the interface watchlists calls. Not an HTTP route |
| `consumer` | Kafka consumer factory (manual ack, own group, DLT) and the `trade-events` listener |
| `delivery` | Dispatcher, `DeliveryLedger`, one `ChannelSender` per channel |
| `domain` | `Notification`, `NotificationStatus`, `NotificationType` |
| `exception` | `NotificationNotFoundException` (`NTF-404`), `InvalidNotificationEventException` (to DLT) |
| `mapper` | MyBatis mapper (`resources/mapper/NotificationMapper.xml`) |
| `service` | Recording, history, and `MessageFormatter` |
| `web` | `NotificationController` and its response DTO |

It depends on `com.team5.preferences.CustomerPreferencesLookup`, the in-process interface the
preferences module publishes.

## The table

One table, `notifications`. Only what the inbox, the dispatcher or an investigation reads is stored.

| Column | Why it is kept |
|---|---|
| `notification_id` | Key used by the inbox routes |
| `event_id` | `UNIQUE`: the idempotency key that makes a replay a no-op |
| `account_id` | Owner; every query filters on it |
| `type` | Drives the title and the inbox colour. The title is derived from it (`NotificationType.title()`), not stored |
| `order_id` | Which order the message is about; null for a price alert |
| `message` | The text the customer reads |
| `channel` | The channel resolved from preferences at send time: the acceptance criterion as a record |
| `status` | `QUEUED` / `HELD` / `SENT` / `FAILED` / `SKIPPED` |
| `last_error` | Why a `FAILED` send failed. Null for every other status |
| `created_at` | Inbox order; when the event was recorded |
| `sent_at` | When it went out, which for a `HELD` notification can be hours after `created_at` |
| `read_at` | Inbox read state and the header bell's unread count |

Deliberately not stored: a per-attempt log (there is at most one attempt per notification, since
`FAILED` is final), an attempt counter, an `updated_at`, and a title per row.

## Routes

All under `/api/v1/accounts/{id}/notifications`, all behind the bearer token the Trade REST API
verifies, and all compare the token's `accountId` claim with `{id}` before reading anything.

| Method | Path | Result |
|---|---|---|
| GET | `/` `?status=&limit=` | Inbox, newest first. `ACC-403` for another account's history |
| GET | `/{notificationId}` | One notification. `NTF-404` if it is not this account's |
| PUT | `/{notificationId}/read` | Marks it read (idempotent) |

## Decisions

| Question | Decision | Why |
|---|---|---|
| When is the offset committed? | After the `QUEUED` row commits, before any send | A provider's bad afternoon must not back up every account on the partition |
| What is the idempotency key? | The envelope's `eventId` (a watchlist alert's `alertId`), under `UNIQUE (event_id)` | A unique constraint, not a read-then-write check, so two concurrent redeliveries cannot both insert |
| Where does the channel come from? | `CustomerPreferencesLookup`, called by the dispatcher for every notification it sends | A preference changed a second ago is the one used; the resolved channel is stored on the row (`channel`) |
| No preference stored? | **Hold** (`HELD`) | Not lost: it is in the inbox at once and goes out on the next pass after a preference is stored. A default channel would send on a channel the customer never chose |
| Preference is `NONE`? | `SKIPPED`, channel `NONE` | The customer opted out of outbound messages; the inbox still shows it |
| Can a customer trigger a notification? | No | Alert delivery is the Java interface `AlertDelivery`. There is no create/send route, so nobody can send themselves arbitrary content |
| Destinations | Fixed per channel type on the server | No webhook, URL or address field anywhere in this API or in preferences, so there is nothing to forge a request with (SSRF) |
| Message content | Written here from symbol, side, quantity, price and a reason code | Anything that does not match those shapes is dropped, so a credential, name, address or card number cannot reach a customer through this module. Watchlists sends structured fields, not text |
| Which channels are real? | `BROWSER` is the inbox itself. `EMAIL` sends real email over SMTP when `NOTIFICATIONS_EMAIL_ENABLED=true`; `PUSH` shows real browser pop-ups (Web Push) when `NOTIFICATIONS_PUSH_ENABLED=true`; each logs otherwise. `SMS` is a logging stub | The routing decision and the recorded channel are real for every channel |
| Retry of a failed send | None: `FAILED` is final | Retry with backoff is out of scope for the week |

## Real email (EMAIL channel)

`SmtpEmailSender` replaces `LoggingEmailSender` when `notifications.email.enabled=true`
(`NOTIFICATIONS_EMAIL_ENABLED` in `.env`). Exactly one of the two exists, chosen at startup.

- **Recipient:** `clients.email` for the notification's own `account_id`, looked up on the server.
  Nothing a caller sends can change where an email goes. No valid address means `FAILED` with
  `last_error = "No valid email address on file"`.
- **Content:** subject `Artha: <title>`; plain-text body is the notification's message (built only from
  structured fields), a link to the inbox, and why the customer is getting it.
- **Failures:** a provider error becomes `FAILED` with a short reason in `last_error`. Connect, read and
  write timeouts are 5 s, so a dead provider cannot hold up the dispatcher.
- **Logs:** notification id and a masked address (`j***@gmail.com`) only.
- **Startup:** with email enabled and `MAIL_USERNAME`/`MAIL_PASSWORD` blank, the API refuses to start.

Setup with Gmail: turn on 2-Step Verification, create an App Password for "Mail", then in the `.env` the
Trade REST API loads set `NOTIFICATIONS_EMAIL_ENABLED=true`, `MAIL_USERNAME=<gmail>`,
`MAIL_PASSWORD=<app password>`. The seed data's addresses are `@example.com`; for a demo, change one
account's `clients.email` in your local database only, never in `seed-data.sql`.

## Browser push (PUSH channel)

`WebPushSender` replaces `LoggingPushSender` when `notifications.push.enabled=true`
(`NOTIFICATIONS_PUSH_ENABLED`). It sends a pop-up through the browser vendor's push service to every
browser the account enabled push on; the service worker (`frontend/trade-ui/public/push-sw.js`) shows
it, and clicking it opens the inbox.

- **Subscribing:** Profile → Alert channel Push → "Enable push on this browser". The browser asks for
  permission, subscribes with the platform's VAPID public key, and the subscription is stored in
  `push_subscriptions` (V14) through `PUT /api/v1/accounts/{id}/push-subscription`.
- **SSRF:** the subscription endpoint comes from the browser and the platform POSTs to it, so
  `PushEndpointPolicy` allows only `https` on the push services (`fcm.googleapis.com`,
  `*.notify.windows.com`, `updates.push.services.mozilla.com`, `web.push.apple.com`), default port,
  no credentials. It is checked when stored and again before every send.
- **Content:** title `Artha: <title>`, body = the notification's message. Encrypted for the browser
  (RFC 8291, aes128gcm) and signed with VAPID (RFC 8292) by the `web-push` library.
- **Outcomes:** delivered to at least one browser → `SENT`. No browser enabled → `FAILED`
  ("Push is not enabled on any browser for this account"). A push service answering 404/410 means the
  browser unsubscribed: its row is deleted. 10 s timeout per browser.
- **Keys:** `PUSH_VAPID_PUBLIC_KEY` / `PUSH_VAPID_PRIVATE_KEY` / `PUSH_VAPID_SUBJECT` in `.env`; the
  command to generate them is in `.env.example`. With push enabled and no keys, the API refuses to start.
- **Where it works:** Chrome or Edge on `http://localhost` or `https` (browsers refuse push on plain
  http elsewhere). Windows notifications must be allowed for the browser.

## Event shapes it accepts

The producers on `trade-events` do not agree on one payload, so `TradeOutcomePayload` is tolerant:

| Event | Producer | Carries |
|---|---|---|
| `ORDER_FILLED` | Trade Executor | `orderId` (number), `accountId`, `symbol`, `side`, `quantity`, `executedPrice` |
| `ORDER_REJECTED` | Trade Executor | `orderId` (number), `accountId`, `rejectionReason` only |
| `ORDER_CANCELLED` | Trade Executor, after the Trade REST API's cancel is confirmed in the database | `orderId` (number), `accountId`, `symbol`, `side`, `quantity`, `price`; `eventId` fixed per order |

How a cancel reaches this module: `DELETE /api/v1/orders/{id}` commits NEW → CANCELLED, then the Trade
REST API publishes `ORDER_CANCELLED` to **`orders`**. The executor reads it, checks the order exists,
belongs to that account and is `CANCELLED` in the database, and publishes `ORDER_CANCELLED` to
**`trade-events`**. Each topic keeps a single writer (`kafka.md`): the Trade REST API writes `orders`,
the executor writes `trade-events`. The executor's `eventId` for a cancel is derived from the order id,
so a redelivered cancel is caught by this module's idempotency like any replay.

So the customer is not kept waiting for the executor, `OrderController.cancelOrder` also records the
cancel notification itself, right after the cancel commits (`NotificationService.recordCancelledOrder`),
with that **same** derived `eventId` (`OrderIdentifiers.cancelledEventId` = the executor's
`TradeEventPublisher.cancelledEventId`; both are pinned to one test value). Whichever of the two arrives
second hits the unique `event_id` and records nothing, so there is one notification per cancelled order.
If the immediate path fails it is only logged: the cancel still succeeds and the executor's path delivers.

A rejection carries no symbol, so the order is looked up in `orders`, joined through `portfolio`
**on the event's account**, which means an event can never pull another account's order into a message.

A message that can never become a notification (malformed JSON, no `eventId`, no account) goes
straight to `trade-events.DLT` with an `x-failure-reason` header. Anything else (e.g. the database
is down) is retried 3 times, 1 s apart, then sent to the DLT.

## Configuration

| Property | Default | |
|---|---|---|
| `notifications.consumer.group-id` | `notifications` | |
| `notifications.consumer.auto-startup` | `true` | `false` in tests (`src/test/resources/config/application.properties`) |
| `notifications.dispatcher.enabled` | `true` | `false` in tests |
| `notifications.dispatcher.interval-ms` | `5000` | |
| `notifications.dispatcher.batch-size` | `50` | |

## Demonstrating a replay

```bash
# 1. Place an order and let it fill. One row, status SENT, channel = the stored preference:
psql -d trade_management -c "SELECT notification_id, type, status, channel FROM notifications ORDER BY 1 DESC LIMIT 5;"

# 2. Stop the Trade REST API, rewind this module's group, start it again:
docker-compose exec kafka kafka-consumer-groups.sh --bootstrap-server kafka:29092 \
  --group notifications --topic trade-events --reset-offsets --to-earliest --execute

# 3. The log shows "Duplicate delivery of eventId=... ignored, nothing sent" for every event;
#    the notifications row count is unchanged and no stub logs a second send.

# 4. Change the preference (PUT /api/v1/accounts/{id}/preferences), place another order:
#    the new row's channel is the new preference, the old row keeps the one it went out on.
```

## Tests

| Execution path | Test |
|---|---|
| A filled order produces a notification | `NotificationServiceImplTest.filledOrder_producesQueuedNotification` |
| A rejected order produces a notification | `NotificationServiceImplTest.rejectedOrder_producesNotificationNamingTheOrderAndTheReason` |
| The channel comes from preferences, and changing it changes the channel | `NotificationDispatcherTest.channelComesFromPreferences_andChangingThePreferenceChangesTheChannel` |
| A replayed event does not send a second message | `TradeEventNotificationListenerTest.replayedEvent_doesNotSendASecondMessage` |
| History is refused to an account that does not own it | `NotificationServiceImplTest.history_isRefusedToAnAccountThatDoesNotOwnIt`, and over HTTP `NotificationControllerTest.listNotifications_anotherAccountsHistory_403AndNothingRead` |

## Known limits

- One dispatcher instance is assumed. Running several Trade REST API replicas would need pending
  rows claimed with `SELECT ... FOR UPDATE SKIP LOCKED` before sending.
- If the process dies after a provider accepts a message but before `SENT` is written, that one
  message is sent again on restart: outbound delivery is at-least-once. Consumption is not affected.
