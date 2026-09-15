export interface ConditionField { code: string; name: string; fieldType: string; configJson: string }
export interface ConditionLeaf { field: string; operator: string; value?: unknown }
export interface ConditionGroup { mode: 'ALL' | 'ANY'; conditions: ConditionNode[] }
export type ConditionNode = ConditionGroup | ConditionLeaf
export function isConditionGroup(node: ConditionNode): node is ConditionGroup { return 'conditions' in node }
export function emptyCondition(): ConditionGroup { return { mode: 'ALL', conditions: [] } }
export const conditionOperatorLabels: Record<string, string> = {
  EQ: '等于', NE: '不等于', GT: '大于', GTE: '大于等于', LT: '小于', LTE: '小于等于',
  IN: '属于任一值', CONTAINS: '包含', EMPTY: '为空', NOT_EMPTY: '不为空',
}
export function operatorsFor(field?: ConditionField) {
  const type = field?.fieldType
  const codes = ['EQ', 'NE', 'EMPTY', 'NOT_EMPTY']
  if (['NUMBER', 'MONEY', 'DATE', 'DATETIME'].includes(type || '')) codes.push('GT', 'GTE', 'LT', 'LTE')
  if (type !== 'MULTI_SELECT') codes.push('IN')
  if (['TEXT', 'MULTILINE_TEXT', 'PHONE', 'EMAIL', 'URL', 'ID_CARD', 'MULTI_SELECT'].includes(type || '')) codes.push('CONTAINS')
  return codes.map(value => ({ value, label: conditionOperatorLabels[value] }))
}
export function initialConditionValue(field?: ConditionField): unknown {
  if (['NUMBER', 'MONEY'].includes(field?.fieldType || '')) return 0
  if (field?.fieldType === 'BOOLEAN') return false
  if (field?.fieldType === 'MULTI_SELECT') return []
  return ''
}
export function newCondition(field?: ConditionField): ConditionLeaf {
  return { field: field?.code || '', operator: field?.fieldType === 'MULTI_SELECT' ? 'CONTAINS' : 'EQ',
    value: field?.fieldType === 'MULTI_SELECT' ? '' : initialConditionValue(field) }
}
export function conditionFieldCodes(node: ConditionNode): string[] {
  return [...new Set(isConditionGroup(node) ? node.conditions.flatMap(conditionFieldCodes) : [node.field])]
}
export function conditionIssues(node: ConditionNode, fields: ConditionField[], depth = 0): string[] {
  if (depth > 8 || conditionFieldCount(node) > 100) return ['条件最多 100 项、8 层']
  if (isConditionGroup(node)) {
    return [...(node.mode === 'ANY' && !node.conditions.length ? ['任一满足的条件组不能为空'] : []),
      ...node.conditions.flatMap(child => conditionIssues(child, fields, depth + 1))]
  }
  const field = fields.find(field => field.code === node.field)
  if (!field) return ['请重新选择已发布且可读取的字段']
  if (!operatorsFor(field).some(op => op.value === node.operator)) return [`${field.name}的运算符不受支持`]
  if (['EMPTY', 'NOT_EMPTY'].includes(node.operator)) return []
  if (node.value === undefined || node.value === null || typeof node.value === 'number' && !Number.isFinite(node.value)) return [`请填写${field.name}的比较值`]
  if (node.operator === 'IN' && (!Array.isArray(node.value) || !node.value.length)) return [`请添加${field.name}的比较值`]
  return []
}
function conditionFieldCount(node: ConditionNode): number { return isConditionGroup(node) ? 1 + node.conditions.reduce((sum, child) => sum + conditionFieldCount(child), 0) : 1 }
export function conditionSummary(node: ConditionNode, fields: ConditionField[]): string {
  if (isConditionGroup(node)) return !node.conditions.length ? '始终满足' : `${node.mode === 'ALL' ? '全部满足' : '任一满足'}（${node.conditions.map(child => conditionSummary(child, fields)).join('；')}）`
  const name = fields.find(field => field.code === node.field)?.name || '不可用字段'
  const value = Array.isArray(node.value) ? node.value.join('、') : typeof node.value === 'boolean' ? node.value ? '是' : '否' : String(node.value ?? '')
  return `${name} ${conditionOperatorLabels[node.operator] || '未配置'}${['EMPTY', 'NOT_EMPTY'].includes(node.operator) ? '' : ` ${value}`}`
}
/** Compatibility only: old bindings are displayed as fields, never as an editable script. */
export function bindingCondition(condition?: ConditionNode | null, legacy?: string): ConditionGroup {
  if (condition) return JSON.parse(JSON.stringify(isConditionGroup(condition) ? condition : { mode: 'ALL', conditions: [condition] }))
  if (!legacy) return emptyCondition()
  const match = /^\s*([A-Za-z_][A-Za-z0-9_]*)\s*(>=|<=|!=|==|>|<)\s*(.+?)\s*$/.exec(legacy)
  if (!match) return { mode: 'ALL', conditions: [{ field: '', operator: 'EQ' }] }
  const text = match[3]!
  const value = /^['"].*['"]$/.test(text) ? text.slice(1, -1) : /^-?\d+(\.\d+)?$/.test(text) ? Number(text) : text
  return { mode: 'ALL', conditions: [{ field: match[1]!, operator: ({ '>': 'GT', '<': 'LT', '>=': 'GTE', '<=': 'LTE', '==': 'EQ', '!=': 'NE' })[match[2]!]!, value }] }
}
