import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { platformMessageApi } from '@/services/platformMessage'
import PlatformMessageView from '@/views/platform/PlatformMessageView.vue'

const router = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => router }))
vi.mock('@/services/platformMessage', () => ({ platformMessageApi: {
  list: vi.fn(), unreadCount: vi.fn(), read: vi.fn(), readAll: vi.fn(), archive: vi.fn(),
} }))

const item = { id: '41', templateCode: 'platform.task.completed', type: 'TASK' as const, title: '任务已完成', body: '平台任务已经完成', target: { type: 'PLATFORM_TASK' as const, id: '21', path: '/platform/work?task=21' }, status: 'UNREAD' as const, createdAt: '2026-08-07T01:00:00Z', readAt: null, archivedAt: null, version: 0 }
const stubs = {
  'a-button': { inheritAttrs: false, props: ['loading', 'disabled'], emits: ['click'], template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
  'a-alert': { props: ['message', 'description'], template: '<div class="alert-stub">{{ message }}{{ description }}</div>' },
  'a-badge': { props: ['count'], template: '<span>{{ count }}</span>' },
  'a-tag': { template: '<span><slot /></span>' },
  'a-spin': { template: '<div><slot /></div>' },
  'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
  'a-pagination': true,
}

describe('PlatformMessageView', () => {
  beforeEach(() => {
    vi.clearAllMocks(); router.push.mockResolvedValue(undefined)
    vi.mocked(platformMessageApi.list).mockResolvedValue({ items: [item], page: 1, size: 20, total: 1 })
    vi.mocked(platformMessageApi.unreadCount).mockResolvedValue({ unreadCount: 1 })
    vi.mocked(platformMessageApi.read).mockResolvedValue({ ...item, status: 'READ', readAt: '2026-08-07T02:00:00Z', version: 1 })
    vi.mocked(platformMessageApi.readAll).mockResolvedValue({ changedCount: 1 })
    vi.mocked(platformMessageApi.archive).mockResolvedValue({ ...item, status: 'ARCHIVED', readAt: '2026-08-07T02:00:00Z', archivedAt: '2026-08-07T02:00:00Z', version: 1 })
  })

  it('loads the isolated namespace, applies filters and opens a safe platform target after read-back', async () => {
    const wrapper = mount(PlatformMessageView, { global: { stubs } })
    await flushPromises()
    expect(platformMessageApi.list).toHaveBeenCalledWith(expect.objectContaining({ status: 'ALL', type: 'ALL', page: 1, size: 20 }))
    await wrapper.get('select').setValue('UNREAD')
    await wrapper.get('form').trigger('submit')
    await flushPromises()
    expect(platformMessageApi.list).toHaveBeenLastCalledWith(expect.objectContaining({ status: 'UNREAD' }))
    await wrapper.get('.platform-message-list article').trigger('click')
    await flushPromises()
    expect(platformMessageApi.read).toHaveBeenCalledWith('41')
    expect(router.push).toHaveBeenCalledWith('/platform/work?task=21')
  })

  it('blocks system-business deep links even if a compromised response reaches the view', async () => {
    vi.mocked(platformMessageApi.list).mockResolvedValueOnce({ items: [{ ...item, target: { ...item.target, path: '/systems/9/workbench' } }], page: 1, size: 20, total: 1 })
    const wrapper = mount(PlatformMessageView, { global: { stubs } })
    await flushPromises()
    await wrapper.get('.platform-message-list article').trigger('click')
    expect(router.push).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('平台消息目标不安全')
  })

  it('supports read-all and archive owner actions', async () => {
    const wrapper = mount(PlatformMessageView, { global: { stubs } })
    await flushPromises()
    await wrapper.get('.platform-message-read-all').trigger('click')
    await flushPromises()
    expect(platformMessageApi.readAll).toHaveBeenCalled()
    await wrapper.findAll('.platform-message-list footer button')[1]!.trigger('click')
    await flushPromises()
    expect(platformMessageApi.archive).toHaveBeenCalledWith('41')
  })
})
