import { describe, expect, it } from 'vitest'
import { activeBinding, bindingSourceLabel, publishedBindingPayload } from './flow-binding'
import type { FlowBindingView } from './types'

describe('Flow binding page state', () => {
  it('shows only the persisted active binding as effective', () => {
    const rows = [{ id: 1, status: 'DISABLED' }, { id: 2, status: 'ACTIVE' }] as FlowBindingView[]
    expect(activeBinding(rows)?.id).toBe(2)
  })

  it('keeps replacement explicit and source language unambiguous', () => {
    expect(bindingSourceLabel('DEFAULT')).toBe('系统公共配置')
    expect(bindingSourceLabel('TENANT_OVERRIDE')).toBe('当前工作空间配置')
    expect(publishedBindingPayload({ moduleId: 1, flowId: 2, triggerEvent: 'CREATE',
      executionMode: 'AFTER_EXECUTION', priorityOrder: 0, conditionExpression: '  amount > 10  ',
      mutuallyExclusive: true }, true)).toMatchObject({ conditionExpression: 'amount > 10', replaceExisting: true })
  })
})
