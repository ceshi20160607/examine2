import { randomUUID } from 'node:crypto'
import { createServer, type Server } from 'node:http'

import { expect, test, type Browser, type Page } from '@playwright/test'

test.use({ trace: 'off' })
test.setTimeout(180_000)

const rootUsername = process.env.E2E_ROOT_USERNAME
const rootPassword = process.env.E2E_ROOT_PASSWORD
const providerSecret = process.env.BATCH100_AI_SECRET ?? 'batch100-local-provider-secret'

let provider: Server
let providerBaseUrl = ''
const providerAuthorizations: string[] = []

test.beforeAll(async () => {
  provider = createServer((request, response) => {
    const chunks: Buffer[] = []
    request.on('data', chunk => chunks.push(Buffer.from(chunk)))
    request.on('end', () => {
      providerAuthorizations.push(String(request.headers.authorization ?? ''))
      const body = Buffer.concat(chunks).toString('utf8')
      const match = body.match(/BATCH100_FLOW_HISTORY\s+(\d+)/)
      const content = JSON.stringify({
        operation: 'FLOW_INSTANCE_HISTORY_QUERY',
        instanceId: match?.[1] ?? '1',
        limit: 20,
      })
      response.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' })
      response.end(JSON.stringify({
        choices: [{ message: { content } }],
        usage: { prompt_tokens: 10, completion_tokens: 6 },
      }))
    })
  })
  await new Promise<void>((resolve, reject) => {
    provider.once('error', reject)
    provider.listen(0, '127.0.0.1', () => resolve())
  })
  const address = provider.address()
  if (!address || typeof address === 'string') throw new Error('Batch100 provider fixture did not bind')
  providerBaseUrl = `http://127.0.0.1:${address.port}`
})

test.afterAll(async () => {
  if (provider?.listening) {
    await new Promise<void>((resolve, reject) => provider.close(error => error ? reject(error) : resolve()))
  }
})

async function apiData<T = Record<string, any>>(
  page: Page,
  path: string,
  method = 'GET',
  body?: unknown,
  idempotencyKey?: string,
  expectedStatus = 200,
) {
  const response = await page.evaluate(async input => {
    const csrf = document.cookie.split('; ')
      .find(item => item.startsWith('EXAMINE_CSRF='))?.split('=')[1] ?? ''
    const result = await fetch(input.path, {
      method: input.method,
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-Token': decodeURIComponent(csrf),
        'X-Request-ID': `batch100-${crypto.randomUUID()}`,
        ...(input.idempotencyKey ? { 'Idempotency-Key': input.idempotencyKey } : {}),
      },
      ...(input.body === undefined ? {} : { body: JSON.stringify(input.body) }),
    })
    return { status: result.status, payload: await result.json() }
  }, { path, method, body, idempotencyKey })
  expect(response.status, JSON.stringify(response.payload)).toBe(expectedStatus)
  return response.payload.data as T
}

async function loginRoot(page: Page) {
  await page.goto('/auth/login')
  await page.getByLabel('账号').fill(rootUsername!)
  await page.getByLabel('密码').fill(rootPassword!)
  await Promise.all([
    page.waitForURL(/\/platform\/workbench$/),
    page.getByRole('button', { name: '登录', exact: true }).click(),
  ])
}

