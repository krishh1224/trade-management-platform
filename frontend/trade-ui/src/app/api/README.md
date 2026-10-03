# API clients

`generated/` holds typed Angular clients produced from the two OpenAPI contracts. Everything else in
this folder is hand-written.

| Directory | Contract | Config |
|---|---|---|
| `generated/trade-api/` | `trade-api/contracts/trade-api.yaml` | `api-gen/trade-api.json` |
| `generated/auth-api/` | `nest-auth/contracts/auth-api.yaml` | `api-gen/auth-api.json` |

## Regenerate

From `frontend/trade-ui`:

```bash
npm ci
npm run generate:api
```

Run it whenever either contract changes, and commit the result. The generator is
[`ng-openapi-gen`](https://github.com/cyclosproject/ng-openapi-gen), pinned to an exact version in
`package.json` (no `^`) and locked in `package-lock.json`, so every developer generates identical
output. Bump it deliberately, regenerate, and review the diff.

`npm run check:api` regenerates and fails if the committed tree differs. Use it in CI.

## Rules

- The generated tree is **committed** (see `.gitignore`). Each run deletes files it did not write, so the
  tree contains only generator output.
- **Never edit a file under `generated/`.** The next run overwrites it. If a generated shape is awkward,
  adapt it in `trade.service.ts` / `auth.service.ts` (or a new service beside them).
- The contracts are not ours to edit. No generator validation is switched off.
- Application code imports `TradeService` / `AuthService`, and model types from `generated/*/models`.
- Both clients name their classes `Api` and `ApiConfiguration`. They are distinct classes in distinct
  directories; import them by path. `provideApi()` in `provide-api.ts` configures both base URLs.
