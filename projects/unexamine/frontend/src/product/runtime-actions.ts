import { api, ApiError } from './api'

export interface ActionDelivery {
  status: 'NONE' | 'STARTED' | 'PENDING' | 'RETRYING'
  flowInstanceId: number | null
  actionId?: number
  mode?: BusinessActionResult<unknown>['mode']
  businessStatus?: string
  errorCode?: string | null
  message?: string | null
  recordId?: number | null
}

export interface BusinessActionResult<T> {
  idempotencyRecordId: number
  mode: 'DIRECT' | 'AFTER_EXECUTION' | 'AFTER_APPROVAL' | 'FLOW_ONLY'
  status: string
  replayed: boolean
  value: T | null
  flowInstanceId: number | null
  delivery?: ActionDelivery
}

/** Keep one request identity across a lost response; a changed form becomes a new action. */
export function createRuntimeAction() {
  let pending: { fingerprint: string; key: string } | undefined
  return async function execute<T>(path: string, method: string, body: unknown, token?: string): Promise<BusinessActionResult<T>> {
    const serialized = JSON.stringify(body)
    const fingerprint = JSON.stringify([token, path, method, serialized])
    if (pending?.fingerprint !== fingerprint) pending = { fingerprint, key: crypto.randomUUID() }
    const result = await executeRuntimeAction<T>(path, method, body, pending.key, token)
    pending = undefined
    return result
  }
}

/** Execute one record action with a caller-owned identity so batch retries can retain failed item keys. */
export async function executeRuntimeAction<T>(path: string, method: string, body: unknown, idempotencyKey: string, token?: string) {
    const result = await api<BusinessActionResult<T>>(path, {
      method, body: JSON.stringify(body), headers: { 'Idempotency-Key': idempotencyKey, Prefer: 'return=action' },
    }, token)
    // A receipt/read failure must not turn a committed write into a reported save failure.
    const receiptPath = `${path.slice(0, path.indexOf('/records'))}/records/action-results/${result.idempotencyRecordId}`
    result.delivery = await api<NonNullable<BusinessActionResult<T>['delivery']>>(receiptPath, {}, token).catch(() => undefined)
    return result
}

export interface RuntimeBatchItem<T> {
  recordId: number
  title: string
  status: 'SUCCESS' | 'FAILED'
  message: string
  result?: BusinessActionResult<T>
  failureKind?: 'REJECTED' | 'UNKNOWN_RESULT'
}

/** 各批量动作共用逐条请求身份；结果可以是目标记录包装，未知提交结果始终重试原请求。 */
export function createRuntimeBatchAction<T extends { id: number; title: string }, TResult = T>() {
  const pending = new Map<number, { path: string; body: unknown; key: string }>()
  return {
    clear: () => pending.clear(),
    async submit(targets: T[], previous: RuntimeBatchItem<TResult>[], options: {
      retryFailed?: boolean
      token: string
      request: (record: T) => { path: string; body: unknown }
      completed: string
    }): Promise<RuntimeBatchItem<TResult>[]> {
      const results = new Map(previous.map(item => [item.recordId, item]))
      for (const record of targets) {
        const old = results.get(record.id)
        if (old?.status === 'SUCCESS' || (options.retryFailed && old?.status !== 'FAILED')) continue
        let request = pending.get(record.id)
        if (!request) {
          const input = options.request(record)
          request = { path: input.path, body: JSON.parse(JSON.stringify(input.body)), key: crypto.randomUUID() }
          pending.set(record.id, request)
        }
        try {
          const result = await executeRuntimeAction<TResult>(request.path, 'POST', request.body, request.key, options.token)
          results.set(record.id, { recordId: record.id, title: record.title, status: 'SUCCESS',
            message: businessActionMessage(result, options.completed), result })
          pending.delete(record.id)
        } catch (reason) {
          // 明确拒绝意味着未提交；校验修正后可使用新版本。超时、限流及服务器故障仍按未知结果保留原请求。
          const rejected = reason instanceof ApiError && (reason.code === 'RECORD_VERSION_CONFLICT'
            || Boolean(reason.httpStatus && reason.httpStatus >= 400 && reason.httpStatus < 500 && ![408, 429].includes(reason.httpStatus)))
          if (rejected) pending.delete(record.id)
          results.set(record.id, { recordId: record.id, title: record.title, status: 'FAILED',
            failureKind: rejected ? 'REJECTED' : 'UNKNOWN_RESULT',
            message: reason instanceof Error ? reason.message : '操作失败，请查看处理结果' })
        }
      }
      return targets.map(record => results.get(record.id)).filter((item): item is RuntimeBatchItem<TResult> => Boolean(item))
    },
  }
}

export function businessActionMessage(result: BusinessActionResult<unknown>, completed: string): string {
  if (result.mode === 'FLOW_ONLY') return '流程已发起，业务数据未变更'
  if (result.mode === 'AFTER_APPROVAL') {
    const status = result.delivery?.businessStatus || result.status
    if (status === 'EXECUTED') return `审批已通过，${completed}`
    if (status === 'REJECTED') return '审批已拒绝，业务数据未变更'
    if (status === 'WITHDRAWN') return '审批已撤回，业务数据未变更'
    if (status === 'TERMINATED' || status === 'RETURNED') return '审批已结束，业务数据未变更'
    if (status === 'CONFLICT' || status === 'EXECUTION_FAILED') return '审批已通过，但业务变更未生效，请查看处理结果'
    return '已提交审批，业务变更尚未生效'
  }
  if (result.mode === 'AFTER_EXECUTION') {
    if (result.delivery?.status === 'STARTED') return `${completed}，流程已发起`
    if (result.delivery?.status === 'RETRYING') return `${completed}；流程暂未发起，系统将自动重试，无需重复提交`
    return `${completed}，流程发起请求已保存，请稍后查看`
  }
  return completed
}

export function conversionTargetCodes(configJson?: string): string[] {
  try {
    const config = JSON.parse(configJson || '{}') as {
      targetModuleCode?: unknown
      targets?: Array<{ moduleCode?: unknown }>
    }
    const candidates = Array.isArray(config.targets)
      ? config.targets.map((item) => item.moduleCode)
      : [config.targetModuleCode]
    return [...new Set(candidates
      .filter((value): value is string => typeof value === 'string')
      .map((value) => value.trim())
      .filter((value) => /^[a-z][a-z0-9_]{1,99}$/.test(value)))]
  } catch {
    return []
  }
}
