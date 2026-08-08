import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import type { RuntimeFieldCapability, RuntimeFieldValue } from '../../src/types/config'
import RuntimeDerivedField from '../../src/views/system/RuntimeDerivedField.vue'

function capability(type: string): RuntimeFieldCapability {
  return {
    fieldCode: type.toLowerCase(), fieldName: type, logicalFieldId: '101', type, mode: 'DERIVED',
    readable: true, writable: false, sensitiveReadable: true, sensitiveQueryable: true, masked: false,
    operators: [], sortable: false, showInList: true, showInDetail: true, options: [],
    schema: { derivedQueryType: type === 'LOOKUP' ? 'TEXT' : 'NUMBER' },
  }
}

function value(type: string, result: unknown, state: string, correlationId?: string): RuntimeFieldValue {
  return {
    fieldCode: type.toLowerCase(), fieldName: type, type,
    value: { result, recalculationState: state, evaluatorVersion: 1, failureCorrelationId: correlationId },
  }
}

function render(field: RuntimeFieldCapability, runtimeValue: RuntimeFieldValue, canRetry = false) {
  return mount(RuntimeDerivedField, {
    props: {
      systemId: '1', moduleCode: 'orders', recordId: '2', recordVersion: 3,
      field, value: runtimeValue, canRetry,
    },
    global: {
      stubs: {
        'a-button': { template: '<button><slot /></button>' },
        'a-alert': { template: '<div class="alert" />' },
      },
    },
  })
}

describe('runtime derived field', () => {
  it('keeps LOOKUP order and wraps each item as a separate value', () => {
    const wrapper = render(capability('LOOKUP'), value('LOOKUP', ['客户甲', '客户乙', '客户甲'], 'READY'))
    expect(wrapper.findAll('.lookup-chip').map((item) => item.text())).toEqual(['客户甲', '客户乙', '客户甲'])
    expect(wrapper.text()).toContain('READY · 已计算')
  })

  it('labels PENDING last-valid semantics without exposing retry', () => {
    const wrapper = render(capability('SUMMARY'), value('SUMMARY', 7, 'PENDING'), true)
    expect(wrapper.text()).toContain('7')
    expect(wrapper.text()).toContain('PENDING · 正在重新计算')
    expect(wrapper.text()).toContain('当前展示上次有效值')
    expect(wrapper.find('button').exists()).toBe(false)
  })

  it('shows FAILED correlation and retry only with the granted capability', () => {
    const denied = render(capability('AGGREGATE'), value('AGGREGATE', 18.5, 'FAILED', 'safe-incident-1'))
    expect(denied.text()).toContain('FAILED · 重新计算失败')
    expect(denied.text()).toContain('safe-incident-1')
    expect(denied.find('button').exists()).toBe(false)

    const granted = render(capability('AGGREGATE'), value('AGGREGATE', 18.5, 'FAILED', 'safe-incident-1'), true)
    expect(granted.find('button').text()).toContain('重试')
  })

  it('distinguishes permission-hidden values from a READY null result', () => {
    const hidden = mount(RuntimeDerivedField, {
      props: { systemId: '1', moduleCode: 'orders', recordId: '2', recordVersion: 3, field: capability('LOOKUP') },
      global: { stubs: { 'a-button': true, 'a-alert': true } },
    })
    expect(hidden.text()).toContain('当前无权查看此派生结果')

    const readyNull = render(capability('FORMULA'), value('FORMULA', null, 'READY'))
    expect(readyNull.text()).toContain('READY · 已计算')
    expect(readyNull.text()).not.toContain('无权查看')
  })
})
