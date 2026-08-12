import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import SystemCommandCenter from '@/components/SystemCommandCenter.vue'
import { runtimeApi } from '@/services/config'
import { globalSearchApi } from '@/services/globalSearch'
import { todoApi } from '@/services/todo'
import { useSessionStore } from '@/stores/session'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
const push = vi.hoisted(() => vi.fn(() => Promise.resolve()))

vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => ({ push }) }))
vi.mock('@/services/config', () => ({ runtimeApi: { navigation: vi.fn() } }))
vi.mock('@/services/globalSearch', () => ({ globalSearchApi: { search: vi.fn() } }))
vi.mock('@/services/todo', () => ({ todoApi: { list: vi.fn() } }))

function applySession() {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: {
      type: 'SYSTEM', account: { id: '1', username: 'member', displayName: 'Member' },
      systemId: '10', systemName: '泵站系统', tenantId: '20', memberId: '30', permissionVersion: '1',
      roleIds: ['role-1'], dataScope: { id: 'scope-1', code: 'all_data', kind: 'ALL' }, restrictedMode: 'NONE',
      permissions: ['module.pump.create', 'event.message.access'], shells: ['SYSTEM_RUNTIME', 'SYSTEM_ADMIN'],
    },
    systems: [
      { id: '10', code: 'pump_ops', name: '泵站系统', status: 'ACTIVE', memberStatus: 'ACTIVE', defaultTenantId: '20', roleNames: [], recentEnteredAt: null },
      { id: '11', code: 'asset_ops', name: '资产系统', status: 'ACTIVE', memberStatus: 'ACTIVE', defaultTenantId: '21', roleNames: [], recentEnteredAt: null },
    ],
    tenants: [],
    firstSystemId: null,
  })
}

function render() {
  return mount(SystemCommandCenter, {
    props: { open: true },
    global: { stubs: {
      Blocks: true, CheckSquare: true, FilePlus2: true, LayoutGrid: true, Menu: true, Search: true, Server: true, Zap: true,
      'a-alert': { props: ['message'], template: '<div class="alert">{{ message }}</div>' },
      'a-modal': { props: ['open'], emits: ['cancel'], template: '<div v-if="open" class="modal"><slot /></div>' },
    } },
  })
}

describe('SystemCommandCenter', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    applySession()
    vi.mocked(runtimeApi.navigation).mockResolvedValue({
      activeVersionId: '40', versionNo: '3',
      groups: [{ id: '50', code: 'business', name: '业务模块', sortOrder: 0, modules: [
        { id: '60', code: 'pump', name: '泵站工单', sortOrder: 0, permissionCode: 'module.pump.view' },
      ] }],
    })
    vi.mocked(todoApi.list).mockResolvedValue({
      items: [{
        id: '70', systemId: '10', tenantId: '20', recipientMemberId: '30', sourceType: 'WORK_TASK', sourceId: '71', sourceVersion: 1,
        actionScope: 'SELF', category: 'TASK', title: '检查泵站', dueAt: null, priority: 1,
        routeHint: '/systems/10/tasks?task=71', representedMemberId: null, availableActions: ['COMPLETE'],
        status: 'OPEN', closeReason: null, createdAt: '2026-08-07T00:00:00Z', updatedAt: '2026-08-07T00:00:00Z', closedAt: null, version: 1,
      }], page: 1, size: 100, total: 1,
    })
    vi.mocked(globalSearchApi.search).mockResolvedValue({
      items: [{ moduleCode: 'pump', moduleName: '泵站工单', recordId: '80', recordNo: 'R-80', displayLabel: '东区泵站', status: 'ACTIVE', matchedFieldCodes: ['name'] }],
      page: 1, size: 20, total: 1,
    })
  })

  it('aggregates menu, system, module, todo and quick actions, then adds real record search', async () => {
    const wrapper = render()
    await flushPromises()

    expect(runtimeApi.navigation).toHaveBeenCalledWith('10')
    expect(todoApi.list).toHaveBeenCalledWith('10', { state: 'OPEN', page: 1, size: 100 })
    expect(wrapper.find('[data-kind="MENU"]').exists()).toBe(true)
    expect(wrapper.find('[data-kind="SYSTEM"]').exists()).toBe(true)
    expect(wrapper.find('[data-kind="MODULE"]').exists()).toBe(true)
    expect(wrapper.find('[data-kind="TODO"]').exists()).toBe(true)
    expect(wrapper.find('[data-kind="ACTION"]').exists()).toBe(true)

    await wrapper.get('.command-search input').setValue('泵站')
    await wrapper.get('.command-search').trigger('submit')
    await flushPromises()

    expect(globalSearchApi.search).toHaveBeenCalledWith('10', { q: '泵站', page: 1, size: 20 })
    expect(wrapper.find('[data-kind="RECORD"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('东区泵站')
    const record = wrapper.findAll('.command-result').find(item => item.text().includes('东区泵站'))
    await record!.trigger('click')
    expect(push).toHaveBeenCalledWith({
      path: '/systems/10/workbench', query: { module: 'pump', mode: 'view', record: '80' },
    })
    expect(wrapper.emitted('update:open')?.at(-1)).toEqual([false])
  })

  it('keeps local command categories usable when record search is too short', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.command-search input').setValue('泵')
    await wrapper.get('.command-search').trigger('submit')
    await flushPromises()

    expect(globalSearchApi.search).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请输入至少 2 个字符')
    expect(wrapper.text()).toContain('检查泵站')
    expect(wrapper.text()).toContain('新建泵站工单')
  })
})
