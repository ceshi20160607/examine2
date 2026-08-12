import { randomUUID } from 'node:crypto'

import { expect, test, type Page, type Response } from '@playwright/test'

test.use({ trace: 'off' })
test.setTimeout(120_000)

const rootUsername = process.env.E2E_ROOT_USERNAME
const rootPassword = process.env.E2E_ROOT_PASSWORD

function credentials() {
  test.skip(!rootUsername || !rootPassword, 'Root credentials are required')
  return { username: rootUsername!, password: rootPassword! }
}

async function login(page: Page, account?: { username: string; password: string }) {
  const activeAccount = account ?? credentials()
  await page.goto('/auth/login')
  await page.getByLabel('账号').fill(activeAccount.username)
  await page.getByLabel('密码').fill(activeAccount.password)
  await Promise.all([
    page.waitForURL(/\/platform\/workbench$/),
    page.getByRole('button', { name: '登录', exact: true }).click(),
  ])
}

async function data(response: Response) {
  expect(response.ok()).toBe(true)
  return (await response.json() as { data: Record<string, unknown> }).data
}

async function apiRequest(page: Page, path: string, method = 'GET', body?: unknown, idempotencyKey?: string) {
  return page.evaluate(async ({ path, method, body, idempotencyKey }) => {
    const csrf = document.cookie.split('; ').find((item) => item.startsWith('EXAMINE_CSRF='))?.split('=')[1] ?? ''
    const response = await fetch(path, {
      method,
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/json',
        'X-CSRF-Token': decodeURIComponent(csrf),
        'X-Request-ID': `vs3-mobile-${crypto.randomUUID()}`,
        ...(idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
    })
    return { status: response.status, payload: await response.json() }
  }, { path, method, body, idempotencyKey })
}

async function apiData(page: Page, path: string, method = 'GET', body?: unknown, idempotencyKey?: string) {
  const response = await apiRequest(page, path, method, body, idempotencyKey)
  expect(response.status, JSON.stringify(response.payload)).toBe(200)
  return response.payload.data as Record<string, any>
}

async function createActiveRuntimeRecord(page: Page, systemId: string, title: string, subject: string) {
  const schema = await apiData(page, `/api/v1/systems/${systemId}/runtime/modules/work_order/record-schema`)
  const createdResponse = await apiRequest(page, `/api/v1/systems/${systemId}/runtime/modules/work_order/records`, 'POST', {
    schemaVersionId: String(schema.schemaVersionId),
    title,
    values: { subject },
  }, randomUUID())
  expect(createdResponse.status, JSON.stringify(createdResponse.payload)).toBe(201)
  const created = createdResponse.payload.data as Record<string, any>
  const activatedResponse = await apiRequest(page,
    `/api/v1/systems/${systemId}/runtime/modules/work_order/records/${created.recordId}:activate`,
    'POST', { expectedVersion: Number(created.version) }, randomUUID())
  expect(activatedResponse.status, JSON.stringify(activatedResponse.payload)).toBe(200)
  return activatedResponse.payload.data as Record<string, any>
}

function publishedPageLayout(page: Record<string, any>) {
  return typeof page.layout_json === 'string' ? JSON.parse(page.layout_json) as Record<string, any>
    : page.layout_json as Record<string, any>
}

async function createPublishedMobileSystem(page: Page) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 8)
  await apiData(page, '/api/v1/context/platform:switch', 'POST', {})
  const system = await apiData(page, '/api/v1/platform/admin/systems', 'POST', {
    code: `vs3_mobile_${suffix}`,
    name: `VS3 Mobile ${suffix}`,
    description: 'VS3 mobile B2 journey',
    tenantMode: 'SINGLE',
  }, randomUUID())
  const systemId = String(system.id)
  await apiData(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const configRoot = `/api/v1/systems/${systemId}/admin/config`
  const group = await apiData(page, `${configRoot}/module-groups`, 'POST', {
    code: 'mobile_business', name: '移动业务', description: '', iconKey: 'folder',
    sortOrder: 0, status: 'ENABLED', draftRevision: '0',
  }, randomUUID())
  const mobileModule = await apiData(page, `${configRoot}/modules`, 'POST', {
    groupId: String(group.id), code: 'mobile_order', name: '移动工单', description: '', iconKey: 'blocks',
    sortOrder: 0, status: 'ENABLED', allowComments: true, allowTeam: true, draftRevision: '1',
  }, randomUUID())
  const archiveGroup = await apiData(page, `${configRoot}/module-groups`, 'POST', {
    code: 'mobile_archive', name: '移动归档', description: '', iconKey: 'archive',
    sortOrder: 1, status: 'ENABLED', draftRevision: '2',
  }, randomUUID())
  await apiData(page, `${configRoot}/modules`, 'POST', {
    groupId: String(archiveGroup.id), code: 'archived_order', name: '归档工单', description: '', iconKey: 'archive',
    sortOrder: 0, status: 'ENABLED', allowComments: true, allowTeam: true, draftRevision: '3',
  }, randomUUID())
  const mobileField = await apiData(page, `${configRoot}/modules/${mobileModule.id}/fields`, 'POST', {
    dictionaryId: null,
    targetModuleId: null,
    code: 'permission_note',
    name: '权限备注',
    type: 'TEXT',
    sortOrder: 0,
    required: false,
    hidden: false,
    readonly: false,
    searchable: false,
    filterable: false,
    showInList: true,
    showInDetail: true,
    indexMode: 'NONE',
    status: 'ENABLED',
    readPermissionMode: 'STAGED',
    writePermissionMode: 'STAGED',
    properties: { maxLength: 200 },
    draftRevision: '4',
  }, randomUUID())
  expect(mobileField).toMatchObject({
    code: 'permission_note',
    readPermissionMode: 'STAGED',
    writePermissionMode: 'STAGED',
  })
  const check = await apiData(page, `${configRoot}/checks`, 'POST', { draftRevision: '5' })
  const root = await apiData(page, configRoot)
  await apiData(page, `${configRoot}:publish`, 'POST', {
    checkId: String(check.id), draftRevision: '5', configRootVersion: String(root.version),
    reason: 'VS3 mobile B2 acceptance',
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})
  return {
    id: systemId,
    name: String(system.name),
    moduleId: String(mobileModule.id),
    fieldId: String(mobileField.id),
    fieldCode: 'permission_note',
    secondaryModuleCode: 'archived_order',
    secondaryGroupName: '移动归档',
    secondaryModuleName: '归档工单',
  }
}

