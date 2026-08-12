import { randomUUID } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { expect as baseExpect, test, type Locator, type Page } from '@playwright/test'

const expect = baseExpect.configure({ timeout: 20_000 })

test.use({ trace: 'off', actionTimeout: 15_000, navigationTimeout: 30_000 })
test.setTimeout(300_000)

const username = process.env.E2E_P4_C1_USERNAME
const password = process.env.E2E_P4_C1_PASSWORD
const systemId = process.env.E2E_P4_C1_SYSTEM_ID
const rootUsername = process.env.E2E_ROOT_USERNAME
const rootPassword = process.env.E2E_ROOT_PASSWORD
const restartEvidence = process.env.E2E_P4_C1_RESTART_EVIDENCE

interface Envelope<T> { code: string; message: string; data: T }
interface SchemaField {
  fieldCode: string
  fieldName: string
  type: string
  options: Array<{ value: string; label: string; parentValue?: string | null }>
}
interface RuntimeSchema { schemaVersionId: string; fields: SchemaField[] }
interface RuntimeValue { fieldCode: string; value: unknown; displayValue?: string }
interface RuntimeRecord {
  recordId: string
  version: number
  title: string
  status: string
  values: RuntimeValue[]
}

function requireRuntimeEnvironment() {
  test.skip(!username || !password || !systemId, 'P4-C1 ordinary-member fixture is required')
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
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) errors.push(`console: ${message.text()}`)
  })
  page.on('pageerror', (error) => errors.push(`page: ${error.message}`))
  page.on('response', (response) => {
    if (response.status() >= 500) errors.push(`http ${response.status()}: ${response.url()}`)
  })
  return errors
}

function formField(page: Page, name: string): Locator {
  return page.locator('.runtime-form-field').filter({ has: page.getByText(name, { exact: true }) })
}

async function selectAnt(page: Page, select: Locator, label: string) {
  await select.click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: label }).first().click()
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0)
}

async function screenshotDrawerSections(drawer: Locator, pathPrefix: string) {
  const body = drawer.locator('.ant-drawer-body')
  await body.evaluate((element) => { element.scrollTop = 0 })
  await drawer.screenshot({ path: `${pathPrefix}-top.png` })
  await body.evaluate((element) => { element.scrollTop = element.scrollHeight })
  await drawer.screenshot({ path: `${pathPrefix}-bottom.png` })
  await body.evaluate((element) => { element.scrollTop = 0 })
}

async function chooseMulti(page: Page, name: string, labels: string[]) {
  const target = formField(page, name)
  await target.locator('.ant-select-selector').click()
  for (const label of labels) {
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: label }).first().click({ force: true })
  }
  await target.locator('.ant-select-selection-search-input').press('Tab')
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0)
}

async function fillTag(page: Page, name: string, values: string[]) {
  const target = formField(page, name)
  const input = target.locator('.ant-select-selection-search-input')
  for (const value of values) {
    await input.fill(value)
    await input.press('Enter')
  }
  await input.blur()
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0)
}

async function waitForSaveButton(page: Page, name: '保存草稿' | '保存修改') {
  const button = page.getByRole('button', { name })
  await expect(button).not.toHaveClass(/ant-btn-loading/)
  return button
}

