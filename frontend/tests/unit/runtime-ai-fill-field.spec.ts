import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { aiFillApi } from '@/services/aiFill'
import type { AiFillProposal } from '@/types/aiFill'
import type { RuntimeFieldCapability } from '@/types/config'
import RuntimeAiFillField from '@/views/system/RuntimeAiFillField.vue'

vi.mock('@/services/aiFill', () => ({ aiFillApi: {
  createProposal: vi.fn(), proposal: vi.fn(), confirm: vi.fn(), reject: vi.fn(),
} }))

const field: RuntimeFieldCapability = {
  fieldCode: 'ai_summary', fieldName: 'AI 摘要', logicalFieldId: '501', type: 'AI_FILL',
  mode: 'READ_ONLY', readable: true, writable: false, sensitiveReadable: false,
  sensitiveQueryable: false, masked: false, operators: [], sortable: false,
  showInList: false, showInDetail: true, options: [], schema: {
    resultSchema: 'STRING', minConfidence: 0.8, overwriteMode: 'CONFIRM',
  },
}
const pending: AiFillProposal = {
  id: 'proposal-1', state: 'PENDING', moduleCode: 'orders', recordId: 'record-1',
  fieldCode: 'ai_summary', fieldName: 'AI 摘要', resultSchema: 'STRING',
  sources: [
    { fieldCode: 'title', fieldName: '标题', displayValue: '订单一', masked: false, sourceVersion: 2 },
    { fieldCode: 'secret_hint', fieldName: '敏感提示', displayValue: null, masked: true, sourceVersion: 2 },
  ],
  beforeDisplayValue: '旧摘要', afterDisplayValue: '新摘要', confidence: 0.92,
  clarification: null, overwrite: true, expiresAt: '2099-08-04T01:00:00Z',
  version: 3, result: null, errorCode: null,
}

function render(overrides: Record<string, unknown> = {}) {
  return mount(RuntimeAiFillField, {
    props: {
      systemId: '10', moduleCode: 'orders', recordId: 'record-1', recordVersion: 7,
      field, value: { fieldCode: 'ai_summary', fieldName: 'AI 摘要', type: 'AI_FILL', displayValue: '旧摘要' },
      ...overrides,
    },
    global: { stubs: {
      'a-button': { inheritAttrs: false, props: ['disabled', 'loading'], emits: ['click'], template: '<button v-bind="$attrs" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>' },
      'a-alert': { props: ['message'], template: '<div class="alert-stub">{{ message }}</div>' },
      'a-tag': { template: '<span><slot /></span>' },
    } },
  })
}

