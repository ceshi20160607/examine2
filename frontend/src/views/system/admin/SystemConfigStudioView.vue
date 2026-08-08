<script setup lang="ts">
import { message, Modal } from 'ant-design-vue'
import { Blocks, BookOpen, CheckCircle2, CircleAlert, Copy, GitCompareArrows, History, LayoutTemplate, Pencil, Plus, RefreshCw, Rocket, RotateCcw, Settings2, ShieldCheck, Trash2 } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import MessageTemplateManager from '@/components/admin/MessageTemplateManager.vue'
import PrintTemplateManager from '@/components/admin/PrintTemplateManager.vue'
import RuleConditionNodeEditor from './RuleConditionNodeEditor.vue'
import { useAdminViewport } from '@/composables/useAdminViewport'
import { ApiRequestError } from '@/services/api'
import { systemAdminApi } from '@/services/admin'
import { configApi } from '@/services/config'
import { configRecoveryApi } from '@/services/configRecovery'
import { useSessionStore } from '@/stores/session'
import type { SystemMember } from '@/types/admin'
import type { CheckReport, ConfigAction, ConfigComponent, ConfigDictionary, ConfigDictionaryItem, ConfigField, ConfigGroup, ConfigModule, ConfigPage, ConfigRoot, ConfigRule, ConfigVersion, PermissionPreview, VersionDiff } from '@/types/config'
import { actorFieldTypes, aiFillOverwriteModes, aiFillResultSchemas, buildCondition, buildDerivedExpressionAst, buildFieldProperties, createConditionGroup, createDerivedOperand, dateFieldTypes, defaultModesForField, derivedCyclePath, derivedExpressionDependencies, derivedExpressionOperators, derivedOperatorArity, derivedSchemaForField, dictionaryFieldTypes, fieldPermissionCode, fieldPermissionModes, fieldTypeGroups, fileFieldTypes, indexModesForField, inferDerivedExpressionSchema, isAiFillSourceField, numericFieldTypes, p4C1FieldTypes, p4C2FieldTypes, p4C4DerivedFieldTypes, readCondition, readFieldProperties, ruleEffectByType, ruleTypes, supportsFieldWritePermission, textFieldTypes, type DerivedResultSchema } from './configStudioModel'
import { buildScenarioConfig, draftsFromPageLayout, emptySharedFilterScenarioDraft, MAX_SHARED_FILTER_SCENARIOS, SharedFilterScenarioValidationError } from '../filterScenarioModel'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const { isMobile } = useAdminViewport()
const systemId = computed(() => String(route.params.systemId))
const loading = ref(false)
const mutating = ref(false)
const errorMessage = ref('')
const root = ref<ConfigRoot | null>(null)
const groups = ref<ConfigGroup[]>([])
const modules = ref<ConfigModule[]>([])
const dictionaries = ref<ConfigDictionary[]>([])
const fields = ref<ConfigField[]>([])
const pages = ref<ConfigPage[]>([])
const components = ref<ConfigComponent[]>([])
const actions = ref<ConfigAction[]>([])
const rules = ref<ConfigRule[]>([])
const versions = ref<ConfigVersion[]>([])
const checkReport = ref<CheckReport | null>(null)
const selectedModuleId = ref('')
const selectedPageId = ref('')
const activeTab = ref('fields')
const modal = ref<'group' | 'module' | 'copy' | 'dictionary' | 'dictionaryItem' | 'field' | 'page' | 'component' | 'action' | 'rule' | 'publish' | 'rollback' | null>(null)
const rollbackTarget = ref<ConfigVersion | null>(null)
const form = reactive<Record<string, any>>({})
const editTarget = ref<{ kind: string; item: any } | null>(null)
const dictionaryOpen = ref(false)
const selectedDictionaryId = ref('')
const dictionaryItems = ref<ConfigDictionaryItem[]>([])
const defaultDictionaryItems = ref<ConfigDictionaryItem[]>([])
const previewOpen = ref(false)
const previewLoading = ref(false)
const previewMembers = ref<SystemMember[]>([])
const previewMemberId = ref('')
const previewTenantId = ref('')
const previewResult = ref<PermissionPreview | null>(null)
const relationFields = ref<ConfigField[]>([])
const diffOpen = ref(false)
const diffLoading = ref(false)
const diffFromId = ref('')
const diffToId = ref('')
const diffResult = ref<VersionDiff | null>(null)
const inspector = ref<{ kind: 'module' | 'field' | 'page' | 'component' | 'action' | 'rule'; item: any } | null>(null)

function queryString(value: unknown) {
  return Array.isArray(value) ? String(value[0] ?? '') : typeof value === 'string' ? value : ''
}

const requestedResource = computed(() => {
  const moduleCode = queryString(route.query.moduleCode)
  const resourceKind = queryString(route.query.resourceKind)
  const resourceCode = queryString(route.query.resourceCode)
  if (!moduleCode || !resourceCode || (resourceKind !== 'page' && resourceKind !== 'field')) return null
  return { moduleCode, resourceKind: resourceKind as 'page' | 'field', resourceCode }
})

const selectedModule = computed(() => modules.value.find((item) => item.id === selectedModuleId.value))
const selectedPage = computed(() => pages.value.find((item) => item.id === selectedPageId.value))
const focusedAgentResource = computed(() => {
  const requested = requestedResource.value
  if (!requested || selectedModule.value?.code !== requested.moduleCode) return null
  if (inspector.value?.kind !== requested.resourceKind || inspector.value.item.code !== requested.resourceCode) return null
  return requested
})
const moduleGroups = computed(() => groups.value.map((group) => ({ ...group, modules: modules.value.filter((item) => item.groupId === group.id) })))
const canPublish = computed(() => root.value?.status === 'CHECKED' && root.value.lastCheck?.status === 'PASSED')
const statusLabels: Record<string, string> = { CLEAN: '已同步', DIRTY: '有未发布修改', CHECKING: '检查中', CHECK_FAILED: '检查未通过', CHECKED: '检查通过', PUBLISHING: '发布中' }
const rootStatus = computed(() => statusLabels[root.value?.status ?? ''] ?? root.value?.status)
const effectTargetKind = computed(() => form.type === 'ACTION_ENABLED' ? 'action' : ['DELETE_ALLOWED', 'APPROVAL_REQUIRED'].includes(form.type) ? 'none' : 'field')
const defaultModeOptions = computed(() => defaultModesForField(form.type))
const fieldIndexModes = computed(() => {
  const modes = indexModesForField(form.type)
  return p4C4DerivedFieldTypes.has(form.type) && derivedResultSchema.value === 'BOOLEAN'
    ? modes.filter((mode) => mode !== 'SORT') : modes
})
const p4C3CompositionTypes = new Set(['RELATION', 'REFERENCE', 'SUBTABLE'])
const targetModuleFieldTypes = new Set(['RELATION', 'SUBTABLE'])
const defaultModeLabels: Record<string, string> = { NONE: '无默认值', FIXED: '固定值', CURRENT_USER: '当前用户', CURRENT_DEPARTMENT: '当前部门', CURRENT_DATE: '当前日期', FORMULA: '公式', PARENT_FIELD: '上级字段联动' }
const permissionModeLabels: Record<string, string> = { INHERIT: 'INHERIT（沿用现有模块规则）', STAGED: 'STAGED（注册权限，暂不限制）', ENFORCED: 'ENFORCED（按角色授权执行）' }
const writePermissionSupported = computed(() => supportsFieldWritePermission(String(form.type ?? ''), Boolean(form.readonly)))
const readPermissionCode = computed(() => fieldPermissionCode(selectedModule.value?.code ?? '', String(form.code ?? ''), 'read'))
const writePermissionCode = computed(() => fieldPermissionCode(selectedModule.value?.code ?? '', String(form.code ?? ''), 'write'))
const stagedPermissionTarget = computed(() => {
  if (root.value?.status !== 'CLEAN' || !selectedModule.value) return null
  const field = fields.value.find((item) => item.readPermissionMode === 'STAGED' || item.writePermissionMode === 'STAGED')
  if (!field) return null
  return {
    field,
    prefix: `module.${selectedModule.value.code}.field.${field.code}.`,
  }
})
const referenceSourceRelations = computed(() => fields.value.filter((item) => item.type === 'RELATION' && item.properties?.multiple !== true))
const referenceSourceRelation = computed(() => referenceSourceRelations.value.find((item) => item.id === form.sourceFieldId))
const derivedSourceRelations = computed(() => fields.value.filter((item) => item.type === 'RELATION'))
const derivedSourceRelation = computed(() => derivedSourceRelations.value.find((item) => item.id === form.relationFieldId))
const effectiveTargetModuleId = computed(() => form.type === 'REFERENCE'
  ? referenceSourceRelation.value?.targetModuleId ?? ''
  : form.type === 'SUMMARY' || form.type === 'LOOKUP'
    ? derivedSourceRelation.value?.targetModuleId ?? '' : form.targetModuleId)
const relationDisplayFields = computed(() => effectiveTargetModuleId.value === selectedModuleId.value ? fields.value : relationFields.value)
const referenceTargetPreview = computed(() => relationDisplayFields.value.find((item) => item.id === form.targetFieldId))
const selectedSubtableColumns = computed(() => relationDisplayFields.value.filter((item) => form.columnFieldIds?.includes(item.id)))
const derivedCurrentFields = computed(() => fields.value.filter((item) =>
  item.id !== editTarget.value?.item.id && derivedSchemaForField(item) !== undefined))
const aiFillSourceFields = computed(() => fields.value.filter((item) =>
  item.id !== editTarget.value?.item.id && isAiFillSourceField(item)))
const derivedTargetFields = computed(() => relationDisplayFields.value.filter((item) => derivedSchemaForField(item) !== undefined))
const derivedSelectableTargetFields = computed(() => {
  if (form.type !== 'SUMMARY') return derivedTargetFields.value
  if (form.reduction === 'SUM' || form.reduction === 'AVG') {
    return derivedTargetFields.value.filter((item) => ['DECIMAL', 'INTEGER'].includes(derivedSchemaForField(item) ?? ''))
  }
  return derivedTargetFields.value.filter((item) => ['DECIMAL', 'INTEGER', 'DATE', 'DATETIME'].includes(derivedSchemaForField(item) ?? ''))
})
const derivedSubtables = computed(() => fields.value.filter((item) => item.type === 'SUBTABLE'))
const selectedAggregateSubtable = computed(() => derivedSubtables.value.find((item) => item.id === form.subtableFieldId))
const derivedAggregateOptions = computed(() => Array.isArray(selectedAggregateSubtable.value?.properties?.aggregates)
  ? selectedAggregateSubtable.value.properties.aggregates as Array<{ id: string; function: string }> : [])
const expressionOperands = computed(() => Array.isArray(form.expressionOperands) ? form.expressionOperands : [])
const expressionArity = computed(() => derivedOperatorArity(form.expressionOperator))
const expressionAst = computed(() => {
  if (!['FORMULA', 'CALCULATED'].includes(form.type)) return undefined
  try { return buildDerivedExpressionAst(form) } catch { return undefined }
})
const derivedResultSchema = computed<DerivedResultSchema | undefined>(() => {
  if (form.type === 'FORMULA' || form.type === 'CALCULATED') {
    return inferDerivedExpressionSchema(expressionAst.value, (id) =>
      derivedSchemaForField(fields.value.find((item) => item.id === id)))
  }
  if (form.type === 'SUMMARY') {
    if (form.reduction === 'COUNT') return 'INTEGER'
    const targetSchema = derivedSchemaForField(derivedTargetFields.value.find((item) => item.id === form.targetFieldId))
    if (form.reduction === 'SUM' || form.reduction === 'AVG') {
      return targetSchema === 'DECIMAL' || targetSchema === 'INTEGER' ? 'DECIMAL' : undefined
    }
    return ['DECIMAL', 'INTEGER', 'DATE', 'DATETIME'].includes(targetSchema ?? '') ? targetSchema : undefined
  }
  if (form.type === 'LOOKUP') return derivedSchemaForField(derivedTargetFields.value.find((item) => item.id === form.targetFieldId))
  if (form.type === 'AGGREGATE') return 'DECIMAL'
  return undefined
})
const derivedDependencyLabels = computed(() => {
  if (form.type === 'FORMULA' || form.type === 'CALCULATED') {
    return derivedExpressionDependencies(expressionAst.value).map((id) => fields.value.find((item) => item.id === id)?.name ?? id)
  }
  if (form.type === 'SUMMARY' || form.type === 'LOOKUP') {
    const relation = derivedSourceRelation.value?.name
    const target = form.reduction === 'COUNT' ? undefined : derivedTargetFields.value.find((item) => item.id === form.targetFieldId)?.name
    return [relation, target].filter(Boolean) as string[]
  }
  if (form.type === 'AGGREGATE') {
    const aggregate = derivedAggregateOptions.value.find((item) => item.id === form.aggregateId)
    return [selectedAggregateSubtable.value?.name, aggregate?.id].filter(Boolean) as string[]
  }
  return []
})
const derivedDirectDependencyIds = computed(() => {
  if (form.type === 'FORMULA' || form.type === 'CALCULATED') return derivedExpressionDependencies(expressionAst.value)
  if (form.type === 'SUMMARY' || form.type === 'LOOKUP') {
    return [form.relationFieldId, form.reduction === 'COUNT' ? undefined : form.targetFieldId].filter(Boolean).map(String)
  }
  if (form.type === 'AGGREGATE') return form.subtableFieldId ? [String(form.subtableFieldId)] : []
  return []
})
const derivedCycle = computed(() => derivedCyclePath(
  String(editTarget.value?.item.id ?? ''),
  derivedDirectDependencyIds.value,
  [...fields.value, ...relationFields.value],
))
const derivedCycleLabels = computed(() => derivedCycle.value.map((id) =>
  [...fields.value, ...relationFields.value].find((field) => field.id === id)?.name ?? id))
const derivedRequiredSelectionsReady = computed(() => !(
  (['FORMULA', 'CALCULATED'].includes(form.type) && !derivedResultSchema.value)
  || (['SUMMARY', 'LOOKUP'].includes(form.type) && !form.relationFieldId)
  || (form.type === 'SUMMARY' && form.reduction !== 'COUNT' && !form.targetFieldId)
  || (form.type === 'LOOKUP' && !form.targetFieldId)
  || (form.type === 'AGGREGATE' && (!form.subtableFieldId || !form.aggregateId))
))
const derivedContractValid = computed(() => Boolean(derivedResultSchema.value)
  && derivedRequiredSelectionsReady.value && !derivedCycle.value.length)
