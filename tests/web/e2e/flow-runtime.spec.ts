import { test, expect, type Page } from '../fixtures/auth';
import { enterFirstSystem } from '../fixtures/system';
import { postApi } from '../fixtures/api';

async function answerDialogs(page: Page, answers: string[]) {
  let index = 0;
  const handler = async (dialog: { accept: (promptText?: string) => Promise<void> }) => {
    await dialog.accept(answers[index++] ?? '');
  };
  page.on('dialog', handler);
  return () => page.removeListener('dialog', handler);
}

async function publishSimpleFlow(page: Page, tempCode: string, tempName: string) {
  const temp = await postApi<{ id: string }>(page, '/v1/system/flow/temps/upsert', {
    tempCode,
    tempName,
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
  return tempCode;
}

test.describe('flow runtime actions', () => {
  test('flow can be started from UI', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    const suffix = Date.now();
    const tempCode = `ui_start_${suffix}`;
    await publishSimpleFlow(page, tempCode, `UI Start ${suffix}`);

    await page.goto('/flow/start');
    await expect(page.getByRole('heading', { name: '发起流程' })).toBeVisible({ timeout: 15_000 });
    const row = page.getByRole('row').filter({ hasText: tempCode });
    await expect(row).toBeVisible({ timeout: 15_000 });

    const removeDialogs = await answerDialogs(page, [`UI Start Title ${suffix}`, '已发起']);
    try {
      await row.getByRole('button', { name: '发起' }).click();
      await expect(page.locator('pre.pre')).toContainText('instanceId', { timeout: 15_000 });
    } finally {
      removeDialogs();
    }
  });

  test('flow task claim shows result on task page', async ({ loggedInPage: page }) => {
    await enterFirstSystem(page);
    const suffix = Date.now();
    const tempCode = `ui_claim_${suffix}`;
    await publishSimpleFlow(page, tempCode, `UI Claim ${suffix}`);
    const started = await postApi<{ instanceId: string; taskId: string }>(page, '/v1/system/flow/instances/start', {
      defCode: tempCode,
      title: `UI Claim ${suffix}`,
      bizType: 'ui_test',
      bizId: `claim_${suffix}`,
    });

    await page.goto(`/flow/task?instanceId=${started.instanceId}&taskId=${started.taskId}`);
    await expect(page.getByRole('heading', { name: '流程任务' })).toBeVisible({ timeout: 15_000 });
    await page.getByRole('button', { name: '领取' }).click();
    await expect(page.locator('pre.pre')).toBeVisible({ timeout: 15_000 });
  });
});
