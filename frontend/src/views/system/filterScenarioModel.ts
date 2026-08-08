import type { ConfigPageLayout, RuntimeDefinition, RuntimeFilterNode, RuntimeRecordSchema, RuntimeSortItem, SharedFilterScenario } from '@/types/config'

export const MAX_SHARED_FILTER_SCENARIOS = 10

export interface SharedFilterScenarioDraft {
  code: string
  name: string
  filterJson: string
  sortJson: string
}

export interface SharedFilterScenarioConfig {
  scenarios: SharedFilterScenario[]
  defaultCode: string | null
}

export class SharedFilterScenarioValidationError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'SharedFilterScenarioValidationError'
  }
}

const codePattern = /^[a-z][a-z0-9]*(?:_[a-z0-9]+)*$/u
const fieldCodePattern = /^[A-Za-z][A-Za-z0-9_-]{0,63}$/u
const operatorPattern = /^[A-Z][A-Z0-9_]{0,63}$/u

function exactKeys(value: Record<string, unknown>, allowed: string[]) {
  const keys = Object.keys(value).sort()
  return keys.length === allowed.length && keys.every((key, index) => key === [...allowed].sort()[index])
}

function parseFilter(value: unknown, state = { predicates: 0 }, depth = 1): value is RuntimeFilterNode {
  if (!value || typeof value !== 'object' || Array.isArray(value) || depth > 5) return false
  const node = value as Record<string, unknown>
  if (node.kind === 'PREDICATE') {
    const allowed = 'value' in node ? ['fieldCode', 'kind', 'operator', 'value'] : ['fieldCode', 'kind', 'operator']
    if (!exactKeys(node, allowed)
      || typeof node.fieldCode !== 'string' || !fieldCodePattern.test(node.fieldCode)
      || typeof node.operator !== 'string' || !operatorPattern.test(node.operator)) return false
    state.predicates += 1
    return state.predicates <= 20
  }
  if (!['AND', 'OR', 'NOT'].includes(String(node.kind)) || !exactKeys(node, ['children', 'kind']) || !Array.isArray(node.children)) return false
  if (!node.children.length || node.children.length > 20 || node.kind === 'NOT' && node.children.length !== 1) return false
  return node.children.every((child) => parseFilter(child, state, depth + 1))
}

function parseSort(value: unknown): value is RuntimeSortItem[] {
  if (!Array.isArray(value) || value.length > 3) return false
  const fields = new Set<string>()
  return value.every((item) => {
    if (!item || typeof item !== 'object' || Array.isArray(item)) return false
    const sort = item as Record<string, unknown>
    const allowed = 'currency' in sort ? ['currency', 'direction', 'fieldCode', 'nulls'] : ['direction', 'fieldCode', 'nulls']
    if (!exactKeys(sort, allowed)
      || typeof sort.fieldCode !== 'string' || !fieldCodePattern.test(sort.fieldCode)
      || !['ASC', 'DESC'].includes(String(sort.direction))
      || !['FIRST', 'LAST'].includes(String(sort.nulls))
      || ('currency' in sort && (typeof sort.currency !== 'string' || !/^[A-Z]{3}$/u.test(sort.currency)))) return false
    if (fields.has(sort.fieldCode)) return false
    fields.add(sort.fieldCode)
    return true
  })
}

function parseJson(text: string, label: string) {
  try {
    return JSON.parse(text) as unknown
  } catch {
    throw new SharedFilterScenarioValidationError(`${label}不是有效的 JSON。`)
  }
}

function validateScenario(value: unknown, label: string): SharedFilterScenario {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new SharedFilterScenarioValidationError(`${label}结构无效。`)
  const scenario = value as Record<string, unknown>
  if (!exactKeys(scenario, ['code', 'filter', 'name', 'sort'])) throw new SharedFilterScenarioValidationError(`${label}包含未知字段。`)
  const code = typeof scenario.code === 'string' ? scenario.code : ''
  const name = typeof scenario.name === 'string' ? scenario.name : ''
  if (!codePattern.test(code) || code.length > 64) throw new SharedFilterScenarioValidationError(`${label}编码必须是 1–64 位小写 snake_case。`)
  if (!name || name !== name.trim() || name.length > 100) throw new SharedFilterScenarioValidationError(`${label}名称必须是 1–100 位非空文本。`)
  if (scenario.filter !== null && !parseFilter(scenario.filter)) throw new SharedFilterScenarioValidationError(`${label}筛选 JSON 不是受支持的 canonical filter。`)
  if (!parseSort(scenario.sort)) throw new SharedFilterScenarioValidationError(`${label}排序 JSON 不是受支持的 canonical sort。`)
  if (scenario.filter === null && scenario.sort.length === 0) throw new SharedFilterScenarioValidationError(`${label}必须至少包含筛选或排序。`)
  return { code, name, filter: scenario.filter as RuntimeFilterNode | null, sort: scenario.sort }
}

export function emptySharedFilterScenarioDraft(): SharedFilterScenarioDraft {
  return { code: '', name: '', filterJson: 'null', sortJson: '[]' }
}

