import { getRecord, queryRecords, queryRecordsByRelation } from '@/api/records'
import { listRelationsByApp, type ModuleRelation } from '@/api/meta'
import type { FieldLike } from '@/utils/fieldTypes'
import { configFromMeta } from '@/utils/fieldTypes'
import { hasId, idToString, sameId, uniqueIds, type IdValue } from '@/utils/id'

export function recordDisplayLabel(
  detail: any,
  displayField?: string | null,
  inlineData?: Record<string, unknown>
): string {
  const data = inlineData || detail?.data || {}
  const id = detail?.record?.id
  if (displayField) {
    const v = data[displayField]
    if (v != null && String(v).trim()) return String(v)
  }
  for (const k of Object.keys(data)) {
    const v = data[k]
    if (v == null || v === '') continue
    if (typeof v === 'object') continue
    return String(v)
  }
  return id ? `#${id}` : '-'
}

export function fieldIncludeCodes(field: FieldLike): string[] {
  const cfg = configFromMeta(field)
  const codes: string[] = []
  const show = field.refDisplayField
  if (show) codes.push(String(show))
  const listFields = cfg.listFields as string[] | undefined
  if (Array.isArray(listFields)) {
    for (const c of listFields) {
      if (c && !codes.includes(c)) codes.push(String(c))
    }
  }
  return codes.slice(0, 30)
}

export async function batchLoadRecordDataMap(
  appId: IdValue,
  modelId: IdValue,
  recordIds: IdValue[],
  fieldCodes: string[]
): Promise<Record<string, Record<string, unknown>>> {
  const ids = uniqueIds(recordIds)
  const codes = fieldCodes.filter(Boolean).slice(0, 30)
  if (!appId || !modelId || !ids.length || !codes.length) return {}

  const r = await queryRecords({
    appId,
    modelId,
    page: 1,
    limit: Math.min(ids.length, 100),
    filters: [{ field: 'id', op: 'in', values: ids }],
    includeFieldCodes: codes
  })
  const map: Record<string, Record<string, unknown>> = {}
  for (const row of r.data?.list || []) {
    const id = idToString(row.id)
    if (hasId(id)) map[id] = (row.data || {}) as Record<string, unknown>
  }
  return map
}

export async function loadRefSelectOptions(params: {
  appId: IdValue
  field: FieldLike
  limit?: number
}): Promise<Array<{ value: string; text: string }>> {
  const refModelId = params.field.refModelId
  if (!refModelId || !params.appId) return []

  const includeFieldCodes = fieldIncludeCodes(params.field)
  const r = await queryRecords({
    appId: params.appId,
    modelId: refModelId,
    page: 1,
    limit: params.limit ?? 50,
    includeFieldCodes
  })
  const list = (r.data?.list || []) as Array<{ id?: IdValue; data?: Record<string, unknown> }>
  const displayField = params.field.refDisplayField

  return list
    .map((row) => {
      const id = idToString(row.id)
      if (!hasId(id)) return null
      return {
        value: id,
        text: recordDisplayLabel(null, displayField, row.data)
      }
    })
    .filter((x): x is { value: string; text: string } => x != null)
}

export async function resolveRefDisplay(
  recordId: IdValue,
  displayField?: string | null
): Promise<string> {
  const id = idToString(recordId)
  if (!hasId(id)) return '-'
  try {
    const d = await getRecord(id)
    return recordDisplayLabel(d.data, displayField)
  } catch {
    return `#${id}`
  }
}

function formatCellValue(v: unknown): string {
  if (v == null || v === '') return '-'
  if (typeof v === 'object') return JSON.stringify(v)
  return String(v)
}

export function relationIdFromField(field: FieldLike): string {
  const cfg = configFromMeta(field)
  const rid = idToString(cfg.relationId as IdValue)
  return hasId(rid) ? rid : ''
}

export async function resolveRelationId(
  field: FieldLike,
  parentModelId: IdValue,
  appId: IdValue
): Promise<string> {
  const explicit = relationIdFromField(field)
  if (hasId(explicit)) return explicit
  const dstId = idToString(field.refModelId as IdValue)
  const srcId = idToString(parentModelId)
  if (!appId || !dstId || !srcId) return ''
  try {
    const r = await listRelationsByApp(appId)
    const list = (r.data || []) as ModuleRelation[]
    const hit = list.find((rel) => {
      const t = String(rel.relType || '').toLowerCase()
      if (t !== '1-n' && t !== '1-1' && t !== '1n' && t !== '11') return false
      return sameId(rel.srcModelId, srcId) && sameId(rel.dstModelId, dstId)
    })
    return hit?.id ? idToString(hit.id) : ''
  } catch {
    return ''
  }
}

