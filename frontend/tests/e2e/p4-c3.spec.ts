import { randomUUID } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { expect as baseExpect, test, type Page } from '@playwright/test'

const expect = baseExpect.configure({ timeout: 20_000 })

test.use({ trace: 'off', actionTimeout: 15_000, navigationTimeout: 30_000 })
test.setTimeout(300_000)

const username = process.env.E2E_P4_C3_USERNAME
const password = process.env.E2E_P4_C3_PASSWORD
const adminUsername = process.env.E2E_P4_C3_ADMIN_USERNAME
const adminPassword = process.env.E2E_P4_C3_ADMIN_PASSWORD
const systemId = process.env.E2E_P4_C3_SYSTEM_ID
const workOrderId = process.env.E2E_P4_C3_WORK_ORDER_ID
const customerId = process.env.E2E_P4_C3_CUSTOMER_ID
const restartEvidence = process.env.E2E_P4_C3_RESTART_EVIDENCE

interface Envelope<T> { code: string; message: string; data: T }
interface RuntimeValue {
  fieldCode: string
  fieldName: string
  type: string
  value?: unknown
  displayValue?: string
}
interface RuntimeRecord {
  recordId: string
  recordNo: string
  version: number
  schemaVersionId: string
  title: string
  status: string
  values: RuntimeValue[]
}
interface RelationPage {
  items: Array<{ targetRecordId: string; targetVersion: number; ordinal: number; title: string }>
  total: number
}
interface SubtablePage {
  items: Array<{
    rowId: string
    version: number
    ordinal: number
    values: RuntimeValue[]
  }>
  total: number
}