export function draftsFromPageLayout(layout: ConfigPageLayout): { drafts: SharedFilterScenarioDraft[]; defaultCode: string | null } {
  const config = parseScenarioConfig(layout)
  return {
    drafts: config.scenarios.map((scenario) => ({
      code: scenario.code,
      name: scenario.name,
      filterJson: JSON.stringify(scenario.filter, null, 2),
      sortJson: JSON.stringify(scenario.sort, null, 2),
    })),
    defaultCode: config.defaultCode,
  }
}

export function buildScenarioConfig(drafts: SharedFilterScenarioDraft[], defaultCode: string | null | undefined): SharedFilterScenarioConfig {
  if (!Array.isArray(drafts) || drafts.length > MAX_SHARED_FILTER_SCENARIOS) {
    throw new SharedFilterScenarioValidationError(`共享筛选方案最多 ${MAX_SHARED_FILTER_SCENARIOS} 项。`)
  }
  const scenarios = drafts.map((draft, index) => validateScenario({
    code: draft.code.trim(),
    name: draft.name.trim(),
    filter: parseJson(draft.filterJson, `第 ${index + 1} 项筛选 JSON`),
    sort: parseJson(draft.sortJson, `第 ${index + 1} 项排序 JSON`),
  }, `第 ${index + 1} 项共享筛选方案`))
  const codes = scenarios.map((scenario) => scenario.code)
  const names = scenarios.map((scenario) => scenario.name)
  if (new Set(codes).size !== codes.length) throw new SharedFilterScenarioValidationError('共享筛选方案编码不能重复。')
  if (new Set(names).size !== names.length) throw new SharedFilterScenarioValidationError('共享筛选方案名称不能重复。')
  const normalizedDefault = defaultCode?.trim() || null
  if (normalizedDefault && !codes.includes(normalizedDefault)) throw new SharedFilterScenarioValidationError('默认共享筛选方案必须引用当前页面中的方案。')
  return { scenarios, defaultCode: normalizedDefault }
}

function parseScenarioConfig(layout: unknown): SharedFilterScenarioConfig {
  if (!layout || typeof layout !== 'object' || Array.isArray(layout)) return { scenarios: [], defaultCode: null }
  const source = layout as Record<string, unknown>
  if (source.filterScenarios === undefined) return { scenarios: [], defaultCode: null }
  if (!Array.isArray(source.filterScenarios) || source.filterScenarios.length > MAX_SHARED_FILTER_SCENARIOS) return { scenarios: [], defaultCode: null }
  try {
    const scenarios = source.filterScenarios.map((scenario, index) => validateScenario(scenario, `第 ${index + 1} 项共享筛选方案`))
    if (new Set(scenarios.map((scenario) => scenario.code)).size !== scenarios.length
      || new Set(scenarios.map((scenario) => scenario.name)).size !== scenarios.length) return { scenarios: [], defaultCode: null }
    const defaultCode = source.defaultFilterScenarioCode === null || source.defaultFilterScenarioCode === undefined
      ? null : String(source.defaultFilterScenarioCode)
    if (defaultCode && !scenarios.some((scenario) => scenario.code === defaultCode)) return { scenarios: [], defaultCode: null }
    return { scenarios, defaultCode }
  } catch {
    return { scenarios: [], defaultCode: null }
  }
}

function enabledDefaultListPage(page: Record<string, unknown>) {
  const defaultFlag = page.is_default === true || page.is_default === 1 || page.is_default === '1'
  return page.page_type === 'LIST' && page.desired_status === 'ENABLED' && defaultFlag
}

export function sharedFilterScenarioConfig(definition: RuntimeDefinition | null): SharedFilterScenarioConfig {
  if (!definition) return { scenarios: [], defaultCode: null }
  const page = definition.pages.find(enabledDefaultListPage)
  return page ? parseScenarioConfig(page.layout_json) : { scenarios: [], defaultCode: null }
}

function filterSupported(node: RuntimeFilterNode, fields: Map<string, RuntimeRecordSchema['fields'][number]>): boolean {
  if (node.kind === 'PREDICATE') {
    const field = fields.get(node.fieldCode)
    return Boolean(field
      && !field.mode.startsWith('SENSITIVE')
      && field.type !== 'IDENTITY'
      && field.type !== 'SECRET'
      && field.operators.includes(node.operator))
  }
  return node.children.every((child) => filterSupported(child, fields))
}

export function sharedFilterScenarioSupported(scenario: SharedFilterScenario, schema: RuntimeRecordSchema | null) {
  if (!schema) return false
  const fields = new Map(schema.fields.map((field) => [field.fieldCode, field]))
  if (scenario.filter && !filterSupported(scenario.filter, fields)) return false
  return scenario.sort.every((sort) => {
    const field = fields.get(sort.fieldCode)
    return Boolean(field && field.sortable && !field.mode.startsWith('SENSITIVE') && field.type !== 'IDENTITY' && field.type !== 'SECRET')
  })
}

export function copyScenarioQuery(scenario: SharedFilterScenario) {
  return {
    filter: JSON.parse(JSON.stringify(scenario.filter)) as RuntimeFilterNode | null,
    sort: JSON.parse(JSON.stringify(scenario.sort)) as RuntimeSortItem[],
  }
}
