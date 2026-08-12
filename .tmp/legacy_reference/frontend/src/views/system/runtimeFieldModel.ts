import type { RuntimeFieldCapability } from '@/types/config'

export type RuntimeDraftScalar = string | number | boolean | null

export interface RuntimeFilterValueDraft {
  value: RuntimeDraftScalar
  secondValue: RuntimeDraftScalar
  values: string[]
  currency: string
  empty: boolean
  pathSnapshotId: string
  params: Record<string, RuntimeDraftScalar>
}

export interface RuntimeJsonQueryPath {
  pathSnapshotId: string
  path: string
  type: 'STRING' | 'DECIMAL' | 'INTEGER' | 'BOOLEAN' | 'DATE' | 'DATETIME'
}

export interface RuntimeCascadeOption {
  value: string
  label: string
  children?: RuntimeCascadeOption[]
}

const numericTypes = new Set(['NUMBER', 'PERCENT', 'RATING', 'PROGRESS'])
const collectionOperators = new Set(['IN', 'HAS_ANY', 'HAS_ALL', 'NOT_ANY'])
const rangeOperators = new Set(['BETWEEN', 'OVERLAPS'])

function schemaString(field: RuntimeFieldCapability, key: string) {
  const value = field.schema[key]
  return typeof value === 'string' ? value.toUpperCase() : ''
}

export function runtimeCurrencies(field: RuntimeFieldCapability): string[] {
  const values: string[] = []
  const configured = field.schema.currencies
  if (Array.isArray(configured)) {
    for (const item of configured) {
      if (typeof item === 'string' && /^[A-Z]{3}$/u.test(item.toUpperCase())) values.push(item.toUpperCase())
    }
  }
  for (const key of ['currency', 'fixedCurrency']) {
    const value = schemaString(field, key)
    if (/^[A-Z]{3}$/u.test(value)) values.push(value)
  }
  const unique = [...new Set(values)]
  return unique.length ? unique : ['CNY']
}

export function runtimeFixedCurrency(field: RuntimeFieldCapability): string {
  return schemaString(field, 'fixedCurrency') || schemaString(field, 'currency')
}

export function runtimeJsonQueryPaths(field: RuntimeFieldCapability): RuntimeJsonQueryPath[] {
  if (!Array.isArray(field.schema.queryPaths)) return []
  return field.schema.queryPaths.flatMap((item) => {
    if (!item || typeof item !== 'object') return []
    const source = item as Record<string, unknown>
    const type = String(source.type ?? '') as RuntimeJsonQueryPath['type']
    const pathSnapshotId = String(source.pathSnapshotId ?? '')
    const path = String(source.path ?? '')
    if (!pathSnapshotId || !path || !['STRING', 'DECIMAL', 'INTEGER', 'BOOLEAN', 'DATE', 'DATETIME'].includes(type)) return []
    return [{ pathSnapshotId, path, type }]
  })
}

export function buildRuntimeCascadeOptions(field: RuntimeFieldCapability): RuntimeCascadeOption[] {
  const values = new Set(field.options.map((option) => option.value))
  const children = new Map<string | null, typeof field.options>()
  for (const option of field.options) {
    const parent = option.parentValue && values.has(option.parentValue) ? option.parentValue : null
    children.set(parent, [...(children.get(parent) ?? []), option])
  }
  const build = (parent: string | null, path: Set<string>): RuntimeCascadeOption[] =>
    (children.get(parent) ?? []).flatMap((option) => {
      if (path.has(option.value)) return []
      const nextPath = new Set(path).add(option.value)
      const nested = build(option.value, nextPath)
      return [{ value: option.value, label: option.label, ...(nested.length ? { children: nested } : {}) }]
    })
  return build(null, new Set())
}

export function normalizeRuntimeTime(value: unknown): string {
  const text = String(value ?? '').trim()
  return /^\d{2}:\d{2}$/u.test(text) ? `${text}:00` : text
}

function currencyFractionDigits(currency: string) {
  try {
    return new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2
  } catch {
    return 2
  }
}

export function normalizeRuntimeMoneyAmount(value: unknown, currency: string): string {
  const text = String(value ?? '').trim()
  if (!/^-?(?:0|[1-9]\d*)(?:\.\d*)?$/u.test(text)) return text
  const digits = currencyFractionDigits(currency)
  const [whole, fraction = ''] = text.split('.')
  if (fraction.length > digits) return text
  return digits === 0 ? whole! : `${whole}.${fraction.padEnd(digits, '0')}`
}

export function isBlankRuntimeValue(field: RuntimeFieldCapability, value: unknown): boolean {
  if (value === undefined || value === null || value === '') return true
  if (Array.isArray(value)) return value.length === 0 || value.every((item) => String(item ?? '').trim() === '')
  if (field.type === 'MONEY') {
    if (typeof value !== 'object') return true
    return String((value as Record<string, unknown>).amount ?? '').trim() === ''
  }
  if (['ADDRESS', 'GEO', 'BARCODE'].includes(field.type) && typeof value === 'object' && !Array.isArray(value)) {
    return Object.values(value as Record<string, unknown>).every((item) => item === null || item === undefined || String(item).trim() === '')
  }
  return false
}

