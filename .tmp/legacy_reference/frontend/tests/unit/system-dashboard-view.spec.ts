import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { runtimeDashboardApi } from '@/services/dashboard'
import { useSessionStore } from '@/stores/session'
import type { RuntimeDashboard } from '@/types/dashboard'
import SystemDashboardView from '@/views/system/SystemDashboardView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('@/services/dashboard', () => ({ runtimeDashboardApi: { systemHome: vi.fn() } }))

const dashboard: RuntimeDashboard = {
  id: '20', code: 'system_home', name: '业务首页', description: '实时概览', placement: 'SYSTEM_HOME', versionId: '21', versionNumber: 1,
  widgets: [
    { code: 'count', type: 'STAT_COUNT', title: '订单数', ordinal: 0, grid: { x: 0, y: 0, width: 3, height: 2 }, dataSourceId: '30', dataSourceVersionId: '31', dataSourceVersionNumber: 1, dataSourceCode: 'orders', rowLimit: null, status: 'OK', error: null, total: 8, fields: [], rows: [], kpiTargets: [] },
    { code: 'list', type: 'DATA_LIST', title: '订单列表', ordinal: 1, grid: { x: 3, y: 0, width: 6, height: 5 }, dataSourceId: '30', dataSourceVersionId: '31', dataSourceVersionNumber: 1, dataSourceCode: 'orders', rowLimit: 5, status: 'OK', error: null, total: 1, fields: [{ fieldCode: 'title', fieldName: '标题', type: 'TEXT' }], rows: [{ recordId: '40', recordNo: 'R1', version: 1, status: 'ACTIVE', values: { title: '采购单' } }], kpiTargets: [] },
    { code: 'failed', type: 'STAT_COUNT', title: '受限统计', ordinal: 2, grid: { x: 9, y: 0, width: 3, height: 2 }, dataSourceId: '32', dataSourceVersionId: '33', dataSourceVersionNumber: 1, dataSourceCode: 'private', rowLimit: null, status: 'ERROR', error: { code: 'SOURCE_FORBIDDEN', message: '无权读取' }, total: null, fields: [], rows: [], kpiTargets: [] },
  ],
}

function applySession(tenantId = '20') {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: { type: 'SYSTEM', account: { id: '1', username: 'member', displayName: 'Member' }, systemId: '10', tenantId, memberId: '30', permissionVersion: '1', permissions: [], shells: ['SYSTEM_RUNTIME'] , roleIds: ['legacy-role-1'], dataScope: { id: 'legacy-scope-1', code: 'legacy_all', kind: 'ALL' }, restrictedMode: 'NONE'},
    systems: [],

    tenants: [],
    firstSystemId: null,
  })
}

function render() {
  return mount(SystemDashboardView, { global: { stubs: {
    'a-button': { props: ['loading'], emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
    'a-alert': { props: ['message', 'description'], template: '<div class="alert-stub">{{ message }} {{ description }}</div>' },
    'a-spin': { template: '<span class="spin-stub" />' },
  } } })
}

describe('SystemDashboardView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    applySession()
  })

  it('renders successful count/list siblings and isolates a partial widget failure', async () => {
    vi.mocked(runtimeDashboardApi.systemHome).mockResolvedValue(dashboard)
    const wrapper = render()
    await flushPromises()
    expect(runtimeDashboardApi.systemHome).toHaveBeenCalledWith('10')
    expect(wrapper.get('[data-widget-code="count"]').text()).toContain('8')
    expect(wrapper.get('[data-widget-code="list"]').text()).toContain('采购单')
    expect(wrapper.get('[data-widget-code="failed"]').text()).toContain('SOURCE_FORBIDDEN')
    expect(wrapper.get('[data-state="partial-error"]').text()).toContain('1 个组件加载失败')
  })

  it('exposes loading, empty and unavailable as distinct runtime states', async () => {
    let resolve!: (value: RuntimeDashboard) => void
    vi.mocked(runtimeDashboardApi.systemHome).mockReturnValue(new Promise(done => { resolve = done }))
    const wrapper = render()
    expect(wrapper.find('[data-state="loading"]').exists()).toBe(true)
    resolve({ ...dashboard, widgets: [] })
    await flushPromises()
    expect(wrapper.get('[data-state="empty"]').text()).toContain('暂时没有组件')

    vi.mocked(runtimeDashboardApi.systemHome).mockRejectedValueOnce(new Error('dashboard unpublished'))
    applySession('21')
    await flushPromises()
    expect(wrapper.get('[data-state="unavailable"]').text()).toContain('dashboard unpublished')
  })
})
