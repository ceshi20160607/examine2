import type { RuntimeFilterNode, RuntimeRecordScope, RuntimeSortItem } from '@/types/config'

export interface RuntimeListState {
  page: number
  size: number
  recordScope: RuntimeRecordScope
  q: string
  filter: RuntimeFilterNode | null
  sort: RuntimeSortItem[]
  columns: string[]
  viewId: string
}

export const runtimeListQueryKeys = new Set([
  'module', 'viewId', 'q', 'page', 'size', 'recordScope', 'sort', 'filter', 'columns',
  'record', 'mode', 'draft',
])

export function defaultRuntimeListState(size = 50): RuntimeListState {
  return { page: 1, size, recordScope: 'active', q: '', filter: null, sort: [], columns: [], viewId: '' }
}

function scalar(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function positiveInteger(value: string, fallback: number, maximum: number) {
  if (!/^[1-9][0-9]*$/.test(value)) return { value: fallback, invalid: Boolean(value) }
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed <= maximum
    ? { value: parsed, invalid: false }
    : { value: fallback, invalid: true }
}

function encodeUtf8(value: string) {
  const bytes = new TextEncoder().encode(value)
  let binary = ''
  bytes.forEach((byte) => { binary += String.fromCharCode(byte) })
  return btoa(binary).replaceAll('+', '-').replaceAll('/', '_').replace(/=+$/u, '')
}

function decodeUtf8(value: string) {
  const normalized = value.replaceAll('-', '+').replaceAll('_', '/')
  const binary = atob(normalized + '='.repeat((4 - normalized.length % 4) % 4))
  return new TextDecoder('utf-8', { fatal: true }).decode(Uint8Array.from(binary, (char) => char.charCodeAt(0)))
}

function canonical(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(canonical)
  if (value && typeof value === 'object') {
    return Object.fromEntries(Object.entries(value as Record<string, unknown>)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, item]) => [key, canonical(item)]))
  }
  return value
}

export function canonicalJson(value: unknown) {
  return JSON.stringify(canonical(value))
}

export function encodeRuntimeState(value: unknown) {
  return encodeUtf8(canonicalJson(value))
}

function decodeRuntimeState<T>(value: string): T {
  return JSON.parse(decodeUtf8(value)) as T
}

function validSort(value: unknown): value is RuntimeSortItem[] {
  return Array.isArray(value) && value.length <= 3 && value.every((item) => {
    if (!item || typeof item !== 'object') return false
    const candidate = item as Partial<RuntimeSortItem>
    const currencyValid = candidate.currency === undefined
      || (typeof candidate.currency === 'string' && /^[A-Z]{3}$/u.test(candidate.currency))
    return currencyValid && typeof candidate.fieldCode === 'string'
      && ['ASC', 'DESC'].includes(String(candidate.direction))
      && ['FIRST', 'LAST'].includes(String(candidate.nulls))
  })
}

function validColumns(value: unknown): value is string[] {
  return Array.isArray(value) && new Set(value).size === value.length
    && value.every((item) => typeof item === 'string' && /^[A-Za-z][A-Za-z0-9_-]{0,63}$/u.test(item))
}

function validFilter(value: unknown, depth = 1): value is RuntimeFilterNode {
  if (!value || typeof value !== 'object' || depth > 5) return false
  const node = value as Record<string, unknown>
  if (node.kind === 'PREDICATE') {
    return typeof node.fieldCode === 'string' && typeof node.operator === 'string'
  }
  if (!['AND', 'OR', 'NOT'].includes(String(node.kind)) || !Array.isArray(node.children)) return false
  if (node.children.length < 1 || node.children.length > (node.kind === 'NOT' ? 1 : 20)) return false
  return node.children.every((child) => validFilter(child, depth + 1))
}

export function parseRuntimeListQuery(query: Record<string, unknown>, defaultSize = 50) {
  const state = defaultRuntimeListState(defaultSize)
  let invalid = Object.keys(query).some((key) => !runtimeListQueryKeys.has(key))
  const parsedPage = positiveInteger(scalar(query.page), 1, 1_000_000)
  const parsedSize = positiveInteger(scalar(query.size), defaultSize, 200)
  state.page = parsedPage.value
  state.size = parsedSize.value
  invalid ||= parsedPage.invalid || parsedSize.invalid
  const scope = scalar(query.recordScope)
  if (scope) {
    if (['active', 'archived', 'trash', 'draft'].includes(scope)) state.recordScope = scope as RuntimeRecordScope
    else invalid = true
  }
  const q = scalar(query.q).trim().replace(/\s+/gu, ' ')
  if (q) {
    if (q.length >= 2 && q.length <= 100) state.q = q
    else invalid = true
  }
  const viewId = scalar(query.viewId)
  if (viewId) {
    if (/^[1-9][0-9]{0,18}$/u.test(viewId)) state.viewId = viewId
    else invalid = true
  }
  for (const key of ['sort', 'columns', 'filter'] as const) {
    const encoded = scalar(query[key])
    if (!encoded) continue
    try {
      const decoded = decodeRuntimeState<unknown>(encoded)
      if (key === 'sort' && validSort(decoded)) state.sort = decoded
      else if (key === 'columns' && validColumns(decoded)) state.columns = decoded
      else if (key === 'filter' && validFilter(decoded)) state.filter = decoded
      else invalid = true
    } catch {
      invalid = true
    }
  }
  return { state, invalid }
}

export function runtimeListRouteQuery(moduleCode: string, state: RuntimeListState, context: Record<string, string> = {}) {
  const query: Record<string, string> = { module: moduleCode }
  if (state.viewId) query.viewId = state.viewId
  if (state.q) query.q = state.q
  if (state.page !== 1) query.page = String(state.page)
  if (state.size !== 50) query.size = String(state.size)
  if (state.recordScope !== 'active') query.recordScope = state.recordScope
  if (state.sort.length) query.sort = encodeRuntimeState(state.sort)
  if (state.filter) query.filter = encodeRuntimeState(state.filter)
  if (state.columns.length) query.columns = encodeRuntimeState(state.columns)
  Object.assign(query, context)
  if (new URLSearchParams(query).toString().length > 4096) throw new Error('URL_QUERY_TOO_LONG')
  return query
}
