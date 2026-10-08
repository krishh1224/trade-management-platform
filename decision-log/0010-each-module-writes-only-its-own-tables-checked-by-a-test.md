# 0010 Each module writes only its own tables, and a test per module fails the build if it does not

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | Sameer S Patil, Ananya Singh (portfolio); krishh1224 (watchlists) |

## Context

All modules in the Trade REST API share one database connection, the same one the order write path
uses (0001). Portfolio began as read-only and then needed `realised_pnl_ledger`. Watchlists owns
`watchlists`, `watchlist_groups`, `price_alerts` and `watchlist_quotes`. One stray `UPDATE accounts` or
`UPDATE orders` in a new module's mapper would corrupt trading state, and nothing in the stack would
reject it. Every migration is applied by the one `apply.sh`, and most tables have a foreign key to
`accounts(acc_id)`.

## Options considered

| Option | For | Against |
|---|---|---|
| A PostgreSQL schema and role per module, with grants limiting each role to its own tables | Enforced by the database at runtime | Needs one datasource per module and schema-qualified cross-schema FKs to `accounts`, and changes how every existing migration and `apply.sh` run. Days of work across every module |
| Convention and code review | No cost | Review is how a stray write gets through: it only takes one missed line |
| A test per module that reads its MyBatis XML and fails if a write names a table outside the module's list | Cheap; fails the build, not production; documents ownership as code | Only covers modules that have the test, and only writes through mapper XML |

## Decision

Each module that writes keeps a test asserting that its mappers write only its own tables:
`PriceAlertMapperIntegrationTest.theModulesMappersWriteOnlyItsOwnTables` for watchlists, and the ledger
and read-only mapper tests for portfolio (`PortfolioReadMapperReadOnlyTest`). A schema per module was the
nearest option. It lost on cost: it is the stronger guarantee, but it means rebuilding the migrations and
the datasource for every module, while the test gives most of the protection in a single file.

## Consequences

- Ownership lives in each module's README table ("owned and written / read only / never touched") and
  in its test. Both have to change together.
- Cost: the check runs at build time and is opt-in. Notifications has no such test today, even though
  it writes `notifications` and `push_subscriptions` and reads `clients`, `orders`, `portfolio` and
  `instruments` (0008). Preferences writes through the shared `AccountQueryMapper`, which no test covers.
- Cost: the test cannot see a write made with `JdbcTemplate` or an annotation-based mapper.
- Follow-up: add the same test to notifications, listing its two owned tables, once 0008's follow-up
  removes the `clients` read.