function requireMemberFixture() {
  test.skip(!username || !password || !systemId || !workOrderId || !customerId,
    'P4-C3 ordinary-member fixture is required')
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
  await envelope(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
}

async function envelope<T>(page: Page, path: string, method = 'GET', data?: unknown): Promise<T> {
  const csrf = (await page.context().cookies()).find((cookie) => cookie.name === 'EXAMINE_CSRF')
  const response = await page.request.fetch(path, {
    method,
    data,
    headers: method === 'GET' ? { 'X-Request-ID': randomUUID() } : {
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

async function waitSaved(page: Page) {
  await expect(page.locator('.runtime-save-state')).toContainText(/草稿已保存|已保存/, { timeout: 25_000 })
}

async function selectRelation(page: Page, title: string) {
  const relation = page.getByRole('region', { name: 'Customer', exact: true })
  const search = relation.getByRole('combobox')
  await search.click()
  await search.fill(title)
  const option = page.locator('.ant-select-dropdown:visible .ant-select-item-option')
    .filter({ hasText: title })
  await expect(option).toHaveCount(1)
  await option.click()
  await expect(relation.getByLabel('已选关联记录').getByText(title, { exact: true })).toBeVisible()
}

async function addSubtableRow(page: Page, description: string) {
  const subtable = page.getByRole('region', { name: 'Order lines', exact: true })
  await subtable.getByRole('button', { name: '新增行', exact: true }).click()
  const modal = page.locator('.ant-modal:visible').filter({ hasText: '新增明细行' })
  await modal.getByLabel('Description').fill(description)
  await modal.getByLabel('Quantity').fill('2')
  await modal.getByLabel('Line amount金额').fill('12.50')
  await modal.getByRole('button', { name: /保\s*存\s*行/ }).click()
  await expect(modal).toHaveCount(0)
  await expect(subtable.locator('.subtable-grid-wrap').getByText(description, { exact: true })).toBeVisible()
}

function referenceValue(record: RuntimeRecord) {
  return record.values.find((value) => value.fieldCode === 'customer_name_ref')
}

async function waitForReadyReference(page: Page, recordId: string) {
  const path = `/api/v1/systems/${systemId}/runtime/modules/work_order/records/${recordId}`
  let record: RuntimeRecord | undefined
  await expect.poll(async () => {
    record = await envelope<RuntimeRecord>(page, path)
    const value = referenceValue(record)
    const payload = value?.value && typeof value.value === 'object'
      ? value.value as Record<string, unknown> : {}
    return payload.recalculationState
  }, { timeout: 30_000 }).toBe('READY')
  return record!
}

test('ordinary member creates and reads a relation, reference and subtable record', async ({ page }) => {
  test.skip(test.info().project.name !== 'desktop', 'desktop mutation proof only')
  requireMemberFixture()
  const errors = captureUnexpectedErrors(page)
  await login(page)

  const customer = await envelope<RuntimeRecord>(page,
    `/api/v1/systems/${systemId}/runtime/modules/customer/records/${customerId}`)
  await page.goto(`/systems/${systemId}/workbench?module=work_order`)
  await expect(page.getByRole('heading', { name: 'Work order', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '新建记录', exact: true }).click()

  const seed = Date.now()
  const title = `P4-C3 browser ${seed}`
  const subject = `Composition journey ${seed}`
  const rowDescription = `Browser line ${seed}`
  await page.getByLabel('记录标题').fill(title)
  await page.getByLabel('Subject').fill(subject)
  await page.getByLabel('Subject').press('Tab')
  await selectRelation(page, customer.title)
  await addSubtableRow(page, rowDescription)
  await waitSaved(page)

  const draftId = new URL(page.url()).searchParams.get('draft')
  expect(draftId).toMatch(/^\d+$/)

  const editDrawer = page.locator('.ant-drawer:visible').filter({ has: page.locator('form.runtime-form') })
  await expect(editDrawer.getByText('已同步', { exact: true })).toBeVisible({ timeout: 30_000 })
  await editDrawer.screenshot({ path: '../.cursor/session/evidence/p4-c3/create-desktop.png' })
  await editDrawer.getByRole('button', { name: '激活记录', exact: true }).click()
  await expect(page).toHaveURL(new RegExp(`record=${draftId}`))

  const record = await waitForReadyReference(page, draftId!)
  expect(record.status).toBe('ACTIVE')
  const relation = await envelope<RelationPage>(page,
    `/api/v1/systems/${systemId}/runtime/modules/work_order/records/${draftId}/relations/customer`)
  expect(relation.total).toBe(1)
  expect(relation.items[0]).toMatchObject({ targetRecordId: customerId, title: customer.title })
  const subtable = await envelope<SubtablePage>(page,
    `/api/v1/systems/${systemId}/runtime/modules/work_order/records/${draftId}/subtables/order_lines?page=1&size=100`)
  expect(subtable.total).toBe(1)
  expect(subtable.items[0]?.values.find((value) => value.fieldCode === 'line_description')?.value).toBe(rowDescription)

  const reference = referenceValue(record)
  const referencePayload = reference?.value as Record<string, unknown>
  expect(referencePayload.result).toBe(customer.title)
  expect(referencePayload.recalculationState).toBe('READY')
  const detail = page.locator('.ant-drawer:visible').filter({ has: page.locator('.detail-list') })
  await expect(detail.getByText(customer.title, { exact: true })).toHaveCount(2)
  await expect(detail.locator('.subtable-grid-wrap').getByText(rowDescription, { exact: true })).toBeVisible()
  await expect(detail.getByText('已同步', { exact: true })).toBeVisible()
  await expect(detail.getByText(customerId!, { exact: true })).toHaveCount(0)
  await detail.screenshot({ path: '../.cursor/session/evidence/p4-c3/detail-created-desktop.png' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  writeFileSync(resolve('..', '.cursor/session/evidence/p4-c3/browser-desktop.json'), JSON.stringify({
    project: test.info().project.name,
    principal: username,
    systemId,
    recordId: draftId,
    title,
    subject,
    customerId,
    customerTitle: customer.title,
    rowDescription,
    recordVersion: record.version,
    referenceState: referencePayload.recalculationState,
    capturedAt: new Date().toISOString(),
  }, null, 2))
  expect(errors).toEqual([])
})

test('ordinary member reads composition fields as mobile cards without overflow', async ({ page }) => {
  test.skip(test.info().project.name !== 'mobile', 'mobile responsive proof only')
  requireMemberFixture()
  const errors = captureUnexpectedErrors(page)
  await login(page)
  await page.goto(`/systems/${systemId}/workbench?module=work_order&mode=view&record=${workOrderId}`)

  const detail = page.locator('.ant-drawer:visible').filter({ has: page.locator('.detail-list') })
  await expect(detail).toBeVisible()
  await expect(detail.getByRole('region', { name: 'Customer', exact: true })).toBeVisible()
  await expect(detail.getByText('已同步', { exact: true })).toBeVisible({ timeout: 30_000 })
  await expect(detail.locator('.subtable-mobile-list')).toBeVisible()
  await expect(detail.locator('.subtable-grid-wrap')).toBeHidden()
  await expect(detail.locator('.subtable-card')).toHaveCount(2)
  await page.screenshot({ path: '../.cursor/session/evidence/p4-c3/detail-mobile.png', fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])
})

test('administrator sees only the dedicated P4-C3 field contracts', async ({ page }) => {
  test.skip(test.info().project.name !== 'desktop', 'desktop configuration proof only')
  test.skip(!adminUsername || !adminPassword || !systemId, 'P4-C3 administrator fixture is required')
  const errors = captureUnexpectedErrors(page)
  await loginAs(page, adminUsername!, adminPassword!)
  await envelope(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  await page.goto(`/systems/${systemId}/admin/configuration`)
  await expect(page.getByRole('heading', { name: '模块配置工作台' })).toBeVisible()
  await page.getByRole('button', { name: /^Work order\b/ }).click()
  await page.getByRole('button', { name: '字段', exact: true }).click()
  const modal = page.locator('.ant-modal:visible')

  const changeType = async (type: string) => {
    await modal.getByLabel('字段类型').click()
    await page.keyboard.type(type)
    await page.keyboard.press('Enter')
  }

  await changeType('RELATION')
  await expect(modal.getByLabel('目标模块')).toBeVisible()
  await expect(modal.getByText('关系字段', { exact: true })).toBeVisible()
  await expect(modal.getByText('允许创建并关联', { exact: true })).toBeVisible()
  await expect(modal.getByText('显示反向关系', { exact: true })).toBeVisible()
  await expect(modal.getByText('限制可选记录', { exact: true })).toBeVisible()
  await expect(modal.getByText('允许导入', { exact: true })).toHaveCount(0)

  await changeType('REFERENCE')
  await expect(modal.getByLabel('目标模块')).toHaveCount(0)
  await expect(modal.getByLabel('引用来源关系')).toBeVisible()
  await expect(modal.getByLabel('引用目标字段')).toBeVisible()

  await changeType('SUBTABLE')
  await expect(modal.getByLabel('目标模块')).toBeVisible()
  await expect(modal.getByLabel('子表列')).toBeVisible()
  await expect(modal.getByText('允许新增行', { exact: true })).toBeVisible()
  await expect(modal.getByText('允许编辑行', { exact: true })).toBeVisible()
  await expect(modal.getByText('允许删除行', { exact: true })).toBeVisible()
  await expect(modal.getByText('允许调整顺序', { exact: true })).toBeVisible()
  await expect(modal.getByText('服务端聚合', { exact: true })).toBeVisible()
  await modal.screenshot({ path: '../.cursor/session/evidence/p4-c3/config-subtable-desktop.png' })
  await modal.getByRole('button', { name: /取\s*消/ }).click()
  expect(errors).toEqual([])
})

test('ordinary member reads the same composition after packaged restart', async ({ page }) => {
  test.skip(test.info().project.name !== 'desktop', 'desktop restart proof only')
  test.skip(!restartEvidence, 'P4-C3 restart evidence path is required')
  requireMemberFixture()
  const evidence = JSON.parse(readFileSync(restartEvidence!, 'utf8')) as {
    recordId: string
    title: string
    customerTitle: string
    rowDescription: string
  }
  const errors = captureUnexpectedErrors(page)
  await login(page)
  await page.goto(`/systems/${systemId}/workbench?module=work_order&mode=view&record=${evidence.recordId}`)
  const detail = page.locator('.ant-drawer:visible').filter({ has: page.locator('.detail-list') })
  await expect(detail.getByText(evidence.title, { exact: true })).toBeVisible()
  await expect(detail.getByText(evidence.customerTitle, { exact: true })).toHaveCount(2)
  await expect(detail.locator('.subtable-grid-wrap').getByText(evidence.rowDescription, { exact: true })).toBeVisible()
  await expect(detail.getByText('已同步', { exact: true })).toBeVisible()

  const record = await waitForReadyReference(page, evidence.recordId)
  expect(record.status).toBe('ACTIVE')
  const relation = await envelope<RelationPage>(page,
    `/api/v1/systems/${systemId}/runtime/modules/work_order/records/${evidence.recordId}/relations/customer`)
  const subtable = await envelope<SubtablePage>(page,
    `/api/v1/systems/${systemId}/runtime/modules/work_order/records/${evidence.recordId}/subtables/order_lines?page=1&size=100`)
  expect(relation.items[0]?.title).toBe(evidence.customerTitle)
  expect(subtable.items[0]?.values.find((value) => value.fieldCode === 'line_description')?.value)
    .toBe(evidence.rowDescription)
  await page.screenshot({ path: '../.cursor/session/evidence/p4-c3/restart-readback-desktop.png', fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])
})
