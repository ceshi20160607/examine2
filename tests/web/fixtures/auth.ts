import { test as base } from '@playwright/test';

/** 使用 auth.setup 保存的登录态，避免每个用例重复登录 */
export const test = base.extend({
  loggedInPage: async ({ page }, use) => {
    await use(page);
  },
});

export { expect } from '@playwright/test';
