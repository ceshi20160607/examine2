import { describe, expect, it } from 'vitest'
import { observedStageCount, readinessLabel, safeRequestId } from './operations-health'

describe('Cycle 53 operations health contract', () => {
  it('never presents an unavailable dependency set as ready', () => {
    expect(readinessLabel()).toBe('尚未体检')
    expect(readinessLabel({ ready: false } as never)).toBe('禁止宣告就绪')
    expect(readinessLabel({ ready: true } as never)).toBe('可接收业务流量')
  })

  it('validates searchable request IDs and counts only observed stages', () => {
    expect(safeRequestId('c53_trace_20260828')).toBe(true)
    expect(safeRequestId('short')).toBe(false)
    expect(observedStageCount({ stages: [
      { observed: true }, { observed: false }, { observed: true },
    ] } as never)).toBe(2)
  })
})
