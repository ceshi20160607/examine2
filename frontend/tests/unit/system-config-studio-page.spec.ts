import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SystemConfigStudioView from '@/views/system/admin/SystemConfigStudioView.vue'

const mocks = vi.hoisted(() => ({
  success: vi.fn(),
  confirm: vi.fn(),
  push: vi.fn(),
  route: { params: { systemId: '10' }, query: {} as Record<string, string> },
  api: {
    root: vi.fn(),
    groups: vi.fn(),
    modules: vi.fn(),
    dictionaries: vi.fn(),
    versions: vi.fn(),
    checkReport: vi.fn(),
    fields: vi.fn(),
    pages: vi.fn(),
    actions: vi.fn(),
    rules: vi.fn(),
    components: vi.fn(),
    createPage: vi.fn(),
    updatePage: vi.fn(),
    createField: vi.fn(),
    updateField: vi.fn(),
  },
}))

vi.mock('ant-design-vue', () => ({
  message: { success: mocks.success },
  Modal: { confirm: mocks.confirm },
}))
vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ push: mocks.push }),
}))
vi.mock('@/services/config', () => ({ configApi: mocks.api }))
vi.mock('@/services/admin', () => ({ systemAdminApi: { listMembers: vi.fn() } }))
vi.mock('@/stores/session', () => ({
  useSessionStore: () => ({ hasPermission: () => false, refreshContext: vi.fn(), tenants: [], context: null }),
}))
vi.mock('@/composables/useAdminViewport', () => ({ useAdminViewport: () => ({ isMobile: false }) }))

const passThrough = { template: '<div><slot /></div>' }
const buttonStub = {
  props: ['disabled', 'loading'],
  emits: ['click'],
  template: '<button :disabled="disabled || loading" @click="$emit(\'click\', $event)"><slot /></button>',
}
const selectStub = {
  props: ['value', 'disabled'],
  emits: ['update:value', 'change'],
  template: `<select :value="value" :disabled="disabled" @change="$emit('update:value', $event.target.value); $emit('change', $event.target.value)"><slot /></select>`,
}
const optionStub = { props: ['value'], template: '<option :value="value"><slot /></option>' }
const inputStub = {
  props: ['value'],
  emits: ['update:value'],
  template: `<input :value="value" @input="$emit('update:value', $event.target.value)" />`,
}
const modalStub = {
  props: ['open', 'title', 'confirmLoading'],
  emits: ['ok', 'cancel'],
  template: `<div v-if="open" class="test-modal"><h2>{{ title }}</h2><slot /><button class="modal-ok" :disabled="confirmLoading" @click="$emit('ok')">确认</button><button class="modal-cancel" @click="$emit('cancel')">取消</button></div>`,
}
const alertStub = { props: ['message'], template: '<div>{{ message }}<slot /></div>' }

const stubs = {
  AdminPageHeader: passThrough,
  MessageTemplateManager: passThrough,
  PrintTemplateManager: passThrough,
  RuleConditionNodeEditor: passThrough,
  'a-alert': alertStub,
  'a-button': buttonStub,
  'a-checkbox': { props: ['checked', 'disabled'], template: '<input type="checkbox" :checked="checked" :disabled="disabled" />' },
  'a-divider': passThrough,
  'a-drawer': { template: '<div />' },
  'a-empty': passThrough,
  'a-form': passThrough,
  'a-form-item': passThrough,
  'a-input': inputStub,
  'a-input-number': inputStub,
  'a-modal': modalStub,
  'a-select': selectStub,
  'a-select-option': optionStub,
  'a-spin': passThrough,
  'a-tab-pane': passThrough,
  'a-tabs': passThrough,
  'a-tag': passThrough,
  'a-textarea': inputStub,
}

const layout = {
  columns: 12,
  gap: 12,
  labelPosition: 'TOP',
  density: 'DEFAULT',
  stickyActions: true,
  pageSize: 20,
  showSearch: true,
  showFilters: true,
}

function page(id: string, type: 'LIST' | 'FORM' | 'DETAIL', isDefault: boolean) {
  return { id, code: `${type.toLowerCase()}_${id}`, name: `${type} page`, type, isDefault, status: 'ENABLED', layout, version: '1' }
}

