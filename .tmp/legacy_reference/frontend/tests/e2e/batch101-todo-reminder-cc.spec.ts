import { randomUUID } from 'node:crypto'

import { expect, test, type Browser, type Page } from '@playwright/test'

test.use({ trace: 'off' })
test.setTimeout(180_000)

const rootUsername = process.env.E2E_ROOT_USERNAME
const rootPassword = process.env.E2E_ROOT_PASSWORD

interface ApiPage<T> {
  items: T[]
  total: number
}

interface EventMessage {
  id: string
  templateCode: string
  status: 'UNREAD' | 'READ' | 'ARCHIVED'
  version: number
  body: string
}

interface TodoItem {
  id: string
  sourceId: string
  sourceType: string
  category: string
  routeHint: string
  status: string
  closeReason: string | null
}

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
        'X-Request-ID': `batch101-${crypto.randomUUID()}`,
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

async function registerMember(browser: Browser, baseURL: string | undefined) {
  const context = await browser.newContext({ baseURL })
  const page = await context.newPage()
  const suffix = `${Date.now()}_${Math.floor(Math.random() * 10_000)}`
  await page.goto('/auth/register')
  await page.getByLabel('用户名').fill(`batch101_${suffix}`)
  await page.getByLabel('姓名').fill('Batch101 Recipient')
  await page.getByLabel('密码').fill('Batch101-Member-Password-42!')
  await page.getByLabel('系统名称').fill(`Batch101 Member Home ${suffix}`)
  await page.getByLabel('系统编码').fill(`b101_${suffix}`.slice(0, 32))
  await Promise.all([
    page.waitForURL(/\/systems\/\d+\/workbench$/),
    page.getByRole('button', { name: '创建并进入系统' }).click(),
  ])
  return { context, page }
}

async function provisionRecipient(root: Page, member: Page) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 8)
  await apiData(root, '/api/v1/context/platform:switch', 'POST', {})
  const system = await apiData<{ id: string }>(
    root,
    '/api/v1/platform/admin/systems',
    'POST',
    {
      code: `batch101_${suffix}`,
      name: `Batch101 Todo Event ${suffix}`,
      description: 'Real reminder and CC Todo owner journey',
      tenantMode: 'MULTI',
    },
    randomUUID(),
  )
  const systemId = String(system.id)
  await apiData(root, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const current = await apiData<{ context: { memberId: string } }>(root, '/api/v1/me/context')
  const rootMemberId = String(current.context.memberId)
  const tenants = await apiData<ApiPage<Record<string, any>>>(
    root, `/api/v1/systems/${systemId}/admin/tenants?size=200`)
  const tenant = tenants.items.find(item => item.isDefault) ?? tenants.items[0]
  const scopes = await apiData<ApiPage<Record<string, any>>>(
    root, `/api/v1/systems/${systemId}/admin/data-scopes?size=200`)
  const dataScope = scopes.items.find(item => item.kind === 'ALL')
  if (!tenant || !dataScope) throw new Error('Batch101 role owner data is incomplete')

  const role = await apiData<Record<string, any>>(
    root, `/api/v1/systems/${systemId}/admin/roles`, 'POST', {
      code: `todo_recipient_${suffix}`,
      name: `Batch101 Todo Recipient ${suffix}`,
      description: 'Read Work, Flow, Event and act through Todo',
    }, randomUUID())
  const draft = await apiData<Record<string, any>>(
    root, `/api/v1/systems/${systemId}/admin/roles/${role.id}/draft`, 'PUT', {
      name: role.name,
      description: role.description,
      permissionCodes: [
        'system.runtime.access',
        'work.task.access',
        'flow.instance.read',
        'event.message.access',
      ],
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
      reason: `Batch101 Todo recipient ${suffix}`,
    }, randomUUID())
  await apiData(root, `/api/v1/systems/${systemId}/admin/access-requests/${requested.id}:approve`, 'POST', {
    reason: 'Grant the exact Batch101 recipient role',
    version: String(requested.version),
    tenantIds: [String(tenant.id)],
    roleIds: [String(role.id)],
  }, randomUUID())
  await apiData(root, '/api/v1/auth/refresh', 'POST', {})
  await apiData(member, '/api/v1/auth/refresh', 'POST', {})
  await apiData(member, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const memberContext = await apiData<{ context: { memberId: string } }>(member, '/api/v1/me/context')
  return {
    systemId,
    tenantId: String(tenant.id),
    rootMemberId,
    memberId: String(memberContext.context.memberId),
  }
}

async function createNativeMessages(
  root: Page,
  fixture: Awaited<ReturnType<typeof provisionRecipient>>,
) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 8)
  const flowRoot = `/api/v1/systems/${fixture.systemId}/flow`
  const definition = await apiData<{ definitionId: string }>(
    root, `${flowRoot}/definitions`, 'POST', {
      name: `Batch101 Approval ${suffix}`,
      approverIds: [fixture.rootMemberId],
    }, undefined, 201)
  await apiData(root, `${flowRoot}/definitions/${definition.definitionId}/draft:check`, 'POST')
  await apiData(root, `${flowRoot}/definitions/${definition.definitionId}:publish`, 'POST')
  const instance = await apiData<{ instanceId: string }>(
    root, `${flowRoot}/definitions/${definition.definitionId}/instances`, 'POST', {
      definitionVersion: null,
      businessKey: `batch101-${suffix}`,
      values: {},
    }, randomUUID(), 201)
  const instanceId = String(instance.instanceId)
  const flowHistory = await apiData<Record<string, any>>(
    root, `${flowRoot}/instances/${instanceId}/history`)
  await apiData(root, `${flowRoot}/instances/${instanceId}/copies`, 'POST', {
    targetMemberId: fixture.memberId,
    message: 'Batch101 private flow copy payload',
  }, randomUUID())

  const reminderAt = new Date(Date.now() + 5_000).toISOString()
  const task = await apiData<{ id: string }>(
    root, `/api/v1/systems/${fixture.systemId}/work/tasks`, 'POST', {
      title: `Batch101 reminder task ${suffix}`,
      assigneeMemberId: fixture.memberId,
      projectId: null,
      description: 'Business fact must remain unchanged by Event mark-read',
      dueAt: new Date(Date.now() + 86_400_000).toISOString(),
      reminderAt,
    }, undefined, 201)
  return { flowRoot, instanceId, flowHistory, taskId: String(task.id) }
}

