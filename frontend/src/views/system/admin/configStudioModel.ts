export const fieldTypeGroups = [
  {
    label: '基础字段',
    values: ['TEXT', 'TEXTAREA', 'PHONE', 'EMAIL', 'URL', 'IDENTITY', 'NUMBER', 'PERCENT', 'MONEY',
      'DATE', 'DATETIME', 'DATE_RANGE', 'TIME', 'TIME_RANGE', 'RADIO', 'MULTI_SELECT', 'CASCADE',
      'SWITCH', 'MEMBER', 'DEPARTMENT', 'TENANT', 'ATTACHMENT', 'IMAGE', 'FILE_GROUP'],
  },
  {
    label: '高级字段',
    values: ['AUTO_NUMBER', 'RELATION', 'REFERENCE', 'SUBTABLE', 'ADDRESS', 'GEO', 'RATING', 'PROGRESS',
      'TAG', 'BARCODE', 'SIGNATURE', 'RICH_TEXT', 'JSON', 'SECRET', 'STATUS', 'FORMULA', 'SUMMARY',
      'CALCULATED', 'LOOKUP', 'AGGREGATE', 'AI_FILL'],
  },
  { label: '系统字段', values: ['CREATED_BY', 'CREATED_AT', 'UPDATED_BY', 'UPDATED_AT'] },
] as const

export const textFieldTypes = new Set(['TEXT', 'TEXTAREA', 'PHONE', 'EMAIL', 'URL', 'IDENTITY', 'ADDRESS',
  'BARCODE', 'RICH_TEXT', 'SECRET', 'AUTO_NUMBER'])
export const numericFieldTypes = new Set(['NUMBER', 'PERCENT', 'MONEY', 'RATING', 'PROGRESS'])
export const orderedFieldTypes = new Set(['NUMBER', 'PERCENT', 'MONEY', 'RATING', 'PROGRESS', 'DATE',
  'DATETIME', 'TIME', 'CREATED_AT', 'UPDATED_AT'])
export const dateFieldTypes = new Set(['DATE', 'DATETIME', 'DATE_RANGE', 'TIME', 'TIME_RANGE', 'CREATED_AT', 'UPDATED_AT'])
export const dictionaryFieldTypes = new Set(['RADIO', 'MULTI_SELECT', 'CASCADE', 'TAG', 'STATUS'])
export const fileFieldTypes = new Set(['ATTACHMENT', 'IMAGE', 'FILE_GROUP', 'SIGNATURE'])
export const actorFieldTypes = new Set(['MEMBER', 'DEPARTMENT', 'TENANT'])
export const relationFieldTypes = new Set(['RELATION', 'REFERENCE', 'SUBTABLE', 'LOOKUP', 'AGGREGATE', 'SUMMARY'])
export const sourceFieldTypes = new Set(['AI_FILL'])
export const aiFillResultSchemas = ['STRING', 'DECIMAL', 'INTEGER', 'DATE', 'DATETIME', 'BOOLEAN'] as const
export const aiFillOverwriteModes = ['NEVER', 'CONFIRM'] as const
export const fieldPermissionModes = ['INHERIT', 'STAGED', 'ENFORCED'] as const
export const p4C4DerivedFieldTypes = new Set(['FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE'])
const systemComputedFieldTypes = new Set(['TENANT', 'AUTO_NUMBER', 'CREATED_BY', 'CREATED_AT', 'UPDATED_BY', 'UPDATED_AT'])
export const derivedResultSchemas = ['STRING', 'DECIMAL', 'INTEGER', 'DATE', 'DATETIME', 'BOOLEAN'] as const
export type DerivedResultSchema = typeof derivedResultSchemas[number]
export const derivedExpressionOperators = ['FIELD', 'ADD', 'SUBTRACT', 'MULTIPLY', 'DIVIDE', 'CONCAT',
  'EQ', 'NE', 'GT', 'GTE', 'LT', 'LTE', 'AND', 'OR', 'NOT', 'IF', 'ADD_DAYS', 'DAYS_BETWEEN'] as const

export interface DerivedExpressionOperand {
  kind: 'FIELD' | 'LITERAL'
  fieldId: string
  literalType: DerivedResultSchema
  literalValue: unknown
}

export function fieldPermissionCode(moduleCode: string, fieldCode: string, direction: 'read' | 'write') {
  return `module.${moduleCode.trim()}.field.${fieldCode.trim()}.${direction}`
}

