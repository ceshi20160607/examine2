import { computed, defineComponent, inject, provide, type PropType } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SystemRolesView from '@/views/system/admin/SystemRolesView.vue'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  success: vi.fn(),
  refreshContext: vi.fn(),
  route: {
    params: { systemId: '10' },
    query: { permissionPrefix: 'module.tickets.field.secret_note.' } as Record<string, string>,
  },
  api: {
    listRoles: vi.fn(),
    listPermissions: vi.fn(),
    listDataScopes: vi.fn(),
    listMembers: vi.fn(),
    getPermissionEvaluation: vi.fn(),
    saveRoleDraft: vi.fn(),
    checkRoleDraft: vi.fn(),
    publishRoleDraft: vi.fn(),
    createRole: vi.fn(),
  },
}))

vi.mock('ant-design-vue', () => ({ message: { success: mocks.success } }))
vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ push: mocks.push }),
}))
vi.mock('@/services/admin', () => ({ systemAdminApi: mocks.api }))
vi.mock('@/stores/session', () => ({
  useSessionStore: () => ({
    tenants: [], context: { tenantId: 'tenant-1' }, refreshContext: mocks.refreshContext,
  }),
}))
vi.mock('@/composables/useAdminViewport', () => ({ useAdminViewport: () => ({ isMobile: false }) }))

const passThrough = { template: '<div><slot /><slot name="actions" /></div>' }
const buttonStub = {
  props: ['disabled', 'loading'],
  emits: ['click'],
  template: '<button :disabled="disabled || loading" @click="$emit(\'click\', $event)"><slot /></button>',
}
const inputStub = {
  props: ['value'],
  emits: ['update:value'],
  template: '<input :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
}
const selectStub = {
  props: ['value', 'disabled'],
  emits: ['update:value'],
  template: '<select :value="value" :disabled="disabled" @change="$emit(\'update:value\', $event.target.value)"><slot /></select>',
}

interface TestCheckboxGroup {
  contains: (value: string) => boolean
  toggle: (value: string, checked: boolean) => void
}

const checkboxGroupStub = defineComponent({
  props: { value: { type: Array as PropType<string[]>, default: () => [] } },
  emits: ['update:value'],
  setup(props, { emit, slots }) {
    const group: TestCheckboxGroup = {
      contains: (value) => props.value.includes(value),
      toggle: (value, checked) => {
        const next = checked
          ? [...new Set([...props.value, value])]
          : props.value.filter((item) => item !== value)
        emit('update:value', next)
      },
    }
    provide('test-checkbox-group', group)
    return () => slots.default?.()
  },
})

const checkboxStub = defineComponent({
  inheritAttrs: false,
  props: { value: { type: String, required: true } },
  setup(props, { attrs, slots }) {
    const group = inject<TestCheckboxGroup>('test-checkbox-group')!
    const checked = computed(() => group.contains(props.value))
    return { attrs, checked, group, slots }
  },
  template: '<label v-bind="attrs"><input type="checkbox" :checked="checked" @change="group.toggle(value, $event.target.checked)" /><slot /></label>',
})

const alertStub = {
  props: ['message', 'description'],
  template: '<div><strong>{{ message }}</strong><span>{{ description }}</span><slot /></div>',
}

const stubs = {
  AdminPageHeader: passThrough,
  AdminMobileNotice: passThrough,
  'a-alert': alertStub,
  'a-button': buttonStub,
  'a-checkbox': checkboxStub,
  'a-checkbox-group': checkboxGroupStub,
  'a-descriptions': passThrough,
  'a-descriptions-item': passThrough,
  'a-drawer': passThrough,
  'a-empty': passThrough,
  'a-form': passThrough,
  'a-form-item': passThrough,
  'a-input': inputStub,
  'a-modal': passThrough,
  'a-select': selectStub,
  'a-spin': passThrough,
  'a-tag': passThrough,
  'a-textarea': inputStub,
}

const readCode = 'module.tickets.field.secret_note.read'
const writeCode = 'module.tickets.field.secret_note.write'
const moduleCode = 'module.tickets.view'
const inactiveCode = 'module.tickets.field.retired.read'

