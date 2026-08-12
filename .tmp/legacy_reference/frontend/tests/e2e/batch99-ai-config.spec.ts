import { randomUUID } from 'node:crypto'
import { createServer, type Server } from 'node:http'

import { expect, test, type Page } from '@playwright/test'

test.use({ trace: 'off' })
test.setTimeout(180_000)

const rootUsername = process.env.E2E_ROOT_USERNAME
const rootPassword = process.env.E2E_ROOT_PASSWORD
const providerSecret = process.env.BATCH99_AI_SECRET ?? 'batch99-local-provider-secret'

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
      let content = '{"operation":"RECORD_QUERY","moduleCode":"batch99_orders","filter":null,"sort":[],"page":1,"size":1}'
      if (body.includes('BATCH99_FILTER_SCENARIO')) {
        content = JSON.stringify({
          operation: 'CONFIG_FILTER_SCENARIO_DRAFT',
          moduleCode: 'batch99_orders',
          pageCode: 'list',
          scenario: {
            code: 'urgent_subjects',
            name: 'Urgent subjects',
            filter: { kind: 'PREDICATE', fieldCode: 'subject', operator: 'CONTAINS', value: 'urgent' },
            sort: [{ fieldCode: 'subject', direction: 'ASC', nulls: 'LAST' }],
          },
          makeDefault: true,
          confidence: 0.99,
          clarification: null,
        })
      } else if (body.includes('BATCH99_FIELD_PERMISSION')) {
        content = JSON.stringify({
          operation: 'CONFIG_FIELD_PERMISSION_STAGE_DRAFT',
          moduleCode: 'batch99_orders',
          fieldCode: 'subject',
          stageRead: true,
          stageWrite: true,
          confidence: 0.99,
          clarification: null,
        })
      }
      const payload = JSON.stringify({
        choices: [{ message: { content } }],
        usage: { prompt_tokens: 12, completion_tokens: 8 },
      })
      response.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' })
      response.end(payload)
    })
  })
  await new Promise<void>((resolve, reject) => {
    provider.once('error', reject)
    provider.listen(0, '127.0.0.1', () => resolve())
  })
  const address = provider.address()
  if (!address || typeof address === 'string') throw new Error('Batch99 provider fixture did not bind')
  providerBaseUrl = `http://127.0.0.1:${address.port}`
})

test.afterAll(async () => {
  if (provider?.listening) {
    await new Promise<void>((resolve, reject) => provider.close(error => error ? reject(error) : resolve()))
  }
})

async function login(page: Page) {
  await page.goto('/auth/login')
  await page.getByLabel('账号').fill(rootUsername!)
  await page.getByLabel('密码').fill(rootPassword!)
  await Promise.all([
    page.waitForURL(/\/platform\/workbench$/),
    page.getByRole('button', { name: '登录', exact: true }).click(),
  ])
}

async function apiData<T = Record<string, any>>(
  page: Page,
  path: string,
  method = 'GET',
  body?: unknown,
  idempotencyKey?: string,
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
        'X-Request-ID': `batch99-${crypto.randomUUID()}`,
        ...(input.idempotencyKey ? { 'Idempotency-Key': input.idempotencyKey } : {}),
      },
      ...(input.body === undefined ? {} : { body: JSON.stringify(input.body) }),
    })
    return { status: result.status, payload: await result.json() }
  }, { path, method, body, idempotencyKey })
  expect(response.status, JSON.stringify(response.payload)).toBe(200)
  return response.payload.data as T
}

