# Docker Compose EC2 Testing Runbook

## Document Purpose

This runbook covers the full local/EC2 workflow for running the platform via
`docker-compose` (three containers: `postgres`, `auth-stub`, `trade-api`),
generating a JWT for a specific account, and exercising every account
endpoint against that account. All commands are bash (tested on an EC2
Amazon Linux host), not PowerShell.

---

## Preconditions

- `docker` and `docker-compose` installed on the host.
- Repository checked out, working directory: `chennai-capstone-SE5-team5/`.
- Ports available on the host: `8081` (trade-api). `postgres` and
  `auth-stub` are not published to the host — they're reached via
  `docker-compose exec`.

---

## Step 1: Create the root `.env` file

Compose refuses to start `auth-stub`/`trade-api` without `JWT_SECRET`
(must be at least 32 bytes). Create `chennai-capstone-SE5-team5/.env`:

```
JWT_SECRET=local-dev-only-trade-api-jwt-secret-not-for-production-use-000001
POSTGRES_DB=trade_management
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
```

---

## Step 2: Start all 3 containers

```bash
cd chennai-capstone-SE5-team5
docker-compose --profile platform up -d --build
```

Check status (wait until all three show healthy):

```bash
docker-compose --profile platform ps
```

Tail logs if something isn't starting:

```bash
docker-compose --profile platform logs -f trade-api
```

---

## Step 3: Confirm the accounts that exist in Postgres

```bash
docker-compose exec postgres psql -U postgres -d trade_management -c "SELECT * FROM accounts;"
```

Example output used for this runbook — account `101` is `ACTIVE`:

```
 acc_id | client_id | account_number | risk_profile | base_currency |  balance  |  status   | version |         created_at         |         updated_at
--------+-----------+----------------+--------------+---------------+-----------+-----------+---------+----------------------------+----------------------------
    101 |         1 | 1000000001     | LOW          | INR           |  98000.00 | ACTIVE    |       0 | 2026-09-10 08:57:07.849478 | 2026-09-10 08:57:07.849478
    102 |         2 | 1000000002     | MODERATE     | INR           | 156000.00 | ACTIVE    |       0 | 2026-09-10 08:57:07.849478 | 2026-09-10 08:57:07.849478
    103 |         3 | 1000000003     | ADVENTUROUS  | INR           |  82000.00 | ACTIVE    |       0 | 2026-09-10 08:57:07.849478 | 2026-09-10 08:57:07.849478
    104 |         4 | 1000000004     | LOW          | INR           | 116600.00 | ACTIVE    |       0 | 2026-09-10 08:57:07.849478 | 2026-09-10 08:57:07.849478
    105 |         5 | 1000000005     | MODERATE     | INR           |  95000.00 | SUSPENDED |       0 | 2026-09-10 08:57:07.849478 | 2026-09-10 08:57:07.849478
    106 |         6 | 1000000006     | ADVENTUROUS  | INR           |  65000.00 | ACTIVE    |       0 | 2026-09-10 08:57:07.849478 | 2026-09-10 08:57:07.849478
(6 rows)
```

To open an interactive shell instead of one-off queries:

```bash
docker-compose exec postgres psql -U postgres -d trade_management
```
(`\dt` lists tables, `\q` quits.)

---

## Step 4: Generate a JWT for account 101

`auth-stub` has no host port mapping, so the token request runs inside the
container via `docker-compose exec`. Set up the shared variables once per
shell session:

```bash
JWT_BASE=http://localhost:8082
API_BASE=http://localhost:8081
SECRET=local-dev-only-trade-api-jwt-secret-not-for-production-use-000001
ACCOUNT_ID=101
```

