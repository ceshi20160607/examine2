import { randomUUID } from 'node:crypto'

import { expect, test, type Page } from '@playwright/test'

test.use({ trace: 'off' })
test.setTimeout(180_000)

const rootUsername = process.env.E2E_ROOT_USERNAME
const rootPassword = process.env.E2E_ROOT_PASSWORD

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
        'X-Request-ID': `batch103-${crypto.randomUUID()}`,
        ...(input.idempotencyKey ? { 'Idempotency-Key': input.idempotencyKey } : {}),
      },
      ...(input.body === undefined ? {} : { body: JSON.stringify(input.body) }),
    })
    return { status: result.status, payload: await result.json() }
  }, { path, method, body, idempotencyKey })
  expect(response.status, JSON.stringify(response.payload)).toBe(expectedStatus)
  return response.payload.data as T
}

async function login(page: Page) {
  await page.goto('/auth/login')
  await page.locator('input[autocomplete="username"]').fill(rootUsername!)
  await page.locator('input[autocomplete="current-password"]').fill(rootPassword!)
  await Promise.all([
    page.waitForURL(/\/platform\/workbench$/),
    page.locator('button[type="submit"]').click(),
  ])
}

async function provisionAnchorModule(page: Page) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 8)
  await apiData(page, '/api/v1/context/platform:switch', 'POST', {})
  const system = await apiData<{ id: string }>(
    page,
    '/api/v1/platform/admin/systems',
    'POST',
    {
      code: `batch103_${suffix}`,
      name: `Batch103 HTTP Data Source ${suffix}`,
      description: 'Real Edge HTTP draft and safe connection-check journey',
      tenantMode: 'SINGLE',
    },
    randomUUID(),
  )
  const systemId = String(system.id)
  await apiData(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const configRoot = `/api/v1/systems/${systemId}/admin/config`
  const group = await apiData<{ id: string }>(page, `${configRoot}/module-groups`, 'POST', {
    code: 'external_sources',
    name: 'External Sources',
    description: '',
    iconKey: 'database',
    sortOrder: 0,
    status: 'ENABLED',
    draftRevision: '0',
  }, randomUUID())
  const module = await apiData<{ id: string }>(page, `${configRoot}/modules`, 'POST', {
    groupId: String(group.id),
    code: 'batch103_anchor',
    name: 'Batch103 Anchor',
    description: '',
    iconKey: 'database',
    sortOrder: 0,
    status: 'ENABLED',
    allowComments: false,
    allowTeam: false,
    draftRevision: '1',
  }, randomUUID())
  await apiData(page, `${configRoot}/modules/${module.id}/fields`, 'POST', {
    dictionaryId: null,
    targetModuleId: null,
    code: 'external_id',
    name: 'External ID',
    type: 'TEXT',
    sortOrder: 0,
    required: false,
    hidden: false,
    readonly: false,
    searchable: true,
    filterable: true,
    showInList: true,
    showInDetail: true,
    indexMode: 'SORT',
    status: 'ENABLED',
    properties: { maxLength: 200 },
    draftRevision: '2',
  }, randomUUID())
  const checked = await apiData<{ id: string }>(page, `${configRoot}/checks`, 'POST', {
    draftRevision: '3',
  })
  const root = await apiData<{ version: string }>(page, configRoot)
  await apiData(page, `${configRoot}:publish`, 'POST', {
    checkId: String(checked.id),
    draftRevision: '3',
    configRootVersion: String(root.version),
    reason: 'Batch103 browser anchor module',
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})
  return { systemId, moduleId: String(module.id) }
}

test('creates and edits an HTTP draft, checks it safely, and keeps publication blocked', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'Batch103 real function journey runs once on desktop Edge')
  test.skip(!rootUsername || !rootPassword, 'E2E_ROOT_USERNAME and E2E_ROOT_PASSWORD are required')

  await login(page)
  const fixture = await provisionAnchorModule(page)
  await page.goto(`/systems/${fixture.systemId}/admin/data-sources`)
  await expect(page.locator('.data-source-workspace')).toBeVisible()
  await page.locator('.data-source-create').click()
  const modal = page.locator('.ant-modal:visible')
  await modal.locator('.create-source-name').fill('Batch103 HTTP draft')
  await modal.locator('.create-source-code').fill('batch103_http')
  await modal.locator('.create-source-kind').click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option-content', {
    hasText: 'HTTP JSON',
  }).click()
  await modal.locator('.create-http-json-endpoint')
    .fill('https://denied.example.test/rows')
  await modal.locator('.create-http-json-timeout').fill('3')
  await modal.locator('.ant-btn-primary').click()

  await expect(page.locator('.http-json-connection')).toBeVisible()
  await expect(page.locator('.http-publish-blocker')).toContainText('SOURCE_RUNTIME_UNAVAILABLE')
  await expect(page.locator('.draft-publish')).toBeDisabled()
  await page.locator('.draft-connection-check').click()
  await expect(page.locator('.connection-check-result[data-code="SAFE_TARGET"]')).toBeVisible()
  await expect(page.locator('.connection-check-result')).not.toContainText('denied.example.test')

  await page.locator('.http-json-endpoint').fill('https://still-denied.example.test/rows')
  await expect(page.locator('.connection-check-result')).toHaveCount(0)
  await page.locator('.draft-connection-check').click()
  await expect(page.locator('.connection-check-result[data-code="SAFE_TARGET"]')).toBeVisible()
  await expect(page.locator('.editor-state')).toHaveAttribute('data-state', 'clean')

  const sources = await apiData<Array<Record<string, any>>>(
    page,
    `/api/v1/systems/${fixture.systemId}/admin/data-sources`,
  )
  const source = sources.find(item => item.code === 'batch103_http')
  expect(source).toBeTruthy()
  expect(String(source!.moduleId)).toBe(fixture.moduleId)
  expect(source!.draft).toMatchObject({
    sourceKind: 'HTTP_JSON',
    outputFields: [],
    httpJsonConnection: {
      endpoint: 'https://still-denied.example.test/rows',
      timeoutSeconds: 3,
    },
  })
  expect(source!.draft.httpJsonConnection).not.toHaveProperty('authSecretRef')
  expect(source!.draftVersion).toBe(2)

  const checked = await apiData<{ issues: Array<{ code: string }> }>(
    page,
    `/api/v1/systems/${fixture.systemId}/admin/data-sources/${source!.id}/draft:check`,
    'POST',
  )
  expect(checked.issues.map(issue => issue.code)).toContain('SOURCE_RUNTIME_UNAVAILABLE')
  await apiData(
    page,
    `/api/v1/systems/${fixture.systemId}/admin/data-sources/${source!.id}/draft:publish`,
    'POST',
    { expectedVersion: source!.draftVersion },
    randomUUID(),
    422,
  )

  await page.screenshot({
    path: '../.cursor/session/evidence/fast-module-http-datasource-check-103/http-draft-safe-check.png',
    fullPage: true,
  })
})
