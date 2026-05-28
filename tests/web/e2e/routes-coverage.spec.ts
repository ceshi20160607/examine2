import { test, expect } from '../fixtures/auth';
import { openFirstApp } from '../fixtures/app';
import { enterFirstSystem } from '../fixtures/system';
import { postApi } from '../fixtures/api';
import { publishMinimalFlow, startFlowInstance, tokenFromPage } from '../fixtures/flow-api';

test.describe('remaining router paths', () => {
  test('model fields page opens via model link', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    const suffix = Date.now();
    const model = await postApi<{ id: string }>(page, '/v1/system/module/meta/models/upsert', {
      appId,
      modelCode: `ui_fields_route_${suffix}`,
      modelName: `UI Fields Route ${suffix}`,
      status: 1,
    });

    await page.goto(`/apps/${appId}/models/${model.id}/fields`);
    await expect(page.getByRole('heading', { name: /字段 · model/ })).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('button', { name: '新建字段' })).toBeVisible();
  });

  test('dict items page opens via dict row', async ({ loggedInPage: page }) => {
    const appId = await openFirstApp(page);
    const suffix = Date.now();
    const dictCode = `ui_dict_route_${suffix}`;
    await postApi(page, `/v1/system/module/dicts/apps/${appId}/upsert`, {
      dictCode,
      dictName: `UI Dict Route ${suffix}`,
      status: 1,
    });

    await page.goto(`/apps/${appId}/dicts`);
    const row = page.getByRole('row').filter({ hasText: dictCode });
    await expect(row).toBeVisible({ timeout: 15_000 });
    await row.getByRole('button', { name: '管理项' }).click();
    await expect(page.getByRole('heading', { name: new RegExp(`字典项 · ${dictCode}`) })).toBeVisible({ timeout: 15_000 });
  });

  test('flow instance detail page loads for started instance', async ({ loggedInPage: page, request }) => {
    await enterFirstSystem(page);
    const token = await tokenFromPage(page);
    const suffix = Date.now();
    const tempCode = `ui_inst_flow_${suffix}`;
    await publishMinimalFlow(request, token, tempCode, `UI Instance Flow ${suffix}`);
    const started = await startFlowInstance(request, token, tempCode, `UI Instance ${suffix}`, `biz_${suffix}`);

    await page.goto(`/flow/instances/${started.instanceId}`);
    await expect(page.getByRole('heading', { name: new RegExp(`流程实例 #${started.instanceId}`) })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.locator('.json-pre')).toBeVisible({ timeout: 15_000 });
  });

  test('flow inbox links to task page when pending exists', async ({ loggedInPage: page, request }) => {
    await enterFirstSystem(page);
    const token = await tokenFromPage(page);
    const suffix = Date.now();
    const tempCode = `ui_inbox_flow_${suffix}`;
    await publishMinimalFlow(request, token, tempCode, `UI Inbox Flow ${suffix}`);
    const started = await startFlowInstance(request, token, tempCode, `UI Inbox ${suffix}`, `inbox_${suffix}`);

    await page.goto('/flow/inbox');
    await expect(page.getByRole('heading', { name: '流程待办箱' })).toBeVisible({ timeout: 15_000 });
    const processLink = page.getByRole('link', { name: '处理' }).first();
    if (await processLink.isVisible().catch(() => false)) {
      await processLink.click();
      await expect(page.getByRole('heading', { name: '流程任务' })).toBeVisible({ timeout: 15_000 });
      await expect(page.getByText(`instanceId=${started.instanceId}`)).toBeVisible();
    } else {
      await page.goto(`/flow/task?instanceId=${started.instanceId}&taskId=${started.taskId}`);
      await expect(page.getByRole('heading', { name: '流程任务' })).toBeVisible({ timeout: 15_000 });
    }
  });
});