async function createPublishedRuntimeRole(
  page: Page,
  systemId: string,
  moduleCode: string,
  fieldPermissionCodes: string[] = [],
) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 8)
  await apiData(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const tenantPage = await apiData(page, `/api/v1/systems/${systemId}/admin/tenants?size=200`)
  const tenant = (tenantPage.items as Record<string, any>[]).find((item) => item.isDefault) ?? tenantPage.items[0]
  const scopePage = await apiData(page, `/api/v1/systems/${systemId}/admin/data-scopes?size=200`)
  const dataScope = (scopePage.items as Record<string, any>[]).find((item) => item.kind === 'ALL')
  if (!tenant || !dataScope) throw new Error('Published runtime role requires a default tenant and ALL data scope')

  const role = await apiData(page, `/api/v1/systems/${systemId}/admin/roles`, 'POST', {
    code: `runtime_member_${suffix}`,
    name: `运行成员 ${suffix}`,
    description: '普通成员运行态验收角色',
  }, randomUUID())
  const draft = await apiData(page, `/api/v1/systems/${systemId}/admin/roles/${role.id}/draft`, 'PUT', {
    name: role.name,
    description: role.description,
    permissionCodes: [
      'system.runtime.access',
      'system.workbench.view',
      `module.${moduleCode}.view`,
      `module.${moduleCode}.create`,
      `module.${moduleCode}.update`,
      ...fieldPermissionCodes,
    ],
    deniedPermissionCodes: [],
    dataScopeId: String(dataScope.id),
    version: String(role.version),
  })
  const checked = await apiData(page, `/api/v1/systems/${systemId}/admin/roles/${role.id}/draft:check`, 'POST', {
    version: String(draft.version),
  }, randomUUID())
  await apiData(page, `/api/v1/systems/${systemId}/admin/roles/${role.id}/draft:publish`, 'POST', {
    version: String(checked.version),
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})
  return {
    id: String(role.id),
    name: String(role.name),
    tenantId: String(tenant.id),
    dataScopeId: String(dataScope.id),
  }
}

function fieldUpdateBody(field: Record<string, any>, draftRevision: string, readPermissionMode: string, writePermissionMode: string) {
  return {
    dictionaryId: field.dictionaryId ?? null,
    targetModuleId: field.targetModuleId ?? null,
    code: field.code,
    name: field.name,
    type: field.type,
    sortOrder: Number(field.sortOrder),
    required: Boolean(field.required),
    hidden: Boolean(field.hidden),
    readonly: Boolean(field.readonly),
    searchable: Boolean(field.searchable),
    filterable: Boolean(field.filterable),
    showInList: Boolean(field.showInList),
    showInDetail: Boolean(field.showInDetail),
    indexMode: field.indexMode,
    status: field.status,
    readPermissionMode,
    writePermissionMode,
    properties: field.properties ?? {},
    version: String(field.version),
    draftRevision,
  }
}

