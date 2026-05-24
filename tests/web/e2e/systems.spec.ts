import { test, expect } from '../fixtures/auth';

test.describe('systems', () => {
  test('create system and enter', async ({ loggedInPage: page }) => {
    await page.goto('/systems');
    const name = `ui_sys_${Date.now()}`;
    await page.getByTestId('system-name-input').fill(name);
    await page.getByTestId('system-create-btn').click();
    const systemBtn = page.locator('button.list__btn').filter({ hasText: name });
    await expect(systemBtn).toBeVisible({ timeout: 15_000 });
    await systemBtn.click();
    await expect(page).toHaveURL(/\/(apps|upload|flow)/, { timeout: 15_000 });
  });
});