function moneyParts(value: unknown) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return { amount: '', currency: '' }
  const source = value as Record<string, unknown>
  return { amount: String(source.amount ?? ''), currency: String(source.currency ?? '') }
}

export function readRuntimeFilterDraft(
  field: RuntimeFieldCapability,
  operator: string,
  source: unknown,
): RuntimeFilterValueDraft {
  const draft: RuntimeFilterValueDraft = {
    value: '', secondValue: '', values: [], currency: runtimeFixedCurrency(field) || runtimeCurrencies(field)[0]!, empty: true,
    pathSnapshotId: '', params: {},
  }
  if (operator === 'EMPTY') {
    draft.empty = source !== false
    return draft
  }
  if (field.type === 'MONEY') {
    const bounds = Array.isArray(source) ? source : []
    const first = moneyParts(rangeOperators.has(operator) ? bounds[0] : source)
    const second = moneyParts(bounds[1])
    draft.value = first.amount
    draft.secondValue = second.amount
    draft.currency = first.currency || second.currency || draft.currency
    return draft
  }
  if (field.type === 'ADDRESS' && operator === 'EQ_REGION' && source && typeof source === 'object') {
    const value = source as Record<string, unknown>
    draft.value = String(value.countryCode ?? '')
    draft.secondValue = String(value.regionCode ?? '')
    return draft
  }
  if (field.type === 'GEO' && source && typeof source === 'object') {
    const value = source as Record<string, unknown>
    for (const key of ['south', 'west', 'north', 'east', 'lat', 'lng', 'radiusMeters']) {
      const item = value[key]
      draft.params[key] = typeof item === 'number' ? item : String(item ?? '')
    }
    return draft
  }
  if (field.type === 'BARCODE') {
    const configured = Array.isArray(field.schema.symbologies) ? field.schema.symbologies.map(String) : ['CODE128']
    draft.currency = configured[0] ?? 'CODE128'
    if (typeof source === 'string') {
      const separator = source.indexOf(':')
      draft.currency = separator > 0 ? source.slice(0, separator) : draft.currency
      draft.value = separator > 0 ? source.slice(separator + 1) : source
    }
    return draft
  }
  if (field.type === 'JSON') {
    if (operator === 'DECLARED_PATH_EXISTS') draft.pathSnapshotId = String(source ?? '')
    else if (source && typeof source === 'object') {
      const value = source as Record<string, unknown>
      draft.pathSnapshotId = String(value.pathSnapshotId ?? '')
      draft.value = (value.value as RuntimeDraftScalar | undefined) ?? ''
    }
    if (!draft.pathSnapshotId) draft.pathSnapshotId = runtimeJsonQueryPaths(field)[0]?.pathSnapshotId ?? ''
    return draft
  }
  if (collectionOperators.has(operator)) {
    draft.values = Array.isArray(source) ? source.map(String) : []
    return draft
  }
  if (rangeOperators.has(operator)) {
    const values = Array.isArray(source) ? source : []
    draft.value = (values[0] as RuntimeDraftScalar | undefined) ?? ''
    draft.secondValue = (values[1] as RuntimeDraftScalar | undefined) ?? ''
    return draft
  }
  if (field.type === 'SWITCH') draft.value = source === true
  else draft.value = (source as RuntimeDraftScalar | undefined) ?? ''
  return draft
}

function normalizedScalar(field: RuntimeFieldCapability, value: RuntimeDraftScalar) {
  if (numericTypes.has(field.type)) {
    const number = Number(value)
    if (!Number.isFinite(number)) throw new Error(`${field.fieldName} 需要有效数字`)
    if (['PERCENT', 'PROGRESS'].includes(field.type) && (number < 0 || number > 100)) {
      throw new Error(`${field.fieldName} 必须在 0 到 100 之间`)
    }
    if (field.type === 'RATING') {
      const maximum = Number(field.schema.maxRating ?? 5)
      if (!Number.isInteger(number) || number < 1 || number > maximum) {
        throw new Error(`${field.fieldName} 必须是 1 到 ${maximum} 的整数`)
      }
    }
    return number
  }
  if (field.type === 'SWITCH') {
    if (typeof value !== 'boolean') throw new Error(`${field.fieldName} 需要是或否`)
    return value
  }
  if (field.type === 'SECRET') {
    const secret = String(value ?? '')
    if (!secret || secret.length > 4096) throw new Error(`${field.fieldName} 需要 1 到 4096 个字符`)
    return secret
  }
  const text = ['TIME', 'TIME_RANGE'].includes(field.type) ? normalizeRuntimeTime(value) : String(value ?? '').trim()
  if (!text) throw new Error(`${field.fieldName} 需要条件值`)
  return text
}

