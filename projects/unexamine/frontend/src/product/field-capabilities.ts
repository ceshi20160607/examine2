export type FieldFamily = 'TEXT' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'SELECTION' | 'IDENTITY' | 'FILE' | 'RELATION' | 'ADVANCED' | 'SYSTEM'

export interface FieldTypeDefinition {
  type: string
  name: string
  hint: string
  family: FieldFamily
}

export const fieldTypes: FieldTypeDefinition[] = [
  { type: 'TEXT', name: '单行文本', hint: '名称、编号等短文本', family: 'TEXT' },
  { type: 'MULTILINE_TEXT', name: '多行文本', hint: '备注和较长说明', family: 'TEXT' },
  { type: 'PHONE', name: '手机号', hint: '手机号格式校验', family: 'TEXT' },
  { type: 'EMAIL', name: '邮箱', hint: '邮箱格式校验', family: 'TEXT' },
  { type: 'URL', name: 'URL', hint: '网页地址', family: 'TEXT' },
  { type: 'ID_CARD', name: '身份证号', hint: '证件格式与脱敏', family: 'TEXT' },
  { type: 'NUMBER', name: '数字', hint: '普通数值', family: 'NUMBER' },
  { type: 'PERCENT', name: '百分比', hint: '百分比与精度', family: 'NUMBER' },
  { type: 'MONEY', name: '金额', hint: '精确金额数值', family: 'NUMBER' },
  { type: 'DATE', name: '日期', hint: '年月日', family: 'DATE' },
  { type: 'DATETIME', name: '日期时间', hint: '日期和具体时间', family: 'DATE' },
  { type: 'DATE_RANGE', name: '日期范围', hint: '开始和结束日期', family: 'DATE' },
  { type: 'TIME', name: '时间', hint: '时分秒', family: 'DATE' },
  { type: 'TIME_RANGE', name: '时间范围', hint: '开始和结束时间', family: 'DATE' },
  { type: 'SINGLE_SELECT', name: '单选', hint: '配置固定选项', family: 'SELECTION' },
  { type: 'MULTI_SELECT', name: '多选', hint: '选择多个字典项', family: 'SELECTION' },
  { type: 'CASCADE', name: '级联选择', hint: '树形字典或模块层级', family: 'SELECTION' },
  { type: 'STATUS', name: '状态', hint: '业务状态选项', family: 'SELECTION' },
  { type: 'BOOLEAN', name: '是否', hint: '是或否', family: 'BOOLEAN' },
  { type: 'MEMBER', name: '成员', hint: '关联当前租户成员', family: 'IDENTITY' },
  { type: 'DEPARTMENT', name: '部门', hint: '关联当前租户部门', family: 'IDENTITY' },
  { type: 'ORGANIZATION', name: '组织/租户', hint: '当前系统组织范围', family: 'IDENTITY' },
  { type: 'ATTACHMENT', name: '附件', hint: '通用附件引用', family: 'FILE' },
  { type: 'IMAGE', name: '图片', hint: '图片预览与附件引用', family: 'FILE' },
  { type: 'FILE', name: '文件', hint: '单个文件引用', family: 'FILE' },
  { type: 'FILE_GROUP', name: '文件组', hint: '文件夹或多文件', family: 'FILE' },
  { type: 'AUTO_NUMBER', name: '自动编号', hint: '并发安全编号规则', family: 'ADVANCED' },
  { type: 'REFERENCE', name: '关联数据', hint: '关联另一模块的业务记录', family: 'RELATION' },
  { type: 'LOOKUP', name: '引用字段', hint: '从关联记录读取字段', family: 'RELATION' },
  { type: 'SUBTABLE', name: '子表', hint: '一主多明细事务', family: 'RELATION' },
  { type: 'ADDRESS', name: '地址', hint: '结构化地址', family: 'ADVANCED' },
  { type: 'LOCATION', name: '地理位置', hint: '经纬度与名称', family: 'ADVANCED' },
  { type: 'RATING', name: '评分', hint: '星级或分值', family: 'NUMBER' },
  { type: 'PROGRESS', name: '进度', hint: '0 到 100 进度', family: 'NUMBER' },
  { type: 'TAG', name: '标签', hint: '带颜色的多标签', family: 'SELECTION' },
  { type: 'BARCODE', name: '条码', hint: '条码内容与展示', family: 'ADVANCED' },
  { type: 'QRCODE', name: '二维码', hint: '二维码内容与展示', family: 'ADVANCED' },
  { type: 'SIGNATURE', name: '签名', hint: '签名文件引用', family: 'FILE' },
  { type: 'RICH_TEXT', name: '富文本', hint: '受控富文本内容', family: 'ADVANCED' },
  { type: 'JSON', name: '结构化数据', hint: '保存成组的结构化内容', family: 'ADVANCED' },
  { type: 'SECRET', name: '密码/密钥', hint: '敏感值与脱敏', family: 'ADVANCED' },
  { type: 'SYSTEM_CREATED_BY', name: '创建人', hint: '只读系统字段', family: 'SYSTEM' },
  { type: 'SYSTEM_CREATED_AT', name: '创建时间', hint: '只读系统字段', family: 'SYSTEM' },
  { type: 'SYSTEM_UPDATED_BY', name: '更新人', hint: '只读系统字段', family: 'SYSTEM' },
  { type: 'SYSTEM_UPDATED_AT', name: '更新时间', hint: '只读系统字段', family: 'SYSTEM' },
  { type: 'FORMULA', name: '公式', hint: '表达式计算结果', family: 'ADVANCED' },
  { type: 'SUMMARY', name: '汇总', hint: '关联记录汇总', family: 'ADVANCED' },
  { type: 'CALCULATION', name: '计算', hint: '业务计算表达式', family: 'ADVANCED' },
  { type: 'AGGREGATE', name: '聚合', hint: '关联模块聚合', family: 'ADVANCED' },
  { type: 'AI_FILL', name: 'AI 填充', hint: '授权模型且人工确认', family: 'ADVANCED' },
]

