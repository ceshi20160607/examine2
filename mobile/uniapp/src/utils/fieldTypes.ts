/** 与后端 ModuleField / ModuleRecordFacade 约定一致的字段类型辅助 */

export type FieldLike = {
  fieldCode?: string
  fieldName?: string
  fieldType?: string
  requiredFlag?: number
  hiddenFlag?: number
  tips?: string | null
  dictCode?: string | null
  refModelId?: number | null
  refDisplayField?: string | null
  multiFlag?: number
  validateType?: string | null
  defaultValue?: string | null
  sortNo?: number
}

/** 日常常用类型（uni-data-select 扁平列表，text 前缀便于检索） */
export const FIELD_TYPE_OPTIONS: Array<{ value: string; text: string }> = [
  { value: 'text', text: '单行文本' },
  { value: 'textarea', text: '多行文本' },
  { value: 'password', text: '密码' },
  { value: 'email', text: '邮箱' },
  { value: 'phone', text: '手机号' },
  { value: 'url', text: '网址链接' },
  { value: 'idcard', text: '身份证号' },
  { value: 'number', text: '数字' },
  { value: 'integer', text: '整数' },
  { value: 'decimal', text: '小数' },
  { value: 'percent', text: '百分比' },
  { value: 'currency', text: '金额' },
  { value: 'date', text: '日期' },
  { value: 'datetime', text: '日期时间' },
  { value: 'time', text: '时间' },
  { value: 'year', text: '年份' },
  { value: 'month', text: '月份' },
  { value: 'boolean', text: '布尔' },
  { value: 'switch', text: '开关' },
  { value: 'select', text: '字典单选' },
  { value: 'multiselect', text: '字典多选' },
  { value: 'radio', text: '单选（字典）' },
  { value: 'checkbox', text: '多选（字典）' },
  { value: 'color', text: '颜色' },
  { value: 'rating', text: '评分' },
  { value: 'slider', text: '滑块' },
  { value: 'region', text: '地区' },
  { value: 'json', text: 'JSON' },
  { value: 'file', text: '附件' },
  { value: 'image', text: '图片' },
  { value: 'attachment', text: '多附件' },
  { value: 'ref', text: '关联（单条）' },
  { value: 'relation', text: '关联（同 ref）' },
  { value: 'ref_multi', text: '关联（多条）' }
]

export function normalizeFieldType(t?: string): string {
  return String(t || 'text').trim().toLowerCase()
}

export function isRefField(f: FieldLike): boolean {
  const t = normalizeFieldType(f.fieldType)
  return t === 'ref' || t === 'relation' || t === 'lookup' || t === 'ref_multi'
}

export function isRefMultiField(f: FieldLike): boolean {
  if ((f.multiFlag ?? 0) === 1 && isRefField(f)) return true
  return normalizeFieldType(f.fieldType) === 'ref_multi'
}

export function fieldTypeNeedsRef(fieldType: string): boolean {
  const t = normalizeFieldType(fieldType)
  return t === 'ref' || t === 'relation' || t === 'lookup' || t === 'ref_multi'
}

export function isNumberField(f: FieldLike): boolean {
  const t = normalizeFieldType(f.fieldType)
  return (
    t === 'number' ||
    t === 'int' ||
    t === 'integer' ||
    t === 'long' ||
    t === 'decimal' ||
    t === 'double' ||
    t === 'float' ||
    t === 'percent' ||
    t === 'currency' ||
    t === 'rating' ||
    t === 'slider'
  )
}

export function isDateField(f: FieldLike): boolean {
  const t = normalizeFieldType(f.fieldType)
  return t === 'date' || t === 'datetime' || t === 'time' || t === 'year' || t === 'month'
}

export function datePickerType(f: FieldLike): 'date' | 'datetime' | 'time' {
  const t = normalizeFieldType(f.fieldType)
  if (t === 'datetime') return 'datetime'
  if (t === 'time') return 'time'
  if (t === 'year') return 'date'
  if (t === 'month') return 'date'
  return 'date'
}

export function isTextareaField(f: FieldLike): boolean {
  return normalizeFieldType(f.fieldType) === 'textarea'
}

export function isPasswordField(f: FieldLike): boolean {
  return normalizeFieldType(f.fieldType) === 'password'
}

export function isJsonField(f: FieldLike): boolean {
  return normalizeFieldType(f.fieldType) === 'json'
}

