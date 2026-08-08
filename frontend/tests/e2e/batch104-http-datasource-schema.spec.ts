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
        'X-Request-ID': `batch104-${crypto.randomUUID()}`,
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
      code: `batch104_${suffix}`,
      name: `Batch104 HTTP Schema ${suffix}`,
      description: 'Real Edge HTTP schema discovery and projection journey',
      tenantMode: 'SINGLE',
    },
    randomUUID(),
  )
  const systemId = String(system.id)
  await apiData(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const configRoot = `/api/v1/systems/${systemId}/admin/config`
  const group = await apiData<{ id: string }>(page, `${configRoot}/module-groups`, 'POST', {
    code: 'external_schemas',
    name: 'External Schemas',
    description: '',
    iconKey: 'database',
    sortOrder: 0,
    status: 'ENABLED',
    draftRevision: '0',
  }, randomUUID())
  const module = await apiData<{ id: string }>(page, `${configRoot}/modules`, 'POST', {
    groupId: String(group.id),
    code: 'batch104_anchor',
    name: 'Batch104 Anchor',
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
    reason: 'Batch104 browser anchor schema',
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})
  return { systemId, moduleId: String(module.id) }
}

test('discovers safely, persists an explicit projection, and keeps HTTP publication blocked', async ({
  page,
}, testInfo) => {
  test.skip(testInfo.project.name !== 'desktop', 'Batch104 real function journey runs once on desktop Edge')
  test.skip(!rootUsername || !rootPassword, 'E2E_ROOT_USERNAME and E2E_ROOT_PASSWORD are required')

  await login(page)
  const fixture = await provisionAnchorModule(page)
  await page.goto(`/systems/${fixture.systemId}/admin/data-sources`)
  await expect(page.locator('.data-source-workspace')).toBeVisible()
  await page.locator('.data-source-create').click()
  const modal = page.locator('.ant-modal:visible')
  await modal.locator('.create-source-name').fill('Batch104 HTTP schema draft')
  await modal.locator('.create-source-code').fill('batch104_http')
  await modal.locator('.create-source-kind').click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option-content', {
    hasText: 'HTTP JSON',
  }).click()
  await modal.locator('.create-http-json-endpoint')
    .fill('https://denied.example.test/schema')
  await modal.locator('.create-http-json-timeout').fill('3')
  await modal.locator('.ant-btn-primary').click()

  await expect(page.locator('.http-field-projection')).toBeVisible()
  await expect(page.locator('.output-editor')).toHaveCount(0)
  await expect(page.locator('.filter-editor')).toHaveCount(0)
  await expect(page.locator('.query-defaults')).toHaveCount(0)
  await page.locator('.draft-schema-discover').click()
  await expect(page.locator('.schema-discovery-result[data-code="SAFE_TARGET"]')).toBeVisible()
  await expect(page.locator('.schema-candidate')).toHaveCount(0)
  await expect(page.locator('.schema-discovery-result')).not.toContainText('denied.example.test')

  await page.locator('.http-json-endpoint').fill('https://still-denied.example.test/schema')
  await expect(page.locator('.schema-discovery-result')).toHaveCount(0)
  await page.locator('.draft-schema-discover').click()
  await expect(page.locator('.schema-discovery-result[data-code="SAFE_TARGET"]')).toBeVisible()
  await expect(page.locator('.editor-state')).toHaveAttribute('data-state', 'clean')

  const sources = await apiData<Array<Record<string, any>>>(
    page,
    `/api/v1/systems/${fixture.systemId}/admin/data-sources`,
  )
  const source = sources.find(item => item.code === 'batch104_http')
  expect(source).toBeTruthy()
  expect(String(source!.moduleId)).toBe(fixture.moduleId)
  expect(source!.draftVersion).toBe(2)
  expect(source!.draft.httpFieldProjections).toEqual([])

  const saved = await apiData<Record<string, any>>(
    page,
    `/api/v1/systems/${fixture.systemId}/admin/data-sources/${source!.id}/draft`,
    'PUT',
    {
      expectedVersion: source!.draftVersion,
      name: source!.name,
      description: source!.description ?? null,
      sourceKind: 'HTTP_JSON',
      httpJsonConnection: {
        endpoint: 'https://still-denied.example.test/schema',
        authSecretRef: null,
        timeoutSeconds: 3,
      },
      httpFieldProjections: [{
        sourceField: 'external_id',
        fieldCode: 'external_id',
        sourceType: 'STRING',
      }],
      outputFields: [],
      fixedFilters: [],
      defaultSort: null,
      defaultTimeFieldCode: null,
    },
  )
  expect(saved.draftVersion).toBe(3)
  expect(saved.draft.httpFieldProjections).toEqual([{
    sourceField: 'external_id', fieldCode: 'external_id', sourceType: 'STRING',
  }])

  await page.reload()
  await expect(page.locator('.http-projection-row')).toHaveCount(1)
  await expect(page.locator('.http-projection-source-field')).toHaveText('external_id')
  await expect(page.locator('.http-projection-source-type')).toHaveText('STRING')
  await expect(page.locator('.http-projection-target')).toHaveValue('external_id')

  const checkedDraft = await apiData<{ issues: Array<{ code: string }> }>(
    page,
    `/api/v1/systems/${fixture.systemId}/admin/data-sources/${source!.id}/draft:check`,
    'POST',
  )
  expect(checkedDraft.issues[0]?.code).toBe('SOURCE_RUNTIME_UNAVAILABLE')
  expect(checkedDraft.issues.map(issue => issue.code)).not.toContain('HTTP_PROJECTION_REQUIRED')
  expect(checkedDraft.issues.map(issue => issue.code)).not.toContain('HTTP_PROJECTION_TYPE_INCOMPATIBLE')
  await apiData(
    page,
    `/api/v1/systems/${fixture.systemId}/admin/data-sources/${source!.id}/draft:publish`,
    'POST',
    { expectedVersion: 3 },
    randomUUID(),
    422,
  )

  await page.screenshot({
    path: '../.cursor/session/evidence/fast-module-http-datasource-schema-104/http-schema-projection.png',
    fullPage: true,
  })
})
