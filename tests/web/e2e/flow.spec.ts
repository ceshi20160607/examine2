import { test, expect } from '../fixtures/auth';
import { enterFirstSystem } from '../fixtures/system';

test.describe('flow', () => {
  test('flow temps page loads', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    await page.goto('/flow/temps');
    await expect(page.getByRole('heading', { name: /流程模板/ })).toBeVisible({ timeout: 15_000 });
  });

  test('flow inbox page loads', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    await page.goto('/flow/inbox');
    await expect(page.getByRole('heading', { name: '流程待办箱' })).toBeVisible({ timeout: 15_000 });
  });
});
