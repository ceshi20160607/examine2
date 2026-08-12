import { randomUUID } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { expect as baseExpect, test, type Locator, type Page } from '@playwright/test'

const expect = baseExpect.configure({ timeout: 20_000 })

test.use({ trace: 'off', actionTimeout: 15_000, navigationTimeout: 30_000 })
test.setTimeout(300_000)

const username = process.env.E2E_P4_B3_USERNAME
const password = process.env.E2E_P4_B3_PASSWORD
const systemId = process.env.E2E_P4_B3_SYSTEM_ID
const restartEvidencePath = process.env.E2E_P4_B3_RESTART_EVIDENCE
const staleFixturePath = process.env.E2E_P4_B3_STALE_FIXTURE

interface Envelope<T> { code: string; message: string; data: T }
interface SchemaField {
  fieldCode: string
  type: string
  options: Array<{ value: string; label: string }>
}
interface RuntimeSchema { schemaVersionId: string; fields: SchemaField[] }
interface RuntimeRecord { recordId: string; version: number; title: string; status: string }

function requireEnvironment() {
  test.skip(!username || !password || !systemId, 'P4-B3 ordinary-member fixture is required')
}

async function loginAs(page: Page, account: string, secret: string) {
  await page.goto('/auth/login')
  await page.getByLabel('账号').fill(account)
  await page.getByLabel('密码').fill(secret)
  await Promise.all([
    page.waitForURL(/\/platform\/workbench$/),
    page.getByRole('button', { name: '登录', exact: true }).click(),
  ])
}

async function login(page: Page) {
  await loginAs(page, username!, password!)
}

async function envelope<T>(page: Page, path: string, method = 'GET', data?: unknown): Promise<T> {
  const csrf = (await page.context().cookies()).find((cookie) => cookie.name === 'EXAMINE_CSRF')
  const response = await page.request.fetch(path, {
    method,
    data,
    headers: method === 'GET' ? undefined : {
      'X-CSRF-Token': decodeURIComponent(csrf?.value ?? ''),
      'Idempotency-Key': randomUUID(),
      'X-Request-ID': randomUUID(),
    },
  })
  const body = await response.json() as Envelope<T>
  expect(response.status(), `${method} ${path}: ${body.code} ${body.message}`).toBeLessThan(400)
  expect(body.code).toBe('OK')
  return body.data
}

async function createActiveRecord(page: Page, schema: RuntimeSchema, title: string, subject: string, amount: number) {
  const option = (code: string) => schema.fields.find((field) => field.fieldCode === code)?.options[0]?.value
  const values: Record<string, unknown> = {
    subject,
    description: `server indexed description ${subject}`,
    amount,
    due_date: amount > 500 ? '2026-09-18' : '2026-08-12',
    scheduled_at: amount > 500 ? '2026-09-10T09:30:00' : '2026-08-10T14:30:00',
  }
  for (const code of ['priority', 'assignee', 'department']) {
    const value = option(code)
    if (value) values[code] = value
  }
  const created = await envelope<RuntimeRecord>(page,
    `/api/v1/systems/${systemId}/runtime/modules/work_order/records`, 'POST', {
      schemaVersionId: schema.schemaVersionId,
      title,
      values,
    })
  return envelope<RuntimeRecord>(page,
    `/api/v1/systems/${systemId}/runtime/modules/work_order/records/${created.recordId}:activate`,
    'POST', { expectedVersion: created.version })
}

async function commandRecord(page: Page, record: RuntimeRecord, command: 'archive' | 'trash') {
  return envelope<RuntimeRecord>(page,
    `/api/v1/systems/${systemId}/runtime/modules/work_order/records/${record.recordId}:${command}`,
    'POST', { expectedVersion: record.version })
}

async function selectAnt(page: Page, select: Locator, text: string) {
  await select.click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option')
    .filter({ hasText: text }).first().click()
}

async function setScope(page: Page, label: string) {
  const response = page.waitForResponse((item) => item.url().includes('/records:query') && item.request().method() === 'POST')
  await page.locator('.ant-segmented-item').filter({ hasText: label }).click()
  expect((await response).ok()).toBe(true)
}

