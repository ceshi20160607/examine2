import { test, expect, type Page } from '../fixtures/auth';
import { openFirstApp } from '../fixtures/app';

async function postApi<T = Record<string, unknown>>(page: Page, path: string, body: unknown): Promise<T> {
  return page.evaluate(
    async ({ path, body }) => {
      const token = window.localStorage.getItem('examine_token') || '';
      const res = await fetch(path, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${token}`,
        },
        body: JSON.stringify(body),
      });
      const json = await res.json();
      if (!json || json.code !== 0) {
        throw new Error(json?.message || `API ${path} failed`);
      }
      return json.data;
    },
    { path, body }
  );
}

test.describe('advanced app configuration pages', () => {
  test('model relation list shows configured relation', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    const suffix = Date.now();
    const parent = await postApi<{ id: string }>(page, '/v1/system/module/meta/models/upsert', {
      appId,
      modelCode: `000_ui_rel_a_${suffix}`,
      modelName: `UI Relation Parent ${suffix}`,
      status: 1,
    });
    const child = await postApi<{ id: string }>(page, '/v1/system/module/meta/models/upsert', {
      appId,
      modelCode: `000_ui_rel_b_${suffix}`,
      modelName: `UI Relation Child ${suffix}`,
      status: 1,
    });
    await postApi(page, '/v1/system/module/meta/fields/upsert', {
      appId,
      modelId: child.id,
      fieldCode: 'parentId',
      fieldName: 'Parent ID',
      fieldType: 'TEXT',
      status: 1,
    });
    await postApi(page, '/v1/system/module/meta/relations/upsert', {
      appId,
      srcModelId: parent.id,
      dstModelId: child.id,
      relType: '1-n',
      configJson: JSON.stringify({ fkField: 'parentId' }),
    });

    await page.goto(`/apps/${appId}/relations`);
    await expect(page.getByRole('heading', { name: /模型关系/ })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('row').filter({ hasText: `UI Relation Parent ${suffix}` })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('row').filter({ hasText: '1-n' })).toBeVisible({ timeout: 15_000 });
  });

  test('flow binding list shows configured binding', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    const suffix = Date.now();
    const model = await postApi<{ id: string }>(page, '/v1/system/module/meta/models/upsert', {
      appId,
      modelCode: `ui_bind_model_${suffix}`,
      modelName: `UI Bind Model ${suffix}`,
      status: 1,
    });
    const temp = await postApi<{ id: string }>(page, '/v1/system/flow/temps/upsert', {
      tempCode: `ui_bind_flow_${suffix}`,
      tempName: `UI Bind Flow ${suffix}`,
      status: 1,
    });
    await postApi(page, '/v1/system/module/flow-bindings/upsert', {
      appId,
      modelId: model.id,
      triggerAction: `on_ui_${suffix}`,
      tempId: temp.id,
      status: 1,
    });

    await page.goto(`/apps/${appId}/flow-bindings?modelId=${model.id}`);
    await page.getByRole('button', { name: '加载绑定' }).click();
    await expect(page.getByRole('row').filter({ hasText: `on_ui_${suffix}` })).toBeVisible({ timeout: 15_000 });
  });

  test('runtime menu opens configured list page', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    const suffix = Date.now();
    const model = await postApi<{ id: string }>(page, '/v1/system/module/meta/models/upsert', {
      appId,
      modelCode: `ui_menu_model_${suffix}`,
      modelName: `UI Menu Model ${suffix}`,
      status: 1,
    });
    const pageDef = await postApi<{ id: string }>(page, '/v1/system/module/pages/upsert', {
      appId,
      pageCode: `ui_menu_page_${suffix}`,
      pageName: `UI Menu Page ${suffix}`,
      pageType: 'list',
      configJson: JSON.stringify({ modelId: model.id }),
      status: 1,
    });
    const menuName = `UI Menu ${suffix}`;
    await postApi(page, `/v1/system/module/rbac/apps/${appId}/menus/upsert`, {
      parentId: 0,
      menuName,
      pageId: pageDef.id,
      sortNo: 0,
      visibleFlag: 1,
    });

    await page.goto(`/apps/${appId}/menus`);
    const menuButton = page.getByRole('button').filter({ hasText: menuName });
    await expect(menuButton).toBeVisible({ timeout: 15_000 });
    await menuButton.click();
    await expect(page.getByRole('heading', { name: 'UI Menu Page ' + suffix })).toBeVisible({ timeout: 15_000 });
    await expect(page).toHaveURL(/\/records/);
  });

  test('export job filter and refresh controls are usable', async ({ loggedInPage: page }) => {
    await openFirstApp(page);
    await page.goto('/export-jobs');
    await expect(page.getByRole('heading', { name: '导出任务' })).toBeVisible({ timeout: 15_000 });
    await page.locator('.filter-select').selectOption('0');
    await expect(page.locator('.filter-select')).toHaveValue('0');
    await page.getByRole('button', { name: '刷新' }).click();
    await expect(page.getByText(/共 \d+ 条/)).toBeVisible({ timeout: 15_000 });
  });

  test('flow task page opens with query ids', async ({ loggedInPage: page }) => {
    await openFirstApp(page);
    await page.goto('/flow/task?instanceId=1&taskId=1');
    await expect(page.getByRole('heading', { name: '流程任务' })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('instanceId=1 taskId=1')).toBeVisible();
    await expect(page.getByRole('button', { name: '同意' })).toBeVisible();
  });
});
