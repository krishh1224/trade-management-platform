import { expect, test } from '@playwright/test';

const username = process.env.E2E_USERNAME;
const password = process.env.E2E_PASSWORD;

test.describe('sign-in flow', () => {
  test.skip(!username || !password, 'Set E2E_USERNAME and E2E_PASSWORD to run the real auth sign-in test.');

  test('signs in against the real auth service', async ({ page }) => {
    await page.goto('/sign-in');

    await page.getByTestId('signin-username').fill(username ?? '');
    await page.getByTestId('signin-password').fill(password ?? '');
    await page.getByTestId('signin-submit').click();

    await expect(page.getByTestId('signout-button')).toBeVisible();
  });
});