async function provisionFlowHistory(page: Page) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 8)
  await apiData(page, '/api/v1/context/platform:switch', 'POST', {})
  const system = await apiData<{ id: string }>(page, '/api/v1/platform/admin/systems', 'POST', {
    code: `batch100_${suffix}`,
    name: `Batch100 Flow History ${suffix}`,
    description: 'Batch100 real Flow history Agent journey',
    tenantMode: 'SINGLE',
  }, randomUUID())
  const systemId = String(system.id)
  await apiData(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const current = await apiData<{ context: { memberId: string } }>(page, '/api/v1/me/context')
  const memberId = String(current.context.memberId)
  const configRoot = `/api/v1/systems/${systemId}/admin/config`
  const group = await apiData<{ id: string }>(page, `${configRoot}/module-groups`, 'POST', {
    code: 'operations', name: 'Operations', description: '', iconKey: 'folder',
    sortOrder: 0, status: 'ENABLED', draftRevision: '0',
  }, randomUUID())
  const module = await apiData<{ id: string }>(page, `${configRoot}/modules`, 'POST', {
    groupId: String(group.id), code: 'batch100_orders', name: 'Batch100 Orders',
    description: '', iconKey: 'clipboard', sortOrder: 0, status: 'ENABLED',
    allowComments: true, allowTeam: false, draftRevision: '1',
  }, randomUUID())
  await apiData(page, `${configRoot}/modules/${module.id}/fields`, 'POST', {
    dictionaryId: null, targetModuleId: null, code: 'subject', name: 'Subject', type: 'TEXT',
    sortOrder: 0, required: false, hidden: false, readonly: false, searchable: true,
    filterable: true, showInList: true, showInDetail: true, indexMode: 'SORT',
    status: 'ENABLED', properties: { maxLength: 200 }, draftRevision: '2',
  }, randomUUID())
  const checked = await apiData<{ id: string }>(page, `${configRoot}/checks`, 'POST', {
    draftRevision: '3',
  })
  const root = await apiData<{ version: string }>(page, configRoot)
  await apiData(page, `${configRoot}:publish`, 'POST', {
    checkId: String(checked.id), draftRevision: '3', configRootVersion: String(root.version),
    reason: 'Batch100 Flow history browser baseline',
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})

  const schema = await apiData<{ schemaVersionId: string }>(
    page, `/api/v1/systems/${systemId}/runtime/modules/batch100_orders/record-schema`)
  const created = await apiData<{ recordId: string; version: number }>(
    page, `/api/v1/systems/${systemId}/runtime/modules/batch100_orders/records`, 'POST', {
      schemaVersionId: String(schema.schemaVersionId),
      title: 'Batch100 bound order',
      values: { subject: 'Flow history visibility proof' },
    }, randomUUID(), 201)
  const active = await apiData<{ recordId: string }>(
    page, `/api/v1/systems/${systemId}/runtime/modules/batch100_orders/records/${created.recordId}:activate`,
    'POST', { expectedVersion: Number(created.version) }, randomUUID())

  const flowBase = `/api/v1/systems/${systemId}/flow`
  const definition = await apiData<{ definitionId: string }>(page, `${flowBase}/definitions`, 'POST', {
    name: `Batch100 Approval ${suffix}`,
      approverIds: [memberId],
  }, undefined, 201)
  await apiData(page, `${flowBase}/definitions/${definition.definitionId}/draft:check`, 'POST')
  await apiData(page, `${flowBase}/definitions/${definition.definitionId}:publish`, 'POST')
  const instance = await apiData<{ instanceId: string }>(
    page, `${flowBase}/definitions/${definition.definitionId}/instances`, 'POST', {
      definitionVersion: null,
      businessKey: `batch100-${suffix}`,
      recordBinding: { moduleCode: 'batch100_orders', recordId: String(active.recordId) },
      values: {},
    }, randomUUID(), 201)
  await apiData(page, `${flowBase}/instances/${instance.instanceId}:approve`, 'POST', {
    comment: 'Batch100 approved through the native Flow owner',
  }, randomUUID())
  const baselineHistory = await apiData<Record<string, any>>(
    page, `${flowBase}/instances/${instance.instanceId}/history`)

  const aiAdmin = `/api/v1/systems/${systemId}/admin/ai`
  const aiProvider = await apiData<{ id: string }>(page, `${aiAdmin}/providers`, 'POST', {
    code: `batch100_provider_${suffix}`,
    name: 'Batch100 local OpenAI-compatible provider',
    baseUrl: providerBaseUrl,
    model: 'batch100-fixture-model',
    secretRef: 'env://BATCH100_AI_SECRET',
    timeoutSeconds: 10,
    enabled: true,
  })
  const policy = await apiData<{ draftVersion: number }>(page, `${aiAdmin}/policy`, 'PUT', {
    expectedVersion: 0,
    providerId: String(aiProvider.id),
    moduleCodes: ['batch100_orders'],
    outboundFields: { batch100_orders: ['subject'] },
    allowedOperations: ['FLOW_INSTANCE_HISTORY_QUERY'],
    writableFields: {},
    fillFields: {},
    maxRows: 20,
    confirmationMode: 'REQUIRED',
    confirmationExpiresSeconds: 600,
    redactionMode: 'STRICT',
    promptVersion: 'batch100-browser-v1',
    enabled: true,
  })
  await apiData(page, `${aiAdmin}/policy:check`, 'POST', {})
  await apiData(page, `${aiAdmin}/policy:publish`, 'POST', {
    expectedVersion: policy.draftVersion,
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})
  return { systemId, flowBase, instanceId: String(instance.instanceId), baselineHistory }
}

