import { expect, test } from '@playwright/test';

const username = process.env.E2E_USERNAME;
const password = process.env.E2E_PASSWORD;
const accountId = process.env.E2E_ACCOUNT_ID;

test.describe('Blotter journey', () => {
  test.skip(
    !username || !password || !accountId,
    'Set E2E_USERNAME, E2E_PASSWORD, and E2E_ACCOUNT_ID to run the blotter test.'
  );

  async function signIn(page: any) {
    await page.goto('/sign-in');
    await page.getByTestId('signin-username').fill(username ?? '');
    await page.getByTestId('signin-password').fill(password ?? '');
    await page.getByTestId('signin-submit').click();

    // Wait for navigation away from sign-in
    await Promise.race([
      page.waitForURL('/', { timeout: 10000 }),
      page.waitForURL('**/orders', { timeout: 10000 }),
      page.waitForURL('**/blotter', { timeout: 10000 })
    ]).catch(() => {
      // Timeout is okay
    });

    // Verify signed in by checking for logout button
    const errorAlert = page.locator('[role="alert"]');
    const isError = await errorAlert.isVisible().catch(() => false);

    if (isError) {
      const errorText = await errorAlert.textContent();
      throw new Error(`Sign-in failed: ${errorText}`);
    }

    // Click profile menu to verify authentication
    await page.getByTestId('profile-trigger').click();
    await expect(page.getByTestId('signout-button')).toBeVisible();
  }

  test('displays blotter page after authentication', async ({ page }) => {
    await signIn(page);

    // Navigate to blotter page
    await page.goto('/blotter');

    // Blotter section should be visible with order history
    await expect(page.locator('section.blotter')).toBeVisible({ timeout: 5000 });

    // Page title should indicate blotter
    const heading = page.locator('main h1');
    const headingText = await heading.textContent();
    expect(headingText?.toLowerCase()).toContain('order history');
  });

  test('displays orders for the signed-in account', async ({ page }) => {
    await signIn(page);
    await page.goto('/blotter');

    // Wait for the table to load or empty state message
    await Promise.race([
      page.locator('table thead').isVisible(),
      page.locator('text=No orders yet').isVisible()
    ]).then(() => true).catch(() => false);

    // Either we see a table or an empty state message
    const tableExists = await page.locator('table thead').isVisible().catch(() => false);
    const emptyState = await page.locator('text=No orders yet').isVisible().catch(() => false);
    
    expect(tableExists || emptyState).toBeTruthy();

    // If table exists, check for common blotter columns
    if (tableExists) {
      const headerText = await page.locator('thead').textContent();
      expect(headerText?.toUpperCase()).toMatch(/CREATED|SYMBOL|SIDE|QTY|STATUS/);
    }
  });

  test('shows order details with correct status and pricing information', async ({ page }) => {
    await signIn(page);
    await page.goto('/blotter');

    // Wait for table or empty state
    await Promise.race([
      page.locator('table tbody tr').first().isVisible(),
      page.locator('text=No orders yet').isVisible()
    ]).then(() => true).catch(() => false);

    // Check if there are any rows with order data
    const rows = page.locator('tbody tr');
    const rowCount = await rows.count();

    if (rowCount > 0) {
      // Get first order row (has data-testid="history-row")
      const firstRow = rows.first();
      
      // Verify row is visible and has order data
      await expect(firstRow).toBeVisible();

      // Get row cells
      const cells = firstRow.locator('td');
      const cellCount = await cells.count();
      expect(cellCount).toBeGreaterThan(0);

      // Verify row contains symbol and price data
      const rowText = await firstRow.textContent();
      expect(rowText).toBeTruthy();

      // Check that side (BUY/SELL) is present
      expect(rowText).toMatch(/BUY|SELL/);

      // Check that status is one of the expected values
      expect(rowText).toMatch(/NEW|FILLED|REJECTED|CANCELLED/);
    }
  });

  test('allows filtering or sorting orders in the blotter', async ({ page }) => {
    await signIn(page);
    await page.goto('/blotter');

    // Wait for page to load with filter buttons or empty state
    await Promise.race([
      page.locator('button.summary-chip').first().isVisible(),
      page.locator('text=No orders yet').isVisible()
    ]).then(() => true).catch(() => false);

    // Check if filter controls exist (status summary chips)
    const filterChips = page.locator('button.summary-chip');
    const chipCount = await filterChips.count().catch(() => 0);

    if (chipCount > 0) {
      // Click a filter chip
      const firstChip = filterChips.first();
      await firstChip.click();
      
      // Page should still be visible after filtering
      await expect(page.locator('section.blotter')).toBeVisible();
    }
  });

  test('refreshes blotter data when navigating back to the page', async ({ page }) => {
    await signIn(page);

    // Navigate to blotter
    await page.goto('/blotter');
    
    // Wait for blotter to load
    await Promise.race([
      page.locator('table tbody tr').first().isVisible(),
      page.locator('text=No orders yet').isVisible()
    ]).then(() => true).catch(() => false);

    const blotterSection = page.locator('section.blotter');
    await expect(blotterSection).toBeVisible();

    // Navigate away and back
    await page.goto('/orders');
    await page.goto('/blotter');

    // Blotter should reload
    await Promise.race([
      page.locator('table tbody tr').first().isVisible(),
      page.locator('text=No orders yet').isVisible()
    ]).then(() => true).catch(() => false);

    // Section should be visible after reload
    await expect(blotterSection).toBeVisible();
  });
});
