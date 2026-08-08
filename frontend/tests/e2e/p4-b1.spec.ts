import { execFileSync } from 'node:child_process'

import { expect, test, type Page } from '@playwright/test'

test.use({ trace: 'off' })
test.setTimeout(180_000)

const memberUsername = process.env.E2E_P4_B1_USERNAME ?? process.env.E2E_P4_A2_USERNAME
const memberPassword = process.env.E2E_P4_B1_PASSWORD ?? process.env.E2E_P4_A2_PASSWORD
const systemId = process.env.E2E_P4_B1_SYSTEM_ID ?? process.env.E2E_P4_A2_SYSTEM_ID
const databaseHost = process.env.E2E_DB_HOST
const databaseUsername = process.env.E2E_DB_USERNAME
const databasePassword = process.env.E2E_DB_PASSWORD
const restartRecordId = process.env.E2E_P4_B1_RESTART_RECORD_ID

function requireEnvironment() {
  test.skip(!memberUsername || !memberPassword, 'P4-B1 ordinary-member credentials are required')
  test.skip(!systemId, 'P4-B1 stable system id is required')
  test.skip(!databaseHost || !databaseUsername || !databasePassword, 'Database cleanup credentials are required')
}

async function login(page: Page) {
  await page.goto('/auth/login')
  await page.getByLabel('账号').fill(memberUsername!)
  await page.getByLabel('密码').fill(memberPassword!)
  await Promise.all([
    page.waitForURL(/\/platform\/workbench$/),
    page.getByRole('button', { name: '登录', exact: true }).click(),
  ])
}

async function choose(page: Page, fieldName: string, optionName: string) {
  const field = page.locator('.runtime-form-field').filter({ has: page.getByText(fieldName, { exact: true }) })
  await field.locator('.ant-select-selector').click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option')
    .filter({ hasText: optionName }).click()
}

async function waitSaved(page: Page) {
  await expect(page.locator('.runtime-save-state')).toContainText(/草稿已保存|已保存/, { timeout: 20_000 })
}

async function externalUpdate(page: Page, recordId: string, value: string) {
  const path = `/api/v1/systems/${systemId}/runtime/modules/work_order/records/${recordId}`
  const detailResponse = await page.request.get(path)
  expect(detailResponse.ok()).toBe(true)
  const envelope = await detailResponse.json()
  const record = envelope.data as {
    version: number
    title: string
    schemaVersionId: string
    values: Array<{ fieldCode: string; fieldName: string; value: unknown }>
  }
  const values = Object.fromEntries(record.values.map((field) => [field.fieldCode, field.value]))
  const subject = record.values.find((field) => field.fieldName === '工单主题')
  expect(subject).toBeTruthy()
  values[subject!.fieldCode] = value
  const csrfCookie = (await page.context().cookies()).find((cookie) => cookie.name === 'EXAMINE_CSRF')
  expect(csrfCookie).toBeTruthy()
  const response = await page.request.put(path, {
    headers: {
      'X-CSRF-Token': decodeURIComponent(csrfCookie!.value),
      'Idempotency-Key': crypto.randomUUID(),
    },
    data: {
      schemaVersionId: record.schemaVersionId,
      title: record.title,
      expectedVersion: record.version,
      values,
    },
  })
  expect(response.ok()).toBe(true)
}

function cleanupDraft(recordId: string) {
  if (!/^\d+$/.test(recordId)) return
  execFileSync('docker', [
    'run', '--rm', 'mysql:8.0.44', 'mysql',
    `--host=${databaseHost}`, '--port=3306', `--user=${databaseUsername}`, `--password=${databasePassword}`,
    '-e', `USE examine2;
      DELETE s FROM un_module_record_search s
        JOIN un_module_record r ON r.record_id=s.record_id
        WHERE s.record_id=${recordId} AND r.status='DRAFT';
      DELETE i FROM un_module_record_index i
        JOIN un_module_record r ON r.record_id=i.record_id
        WHERE i.record_id=${recordId} AND r.status='DRAFT';
      DELETE v FROM un_module_record_value v
        JOIN un_module_record r ON r.record_id=v.record_id
        WHERE v.record_id=${recordId} AND r.status='DRAFT';
      DELETE FROM un_module_record WHERE record_id=${recordId} AND status='DRAFT';`,
  ], { stdio: 'pipe' })
}