async function enforcePublishedFieldPermissions(
  page: Page,
  systemId: string,
  moduleId: string,
  fieldId: string,
) {
  await apiData(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const configRoot = `/api/v1/systems/${systemId}/admin/config`
  const root = await apiData(page, configRoot)
  const fields = await apiData(page, `${configRoot}/modules/${moduleId}/fields`) as Record<string, any>[]
  const field = fields.find((candidate) => String(candidate.id) === fieldId)
  if (!field) throw new Error(`Field ${fieldId} is unavailable for enforcement`)
  const updated = await apiData(page, `${configRoot}/modules/${moduleId}/fields/${fieldId}`, 'PUT',
    fieldUpdateBody(field, String(root.draftRevision), 'ENFORCED', 'ENFORCED'))
  expect(updated).toMatchObject({ readPermissionMode: 'ENFORCED', writePermissionMode: 'ENFORCED' })
  const check = await apiData(page, `${configRoot}/checks`, 'POST', { draftRevision: String(updated.updatedRevision) })
  expect(check).toMatchObject({ status: 'PASSED', blockerCount: 0 })
  const checkedRoot = await apiData(page, configRoot)
  await apiData(page, `${configRoot}:publish`, 'POST', {
    checkId: String(check.id),
    draftRevision: String(updated.updatedRevision),
    configRootVersion: String(checkedRoot.version),
    reason: 'Batch98 enforce staged field permissions',
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})
}

async function republishRuntimeRole(
  page: Page,
  systemId: string,
  roleId: string,
  permissionCodes: string[],
) {
  await apiData(page, `/api/v1/context/systems/${systemId}:switch`, 'POST', {})
  const roles = await apiData(page, `/api/v1/systems/${systemId}/admin/roles?size=200`)
  const role = (roles.items as Record<string, any>[]).find((candidate) => String(candidate.id) === roleId)
  if (!role) throw new Error(`Role ${roleId} is unavailable`)
  const draft = await apiData(page, `/api/v1/systems/${systemId}/admin/roles/${roleId}/draft`, 'PUT', {
    name: role.name,
    description: role.description,
    permissionCodes,
    deniedPermissionCodes: [],
    dataScopeId: String(role.dataScopeId),
    version: String(role.version),
  })
  const checked = await apiData(page, `/api/v1/systems/${systemId}/admin/roles/${roleId}/draft:check`, 'POST', {
    version: String(draft.version),
  }, randomUUID())
  await apiData(page, `/api/v1/systems/${systemId}/admin/roles/${roleId}/draft:publish`, 'POST', {
    version: String(checked.version),
  }, randomUUID())
  await apiData(page, '/api/v1/auth/refresh', 'POST', {})
}

async function endSession(page: Page) {
  const response = await apiRequest(page, '/api/v1/auth/logout', 'POST', {})
  expect([200, 401]).toContain(response.status)
  await page.context().clearCookies()
  await page.goto('/auth/login')
  await expect(page.getByRole('heading', { name: '登录', exact: true })).toBeVisible()
}

async function registerOrdinaryMember(page: Page) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 8)
  const username = `member_${suffix}`
  const password = `Vs3-Member-${suffix}-42!`
  const displayName = `普通成员 ${suffix}`
  await page.goto('/auth/register')
  await page.getByLabel('用户名').fill(username)
  await page.getByLabel('姓名').fill(displayName)
  await page.getByLabel('密码').fill(password)
  await page.getByLabel('系统名称').fill(`成员个人系统 ${suffix}`)
  await page.getByLabel('系统编码').fill(`member_home_${suffix}`)
  await Promise.all([
    page.waitForURL(/\/systems\/\d+\/workbench$/),
    page.getByRole('button', { name: '创建并进入系统', exact: true }).click(),
  ])
  return { username, password, displayName }
}

async function createSystem(page: Page) {
  const suffix = randomUUID().replaceAll('-', '').slice(0, 8)
  const name = `VS3 Config ${suffix}`
  await page.locator('button[title="个人菜单"]').click()
  await page.getByRole('menuitem', { name: /平台后台/ }).click()
  await page.getByRole('button', { name: '创建系统' }).click()
  const dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('系统名称').fill(name)
  await dialog.getByLabel('系统编码').fill(`vs3_${suffix}`)
  await dialog.getByLabel('系统说明').fill('VS3 browser publication journey')
  const [response] = await Promise.all([
    page.waitForResponse((item) => item.request().method() === 'POST' && new URL(item.url()).pathname === '/api/v1/platform/admin/systems'),
    dialog.getByRole('button', { name: '保 存', exact: true }).click(),
  ])
  return { id: String((await data(response)).id), name }
}

async function openSystem(page: Page, system: { id: string; name: string }) {
  await page.getByRole('link', { name: '返回工作台' }).click()
  await page.getByRole('button', { name: new RegExp(system.name) }).click()
  await page.waitForURL(new RegExp(`/systems/${system.id}/workbench$`))
}

