import { test, expect } from '../fixtures/auth';
import { openFirstApp } from '../fixtures/app';

const hubRoutes: { suffix: string; heading: RegExp }[] = [
  { suffix: '', heading: /应用 #/ },
  { suffix: '/models', heading: /模型/ },
  { suffix: '/dicts', heading: /字典/ },
  { suffix: '/depts', heading: /部门/ },
  { suffix: '/relations', heading: /模型关系/ },
  { suffix: '/pages', heading: /页面设计/ },
  { suffix: '/list-views', heading: /列表视图/ },
  { suffix: '/exports', heading: /导出模板/ },
  { suffix: '/flow-bindings', heading: /流程绑定/ },
  { suffix: '/rbac', heading: /RBAC/ },
  { suffix: '/menus', heading: /运行时菜单/ },
];

test.describe('app hub modules', () => {
  test('all hub pages load', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    for (const r of hubRoutes) {
      await page.goto(`/apps/${appId}${r.suffix}`);
      await expect(page.getByRole('heading', { name: r.heading })).toBeVisible({ timeout: 15_000 });
    }
  });
});