const derivedOperatorLabels: Record<string, string> = {
  FIELD: '直接取值', ADD: '加法', SUBTRACT: '减法', MULTIPLY: '乘法', DIVIDE: '除法', CONCAT: '文本拼接',
  EQ: '等于', NE: '不等于', GT: '大于', GTE: '大于等于', LT: '小于', LTE: '小于等于',
  AND: '并且', OR: '或者', NOT: '取反', IF: '条件分支', ADD_DAYS: '日期加天数', DAYS_BETWEEN: '日期相差天数',
}
const inspectorDetails = computed(() => {
  if (!inspector.value) return []
  const { kind, item } = inspector.value
  const details = [
    ['编码', item.code ?? item.key],
    ['类型', item.type ?? 'MODULE'],
    ['状态', item.status ?? selectedModule.value?.status],
  ]
  if (kind === 'field') details.push(['必填', item.required ? '是' : '否'], ['索引', item.indexMode])
  if (kind === 'component') details.push(['网格', `${item.gridRow}:${item.gridColumn} / ${item.gridSpan}`])
  if (kind === 'action') details.push(['位置', item.placement], ['权限', item.permissionCode])
  if (kind === 'rule') details.push(['优先级', String(item.priority)])
  if (kind === 'page') details.push(['默认页', item.isDefault ? '是' : '否'])
  return details.filter((detail) => detail[1] !== undefined && detail[1] !== null && detail[1] !== '')
})

function fieldType(fieldId: string) {
  return fields.value.find((item) => item.id === fieldId)?.type
}

function relationFieldType(fieldId: string) {
  return relationDisplayFields.value.find((item) => item.id === fieldId)?.type
}

function initializeRelationFilter() {
  form.relationFilter = createConditionGroup(relationDisplayFields.value[0]?.id ?? '')
}

function ensureExpressionOperands() {
  if (!Array.isArray(form.expressionOperands)) form.expressionOperands = []
  while (form.expressionOperands.length < expressionArity.value) {
    form.expressionOperands.push(createDerivedOperand(derivedCurrentFields.value[0]?.id ?? ''))
  }
  if (form.expressionOperands.length > expressionArity.value) {
    form.expressionOperands.splice(expressionArity.value)
  }
}

function expressionOperatorChanged(operator: string) {
  form.expressionOperator = operator
  ensureExpressionOperands()
}

function expressionOperandLabel(index: number) {
  if (form.expressionOperator === 'IF') return ['条件', '成立时', '不成立时'][index]
  if (form.expressionOperator === 'NOT') return '布尔值'
  if (form.expressionOperator === 'ADD_DAYS') return index === 0 ? '日期' : '天数'
  return `操作数 ${index + 1}`
}

function derivedRelationChanged() {
  form.targetFieldId = ''
}

function summaryReductionChanged(reduction: string) {
  form.reduction = reduction
  form.targetFieldId = ''
}

function aggregateSubtableChanged() {
  form.aggregateId = derivedAggregateOptions.value[0]?.id ?? ''
}

function literalTypeChanged(operand: Record<string, any>, type: DerivedResultSchema) {
  operand.literalType = type
  operand.literalValue = type === 'BOOLEAN' ? false : type === 'DECIMAL' || type === 'INTEGER' ? 0 : ''
}

function fieldTypeChanged(type: string) {
  form.type = type
  if (!fieldIndexModes.value.includes(form.indexMode)) form.indexMode = form.filterable ? 'FILTER' : 'NONE'
  if (p4C1FieldTypes.has(type)) {
    form.searchable = false
    if (form.filterable && form.indexMode === 'NONE') form.indexMode = 'FILTER'
  }
  if (type === 'MONEY' && (!Array.isArray(form.currencies) || !form.currencies.length)) {
    form.currencies = ['CNY', 'USD']
    form.defaultMoneyCurrency = 'CNY'
  }
  if (p4C2FieldTypes.has(type)) {
    form.searchable = type === 'RICH_TEXT'
    if (type === 'IDENTITY' || type === 'SECRET') form.sensitive = true
  }
  if (p4C4DerivedFieldTypes.has(type)) {
    form.required = false
    form.readonly = true
    form.searchable = false
    form.sensitive = false
    form.unique = false
    form.defaultMode = 'NONE'
    if (type === 'FORMULA' || type === 'CALCULATED') {
      form.expressionOperator = 'FIELD'
      form.expressionOperands = [createDerivedOperand(derivedCurrentFields.value[0]?.id ?? '')]
    }
    if (type === 'SUMMARY' || type === 'LOOKUP') {
      form.relationFieldId = derivedSourceRelations.value[0]?.id ?? ''
      form.reduction = type === 'SUMMARY' ? 'COUNT' : undefined
      form.targetFieldId = ''
      form.distinct = false
    }
    if (type === 'AGGREGATE') {
      form.subtableFieldId = derivedSubtables.value[0]?.id ?? ''
      form.aggregateId = ''
    }
  }
}

function openStagedRoleGrants() {
  const target = stagedPermissionTarget.value
  if (!target) return
  router.push({
    name: 'system-admin-roles',
    params: { systemId: systemId.value },
    query: { permissionPrefix: target.prefix },
  })
}

let temporarySnapshotSequence = 0

function nextTemporarySnapshotId() {
  temporarySnapshotSequence += 1
  return (BigInt(Date.now()) * 100_000n + BigInt(temporarySnapshotSequence)).toString()
}

function addJsonQueryPath() {
  if (!Array.isArray(form.queryPaths)) form.queryPaths = []
  form.queryPaths.push({ pathSnapshotId: nextTemporarySnapshotId(), path: '$.', type: 'STRING' })
}

function addStatusTransition() {
  if (!Array.isArray(form.transitions)) form.transitions = []
  const enabled = defaultDictionaryItems.value.filter((item) => item.status === 'ENABLED')
  form.transitions.push({ from: enabled[0]?.id ?? '', to: enabled[1]?.id ?? '' })
}

function addSubtableAggregate() {
  if (!Array.isArray(form.aggregates)) form.aggregates = []
  form.aggregates.push({ id: `aggregate_${form.aggregates.length + 1}`, function: 'COUNT', columnFieldId: '' })
}

function relationFilterFieldIds(node: any): string[] {
  if (!node) return []
  if (node.kind === 'condition') return node.fieldId ? [node.fieldId] : []
  return Array.isArray(node.children) ? node.children.flatMap(relationFilterFieldIds) : []
}


function inspect(kind: 'module' | 'field' | 'page' | 'component' | 'action' | 'rule', item: any) {
  inspector.value = { kind, item }
}

function selectModule(item: ConfigModule) {
  selectedModuleId.value = item.id
  inspect('module', item)
}

function editInspected() {
  if (inspector.value) openEdit(inspector.value.kind, inspector.value.item)
}

function deleteInspected() {
  if (inspector.value) removeResource(inspector.value.kind, inspector.value.item)
}

function report(error: unknown) {
  errorMessage.value = error instanceof ApiRequestError ? `${error.message} (${error.code})` : '操作失败，请稍后重试'
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [rootValue, groupValues, moduleValues, dictionaryValues, versionValues] = await Promise.all([
      configApi.root(systemId.value), configApi.groups(systemId.value), configApi.modules(systemId.value),
      configApi.dictionaries(systemId.value), configApi.versions(systemId.value),
    ])
    root.value = rootValue
    groups.value = groupValues
    modules.value = moduleValues
    dictionaries.value = dictionaryValues
    versions.value = versionValues
    checkReport.value = rootValue.lastCheck ? await configApi.checkReport(systemId.value, rootValue.lastCheck.id) : null
    const requestedModule = requestedResource.value
      ? moduleValues.find((item) => item.code === requestedResource.value?.moduleCode)
      : undefined
    if (requestedModule) selectedModuleId.value = requestedModule.id
    else if (!selectedModuleId.value || !moduleValues.some((item) => item.id === selectedModuleId.value)) selectedModuleId.value = moduleValues[0]?.id ?? ''
  } catch (error) { report(error) } finally { loading.value = false }
}

async function loadModule(preferredPageId?: string) {
  if (!selectedModuleId.value) { fields.value = []; pages.value = []; actions.value = []; rules.value = []; return }
  try {
    const [fieldValues, pageValues, actionValues, ruleValues] = await Promise.all([
      configApi.fields(systemId.value, selectedModuleId.value), configApi.pages(systemId.value, selectedModuleId.value),
      configApi.actions(systemId.value, selectedModuleId.value), configApi.rules(systemId.value, selectedModuleId.value),
    ])
    fields.value = fieldValues; pages.value = pageValues; actions.value = actionValues; rules.value = ruleValues
    const requested = selectedModule.value?.code === requestedResource.value?.moduleCode ? requestedResource.value : null
    const requestedPage = requested?.resourceKind === 'page'
      ? pageValues.find((item) => item.code === requested.resourceCode)
      : undefined
    selectedPageId.value = requestedPage?.id ?? (preferredPageId && pageValues.some((item) => item.id === preferredPageId)
      ? preferredPageId
      : pageValues.some((item) => item.id === selectedPageId.value) ? selectedPageId.value : pageValues[0]?.id ?? '')
    if (requestedPage) {
      activeTab.value = 'pages'
      inspect('page', requestedPage)
    } else if (requested?.resourceKind === 'field') {
      const requestedField = fieldValues.find((item) => item.code === requested.resourceCode)
      if (requestedField) {
        activeTab.value = 'fields'
        inspect('field', requestedField)
      }
    }
  } catch (error) { report(error) }
}

async function loadComponents() {
  if (!selectedModuleId.value || !selectedPageId.value) { components.value = []; return }
  try { components.value = await configApi.components(systemId.value, selectedModuleId.value, selectedPageId.value) }
  catch (error) { report(error) }
}

watch(selectedModuleId, () => loadModule())
watch(selectedPageId, loadComponents)
watch(effectiveTargetModuleId, async (moduleId) => {
  if (!moduleId || moduleId === selectedModuleId.value) relationFields.value = []
  else try { relationFields.value = await configApi.fields(systemId.value, moduleId) }
  catch (error) { report(error) }
  if (form.useRelationFilter) {
    const available = new Set(relationDisplayFields.value.map((item) => item.id))
    const selected = relationFilterFieldIds(form.relationFilter)
    if (!selected.length || selected.some((id) => !available.has(id))) initializeRelationFilter()
  }
})

function open(type: typeof modal.value, target?: ConfigVersion) {
  modal.value = type; rollbackTarget.value = target ?? null
  editTarget.value = null
  Object.keys(form).forEach((key) => delete form[key])
  Object.assign(form, {
    code: '', name: '', description: '', iconKey: '', groupId: groups.value[0]?.id,
    type: type === 'dictionary' || type === 'page' ? 'LIST' : type === 'field' ? 'TEXT' : type === 'action' ? 'CREATE' : type === 'rule' ? 'FIELD_VISIBILITY' : 'FIELD',
    placement: 'TOOLBAR', fieldId: fields.value[0]?.id, pageId: selectedPageId.value, targetModuleId: selectedModuleId.value,
    required: false, hidden: false, readonly: false, searchable: true, filterable: true, showInList: true, showInDetail: true,
    readPermissionMode: 'INHERIT', writePermissionMode: 'INHERIT',
    status: 'ENABLED', sortOrder: type === 'group' ? groups.value.length : type === 'module' ? modules.value.length : type === 'dictionaryItem' ? dictionaryItems.value.length : type === 'field' ? fields.value.length : type === 'component' ? components.value.length : type === 'action' ? actions.value.length : 0, isDefault: false, color: '', semanticKey: '', parentId: null,
    placeholder: '', helpText: '', defaultMode: 'NONE', defaultValue: '', defaultValueRange: ['', ''], defaultFormula: '', parentFieldId: '', displayFormat: '', width: undefined, unit: '', mask: '', sensitive: false, unique: false, validationMessage: '',
    minLength: undefined, maxLength: type === 'field' ? 200 : undefined, pattern: '', trim: true, multiline: false, sanitize: true, rows: 3,
    minimum: undefined, maximum: undefined, precision: 18, scale: 2, roundingMode: 'HALF_UP', thousandsSeparator: false,
    includeTime: false, timezone: 'Asia/Shanghai', format: '', multiple: false, clearable: true, optionSearchable: true, displayStyle: 'DEFAULT', maxSelections: undefined,
    maxFiles: 1, maxSizeMb: 20, allowedExtensions: '', imageOnly: false, preview: true, watermark: false,
    displayFieldId: '', valueFieldId: '', targetFieldId: '', cascadeDelete: false, aggregation: '', sourceFieldId: '', sourceFieldIds: [],
    allowCreate: false, reverseRelation: false, useRelationFilter: false, relationFilter: createConditionGroup(fields.value[0]?.id ?? ''),
    minRows: 0, maxRows: 100, columnFieldIds: [], allowRowCreate: true, allowRowUpdate: true, allowRowDelete: true, allowRowReorder: true, aggregates: [], allowImport: true, allowExport: true,
    resultSchema: '', expressionOperator: 'FIELD', expressionOperands: [createDerivedOperand(fields.value[0]?.id ?? '')],
    relationFieldId: '', reduction: 'COUNT', distinct: false, subtableFieldId: '', aggregateId: '',
    promptTemplate: '', modelPolicy: 'SYSTEM_DEFAULT', minConfidence: 0.8,
    overwriteMode: 'CONFIRM', autoNumberPrefix: '', digits: 6,
    addressLevel: 'CITY', coordinateSystem: 'WGS84', geoPrecision: 6, currency: 'CNY', currencies: ['CNY', 'USD'], fixedCurrency: '', defaultMoneyAmount: '', defaultMoneyCurrency: 'CNY', selectionScope: 'ALL',
    allowInactive: false, jsonSchemaType: 'object', jsonItemType: 'string', jsonAllowAdditional: true,
    defaultCountry: 'CN', identityKind: 'CN_RESIDENT_ID', symbologies: ['CODE128', 'EAN13'],
    jsonSchemaText: JSON.stringify({ type: 'object', additionalProperties: false }, null, 2), queryPaths: [],
    initialStateIds: [], transitions: [],
    maxRating: 5, step: 1, indexMode: type === 'field' ? 'FILTER' : undefined,
    columns: 12, gap: 12, labelPosition: 'TOP', density: 'DEFAULT', stickyActions: true, pageSize: 20, showSearch: true, showFilters: true,
    filterScenarios: [], defaultFilterScenarioCode: null,
    parentComponentId: null, gridRow: 0, gridColumn: 0, gridSpan: 12, content: '', collapsible: false, collapsed: false, actionId: actions.value[0]?.id,
    confirmMessage: '', style: 'PRIMARY', openMode: 'CURRENT', successMessage: '', targetPageId: '', selectionMode: 'SINGLE', danger: false, disabledReason: '',
    priority: 100, ruleCondition: createConditionGroup(fields.value[0]?.id ?? ''), effectValue: true,
    reason: type === 'rollback' ? `恢复 V${target?.versionNo} 为新草稿` : '',
  })
  if (type === 'copy' && selectedModule.value) {
    form.groupId = selectedModule.value.groupId
    form.name = `${selectedModule.value.name} 副本`
    form.code = `${selectedModule.value.code}_copy`
  }
}

