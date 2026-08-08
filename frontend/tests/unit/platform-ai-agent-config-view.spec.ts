import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { platformAiAdminApi } from '@/services/platformAi'
import type { PlatformAiCapability, PlatformAiPolicy, PlatformAiProvider } from '@/types/platformAi'
import PlatformAiAgentConfigView from '@/views/platform/admin/PlatformAiAgentConfigView.vue'

vi.mock('@/services/platformAi', () => ({ platformAiAdminApi: {
  providers: vi.fn(), createProvider: vi.fn(), updateProvider: vi.fn(), policy: vi.fn(), savePolicy: vi.fn(),
  checkPolicy: vi.fn(), publishPolicy: vi.fn(), capability: vi.fn(),
} }))

const provider: PlatformAiProvider = {
  id: 'provider-1', code: 'platform-main', name: 'Platform model', baseUrl: 'https://ai.example/v1',
  model: 'model-a', secretRef: 'vault://ai/platform/v1', timeoutSeconds: 30, enabled: true, version: 2,
}
const policy: PlatformAiPolicy = {
  draftVersion: 3, status: 'DRAFT', providerId: 'provider-1', providerVersion: 2,
  allowedOperations: ['AUTHORIZED_SYSTEMS_QUERY', 'PLATFORM_OPERATIONS_QUERY', 'PLATFORM_TASK_DRAFT', 'SYSTEM_SWITCH_GUIDANCE'], maxSystems: 50,
  dailyRequestQuota: 1000, dailyTokenQuota: 1000000, maxConcurrency: 4, strictRedaction: true,
  dataResidency: 'PLATFORM_METADATA_ONLY', promptVersion: 'platform-v1', enabled: true,
  activeVersionId: 'policy-2',
}
const capability: PlatformAiCapability = { available: true, reason: null, policyVersion: 'policy-2' }

