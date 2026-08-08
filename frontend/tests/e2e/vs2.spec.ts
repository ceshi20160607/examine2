import { randomUUID } from 'node:crypto'

import { expect, test, type Locator, type Page, type Response } from '@playwright/test'

// Root credentials must never be recorded in a retained Playwright trace.
test.use({ trace: 'off' })

const rootUsername = process.env.E2E_ROOT_USERNAME
const rootPassword = process.env.E2E_ROOT_PASSWORD
const mobileReadonlyNotice = '当前宽度仅支持查看，管理操作请使用桌面端。'

function uniqueValue(prefix: string) {
  return `${prefix}_${Date.now().toString(36)}_${randomUUID().replaceAll('-', '').slice(0, 6)}`.toLowerCase()
}

function requireRootCredentials() {
  test.skip(!rootUsername || !rootPassword, 'E2E_ROOT_USERNAME and E2E_ROOT_PASSWORD are required')
  return { username: rootUsername!, password: rootPassword! }
}

async function login(page: Page, account: string, password: string) {
  await page.goto('/auth/login')
  await page.getByLabel('账号').fill(account)
  await page.getByLabel('密码').fill(password)
  await Promise.all([
    page.waitForURL(/\/platform\/workbench$/),
    page.getByRole('button', { name: '登录', exact: true }).click(),
  ])
}

async function openPersonalMenu(page: Page) {
  await page.locator('button[title="个人菜单"]').click()
}

async function responseData(response: Response): Promise<Record<string, unknown>> {
  expect(response.ok(), `unexpected response ${response.status()} for ${response.url()}`).toBe(true)
  const envelope = await response.json() as { data?: Record<string, unknown> }
  expect(envelope.data).toBeTruthy()
  return envelope.data!
}

async function createSystemThroughUi(page: Page, name: string, code: string) {
  await page.getByRole('navigation', { name: '平台后台导航' }).getByRole('link', { name: '系统治理' }).click()
  await expect(page.getByRole('heading', { name: '系统治理' })).toBeVisible()
  await page.getByRole('button', { name: '创建系统' }).click()

  const dialog = page.locator('.ant-modal:visible')
  await expect(dialog.getByText('创建系统', { exact: true })).toBeVisible()
  await dialog.getByLabel('系统名称').fill(name)
  await dialog.getByLabel('系统编码').fill(code)
  await dialog.getByLabel('系统说明').fill('由 VS2 Playwright 真实 UI 旅程创建')

  const [createdResponse] = await Promise.all([
    page.waitForResponse((response) =>
      response.request().method() === 'POST' &&
      new URL(response.url()).pathname === '/api/v1/platform/admin/systems',
    ),
    dialog.getByRole('button', { name: /保\s*存/ }).click(),
  ])
  const created = await responseData(createdResponse)
  const systemId = String(created.id ?? '')
  expect(systemId).toMatch(/^\d+$/)
  await expect(page.getByText('系统信息已保存')).toBeVisible()
  await expect(page.getByRole('cell', { name: new RegExp(name) })).toBeVisible()
  return systemId
}

async function selectFirstAntOption(page: Page, select: Locator) {
  await select.click()
  const option = page.locator('.ant-select-dropdown:visible .ant-select-item-option').first()
  await expect(option).toBeVisible()
  await option.click()
}

async function assertNoHorizontalOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
  }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport + 1)
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport + 1)
}

