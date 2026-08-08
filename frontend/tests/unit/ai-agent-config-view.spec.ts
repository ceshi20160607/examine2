import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { aiAdminApi } from '@/services/ai'
import type { AiCapability, AiPolicy, AiProvider } from '@/types/ai'
import AiAgentConfigView from '@/views/system/admin/AiAgentConfigView.vue'

const route = vi.hoisted(() => ({ params: { systemId: '10' } }))
vi.mock('vue-router', () => ({ useRoute: () => route }))
vi.mock('@/services/ai', () => ({ aiAdminApi: {
  providers: vi.fn(), createProvider: vi.fn(), updateProvider: vi.fn(), policy: vi.fn(), savePolicy: vi.fn(),
  checkPolicy: vi.fn(), publishPolicy: vi.fn(), capability: vi.fn(),
} }))

const provider: AiProvider = {
  id: 'provider-1', code: 'openai-main', name: 'OpenAI compatible', baseUrl: 'https://ai.example/v1',
  model: 'model-a', secretRef: 'vault://ai/provider/v1', timeoutSeconds: 30, enabled: true, version: 2,
}
const policy: AiPolicy = {
  draftVersion: 3, activeVersionId: 'policy-2', providerId: 'provider-1', moduleCodes: ['orders'],
  outboundFields: { orders: ['orderNo', 'amount'] }, maxRows: 20, redactionMode: 'STRICT',
  allowedOperations: ['RECORD_QUERY', 'RECORD_CREATE', 'RECORD_UPDATE', 'AI_FILL', 'CONFIG_FIELD_DRAFT', 'CONFIG_SELECTION_FIELD_DRAFT', 'CONFIG_PAGE_LAYOUT_DRAFT', 'CONFIG_FILTER_SCENARIO_DRAFT', 'CONFIG_FIELD_PERMISSION_STAGE_DRAFT', 'RECORD_CONTEXT_SUMMARY', 'RECORD_COMMENT_QUERY', 'RECORD_HISTORY_QUERY', 'RECORD_FILE_QUERY', 'WORK_TASK_QUERY', 'WORK_DAILY_REPORT_QUERY', 'WORK_PROJECT_METRICS_QUERY', 'RUNTIME_STATISTICS_QUERY', 'RUNTIME_REPORT_QUERY', 'FLOW_INSTANCE_HISTORY_QUERY', 'WORK_TASK_DRAFT', 'WORK_DAILY_REPORT_DRAFT', 'FLOW_DEFINITION_DRAFT', 'CONFIG_REPORT_DRAFT', 'CONFIG_PRINT_TEMPLATE_DRAFT'],
  writableFields: { orders: ['amount', 'status'] }, confirmationMode: 'REQUIRED',
  fillFields: { orders: ['aiSummary'] },
  confirmationExpiresSeconds: 900, promptVersion: 'v1', enabled: true,
}
const capability: AiCapability = { available: true, reason: null, policyVersion: 'policy-2' }

