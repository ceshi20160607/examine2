import { test, expect, type Page } from '../fixtures/auth';
import { openFirstApp } from '../fixtures/app';
import { enterFirstSystem } from '../fixtures/system';

async function answerDialogs(page: Page, answers: string[]) {
  let index = 0;
  const handler = async (dialog: { accept: (promptText?: string) => Promise<void> }) => {
    await dialog.accept(answers[index++] ?? '');
  };
  page.on('dialog', handler);
  return () => page.removeListener('dialog', handler);
}

async function postApi(page: Page, path: string, body: unknown) {
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

test.describe('deep linked management pages', () => {
  test('page editor opens for a created page', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    const suffix = Date.now();
    const pageCode = `ui_page_${suffix}`;
    const removeDialogs = await answerDialogs(page, [pageCode, `UI Page ${suffix}`, 'list']);

    try {
      await page.goto(`/apps/${appId}/pages`);
      await page.getByRole('button', { name: '新建页面' }).click();
      const row = page.getByRole('row').filter({ hasText: pageCode });
      await expect(row).toBeVisible({ timeout: 15_000 });
      await row.getByRole('link', { name: '编辑' }).click();
      await expect(page.getByRole('heading', { name: /页面编辑 #/ })).toBeVisible({ timeout: 15_000 });
      await expect(page.getByRole('button', { name: '保存页面' })).toBeVisible();
    } finally {
      removeDialogs();
    }
  });

  test('rbac role menu and page permission pages open', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    const suffix = Date.now();
    const roleCode = `ui_role_${suffix}`;

    await page.goto(`/apps/${appId}/rbac`);
    await page.getByPlaceholder('roleCode').fill(roleCode);
    await page.getByPlaceholder('roleName').fill(`UI Role ${suffix}`);
    await page.getByRole('button', { name: '保存角色' }).click();

    const row = page.getByRole('row').filter({ hasText: roleCode });
    await expect(row).toBeVisible({ timeout: 15_000 });

    await row.getByRole('link', { name: '菜单' }).click();
    await expect(page.getByRole('heading', { name: /角色菜单权限/ })).toBeVisible({ timeout: 15_000 });

    await page.goto(`/apps/${appId}/rbac`);
    await expect(row).toBeVisible({ timeout: 15_000 });
    await row.getByRole('link', { name: '页面' }).click();
    await expect(page.getByRole('heading', { name: /角色页面权限/ })).toBeVisible({ timeout: 15_000 });
  });

  test('open app detail page opens after creating app', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    const suffix = Date.now();
    const appName = `UI Open App ${suffix}`;
    const removeDialogs = await answerDialogs(page, [appName, `ui_open_${suffix}`]);

    try {
      await page.goto('/platform/open-apps');
      await page.getByRole('button', { name: '创建应用' }).click();
      const row = page.getByRole('row').filter({ hasText: appName });
      await expect(row).toBeVisible({ timeout: 15_000 });
      await row.getByRole('link', { name: '详情' }).click();
      await expect(page.getByRole('heading', { name: /开放应用 #/ })).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText('开放 API 签名调用（v1）')).toBeVisible();
    } finally {
      removeDialogs();
    }
  });

  test('record detail opens from records list or newly created record', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    await page.goto(`/apps/${appId}/models`);
    let modelLink = page.locator(`a[href*="/apps/${appId}/models/"]`).first();
    if (!(await modelLink.isVisible().catch(() => false))) {
      const suffix = Date.now();
      const removeDialogs = await answerDialogs(page, [`ui_model_${suffix}`, `UI Model ${suffix}`]);
      try {
        await page.getByRole('button', { name: '新建模型' }).click();
        await expect(page.getByRole('row').filter({ hasText: `ui_model_${suffix}` })).toBeVisible({ timeout: 15_000 });
      } finally {
        removeDialogs();
      }
      modelLink = page.locator(`a[href*="/apps/${appId}/models/"]`).first();
    }

    const href = await modelLink.getAttribute('href');
    const modelId = href?.match(/models\/(\d+)\//)?.[1];
    test.skip(!modelId, '模型链接中没有 modelId');

    await page.goto(`/records?appId=${appId}&modelId=${modelId}`);
    let detailLink = page.getByRole('link', { name: '详情' }).first();
    if (!(await detailLink.isVisible().catch(() => false))) {
      await page.goto(`/records/form?appId=${appId}&modelId=${modelId}`);
      await expect(page.getByRole('heading', { name: '新建记录' })).toBeVisible({ timeout: 15_000 });
      await page.getByRole('button', { name: '创建' }).click();
      await expect(page).toHaveURL(/recordId=/, { timeout: 15_000 });
      const recordId = new URL(page.url()).searchParams.get('recordId');
      test.skip(!recordId, '创建记录后没有 recordId');
      await page.goto(`/records/detail?appId=${appId}&modelId=${modelId}&recordId=${recordId}`);
    } else {
      await detailLink.click();
    }

    await expect(page.getByRole('heading', { name: /记录详情 #/ })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('heading', { name: '变更历史' })).toBeVisible();
  });

  test('flow graph designer opens from template detail when a version exists', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    await page.goto('/flow/temps');
    const suffix = Date.now();
    const temp = await postApi(page, '/v1/system/flow/temps/upsert', {
      tempCode: `ui_flow_${suffix}`,
      tempName: `UI Flow ${suffix}`,
      status: 1,
    });
    const ver = await postApi(page, '/v1/system/flow/temp-vers/upsert', {
      tempId: temp.id,
      publishStatus: 1,
      formJson: '{}',
    });

    await page.goto(`/flow/temps/${temp.id}/versions/${ver.id}/designer`);
    await expect(page.getByRole('heading', { name: '流程图设计器' })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('button', { name: '保存' })).toBeVisible();
  });
});
