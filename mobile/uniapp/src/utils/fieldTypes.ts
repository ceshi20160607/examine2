/** 字段类型运行时判断（基于后端 ModuleFieldType 枚举 code） */

import {
  configFromMeta,
  defaultConfigFor,
  fieldTypeDef,
  fieldTypeOptionsForSelect,
  parseFieldTypeCode,
  type ModuleFieldTypeCode
} from '@/utils/fieldTypeEnum'
import type { IdValue } from '@/utils/id'

export type FieldLike = {
  fieldCode?: string
  fieldName?: string
  fieldType?: string
  requiredFlag?: number
  hiddenFlag?: number
  tips?: string | null
  dictCode?: string | null
  refModelId?: IdValue | null
  refDisplayField?: string | null
  relationModuleLabel?: string | null
  configJson?: string | null
  multiFlag?: number
  validateType?: string | null
  defaultValue?: string | null
  sortNo?: number
}

export { fieldTypeOptionsForSelect as FIELD_TYPE_OPTIONS }
export { parseFieldTypeCode, defaultConfigFor, configFromMeta, type ModuleFieldTypeCode }

export function fieldTypeCode(f: FieldLike): ModuleFieldTypeCode {
  return parseFieldTypeCode(f.fieldType)
}

export function normalizeFieldType(t?: string): string {
  return parseFieldTypeCode(t)
}

export function fieldTypeNeedsDict(fieldType: string): boolean {
  return !!fieldTypeDef(parseFieldTypeCode(fieldType)).needsDict
}

export function fieldTypeNeedsRef(fieldType: string): boolean {
  return !!fieldTypeDef(parseFieldTypeCode(fieldType)).needsRef
}

export function isRefField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'REF_MODULE'
}

export function isRefMultiField(f: FieldLike): boolean {
  if (!isRefField(f)) return false
  const cfg = configFromMeta(f)
  if (cfg.displayStyle === 'table' || cfg.subTable === true) return true
  return (f.multiFlag ?? 0) === 1 || cfg.multi === true
}

/** 子表/明细：REF_MODULE + displayStyle=table 或 subTable=true */
export function isRefTableField(f: FieldLike): boolean {
  if (!isRefField(f)) return false
  const cfg = configFromMeta(f)
  return cfg.displayStyle === 'table' || cfg.subTable === true
}

export function isAddressMapEnabled(f: FieldLike): boolean {
  if (!isAddressField(f)) return false
  const cfg = configFromMeta(f)
  return cfg.includeLocation === true || cfg.mapPicker === true
}

export function isNumberField(f: FieldLike): boolean {
  const c = fieldTypeCode(f)
  return c === 'NUMBER' || c === 'MONEY' || c === 'PERCENT'
}

export function isDateField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'DATETIME'
}

export function isDateRangeField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'DATE_RANGE'
}

export function datePickerType(f: FieldLike): 'date' | 'datetime' | 'time' {
  const cfg = configFromMeta(f)
  const mode = String(cfg.pickerMode || 'datetime')
  if (mode === 'date') return 'date'
  if (mode === 'time') return 'time'
  return 'datetime'
}

export function isTextareaField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'TEXTAREA'
}

export function isRichTextField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'RICH_TEXT'
}

export function isSignatureField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'SIGNATURE'
}

export function isRatingSelectField(f: FieldLike): boolean {
  if (fieldTypeCode(f) !== 'SELECT') return false
  return configFromMeta(f).displayStyle === 'rating'
}

export function isPasswordField(f: FieldLike): boolean {
  if (fieldTypeCode(f) !== 'TEXT') return false
  return configFromMeta(f).inputStyle === 'password'
}

export function isJsonField(f: FieldLike): boolean {
  return false
}

export function isBooleanField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'BOOLEAN'
}

export function isFileField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'FILE'
}

export function isImageField(f: FieldLike): boolean {
  return false
}

export function isDictSingle(f: FieldLike): boolean {
  if (fieldTypeCode(f) !== 'SELECT' || !f.dictCode) return false
  const style = String(configFromMeta(f).displayStyle || 'dropdown')
  return style !== 'rating' && style !== 'radio'
}

export function isRadioSelectField(f: FieldLike): boolean {
  if (fieldTypeCode(f) !== 'SELECT' || !f.dictCode) return false
  return configFromMeta(f).displayStyle === 'radio'
}

export function isDictMulti(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'MULTI_SELECT' && !!f.dictCode
}

export function isValidatedTextField(f: FieldLike): boolean {
  if (fieldTypeCode(f) !== 'TEXT') return false
  const s = configFromMeta(f).inputStyle
  return s === 'email' || s === 'phone' || s === 'url'
}

export function isPlainTextField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'TEXT' && !isPasswordField(f) && !isValidatedTextField(f)
}

export function isTitleField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'TITLE'
}

export function isAddressField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'ADDRESS'
}

export function isTagField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'TAG'
}

export function isPersonField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'PERSON'
}

export function isDepartmentField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'DEPARTMENT'
}

export function isSerialNoField(f: FieldLike): boolean {
  return fieldTypeCode(f) === 'SERIAL_NO'
}

export function multiFlagForFieldType(fieldType: string, config?: Record<string, unknown>): number {
  const code = parseFieldTypeCode(fieldType)
  if (code === 'MULTI_SELECT') return 1
  if (code === 'REF_MODULE' && config?.multi) return 1
  if ((code === 'PERSON' || code === 'DEPARTMENT') && config?.multi) return 1
  return 0
}

export function validateTypeForFieldType(fieldType: string, config?: Record<string, unknown>): string | null {
  if (parseFieldTypeCode(fieldType) !== 'TEXT') return null
  const style = String(config?.inputStyle || 'normal')
  if (style === 'email') return 'email'
  if (style === 'phone') return 'phone'
  if (style === 'url') return 'url'
  if (style === 'password') return 'password'
  return null
}

/** 提交 fieldType：已是枚举 code，原样提交 */
export function storageFieldType(fieldType: string): string {
  return parseFieldTypeCode(fieldType)
}

export function uiFieldTypeFromMeta(f: FieldLike): ModuleFieldTypeCode {
  return parseFieldTypeCode(f.fieldType)
}

export function inputTypeForField(f: FieldLike): string | undefined {
  const style = configFromMeta(f).inputStyle
  if (style === 'password') return 'password'
  if (style === 'phone') return 'number'
  return undefined
}

export function buildConfigJson(fieldType: string, config: Record<string, unknown>): string | null {
  const c = { ...defaultConfigFor(parseFieldTypeCode(fieldType)), ...config }
  const keys = Object.keys(c).filter((k) => c[k] !== undefined && c[k] !== '')
  if (keys.length === 0) return null
  return JSON.stringify(c)
}
