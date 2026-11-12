import { test, expect, type Page } from '@playwright/test';

test.describe('WebSocket real-time updates', () => {
  async function login(page: Page) {
    await page.goto('/login');
    await page.getByLabel('Email').fill('system@memoryvault.local');
    await page.getByLabel('Password').fill('memoryvault');
    await page.getByRole('button', { name: /sign in/i }).click();
    await page.waitForURL('**/reader', { timeout: 10_000 });
  }

  /** Collects page errors and resolves once the app's STOMP WebSocket opens. */
  function watch(page: Page) {
    const errors: string[] = [];
    page.on('pageerror', (err) => errors.push(err.message));
    const socketOpened = page.waitForEvent('websocket', {
      predicate: (ws) => ws.url().includes('/ws'),
      timeout: 15_000,
    });
    return { errors, socketOpened };
  }

  test('each tab opens a WebSocket connection without errors', async ({ browser }) => {
    // Two browser contexts simulate two independent tabs/sessions.
    const context1 = await browser.newContext();
    const context2 = await browser.newContext();
    try {
      const page1 = await context1.newPage();
      const page2 = await context2.newPage();

      // Attach listeners before navigating so nothing is missed.
      const tab1 = watch(page1);
      const tab2 = watch(page2);

      await login(page1);
      await login(page2);

      const [ws1, ws2] = await Promise.all([tab1.socketOpened, tab2.socketOpened]);
      expect(ws1.isClosed()).toBe(false);
      expect(ws2.isClosed()).toBe(false);

      expect(tab1.errors.filter((e) => /websocket|stomp/i.test(e))).toHaveLength(0);
      expect(tab2.errors.filter((e) => /websocket|stomp/i.test(e))).toHaveLength(0);
    } finally {
      await context1.close();
      await context2.close();
    }
  });
});