export function supportsFieldWritePermission(type: string, readonly: boolean) {
  return !readonly
    && type !== 'REFERENCE'
    && type !== 'AI_FILL'
    && !p4C4DerivedFieldTypes.has(type)
    && !systemComputedFieldTypes.has(type)
}

export type DerivedExpressionNode =
  | { fieldId: string }
  | { literalType: DerivedResultSchema; value: unknown }
  | { op: string; args: DerivedExpressionNode[] }

const derivedScalarSchemas: Record<string, DerivedResultSchema> = {
  TEXT: 'STRING', TEXTAREA: 'STRING', PHONE: 'STRING', EMAIL: 'STRING', URL: 'STRING', BARCODE: 'STRING',
  RICH_TEXT: 'STRING', RADIO: 'STRING', STATUS: 'STRING', NUMBER: 'DECIMAL', PERCENT: 'DECIMAL',
  MONEY: 'DECIMAL', PROGRESS: 'DECIMAL', RATING: 'INTEGER', DATE: 'DATE', DATETIME: 'DATETIME', SWITCH: 'BOOLEAN',
}

export function derivedSchemaForField(field?: { type?: string; properties?: Record<string, unknown> }): DerivedResultSchema | undefined {
  if (!field?.type) return undefined
  if (p4C4DerivedFieldTypes.has(field.type)) {
    const declared = String(field.properties?.resultSchema ?? '')
    return derivedResultSchemas.includes(declared as DerivedResultSchema) ? declared as DerivedResultSchema : undefined
  }
  return derivedScalarSchemas[field.type]
}

export function isAiFillSourceField(field?: {
  type?: string
  hidden?: boolean
  status?: string
  properties?: Record<string, unknown>
}) {
  return Boolean(field?.type)
    && field?.hidden !== true
    && (!field?.status || field.status === 'ENABLED')
    && !p4C4DerivedFieldTypes.has(field!.type!)
    && field!.type !== 'AI_FILL'
    && derivedSchemaForField(field) !== undefined
}

export function derivedOperatorArity(operator?: string) {
  if (!operator || operator === 'FIELD' || operator === 'NOT') return 1
  if (operator === 'IF') return 3
  return 2
}

export function createDerivedOperand(fieldId = ''): DerivedExpressionOperand {
  return { kind: 'FIELD', fieldId, literalType: 'STRING', literalValue: '' }
}

function derivedLiteralValue(type: DerivedResultSchema, value: unknown) {
  if (type === 'DECIMAL') {
    const number = Number(value)
    if (!Number.isFinite(number)) throw new Error('DECIMAL 常量必须是有限数值')
    return number
  }
  if (type === 'INTEGER') {
    const number = Number(value)
    if (!Number.isSafeInteger(number)) throw new Error('INTEGER 常量必须是安全整数')
    return number
  }
  if (type === 'BOOLEAN') return value === true || String(value) === 'true'
  return String(value ?? '')
}

function buildDerivedOperand(operand: DerivedExpressionOperand): DerivedExpressionNode {
  if (operand.kind === 'LITERAL') {
    return { literalType: operand.literalType, value: derivedLiteralValue(operand.literalType, operand.literalValue) }
  }
  return { fieldId: String(operand.fieldId ?? '') }
}

export function buildDerivedExpressionAst(form: Record<string, any>): DerivedExpressionNode {
  const operator = String(form.expressionOperator ?? 'FIELD')
  const operands = Array.isArray(form.expressionOperands) ? form.expressionOperands : []
  const args = Array.from({ length: derivedOperatorArity(operator) }, (_, index) =>
    buildDerivedOperand(operands[index] ?? createDerivedOperand()))
  return operator === 'FIELD' ? args[0]! : { op: operator, args }
}

function readDerivedOperand(node?: Record<string, any>): DerivedExpressionOperand {
  if (node?.literalType) {
    return { kind: 'LITERAL', fieldId: '', literalType: node.literalType, literalValue: node.value }
  }
  return createDerivedOperand(String(node?.fieldId ?? ''))
}

export function readDerivedExpressionAst(form: Record<string, any>, source?: Record<string, any>) {
  form.expressionOperator = source?.op ?? 'FIELD'
  const nodes = Array.isArray(source?.args) ? source.args : [source]
  form.expressionOperands = nodes.map((node: Record<string, any>) => readDerivedOperand(node))
  while (form.expressionOperands.length < derivedOperatorArity(form.expressionOperator)) {
    form.expressionOperands.push(createDerivedOperand())
  }
}