test.describe('VS2 desktop Root journey', () => {
  test('creates a system and reaches every platform/system administration area', async ({ page }) => {
    test.skip(test.info().project.name !== 'desktop', 'desktop-only journey')
    const root = requireRootCredentials()
    const suffix = uniqueValue('desk')
    const systemName = `VS2 Desktop ${suffix}`
    const systemCode = `vs2_${suffix}`.slice(0, 32)

    await login(page, root.username, root.password)
    await openPersonalMenu(page)
    await page.getByRole('menuitem', { name: /平台后台/ }).click()
    await expect(page).toHaveURL(/\/platform\/admin\/systems$/)
    await expect(page.getByRole('heading', { name: '系统治理' })).toBeVisible()

    const platformNav = page.getByRole('navigation', { name: '平台后台导航' })
    await platformNav.getByRole('link', { name: '组织账号' }).click()
    await expect(page.getByRole('heading', { name: '组织账号' })).toBeVisible()

    await platformNav.getByRole('link', { name: '平台角色' }).click()
    await expect(page.getByRole('heading', { name: '平台角色' })).toBeVisible()
    const effectColumns = page.locator('.permission-effect-columns').first()
    await expect(effectColumns.getByText('允许', { exact: true })).toBeVisible()
    await expect(effectColumns.getByText('拒绝', { exact: true })).toBeVisible()

    await page.getByRole('button', { name: '权限预览' }).click()
    const drawer = page.locator('.ant-drawer:visible')
    await expect(drawer.getByText('平台账号权限预览', { exact: true })).toBeVisible()
    const accountSelect = drawer.locator('.permission-principal-select')
    await expect(accountSelect).toBeVisible()
    await selectFirstAntOption(page, accountSelect)
    await drawer.getByRole('button', { name: '查看权限' }).click()
    await expect(drawer.getByText('来源角色', { exact: true })).toBeVisible()
    await expect(drawer.getByText('数据范围', { exact: true })).toBeVisible()
    await expect(drawer.getByText('权限决策', { exact: true })).toBeVisible()
    const decision = drawer.locator('.permission-decision-row').first()
    await expect(decision).toBeVisible()
    await expect(decision.locator('code')).not.toBeEmpty()
    await expect(decision.locator('.ant-tag')).toHaveText(/ALLOW|DENY/)
    await page.keyboard.press('Escape')

    const systemId = await createSystemThroughUi(page, systemName, systemCode)

    await page.getByRole('link', { name: '返回工作台' }).click()
    const systemRow = page.getByRole('button', { name: new RegExp(systemName) })
    await expect(systemRow).toBeVisible()
    await systemRow.click()
    await expect(page).toHaveURL(new RegExp(`/systems/${systemId}/workbench$`))
    await expect(page.getByRole('heading', { name: systemName })).toBeVisible()

    await openPersonalMenu(page)
    await page.getByRole('menuitem', { name: /系统后台/ }).click()
    await expect(page).toHaveURL(new RegExp(`/systems/${systemId}/admin/settings$`))

    const systemNav = page.getByRole('navigation', { name: '系统后台导航' })
    const destinations = [
      ['系统设置', '系统设置'],
      ['租户管理', '租户管理'],
      ['组织成员', '组织成员'],
      ['角色权限', '角色权限'],
      ['访问申请', '访问申请'],
    ] as const
    for (const [linkName, headingName] of destinations) {
      await systemNav.getByRole('link', { name: linkName }).click()
      await expect(page.getByRole('heading', { name: headingName })).toBeVisible()
    }
  })
})

test.describe('VS2 mobile boundary journey', () => {
  test('registers, switches system context, and keeps administration read-only without overflow', async ({ page }) => {
    test.skip(test.info().project.name !== 'mobile', 'mobile-only journey')
    const suffix = uniqueValue('mob')
    const username = `user_${suffix}`.slice(0, 32)
    const password = `M9!${randomUUID()}aA`
    const systemName = `VS2 Mobile ${suffix}`
    const systemCode = `m_${suffix}`.slice(0, 32)

    await page.goto('/auth/register')
    await page.getByLabel('用户名').fill(username)
    await page.getByLabel('姓名').fill('VS2 移动端用户')
    await page.getByLabel('密码').fill(password)
    await page.getByLabel('系统名称').fill(systemName)
    await page.getByLabel('系统编码').fill(systemCode)
    await Promise.all([
      page.waitForURL(/\/systems\/\d+\/workbench$/),
      page.getByRole('button', { name: '创建并进入系统' }).click(),
    ])
    const systemId = page.url().match(/\/systems\/(\d+)\/workbench$/)?.[1]
    expect(systemId).toBeTruthy()
    await expect(page.getByRole('heading', { name: systemName })).toBeVisible()
    await assertNoHorizontalOverflow(page)

    await openPersonalMenu(page)
    await page.getByRole('menuitem', { name: /返回平台/ }).click()
    await expect(page).toHaveURL(/\/platform\/workbench$/)
    const systemRow = page.getByRole('button', { name: new RegExp(systemName) })
    await expect(systemRow).toBeVisible()
    await systemRow.click()
    await expect(page).toHaveURL(new RegExp(`/systems/${systemId}/workbench$`))

    await openPersonalMenu(page)
    await page.getByRole('menuitem', { name: /系统后台/ }).click()
    await page.getByRole('navigation', { name: '系统后台导航' }).getByRole('link', { name: '租户管理' }).click()
    await expect(page.getByRole('heading', { name: '租户管理' })).toBeVisible()
    await expect(page.getByText(mobileReadonlyNotice)).toBeVisible()
    await expect(page.getByRole('button', { name: '新增租户' })).toHaveCount(0)
    await assertNoHorizontalOverflow(page)

    await page.getByRole('navigation', { name: '系统后台导航' }).getByRole('link', { name: '角色权限' }).click()
    await expect(page.getByRole('heading', { name: '角色权限' })).toBeVisible()
    await expect(page.getByText(mobileReadonlyNotice)).toBeVisible()
    await expect(page.getByRole('button', { name: '新增角色' })).toHaveCount(0)
    await expect(page.getByRole('button', { name: '权限预览' })).toHaveCount(0)
    await assertNoHorizontalOverflow(page)
  })
})
