import type { ReportFieldPin } from '@/types/report'

export const MAX_REPORT_FIELDS = 100

export function canonicalReportFieldCodes(values: readonly string[]) {
  const result: string[] = []
  const seen = new Set<string>()
  for (const value of values) {
    const code = value.trim()
    if (!code || seen.has(code)) continue
    seen.add(code)
    result.push(code)
    if (result.length === MAX_REPORT_FIELDS) break
  }
  return result
}

export function validateReportFields(values: readonly string[]) {
  const canonical = canonicalReportFieldCodes(values)
  if (canonical.length < 1) return '报表至少需要一个输出字段'
  if (values.length > MAX_REPORT_FIELDS) return `报表最多支持 ${MAX_REPORT_FIELDS} 个输出字段`
  if (canonical.length !== values.length) return '输出字段不能为空或重复'
  return ''
}

export function moveReportField(values: readonly string[], index: number, offset: -1 | 1) {
  const result = [...values]
  const target = index + offset
  if (index < 0 || index >= result.length || target < 0 || target >= result.length) return result
  const [field] = result.splice(index, 1)
  if (field) result.splice(target, 0, field)
  return result
}

export function reportFieldLabel(field: Pick<ReportFieldPin, 'code' | 'name' | 'type'>) {
  return `${field.name || field.code} · ${field.code} · ${field.type || 'UNKNOWN'}`
}
