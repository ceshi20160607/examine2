import { test, expect } from '../fixtures/auth';
import { openFirstApp } from '../fixtures/app';

test.describe('records pages', () => {
  test('record form with query opens', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    await page.goto(`/apps/${appId}/models`);
    await expect(page.getByRole('heading', { name: /模型/ })).toBeVisible();
    const modelLink = page.locator(`a[href*="/apps/${appId}/models/"]`).first();
    let modelId = '0';
    if (await modelLink.isVisible().catch(() => false)) {
      const href = await modelLink.getAttribute('href');
      const m = href?.match(/models\/(\d+)\//);
      if (m) modelId = m[1];
    }
    await page.goto(`/records/form?appId=${appId}&modelId=${modelId}`);
    await expect(page.getByRole('heading', { name: '新建记录' })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(/appId=.*modelId=/)).toBeVisible();
  });
});