async function openRecordByTitle(page: Page, title: string) {
  const mobile = page.locator('.record-mobile-item').filter({ hasText: title })
  if (await mobile.isVisible()) await mobile.click()
  else await page.locator('.record-table tbody tr').filter({ hasText: title }).getByTitle('查看详情').click()
}

function visibleRecords(page: Page) {
  return page.locator('.record-table-wrap:visible, .record-mobile-list:visible')
}

function captureUnexpectedErrors(page: Page) {
  const errors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) {
      errors.push(`console: ${message.text()}`)
    }
  })
  page.on('pageerror', (error) => errors.push(`page: ${error.message}`))
  page.on('response', (response) => {
    if (response.status() >= 500) errors.push(`http ${response.status()}: ${response.url()}`)
  })
  return errors
}

test('ordinary member uses server query, URL state, scopes and personal saved views', async ({ page }) => {
  requireEnvironment()
  const errors = captureUnexpectedErrors(page)
  await login(page)
  await page.goto(`/systems/${systemId}/workbench?module=work_order`)
  await expect(page.getByRole('heading', { name: '现场工单', exact: true })).toBeVisible()

  const schema = await envelope<RuntimeSchema>(page,
    `/api/v1/systems/${systemId}/runtime/modules/work_order/record-schema`)
  const suffix = `${test.info().project.name.replaceAll('-', '')}${Date.now()}${randomUUID().slice(0, 4)}`
  const token = `queryalpha${suffix}`
  const highTitle = `查询验收高金额 ${suffix}`
  const lowTitle = `查询验收低金额 ${suffix}`
  const archivedTitle = `查询验收归档 ${suffix}`
  const trashTitle = `查询验收回收站 ${suffix}`
  const high = await createActiveRecord(page, schema, highTitle, `${token} urgent`, 980)
  await createActiveRecord(page, schema, lowTitle, `querybeta${suffix} normal`, 120)
  const archived = await createActiveRecord(page, schema, archivedTitle, `archive${suffix}`, 500)
  const trash = await createActiveRecord(page, schema, trashTitle, `trash${suffix}`, 700)
  await commandRecord(page, archived, 'archive')
  await commandRecord(page, trash, 'trash')
  await page.reload()

  const searchResponse = page.waitForResponse((item) => item.url().includes('/records:query'))
  await page.getByPlaceholder('搜索可检索字段').fill(token)
  await page.getByPlaceholder('搜索可检索字段').press('Enter')
  expect((await searchResponse).ok()).toBe(true)
  await expect(visibleRecords(page).getByText(highTitle, { exact: true })).toBeVisible()
  await expect(page.getByText(lowTitle, { exact: true })).toHaveCount(0)
  await expect(page).toHaveURL(new RegExp(`q=${token}`))

  await page.getByRole('button', { name: '筛选、排序和显示列' }).click()
  const drawer = page.locator('.ant-drawer.ant-drawer-open')
  await expect(drawer).toBeVisible()
  if (test.info().project.name === 'mobile') {
    const box = await drawer.boundingBox()
    expect(box?.width).toBeGreaterThanOrEqual(389)
  }
  await drawer.getByRole('button', { name: '添加条件' }).click()
  let rows = drawer.locator('.filter-row')
  await selectAnt(page, rows.nth(0).getByLabel('筛选字段'), '预计费用')
  await selectAnt(page, rows.nth(0).getByLabel('筛选操作符'), '大于等于')
  await rows.nth(0).getByLabel('筛选值').fill('500')
  await drawer.getByRole('button', { name: '添加条件' }).click()
  rows = drawer.locator('.filter-row')
  await selectAnt(page, rows.nth(1).getByLabel('筛选字段'), '工单主题')
  await selectAnt(page, rows.nth(1).getByLabel('筛选操作符'), '包含')
  await rows.nth(1).getByLabel('筛选值').fill('blocked')
  await rows.nth(1).getByText('排除', { exact: true }).click()
  await drawer.getByRole('button', { name: '添加排序' }).click()
  const sortRow = drawer.locator('.sort-row').first()
  await selectAnt(page, sortRow.getByLabel('排序字段'), '预计费用')
  await selectAnt(page, sortRow.getByLabel('排序方向'), '降序')
  const columnSection = drawer.locator('.query-builder-section').filter({ hasText: '显示列' })
  await columnSection.getByText('到期日期', { exact: true }).click()
  const applyResponse = page.waitForResponse((item) => item.url().includes('/records:query'))
  await drawer.getByRole('button', { name: '应用查询' }).click()
  const applied = await applyResponse
  expect(applied.ok()).toBe(true)
  const appliedBody = await applied.json() as Envelope<{ total: number; queryHash: string }>
  expect(appliedBody.data.total).toBe(1)
  expect(appliedBody.data.queryHash).toMatch(/^[a-f0-9]{64}$/)
  await expect(page.getByText('筛选已启用', { exact: true })).toBeVisible()
  await expect(page.getByText('1 项排序', { exact: true })).toBeVisible()
  expect(new URL(page.url()).searchParams.get('filter')).toBeTruthy()
  expect(new URL(page.url()).searchParams.get('sort')).toBeTruthy()
  expect(new URL(page.url()).searchParams.get('columns')).toBeTruthy()

  const viewName = `我的查询 ${suffix}`
  await page.getByRole('button', { name: '保存为新视图' }).click()
  let modal = page.locator('.ant-modal:visible')
  await modal.locator('.saved-view-name input').fill(viewName)
  await modal.locator('.ant-modal-footer .ant-btn-primary').click()
  await expect(modal).toBeHidden()
  const viewId = new URL(page.url()).searchParams.get('viewId') ?? ''
  expect(viewId).toMatch(/^\d+$/)
  await expect(page.getByText(viewName, { exact: true }).first()).toBeVisible()

  const renamedView = `${viewName} 已更新`
  await page.getByRole('button', { name: '更新当前视图' }).click()
  modal = page.locator('.ant-modal:visible')
  await modal.locator('.saved-view-name input').fill(renamedView)
  await modal.locator('.ant-modal-footer .ant-btn-primary').click()
  await expect(modal).toBeHidden()
  await expect(page.getByText(renamedView, { exact: true }).first()).toBeVisible()
  const persistedListUrl = page.url()
  await page.reload()
  await expect(page.getByText(renamedView, { exact: true }).first()).toBeVisible()
  await expect(visibleRecords(page).getByText(highTitle, { exact: true })).toBeVisible()

  await openRecordByTitle(page, highTitle)
  await expect(page.locator('.ant-drawer.ant-drawer-open').getByText(highTitle, { exact: true })).toBeVisible()
  await page.locator('.ant-drawer.ant-drawer-open .ant-drawer-close').click()
  await expect(page).toHaveURL(persistedListUrl)

  await page.getByRole('button', { name: '清除条件' }).click()
  await setScope(page, '已归档')
  await expect(visibleRecords(page).getByText(archivedTitle, { exact: true })).toBeVisible()
  const archivedUrl = page.url()
  await setScope(page, '回收站')
  await expect(visibleRecords(page).getByText(trashTitle, { exact: true })).toBeVisible()
  const trashUrl = page.url()
  await page.goBack()
  await expect(page).toHaveURL(archivedUrl)
  await expect(visibleRecords(page).getByText(archivedTitle, { exact: true })).toBeVisible()
  await page.goForward()
  await expect(page).toHaveURL(trashUrl)
  await expect(visibleRecords(page).getByText(trashTitle, { exact: true })).toBeVisible()
  await setScope(page, '使用中')

  await selectAnt(page, page.getByLabel('保存视图'), renamedView)
  await expect(visibleRecords(page).getByText(highTitle, { exact: true })).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`viewId=${viewId}`))
  await page.screenshot({
    path: `../.cursor/session/evidence/p4-b3/query-saved-view-${test.info().project.name}.png`,
    fullPage: true,
  })

  const evidence = {
    project: test.info().project.name,
    principal: username,
    systemId,
    viewId,
    viewName: renamedView,
    listUrl: new URL(page.url()).pathname + new URL(page.url()).search,
    activeRecordId: high.recordId,
    activeTitle: highTitle,
    archivedRecordId: archived.recordId,
    trashRecordId: trash.recordId,
    capturedAt: new Date().toISOString(),
  }
  writeFileSync(resolve('..', '.cursor/session/evidence/p4-b3', `browser-${test.info().project.name}.json`), JSON.stringify(evidence, null, 2))

  if (test.info().project.name === 'mobile') {
    await page.getByRole('button', { name: '删除当前视图' }).click()
    const confirm = page.locator('.ant-modal:visible')
    await confirm.locator('.ant-modal-confirm-btns .ant-btn-primary').click()
    await expect(confirm).toBeHidden()
    await expect(page.locator('.saved-view-select .ant-select-selection-placeholder')).toHaveText('保存视图')
    const remaining = await envelope<{ items: Array<{ viewId: string }> }>(page,
      `/api/v1/systems/${systemId}/runtime/saved-views?moduleCode=work_order`)
    expect(remaining.items.some((view) => view.viewId === viewId)).toBe(false)
  }
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])
})