export function inferDerivedExpressionSchema(
  node: DerivedExpressionNode | undefined,
  schemaForField: (id: string) => DerivedResultSchema | undefined,
): DerivedResultSchema | undefined {
  if (!node) return undefined
  if ('fieldId' in node) return schemaForField(node.fieldId)
  if ('literalType' in node) return node.literalType
  const schemas = node.args.map((item) => inferDerivedExpressionSchema(item, schemaForField))
  if (schemas.some((schema) => !schema)) return undefined
  const [first, second, third] = schemas
  const numeric = (schema?: DerivedResultSchema) => schema === 'DECIMAL' || schema === 'INTEGER'
  const ordered = (schema?: DerivedResultSchema) => numeric(schema) || schema === 'DATE' || schema === 'DATETIME'
  if (['ADD', 'SUBTRACT', 'MULTIPLY', 'DIVIDE'].includes(node.op)) return schemas.every(numeric) ? 'DECIMAL' : undefined
  if (node.op === 'CONCAT') return schemas.every((schema) => schema === 'STRING') ? 'STRING' : undefined
  if (['EQ', 'NE'].includes(node.op)) return first === second ? 'BOOLEAN' : undefined
  if (['GT', 'GTE', 'LT', 'LTE'].includes(node.op)) return first === second && ordered(first) ? 'BOOLEAN' : undefined
  if (['AND', 'OR'].includes(node.op)) return schemas.every((schema) => schema === 'BOOLEAN') ? 'BOOLEAN' : undefined
  if (node.op === 'NOT') return first === 'BOOLEAN' ? 'BOOLEAN' : undefined
  if (node.op === 'IF') return first === 'BOOLEAN' && second === third ? second : undefined
  if (node.op === 'ADD_DAYS') return (first === 'DATE' || first === 'DATETIME') && second === 'INTEGER' ? first : undefined
  if (node.op === 'DAYS_BETWEEN') return (first === 'DATE' || first === 'DATETIME') && first === second ? 'INTEGER' : undefined
  return undefined
}

export function derivedExpressionDependencies(node?: DerivedExpressionNode): string[] {
  if (!node) return []
  if ('fieldId' in node) return node.fieldId ? [node.fieldId] : []
  if ('literalType' in node) return []
  return [...new Set(node.args.flatMap(derivedExpressionDependencies))]
}

export function derivedFieldDependencyIds(field?: { type?: string; properties?: Record<string, any> }): string[] {
  if (!field?.type || !field.properties) return []
  if (field.type === 'FORMULA' || field.type === 'CALCULATED') {
    return derivedExpressionDependencies(field.properties.expressionAst as DerivedExpressionNode | undefined)
  }
  if (field.type === 'SUMMARY' || field.type === 'LOOKUP') {
    return [field.properties.relationFieldId, field.properties.targetFieldId].filter(Boolean).map(String)
  }
  if (field.type === 'AGGREGATE') return field.properties.subtableFieldId ? [String(field.properties.subtableFieldId)] : []
  return []
}

export function derivedCyclePath(
  ownerId: string,
  directDependencies: string[],
  fields: Array<{ id: string; type: string; properties: Record<string, any> }>,
): string[] {
  if (!ownerId) return []
  const byId = new Map(fields.map((field) => [field.id, field]))
  const visit = (fieldId: string, path: string[], visiting: Set<string>): string[] => {
    if (fieldId === ownerId) return [...path, fieldId]
    if (visiting.has(fieldId)) return []
    const field = byId.get(fieldId)
    if (!field) return []
    const nextVisiting = new Set(visiting).add(fieldId)
    for (const dependency of derivedFieldDependencyIds(field)) {
      const cycle = visit(dependency, [...path, fieldId], nextVisiting)
      if (cycle.length) return cycle
    }
    return []
  }
  for (const dependency of directDependencies) {
    const cycle = visit(dependency, [ownerId], new Set())
    if (cycle.length) return cycle
  }
  return []
}
export const systemFieldTypes = new Set(['CREATED_BY', 'CREATED_AT', 'UPDATED_BY', 'UPDATED_AT'])
export const p4C1ScalarFieldTypes = new Set(['PERCENT', 'MONEY', 'TIME', 'SWITCH', 'RATING', 'PROGRESS'])
export const p4C1CollectionFieldTypes = new Set(['DATE_RANGE', 'TIME_RANGE', 'MULTI_SELECT', 'CASCADE', 'TAG'])
export const p4C1FieldTypes = new Set([...p4C1ScalarFieldTypes, ...p4C1CollectionFieldTypes])
export const p4C2FieldTypes = new Set(['PHONE', 'EMAIL', 'URL', 'IDENTITY', 'ADDRESS', 'GEO', 'BARCODE',
  'RICH_TEXT', 'JSON', 'SECRET', 'STATUS'])