function render() {
  return mount(AiAgentConfigView, { global: { stubs: {
    AdminPageHeader: { props: ['title', 'description'], template: '<header><h1>{{ title }}</h1><p>{{ description }}</p><slot name="actions" /></header>' },
    Bot: true, CheckCircle2: true, Plus: true, RefreshCw: true, Rocket: true, Save: true,
    'a-button': { inheritAttrs: false, props: ['disabled', 'loading'], emits: ['click'], template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
    'a-alert': { props: ['message', 'description'], template: '<div class="alert-stub">{{ message }} {{ description }}</div>' },
    'a-spin': { template: '<div><slot /></div>' }, 'a-tag': { template: '<span><slot /></span>' },
    'a-empty': { props: ['description'], template: '<div>{{ description }}</div>' },
  } } })
}

describe('AiAgentConfigView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(aiAdminApi.providers).mockResolvedValue([provider])
    vi.mocked(aiAdminApi.policy).mockResolvedValue(policy)
    vi.mocked(aiAdminApi.capability).mockResolvedValue(capability)
    vi.mocked(aiAdminApi.createProvider).mockResolvedValue({ ...provider, version: 1 })
    vi.mocked(aiAdminApi.updateProvider).mockResolvedValue({ ...provider, version: 3 })
    vi.mocked(aiAdminApi.savePolicy).mockResolvedValue({ ...policy, draftVersion: 4 })
    vi.mocked(aiAdminApi.checkPolicy).mockResolvedValue({ status: 'PASSED', issues: [] })
    vi.mocked(aiAdminApi.publishPolicy).mockResolvedValue({ ...policy, draftVersion: 4, activeVersionId: 'policy-4' })
  })

  it('loads capability, provider/model SecretRef and strict policy fields', async () => {
    const wrapper = render()
    await flushPromises()

    expect(aiAdminApi.providers).toHaveBeenCalledWith('10')
    expect(aiAdminApi.policy).toHaveBeenCalledWith('10')
    expect(aiAdminApi.capability).toHaveBeenCalledWith('10')
    expect((wrapper.get('.ai-provider-secret-ref').element as HTMLInputElement).value).toBe('vault://ai/provider/v1')
    expect((wrapper.get('.ai-provider-model').element as HTMLInputElement).value).toBe('model-a')
    expect((wrapper.get('.ai-policy-modules').element as HTMLTextAreaElement).value).toBe('orders')
    expect((wrapper.get('.ai-policy-fields').element as HTMLTextAreaElement).value).toContain('orders.orderNo')
    expect((wrapper.get('.ai-policy-writable-fields').element as HTMLTextAreaElement).value).toContain('orders.amount')
    expect((wrapper.get('.ai-policy-fill-fields').element as HTMLTextAreaElement).value).toContain('orders.aiSummary')
    expect((wrapper.get('.ai-policy-confirmation-mode').element as HTMLInputElement).value).toContain('REQUIRED')
    expect((wrapper.get('.ai-policy-confirmation-expiry').element as HTMLInputElement).value).toBe('900')
    expect(wrapper.findAll('.ai-policy-operations input:checked')).toHaveLength(24)
    expect(wrapper.text()).toContain('CONFIG_FIELD_DRAFT（仅配置草稿）')
    expect(wrapper.text()).toContain('CONFIG_SELECTION_FIELD_DRAFT（选择字段草稿）')
    expect(wrapper.text()).toContain('CONFIG_PAGE_LAYOUT_DRAFT（页面布局草稿）')
    expect(wrapper.text()).toContain('CONFIG_FILTER_SCENARIO_DRAFT（共享筛选方案草稿）')
    expect(wrapper.text()).toContain('CONFIG_FIELD_PERMISSION_STAGE_DRAFT（字段权限 STAGED 草稿）')
    expect(wrapper.get('.ai-policy-config-filter-scenario-draft').element).toHaveProperty('checked', true)
    expect(wrapper.get('.ai-policy-config-field-permission-stage-draft').element).toHaveProperty('checked', true)
    expect(wrapper.text()).toContain('RECORD_CONTEXT_SUMMARY（只读）')
    expect(wrapper.text()).toContain('RECORD_COMMENT_QUERY（记录评论，只读）')
    expect(wrapper.text()).toContain('RECORD_HISTORY_QUERY（记录历史，只读）')
    expect(wrapper.text()).toContain('RECORD_FILE_QUERY（附件元数据，只读）')
    expect(wrapper.text()).toContain('WORK_TASK_QUERY（只读）')
    expect(wrapper.text()).toContain('WORK_DAILY_REPORT_QUERY（只读）')
    expect(wrapper.text()).toContain('WORK_PROJECT_METRICS_QUERY（项目进度与指标，只读）')
    expect(wrapper.text()).toContain('RUNTIME_STATISTICS_QUERY（已发布数据源统计与趋势，只读）')
    expect(wrapper.text()).toContain('RUNTIME_REPORT_QUERY（已发布报表，只读）')
    expect(wrapper.text()).toContain('FLOW_INSTANCE_HISTORY_QUERY（审批实例历史，只读）')
    expect(wrapper.get('.ai-policy-flow-instance-history-query').element).toHaveProperty('checked', true)
    expect(wrapper.text()).toContain('TODO_QUERY（当前成员待办，只读）')
    expect(wrapper.text()).toContain('MESSAGE_QUERY（当前成员消息，只读）')
    expect(wrapper.text()).toContain('WORK_TASK_DRAFT（确认后创建）')
    expect(wrapper.text()).toContain('WORK_DAILY_REPORT_DRAFT（确认后创建草稿）')
    expect(wrapper.text()).toContain('FLOW_DEFINITION_DRAFT（仅创建未发布定义）')
    expect(wrapper.text()).toContain('CONFIG_REPORT_DRAFT（仅创建未发布报表）')
    expect(wrapper.text()).toContain('CONFIG_PRINT_TEMPLATE_DRAFT（仅创建 DISABLED 模板）')
    expect((wrapper.get('.ai-policy-redaction').element as HTMLInputElement).value).toContain('STRICT')
    expect(wrapper.text()).toContain('策略版本 policy-2')
    expect(wrapper.text()).toContain('不接收明文 Provider 密钥')
  })

  it('rejects plaintext credentials and saves only a SecretRef provider configuration', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.ai-provider-secret-ref').setValue('env:AI_PROVIDER_SECRET')
    await wrapper.get('.ai-provider-save').trigger('click')
    expect(aiAdminApi.updateProvider).not.toHaveBeenCalled()
    expect(wrapper.get('.ai-admin-error').text()).toContain('scheme://reference')

    await wrapper.get('.ai-provider-secret-ref').setValue('vault://ai/provider/v2')
    await wrapper.get('.ai-provider-save').trigger('click')
    await flushPromises()
    expect(aiAdminApi.updateProvider).toHaveBeenCalledWith('10', 'provider-1', {
      expectedVersion: 2, code: 'openai-main', name: 'OpenAI compatible',
      baseUrl: 'https://ai.example/v1', model: 'model-a', secretRef: 'vault://ai/provider/v2',
      timeoutSeconds: 30, enabled: true,
    })
  })

  it('creates a Provider without asking the administrator for a technical ID', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.ai-provider-new').trigger('click')
    expect(wrapper.find('.ai-provider-id').exists()).toBe(false)
    expect(wrapper.text()).toContain('保存时由服务端生成')
    await wrapper.get('.ai-provider-code').setValue('provider-new')
    await wrapper.get('.ai-provider-name').setValue('Provider New')
    await wrapper.get('.ai-provider-base-url').setValue('https://new.example/v1')
    await wrapper.get('.ai-provider-model').setValue('model-new')
    await wrapper.get('.ai-provider-secret-ref').setValue('vault://ai/provider/new')
    await wrapper.get('.ai-provider-timeout').setValue('30')
    await wrapper.get('.ai-provider-save').trigger('click')
    await flushPromises()
    expect(aiAdminApi.createProvider).toHaveBeenCalledWith('10', {
      code: 'provider-new', name: 'Provider New', baseUrl: 'https://new.example/v1',
      model: 'model-new', secretRef: 'vault://ai/provider/new', timeoutSeconds: 30, enabled: true,
    })
  })

  it('saves grouped outbound fields, checks and publishes the versioned policy', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.ai-policy-modules').setValue('orders\ncustomers')
    await wrapper.get('.ai-policy-fields').setValue('orders.orderNo\norders.amount\ncustomers.name')
    await wrapper.get('.ai-policy-writable-fields').setValue('orders.amount\norders.status\ncustomers.name')
    await wrapper.get('.ai-policy-fill-fields').setValue('orders.aiSummary\ncustomers.aiSummary')
    await wrapper.get('.ai-policy-confirmation-expiry').setValue('1200')
    await wrapper.get('.ai-policy-max-rows').setValue('25')
    await wrapper.get('.ai-policy-save').trigger('click')
    await flushPromises()
    expect(aiAdminApi.savePolicy).toHaveBeenCalledWith('10', {
      expectedVersion: 3, providerId: 'provider-1', moduleCodes: ['orders', 'customers'],
      outboundFields: { orders: ['orderNo', 'amount'], customers: ['name'] }, maxRows: 25,
      allowedOperations: ['RECORD_QUERY', 'RECORD_CREATE', 'RECORD_UPDATE', 'AI_FILL', 'CONFIG_FIELD_DRAFT', 'CONFIG_SELECTION_FIELD_DRAFT', 'CONFIG_PAGE_LAYOUT_DRAFT', 'CONFIG_FILTER_SCENARIO_DRAFT', 'CONFIG_FIELD_PERMISSION_STAGE_DRAFT', 'RECORD_CONTEXT_SUMMARY', 'RECORD_COMMENT_QUERY', 'RECORD_HISTORY_QUERY', 'RECORD_FILE_QUERY', 'WORK_TASK_QUERY', 'WORK_DAILY_REPORT_QUERY', 'WORK_PROJECT_METRICS_QUERY', 'RUNTIME_STATISTICS_QUERY', 'RUNTIME_REPORT_QUERY', 'FLOW_INSTANCE_HISTORY_QUERY', 'WORK_TASK_DRAFT', 'WORK_DAILY_REPORT_DRAFT', 'FLOW_DEFINITION_DRAFT', 'CONFIG_REPORT_DRAFT', 'CONFIG_PRINT_TEMPLATE_DRAFT'],
      writableFields: { orders: ['amount', 'status'], customers: ['name'] },
      fillFields: { orders: ['aiSummary'], customers: ['aiSummary'] },
      confirmationMode: 'REQUIRED', confirmationExpiresSeconds: 1200,
      redactionMode: 'STRICT', promptVersion: 'v1', enabled: true,
    })

    await wrapper.get('.ai-policy-check').trigger('click')
    await flushPromises()
    expect(aiAdminApi.checkPolicy).toHaveBeenCalledWith('10')
    expect(wrapper.text()).toContain('检查结果：PASSED')
    await wrapper.get('.ai-policy-publish').trigger('click')
    await flushPromises()
    expect(aiAdminApi.publishPolicy).toHaveBeenCalledWith('10', 4)
    expect(aiAdminApi.capability).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('Agent 策略已发布')
  })

  it('requires writable-field scope and bounded expiry for write operations', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.ai-policy-writable-fields').setValue('')
    await wrapper.get('.ai-policy-save').trigger('click')
    expect(aiAdminApi.savePolicy).not.toHaveBeenCalled()
    expect(wrapper.get('.ai-admin-error').text()).toContain('必须配置至少一个可写字段')

    await wrapper.get('.ai-policy-writable-fields').setValue('orders.amount')
    await wrapper.get('.ai-policy-confirmation-expiry').setValue('59')
    await wrapper.get('.ai-policy-save').trigger('click')
    expect(aiAdminApi.savePolicy).not.toHaveBeenCalled()
    expect(wrapper.get('.ai-admin-error').text()).toContain('60 到 3600 秒')
  })

  it('requires AI_FILL operation and field scope to be enabled together', async () => {
    const wrapper = render()
    await flushPromises()
    await wrapper.get('.ai-policy-fill-fields').setValue('')
    await wrapper.get('.ai-policy-save').trigger('click')
    expect(aiAdminApi.savePolicy).not.toHaveBeenCalled()
    expect(wrapper.get('.ai-admin-error').text()).toContain('必须配置至少一个允许智能填充的字段')
  })
})
