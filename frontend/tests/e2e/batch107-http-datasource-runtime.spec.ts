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
        'X-Request-ID': `batch107-${crypto.randomUUID()}`,
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
      code: `batch107_${suffix}`,
      name: `Batch107 HTTP Runtime ${suffix}`,
      description: 'Real Edge explicit published HTTP rows UI journey',
      tenantMode: 'SINGLE',
    },
    randomUUID(),
  )
  const systemId = String(system.id)
  await apiData(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const configRoot = `/api/v1/systems/${systemId}/admin/config`
  const group = await apiData<{ id: string }>(page, `${configRoot}/module-groups`, 'POST', {
    code: 'runtime_anchor', name: 'Runtime Anchor', description: '', iconKey: 'database',
    sortOrder: 0, status: 'ENABLED', draftRevision: '0',
  }, randomUUID())
  const module = await apiData<{ id: string }>(page, `${configRoot}/modules`, 'POST', {
    groupId: String(group.id), code: 'batch107_anchor', name: 'Batch107 Anchor',
    description: '', iconKey: 'database', sortOrder: 0, status: 'ENABLED',
    allowComments: false, allowTeam: false, draftRevision: '1',
  }, randomUUID())
  await apiData(page, `${configRoot}/modules/${module.id}/fields`, 'POST', {
    dictionaryId: null, targetModuleId: null, code: 'external_name', name: 'External Name',
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
    reason: 'Batch107 browser anchor schema',
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})

  const adminRoot = `/api/v1/systems/${systemId}/admin/data-sources`
  const source = await apiData<Record<string, any>>(page, adminRoot, 'POST', {
    code: 'batch107_http', moduleId: String(module.id), name: 'Batch107 HTTP runtime',
    description: 'Explicit load only', sourceKind: 'HTTP_JSON',
    httpJsonConnection: {
      endpoint: 'https://denied.example.test/runtime', authSecretRef: null, timeoutSeconds: 3,
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
        endpoint: 'https://denied.example.test/runtime', authSecretRef: null, timeoutSeconds: 3,
      },
      httpFieldProjections: [{
        sourceField: 'remote_name', fieldCode: 'external_name', sourceType: 'STRING',
      }],
      outputFields: [], fixedFilters: [], defaultSort: null, defaultTimeFieldCode: null,
    },
  )
  return {
    systemId,
    moduleId: String(module.id),
    sourceId: String(source.id),
    source: saved,
    adminRoot,
    activeVersionId: '910700000000000001',
  }
}

test('loads published HTTP rows only after one explicit action', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'Batch107 real function journey runs once on desktop Edge')
  test.skip(!rootUsername || !rootPassword, 'E2E_ROOT_USERNAME and E2E_ROOT_PASSWORD are required')

  await login(page)
  const fixture = await provision(page)
  const publishedAt = '2026-08-05T09:00:00Z'
  const version = {
    id: fixture.activeVersionId,
    dataSourceId: fixture.sourceId,
    versionNumber: 1,
    code: 'batch107_http',
    moduleId: fixture.moduleId,
    moduleCode: 'batch107_anchor',
    schemaVersionId: 'batch107-browser-safe',
    name: 'Batch107 HTTP runtime',
    description: 'Explicit load only',
    fingerprint: '7'.repeat(64),
    snapshot: fixture.source.draft,
    publishedAt,
    publishedBy: '1',
    active: true,
  }

  await page.route(`**${fixture.adminRoot}**`, async route => {
    const request = route.request()
    if (request.method() !== 'GET') {
      await route.continue()
      return
    }
    const path = new URL(request.url()).pathname
    const responseBody = (data: unknown) => JSON.stringify({
      code: 'OK',
      message: 'OK',
      data,
      requestId: 'batch107',
      traceId: 'batch107',
      errors: [],
    })
    if (path === `${fixture.adminRoot}/${fixture.sourceId}/versions`) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: responseBody([version]),
      })
      return
    }
    if (path === fixture.adminRoot) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: responseBody([{
          ...fixture.source,
          activeVersionId: fixture.activeVersionId,
          activeVersionNumber: 1,
        }]),
      })
      return
    }
    if (path === `${fixture.adminRoot}/${fixture.sourceId}`) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: responseBody({
          ...fixture.source,
          activeVersionId: fixture.activeVersionId,
          activeVersionNumber: 1,
        }),
      })
      return
    }
    await route.continue()
  })

  let runtimeRequests = 0
  await page.route(
    `**/api/v1/systems/${fixture.systemId}/data-sources/batch107_http/http-rows`,
    async route => {
      runtimeRequests += 1
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'OK',
          message: 'OK',
          data: {
            dataSourceId: fixture.sourceId,
            dataSourceCode: 'batch107_http',
            dataSourceVersionId: fixture.activeVersionId,
            dataSourceVersionNumber: 1,
            fields: [
              { fieldCode: 'external_name', sourceType: 'STRING' },
              { fieldCode: 'enabled', sourceType: 'BOOLEAN' },
            ],
            rows: [{ rowIndex: 1, values: { external_name: '<b>remote</b>', enabled: true } }],
          },
          requestId: 'batch107',
          traceId: 'batch107',
          errors: [],
        }),
      })
    },
  )

  await page.goto(`/systems/${fixture.systemId}/admin/data-sources`, { waitUntil: 'networkidle' })
  await expect(page.locator('.data-sources-page')).toBeVisible({ timeout: 15_000 })
  await expect(page.locator('.published-http-rows-load')).toBeVisible()
  await expect(page.locator('.published-http-rows-result')).toHaveCount(0)
  expect(runtimeRequests).toBe(0)

  await page.locator('.published-http-rows-load').click()
  await expect(page.locator('.published-http-rows-result')).toBeVisible()
  expect(runtimeRequests).toBe(1)
  await expect(page.locator('.published-http-field')).toHaveCount(2)
  await expect(page.locator('.published-http-row[data-row-index="1"]'))
    .toContainText('<b>remote</b>')
  await expect(page.locator('.published-http-row[data-row-index="1"] b')).toHaveCount(0)
  await expect(page.locator('.published-http-row[data-row-index="1"]')).toContainText('true')
  await expect(page.locator('.statistics-tester')).toHaveCount(0)
  await expect(page.locator('.published-preview')).toHaveCount(0)

  await page.screenshot({
    path: '../.cursor/session/evidence/fast-module-http-datasource-runtime-107/http-published-rows.png',
    fullPage: true,
  })
})
