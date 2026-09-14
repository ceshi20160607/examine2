import { afterEach, describe, expect, it, vi } from 'vitest'
import { conversionTargetCodes, createRuntimeAction, businessActionMessage } from './runtime-actions'

afterEach(() => vi.unstubAllGlobals())

describe('shared runtime action submission', () => {
  const path = '/api/runtime/modules/customer/records'
  const result = { idempotencyRecordId: 7, mode: 'AFTER_EXECUTION' as const, status: 'COMPLETED',
    replayed: false, value: { id: 10 }, flowInstanceId: null }
  const response = (data: unknown) => new Response(JSON.stringify({ code: 'OK', data }), { status: 200 })

  it('retries a lost response with the same key and reads delivery without repeating the write', async () => {
    const fetcher = vi.fn().mockRejectedValueOnce(new TypeError('connection lost'))
      .mockResolvedValueOnce(response(result)).mockResolvedValueOnce(response({ status: 'RETRYING', flowInstanceId: null }))
    vi.stubGlobal('fetch', fetcher)
    const action = createRuntimeAction()
    await expect(action(path, 'POST', { title: 'Customer' }, 'token')).rejects.toThrow('connection lost')
    const saved = await action(path, 'POST', { title: 'Customer' }, 'token')
    expect(fetcher.mock.calls[0]![1].headers.get('Idempotency-Key')).toBe(fetcher.mock.calls[1]![1].headers.get('Idempotency-Key'))
    expect(fetcher.mock.calls[2]![0]).toBe(`${path}/action-results/7`)
    expect(businessActionMessage(saved, '业务数据已创建')).toContain('业务数据已创建；流程暂未发起')
  })

  it('a failed receipt lookup keeps the committed result successful; a later new action has a new key', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(response(result)).mockRejectedValueOnce(new TypeError('receipt offline'))
      .mockResolvedValueOnce(response(result)).mockResolvedValueOnce(response({ status: 'STARTED', flowInstanceId: 40 }))
    vi.stubGlobal('fetch', fetcher)
    const action = createRuntimeAction()
    expect((await action(path, 'POST', { title: 'Customer' })).value).toEqual({ id: 10 })
    await action(path, 'POST', { title: 'Customer' })
    expect(fetcher.mock.calls[0]![1].headers.get('Idempotency-Key')).not.toBe(fetcher.mock.calls[2]![1].headers.get('Idempotency-Key'))
  })

  it('only starting a flow cannot be described as a created business record', () => {
    expect(businessActionMessage({ ...result, mode: 'FLOW_ONLY', value: null, flowInstanceId: 30 }, '业务数据已创建'))
      .toBe('流程已发起，业务数据未变更')
  })

  it('distinguishes approval success from the business execution outcome', () => {
    const pending = { ...result, mode: 'AFTER_APPROVAL' as const, status: 'PENDING_APPROVAL', value: null }
    expect(businessActionMessage(pending, '业务数据已创建')).toContain('尚未生效')
    const conflict = { ...pending, delivery: { status: 'STARTED' as const, flowInstanceId: 40, businessStatus: 'CONFLICT' } }
    expect(businessActionMessage(conflict, '业务数据已创建')).toContain('业务变更未生效')
    expect(businessActionMessage({ ...conflict, delivery: { ...conflict.delivery, businessStatus: 'EXECUTED' } }, '业务数据已创建'))
      .toBe('审批已通过，业务数据已创建')
    expect(businessActionMessage({ ...pending, status: 'REJECTED' }, '业务数据已创建')).toContain('业务数据未变更')
  })
})

describe('runtime conversion actions', () => {
  it('extracts unique configured targets in their published order', () => {
    expect(conversionTargetCodes(JSON.stringify({ targets: [
      { moduleCode: 'opportunity' }, { moduleCode: 'order' }, { moduleCode: 'opportunity' },
    ] }))).toEqual(['opportunity', 'order'])
  })

  it('supports the compact single-target form and rejects invalid configuration', () => {
    expect(conversionTargetCodes('{"targetModuleCode":"lead"}')).toEqual(['lead'])
    expect(conversionTargetCodes('{broken')).toEqual([])
    expect(conversionTargetCodes('{"targetModuleCode":"Other System"}')).toEqual([])
  })
})