async function fillElevenFields(page: Page, completionRate: string, suffix: string) {
  await formField(page, '完成率').locator('input').fill(completionRate)
  await selectAnt(page, formField(page, '预算').getByLabel('预算币种'), 'CNY')
  await formField(page, '预算').getByLabel('预算金额').fill('1280')
  await formField(page, '预算').getByLabel('预算金额').blur()
  await formField(page, '服务日期').getByLabel('服务日期开始').fill('2026-08-10')
  await formField(page, '服务日期').getByLabel('服务日期结束').fill('2026-08-12')
  await formField(page, '开始时间').getByLabel('开始时间').fill('09:30:00')
  await formField(page, '服务时段').getByLabel('服务时段开始').fill('09:30:00')
  await formField(page, '服务时段').getByLabel('服务时段结束').fill('18:00:00')
  await chooseMulti(page, '服务标签', ['普通', '紧急'])

  const cascade = formField(page, '服务区域')
  await cascade.locator('.ant-select-selector').click()
  await page.locator('.ant-cascader-dropdown:visible .ant-cascader-menu-item').filter({ hasText: '华东' }).click()
  await page.locator('.ant-cascader-dropdown:visible .ant-cascader-menu-item').filter({ hasText: '上海' }).click()
  await expect(page.locator('.runtime-form')).toBeVisible()

  await formField(page, '是否启用').locator('.ant-switch').click()
  await expect(page.locator('.runtime-form')).toBeVisible()
  await formField(page, '评分').locator('.ant-rate-star').nth(3).click()
  await expect(page.locator('.runtime-form')).toBeVisible()
  await formField(page, '进度').getByLabel('进度').fill('45')
  await expect(page.locator('.runtime-form')).toBeVisible()
  await fillTag(page, '自定义标签', [`现场-${suffix}`, '加急'])
  await expect(page.locator('.runtime-form')).toBeVisible()
}

function valueByCode(record: RuntimeRecord, code: string) {
  return record.values.find((item) => item.fieldCode === code)
}

async function expectDetailValues(page: Page, record: RuntimeRecord) {
  const drawer = page.locator('.ant-drawer.ant-drawer-open')
  await expect(drawer).toBeVisible()
  for (const value of record.values) {
    if (value.displayValue) await expect(drawer.getByText(value.displayValue, { exact: true })).toBeVisible()
  }
}

