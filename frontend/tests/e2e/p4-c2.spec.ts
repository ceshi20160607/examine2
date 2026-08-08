import { randomUUID } from 'node:crypto'
import { readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { expect as baseExpect, test, type Locator, type Page } from '@playwright/test'

const expect = baseExpect.configure({ timeout: 20_000 })

test.use({ trace: 'off', actionTimeout: 15_000, navigationTimeout: 30_000 })
test.setTimeout(360_000)

const username = process.env.E2E_P4_C2_USERNAME
const password = process.env.E2E_P4_C2_PASSWORD
const systemId = process.env.E2E_P4_C2_SYSTEM_ID
const rootUsername = process.env.E2E_ROOT_USERNAME
const rootPassword = process.env.E2E_ROOT_PASSWORD
const restartEvidence = process.env.E2E_P4_C2_RESTART_EVIDENCE

interface Envelope<T> { code: string; message: string; data: T }
interface SchemaField {
  fieldCode: string
  fieldName: string
  type: string
  mode: string
  masked: boolean
  sensitiveReadable: boolean
  sensitiveQueryable: boolean
  operators: string[]
  sortable: boolean
  options: Array<{ value: string; label: string }>
  schema: Record<string, unknown>
}
interface RuntimeSchema { schemaVersionId: string; fields: SchemaField[] }
interface RuntimeValue { fieldCode: string; type: string; value?: unknown; displayValue?: string }
interface RuntimeRecord {
  recordId: string
  version: number
  title: string
  status: string
  values: RuntimeValue[]
}

function requireRuntimeEnvironment() {
  test.skip(!username || !password || !systemId, 'P4-C2 ordinary-member fixture is required')
}

async function loginAs(page: Page, account: string, secret: string) {
  await page.goto('/auth/login')
  await page.getByLabel('账号').fill(account)
  await page.getByLabel('密码').fill(secret)
  await Promise.all([
    page.waitForURL(/\/platform\/workbench$/),
    page.getByRole('button', { name: '登录', exact: true }).click(),
  ])
  await expect(page.locator('.workspace-heading .ant-btn')).not.toHaveClass(/ant-btn-loading/)
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
  const option = page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: label }).first()
  if (await option.count() === 0) {
    const holder = page.locator('.ant-select-dropdown:visible .rc-virtual-list-holder')
    if (await holder.count()) {
      await holder.evaluate((element) => {
        element.scrollTop = element.scrollHeight
        element.dispatchEvent(new Event('scroll', { bubbles: true }))
      })
      await expect(option).toBeVisible()
    }
  }
  await option.click({ force: true })
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0)
}

async function screenshotDrawer(drawer: Locator, prefix: string) {
  const body = drawer.locator('.ant-drawer-body')
  await body.evaluate((element) => { element.scrollTop = 0 })
  await drawer.screenshot({ path: `${prefix}-top.png` })
  await body.evaluate((element) => { element.scrollTop = element.scrollHeight })
  await drawer.screenshot({ path: `${prefix}-bottom.png` })
  await body.evaluate((element) => { element.scrollTop = 0 })
}

function cnIdentity(seed: number) {
  const weights = [7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2]
  const checks = ['1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2']
  const day = String(seed % 27 + 1).padStart(2, '0')
  const sequence = String(seed % 899 + 100).padStart(3, '0')
  const first = `110101199003${day}${sequence}`
  const sum = [...first].reduce((total, digit, index) => total + Number(digit) * weights[index]!, 0)
  return `${first}${checks[sum % 11]}`
}

function valueByCode(record: RuntimeRecord, code: string) {
  return record.values.find((item) => item.fieldCode === code)
}

function predicates(node: unknown): Array<Record<string, unknown>> {
  if (!node || typeof node !== 'object') return []
  const source = node as Record<string, unknown>
  if (source.kind === 'PREDICATE') return [source]
  return Array.isArray(source.children) ? source.children.flatMap(predicates) : []
}

