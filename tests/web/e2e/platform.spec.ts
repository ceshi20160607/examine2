import { test, expect } from '../fixtures/auth';
import { enterFirstSystem } from '../fixtures/system';

test.describe('platform routes', () => {
  test('platform inbox page', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    await page.goto('/platform/inbox');
    await expect(page.getByRole('heading', { name: '平台消息 / 待办' })).toBeVisible();
  });

  test('open apps page', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    await page.goto('/platform/open-apps');
    await expect(page.getByRole('heading', { name: '开放应用' })).toBeVisible();
  });

  test('upload page', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    await page.goto('/upload');
    await expect(page.getByRole('heading', { name: '文件上传' })).toBeVisible();
  });

  test('export jobs page', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    await page.goto('/export-jobs');
    await expect(page.getByRole('heading', { name: '导出任务' })).toBeVisible();
  });
});
