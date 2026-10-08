# 0008 Accounts owns the customer's contact details; notifications looks the address up by account on the server

| Field | Value |
|---|---|
| Status | accepted, with the follow-up below outstanding |
| Date | 2026-10-07 |
| Decided by | krishh1224 (notifications); Srey (preferences) |

## Context

The `EMAIL` channel needs an address and a future real `SMS` channel would need a phone number. Both
already exist on `clients` (`email`, `phone`), which belongs to the accounts side of the Trade REST API
(`AccountQueryMapper`). `customer_preferences` holds only currency, theme and `alert_channel`. `PUSH`
needs a per-browser subscription that did not exist anywhere. Sending to a destination a caller can
choose is the classic way to turn a notification feature into a spam or SSRF tool.

## Options considered

| Option | For | Against |
|---|---|---|
| Preferences stores a destination per channel (email, phone, webhook URL), editable on the Profile page | The customer can send alerts to a different inbox; preferences becomes "everything about how to reach me" | A second, unverified copy of the address. A customer-supplied destination can point anywhere, and a webhook URL is server-side request forgery |
| Accounts publishes a `ContactDetailsLookup` interface; notifications calls it | Consistent with 0001; `clients` stays private to accounts | One more interface and test in a module notifications does not own |
| Notifications reads `clients.email` for the notification's own `account_id` with its own query | One `SELECT`; the address can only be the one on the account's record | Breaks 0001: notifications reads a table it does not own |

## Decision

The address comes from `clients.email` for the notification's own `account_id`, looked up by
`SmtpEmailSender` at send time. Neither preferences nor any request body carries a destination. Push
subscriptions are owned by notifications in `push_subscriptions` (V14), because they are per browser
and exist only for notifications. Their endpoints are allow-listed to the known push services. The
destination-in-preferences option lost on security: no field anywhere in the API can redirect a
message. The interface option was the right shape and was not built this sprint. The direct read is
recorded here as a known exception to 0001.

## Consequences

- An email can only go to the address on the account. A missing or malformed address is `FAILED` with
  "No valid email address on file" (0006). Logs show a masked address only.
- A customer cannot send alerts to a different inbox than their account address. Changing it means
  changing the account record.
- Cost: notifications depends on `clients.acc_id` and `clients.email` by name. An accounts change to
  `clients` breaks email silently at runtime, not at compile time.
- `SMS` stays a logging stub. A real one would read `clients.phone` the same way, and should only be
  built after the follow-up.
- Follow-up: add a contact lookup to `AccountService` (or a `ContactDetailsLookup` interface), move
  `findEmailForAccount` out of `NotificationMapper.xml`, and add notifications to the own-tables check
  in 0010.
