import { test, expect } from '../fixtures/auth';
import { enterFirstSystem } from '../fixtures/system';

test.describe('records', () => {
  test('records list page loads', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    await page.goto('/records');
    await expect(page.getByRole('heading', { name: '记录列表' })).toBeVisible({ timeout: 15_000 });
  });
});
