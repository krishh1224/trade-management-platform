import { expect, test } from '@playwright/test';

const username = process.env.E2E_USERNAME;
const password = process.env.E2E_PASSWORD;
const accountId = process.env.E2E_ACCOUNT_ID;

test.describe('Sign-in journey', () => {
  test.skip(!username || !password, 'Set E2E_USERNAME and E2E_PASSWORD to run the real auth sign-in test.');

  test('redirects unauthenticated user accessing a protected route to sign-in with return address', async ({
    page
  }) => {
    // Try to access the orders (protected) route without signing in
    await page.goto('/orders');

    // Should be redirected to sign-in with returnUrl query parameter
    await page.waitForURL('**/sign-in**');
    const url = new URL(page.url());
    expect(url.pathname).toBe('/sign-in');
    expect(url.searchParams.get('returnUrl')).toBe('/orders');
  });

  test('shows an error when sign-in credentials are refused', async ({ page }) => {
    await page.goto('/sign-in');

    // Attempt sign-in with invalid credentials
    await page.getByTestId('signin-username').fill('invaliduser123');
    await page.getByTestId('signin-password').fill('wrongpassword123');
    await page.getByTestId('signin-submit').click();

    // Should see an error message and remain on sign-in page
    const errorMessage = page.locator('[role="alert"]');
    await expect(errorMessage).toBeVisible();
    const errorText = await errorMessage.textContent();
    expect(errorText).toContain('Authentication failed');
    
    // Verify we're still on sign-in page
    await page.waitForURL('**/sign-in');
  });

  test('successfully signs in and displays dashboard with account information', async ({ page }) => {
    await page.goto('/sign-in');

    await page.getByTestId('signin-username').fill(username ?? '');
    await page.getByTestId('signin-password').fill(password ?? '');
    await page.getByTestId('signin-submit').click();

    // After successful sign-in, should navigate to dashboard home page
    await page.waitForURL('/', { timeout: 10000 });
    
    // Verify we're on the dashboard page (not sign-in)
    expect(page.url()).toBe('http://localhost:4200/');

    // Verify dashboard displays account information in header (profile trigger button)
    const profileTrigger = page.getByTestId('profile-trigger');
    await expect(profileTrigger).toBeVisible();

    // Verify account ID is displayed in the profile button text
    if (accountId) {
      const profileText = await profileTrigger.textContent();
      expect(profileText).toContain(`Account ${accountId}`);
    }
  });

  test('sign out button redirects authenticated user to sign-in page', async ({ page }) => {
    // Sign in first
    await page.goto('/sign-in');
    await page.getByTestId('signin-username').fill(username ?? '');
    await page.getByTestId('signin-password').fill(password ?? '');
    await page.getByTestId('signin-submit').click();

    // Wait for successful sign-in (redirects to dashboard home)
    await page.waitForURL('/', { timeout: 10000 });

    // Open the profile menu
    await page.getByTestId('profile-trigger').click();

    // Click sign-out button
    const signoutButton = page.getByTestId('signout-button');
    await expect(signoutButton).toBeVisible();
    await signoutButton.click();

    // Should be redirected to sign-in page
    await page.waitForURL('**/sign-in', { timeout: 10000 });
    expect(page.url()).toContain('/sign-in');
  });

  test('invalid password format prevents sign-in', async ({ page }) => {
    await page.goto('/sign-in');

    // Use valid username but invalid/weak password format
    const invalidPassword = '123'; // Too short or weak format
    
    await page.getByTestId('signin-username').fill(username ?? '');
    await page.getByTestId('signin-password').fill(invalidPassword);
    await page.getByTestId('signin-submit').click();

    // Should see an error message
    const errorMessage = page.locator('[role="alert"]');
    await expect(errorMessage).toBeVisible({ timeout: 5000 });

    // Verify we're still on sign-in page (not redirected to dashboard)
    expect(page.url()).toContain('/sign-in');
  });
});