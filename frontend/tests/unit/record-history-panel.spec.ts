import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { recordHistoryApi } from '@/services/history'
import { useSessionStore } from '@/stores/session'
import RecordHistoryPanel from '@/views/system/RecordHistoryPanel.vue'

vi.mock('@/services/history', () => ({
  recordHistoryApi: { list: vi.fn() },
}))

function applySession(permissions: string[]) {
  useSessionStore().applyAuth({
    account: { id: '1', username: 'member', displayName: 'Member' },
    context: {
      type: 'SYSTEM',
      account: { id: '1', username: 'member', displayName: 'Member' },
      systemId: '10',
      tenantId: '20',
      memberId: '100',
      permissionVersion: '1',
      permissions,
      shells: ['SYSTEM_RUNTIME'],
    },
    systems: [],
  })
}

function render() {
  return mount(RecordHistoryPanel, {
    props: { systemId: '10', moduleCode: 'work_order', recordId: '30' },
    global: {
      stubs: {
        'a-alert': {
          props: ['message', 'description'],
          template: '<div>{{ message }} {{ description }}</div>',
        },
        'a-spin': { template: '<div><slot /></div>' },
        'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
        'a-button': { template: '<button><slot /></button>' },
        'a-timeline': { template: '<div><slot /></div>' },
        'a-timeline-item': { template: '<div><slot /></div>' },
        'a-tag': { template: '<span><slot /></span>' },
        'a-pagination': { template: '<div />' },
      },
    },
  })
}

describe('RecordHistoryPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fails closed without history permission', async () => {
    applySession(['system.runtime.access', 'module.work_order.view'])
    const wrapper = render()
    await flushPromises()

    expect(wrapper.text()).toContain('无权查看记录历史')
    expect(recordHistoryApi.list).not.toHaveBeenCalled()
  })

  it('renders visible and masked changes from the scoped page', async () => {
    applySession([
      'system.runtime.access',
      'module.work_order.view',
      'module.work_order.history.read',
    ])
    vi.mocked(recordHistoryApi.list).mockResolvedValue({
      items: [{
        historyId: '50',
        recordVersion: 2,
        action: 'RECORD_UPDATED',
        actorMemberId: '100',
        occurredAt: '2026-07-25T12:00:00Z',
        diff: [
          { fieldCode: '$title', beforeValue: '旧标题', afterValue: '新标题', masked: false },
          { fieldCode: 'secret', beforeValue: null, afterValue: null, masked: true },
        ],
      }],
      page: 1,
      size: 20,
      total: 1,
    })

    const wrapper = render()
    await flushPromises()

    expect(recordHistoryApi.list).toHaveBeenCalledWith('10', 'work_order', '30', 1)
    expect(wrapper.text()).toContain('更新记录')
    expect(wrapper.text()).toContain('旧标题')
    expect(wrapper.text()).toContain('新标题')
    expect(wrapper.text()).toContain('已脱敏')
  })
})
