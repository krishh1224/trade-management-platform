# 0002 Notifications holds a message when preferences has no channel stored, rather than choosing one

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | krishh1224 (notifications); Srey (preferences, V11/V12) |

## Context

The story requires the delivery channel to come from Customer Preferences. A `customer_preferences`
row exists only for accounts that were present when V11 ran, which backfilled them with `EMAIL`, and
for accounts whose customer has since saved the Profile page. An account inserted after V11 has no row.
V12 changed the column default to `NONE`, but a default applies only once a row is inserted.
`CustomerPreferencesLookup.findAlertChannel` therefore has three answers: a channel, `NONE` (opted out),
or empty (never chose). The dispatcher needs a rule for empty.

## Options considered

| Option | For | Against |
|---|---|---|
| Default to `EMAIL` (V11's original backfill value) | The customer hears about their fill straight away | Sends on a channel the customer never chose, to an address they may not expect alerts at. Cannot be undone once sent |
| Default to `BROWSER` (the inbox) and mark `SENT` | Harmless: the inbox shows every notification anyway | Records `channel = BROWSER` as if the customer chose it. Nothing outbound ever goes out for this row, even after they choose `EMAIL` |
| Treat empty as `NONE` and mark `SKIPPED` | Matches what `GET /preferences` already shows (it `COALESCE`s a missing row to `NONE`) | Treats "never asked" as "opted out". The message is final and never goes out |
| Hold it (`HELD`) and look again on every dispatcher pass | Nothing is lost or sent wrongly. The row goes out on the channel the customer picks, as soon as they pick one | Rows can wait indefinitely; the dispatcher re-reads them every 5 s |

## Decision

Empty means `HELD`. The row is in the inbox immediately and goes out on the first pass after a
preference is stored. `SKIPPED` was the nearest option because it matches the Profile page. It lost
because it is final: a skipped row can never be sent, while a held row can still become sent or skipped
once the customer decides. Of the four options, hold is the only one we can reverse.

## Consequences

- The `status` check constraint and `findPending` both include `HELD`. `findPending` orders `QUEUED`
  ahead of `HELD`, so held rows never starve new ones within a batch of 50.
- Cost: the UI and the dispatcher disagree. For an account with no row, Profile shows "None" while
  notifications is holding its messages. A customer who saves `EMAIL` for the first time then receives
  every held message at once, possibly hours or days old (`sent_at` far after `created_at`).
- Cost: held rows have no expiry, and each one costs a preferences lookup on every pass it is read.
- Follow-up: either create a `customer_preferences` row when an account is created, or make
  `GET /preferences` report "not set" instead of `NONE`. Both are owned by preferences, not notifications.
- Depends on 0003: the channel is looked up when sending, which is what lets a hold resolve itself.
