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
        'X-Request-ID': `batch106-${crypto.randomUUID()}`,
        ...(input.idempotencyKey ? { 'Idempotency-Key': input.idempotencyKey } : {}),
      },
      ...(input.body === undefined ? {} : { body: JSON.stringify(input.body) }),
    })
    return { status: result.status, payload: await result.json() }
  }, { path, method, body, idempotencyKey })
  expect(response.status, JSON.stringify(response.payload)).toBe(200)
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

async function provision(page: Page) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 8)
  await apiData(page, '/api/v1/context/platform:switch', 'POST', {})
  const system = await apiData<{ id: string }>(
    page,
    '/api/v1/platform/admin/systems',
    'POST',
    {
      code: `batch106_${suffix}`,
      name: `Batch106 HTTP Publish ${suffix}`,
      description: 'Real Edge HTTP publication default-deny journey',
      tenantMode: 'SINGLE',
    },
    randomUUID(),
  )
  const systemId = String(system.id)
  await apiData(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const configRoot = `/api/v1/systems/${systemId}/admin/config`
  const group = await apiData<{ id: string }>(page, `${configRoot}/module-groups`, 'POST', {
    code: 'publish_anchor', name: 'Publish Anchor', description: '', iconKey: 'database',
    sortOrder: 0, status: 'ENABLED', draftRevision: '0',
  }, randomUUID())
  const module = await apiData<{ id: string }>(page, `${configRoot}/modules`, 'POST', {
    groupId: String(group.id), code: 'batch106_anchor', name: 'Batch106 Anchor',
    description: '', iconKey: 'database', sortOrder: 0, status: 'ENABLED',
    allowComments: false, allowTeam: false, draftRevision: '1',
  }, randomUUID())
  await apiData(page, `${configRoot}/modules/${module.id}/fields`, 'POST', {
    dictionaryId: null, targetModuleId: null, code: 'external_id', name: 'External ID',
    type: 'TEXT', sortOrder: 0, required: false, hidden: false, readonly: false,
    searchable: true, filterable: true, showInList: true, showInDetail: true,
    indexMode: 'SORT', status: 'ENABLED', properties: { maxLength: 200 }, draftRevision: '2',
  }, randomUUID())
  const checked = await apiData<{ id: string }>(page, `${configRoot}/checks`, 'POST', {
    draftRevision: '3',
  })
  const root = await apiData<{ version: string }>(page, configRoot)
  await apiData(page, `${configRoot}:publish`, 'POST', {
    checkId: String(checked.id), draftRevision: '3', configRootVersion: String(root.version),
    reason: 'Batch106 browser anchor schema',
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})

  const adminRoot = `/api/v1/systems/${systemId}/admin/data-sources`
  const source = await apiData<Record<string, any>>(page, adminRoot, 'POST', {
    code: 'batch106_http', moduleId: String(module.id), name: 'Batch106 HTTP publish',
    description: 'Mapped HTTP publication default deny', sourceKind: 'HTTP_JSON',
    httpJsonConnection: {
      endpoint: 'https://denied.example.test/publish', authSecretRef: null, timeoutSeconds: 3,
    },
  }, randomUUID())
  const saved = await apiData<Record<string, any>>(
    page,
    `${adminRoot}/${source.id}/draft`,
    'PUT',
    {
      expectedVersion: source.draftVersion, name: source.name, description: source.description,
      sourceKind: 'HTTP_JSON',
      httpJsonConnection: {
        endpoint: 'https://denied.example.test/publish', authSecretRef: null, timeoutSeconds: 3,
      },
      httpFieldProjections: [{
        sourceField: 'external_id', fieldCode: 'external_id', sourceType: 'STRING',
      }],
      outputFields: [], fixedFilters: [], defaultSort: null, defaultTimeFieldCode: null,
    },
  )
  return { systemId, sourceId: String(source.id), draftVersion: Number(saved.draftVersion) }
}

test('saves once then performs one safe default-denied HTTP publication preflight', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'Batch106 real function journey runs once on desktop Edge')
  test.skip(!rootUsername || !rootPassword, 'E2E_ROOT_USERNAME and E2E_ROOT_PASSWORD are required')

  await login(page)
  const fixture = await provision(page)
  const observed = { save: 0, publish: 0, check: 0, runtime: 0 }
  page.on('request', request => {
    const path = new URL(request.url()).pathname
    if (request.method() === 'PUT' && path.endsWith('/draft')) observed.save += 1
    if (request.method() === 'POST' && path.endsWith('/draft:publish')) observed.publish += 1
    if (request.method() === 'POST' && path.endsWith('/draft:check')) observed.check += 1
    if (path.includes('/data-sources/batch106_http') && !path.includes('/admin/')) observed.runtime += 1
  })

  await page.goto(`/systems/${fixture.systemId}/admin/data-sources`, { waitUntil: 'networkidle' })
  await expect(page.locator('.data-sources-page')).toBeVisible({ timeout: 15_000 })
  await expect(page.locator('.draft-publish')).toBeEnabled()
  await expect(page.locator('.published-preview')).toHaveCount(0)
  await expect(page.locator('.statistics-tester')).toHaveCount(0)

  await page.locator('.http-json-endpoint')
    .fill('https://still-denied.example.test/publish')
  await expect(page.locator('.editor-state')).toHaveAttribute('data-state', 'dirty')
  await page.locator('.draft-publish').click()

  const error = page.locator('.data-source-error')
  await expect(error).toContainText('The HTTP publication endpoint is not permitted')
  await expect(error).not.toContainText('still-denied.example.test')
  await expect(page.locator('.editor-state')).toHaveAttribute('data-state', 'error')
  expect(observed).toEqual({ save: 1, publish: 1, check: 0, runtime: 0 })

  const detail = await apiData<Record<string, any>>(
    page,
    `/api/v1/systems/${fixture.systemId}/admin/data-sources/${fixture.sourceId}`,
  )
  expect(detail.draftVersion).toBe(fixture.draftVersion + 1)
  expect(detail.activeVersionId ?? null).toBeNull()
  expect(detail.draft.httpJsonConnection.endpoint)
    .toBe('https://still-denied.example.test/publish')
  const versions = await apiData<any[]>(
    page,
    `/api/v1/systems/${fixture.systemId}/admin/data-sources/${fixture.sourceId}/versions`,
  )
  expect(versions).toEqual([])

  await page.screenshot({
    path: '../.cursor/session/evidence/fast-module-http-datasource-publish-106/http-publish-default-deny.png',
    fullPage: true,
  })
})
