import { expect, type Page } from '@playwright/test';

/** 进入列表中第一个自建系统（无则先创建） */
export async function enterFirstSystem(page: Page) {
  if (!page.url().includes('/systems')) {
    await page.goto('/systems');
  }
  const firstBtn = page.locator('button.list__btn').first();
  const visible = await firstBtn.isVisible().catch(() => false);
  if (!visible) {
    const name = `ui_auto_${Date.now()}`;
    await page.getByTestId('system-name-input').fill(name);
    await page.getByTestId('system-create-btn').click();
    await expect(page.locator('button.list__btn').filter({ hasText: name })).toBeVisible({ timeout: 15_000 });
    await page.locator('button.list__btn').filter({ hasText: name }).click();
  } else {
    await firstBtn.click();
  }
  await expect(page).toHaveURL(/\/(apps|upload|flow)/, { timeout: 15_000 });
}