const definitions = new Map(fieldTypes.map((field) => [field.type, field]))
const textRuntimeFieldTypes = new Set(['TEXT', 'MULTILINE_TEXT', 'PHONE', 'EMAIL', 'URL', 'ID_CARD', 'BARCODE', 'QRCODE'])
const nonUniqueFieldTypes = new Set([
  'RICH_TEXT', 'JSON', 'ADDRESS', 'LOCATION', 'SECRET', 'ATTACHMENT', 'IMAGE', 'FILE', 'FILE_GROUP', 'SUBTABLE', 'SIGNATURE',
  'LOOKUP', 'FORMULA', 'SUMMARY', 'CALCULATION', 'AGGREGATE', 'AI_FILL',
  'SYSTEM_CREATED_BY', 'SYSTEM_CREATED_AT', 'SYSTEM_UPDATED_BY', 'SYSTEM_UPDATED_AT',
])

export function fieldFamily(fieldType?: string): FieldFamily | undefined {
  return fieldType ? definitions.get(fieldType)?.family : undefined
}

export function isBasicRuntimeField(fieldType: string) {
  return textRuntimeFieldTypes.has(fieldType) || ['NUMBER', 'DATE', 'DATETIME', 'BOOLEAN'].includes(fieldType)
}

export function supportsTextConstraints(fieldType: string) {
  return textRuntimeFieldTypes.has(fieldType) || ['RICH_TEXT', 'SECRET'].includes(fieldType)
}
export function isSharedRuntimeField(fieldType: string) {
  return isBasicRuntimeField(fieldType) || ['DATE_RANGE', 'TIME', 'TIME_RANGE', 'RICH_TEXT', 'JSON', 'MONEY', 'PERCENT', 'RATING', 'PROGRESS', 'ADDRESS', 'LOCATION', 'SINGLE_SELECT', 'MULTI_SELECT', 'TAG', 'STATUS', 'SECRET'].includes(fieldType)
}

/** 地址和位置在列表、详情使用同一业务摘要，不将 JSON 对象直接展示给用户。 */
export function formatPlaceFieldValue(fieldType: string, value: unknown) {
  if (!['ADDRESS', 'LOCATION'].includes(fieldType)) return undefined
  if (!value || typeof value !== 'object' || Array.isArray(value)) return '—'
  const part = value as Record<string, unknown>
  if (fieldType === 'ADDRESS') return ['province', 'city', 'district', 'detail']
    .map(key => typeof part[key] === 'string' ? part[key] : '').join('') || '—'
  const coordinate = `${part.latitude ?? ''}, ${part.longitude ?? ''}`
  return typeof part.name === 'string' && part.name ? `${part.name}（${coordinate}）` : coordinate
}

