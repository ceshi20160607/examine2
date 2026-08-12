import { expect, test, type Page } from '@playwright/test'

interface ApiCall {
  method: string
  path: string
}

type EntryScenario = 'enabled-owner' | 'disabled-member' | 'disabled-admin'

const account = { id: 'account-entry-1', username: 'entry_owner', displayName: '系统入口管理员' }
const enabledSystem = {
  id: 'system-entry-1',
  code: 'entry_system',
  name: '入口验证系统',
  status: 'ACTIVE',
  memberStatus: 'ACTIVE',
  defaultTenantId: 'tenant-entry-1',
  roleNames: ['系统超级管理员'],
  recentEnteredAt: '2026-08-10T09:10:00+08:00',
}
const disabledSystem = {
  ...enabledSystem,
  id: 'system-disabled-1',
  code: 'disabled_system',
  name: '已停用验证系统',
  status: 'DISABLED',
}
const defaultTenant = {
  id: 'tenant-entry-1',
  code: 'default',
  name: '默认租户',
  status: 'ACTIVE',
  isDefault: true,
}

function platformContext(system = enabledSystem) {
  return {
    account,
    context: {
      type: 'PLATFORM',
      account,
      roleIds: [],
      dataScope: null,
      permissionVersion: '1',
      permissions: ['platform.runtime.access'],
      shells: ['PLATFORM_RUNTIME'],
      restrictedMode: 'NONE',
    },
    systems: [{ ...system, id: 'stale-login-snapshot', name: '不应显示的登录快照' }],
    tenants: [],
    firstSystemId: null,
  }
}

function systemContext(mode: 'NONE' | 'ADMIN_SETTINGS_ONLY') {
  const settingsOnly = mode === 'ADMIN_SETTINGS_ONLY'
  const system = settingsOnly ? disabledSystem : enabledSystem
  return {
    account,
    context: {
      type: 'SYSTEM',
      account,
      systemId: system.id,
      systemName: system.name,
      tenantId: 'tenant-entry-1',
      tenantName: '默认租户',
      memberId: 'member-entry-1',
      roleIds: ['role-entry-1'],
      dataScope: { id: 'scope-entry-1', code: 'all_data', kind: 'ALL' },
      permissionVersion: '4',
      permissions: settingsOnly
        ? ['system.admin.access']
        : ['system.admin.access', 'system.runtime.access'],
      shells: settingsOnly
        ? ['SYSTEM_ADMIN']
        : ['SYSTEM_RUNTIME', 'SYSTEM_ADMIN'],
      restrictedMode: mode,
    },
    systems: [system],
    tenants: [defaultTenant],
    firstSystemId: null,
  }
}

function envelope(data: unknown, code = 'OK', message = '') {
  return {
    code,
    message,
    data,
    requestId: 'p1-entry-browser-request',
    traceId: 'p1-entry-browser-trace',
    errors: [],
  }
}

async function installSystemEntryApi(page: Page, scenario: EntryScenario) {
  let authenticatedContext: ReturnType<typeof platformContext> | ReturnType<typeof systemContext> | null = null
  const apiCalls: ApiCall[] = []
  const listedSystem = scenario === 'enabled-owner' ? enabledSystem : disabledSystem

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const method = request.method()
    const path = new URL(request.url()).pathname
    apiCalls.push({ method, path })

    if (method === 'GET' && path === '/api/v1/me/context') {
      await route.fulfill(authenticatedContext
        ? { status: 200, json: envelope(authenticatedContext) }
        : { status: 401, json: envelope(null, 'AUTH_SESSION_REQUIRED', 'Authentication required') })
      return
    }
    if (method === 'POST' && path === '/api/v1/auth/refresh') {
      await route.fulfill(authenticatedContext
        ? { status: 200, json: envelope(authenticatedContext) }
        : { status: 401, json: envelope(null, 'AUTH_REFRESH_INVALID', 'Refresh required') })
      return
    }
    if (method === 'POST' && path === '/api/v1/auth/login') {
      const input = request.postDataJSON() as { account?: string; password?: string }
      const valid = input.account === 'entry_owner' && input.password === 'entry-browser-password'
      if (!valid) {
        await route.fulfill({ status: 401, json: envelope(null, 'AUTH_INVALID_CREDENTIALS', 'Invalid credentials') })
        return
      }
      authenticatedContext = platformContext(listedSystem)
      await route.fulfill({ status: 200, json: envelope(authenticatedContext) })
      return
    }
    if (method === 'GET' && path === '/api/v1/context/systems') {
      await route.fulfill({ status: 200, json: envelope([listedSystem]) })
      return
    }

    const selectedPath = `/api/v1/context/systems/${listedSystem.id}:switch`
    if (method === 'POST' && path === selectedPath) {
      if (scenario === 'disabled-member') {
        await route.fulfill({
          status: 403,
          json: envelope(null, 'SYSTEM_DISABLED', 'Raw disabled system diagnostic'),
        })
        return
      }
      authenticatedContext = systemContext(scenario === 'disabled-admin' ? 'ADMIN_SETTINGS_ONLY' : 'NONE')
      await route.fulfill({ status: 200, json: envelope(authenticatedContext) })
      return
    }
    if (method === 'POST' && path === '/api/v1/auth/logout') {
      authenticatedContext = null
      await route.fulfill({ status: 200, json: envelope(null) })
      return
    }

    await route.fulfill({ status: 404, json: envelope(null, 'UNEXPECTED_API', 'Unexpected API') })
  })

  return apiCalls
}