test('ordinary member restores the desktop query and saved view after packaged-backend restart', async ({ page }) => {
  test.skip(!username || !password || !systemId || !restartEvidencePath, 'P4-B3 restart evidence is required')
  const evidence = JSON.parse(readFileSync(restartEvidencePath!, 'utf8')) as {
    viewId: string
    viewName: string
    listUrl: string
    activeTitle: string
  }
  const errors = captureUnexpectedErrors(page)
  await login(page)
  await page.goto(evidence.listUrl)
  await expect(page.getByText(evidence.viewName, { exact: true }).first()).toBeVisible()
  await expect(visibleRecords(page).getByText(evidence.activeTitle, { exact: true })).toBeVisible()
  expect(new URL(page.url()).searchParams.get('viewId')).toBe(evidence.viewId)
  const views = await envelope<{ items: Array<{ viewId: string; name: string }> }>(page,
    `/api/v1/systems/${systemId}/runtime/saved-views?moduleCode=work_order`)
  expect(views.items).toContainEqual(expect.objectContaining({ viewId: evidence.viewId, name: evidence.viewName }))
  await page.screenshot({ path: '../.cursor/session/evidence/p4-b3/restart-readback-desktop.png', fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])
})

test('ordinary member explicitly repairs a saved view after its field is retired', async ({ page }) => {
  test.skip(!staleFixturePath, 'P4-B3 stale-view fixture is required')
  const fixture = JSON.parse(readFileSync(staleFixturePath!, 'utf8').replace(/^\uFEFF/u, '')) as {
    username: string
    password: string
    systemId: string
    staleViewId: string
    staleViewName: string
  }
  const errors = captureUnexpectedErrors(page)
  await loginAs(page, fixture.username, fixture.password)
  const queryResponse = page.waitForResponse((response) => response.url().includes('/records:query'))
  await page.goto(`/systems/${fixture.systemId}/workbench?module=work_order&viewId=${fixture.staleViewId}`)
  const queried = await queryResponse
  const queriedBody = await queried.json() as Envelope<{ invalidNodes: string[] }>
  expect(queriedBody.data.invalidNodes.some((node) => node.startsWith('/filter'))).toBe(true)
  await expect(page.getByText('保存视图包含已失效条件', { exact: true })).toBeVisible()
  await expect(page.getByText(/服务器已忽略/)).toBeVisible()

  const updateResponse = page.waitForResponse((response) => response.url().includes(`/saved-views/${fixture.staleViewId}`)
    && response.request().method() === 'PUT')
  await page.getByRole('button', { name: '确认修复并更新' }).click()
  expect((await updateResponse).ok()).toBe(true)
  await expect(page.getByText('保存视图包含已失效条件', { exact: true })).toHaveCount(0)
  const views = await envelope<{ items: Array<{ viewId: string; query: { filter: unknown }; columns: string[] }> }>(page,
    `/api/v1/systems/${fixture.systemId}/runtime/saved-views?moduleCode=work_order`)
  const repaired = views.items.find((view) => view.viewId === fixture.staleViewId)
  expect(repaired?.query.filter).toBeNull()
  expect(repaired?.columns).not.toContain('description')
  await page.screenshot({ path: '../.cursor/session/evidence/p4-b3/stale-view-repaired-desktop.png', fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])
})