export const p4C2UniqueFieldTypes = new Set(['PHONE', 'EMAIL', 'URL', 'IDENTITY', 'BARCODE', 'SECRET'])
export const p4C2SortableFieldTypes = new Set(['BARCODE', 'STATUS'])
export const fixedDefaultFieldTypes = new Set([
  'TEXT', 'TEXTAREA', 'PHONE', 'EMAIL', 'URL', 'IDENTITY', 'ADDRESS', 'BARCODE', 'RICH_TEXT',
  'NUMBER', 'PERCENT', 'MONEY', 'RATING', 'PROGRESS', 'DATE', 'DATETIME', 'DATE_RANGE', 'TIME',
  'TIME_RANGE', 'RADIO', 'MULTI_SELECT', 'CASCADE', 'TAG', 'STATUS', 'SWITCH', 'JSON',
])

export function defaultModesForField(type?: string) {
  const modes = ['NONE']
  if (type && (['RELATION', 'REFERENCE', 'SUBTABLE'].includes(type)
    || p4C4DerivedFieldTypes.has(type) || sourceFieldTypes.has(type))) return modes
  if (type && fixedDefaultFieldTypes.has(type)) modes.push('FIXED')
  if (type === 'MEMBER') modes.push('CURRENT_USER')
  if (type === 'DEPARTMENT') modes.push('CURRENT_DEPARTMENT')
  if (type === 'DATE' || type === 'DATETIME') modes.push('CURRENT_DATE')
  if (type && !systemFieldTypes.has(type) && type !== 'AUTO_NUMBER') modes.push('FORMULA', 'PARENT_FIELD')
  return modes
}

export function indexModesForField(type?: string) {
  if (type === 'AI_FILL') return ['NONE']
  if (type === 'LOOKUP') return ['NONE', 'FILTER']
  if (type && p4C4DerivedFieldTypes.has(type)) return ['NONE', 'FILTER', 'SORT']
  if (type === 'RELATION') return ['NONE', 'FILTER']
  if (type === 'REFERENCE') return ['NONE', 'FILTER', 'SORT']
  if (type === 'SUBTABLE') return ['NONE', 'FILTER']
  if (type && p4C1CollectionFieldTypes.has(type)) return ['NONE', 'FILTER']
  if (type && p4C1ScalarFieldTypes.has(type)) return ['NONE', 'FILTER', 'SORT', 'UNIQUE']
  if (type && p4C2FieldTypes.has(type)) {
    const modes = ['NONE', 'FILTER']
    if (p4C2SortableFieldTypes.has(type)) modes.push('SORT')
    if (p4C2UniqueFieldTypes.has(type)) modes.push('UNIQUE')
    return modes
  }
  return ['NONE', 'FILTER', 'SORT', 'UNIQUE', 'STATISTIC']
}

export const ruleTypes = ['FIELD_VISIBILITY', 'FIELD_REQUIRED', 'FIELD_READ_ONLY', 'ACTION_ENABLED',
  'DELETE_ALLOWED', 'APPROVAL_REQUIRED'] as const
export const ruleEffectByType: Record<string, string> = {
  FIELD_VISIBILITY: 'VISIBLE',
  FIELD_REQUIRED: 'REQUIRED',
  FIELD_READ_ONLY: 'READ_ONLY',
  ACTION_ENABLED: 'ACTION_ENABLED',
  DELETE_ALLOWED: 'DELETE_ALLOWED',
  APPROVAL_REQUIRED: 'APPROVAL_REQUIRED',
}

export interface RuleConditionForm {
  key: number
  kind: 'condition'
  fieldId: string
  operator: string
  value: string | number
  valueEnd: string | number
}

export interface RuleGroupForm {
  key: number
  kind: 'group'
  join: 'AND' | 'OR'
  children: RuleNodeForm[]
}

export type RuleNodeForm = RuleConditionForm | RuleGroupForm

let conditionKey = 0

function nextConditionKey() {
  conditionKey += 1
  return conditionKey
}