function role(overrides: Record<string, unknown> = {}) {
  return {
    id: 'role-1', code: 'operators', name: '操作员', description: '', status: 'ACTIVE', builtin: false,
    memberCount: 2, permissionCodes: [readCode, moduleCode, inactiveCode], deniedPermissionCodes: [],
    draftStatus: 'DRAFT', publishedVersion: '3', version: '7', ...overrides,
  }
}

const permissions = [
  { id: 'permission-read', code: readCode, name: '读取保密字段', type: 'FIELD' },
  { id: 'permission-write', code: writeCode, name: '写入保密字段', type: 'FIELD' },
  { id: 'permission-module', code: moduleCode, name: '查看工单', type: 'MENU' },
]

async function render(roleValue = role()) {
  mocks.api.listRoles.mockResolvedValue({ items: [roleValue], page: 1, size: 200, total: 1 })
  mocks.api.listPermissions.mockResolvedValue({ items: permissions, page: 1, size: 500, total: permissions.length })
  mocks.api.listDataScopes.mockResolvedValue({ items: [], page: 1, size: 200, total: 0 })
  mocks.api.saveRoleDraft.mockImplementation(async (_systemId, _roleId, input) => role({
    permissionCodes: input.permissionCodes,
    deniedPermissionCodes: input.deniedPermissionCodes,
    version: '8',
  }))
  mocks.api.checkRoleDraft.mockResolvedValue(role({ draftStatus: 'CHECKED', version: '8' }))
  mocks.api.publishRoleDraft.mockResolvedValue(role({ draftStatus: 'PUBLISHED', publishedVersion: '4', version: '9' }))
  const wrapper = mount(SystemRolesView, { global: { stubs } })
  await flushPromises()
  return wrapper
}

describe('SystemRolesView field permission grant path', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.route.query = { permissionPrefix: 'module.tickets.field.secret_note.' }
  })

  it('loads ACTIVE definitions, focuses the requested prefix and drops inactive role links on save', async () => {
    const wrapper = await render()

    expect(mocks.api.listPermissions).toHaveBeenCalledWith('10', { page: 1, size: 500, status: 'ACTIVE' })
    expect((wrapper.get('.role-permission-search').element as HTMLInputElement).value)
      .toBe('module.tickets.field.secret_note.')
    expect(wrapper.findAll('.role-permission-row[data-focused="true"]')).toHaveLength(4)
    expect(wrapper.get('.role-permission-focus-notice').text()).toContain('已定位 2 项字段权限')

    await wrapper.get('.role-draft-save').trigger('click')
    await flushPromises()
    expect(mocks.api.saveRoleDraft).toHaveBeenCalledWith('10', 'role-1', expect.objectContaining({
      permissionCodes: [readCode, moduleCode],
      deniedPermissionCodes: [],
      version: '7',
    }))
  })

  it('keeps allow and deny mutually exclusive while preserving unrelated ACTIVE grants', async () => {
    const wrapper = await render()
    await wrapper.get(`.role-permission-deny[data-permission-code="${readCode}"] input`).setValue(true)
    await flushPromises()
    await wrapper.get('.role-draft-save').trigger('click')
    await flushPromises()

    expect(mocks.api.saveRoleDraft).toHaveBeenCalledWith('10', 'role-1', expect.objectContaining({
      permissionCodes: [moduleCode],
      deniedPermissionCodes: [readCode],
    }))
  })

  it('refreshes session after publish and exposes exact configuration and runtime paths', async () => {
    const wrapper = await render(role({ draftStatus: 'CHECKED' }))
    await wrapper.get('.role-draft-publish').trigger('click')
    await flushPromises()

    expect(mocks.api.publishRoleDraft).toHaveBeenCalledWith('10', 'role-1', '7')
    expect(mocks.refreshContext).toHaveBeenCalledOnce()
    expect(wrapper.get('.role-publish-next-steps').text()).toContain('STAGED 改为 ENFORCED')

    await wrapper.get('.role-return-configuration').trigger('click')
    await wrapper.get('.role-open-runtime').trigger('click')
    expect(mocks.push).toHaveBeenNthCalledWith(1, {
      name: 'system-admin-configuration', params: { systemId: '10' },
      query: { permissionPrefix: 'module.tickets.field.secret_note.' },
    })
    expect(mocks.push).toHaveBeenNthCalledWith(2, {
      name: 'system-workbench', params: { systemId: '10' }, query: { module: 'tickets' },
    })
  })
})
