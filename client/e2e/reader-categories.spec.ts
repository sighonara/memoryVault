import { test, expect } from '@playwright/test';

/**
 * Reader category E2E tests require a running backend.
 * They authenticate first, then test feed category management flows.
 *
 * View mode and sort order are persisted server-side as user preferences, so
 * the toggle tests click twice to leave the seed user's preferences unchanged.
 */
test.describe('Reader Categories (requires backend)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill('system@memoryvault.local');
    await page.getByLabel('Password').fill('memoryvault');
    await page.getByRole('button', { name: /sign in/i }).click();
    await page.waitForURL('**/reader', { timeout: 10_000 });
  });

  test('reader page shows All Items heading', async ({ page }) => {
    await expect(page.locator('app-feed-toolbar').getByText('All Items')).toBeVisible({ timeout: 5_000 });
  });

  test('sidebar shows Subscribed category', async ({ page }) => {
    await expect(page.locator('text=Subscribed')).toBeVisible({ timeout: 5_000 });
  });

  test('can toggle between List and Full view modes', async ({ page }) => {
    const toggle = page.locator('app-feed-toolbar').getByRole('button', { name: /^(full|list) view$/i });
    await expect(toggle).toBeVisible({ timeout: 5_000 });

    const before = await toggle.getAttribute('aria-label');
    await toggle.click();
    await expect(toggle).not.toHaveAttribute('aria-label', before!);
    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-label', before!);
  });

  test('can toggle sort order', async ({ page }) => {
    const toggle = page.locator('app-feed-toolbar').getByRole('button', { name: /^(newest|oldest) first$/i });
    await expect(toggle).toBeVisible({ timeout: 5_000 });

    const before = await toggle.getAttribute('aria-label');
    await toggle.click();
    await expect(toggle).not.toHaveAttribute('aria-label', before!);
    await toggle.click();
    await expect(toggle).toHaveAttribute('aria-label', before!);
  });

  test('can toggle unread filter', async ({ page }) => {
    const toggle = page.locator('app-feed-toolbar').getByRole('button', { name: /^show (all|unread)$/i });
    await expect(toggle).toBeVisible({ timeout: 5_000 });

    const before = (await toggle.textContent())!.trim();
    await toggle.click();
    await expect(toggle).not.toHaveText(before);
    await toggle.click();
    await expect(toggle).toHaveText(before);
  });

  test('clicking All Items in sidebar loads all items view', async ({ page }) => {
    // Select a category first so the click on All Items actually changes the view.
    await page.locator('.reader-sidebar').getByText('Subscribed').click();
    await expect(page.locator('app-feed-toolbar').getByText('Subscribed')).toBeVisible();

    await page.locator('.reader-sidebar').getByText('All Items').click();
    await expect(page.locator('app-feed-toolbar').getByText('All Items')).toBeVisible();
  });
});