async function waitForBothMessages(member: Page, systemId: string) {
  let messages: EventMessage[] = []
  await expect.poll(async () => {
    const page = await apiData<ApiPage<EventMessage>>(
      member, `/api/v1/systems/${systemId}/event/messages?status=ALL&page=1&size=100`)
    messages = page.items.filter(item => [
      'WORK_TASK_REMINDER', 'FLOW_INSTANCE_COPIED',
    ].includes(item.templateCode))
    return new Set(messages.map(item => item.templateCode)).size
  }, { timeout: 30_000, intervals: [500, 1_000, 2_000] }).toBe(2)
  return messages
}

test('projects native reminders and copies into Todo and marks Event read without business writes', async ({
  page,
  browser,
  baseURL,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'Batch101 real owner journey runs once on desktop Edge')
  test.skip(!rootUsername || !rootPassword, 'E2E_ROOT_USERNAME and E2E_ROOT_PASSWORD are required')
  await loginRoot(page)
  const member = await registerMember(browser, baseURL)
  try {
    const fixture = await provisionRecipient(page, member.page)
    const native = await createNativeMessages(page, fixture)
    const messages = await waitForBothMessages(member.page, fixture.systemId)
    const reminderMessage = messages.find(item => item.templateCode === 'WORK_TASK_REMINDER')!
    const ccMessage = messages.find(item => item.templateCode === 'FLOW_INSTANCE_COPIED')!
    expect(reminderMessage.body).toContain('Batch101 reminder task')
    expect(ccMessage.body).toContain('Batch101 private flow copy payload')

    const taskBaseline = await apiData<Record<string, any>>(
      page, `/api/v1/systems/${fixture.systemId}/work/tasks/${native.taskId}`)
    await member.page.goto(`/systems/${fixture.systemId}/todos`)
    await expect(member.page.locator('.todo-page')).toBeVisible()
    await member.page.locator('.todo-refresh').click()
    await expect(member.page.locator('.todo-card-reminder')).toHaveCount(1)
    await expect(member.page.locator('.todo-card-cc')).toHaveCount(1)
    await expect(member.page.locator('.todo-page'))
      .not.toContainText('Batch101 private flow copy payload')

    const open = await apiData<ApiPage<TodoItem>>(
      member.page,
      `/api/v1/systems/${fixture.systemId}/todos?category=ALL&state=OPEN&time=ALL&page=1&size=100`,
    )
    const reminderTodo = open.items.find(item => item.sourceId === reminderMessage.id)!
    const ccTodo = open.items.find(item => item.sourceId === ccMessage.id)!
    expect(reminderTodo).toMatchObject({
      sourceType: 'EVENT_MESSAGE',
      category: 'REMINDER',
      routeHint: `/systems/${fixture.systemId}/tasks?taskId=${native.taskId}`,
    })
    expect(ccTodo).toMatchObject({
      sourceType: 'EVENT_MESSAGE',
      category: 'CC',
      routeHint: `/systems/${fixture.systemId}/flows?instanceId=${native.instanceId}`,
    })
    await member.page.screenshot({
      path: '../.cursor/session/evidence/fast-todo-reminder-cc-101/todo-reminder-cc.png',
      fullPage: true,
    })

    await member.page.locator('.todo-card-cc').click()
    await expect(member.page.locator('.todo-action-mark-read')).toBeVisible()
    await member.page.locator('.todo-action-mark-read').click()
    await expect(member.page.locator('.ant-modal')).toBeVisible()
    await member.page.locator('.ant-modal .ant-btn-primary').click()
    await expect(member.page.locator('.todo-result')).toBeVisible()
    await expect(member.page.locator('.todo-card-cc')).toHaveCount(0)

    const afterTodoAction = await apiData<ApiPage<EventMessage>>(
      member.page,
      `/api/v1/systems/${fixture.systemId}/event/messages?status=ALL&page=1&size=100`,
    )
    expect(afterTodoAction.items.find(item => item.id === ccMessage.id)?.status).toBe('READ')

    await apiData(
      member.page,
      `/api/v1/systems/${fixture.systemId}/event/messages/${reminderMessage.id}:read`,
      'POST',
      {},
    )
    await member.page.locator('.todo-refresh').click()
    await expect(member.page.locator('.todo-card-reminder')).toHaveCount(0)
    const closedReminder = await apiData<ApiPage<TodoItem>>(
      member.page,
      `/api/v1/systems/${fixture.systemId}/todos?category=REMINDER&state=CLOSED&time=ALL&page=1&size=100`,
    )
    expect(closedReminder.items.find(item => item.sourceId === reminderMessage.id)?.closeReason)
      .toBe('SOURCE_COMPLETED')

    await apiData(page, `/api/v1/systems/${fixture.systemId}/todos:refresh`, 'POST', {})
    const rootEventTodos = await apiData<ApiPage<TodoItem>>(
      page,
      `/api/v1/systems/${fixture.systemId}/todos?category=CC&state=ALL&time=ALL&page=1&size=100`,
    )
    expect(rootEventTodos.total).toBe(0)

    const taskAfter = await apiData<Record<string, any>>(
      page, `/api/v1/systems/${fixture.systemId}/work/tasks/${native.taskId}`)
    const flowHistoryAfter = await apiData<Record<string, any>>(
      page, `${native.flowRoot}/instances/${native.instanceId}/history`)
    expect(taskAfter).toEqual(taskBaseline)
    expect(flowHistoryAfter).toEqual(native.flowHistory)

    const isolatedTenant = await apiData<{ id: string }>(
      page, `/api/v1/systems/${fixture.systemId}/admin/tenants`, 'POST', {
        code: `isolated_${Date.now()}`,
        name: 'Batch101 Isolated Tenant',
      }, randomUUID())
    await apiData(page, '/api/v1/auth/refresh', 'POST', {})
    await apiData(page, `/api/v1/context/tenants/${isolatedTenant.id}:switch`, 'POST', {})
    await apiData(page, `/api/v1/systems/${fixture.systemId}/todos:refresh`, 'POST', {})
    const isolated = await apiData<ApiPage<TodoItem>>(
      page,
      `/api/v1/systems/${fixture.systemId}/todos?category=ALL&state=ALL&time=ALL&page=1&size=100`,
    )
    expect(isolated.total).toBe(0)
  } finally {
    await member.context.close()
  }
})
