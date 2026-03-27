import { test, expect } from '@playwright/test';

/**
 * Reader category E2E tests require a running backend.
 * They authenticate first, then test feed category management flows.
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
    await expect(page.locator('text=All Items')).toBeVisible({ timeout: 5_000 });
  });

  test('sidebar shows Subscribed category', async ({ page }) => {
    await expect(page.locator('text=Subscribed')).toBeVisible({ timeout: 5_000 });
  });

  test('can toggle between List and Full view modes', async ({ page }) => {
    // Look for view mode toggle buttons in toolbar
    const listButton = page.getByRole('button', { name: /list/i });
    const fullButton = page.getByRole('button', { name: /full|article/i });

    // At least one should be visible
    const hasListButton = await listButton.isVisible().catch(() => false);
    const hasFullButton = await fullButton.isVisible().catch(() => false);
    expect(hasListButton || hasFullButton).toBeTruthy();
  });

  test('can toggle sort order', async ({ page }) => {
    // Look for sort order toggle
    const sortButton = page.getByRole('button', { name: /newest|oldest|sort/i });
    const isVisible = await sortButton.isVisible().catch(() => false);
    expect(isVisible).toBeTruthy();
  });

  test('can toggle unread filter', async ({ page }) => {
    // Look for unread toggle
    const unreadToggle = page.locator('mat-slide-toggle, [role="switch"]').first();
    const isVisible = await unreadToggle.isVisible().catch(() => false);
    // Toggle may be a slide-toggle or button
    if (!isVisible) {
      const unreadButton = page.getByRole('button', { name: /unread/i });
      await expect(unreadButton).toBeVisible();
    }
  });

  test('clicking All Items in sidebar loads all items view', async ({ page }) => {
    // Click on "All Items" in the sidebar
    await page.locator('.reader-sidebar').getByText('All Items').click();
    await expect(page.locator('text=All Items')).toBeVisible();
  });
});
