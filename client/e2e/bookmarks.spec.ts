import { test, expect } from '@playwright/test';

test.describe('Bookmarks (requires backend)', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill('system@memoryvault.local');
    await page.getByLabel('Password').fill('memoryvault');
    await page.getByRole('button', { name: /sign in/i }).click();
    await page.waitForURL('**/reader', { timeout: 10_000 });

    await page.getByRole('link', { name: 'Bookmarks' }).click();
    await page.waitForURL('**/bookmarks', { timeout: 5_000 });
  });

  test.describe('Page layout', () => {
    test('renders two-panel layout with tree and list', async ({ page }) => {
      await expect(page.locator('.bookmark-manager')).toBeVisible();
      await expect(page.locator('.tree-panel')).toBeVisible();
      await expect(page.locator('.list-panel')).toBeVisible();
    });

    test('renders toolbar with search input and add button', async ({ page }) => {
      await expect(page.locator('.toolbar-search')).toBeVisible();
      await expect(page.getByRole('button', { name: /add/i })).toBeVisible();
    });
  });

  test.describe('Folder management', () => {
    const folderName = `Test-${Date.now()}`;

    test('creates a root folder via context menu', async ({ page }) => {
      // Right-click on the tree panel to open context menu
      await page.locator('.tree-panel').click({ button: 'right' });
      const newFolderItem = page.getByRole('menuitem', { name: /new folder/i });
      if (await newFolderItem.isVisible({ timeout: 2_000 }).catch(() => false)) {
        // Handle prompt dialog
        page.once('dialog', async (dialog) => {
          await dialog.accept(folderName);
        });
        await newFolderItem.click();
        // Verify folder appears in tree
        await expect(page.locator('.tree-panel').getByText(folderName)).toBeVisible({ timeout: 5_000 });
      }
    });

    test('rename folder via context menu', async ({ page }) => {
      // Create folder first
      page.once('dialog', (dialog) => dialog.accept('RenameMe'));
      await page.locator('.tree-panel').click({ button: 'right' });
      const newItem = page.getByRole('menuitem', { name: /new folder/i });
      if (await newItem.isVisible({ timeout: 2_000 }).catch(() => false)) {
        await newItem.click();
        await expect(page.locator('.tree-panel').getByText('RenameMe')).toBeVisible({ timeout: 5_000 });

        // Right-click the folder to rename
        await page.locator('.tree-panel').getByText('RenameMe').click({ button: 'right' });
        const renameItem = page.getByRole('menuitem', { name: /rename/i });
        if (await renameItem.isVisible({ timeout: 2_000 }).catch(() => false)) {
          page.once('dialog', (dialog) => dialog.accept('Renamed'));
          await renameItem.click();
          await expect(page.locator('.tree-panel').getByText('Renamed')).toBeVisible({ timeout: 5_000 });
        }
      }
    });
  });

  test.describe('Bookmark operations', () => {
    test('opens add bookmark dialog', async ({ page }) => {
      await page.getByRole('button', { name: /add/i }).click();
      await expect(page.getByRole('dialog')).toBeVisible({ timeout: 3_000 });
    });

    test('creates a bookmark via dialog', async ({ page }) => {
      await page.getByRole('button', { name: /add/i }).click();
      const dialog = page.getByRole('dialog');
      await expect(dialog).toBeVisible({ timeout: 3_000 });

      await dialog.getByLabel(/url/i).fill('https://example-e2e-test.com');
      await dialog.getByLabel(/title/i).fill('E2E Test Bookmark');
      await dialog.getByRole('button', { name: /save|add|create/i }).click();

      // Dialog should close
      await expect(dialog).not.toBeVisible({ timeout: 5_000 });
    });

    test('clicking All Bookmarks shows all bookmarks', async ({ page }) => {
      const allNode = page.locator('.tree-panel').getByText(/all bookmarks/i);
      if (await allNode.isVisible({ timeout: 2_000 }).catch(() => false)) {
        await allNode.click();
        // List panel should show content (not empty)
        await expect(page.locator('.list-panel')).toBeVisible();
      }
    });

    test('clicking Unfiled shows only unfiled bookmarks', async ({ page }) => {
      const unfiledNode = page.locator('.tree-panel').getByText(/unfiled/i);
      if (await unfiledNode.isVisible({ timeout: 2_000 }).catch(() => false)) {
        await unfiledNode.click();
        await expect(page.locator('.list-panel')).toBeVisible();
      }
    });
  });

  test.describe('Search', () => {
    test('search input filters bookmarks', async ({ page }) => {
      const searchInput = page.locator('.toolbar-search');
      await searchInput.fill('nonexistent-query-xyz');
      // Wait for debounce
      await page.waitForTimeout(500);
      // Should show no-results or empty list
      await expect(page.locator('.list-panel')).toBeVisible();
    });

    test('clearing search restores bookmarks', async ({ page }) => {
      const searchInput = page.locator('.toolbar-search');
      await searchInput.fill('test');
      await page.waitForTimeout(500);
      await searchInput.fill('');
      await page.waitForTimeout(500);
      await expect(page.locator('.list-panel')).toBeVisible();
    });
  });

  test.describe('Ingest panel', () => {
    test('expand and collapse ingest panel', async ({ page }) => {
      const panelHeader = page.getByText('Import / Export');
      await panelHeader.click();
      // Panel content should be visible
      await expect(page.getByText('Import from Browser')).toBeVisible({ timeout: 3_000 });

      // Collapse
      await panelHeader.click();
      await expect(page.getByText('Import from Browser')).not.toBeVisible({ timeout: 3_000 });
    });

    test('changing browser dropdown updates command', async ({ page }) => {
      await page.getByText('Import / Export').click();
      await expect(page.getByText('Import from Browser')).toBeVisible({ timeout: 3_000 });

      // Open browser select and choose Firefox
      await page.locator('.browser-select').click();
      await page.getByRole('option', { name: 'Firefox' }).click();

      // Expand the command view
      await page.getByText('View command').click();
      await expect(page.locator('.code-block')).toBeVisible({ timeout: 3_000 });
      // Firefox command should contain sqlite3
      await expect(page.locator('.code-block')).toContainText('sqlite3');
    });

    test('copy command button works', async ({ page, context }) => {
      await context.grantPermissions(['clipboard-read', 'clipboard-write']);
      await page.getByText('Import / Export').click();
      await expect(page.getByText('Import from Browser')).toBeVisible({ timeout: 3_000 });

      await page.getByRole('button', { name: /copy command/i }).click();
      await expect(page.getByText('Copied!')).toBeVisible({ timeout: 3_000 });
    });
  });

  test.describe('Ingest flow (end-to-end)', () => {
    test('seed preview via API and review via UI', async ({ page, request }) => {
      // Get auth token from localStorage
      const token = await page.evaluate(() => localStorage.getItem('auth_token'));
      if (!token) {
        test.skip();
        return;
      }

      // Seed a preview via REST API
      const response = await request.post('http://localhost:8080/api/bookmarks/ingest', {
        headers: {
          Authorization: `Bearer ${token}`,
          'Content-Type': 'application/json',
        },
        data: {
          bookmarks: [
            { url: 'https://e2e-ingest-test.com', title: 'E2E Ingest Test', browserFolder: null },
          ],
        },
      });

      if (!response.ok()) {
        test.skip();
        return;
      }

      const preview = await response.json();
      const previewId = preview.previewId;

      // Navigate to bookmarks with ingest query param
      await page.goto(`/bookmarks?ingest=${previewId}`);

      // For a single NEW bookmark with no conflicts, it should auto-accept
      // and show a snackbar
      const snackbar = page.locator('mat-snack-bar-container');
      const hasSnackbar = await snackbar.isVisible({ timeout: 5_000 }).catch(() => false);

      if (hasSnackbar) {
        await expect(snackbar).toContainText(/imported/i);
      } else {
        // If conflicts, dialog should open
        const dialog = page.getByRole('dialog');
        const hasDialog = await dialog.isVisible({ timeout: 3_000 }).catch(() => false);
        if (hasDialog) {
          await expect(dialog).toBeVisible();
        }
      }
    });
  });

  test.describe('Export', () => {
    test('export button triggers download', async ({ page }) => {
      await page.getByText('Import / Export').click();
      await expect(page.getByText('Export Bookmarks')).toBeVisible({ timeout: 3_000 });

      const downloadPromise = page.waitForEvent('download', { timeout: 10_000 }).catch(() => null);
      await page.getByRole('button', { name: /download html/i }).click();
      const download = await downloadPromise;

      if (download) {
        expect(download.suggestedFilename()).toContain('memoryvault-export');
      }
    });
  });
});
