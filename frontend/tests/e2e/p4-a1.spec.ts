import { execFileSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'

import { expect, test, type Page } from '@playwright/test'

test.use({ trace: 'off' })
test.setTimeout(120_000)

const rootUsername = process.env.E2E_ROOT_USERNAME
const rootPassword = process.env.E2E_ROOT_PASSWORD
const databaseHost = process.env.E2E_DB_HOST
const databaseUsername = process.env.E2E_DB_USERNAME
const databasePassword = process.env.E2E_DB_PASSWORD

function requireEnvironment() {
  test.skip(!rootUsername || !rootPassword, 'Root credentials are required')
  test.skip(!databaseHost || !databaseUsername || !databasePassword, 'Database fixture credentials are required')
}

async function login(page: Page) {
  await page.goto('/auth/login')
  await page.getByLabel('账号').fill(rootUsername!)
  await page.getByLabel('密码').fill(rootPassword!)
  await Promise.all([
    page.waitForURL(/\/platform\/workbench$/),
    page.getByRole('button', { name: '登录', exact: true }).click(),
  ])
}

async function apiData(page: Page, path: string, method = 'GET', body?: unknown, idempotencyKey?: string) {
  const response = await page.evaluate(async ({ path, method, body, idempotencyKey }) => {
    const csrf = document.cookie.split('; ').find((item) => item.startsWith('EXAMINE_CSRF='))?.split('=')[1] ?? ''
    const result = await fetch(path, {
      method,
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-Token': decodeURIComponent(csrf),
        'X-Request-ID': `p4-a1-${crypto.randomUUID()}`,
        ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    })
    return { status: result.status, payload: await result.json() }
  }, { path, method, body, idempotencyKey })
  expect(response.status, JSON.stringify(response.payload)).toBe(200)
  return response.payload.data as Record<string, any>
}

async function createPublishedRuntimeSystem(page: Page) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 8)
  await apiData(page, '/api/v1/context/platform:switch', 'POST', {})
  const system = await apiData(page, '/api/v1/platform/admin/systems', 'POST', {
    code: `p4_runtime_${suffix}`,
    name: `P4 记录工作台 ${suffix}`,
    description: 'P4-A1 real record list and detail journey',
    tenantMode: 'SINGLE',
  }, randomUUID())
  const systemId = String(system.id)
  await apiData(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const configRoot = `/api/v1/systems/${systemId}/admin/config`
  const group = await apiData(page, `${configRoot}/module-groups`, 'POST', {
    code: 'operations', name: '运营管理', description: '', iconKey: 'folder',
    sortOrder: 0, status: 'ENABLED', draftRevision: '0',
  }, randomUUID())
  const module = await apiData(page, `${configRoot}/modules`, 'POST', {
    groupId: String(group.id), code: 'work_order', name: '现场工单', description: '', iconKey: 'clipboard',
    sortOrder: 0, status: 'ENABLED', allowComments: true, allowTeam: true, draftRevision: '1',
  }, randomUUID())
  const field = await apiData(page, `${configRoot}/modules/${module.id}/fields`, 'POST', {
    dictionaryId: null, targetModuleId: null, code: 'subject', name: '工单主题', type: 'TEXT',
    sortOrder: 0, required: true, hidden: false, readonly: false, searchable: true, filterable: true,
    showInList: true, showInDetail: true, indexMode: 'NONE', status: 'ENABLED',
    properties: { maxLength: 200 }, draftRevision: '2',
  }, randomUUID())
  const check = await apiData(page, `${configRoot}/checks`, 'POST', { draftRevision: '3' })
  const root = await apiData(page, configRoot)
  const published = await apiData(page, `${configRoot}:publish`, 'POST', {
    checkId: String(check.id), draftRevision: '3', configRootVersion: String(root.version),
    reason: 'P4-A1 browser acceptance',
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})
  const tenants = await apiData(page, `/api/v1/systems/${systemId}/admin/tenants?size=200`)
  const members = await apiData(page, `/api/v1/systems/${systemId}/admin/members?size=200`)
  const tenant = tenants.items.find((item: Record<string, any>) => item.isDefault) ?? tenants.items[0]
  const member = members.items[0]
  return {
    id: systemId,
    name: String(system.name),
    tenantId: String(tenant.id),
    memberId: String(member.id),
    moduleId: String(module.id),
    fieldId: String(field.id),
    schemaVersionId: String(published.version.id),
  }
}

