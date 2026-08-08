import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { platformSettingsApi } from '@/services/platformSettings'
import PlatformGlobalSettingsView from '@/views/platform/admin/PlatformGlobalSettingsView.vue'

vi.mock('@/services/platformSettings', () => ({ platformSettingsApi: { get: vi.fn(), update: vi.fn() } }))
vi.mock('ant-design-vue', () => ({ message: { success: vi.fn() } }))

const settings = {
  profile: { name: 'Operations', description: 'Governance' },
  storage: { defaultMode: 'S3' as const, maximumUploadBytes: 50_000_000, retentionDays: 90 },
  security: { sessionIdleMinutes: 45, passwordMinimumLength: 14, requireMfaForAdmins: true },
  quota: { defaultMemberLimit: 500, defaultModuleLimit: 100, defaultStorageBytes: 50_000_000_000 },
  backup: { enabled: true, retentionDays: 60, intervalHours: 12 },
  release: { maintenanceMode: false, channel: 'CANARY' as const, approvalRequired: true },
  version: '3', updatedAt: '2026-08-07T00:00:00Z',
}

const stubs = {
  'a-button': { props: ['loading'], emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
  'a-alert': { props: ['message', 'description'], template: '<div>{{ message }} {{ description }}</div>' },
  'a-tabs': { template: '<div><slot /></div>' },
  'a-tab-pane': { props: ['tab'], template: '<section><h2>{{ tab }}</h2><slot /></section>' },
  RefreshCw: true, Save: true,
}

describe('PlatformGlobalSettingsView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(platformSettingsApi.get).mockResolvedValue(settings)
    vi.mocked(platformSettingsApi.update).mockResolvedValue({ ...settings, version: '4' })
  })

  it('loads all six policy groups and saves with optimistic version', async () => {
    const wrapper = mount(PlatformGlobalSettingsView, { global: { stubs } })
    await flushPromises()
    expect(wrapper.text()).toContain('平台信息')
    expect(wrapper.text()).toContain('默认配额')
    expect(wrapper.text()).toContain('配置版本 3')
    expect(wrapper.text()).toContain('连接串、凭据和 SecretRef 不在此处保存')

    const save = wrapper.findAll('button').find(button => button.text().includes('保存策略'))!
    await save.trigger('click')
    await flushPromises()
    expect(platformSettingsApi.update).toHaveBeenCalledWith(expect.objectContaining({
      profile: { name: 'Operations', description: 'Governance' }, expectedVersion: '3',
    }))
    expect(wrapper.text()).toContain('配置版本 4')
  })
})
