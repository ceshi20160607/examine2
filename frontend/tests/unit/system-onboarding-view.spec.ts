import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { systemAdminApi } from '@/services/admin'
import { configApi } from '@/services/config'
import SystemOnboardingView from '@/views/system/admin/SystemOnboardingView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
const push = vi.hoisted(() => vi.fn())

vi.mock('vue-router', () => ({
  useRoute: () => route,
  useRouter: () => ({ push }),
}))

vi.mock('@/services/admin', () => ({
  systemAdminApi: { listMembers: vi.fn(), listTenants: vi.fn() },
}))

vi.mock('@/services/config', () => ({
  configApi: {
    root: vi.fn(), groups: vi.fn(), modules: vi.fn(), fields: vi.fn(), pages: vi.fn(),
    preview: vi.fn(), check: vi.fn(),
  },
}))

function render() {
  return mount(SystemOnboardingView, {
    global: { stubs: {
      CheckCircle2: true, CircleAlert: true, Eye: true, RefreshCw: true, Rocket: true, ShieldCheck: true,
      'a-alert': { props: ['message'], template: '<div class="alert">{{ message }}</div>' },
      'a-button': { inheritAttrs: false, props: ['loading'], emits: ['click'], template: '<button v-bind="$attrs" @click="$emit(\'click\')"><slot /></button>' },
      'a-tag': { template: '<span><slot /></span>' },
    } },
  })
}

describe('SystemOnboardingView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(configApi.root).mockResolvedValue({
      systemId: '10', status: 'DIRTY', draftRevision: '7', activeVersionId: '70', version: '3',
    })
    vi.mocked(configApi.groups).mockResolvedValue([
      { id: '20', code: 'business', name: '业务', sortOrder: 0, status: 'ENABLED', version: '1', updatedRevision: '4' },
    ])
    vi.mocked(configApi.modules).mockResolvedValue([
      { id: '30', groupId: '20', code: 'pump', name: '泵站', sortOrder: 0, status: 'ENABLED', allowComments: true, allowTeam: true, version: '1', updatedRevision: '5' },
    ])
    vi.mocked(configApi.fields).mockResolvedValue([
      { id: '40', moduleId: '30', code: 'name', name: '名称', type: 'TEXT', sortOrder: 0, required: true, hidden: false, readonly: false, searchable: true, filterable: true, showInList: true, showInDetail: true, indexMode: 'NORMAL', status: 'ENABLED', readPermissionMode: 'INHERIT', writePermissionMode: 'INHERIT', properties: {}, version: '1', updatedRevision: '6' },
    ])
    vi.mocked(configApi.pages).mockResolvedValue(['LIST', 'FORM', 'DETAIL'].map((type, index) => ({
      id: String(50 + index), moduleId: '30', code: type.toLocaleLowerCase(), name: type,
      type: type as 'LIST' | 'FORM' | 'DETAIL', isDefault: true, status: 'ENABLED' as const,
      layout: {}, version: '1', updatedRevision: '7',
    })))
    vi.mocked(systemAdminApi.listMembers).mockResolvedValue({
      items: [{ id: '60', accountId: '6', displayName: '普通成员', status: 'ACTIVE', departmentIds: [], tenantIds: ['80'], roleIds: ['90'], version: '1' }],
      page: 1, size: 100, total: 1,
    })
    vi.mocked(systemAdminApi.listTenants).mockResolvedValue({
      items: [{ id: '80', systemId: '10', code: 'default', name: '默认租户', status: 'ACTIVE', isDefault: true, memberCount: 1, version: '1' }],
      page: 1, size: 100, total: 1,
    })
    vi.mocked(configApi.preview).mockResolvedValue({
      memberId: '60', tenantId: '80', permissionVersion: '2', root: false,
      sourceRoles: [{ id: '90', code: 'member', name: '普通成员', publishedVersion: '1' }],
      dataScopes: [], active: { versionId: '70', groups: [] },
      draft: { revision: '7', groups: [{ id: '20', code: 'business', name: '业务', sortOrder: 0, modules: [{ id: '30', code: 'pump', name: '泵站', sortOrder: 0, fields: ['name'], actions: [] }] }] },
    })
    vi.mocked(configApi.check).mockResolvedValue({
      id: '100', status: 'PASSED', blockerCount: 0, warningCount: 1, draftRevision: '7', version: '1',
      draftChecksum: 'a'.repeat(64), snapshotSizeBytes: 1024, expiresAt: '2026-08-08T00:00:00Z', issues: [],
    })
  })

  it('runs minimum configuration, ordinary-member preview and publication check in one visible result', async () => {
    const wrapper = render()
    await wrapper.get('.onboarding-run').trigger('click')
    await flushPromises()

    expect(configApi.fields).toHaveBeenCalledWith('10', '30')
    expect(configApi.pages).toHaveBeenCalledWith('10', '30')
    expect(configApi.preview).toHaveBeenCalledWith('10', '60', '80')
    expect(configApi.check).toHaveBeenCalledWith('10', '7')
    expect(wrapper.get('.onboarding-summary').attributes('data-state')).toBe('ready')
    expect(wrapper.text()).toContain('系统已具备可发布的最小业务结构')
    expect(wrapper.text()).toContain('普通成员 在草稿中可见 1 个模块')
    expect(wrapper.text()).toContain('修订 7 可以发布')

    await wrapper.get('.onboarding-preview-runtime').trigger('click')
    expect(push).toHaveBeenCalledWith({ path: '/systems/10/workbench', query: { module: 'pump' } })
  })

  it('reports missing member preview and server blockers without claiming readiness', async () => {
    vi.mocked(systemAdminApi.listMembers).mockResolvedValueOnce({ items: [], page: 1, size: 100, total: 0 })
    vi.mocked(configApi.check).mockResolvedValueOnce({
      id: '101', status: 'FAILED', blockerCount: 1, warningCount: 0, draftRevision: '7', version: '1',
      draftChecksum: 'b'.repeat(64), snapshotSizeBytes: 800, expiresAt: '2026-08-08T00:00:00Z',
      issues: [{ id: '1', severity: 'BLOCKER', code: 'PAGE_MISSING', message: '缺少默认页面' }],
    })
    const wrapper = render()
    await wrapper.get('.onboarding-run').trigger('click')
    await flushPromises()

    expect(wrapper.get('.onboarding-summary').attributes('data-state')).toBe('blocked')
    expect(wrapper.text()).toContain('没有可用于验收的启用普通成员')
    expect(wrapper.text()).toContain('PAGE_MISSING：缺少默认页面')
    expect(wrapper.find('.onboarding-preview-runtime').exists()).toBe(false)
  })
})