Generate the token and capture it in `$TOKEN` (the `-T` flag disables TTY
allocation so piped output isn't corrupted):

```bash
TOKEN=$(docker-compose exec -T auth-stub wget -qO- "http://localhost:8082/token?accountId=$ACCOUNT_ID&expOffsetSec=600&secret=$SECRET&headerAlg=HS256&signAlg=HS256" | sed -E 's/.*"token":"([^"]+)".*/\1/')
echo "$TOKEN"
```

Sanity-check the claims inside the token before using it:

```bash
echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | python3 -m json.tool
```

Expected: `{"accountId": 101, "exp": <epoch seconds ~10 min from now>}`.

> The JWT's `accountId` claim must exactly match the `{id}` in the URL you
> call next, or the API returns `403 ACC-403` ("Account not active") —
> this check is a pure claim-vs-path-id comparison in
> `AccountServiceImpl.assertAccountIsReadable`, unrelated to the DB
> `status` column.

---

## Step 5: Test all account-101 endpoints

```bash
curl -s -w "\nHTTP %{http_code}\n\n" "$API_BASE/api/v1/accounts/$ACCOUNT_ID"           -H "Authorization: Bearer $TOKEN"
curl -s -w "\nHTTP %{http_code}\n\n" "$API_BASE/api/v1/accounts/$ACCOUNT_ID/balance"   -H "Authorization: Bearer $TOKEN"
curl -s -w "\nHTTP %{http_code}\n\n" "$API_BASE/api/v1/accounts/$ACCOUNT_ID/positions" -H "Authorization: Bearer $TOKEN"
curl -s -w "\nHTTP %{http_code}\n\n" "$API_BASE/api/v1/accounts/$ACCOUNT_ID/orders"    -H "Authorization: Bearer $TOKEN"
```

Expected: `HTTP 200` on all four, with account `101`'s data in the body.

---

## Step 6: Test order endpoints (POST / DELETE)

These live in `OrderController` (`services/order-service/src/main/java/com/team5/controller/OrderController.java`), not `AccountController`. `symbol` must be a tradable ticker from the seeded `instruments` table (e.g. `INFY.NS`, `RELIANCE.NS`, `TATASTEEL.BO`). `accountId` in the body must match the JWT's `accountId` claim (same `101`).

### Place an order — `POST /api/v1/orders`

```bash
curl -s -w "\nHTTP %{http_code}\n\n" -X POST "$API_BASE/api/v1/orders" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
        "accountId": 101,
        "symbol": "INFY.NS",
        "side": "BUY",
        "quantity": 10,
        "price": 1500.00,
        "idempotencyKey": "order-101-test-0001"
      }'
```

Expected: `HTTP 200` with the created order, including its `id` (a UUID). Save that id:

```bash
ORDER_ID="<id from the response above>"
```

`idempotencyKey` must be unique per new order (8–100 chars) — reusing one returns the original order instead of creating a new one, so change it for each test.

### Cancel an order — `DELETE /api/v1/orders/{id}`

```bash
curl -s -w "\nHTTP %{http_code}\n\n" -X DELETE "$API_BASE/api/v1/orders/$ORDER_ID" \
  -H "Authorization: Bearer $TOKEN"
```

Expected: `HTTP 200` with the order now in `CANCELLED` status. Cancelling an order that belongs to another account returns `403 ACC-403`; an unknown order id returns `404`.

---

### Negative checks (optional, same style as the local JWT runbook)

Missing header:

```bash
curl -s -w "\nHTTP %{http_code}\n" "$API_BASE/api/v1/accounts/$ACCOUNT_ID"
```

Expired token:

```bash
EXPIRED=$(docker-compose exec -T auth-stub wget -qO- "http://localhost:8082/token?accountId=$ACCOUNT_ID&expOffsetSec=-60&secret=$SECRET&headerAlg=HS256&signAlg=HS256" | sed -E 's/.*"token":"([^"]+)".*/\1/')
curl -s -w "\nHTTP %{http_code}\n" "$API_BASE/api/v1/accounts/$ACCOUNT_ID" -H "Authorization: Bearer $EXPIRED"
```

Wrong account (mismatch → `403 ACC-403`) — token for `101`, URL for `102`:

```bash
curl -s -w "\nHTTP %{http_code}\n" "$API_BASE/api/v1/accounts/102" -H "Authorization: Bearer $TOKEN"
```

---

## Teardown

```bash
docker-compose --profile platform down
```

Add `-v` if you also want to drop the `pgdata` volume and reseed from
scratch next time:

```bash
docker-compose --profile platform down -v
```

---

## Reference

- Full endpoint set:
  - `AccountController` (`services/order-service/src/main/java/com/team5/controller/AccountController.java`): `GET /{id}`, `GET /{id}/balance`, `GET /{id}/positions`, `GET /{id}/orders` — no "list all accounts" endpoint; each JWT is scoped to one account.
  - `OrderController` (`services/order-service/src/main/java/com/team5/controller/OrderController.java`): `POST /api/v1/orders` (place order), `DELETE /api/v1/orders/{id}` (cancel order).