function render() {
  return mount(PlatformAiAgentConfigView, { global: { stubs: {
    AdminPageHeader: { props: ['title', 'description'], template: '<header><h1>{{ title }}</h1><p>{{ description }}</p><slot name="actions" /></header>' },
    Bot: true, CheckCircle2: true, Plus: true, RefreshCw: true, Rocket: true, Save: true,
    'a-button': { inheritAttrs: false, props: ['disabled', 'loading'], emits: ['click'], template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
    'a-alert': { props: ['message', 'description'], template: '<div class="alert-stub">{{ message }} {{ description }}</div>' },
    'a-spin': { template: '<div><slot /></div>' }, 'a-tag': { template: '<span><slot /></span>' },
    'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
  } } })
}

describe('PlatformAiAgentConfigView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(platformAiAdminApi.providers).mockResolvedValue([provider])
    vi.mocked(platformAiAdminApi.policy).mockResolvedValue(policy)
    vi.mocked(platformAiAdminApi.capability).mockResolvedValue(capability)
    vi.mocked(platformAiAdminApi.createProvider).mockResolvedValue({ ...provider, version: 1 })
    vi.mocked(platformAiAdminApi.updateProvider).mockResolvedValue({ ...provider, version: 3 })
    vi.mocked(platformAiAdminApi.savePolicy).mockResolvedValue({ ...policy, draftVersion: 4 })
    vi.mocked(platformAiAdminApi.checkPolicy).mockResolvedValue({ status: 'PASSED', issues: [] })
    vi.mocked(platformAiAdminApi.publishPolicy).mockResolvedValue({ ...policy, draftVersion: 4, status: 'PUBLISHED', activeVersionId: 'policy-4' })
  })

  it('loads a SecretRef provider and the immutable platform metadata boundary', async () => {
    const wrapper = render()
    await flushPromises()
    expect(platformAiAdminApi.providers).toHaveBeenCalledWith()
    expect(platformAiAdminApi.policy).toHaveBeenCalledWith()
    expect(platformAiAdminApi.capability).toHaveBeenCalledWith()
    expect((wrapper.get('.platform-ai-provider-secret-ref').element as HTMLInputElement).value).toBe('vault://ai/platform/v1')
    expect((wrapper.get('.platform-ai-policy-redaction').element as HTMLInputElement).value).toContain('STRICT')
    expect((wrapper.get('.platform-ai-policy-residency').element as HTMLInputElement).value).toBe('PLATFORM_METADATA_ONLY')
    expect(wrapper.findAll('.platform-ai-policy-operations input:checked')).toHaveLength(4)
    expect(wrapper.text()).toContain('PLATFORM_OPERATIONS_QUERY（只读）')
    expect(wrapper.text()).toContain('PLATFORM_TASK_DRAFT（确认后创建）')
    expect(wrapper.text()).toContain('不能携带模块、字段或业务记录')
    expect(wrapper.text()).not.toContain('RECORD_CREATE')
  })

  it('rejects plaintext credentials and saves a versioned platform Provider', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.platform-ai-provider-secret-ref').setValue('env:PLAIN')
    await wrapper.get('.platform-ai-provider-save').trigger('click')
    expect(platformAiAdminApi.updateProvider).not.toHaveBeenCalled()
    expect(wrapper.get('.platform-ai-admin-error').text()).toContain('scheme://reference')

    await wrapper.get('.platform-ai-provider-secret-ref').setValue('vault://ai/platform/v2')
    await wrapper.get('.platform-ai-provider-save').trigger('click')
    await flushPromises()
    expect(platformAiAdminApi.updateProvider).toHaveBeenCalledWith('provider-1', {
      expectedVersion: 2, code: 'platform-main', name: 'Platform model', baseUrl: 'https://ai.example/v1',
      model: 'model-a', secretRef: 'vault://ai/platform/v2', timeoutSeconds: 30, enabled: true,
    })
  })

  it('saves, checks and publishes the bounded read-only policy', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.platform-ai-policy-max-systems').setValue('80')
    await wrapper.get('.platform-ai-policy-request-quota').setValue('5000')
    await wrapper.get('.platform-ai-policy-token-quota').setValue('2000000')
    await wrapper.get('.platform-ai-policy-concurrency').setValue('8')
    await wrapper.get('.platform-ai-policy-save').trigger('click')
    await flushPromises()
    expect(platformAiAdminApi.savePolicy).toHaveBeenCalledWith({
      expectedVersion: 3, providerId: 'provider-1',
      allowedOperations: ['AUTHORIZED_SYSTEMS_QUERY', 'PLATFORM_OPERATIONS_QUERY', 'PLATFORM_TASK_DRAFT', 'SYSTEM_SWITCH_GUIDANCE'], maxSystems: 80,
      dailyRequestQuota: 5000, dailyTokenQuota: 2000000, maxConcurrency: 8, strictRedaction: true,
      dataResidency: 'PLATFORM_METADATA_ONLY', promptVersion: 'platform-v1', enabled: true,
    })
    await wrapper.get('.platform-ai-policy-check').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('检查结果：PASSED')
    await wrapper.get('.platform-ai-policy-publish').trigger('click')
    await flushPromises()
    expect(platformAiAdminApi.publishPolicy).toHaveBeenCalledWith(4)
    expect(platformAiAdminApi.capability).toHaveBeenCalledTimes(2)
  })

  it('blocks out-of-range quota and incomplete operation scopes before transport', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.platform-ai-policy-concurrency').setValue('17')
    await wrapper.get('.platform-ai-policy-save').trigger('click')
    expect(platformAiAdminApi.savePolicy).not.toHaveBeenCalled()
    expect(wrapper.get('.platform-ai-admin-error').text()).toContain('1 到 16')

    await wrapper.get('.platform-ai-policy-concurrency').setValue('4')
    await wrapper.findAll('.platform-ai-policy-operations input')[1]!.setValue(false)
    await wrapper.get('.platform-ai-policy-save').trigger('click')
    expect(platformAiAdminApi.savePolicy).not.toHaveBeenCalled()
    expect(wrapper.get('.platform-ai-admin-error').text()).toContain('系统切换引导')
  })
})