function openEdit(kind: Exclude<typeof modal.value, 'copy' | 'publish' | 'rollback' | null>, item: any) {
  open(kind)
  editTarget.value = { kind, item }
  Object.assign(form, {
    code: item.code ?? item.key,
    name: item.name ?? item.label ?? item.properties?.label,
    description: item.description ?? '', category: item.category ?? '', iconKey: item.iconKey ?? '', groupId: item.groupId,
    type: item.type, placement: item.placement, confirmMessage: item.confirmMessage ?? '',
    dictionaryId: item.dictionaryId, parentId: item.parentId ?? null, semanticKey: item.semanticKey ?? '',
    color: item.color ?? '', isDefault: item.isDefault ?? false, sortOrder: item.sortOrder ?? 0,
    status: item.status ?? 'ENABLED', required: item.required ?? false, hidden: item.hidden ?? false,
    readonly: item.readonly ?? false, searchable: item.searchable ?? false,
    filterable: item.filterable ?? false, showInList: item.showInList ?? false,
    showInDetail: item.showInDetail ?? false, fieldId: item.fieldId ?? item.condition?.fieldId,
    pageId: item.pageId ?? selectedPageId.value, targetModuleId: item.targetModuleId ?? selectedModuleId.value,
    indexMode: item.indexMode ?? 'NONE',
    readPermissionMode: item.readPermissionMode ?? 'INHERIT',
    writePermissionMode: item.writePermissionMode ?? 'INHERIT',
    parentComponentId: item.parentComponentId ?? null, gridRow: item.gridRow ?? 0,
    gridColumn: item.gridColumn ?? 0, gridSpan: item.gridSpan ?? 12, priority: item.priority ?? 100,
  })
  if (kind === 'field') {
    readFieldProperties(form, item.properties)
    if (p4C4DerivedFieldTypes.has(form.type)) {
      form.required = false
      form.readonly = true
      form.searchable = false
    } else if (form.type === 'AI_FILL') {
      form.readonly = true
    }
  }
  if (kind === 'page') {
    Object.assign(form, item.layout)
    const scenarioConfig = draftsFromPageLayout(item.layout)
    form.filterScenarios = scenarioConfig.drafts
    form.defaultFilterScenarioCode = scenarioConfig.defaultCode
  }
  if (kind === 'component') Object.assign(form, item.properties)
  if (kind === 'action') Object.assign(form, item.properties)
  if (kind === 'rule') {
    form.ruleCondition = readCondition(item.condition, fields.value[0]?.id ?? '')
    form.effectValue = item.effects?.[0]?.value ?? true
    const targetId = item.effects?.[0]?.targetId ?? ''
    if (item.type === 'ACTION_ENABLED') form.actionId = targetId
    else if (!['DELETE_ALLOWED', 'APPROVAL_REQUIRED'].includes(item.type)) form.fieldId = targetId
  }
}

function openVersionDiff() {
  if (versions.value.length < 2) return
  diffFromId.value = versions.value[1]?.id ?? ''
  diffToId.value = versions.value[0]?.id ?? ''
  diffResult.value = null
  diffOpen.value = true
  runVersionDiff()
}

async function runVersionDiff() {
  if (!diffFromId.value || !diffToId.value || diffFromId.value === diffToId.value) return
  diffLoading.value = true
  try { diffResult.value = await configApi.diff(systemId.value, diffFromId.value, diffToId.value) }
  catch (error) { report(error) } finally { diffLoading.value = false }
}

async function openDictionaries() {
  dictionaryOpen.value = true
  if (!selectedDictionaryId.value || !dictionaries.value.some((item) => item.id === selectedDictionaryId.value)) {
    selectedDictionaryId.value = dictionaries.value[0]?.id ?? ''
  }
  await loadDictionaryItems()
}

async function loadDictionaryItems() {
  if (!selectedDictionaryId.value) { dictionaryItems.value = []; return }
  try { dictionaryItems.value = await configApi.dictionaryItems(systemId.value, selectedDictionaryId.value) }
  catch (error) { report(error) }
}

watch(selectedDictionaryId, loadDictionaryItems)
watch(() => form.dictionaryId, async (dictionaryId) => {
  if (!dictionaryId || !dictionaryFieldTypes.has(form.type)) { defaultDictionaryItems.value = []; return }
  try { defaultDictionaryItems.value = await configApi.dictionaryItems(systemId.value, dictionaryId) }
  catch (error) { report(error) }
})
watch(() => form.type, (type) => {
  if (!defaultModesForField(type).includes(form.defaultMode)) form.defaultMode = 'NONE'
  if (type === 'AI_FILL') {
    form.readonly = true
    if (!aiFillResultSchemas.includes(form.resultSchema)) form.resultSchema = 'STRING'
    form.modelPolicy = 'SYSTEM_DEFAULT'
    if (!Number.isFinite(Number(form.minConfidence))) form.minConfidence = 0.8
    if (!aiFillOverwriteModes.includes(form.overwriteMode)) form.overwriteMode = 'CONFIRM'
  }
})
watch([() => form.type, () => form.readonly], () => {
  if (!writePermissionSupported.value) form.writePermissionMode = 'INHERIT'
})
watch(derivedResultSchema, (schemaName) => {
  if (schemaName === 'BOOLEAN' && form.indexMode === 'SORT') form.indexMode = form.filterable ? 'FILTER' : 'NONE'
})

function defined(source: Record<string, any>) {
  return Object.fromEntries(Object.entries(source).filter(([, value]) => value !== undefined && value !== null && value !== ''))
}

function addFilterScenario() {
  if (form.filterScenarios.length < MAX_SHARED_FILTER_SCENARIOS) form.filterScenarios.push(emptySharedFilterScenarioDraft())
}

function removeFilterScenario(index: number) {
  const removed = form.filterScenarios[index]?.code.trim()
  form.filterScenarios.splice(index, 1)
  if (removed && form.defaultFilterScenarioCode === removed) form.defaultFilterScenarioCode = null
}

function pageLayout() {
  const retained = editTarget.value?.kind === 'page' && editTarget.value.item.layout
    ? { ...editTarget.value.item.layout } : {}
  const layout = defined({ ...retained, columns: form.columns, gap: form.gap, labelPosition: form.labelPosition, density: form.density,
    stickyActions: form.stickyActions, pageSize: form.pageSize, showSearch: form.showSearch, showFilters: form.showFilters })
  delete layout.filterScenarios
  delete layout.defaultFilterScenarioCode
  if (form.type === 'LIST') {
    const scenarioConfig = buildScenarioConfig(form.filterScenarios, form.defaultFilterScenarioCode)
    layout.filterScenarios = scenarioConfig.scenarios
    if (scenarioConfig.defaultCode) layout.defaultFilterScenarioCode = scenarioConfig.defaultCode
  }
  return layout
}

function componentProperties() {
  const common = { label: form.name }
  if (form.type === 'SECTION') return defined({ ...common, title: form.name, collapsible: form.collapsible, collapsed: form.collapsed, columns: form.columns, gap: form.gap })
  if (form.type === 'TABS' || form.type === 'TAB') return defined({ ...common, title: form.name })
  if (form.type === 'TEXT') return defined({ ...common, content: form.content })
  if (form.type === 'ACTION') return defined({ ...common, actionId: form.actionId })
  return common
}

function actionProperties() {
  return defined({ style: form.style, iconKey: form.iconKey, openMode: form.openMode,
    successMessage: form.successMessage, targetPageId: form.targetPageId, selectionMode: form.selectionMode,
    danger: form.danger, disabledReason: form.disabledReason })
}

async function submit() {
  if (!root.value || !modal.value) return
  if (modal.value === 'field' && p4C4DerivedFieldTypes.has(form.type)) {
    form.resultSchema = derivedResultSchema.value
    if (!derivedContractValid.value) {
      errorMessage.value = derivedCycle.value.length
        ? `当前依赖会形成循环：${derivedCycleLabels.value.join(' → ')}`
        : '请补全类型兼容的计算来源；结果类型必须能够由结构化依赖推导。'
      return
    }
  }
  if (modal.value === 'field' && form.type === 'AI_FILL') {
    const available = new Set(aiFillSourceFields.value.map((field) => field.id))
    if (!Array.isArray(form.sourceFieldIds)
      || form.sourceFieldIds.some((fieldId: string) => !available.has(fieldId))) {
      errorMessage.value = 'AI_FILL 来源必须是当前模块中可读取的非敏感标量字段。'
      return
    }
  }
  mutating.value = true; errorMessage.value = ''
  try {
    const revision = root.value.draftRevision
    const editing = editTarget.value?.item
    let createdPageId: string | undefined
    if (modal.value === 'group') {
      const body = { code: form.code, name: form.name, description: form.description, iconKey: form.iconKey, sortOrder: form.sortOrder, status: form.status, draftRevision: revision }
      if (editing) await configApi.updateGroup(systemId.value, editing.id, { ...body, version: editing.version })
      else await configApi.createGroup(systemId.value, body)
    }
    if (modal.value === 'module') {
      const body = { groupId: form.groupId, code: form.code, name: form.name, description: form.description, iconKey: form.iconKey, sortOrder: form.sortOrder, status: form.status, allowComments: editing?.allowComments ?? true, allowTeam: editing?.allowTeam ?? true, draftRevision: revision }
      if (editing) await configApi.updateModule(systemId.value, editing.id, { ...body, version: editing.version })
      else await configApi.createModule(systemId.value, body)
    }
    if (modal.value === 'copy' && selectedModule.value) await configApi.copyModule(systemId.value, selectedModule.value.id, { groupId: form.groupId, code: form.code, name: form.name, sourceVersion: selectedModule.value.version, draftRevision: revision })
    if (modal.value === 'dictionary') {
      const body = { code: form.code, name: form.name, type: form.type, category: form.category, description: form.description, status: form.status, draftRevision: revision }
      if (editing) await configApi.updateDictionary(systemId.value, editing.id, { ...body, version: editing.version })
      else await configApi.createDictionary(systemId.value, body)
    }
    if (modal.value === 'dictionaryItem' && selectedDictionaryId.value) {
      const body = { parentId: form.parentId || null, code: form.code, label: form.name, semanticKey: form.semanticKey, color: form.color, iconKey: form.iconKey, sortOrder: form.sortOrder, isDefault: form.isDefault, status: form.status, draftRevision: revision }
      if (editing) await configApi.updateDictionaryItem(systemId.value, selectedDictionaryId.value, editing.id, { ...body, version: editing.version })
      else await configApi.createDictionaryItem(systemId.value, selectedDictionaryId.value, body)
    }
    if (modal.value === 'field' && selectedModule.value) {
      const body = { dictionaryId: dictionaryFieldTypes.has(form.type) ? form.dictionaryId : null, targetModuleId: targetModuleFieldTypes.has(form.type) ? form.targetModuleId : null, code: form.code, name: form.name, type: form.type, sortOrder: form.sortOrder, required: p4C4DerivedFieldTypes.has(form.type) ? false : form.required, hidden: form.hidden, readonly: form.type === 'REFERENCE' || p4C4DerivedFieldTypes.has(form.type) || form.type === 'AI_FILL' ? true : form.readonly, searchable: p4C4DerivedFieldTypes.has(form.type) ? false : form.searchable, filterable: form.filterable, showInList: form.showInList, showInDetail: form.showInDetail, indexMode: form.indexMode ?? editing?.indexMode ?? 'NONE', status: form.status, readPermissionMode: form.readPermissionMode ?? 'INHERIT', writePermissionMode: writePermissionSupported.value ? form.writePermissionMode ?? 'INHERIT' : 'INHERIT', properties: buildFieldProperties(form, relationFieldType), draftRevision: revision }
      if (editing) await configApi.updateField(systemId.value, selectedModule.value.id, editing.id, { ...body, version: editing.version })
      else await configApi.createField(systemId.value, selectedModule.value.id, body)
    }
    if (modal.value === 'page' && selectedModule.value) {
      const isDefault = editing?.isDefault ?? !pages.value.some((page) => page.type === form.type && page.status === 'ENABLED' && page.isDefault)
      const body = { code: form.code, name: form.name, type: form.type, isDefault, status: form.status, layout: pageLayout(), draftRevision: revision }
      if (editing) await configApi.updatePage(systemId.value, selectedModule.value.id, editing.id, { ...body, version: editing.version })
      else createdPageId = (await configApi.createPage(systemId.value, selectedModule.value.id, body)).id
    }
    if (modal.value === 'component' && selectedModule.value && form.pageId) {
      const body = { parentComponentId: form.parentComponentId || null, fieldId: form.type === 'FIELD' ? form.fieldId : null, key: form.code, type: form.type, sortOrder: form.sortOrder, gridRow: form.gridRow, gridColumn: form.gridColumn, gridSpan: form.gridSpan, properties: componentProperties(), draftRevision: revision }
      if (editing) await configApi.updateComponent(systemId.value, selectedModule.value.id, form.pageId, editing.id, { ...body, version: editing.version })
      else await configApi.createComponent(systemId.value, selectedModule.value.id, form.pageId, body)
    }
    if (modal.value === 'action' && selectedModule.value) {
      const body = { code: form.code, name: form.name, type: form.type, placement: form.placement, confirmMessage: form.confirmMessage, sortOrder: form.sortOrder, status: form.status, properties: actionProperties(), draftRevision: revision }
      if (editing) await configApi.updateAction(systemId.value, selectedModule.value.id, editing.id, { ...body, version: editing.version })
      else await configApi.createAction(systemId.value, selectedModule.value.id, body)
    }
    if (modal.value === 'rule' && selectedModule.value && form.ruleCondition) {
      const targetId = effectTargetKind.value === 'action' ? form.actionId : effectTargetKind.value === 'field' ? form.fieldId : null
      const body = { code: form.code, name: form.name, type: form.type, priority: form.priority, condition: buildCondition(form.ruleCondition, fieldType), effects: [{ effect: ruleEffectByType[form.type], targetId, value: form.effectValue }], status: form.status, draftRevision: revision }
      if (editing) await configApi.updateRule(systemId.value, selectedModule.value.id, editing.id, { ...body, version: editing.version })
      else await configApi.createRule(systemId.value, selectedModule.value.id, body)
    }
    if (modal.value === 'publish' && root.value.lastCheck) { await configApi.publish(systemId.value, { checkId: root.value.lastCheck.id, draftRevision: revision, configRootVersion: root.value.version, reason: form.reason }); await session.refreshContext() }
    if (modal.value === 'rollback' && rollbackTarget.value) {
      await configRecoveryApi.moduleConfig(
        systemId.value,
        rollbackTarget.value.id,
        Number(rollbackTarget.value.versionNo),
        { expectedVersion: root.value.version, reason: form.reason },
      )
    }
    modal.value = null; editTarget.value = null; message.success('操作已完成'); await load(); await loadModule(createdPageId); await loadComponents(); await loadDictionaryItems()
  } catch (error) {
    if (error instanceof SharedFilterScenarioValidationError) errorMessage.value = error.message
    else report(error)
  } finally { mutating.value = false }
}

