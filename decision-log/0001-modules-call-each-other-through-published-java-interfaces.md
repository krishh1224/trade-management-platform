# 0001 Modules call each other through published Java interfaces, not HTTP, Kafka or each other's tables

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |
| Decided by | krishh1224 (notifications); Srey (preferences) |

## Context

Orders, accounts, preferences, portfolio, watchlists and notifications are all packages inside one
Spring Boot process, the Trade REST API (`services/order-service`). They share one PostgreSQL
database and one connection pool, so nothing in the framework stops a mapper in one package from
reading or writing another package's tables. Every REST route requires a bearer token and compares its
`accountId` claim with the path. Notifications runs a background dispatcher, and watchlists runs a
Kafka listener. Neither one acts for a customer, so neither has a token. Topic auto-creation is
disabled, and each topic has exactly one writer (`services/order-service/design/kafka.md`).

## Options considered

| Option | For | Against |
|---|---|---|
| Read the other module's tables directly with our own SQL | No new code in the other module; one query | Couples us to their column names and enum values; the owner cannot change their schema without finding every reader |
| Call the other module's REST route over HTTP (loopback) | Same boundary an external client sees; would survive splitting into services | The caller has no token. We would need an unauthenticated internal route, and that is exactly what would let a customer trigger a send to themselves or anyone else |
| Publish an event on Kafka for the other module to consume | Fully decoupled; the other module can be down | A new topic for every interaction, created by hand. A topic has one writer, so `trade-events` is closed to us. Eventual consistency where watchlists needs the alert and the notification to commit together (0003) |
| The owning module publishes a small Java interface in its own package (`CustomerPreferencesLookup`, `AlertDelivery`, `AccountService`) and callers depend only on that | Compile-time contract; the owner keeps its table private; can join the caller's transaction | Only works while both modules run in one process; the interface takes no token, so the caller must scope by account itself |

## Decision

Each module that others depend on publishes a Java interface. Callers use that interface, never the
owner's tables and never an HTTP route. HTTP was the nearest option, and two things ruled it out. First,
an internal caller has no token. Second, an untokened route that creates notifications is a security
hole. An interface cannot be reached from outside the process at all.

## Consequences

- Easy: preferences can rename `customer_preferences.alert_channel` and only
  `MyBatisCustomerPreferencesLookup` changes. Callers are unit-tested against a fake of the interface.
- `CustomerPreferencesLookup` returns `Optional.empty()` for "never chose" and `NONE` for "opted out".
  That distinction exists only because we wrote the interface. A raw table read with `COALESCE`
  would have lost it (0002).
- Cost: modules cannot be deployed separately. Splitting one out means turning its interface into a
  remote call and choosing how that call authenticates.
- Cost: the interfaces carry no token. A caller is trusted to act only on the account its event or
  alert belongs to. This is stated in the `CustomerPreferencesLookup` comment and is not enforced.
- Known exceptions, not yet fixed: notifications reads `clients.email` (0008) and joins
  `orders`/`portfolio`/`instruments` for rejection messages, directly in `NotificationMapper.xml`.
  0010 is how we detect any new ones.