function fkFieldFromRelation(rel: ModuleRelation | null): string {
  if (!rel?.configJson) return ''
  try {
    const cfg = typeof rel.configJson === 'string' ? JSON.parse(rel.configJson) : rel.configJson
    return cfg?.fkField ? String(cfg.fkField).trim() : ''
  } catch {
    return ''
  }
}

export function rowMapFromQueryList(
  list: Array<{ id?: IdValue; data?: Record<string, unknown> }>,
  columns: Array<{ code: string; label: string }>,
  field: FieldLike,
  options: Array<{ value: number | string; text: string }>
): Record<string, { id: string; cells: Record<string, string> }> {
  const optById = new Map(options.map((o) => [idToString(o.value), o.text]))
  const map: Record<string, { id: string; cells: Record<string, string> }> = {}
  for (const row of list) {
    const id = idToString(row.id)
    if (!hasId(id)) continue
    const data = row.data || {}
    const cells: Record<string, string> = {}
    for (const col of columns) {
      if (col.code === '_title') {
        cells._title =
          optById.get(id) || recordDisplayLabel(null, field.refDisplayField, data) || `#${id}`
      } else {
        cells[col.code] = formatCellValue(data[col.code])
      }
    }
    map[id] = { id, cells }
  }
  return map
}

export async function loadSubRowsByRelation(params: {
  field: FieldLike
  appId: IdValue
  parentModelId: IdValue
  parentRecordId: IdValue
  columns: Array<{ code: string; label: string }>
  options: Array<{ value: number | string; text: string }>
  limit?: number
}): Promise<{
  ids: string[]
  rowMap: Record<string, { id: string; cells: Record<string, string> }>
  relationId?: string
  relType?: string
  fkField?: string
}> {
  const pid = idToString(params.parentRecordId)
  if (!hasId(pid) || !params.appId) return { ids: [], rowMap: {} }

  const relationId = await resolveRelationId(params.field, params.parentModelId, params.appId)
  if (!relationId) return { ids: [], rowMap: {} }

  const r = await listRelationsByApp(params.appId)
  const rel = ((r.data || []) as ModuleRelation[]).find((x) => sameId(x.id, relationId)) || null
  const relType = String(rel?.relType || '1-n').toLowerCase()
  if (relType === 'n-n') {
    return { ids: [], rowMap: {}, relationId, relType }
  }

  const includeFieldCodes = fieldIncludeCodes(params.field)
  const qr = await queryRecordsByRelation({
    relationId,
    parentRecordId: pid,
    query: { page: 1, limit: params.limit ?? 200, includeFieldCodes }
  })
  const list = (qr.data?.list || []) as Array<{ id?: IdValue; data?: Record<string, unknown> }>
  const ids = uniqueIds(list.map((x) => x.id))
  return {
    ids,
    rowMap: rowMapFromQueryList(list, params.columns, params.field, params.options),
    relationId,
    relType,
    fkField: fkFieldFromRelation(rel)
  }
}

export async function buildRowCellsMap(
  appId: IdValue,
  modelId: IdValue,
  recordIds: IdValue[],
  columns: Array<{ code: string; label: string }>,
  displayField: string | undefined,
  options: Array<{ value: number | string; text: string }>
): Promise<Record<string, { id: string; cells: Record<string, string> }>> {
  const codes = columns
    .map((c) => c.code)
    .filter((code) => code && code !== '_title')
  if (displayField && !codes.includes(displayField)) codes.unshift(displayField)

  const dataMap = await batchLoadRecordDataMap(appId, modelId, recordIds, codes)
  const optById = new Map(options.map((o) => [idToString(o.value), o.text]))
  const out: Record<string, { id: string; cells: Record<string, string> }> = {}

  for (const rawId of recordIds) {
    const id = idToString(rawId)
    if (!hasId(id)) continue
    const data = dataMap[id] || {}
    const cells: Record<string, string> = {}
    for (const col of columns) {
      if (col.code === '_title') {
        cells._title =
          optById.get(id) || recordDisplayLabel(null, displayField, data) || `#${id}`
      } else {
        cells[col.code] = formatCellValue(data[col.code])
      }
    }
    out[id] = { id, cells }
  }
  return out
}
