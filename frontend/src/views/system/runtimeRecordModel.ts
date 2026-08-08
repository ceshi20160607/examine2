import { ApiRequestError } from '@/services/api'
import type { RuntimeFieldValue } from '@/types/config'

export const derivedRuntimeFieldTypes = new Set(['FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE'])

export interface DerivedRuntimeEnvelope {
  result?: unknown
  recalculationState?: string
  evaluatorVersion?: number
  dependencyVersions?: unknown[]
  failureCorrelationId?: string
}

export function derivedRuntimeEnvelope(field?: RuntimeFieldValue): DerivedRuntimeEnvelope | undefined {
  if (!field || !derivedRuntimeFieldTypes.has(field.type) || !field.value || typeof field.value !== 'object') return undefined
  return field.value as DerivedRuntimeEnvelope
}

export function derivedRuntimeState(field?: RuntimeFieldValue): 'READY' | 'PENDING' | 'FAILED' {
  const state = String(derivedRuntimeEnvelope(field)?.recalculationState ?? 'READY')
  return state === 'PENDING' || state === 'FAILED' ? state : 'READY'
}

export function derivedRuntimeResult(field?: RuntimeFieldValue): unknown {
  return derivedRuntimeEnvelope(field)?.result
}

export function derivedFailureCorrelationId(field?: RuntimeFieldValue): string {
  return field?.failureCorrelationId ?? String(derivedRuntimeEnvelope(field)?.failureCorrelationId ?? '')
}

export function displayRuntimeValue(field?: RuntimeFieldValue): string {
  if (!field) return '-'
  if (field.displayValue !== undefined && field.displayValue !== '') return field.displayValue
  if ((field.type === 'REFERENCE' || derivedRuntimeFieldTypes.has(field.type)) && field.value && typeof field.value === 'object') {
    const result = (field.value as Record<string, unknown>).result
    if (Array.isArray(result)) return result.length ? result.join(', ') : '-'
    return result === null || result === undefined || result === '' ? '-' : String(result)
  }
  if (field.value === null || field.value === undefined || field.value === '') return '-'
  if (Array.isArray(field.value)) return field.value.join(', ')
  return String(field.value)
}

export function runtimeValueByCode(values: RuntimeFieldValue[], fieldCode: string): string {
  return displayRuntimeValue(values.find((item) => item.fieldCode === fieldCode))
}

export function runtimeStatusLabel(status: string): string {
  return ({ DRAFT: '草稿', ACTIVE: '使用中', ARCHIVED: '已归档', TRASHED: '回收站', EXPIRED: '已过期' } as Record<string, string>)[status] ?? status
}

export interface SingleFlightSaveCoordinator<T> {
  request(snapshot: T): Promise<void>
  isSaving(): boolean
  hasPending(): boolean
}

export function createSingleFlightSaveCoordinator<T>(
  save: (snapshot: T) => Promise<void>,
): SingleFlightSaveCoordinator<T> {
  let pending: T | undefined
  let draining: Promise<void> | undefined

  async function drain() {
    try {
      while (pending !== undefined) {
        const next = pending
        pending = undefined
        await save(next)
      }
    } finally {
      draining = undefined
    }
  }

  return {
    request(snapshot) {
      pending = snapshot
      draining ??= drain()
      return draining
    },
    isSaving: () => draining !== undefined,
    hasPending: () => pending !== undefined,
  }
}

export function runtimeErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof ApiRequestError)) return fallback
  if (error.code === 'PERMISSION_DENIED') return '当前成员没有查看此模块记录的权限。'
  if (error.code === 'RECORD_NOT_FOUND') return '记录不存在，或当前成员无权查看。'
  if (error.code === 'FIELD_RUNTIME_UNAVAILABLE') return '此模块的字段结构暂不可用于记录读取。'
  if (error.code === 'RECORD_VALIDATION_FAILED') return error.message || '请检查表单中的字段。'
  if (error.code === 'FIELD_VALUE_INVALID') return error.message || '字段值格式或范围无效，请检查后重试。'
  if (error.code === 'FIELD_UNIQUE_CONFLICT') return '此字段值已被使用，请更换后重试。'
  if (error.code === 'RECORD_FIELD_FORBIDDEN') return '当前成员不能填写这个字段。'
  if (error.code === 'FIELD_WRITE_FORBIDDEN') return '当前成员不能填写这个字段。'
  if (error.code === 'RECORD_SCHEMA_STALE') return '字段结构已更新，请刷新后重试。'
  if (error.code === 'RECORD_VERSION_CONFLICT') return '记录已被其他操作更新，请选择重新加载或复制为新草稿。'
  if (error.code === 'RECORD_STATE_INVALID') return '当前记录状态不能执行此操作。'
  if (error.code === 'IDEMPOTENCY_CONFLICT') return '操作请求已变化，请重新发起。'
  if (error.code === 'QUERY_INVALID') return error.message || '查询条件无效，请检查筛选值。'
  if (error.code === 'QUERY_FIELD_UNAVAILABLE') return '当前成员不能使用此字段筛选或排序。'
  if (error.code === 'RECORD_RELATION_INVALID') return error.message || '关联记录无效、不可见或已发生变化，请重新搜索。'
  if (error.code === 'SUBTABLE_ROW_INVALID') return error.message || '明细行已发生变化或内容无效，请重新加载。'
  if (error.code === 'RECALCULATION_FAILED') return '派生或引用字段重新计算失败，请在详情中重试。'
  if (error.code === 'RECALCULATION_NOT_RETRYABLE') return '重新计算状态已变化，请刷新详情后确认最新结果。'
  return error.message || fallback
}
