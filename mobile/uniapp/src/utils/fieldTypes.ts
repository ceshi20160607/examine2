/** 与后端 ModuleRecordFacade / 元数据约定一致的字段类型辅助 */

export type FieldLike = {
  fieldCode?: string
  fieldName?: string
  fieldType?: string
  requiredFlag?: number
  hiddenFlag?: number
  tips?: string | null
  dictCode?: string | null
  multiFlag?: number
  defaultValue?: string | null
  sortNo?: number
}

export const FIELD_TYPE_OPTIONS: Array<{ value: string; text: string }> = [
  { value: 'text', text: '单行文本 text' },
  { value: 'textarea', text: '多行文本 textarea' },
  { value: 'number', text: '数字 number' },
  { value: 'date', text: '日期 date' },
  { value: 'datetime', text: '日期时间 datetime' },
  { value: 'time', text: '时间 time' },
  { value: 'boolean', text: '布尔 boolean' },
  { value: 'select', text: '字典单选 select' },
  { value: 'multiselect', text: '字典多选 multiselect' },
  { value: 'file', text: '附件 file' }
]

export function normalizeFieldType(t?: string): string {
  return String(t || 'text').trim().toLowerCase()
}

export function isNumberField(f: FieldLike): boolean {
  const t = normalizeFieldType(f.fieldType)
  return t === 'number' || t === 'int' || t === 'integer' || t === 'long' || t === 'decimal' || t === 'double' || t === 'float'
}

export function isDateField(f: FieldLike): boolean {
  const t = normalizeFieldType(f.fieldType)
  return t === 'date' || t === 'datetime' || t === 'time'
}

export function datePickerType(f: FieldLike): 'date' | 'datetime' | 'time' {
  const t = normalizeFieldType(f.fieldType)
  if (t === 'datetime') return 'datetime'
  if (t === 'time') return 'time'
  return 'date'
}

export function isTextareaField(f: FieldLike): boolean {
  return normalizeFieldType(f.fieldType) === 'textarea'
}

export function isBooleanField(f: FieldLike): boolean {
  const t = normalizeFieldType(f.fieldType)
  return t === 'boolean' || t === 'bool' || t === 'switch'
}

export function isFileField(f: FieldLike): boolean {
  const t = normalizeFieldType(f.fieldType)
  return t === 'file' || t === 'upload' || t === 'attachment'
}

export function isDictSingle(f: FieldLike): boolean {
  if (f.dictCode && (f.multiFlag ?? 0) !== 1) return true
  const t = normalizeFieldType(f.fieldType)
  return (t === 'select' || t === 'dict' || t === 'radio') && !!f.dictCode
}

export function isDictMulti(f: FieldLike): boolean {
  if (f.dictCode && (f.multiFlag ?? 0) === 1) return true
  const t = normalizeFieldType(f.fieldType)
  return (t === 'multiselect' || t === 'multi_select' || t === 'checkbox') && !!f.dictCode
}

export function isPlainTextField(f: FieldLike): boolean {
  return (
    !isNumberField(f) &&
    !isDateField(f) &&
    !isTextareaField(f) &&
    !isBooleanField(f) &&
    !isFileField(f) &&
    !isDictSingle(f) &&
    !isDictMulti(f)
  )
}

export function fieldTypeNeedsDict(fieldType: string): boolean {
  const t = normalizeFieldType(fieldType)
  return t === 'select' || t === 'multiselect' || t === 'dict' || t === 'radio' || t === 'checkbox'
}

export function multiFlagForFieldType(fieldType: string): number {
  return normalizeFieldType(fieldType) === 'multiselect' ? 1 : 0
}

export function storageFieldType(fieldType: string): string {
  const t = normalizeFieldType(fieldType)
  if (t === 'select' || t === 'multiselect') return 'text'
  return t
}
