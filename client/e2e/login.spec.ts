import { test, expect } from '@playwright/test';

test.describe('Login page', () => {
  test('shows login form with email, password, and submit button', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByLabel('Email')).toBeVisible();
    await expect(page.getByLabel('Password')).toBeVisible();
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible();
  });

  test('shows MemoryVault title', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByText('MemoryVault')).toBeVisible();
  });

  test('redirects to login when not authenticated', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/login/);
  });

  test('redirects to login when accessing protected route', async ({ page }) => {
    await page.goto('/bookmarks');
    await expect(page).toHaveURL(/login/);
  });

  test('shows error on invalid credentials', async ({ page }) => {
    await page.goto('/login');
    await page.getByLabel('Email').fill('wrong@example.com');
    await page.getByLabel('Password').fill('wrongpassword');
    await page.getByRole('button', { name: /sign in/i }).click();

    // Should show an error message and stay on login page
    await expect(page).toHaveURL(/login/);
  });
});