function seedRecords(system: Awaited<ReturnType<typeof createPublishedRuntimeSystem>>) {
  const recordBase = 7_100_000_000_000_000_000n + BigInt(Date.now() % 1_000_000_000) * 100n
  const rows = [
    { id: recordBase, no: 'WO-001', title: 'Replace access reader' },
    { id: recordBase + 1n, no: 'WO-002', title: 'Inspect backup power' },
  ]
  const sql = rows.map((row) => `
    INSERT INTO examine2.un_module_record
      (id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,record_no,title,status,owner_member_id,created_at,created_by,updated_at,updated_by,version)
    VALUES (${row.id},${system.id},${system.tenantId},${row.id},${system.schemaVersionId},${system.moduleId},${system.moduleId},'${row.no}','${row.title}','ACTIVE',${system.memberId},NOW(3),${system.memberId},NOW(3),${system.memberId},0);
    INSERT INTO examine2.un_module_record_value
      (id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,field_snapshot_id,logical_field_id,field_type,ordinal,string_value,display_value,created_at,created_by,updated_at,updated_by,version)
    VALUES (${row.id + 10n},${system.id},${system.tenantId},${row.id},${system.schemaVersionId},${system.moduleId},${system.moduleId},${system.fieldId},${system.fieldId},'TEXT',0,'${row.title}','${row.title}',NOW(3),${system.memberId},NOW(3),${system.memberId},0);
  `).join('\n')
  execFileSync('docker', [
    'run', '--rm', 'mysql:8.0.44', 'mysql',
    `--host=${databaseHost}`, '--port=3306', `--user=${databaseUsername}`, `--password=${databasePassword}`,
    '-e', sql,
  ], { stdio: 'pipe' })
  return rows
}

test('ordinary workbench reads real records and preserves detail context', async ({ page }) => {
  requireEnvironment()
  const errors: string[] = []
  page.on('console', (message) => {
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) {
      errors.push(`console: ${message.text()}`)
    }
  })
  page.on('pageerror', (error) => errors.push(`page: ${error.message}`))
  page.on('response', (response) => { if (response.status() >= 500) errors.push(`http ${response.status()}: ${response.url()}`) })

  await login(page)
  const system = await createPublishedRuntimeSystem(page)
  const rows = seedRecords(system)
  await page.goto(`/systems/${system.id}/workbench?module=work_order`)

  await expect(page.getByRole('heading', { name: system.name })).toBeVisible()
  await expect(page.getByRole('heading', { name: '现场工单' })).toBeVisible()

  if (test.info().project.name === 'mobile') {
    await expect(page.locator('.record-mobile-list').getByText('工单主题', { exact: true }).first()).toBeVisible()
    await expect(page.locator('.record-mobile-item')).toHaveCount(2)
    await page.screenshot({
      path: '../.cursor/session/evidence/p4-a1/workbench-list-mobile.png',
      fullPage: true,
    })
    await page.locator('.record-mobile-item').filter({ hasText: 'WO-002' }).click()
  } else {
    await expect(page.locator('.record-table').getByText('工单主题', { exact: true })).toBeVisible()
    await expect(page.locator('.record-table tbody tr')).toHaveCount(2)
    await page.locator('.record-table .record-link').filter({ hasText: 'WO-002' }).click()
  }

  await expect(page.locator('.ant-drawer')).toBeVisible()
  await expect(page.locator('.ant-drawer').getByText('Inspect backup power', { exact: true }).first()).toBeVisible()
  await expect(page).toHaveURL(new RegExp(`record=${rows[1].id}`))
  await page.reload()
  await expect(page.locator('.ant-drawer').getByText('Inspect backup power', { exact: true }).first()).toBeVisible()
  await page.screenshot({
    path: `../.cursor/session/evidence/p4-a1/workbench-detail-${test.info().project.name}.png`,
    fullPage: true,
  })

  await page.locator('.ant-drawer .ant-drawer-close').click()
  await page.getByText('已归档', { exact: true }).first().click()
  await expect(page.getByText('当前状态下还没有记录', { exact: true })).toBeVisible()
  await page.getByText('使用中', { exact: true }).first().click()
  const activeRecords = test.info().project.name === 'mobile'
    ? page.locator('.record-mobile-list')
    : page.locator('.record-table')
  await expect(activeRecords.getByText('WO-001', { exact: true })).toBeVisible()

  await page.goto(`/systems/${system.id}/workbench?module=work_order&record=${rows[1].id + 999n}`)
  await expect(page.getByText('记录不存在，或当前成员无权查看。', { exact: true })).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])

  await page.screenshot({
    path: `../.cursor/session/evidence/p4-a1/workbench-failure-${test.info().project.name}.png`,
    fullPage: true,
  })
})
