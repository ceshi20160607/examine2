import { test, expect } from '../fixtures/auth';
import { enterFirstSystem } from '../fixtures/system';

test.describe('apps', () => {
  test('apps page lists default app after enter system', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    await page.goto('/apps');
    await expect(page.getByRole('heading', { name: '应用' })).toBeVisible();
    await expect(page.locator('a.list__link').first()).toBeVisible({ timeout: 15_000 });
  });
});
