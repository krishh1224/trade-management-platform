# Playwright E2E Tests - Quick Start Guide

## Overview

Four complete Playwright test journeys have been implemented for the Artha trading application:

**Core Journeys (Required):**
1. **Sign-In Journey** (`e2e/sign-in.spec.ts`) - 4 tests
2. **Place-Order Journey** (`e2e/place-order.spec.ts`) - 5 tests

**Additional Journeys:**
3. **Blotter Journey** (`e2e/blotter.spec.ts`) - 5 tests
4. **Watchlist Journey** (`e2e/watchlist.spec.ts`) - 7 tests

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
cd services/auth-service
npm start

# Terminal 2: Start the Trade API
cd services/order-service
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

### Blotter Journey (5 tests)

1. **Display Blotter Page Test**
   - Verifies blotter page loads after authentication
   - Confirms page header/title indicates blotter

2. **Display Orders Test**
   - Verifies orders table loads for signed-in account
   - Confirms table has standard blotter columns

3. **Order Details Test**
   - Verifies order rows display Order ID, Symbol, Side, Status
   - Confirms status is one of: NEW, FILLED, REJECTED, CANCELLED
   - Validates pricing information is shown

4. **Filtering/Sorting Test**
   - Checks for filter or sort controls
   - Tests interactive filtering if available
   - Verifies table remains visible after filtering

5. **Data Refresh Test**
   - Navigates away and back to blotter
   - Verifies data reloads correctly
   - Confirms table structure is intact after refresh

### Watchlist Journey (7 tests)

1. **Display Watchlist Test**
   - Verifies watchlist section loads after authentication
   - Confirms watchlist groups are visible

2. **Add to Watchlist Test**
   - Adds an instrument to watchlist
   - Verifies success message appears
   - Confirms operation completes

3. **Display Items Test**
   - Verifies watchlist items show symbol, price, and changes
   - Confirms price data is present and valid
   - Checks change percentage displays correctly

4. **Remove from Watchlist Test**
   - Removes an instrument from watchlist
   - Verifies success or confirmation message
   - Confirms item is no longer in list

5. **Manage Groups Test**
   - Creates new watchlist groups
   - Fills group name and submits
   - Verifies new group appears in list

6. **Data Refresh Test**
   - Navigates away and back to profile/watchlist
   - Verifies watchlist data reloads correctly
   - Confirms structure is intact

7. **Account Isolation Test**
   - Verifies each test has fresh sign-in
   - Confirms watchlist data belongs only to signed-in user
   - Validates no permissions errors or cross-account data leaks

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

### Blotter Elements
- `blotter-container` - Main blotter container
- `blotter-table` - Orders table
- `blotter-header` - Table header row
- `blotter-filters` - Filter controls (optional)
- `blotter-sort` - Sort controls (optional)
- `filter-status` - Status filter selector
- `order-id` - Order ID column
- `order-symbol` - Symbol column
- `order-side` - Side column (BUY/SELL)
- `order-status` - Status column (NEW, FILLED, REJECTED, CANCELLED)

### Watchlist Elements
- `watchlist-section` - Main watchlist section
- `watchlist-groups` - Container for watchlist groups
- `watchlist-group-name` - Group name label
- `watchlist-item` - Individual watchlist item
- `watchlist-symbol` - Instrument symbol in watchlist
- `watchlist-price` - Current price display
- `watchlist-change` - Price change percentage
- `add-to-watchlist-btn` - Button to add instrument
- `remove-from-watchlist-btn` - Button to remove instrument
- `add-watchlist-group-btn` - Button to create new group
- `group-name-input` - Input for group name
- `create-group-submit` - Submit button for new group
- `watchlist-add-symbol` - Symbol input for adding to watchlist

### Common Elements
- `signout-button` - Sign out button
- `profile-trigger` - Profile menu trigger/button
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