function requiredNumber(field: RuntimeFieldCapability, value: RuntimeDraftScalar, label: string, minimum: number, maximum: number) {
  const number = Number(value)
  if (!Number.isFinite(number) || number < minimum || number > maximum) {
    throw new Error(`${field.fieldName}${label}必须在 ${minimum} 到 ${maximum} 之间`)
  }
  return number
}

function jsonPathValue(field: RuntimeFieldCapability, pathId: string, value: RuntimeDraftScalar) {
  const path = runtimeJsonQueryPaths(field).find((item) => item.pathSnapshotId === pathId)
  if (!path) throw new Error(`${field.fieldName} 需要已声明的 JSON 路径`)
  if (path.type === 'BOOLEAN') {
    if (typeof value === 'boolean') return value
    if (value === 'true' || value === 'false') return value === 'true'
    throw new Error(`${field.fieldName} 路径值需要是布尔值`)
  }
  if (path.type === 'INTEGER') {
    const number = Number(value)
    if (!Number.isSafeInteger(number)) throw new Error(`${field.fieldName} 路径值需要是整数`)
    return number
  }
  if (path.type === 'DECIMAL') {
    const number = Number(value)
    if (!Number.isFinite(number)) throw new Error(`${field.fieldName} 路径值需要是数字`)
    return number
  }
  const text = String(value ?? '').trim()
  if (!text) throw new Error(`${field.fieldName} 需要路径值`)
  return text
}

export function runtimePredicateValue(
  field: RuntimeFieldCapability,
  operator: string,
  draft: RuntimeFilterValueDraft,
): unknown {
  if (operator === 'EMPTY') return draft.empty
  if (field.type === 'ADDRESS' && operator === 'EQ_REGION') {
    const countryCode = String(draft.value ?? '').trim().toUpperCase()
    const regionCode = String(draft.secondValue ?? '').trim().toUpperCase()
    if (!/^[A-Z]{2}$/u.test(countryCode) || !regionCode) throw new Error(`${field.fieldName} 需要国家代码和地区代码`)
    return { countryCode, regionCode }
  }
  if (field.type === 'GEO' && operator === 'WITHIN_BOX') {
    const south = requiredNumber(field, draft.params.south ?? '', '南纬边界', -90, 90)
    const west = requiredNumber(field, draft.params.west ?? '', '西经边界', -180, 180)
    const north = requiredNumber(field, draft.params.north ?? '', '北纬边界', -90, 90)
    const east = requiredNumber(field, draft.params.east ?? '', '东经边界', -180, 180)
    if (south >= north || west >= east) throw new Error(`${field.fieldName} 边界顺序无效`)
    return { south, west, north, east }
  }
  if (field.type === 'GEO' && operator === 'NEAR') {
    const lat = requiredNumber(field, draft.params.lat ?? '', '纬度', -90, 90)
    const lng = requiredNumber(field, draft.params.lng ?? '', '经度', -180, 180)
    const radiusMeters = requiredNumber(field, draft.params.radiusMeters ?? '', '半径', Number.MIN_VALUE, 500000)
    return { lat, lng, radiusMeters }
  }
  if (field.type === 'BARCODE') {
    const symbology = draft.currency || String(field.schema.symbologies instanceof Array ? field.schema.symbologies[0] ?? '' : '')
    const payload = String(draft.value ?? '').trim()
    if (!['CODE128', 'EAN13'].includes(symbology) || !payload) throw new Error(`${field.fieldName} 需要条码制式和内容`)
    return `${symbology}:${payload}`
  }
  if (field.type === 'JSON') {
    if (!runtimeJsonQueryPaths(field).some((item) => item.pathSnapshotId === draft.pathSnapshotId)) {
      throw new Error(`${field.fieldName} 需要已声明的 JSON 路径`)
    }
    if (operator === 'DECLARED_PATH_EXISTS') return draft.pathSnapshotId
    return { pathSnapshotId: draft.pathSnapshotId, value: jsonPathValue(field, draft.pathSnapshotId, draft.value) }
  }
  if (field.type === 'MONEY') {
    if (!runtimeCurrencies(field).includes(draft.currency)) throw new Error(`${field.fieldName} 需要有效币种`)
    const money = (value: RuntimeDraftScalar) => {
      const amount = normalizeRuntimeMoneyAmount(value, draft.currency)
      if (!amount) throw new Error(`${field.fieldName} 需要金额`)
      return { amount, currency: draft.currency }
    }
    return rangeOperators.has(operator) ? [money(draft.value), money(draft.secondValue)] : money(draft.value)
  }
  if (collectionOperators.has(operator)) {
    const values = [...new Set(draft.values.map((item) => item.trim()).filter(Boolean))]
    if (!values.length) throw new Error(`${field.fieldName} 至少选择一个值`)
    return values
  }
  if (rangeOperators.has(operator)) {
    return [normalizedScalar(field, draft.value), normalizedScalar(field, draft.secondValue)]
  }
  return normalizedScalar(field, draft.value)
}

export function isRuntimeCollectionOperator(operator: string) {
  return collectionOperators.has(operator)
}

export function isRuntimeRangeOperator(operator: string) {
  return rangeOperators.has(operator)
}
