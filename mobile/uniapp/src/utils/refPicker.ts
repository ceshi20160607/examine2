import { getRecord, queryRecords, queryRecordsByRelation } from '@/api/records'
import { listRelationsByApp, type ModuleRelation } from '@/api/meta'
import type { FieldLike } from '@/utils/fieldTypes'
import { configFromMeta } from '@/utils/fieldTypes'

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
  appId: number,
  modelId: number,
  recordIds: number[],
  fieldCodes: string[]
): Promise<Record<number, Record<string, unknown>>> {
  const ids = [...new Set(recordIds.filter((n) => n > 0))]
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
  const map: Record<number, Record<string, unknown>> = {}
  for (const row of r.data?.list || []) {
    const id = Number(row.id)
    if (id > 0) map[id] = (row.data || {}) as Record<string, unknown>
  }
  return map
}

export async function loadRefSelectOptions(params: {
  appId: number
  field: FieldLike
  limit?: number
}): Promise<Array<{ value: number; text: string }>> {
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
  const list = (r.data?.list || []) as Array<{ id?: number; data?: Record<string, unknown> }>
  const displayField = params.field.refDisplayField

  return list
    .map((row) => {
      const id = Number(row.id)
      if (!id) return null
      return {
        value: id,
        text: recordDisplayLabel(null, displayField, row.data)
      }
    })
    .filter((x): x is { value: number; text: string } => x != null)
}

export async function resolveRefDisplay(
  recordId: number | string | null | undefined,
  displayField?: string | null
): Promise<string> {
  const id = Number(recordId)
  if (!id || !Number.isFinite(id)) return '-'
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

export function relationIdFromField(field: FieldLike): number {
  const cfg = configFromMeta(field)
  const rid = Number(cfg.relationId)
  return rid > 0 ? rid : 0
}

export async function resolveRelationId(
  field: FieldLike,
  parentModelId: number,
  appId: number
): Promise<number> {
  const explicit = relationIdFromField(field)
  if (explicit > 0) return explicit
  const dstId = Number(field.refModelId)
  const srcId = Number(parentModelId)
  if (!appId || !dstId || !srcId) return 0
  try {
    const r = await listRelationsByApp(appId)
    const list = (r.data || []) as ModuleRelation[]
    const hit = list.find((rel) => {
      const t = String(rel.relType || '').toLowerCase()
      if (t !== '1-n' && t !== '1-1' && t !== '1n' && t !== '11') return false
      return Number(rel.srcModelId) === srcId && Number(rel.dstModelId) === dstId
    })
    return hit?.id ? Number(hit.id) : 0
  } catch {
    return 0
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
  list: Array<{ id?: number; data?: Record<string, unknown> }>,
  columns: Array<{ code: string; label: string }>,
  field: FieldLike,
  options: Array<{ value: number | string; text: string }>
): Record<number, { id: number; cells: Record<string, string> }> {
  const optById = new Map(options.map((o) => [Number(o.value), o.text]))
  const map: Record<number, { id: number; cells: Record<string, string> }> = {}
  for (const row of list) {
    const id = Number(row.id)
    if (!id) continue
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
  appId: number
  parentModelId: number
  parentRecordId: number
  columns: Array<{ code: string; label: string }>
  options: Array<{ value: number | string; text: string }>
  limit?: number
}): Promise<{
  ids: number[]
  rowMap: Record<number, { id: number; cells: Record<string, string> }>
  relationId?: number
  relType?: string
  fkField?: string
}> {
  const pid = Number(params.parentRecordId)
  if (!pid || !params.appId) return { ids: [], rowMap: {} }

  const relationId = await resolveRelationId(params.field, params.parentModelId, params.appId)
  if (!relationId) return { ids: [], rowMap: {} }

  const r = await listRelationsByApp(params.appId)
  const rel = ((r.data || []) as ModuleRelation[]).find((x) => Number(x.id) === relationId) || null
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
  const list = (qr.data?.list || []) as Array<{ id?: number; data?: Record<string, unknown> }>
  const ids = list.map((x) => Number(x.id)).filter((n) => n > 0)
  return {
    ids,
    rowMap: rowMapFromQueryList(list, params.columns, params.field, params.options),
    relationId,
    relType,
    fkField: fkFieldFromRelation(rel)
  }
}

export async function buildRowCellsMap(
  appId: number,
  modelId: number,
  recordIds: number[],
  columns: Array<{ code: string; label: string }>,
  displayField: string | undefined,
  options: Array<{ value: number | string; text: string }>
): Promise<Record<number, { id: number; cells: Record<string, string> }>> {
  const codes = columns
    .map((c) => c.code)
    .filter((code) => code && code !== '_title')
  if (displayField && !codes.includes(displayField)) codes.unshift(displayField)

  const dataMap = await batchLoadRecordDataMap(appId, modelId, recordIds, codes)
  const optById = new Map(options.map((o) => [Number(o.value), o.text]))
  const out: Record<number, { id: number; cells: Record<string, string> }> = {}

  for (const id of recordIds) {
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
