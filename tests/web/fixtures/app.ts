import { expect, type Page } from '@playwright/test';
import { enterFirstSystem } from './system';

/** 进入系统并打开第一个应用，返回 appId */
export async function openFirstApp(page: Page): Promise<string> {
  await enterFirstSystem(page);
  await page.goto('/apps');
  const link = page.locator('a.list__link').first();
  await expect(link).toBeVisible({ timeout: 15_000 });
  await link.click();
  await expect(page).toHaveURL(/\/apps\/\d+/);
  const m = page.url().match(/\/apps\/(\d+)/);
  if (!m) throw new Error('appId not found in URL');
  return m[1];
}
