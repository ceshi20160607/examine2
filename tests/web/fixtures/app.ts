import { expect, type Page } from '@playwright/test';
import { postApi } from './api';
import { enterFirstSystem } from './system';

/** 若当前系统尚无应用（bootstrap 未完成时），补建 default 应用 */
export async function ensureDefaultApp(page: Page): Promise<void> {
  const apps = await postApi<Array<{ id?: string }>>(page, '/v1/system/module/meta/apps');
  if (Array.isArray(apps) && apps.length > 0) return;
  await postApi(page, '/v1/system/module/meta/apps/upsert', {
    appCode: 'default',
    appName: 'Default',
    status: 1,
  });
}

/** 进入系统并打开第一个应用，返回 appId */
export async function openFirstApp(page: Page): Promise<string> {
  await enterFirstSystem(page);
  await ensureDefaultApp(page);
  await page.goto('/apps');
  const link = page.locator('a.list__link').first();
  await expect(link).toBeVisible({ timeout: 15_000 });
  await link.click();
  await expect(page).toHaveURL(/\/apps\/\d+/);
  const m = page.url().match(/\/apps\/(\d+)/);
  if (!m) throw new Error('appId not found in URL');
  return m[1];
}
