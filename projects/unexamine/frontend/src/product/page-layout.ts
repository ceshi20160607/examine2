import { parseFormLayout, type FormLayout } from './form-layout'
import type { RuntimeField } from './types'

export interface PageColumn { code: string; label: string; width: number; field?: RuntimeField }
export function pageDensity(value: unknown): 'compact' | 'comfortable' {
  const layout = parseFormLayout(value)
  const table = layout.components?.find(component => component.type === 'TABLE' || component.type === 'SECTION')
  return (layout.density ?? table?.density) === 'compact' ? 'compact' : 'comfortable'
}
export function columnWidth(value: unknown, fallback = 140) {
  return Number.isInteger(value) && Number(value) >= 80 && Number(value) <= 600 ? Number(value) : fallback
}
export function listFieldCodes(value: unknown, fields: RuntimeField[]) {
  const layout = parseFormLayout(value)
  const available = new Set(fields.filter(field => field.status === 'ACTIVE').map(field => field.code))
  const codes = Array.isArray(layout.fieldCodes) ? layout.fieldCodes : [...available].slice(0, 2)
  return [...new Set(codes)].filter(code => available.has(code))
}
export function listPageSettings(value: unknown, columns: PageColumn[]) {
  const layout = parseFormLayout(value)
  const allowed = new Set(columns.map(column => column.code))
  const codes = ['title', ...listFieldCodes(layout, columns.flatMap(column => column.field ? [column.field] : [])), 'status']
  const visible = [...new Set(codes)].filter(code => allowed.has(code))
  const fixed = Array.isArray(layout.fixedFieldCodes) ? layout.fixedFieldCodes as string[] : ['title']
  const widths = parseFormLayout(layout.columnWidths)
  return {
    visible, fixed: [...new Set(fixed)].filter(code => visible.includes(code)).slice(0, 3),
    columns: columns.map(column => ({ ...column, width: columnWidth(widths[column.code], column.width) })),
    density: pageDensity(layout),
  }
}
export function materializeListLayout(original: unknown, codes: string[], widths: Record<string, number>, fixed: string[], title: string, density: string): FormLayout & { components: Array<Record<string, unknown>>; columnWidths: Record<string, number> } {
  const layout = parseFormLayout(original)
  const components = Array.isArray(layout.components) ? [...layout.components] : []
  const index = components.findIndex(component => component.type === 'TABLE')
  const table = { ...(index < 0 ? {} : components[index]), id: components[index]?.id || 'main', type: 'TABLE', title, fieldCodes: [...codes], density }
  if (index < 0) components.push(table); else components[index] = table
  const visible = new Set(['title', ...codes, 'status'])
  return { ...layout, schemaVersion: 1, layoutType: 'LIST', fieldCodes: [...codes], density,
    columnWidths: Object.fromEntries(Object.entries(widths).filter(([code]) => visible.has(code)).map(([code, width]) => [code, columnWidth(width)])),
    fixedFieldCodes: [...new Set(fixed)].filter(code => visible.has(code)).slice(0, 3), components }
}
