import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { recordTeamApi } from '@/services/collab'
import { recordCommentApi } from '@/services/comment'
import { useSessionStore } from '@/stores/session'
import RecordCommentPanel from '@/views/system/RecordCommentPanel.vue'

vi.mock('@/services/comment', () => ({
  recordCommentApi: {
    list: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    remove: vi.fn(),
  },
}))

vi.mock('@/services/collab', () => ({
  recordTeamApi: { get: vi.fn() },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
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

      roleIds: ['legacy-role-1'],
      dataScope: { id: 'legacy-scope-1', code: 'legacy_all', kind: 'ALL' },
      restrictedMode: 'NONE',
    },
    systems: [],

    tenants: [],
    firstSystemId: null,
  })
}

function render() {
  return mount(RecordCommentPanel, {
    props: { systemId: '10', moduleCode: 'work_order', recordId: '30' },
    global: {
      stubs: {
        'a-alert': {
          props: ['message', 'description'],
          template: '<div>{{ message }} {{ description }}</div>',
        },
        'a-spin': { template: '<div><slot /></div>' },
        'a-empty': {
          props: ['description'],
          template: '<div>{{ description }}</div>',
        },
        'a-button': { template: '<button><slot /></button>' },
        'a-popconfirm': { template: '<div><slot /></div>' },
        'a-pagination': { template: '<div />' },
        'a-textarea': {
          props: ['value'],
          emits: ['update:value'],
          template: '<textarea :value="value" @input="$emit(\'update:value\', $event.target.value)" />',
        },
        'a-select': {
          emits: ['update:value'],
          template: '<button data-mention @click="$emit(\'update:value\', [\'101\'])">选择提及</button>',
        },
        'a-modal': { template: '<div><slot /></div>' },
      },
    },
  })
}

describe('RecordCommentPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    vi.mocked(recordTeamApi.get).mockResolvedValue({
      systemId: '10', tenantId: '20', recordId: '30', version: 1,
      ownerMemberId: '100', members: [],
    })
  })

  it('fails closed before requesting comments without module view permission', async () => {
    applySession(['system.runtime.access'])
    const wrapper = render()
    await flushPromises()

    expect(wrapper.text()).toContain('无权查看记录评论')
    expect(recordCommentApi.list).not.toHaveBeenCalled()
  })

  it('loads active and tombstoned comments from the scoped record', async () => {
    applySession(['system.runtime.access', 'module.work_order.view'])
    vi.mocked(recordCommentApi.list).mockResolvedValue({
      items: [
        {
          commentId: '40',
          recordId: '30',
          parentCommentId: null,
          authorMemberId: '100',
          body: 'First comment',
          deleted: false,
          version: 1,
          createdAt: '2026-07-25T10:00:00Z',
          updatedAt: '2026-07-25T10:00:00Z',
          mentionedMemberIds: ['101'],
          canEdit: true,
          canDelete: true,
        },
        {
          commentId: '41',
          recordId: '30',
          parentCommentId: '40',
          authorMemberId: '101',
          body: null,
          deleted: true,
          version: 2,
          createdAt: '2026-07-25T10:01:00Z',
          updatedAt: '2026-07-25T10:02:00Z',
          mentionedMemberIds: [],
          canEdit: false,
          canDelete: false,
        },
      ],
      page: 1,
      size: 20,
      total: 2,
    })

    const wrapper = render()
    await flushPromises()

    expect(recordCommentApi.list).toHaveBeenCalledWith('10', 'work_order', '30', 1)
    expect(wrapper.text()).toContain('First comment')
    expect(wrapper.text()).toContain('该评论已删除')
    expect(wrapper.text()).toContain('成员 101')
  })

  it('submits explicit record-team mention ids with the plain-text comment', async () => {
    applySession(['system.runtime.access', 'module.work_order.view'])
    vi.mocked(recordCommentApi.list).mockResolvedValue({ items: [], page: 1, size: 20, total: 0 })
    vi.mocked(recordTeamApi.get).mockResolvedValue({
      systemId: '10', tenantId: '20', recordId: '30', version: 1,
      ownerMemberId: '100',
      members: [
        { memberId: '100', role: 'OWNER' },
        { memberId: '101', role: 'COLLABORATOR' },
      ],
    })
    vi.mocked(recordCommentApi.create).mockResolvedValue({
      commentId: '42', recordId: '30', parentCommentId: null, authorMemberId: '100',
      body: '请复核', deleted: false, version: 1,
      createdAt: '2026-07-29T08:00:00Z', updatedAt: '2026-07-29T08:00:00Z',
      mentionedMemberIds: ['101'], canEdit: true, canDelete: true,
    })

    const wrapper = render()
    await flushPromises()
    await wrapper.find('[data-mention]').trigger('click')
    await wrapper.find('textarea').setValue('请复核')
    const publish = wrapper.findAll('button').find(button => button.text().includes('发布评论'))
    expect(publish).toBeTruthy()
    await publish!.trigger('click')
    await flushPromises()

    expect(recordCommentApi.create).toHaveBeenCalledWith('10', 'work_order', '30', {
      body: '请复核',
      mentionedMemberIds: ['101'],
    })
  })
})