async function fillP4C2Form(page: Page, seed: number, secret: string) {
  const suffix = `${seed}-${randomUUID().slice(0, 5)}`
  const phone = `139${String(seed).slice(-8).padStart(8, '0')}`
  const identity = cnIdentity(seed)
  const email = `accept-${suffix}@example.com`
  const url = `https://example.com/accept/${suffix}`
  const barcode = `ASSET-${suffix}`
  const ticket = `TICKET-${String(seed).slice(-7)}`

  await page.getByLabel('工单主题').fill(`结构化字段 ${suffix}`)
  await formField(page, 'Contact phone').getByLabel('Contact phone1').fill(phone)
  await formField(page, 'Contact email').getByLabel('Contact email1').fill(email)
  await formField(page, 'Reference URL').getByLabel('Reference URL').fill(url)
  await formField(page, 'Identity number').getByLabel('Identity number').fill(identity)
  await formField(page, 'Service address').getByLabel('地址显示文本').fill(`Beijing Chaoyang ${suffix}`)
  await formField(page, 'Service address').getByLabel('国家代码').fill('CN')
  await formField(page, 'Service address').getByLabel('地区代码').fill('BJ')
  await formField(page, 'Service address').getByLabel('城市').fill('Beijing')
  await formField(page, 'Service coordinates').getByLabel('纬度').fill('39.9')
  await formField(page, 'Service coordinates').getByLabel('经度').fill('116.4')
  await formField(page, 'Asset barcode').getByLabel('条码内容').fill(barcode)

  const rich = formField(page, 'Rich notes').getByRole('textbox', { name: 'Rich notes', exact: true })
  await rich.fill(`Browser acceptance ${suffix}`)
  await rich.press('ControlOrMeta+A')
  await formField(page, 'Rich notes').getByRole('button', { name: '粗体' }).click()
  await formField(page, 'Metadata JSON').getByLabel('Metadata JSON').fill(JSON.stringify({ ticket, count: 7 }, null, 2))
  await formField(page, 'Private secret').getByLabel('Private secret').fill(secret)
  await selectAnt(page, formField(page, 'Workflow status').locator('.ant-select'), 'New')

  return { suffix, phone, identity, email, url, barcode, ticket }
}