async function registerAccount(page: Page) {
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 10_000)}`
  const username = `batch100_${suffix}`
  const oldPassword = 'Batch100-Old-Password-42!'
  const newPassword = 'Batch100-New-Password-84!'
  await page.goto('/auth/register')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('姓名').fill('Batch100 Account Security')
  await page.getByLabel('密码').fill(oldPassword)
  await page.getByLabel('系统名称').fill(`Batch100 Security ${suffix}`)
  await page.getByLabel('系统编码').fill(`b100_${suffix}`.slice(0, 32))
  await Promise.all([
    page.waitForURL(/\/systems\/\d+\/workbench$/),
    page.getByRole('button', { name: '创建并进入系统' }).click(),
  ])
  return { username, oldPassword, newPassword }
}

async function login(page: Page, username: string, password: string) {
  await page.goto('/auth/login')
  await page.getByLabel('账号').fill(username)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
}

async function secondSession(browser: Browser, baseURL: string | undefined, username: string, password: string) {
  const context = await browser.newContext({ baseURL })
  const page = await context.newPage()
  await login(page, username, password)
  await page.waitForURL(/\/platform\/workbench$/)
  return { context, page }
}

async function provisionLimitedAdminRoute(root: Page, member: Page) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 8)
  await apiData(root, '/api/v1/context/platform:switch', 'POST', {})
  const system = await apiData<{ id: string }>(root, '/api/v1/platform/admin/systems', 'POST', {
    code: `batch100_admin_${suffix}`,
    name: `Batch100 Limited Admin ${suffix}`,
    description: 'Batch100 administration-root reachability proof',
    tenantMode: 'SINGLE',
  }, randomUUID())
  const systemId = String(system.id)
  await apiData(root, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const tenants = await apiData<{ items: Record<string, any>[] }>(
    root, `/api/v1/systems/${systemId}/admin/tenants?size=200`)
  const tenant = tenants.items.find(item => item.isDefault) ?? tenants.items[0]
  const scopes = await apiData<{ items: Record<string, any>[] }>(
    root, `/api/v1/systems/${systemId}/admin/data-scopes?size=200`)
  const dataScope = scopes.items.find(item => item.kind === 'ALL')
  if (!tenant || !dataScope) throw new Error('Batch100 limited administrator owner data is incomplete')

  const role = await apiData<Record<string, any>>(
    root, `/api/v1/systems/${systemId}/admin/roles`, 'POST', {
      code: `limited_admin_${suffix}`,
      name: `Batch100 Limited Admin ${suffix}`,
      description: 'Config permission without system.admin.access must fall through',
    }, randomUUID())
  const draft = await apiData<Record<string, any>>(
    root, `/api/v1/systems/${systemId}/admin/roles/${role.id}/draft`, 'PUT', {
      name: role.name,
      description: role.description,
      permissionCodes: ['system.runtime.access', 'module.config.manage', 'event.template.manage'],
      deniedPermissionCodes: [],
      dataScopeId: String(dataScope.id),
      version: String(role.version),
    })
  const checked = await apiData<Record<string, any>>(
    root, `/api/v1/systems/${systemId}/admin/roles/${role.id}/draft:check`, 'POST', {
      version: String(draft.version),
    }, randomUUID())
  await apiData(root, `/api/v1/systems/${systemId}/admin/roles/${role.id}/draft:publish`, 'POST', {
    version: String(checked.version),
  }, randomUUID())
  await apiData(root, '/api/v1/auth/refresh', 'POST', {})

  await apiData(member, '/api/v1/context/platform:switch', 'POST', {})
  const requested = await apiData<Record<string, any>>(
    member, `/api/v1/context/systems/${systemId}/access-requests`, 'POST', {
      targetTenantId: String(tenant.id),
      reason: `Batch100 limited route ${suffix}`,
    }, randomUUID())
  await apiData(root, `/api/v1/systems/${systemId}/admin/access-requests/${requested.id}:approve`, 'POST', {
    reason: 'Batch100 grants the exact limited administration role',
    version: String(requested.version),
    tenantIds: [String(tenant.id)],
    roleIds: [String(role.id)],
  }, randomUUID())
  await apiData(member, '/api/v1/auth/refresh', 'POST', {})
  await apiData(member, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  return systemId
}

test('changes the password and revokes every other browser session', async ({ page, browser, baseURL }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'Batch100 real account-security journey runs once on desktop Edge')
  const account = await registerAccount(page)
  const sibling = await secondSession(browser, baseURL, account.username, account.oldPassword)
  try {
    await page.getByRole('button', { name: /Batch100 Account Security/ }).click()
    await page.locator('.account-security-menu-trigger').click()
    await expect(page.locator('.account-security-dialog')).toBeVisible()
    await page.locator('.account-security-current-password input').fill(account.oldPassword)
    await page.locator('.account-security-new-password input').fill(account.newPassword)
    await page.locator('.account-security-repeat-password input').fill(account.newPassword)
    await Promise.all([
      page.waitForURL(/\/auth\/login\?notice=password-changed$/),
      page.locator('.account-security-submit').click(),
    ])
    await expect(page.locator('.password-change-success')).toBeVisible()

    const siblingStatus = await sibling.page.evaluate(async () => {
      const result = await fetch('/api/v1/me/context', { credentials: 'same-origin' })
      return result.status
    })
    expect(siblingStatus).toBe(401)

    await page.getByLabel('账号').fill(account.username)
    await page.getByLabel('密码').fill(account.oldPassword)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await expect(page.locator('.form-alert').filter({ hasText: '账号或密码不正确' })).toBeVisible()
    await page.getByLabel('密码').fill(account.newPassword)
    await Promise.all([
      page.waitForURL(/\/platform\/workbench$/),
      page.getByRole('button', { name: '登录', exact: true }).click(),
    ])
    await page.screenshot({
      path: '../.cursor/session/evidence/fast-account-security-flow-history-100/password-change-relogin.png',
      fullPage: true,
    })
  } finally {
    await sibling.context.close()
  }
})

test('reads a native bound Flow history through the System Agent without business writes', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'Batch100 real Flow history journey runs once on desktop Edge')
  test.skip(!rootUsername || !rootPassword, 'E2E_ROOT_USERNAME and E2E_ROOT_PASSWORD are required')
  await loginRoot(page)
  const fixture = await provisionFlowHistory(page)
  await page.goto(`/systems/${fixture.systemId}/agent`)
  await expect(page.locator('.agent-layout')).toBeVisible()
  await page.locator('.agent-session-title').fill('Batch100 Flow instance history')
  await page.locator('.agent-session-create-button').click()
  await expect(page.locator('.agent-message-input')).toBeVisible()
  await page.locator('.agent-message-input').fill(`BATCH100_FLOW_HISTORY ${fixture.instanceId}`)
  await page.locator('.agent-message-submit').click()

  const result = page.locator('.agent-flow-instance-history-result')
  await expect(result).toBeVisible()
  await expect(result).toContainText(`审批实例 ${fixture.instanceId}`)
  await expect(result).toContainText('Batch100 approved through the native Flow owner')
  await expect(result.locator('li[data-sequence]')).toHaveCount(2)
  await expect(result.locator('.agent-flow-instance-history-route'))
    .toHaveAttribute('href', `/systems/${fixture.systemId}/flows`)

  const afterHistory = await apiData<Record<string, any>>(
    page, `${fixture.flowBase}/instances/${fixture.instanceId}/history`)
  expect(afterHistory).toEqual(fixture.baselineHistory)
  expect(providerAuthorizations).toContain(`Bearer ${providerSecret}`)
  await page.screenshot({
    path: '../.cursor/session/evidence/fast-account-security-flow-history-100/flow-instance-history.png',
    fullPage: true,
  })
})

test('routes a permission-limited administrator to the first fully authorized owner page', async ({ page, browser, baseURL }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'Batch100 real administration-root journey runs once on desktop Edge')
  test.skip(!rootUsername || !rootPassword, 'E2E_ROOT_USERNAME and E2E_ROOT_PASSWORD are required')
  await loginRoot(page)
  const context = await browser.newContext({ baseURL })
  const memberPage = await context.newPage()
  try {
    await registerAccount(memberPage)
    const systemId = await provisionLimitedAdminRoute(page, memberPage)
    await memberPage.goto(`/systems/${systemId}/admin`)
    await expect(memberPage).toHaveURL(new RegExp(`/systems/${systemId}/admin/message-templates$`))
    await expect(memberPage).not.toHaveURL(/\/admin\/configuration$/)
    await memberPage.screenshot({
      path: '../.cursor/session/evidence/fast-account-security-flow-history-100/limited-admin-root.png',
      fullPage: true,
    })
  } finally {
    await context.close()
  }
})
