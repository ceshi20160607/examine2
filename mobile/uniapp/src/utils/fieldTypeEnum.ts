/** 与后端 ModuleFieldType 枚举一致 */

export type ModuleFieldTypeCode =
  | 'TEXT'
  | 'TEXTAREA'
  | 'NUMBER'
  | 'MONEY'
  | 'PERCENT'
  | 'DATETIME'
  | 'BOOLEAN'
  | 'SELECT'
  | 'MULTI_SELECT'
  | 'ADDRESS'
  | 'PERSON'
  | 'DEPARTMENT'
  | 'TITLE'
  | 'SIGNATURE'
  | 'SERIAL_NO'
  | 'RICH_TEXT'
  | 'REF_MODULE'
  | 'TAG'
  | 'DATE_RANGE'
  | 'FILE'

export type FieldTypeDef = {
  code: ModuleFieldTypeCode
  label: string
  needsDict?: boolean
  needsRef?: boolean
  allowsMulti?: boolean
  displayOnly?: boolean
}

export const MODULE_FIELD_TYPES: FieldTypeDef[] = [
  { code: 'TEXT', label: '单行文本' },
  { code: 'TEXTAREA', label: '多行文本' },
  { code: 'NUMBER', label: '数字' },
  { code: 'MONEY', label: '金额' },
  { code: 'PERCENT', label: '百分比' },
  { code: 'DATETIME', label: '日期时间' },
  { code: 'BOOLEAN', label: '布尔' },
  { code: 'SELECT', label: '单选', needsDict: true },
  { code: 'MULTI_SELECT', label: '多选', needsDict: true, allowsMulti: true },
  { code: 'ADDRESS', label: '地址' },
  { code: 'PERSON', label: '人员', allowsMulti: true },
  { code: 'DEPARTMENT', label: '部门', allowsMulti: true },
  { code: 'TITLE', label: '占位标题', displayOnly: true },
  { code: 'SIGNATURE', label: '手写签名' },
  { code: 'SERIAL_NO', label: '自定义编号' },
  { code: 'RICH_TEXT', label: '富文本' },
  { code: 'REF_MODULE', label: '关联模块', needsRef: true, allowsMulti: true },
  { code: 'TAG', label: '标签' },
  { code: 'DATE_RANGE', label: '日期区间' },
  { code: 'FILE', label: '附件' }
]

const LEGACY_MAP: Record<string, ModuleFieldTypeCode> = {
  text: 'TEXT',
  string: 'TEXT',
  password: 'TEXT',
  email: 'TEXT',
  phone: 'TEXT',
  url: 'TEXT',
  textarea: 'TEXTAREA',
  number: 'NUMBER',
  integer: 'NUMBER',
  decimal: 'NUMBER',
  money: 'MONEY',
  currency: 'MONEY',
  percent: 'PERCENT',
  date: 'DATETIME',
  datetime: 'DATETIME',
  time: 'DATETIME',
  boolean: 'BOOLEAN',
  bool: 'BOOLEAN',
  switch: 'BOOLEAN',
  select: 'SELECT',
  multiselect: 'MULTI_SELECT',
  radio: 'SELECT',
  checkbox: 'MULTI_SELECT',
  address: 'ADDRESS',
  region: 'ADDRESS',
  person: 'PERSON',
  user: 'PERSON',
  department: 'DEPARTMENT',
  dept: 'DEPARTMENT',
  title: 'TITLE',
  signature: 'SIGNATURE',
  sign: 'SIGNATURE',
  serial_no: 'SERIAL_NO',
  serial: 'SERIAL_NO',
  rich_text: 'RICH_TEXT',
  richtext: 'RICH_TEXT',
  ref: 'REF_MODULE',
  relation: 'REF_MODULE',
  ref_multi: 'REF_MODULE',
  tag: 'TAG',
  tags: 'TAG',
  date_range: 'DATE_RANGE',
  file: 'FILE',
  image: 'FILE',
  attachment: 'FILE'
}

export function parseFieldTypeCode(raw?: string | null): ModuleFieldTypeCode {
  if (!raw) return 'TEXT'
  const u = String(raw).trim().toUpperCase()
  const hit = MODULE_FIELD_TYPES.find((t) => t.code === u)
  if (hit) return hit.code
  return LEGACY_MAP[String(raw).trim().toLowerCase()] || 'TEXT'
}

export function fieldTypeDef(code: ModuleFieldTypeCode): FieldTypeDef {
  return MODULE_FIELD_TYPES.find((t) => t.code === code) || MODULE_FIELD_TYPES[0]
}

export function fieldTypeOptionsForSelect() {
  return MODULE_FIELD_TYPES.map((t) => ({ value: t.code, text: t.label }))
}

export function defaultConfigFor(code: ModuleFieldTypeCode): Record<string, unknown> {
  switch (code) {
    case 'TEXT':
      return { inputStyle: 'normal', maxLength: 200 }
    case 'MONEY':
      return { decimalPlaces: 2 }
    case 'PERCENT':
      return { decimalPlaces: 0 }
    case 'DATETIME':
      return { pickerMode: 'datetime' }
    case 'SELECT':
      return { displayStyle: 'dropdown' }
    case 'MULTI_SELECT':
      return { displayStyle: 'dropdown' }
    case 'ADDRESS':
      // includeLocation=true 时表单端启用地图选点；regionStyle/detailMode 控制级联与详细地址
      return { regionStyle: 'cascade', includeLocation: false, detailMode: 'manual', mapPicker: false }
    case 'PERSON':
    case 'DEPARTMENT':
      return { scope: 'system', multi: false }
    case 'TITLE':
      return { content: '' }
    case 'REF_MODULE':
      // displayStyle: inline=单行选择 | list=列表多选 | table=子表/明细（一对多关联行）
      return { displayStyle: 'inline', listFields: [], multi: true, subTable: false }
    case 'TAG':
      return { tags: [], allowCustom: true }
    case 'SERIAL_NO':
      return { segments: [{ type: 'seq', width: 4, reset: 'never' }] }
    case 'DATE_RANGE':
      return { pickerMode: 'date' }
    default:
      return {}
  }
}

export function configFromMeta(f: {
  fieldType?: string
  validateType?: string | null
  multiFlag?: number
  configJson?: string | null
}): Record<string, unknown> {
  const code = parseFieldTypeCode(f.fieldType)
  let cfg: Record<string, unknown> = {}
  if (f.configJson) {
    try {
      cfg = JSON.parse(f.configJson)
    } catch {
      cfg = {}
    }
  }
  if (code === 'TEXT' && f.validateType) {
    const vt = String(f.validateType).toLowerCase()
    const style =
      vt === 'password' ? 'password' : vt === 'phone' ? 'phone' : vt === 'email' ? 'email' : vt === 'url' ? 'url' : 'normal'
    cfg = { ...defaultConfigFor('TEXT'), ...cfg, inputStyle: cfg.inputStyle || style }
  }
  if (code === 'REF_MODULE' && (f.multiFlag ?? 0) === 1) {
    cfg = { ...cfg, multi: true }
  }
  return { ...defaultConfigFor(code), ...cfg }
}
