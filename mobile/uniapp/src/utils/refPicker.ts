import { getRecord, queryRecords } from '@/api/records'
import type { FieldLike } from '@/utils/fieldTypes'

export function recordDisplayLabel(detail: any, displayField?: string | null): string {
  const data = detail?.data || {}
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

export async function loadRefSelectOptions(params: {
  appId: number
  field: FieldLike
  limit?: number
}): Promise<Array<{ value: number; text: string }>> {
  const refModelId = params.field.refModelId
  if (!refModelId || !params.appId) return []

  const r = await queryRecords({
    appId: params.appId,
    modelId: refModelId,
    page: 1,
    limit: params.limit ?? 50
  })
  const list = (r.data?.list || []) as Array<{ id?: number }>
  const opts: Array<{ value: number; text: string }> = []

  for (const row of list) {
    const id = Number(row.id)
    if (!id) continue
    let text = `#${id}`
    try {
      const d = await getRecord(id)
      text = recordDisplayLabel(d.data, params.field.refDisplayField)
    } catch {
      // keep fallback label
    }
    opts.push({ value: id, text })
  }
  return opts
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