test('desktop configures, publishes, and reads active runtime navigation', async ({ page }) => {
  test.skip(!['desktop', 'desktop-compact'].includes(test.info().project.name), 'desktop-only')
  const savedViewMutations: string[] = []
  page.on('request', (request) => {
    const path = new URL(request.url()).pathname
    if (path.includes('/runtime/saved-views') && request.method() !== 'GET') {
      savedViewMutations.push(`${request.method()} ${path}`)
    }
  })
  await login(page)
  const system = await createSystem(page)
  await openSystem(page, system)
  await page.locator('button[title="个人菜单"]').click()
  await page.getByRole('menuitem', { name: /系统后台/ }).click()
  await page.getByRole('navigation', { name: '系统后台导航' }).getByRole('link', { name: '模块配置' }).click()
  await expect(page.getByRole('heading', { name: '模块配置工作台' })).toBeVisible()

  await page.getByTitle('新建模块组').click()
  let dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('名称').fill('业务管理')
  await dialog.getByLabel('编码').fill('business')
  const [groupResponse] = await Promise.all([
    page.waitForResponse((item) => item.request().method() === 'POST' && new URL(item.url()).pathname.endsWith('/module-groups')),
    dialog.getByRole('button', { name: /确\s*认/ }).click(),
  ])
  await data(groupResponse)
  await expect(page.getByText('业务管理', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '新建模块', exact: true }).click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('名称').fill('工单')
  await dialog.getByLabel('编码').fill('work_order')
  await dialog.getByRole('button', { name: /确\s*认/ }).click()
  await expect(page.getByRole('heading', { name: '工单' })).toBeVisible()

  await page.getByRole('button', { name: '字段', exact: true }).click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('名称').fill('主题')
  await dialog.getByLabel('编码').fill('subject')
  await dialog.getByRole('button', { name: /确\s*认/ }).click()
  await expect(page.getByText('主题', { exact: true })).toBeVisible()
  await page.getByTitle('编辑字段').click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('名称').fill('工单主题')
  await dialog.getByRole('button', { name: /确\s*认/ }).click()
  await expect(page.getByText('工单主题', { exact: true })).toBeVisible()

  await page.getByRole('button', { name: '字段', exact: true }).click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('字段类型').click()
  await page.keyboard.type('SUBTABLE')
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: /^SUBTABLE$/ }).click()
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0)
  await dialog.getByLabel('名称').fill('工单明细')
  await dialog.getByLabel('编码').fill('order_details')
  await dialog.getByLabel('子表列').click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: /^工单主题$/ }).click()
  await dialog.getByText('新建字段', { exact: true }).click()
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0)
  await dialog.getByText('允许编辑行', { exact: true }).click()
  const [subtableResponse] = await Promise.all([
    page.waitForResponse((item) => item.request().method() === 'POST' && new URL(item.url()).pathname.endsWith('/fields')),
    dialog.getByRole('button', { name: /确\s*认/ }).click(),
  ])
  const savedSubtable = await data(subtableResponse) as Record<string, any>
  expect(savedSubtable.properties).not.toHaveProperty('defaultMode')
  expect(savedSubtable.properties.columnFieldIds).toHaveLength(1)
  expect(savedSubtable.properties).not.toHaveProperty('filter')
  expect(savedSubtable.properties.allowRowUpdate).toBe(false)
  await expect(page.getByText('工单明细', { exact: true })).toBeVisible()

  await page.getByRole('tab', { name: '页面' }).click()
  await page.getByRole('button', { name: '新建页面', exact: true }).click()
  dialog = page.locator('.ant-modal:visible')
  await expect(dialog.getByLabel('页面类型')).toContainText('LIST')
  await dialog.getByLabel('名称').fill('工单看板')
  await dialog.getByLabel('编码').fill('work_order_board')
  const [pageResponse] = await Promise.all([
    page.waitForResponse((item) => item.request().method() === 'POST'
      && new URL(item.url()).pathname.endsWith('/pages')),
    dialog.getByRole('button', { name: /确\s*认/ }).click(),
  ])
  const createdPage = await data(pageResponse) as Record<string, any>
  const createPageRequest = pageResponse.request().postDataJSON() as Record<string, any>
  expect(pageResponse.request().headers()['idempotency-key']).toBeTruthy()
  expect(createPageRequest).toEqual({
    code: 'work_order_board',
    name: '工单看板',
    type: 'LIST',
    isDefault: false,
    status: 'ENABLED',
    layout: {
      columns: 12,
      gap: 12,
      labelPosition: 'TOP',
      density: 'DEFAULT',
      stickyActions: true,
      pageSize: 20,
      showSearch: true,
      showFilters: true,
      filterScenarios: [],
    },
    draftRevision: expect.any(String),
  })
  expect(createdPage).toMatchObject({ code: 'work_order_board', name: '工单看板', type: 'LIST', isDefault: false })
  await expect(page.locator('.page-toolbar .ant-select-selection-item')).toContainText('工单看板 · LIST')
  await page.getByRole('button', { name: '组件', exact: true }).click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('名称').fill('主题组件')
  await dialog.getByLabel('编码').fill('subject_component')
  await dialog.getByRole('button', { name: /确\s*认/ }).click()
  await expect(page.getByText('主题组件', { exact: true })).toBeVisible()

  await page.getByRole('tab', { name: '动作' }).click()
  await page.getByRole('button', { name: '动作', exact: true }).click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('名称').fill('处理工单')
  await dialog.getByLabel('编码').fill('process_order')
  await dialog.getByRole('button', { name: /确\s*认/ }).click()
  await expect(page.getByText('处理工单', { exact: true })).toBeVisible()

  await page.getByRole('tab', { name: '规则' }).click()
  await page.getByRole('button', { name: '规则', exact: true }).click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('名称').fill('主题可见规则')
  await dialog.getByLabel('编码').fill('subject_visible')
  await dialog.getByTitle('添加条件').first().click()
  await dialog.getByTitle('添加条件组').first().click()
  await expect(dialog.getByLabel('条件字段')).toHaveCount(3)
  await expect(dialog.getByTitle('添加条件组')).toHaveCount(2)
  const ruleOperators = dialog.getByLabel('条件运算符')
  for (let index = 0; index < 3; index += 1) {
    await ruleOperators.nth(index).click()
    await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: /^EQ$/ }).click()
    await expect(ruleOperators.nth(index)).toContainText('EQ')
    await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0)
  }
  await expect(dialog.getByLabel('条件值')).toHaveCount(3)
  await dialog.getByLabel('规则目标字段').click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: /^工单明细$/ }).click()
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0)
  const ruleValues = dialog.getByLabel('条件值')
  await ruleValues.nth(0).fill('subject-root')
  await ruleValues.nth(1).fill('subject-sibling')
  await ruleValues.nth(2).fill('subject-nested')
  const joinOptions = dialog.locator('label.ant-segmented-item')
  await expect(joinOptions).toHaveCount(4)
  await joinOptions.filter({ hasText: /^OR$/ }).nth(1).click()

  await dialog.getByRole('button', { name: /确\s*认/ }).click()
  await expect(page.getByText('主题可见规则', { exact: true })).toBeVisible()
  await page.getByTitle('编辑规则').click()
  dialog = page.locator('.ant-modal:visible')
  await expect(dialog.locator('.ant-segmented')).toHaveCount(2)
  await expect(dialog.getByLabel('条件字段')).toHaveCount(3)
  await expect(dialog.locator('label.ant-segmented-item').filter({ hasText: /^OR$/ }).nth(1)).toHaveClass(/ant-segmented-item-selected/)
  await expect(dialog.getByLabel('条件值').nth(0)).toHaveValue('subject-root')
  await expect(dialog.getByLabel('条件值').nth(1)).toHaveValue('subject-sibling')
  await expect(dialog.getByLabel('条件值').nth(2)).toHaveValue('subject-nested')
  await expect(dialog.getByLabel('规则目标字段')).toContainText('工单明细')
  await dialog.getByRole('button', { name: /取\s*消/ }).click()


  await page.getByRole('button', { name: '复制', exact: true }).click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('名称').fill('工单副本')
  await dialog.getByLabel('编码').fill('work_order_copy')
  await dialog.getByRole('button', { name: /确\s*认/ }).click()
  await expect(page.getByRole('button', { name: /工单副本/ })).toBeVisible()
  await page.getByRole('button', { name: /工单副本/ }).click()
  await page.getByRole('tab', { name: '页面' }).click()
  await page.locator('.config-page-select').click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: /^工单看板 · LIST$/ }).click()
  await expect(page.locator('.ant-select-dropdown:visible')).toHaveCount(0)
  await expect(page.getByText('主题组件', { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '动作' }).click()
  await expect(page.getByText('处理工单', { exact: true })).toBeVisible()
  await page.getByRole('tab', { name: '规则' }).click()
  await expect(page.getByText('主题可见规则', { exact: true })).toBeVisible()
  await page.getByText('主题可见规则', { exact: true }).click()
  await expect(page.getByText('属性', { exact: true })).toBeVisible()
  await expect(page.getByText('优先级', { exact: true })).toBeVisible()
  const columns = await page.evaluate(() => {
    const workbench = document.querySelector('.config-workbench')
    const panes = workbench ? Array.from(workbench.children).map((element) => element.getBoundingClientRect()) : []
    return { count: panes.length, overlaps: panes.some((pane, index) => index > 0 && pane.left < panes[index - 1]!.right - 1), right: panes.at(-1)?.right ?? 0, viewport: window.innerWidth }
  })
  expect(columns.count).toBe(3)
  expect(columns.overlaps).toBe(false)
  expect(columns.right).toBeLessThanOrEqual(columns.viewport + 1)
  const overflowingRows = await page.evaluate(() => Array.from(document.querySelectorAll('.resource-row')).filter((row) => {
    const rowRect = row.getBoundingClientRect()
    return Array.from(row.children).some((child) => {
      const rect = child.getBoundingClientRect()
      return rect.left < rowRect.left - 1 || rect.right > rowRect.right + 1
    })
  }).length)
  expect(overflowingRows).toBe(0)
  await page.screenshot({ path: '../.cursor/session/evidence/vs3/config-studio-' + test.info().project.name + '.png' })

  await page.getByRole('button', { name: '管理字典', exact: true }).click()
  let drawer = page.locator('.ant-drawer-content').filter({ hasText: '数据字典' })
  await drawer.getByRole('button', { name: '字典', exact: true }).click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('名称').fill('工单状态')
  await dialog.getByLabel('编码').fill('work_order_status')
  await dialog.getByRole('button', { name: /确\s*认/ }).click()
  await drawer.getByRole('button', { name: /工单状态/ }).click()
  await drawer.getByRole('button', { name: '字典项', exact: true }).click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('名称').fill('待处理')
  await dialog.getByLabel('编码').fill('pending')
  await dialog.getByRole('button', { name: /确\s*认/ }).click()
  await expect(drawer.getByText('待处理', { exact: true })).toBeVisible()
  await drawer.getByRole('button', { name: 'Close' }).click()

  await page.getByRole('button', { name: '成员预览', exact: true }).click()
  drawer = page.locator('.ant-drawer-content').filter({ hasText: '成员权限预览' })
  await expect(drawer.getByText('待发布草稿', { exact: true })).toBeVisible()
  await expect(drawer.getByText('工单', { exact: false }).first()).toBeVisible()
  await drawer.getByRole('button', { name: 'Close' }).click()

  await page.getByRole('button', { name: '检查', exact: true }).click()
  await expect(page.getByText('检查通过', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '发布', exact: true }).click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('变更原因').fill('VS3 browser acceptance')
  await dialog.getByRole('button', { name: /确\s*认/ }).click()
  await expect(page.getByText('运行中')).toBeVisible()
  const runtimeDefinition = await apiData(page, `/api/v1/systems/${system.id}/runtime/modules/work_order/definition`)
  const publishedPage = (runtimeDefinition.pages as Record<string, any>[])
    .find((item) => item.page_code === 'work_order_board')
  expect(publishedPage).toMatchObject({
    id: createdPage.id,
    page_code: 'work_order_board',
    page_name: '工单看板',
    page_type: 'LIST',
    desired_status: 'ENABLED',
  })
  expect(Boolean(publishedPage?.is_default)).toBe(false)
  expect(runtimeDefinition.components).toEqual(expect.arrayContaining([
    expect.objectContaining({ page_id: createdPage.id, component_key: 'subject_component' }),
  ]))
  const publishedDefaultListPageV1 = (runtimeDefinition.pages as Record<string, any>[])
    .find((item) => item.page_type === 'LIST' && Boolean(item.is_default))
  expect(publishedDefaultListPageV1).toBeTruthy()
  expect(publishedPageLayout(publishedDefaultListPageV1!)).not.toHaveProperty('filterScenarios')
  expect(publishedPageLayout(publishedDefaultListPageV1!)).not.toHaveProperty('defaultFilterScenarioCode')

  await page.getByTitle('编辑模块组').click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('名称').fill('业务管理二版')
  await dialog.getByRole('button', { name: /确\s*认/ }).click()

  await page.locator('.module-item').filter({ hasText: /work_order$/ }).click()
  await page.getByRole('tab', { name: '页面' }).click()
  await page.locator('.config-page-select').click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option')
    .filter({ hasText: `${publishedDefaultListPageV1!.page_name} · LIST` }).click()
  await page.getByTitle('编辑页面').click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByRole('button', { name: '添加方案', exact: true }).click()
  await dialog.getByRole('button', { name: '添加方案', exact: true }).click()
  const scenarioCodes = dialog.getByLabel('共享筛选方案编码')
  const scenarioNames = dialog.getByLabel('共享筛选方案名称')
  const scenarioFilters = dialog.getByLabel('共享筛选方案筛选 JSON')
  const scenarioSorts = dialog.getByLabel('共享筛选方案排序 JSON')
  await scenarioCodes.nth(0).fill('alpha_orders')
  await scenarioNames.nth(0).fill('甲类工单')
  await scenarioFilters.nth(0).fill(JSON.stringify({
    kind: 'PREDICATE', fieldCode: 'subject', operator: 'EQ', value: 'scenario-alpha',
  }))
  await scenarioSorts.nth(0).fill('[]')
  await scenarioCodes.nth(1).fill('beta_orders')
  await scenarioNames.nth(1).fill('乙类工单')
  await scenarioFilters.nth(1).fill(JSON.stringify({
    kind: 'PREDICATE', fieldCode: 'subject', operator: 'EQ', value: 'scenario-beta',
  }))
  await scenarioSorts.nth(1).fill('[]')
  await dialog.getByLabel('默认共享筛选方案').click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: '甲类工单' }).click()
  const [scenarioPageResponse] = await Promise.all([
    page.waitForResponse((item) => item.request().method() === 'PUT'
      && new URL(item.url()).pathname.endsWith(`/pages/${publishedDefaultListPageV1!.id}`)),
    dialog.getByRole('button', { name: /确\s*认/ }).click(),
  ])
  await data(scenarioPageResponse)
  const scenarioPageRequest = scenarioPageResponse.request().postDataJSON() as Record<string, any>
  expect(scenarioPageRequest).toEqual({
    code: publishedDefaultListPageV1!.page_code,
    name: publishedDefaultListPageV1!.page_name,
    type: 'LIST',
    isDefault: true,
    status: 'ENABLED',
    version: expect.any(String),
    draftRevision: expect.any(String),
    layout: {
      columns: 12,
      gap: 12,
      labelPosition: 'TOP',
      density: 'DEFAULT',
      stickyActions: true,
      pageSize: 20,
      showSearch: true,
      showFilters: true,
      filterScenarios: [
        {
          code: 'alpha_orders', name: '甲类工单',
          filter: { kind: 'PREDICATE', fieldCode: 'subject', operator: 'EQ', value: 'scenario-alpha' },
          sort: [],
        },
        {
          code: 'beta_orders', name: '乙类工单',
          filter: { kind: 'PREDICATE', fieldCode: 'subject', operator: 'EQ', value: 'scenario-beta' },
          sort: [],
        },
      ],
      defaultFilterScenarioCode: 'alpha_orders',
    },
  })
  const draftHiddenDefinition = await apiData(page, `/api/v1/systems/${system.id}/runtime/modules/work_order/definition`)
  expect(draftHiddenDefinition.activeVersionId).toBe(runtimeDefinition.activeVersionId)
  const draftHiddenDefaultPage = (draftHiddenDefinition.pages as Record<string, any>[])
    .find((item) => item.page_type === 'LIST' && Boolean(item.is_default))
  expect(publishedPageLayout(draftHiddenDefaultPage!)).not.toHaveProperty('filterScenarios')
  expect(publishedPageLayout(draftHiddenDefaultPage!)).not.toHaveProperty('defaultFilterScenarioCode')

  await page.getByRole('button', { name: '检查', exact: true }).click()
  await expect(page.getByText('检查通过', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '发布', exact: true }).click()
  dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('变更原因').fill('VS3 browser acceptance V2')
  await dialog.getByRole('button', { name: /确\s*认/ }).click()
  await expect(page.getByText('V2', { exact: false }).first()).toBeVisible()
  const runtimeDefinitionV2 = await apiData(page, `/api/v1/systems/${system.id}/runtime/modules/work_order/definition`)
  expect(runtimeDefinitionV2.activeVersionId).not.toBe(runtimeDefinition.activeVersionId)
  const publishedDefaultListPageV2 = (runtimeDefinitionV2.pages as Record<string, any>[])
    .find((item) => item.page_type === 'LIST' && Boolean(item.is_default))
  expect(publishedPageLayout(publishedDefaultListPageV2!)).toEqual({
    columns: 12,
    gap: 12,
    labelPosition: 'TOP',
    density: 'DEFAULT',
    stickyActions: true,
    pageSize: 20,
    showSearch: true,
    showFilters: true,
    filterScenarios: [
      { code: 'alpha_orders', name: '甲类工单', filter: { kind: 'PREDICATE', fieldCode: 'subject', operator: 'EQ', value: 'scenario-alpha' }, sort: [] },
      { code: 'beta_orders', name: '乙类工单', filter: { kind: 'PREDICATE', fieldCode: 'subject', operator: 'EQ', value: 'scenario-beta' }, sort: [] },
    ],
    defaultFilterScenarioCode: 'alpha_orders',
  })
  await page.getByTitle('比较版本').click()
  const diffDrawer = page.locator('.ant-drawer-content').filter({ hasText: '版本差异' })
  const groupDiff = diffDrawer.locator('.diff-sections section').filter({ hasText: 'groups' })
  const pageDiff = diffDrawer.locator('.diff-sections section').filter({ hasText: 'pages' })
  await expect(groupDiff).toContainText('修改 1')
  await expect(pageDiff).toContainText('修改 1')
  await diffDrawer.getByRole('button', { name: 'Close' }).click()

  const alphaTitle = `甲类场景记录 ${Date.now()}`
  const betaTitle = `乙类场景记录 ${Date.now()}`
  await createActiveRuntimeRecord(page, system.id, alphaTitle, 'scenario-alpha')
  await createActiveRuntimeRecord(page, system.id, betaTitle, 'scenario-beta')

  const alphaQueryPromise = page.waitForResponse((item) => item.request().method() === 'POST'
    && new URL(item.url()).pathname.endsWith('/runtime/modules/work_order/records:query'))
  await page.goto(`/systems/${system.id}/workbench?module=work_order`)
  const alphaQueryResponse = await alphaQueryPromise
  expect(alphaQueryResponse.ok()).toBe(true)
  expect(alphaQueryResponse.request().postDataJSON()).toEqual({
    schemaVersionId: expect.any(String),
    page: 1,
    size: 50,
    recordScope: 'active',
    q: null,
    filter: { kind: 'PREDICATE', fieldCode: 'subject', operator: 'EQ', value: 'scenario-alpha' },
    sort: [],
    columns: [],
    viewId: null,
  })
  const alphaQueryPayload = await alphaQueryResponse.json() as Record<string, any>
  expect(alphaQueryPayload.data.total).toBe(1)
  expect((alphaQueryPayload.data.rows as Record<string, any>[]).map((row) => row.title)).toEqual([alphaTitle])
  await expect(page.getByRole('heading', { name: system.name })).toBeVisible()
  await expect(page.getByRole('button', { name: '工单', exact: true })).toBeVisible()
  const sharedScenarioSelect = page.getByLabel('共享筛选方案')
  await expect(sharedScenarioSelect).toContainText('甲类工单')
  const recordTable = page.locator('.record-table-wrap')
  await expect(recordTable.getByText(alphaTitle, { exact: true })).toBeVisible()
  await expect(recordTable.getByText(betaTitle, { exact: true })).toHaveCount(0)
  const scenarioQueryResponse = page.waitForResponse((item) => item.request().method() === 'POST'
    && new URL(item.url()).pathname.endsWith('/records:query'))
  await sharedScenarioSelect.click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: '乙类工单' }).click()
  const betaQueryResponse = await scenarioQueryResponse
  expect(betaQueryResponse.ok()).toBe(true)
  expect(betaQueryResponse.request().postDataJSON()).toEqual({
    schemaVersionId: expect.any(String),
    page: 1,
    size: 50,
    recordScope: 'active',
    q: null,
    filter: { kind: 'PREDICATE', fieldCode: 'subject', operator: 'EQ', value: 'scenario-beta' },
    sort: [],
    columns: [],
    viewId: null,
  })
  const betaQueryPayload = await betaQueryResponse.json() as Record<string, any>
  expect(betaQueryPayload.data.total).toBe(1)
  expect((betaQueryPayload.data.rows as Record<string, any>[]).map((row) => row.title)).toEqual([betaTitle])
  await expect(recordTable.getByText(betaTitle, { exact: true })).toBeVisible()
  await expect(recordTable.getByText(alphaTitle, { exact: true })).toHaveCount(0)
  const runtimeUrl = new URL(page.url())
  expect(runtimeUrl.searchParams.has('filter')).toBe(true)
  expect(runtimeUrl.searchParams.has('viewId')).toBe(false)
  expect(savedViewMutations).toEqual([])
  expect(await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)).toBe(false)
})