function removeResource(kind: 'group' | 'module' | 'dictionary' | 'dictionaryItem' | 'field' | 'page' | 'component' | 'action' | 'rule', item: any) {
  Modal.confirm({
    title: `删除“${item.name ?? item.label ?? item.key}”`,
    content: '删除前会检查页面、字段、规则和权限引用；存在引用时不会删除。',
    okText: '删除', okType: 'danger', cancelText: '取消',
    async onOk() {
      if (!root.value) return
      const body = { version: item.version, draftRevision: root.value.draftRevision }
      try {
        if (kind === 'group') await configApi.deleteGroup(systemId.value, item.id, body)
        if (kind === 'module') await configApi.deleteModule(systemId.value, item.id, body)
        if (kind === 'dictionary') await configApi.deleteDictionary(systemId.value, item.id, body)
        if (kind === 'dictionaryItem') await configApi.deleteDictionaryItem(systemId.value, selectedDictionaryId.value, item.id, body)
        if (kind === 'field' && selectedModule.value) await configApi.deleteField(systemId.value, selectedModule.value.id, item.id, body)
        if (kind === 'page' && selectedModule.value) await configApi.deletePage(systemId.value, selectedModule.value.id, item.id, body)
        if (kind === 'component' && selectedModule.value) await configApi.deleteComponent(systemId.value, selectedModule.value.id, item.pageId, item.id, body)
        if (kind === 'action' && selectedModule.value) await configApi.deleteAction(systemId.value, selectedModule.value.id, item.id, body)
        if (kind === 'rule' && selectedModule.value) await configApi.deleteRule(systemId.value, selectedModule.value.id, item.id, body)
        message.success('已删除')
        inspector.value = null
        await load(); await loadModule(); await loadComponents(); await loadDictionaryItems()
      } catch (error) { report(error); throw error }
    },
  })
}

async function runCheck() {
  if (!root.value) return
  mutating.value = true
  try { checkReport.value = await configApi.check(systemId.value, root.value.draftRevision); message.success(checkReport.value.status === 'PASSED' ? '配置检查通过' : '配置检查发现阻断项'); await load() }
  catch (error) { report(error) } finally { mutating.value = false }
}

async function openPermissionPreview() {
  previewOpen.value = true
  previewResult.value = null
  previewLoading.value = true
  try {
    previewMembers.value = (await systemAdminApi.listMembers(systemId.value, { page: 1, size: 200, status: 'ACTIVE' })).items
    const member = previewMembers.value[0]
    previewMemberId.value = member?.id ?? ''
    previewTenantId.value = member?.tenantIds[0] ?? ''
    if (previewMemberId.value && previewTenantId.value) await runPermissionPreview()
  } catch (error) { report(error) } finally { previewLoading.value = false }
}

function selectPreviewMember(memberId: string) {
  const member = previewMembers.value.find((item) => item.id === memberId)
  previewTenantId.value = member?.tenantIds[0] ?? ''
  previewResult.value = null
}

async function runPermissionPreview() {
  if (!previewMemberId.value || !previewTenantId.value) return
  previewLoading.value = true
  try { previewResult.value = await configApi.preview(systemId.value, previewMemberId.value, previewTenantId.value) }
  catch (error) { report(error) } finally { previewLoading.value = false }
}

onMounted(async () => { await load(); await loadModule(); await loadComponents() })
</script>