export function createCondition(fieldId = ''): RuleConditionForm {
  return { key: nextConditionKey(), kind: 'condition', fieldId, operator: 'NOT_EMPTY', value: '', valueEnd: '' }
}

export function createConditionGroup(fieldId = '', children?: RuleNodeForm[]): RuleGroupForm {
  return { key: nextConditionKey(), kind: 'group', join: 'AND', children: children ?? [createCondition(fieldId)] }
}

export function operatorsForField(type?: string) {
  const values = ['EQ', 'NE', 'IN', 'NOT_IN', 'EMPTY', 'NOT_EMPTY']
  if (type && (textFieldTypes.has(type) || dictionaryFieldTypes.has(type))) values.push('CONTAINS')
  if (type && orderedFieldTypes.has(type)) values.push('GT', 'GTE', 'LT', 'LTE', 'BETWEEN')
  return values
}

function scalar(type: string | undefined, value: string | number) {
  if (type && numericFieldTypes.has(type) && value !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : value
  }
  if (type === 'SWITCH') return String(value) === 'true'
  return value
}

function buildConditionNode(node: RuleNodeForm, typeForField: (id: string) => string | undefined): Record<string, any> {
  if (node.kind === 'group') {
    return { join: node.join, children: node.children.map((child) => buildConditionNode(child, typeForField)) }
  }
  let value: unknown
  if (!['EMPTY', 'NOT_EMPTY'].includes(node.operator)) {
    if (['IN', 'NOT_IN'].includes(node.operator)) {
      value = String(node.value).split(',').map((item) => item.trim()).filter(Boolean)
        .map((item) => scalar(typeForField(node.fieldId), item))
    } else if (node.operator === 'BETWEEN') {
      value = [scalar(typeForField(node.fieldId), node.value), scalar(typeForField(node.fieldId), node.valueEnd)]
    } else {
      value = scalar(typeForField(node.fieldId), node.value)
    }
  }
  return { fieldId: node.fieldId, operator: node.operator, ...(value === undefined ? {} : { value }), children: [] }
}

export function buildCondition(node: RuleNodeForm, typeForField: (id: string) => string | undefined) {
  return buildConditionNode(node, typeForField)
}

function readConditionNode(source: Record<string, any> | undefined, fallbackFieldId: string): RuleNodeForm {
  if (source?.join && Array.isArray(source.children)) {
    return {
      key: nextConditionKey(),
      kind: 'group',
      join: source.join === 'OR' ? 'OR' : 'AND',
      children: source.children.length
        ? source.children.map((child: Record<string, any>) => readConditionNode(child, fallbackFieldId))
        : [createCondition(fallbackFieldId)],
    }
  }
  const operator = source?.operator ?? 'NOT_EMPTY'
  const values = Array.isArray(source?.value) ? source?.value : []
  return {
    ...createCondition(source?.fieldId ?? fallbackFieldId),
    operator,
    value: ['IN', 'NOT_IN'].includes(operator) ? values.join(', ') : values[0] ?? source?.value ?? '',
    valueEnd: operator === 'BETWEEN' ? values[1] ?? '' : '',
  }
}

export function readCondition(condition: Record<string, any> | undefined, fallbackFieldId: string): RuleGroupForm {
  const node = readConditionNode(condition, fallbackFieldId)
  return node.kind === 'group' ? node : createConditionGroup(fallbackFieldId, [node])
}
function compact(source: Record<string, any>) {
  return Object.fromEntries(Object.entries(source).filter(([, value]) => value !== undefined && value !== null
    && value !== '' && (!Array.isArray(value) || value.length > 0)))
}