test('ordinary member requests access and sees only the authorized published module', async ({ page }) => {
  test.skip(test.info().project.name !== 'desktop', 'primary desktop journey only')
  await login(page)
  const system = await createPublishedMobileSystem(page)
  const readPermission = `module.mobile_order.field.${system.fieldCode}.read`
  const writePermission = `module.mobile_order.field.${system.fieldCode}.write`
  const activePermissionPage = await apiData(page,
    `/api/v1/systems/${system.id}/admin/permissions?page=1&size=500&status=ACTIVE`)
  expect((activePermissionPage.items as Record<string, any>[]).map((permission) => permission.code))
    .toEqual(expect.arrayContaining([readPermission, writePermission]))
  const role = await createPublishedRuntimeRole(page, system.id, 'mobile_order', [readPermission])
  await enforcePublishedFieldPermissions(page, system.id, system.moduleId, system.fieldId)
  await endSession(page)

  const member = await registerOrdinaryMember(page)
  await page.goto(`/systems/${system.id}/workbench`)
  await expect(page).toHaveURL(new RegExp(`/no-member/${system.id}$`))
  await expect(page.getByRole('heading', { name: '申请系统访问权限' })).toBeVisible()
  await page.getByLabel('目标租户 ID（可选）').fill(role.tenantId)
  await page.getByLabel('申请原因').fill('需要处理移动工单')
  await page.getByRole('button', { name: '提交申请', exact: true }).click()
  await expect(page.getByText('等待管理员审核', { exact: true })).toBeVisible()
  await endSession(page)

  await login(page)
  await page.goto(`/systems/${system.id}/admin/access-requests`)
  const requestRow = page.getByRole('row').filter({ hasText: member.displayName })
  await expect(requestRow).toBeVisible()
  await requestRow.getByRole('button', { name: '批准', exact: true }).click()
  const dialog = page.locator('.ant-modal:visible')
  await dialog.getByLabel('绑定角色').click()
  await page.locator('.ant-select-dropdown:visible .ant-select-item-option').filter({ hasText: role.name }).click()
  await dialog.getByLabel('审核意见').fill('批准普通成员运行态访问')
  await dialog.locator('.ant-modal-footer .ant-btn-primary').click()
  await expect(page.getByText('申请已批准', { exact: true })).toBeVisible()
  await endSession(page)

  await login(page, member)
  await page.goto(`/systems/${system.id}/workbench`)
  await expect(page.getByRole('heading', { name: system.name })).toBeVisible()
  await expect(page.getByRole('button', { name: '移动工单', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: system.secondaryModuleName, exact: true })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '移动工单', exact: true })).toBeVisible()
  const readOnlySchema = await apiData(page,
    `/api/v1/systems/${system.id}/runtime/modules/mobile_order/record-schema`)
  const readOnlyField = (readOnlySchema.fields as Record<string, any>[])
    .find((field) => field.fieldCode === system.fieldCode)
  expect(readOnlyField).toMatchObject({ fieldCode: system.fieldCode, writable: false })
  await expect(page.getByText('1 个可见字段', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '新建记录', exact: true }).click()
  await expect(page.locator('#runtime-field-permission_note')).toHaveCount(0)
  await page.locator('.ant-drawer.ant-drawer-open .ant-drawer-close').click()
  const deniedDefinition = await apiRequest(page, `/api/v1/systems/${system.id}/runtime/modules/${system.secondaryModuleCode}/definition`)
  expect(deniedDefinition.status).toBe(403)
  await page.locator('button[title="个人菜单"]').click()
  await expect(page.getByRole('menuitem', { name: /系统后台/ })).toHaveCount(0)
  await page.screenshot({ path: '../.cursor/session/evidence/vs3/ordinary-member-runtime-read-only.png' })
  await endSession(page)

  await login(page)
  await republishRuntimeRole(page, system.id, role.id, [
    'system.runtime.access',
    'system.workbench.view',
    'module.mobile_order.view',
    'module.mobile_order.create',
    'module.mobile_order.update',
    readPermission,
    writePermission,
  ])
  await endSession(page)

  await login(page, member)
  await page.goto(`/systems/${system.id}/workbench?module=mobile_order`)
  await expect(page.getByRole('heading', { name: system.name })).toBeVisible()
  const writableSchema = await apiData(page,
    `/api/v1/systems/${system.id}/runtime/modules/mobile_order/record-schema`)
  const writableField = (writableSchema.fields as Record<string, any>[])
    .find((field) => field.fieldCode === system.fieldCode)
  expect(writableField).toMatchObject({ fieldCode: system.fieldCode, writable: true })
  await expect(page.getByText('1 个可见字段', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: '新建记录', exact: true }).click()
  await expect(page.locator('#runtime-field-permission_note')).toBeVisible()
  await page.screenshot({ path: '../.cursor/session/evidence/vs3/ordinary-member-runtime-field-write.png' })
})

