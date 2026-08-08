import type {
  PageType,
  RuntimeFieldCapability,
  RuntimePublishedCondition,
  RuntimePublishedPage,
  RuntimeRecordSchema,
  RuntimeRuleDecision,
} from '@/types/config'

export function publishedPage(schema: RuntimeRecordSchema | null, type: PageType): RuntimePublishedPage | null {
  return schema?.publishedRuntime?.pages.find((page) => page.type === type) ?? null
}

export function orderedPublishedFields(
  schema: RuntimeRecordSchema | null,
  type: PageType,
  fallback: (field: RuntimeFieldCapability) => boolean,
): RuntimeFieldCapability[] {
  if (!schema) return []
  const page = publishedPage(schema, type)
  if (!page?.fields.length) return schema.fields.filter(fallback)
  const byCode = new Map(schema.fields.map((field) => [field.fieldCode, field]))
  return page.fields.map((placement) => byCode.get(placement.fieldCode)).filter(Boolean) as RuntimeFieldCapability[]
}

export function evaluatePublishedRules(
  schema: RuntimeRecordSchema | null,
  values: Record<string, unknown>,
): RuntimeRuleDecision {
  const hiddenFields = new Set<string>()
  const requiredFields = new Set(schema?.fields.filter((field) => field.schema.required === true).map((field) => field.fieldCode) ?? [])
  const readonlyFields = new Set(schema?.fields.filter((field) => !field.writable).map((field) => field.fieldCode) ?? [])
  const disabledActions = new Set<string>()
  const applied = new Set<string>()
  let deleteAllowed = true
  let approvalRequired = false
  let deleteResolved = false
  let approvalResolved = false

  const rules = [...(schema?.publishedRuntime?.rules ?? [])]
    .sort((left, right) => left.priority - right.priority || left.ruleCode.localeCompare(right.ruleCode))
  for (const rule of rules) {
    if (!conditionMatches(rule.condition, values)) continue
    for (const effect of rule.effects) {
      const key = `${effect.effect}:${effect.targetCode ?? ''}`
      if (applied.has(key)) continue
      applied.add(key)
      if (effect.effect === 'VISIBLE') membership(hiddenFields, effect.targetCode, !effect.value)
      else if (effect.effect === 'REQUIRED') membership(requiredFields, effect.targetCode, effect.value)
      else if (effect.effect === 'READ_ONLY') membership(readonlyFields, effect.targetCode, effect.value)
      else if (effect.effect === 'ACTION_ENABLED' && !effect.value) {
        if (effect.targetCode) disabledActions.add(effect.targetCode)
        if (effect.targetAction) disabledActions.add(effect.targetAction)
      } else if (effect.effect === 'DELETE_ALLOWED' && !deleteResolved) {
        deleteAllowed = effect.value
        deleteResolved = true
      } else if (effect.effect === 'APPROVAL_REQUIRED' && !approvalResolved) {
        approvalRequired = effect.value
        approvalResolved = true
      }
    }
  }
  return { hiddenFields, requiredFields, readonlyFields, disabledActions, deleteAllowed, approvalRequired }
}

function membership(target: Set<string>, value: string | null | undefined, present: boolean) {
  if (!value) return
  if (present) target.add(value)
  else target.delete(value)
}

function conditionMatches(condition: RuntimePublishedCondition, values: Record<string, unknown>): boolean {
  if (condition.join) {
    return condition.join === 'AND'
      ? condition.children.every((child) => conditionMatches(child, values))
      : condition.children.some((child) => conditionMatches(child, values))
  }
  const actual = condition.fieldCode ? values[condition.fieldCode] : undefined
  const expected = condition.value
  if (condition.operator === 'EMPTY') return empty(actual)
  if (condition.operator === 'NOT_EMPTY') return !empty(actual)
  if (condition.operator === 'EQ') return compare(actual, expected) === 0
  if (condition.operator === 'NE') return compare(actual, expected) !== 0
  if (condition.operator === 'GT') return compare(actual, expected) > 0
  if (condition.operator === 'GTE') return compare(actual, expected) >= 0
  if (condition.operator === 'LT') return compare(actual, expected) < 0
  if (condition.operator === 'LTE') return compare(actual, expected) <= 0
  if (condition.operator === 'IN') return Array.isArray(expected) && expected.some((item) => compare(item, actual) === 0)
  if (condition.operator === 'NOT_IN') return Array.isArray(expected) && !expected.some((item) => compare(item, actual) === 0)
  if (condition.operator === 'CONTAINS') return contains(actual, expected)
  if (condition.operator === 'BETWEEN') return Array.isArray(expected) && expected.length === 2
    && compare(actual, expected[0]) >= 0 && compare(actual, expected[1]) <= 0
  return false
}

function empty(value: unknown) {
  return value == null || value === '' || Array.isArray(value) && value.length === 0
    || typeof value === 'object' && !Array.isArray(value) && Object.keys(value as object).length === 0
}

function contains(container: unknown, candidate: unknown) {
  if (Array.isArray(container)) return container.some((item) => compare(item, candidate) === 0)
  return typeof container === 'string' && typeof candidate === 'string' && container.includes(candidate)
}

function compare(left: unknown, right: unknown) {
  if (empty(left) && empty(right)) return 0
  const leftNumber = typeof left === 'number' ? left : Number.NaN
  const rightNumber = typeof right === 'number' ? right : Number.NaN
  if (!Number.isNaN(leftNumber) && !Number.isNaN(rightNumber)) return leftNumber - rightNumber
  if (typeof left === 'boolean' && typeof right === 'boolean') return Number(left) - Number(right)
  return String(left ?? '').localeCompare(String(right ?? ''))
}
