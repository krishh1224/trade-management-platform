# 0006 A failed delivery is recorded as final with its reason, and is not retried

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | krishh1224 (notifications) |

## Context

After 0005, a send happens in `NotificationDispatcher`, a single-threaded loop that reads up to 50
pending rows every 5 s and calls one `ChannelSender` per row. A sender can fail for reasons that will
never clear, such as "No valid email address on file", no browser subscribed to push, or no sender
configured for the channel. It can also fail for reasons that might clear: an SMTP refusal, a timeout,
or a push service 5xx. Every notification is already readable in the customer's inbox, whatever happens
outbound. The table has one row per notification and no attempts history.

## Options considered

| Option | For | Against |
|---|---|---|
| Retry with backoff: `attempts` and `next_attempt_at` columns, give up after N | A provider blip does not cost the customer their email | Needs a rule for which errors are permanent. Each retry of a dead provider costs up to 5 s (email) or 10 s per browser (push) of the single dispatcher thread, delaying every other account's messages |
| On failure, fall back to `BROWSER` and mark `SENT` | The customer "has" it | The inbox already has it, so this adds nothing and records a channel nobody chose. It hides the failure |
| Mark `FAILED`, store the channel and a short `last_error`, never touch the row again | One attempt, bounded cost per row, the reason is visible to an investigator | A transient failure loses that outbound message permanently |

## Decision

`FAILED` is final. `last_error` holds the provider's short reason (300 characters at most, never
credentials). Retry with backoff was the nearest option. It lost because the dispatcher is one thread
with 5 to 10 s timeouts, and a retry policy that does not separate permanent from transient errors can
spend whole passes on a dead provider. Building that classification was out of scope for the sprint.
The cost of not retrying is bounded because the inbox always has the message.

## Consequences

- No per-attempt log, attempt counter or `updated_at` is stored, because there is only ever one attempt.
- A dispatch interrupted by our own database error is different from a provider failure. That row is
  left `QUEUED`/`HELD` and picked up next pass, so only provider-side failures become `FAILED`.
- Cost: a 30-second SMTP outage means every email due in that window is never sent. Nothing alerts
  on it except a `WARN` log line and the rows' `last_error`. There is no tool to re-drive failed rows.
- Follow-up when retry is added: classify errors into permanent and transient, add `attempts` and
  `next_attempt_at`, extend `findPending` and its partial index, and claim rows with
  `FOR UPDATE SKIP LOCKED` (see 0005) before running more than one dispatcher.