async function render(
  initialPages: ReturnType<typeof page>[] = [],
  initialFields: Array<Record<string, any>> = [],
  rootOverride: Record<string, unknown> = {},
  initialModules: Array<Record<string, any>> = [{ id: 'module-1', groupId: 'group-1', code: 'tickets', name: '工单', status: 'ENABLED', version: '1' }],
) {
  let currentPages = [...initialPages]
  mocks.api.root.mockResolvedValue({ systemId: '10', status: 'DIRTY', draftRevision: '7', version: '3', lastCheck: null, ...rootOverride })
  mocks.api.groups.mockResolvedValue([{ id: 'group-1', code: 'core', name: '核心', status: 'ENABLED', version: '1' }])
  mocks.api.modules.mockResolvedValue(initialModules)
  mocks.api.dictionaries.mockResolvedValue([])
  mocks.api.versions.mockResolvedValue([])
  mocks.api.fields.mockResolvedValue(initialFields)
  mocks.api.pages.mockImplementation(async () => currentPages)
  mocks.api.actions.mockResolvedValue([])
  mocks.api.rules.mockResolvedValue([])
  mocks.api.components.mockResolvedValue([])
  mocks.api.createPage.mockImplementation(async (_systemId, _moduleId, body: Record<string, unknown>) => {
    const created = { id: 'page-new', version: '1', ...body }
    currentPages = [...currentPages, created as ReturnType<typeof page>]
    return created
  })
  mocks.api.updatePage.mockImplementation(async (_systemId, _moduleId, id, body: Record<string, unknown>) => ({ id, ...body }))
  mocks.api.createField.mockImplementation(async (_systemId, _moduleId, body: Record<string, unknown>) => ({ id: 'field-new', version: '1', ...body }))
  mocks.api.updateField.mockImplementation(async (_systemId, _moduleId, id, body: Record<string, unknown>) => ({ id, ...body }))

  const wrapper = mount(SystemConfigStudioView, { global: { stubs } })
  await flushPromises()
  await flushPromises()
  return wrapper
}

async function fillPageForm(wrapper: ReturnType<typeof mount>, type: 'LIST' | 'FORM' | 'DETAIL' = 'DETAIL') {
  await wrapper.get('input[aria-label="名称"]').setValue('详情页')
  await wrapper.get('input[aria-label="编码"]').setValue('detail')
  await wrapper.get('.config-page-type').setValue(type)
}

