import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import MemberPicker from '@/components/runtime/MemberPicker.vue'
import { memberDirectoryApi } from '@/services/memberDirectory'
import { useSessionStore } from '@/stores/session'

vi.mock('@/services/memberDirectory', () => ({
  memberDirectoryApi: { list: vi.fn() },
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
  return mount(MemberPicker, {
    props: { systemId: '10', value: '' },
    global: {
      stubs: {
        'a-select': {
          props: ['options', 'value'],
          emits: ['change', 'search', 'clear'],
          template: `
            <div>
              <button class="choose" @click="$emit('change', options[0]?.value)">
                {{ options[0]?.label }}
              </button>
            </div>
          `,
        },
      },
    },
  })
}

describe('MemberPicker', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('loads active tenant options and emits the selected member ID', async () => {
    applySession(['system.runtime.access'])
    vi.mocked(memberDirectoryApi.list).mockResolvedValue({
      items: [{ memberId: '101', memberCode: 'M-101', displayName: '张三' }],
      page: 1,
      size: 20,
      total: 1,
    })

    const wrapper = render()
    await flushPromises()

    expect(memberDirectoryApi.list).toHaveBeenCalledWith('10', '')
    expect(wrapper.text()).toContain('张三（M-101）')
    await wrapper.get('.choose').trigger('click')
    expect(wrapper.emitted('update:value')).toEqual([['101']])
  })

  it('fails closed without runtime access permission', async () => {
    applySession([])
    const wrapper = render()
    await flushPromises()

    expect(memberDirectoryApi.list).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('')
  })
})
