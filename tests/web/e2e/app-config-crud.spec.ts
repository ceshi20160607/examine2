import { test, expect, type Page } from '../fixtures/auth';
import { openFirstApp } from '../fixtures/app';

async function answerDialogs(page: Page, answers: string[]) {
  let index = 0;
  const handler = async (dialog: { accept: (promptText?: string) => Promise<void> }) => {
    await dialog.accept(answers[index++] ?? '');
  };
  page.on('dialog', handler);
  return () => page.removeListener('dialog', handler);
}

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

test.describe('app configuration CRUD pages', () => {
  test('model and field can be created from UI', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    const suffix = Date.now();
    const modelCode = `ui_model_${suffix}`;
    const fieldCode = `ui_field_${suffix}`;
    const removeDialogs = await answerDialogs(page, [modelCode, `UI Model ${suffix}`, fieldCode, `UI Field ${suffix}`, 'TEXT']);

    try {
      await page.goto(`/apps/${appId}/models`);
      await page.getByRole('button', { name: '新建模型' }).click();
      const modelRow = page.getByRole('row').filter({ hasText: modelCode });
      await expect(modelRow).toBeVisible({ timeout: 15_000 });

      await modelRow.getByRole('link', { name: '字段' }).click();
      await expect(page.getByRole('heading', { name: /字段 · model/ })).toBeVisible({ timeout: 15_000 });
      await page.getByRole('button', { name: '新建字段' }).click();
      await expect(page.getByRole('row').filter({ hasText: fieldCode })).toBeVisible({ timeout: 15_000 });
    } finally {
      removeDialogs();
    }
  });

  test('dict item can be created from UI', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    const suffix = Date.now();
    const dictCode = `ui_dict_${suffix}`;
    const itemValue = `v_${suffix}`;
    const removeDialogs = await answerDialogs(page, [dictCode, `UI Dict ${suffix}`, itemValue, `Label ${suffix}`]);

    try {
      await page.goto(`/apps/${appId}/dicts`);
      await page.getByRole('button', { name: '新建字典' }).click();
      const dictRow = page.getByRole('row').filter({ hasText: dictCode });
      await expect(dictRow).toBeVisible({ timeout: 15_000 });

      await dictRow.getByRole('button', { name: '管理项' }).click();
      await expect(page.getByRole('heading', { name: new RegExp(`字典项 · ${dictCode}`) })).toBeVisible({ timeout: 15_000 });
      await page.getByRole('button', { name: '新增项' }).click();
      await expect(page.getByRole('row').filter({ hasText: itemValue })).toBeVisible({ timeout: 15_000 });
    } finally {
      removeDialogs();
    }
  });

  test('dept can be created from UI', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    const suffix = Date.now();
    const deptCode = `ui_dept_${suffix}`;
    const removeDialogs = await answerDialogs(page, [deptCode, `UI Dept ${suffix}`, '0']);

    try {
      await page.goto(`/apps/${appId}/depts`);
      await page.getByRole('button', { name: '新建部门' }).click();
      await expect(page.getByRole('row').filter({ hasText: deptCode })).toBeVisible({ timeout: 15_000 });
    } finally {
      removeDialogs();
    }
  });

  test('list view columns and filter templates can be configured', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    const suffix = Date.now();
    const model = await postApi<{ id: string }>(page, '/v1/system/module/meta/models/upsert', {
      appId,
      modelCode: `ui_lv_model_${suffix}`,
      modelName: `UI List Model ${suffix}`,
      status: 1,
    });
    const field = await postApi<{ id: string }>(page, '/v1/system/module/meta/fields/upsert', {
      appId,
      modelId: model.id,
      fieldCode: `ui_lv_field_${suffix}`,
      fieldName: `UI List Field ${suffix}`,
      fieldType: 'TEXT',
      status: 1,
    });
    const viewCode = `ui_view_${suffix}`;
    const filterCode = `ui_filter_${suffix}`;
    const removeDialogs = await answerDialogs(page, [viewCode, `UI View ${suffix}`, String(field.id), `UI Column ${suffix}`, filterCode, `UI Filter ${suffix}`]);

    try {
      await page.goto(`/apps/${appId}/list-views?modelId=${model.id}`);
      await page.getByRole('button', { name: '新建视图' }).click();
      const viewRow = page.getByRole('row').filter({ hasText: viewCode });
      await expect(viewRow).toBeVisible({ timeout: 15_000 });

      await viewRow.getByRole('button', { name: '列配置' }).click();
      await page.getByRole('button', { name: '添加列' }).click();
      await expect(page.getByRole('row').filter({ hasText: `UI Column ${suffix}` })).toBeVisible({ timeout: 15_000 });

      await page.getByRole('button', { name: '新建筛选模板' }).click();
      await expect(page.getByRole('row').filter({ hasText: filterCode })).toBeVisible({ timeout: 15_000 });
    } finally {
      removeDialogs();
    }
  });

  test('export template field config can be loaded', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    const suffix = Date.now();
    const model = await postApi<{ id: string }>(page, '/v1/system/module/meta/models/upsert', {
      appId,
      modelCode: `ui_export_model_${suffix}`,
      modelName: `UI Export Model ${suffix}`,
      status: 1,
    });
    const field = await postApi<{ id: string }>(page, '/v1/system/module/meta/fields/upsert', {
      appId,
      modelId: model.id,
      fieldCode: `ui_export_field_${suffix}`,
      fieldName: `UI Export Field ${suffix}`,
      fieldType: 'TEXT',
      status: 1,
    });
    const tplCode = `ui_export_${suffix}`;
    const removeDialogs = await answerDialogs(page, [tplCode, `UI Export ${suffix}`]);

    try {
      await page.goto(`/apps/${appId}/exports?modelId=${model.id}`);
      await page.getByRole('button', { name: '新建模板' }).click();
      const tplRow = page.getByRole('row').filter({ hasText: tplCode });
      await expect(tplRow).toBeVisible({ timeout: 15_000 });

      const tplId = (await tplRow.locator('td').first().innerText()).trim();
      await postApi(page, '/v1/system/module/exports/fields/upsert', {
        tplId,
        fieldId: field.id,
        colTitle: `UI Export Field ${suffix}`,
        sortNo: 0,
      });

      await tplRow.getByRole('button', { name: '字段' }).click();
      await expect(page.getByRole('heading', { name: /模板字段 · #/ })).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('row').filter({ hasText: `UI Export Field ${suffix}` })).toBeVisible({ timeout: 15_000 });
    } finally {
      removeDialogs();
    }
  });
});