export function isBooleanField(f: FieldLike): boolean {
  const t = normalizeFieldType(f.fieldType)
  return t === 'boolean' || t === 'bool' || t === 'switch'
}

export function isFileField(f: FieldLike): boolean {
  const t = normalizeFieldType(f.fieldType)
  return t === 'file' || t === 'upload' || t === 'attachment' || t === 'image'
}

export function isImageField(f: FieldLike): boolean {
  return normalizeFieldType(f.fieldType) === 'image'
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

export function isValidatedTextField(f: FieldLike): boolean {
  const t = normalizeFieldType(f.fieldType)
  return t === 'email' || t === 'phone' || t === 'url' || t === 'idcard' || t === 'id_card'
}

export function isPlainTextField(f: FieldLike): boolean {
  const t = normalizeFieldType(f.fieldType)
  if (t === 'color' || t === 'region') return true
  return (
    !isNumberField(f) &&
    !isDateField(f) &&
    !isTextareaField(f) &&
    !isPasswordField(f) &&
    !isJsonField(f) &&
    !isBooleanField(f) &&
    !isFileField(f) &&
    !isDictSingle(f) &&
    !isDictMulti(f) &&
    !isRefField(f) &&
    !isValidatedTextField(f)
  )
}

export function fieldTypeNeedsDict(fieldType: string): boolean {
  const t = normalizeFieldType(fieldType)
  return t === 'select' || t === 'multiselect' || t === 'dict' || t === 'radio' || t === 'checkbox'
}

export function multiFlagForFieldType(fieldType: string): number {
  const t = normalizeFieldType(fieldType)
  if (t === 'multiselect' || t === 'checkbox' || t === 'ref_multi' || t === 'attachment') return 1
  return 0
}

export function validateTypeForFieldType(fieldType: string): string | null {
  const t = normalizeFieldType(fieldType)
  if (t === 'email') return 'email'
  if (t === 'phone') return 'phone'
  if (t === 'url') return 'url'
  if (t === 'idcard' || t === 'id_card') return 'idCard'
  return null
}

/** 提交到后端的 fieldType */
export function storageFieldType(fieldType: string): string {
  const t = normalizeFieldType(fieldType)
  if (t === 'select' || t === 'multiselect' || t === 'radio' || t === 'checkbox') return 'text'
  if (t === 'ref_multi') return 'ref'
  if (
    t === 'textarea' ||
    t === 'password' ||
    t === 'email' ||
    t === 'phone' ||
    t === 'url' ||
    t === 'idcard' ||
    t === 'color' ||
    t === 'region' ||
    t === 'switch'
  ) {
    return 'text'
  }
  if (t === 'integer' || t === 'decimal' || t === 'percent' || t === 'currency' || t === 'rating' || t === 'slider') {
    return 'number'
  }
  if (t === 'year' || t === 'month') return 'date'
  if (t === 'image' || t === 'attachment') return 'file'
  if (t === 'relation' || t === 'lookup') return 'ref'
  return t
}

/** 从后端字段还原移动端类型选择值 */
export function uiFieldTypeFromMeta(f: FieldLike): string {
  const t = normalizeFieldType(f.fieldType)
  if (f.dictCode && (f.multiFlag ?? 0) === 1) return 'multiselect'
  if (f.dictCode) return 'select'
  if ((t === 'ref' || t === 'relation') && (f.multiFlag ?? 0) === 1) return 'ref_multi'
  if (t === 'ref' || t === 'relation') return 'ref'
  if (f.validateType === 'email') return 'email'
  if (f.validateType === 'phone') return 'phone'
  if (f.validateType === 'url') return 'url'
  if (f.validateType === 'idCard') return 'idcard'
  if (t === 'text' && f.validateType) return String(f.validateType).toLowerCase()
  if (t === 'number' && f.fieldType) return t
  if (t === 'bool') return 'boolean'
  if (t === 'enum') return f.dictCode ? 'select' : 'text'
  return t || 'text'
}

export function inputTypeForField(f: FieldLike): string | undefined {
  const t = normalizeFieldType(f.fieldType)
  if (t === 'password') return 'password'
  if (t === 'email') return 'text'
  if (t === 'phone') return 'number'
  if (t === 'url') return 'text'
  if (t === 'color') return 'text'
  return undefined
}
