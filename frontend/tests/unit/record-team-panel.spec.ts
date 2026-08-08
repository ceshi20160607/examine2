import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { ApiRequestError } from '@/services/api'
import { recordTeamApi } from '@/services/collab'
import { useSessionStore } from '@/stores/session'
import RecordTeamPanel from '@/views/system/RecordTeamPanel.vue'

vi.mock('@/services/collab', () => ({
  recordTeamApi: {
    get: vi.fn(),
    initialize: vi.fn(),
    addMember: vi.fn(),
    changeRole: vi.fn(),
    removeMember: vi.fn(),
    transferOwnership: vi.fn(),
  },
}))

const team = {
  systemId: '10',
  tenantId: '20',
  recordId: '30',
  version: 2,
  ownerMemberId: '100',
  members: [
    { memberId: '100', role: 'OWNER' as const },
    { memberId: '101', role: 'COLLABORATOR' as const },
  ],
}

function applySession(permissions: string[]) {
  const session = useSessionStore()
  session.applyAuth({
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
  return mount(RecordTeamPanel, {
    props: { systemId: '10', moduleCode: 'work_order', recordId: '30' },
    global: {
      stubs: {
        'a-alert': {
          props: ['message', 'description'],
          template: '<div class="alert">{{ message }} {{ description }}</div>',
        },
        'a-spin': { template: '<div><slot /></div>' },
        'a-empty': {
          props: ['description'],
          template: '<div class="empty">{{ description }}</div>',
        },
        'a-button': {
          template: '<button @click="$emit(\'click\')"><slot /></button>',
        },
        'a-tag': { template: '<span><slot /></span>' },
        'a-select': { template: '<div class="select" />' },
        'a-popconfirm': { template: '<div><slot /></div>' },
        'a-modal': {
          props: ['open'],
          template: '<div v-if="open"><slot /></div>',
        },
        'a-form': { template: '<form><slot /></form>' },
        'a-form-item': { template: '<label><slot /></label>' },
        'a-input': { template: '<input />' },
      },
    },
  })
}

describe('RecordTeamPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('fails closed before making a request when view permission is absent', async () => {
    applySession(['system.runtime.access'])

    const wrapper = render()
    await flushPromises()

    expect(wrapper.text()).toContain('无权查看协作团队')
    expect(recordTeamApi.get).not.toHaveBeenCalled()
  })

  it('loads members and exposes mutations granted by module permissions', async () => {
    applySession([
      'system.runtime.access',
      'module.work_order.view',
      'module.work_order.update',
      'module.work_order.action.transfer',
    ])
    vi.mocked(recordTeamApi.get).mockResolvedValue(team)

    const wrapper = render()
    await flushPromises()

    expect(recordTeamApi.get).toHaveBeenCalledWith('10', 'work_order', '30')
    expect(wrapper.text()).toContain('成员 100')
    expect(wrapper.text()).toContain('成员 101')
    expect(wrapper.text()).toContain('添加成员')
    expect(wrapper.text()).toContain('转移所有权')
  })

  it('offers initialization only to a member with module update permission', async () => {
    applySession([
      'system.runtime.access',
      'module.work_order.view',
      'module.work_order.update',
    ])
    vi.mocked(recordTeamApi.get).mockRejectedValue(new ApiRequestError(404, {
      code: 'RECORD_TEAM_NOT_FOUND',
      message: 'Not initialized',
      data: null,
      requestId: 'request-1',
      traceId: 'trace-1',
      errors: [],
    }))
    vi.mocked(recordTeamApi.initialize).mockResolvedValue({ created: true, team })

    const wrapper = render()
    await flushPromises()

    expect(wrapper.text()).toContain('当前记录尚未初始化协作团队')
    const initialize = wrapper.findAll('button')
      .find((button) => button.text().includes('初始化协作团队'))
    expect(initialize).toBeDefined()

    await initialize!.trigger('click')
    await flushPromises()

    expect(recordTeamApi.initialize).toHaveBeenCalledWith('10', 'work_order', '30')
    expect(wrapper.text()).toContain('成员 100')
  })
})
