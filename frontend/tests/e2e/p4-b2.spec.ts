import { execFileSync } from 'node:child_process'
import { randomUUID } from 'node:crypto'
import { writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { expect as baseExpect, test, type Locator, type Page } from '@playwright/test'

const expect = baseExpect.configure({ timeout: 20_000 })

test.use({ trace: 'off' })
test.setTimeout(240_000)

const memberUsername = process.env.E2E_P4_B2_USERNAME
const memberPassword = process.env.E2E_P4_B2_PASSWORD
const systemId = process.env.E2E_P4_B2_SYSTEM_ID
const databaseHost = process.env.E2E_DB_HOST
const databaseUsername = process.env.E2E_DB_USERNAME
const databasePassword = process.env.E2E_DB_PASSWORD
const restartActiveRecordId = process.env.E2E_P4_B2_RESTART_ACTIVE_RECORD_ID
const restartDraftRecordId = process.env.E2E_P4_B2_RESTART_DRAFT_RECORD_ID

function requireEnvironment() {
  test.skip(!memberUsername || !memberPassword, 'P4-B2 ordinary-member credentials are required')
  test.skip(!systemId, 'P4-B2 stable system id is required')
  test.skip(!databaseHost || !databaseUsername || !databasePassword, 'Database verification credentials are required')
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

function mysql(sql: string) {
  return execFileSync('docker', [
    'run', '--rm', 'mysql:8.0.44', 'mysql', '--default-character-set=utf8mb4', '--batch', '--raw',
    '--skip-column-names', `--host=${databaseHost}`, '--port=3306',
    `--user=${databaseUsername}`, `--password=${databasePassword}`, '-e', sql,
  ], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
}

function field(page: Page, name: string): Locator {
  return page.locator('.runtime-form-field').filter({ has: page.getByText(name, { exact: true }) })
}

async function choose(page: Page, fieldName: string, optionName: string) {
  const target = field(page, fieldName)
  await target.locator('.ant-select-selector').click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option')
    .filter({ hasText: optionName }).click()
}

interface RecordValues {
  title: string
  subject: string
  description: string
  amount: string
  dueDate: string
  scheduledAt: string
}

async function fillEightFields(page: Page, values: RecordValues) {
  await page.getByLabel('记录标题').fill(values.title)
  await page.getByLabel('工单主题').fill(values.subject)
  await page.getByLabel('处理说明').fill(values.description)
  await field(page, '预计费用').locator('input').fill(values.amount)
  await page.locator('input[type="date"]').fill(values.dueDate)
  await page.locator('input[type="datetime-local"]').fill(values.scheduledAt)
  await choose(page, '优先级', '紧急')
  await choose(page, '负责人', 'Admin')
  await choose(page, '负责部门', '业务运营部')
}

async function confirmLifecycle(page: Page, title: string) {
  const modal = page.locator('.ant-modal:visible')
  await expect(modal).toContainText(title)
  const layers = await page.evaluate(() => {
    const modalWrap = Array.from(document.querySelectorAll<HTMLElement>('.ant-modal-wrap'))
      .find((item) => getComputedStyle(item).display !== 'none')
    const drawer = document.querySelector<HTMLElement>('.ant-drawer.ant-drawer-open')
    return {
      modal: Number(getComputedStyle(modalWrap!).zIndex),
      drawer: Number(getComputedStyle(drawer!).zIndex),
    }
  })
  expect(layers.modal).toBeGreaterThan(layers.drawer)
  await modal.locator('.ant-modal-footer .ant-btn-primary').click()
  await expect(modal).toBeHidden()
}

async function expectDetailStatus(page: Page, label: string) {
  const drawer = page.locator('.ant-drawer.ant-drawer-open')
  await expect(drawer).toBeVisible()
  await expect(drawer.getByText(label, { exact: true }).first()).toBeVisible()
  return drawer
}

async function runtimeRecordStatus(page: Page, recordId: string) {
  return page.evaluate(async ({ systemId, recordId }) => {
    const response = await fetch(`/api/v1/systems/${systemId}/runtime/modules/work_order/records/${recordId}`, {
      credentials: 'same-origin',
      headers: { 'X-Request-ID': `p4-b2-poll-${crypto.randomUUID()}` },
    })
    const envelope = await response.json()
    return { httpStatus: response.status, code: envelope.code, status: envelope.data?.status }
  }, { systemId: systemId!, recordId })
}

test('ordinary member completes archive, trash and expired-draft recovery journeys', async ({ page }) => {
  requireEnvironment()
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

  await login(page)
  await page.goto(`/systems/${systemId}/workbench?module=work_order`)
  await expect(page.getByRole('heading', { name: /P4 生命周期验收/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: '现场工单', exact: true })).toBeVisible()

  const suffix = `${test.info().project.name}-${Date.now()}-${randomUUID().slice(0, 6)}`
  const activeValues: RecordValues = {
    title: `生命周期主记录 ${suffix}`,
    subject: `配电柜巡检 ${suffix}`,
    description: '八字段记录在归档、回收和恢复后必须保持一致。',
    amount: '1280.50',
    dueDate: '2026-08-15',
    scheduledAt: '2026-08-10T09:30',
  }

  await page.getByRole('button', { name: '新建记录' }).click()
  await fillEightFields(page, activeValues)
  await page.getByRole('button', { name: '保存草稿' }).click()
  await expect(page.getByText(/草稿已保存/)).toBeVisible()
  await page.getByRole('button', { name: '激活记录' }).click()
  await expect(page).toHaveURL(/mode=view&record=\d+/)
  const activeRecordId = new URL(page.url()).searchParams.get('record') ?? ''
  expect(activeRecordId).toMatch(/^\d+$/)
  let drawer = await expectDetailStatus(page, '使用中')
  await expect(drawer.getByText(activeValues.subject, { exact: true })).toBeVisible()
  await expect(drawer.getByText('紧急', { exact: true })).toBeVisible()
  await expect(drawer.getByText('业务运营部', { exact: true })).toBeVisible()

  await drawer.getByRole('button', { name: '归档', exact: true }).click()
  await confirmLifecycle(page, '归档记录')
  drawer = await expectDetailStatus(page, '已归档')
  await page.reload()
  drawer = await expectDetailStatus(page, '已归档')
  await expect(drawer.getByRole('button', { name: '取消归档', exact: true })).toBeVisible()
  await drawer.getByRole('button', { name: '取消归档', exact: true }).click()
  await confirmLifecycle(page, '取消归档')
  drawer = await expectDetailStatus(page, '使用中')

  await drawer.getByRole('button', { name: '移入回收站', exact: true }).click()
  await confirmLifecycle(page, '移入回收站')
  drawer = await expectDetailStatus(page, '回收站')
  await expect(drawer.getByText('记录在回收站中', { exact: true })).toBeVisible()
  await page.reload()
  drawer = await expectDetailStatus(page, '回收站')
  await expect(drawer.getByText(activeValues.subject, { exact: true })).toBeVisible()
  await drawer.getByRole('button', { name: '恢复', exact: true }).click()
  await confirmLifecycle(page, '恢复记录')
  drawer = await expectDetailStatus(page, '使用中')
  await expect(drawer.getByText(activeValues.description, { exact: true })).toBeVisible()
  await page.screenshot({
    path: `../.cursor/session/evidence/p4-b2/archive-trash-restored-${test.info().project.name}.png`,
    fullPage: true,
  })

  await page.locator('.ant-drawer.ant-drawer-open .ant-drawer-close').click()
  await page.getByRole('button', { name: '新建记录' }).click()
  const draftValues: RecordValues = {
    title: `草稿恢复记录 ${suffix}`,
    subject: `待恢复工单 ${suffix}`,
    description: '草稿放弃、恢复、到期和再次恢复后仍可继续编辑。',
    amount: '350.25',
    dueDate: '2026-09-01',
    scheduledAt: '2026-08-20T14:45',
  }
  await fillEightFields(page, draftValues)
  await page.getByRole('button', { name: '保存草稿' }).click()
  await expect(page.getByText(/草稿已保存/)).toBeVisible()
  await expect(page).toHaveURL(/mode=edit&draft=\d+/)
  const draftRecordId = new URL(page.url()).searchParams.get('draft') ?? ''
  expect(draftRecordId).toMatch(/^\d+$/)
  await page.getByRole('button', { name: '放弃草稿', exact: true }).click()
  await confirmLifecycle(page, '放弃草稿')
  drawer = await expectDetailStatus(page, '回收站')
  await page.reload()
  drawer = await expectDetailStatus(page, '回收站')
  await drawer.getByRole('button', { name: '恢复', exact: true }).click()
  await confirmLifecycle(page, '恢复记录')
  await expect(page).toHaveURL(new RegExp(`mode=edit.*draft=${draftRecordId}`))
  await expect(page.getByLabel('工单主题')).toHaveValue(draftValues.subject)
  await expect(field(page, '优先级').locator('.ant-select-selection-item')).toHaveText('紧急')
  await expect(field(page, '负责部门').locator('.ant-select-selection-item')).toHaveText('业务运营部')

  const expiryUpdate = mysql(`UPDATE examine2.un_module_record SET draft_expires_at=DATE_SUB(NOW(3), INTERVAL 1 MINUTE) WHERE record_id=${draftRecordId} AND system_id=${systemId} AND status='DRAFT'; SELECT ROW_COUNT();`)
  expect(expiryUpdate.split(/\r?\n/).at(-1)).toBe('1')
  await expect.poll(async () => (await runtimeRecordStatus(page, draftRecordId)).status, {
    timeout: 75_000,
    intervals: [1_000, 2_000, 3_000],
  }).toBe('EXPIRED')

  await page.goto(`/systems/${systemId}/workbench?module=work_order&mode=view&record=${draftRecordId}`)
  drawer = await expectDetailStatus(page, '已过期')
  await expect(drawer.getByText('草稿已过期', { exact: true })).toBeVisible()
  await expect(drawer.getByTitle('编辑记录')).toHaveCount(0)
  await page.reload()
  drawer = await expectDetailStatus(page, '已过期')
  await drawer.getByRole('button', { name: '恢复草稿', exact: true }).click()
  await confirmLifecycle(page, '恢复过期草稿')
  await expect(page).toHaveURL(new RegExp(`mode=edit.*draft=${draftRecordId}`))
  await expect(page.getByLabel('工单主题')).toHaveValue(draftValues.subject)
  await page.getByLabel('记录标题').fill(`${draftValues.title} · 已恢复`)
  await page.getByRole('button', { name: '保存草稿' }).click()
  await expect(page.getByText(/草稿已保存/)).toBeVisible()
  await page.screenshot({
    path: `../.cursor/session/evidence/p4-b2/expired-recovered-${test.info().project.name}.png`,
    fullPage: true,
  })

  const recordReadback = mysql(`SELECT record_id,status,COALESCE(prior_status,'NULL'),IF(deleted_at IS NULL,1,0),IF(draft_expires_at IS NULL,0,draft_expires_at>DATE_ADD(NOW(),INTERVAL 29 DAY)) FROM examine2.un_module_record WHERE record_id IN (${activeRecordId},${draftRecordId}) ORDER BY record_id;`)
  expect(recordReadback).toContain(`${activeRecordId}\tACTIVE\tNULL\t1\t0`)
  expect(recordReadback).toContain(`${draftRecordId}\tDRAFT\tNULL\t1\t1`)
  const auditReadback = mysql(`SELECT operation_type,COUNT(*) FROM examine2.un_audit_operation WHERE aggregate_type='RUNTIME_RECORD' AND aggregate_id IN ('${activeRecordId}','${draftRecordId}') GROUP BY operation_type ORDER BY operation_type;`)
  for (const operation of ['RECORD_ARCHIVED', 'RECORD_UNARCHIVED', 'RECORD_TRASHED', 'RECORD_RESTORED_FROM_TRASH', 'RECORD_DRAFT_DISCARDED', 'RECORD_DRAFT_EXPIRED', 'RECORD_DRAFT_RECOVERED']) {
    expect(auditReadback).toContain(operation)
  }
  const outboxCount = Number(mysql(`SELECT COUNT(*) FROM examine2.un_sys_outbox_event WHERE aggregate_type='RUNTIME_RECORD' AND aggregate_id IN ('${activeRecordId}','${draftRecordId}');`))
  expect(outboxCount).toBeGreaterThanOrEqual(11)

  writeFileSync(resolve('..', '.cursor/session/evidence/p4-b2', `browser-readback-${test.info().project.name}.json`), JSON.stringify({
    project: test.info().project.name,
    principal: memberUsername,
    systemId,
    activeRecordId,
    recoveredDraftId: draftRecordId,
    recordReadback,
    auditReadback,
    outboxCount,
    capturedAt: new Date().toISOString(),
  }, null, 2))

  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])
})

test('ordinary member reads lifecycle records after backend restart', async ({ page }) => {
  test.skip(!memberUsername || !memberPassword || !systemId, 'P4-B2 ordinary-member runtime is required')
  test.skip(!restartActiveRecordId || !restartDraftRecordId, 'P4-B2 restart record ids are required')
  const errors: string[] = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('response', (response) => {
    if (response.status() >= 500) errors.push(`http ${response.status()}: ${response.url()}`)
  })

  await login(page)
  await page.goto(`/systems/${systemId}/workbench?module=work_order&mode=view&record=${restartActiveRecordId}`)
  let drawer = await expectDetailStatus(page, '使用中')
  await expect(drawer.getByText(/配电柜巡检 desktop-/).first()).toBeVisible()
  await expect(drawer.getByText('业务运营部', { exact: true })).toBeVisible()
  await expect(drawer.getByRole('button', { name: '归档', exact: true })).toBeVisible()
  await expect(drawer.getByRole('button', { name: '移入回收站', exact: true })).toBeVisible()

  await page.goto(`/systems/${systemId}/workbench?module=work_order&mode=edit&draft=${restartDraftRecordId}`)
  await expect(page.locator('.ant-drawer-title').filter({ hasText: '编辑草稿' })).toBeVisible()
  await expect(page.getByLabel('记录标题')).toHaveValue(/草稿恢复记录 desktop-.*已恢复/)
  await expect(page.getByLabel('工单主题')).toHaveValue(/待恢复工单 desktop-/)
  await expect(field(page, '优先级').locator('.ant-select-selection-item')).toHaveText('紧急')
  await expect(field(page, '负责部门').locator('.ant-select-selection-item')).toHaveText('业务运营部')
  await expect(page.getByRole('button', { name: '放弃草稿', exact: true })).toBeVisible()
  await page.screenshot({ path: '../.cursor/session/evidence/p4-b2/restart-readback-desktop.png' })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
  expect(errors).toEqual([])
})
