import { describe, expect, it } from 'vitest'
import { parseManualStatusMappings } from './flow-runtime'
import { todoActionLabel, todoNeedsComment, todoNeedsTarget, todoStatusLabel, todoTypeLabel } from './todo'

describe('Cycle 39 interaction state', () => {
  it('parses explicit Flow business status mappings without changing the published definition', () => {
    expect(parseManualStatusMappings('{"approve":{"businessAction":"APPROVE","targetStatus":"APPROVED"}}')).toEqual({
      APPROVE: { businessAction: 'APPROVE', targetStatus: 'APPROVED' },
    })
    expect(() => parseManualStatusMappings('[]')).toThrow('状态映射必须是 JSON 对象')
  })

  it('keeps todo source, status and action semantics explicit', () => {
    expect(todoStatusLabel('PENDING')).toBe('待处理')
    expect(todoStatusLabel('SUSPENDED')).toBe('加签暂停')
    expect(todoTypeLabel('APPROVAL')).toBe('审批')
    expect(todoActionLabel('TRANSFER')).toBe('转交')
    expect(todoNeedsComment('REJECT')).toBe(true)
    expect(todoNeedsTarget('TRANSFER')).toBe(true)
  })
})
