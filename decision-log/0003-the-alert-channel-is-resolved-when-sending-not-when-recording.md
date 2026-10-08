# 0003 The alert channel is resolved when a notification is sent, not when it is recorded

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | krishh1224 (notifications) |

## Context

A notification is recorded in one step and sent in another (0005). The listener inserts a `QUEUED` row,
and the dispatcher picks it up on its next pass, every 5 s. A `HELD` row can be picked up hours later. The
customer can change their alert channel at any moment through `PUT /api/v1/accounts/{id}/preferences`.
Acceptance requires that changing the preference changes the channel used. The channel also has to be
visible afterwards as a record of what was used.

## Options considered

| Option | For | Against |
|---|---|---|
| Look up the channel in the listener and store it on the row as the instruction for the dispatcher | One lookup per notification; the row says up front how it will go | A preference changed between record and send is ignored. A row recorded with no preference can never resolve (0002) |
| Look up the channel in the dispatcher, immediately before each send, and store the resolved value on the row afterwards | The latest preference always wins; `HELD` rows resolve naturally; `channel` records what was actually used | One lookup per notification per pass, repeated for every held row |

## Decision

The dispatcher calls `CustomerPreferencesLookup.findAlertChannel` for each notification it is about to
send. The resolved channel is written to `notifications.channel` together with the outcome. What decided
it: a record-time channel cannot implement 0002's hold, and the story asks that a changed preference
take effect.

## Consequences

- `notifications.channel` is null until a send is attempted and is never an instruction, only a record.
  An old row keeps the channel it went out on after the preference changes. The replay demo in the
  notifications README relies on this.
- Cost: a preferences query for every pending row on every pass. Held rows are re-queried until they
  resolve, at most 50 per pass.
- Cost: a message recorded while the preference was `EMAIL` can go out by `PUSH` if the customer
  switched within the 5 s window. We accept this because it is what the customer asked for most recently.