function monitorBrowser(page: Page, additionallyExpectedStatus?: string) {
  const problems: string[] = []
  page.on('console', (message) => {
    const location = message.location().url
    const expectedAnonymous = ['/api/v1/me/context', '/api/v1/auth/refresh']
      .some((path) => location.endsWith(path)) && message.text().includes('401 (Unauthorized)')
    const expectedScenarioError = additionallyExpectedStatus
      && location.endsWith('/api/v1/context/systems/system-disabled-1:switch')
      && message.text().includes(additionallyExpectedStatus)
    if (['error', 'warning'].includes(message.type()) && !expectedAnonymous && !expectedScenarioError) {
      problems.push(`console: ${message.text()}`)
    }
  })
  page.on('pageerror', (error) => problems.push(`page: ${error.message}`))
  return problems
}

async function signInAndWaitForList(page: Page) {
  await page.goto('/login')
  await page.getByLabel('账号').fill('entry_owner')
  await page.getByLabel('密码').fill('entry-browser-password')
  await Promise.all([
    page.waitForURL(/\/platform\/systems$/),
    page.getByRole('button', { name: '登录', exact: true }).click(),
  ])
  await expect(page.getByRole('heading', { name: '我的系统', exact: true })).toBeVisible()
  await expect(page.getByText('不应显示的登录快照', { exact: true })).toHaveCount(0)
}

function assertOnlyCalls(apiCalls: ApiCall[], allowed: string[]) {
  const allowlist = new Set(allowed)
  expect(apiCalls
    .map((call) => `${call.method} ${call.path}`)
    .filter((call) => !allowlist.has(call))).toEqual([])
  const paths = apiCalls.map((call) => call.path)
  expect(paths.filter((path) => path === '/api/v1/context/platform:switch')).toEqual([])
  expect(paths.filter((path) => /\/tenants?(?:\/|$|:)/i.test(path))).toEqual([])
  expect(paths.filter((path) => /\/(?:modules?|operations|reports|favorites|follow|sso|ai)(?:\/|$|:)/i.test(path))).toEqual([])
}

async function expectNoFutureNavigation(page: Page) {
  await expect(page.getByText(/运营|报表|收藏|关注|SSO|AI|模块|租户|创建系统|新建系统|平台切换|占位|调试/)).toHaveCount(0)
}

test('enabled owner enters onboarding, survives reload and logs out through the exact API boundary', async ({ page }) => {
  const apiCalls = await installSystemEntryApi(page, 'enabled-owner')
  const browserProblems = monitorBrowser(page)

  await signInAndWaitForList(page)
  await expect(page.getByText('入口验证系统', { exact: true })).toBeVisible()
  await expect(page.getByText('系统超级管理员', { exact: true })).toBeVisible()
  await expect(page.locator('nav').getByRole('link', { name: '我的系统', exact: true })).toBeVisible()
  await expectNoFutureNavigation(page)

  await Promise.all([
    page.waitForURL(/\/systems\/system-entry-1\/admin\/onboarding$/),
    page.getByRole('button', { name: '进入系统 入口验证系统', exact: true }).click(),
  ])

  await expect(page.getByText('系统上下文已就绪', { exact: true })).toBeVisible()
  await expect(page.getByRole('heading', { name: '欢迎进入 入口验证系统', exact: true })).toBeVisible()
  await expect(page.getByText('系统超级管理员', { exact: true })).toBeVisible()
  await expect(page.locator('nav').getByRole('link', { name: '配置引导', exact: true })).toBeVisible()
  await expectNoFutureNavigation(page)

  await page.reload()
  await expect(page).toHaveURL('/systems/system-entry-1/admin/onboarding')
  await expect(page.getByText('系统上下文已就绪', { exact: true })).toBeVisible()
  await expect(page.getByText('系统超级管理员', { exact: true })).toBeVisible()

  await Promise.all([
    page.waitForURL(/\/login$/),
    page.getByRole('button', { name: '退出登录', exact: true }).click(),
  ])
  await expect(page.getByRole('heading', { name: '登录', exact: true })).toBeVisible()

  const paths = apiCalls.map((call) => call.path)
  expect(paths.filter((path) => path === '/api/v1/context/systems')).toHaveLength(1)
  expect(paths.filter((path) => path === '/api/v1/context/systems/system-entry-1:switch')).toHaveLength(1)
  expect(paths.filter((path) => path === '/api/v1/me/context')).toHaveLength(2)
  expect(paths.filter((path) => path === '/api/v1/auth/refresh')).toHaveLength(1)
  expect(paths.filter((path) => path === '/api/v1/auth/logout')).toHaveLength(1)
  assertOnlyCalls(apiCalls, [
    'GET /api/v1/me/context',
    'POST /api/v1/auth/refresh',
    'POST /api/v1/auth/login',
    'GET /api/v1/context/systems',
    'POST /api/v1/context/systems/system-entry-1:switch',
    'POST /api/v1/auth/logout',
  ])
  expect(browserProblems).toEqual([])
})