test('configuration layout honors the compact desktop boundary', async ({ page }) => {
  test.skip(test.info().project.name !== 'desktop', 'primary desktop project only')
  await registerOrdinaryMember(page)
  const systemId = new URL(page.url()).pathname.match(/^\/systems\/(\d+)\/workbench$/)?.[1]
  expect(systemId).toBeTruthy()
  await page.goto(`/systems/${systemId}/admin/configuration`)
  await expect(page.getByRole('heading', { name: '模块配置工作台' })).toBeVisible()

  for (const width of [1199, 1200, 1277, 1278]) {
    await page.setViewportSize({ width, height: 720 })
    const metrics = await page.evaluate(() => {
      const workbench = document.querySelector('.config-workbench')
      const panes = workbench
        ? Array.from(workbench.children)
          .filter((element) => getComputedStyle(element).display !== 'none')
          .map((element) => element.getBoundingClientRect())
        : []
      return {
        visiblePanes: panes.length,
        overlaps: panes.some((pane, index) => index > 0 && pane.left < panes[index - 1]!.right - 1),
        right: panes.at(-1)?.right ?? 0,
        documentWidth: document.documentElement.scrollWidth,
        viewportWidth: window.innerWidth,
      }
    })
    expect(metrics.visiblePanes).toBe(width < 1200 ? 2 : 3)
    expect(metrics.overlaps).toBe(false)
    expect(metrics.right).toBeLessThanOrEqual(metrics.viewportWidth + 1)
    expect(metrics.documentWidth).toBeLessThanOrEqual(metrics.viewportWidth + 1)
    if (width === 1200) {
      await page.screenshot({ path: '../.cursor/session/evidence/vs3/config-studio-boundary-1200.png' })
    }
  }
})

