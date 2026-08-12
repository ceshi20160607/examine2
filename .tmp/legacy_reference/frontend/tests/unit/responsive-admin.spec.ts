import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import PlatformSystemsView from '@/views/platform/admin/PlatformSystemsView.vue'

const mocks = vi.hoisted(() => ({
  listSystems: vi.fn(),
}))

vi.mock('@/services/admin', () => ({
  platformAdminApi: {
    listSystems: mocks.listSystems,
  },
}))

function matchMedia(matches: boolean): MediaQueryList {
  return {
    matches,
    media: '(max-width: 720px)',
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }
}

function render(matches: boolean) {
  vi.stubGlobal('matchMedia', vi.fn(() => matchMedia(matches)))
  return mount(PlatformSystemsView, {
    global: {
      stubs: {
        AdminPageHeader: { template: '<header><slot name="actions" /></header>' },
        AdminMobileNotice: { template: '<div data-testid="readonly-notice" />' },
        'a-alert': true,
        'a-button': true,
        'a-empty': true,
        'a-form': true,
        'a-form-item': true,
        'a-input': true,
        'a-modal': true,
        'a-select': true,
        'a-select-option': true,
        'a-spin': true,
        'a-table': { template: '<div data-testid="admin-table" />' },
        'a-tag': { template: '<span><slot /></span>' },
        'a-textarea': true,
      },
    },
  })
}

describe('responsive administration rendering', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    mocks.listSystems.mockResolvedValue({
      items: [{
        id: 'system-1', code: 'demo', name: 'Demo', description: '', status: 'ACTIVE',
        tenantMode: 'SINGLE', ownerAccountId: 'account-1', version: '1',
      }],
      page: 1,
      size: 20,
      total: 1,
    })
  })

  it('renders readonly records without mounting the table on narrow screens', async () => {
    const wrapper = render(true)
    await flushPromises()

    expect(wrapper.find('[data-testid="readonly-notice"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="admin-table"]').exists()).toBe(false)
    expect(wrapper.findAll('article')).toHaveLength(1)
  })

  it('mounts the editable table on desktop screens', async () => {
    const wrapper = render(false)
    await flushPromises()

    expect(wrapper.find('[data-testid="readonly-notice"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="admin-table"]').exists()).toBe(true)
    expect(wrapper.findAll('article')).toHaveLength(0)
  })
})