<template>
  <section class="admin-page config-studio">
    <AdminPageHeader title="模块配置工作台" description="设计模块、字段和页面，检查后发布为系统运行版本。">
      <template #actions>
        <a-button v-if="!isMobile" @click="openPermissionPreview"><ShieldCheck :size="16" />成员预览</a-button>
        <a-button :loading="loading" title="刷新" @click="load"><RefreshCw :size="16" /></a-button>
        <a-button v-if="!isMobile" :loading="mutating" @click="runCheck"><CheckCircle2 :size="16" />检查</a-button>
        <a-button v-if="!isMobile" type="primary" :disabled="!canPublish" @click="open('publish')"><Rocket :size="16" />发布</a-button>
      </template>
    </AdminPageHeader>
    <a-alert v-if="errorMessage" class="admin-alert" type="error" show-icon :message="errorMessage" closable @close="errorMessage = ''" />

    <div class="config-status-strip">
      <span :class="['config-state', root?.status?.toLowerCase()]"><CircleAlert :size="15" />{{ rootStatus }}</span>
      <span>草稿 R{{ root?.draftRevision ?? '0' }}</span><span>运行版本 {{ versions.find((item) => item.active)?.versionNo ? `V${versions.find((item) => item.active)?.versionNo}` : '未发布' }}</span>
      <span v-if="root?.lastCheck">最近检查：{{ root.lastCheck.blockerCount }} 阻断 / {{ root.lastCheck.warningCount }} 提醒</span>
    </div>
    <div
      v-if="focusedAgentResource"
      class="config-agent-focus-notice"
      :data-module-code="focusedAgentResource.moduleCode"
      :data-resource-kind="focusedAgentResource.resourceKind"
      :data-resource-code="focusedAgentResource.resourceCode"
    >已从最新配置定位 {{ focusedAgentResource.resourceKind === 'page' ? '页面' : '字段' }}：{{ focusedAgentResource.resourceCode }}</div>
    <div v-if="stagedPermissionTarget" class="config-staged-role-path" :data-permission-prefix="stagedPermissionTarget.prefix">
      <span><strong>{{ stagedPermissionTarget.field.name }}</strong> 的字段权限已完成 STAGED 发布；先为角色授权，再返回配置切换为 ENFORCED。</span>
      <a-button class="config-open-role-grants" aria-label="前往角色授权" type="primary" @click="openStagedRoleGrants">前往角色授权</a-button>
    </div>

    <template v-if="isMobile">
      <div class="config-mobile-readonly">
        <a-alert type="info" show-icon message="移动端为只读模式，可查看检查结果和版本历史。" />
        <div v-if="root?.lastCheck" class="mobile-check"><strong>检查 {{ root.lastCheck.status }}</strong><span>{{ root.lastCheck.blockerCount }} 个阻断项，{{ root.lastCheck.warningCount }} 个提醒</span></div>
        <div class="version-list"><div v-for="version in versions" :key="version.id" class="version-row"><span><strong>V{{ version.versionNo }}</strong> {{ version.sourceType }}</span><a-tag v-if="version.active" color="green">运行中</a-tag><small>{{ version.reason }}</small></div></div>
      </div>
    </template>

    <div v-else class="config-workbench">
      <aside class="config-resource-pane">
        <div class="pane-title"><span><Blocks :size="17" />模块</span><div><button title="新建模块组" @click="open('group')"><Plus :size="15" /></button><button title="新建模块" :disabled="!groups.length" @click="open('module')"><Blocks :size="15" /></button></div></div>
        <div v-if="!groups.length" class="pane-empty">先创建模块组</div>
        <div v-for="group in moduleGroups" :key="group.id" class="module-group">
          <div class="group-name"><span>{{ group.name }}</span><span class="row-actions"><button title="编辑模块组" @click="openEdit('group', group)"><Pencil :size="13" /></button><button title="删除模块组" @click="removeResource('group', group)"><Trash2 :size="13" /></button></span></div>
          <button v-for="item in group.modules" :key="item.id" :class="['module-item', { active: item.id === selectedModuleId }]" @click="selectModule(item)"><Settings2 :size="15" /><span>{{ item.name }}</span><small>{{ item.code }}</small></button>
        </div>
        <div class="dictionary-shortcut"><button @click="openDictionaries"><BookOpen :size="15" />管理字典</button><span>{{ dictionaries.length }} 个字典</span></div>
      </aside>

      <main class="config-design-pane">
        <div v-if="selectedModule" class="module-heading"><div><h2>{{ selectedModule.name }}</h2><span>{{ selectedModule.code }}</span></div><div class="module-heading-actions"><a-button size="small" title="编辑模块" @click="openEdit('module', selectedModule)"><Pencil :size="14" /></a-button><a-button size="small" title="复制模块" @click="open('copy')"><Copy :size="14" />复制</a-button><a-button size="small" danger title="删除模块" @click="removeResource('module', selectedModule)"><Trash2 :size="14" /></a-button><a-tag :color="selectedModule.status === 'ENABLED' ? 'green' : 'default'">{{ selectedModule.status }}</a-tag></div></div>
        <a-empty v-else description="选择或创建模块" />
        <a-tabs v-if="selectedModule" v-model:activeKey="activeTab" class="config-tabs">
          <a-tab-pane key="fields" tab="字段">
            <div class="tab-command"><span>{{ fields.length }} 个字段</span><a-button class="config-field-create" size="small" type="primary" @click="open('field')"><Plus :size="14" />字段</a-button></div>
            <div class="resource-table"><div v-for="field in fields" :key="field.id" :class="['resource-row', { selected: inspector?.kind === 'field' && inspector.item.id === field.id }]" data-resource-kind="field" :data-resource-code="field.code" @click="inspect('field', field)"><span><strong>{{ field.name }}</strong><small>{{ field.code }}</small></span><a-tag>{{ field.type }}</a-tag><span>{{ field.required ? '必填' : '可选' }}</span><span>{{ field.showInList ? '列表显示' : '仅详情' }}</span><span class="row-actions"><button title="编辑字段" @click.stop="openEdit('field', field)"><Pencil :size="14" /></button><button title="删除字段" @click.stop="removeResource('field', field)"><Trash2 :size="14" /></button></span></div><a-empty v-if="!fields.length" description="尚未配置字段" /></div>
          </a-tab-pane>
          <a-tab-pane key="pages" tab="页面设计">
            <div class="page-toolbar"><a-select v-model:value="selectedPageId" class="config-page-select" data-resource-kind="page" :data-resource-code="selectedPage?.code" style="width: 220px" @change="selectedPage && inspect('page', selectedPage)"><a-select-option v-for="page in pages" :key="page.id" :value="page.id">{{ page.name }} · {{ page.type }}</a-select-option></a-select><span class="row-actions"><a-button class="config-page-create" size="small" type="primary" @click="open('page')"><Plus :size="14" />新建页面</a-button><a-button size="small" :disabled="!selectedPage" title="编辑页面" @click="selectedPage && openEdit('page', selectedPage)"><Pencil :size="14" /></a-button><a-button size="small" danger :disabled="!selectedPage" title="删除页面" @click="selectedPage && removeResource('page', selectedPage)"><Trash2 :size="14" /></a-button><a-button size="small" type="primary" :disabled="!selectedPage" @click="open('component')"><Plus :size="14" />组件</a-button></span></div>
            <div class="page-canvas" data-resource-kind="page" :data-resource-code="selectedPage?.code"><div class="canvas-title"><LayoutTemplate :size="16" />{{ selectedPage?.name }}</div><div v-if="components.length" class="component-grid"><div v-for="component in components" :key="component.id" :class="['component-block', { selected: inspector?.kind === 'component' && inspector.item.id === component.id }]" :style="{ gridColumn: `span ${Math.min(component.gridSpan, 12)}` }" @click="inspect('component', component)"><span class="component-actions"><button title="编辑组件" @click.stop="openEdit('component', component)"><Pencil :size="13" /></button><button title="删除组件" @click.stop="removeResource('component', component)"><Trash2 :size="13" /></button></span><strong>{{ component.properties.label || component.key }}</strong><small>{{ component.type }} · {{ component.key }}</small></div></div><a-empty v-else description="页面尚未放置组件" /></div>
          </a-tab-pane>
          <a-tab-pane key="actions" tab="动作"><div class="tab-command"><span>{{ actions.length }} 个动作</span><a-button size="small" type="primary" @click="open('action')"><Plus :size="14" />动作</a-button></div><div class="resource-table"><div v-for="action in actions" :key="action.id" :class="['resource-row', { selected: inspector?.kind === 'action' && inspector.item.id === action.id }]" @click="inspect('action', action)"><span><strong>{{ action.name }}</strong><small>{{ action.code }}</small></span><a-tag>{{ action.type }}</a-tag><span>{{ action.placement }}</span><small>{{ action.permissionCode }}</small><span class="row-actions"><button title="编辑动作" @click.stop="openEdit('action', action)"><Pencil :size="14" /></button><button title="删除动作" @click.stop="removeResource('action', action)"><Trash2 :size="14" /></button></span></div><a-empty v-if="!actions.length" description="尚未配置动作" /></div></a-tab-pane>
          <a-tab-pane key="rules" tab="规则"><div class="tab-command"><span>{{ rules.length }} 条规则</span><a-button size="small" type="primary" :disabled="!fields.length" @click="open('rule')"><Plus :size="14" />规则</a-button></div><div class="resource-table"><div v-for="rule in rules" :key="rule.id" :class="['resource-row', { selected: inspector?.kind === 'rule' && inspector.item.id === rule.id }]" @click="inspect('rule', rule)"><span><strong>{{ rule.name }}</strong><small>{{ rule.code }}</small></span><a-tag>{{ rule.type }}</a-tag><span>优先级 {{ rule.priority }}</span><span></span><span class="row-actions"><button title="编辑规则" @click.stop="openEdit('rule', rule)"><Pencil :size="14" /></button><button title="删除规则" @click.stop="removeResource('rule', rule)"><Trash2 :size="14" /></button></span></div><a-empty v-if="!rules.length" description="尚未配置规则" /></div></a-tab-pane>
          <a-tab-pane key="print-templates" tab="打印模板"><PrintTemplateManager :system-id="systemId" :module-id="selectedModule.id" :fields="fields" /></a-tab-pane>
          <a-tab-pane v-if="session.hasPermission('event.template.manage')" key="message-templates" tab="消息模板"><MessageTemplateManager :system-id="systemId" /></a-tab-pane>
        </a-tabs>
      </main>

      <aside class="config-inspector-pane">
        <div class="pane-title"><span><Settings2 :size="17" />属性</span></div>
        <div v-if="inspector" class="inspector-properties">
          <header><strong>{{ inspector.item.name ?? inspector.item.key }}</strong><span class="row-actions"><button title="编辑所选资源" @click="editInspected"><Pencil :size="14" /></button><button title="删除所选资源" @click="deleteInspected"><Trash2 :size="14" /></button></span></header>
          <dl><template v-for="detail in inspectorDetails" :key="detail[0]"><dt>{{ detail[0] }}</dt><dd>{{ detail[1] }}</dd></template></dl>
        </div>
        <div v-else class="pane-empty">选择资源查看属性</div>
        <div v-if="checkReport?.issues.length" class="check-issues"><div v-for="issue in checkReport.issues" :key="issue.id" :class="['check-issue', issue.severity.toLowerCase()]"><strong>{{ issue.message }}</strong><small>{{ issue.suggestedAction }}</small></div></div>
        <div class="pane-title history-title"><span><History :size="17" />发布历史</span><button title="比较版本" :disabled="versions.length < 2" @click="openVersionDiff"><GitCompareArrows :size="15" /></button></div>
        <div class="version-list"><div v-for="version in versions" :key="version.id" class="version-row"><span><strong>V{{ version.versionNo }}</strong> {{ version.sourceType }}</span><a-tag v-if="version.active" color="green">运行中</a-tag><small>{{ version.reason }}</small><button v-if="!version.active && root?.status === 'CLEAN'" title="恢复为新草稿（不会切换运行版本）" @click="open('rollback', version)"><RotateCcw :size="14" /></button></div><div v-if="!versions.length" class="pane-empty">暂无发布版本</div></div>
      </aside>
    </div>

    <a-drawer v-model:open="dictionaryOpen" title="数据字典" :width="820" :root-style="{ zIndex: 1000 }">
      <template #extra><a-button type="primary" @click="open('dictionary')"><Plus :size="14" />字典</a-button></template>
      <div class="dictionary-manager">
        <aside>
          <a-empty v-if="!dictionaries.length" description="尚未创建字典" />
          <div v-for="dictionary in dictionaries" :key="dictionary.id" :class="['dictionary-row', { active: dictionary.id === selectedDictionaryId }]">
            <button @click="selectedDictionaryId = dictionary.id"><strong>{{ dictionary.name }}</strong><small>{{ dictionary.code }} · {{ dictionary.type }}</small></button>
            <span class="row-actions"><button title="编辑字典" @click="openEdit('dictionary', dictionary)"><Pencil :size="13" /></button><button title="删除字典" @click="removeResource('dictionary', dictionary)"><Trash2 :size="13" /></button></span>
          </div>
        </aside>
        <main>
          <div class="tab-command"><span>{{ dictionaryItems.length }} 个字典项</span><a-button size="small" type="primary" :disabled="!selectedDictionaryId" @click="open('dictionaryItem')"><Plus :size="14" />字典项</a-button></div>
          <a-empty v-if="selectedDictionaryId && !dictionaryItems.length" description="尚未创建字典项" />
          <div v-for="item in dictionaryItems" :key="item.id" class="dictionary-item" :style="{ paddingLeft: `${12 + item.depth * 18}px` }">
            <span v-if="item.color" class="color-swatch" :style="{ background: item.color }"></span>
            <span><strong>{{ item.label }}</strong><small>{{ item.code }}{{ item.isDefault ? ' · 默认' : '' }}</small></span>
            <a-tag :color="item.status === 'ENABLED' ? 'green' : 'default'">{{ item.status }}</a-tag>
            <span class="row-actions"><button title="编辑字典项" @click="openEdit('dictionaryItem', item)"><Pencil :size="14" /></button><button title="删除字典项" @click="removeResource('dictionaryItem', item)"><Trash2 :size="14" /></button></span>
          </div>
        </main>
      </div>
    </a-drawer>

    <a-drawer v-model:open="previewOpen" title="成员权限预览" :width="720" :root-style="{ zIndex: 1000 }">
      <div class="preview-controls">
        <a-select v-model:value="previewMemberId" aria-label="预览成员" placeholder="选择成员" @change="selectPreviewMember">
          <a-select-option v-for="member in previewMembers" :key="member.id" :value="member.id">{{ member.displayName }} · {{ member.username || member.id }}</a-select-option>
        </a-select>
        <a-select v-model:value="previewTenantId" aria-label="预览租户" placeholder="选择租户">
          <a-select-option v-for="tenantId in previewMembers.find((item) => item.id === previewMemberId)?.tenantIds || []" :key="tenantId" :value="tenantId">{{ tenantId }}</a-select-option>
        </a-select>
        <a-button type="primary" :loading="previewLoading" :disabled="!previewMemberId || !previewTenantId" @click="runPermissionPreview">重新预览</a-button>
      </div>
      <a-alert v-if="previewResult" type="info" show-icon :message="`权限版本 ${previewResult.permissionVersion}`" :description="previewResult.sourceRoles.map((role) => role.name).join('、') || '未授予角色'" />
      <div v-if="previewResult" class="preview-compare">
        <section v-for="tree in [{ key: 'active', title: '当前运行版本', value: previewResult.active }, { key: 'draft', title: '待发布草稿', value: previewResult.draft }]" :key="tree.key">
          <header><strong>{{ tree.title }}</strong><span>{{ tree.value.versionId ? `V ${tree.value.versionId}` : tree.value.revision ? `R ${tree.value.revision}` : '未发布' }}</span></header>
          <a-empty v-if="!tree.value.groups.length" description="该成员无可见模块" />
          <div v-for="group in tree.value.groups" :key="group.id" class="preview-group">
            <strong>{{ group.name }}</strong>
            <div v-for="item in group.modules" :key="item.id" class="preview-module">
              <span>{{ item.name }} <small>{{ item.code }}</small></span>
              <small>{{ item.fields.length }} 字段 · {{ item.actions.length }} 动作</small>
            </div>
          </div>
        </section>
      </div>
    </a-drawer>

    <a-drawer v-model:open="diffOpen" title="版本差异" :width="680" :root-style="{ zIndex: 1000 }">
      <div class="diff-controls">
        <a-select v-model:value="diffFromId" aria-label="起始版本"><a-select-option v-for="version in versions" :key="version.id" :value="version.id">V{{ version.versionNo }} · {{ version.reason }}</a-select-option></a-select>
        <a-select v-model:value="diffToId" aria-label="目标版本"><a-select-option v-for="version in versions" :key="version.id" :value="version.id">V{{ version.versionNo }} · {{ version.reason }}</a-select-option></a-select>
        <a-button type="primary" :loading="diffLoading" :disabled="!diffFromId || !diffToId || diffFromId === diffToId" @click="runVersionDiff">比较</a-button>
      </div>
      <a-alert v-if="diffFromId === diffToId" type="warning" show-icon message="请选择两个不同版本" />
      <div v-if="diffResult" class="diff-sections">
        <section v-for="(change, section) in diffResult.changes" :key="section">
          <header><strong>{{ section }}</strong><span>新增 {{ change.added.length }} · 删除 {{ change.removed.length }} · 修改 {{ change.changed.length }}</span></header>
          <div v-if="change.added.length || change.removed.length || change.changed.length" class="diff-values"><span v-for="id in change.added" :key="`a-${id}`">+ {{ id }}</span><span v-for="id in change.removed" :key="`r-${id}`">- {{ id }}</span><span v-for="id in change.changed" :key="`c-${id}`">~ {{ id }}</span></div>
        </section>
      </div>
    </a-drawer>

    <a-modal :open="modal !== null" :z-index="1200" :width="modal === 'field' || modal === 'rule' ? 760 : 560" :body-style="{ maxHeight: '70vh', overflowY: 'auto' }" :title="modal === 'publish' ? '发布配置' : modal === 'rollback' ? '恢复历史版本为草稿' : modal === 'copy' ? '复制模块' : `${editTarget ? '编辑' : '新建'}${({group:'模块组',module:'模块',dictionary:'字典',dictionaryItem:'字典项',field:'字段',page:'页面',component:'页面组件',action:'动作',rule:'规则'} as any)[modal || ''] || ''}`" :confirm-loading="mutating" ok-text="确认" cancel-text="取消" @ok="submit" @cancel="modal = null">
      <a-form layout="vertical">
        <template v-if="modal === 'publish' || modal === 'rollback'"><a-alert v-if="modal === 'rollback'" type="info" show-icon message="仅恢复为新草稿" description="当前运行版本和历史版本保持不变；恢复后必须重新检查并显式发布。" /><a-form-item label="变更原因" required><a-input v-model:value="form.reason" aria-label="变更原因" :maxlength="500" /></a-form-item></template>
        <template v-else>
          <a-form-item v-if="modal === 'module' || modal === 'copy'" label="模块组" required><a-select v-model:value="form.groupId"><a-select-option v-for="group in groups" :key="group.id" :value="group.id">{{ group.name }}</a-select-option></a-select></a-form-item>
          <a-form-item v-if="modal === 'component'" label="页面" required><a-select v-model:value="form.pageId"><a-select-option v-for="page in pages" :key="page.id" :value="page.id">{{ page.name }}</a-select-option></a-select></a-form-item>
          <a-form-item v-if="modal === 'dictionary'" label="类型"><a-select v-model:value="form.type"><a-select-option v-for="value in ['LIST','TREE','CASCADE','STATUS','TAG','FIELD_OPTION']" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item>
          <a-form-item v-if="modal === 'field'" label="类型"><a-select :value="form.type" aria-label="字段类型" show-search option-filter-prop="value" @change="fieldTypeChanged"><a-select-opt-group v-for="group in fieldTypeGroups" :key="group.label" :label="group.label"><a-select-option v-for="value in group.values" :key="value" :value="value">{{ value }}</a-select-option></a-select-opt-group></a-select></a-form-item>
          <a-form-item v-if="modal === 'page'" label="类型"><a-select v-model:value="form.type" class="config-page-type" aria-label="页面类型" :disabled="Boolean(editTarget)"><a-select-option v-for="value in ['LIST','FORM','DETAIL']" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item>
          <a-form-item v-if="modal === 'component'" label="类型"><a-select v-model:value="form.type"><a-select-option v-for="value in ['FIELD','SECTION','TABS','TAB','ACTION','TEXT','DIVIDER']" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item>
          <a-form-item v-if="modal === 'action'" label="类型"><a-select v-model:value="form.type"><a-select-option v-for="value in ['CREATE','UPDATE','DELETE','CUSTOM','APPROVAL']" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item>
          <a-form-item v-if="modal === 'rule'" label="类型"><a-select v-model:value="form.type"><a-select-option v-for="value in ruleTypes" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item>
          <a-form-item v-if="modal === 'action'" label="位置"><a-select v-model:value="form.placement"><a-select-option v-for="value in ['TOOLBAR','ROW','DETAIL','BATCH']" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item>
          <a-form-item v-if="modal === 'component' && form.type === 'FIELD'" label="字段" required><a-select v-model:value="form.fieldId"><a-select-option v-for="field in fields" :key="field.id" :value="field.id">{{ field.name }}</a-select-option></a-select></a-form-item>
          <a-form-item v-if="modal === 'component' && form.type === 'ACTION'" label="动作" required><a-select v-model:value="form.actionId"><a-select-option v-for="action in actions" :key="action.id" :value="action.id">{{ action.name }}</a-select-option></a-select></a-form-item>
          <a-form-item v-if="modal === 'component'" label="父组件"><a-select v-model:value="form.parentComponentId" allow-clear><a-select-option v-for="component in components.filter((candidate) => candidate.id !== editTarget?.item.id)" :key="component.id" :value="component.id">{{ component.properties.label || component.key }}</a-select-option></a-select></a-form-item>
          <a-form-item v-if="modal === 'field' && dictionaryFieldTypes.has(form.type)" label="字典" required><a-select v-model:value="form.dictionaryId"><a-select-option v-for="dictionary in dictionaries" :key="dictionary.id" :value="dictionary.id">{{ dictionary.name }}</a-select-option></a-select></a-form-item>
          <a-form-item v-if="modal === 'field' && targetModuleFieldTypes.has(form.type)" label="目标模块" required><a-select v-model:value="form.targetModuleId" aria-label="目标模块"><a-select-option v-for="item in modules" :key="item.id" :value="item.id">{{ item.name }}</a-select-option></a-select></a-form-item>
          <a-form-item label="名称" required><a-input v-model:value="form.name" aria-label="名称" :maxlength="128" /></a-form-item>
          <a-form-item label="编码" required><a-input v-model:value="form.code" aria-label="编码" placeholder="小写字母、数字和下划线" :maxlength="64" /></a-form-item>
          <a-form-item v-if="['group','module','dictionary'].includes(modal || '')" label="说明"><a-input v-model:value="form.description" aria-label="说明" :maxlength="500" /></a-form-item>
          <a-form-item v-if="modal === 'dictionary'" label="分类"><a-input v-model:value="form.category" aria-label="字典分类" :maxlength="64" /></a-form-item>
          <a-form-item v-if="modal === 'dictionaryItem'" label="父级"><a-select v-model:value="form.parentId" allow-clear><a-select-option v-for="item in dictionaryItems.filter((candidate) => candidate.id !== editTarget?.item.id)" :key="item.id" :value="item.id">{{ item.label }}</a-select-option></a-select></a-form-item>
          <div v-if="modal === 'dictionaryItem'" class="dictionary-item-fields"><a-form-item label="语义"><a-input v-model:value="form.semanticKey" :maxlength="32" /></a-form-item><a-form-item label="颜色"><a-input v-model:value="form.color" type="color" /></a-form-item></div>
          <template v-if="modal === 'page'">
            <a-divider>布局</a-divider>
            <div class="modal-grid three"><a-form-item label="列数"><a-input-number v-model:value="form.columns" :min="1" :max="24" /></a-form-item><a-form-item label="间距"><a-input-number v-model:value="form.gap" :min="0" :max="64" /></a-form-item><a-form-item label="每页数量"><a-input-number v-model:value="form.pageSize" :min="1" :max="500" /></a-form-item></div>
            <div class="modal-grid"><a-form-item label="标签位置"><a-select v-model:value="form.labelPosition"><a-select-option value="TOP">顶部</a-select-option><a-select-option value="LEFT">左侧</a-select-option></a-select></a-form-item><a-form-item label="密度"><a-select v-model:value="form.density"><a-select-option value="DEFAULT">标准</a-select-option><a-select-option value="COMPACT">紧凑</a-select-option></a-select></a-form-item></div>
            <div class="modal-checks"><a-checkbox v-model:checked="form.stickyActions">固定动作栏</a-checkbox><a-checkbox v-model:checked="form.showSearch">显示搜索</a-checkbox><a-checkbox v-model:checked="form.showFilters">显示筛选</a-checkbox></div>
            <template v-if="form.type === 'LIST'">
              <a-divider>共享筛选方案</a-divider>
              <div class="filter-scenario-heading"><span>最多 {{ MAX_SHARED_FILTER_SCENARIOS }} 项；筛选和排序使用 canonical JSON。</span><a-button class="config-filter-scenario-add" :disabled="form.filterScenarios.length >= MAX_SHARED_FILTER_SCENARIOS" @click="addFilterScenario"><Plus :size="14" />添加方案</a-button></div>
              <div v-for="(scenario, index) in form.filterScenarios" :key="index" class="config-filter-scenario-row">
                <div class="modal-grid"><a-form-item label="编码" required><a-input v-model:value="scenario.code" class="config-filter-scenario-code" aria-label="共享筛选方案编码" :maxlength="64" /></a-form-item><a-form-item label="名称" required><a-input v-model:value="scenario.name" class="config-filter-scenario-name" aria-label="共享筛选方案名称" :maxlength="100" /></a-form-item></div>
                <a-form-item label="筛选 JSON" required><a-textarea v-model:value="scenario.filterJson" class="config-filter-scenario-filter" aria-label="共享筛选方案筛选 JSON" :rows="5" /></a-form-item>
                <a-form-item label="排序 JSON" required><a-textarea v-model:value="scenario.sortJson" class="config-filter-scenario-sort" aria-label="共享筛选方案排序 JSON" :rows="4" /></a-form-item>
                <a-button class="config-filter-scenario-remove" danger @click="removeFilterScenario(Number(index))"><Trash2 :size="14" />删除方案</a-button>
              </div>
              <a-form-item label="默认共享筛选方案"><a-select v-model:value="form.defaultFilterScenarioCode" class="config-filter-scenario-default" aria-label="默认共享筛选方案" allow-clear><a-select-option v-for="(scenario, index) in form.filterScenarios" :key="`${index}-${scenario.code}`" :value="scenario.code" :disabled="!scenario.code.trim()">{{ scenario.name || scenario.code || `方案 ${Number(index) + 1}` }}</a-select-option></a-select></a-form-item>
            </template>
          </template>
          <template v-if="modal === 'component'">
            <div class="modal-grid three"><a-form-item label="行"><a-input-number v-model:value="form.gridRow" :min="0" :max="999" /></a-form-item><a-form-item label="列"><a-input-number v-model:value="form.gridColumn" :min="0" :max="23" /></a-form-item><a-form-item label="跨度"><a-input-number v-model:value="form.gridSpan" :min="1" :max="24" /></a-form-item></div>
            <a-form-item v-if="form.type === 'TEXT'" label="文本内容"><a-textarea v-model:value="form.content" :maxlength="10000" :rows="3" /></a-form-item>
            <div v-if="form.type === 'SECTION'" class="modal-checks"><a-checkbox v-model:checked="form.collapsible">可折叠</a-checkbox><a-checkbox v-model:checked="form.collapsed">默认折叠</a-checkbox></div>
          </template>
          <template v-if="modal === 'field'">
            <a-divider>字段权限声明</a-divider>
            <a-alert class="field-permission-lifecycle-notice" type="info" show-icon message="安全顺序：STAGED → 角色授权 → ENFORCED" description="STAGED 只注册可授权权限，不限制运行时；完成角色草稿保存、检查和发布后，再改为 ENFORCED 并重新检查、发布配置。INHERIT 保持现有模块级规则。" />
            <div class="field-permission-controls">
              <a-form-item label="读取权限模式">
                <a-select v-model:value="form.readPermissionMode" class="field-read-permission-mode" aria-label="字段读取权限模式">
                  <a-select-option v-for="mode in fieldPermissionModes" :key="mode" :value="mode">{{ permissionModeLabels[mode] }}</a-select-option>
                </a-select>
              </a-form-item>
              <code class="field-permission-code-preview field-read-permission-code" data-direction="read">{{ readPermissionCode }}</code>
              <a-form-item label="写入权限模式">
                <a-select v-model:value="form.writePermissionMode" class="field-write-permission-mode" aria-label="字段写入权限模式" :disabled="!writePermissionSupported">
                  <a-select-option v-for="mode in fieldPermissionModes" :key="mode" :value="mode">{{ permissionModeLabels[mode] }}</a-select-option>
                </a-select>
              </a-form-item>
              <code class="field-permission-code-preview field-write-permission-code" data-direction="write">{{ writePermissionCode }}</code>
              <small v-if="!writePermissionSupported" class="field-write-permission-disabled">只读、引用、派生、系统计算和 AI_FILL 字段只能使用 INHERIT 写权限模式。</small>
            </div>
            <a-divider>通用属性</a-divider>
            <div class="modal-grid"><a-form-item v-if="!p4C4DerivedFieldTypes.has(form.type) && form.type !== 'AI_FILL'" label="占位提示"><a-input v-model:value="form.placeholder" /></a-form-item><a-form-item label="帮助文本"><a-input v-model:value="form.helpText" /></a-form-item></div>
            <div v-if="!p4C3CompositionTypes.has(form.type) && !p4C4DerivedFieldTypes.has(form.type) && form.type !== 'AI_FILL'" class="modal-grid"><a-form-item label="默认值来源"><a-select v-model:value="form.defaultMode" aria-label="默认值来源"><a-select-option v-for="mode in defaultModeOptions" :key="mode" :value="mode">{{ defaultModeLabels[mode] }}</a-select-option></a-select></a-form-item><a-form-item label="校验提示"><a-input v-model:value="form.validationMessage" /></a-form-item></div>
            <div v-if="form.defaultMode === 'FIXED' && form.type === 'MONEY'" class="modal-grid"><a-form-item label="默认金额" required><a-input v-model:value="form.defaultMoneyAmount" inputmode="decimal" aria-label="默认金额" /></a-form-item><a-form-item label="默认币种" required><a-select v-model:value="form.defaultMoneyCurrency" aria-label="默认币种"><a-select-option v-for="value in form.currencies" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item></div>
            <a-form-item v-else-if="form.defaultMode === 'FIXED' && numericFieldTypes.has(form.type)" label="固定默认值" required><a-input-number v-model:value="form.defaultValue" aria-label="固定默认值" style="width:100%" /></a-form-item>
            <a-form-item v-else-if="form.defaultMode === 'FIXED' && form.type === 'SWITCH'" label="固定默认值" required><a-switch v-model:checked="form.defaultValue" aria-label="固定默认值" /></a-form-item>
            <div v-else-if="form.defaultMode === 'FIXED' && ['DATE_RANGE','TIME_RANGE'].includes(form.type)" class="modal-grid"><a-form-item label="起始默认值" required><a-input v-model:value="form.defaultValueRange[0]" aria-label="起始默认值" :type="form.type === 'DATE_RANGE' ? 'date' : 'time'" /></a-form-item><a-form-item label="结束默认值" required><a-input v-model:value="form.defaultValueRange[1]" aria-label="结束默认值" :type="form.type === 'DATE_RANGE' ? 'date' : 'time'" /></a-form-item></div>
            <a-form-item v-else-if="form.defaultMode === 'FIXED' && dateFieldTypes.has(form.type)" label="固定默认值" required><a-input v-model:value="form.defaultValue" aria-label="固定默认值" :type="form.type === 'DATE' ? 'date' : form.type === 'DATETIME' ? 'datetime-local' : 'time'" /></a-form-item>
            <a-form-item v-else-if="form.defaultMode === 'FIXED' && dictionaryFieldTypes.has(form.type)" label="固定默认值" required><a-select v-model:value="form.defaultValue" aria-label="固定默认值" :mode="form.type === 'MULTI_SELECT' || form.multiple ? 'multiple' : undefined" show-search><a-select-option v-for="item in defaultDictionaryItems" :key="item.id" :value="item.id">{{ item.label }}</a-select-option></a-select></a-form-item>
            <a-form-item v-else-if="form.defaultMode === 'FIXED' && form.type === 'JSON'" label="固定默认值" required><a-textarea v-model:value="form.defaultValue" aria-label="固定默认值" :rows="4" /></a-form-item>
            <a-form-item v-else-if="form.defaultMode === 'FIXED'" label="固定默认值" required><a-input v-model:value="form.defaultValue" aria-label="固定默认值" /></a-form-item>
            <a-form-item v-if="form.defaultMode === 'FORMULA'" label="默认值公式" required><a-textarea v-model:value="form.defaultFormula" :rows="2" :maxlength="2000" /></a-form-item>
            <a-form-item v-if="form.defaultMode === 'PARENT_FIELD'" label="上级联动字段" required><a-select v-model:value="form.parentFieldId" aria-label="上级联动字段"><a-select-option v-for="field in fields.filter((candidate) => candidate.id !== editTarget?.item.id)" :key="field.id" :value="field.id">{{ field.name }}</a-select-option></a-select></a-form-item>
            <div class="modal-grid three"><a-form-item label="列宽"><a-input-number v-model:value="form.width" :min="40" :max="1200" /></a-form-item><a-form-item label="索引"><a-select v-model:value="form.indexMode" aria-label="索引"><a-select-option v-for="value in fieldIndexModes" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item><a-form-item label="单位"><a-input v-model:value="form.unit" /></a-form-item></div>
            <div v-if="!p4C3CompositionTypes.has(form.type) && !p4C4DerivedFieldTypes.has(form.type) && form.type !== 'AI_FILL'" class="modal-checks"><a-checkbox v-model:checked="form.sensitive">敏感数据</a-checkbox><a-checkbox v-model:checked="form.unique">值唯一</a-checkbox></div>
            <template v-if="textFieldTypes.has(form.type) && !p4C2FieldTypes.has(form.type)"><a-divider>文本属性</a-divider><div class="modal-grid three"><a-form-item label="最小长度"><a-input-number v-model:value="form.minLength" :min="0" /></a-form-item><a-form-item label="最大长度"><a-input-number v-model:value="form.maxLength" :min="1" /></a-form-item><a-form-item label="显示行数"><a-input-number v-model:value="form.rows" :min="1" :max="100" /></a-form-item></div><a-form-item label="正则校验"><a-input v-model:value="form.pattern" /></a-form-item><div class="modal-checks"><a-checkbox v-model:checked="form.trim">去除首尾空格</a-checkbox><a-checkbox v-model:checked="form.multiline">允许多行</a-checkbox><a-checkbox v-model:checked="form.sanitize">清理危险内容</a-checkbox></div></template>
            <template v-if="numericFieldTypes.has(form.type)"><a-divider>数值属性</a-divider><div class="modal-grid"><a-form-item label="最小值"><a-input-number v-model:value="form.minimum" style="width:100%" /></a-form-item><a-form-item label="最大值"><a-input-number v-model:value="form.maximum" style="width:100%" /></a-form-item></div><div class="modal-grid three"><a-form-item label="精度"><a-input-number v-model:value="form.precision" :min="1" :max="38" /></a-form-item><a-form-item label="小数位"><a-input-number v-model:value="form.scale" :min="0" :max="form.type === 'PERCENT' ? 4 : form.type === 'PROGRESS' ? 2 : 18" /></a-form-item><a-form-item label="步长"><a-input-number v-model:value="form.step" :min="0.01" /></a-form-item></div><div class="modal-grid"><a-form-item label="舍入方式"><a-select v-model:value="form.roundingMode"><a-select-option v-for="value in ['UP','DOWN','CEILING','FLOOR','HALF_UP','HALF_DOWN','HALF_EVEN']" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item><a-form-item label="千位分隔"><a-switch v-model:checked="form.thousandsSeparator" /></a-form-item></div><template v-if="form.type === 'MONEY'"><div class="modal-grid"><a-form-item label="允许币种" required><a-select v-model:value="form.currencies" mode="multiple" aria-label="允许币种"><a-select-option v-for="value in ['CNY','USD','EUR','JPY','GBP']" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item><a-form-item label="固定币种"><a-select v-model:value="form.fixedCurrency" allow-clear aria-label="固定币种"><a-select-option v-for="value in form.currencies" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item></div></template></template>
            <template v-if="dateFieldTypes.has(form.type)"><a-divider>日期属性</a-divider><div class="modal-grid"><a-form-item label="最早值"><a-input v-model:value="form.minimum" /></a-form-item><a-form-item label="最晚值"><a-input v-model:value="form.maximum" /></a-form-item></div><div class="modal-grid"><a-form-item label="时区"><a-input v-model:value="form.timezone" /></a-form-item><a-form-item label="格式"><a-input v-model:value="form.format" /></a-form-item></div><div class="modal-checks"><a-checkbox v-model:checked="form.includeTime">包含时间</a-checkbox></div></template>
            <template v-if="(dictionaryFieldTypes.has(form.type) && form.type !== 'STATUS') || form.type === 'SWITCH'"><a-divider>选项属性</a-divider><div class="modal-grid"><a-form-item label="显示样式"><a-select v-model:value="form.displayStyle"><a-select-option value="DEFAULT">标准</a-select-option><a-select-option value="TAG">标签</a-select-option><a-select-option value="BUTTON">按钮</a-select-option></a-select></a-form-item><a-form-item label="最多选择"><a-input-number v-model:value="form.maxSelections" :min="1" :max="1000" /></a-form-item></div><div class="modal-checks"><a-checkbox v-model:checked="form.multiple">允许多选</a-checkbox><a-checkbox v-model:checked="form.clearable">允许清空</a-checkbox><a-checkbox v-model:checked="form.optionSearchable">选项搜索</a-checkbox></div></template>
            <template v-if="fileFieldTypes.has(form.type)"><a-divider>文件属性</a-divider><div class="modal-grid"><a-form-item label="最多文件"><a-input-number v-model:value="form.maxFiles" :min="1" :max="100" /></a-form-item><a-form-item label="单文件上限(MB)"><a-input-number v-model:value="form.maxSizeMb" :min="1" :max="1024" /></a-form-item></div><a-form-item label="允许扩展名"><a-input v-model:value="form.allowedExtensions" placeholder="pdf, docx, png" /></a-form-item><div class="modal-checks"><a-checkbox v-model:checked="form.imageOnly">仅图片</a-checkbox><a-checkbox v-model:checked="form.preview">允许预览</a-checkbox><a-checkbox v-model:checked="form.watermark">下载水印</a-checkbox></div></template>
            <template v-if="form.type === 'RELATION'"><a-divider>关系字段</a-divider><div class="modal-grid"><a-form-item label="关联数量" required><a-segmented v-model:value="form.multiple" :options="[{ label: '单条 0..1', value: false }, { label: '多条 0..500', value: true }]" /></a-form-item><a-form-item label="显示字段"><a-select v-model:value="form.displayFieldId" allow-clear aria-label="关系显示字段"><a-select-option v-for="field in relationDisplayFields" :key="field.id" :value="field.id">{{ field.name }}</a-select-option></a-select></a-form-item></div><div class="modal-checks"><a-checkbox v-model:checked="form.allowCreate">允许创建并关联</a-checkbox><a-checkbox v-model:checked="form.reverseRelation">显示反向关系</a-checkbox><a-checkbox v-model:checked="form.useRelationFilter" @change="form.useRelationFilter && initializeRelationFilter()">限制可选记录</a-checkbox></div><div v-if="form.useRelationFilter" class="relation-filter-editor"><RuleConditionNodeEditor :node="form.relationFilter" :fields="relationDisplayFields" :depth="1" /></div></template>
            <template v-if="form.type === 'REFERENCE'"><a-divider>引用字段</a-divider><a-form-item label="来源关系" required><a-select v-model:value="form.sourceFieldId" aria-label="引用来源关系"><a-select-option v-for="field in referenceSourceRelations" :key="field.id" :value="field.id">{{ field.name }}</a-select-option></a-select></a-form-item><a-form-item label="目标字段" required><a-select v-model:value="form.targetFieldId" aria-label="引用目标字段"><a-select-option v-for="field in relationDisplayFields" :key="field.id" :value="field.id">{{ field.name }}</a-select-option></a-select></a-form-item><a-alert v-if="referenceTargetPreview" type="info" show-icon :message="`只读结果：${referenceTargetPreview.name} (${referenceTargetPreview.type})`" /></template>
            <template v-if="actorFieldTypes.has(form.type)"><a-divider>组织范围</a-divider><a-form-item label="选择范围"><a-select v-model:value="form.selectionScope"><a-select-option value="ALL">全部可见成员</a-select-option><a-select-option value="CURRENT_TENANT">当前租户</a-select-option><a-select-option value="CURRENT_DEPARTMENT">当前部门</a-select-option><a-select-option value="SPECIFIED">指定范围</a-select-option></a-select></a-form-item><div class="modal-checks"><a-checkbox v-model:checked="form.multiple">允许多选</a-checkbox><a-checkbox v-model:checked="form.allowInactive">允许选择停用对象</a-checkbox></div></template>
            <template v-if="form.type === 'SUBTABLE'"><a-divider>子表字段</a-divider><div class="modal-grid"><a-form-item label="最少行数" required><a-input-number v-model:value="form.minRows" :min="0" :max="200" /></a-form-item><a-form-item label="最多行数" required><a-input-number v-model:value="form.maxRows" :min="form.minRows || 0" :max="200" /></a-form-item></div><a-form-item label="子表列" required><a-select v-model:value="form.columnFieldIds" aria-label="子表列" mode="multiple" show-search><a-select-option v-for="field in relationDisplayFields" :key="field.id" :value="field.id">{{ field.name }}</a-select-option></a-select></a-form-item><div class="modal-checks"><a-checkbox v-model:checked="form.allowRowCreate">允许新增行</a-checkbox><a-checkbox v-model:checked="form.allowRowUpdate">允许编辑行</a-checkbox><a-checkbox v-model:checked="form.allowRowDelete">允许删除行</a-checkbox><a-checkbox v-model:checked="form.allowRowReorder">允许调整顺序</a-checkbox></div><div class="p4c2-list-heading"><strong>服务端聚合</strong><a-button type="button" @click="addSubtableAggregate"><Plus :size="15" />添加聚合</a-button></div><div v-for="(aggregate, index) in form.aggregates" :key="index" class="p4c2-config-row subtable-aggregate-row"><a-input v-model:value="aggregate.id" aria-label="聚合标识" placeholder="total_amount" /><a-select v-model:value="aggregate.function" aria-label="聚合函数"><a-select-option v-for="value in ['COUNT','SUM','MIN','MAX','AVG']" :key="value" :value="value">{{ value }}</a-select-option></a-select><a-select v-if="aggregate.function !== 'COUNT'" v-model:value="aggregate.columnFieldId" aria-label="聚合列"><a-select-option v-for="field in selectedSubtableColumns" :key="field.id" :value="field.id">{{ field.name }}</a-select-option></a-select><span v-else class="aggregate-no-column">无需列</span><a-button type="button" danger title="删除聚合" @click="form.aggregates.splice(index, 1)"><Trash2 :size="15" /></a-button></div></template>
            <template v-if="form.type === 'PHONE'"><a-divider>手机号规则</a-divider><a-form-item label="默认国家或地区" required><a-input v-model:value="form.defaultCountry" aria-label="手机号默认国家或地区" :maxlength="2" /></a-form-item></template>
            <template v-if="form.type === 'IDENTITY'"><a-divider>身份标识规则</a-divider><a-form-item label="标识类型" required><a-select v-model:value="form.identityKind" aria-label="身份标识类型"><a-select-option value="CN_RESIDENT_ID">中国居民身份证</a-select-option><a-select-option value="GENERIC">通用标识</a-select-option></a-select></a-form-item><a-form-item v-if="form.identityKind === 'GENERIC'" label="完整匹配规则" required><a-input v-model:value="form.pattern" aria-label="身份标识完整匹配规则" placeholder="^[A-Z0-9]{6,32}$" /></a-form-item></template>
            <template v-if="form.type === 'GEO'"><a-divider>地理位置规则</a-divider><a-form-item label="坐标系"><a-select :value="'WGS84'" disabled aria-label="地理位置坐标系"><a-select-option value="WGS84">WGS84</a-select-option></a-select></a-form-item></template>
            <template v-if="form.type === 'BARCODE'"><a-divider>条码规则</a-divider><a-form-item label="允许制式" required><a-select v-model:value="form.symbologies" mode="multiple" aria-label="条码允许制式"><a-select-option value="CODE128">CODE128</a-select-option><a-select-option value="EAN13">EAN13</a-select-option></a-select></a-form-item></template>
            <template v-if="form.type === 'RICH_TEXT'"><a-divider>富文本规则</a-divider><a-form-item label="服务端安全清理"><a-switch :checked="true" disabled aria-label="富文本服务端安全清理" /></a-form-item></template>
            <template v-if="form.type === 'JSON'"><a-divider>JSON 结构</a-divider><a-form-item label="JSON Schema" required><a-textarea v-model:value="form.jsonSchemaText" aria-label="JSON Schema" :rows="7" /></a-form-item><div class="p4c2-list-heading"><strong>可查询路径</strong><a-button type="button" @click="addJsonQueryPath"><Plus :size="15" />添加路径</a-button></div><div v-for="(path, index) in form.queryPaths" :key="path.pathSnapshotId" class="p4c2-config-row json-path-row"><a-input v-model:value="path.path" aria-label="JSON 查询路径" placeholder="$.ticket" /><a-select v-model:value="path.type" aria-label="JSON 查询路径类型"><a-select-option v-for="value in ['STRING','DECIMAL','INTEGER','BOOLEAN','DATE','DATETIME']" :key="value" :value="value">{{ value }}</a-select-option></a-select><a-button type="button" danger title="删除查询路径" @click="form.queryPaths.splice(index, 1)"><Trash2 :size="15" /></a-button></div></template>
            <template v-if="form.type === 'SECRET'"><a-divider>密文规则</a-divider><div class="modal-grid"><a-form-item label="最小长度"><a-input-number :value="1" disabled /></a-form-item><a-form-item label="最大长度"><a-input-number :value="4096" disabled /></a-form-item></div></template>
            <template v-if="form.type === 'STATUS'"><a-divider>状态机</a-divider><a-form-item label="初始状态" required><a-select v-model:value="form.initialStateIds" mode="multiple" aria-label="状态机初始状态"><a-select-option v-for="item in defaultDictionaryItems.filter((candidate) => candidate.status === 'ENABLED')" :key="item.id" :value="item.id">{{ item.label }}</a-select-option></a-select></a-form-item><div class="p4c2-list-heading"><strong>允许转换</strong><a-button type="button" @click="addStatusTransition"><Plus :size="15" />添加转换</a-button></div><div v-for="(edge, index) in form.transitions" :key="index" class="p4c2-config-row status-transition-row"><a-select v-model:value="edge.from" aria-label="状态转换起点"><a-select-option v-for="item in defaultDictionaryItems.filter((candidate) => candidate.status === 'ENABLED')" :key="item.id" :value="item.id">{{ item.label }}</a-select-option></a-select><span>到</span><a-select v-model:value="edge.to" aria-label="状态转换终点"><a-select-option v-for="item in defaultDictionaryItems.filter((candidate) => candidate.status === 'ENABLED')" :key="item.id" :value="item.id">{{ item.label }}</a-select-option></a-select><a-button type="button" danger title="删除状态转换" @click="form.transitions.splice(index, 1)"><Trash2 :size="15" /></a-button></div></template>
            <template v-if="form.type === 'AUTO_NUMBER'"><a-divider>编号属性</a-divider><div class="modal-grid"><a-form-item label="编号前缀"><a-input v-model:value="form.autoNumberPrefix" /></a-form-item><a-form-item label="数字位数"><a-input-number v-model:value="form.digits" :min="1" :max="32" /></a-form-item></div></template>
            <template v-if="form.type === 'RATING'"><a-divider>评分属性</a-divider><div class="modal-grid"><a-form-item label="最高分"><a-input-number v-model:value="form.maxRating" :min="1" :max="20" /></a-form-item><a-form-item label="步长"><a-input-number v-model:value="form.step" :min="0.1" /></a-form-item></div></template>
            <template v-if="form.type === 'FORMULA' || form.type === 'CALCULATED'">
              <a-divider>结构化表达式</a-divider>
              <a-alert type="info" show-icon message="表达式只由受支持的字段、类型化常量和运算符组成，不执行自由代码。" />
              <a-form-item label="运算符" required><a-select :value="form.expressionOperator" aria-label="派生表达式运算符" @change="expressionOperatorChanged"><a-select-option v-for="operator in derivedExpressionOperators" :key="operator" :value="operator">{{ derivedOperatorLabels[operator] }}</a-select-option></a-select></a-form-item>
              <div v-for="(operand, index) in expressionOperands.slice(0, expressionArity)" :key="index" class="derived-operand">
                <a-form-item :label="expressionOperandLabel(index)" required>
                  <a-segmented v-model:value="operand.kind" :options="[{ label: '字段', value: 'FIELD' }, { label: '常量', value: 'LITERAL' }]" />
                  <a-select v-if="operand.kind === 'FIELD'" v-model:value="operand.fieldId" :aria-label="`${expressionOperandLabel(index)}字段`" show-search><a-select-option v-for="field in derivedCurrentFields" :key="field.id" :value="field.id">{{ field.name }} · {{ derivedSchemaForField(field) }}</a-select-option></a-select>
                  <div v-else class="derived-literal"><a-select :value="operand.literalType" :aria-label="`${expressionOperandLabel(index)}常量类型`" @change="literalTypeChanged(operand, $event)"><a-select-option v-for="schemaName in ['STRING','DECIMAL','INTEGER','DATE','DATETIME','BOOLEAN']" :key="schemaName" :value="schemaName">{{ schemaName }}</a-select-option></a-select><a-switch v-if="operand.literalType === 'BOOLEAN'" v-model:checked="operand.literalValue" /><a-input-number v-else-if="operand.literalType === 'DECIMAL' || operand.literalType === 'INTEGER'" v-model:value="operand.literalValue" :precision="operand.literalType === 'INTEGER' ? 0 : undefined" style="width:100%" /><a-input v-else v-model:value="operand.literalValue" :type="operand.literalType === 'DATE' ? 'date' : operand.literalType === 'DATETIME' ? 'datetime-local' : 'text'" /></div>
                </a-form-item>
              </div>
            </template>
            <template v-if="form.type === 'SUMMARY'">
              <a-divider>关系汇总</a-divider>
              <div class="modal-grid"><a-form-item label="来源关系" required><a-select v-model:value="form.relationFieldId" aria-label="汇总来源关系" @change="derivedRelationChanged"><a-select-option v-for="field in derivedSourceRelations" :key="field.id" :value="field.id">{{ field.name }}</a-select-option></a-select></a-form-item><a-form-item label="归并方式" required><a-select :value="form.reduction" aria-label="汇总归并方式" @change="summaryReductionChanged"><a-select-option v-for="value in ['COUNT','SUM','MIN','MAX','AVG']" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item></div>
              <a-form-item v-if="form.reduction !== 'COUNT'" label="目标字段" required><a-select v-model:value="form.targetFieldId" aria-label="汇总目标字段" show-search><a-select-option v-for="field in derivedSelectableTargetFields" :key="field.id" :value="field.id">{{ field.name }} · {{ derivedSchemaForField(field) }}</a-select-option></a-select></a-form-item>
            </template>
            <template v-if="form.type === 'LOOKUP'">
              <a-divider>关系查找</a-divider>
              <div class="modal-grid"><a-form-item label="来源关系" required><a-select v-model:value="form.relationFieldId" aria-label="查找来源关系" @change="derivedRelationChanged"><a-select-option v-for="field in derivedSourceRelations" :key="field.id" :value="field.id">{{ field.name }}</a-select-option></a-select></a-form-item><a-form-item label="目标字段" required><a-select v-model:value="form.targetFieldId" aria-label="查找目标字段" show-search><a-select-option v-for="field in derivedSelectableTargetFields" :key="field.id" :value="field.id">{{ field.name }} · {{ derivedSchemaForField(field) }}</a-select-option></a-select></a-form-item></div>
              <a-checkbox v-model:checked="form.distinct">结果去重（保持首次出现顺序）</a-checkbox>
            </template>
            <template v-if="form.type === 'AGGREGATE'">
              <a-divider>子表聚合</a-divider>
              <div class="modal-grid"><a-form-item label="子表字段" required><a-select v-model:value="form.subtableFieldId" aria-label="聚合子表字段" @change="aggregateSubtableChanged"><a-select-option v-for="field in derivedSubtables" :key="field.id" :value="field.id">{{ field.name }}</a-select-option></a-select></a-form-item><a-form-item label="已声明聚合" required><a-select v-model:value="form.aggregateId" aria-label="已声明子表聚合"><a-select-option v-for="aggregate in derivedAggregateOptions" :key="aggregate.id" :value="aggregate.id">{{ aggregate.id }} · {{ aggregate.function }}</a-select-option></a-select></a-form-item></div>
            </template>
            <div v-if="p4C4DerivedFieldTypes.has(form.type)" class="derived-contract-preview" :data-valid="derivedContractValid"><strong>{{ derivedCycle.length ? '发布校验：检测到循环依赖' : `推导结果：${derivedResultSchema ?? '类型不兼容'}` }}</strong><span>{{ derivedCycle.length ? derivedCycleLabels.join(' → ') : `依赖：${derivedDependencyLabels.length ? derivedDependencyLabels.join(' → ') : '尚未选择'}` }}</span></div>
            <template v-if="form.type === 'AI_FILL'">
              <a-divider>智能填充契约</a-divider>
              <a-alert class="ai-fill-contract-notice" type="info" show-icon message="AI_FILL 是只读物化字段" description="普通创建、编辑、批量与导入不会写入此字段；成员生成预览并显式确认后才会物化。" />
              <div class="modal-grid">
                <a-form-item label="结果标量类型" required><a-select v-model:value="form.resultSchema" class="ai-fill-result-schema"><a-select-option v-for="value in aiFillResultSchemas" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item>
                <a-form-item label="系统模型策略" required><a-input v-model:value="form.modelPolicy" class="ai-fill-model-policy" disabled /></a-form-item>
                <a-form-item label="最低置信度（0.50–1.00）" required><a-input-number v-model:value="form.minConfidence" class="ai-fill-min-confidence" :min="0.5" :max="1" :step="0.05" /></a-form-item>
                <a-form-item label="已有值覆盖方式" required><a-select v-model:value="form.overwriteMode" class="ai-fill-overwrite-mode"><a-select-option v-for="value in aiFillOverwriteModes" :key="value" :value="value">{{ value === 'NEVER' ? 'NEVER（禁止覆盖）' : 'CONFIRM（再次确认）' }}</a-select-option></a-select></a-form-item>
              </div>
              <a-form-item label="来源字段（同模块 1–16 个非敏感标量字段）" required><a-select v-model:value="form.sourceFieldIds" class="ai-fill-source-fields" mode="multiple" show-search :max-tag-count="6"><a-select-option v-for="field in aiFillSourceFields" :key="field.id" :value="field.id">{{ field.name }} · {{ field.code }} · {{ derivedSchemaForField(field) }}</a-select-option></a-select></a-form-item>
              <a-form-item label="提示模板" required><a-textarea v-model:value="form.promptTemplate" class="ai-fill-prompt-template" :rows="4" :maxlength="4000" show-count /></a-form-item>
            </template>
          </template>
          <template v-if="modal === 'action'">
            <a-form-item label="二次确认"><a-input v-model:value="form.confirmMessage" :maxlength="300" /></a-form-item>
            <div class="modal-grid"><a-form-item label="样式"><a-select v-model:value="form.style"><a-select-option v-for="value in ['PRIMARY','DEFAULT','DANGER','LINK']" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item><a-form-item label="打开方式"><a-select v-model:value="form.openMode"><a-select-option v-for="value in ['CURRENT','DRAWER','MODAL','NEW_TAB']" :key="value" :value="value">{{ value }}</a-select-option></a-select></a-form-item></div>
            <a-form-item label="目标页面"><a-select v-model:value="form.targetPageId" allow-clear><a-select-option v-for="page in pages" :key="page.id" :value="page.id">{{ page.name }}</a-select-option></a-select></a-form-item>
            <a-form-item label="成功提示"><a-input v-model:value="form.successMessage" /></a-form-item><div class="modal-checks"><a-checkbox v-model:checked="form.danger">危险动作</a-checkbox></div>
          </template>
          <template v-if="modal === 'rule'">
            <a-form-item label="优先级"><a-input-number v-model:value="form.priority" :min="0" :max="10000" /></a-form-item>
            <a-divider>生效条件</a-divider>
            <RuleConditionNodeEditor v-if="form.ruleCondition" :node="form.ruleCondition" :fields="fields" :depth="1" />
            <a-divider>规则效果</a-divider>
            <a-form-item v-if="effectTargetKind === 'field'" label="目标字段" required><a-select v-model:value="form.fieldId" aria-label="规则目标字段"><a-select-option v-for="field in fields" :key="field.id" :value="field.id">{{ field.name }}</a-select-option></a-select></a-form-item>
            <a-form-item v-if="effectTargetKind === 'action'" label="目标动作" required><a-select v-model:value="form.actionId" aria-label="规则目标动作"><a-select-option v-for="action in actions" :key="action.id" :value="action.id">{{ action.name }}</a-select-option></a-select></a-form-item>
            <a-form-item label="效果值"><a-switch v-model:checked="form.effectValue" /></a-form-item>
          </template>
          <a-form-item v-if="['group','module','dictionary','dictionaryItem','field','page','action','rule'].includes(modal || '')" label="状态"><a-select v-model:value="form.status"><a-select-option value="ENABLED">启用</a-select-option><a-select-option value="DISABLED">停用</a-select-option><a-select-option value="ARCHIVED">归档</a-select-option></a-select></a-form-item>
          <a-form-item v-if="['group','module','dictionaryItem','field','component','action'].includes(modal || '')" label="排序"><a-input-number v-model:value="form.sortOrder" :min="0" :max="100000" style="width:100%" /></a-form-item>
          <div v-if="modal === 'dictionaryItem'" class="modal-checks"><a-checkbox v-model:checked="form.isDefault">默认项</a-checkbox></div>
          <div v-if="modal === 'field'" class="modal-checks"><a-checkbox v-model:checked="form.required" :disabled="p4C4DerivedFieldTypes.has(form.type)">必填</a-checkbox><a-checkbox v-model:checked="form.hidden">隐藏</a-checkbox><a-checkbox v-model:checked="form.readonly" :disabled="p4C4DerivedFieldTypes.has(form.type) || form.type === 'AI_FILL'">只读</a-checkbox><a-checkbox v-model:checked="form.searchable" :disabled="p4C4DerivedFieldTypes.has(form.type) || p4C1FieldTypes.has(form.type) || (p4C2FieldTypes.has(form.type) && form.type !== 'RICH_TEXT')">可搜索</a-checkbox><a-checkbox v-model:checked="form.filterable">可筛选</a-checkbox><a-checkbox v-model:checked="form.showInList">列表显示</a-checkbox><a-checkbox v-model:checked="form.showInDetail">详情显示</a-checkbox></div>
        </template>
      </a-form>
    </a-modal>
  </section>
