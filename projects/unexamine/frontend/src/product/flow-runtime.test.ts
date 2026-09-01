import { describe, expect, it } from 'vitest'
import { parseRuntimeVariables, runtimeStatusLabel } from './flow-runtime'

describe('flow runtime presentation', () => {
  it('parses only object variables', () => {
    expect(parseRuntimeVariables('{"amount":8000}')).toEqual({ amount: 8000 })
    expect(() => parseRuntimeVariables('[1]')).toThrow('JSON 对象')
  })
  it('uses readable runtime statuses without hiding unknown states', () => {
    expect(runtimeStatusLabel('WAITING')).toBe('待审批')
    expect(runtimeStatusLabel('CUSTOM')).toBe('CUSTOM')
  })
})