test('ordinary member stays in My Systems with an explicit disabled-system denial', async ({ page }) => {
  const apiCalls = await installSystemEntryApi(page, 'disabled-member')
  const browserProblems = monitorBrowser(page, '403 (Forbidden)')

  await signInAndWaitForList(page)
  await expect(page.getByText('已停用验证系统', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '进入系统 已停用验证系统', exact: true }).click()

  await expect(page).toHaveURL('/platform/systems')
  await expect(page.getByRole('alert')).toContainText('该系统当前已停用，当前账号不能进入。')
  await expect(page.getByRole('alert')).not.toContainText(/Raw disabled|request|trace/i)
  await expectNoFutureNavigation(page)

  const paths = apiCalls.map((call) => call.path)
  expect(paths.filter((path) => path === '/api/v1/context/systems')).toHaveLength(1)
  expect(paths.filter((path) => path === '/api/v1/context/systems/system-disabled-1:switch')).toHaveLength(1)
  expect(paths.filter((path) => path === '/api/v1/me/context')).toHaveLength(1)
  expect(paths.filter((path) => path === '/api/v1/auth/refresh')).toHaveLength(1)
  assertOnlyCalls(apiCalls, [
    'GET /api/v1/me/context',
    'POST /api/v1/auth/refresh',
    'POST /api/v1/auth/login',
    'GET /api/v1/context/systems',
    'POST /api/v1/context/systems/system-disabled-1:switch',
  ])
  expect(browserProblems).toEqual([])
})

test('disabled administrator enters settings-only and keeps the restriction after reload', async ({ page }) => {
  const apiCalls = await installSystemEntryApi(page, 'disabled-admin')
  const browserProblems = monitorBrowser(page)

  await signInAndWaitForList(page)
  await Promise.all([
    page.waitForURL(/\/systems\/system-disabled-1\/admin\/onboarding$/),
    page.getByRole('button', { name: '进入系统 已停用验证系统', exact: true }).click(),
  ])

  await expect(page.getByText('系统已停用，仅可进行管理设置', { exact: true })).toBeVisible()
  await expect(page.locator('nav').getByRole('link', { name: '配置引导', exact: true })).toBeVisible()
  await expect(page.getByText(/SYSTEM_RUNTIME|运行入口/)).toHaveCount(0)
  await expectNoFutureNavigation(page)

  await page.reload()
  await expect(page).toHaveURL('/systems/system-disabled-1/admin/onboarding')
  await expect(page.getByText('系统已停用，仅可进行管理设置', { exact: true })).toBeVisible()
  await expect(page.getByText(/SYSTEM_RUNTIME|运行入口/)).toHaveCount(0)

  const paths = apiCalls.map((call) => call.path)
  expect(paths.filter((path) => path === '/api/v1/context/systems')).toHaveLength(1)
  expect(paths.filter((path) => path === '/api/v1/context/systems/system-disabled-1:switch')).toHaveLength(1)
  expect(paths.filter((path) => path === '/api/v1/me/context')).toHaveLength(2)
  expect(paths.filter((path) => path === '/api/v1/auth/refresh')).toHaveLength(1)
  assertOnlyCalls(apiCalls, [
    'GET /api/v1/me/context',
    'POST /api/v1/auth/refresh',
    'POST /api/v1/auth/login',
    'GET /api/v1/context/systems',
    'POST /api/v1/context/systems/system-disabled-1:switch',
  ])
  expect(browserProblems).toEqual([])
})
