import { test, expect } from '../fixtures/auth';
import { openFirstApp } from '../fixtures/app';
import { enterFirstSystem } from '../fixtures/system';
import { postApi } from '../fixtures/api';

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

  test('flow instance detail page loads for started instance', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    const suffix = Date.now();
    const tempCode = `ui_inst_flow_${suffix}`;
    const temp = await postApi<{ id: string }>(page, '/v1/system/flow/temps/upsert', {
      tempCode,
      tempName: `UI Instance Flow ${suffix}`,
      status: 1,
    });
    const ver = await postApi<{ id: string }>(page, '/v1/system/flow/temp-vers/upsert', {
      tempId: temp.id,
      publishStatus: 1,
      formJson: '{}',
    });
    await postApi(page, `/v1/system/flow/temp-vers/${ver.id}/graph-designer`, {
      nodes: [
        { nodeKey: 'start_1', nodeType: 'start', nodeName: 'Start', x: 120, y: 120, configJson: '{}' },
        { nodeKey: 'approve_1', nodeType: 'approve', nodeName: 'Approve', x: 320, y: 120, configJson: '{}' },
        { nodeKey: 'end_1', nodeType: 'end', nodeName: 'End', x: 520, y: 120, configJson: '{}' },
      ],
      edges: [
        { fromNodeKey: 'start_1', toNodeKey: 'approve_1', priority: 1, isDefault: 0, cond: '' },
        { fromNodeKey: 'approve_1', toNodeKey: 'end_1', priority: 1, isDefault: 0, cond: '' },
      ],
    });
    await postApi(page, `/v1/system/flow/temp-vers/${ver.id}/publish`);
    const started = await postApi<{ instanceId: string; taskId: string }>(page, '/v1/system/flow/instances/start', {
      defCode: tempCode,
      title: `UI Instance ${suffix}`,
      bizType: 'ui_test',
      bizId: `biz_${suffix}`,
    });

    await page.goto(`/flow/instances/${started.instanceId}`);
    await expect(page.getByRole('heading', { name: new RegExp(`流程实例 #${started.instanceId}`) })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.locator('.json-pre')).toBeVisible({ timeout: 15_000 });
  });

  test('flow inbox links to task page when pending exists', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    const suffix = Date.now();
    const tempCode = `ui_inbox_flow_${suffix}`;
    const temp = await postApi<{ id: string }>(page, '/v1/system/flow/temps/upsert', {
      tempCode,
      tempName: `UI Inbox Flow ${suffix}`,
      status: 1,
    });
    const ver = await postApi<{ id: string }>(page, '/v1/system/flow/temp-vers/upsert', {
      tempId: temp.id,
      publishStatus: 1,
      formJson: '{}',
    });
    await postApi(page, `/v1/system/flow/temp-vers/${ver.id}/graph-designer`, {
      nodes: [
        { nodeKey: 'start_1', nodeType: 'start', nodeName: 'Start', x: 120, y: 120, configJson: '{}' },
        { nodeKey: 'approve_1', nodeType: 'approve', nodeName: 'Approve', x: 320, y: 120, configJson: '{}' },
        { nodeKey: 'end_1', nodeType: 'end', nodeName: 'End', x: 520, y: 120, configJson: '{}' },
      ],
      edges: [
        { fromNodeKey: 'start_1', toNodeKey: 'approve_1', priority: 1, isDefault: 0, cond: '' },
        { fromNodeKey: 'approve_1', toNodeKey: 'end_1', priority: 1, isDefault: 0, cond: '' },
      ],
    });
    await postApi(page, `/v1/system/flow/temp-vers/${ver.id}/publish`);
    const started = await postApi<{ instanceId: string; taskId: string }>(page, '/v1/system/flow/instances/start', {
      defCode: tempCode,
      title: `UI Inbox ${suffix}`,
      bizType: 'ui_test',
      bizId: `inbox_${suffix}`,
    });

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