export function buildFieldProperties(form: Record<string, any>, typeForRelationField: (id: string) => string | undefined = () => undefined) {
  const p4C3Composition = ['RELATION', 'REFERENCE', 'SUBTABLE'].includes(form.type)
  const p4C4Derived = p4C4DerivedFieldTypes.has(form.type)
  const governedMaterialized = p4C4Derived || form.type === 'AI_FILL'
  const common = governedMaterialized
    ? compact({ helpText: form.helpText, displayFormat: form.displayFormat, width: form.width, unit: form.unit })
    : compact({
      placeholder: form.placeholder, helpText: form.helpText, defaultMode: p4C3Composition ? undefined : form.defaultMode,
      defaultValue: !p4C3Composition && form.defaultMode === 'FIXED' ? buildFixedDefault(form) : undefined,
      defaultFormula: !p4C3Composition && form.defaultMode === 'FORMULA' ? form.defaultFormula : undefined,
      parentFieldId: !p4C3Composition && form.defaultMode === 'PARENT_FIELD' ? form.parentFieldId : undefined,
      displayFormat: form.displayFormat, width: form.width, unit: form.unit, mask: form.mask,
      sensitive: p4C3Composition ? undefined : form.sensitive,
      unique: p4C3Composition ? undefined : form.unique,
      validationMessage: form.validationMessage,
    })
  let specific: Record<string, any> = {}
  if (textFieldTypes.has(form.type) && !p4C2FieldTypes.has(form.type)) specific = compact({ minLength: form.minLength, maxLength: form.maxLength,
    pattern: form.pattern, trim: form.trim, multiline: form.multiline, sanitize: form.sanitize, rows: form.rows })
  if (numericFieldTypes.has(form.type)) specific = compact({ minimum: form.minimum, maximum: form.maximum,
    precision: form.precision, scale: form.scale, roundingMode: form.roundingMode,
    thousandsSeparator: form.thousandsSeparator, step: form.step })
  if (form.type === 'MONEY') {
    const currencies = Array.isArray(form.currencies) ? [...new Set(form.currencies)] : []
    specific = { ...specific, ...compact(currencies.length
      ? { currencies, fixedCurrency: form.fixedCurrency }
      : { currency: form.currency }) }
  }
  if (actorFieldTypes.has(form.type)) specific = compact({ multiple: form.multiple,
    selectionScope: form.selectionScope, allowInactive: form.allowInactive })
  if (dateFieldTypes.has(form.type)) specific = compact({ minimum: form.minimum, maximum: form.maximum,
    includeTime: form.includeTime, timezone: form.timezone, format: form.format })
  if ((dictionaryFieldTypes.has(form.type) && form.type !== 'STATUS') || form.type === 'SWITCH') specific = compact({ multiple: form.multiple,
    clearable: form.clearable, searchable: form.optionSearchable, displayStyle: form.displayStyle,
    maxSelections: form.maxSelections })
  if (fileFieldTypes.has(form.type)) specific = compact({ maxFiles: form.maxFiles, maxSizeMb: form.maxSizeMb,
    allowedExtensions: String(form.allowedExtensions ?? '').split(',').map((item) => item.trim()).filter(Boolean),
    imageOnly: form.imageOnly, preview: form.preview, watermark: form.watermark })
  if (form.type === 'RELATION') specific = compact({ multiple: form.multiple,
    displayFieldId: form.displayFieldId, allowCreate: form.allowCreate,
    reverseRelation: form.reverseRelation,
    filter: form.useRelationFilter && form.relationFilter
      ? buildCondition(form.relationFilter, typeForRelationField)
      : undefined })
  else if (form.type === 'REFERENCE') specific = compact({
    sourceFieldId: form.sourceFieldId, targetFieldId: form.targetFieldId,
  })
  else if (form.type === 'SUBTABLE') specific = compact({
    minRows: form.minRows, maxRows: form.maxRows, columnFieldIds: form.columnFieldIds,
    allowRowCreate: form.allowRowCreate, allowRowUpdate: form.allowRowUpdate,
    allowRowDelete: form.allowRowDelete, allowRowReorder: form.allowRowReorder,
    aggregates: Array.isArray(form.aggregates) ? form.aggregates.map((item: Record<string, unknown>) => compact({
      id: item.id, function: item.function, columnFieldId: item.function === 'COUNT' ? undefined : item.columnFieldId,
    })) : [],
  })
  else if (!p4C4Derived && relationFieldTypes.has(form.type)) specific = compact({ multiple: form.multiple,
    displayFieldId: form.displayFieldId, valueFieldId: form.valueFieldId, cascadeDelete: form.cascadeDelete,
    aggregation: form.aggregation, sourceFieldId: form.sourceFieldId, allowCreate: form.allowCreate,
    reverseRelation: form.reverseRelation,
    filter: form.useRelationFilter && form.relationFilter
      ? buildCondition(form.relationFilter, typeForRelationField)
      : undefined })
  let advanced: Record<string, any> = {}
  if (form.type === 'FORMULA' || form.type === 'CALCULATED') advanced = {
    resultSchema: form.resultSchema,
    astVersion: 1,
    expressionAst: buildDerivedExpressionAst(form),
  }
  if (form.type === 'SUMMARY') advanced = compact({
    resultSchema: form.resultSchema,
    relationFieldId: form.relationFieldId,
    targetFieldId: form.reduction === 'COUNT' ? undefined : form.targetFieldId,
    reduction: form.reduction,
  })
  if (form.type === 'LOOKUP') advanced = compact({
    resultSchema: form.resultSchema,
    relationFieldId: form.relationFieldId,
    targetFieldId: form.targetFieldId,
    distinct: form.distinct,
  })
  if (form.type === 'AGGREGATE') advanced = compact({
    resultSchema: 'DECIMAL',
    subtableFieldId: form.subtableFieldId,
    aggregateId: form.aggregateId,
  })
  if (form.type === 'AI_FILL') {
    const resultSchema = String(form.resultSchema ?? '')
    const sourceFieldIds = [...new Set((Array.isArray(form.sourceFieldIds) ? form.sourceFieldIds : [])
      .map(String).filter(Boolean))]
    const promptTemplate = String(form.promptTemplate ?? '').trim()
    const modelPolicy = String(form.modelPolicy ?? '')
    const minConfidence = Number(form.minConfidence)
    const overwriteMode = String(form.overwriteMode ?? '')
    if (!aiFillResultSchemas.includes(resultSchema as typeof aiFillResultSchemas[number])) {
      throw new Error('AI_FILL 结果类型必须是受支持的标量类型')
    }
    if (sourceFieldIds.length < 1 || sourceFieldIds.length > 16) {
      throw new Error('AI_FILL 来源字段必须选择 1 到 16 个')
    }
    if (!promptTemplate || promptTemplate.length > 4000) {
      throw new Error('AI_FILL 提示模板长度必须是 1 到 4000 个字符')
    }
    if (modelPolicy !== 'SYSTEM_DEFAULT') {
      throw new Error('AI_FILL 模型策略必须是 SYSTEM_DEFAULT')
    }
    if (!Number.isFinite(minConfidence) || minConfidence < 0.5 || minConfidence > 1) {
      throw new Error('AI_FILL 最低置信度必须在 0.50 到 1.00 之间')
    }
    if (!aiFillOverwriteModes.includes(overwriteMode as typeof aiFillOverwriteModes[number])) {
      throw new Error('AI_FILL 覆盖模式必须是 NEVER 或 CONFIRM')
    }
    advanced = {
      resultSchema, sourceFieldIds, promptTemplate, modelPolicy, minConfidence, overwriteMode,
    }
  }
  if (form.type === 'AUTO_NUMBER') advanced = compact({ autoNumberPrefix: form.autoNumberPrefix, digits: form.digits })
  if (form.type === 'PHONE') advanced = compact({ defaultCountry: String(form.defaultCountry ?? '').toUpperCase() })
  if (form.type === 'IDENTITY') advanced = compact({
    identityKind: form.identityKind,
    pattern: form.identityKind === 'GENERIC' ? form.pattern : undefined,
  })
  if (form.type === 'GEO') advanced = { coordinateSystem: 'WGS84' }
  if (form.type === 'BARCODE') advanced = {
    symbologies: [...new Set(Array.isArray(form.symbologies) ? form.symbologies : [])],
  }
  if (form.type === 'RICH_TEXT') advanced = { sanitize: true }
  if (form.type === 'JSON') {
    let jsonSchema: unknown
    try { jsonSchema = JSON.parse(String(form.jsonSchemaText || '{}')) }
    catch { throw new Error('JSON Schema 必须是有效 JSON') }
    if (!jsonSchema || typeof jsonSchema !== 'object' || Array.isArray(jsonSchema)) {
      throw new Error('JSON Schema 根节点必须是对象')
    }
    advanced = { jsonSchema, queryPaths: (form.queryPaths ?? []).map((path: Record<string, unknown>) => ({
      pathSnapshotId: String(path.pathSnapshotId ?? ''),
      path: String(path.path ?? '').trim(),
      type: String(path.type ?? '').toUpperCase(),
    })) }
  }
  if (form.type === 'SECRET') advanced = { minLength: 1, maxLength: 4096 }
  if (form.type === 'STATUS') advanced = {
    initialStateIds: [...new Set(form.initialStateIds ?? [])],
    transitions: (form.transitions ?? []).map((edge: Record<string, unknown>) => ({
      from: String(edge.from ?? ''), to: String(edge.to ?? ''),
    })),
  }
  if (form.type === 'RATING') advanced = compact({ maxRating: form.maxRating, step: form.step })
  if (form.type === 'PROGRESS') advanced = compact({ step: form.step })
  return { ...common, ...specific, ...advanced }
}

