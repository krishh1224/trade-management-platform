# Playwright E2E Tests - Quick Start Guide

## Overview

Two complete Playwright test journeys have been implemented for the Artha trading application:

1. **Sign-In Journey** (`e2e/sign-in.spec.ts`) - 4 tests
2. **Place-Order Journey** (`e2e/place-order.spec.ts`) - 5 tests

Each test is fully independent and can run in any order. All credentials are read from environment variables.

## Quick Setup

### 1. Set Environment Variables

Choose your method based on your shell:

#### PowerShell (Windows)

```powershell
$env:E2E_BASE_URL = "http://localhost:4200"
$env:E2E_USERNAME = "your-test-username"
$env:E2E_PASSWORD = "your-test-password"
$env:E2E_ACCOUNT_ID = "your-account-id"
$env:E2E_SYMBOL = "INFY.NS"
```

#### Bash/Linux/macOS

```bash
export E2E_BASE_URL=http://localhost:4200
export E2E_USERNAME=your-test-username
export E2E_PASSWORD=your-test-password
export E2E_ACCOUNT_ID=your-account-id
export E2E_SYMBOL=INFY.NS
```

### 2. Ensure Services Are Running

Before running tests, make sure all services are up:

```bash
# Terminal 1: Start the Auth API
cd nest-auth
npm start

# Terminal 2: Start the Trade API
cd trade-api
./mvnw spring-boot:run

# Terminal 3: Start the Angular app
cd frontend/trade-ui
npm run start

# Terminal 4: Run Playwright tests
cd frontend/trade-ui
npm run test:e2e
```

### 3. Run the Tests

```bash
# From frontend/trade-ui directory
npm run test:e2e
```

#### Run specific test file

```bash
# Sign-in tests only
npx playwright test e2e/sign-in.spec.ts

# Place-order tests only
npx playwright test e2e/place-order.spec.ts
```

#### Run with UI (easier debugging)

```bash
npx playwright test --ui
```

#### Run in headed mode (see the browser)

```bash
npx playwright test --headed
```

## What Each Journey Tests

### Sign-In Journey (4 tests)

1. **Guard Redirect Test**
   - Accesses protected route `/orders` without authentication
   - Verifies redirect to `/sign-in` with `returnUrl=/orders`
   - Tests route guard functionality

2. **Refused Sign-In Test**
   - Attempts sign-in with invalid credentials
   - Verifies error message appears
   - Confirms still on sign-in page

3. **Successful Sign-In Test**
   - Signs in with valid credentials
   - Verifies sign-out button appears
   - Confirms navigation to home page

4. **Return URL Test**
   - Navigates to protected route with returnUrl parameter
   - Performs sign-in
   - Verifies successful authentication and access to protected area

### Place-Order Journey (5 tests)

1. **Read-Only Account Test**
   - Verifies account field is read-only
   - Confirms account ID matches signed-in user

2. **Validation Test**
   - Attempts to submit empty form
   - Tests invalid symbol format
   - Tests invalid quantity (zero)
   - Tests invalid price (negative, too many decimals)
   - Verifies error messages appear

3. **Successful Order Test**
   - Fills valid order form
   - Submits order
   - Verifies response with status (NEW, FILLED, or REJECTED)
   - Confirms order ID is shown

4. **Error Handling Test**
   - Attempts order with invalid symbol
   - Verifies API error is displayed as readable message
   - Confirms no error codes shown to user

5. **Idempotency Test**
   - Places an order successfully
   - Verifies unique idempotency key used
   - Confirms each order is independent

## Test Selectors

All tests use stable `data-testid` attributes:

### Sign-In Form
- `signin-username` - Username input
- `signin-password` - Password input
- `signin-submit` - Submit button

### Orders Form
- `order-account` - Account field (read-only)
- `order-side` - Side selector (BUY/SELL)
- `order-symbol` - Instrument/symbol input
- `order-quantity` - Quantity input
- `order-price` - Price input
- `order-submit` - Submit button

### Common Elements
- `signout-button` - Sign out button
- `[role="alert"]` - Error messages
- `[role="status"]` - Success messages

## Troubleshooting

### Test is skipped

The test requires specific environment variables. Run the test with list flag to see which variables are missing:

```bash
npx playwright test --list
```

**Fix**: Set all required environment variables before running tests

### Authentication fails

**Possible causes:**
- Wrong username/password
- Auth API not running
- CORS settings on Auth API

**Fix:**
```bash
# Verify Auth API is running
curl http://localhost:3000/health

# Check CORS settings in nest-auth
```

### Order placement fails

**Possible causes:**
- Trade API not running
- Invalid account ID
- Invalid symbol
- Insufficient funds

**Fix:**
```bash
# Verify Trade API is running
curl http://localhost:8081/actuator/health

# Check account and symbol are valid
```

### Timeout errors

Increase timeout and run with more verbose output:

```bash
npx playwright test --timeout=60000 --debug
```

### See browser during test

Run tests in headed mode to watch execution:

```bash
npx playwright test --headed
```

### Debug specific test

```bash
npx playwright test --debug
# Then use Playwright Inspector to step through
```

## Test Isolation

Each test:
- ✅ Runs in a fresh browser context
- ✅ Performs its own sign-in (no shared tokens)
- ✅ Uses unique idempotency keys for orders
- ✅ Leaves no state for other tests
- ✅ Can be run in any order
- ✅ Can be run in parallel (if configured)

## Generated Artifacts

Failed tests generate useful debugging info:

```
.playwright/          # Playwright traces directory
  └── traces/
      ├── sign-in.spec.ts-1-sign-in-1-refus.trace.zip
      └── ... other traces
```

View traces:
```bash
npx playwright show-trace .playwright/traces/sign-in.spec.ts-1-sign-in-1-refus.trace.zip
```

## Documentation

For comprehensive documentation including CI/CD setup, see [e2e/README.md](./README.md)

## Quick Reference

| Command | Purpose |
|---------|---------|
| `npm run test:e2e` | Run all tests |
| `npx playwright test --ui` | Run with interactive UI |
| `npx playwright test --headed` | See the browser |
| `npx playwright test --debug` | Debug mode |
| `npx playwright test --list` | Show tests and skip reasons |
| `npx playwright show-report` | View test report |

## Next Steps

1. ✅ **Set environment variables** for your test environment
2. ✅ **Start all services** (Auth API, Trade API, Angular app)
3. ✅ **Run tests** with `npm run test:e2e`
4. ✅ **Review results** - tests should pass with valid credentials
5. ✅ **Use for CI/CD** - add to your GitHub Actions or other CI pipeline

## Support

For issues or questions:
1. Check the [e2e/README.md](./README.md) for detailed documentation
2. Review Playwright docs: https://playwright.dev/
3. Check test output with `--debug` flag
4. Verify all services are running and accessible
