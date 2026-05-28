import { test, expect } from '../fixtures/auth';
import { enterFirstSystem } from '../fixtures/system';
import { publishMinimalFlow, startFlowInstance, tokenFromPage } from '../fixtures/flow-api';

async function answerDialogs(page: import('@playwright/test').Page, answers: string[]) {
  let index = 0;
  const handler = async (dialog: { accept: (promptText?: string) => Promise<void> }) => {
    await dialog.accept(answers[index++] ?? '');
  };
  page.on('dialog', handler);
  return () => page.removeListener('dialog', handler);
}

test.describe('flow runtime actions', () => {
  test('flow can be started from UI', async ({ loggedInPage: page, request }) => {
    await enterFirstSystem(page);
    const token = await tokenFromPage(page);
    const suffix = Date.now();
    const tempCode = `ui_start_${suffix}`;
    await publishMinimalFlow(request, token, tempCode, `UI Start ${suffix}`);

    await page.goto('/flow/start');
    await expect(page.getByRole('heading', { name: '发起流程' })).toBeVisible({ timeout: 15_000 });
    await page.getByRole('button', { name: '刷新模板' }).click();
    const row = page.getByRole('row').filter({ hasText: tempCode });
    await expect(row).toBeVisible({ timeout: 15_000 });

    const removeDialogs = await answerDialogs(page, [`UI Start Title ${suffix}`, '已发起']);
    try {
      await row.getByRole('button', { name: '发起' }).click();
      await expect(page.locator('p.error')).toBeHidden({ timeout: 15_000 });
      await expect(page.locator('pre.pre')).toContainText('instanceId', { timeout: 15_000 });
    } finally {
      removeDialogs();
    }
  });

  test('flow task claim shows result on task page', async ({ loggedInPage: page, request }) => {
    await enterFirstSystem(page);
    const token = await tokenFromPage(page);
    const suffix = Date.now();
    const tempCode = `ui_claim_${suffix}`;
    await publishMinimalFlow(request, token, tempCode, `UI Claim ${suffix}`);
    const started = await startFlowInstance(request, token, tempCode, `UI Claim ${suffix}`, `claim_${suffix}`);

    await page.goto(`/flow/task?instanceId=${started.instanceId}&taskId=${started.taskId}`);
    await expect(page.getByRole('heading', { name: '流程任务' })).toBeVisible({ timeout: 15_000 });
    await page.getByRole('button', { name: '领取', exact: true }).click();
    await expect(page.locator('pre.pre')).toBeVisible({ timeout: 15_000 });
  });
});