function buildFixedDefault(form: Record<string, any>) {
  if (form.type === 'MONEY') {
    const currency = form.defaultMoneyCurrency || form.fixedCurrency || form.currencies?.[0] || form.currency
    return { amount: String(form.defaultMoneyAmount ?? '').trim(), currency }
  }
  if (form.type === 'DATE_RANGE' || form.type === 'TIME_RANGE') return form.defaultValueRange
  if (form.type === 'JSON' && typeof form.defaultValue === 'string') {
    try { return JSON.parse(form.defaultValue) }
    catch { throw new Error('固定默认值必须是有效 JSON') }
  }
  return form.defaultValue
}

export function readFieldProperties(form: Record<string, any>, properties: Record<string, any> = {}) {
  const { searchable, filter, ...rest } = properties
  Object.assign(form, rest)
  form.defaultMode = properties.defaultMode ?? (properties.defaultValue !== undefined ? 'FIXED' : 'NONE')
  if (form.type === 'DATE_RANGE' || form.type === 'TIME_RANGE') {
    form.defaultValueRange = Array.isArray(properties.defaultValue) ? [...properties.defaultValue] : ['', '']
  } else if (form.type === 'MONEY' && properties.defaultValue && typeof properties.defaultValue === 'object') {
    form.defaultMoneyAmount = String(properties.defaultValue.amount ?? '')
    form.defaultMoneyCurrency = properties.defaultValue.currency ?? properties.fixedCurrency
  } else if (form.type === 'JSON' && properties.defaultValue !== undefined) {
    form.defaultValue = JSON.stringify(properties.defaultValue, null, 2)
  }
  if (searchable !== undefined) form.optionSearchable = searchable
  if (form.type === 'MONEY') {
    if (!Array.isArray(form.currencies) || !form.currencies.length) form.currencies = [properties.currency ?? 'CNY']
    if (!form.defaultMoneyCurrency) form.defaultMoneyCurrency = properties.fixedCurrency ?? form.currencies[0]
  }
  if (Array.isArray(properties.allowedExtensions)) form.allowedExtensions = properties.allowedExtensions.join(', ')
  if (!Array.isArray(form.sourceFieldIds)) form.sourceFieldIds = []
  if (form.type === 'AI_FILL') {
    form.resultSchema = aiFillResultSchemas.includes(properties.resultSchema)
      ? properties.resultSchema : 'STRING'
    form.modelPolicy = 'SYSTEM_DEFAULT'
    form.minConfidence = Number(properties.minConfidence ?? 0.8)
    form.overwriteMode = aiFillOverwriteModes.includes(properties.overwriteMode)
      ? properties.overwriteMode : 'CONFIRM'
  }
  if ((form.type === 'FORMULA' || form.type === 'CALCULATED') && properties.expressionAst) {
    readDerivedExpressionAst(form, properties.expressionAst as Record<string, any>)
  }
  if (!Array.isArray(form.columnFieldIds)) form.columnFieldIds = []
  if (filter && typeof filter === 'object') {
    form.useRelationFilter = true
    form.relationFilter = readCondition(filter, '')
  } else {
    form.useRelationFilter = false
  }
  if (properties.jsonSchema && typeof properties.jsonSchema === 'object') {
    form.jsonSchemaType = properties.jsonSchema.type ?? 'object'
    form.jsonItemType = properties.jsonSchema.items?.type ?? 'string'
    form.jsonAllowAdditional = properties.jsonSchema.additionalProperties ?? true
    form.jsonSchemaText = JSON.stringify(properties.jsonSchema, null, 2)
  }
  if (!form.jsonSchemaText) form.jsonSchemaText = JSON.stringify({ type: 'object', additionalProperties: false }, null, 2)
  form.queryPaths = Array.isArray(properties.queryPaths) ? properties.queryPaths.map((path: Record<string, unknown>) => ({ ...path })) : []
  form.symbologies = Array.isArray(properties.symbologies) ? [...properties.symbologies] : ['CODE128', 'EAN13']
  form.initialStateIds = Array.isArray(properties.initialStateIds) ? [...properties.initialStateIds] : []
  form.transitions = Array.isArray(properties.transitions) ? properties.transitions.map((edge: Record<string, unknown>) => ({ ...edge })) : []
}
