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
        'X-Request-ID': `batch105-${crypto.randomUUID()}`,
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

async function provision(page: Page) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 8)
  await apiData(page, '/api/v1/context/platform:switch', 'POST', {})
  const system = await apiData<{ id: string }>(
    page,
    '/api/v1/platform/admin/systems',
    'POST',
    {
      code: `batch105_${suffix}`,
      name: `Batch105 HTTP Preview ${suffix}`,
      description: 'Real Edge administrator HTTP draft preview journey',
      tenantMode: 'SINGLE',
    },
    randomUUID(),
  )
  const systemId = String(system.id)
  await apiData(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const configRoot = `/api/v1/systems/${systemId}/admin/config`
  const group = await apiData<{ id: string }>(page, `${configRoot}/module-groups`, 'POST', {
    code: 'preview_anchor', name: 'Preview Anchor', description: '', iconKey: 'database',
    sortOrder: 0, status: 'ENABLED', draftRevision: '0',
  }, randomUUID())
  const module = await apiData<{ id: string }>(page, `${configRoot}/modules`, 'POST', {
    groupId: String(group.id), code: 'batch105_anchor', name: 'Batch105 Anchor',
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
    reason: 'Batch105 browser anchor schema',
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})

  const adminRoot = `/api/v1/systems/${systemId}/admin/data-sources`
  const source = await apiData<Record<string, any>>(page, adminRoot, 'POST', {
    code: 'batch105_http', moduleId: String(module.id), name: 'Batch105 HTTP preview',
    description: 'Administrator-only draft preview', sourceKind: 'HTTP_JSON',
    httpJsonConnection: {
      endpoint: 'https://denied.example.test/preview', authSecretRef: null, timeoutSeconds: 3,
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
        endpoint: 'https://denied.example.test/preview', authSecretRef: null, timeoutSeconds: 3,
      },
      httpFieldProjections: [{
        sourceField: 'external_id', fieldCode: 'external_id', sourceType: 'STRING',
      }],
      outputFields: [], fixedFilters: [], defaultSort: null, defaultTimeFieldCode: null,
    },
  )
  return { systemId, sourceId: String(source.id), draftVersion: Number(saved.draftVersion) }
}

test('previews the exact saved HTTP draft safely and clears stale results', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'Batch105 real function journey runs once on desktop Edge')
  test.skip(!rootUsername || !rootPassword, 'E2E_ROOT_USERNAME and E2E_ROOT_PASSWORD are required')

  await login(page)
  const fixture = await provision(page)
  await page.goto(`/systems/${fixture.systemId}/admin/data-sources`)
  await expect(page.locator('.http-projection-row')).toHaveCount(1)
  await expect(page.locator('.draft-rows-preview')).toBeVisible()

  await page.locator('.draft-rows-preview').click()
  const first = page.locator('.draft-preview-result[data-code="SAFE_TARGET"]')
  await expect(first).toBeVisible()
  await expect(first).toHaveAttribute('data-draft-version', String(fixture.draftVersion))
  await expect(first).not.toContainText('denied.example.test')
  await expect(page.locator('.draft-preview-row')).toHaveCount(0)

  await page.locator('.http-json-endpoint').fill('https://still-denied.example.test/preview')
  await expect(page.locator('.draft-preview-result')).toHaveCount(0)
  await expect(page.locator('.editor-state')).toHaveAttribute('data-state', 'dirty')
  await page.locator('.draft-rows-preview').click()
  const second = page.locator('.draft-preview-result[data-code="SAFE_TARGET"]')
  await expect(second).toBeVisible()
  await expect(second).toHaveAttribute('data-draft-version', String(fixture.draftVersion + 1))
  await expect(page.locator('.editor-state')).toHaveAttribute('data-state', 'clean')

  const detail = await apiData<Record<string, any>>(
    page,
    `/api/v1/systems/${fixture.systemId}/admin/data-sources/${fixture.sourceId}`,
  )
  expect(detail.draftVersion).toBe(fixture.draftVersion + 1)
  expect(detail.draft.httpFieldProjections).toEqual([{
    sourceField: 'external_id', fieldCode: 'external_id', sourceType: 'STRING',
  }])
  await apiData(
    page,
    `/api/v1/systems/${fixture.systemId}/admin/data-sources/${fixture.sourceId}/draft:publish`,
    'POST',
    { expectedVersion: detail.draftVersion },
    randomUUID(),
    422,
  )

  await page.screenshot({
    path: '../.cursor/session/evidence/fast-module-http-datasource-preview-105/http-draft-preview.png',
    fullPage: true,
  })
})