test('ordinary member uses all P4-C2 controls, structured queries and sensitive boundaries', async ({ page }) => {
  requireRuntimeEnvironment()
  const errors = captureUnexpectedErrors(page)
  await login(page)
  const runtimeRoot = `/api/v1/systems/${systemId}/runtime/modules/work_order`
  await page.goto(`/systems/${systemId}/workbench?module=work_order`)
  await expect(page.getByRole('heading', { name: '现场工单', exact: true })).toBeVisible()

  const schema = await envelope<RuntimeSchema>(page, `${runtimeRoot}/record-schema`)
  const p4c2Types = schema.fields.filter((field) => [
    'PHONE', 'EMAIL', 'URL', 'IDENTITY', 'ADDRESS', 'GEO', 'BARCODE', 'RICH_TEXT', 'JSON', 'SECRET', 'STATUS',
  ].includes(field.type)).map((field) => field.type)
  expect(p4c2Types).toEqual([
    'PHONE', 'EMAIL', 'URL', 'IDENTITY', 'ADDRESS', 'GEO', 'BARCODE', 'RICH_TEXT', 'JSON', 'SECRET', 'STATUS',
  ])
  expect(schema.fields.find((field) => field.type === 'IDENTITY')).toMatchObject({
    mode: 'SENSITIVE_WRITABLE', sensitiveReadable: true, sensitiveQueryable: true,
  })
  expect(schema.fields.find((field) => field.type === 'SECRET')).toMatchObject({
    mode: 'SENSITIVE_WRITABLE', masked: true, sensitiveQueryable: true,
  })

  const seed = Date.now() + (test.info().project.name === 'mobile' ? 1000 : 0)
  const secret = `browser-secret-${seed}-${randomUUID()}`
  const replacementSecret = `browser-replacement-${seed}-${randomUUID()}`
  const title = `P4-C2 浏览器验收 ${test.info().project.name} ${seed}`
  await page.getByRole('button', { name: '新建记录' }).click()
  await page.getByLabel('记录标题').fill(title)
  const expected = await fillP4C2Form(page, seed, secret)
  const formDrawer = page.locator('.ant-drawer:visible').filter({ has: page.locator('form.runtime-form') })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await screenshotDrawer(formDrawer, `../.cursor/session/evidence/p4-c2/form-${test.info().project.name}`)
  await page.getByRole('button', { name: '保存草稿' }).click()
  await expect(page.getByText(/草稿已保存/)).toBeVisible()
  await page.getByRole('button', { name: '激活记录' }).click()
  await expect(page).toHaveURL(/mode=view&record=\d+/)
  const recordId = new URL(page.url()).searchParams.get('record')!
  let record = await envelope<RuntimeRecord>(page, `${runtimeRoot}/records/${recordId}`)

  expect(record.status).toBe('ACTIVE')
  expect(valueByCode(record, 'contact_phone')?.value).toEqual([`+86${expected.phone}`])
  expect(valueByCode(record, 'contact_email')?.value).toEqual([expected.email])
  expect(valueByCode(record, 'reference_url')?.value).toBe(expected.url)
  expect(valueByCode(record, 'identity_number')?.value).toBe(expected.identity)
  expect(valueByCode(record, 'service_address')?.value).toMatchObject({ countryCode: 'CN', regionCode: 'BJ' })
  expect(valueByCode(record, 'service_geo')?.value).toEqual({ lat: 39.9, lng: 116.4 })
  expect(valueByCode(record, 'asset_barcode')?.value).toEqual({ symbology: 'CODE128', payload: expected.barcode })
  expect(String(valueByCode(record, 'rich_notes')?.value)).toContain('<strong>')
  expect(valueByCode(record, 'metadata_json')?.value).toEqual({ count: 7, ticket: expected.ticket })
  const secretValue = valueByCode(record, 'private_secret')!
  expect(Object.hasOwn(secretValue, 'value')).toBe(false)
  expect(secretValue.displayValue).toBe('********')
  expect(valueByCode(record, 'workflow_status')?.displayValue).toBe('New')

  const detailDrawer = page.locator('.ant-drawer:visible').filter({ has: page.locator('.detail-list') })
  const secretRow = detailDrawer.locator('.detail-list>div').filter({ hasText: 'Private secret' })
  await expect(secretRow.getByText('已设置', { exact: true })).toBeVisible()
  await expect(detailDrawer.getByText(secret, { exact: true })).toHaveCount(0)
  const urlLink = detailDrawer.locator('.detail-list>div').filter({ hasText: 'Reference URL' }).locator('a')
  await expect(urlLink).toHaveAttribute('href', expected.url)
  await expect(urlLink).toHaveAttribute('rel', 'noopener noreferrer')
  expect(await detailDrawer.locator('script').count()).toBe(0)
  await screenshotDrawer(detailDrawer, `../.cursor/session/evidence/p4-c2/detail-${test.info().project.name}`)

  await detailDrawer.getByTitle('编辑记录').click()
  const editDrawer = page.locator('.ant-drawer:visible').filter({ has: page.locator('form.runtime-form') })
  const secretField = formField(page, 'Private secret')
  await expect(secretField.getByLabel('Private secret')).toHaveValue('')
  await expect(secretField.getByRole('button', { name: '清除已保存值' })).toBeVisible()
  await secretField.getByLabel('Private secret').fill(replacementSecret)
  await selectAnt(page, formField(page, 'Workflow status').locator('.ant-select'), 'Active')
  const updateResponsePromise = page.waitForResponse((response) => response.url().endsWith(`/records/${recordId}`) && response.request().method() === 'PUT')
  await editDrawer.getByRole('button', { name: '保存修改' }).click()
  expect((await updateResponsePromise).ok()).toBe(true)
  record = await envelope<RuntimeRecord>(page, `${runtimeRoot}/records/${recordId}`)
  expect(valueByCode(record, 'workflow_status')?.displayValue).toBe('Active')
  expect(Object.hasOwn(valueByCode(record, 'private_secret')!, 'value')).toBe(false)
  await page.goto(`/systems/${systemId}/workbench?module=work_order`)

  await page.getByRole('button', { name: '筛选、排序和显示列' }).click()
  const queryDrawer = page.locator('.ant-drawer.ant-drawer-open')
  await queryDrawer.getByRole('button', { name: '添加条件' }).click()
  let rows = queryDrawer.locator('.filter-row')
  await selectAnt(page, rows.nth(0).getByLabel('筛选字段'), 'Service coordinates')
  await selectAnt(page, rows.nth(0).getByLabel('筛选操作符'), '矩形范围内')
  await rows.nth(0).getByLabel('南纬边界').fill('39.8')
  await rows.nth(0).getByLabel('西经边界').fill('116.3')
  await rows.nth(0).getByLabel('北纬边界').fill('40')
  await rows.nth(0).getByLabel('东经边界').fill('116.5')
  await queryDrawer.getByRole('button', { name: '添加条件' }).click()
  rows = queryDrawer.locator('.filter-row')
  await selectAnt(page, rows.nth(1).getByLabel('筛选字段'), 'Metadata JSON')
  await selectAnt(page, rows.nth(1).getByLabel('JSON 声明路径'), '$.ticket')
  await rows.nth(1).getByLabel('JSON 路径值').fill(expected.ticket)
  await queryDrawer.getByRole('button', { name: '添加条件' }).click()
  rows = queryDrawer.locator('.filter-row')
  await selectAnt(page, rows.nth(2).getByLabel('筛选字段'), 'Private secret')
  await rows.nth(2).getByLabel('敏感字段筛选值').fill(replacementSecret)
  await queryDrawer.getByRole('button', { name: '添加排序' }).click()
  await selectAnt(page, queryDrawer.locator('.sort-row').first().getByLabel('排序字段'), 'Asset barcode')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  await screenshotDrawer(queryDrawer, `../.cursor/session/evidence/p4-c2/query-${test.info().project.name}`)

  const queryResponsePromise = page.waitForResponse((response) => response.url().includes('/records:query'))
  await queryDrawer.getByRole('button', { name: '应用查询' }).click()
  const queryResponse = await queryResponsePromise
  expect(queryResponse.ok()).toBe(true)
  const body = queryResponse.request().postDataJSON() as Record<string, unknown>
  const queryPredicates = predicates(body.filter)
  expect(queryPredicates.find((item) => item.fieldCode === 'service_geo')?.value)
    .toEqual({ south: 39.8, west: 116.3, north: 40, east: 116.5 })
  expect(queryPredicates.find((item) => item.fieldCode === 'metadata_json')?.value)
    .toMatchObject({ value: expected.ticket })
  expect(queryPredicates.find((item) => item.fieldCode === 'private_secret')?.operator).toBe('EQ')
  await expect(page.locator('.record-table-wrap:visible, .record-mobile-list:visible').getByText(title, { exact: true })).toBeVisible()
  expect(page.url()).not.toContain(encodeURIComponent(replacementSecret))
  expect(new URL(page.url()).searchParams.has('filter')).toBe(false)
  const browserStorage = await page.evaluate(() => JSON.stringify({
    local: Object.fromEntries(Object.entries(localStorage)),
    session: Object.fromEntries(Object.entries(sessionStorage)),
  }))
  expect(browserStorage).not.toContain(replacementSecret)
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)

  writeFileSync(resolve('..', '.cursor/session/evidence/p4-c2', `browser-${test.info().project.name}.json`), JSON.stringify({
    project: test.info().project.name,
    principal: username,
    systemId,
    recordId,
    title,
    canonicalValues: record.values,
    structuredQueryFields: queryPredicates.map((item) => item.fieldCode),
    sensitiveDataPersistedInUrlOrStorage: false,
    capturedAt: new Date().toISOString(),
  }, null, 2))
  expect(errors).toEqual([])
})

