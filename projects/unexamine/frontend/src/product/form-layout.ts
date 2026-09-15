import type { ConfiguredField } from './types'

export interface FormSection {
  id: string
  title: string
  columns: number
  fieldCodes: string[]
  extra: Record<string, unknown>
}
export interface FormLayout extends Record<string, unknown> {
  schemaVersion?: number
  fieldCodes?: string[]
  fieldSpans?: Record<string, number>
  components?: Array<Record<string, unknown>>
}
export function parseFormLayout(value: unknown): FormLayout {
  try {
    const result = typeof value === 'string' ? JSON.parse(value) : value
    return result && typeof result === 'object' && !Array.isArray(result) ? result as FormLayout : {}
  } catch { return {} }
}
export function formColumns(value: unknown) {
  return Number.isInteger(value) && Number(value) >= 1 && Number(value) <= 3 ? Number(value) : 2
}
export function formSections(value: unknown, fields: ConfiguredField[]): FormSection[] {
  const layout = parseFormLayout(value)
  const active = new Set(fields.filter(field => field.status === 'ACTIVE').map(field => field.code))
  const codes = Array.isArray(layout.fieldCodes) ? layout.fieldCodes : [...active]
  const visible = new Set(codes.filter(code => active.has(code)))
  const used = new Set<string>()
  const components = Array.isArray(layout.components) ? layout.components : []
  const sections = components.filter(component => component.type === 'SECTION').map((component, index) => ({
    id: String(component.id || `section-${index}`), title: String(component.title || '主要信息'),
    columns: formColumns(component.columns), extra: { ...component },
    fieldCodes: (Array.isArray(component.fieldCodes) ? component.fieldCodes as string[] : [])
      .filter(code => { if (!visible.has(code) || used.has(code)) return false; used.add(code); return true }),
  }))
  if (!sections.length) sections.push({ id: 'main', title: '主要信息', columns: 2, fieldCodes: [], extra: {} })
  sections[0]!.fieldCodes.push(...[...visible].filter(code => !used.has(code)))
  return sections
}
export function materializeFormLayout(original: unknown, sections: FormSection[], spans: Record<string, number> = {}): FormLayout {
  const layout = parseFormLayout(original)
  const fieldCodes = sections.flatMap(section => section.fieldCodes)
  return {
    ...layout, schemaVersion: 1, layoutType: 'FORM', fieldCodes,
    fieldSpans: Object.fromEntries(Object.entries(spans).filter(([code]) => fieldCodes.includes(code))),
    components: [...(Array.isArray(layout.components) ? layout.components : []).filter(component => component.type !== 'SECTION'),
      ...sections.map(section => ({ ...section.extra, id: section.id, type: 'SECTION', title: section.title,
        columns: section.columns, fieldCodes: [...section.fieldCodes] }))],
  }
}
export function placeFormField(sections: FormSection[], code: string, sectionId: string, beforeCode?: string): FormSection[] {
  const target = sections.find(section => section.id === sectionId)
  if (!target || code === beforeCode) return sections
  const result = sections.map(section => ({ ...section, fieldCodes: section.fieldCodes.filter(item => item !== code) }))
  const destination = result.find(section => section.id === sectionId)!
  const index = beforeCode ? destination.fieldCodes.indexOf(beforeCode) : -1
  destination.fieldCodes.splice(index < 0 ? destination.fieldCodes.length : index, 0, code)
  return result
}
export function fieldSpan(layout: unknown, code: string, columns: number) {
  const span = parseFormLayout(layout).fieldSpans?.[code]
  return Number.isInteger(span) && Number(span) > 0 ? Math.min(Number(span), columns) : 1
}
