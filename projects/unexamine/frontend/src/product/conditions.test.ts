import { describe, expect, it } from 'vitest'
import { bindingCondition, conditionFieldCodes, conditionIssues, conditionSummary, emptyCondition, newCondition, operatorsFor, type ConditionField } from './conditions'
import { publishedBindingPayload } from './flow-binding'
const fields: ConditionField[] = [{ code: 'reading', name: '仪表读数', fieldType: 'NUMBER', configJson: '{}' }, { code: 'running', name: '正常运行', fieldType: 'BOOLEAN', configJson: '{}' }]
describe('structured module trigger configuration', () => {
  it('preserves typed nested conditions and immutable input when reloading a binding', () => {
    const source = { mode: 'ALL' as const, conditions: [{ field: 'reading', operator: 'GT', value: 10 }, { mode: 'ANY' as const, conditions: [{ field: 'running', operator: 'EQ', value: false }] }] }
    const loaded = bindingCondition(source)
    expect(loaded).toEqual(source)
    loaded.conditions.pop()
    expect(source.conditions).toHaveLength(2)
    expect(conditionFieldCodes(source)).toEqual(['reading', 'running'])
    expect(conditionIssues(source, fields)).toEqual([])
  })
  it('requires repair of unavailable old references and does not display raw IDs or expression text', () => {
    const old = bindingCondition(undefined, 'amount > 10')
    expect(conditionIssues(old, fields)).toEqual(['请重新选择已发布且可读取的字段'])
    expect(conditionSummary(old, fields)).toBe('全部满足（不可用字段 大于 10）')
    expect(bindingCondition(undefined, 'amount ?? 10').conditions).toHaveLength(1)
    expect(conditionIssues(bindingCondition(undefined, 'amount ?? 10'), fields)).not.toEqual([])
  })
  it('offers typed operators and retains false and zero rather than treating them as blank', () => {
    expect(operatorsFor(fields[1]).map(item => item.value)).not.toContain('GT')
    expect(newCondition(fields[0]).value).toBe(0)
    expect(newCondition(fields[1]).value).toBe(false)
    expect(conditionIssues({ field: 'running', operator: 'EQ', value: false }, fields)).toEqual([])
    expect(conditionIssues({ field: 'reading', operator: 'IN', value: [] }, fields)).not.toEqual([])
  })
  it('allows direct configuration without selecting a Flow and transports other modes explicitly', () => {
    for (const executionMode of ['DIRECT', 'AFTER_EXECUTION', 'AFTER_APPROVAL', 'FLOW_ONLY']) {
      const payload = publishedBindingPayload({ moduleId: 30, flowId: 100, triggerEvent: 'CREATE', executionMode, priorityOrder: 0, condition: emptyCondition(), mutuallyExclusive: true })
      expect(payload.flowId).toBe(executionMode === 'DIRECT' ? undefined : 100)
      expect(payload.condition).toEqual(emptyCondition())
      expect(payload.conditionExpression).toBeUndefined()
      expect(payload.replaceExisting).toBe(false)
    }
  })
})