test('mobile configuration route stays read-only and has no horizontal overflow', async ({ page }) => {
  test.skip(test.info().project.name !== 'mobile', 'mobile-only')
  await login(page)
  const system = await createPublishedMobileSystem(page)
  await page.goto(`/systems/${system.id}/workbench?module=${system.secondaryModuleCode}`)
  await expect(page.getByRole('heading', { name: system.name })).toBeVisible()
  const selectors = page.locator('.runtime-mobile-selectors .ant-select')
  await expect(selectors).toHaveCount(2)
  await expect(selectors.nth(0)).toContainText(system.secondaryGroupName)
  await expect(selectors.nth(1)).toContainText(system.secondaryModuleName)
  await expect(page.getByText('当前状态下还没有记录')).toBeVisible()
  await page.goto(`/systems/${system.id}/admin/configuration`)
  await expect(page.getByText('移动端为只读模式，可查看检查结果和版本历史。')).toBeVisible()
  await expect(page.getByTitle('新建模块组')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '检查', exact: true })).toHaveCount(0)
  await expect(page.getByRole('button', { name: '发布', exact: true })).toHaveCount(0)
  const size = await page.evaluate(() => ({ document: document.documentElement.scrollWidth, viewport: window.innerWidth }))
  expect(size.document).toBeLessThanOrEqual(size.viewport + 1)
  await page.screenshot({ path: '../.cursor/session/evidence/vs3/config-studio-mobile.png' })
})
