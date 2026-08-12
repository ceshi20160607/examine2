import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { auditApi } from '@/services/audit'
import UnifiedAuditLogsView from '@/views/admin/UnifiedAuditLogsView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '41' } }))

vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('@/services/audit', () => ({ auditApi: {
  platformLogs: vi.fn(), systemLogs: vi.fn(), platformHealth: vi.fn(), systemHealth: vi.fn(),
} }))

const health = {
  generatedAt: '2026-08-07T00:00:00Z', overallStatus: 'AVAILABLE' as const,
  version: '1.2.3', flywayVersion: '8.93.0',
  components: [{ code: 'DB', label: 'Database', status: 'AVAILABLE' as const, summary: 'Database probe succeeded.', hint: 'No action required.' }],
}
const page = {
  page: 1, size: 20, total: 1,
  items: [{
    id: 'OPERATION:1', category: 'CONFIG' as const, source: 'ADMIN', event: 'MODULE_UPDATE',
    actorId: '2', actorName: 'Alice', objectType: 'MODULE', objectId: 'pump', result: 'SUCCESS' as const,
    requestId: 'request-1', traceId: 'trace-1', systemId: '41', tenantId: '73',
    occurredAt: '2026-08-07T00:00:00Z', details: { Context: 'SYSTEM' },
  }],
}

const stubs = {
  'a-input': { props: ['value'], emits: ['update:value'], template: '<input :value="value" @input="$emit(\'update:value\', $event.target.value)">' },
  'a-button': { props: ['htmlType'], emits: ['click'], template: '<button :type="htmlType || \'button\'" @click="$emit(\'click\')"><slot /></button>' },
  'a-alert': { props: ['message', 'description'], template: '<div class="alert">{{ message }} {{ description }}</div>' },
  'a-tag': { template: '<span class="tag"><slot /></span>' },
  'a-skeleton': true,
  'a-pagination': true,
  'a-drawer': { props: ['open', 'title'], template: '<aside v-if="open"><h2>{{ title }}</h2><slot /></aside>' },
  'a-tabs': { template: '<div><slot /></div>' },
  'a-tab-pane': { props: ['tab'], template: '<section><h3>{{ tab }}</h3><slot /></section>' },
  Eye: true, FilterX: true, RefreshCw: true, Search: true,
}

function render(zone: 'platform' | 'system') {
  return mount(UnifiedAuditLogsView, { props: { zone }, global: { stubs } })
}

describe('UnifiedAuditLogsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(auditApi.platformLogs).mockResolvedValue(page)
    vi.mocked(auditApi.systemLogs).mockResolvedValue(page)
    vi.mocked(auditApi.platformHealth).mockResolvedValue(health)
    vi.mocked(auditApi.systemHealth).mockResolvedValue(health)
  })

  it('loads the platform health summary and filtered unified log list', async () => {
    const wrapper = render('platform')
    await flushPromises()

    expect(auditApi.platformHealth).toHaveBeenCalledOnce()
    expect(auditApi.platformLogs).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 20 }))
    expect(wrapper.text()).toContain('版本 1.2.3 · Flyway 8.93.0')
    expect(wrapper.text()).toContain('MODULE_UPDATE')
    expect(wrapper.text()).not.toContain('env://MUST_NOT_LEAK')

    const inputs = wrapper.findAll('.audit-filters input')
    await inputs[0]!.setValue('request / 1')
    await inputs[1]!.setValue('trace-1')
    await wrapper.find('.audit-filters select').setValue('CONFIG')
    await wrapper.find('.audit-filters').trigger('submit')
    await flushPromises()
    expect(auditApi.platformLogs).toHaveBeenLastCalledWith(expect.objectContaining({
      requestId: 'request / 1', traceId: 'trace-1', category: 'CONFIG', page: 1,
    }))

    const detail = wrapper.findAll('button').find(button => button.text().includes('详情'))
    await detail!.trigger('click')
    expect(wrapper.text()).toContain('主体与对象')
    expect(wrapper.text()).toContain('Request ID')
  })

  it('uses system-scoped endpoints with the route system id', async () => {
    render('system')
    await flushPromises()
    expect(auditApi.systemLogs).toHaveBeenCalledWith('41', expect.objectContaining({ page: 1 }))
    expect(auditApi.systemHealth).toHaveBeenCalledWith('41')
    expect(auditApi.platformLogs).not.toHaveBeenCalled()
  })
})