test('ordinary member uses all eleven responsive controls, typed query and unique feedback', async ({ page }) => {
  requireRuntimeEnvironment()
  const errors = captureUnexpectedErrors(page)
  await login(page)
  const runtimeRoot = `/api/v1/systems/${systemId}/runtime/modules/work_order`
  await page.goto(`/systems/${systemId}/workbench?module=work_order`)
  await expect(page.getByRole('heading', { name: '现场工单', exact: true })).toBeVisible()

  const schema = await envelope<RuntimeSchema>(page, `${runtimeRoot}/record-schema`)
  expect(schema.fields.map((field) => field.type)).toEqual([
    'PERCENT', 'MONEY', 'DATE_RANGE', 'TIME', 'TIME_RANGE', 'MULTI_SELECT',
    'CASCADE', 'SWITCH', 'RATING', 'PROGRESS', 'TAG',
  ])
  const cascade = schema.fields.find((field) => field.type === 'CASCADE')!
  expect(cascade.options.find((option) => option.label === '上海')?.parentValue).toBe(
    cascade.options.find((option) => option.label === '华东')?.value,
  )

  const suffix = `${test.info().project.name}-${Date.now()}-${randomUUID().slice(0, 4)}`
  const completionRate = (60 + Math.random() * 30).toFixed(4)
  const title = `十一字段验收 ${suffix}`
  await page.getByRole('button', { name: '新建记录' }).click()
  await page.getByLabel('记录标题').fill(title)
  await fillElevenFields(page, completionRate, suffix)
  await (await waitForSaveButton(page, '保存草稿')).click()
  await expect(page.getByText(/草稿已保存/)).toBeVisible()
  await screenshotDrawerSections(
    page.locator('.ant-drawer:visible').filter({ has: page.locator('form.runtime-form') }),
    `../.cursor/session/evidence/p4-c1/eleven-field-form-${test.info().project.name}`,
  )
  await page.getByRole('button', { name: '激活记录' }).click()
  await expect(page).toHaveURL(/mode=view&record=\d+/)
  const recordId = new URL(page.url()).searchParams.get('record')!
  let record = await envelope<RuntimeRecord>(page, `${runtimeRoot}/records/${recordId}`)
  expect(record.status).toBe('ACTIVE')
  expect(record.values).toHaveLength(11)
  expect(valueByCode(record, 'completion_rate')?.value).toBe(Number(completionRate))
  expect(valueByCode(record, 'budget')?.value).toEqual({ amount: '1280.00', currency: 'CNY' })
  expect(valueByCode(record, 'service_dates')?.value).toEqual(['2026-08-10', '2026-08-12'])
  expect(valueByCode(record, 'start_time')?.value).toBe('09:30:00')
  expect(valueByCode(record, 'service_hours')?.value).toEqual(['09:30:00', '18:00:00'])
  expect(valueByCode(record, 'enabled')?.value).toBe(true)
  expect(valueByCode(record, 'rating')?.value).toBe(4)
  expect(valueByCode(record, 'progress')?.value).toBe(45)
  expectDetailValues(page, record)
  await screenshotDrawerSections(
    page.locator('.ant-drawer:visible').filter({ has: page.locator('.detail-list') }),
    `../.cursor/session/evidence/p4-c1/eleven-fields-${test.info().project.name}`,
  )

  const detailDrawer = page.locator('.ant-drawer:visible').filter({ has: page.getByTitle('编辑记录') })
  await detailDrawer.getByTitle('编辑记录').click()
  const editDrawer = page.locator('.ant-drawer:visible').filter({ has: page.locator('form.runtime-form') })
  await expect(editDrawer.locator('.ant-drawer-title')).toContainText('编辑记录')
  await formField(page, '进度').getByLabel('进度').fill('65')
  const updateResponse = page.waitForResponse((response) => response.url().includes(`/records/${recordId}`) && response.request().method() === 'PUT')
  await (await waitForSaveButton(page, '保存修改')).click()
  expect((await updateResponse).ok()).toBe(true)
  await page.goto(`/systems/${systemId}/workbench?module=work_order&mode=view&record=${recordId}`)
  await expect(page.locator('.ant-drawer:visible')).toBeVisible()
  await page.reload()
  record = await envelope<RuntimeRecord>(page, `${runtimeRoot}/records/${recordId}`)
  expect(valueByCode(record, 'progress')?.value).toBe(65)
  expect(valueByCode(record, 'tags')?.value).toEqual([`现场-${suffix}`, '加急'])
  await expectDetailValues(page, record)
  await page.locator('.ant-drawer.ant-drawer-open .ant-drawer-close').click()

  await page.getByRole('button', { name: '筛选、排序和显示列' }).click()
  const queryDrawer = page.locator('.ant-drawer.ant-drawer-open')
  await queryDrawer.getByRole('button', { name: '添加条件' }).click()
  let rows = queryDrawer.locator('.filter-row')
  await selectAnt(page, rows.nth(0).getByLabel('筛选字段'), '完成率')
  await selectAnt(page, rows.nth(0).getByLabel('筛选操作符'), '大于等于')
  await rows.nth(0).getByLabel('筛选值').fill(completionRate)
  await queryDrawer.getByRole('button', { name: '添加条件' }).click()
  rows = queryDrawer.locator('.filter-row')
  await selectAnt(page, rows.nth(1).getByLabel('筛选字段'), '预算')
  await selectAnt(page, rows.nth(1).getByLabel('筛选操作符'), '等于')
  await selectAnt(page, rows.nth(1).getByLabel('筛选币种'), 'CNY')
  await rows.nth(1).getByLabel('筛选金额').fill('1280')
  await queryDrawer.getByRole('button', { name: '添加排序' }).click()
  const sortRow = queryDrawer.locator('.sort-row').first()
  await selectAnt(page, sortRow.getByLabel('排序字段'), '预算')
  await selectAnt(page, sortRow.getByLabel('排序币种'), 'CNY')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await screenshotDrawerSections(
    queryDrawer,
    `../.cursor/session/evidence/p4-c1/typed-query-${test.info().project.name}`,
  )
  const queryResponse = page.waitForResponse((response) => response.url().includes('/records:query'))
  await queryDrawer.getByRole('button', { name: '应用查询' }).click()
  const response = await queryResponse
  expect(response.ok()).toBe(true)
  const queried = await response.json() as Envelope<{ total: number }>
  expect(queried.data.total).toBeGreaterThanOrEqual(1)
  const visibleList = page.locator('.record-table-wrap:visible, .record-mobile-list:visible')
  await expect(visibleList.getByText(title, { exact: true })).toBeVisible()
  await visibleList.screenshot({ path: `../.cursor/session/evidence/p4-c1/typed-query-result-${test.info().project.name}.png` })

  await page.getByRole('button', { name: '清除条件' }).click()
  await page.getByRole('button', { name: '新建记录' }).click()
  await page.getByLabel('记录标题').fill(`唯一冲突 ${suffix}`)
  await formField(page, '完成率').locator('input').fill(completionRate)
  await (await waitForSaveButton(page, '保存草稿')).click()
  await expect(page.getByText(/草稿已保存/)).toBeVisible()
  await page.getByRole('button', { name: '激活记录' }).click()
  await expect(page.getByText('此字段值已被使用，请更换后重试。', { exact: true })).toBeVisible()
  await expect(formField(page, '完成率').getByText(/unique|唯一|conflict/i)).toBeVisible()

  writeFileSync(resolve('..', '.cursor/session/evidence/p4-c1', `browser-${test.info().project.name}.json`), JSON.stringify({
    project: test.info().project.name,
    principal: username,
    systemId,
    recordId,
    title,
    completionRate,
    canonicalValues: record.values,
    capturedAt: new Date().toISOString(),
  }, null, 2))
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])
})

