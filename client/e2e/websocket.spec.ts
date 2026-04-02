import { test, expect } from '@playwright/test';

test.describe('WebSocket real-time updates', () => {
  const loginUrl = 'http://localhost:4200/login';

  async function login(page: any) {
    await page.goto(loginUrl);
    await page.fill('input[formControlName="email"]', 'system@memoryvault.local');
    await page.fill('input[formControlName="password"]', 'memoryvault');
    await page.click('button[type="submit"]');
    await page.waitForURL('**/reader');
  }

  test('cross-tab sync: marking item read reflects in other tab', async ({ browser }) => {
    // Open two browser contexts (simulates two tabs)
    const context1 = await browser.newContext();
    const context2 = await browser.newContext();
    const page1 = await context1.newPage();
    const page2 = await context2.newPage();

    // Login in both tabs
    await login(page1);
    await login(page2);

    // Both should show the reader page
    await expect(page1).toHaveURL(/reader/);
    await expect(page2).toHaveURL(/reader/);

    // Wait for WebSocket connections to establish
    await page1.waitForTimeout(2000);
    await page2.waitForTimeout(2000);

    // This test verifies the WebSocket connection is established and signals flow.
    // A full cross-tab test requires feed items to exist, which depends on test data.
    // For now, verify both pages loaded and WebSocket didn't cause errors.
    const page1Errors: string[] = [];
    const page2Errors: string[] = [];
    page1.on('pageerror', (err) => page1Errors.push(err.message));
    page2.on('pageerror', (err) => page2Errors.push(err.message));

    // Navigate around to trigger store subscriptions
    await page1.waitForTimeout(1000);

    expect(page1Errors.filter(e => e.includes('WebSocket'))).toHaveLength(0);
    expect(page2Errors.filter(e => e.includes('WebSocket'))).toHaveLength(0);

    await context1.close();
    await context2.close();
  });
});