</template>

<style scoped>
.config-studio{min-width:0}.config-status-strip{display:flex;align-items:center;gap:20px;min-height:44px;padding:0 18px;border-block:1px solid #dde3e8;background:#f7f9fa;color:#4b5965;font-size:13px}.config-state{display:inline-flex;align-items:center;gap:6px;font-weight:700;color:#087f73}.config-state.dirty,.config-state.check_failed{color:#b76700}.config-workbench{display:grid;grid-template-columns:minmax(180px,230px) minmax(480px,1fr) minmax(210px,260px);min-height:calc(100vh - 205px);background:#fff}.config-resource-pane,.config-inspector-pane{min-width:0;background:#f8fafb}.config-resource-pane{border-right:1px solid #dfe5e9}.config-inspector-pane{border-left:1px solid #dfe5e9}.pane-title{height:48px;display:flex;align-items:center;justify-content:space-between;padding:0 14px;border-bottom:1px solid #dfe5e9;font-weight:700}.pane-title>span{display:flex;align-items:center;gap:7px}.pane-title button,.version-row button{border:0;background:transparent;padding:5px;color:#52616d;cursor:pointer}.pane-title button:hover,.version-row button:hover{color:#087f73;background:#e8f3f1}.module-group{padding:10px 8px 2px}.group-name{padding:5px 8px;color:#697782;font-size:12px;font-weight:700;text-transform:uppercase}.module-item{width:100%;height:42px;display:grid;grid-template-columns:18px 1fr auto;align-items:center;gap:7px;border:0;background:transparent;padding:0 9px;text-align:left;color:#33424d;cursor:pointer}.module-item span{overflow:hidden;text-overflow:ellipsis}.module-item small{color:#8a969e}.module-item.active{background:#e2f1ee;color:#076f65}.dictionary-shortcut{display:flex;justify-content:space-between;align-items:center;margin:12px 10px;padding-top:12px;border-top:1px solid #dfe5e9;font-size:12px;color:#73808a}.dictionary-shortcut button{display:flex;align-items:center;gap:6px;border:0;background:transparent;color:#2563a6;cursor:pointer}.config-design-pane{min-width:0;padding:0 18px}.module-heading{height:64px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #e2e7ea}.module-heading h2{margin:0;font-size:18px}.module-heading span{font-size:12px;color:#7c8891}.config-tabs{margin-top:2px}.tab-command,.page-toolbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:12px}.resource-table{border-top:1px solid #e0e6ea}.resource-row{min-height:52px;display:grid;grid-template-columns:minmax(160px,1fr) 120px 110px minmax(120px,auto);align-items:center;gap:12px;border-bottom:1px solid #e8ecef;padding:7px 8px}.resource-row>span:first-child{display:flex;flex-direction:column}.resource-row small{color:#7b8790;overflow:hidden;text-overflow:ellipsis}.page-canvas{min-height:380px;border:1px solid #dce3e7;background:#f6f8f9;padding:12px}.canvas-title{display:flex;align-items:center;gap:7px;margin-bottom:12px;font-weight:700}.component-grid{display:grid;grid-template-columns:repeat(12,1fr);gap:8px}.component-block{min-height:68px;padding:12px;border:1px solid #cfdade;background:#fff;display:flex;flex-direction:column;justify-content:center}.component-block small{color:#77848d}.version-list{padding:8px}.version-row{position:relative;display:grid;grid-template-columns:1fr auto;gap:4px;padding:10px 7px;border-bottom:1px solid #e0e6e9}.version-row small{grid-column:1/-1;color:#75818a}.version-row button{position:absolute;right:3px;bottom:5px}.pane-empty{padding:20px;color:#849099;text-align:center}.check-issues{padding:8px;border-bottom:1px solid #dfe5e9}.check-issue{padding:8px;margin-bottom:6px;border-left:3px solid #d19a42;background:#fff}.check-issue.blocker{border-color:#bd3737}.check-issue strong,.check-issue small{display:block}.check-issue small{color:#737f88;margin-top:3px}.config-mobile-readonly{display:grid;gap:12px;padding:14px}.mobile-check{display:flex;justify-content:space-between;padding:12px;border-block:1px solid #e1e6e9}.modal-checks{display:flex;flex-wrap:wrap;gap:12px}.pane-empty+.pane-empty{display:none}@media(max-width:1199px){.config-workbench{grid-template-columns:220px minmax(480px,1fr)}.config-inspector-pane{display:none}}
.config-staged-role-path{display:flex;align-items:center;justify-content:space-between;gap:16px;padding:10px 18px;border-bottom:1px solid #dce5e8;background:#eef8f6}.field-permission-controls{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:0 12px}.field-permission-code-preview{display:block;margin:-12px 0 14px;padding:7px 9px;background:#f3f6f7;overflow-wrap:anywhere}.field-write-permission-disabled{grid-column:1/-1;margin:-5px 0 12px;color:#7a5526}
.preview-controls{display:grid;grid-template-columns:minmax(180px,1fr) minmax(180px,1fr) auto;gap:10px;margin-bottom:16px}.preview-compare{display:grid;grid-template-columns:1fr 1fr;gap:14px;margin-top:16px}.preview-compare>section{min-width:0;border:1px solid #dfe5e9}.preview-compare header{display:flex;justify-content:space-between;padding:10px 12px;border-bottom:1px solid #dfe5e9;background:#f7f9fa}.preview-compare header span,.preview-module small{color:#73808a}.preview-group{padding:10px 12px;border-bottom:1px solid #edf0f2}.preview-module{display:flex;justify-content:space-between;gap:12px;padding:8px 0 0}.preview-module span{min-width:0}.preview-module span small{margin-left:5px}@media(max-width:760px){.preview-controls,.preview-compare{grid-template-columns:1fr}}
.module-heading-actions{display:flex;align-items:center;gap:10px}
.resource-row{grid-template-columns:minmax(140px,1fr) 110px 95px minmax(100px,auto) 62px}.row-actions{display:inline-flex!important;align-items:center;justify-content:flex-end;gap:3px}.row-actions button,.component-actions button,.group-name button{display:inline-grid;place-items:center;width:28px;height:28px;border:0;background:transparent;color:#687781;cursor:pointer}.row-actions button:hover,.component-actions button:hover,.group-name button:hover{background:#e7eeef;color:#087f73}.group-name{display:flex;align-items:center;justify-content:space-between;gap:6px}.component-block{position:relative}.component-actions{position:absolute;top:4px;right:4px;display:flex}.dictionary-manager{display:grid;grid-template-columns:260px 1fr;min-height:520px;border:1px solid #dfe5e9}.dictionary-manager>aside{border-right:1px solid #dfe5e9;background:#f7f9fa}.dictionary-manager>main{padding:14px}.dictionary-row{display:grid;grid-template-columns:1fr auto;align-items:center;border-bottom:1px solid #e4e9ec}.dictionary-row>button{display:flex;flex-direction:column;align-items:flex-start;min-width:0;padding:11px 12px;border:0;background:transparent;text-align:left;cursor:pointer}.dictionary-row>button small,.dictionary-item small{color:#77848d}.dictionary-row.active{background:#e2f1ee}.dictionary-item{display:grid;grid-template-columns:18px minmax(140px,1fr) auto 64px;align-items:center;gap:8px;min-height:52px;border-bottom:1px solid #e6eaed}.dictionary-item>span:nth-child(2){display:flex;flex-direction:column}.color-swatch{width:14px;height:14px;border:1px solid #c8d0d5}.dictionary-item-fields{display:grid;grid-template-columns:1fr 100px;gap:12px}@media(max-width:760px){.dictionary-manager{grid-template-columns:1fr}.dictionary-manager>aside{border-right:0;border-bottom:1px solid #dfe5e9}}
.modal-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}.modal-grid.three{grid-template-columns:repeat(3,minmax(0,1fr))}.modal-grid :deep(.ant-input-number){width:100%}.rule-condition-row{display:grid;grid-template-columns:minmax(130px,1fr) 130px minmax(120px,1fr) minmax(100px,1fr) 34px;gap:8px;align-items:center;margin-bottom:8px}.rule-condition-row>button{width:34px;padding:0}@media(max-width:760px){.modal-grid,.modal-grid.three{grid-template-columns:1fr}.rule-condition-row{grid-template-columns:1fr 1fr}.rule-condition-row>button{justify-self:end}}
.resource-row,.component-block{cursor:pointer}.resource-row.selected,.component-block.selected{background:#e8f3f1;box-shadow:inset 3px 0 #087f73}.inspector-properties{padding:10px 12px;border-bottom:1px solid #dfe5e9;background:#fff}.inspector-properties header{display:flex;align-items:center;justify-content:space-between;gap:8px}.inspector-properties dl{display:grid;grid-template-columns:66px 1fr;gap:7px 9px;margin:12px 0 2px;font-size:12px}.inspector-properties dt{color:#77848d}.inspector-properties dd{min-width:0;margin:0;overflow-wrap:anywhere;color:#2f3c45}.history-title{border-top:1px solid #dfe5e9}
.resource-row{grid-template-columns:minmax(120px,1fr) 88px 68px minmax(70px,.8fr) 62px;gap:6px;overflow:hidden}.resource-row>*{min-width:0;overflow:hidden;text-overflow:ellipsis}.resource-row>.row-actions{overflow:visible}.diff-controls{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr) auto;gap:10px;margin-bottom:14px}.diff-sections{border-top:1px solid #dfe5e9}.diff-sections section{padding:10px 0;border-bottom:1px solid #e6ebee}.diff-sections header{display:flex;justify-content:space-between;gap:12px}.diff-sections header span{color:#73808a}.diff-values{display:flex;flex-wrap:wrap;gap:6px;margin-top:7px}.diff-values span{padding:2px 6px;background:#f3f6f7;font-family:monospace;font-size:12px}@media(max-width:760px){.diff-controls{grid-template-columns:1fr}}
.p4c2-list-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin:8px 0}.p4c2-list-heading .ant-btn{display:inline-flex;align-items:center;gap:6px}.p4c2-config-row{display:grid;align-items:center;gap:8px;margin-bottom:8px}.json-path-row{grid-template-columns:minmax(160px,1fr) 130px 34px}.status-transition-row{grid-template-columns:minmax(120px,1fr) auto minmax(120px,1fr) 34px}.subtable-aggregate-row{grid-template-columns:minmax(130px,1fr) 100px minmax(140px,1fr) 34px}.aggregate-no-column{display:grid;place-items:center;min-height:32px;border:1px dashed #d5dde1;color:#75818a}.p4c2-config-row>.ant-btn{width:34px;padding:0}@media(max-width:760px){.json-path-row,.status-transition-row,.subtable-aggregate-row{grid-template-columns:minmax(0,1fr) 34px}.json-path-row>.ant-select,.status-transition-row>.ant-select,.subtable-aggregate-row>.ant-select,.subtable-aggregate-row>.aggregate-no-column{grid-column:1}.status-transition-row>span{display:none}.p4c2-config-row>.ant-btn{grid-column:2;grid-row:1}}
.derived-operand{margin-top:10px;padding:12px;border:1px solid #dce4e8;background:#f8fafb}.derived-operand :deep(.ant-form-item){margin-bottom:0}.derived-operand :deep(.ant-form-item-control-input-content){display:grid;grid-template-columns:auto minmax(0,1fr);gap:10px}.derived-literal{display:grid;grid-template-columns:130px minmax(0,1fr);gap:8px}.derived-contract-preview{display:flex;flex-wrap:wrap;justify-content:space-between;gap:8px;margin-top:12px;padding:11px 13px;border-left:3px solid #b96934;background:#fff6ed;color:#8b4c22}.derived-contract-preview[data-valid="true"]{border-left-color:#13806f;background:#edf8f5;color:#096b5f}.derived-contract-preview span{overflow-wrap:anywhere}@media(max-width:760px){.derived-operand :deep(.ant-form-item-control-input-content),.derived-literal{grid-template-columns:1fr}.derived-contract-preview{display:grid}}
.filter-scenario-heading{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:10px}.filter-scenario-heading>span{color:#75818a;font-size:12px}.config-filter-scenario-row{margin-bottom:12px;padding:12px;border:1px solid #dce4e8;background:#f8fafb}.config-filter-scenario-row>.ant-btn{display:flex;margin-left:auto;align-items:center;gap:6px}
</style>
