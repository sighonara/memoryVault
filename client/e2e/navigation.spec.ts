import { test, expect } from '@playwright/test';

/**
 * Navigation tests require a running backend for login.
 * They authenticate first, then verify navigation links work.
 */
test.describe('Navigation (requires backend)', () => {
  test.beforeEach(async ({ page }) => {
    // Login before each navigation test
    await page.goto('/login');
    await page.getByLabel('Email').fill('system@memoryvault.local');
    await page.getByLabel('Password').fill('memoryvault');
    await page.getByRole('button', { name: /sign in/i }).click();

    // Wait for redirect to reader (default route after login)
    await page.waitForURL('**/reader', { timeout: 10_000 });
  });

  test('navigates to reader page after login', async ({ page }) => {
    await expect(page).toHaveURL(/reader/);
  });

  test('navigates to bookmarks page', async ({ page }) => {
    await page.getByRole('link', { name: 'Bookmarks' }).click();
    await expect(page).toHaveURL(/bookmarks/);
  });

  test('navigates to YouTube page', async ({ page }) => {
    await page.getByRole('link', { name: 'YouTube' }).click();
    await expect(page).toHaveURL(/youtube/);
  });

  test('navigates to admin page', async ({ page }) => {
    await page.getByRole('link', { name: 'Admin' }).click();
    await expect(page).toHaveURL(/admin/);
  });

  test('navigates back to reader page', async ({ page }) => {
    await page.getByRole('link', { name: 'Bookmarks' }).click();
    await expect(page).toHaveURL(/bookmarks/);

    await page.getByRole('link', { name: 'Reader' }).click();
    await expect(page).toHaveURL(/reader/);
  });

  test('can logout and return to login page', async ({ page }) => {
    // Click the user menu icon
    await page.getByRole('button').filter({ hasText: 'account_circle' }).click();
    // Click logout menu item
    await page.getByRole('menuitem', { name: 'Logout' }).click();

    await expect(page).toHaveURL(/login/);
  });
});
