import { test, expect } from '../fixtures/auth';
import { enterFirstSystem } from '../fixtures/system';

test.describe('flow pages', () => {
  test.beforeEach(async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
  });

  test('flow start page', async ({ page }) => {
    await page.goto('/flow/start');
    await expect(page.getByRole('heading', { name: '发起流程' })).toBeVisible();
  });

  test('flow instances page', async ({ page }) => {
    await page.goto('/flow/instances');
    await expect(page.getByRole('heading', { name: '流程实例' })).toBeVisible();
  });

  test('flow temp detail when templates exist', async ({ page }) => {
    await page.goto('/flow/temps');
    await expect(page.getByRole('heading', { name: '流程模板' })).toBeVisible();
    const link = page.locator('a[href*="/flow/temps/"]').first();
    if (await link.isVisible().catch(() => false)) {
      await link.click();
      await expect(page.getByRole('heading', { name: /流程模板版本/ })).toBeVisible({ timeout: 15_000 });
    }
  });
});
