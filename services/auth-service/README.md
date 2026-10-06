# Nest Auth Service

Sprint 8 auth service: Node 20+, TypeScript, NestJS. Implements the four
routes in [`contracts/auth-api.yaml`](contracts/auth-api.yaml) plus a health
endpoint.

## Configuration

All configuration is read from the environment at runtime — see
[`.env.example`](.env.example). `JWT_SECRET` must be at least 32 bytes; the
service refuses to start otherwise (`POSTGRES_DB`/`POSTGRES_USER` are also
required, with no default).

## Running locally (no Docker)

```bash
cp .env.example .env   # then fill in the values
npm ci
npm run build
npm start
```

## Development

```bash
npm run start:dev   # watch mode
npm test            # unit tests, no live Postgres required (DB calls are mocked)
```

## Docker

Built and run as part of the root `docker-compose.yml` (`nest-auth` service,
`platform` profile):

```bash
docker compose --profile platform up -d --build nest-auth
```

## Endpoints

- `POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`,
  `GET /auth/me` — as specified in `contracts/auth-api.yaml`.
- `GET /health` — `200 {status: "UP", postgresReachable: true}` if it can
  reach Postgres, otherwise `503 {status: "DOWN", postgresReachable: false}`.
  Not part of the auth contract, so it is excluded from the OpenAPI document.

## API documentation (OpenAPI)

The running service publishes its own OpenAPI document, generated at startup
by `@nestjs/swagger` from the decorators on `AuthController` and the DTOs
(`src/auth/auth.dto.ts`, `src/auth/refresh.dto.ts`). Nothing is maintained by
hand beside the code.

| What | Path | Local URL |
|---|---|---|
| Human page (Swagger UI) | `/docs` | http://localhost:3000/docs |
| JSON document | `/docs-json` | http://localhost:3000/docs-json |

This does not replace `contracts/auth-api.yaml`, which stays the binding
contract. The generated document is the evidence that the code still matches
it: compare the paths, status codes and schemas in `/docs-json` against the
contract.

## Credential store

`credentials` (see `databases/postgresql/migrations/V3_add_credentials.sql`) holds
only `account_id` + `password_hash`, keyed to `account_id` (unique FK →
`accounts.acc_id`) rather than `client_id`: trade-api's JWT contract and its
authorization checks are scoped to a single `accountId` claim, not a client,
so a successful login resolves straight to that claim with no "which of
this client's accounts" lookup in between. `accounts.client_id` isn't unique
(the schema allows one client to own multiple accounts), so if a real
one-login-multiple-accounts requirement ever shows up, this table will need
to change - not assumed away here.

There's deliberately no `name`/display column here: a client's name already
lives in `clients.name`, reachable via `accounts.client_id`. Duplicating it
onto this row would be exactly the denormalization the rest of this schema
avoids elsewhere (`accounts` doesn't copy `clients.name`, `portfolio` doesn't
copy anything from `accounts`, etc). If a display name is ever needed
alongside a credential, join through `accounts` → `clients` for it rather
than storing a copy. `CredentialsRepository.findByAccountId` is the only
lookup, by the unique key alone. This repository is read-only in this story;
there's no registration/login endpoint until a follow-up story wires this up
to JWT issuance.

Passwords are hashed with bcrypt at cost 12 (`src/credentials/password.service.ts`).
Cost 12 measured ~270-280ms per hash on the dev machine this was built on —
same order of magnitude as the ~100ms target, and the floor this team
decided not to go below (lower cost cuts an offline attacker's per-guess
cost by the same factor). Re-measure on the actual deployment host before
relying on that number; adjust only if it's wildly off, never below 12.

## Logging

`RedactingLogger` (`src/logging/`) is the only logger this service uses —
wired app-wide via `app.useLogger()` in `main.ts`. It walks any logged
object to unlimited depth and replaces values whose key name matches
`password`, `secret`, `token`, `authorization`, `apiKey`, etc. (case-insensitive)
with `"[REDACTED]"`. This is defense-in-depth, not a substitute for call-site
discipline: `CredentialsRepository` logs only `{ accountId }` on failure,
never the raw error object or a whole DTO/request body.
