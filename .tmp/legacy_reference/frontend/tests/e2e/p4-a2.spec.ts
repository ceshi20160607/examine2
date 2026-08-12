import { execFileSync } from 'node:child_process'

import { expect, test, type Page } from '@playwright/test'

test.use({ trace: 'off' })
test.setTimeout(120_000)

const memberUsername = process.env.E2E_P4_A2_USERNAME
const memberPassword = process.env.E2E_P4_A2_PASSWORD
const systemId = process.env.E2E_P4_A2_SYSTEM_ID
const databaseHost = process.env.E2E_DB_HOST
const databaseUsername = process.env.E2E_DB_USERNAME
const databasePassword = process.env.E2E_DB_PASSWORD

function requireEnvironment() {
  test.skip(!memberUsername || !memberPassword, 'P4-A2 ordinary-member credentials are required')
  test.skip(!systemId, 'P4-A2 stable system id is required')
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

async function choose(page: Page, fieldName: string, optionName?: string) {
  const field = page.locator('.runtime-form-field').filter({ has: page.getByText(fieldName, { exact: true }) })
  await field.locator('.ant-select-selector').click()
  const options = page.locator('.ant-select-dropdown:visible .ant-select-item-option')
  if (optionName) await options.filter({ hasText: optionName }).click()
  else await options.first().click()
}

function cleanupDraft(recordId: string) {
  if (!/^\d+$/.test(recordId)) return
  execFileSync('docker', [
    'run', '--rm', 'mysql:8.0.44', 'mysql',
    `--host=${databaseHost}`, '--port=3306', `--user=${databaseUsername}`, `--password=${databasePassword}`,
    '-e', `DELETE FROM examine2.un_module_record_search WHERE record_id=${recordId};
      DELETE FROM examine2.un_module_record_index WHERE record_id=${recordId};
      DELETE FROM examine2.un_module_record_value WHERE record_id=${recordId};
      DELETE FROM examine2.un_module_record WHERE record_id=${recordId} AND status='DRAFT';`,
  ], { stdio: 'pipe' })
}

test('ordinary member creates a typed draft, activates it and reloads the detail', async ({ page }) => {
  requireEnvironment()
  const errors: string[] = []
  let incompleteDraftId = ''
  page.on('console', (message) => {
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) {
      errors.push(`console: ${message.text()}`)
    }
  })
  page.on('pageerror', (error) => errors.push(`page: ${error.message}`))
  page.on('response', (response) => {
    if (response.status() >= 500) errors.push(`http ${response.status()}: ${response.url()}`)
  })

  try {
    await login(page)
    await page.goto(`/systems/${systemId}/workbench?module=work_order`)
    await expect(page.getByRole('heading', { name: 'P4 基础字段验收' })).toBeVisible()
    await expect(page.getByRole('heading', { name: '现场工单' })).toBeVisible()
    await page.getByRole('button', { name: '新建记录' }).click()
    await expect(page.locator('.ant-drawer-title')).toHaveText('新建现场工单')

    await page.getByRole('button', { name: '保存草稿' }).click()
    await expect(page.getByText(/草稿已保存/)).toBeVisible()
    incompleteDraftId = new URL(page.url()).searchParams.get('draft') ?? ''
    expect(incompleteDraftId).toMatch(/^\d+$/)
    await page.getByRole('button', { name: '激活记录' }).click()
    await expect(page.getByText('记录存在未填写的必填字段')).toBeVisible()
    await expect(page.getByText('工单主题 为必填字段')).toBeVisible()
    await page.locator('.ant-drawer .ant-drawer-close').click()

    await page.getByRole('button', { name: '新建记录' }).click()
    const title = `浏览器验收-${test.info().project.name}-${Date.now()}`
    await page.getByLabel('记录标题').fill(title)
    await page.getByLabel('工单主题').fill('现场配电柜巡检')
    await page.getByLabel('处理说明').fill('完成八类基础字段的真实表单录入与重启读回。')
    await page.locator('.runtime-form-field').filter({ hasText: '预计费用' }).locator('input').fill('950.75')
    await page.locator('input[type="date"]').fill('2026-08-01')
    await page.locator('input[type="datetime-local"]').fill('2026-07-22T10:30')
    await choose(page, '优先级', '紧急')
    await choose(page, '负责人', 'Admin')
    await choose(page, '负责部门', '业务运营部')
    await page.getByRole('button', { name: '保存草稿' }).click()
    await expect(page.getByText(/草稿已保存/)).toBeVisible()
    const activeDraftId = new URL(page.url()).searchParams.get('draft') ?? ''
    expect(activeDraftId).toMatch(/^\d+$/)
    await page.screenshot({
      path: `../.cursor/session/evidence/p4-a2/create-draft-${test.info().project.name}.png`,
    })

    await page.getByRole('button', { name: '激活记录' }).click()
    await expect(page.locator('.ant-drawer').getByText(title, { exact: true })).toBeVisible()
    await expect(page.locator('.ant-drawer').getByText('现场配电柜巡检', { exact: true })).toBeVisible()
    await expect(page.locator('.ant-drawer').getByText('紧急', { exact: true })).toBeVisible()
    await expect(page).toHaveURL(new RegExp(`record=${activeDraftId}`))
    await page.reload()
    await expect(page.locator('.ant-drawer').getByText(title, { exact: true })).toBeVisible()
    await expect(page.locator('.ant-drawer').getByText('2026-08-01', { exact: true })).toBeVisible()
    await page.screenshot({
      path: `../.cursor/session/evidence/p4-a2/activated-detail-${test.info().project.name}.png`,
    })

    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true)
    expect(errors).toEqual([])
  } finally {
    if (incompleteDraftId) cleanupDraft(incompleteDraftId)
  }
})