test('administrator sees exact P4-C2 configuration constraints', async ({ page }) => {
  test.skip(test.info().project.name !== 'desktop', 'desktop configuration proof only')
  test.skip(!rootUsername || !rootPassword || !systemId, 'root credentials and P4-C2 fixture are required')
  const errors = captureUnexpectedErrors(page)
  await loginAs(page, rootUsername!, rootPassword!)
  await envelope(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  await page.goto(`/systems/${systemId}/admin/configuration`)
  await expect(page.getByRole('heading', { name: '模块配置工作台' })).toBeVisible()
  await page.getByRole('button', { name: '字段', exact: true }).click()
  const modal = page.locator('.ant-modal:visible')

  const changeType = async (type: string) => {
    await modal.getByLabel('字段类型').click()
    await page.keyboard.type(type)
    await page.keyboard.press('Enter')
  }
  await changeType('GEO')
  await expect(modal.getByLabel('地理位置坐标系')).toHaveClass(/ant-select-disabled/)
  await expect(modal.getByLabel('地理位置坐标系')).toContainText('WGS84')
  await changeType('BARCODE')
  await expect(modal.getByLabel('条码允许制式')).toBeVisible()
  await changeType('JSON')
  await expect(modal.getByLabel('JSON Schema')).toHaveValue(/"type": "object"/)
  await modal.getByRole('button', { name: '添加路径' }).click()
  await expect(modal.getByLabel('JSON 查询路径', { exact: true })).toHaveValue('$.')
  await changeType('SECRET')
  await modal.getByLabel('默认值来源').click()
  await expect(page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: '固定值' })).toHaveCount(0)
  await modal.getByLabel('默认值来源').click()
  await expect(modal.locator('.ant-form-item').filter({ hasText: '最大长度' }).locator('input')).toHaveValue('4096')
  await modal.screenshot({ path: '../.cursor/session/evidence/p4-c2/config-secret-desktop.png' })
  await modal.getByRole('button', { name: /取\s*消/ }).click()
  expect(errors).toEqual([])
})

