import { test, expect } from '@playwright/test';

test.describe('auth', () => {
  test('login page loads', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('heading', { name: '登录' })).toBeVisible();
    await expect(page.getByTestId('login-submit')).toBeVisible();
  });

  test('register page loads', async ({ page }) => {
    await page.goto('/register');
    await expect(page.getByRole('heading', { name: '注册' })).toBeVisible();
    await expect(page.getByRole('button', { name: '注册' })).toBeVisible();
    await expect(page.getByRole('link', { name: '已有账号？登录' })).toBeVisible();
  });

  test('login redirects to systems', async ({ page }) => {
    const user = process.env.SMOKE_USER || 'admin';
    const pass = process.env.SMOKE_PASS || '123123aa';
    await page.goto('/login');
    await page.getByLabel('用户名').fill(user);
    await page.locator('input[type="password"]').fill(pass);
    await page.getByTestId('login-submit').click();
    await expect(page).toHaveURL(/\/systems/, { timeout: 30_000 });
    await expect(page.getByRole('heading', { name: '选择系统' })).toBeVisible();
  });
});
