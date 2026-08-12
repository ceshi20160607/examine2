import { describe, expect, it } from 'vitest'

import { ApiRequestError } from '../../src/services/api'
import {
  createSingleFlightSaveCoordinator,
  derivedFailureCorrelationId,
  derivedRuntimeResult,
  derivedRuntimeState,
  displayRuntimeValue,
  runtimeErrorMessage,
  runtimeStatusLabel,
  runtimeValueByCode,
} from '../../src/views/system/runtimeRecordModel'

describe('runtime record model', () => {
  it('prefers server display values and keeps missing values explicit', () => {
    const values = [
      { fieldCode: 'owner', fieldName: '负责人', type: 'MEMBER', value: '9', displayValue: '张三' },
      { fieldCode: 'amount', fieldName: '金额', type: 'NUMBER', value: 125.5 },
    ]

    expect(runtimeValueByCode(values, 'owner')).toBe('张三')
    expect(runtimeValueByCode(values, 'amount')).toBe('125.5')
    expect(runtimeValueByCode(values, 'missing')).toBe('-')
    expect(displayRuntimeValue({ fieldCode: 'tags', fieldName: '标签', type: 'TEXT', value: ['A', 'B'] }))
      .toBe('A, B')
  })

  it('maps runtime states and guarded API failures to user-facing text', () => {
    const denied = new ApiRequestError(403, {
      code: 'PERMISSION_DENIED', message: 'denied', data: null,
      requestId: 'request-1', traceId: 'trace-1', errors: [],
    })
    const hidden = new ApiRequestError(404, {
      code: 'RECORD_NOT_FOUND', message: 'not found', data: null,
      requestId: 'request-2', traceId: 'trace-2', errors: [],
    })
    const recalculation = new ApiRequestError(409, {
      code: 'RECALCULATION_FAILED', message: 'failed', data: null,
      requestId: 'request-3', traceId: 'trace-3', errors: [],
    })
    const staleSchema = new ApiRequestError(409, {
      code: 'RECORD_SCHEMA_STALE', message: 'stale schema', data: null,
      requestId: 'request-4', traceId: 'trace-4', errors: [],
    })
    const versionConflict = new ApiRequestError(409, {
      code: 'RECORD_VERSION_CONFLICT', message: 'version conflict', data: null,
      requestId: 'request-5', traceId: 'trace-5', errors: [],
    })

    expect(runtimeStatusLabel('ACTIVE')).toBe('使用中')
    expect(runtimeStatusLabel('ARCHIVED')).toBe('已归档')
    expect(runtimeStatusLabel('DRAFT')).toBe('草稿')
    expect(runtimeStatusLabel('TRASHED')).toBe('回收站')
    expect(runtimeStatusLabel('EXPIRED')).toBe('已过期')
    expect(runtimeErrorMessage(denied, 'fallback')).toContain('没有查看')
    expect(runtimeErrorMessage(hidden, 'fallback')).toContain('无权查看')
    expect(runtimeErrorMessage(recalculation, 'fallback')).toContain('派生或引用字段')
    expect(runtimeErrorMessage(staleSchema, 'fallback')).toContain('字段结构已更新')
    expect(runtimeErrorMessage(versionConflict, 'fallback')).toContain('记录已被其他操作更新')
    expect(runtimeErrorMessage(new Error('network'), '加载失败')).toBe('加载失败')
  })

  it('unwraps derived READY, PENDING and FAILED envelopes while retaining last-valid results', () => {
    const ready = { fieldCode: 'total', fieldName: '总额', type: 'FORMULA', value: {
      result: 125.5, recalculationState: 'READY', evaluatorVersion: 1,
    } }
    const pending = { fieldCode: 'names', fieldName: '客户', type: 'LOOKUP', value: {
      result: ['甲', '乙'], recalculationState: 'PENDING', evaluatorVersion: 1,
    } }
    const failed = { fieldCode: 'count', fieldName: '数量', type: 'SUMMARY', value: {
      result: 7, recalculationState: 'FAILED', failureCorrelationId: 'failure-safe-1',
    } }

    expect(displayRuntimeValue(ready)).toBe('125.5')
    expect(displayRuntimeValue(pending)).toBe('甲, 乙')
    expect(displayRuntimeValue(failed)).toBe('7')
    expect(derivedRuntimeState(ready)).toBe('READY')
    expect(derivedRuntimeState(pending)).toBe('PENDING')
    expect(derivedRuntimeState(failed)).toBe('FAILED')
    expect(derivedRuntimeResult(pending)).toEqual(['甲', '乙'])
    expect(derivedFailureCorrelationId(failed)).toBe('failure-safe-1')
  })

  it('runs one save at a time and coalesces later edits to the newest snapshot', async () => {
    const started: string[] = []
    const completed: string[] = []
    let releaseFirst: (() => void) | undefined
    const firstGate = new Promise<void>((resolve) => { releaseFirst = resolve })
    const coordinator = createSingleFlightSaveCoordinator<string>(async (value) => {
      started.push(value)
      if (value === 'first') await firstGate
      completed.push(value)
    })

    const first = coordinator.request('first')
    const second = coordinator.request('second')
    const third = coordinator.request('third')

    expect(started).toEqual(['first'])
    expect(coordinator.isSaving()).toBe(true)
    expect(coordinator.hasPending()).toBe(true)
    releaseFirst?.()
    await Promise.all([first, second, third])

    expect(started).toEqual(['first', 'third'])
    expect(completed).toEqual(['first', 'third'])
    expect(coordinator.isSaving()).toBe(false)
    expect(coordinator.hasPending()).toBe(false)
  })
})