test('ordinary member edits, autosaves, restores and resolves a CAS conflict', async ({ page }) => {
  requireEnvironment()
  const errors: string[] = []
  let draftId = ''
  let copiedDraftId = ''
  let expectedAutosaveFailure = false
  let failNextAutosave = false
  let delayNextAutosave = false
  let autosaveRequests = 0
  let activeAutosaves = 0
  let maxActiveAutosaves = 0

  page.on('console', (message) => {
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) {
      errors.push(`console: ${message.text()}`)
    }
  })
  page.on('pageerror', (error) => errors.push(`page: ${error.message}`))
  page.on('response', (response) => {
    if (response.status() >= 500 && !(expectedAutosaveFailure && response.url().endsWith(':autosave'))) {
      errors.push(`http ${response.status()}: ${response.url()}`)
    }
  })

  await page.route(/:autosave$/, async (route) => {
    autosaveRequests += 1
    activeAutosaves += 1
    maxActiveAutosaves = Math.max(maxActiveAutosaves, activeAutosaves)
    try {
      if (failNextAutosave) {
        failNextAutosave = false
        expectedAutosaveFailure = true
        await route.fulfill({
          status: 503,
          contentType: 'application/json',
          body: JSON.stringify({
            code: 'AUTOSAVE_TEST_FAILURE',
            message: '模拟自动保存失败',
            data: null,
            requestId: 'p4-b1-e2e',
            traceId: 'p4-b1-e2e',
            errors: [],
          }),
        })
        return
      }
      if (delayNextAutosave) {
        delayNextAutosave = false
        await new Promise((resolve) => setTimeout(resolve, 1200))
      }
      const response = await route.fetch()
      await route.fulfill({ response })
    } finally {
      activeAutosaves -= 1
    }
  })

  try {
    await login(page)
    await page.goto(`/systems/${systemId}/workbench?module=work_order`)
    await expect(page.getByRole('heading', { name: '现场工单' })).toBeVisible()
    await page.getByRole('button', { name: '新建记录' }).click()
    await expect(page.locator('.ant-drawer.ant-drawer-open .ant-drawer-title')).toHaveText('新建现场工单')

    const title = `P4-B1-${test.info().project.name}-${Date.now()}`
    await page.getByLabel('记录标题').fill(title)
    await expect(page.locator('.runtime-save-state')).toContainText('未保存')
    await waitSaved(page)
    draftId = new URL(page.url()).searchParams.get('draft') ?? ''
    expect(draftId).toMatch(/^\d+$/)
    await expect(page).toHaveURL(/mode=edit/)

    delayNextAutosave = true
    const beforeCoalescing = autosaveRequests
    await page.getByLabel('工单主题').fill('单飞保存-第一版')
    await page.getByLabel('工单主题').press('Tab')
    await expect(page.locator('.runtime-save-state')).toContainText('正在保存')
    await page.getByLabel('处理说明').fill('保存进行中产生的后续修改必须合并。')
    await page.getByLabel('处理说明').press('Tab')
    await waitSaved(page)
    await expect.poll(() => autosaveRequests - beforeCoalescing).toBe(2)
    expect(maxActiveAutosaves).toBe(1)

    await page.locator('.runtime-form-field').filter({ hasText: '预计费用' }).locator('input').fill('1688.50')
    await page.locator('input[type="date"]').fill('2026-08-18')
    await page.locator('input[type="datetime-local"]').fill('2026-08-10T09:15')
    await choose(page, '优先级', '紧急')
    await choose(page, '负责人', 'Admin')
    await choose(page, '负责部门', '业务运营部')
    await page.getByRole('button', { name: '保存草稿' }).click()
    await waitSaved(page)

    await page.reload()
    await expect(page.locator('.ant-drawer.ant-drawer-open .ant-drawer-title')).toContainText('编辑草稿')
    await expect(page.getByLabel('工单主题')).toHaveValue('单飞保存-第一版')
    await expect(page.getByLabel('处理说明')).toHaveValue('保存进行中产生的后续修改必须合并。')
    await expect(page.locator('.runtime-save-state')).toContainText('已恢复服务端草稿')

    failNextAutosave = true
    expectedAutosaveFailure = true
    await page.getByLabel('处理说明').fill('失败时必须保留的本地内容')
    await expect(page.locator('.runtime-save-state')).toContainText('保存失败', { timeout: 20_000 })
    await expect(page.getByLabel('处理说明')).toHaveValue('失败时必须保留的本地内容')
    expectedAutosaveFailure = false
    await page.getByRole('button', { name: '重试' }).click()
    await waitSaved(page)

    const serverValue1 = '服务端并发版本-A'
    await externalUpdate(page, draftId, serverValue1)
    await page.getByLabel('工单主题').fill('本地冲突版本-A')
    await page.getByLabel('工单主题').press('Tab')
    await expect(page.getByText('服务端已有较新版本')).toBeVisible()
    await expect(page.getByText(/values\.subject|values\./)).toBeVisible()
    await page.getByRole('button', { name: '重新加载服务端草稿' }).click()
    await expect(page.getByLabel('工单主题')).toHaveValue(serverValue1)

    const serverValue2 = '服务端并发版本-B'
    await externalUpdate(page, draftId, serverValue2)
    await page.getByLabel('工单主题').fill('复制保留的本地版本')
    await page.getByLabel('工单主题').press('Tab')
    await expect(page.getByText('服务端已有较新版本')).toBeVisible()
    await page.getByRole('button', { name: '复制为新草稿' }).click()
    await waitSaved(page)
    copiedDraftId = new URL(page.url()).searchParams.get('draft') ?? ''
    expect(copiedDraftId).toMatch(/^\d+$/)
    expect(copiedDraftId).not.toBe(draftId)
    await expect(page.getByLabel('工单主题')).toHaveValue('复制保留的本地版本')

    await page.getByRole('button', { name: '激活记录' }).click()
    await expect(page).toHaveURL(new RegExp(`record=${copiedDraftId}`))
    await expect(page.locator('.ant-drawer.ant-drawer-open').getByText('复制保留的本地版本', { exact: true })).toBeVisible()
    await page.getByTitle('编辑记录').click()
    await expect(page.locator('.ant-drawer.ant-drawer-open .ant-drawer-title').filter({ hasText: '编辑记录' })).toBeVisible()
    const activeTitle = `${title}-已编辑`
    await page.getByLabel('记录标题').fill(activeTitle)
    await page.getByRole('button', { name: '保存修改' }).click()
    await waitSaved(page)
    await page.reload()
    await expect(page.locator('.ant-drawer.ant-drawer-open .ant-drawer-title').filter({ hasText: '编辑记录' })).toBeVisible()
    await expect(page.getByLabel('记录标题')).toHaveValue(activeTitle)

    await page.screenshot({
      path: `../.cursor/session/evidence/p4-b1/edit-autosave-${test.info().project.name}.png`,
    })
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    expect(errors).toEqual([])
  } finally {
    if (draftId) cleanupDraft(draftId)
    if (copiedDraftId) cleanupDraft(copiedDraftId)
  }
})

test('ordinary member reads the persisted ACTIVE edit after backend restart', async ({ page }) => {
  test.skip(!memberUsername || !memberPassword, 'P4-B1 ordinary-member credentials are required')
  test.skip(!systemId || !restartRecordId, 'P4-B1 restart record id is required')
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('response', (response) => {
    if (response.status() >= 500) errors.push(`http ${response.status()}: ${response.url()}`)
  })

  await login(page)
  await page.goto(`/systems/${systemId}/workbench?module=work_order&mode=edit&record=${restartRecordId}`)
  await expect(page.locator('.ant-drawer.ant-drawer-open .ant-drawer-title').filter({ hasText: '编辑记录' })).toBeVisible()
  await expect(page.getByLabel('记录标题')).toHaveValue(/^P4-B1-desktop-\d+-已编辑$/)
  await expect(page.getByLabel('工单主题')).toHaveValue('复制保留的本地版本')
  await expect(page.getByLabel('处理说明')).toHaveValue('失败时必须保留的本地内容')
  await expect(page.locator('.runtime-save-state')).toContainText('已保存')
  await page.screenshot({
    path: `../.cursor/session/evidence/p4-b1/restart-readback-${test.info().project.name}.png`,
  })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])
})