async function provision(page: Page) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 8)
  await apiData(page, '/api/v1/context/platform:switch', 'POST', {})
  const system = await apiData<{ id: string; name: string }>(page,
    '/api/v1/platform/admin/systems', 'POST', {
      code: `batch99_${suffix}`,
      name: `Batch99 AI 配置建议 ${suffix}`,
      description: 'Batch99 real Edge AI configuration suggestion journey',
      tenantMode: 'SINGLE',
    }, randomUUID())
  const systemId = String(system.id)
  await apiData(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const configRoot = `/api/v1/systems/${systemId}/admin/config`
  const group = await apiData<{ id: string }>(page, `${configRoot}/module-groups`, 'POST', {
    code: 'operations', name: 'Operations', description: '', iconKey: 'folder',
    sortOrder: 0, status: 'ENABLED', draftRevision: '0',
  }, randomUUID())
  const module = await apiData<{ id: string }>(page, `${configRoot}/modules`, 'POST', {
    groupId: String(group.id), code: 'batch99_orders', name: 'Batch99 Orders',
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
  const published = await apiData<{ version: { id: string } }>(page, `${configRoot}:publish`, 'POST', {
    checkId: String(checked.id), draftRevision: '3', configRootVersion: String(root.version),
    reason: 'Batch99 browser acceptance baseline',
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})

  const aiAdmin = `/api/v1/systems/${systemId}/admin/ai`
  const aiProvider = await apiData<{ id: string }>(page, `${aiAdmin}/providers`, 'POST', {
    code: `batch99_provider_${suffix}`,
    name: 'Batch99 local OpenAI-compatible provider',
    baseUrl: providerBaseUrl,
    model: 'batch99-fixture-model',
    secretRef: 'env://BATCH99_AI_SECRET',
    timeoutSeconds: 10,
    enabled: true,
  })
  const policy = await apiData<{ draftVersion: number }>(page, `${aiAdmin}/policy`, 'PUT', {
    expectedVersion: 0,
    providerId: String(aiProvider.id),
    moduleCodes: ['batch99_orders'],
    outboundFields: { batch99_orders: ['subject'] },
    allowedOperations: ['CONFIG_FILTER_SCENARIO_DRAFT', 'CONFIG_FIELD_PERMISSION_STAGE_DRAFT'],
    writableFields: {},
    fillFields: {},
    maxRows: 10,
    confirmationMode: 'REQUIRED',
    confirmationExpiresSeconds: 600,
    redactionMode: 'STRICT',
    promptVersion: 'batch99-browser-v1',
    enabled: true,
  })
  await apiData(page, `${aiAdmin}/policy:check`, 'POST', {})
  await apiData(page, `${aiAdmin}/policy:publish`, 'POST', {
    expectedVersion: policy.draftVersion,
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})
  return {
    systemId,
    moduleId: String(module.id),
    activeVersionId: String(published.version.id),
    configRoot,
  }
}

test('AI suggestions update only one config draft revision and focus fresh owner state', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'Batch99 real journey runs once on desktop Edge')
  test.skip(!rootUsername || !rootPassword, 'E2E_ROOT_USERNAME and E2E_ROOT_PASSWORD are required')

  await login(page)
  const fixture = await provision(page)
  await page.goto(`/systems/${fixture.systemId}/agent`)
  await expect(page.locator('.agent-layout')).toBeVisible()
  await page.locator('.agent-session-title').fill('Batch99 AI suggestions')
  await page.locator('.agent-session-create-button').click()
  await expect(page.locator('.agent-message-input')).toBeVisible()

  const baseline = await apiData<{ draftRevision: string; activeVersionId: string }>(page, fixture.configRoot)
  await page.locator('.agent-message-input').fill('BATCH99_FILTER_SCENARIO create a shared list filter')
  await page.locator('.agent-message-submit').click()
  const filterCard = page.locator('.config-filter-scenario-proposal[data-artifact-kind="FILTER_SCENARIO"]')
  await expect(filterCard).toBeVisible()
  await expect(filterCard.locator('.config-filter-scenario-filter')).toContainText('urgent')
  const beforeFilterConfirm = await apiData<{ draftRevision: string }>(page, fixture.configRoot)
  expect(beforeFilterConfirm.draftRevision).toBe(baseline.draftRevision)
  await filterCard.locator('.config-filter-scenario-confirm').click()
  await expect(filterCard.locator('.config-filter-scenario-result')).toBeVisible()
  await expect(filterCard.locator('[data-scenario-code="urgent_subjects"]')).toBeVisible()
  const afterFilter = await apiData<{ draftRevision: string; activeVersionId: string }>(page, fixture.configRoot)
  expect(BigInt(afterFilter.draftRevision)).toBe(BigInt(baseline.draftRevision) + 1n)
  expect(afterFilter.activeVersionId).toBe(fixture.activeVersionId)

  await filterCard.locator('.config-suggestion-open-studio').click()
  await expect(page).toHaveURL(/resourceKind=page/)
  await expect(page.locator('.config-agent-focus-notice[data-resource-kind="page"][data-resource-code="list"]'))
    .toBeVisible()

  await page.goto(`/systems/${fixture.systemId}/agent`)
  await expect(page.locator('.agent-message-input')).toBeVisible()
  await page.locator('.agent-message-input').fill('BATCH99_FIELD_PERMISSION stage field access')
  await page.locator('.agent-message-submit').click()
  const permissionCard = page.locator('.config-field-permission-stage-proposal[data-artifact-kind="FIELD_PERMISSION_STAGE"]')
  await expect(permissionCard).toBeVisible()
  await expect(permissionCard.locator('.config-field-permission-read-code'))
    .toContainText('module.batch99_orders.field.subject.read')
  const beforePermissionConfirm = await apiData<{ draftRevision: string }>(page, fixture.configRoot)
  expect(beforePermissionConfirm.draftRevision).toBe(afterFilter.draftRevision)
  await permissionCard.locator('.config-field-permission-stage-confirm').click()
  await expect(permissionCard.locator('.config-field-permission-stage-result')).toBeVisible()
  await expect(permissionCard.locator('.config-field-permission-read-mode')).toContainText('STAGED')
  await expect(permissionCard.locator('.config-field-permission-write-mode')).toContainText('STAGED')

  const afterPermission = await apiData<{ draftRevision: string; activeVersionId: string }>(page, fixture.configRoot)
  expect(BigInt(afterPermission.draftRevision)).toBe(BigInt(afterFilter.draftRevision) + 1n)
  expect(afterPermission.activeVersionId).toBe(fixture.activeVersionId)
  const fields = await apiData<Array<{ code: string; readPermissionMode: string; writePermissionMode: string }>>(
    page, `${fixture.configRoot}/modules/${fixture.moduleId}/fields`)
  const subject = fields.find(field => field.code === 'subject')
  expect(subject?.readPermissionMode).toBe('STAGED')
  expect(subject?.writePermissionMode).toBe('STAGED')
  expect(providerAuthorizations).toContain(`Bearer ${providerSecret}`)

  await permissionCard.locator('.config-suggestion-open-studio').click()
  await expect(page).toHaveURL(/resourceKind=field/)
  await expect(page.locator('.config-agent-focus-notice[data-resource-kind="field"][data-resource-code="subject"]'))
    .toBeVisible()
  await page.screenshot({
    path: '../.cursor/session/evidence/fast-ai-config-suggestions-99/config-studio-field-focus.png',
    fullPage: true,
  })
})