test('administrator sees P4-C1 configuration constraints', async ({ page }) => {
  test.skip(test.info().project.name !== 'desktop', 'desktop configuration proof only')
  test.skip(!rootUsername || !rootPassword || !systemId, 'root credentials and P4-C1 fixture are required')
  const errors = captureUnexpectedErrors(page)
  await loginAs(page, rootUsername!, rootPassword!)
  await envelope(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  await page.goto(`/systems/${systemId}/admin/configuration`)
  await expect(page.getByRole('heading', { name: '模块配置工作台' })).toBeVisible()
  await page.getByRole('button', { name: '字段', exact: true }).click()
  const modal = page.locator('.ant-modal:visible')
  await modal.getByLabel('字段类型').click()
  await page.keyboard.type('MONEY')
  await page.keyboard.press('Enter')
  await expect(modal.getByText('允许币种', { exact: true })).toBeVisible()
  await expect(modal.getByText('固定币种', { exact: true })).toBeVisible()
  await expect(modal.getByText('可搜索', { exact: true }).locator('..').locator('input')).toBeDisabled()
  await modal.getByLabel('索引').click()
  const options = page.locator('.ant-select-dropdown:visible .ant-select-item-option')
  await expect(options.filter({ hasText: 'UNIQUE' })).toBeVisible()
  await expect(options.filter({ hasText: 'STATISTIC' })).toHaveCount(0)
  await modal.getByLabel('索引').click()
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0)
  await modal.getByText('允许币种', { exact: true }).scrollIntoViewIfNeeded()
  await modal.screenshot({ path: '../.cursor/session/evidence/p4-c1/config-money-desktop.png' })
  await modal.getByRole('button', { name: /取\s*消/ }).click()
  expect(errors).toEqual([])
})

test('ordinary member reads the same eleven-field record after packaged restart', async ({ page }) => {
  test.skip(test.info().project.name !== 'desktop', 'desktop restart proof only')
  test.skip(!restartEvidence, 'P4-C1 restart evidence path is required')
  requireRuntimeEnvironment()
  const evidence = JSON.parse(readFileSync(restartEvidence!, 'utf8')) as {
    recordId: string
    title: string
    canonicalValues: RuntimeValue[]
  }
  const errors = captureUnexpectedErrors(page)
  await login(page)
  await envelope(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  await page.goto(`/systems/${systemId}/workbench?module=work_order&mode=view&record=${evidence.recordId}`)
  await expect(page.locator('.ant-drawer:visible')).toBeVisible()
  const record = await envelope<RuntimeRecord>(page,
    `/api/v1/systems/${systemId}/runtime/modules/work_order/records/${evidence.recordId}`)
  expect(record.status).toBe('ACTIVE')
  expect(record.values.map((value) => ({ fieldCode: value.fieldCode, value: value.value }))).toEqual(
    evidence.canonicalValues.map((value) => ({ fieldCode: value.fieldCode, value: value.value })),
  )
  await expect(page.locator('.ant-drawer.ant-drawer-open').getByText(evidence.title, { exact: true })).toBeVisible()
  await expectDetailValues(page, record)
  await page.screenshot({ path: '../.cursor/session/evidence/p4-c1/restart-readback-desktop.png', fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])
})
