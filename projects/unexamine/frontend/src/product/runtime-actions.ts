import { api } from './api'

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
    const result = await api<BusinessActionResult<T>>(path, {
      method, body: serialized, headers: { 'Idempotency-Key': pending.key, Prefer: 'return=action' },
    }, token)
    pending = undefined
    // A receipt/read failure must not turn a committed write into a reported save failure.
    const receiptPath = `${path.slice(0, path.indexOf('/records'))}/records/action-results/${result.idempotencyRecordId}`
    result.delivery = await api<NonNullable<BusinessActionResult<T>['delivery']>>(receiptPath, {}, token).catch(() => undefined)
    return result
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
