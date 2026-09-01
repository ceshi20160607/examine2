import { describe, expect, it } from 'vitest'
import { canEditLog, revisionDifference, sortWorkLogs } from './work-log'
import type { WorkLog, WorkLogRevision } from './types'

describe('manual work log page state', () => {
  it('places the newest saved daily log first', () => {
    const rows = [
      { id: 1, workDate: '2026-08-21', updatedAt: '2026-08-21T12:00:00' },
      { id: 2, workDate: '2026-08-22', updatedAt: '2026-08-22T09:00:00' },
    ] as WorkLog[]
    expect(sortWorkLogs(rows).map(row => row.id)).toEqual([2, 1])
  })

  it('describes immutable revision differences and locks submitted content', () => {
    const first = { snapshot: { content: 'A', status: 'DRAFT' } } as unknown as WorkLogRevision
    const second = { snapshot: { content: 'B', status: 'SUBMITTED' } } as unknown as WorkLogRevision
    expect(revisionDifference(second, first)).toContain('content')
    expect(revisionDifference(second, first)).toContain('status')
    expect(canEditLog('SUBMITTED')).toBe(false)
    expect(canEditLog('WITHDRAWN')).toBe(true)
  })
})