test('ordinary member reads the same P4-C2 record after packaged restart', async ({ page }) => {
  test.skip(test.info().project.name !== 'desktop', 'desktop restart proof only')
  test.skip(!restartEvidence, 'P4-C2 restart evidence path is required')
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
  const secret = valueByCode(record, 'private_secret')!
  expect(Object.hasOwn(secret, 'value')).toBe(false)
  await expect(page.locator('.detail-list>div').filter({ hasText: 'Private secret' }).getByText('已设置', { exact: true })).toBeVisible()

  const schema = await envelope<RuntimeSchema>(page,
    `/api/v1/systems/${systemId}/runtime/modules/work_order/record-schema`)
  const metadata = valueByCode(record, 'metadata_json')?.value as { ticket?: string }
  const metadataField = schema.fields.find((field) => field.fieldCode === 'metadata_json')
  const queryPaths = metadataField?.schema.queryPaths as Array<{ pathSnapshotId: string; path: string }> | undefined
  const ticketPath = queryPaths?.find((path) => path.path === '$.ticket')
  expect(ticketPath?.pathSnapshotId).toBeTruthy()
  const queryResult = await envelope<{ rows: RuntimeRecord[] }>(page,
    `/api/v1/systems/${systemId}/runtime/modules/work_order/records:query`, 'POST', {
      schemaVersionId: schema.schemaVersionId,
      filter: {
        kind: 'PREDICATE',
        fieldCode: 'metadata_json',
        operator: 'DECLARED_PATH_EQ',
        value: { pathSnapshotId: ticketPath!.pathSnapshotId, value: metadata.ticket },
      },
      recordScope: 'active',
      q: null,
      sort: [],
      columns: [],
      viewId: null,
      page: 1,
      size: 20,
    })
  expect(queryResult.rows.map((item) => item.recordId)).toContain(evidence.recordId)
  await page.screenshot({ path: '../.cursor/session/evidence/p4-c2/restart-readback-desktop.png', fullPage: true })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])
})