/** 日期和时间范围按业务的起止值展示，保存值仍保持两个元素的数组。 */
export function formatTemporalFieldValue(fieldType: string, value: unknown) {
  if (fieldType === 'TIME') return typeof value === 'string' ? value : '—'
  if (fieldType !== 'DATE_RANGE' && fieldType !== 'TIME_RANGE') return undefined
  if (!Array.isArray(value) || value.length !== 2) return '—'
  const [start, end] = value
  return typeof start === 'string' && typeof end === 'string' && start && end ? `${start} ～ ${end}` : '—'
}
export function supportsFieldSearch(fieldType: string) { return !['JSON', 'ADDRESS', 'LOCATION', 'SECRET'].includes(fieldType) }
export function supportsContentSort(fieldType: string) { return !['RICH_TEXT', 'JSON', 'ADDRESS', 'LOCATION', 'SECRET', 'MULTI_SELECT', 'TAG'].includes(fieldType) }

export function builtInTextFormat(fieldType: string) {
  return ({
    PHONE: '自动校验并规范化电话号码，可输入空格、短横线和括号',
    EMAIL: '自动校验完整邮箱地址',
    URL: '自动校验完整的 http 或 https 地址',
    ID_CARD: '自动校验 15 位或 18 位居民身份证号、出生日期和校验位',
  } as Record<string, string>)[fieldType] || ''
}

export function supportsUniqueValue(fieldType: string) {
  return !nonUniqueFieldTypes.has(fieldType)
}

export function defaultEditor(fieldType: string): 'TEXT' | 'NUMBER' | 'DATE' | 'DATETIME' | 'BOOLEAN' {
  if (fieldType === 'BOOLEAN') return 'BOOLEAN'
  if (fieldType === 'DATE') return 'DATE'
  if (fieldType === 'DATETIME') return 'DATETIME'
  if (fieldFamily(fieldType) === 'NUMBER') return 'NUMBER'
  return 'TEXT'
}

/** 金额始终按字段发布配置显示；用字符串补位，避免前端浮点换算改写业务金额。 */
export function moneyConfiguration(configJson?: string) {
  let config: { currency?: unknown; precision?: unknown } = {}
  try { config = JSON.parse(configJson || '{}') as typeof config } catch { /* 无效配置由服务端发布校验处理。 */ }
  const configured = Number(config.precision)
  return {
    currency: typeof config.currency === 'string' ? config.currency.trim() : '',
    precision: Number.isInteger(configured) && configured >= 0 && configured <= 6 ? configured : 2,
  }
}

export function moneyPrecisionError(value: unknown, precision: number) {
  if (value === undefined || value === null || value === '') return ''
  const match = /^-?\d+(?:\.(\d+))?$/.exec(String(value).trim())
  if (!match) return '' // 数字格式仍由输入控件和服务端校验。
  return (match[1] || '').replace(/0+$/, '').length > precision ? `最多保留 ${precision} 位小数` : ''
}

export function formatMoneyValue(value: unknown, configJson?: string) {
  const { currency, precision } = moneyConfiguration(configJson)
  const raw = String(value).trim()
  const match = /^(-?)(\d+)(?:\.(\d+))?$/.exec(raw)
  if (!match) return currency ? `${currency} ${raw}` : raw
  const fraction = (match[3] || '').padEnd(precision, '0')
  const amount = `${match[1]}${match[2]}${precision ? `.${fraction}` : ''}`
  return currency ? `${currency} ${amount}` : amount
}

export function filterOperators(fieldType?: string) {
  // 秘密值不参与任何条件推断，展示权限不能变成查询权限。
  if (fieldType === 'SECRET') return []
  const common = [{ value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' }]
  const empty = [{ value: 'EMPTY', label: '为空' }, { value: 'NOT_EMPTY', label: '不为空' }]
  const family = fieldFamily(fieldType)
  if (fieldType === 'JSON') return empty
  if (fieldType === 'RICH_TEXT') return [...common, { value: 'CONTAINS', label: '包含' }, ...empty]
  if (family === 'BOOLEAN' || family === 'SELECTION') return [...common, ...empty]
  if (family === 'NUMBER' || family === 'DATE') return [...common,
    { value: 'GT', label: '大于' }, { value: 'GTE', label: '大于等于' },
    { value: 'LT', label: '小于' }, { value: 'LTE', label: '小于等于' }, ...empty]
  return [...common, { value: 'CONTAINS', label: '包含' }, ...empty]
}