describe('RuntimeAiFillField', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(aiFillApi.createProposal).mockResolvedValue(pending)
    vi.mocked(aiFillApi.proposal).mockResolvedValue(pending)
    vi.mocked(aiFillApi.confirm).mockResolvedValue({
      ...pending, state: 'SUCCEEDED', version: 4,
      result: {
        materializationId: 'materialization-1', recordId: 'record-1', recordVersion: 8,
        fieldCode: 'ai_summary', displayValue: '新摘要', confidence: 0.92,
      },
    })
    vi.mocked(aiFillApi.reject).mockResolvedValue({ ...pending, state: 'REJECTED', version: 4 })
  })

  it('generates one safe preview, blocks duplicate clicks and confirms one real materialization', async () => {
    let resolveGenerate!: (value: AiFillProposal) => void
    vi.mocked(aiFillApi.createProposal).mockReturnValue(new Promise(resolve => { resolveGenerate = resolve }))
    const wrapper = render()
    expect(wrapper.text()).toContain('旧摘要')

    await wrapper.get('.ai-fill-generate').trigger('click')
    await wrapper.get('.ai-fill-generate').trigger('click')
    expect(aiFillApi.createProposal).toHaveBeenCalledTimes(1)
    expect(aiFillApi.createProposal).toHaveBeenCalledWith(
      '10', 'orders', 'record-1', 'ai_summary', { expectedRecordVersion: 7 }, expect.any(String),
    )
    resolveGenerate(pending)
    await flushPromises()

    const preview = wrapper.get('.ai-fill-proposal')
    expect(preview.text()).toContain('旧摘要')
    expect(preview.text()).toContain('新摘要')
    expect(preview.text()).toContain('92%（最低 80%）')
    expect(preview.text()).toContain('覆盖提醒')
    expect(preview.text()).toContain('标题')
    expect(preview.text()).toContain('订单一')
    expect(preview.text()).toContain('已脱敏')

    let resolveConfirm!: (value: AiFillProposal) => void
    vi.mocked(aiFillApi.confirm).mockReturnValue(new Promise(resolve => { resolveConfirm = resolve }))
    await wrapper.get('.ai-fill-confirm').trigger('click')
    await wrapper.get('.ai-fill-confirm').trigger('click')
    expect(aiFillApi.confirm).toHaveBeenCalledTimes(1)
    expect(aiFillApi.confirm).toHaveBeenCalledWith(
      '10', 'orders', 'record-1', 'ai_summary', 'proposal-1', 3, expect.any(String),
    )
    resolveConfirm({
      ...pending, state: 'SUCCEEDED', version: 4,
      result: {
        materializationId: 'materialization-1', recordId: 'record-1', recordVersion: 8,
        fieldCode: 'ai_summary', displayValue: '新摘要', confidence: 0.92,
      },
    })
    await flushPromises()
    expect(wrapper.get('.ai-fill-result').text()).toContain('业务模块真实回读')
    expect(wrapper.get('.ai-fill-result').text()).toContain('materialization-1')
    expect(wrapper.get('.ai-fill-result').text()).toContain('记录版本8')
    expect(wrapper.emitted('materialized')?.[0]).toEqual([expect.objectContaining({ recordVersion: 8 })])
  })

  it('keeps clarification/low-confidence proposals non-writable', async () => {
    vi.mocked(aiFillApi.createProposal).mockResolvedValue({
      ...pending, state: 'CLARIFICATION_REQUIRED', confidence: 0.61,
      clarification: '请补充订单用途', overwrite: false,
    })
    const wrapper = render({ value: undefined })
    await wrapper.get('.ai-fill-generate').trigger('click')
    await flushPromises()
    expect(wrapper.get('.ai-fill-clarification').text()).toContain('请补充订单用途')
    expect(wrapper.get('.ai-fill-proposal').text()).toContain('需要补充信息')
    expect(wrapper.find('.ai-fill-confirm').exists()).toBe(false)
    expect(wrapper.find('.ai-fill-reject').exists()).toBe(false)
    expect(aiFillApi.confirm).not.toHaveBeenCalled()
  })

  it('persists explicit rejection once and never materializes the proposed value', async () => {
    const wrapper = render()
    await wrapper.get('.ai-fill-generate').trigger('click')
    await flushPromises()
    let resolveReject!: (value: AiFillProposal) => void
    vi.mocked(aiFillApi.reject).mockReturnValue(new Promise(resolve => { resolveReject = resolve }))
    const rejectButton = wrapper.get('.ai-fill-reject')
    await rejectButton.trigger('click')
    await rejectButton.trigger('click')
    resolveReject({ ...pending, state: 'REJECTED', version: 4 })
    await flushPromises()
    expect(aiFillApi.reject).toHaveBeenCalledTimes(1)
    expect(aiFillApi.reject).toHaveBeenCalledWith(
      '10', 'orders', 'record-1', 'ai_summary', 'proposal-1', 3, expect.any(String),
    )
    expect(wrapper.get('.ai-fill-proposal').text()).toContain('已拒绝')
    expect(wrapper.find('.ai-fill-result').exists()).toBe(false)
    expect(wrapper.emitted('materialized')).toBeUndefined()
  })

  it('refreshes a stale-source failure and never reports it as a successful write', async () => {
    vi.mocked(aiFillApi.confirm).mockRejectedValue(new Error('SOURCE_VERSION_CHANGED'))
    vi.mocked(aiFillApi.proposal).mockResolvedValue({
      ...pending, state: 'FAILED', version: 4, errorCode: 'SOURCE_VERSION_CHANGED',
    })
    const wrapper = render()
    await wrapper.get('.ai-fill-generate').trigger('click')
    await flushPromises()
    await wrapper.get('.ai-fill-confirm').trigger('click')
    await flushPromises()
    expect(aiFillApi.proposal).toHaveBeenCalledWith(
      '10', 'orders', 'record-1', 'ai_summary', 'proposal-1',
    )
    expect(wrapper.get('.ai-fill-error').text()).toContain('来源字段已变化')
    expect(wrapper.get('.ai-fill-proposal').text()).toContain('物化失败')
    expect(wrapper.find('.ai-fill-result').exists()).toBe(false)
    expect(wrapper.emitted('materialized')).toBeUndefined()
  })
})
