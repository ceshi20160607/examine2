import { afterEach, describe, expect, it, vi } from 'vitest'
import { conversionTargetCodes, createRuntimeAction, createRuntimeBatchAction, executeRuntimeAction, businessActionMessage } from './runtime-actions'

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

  it('retains a batch item key and uses the module receipt endpoint for a manual action', async () => {
    const fetcher = vi.fn().mockRejectedValueOnce(new TypeError('connection lost'))
      .mockResolvedValueOnce(response(result)).mockResolvedValueOnce(response({ status: 'STARTED', flowInstanceId: 40 }))
    vi.stubGlobal('fetch', fetcher)
    const manualPath = `${path}/10/actions/ACTION_REVIEW`
    await expect(executeRuntimeAction(manualPath, 'POST', { version: 2, values: { note: '复核完成' } }, 'item-key')).rejects.toThrow()
    await executeRuntimeAction(manualPath, 'POST', { version: 2, values: { note: '复核完成' } }, 'item-key')
    expect(fetcher.mock.calls[0]![1].headers.get('Idempotency-Key')).toBe('item-key')
    expect(fetcher.mock.calls[1]![1].headers.get('Idempotency-Key')).toBe('item-key')
    expect(fetcher.mock.calls[2]![0]).toBe(`${path}/action-results/7`)
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

describe('shared per-record batch submission', () => {
  const targets = [{ id: 1, title: '巡检一', version: 1 }, { id: 2, title: '巡检二', version: 1 }]
  const ok = (id: number) => new Response(JSON.stringify({ code: 'OK', data: {
    idempotencyRecordId: id, mode: 'DIRECT', status: 'COMPLETED', value: { id }, replayed: false,
  } }))
  const receipt = () => new Response(JSON.stringify({ code: 'OK', data: { status: 'NONE' } }))
  const options = (reason = '重复记录') => ({ token: 'token', completed: '记录已移入回收站',
    request: (record: typeof targets[number]) => ({ path: `/api/runtime/modules/check/records/${record.id}/delete`, body: { version: record.version, reason } }) })

  it('keeps partial success, refreshes only a rejected version and never submits a successful item again', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(ok(1)).mockResolvedValueOnce(receipt())
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'RECORD_VERSION_CONFLICT', message: '记录版本已变化' }), { status: 409 }))
      .mockResolvedValueOnce(ok(2)).mockResolvedValueOnce(receipt())
    vi.stubGlobal('fetch', fetcher)
    const batch = createRuntimeBatchAction<typeof targets[number]>()
    const first = await batch.submit(targets, [], options())
    expect(first.map(item => item.status)).toEqual(['SUCCESS', 'FAILED'])
    const final = await batch.submit([targets[0]!, { ...targets[1]!, version: 2 }], first, { ...options(), retryFailed: true })
    expect(final.map(item => item.status)).toEqual(['SUCCESS', 'SUCCESS'])
    const writes = fetcher.mock.calls.filter(call => String(call[0]).endsWith('/delete'))
    expect(writes.map(call => call[0])).toEqual(['/api/runtime/modules/check/records/1/delete', '/api/runtime/modules/check/records/2/delete', '/api/runtime/modules/check/records/2/delete'])
    expect(JSON.parse(writes[2]![1].body).version).toBe(2)
    expect(writes[1]![1].headers.get('Idempotency-Key')).not.toBe(writes[2]![1].headers.get('Idempotency-Key'))
  })

  it('retries an unknown result with the original body/path/key even if the list and form have changed', async () => {
    const fetcher = vi.fn().mockRejectedValueOnce(new TypeError('connection lost'))
      .mockResolvedValueOnce(ok(1)).mockResolvedValueOnce(receipt())
    vi.stubGlobal('fetch', fetcher)
    const batch = createRuntimeBatchAction<typeof targets[number]>()
    const first = await batch.submit([targets[0]!], [], options())
    await batch.submit([{ ...targets[0]!, version: 9 }], first, { ...options('已修改原因'), retryFailed: true })
    expect(fetcher.mock.calls[1]![0]).toBe(fetcher.mock.calls[0]![0])
    expect(fetcher.mock.calls[1]![1].body).toBe(fetcher.mock.calls[0]![1].body)
    expect(fetcher.mock.calls[1]![1].headers.get('Idempotency-Key')).toBe(fetcher.mock.calls[0]![1].headers.get('Idempotency-Key'))
  })

  it('retries corrected conversion validation with a new version/key while preserving targets and pending approval', async () => {
    const converted = { idempotencyRecordId: 11, mode: 'DIRECT', status: 'COMPLETED', value: { conversionId: 8, targetRecord: { id: 99 } } }
    const pending = { idempotencyRecordId: 12, mode: 'AFTER_APPROVAL', status: 'PENDING_APPROVAL', value: null }
    const fetcher = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ code: 'OK', data: converted })))
      .mockResolvedValueOnce(receipt())
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'CONVERSION_MAPPING_INVALID', message: '请填写客户名称' }), { status: 409 }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ code: 'OK', data: pending })))
      .mockResolvedValueOnce(receipt())
    vi.stubGlobal('fetch', fetcher)
    const batch = createRuntimeBatchAction<typeof targets[number], typeof converted.value>()
    const convertOptions = { token: 'token', completed: '转化已完成', request: (record: typeof targets[number]) => ({
      path: `/api/runtime/modules/check/records/${record.id}/convert`, body: { version: record.version, targetModuleCode: 'customer' },
    }) }
    const first = await batch.submit(targets, [], convertOptions)
    expect(first[0]?.result?.value?.targetRecord.id).toBe(99)
    expect(first[1]?.failureKind).toBe('REJECTED')
    const final = await batch.submit([targets[0]!, { ...targets[1]!, version: 2 }], first, { ...convertOptions, retryFailed: true })
    expect(final[1]?.message).toContain('尚未生效')
    await batch.submit(targets, final, { ...convertOptions, retryFailed: true })
    const writes = fetcher.mock.calls.filter(call => String(call[0]).endsWith('/convert'))
    expect(writes.map(call => call[0])).toEqual(['/api/runtime/modules/check/records/1/convert', '/api/runtime/modules/check/records/2/convert', '/api/runtime/modules/check/records/2/convert'])
    expect(JSON.parse(writes[2]![1].body).version).toBe(2)
    expect(writes[2]![1].headers.get('Idempotency-Key')).not.toBe(writes[1]![1].headers.get('Idempotency-Key'))
  })

  it('keeps original conversion identity after a server error even when the record version changed', async () => {
    const fetcher = vi.fn().mockResolvedValueOnce(new Response(JSON.stringify({ code: 'SERVER_ERROR', message: '结果未知' }), { status: 500 }))
      .mockResolvedValueOnce(ok(1)).mockResolvedValueOnce(receipt())
    vi.stubGlobal('fetch', fetcher)
    const batch = createRuntimeBatchAction<typeof targets[number]>()
    const first = await batch.submit([targets[0]!], [], options())
    expect(first[0]?.failureKind).toBe('UNKNOWN_RESULT')
    await batch.submit([{ ...targets[0]!, version: 9 }], first, { ...options(), retryFailed: true })
    expect(fetcher.mock.calls[1]![1].body).toBe(fetcher.mock.calls[0]![1].body)
    expect(fetcher.mock.calls[1]![1].headers.get('Idempotency-Key')).toBe(fetcher.mock.calls[0]![1].headers.get('Idempotency-Key'))
  })
})