describe('SystemConfigStudioView page creation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.route.query = {}
  })

  it('keeps creation reachable with zero pages, derives a default, reloads and selects the empty page', async () => {
    const wrapper = await render([])

    expect(wrapper.find('.config-page-create').exists()).toBe(true)
    await wrapper.get('.config-page-create').trigger('click')
    expect(wrapper.get('[aria-label="页面类型"]').attributes('disabled')).toBeUndefined()
    expect((wrapper.get('[aria-label="页面类型"]').element as HTMLSelectElement).value).toBe('LIST')

    await fillPageForm(wrapper, 'DETAIL')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(mocks.api.createPage).toHaveBeenCalledWith('10', 'module-1', {
      code: 'detail',
      name: '详情页',
      type: 'DETAIL',
      isDefault: true,
      status: 'ENABLED',
      layout,
      draftRevision: '7',
    })
    expect((wrapper.get('.config-page-select').element as HTMLSelectElement).value).toBe('page-new')
    expect(mocks.api.components).toHaveBeenLastCalledWith('10', 'module-1', 'page-new')
    expect(wrapper.find('.test-modal').exists()).toBe(false)
  })

  it('derives default from the submitted type and keeps edit on PUT with a disabled type control', async () => {
    const wrapper = await render([page('page-list', 'LIST', true)])

    await wrapper.get('.config-page-create').trigger('click')
    await fillPageForm(wrapper, 'LIST')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(mocks.api.createPage.mock.calls[0]?.[2]).toMatchObject({ type: 'LIST', isDefault: false })

    await wrapper.get('button[title="编辑页面"]').trigger('click')
    expect(wrapper.get('[aria-label="页面类型"]').attributes('disabled')).toBeDefined()
    await wrapper.get('input[aria-label="名称"]').setValue('更新后的列表')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(mocks.api.updatePage).toHaveBeenCalledOnce()
    expect(mocks.api.updatePage).toHaveBeenCalledWith('10', 'module-1', 'page-new', expect.objectContaining({
      name: '更新后的列表', type: 'LIST', isDefault: false, version: '1', draftRevision: '7',
    }))
    expect(mocks.api.createPage).toHaveBeenCalledOnce()
  })

  it('keeps the create modal open and reports the existing API error when creation fails', async () => {
    const wrapper = await render([])
    mocks.api.createPage.mockRejectedValueOnce(new Error('network'))

    await wrapper.get('.config-page-create').trigger('click')
    await fillPageForm(wrapper)
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(wrapper.find('.test-modal').exists()).toBe(true)
    expect(wrapper.get('.admin-alert').text()).toContain('操作失败，请稍后重试')
  })

  it('authors canonical LIST scenarios and submits the selected default through page layout only', async () => {
    const wrapper = await render([])
    await wrapper.get('.config-page-create').trigger('click')
    await fillPageForm(wrapper, 'LIST')
    await wrapper.get('.config-filter-scenario-add').trigger('click')
    await wrapper.get('[aria-label="共享筛选方案编码"]').setValue('urgent_first')
    await wrapper.get('[aria-label="共享筛选方案名称"]').setValue('紧急优先')
    await wrapper.get('[aria-label="共享筛选方案筛选 JSON"]').setValue('{"kind":"PREDICATE","fieldCode":"priority","operator":"EQ","value":"HIGH"}')
    await wrapper.get('[aria-label="共享筛选方案排序 JSON"]').setValue('[{"fieldCode":"created_at","direction":"DESC","nulls":"LAST"}]')
    await wrapper.get('[aria-label="默认共享筛选方案"]').setValue('urgent_first')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(mocks.api.createPage.mock.calls[0]?.[2]).toMatchObject({
      type: 'LIST',
      layout: {
        ...layout,
        filterScenarios: [{
          code: 'urgent_first',
          name: '紧急优先',
          filter: { kind: 'PREDICATE', fieldCode: 'priority', operator: 'EQ', value: 'HIGH' },
          sort: [{ fieldCode: 'created_at', direction: 'DESC', nulls: 'LAST' }],
        }],
        defaultFilterScenarioCode: 'urgent_first',
      },
    })
  })

  it('retains authored values on client validation failure and strips scenario keys from non-LIST pages', async () => {
    const wrapper = await render([])
    await wrapper.get('.config-page-create').trigger('click')
    await fillPageForm(wrapper, 'LIST')
    await wrapper.get('.config-filter-scenario-add').trigger('click')
    await wrapper.get('[aria-label="共享筛选方案编码"]').setValue('broken')
    await wrapper.get('[aria-label="共享筛选方案名称"]').setValue('错误方案')
    await wrapper.get('[aria-label="共享筛选方案筛选 JSON"]').setValue('{bad')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(mocks.api.createPage).not.toHaveBeenCalled()
    expect(wrapper.find('.test-modal').exists()).toBe(true)
    expect(wrapper.get('[aria-label="共享筛选方案筛选 JSON"]').element.getAttribute('value')).toBe('{bad')
    expect(wrapper.get('.admin-alert').text()).toContain('不是有效的 JSON')

    await wrapper.get('[aria-label="页面类型"]').setValue('FORM')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()
    const submittedLayout = mocks.api.createPage.mock.calls[0]?.[2].layout as Record<string, unknown>
    expect(submittedLayout).not.toHaveProperty('filterScenarios')
    expect(submittedLayout).not.toHaveProperty('defaultFilterScenarioCode')
  })

  it('initializes LIST edit from canonical scenarios and retains unrelated layout keys', async () => {
    const existing = {
      ...page('page-list', 'LIST', true),
      layout: {
        ...layout,
        futureLayoutKey: { enabled: true },
        filterScenarios: [{
          code: 'urgent_first',
          name: '紧急优先',
          filter: { kind: 'PREDICATE', fieldCode: 'priority', operator: 'EQ', value: 'HIGH' },
          sort: [],
        }],
        defaultFilterScenarioCode: 'urgent_first',
      },
    }
    const wrapper = await render([existing])
    await wrapper.get('button[title="编辑页面"]').trigger('click')

    expect((wrapper.get('[aria-label="共享筛选方案编码"]').element as HTMLInputElement).value).toBe('urgent_first')
    expect((wrapper.get('[aria-label="默认共享筛选方案"]').element as HTMLSelectElement).value).toBe('urgent_first')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(mocks.api.updatePage.mock.calls[0]?.[3].layout).toMatchObject({
      ...layout,
      futureLayoutKey: { enabled: true },
      filterScenarios: [expect.objectContaining({ code: 'urgent_first', name: '紧急优先' })],
      defaultFilterScenarioCode: 'urgent_first',
    })
  })

  it('authors three-state field permissions with exact canonical code previews and defaults', async () => {
    const wrapper = await render()
    await wrapper.get('.config-field-create').trigger('click')

    expect((wrapper.get('.field-read-permission-mode').element as HTMLSelectElement).value).toBe('INHERIT')
    expect((wrapper.get('.field-write-permission-mode').element as HTMLSelectElement).value).toBe('INHERIT')
    await wrapper.get('input[aria-label="名称"]').setValue('内部备注')
    await wrapper.get('input[aria-label="编码"]').setValue('secret_note')
    await wrapper.get('.field-read-permission-mode').setValue('STAGED')
    await wrapper.get('.field-write-permission-mode').setValue('ENFORCED')

    expect(wrapper.get('.field-read-permission-code').text()).toBe('module.tickets.field.secret_note.read')
    expect(wrapper.get('.field-write-permission-code').text()).toBe('module.tickets.field.secret_note.write')
    expect(wrapper.get('.field-permission-lifecycle-notice').text()).toContain('STAGED → 角色授权 → ENFORCED')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(mocks.api.createField).toHaveBeenCalledWith('10', 'module-1', expect.objectContaining({
      code: 'secret_note',
      readPermissionMode: 'STAGED',
      writePermissionMode: 'ENFORCED',
      draftRevision: '7',
    }))
  })

  it('round-trips modes and forces readonly field write mode to INHERIT without losing authored read mode', async () => {
    const readonlyField = {
      id: 'field-1', moduleId: 'module-1', code: 'audit_note', name: '审计备注', type: 'TEXT',
      sortOrder: 0, required: false, hidden: false, readonly: true, searchable: true, filterable: true,
      showInList: true, showInDetail: true, indexMode: 'FILTER', status: 'ENABLED', properties: {},
      readPermissionMode: 'STAGED', writePermissionMode: 'ENFORCED', version: '4', updatedRevision: '7',
    }
    const wrapper = await render([], [readonlyField])
    await wrapper.get('button[title="编辑字段"]').trigger('click')
    await flushPromises()

    expect((wrapper.get('.field-read-permission-mode').element as HTMLSelectElement).value).toBe('STAGED')
    expect((wrapper.get('.field-write-permission-mode').element as HTMLSelectElement).value).toBe('INHERIT')
    expect(wrapper.get('.field-write-permission-mode').attributes('disabled')).toBeDefined()
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(mocks.api.updateField).toHaveBeenCalledWith('10', 'module-1', 'field-1', expect.objectContaining({
      readPermissionMode: 'STAGED', writePermissionMode: 'INHERIT', version: '4',
    }))
  })

  it('keeps failed field permission authorship and exposes a stable role shortcut only for published STAGED fields', async () => {
    const wrapper = await render()
    mocks.api.createField.mockRejectedValueOnce(new Error('network'))
    await wrapper.get('.config-field-create').trigger('click')
    await wrapper.get('input[aria-label="名称"]').setValue('保密字段')
    await wrapper.get('input[aria-label="编码"]').setValue('private_data')
    await wrapper.get('.field-read-permission-mode').setValue('STAGED')
    await wrapper.get('.modal-ok').trigger('click')
    await flushPromises()

    expect(wrapper.find('.test-modal').exists()).toBe(true)
    expect((wrapper.get('.field-read-permission-mode').element as HTMLSelectElement).value).toBe('STAGED')
    expect(wrapper.get('.field-read-permission-code').text()).toBe('module.tickets.field.private_data.read')

    wrapper.unmount()
    const published = await render([], [{
      id: 'field-2', moduleId: 'module-1', code: 'private_data', name: '保密字段', type: 'TEXT',
      sortOrder: 0, required: false, hidden: false, readonly: false, searchable: true, filterable: true,
      showInList: true, showInDetail: true, indexMode: 'FILTER', status: 'ENABLED', properties: {},
      readPermissionMode: 'STAGED', writePermissionMode: 'INHERIT', version: '1', updatedRevision: '8',
    }], { status: 'CLEAN', draftRevision: '8' })

    expect(published.get('.config-staged-role-path').attributes('data-permission-prefix'))
      .toBe('module.tickets.field.private_data.')
    await published.get('.config-open-role-grants').trigger('click')
    expect(mocks.push).toHaveBeenCalledWith({
      name: 'system-admin-roles', params: { systemId: '10' },
      query: { permissionPrefix: 'module.tickets.field.private_data.' },
    })
  })

  it('uses query identifiers to load fresh owner fields, select the module/tab/field, and never mutate', async () => {
    mocks.route.query = { moduleCode: 'orders', resourceKind: 'field', resourceCode: 'secret_note' }
    const modules = [
      { id: 'module-tickets', groupId: 'group-1', code: 'tickets', name: '工单', status: 'ENABLED', version: '1' },
      { id: 'module-orders', groupId: 'group-1', code: 'orders', name: '订单', status: 'ENABLED', version: '1' },
    ]
    const targetField = {
      id: 'field-secret-note', moduleId: 'module-orders', code: 'secret_note', name: '保密备注', type: 'TEXT',
      sortOrder: 0, required: false, hidden: false, readonly: false, searchable: true, filterable: true,
      showInList: true, showInDetail: true, indexMode: 'FILTER', status: 'ENABLED', properties: {},
      readPermissionMode: 'STAGED', writePermissionMode: 'INHERIT', version: '6', updatedRevision: '9',
    }
    const wrapper = await render([], [targetField], {}, modules)

    expect(mocks.api.fields).toHaveBeenCalledWith('10', 'module-orders')
    expect(mocks.api.pages).toHaveBeenCalledWith('10', 'module-orders')
    expect(wrapper.get('.module-item.active').text()).toContain('orders')
    const notice = wrapper.get('.config-agent-focus-notice')
    expect(notice.attributes()).toMatchObject({
      'data-module-code': 'orders', 'data-resource-kind': 'field', 'data-resource-code': 'secret_note',
    })
    expect(notice.text()).toContain('已从最新配置定位 字段：secret_note')
    expect(wrapper.get('.resource-row[data-resource-kind="field"][data-resource-code="secret_note"]').classes()).toContain('selected')
    expect(mocks.api.createField).not.toHaveBeenCalled()
    expect(mocks.api.updateField).not.toHaveBeenCalled()
  })

  it('uses query identifiers to select a fresh owner page without injecting proposal state or mutating', async () => {
    mocks.route.query = { moduleCode: 'orders', resourceKind: 'page', resourceCode: 'list_page-orders' }
    const modules = [
      { id: 'module-tickets', groupId: 'group-1', code: 'tickets', name: '工单', status: 'ENABLED', version: '1' },
      { id: 'module-orders', groupId: 'group-1', code: 'orders', name: '订单', status: 'ENABLED', version: '1' },
    ]
    const targetPage = page('page-orders', 'LIST', true)
    const wrapper = await render([targetPage], [], {}, modules)

    expect(mocks.api.pages).toHaveBeenCalledWith('10', 'module-orders')
    expect((wrapper.get('.config-page-select').element as HTMLSelectElement).value).toBe('page-orders')
    expect(wrapper.get('.config-page-select').attributes('data-resource-code')).toBe('list_page-orders')
    const notice = wrapper.get('.config-agent-focus-notice')
    expect(notice.attributes('data-resource-kind')).toBe('page')
    expect(notice.text()).toContain('已从最新配置定位 页面：list_page-orders')
    expect(wrapper.get('.page-canvas').attributes('data-resource-code')).toBe('list_page-orders')
    expect(mocks.api.createPage).not.toHaveBeenCalled()
    expect(mocks.api.updatePage).not.toHaveBeenCalled()
  })
})
