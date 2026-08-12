<script setup lang="ts">
import { message } from 'ant-design-vue'
import { CheckCircle2, ChevronDown, ChevronUp, Database, Eye, Play, Plus, RefreshCw, RotateCcw, Save, Send, Trash2, X } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import MultiModuleJoinEditor from '@/components/admin/MultiModuleJoinEditor.vue'
import StatisticsResultView from '@/components/statistics/StatisticsResultView.vue'
import { ApiRequestError } from '@/services/api'
import { dataSourceAdminApi, runtimeDataSourceApi } from '@/services/dataSource'
import { configRecoveryApi } from '@/services/configRecovery'
import type {
  CreateDataSourceInput,
  DataSourceCheckResult,
  DataSourceConnectionCheckResult,
  DataSourceDetail,
  DataSourceDraftPreviewScalar,
  DataSourceDraftRowsPreviewResult,
  DataSourceFieldCapability,
  DataSourceModuleCapability,
  DataSourceMultiModuleJoin,
  DataSourceSchemaDiscoveryResult,
  DataSourceSummary,
  DataSourceSourceKind,
  DataSourceVersion,
  HttpJsonConnection,
  HttpJsonDiscoveredField,
  HttpJsonFieldProjection,
  HttpJsonSelectableSourceType,
  JdbcTableDiscoveredField,
  JdbcTableFieldProjection,
  JdbcTableSelectableSourceType,
  PublishedHttpDataSourceRows,
  PublishedHttpDataSourceScalar,
  PublishedJdbcDataSourceRows,
  RuntimeDataSourceField,
  RuntimeDataSourceMetadata,
  RuntimeDataSourceRow,
  RuntimeDataSourceRows,
  SaveDataSourceDraftInput,
} from '@/types/dataSource'
import type { DataSourceStatisticsCapabilities, DataSourceStatisticsResult, StatisticsVisualization } from '@/types/statistics'
import {
  fromFixedFilter,
  inputTypeForField,
  isBooleanField,
  isCollectionOperator,
  isNoValueOperator,
  isRangeOperator,
  normalizeJdbcTableConnection,
  toFixedFilter,
  type DataSourceFilterDraft,
} from './dataSourceEditorModel'
import {
  aggregationChanged,
  applyStatisticsVisualization,
  buildStatisticsRequest,
  createStatisticsDraft,
  statisticsCapabilities,
  validateStatisticsDraft,
} from './statisticsEditorModel'
import { useAdminViewport } from '@/composables/useAdminViewport'

type EditorState = 'clean' | 'dirty' | 'saving' | 'checked' | 'published' | 'error'

const { isMobile } = useAdminViewport()
const route = useRoute()
const systemId = computed(() => String(route.params.systemId))
const sources = ref<DataSourceSummary[]>([])
const modules = ref<DataSourceModuleCapability[]>([])
const sourceSearch = ref('')
const sourceKindFilter = ref<'ALL' | DataSourceSourceKind>('ALL')
const sourcePublishFilter = ref<'ALL' | 'PUBLISHED' | 'DRAFT'>('ALL')
const activeTab = ref<'overview' | 'connection' | 'preview' | 'versions' | 'statistics'>('overview')
const selected = ref<DataSourceDetail | null>(null)
const versions = ref<DataSourceVersion[]>([])
const runtimeMetadata = ref<RuntimeDataSourceMetadata | null>(null)
const runtimeRows = ref<RuntimeDataSourceRows | null>(null)
const runtimeStatisticsCapabilities = ref<DataSourceStatisticsCapabilities | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const previewLoading = ref(false)
const statisticsLoading = ref(false)
const editorState = ref<EditorState>('clean')
const draftDirty = ref(false)
const errorMessage = ref('')
const createError = ref('')
const checkResult = ref<DataSourceCheckResult | null>(null)
const connectionCheckResult = ref<DataSourceConnectionCheckResult | null>(null)
const connectionCheckError = ref('')
const connectionChecking = ref(false)
const schemaDiscoveryResult = ref<DataSourceSchemaDiscoveryResult | null>(null)
const schemaDiscoveryError = ref('')
const schemaDiscovering = ref(false)
const draftRowsPreviewResult = ref<DataSourceDraftRowsPreviewResult | null>(null)
const draftRowsPreviewError = ref('')
const draftRowsPreviewing = ref(false)
const publishing = ref(false)
const publishedHttpRowsResult = ref<PublishedHttpDataSourceRows | null>(null)
const publishedHttpRowsError = ref('')
const publishedHttpRowsLoading = ref(false)
const publishedJdbcRowsResult = ref<PublishedJdbcDataSourceRows | null>(null)
const publishedJdbcRowsError = ref('')
const publishedJdbcRowsLoading = ref(false)
const createOpen = ref(false)
const creating = ref(false)
const outputCandidate = ref('')
const projectionSourceCandidate = ref('')
const projectionTargetCandidate = ref('')
const jdbcProjectionSourceCandidate = ref('')
const jdbcProjectionTargetCandidate = ref('')
const previewPage = ref(1)
const statisticsVisualization = ref<StatisticsVisualization>('STAT_VALUE')
const statisticsDraft = reactive(createStatisticsDraft())
const statisticsResult = ref<DataSourceStatisticsResult | null>(null)
const statisticsError = ref('')
const previewSize = 10
let selectionGeneration = 0
let previewGeneration = 0
let schemaDiscoveryGeneration = 0
let draftRowsPreviewGeneration = 0
let publishGeneration = 0
let activePublishRequest = 0
let publishedHttpRowsGeneration = 0
let publishedJdbcRowsGeneration = 0

const createForm = reactive<CreateDataSourceInput>({
  code: '',
  moduleId: '',
  name: '',
  description: '',
  sourceKind: 'NATIVE_MODULE',
})
const createHttp = reactive({ endpoint: '', authSecretRef: '', timeoutSeconds: 5 })
const createJdbc = reactive({
  host: '', port: 3306, databaseName: '', tableName: '',
  usernameSecretRef: '', passwordSecretRef: '', connectTimeoutSeconds: 5, queryTimeoutSeconds: 10,
})
function defaultJoinPlan(anchorModuleId = ''): DataSourceMultiModuleJoin {
  const candidates = sources.value
    .filter(source => source.id !== selected.value?.id
      && source.activeVersionId
      && (source.sourceKind ?? source.draft?.sourceKind ?? 'NATIVE_MODULE') !== 'MULTI_MODULE_JOIN')
  const anchor = candidates.find(source => !anchorModuleId || source.moduleId === anchorModuleId)
  const secondary = candidates.find(source => source.id !== anchor?.id && source.moduleId !== anchor?.moduleId)
    ?? candidates.find(source => source.id !== anchor?.id)
  const selectedSources = [anchor, secondary]
  const inputs = selectedSources.map((source, index) => ({
    alias: `source${index + 1}`,
    dataSourceId: source?.id ?? '',
    dataSourceVersionId: source?.activeVersionId ?? '',
  }))
  return {
    inputs,
    edges: [{
      leftAlias: inputs[0]!.alias, leftFieldCode: '', rightAlias: inputs[1]!.alias,
      rightFieldCode: '', joinType: 'LEFT', cardinality: 'MANY_TO_ONE',
    }],
    projections: [{ sourceAlias: inputs[0]!.alias, sourceFieldCode: '', fieldCode: `${inputs[0]!.alias}__` }],
    failureMode: 'FAIL_FAST', timeoutSeconds: 5, rowLimit: 50,
  }
}
const createJoinPlan = ref<DataSourceMultiModuleJoin>(defaultJoinPlan())
const editor = reactive({
  name: '',
  description: '',
  sourceKind: 'NATIVE_MODULE' as DataSourceSourceKind,
  httpEndpoint: '',
  httpAuthSecretRef: '',
  httpTimeoutSeconds: 5,
  httpFieldProjections: [] as HttpJsonFieldProjection[],
  jdbcHost: '',
  jdbcPort: 3306,
  jdbcDatabaseName: '',
  jdbcTableName: '',
  jdbcUsernameSecretRef: '',
  jdbcPasswordSecretRef: '',
  jdbcUsernameConfigured: false,
  jdbcPasswordConfigured: false,
  jdbcConnectTimeoutSeconds: 5,
  jdbcQueryTimeoutSeconds: 10,
  jdbcFieldProjections: [] as JdbcTableFieldProjection[],
  multiModuleJoin: defaultJoinPlan(),
  outputFields: [] as string[],
  filters: [] as DataSourceFilterDraft[],
  sortFieldCode: '',
  sortDirection: 'ASC' as 'ASC' | 'DESC',
  timeFieldCode: '',
})

const selectedModule = computed(() => {
  if (!selected.value) return undefined
  return modules.value.find((item) => item.moduleId === selected.value?.moduleId)
    ?? modules.value.find((item) => item.moduleCode === selected.value?.moduleCode)
})
const availableFields = computed(() => selectedModule.value?.fields.filter((field) => field.available) ?? [])
const outputOptions = computed(() => availableFields.value.filter((field) => !editor.outputFields.includes(field.fieldCode)))
const sortableFields = computed(() => editor.outputFields
  .map(fieldByCode)
  .filter((field): field is DataSourceFieldCapability => Boolean(field?.sortable)))
const temporalFields = computed(() => editor.outputFields
  .map(fieldByCode)
  .filter((field): field is DataSourceFieldCapability => Boolean(field?.temporal)))
const blockerCount = computed(() => checkResult.value?.blockerCount
  ?? checkResult.value?.issues.filter((item) => item.severity === 'BLOCKER').length
  ?? 0)
const warningCount = computed(() => checkResult.value?.warningCount
  ?? checkResult.value?.issues.filter((item) => item.severity === 'WARNING').length
  ?? 0)
const previewFields = computed<RuntimeDataSourceField[]>(() =>
  runtimeMetadata.value?.outputFields ?? runtimeMetadata.value?.fields ?? [])
const previewItems = computed<RuntimeDataSourceRow[]>(() => runtimeRows.value?.rows ?? runtimeRows.value?.items ?? [])
const canPrevious = computed(() => previewPage.value > 1)
const canNext = computed(() => previewPage.value * previewSize < (runtimeRows.value?.total ?? 0))
const activePublishedVersion = computed(() => versions.value.find(version =>
  version.id === selected.value?.activeVersionId || version.active))
const isHttpDraft = computed(() => editor.sourceKind === 'HTTP_JSON')
const isJdbcDraft = computed(() => editor.sourceKind === 'JDBC_TABLE')
const isJoinDraft = computed(() => editor.sourceKind === 'MULTI_MODULE_JOIN')
const isNativeDraft = computed(() => editor.sourceKind === 'NATIVE_MODULE')
const isExternalDraft = computed(() => isHttpDraft.value || isJdbcDraft.value)
const isPublishedHttp = computed(() => {
  if (!selected.value?.activeVersionId) return false
  return (activePublishedVersion.value?.snapshot.sourceKind ?? draftSourceKind(selected.value)) === 'HTTP_JSON'
})
const isPublishedJdbc = computed(() => {
  if (!selected.value?.activeVersionId) return false
  return (activePublishedVersion.value?.snapshot.sourceKind ?? draftSourceKind(selected.value)) === 'JDBC_TABLE'
})
const httpPublishReady = computed(() => isHttpDraft.value
  ? editor.httpFieldProjections.length > 0
  : isJdbcDraft.value
    ? editor.jdbcFieldProjections.length > 0
    : true)
const filteredSources = computed(() => {
  const query = sourceSearch.value.trim().toLocaleLowerCase()
  return sources.value.filter((source) => {
    const kind = summarySourceKind(source)
    if (sourceKindFilter.value !== 'ALL' && kind !== sourceKindFilter.value) return false
    if (sourcePublishFilter.value === 'PUBLISHED' && !source.activeVersionId) return false
    if (sourcePublishFilter.value === 'DRAFT' && source.activeVersionId) return false
    if (!query) return true
    return [source.name, source.code, moduleName(source), sourceTarget(source)]
      .some(value => String(value ?? '').toLocaleLowerCase().includes(query))
  })
})
const projectionSourceOptions = computed(() => {
  const mapped = new Set(editor.httpFieldProjections.map(item => item.sourceField))
  return (schemaDiscoveryResult.value?.fields ?? []).filter((field): field is HttpJsonDiscoveredField =>
    isHttpDiscoveredField(field)
      && field.selectable
      && selectableSourceType(field.inferredType)
      && !mapped.has(field.sourceField))
})
const selectedProjectionSource = computed(() => projectionSourceOptions.value.find(
  field => field.sourceField === projectionSourceCandidate.value,
))
const projectionTargetOptions = computed(() => selectedProjectionSource.value
  && selectableSourceType(selectedProjectionSource.value.inferredType)
  ? compatibleAnchorFields(selectedProjectionSource.value.inferredType)
  : [])
const jdbcProjectionSourceOptions = computed(() => {
  const mapped = new Set(editor.jdbcFieldProjections.map(item => item.sourceColumn))
  return (schemaDiscoveryResult.value?.fields ?? []).filter((field): field is JdbcTableDiscoveredField =>
    isJdbcDiscoveredField(field)
      && field.selectable
      && selectableJdbcSourceType(field.inferredType)
      && !mapped.has(field.sourceColumn))
})
const selectedJdbcProjectionSource = computed(() => jdbcProjectionSourceOptions.value.find(
  field => field.sourceColumn === jdbcProjectionSourceCandidate.value,
))
const jdbcProjectionTargetOptions = computed(() => selectedJdbcProjectionSource.value
  && selectableJdbcSourceType(selectedJdbcProjectionSource.value.inferredType)
  ? compatibleAnchorFields(selectedJdbcProjectionSource.value.inferredType)
  : [])
const statisticsCapability = computed(() => statisticsCapabilities(
  runtimeStatisticsCapabilities.value?.fields ?? [],
))
const statisticsIssues = computed(() => validateStatisticsDraft(
  statisticsDraft,
  statisticsCapability.value,
  statisticsVisualization.value,
))

const stateText: Record<EditorState, string> = {
  clean: '草稿已同步',
  dirty: '有未保存修改',
  saving: '正在保存',
  checked: '检查已通过',
  published: '已发布',
  error: '操作失败',
}

function requestError(error: unknown, fallback: string) {
  return error instanceof ApiRequestError
    ? error.message || error.code
    : error instanceof Error
      ? error.message
      : fallback
}

function draftSourceKind(detail: DataSourceDetail) {
  return detail.draft.sourceKind ?? detail.sourceKind ?? 'NATIVE_MODULE'
}

function sourceKindLabel(value: DataSourceSourceKind) {
  if (value === 'HTTP_JSON') return 'HTTP JSON'
  if (value === 'JDBC_TABLE') return '只读 MySQL 表'
  if (value === 'MULTI_MODULE_JOIN') return '多模块 Join'
  return '原生模块'
}

function summarySourceKind(source: DataSourceSummary) {
  if (selected.value?.id === source.id) return editor.sourceKind
  return source.sourceKind ?? source.draft?.sourceKind ?? 'NATIVE_MODULE'
}

function sourceTarget(source: DataSourceSummary) {
  if (selected.value?.id === source.id && editor.sourceKind === 'JDBC_TABLE') {
    const target = [editor.jdbcDatabaseName, editor.jdbcTableName].filter(Boolean).join('.')
    if (target) return target
  }
  return moduleName(source)
}

function validateHttpConnection(connection: HttpJsonConnection) {
  if (!connection.endpoint || connection.endpoint.length > 1024) {
    throw new Error('HTTPS Endpoint 长度必须为 1 到 1024 个字符')
  }
  let endpoint: URL
  try {
    endpoint = new URL(connection.endpoint)
  } catch {
    throw new Error('Endpoint 必须是合法的 HTTPS URL')
  }
  if (endpoint.protocol !== 'https:' || endpoint.username || endpoint.password
      || endpoint.search || endpoint.hash || endpoint.port === '0') {
    throw new Error('Endpoint 必须使用 HTTPS，且不能包含用户信息、查询参数或 fragment')
  }
  if (!Number.isInteger(connection.timeoutSeconds)
      || connection.timeoutSeconds < 1 || connection.timeoutSeconds > 10) {
    throw new Error('超时时间必须是 1 到 10 秒的整数')
  }
  const secretRef = connection.authSecretRef?.trim() ?? ''
  if (secretRef && (secretRef.length > 512
      || !/^env:\/\/EXAMINE_DS_S[1-9]\d*_T[1-9]\d*_[A-Z][A-Z0-9_]{0,127}_V[1-9]\d{0,8}$/u.test(secretRef))) {
    throw new Error('SecretRef 必须使用当前数据源命名空间的 env://EXAMINE_DS_S{systemId}_T{tenantId}_{ALIAS}_V{n} 格式')
  }
  return { ...connection, authSecretRef: secretRef || null }
}

function httpConnection(endpoint: string, authSecretRef: string, timeoutSeconds: number) {
  return validateHttpConnection({
    endpoint: endpoint.trim(),
    authSecretRef: authSecretRef.trim() || null,
    timeoutSeconds: Number(timeoutSeconds),
  })
}

function fieldByCode(code: string) {
  return selectedModule.value?.fields.find((field) => field.fieldCode === code)
}

function selectableSourceType(value: string): value is HttpJsonSelectableSourceType {
  return ['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN'].includes(value)
}

function selectableJdbcSourceType(value: string): value is JdbcTableSelectableSourceType {
  return ['STRING', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'DATE', 'TIME', 'DATETIME'].includes(value)
}

function isHttpDiscoveredField(
  field: HttpJsonDiscoveredField | JdbcTableDiscoveredField,
): field is HttpJsonDiscoveredField {
  return 'sourceField' in field
}

function isJdbcDiscoveredField(
  field: HttpJsonDiscoveredField | JdbcTableDiscoveredField,
): field is JdbcTableDiscoveredField {
  return 'sourceColumn' in field
}

function discoveredSourceName(field: HttpJsonDiscoveredField | JdbcTableDiscoveredField) {
  return isJdbcDiscoveredField(field) ? field.sourceColumn : field.sourceField
}

function compatibleAnchorFields(sourceType: JdbcTableSelectableSourceType, currentFieldCode = '') {
  const used = new Set(editor.httpFieldProjections
    .map(item => item.fieldCode)
    .filter(code => code !== currentFieldCode))
  return availableFields.value.filter((field) => {
    if (used.has(field.fieldCode)) return false
    const targetType = field.type.toUpperCase()
    if (sourceType === 'STRING') return ['TEXT', 'TEXTAREA', 'RICH_TEXT'].includes(targetType)
    if (sourceType === 'INTEGER' || sourceType === 'DECIMAL') return targetType === 'NUMBER'
    if (sourceType === 'BOOLEAN') return targetType === 'SWITCH'
    if (sourceType === 'DATE') return targetType === 'DATE'
    if (sourceType === 'TIME') return targetType === 'TIME'
    return sourceType === 'DATETIME' && ['DATETIME', 'CREATED_AT', 'UPDATED_AT'].includes(targetType)
  })
}

function resetProjectionCandidate() {
  const candidate = projectionSourceOptions.value[0]
  projectionSourceCandidate.value = candidate?.sourceField ?? ''
  if (!candidate || !selectableSourceType(candidate.inferredType)) {
    projectionTargetCandidate.value = ''
    return
  }
  const targets = compatibleAnchorFields(candidate.inferredType)
  projectionTargetCandidate.value = targets.some(item => item.fieldCode === candidate.suggestedFieldCode)
    ? candidate.suggestedFieldCode ?? ''
    : targets[0]?.fieldCode ?? ''
}

function resetJdbcProjectionCandidate() {
  const candidate = jdbcProjectionSourceOptions.value[0]
  jdbcProjectionSourceCandidate.value = candidate?.sourceColumn ?? ''
  if (!candidate || !selectableJdbcSourceType(candidate.inferredType)) {
    jdbcProjectionTargetCandidate.value = ''
    return
  }
  const targets = compatibleAnchorFields(candidate.inferredType)
  jdbcProjectionTargetCandidate.value = targets.some(item => item.fieldCode === candidate.suggestedFieldCode)
    ? candidate.suggestedFieldCode ?? ''
    : targets[0]?.fieldCode ?? ''
}

function clearSchemaDiscovery() {
  schemaDiscoveryGeneration += 1
  schemaDiscoveryResult.value = null
  schemaDiscoveryError.value = ''
  schemaDiscovering.value = false
  projectionSourceCandidate.value = ''
  projectionTargetCandidate.value = ''
  jdbcProjectionSourceCandidate.value = ''
  jdbcProjectionTargetCandidate.value = ''
}

function clearDraftRowsPreview() {
  draftRowsPreviewGeneration += 1
  draftRowsPreviewResult.value = null
  draftRowsPreviewError.value = ''
  draftRowsPreviewing.value = false
}

function clearPublishedHttpRows() {
  publishedHttpRowsGeneration += 1
  publishedHttpRowsResult.value = null
  publishedHttpRowsError.value = ''
  publishedHttpRowsLoading.value = false
}

function clearPublishedJdbcRows() {
  publishedJdbcRowsGeneration += 1
  publishedJdbcRowsResult.value = null
  publishedJdbcRowsError.value = ''
  publishedJdbcRowsLoading.value = false
}

function moduleName(source: DataSourceSummary) {
  return modules.value.find((item) => item.moduleId === source.moduleId)?.moduleName
    ?? modules.value.find((item) => item.moduleCode === source.moduleCode)?.moduleName
    ?? source.moduleCode
    ?? source.moduleId
}

function markDirty() {
  if (!selected.value || editorState.value === 'saving') return
  publishGeneration += 1
  draftDirty.value = true
  editorState.value = 'dirty'
  checkResult.value = null
  connectionCheckResult.value = null
  connectionCheckError.value = ''
  errorMessage.value = ''
}

function markConnectionDirty() {
  clearSchemaDiscovery()
  clearDraftRowsPreview()
  markDirty()
}

function markProjectionDirty() {
  clearDraftRowsPreview()
  markDirty()
}

function sourceKindChanged() {
  clearSchemaDiscovery()
  clearDraftRowsPreview()
  if (editor.sourceKind !== 'NATIVE_MODULE') {
    editor.outputFields = []
    editor.filters = []
    editor.sortFieldCode = ''
    editor.timeFieldCode = ''
  }
  if (editor.sourceKind !== 'HTTP_JSON') {
    editor.httpEndpoint = ''
    editor.httpAuthSecretRef = ''
    editor.httpTimeoutSeconds = 5
    editor.httpFieldProjections = []
  }
  if (editor.sourceKind !== 'JDBC_TABLE') {
    editor.jdbcHost = ''
    editor.jdbcPort = 3306
    editor.jdbcDatabaseName = ''
    editor.jdbcTableName = ''
    editor.jdbcUsernameSecretRef = ''
    editor.jdbcPasswordSecretRef = ''
    editor.jdbcUsernameConfigured = false
    editor.jdbcPasswordConfigured = false
    editor.jdbcConnectTimeoutSeconds = 5
    editor.jdbcQueryTimeoutSeconds = 10
    editor.jdbcFieldProjections = []
  }
  if (editor.sourceKind !== 'MULTI_MODULE_JOIN') {
    editor.multiModuleJoin = defaultJoinPlan()
  } else if (!editor.multiModuleJoin.inputs.length) {
    editor.multiModuleJoin = defaultJoinPlan()
  }
  markDirty()
}

function createSourceKindChanged() {
  Object.assign(createHttp, { endpoint: '', authSecretRef: '', timeoutSeconds: 5 })
  Object.assign(createJdbc, {
    host: '', port: 3306, databaseName: '', tableName: '', usernameSecretRef: '',
    passwordSecretRef: '', connectTimeoutSeconds: 5, queryTimeoutSeconds: 10,
  })
  createJoinPlan.value = defaultJoinPlan(createForm.moduleId)
  createError.value = ''
}

function createAnchorChanged() {
  if (createForm.sourceKind === 'MULTI_MODULE_JOIN') {
    createJoinPlan.value = defaultJoinPlan(createForm.moduleId)
  }
}

function cloneJoinPlan(plan: DataSourceMultiModuleJoin): DataSourceMultiModuleJoin {
  return {
    inputs: plan.inputs.map(item => ({ ...item })),
    edges: plan.edges.map(item => ({ ...item })),
    projections: plan.projections.map(item => ({ ...item })),
    failureMode: plan.failureMode,
    timeoutSeconds: Number(plan.timeoutSeconds),
    rowLimit: Number(plan.rowLimit),
  }
}

function validatedJoinPlan(plan: DataSourceMultiModuleJoin) {
  const value = cloneJoinPlan(plan)
  if (value.inputs.length < 2 || value.inputs.length > 8
      || value.edges.length !== value.inputs.length - 1) {
    throw new Error('多模块 Join 必须包含 2 至 8 个输入，并为每个后续输入配置一条连接边')
  }
  const aliases = new Set<string>()
  value.inputs.forEach((input) => {
    input.alias = input.alias.trim()
    if (!/^[A-Za-z][A-Za-z0-9_]{0,19}$/u.test(input.alias)
        || aliases.has(input.alias) || !input.dataSourceId || !input.dataSourceVersionId) {
      throw new Error('每个 Join 输入都需要唯一合法别名、已发布数据源和精确版本')
    }
    aliases.add(input.alias)
  })
  const introduced = new Set([value.inputs[0]!.alias])
  value.edges.forEach((edge, index) => {
    edge.leftFieldCode = edge.leftFieldCode.trim()
    edge.rightFieldCode = edge.rightFieldCode.trim()
    const expectedRight = value.inputs[index + 1]!.alias
    if (!introduced.has(edge.leftAlias) || edge.rightAlias !== expectedRight
        || !edge.leftFieldCode || !edge.rightFieldCode) {
      throw new Error('Join 连接必须按左深顺序引用已引入输入，并填写左右连接字段')
    }
    introduced.add(edge.rightAlias)
  })
  const outputCodes = new Set<string>()
  if (!value.projections.length || value.projections.length > 50) {
    throw new Error('Join 输出字段数量必须为 1 到 50 个')
  }
  value.projections.forEach((projection) => {
    projection.sourceFieldCode = projection.sourceFieldCode.trim()
    projection.fieldCode = projection.fieldCode.trim()
    if (!aliases.has(projection.sourceAlias) || !projection.sourceFieldCode
        || !projection.fieldCode.startsWith(`${projection.sourceAlias}__`)
        || outputCodes.has(projection.fieldCode)) {
      throw new Error('Join 输出必须引用有效来源字段，使用“别名__字段”命名且不能重复')
    }
    outputCodes.add(projection.fieldCode)
  })
  if (!Number.isInteger(value.timeoutSeconds) || value.timeoutSeconds < 1 || value.timeoutSeconds > 10
      || !Number.isInteger(value.rowLimit) || value.rowLimit < 1 || value.rowLimit > 100) {
    throw new Error('Join 超时必须为 1 到 10 秒，结果行上限必须为 1 到 100')
  }
  return value
}

function updateEditorJoinPlan(value: DataSourceMultiModuleJoin) {
  editor.multiModuleJoin = value
  markDirty()
}

function updateCreateJoinPlan(value: DataSourceMultiModuleJoin) {
  createJoinPlan.value = value
  createError.value = ''
}

function replaceSource(source: DataSourceSummary) {
  const index = sources.value.findIndex((item) => item.id === source.id)
  sources.value = index < 0
    ? [source, ...sources.value]
    : sources.value.map((item) => item.id === source.id ? source : item)
}

function applyDetail(detail: DataSourceDetail) {
  if (selected.value?.id === detail.id
      && selected.value.activeVersionId !== detail.activeVersionId) {
    clearPublishedHttpRows()
    clearPublishedJdbcRows()
  }
  const connection = detail.draft.httpJsonConnection
  const jdbcConnection = detail.draft.jdbcTableConnection
  const sourceKind = draftSourceKind(detail)
  selected.value = detail
  Object.assign(editor, {
    name: detail.name,
    description: detail.description ?? '',
    sourceKind,
    httpEndpoint: connection?.endpoint ?? '',
    httpAuthSecretRef: connection?.authSecretRef ?? '',
    httpTimeoutSeconds: connection?.timeoutSeconds ?? 5,
    httpFieldProjections: (detail.draft.httpFieldProjections ?? []).map(item => ({ ...item })),
    jdbcHost: jdbcConnection?.host ?? '',
    jdbcPort: jdbcConnection?.port ?? 3306,
    jdbcDatabaseName: jdbcConnection?.databaseName ?? '',
    jdbcTableName: jdbcConnection?.tableName ?? '',
    jdbcUsernameSecretRef: '',
    jdbcPasswordSecretRef: '',
    jdbcUsernameConfigured: jdbcConnection?.usernameConfigured ?? false,
    jdbcPasswordConfigured: jdbcConnection?.passwordConfigured ?? false,
    jdbcConnectTimeoutSeconds: jdbcConnection?.connectTimeoutSeconds ?? 5,
    jdbcQueryTimeoutSeconds: jdbcConnection?.queryTimeoutSeconds ?? 10,
    jdbcFieldProjections: (detail.draft.jdbcFieldProjections ?? []).map(item => ({ ...item })),
    multiModuleJoin: detail.draft.multiModuleJoin
      ? {
          ...detail.draft.multiModuleJoin,
          inputs: detail.draft.multiModuleJoin.inputs.map(item => ({ ...item })),
          edges: detail.draft.multiModuleJoin.edges.map(item => ({ ...item })),
          projections: detail.draft.multiModuleJoin.projections.map(item => ({ ...item })),
        }
      : defaultJoinPlan(),
    outputFields: sourceKind === 'NATIVE_MODULE' ? detail.draft.outputFields.map((item) => item.fieldCode) : [],
    filters: sourceKind === 'NATIVE_MODULE' ? detail.draft.fixedFilters.map((item) => fromFixedFilter(item)) : [],
    sortFieldCode: sourceKind === 'NATIVE_MODULE' ? detail.draft.defaultSort?.fieldCode ?? '' : '',
    sortDirection: detail.draft.defaultSort?.direction ?? 'ASC',
    timeFieldCode: sourceKind === 'NATIVE_MODULE' ? detail.draft.defaultTimeFieldCode ?? '' : '',
  })
  outputCandidate.value = ''
  checkResult.value = null
  connectionCheckResult.value = null
  connectionCheckError.value = ''
  clearSchemaDiscovery()
  clearDraftRowsPreview()
  editorState.value = 'clean'
  draftDirty.value = false
  replaceSource(detail)
}

function resetStatisticsTester() {
  Object.assign(statisticsDraft, createStatisticsDraft())
  statisticsVisualization.value = 'STAT_VALUE'
  statisticsResult.value = null
  statisticsError.value = ''
  statisticsLoading.value = false
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [catalog, items] = await Promise.all([
      dataSourceAdminApi.catalog(systemId.value),
      dataSourceAdminApi.list(systemId.value),
    ])
    modules.value = catalog.modules
    sources.value = items
    if (selected.value) {
      const refreshed = items.find((item) => item.id === selected.value?.id)
      if (refreshed) await selectSource(refreshed)
    } else if (items[0]) {
      await selectSource(items[0])
    }
  } catch (error) {
    errorMessage.value = requestError(error, '数据源目录加载失败')
    editorState.value = 'error'
  } finally {
    loading.value = false
  }
}

async function selectSource(source: DataSourceSummary) {
  const generation = ++selectionGeneration
  activeTab.value = 'overview'
  publishGeneration += 1
  publishing.value = false
  activePublishRequest = 0
  previewGeneration += 1
  clearPublishedHttpRows()
  clearPublishedJdbcRows()
  detailLoading.value = true
  previewLoading.value = false
  errorMessage.value = ''
  runtimeMetadata.value = null
  runtimeRows.value = null
  runtimeStatisticsCapabilities.value = null
  connectionCheckResult.value = null
  connectionCheckError.value = ''
  connectionChecking.value = false
  clearSchemaDiscovery()
  clearDraftRowsPreview()
  resetStatisticsTester()
  try {
    const [detail, sourceVersions] = await Promise.all([
      dataSourceAdminApi.detail(systemId.value, source.id),
      dataSourceAdminApi.versions(systemId.value, source.id),
    ])
    if (generation !== selectionGeneration) return
    applyDetail(detail)
    versions.value = sourceVersions
    const publishedKind = sourceVersions.find(item =>
      item.id === detail.activeVersionId || item.active)?.snapshot.sourceKind ?? draftSourceKind(detail)
    if (detail.activeVersionId) {
      if (publishedKind === 'HTTP_JSON' || publishedKind === 'JDBC_TABLE') {
        await loadStatisticsCapabilities(generation)
      } else {
        await loadPreview(1, generation)
      }
    }
  } catch (error) {
    if (generation !== selectionGeneration) return
    errorMessage.value = requestError(error, '数据源详情加载失败')
    editorState.value = 'error'
  } finally {
    if (generation === selectionGeneration) detailLoading.value = false
  }
}

function openCreate() {
  const first = modules.value.find((item) => item.available)
  Object.assign(createForm, {
    code: '', moduleId: first?.moduleId ?? '', name: '', description: '',
    sourceKind: 'NATIVE_MODULE', httpJsonConnection: null,
  })
  Object.assign(createHttp, { endpoint: '', authSecretRef: '', timeoutSeconds: 5 })
  Object.assign(createJdbc, {
    host: '', port: 3306, databaseName: '', tableName: '', usernameSecretRef: '',
    passwordSecretRef: '', connectTimeoutSeconds: 5, queryTimeoutSeconds: 10,
  })
  createJoinPlan.value = defaultJoinPlan(first?.moduleId ?? '')
  createError.value = ''
  createOpen.value = true
}

async function createSource() {
  const input: CreateDataSourceInput = {
    code: createForm.code.trim(),
    moduleId: createForm.moduleId,
    name: createForm.name.trim(),
    description: createForm.description?.trim() || null,
  }
  if (!input.code || !input.moduleId || !input.name) {
    createError.value = '编码、名称和来源模块均为必填项'
    return
  }
  try {
    if (createForm.sourceKind === 'HTTP_JSON') {
      input.sourceKind = 'HTTP_JSON'
      input.httpJsonConnection = httpConnection(
        createHttp.endpoint,
        createHttp.authSecretRef,
        createHttp.timeoutSeconds,
      )
    } else if (createForm.sourceKind === 'JDBC_TABLE') {
      input.sourceKind = 'JDBC_TABLE'
      input.jdbcTableConnection = normalizeJdbcTableConnection({ ...createJdbc })
    } else if (createForm.sourceKind === 'MULTI_MODULE_JOIN') {
      input.sourceKind = 'MULTI_MODULE_JOIN'
      input.multiModuleJoin = validatedJoinPlan(createJoinPlan.value)
    }
  } catch (error) {
    createError.value = requestError(error, '数据源连接配置无效')
    return
  }
  creating.value = true
  createError.value = ''
  try {
    const created = await dataSourceAdminApi.create(systemId.value, input)
    createOpen.value = false
    replaceSource(created)
    await selectSource(created)
  } catch (error) {
    createError.value = requestError(error, '数据源创建失败')
  } finally {
    creating.value = false
  }
}

function addOutputField() {
  if (!outputCandidate.value || editor.outputFields.length >= 50) return
  editor.outputFields.push(outputCandidate.value)
  outputCandidate.value = ''
  markDirty()
}

function moveOutput(index: number, offset: -1 | 1) {
  const target = index + offset
  if (target < 0 || target >= editor.outputFields.length) return
  const [item] = editor.outputFields.splice(index, 1)
  if (item) editor.outputFields.splice(target, 0, item)
  markDirty()
}

function removeOutput(index: number) {
  const [removed] = editor.outputFields.splice(index, 1)
  if (removed === editor.sortFieldCode) editor.sortFieldCode = ''
  if (removed === editor.timeFieldCode) editor.timeFieldCode = ''
  markDirty()
}

function projectionSourceChanged() {
  const candidate = selectedProjectionSource.value
  if (!candidate || !selectableSourceType(candidate.inferredType)) {
    projectionTargetCandidate.value = ''
    return
  }
  const targets = compatibleAnchorFields(candidate.inferredType)
  projectionTargetCandidate.value = targets.some(item => item.fieldCode === candidate.suggestedFieldCode)
    ? candidate.suggestedFieldCode ?? ''
    : targets[0]?.fieldCode ?? ''
}

function addHttpProjection() {
  const candidate = selectedProjectionSource.value
  if (!candidate || !selectableSourceType(candidate.inferredType)
      || editor.httpFieldProjections.length >= 50) return
  const target = compatibleAnchorFields(candidate.inferredType).find(
    field => field.fieldCode === projectionTargetCandidate.value,
  )
  if (!target) return
  editor.httpFieldProjections.push({
    sourceField: candidate.sourceField,
    fieldCode: target.fieldCode,
    sourceType: candidate.inferredType,
  })
  markProjectionDirty()
  resetProjectionCandidate()
}

function projectionTargets(projection: HttpJsonFieldProjection) {
  return compatibleAnchorFields(projection.sourceType, projection.fieldCode)
}

function projectionChanged() {
  markProjectionDirty()
  resetProjectionCandidate()
}

function moveHttpProjection(index: number, offset: -1 | 1) {
  const target = index + offset
  if (target < 0 || target >= editor.httpFieldProjections.length) return
  const [projection] = editor.httpFieldProjections.splice(index, 1)
  if (projection) editor.httpFieldProjections.splice(target, 0, projection)
  markProjectionDirty()
}

function removeHttpProjection(index: number) {
  editor.httpFieldProjections.splice(index, 1)
  markProjectionDirty()
  resetProjectionCandidate()
}

function jdbcProjectionSourceChanged() {
  const candidate = selectedJdbcProjectionSource.value
  if (!candidate || !selectableJdbcSourceType(candidate.inferredType)) {
    jdbcProjectionTargetCandidate.value = ''
    return
  }
  const targets = compatibleAnchorFields(candidate.inferredType)
  jdbcProjectionTargetCandidate.value = targets.some(item => item.fieldCode === candidate.suggestedFieldCode)
    ? candidate.suggestedFieldCode ?? ''
    : targets[0]?.fieldCode ?? ''
}

function addJdbcProjection() {
  const candidate = selectedJdbcProjectionSource.value
  if (!candidate || !selectableJdbcSourceType(candidate.inferredType)
      || editor.jdbcFieldProjections.length >= 50) return
  const target = compatibleAnchorFields(candidate.inferredType).find(
    field => field.fieldCode === jdbcProjectionTargetCandidate.value,
  )
  if (!target) return
  editor.jdbcFieldProjections.push({
    sourceColumn: candidate.sourceColumn,
    fieldCode: target.fieldCode,
    sourceType: candidate.inferredType,
  })
  markProjectionDirty()
  resetJdbcProjectionCandidate()
}

function jdbcProjectionTargets(projection: JdbcTableFieldProjection) {
  return compatibleAnchorFields(projection.sourceType, projection.fieldCode)
}

function jdbcProjectionChanged() {
  markProjectionDirty()
  resetJdbcProjectionCandidate()
}

function moveJdbcProjection(index: number, offset: -1 | 1) {
  const target = index + offset
  if (target < 0 || target >= editor.jdbcFieldProjections.length) return
  const [projection] = editor.jdbcFieldProjections.splice(index, 1)
  if (projection) editor.jdbcFieldProjections.splice(target, 0, projection)
  markProjectionDirty()
}

function removeJdbcProjection(index: number) {
  editor.jdbcFieldProjections.splice(index, 1)
  markProjectionDirty()
  resetJdbcProjectionCandidate()
}

function addFilter() {
  if (editor.filters.length >= 20) return
  const field = availableFields.value.find((item) => item.operators.length)
  editor.filters.push({
    key: crypto.randomUUID(),
    fieldCode: field?.fieldCode ?? '',
    operator: field?.operators[0] ?? '',
    value: '',
    secondValue: '',
  })
  markDirty()
}

function filterFieldChanged(row: DataSourceFilterDraft) {
  const field = fieldByCode(row.fieldCode)
  row.operator = field?.operators[0] ?? ''
  row.value = ''
  row.secondValue = ''
  markDirty()
}

function filterOperatorChanged(row: DataSourceFilterDraft) {
  row.value = ''
  row.secondValue = ''
  markDirty()
}

function removeFilter(index: number) {
  editor.filters.splice(index, 1)
  markDirty()
}

function validatedHttpProjections() {
  if (editor.httpFieldProjections.length > 50) {
    throw new Error('HTTP JSON 字段投影不能超过 50 个')
  }
  const sourceFields = new Set<string>()
  const fieldCodes = new Set<string>()
  return editor.httpFieldProjections.map((projection) => {
    const sourceLength = [...projection.sourceField].length
    if (sourceLength < 1 || sourceLength > 128
        || /[\p{Cc}\p{Cf}\p{Cs}]/u.test(projection.sourceField)) {
      throw new Error('HTTP JSON 来源字段无效')
    }
    if (sourceFields.has(projection.sourceField) || fieldCodes.has(projection.fieldCode)) {
      throw new Error('HTTP JSON 来源字段和锚点字段不能重复映射')
    }
    const target = fieldByCode(projection.fieldCode)
    if (!target?.available
        || !compatibleAnchorFields(projection.sourceType, projection.fieldCode)
          .some(field => field.fieldCode === projection.fieldCode)) {
      throw new Error('HTTP JSON 投影与锚点字段类型不兼容')
    }
    sourceFields.add(projection.sourceField)
    fieldCodes.add(projection.fieldCode)
    return { ...projection }
  })
}

function validatedJdbcProjections() {
  if (editor.jdbcFieldProjections.length > 50) {
    throw new Error('MySQL 字段投影不能超过 50 个')
  }
  const sourceColumns = new Set<string>()
  const fieldCodes = new Set<string>()
  return editor.jdbcFieldProjections.map((projection) => {
    if (!/^[A-Za-z_][A-Za-z0-9_]{0,63}$/u.test(projection.sourceColumn)) {
      throw new Error('MySQL 来源列必须使用安全标识符')
    }
    if (sourceColumns.has(projection.sourceColumn) || fieldCodes.has(projection.fieldCode)) {
      throw new Error('MySQL 来源列和锚点字段不能重复映射')
    }
    const target = fieldByCode(projection.fieldCode)
    if (!target?.available
        || !compatibleAnchorFields(projection.sourceType, projection.fieldCode)
          .some(field => field.fieldCode === projection.fieldCode)) {
      throw new Error('MySQL 投影与锚点字段类型不兼容')
    }
    sourceColumns.add(projection.sourceColumn)
    fieldCodes.add(projection.fieldCode)
    return { ...projection }
  })
}

function buildDraftInput(): SaveDataSourceDraftInput {
  if (!selected.value) throw new Error('请先选择数据源')
  if (!editor.name.trim()) throw new Error('数据源名称不能为空')
  if (editor.outputFields.length > 50
      || editor.sourceKind === 'NATIVE_MODULE' && editor.outputFields.length < 1) {
    throw new Error(editor.sourceKind === 'HTTP_JSON'
      ? 'HTTP JSON 草稿输出字段不能超过 50 个'
      : '输出字段数量必须为 1 到 50 个')
  }
  if (new Set(editor.outputFields).size !== editor.outputFields.length) {
    throw new Error('输出字段不能重复')
  }
  if (editor.filters.length > 20) throw new Error('固定筛选不能超过 20 条')
  const fixedFilters = editor.filters.map((row) => {
    const field = fieldByCode(row.fieldCode)
    if (!field?.available) throw new Error('固定筛选包含不可用字段')
    return toFixedFilter(row, field)
  })
  const input: SaveDataSourceDraftInput = {
    expectedVersion: selected.value.draftVersion,
    name: editor.name.trim(),
    description: editor.description.trim() || null,
    outputFields: editor.outputFields.map((fieldCode) => ({ fieldCode })),
    fixedFilters,
    defaultSort: editor.sortFieldCode
      ? { fieldCode: editor.sortFieldCode, direction: editor.sortDirection }
      : null,
    defaultTimeFieldCode: editor.timeFieldCode || null,
  }
  if (editor.sourceKind === 'HTTP_JSON') {
    input.outputFields = []
    input.fixedFilters = []
    input.defaultSort = null
    input.defaultTimeFieldCode = null
    input.sourceKind = 'HTTP_JSON'
    input.httpJsonConnection = httpConnection(
      editor.httpEndpoint,
      editor.httpAuthSecretRef,
      editor.httpTimeoutSeconds,
    )
    input.httpFieldProjections = validatedHttpProjections()
    input.jdbcTableConnection = null
    input.jdbcFieldProjections = []
    input.multiModuleJoin = null
  } else if (editor.sourceKind === 'JDBC_TABLE') {
    input.outputFields = []
    input.fixedFilters = []
    input.defaultSort = null
    input.defaultTimeFieldCode = null
    input.sourceKind = 'JDBC_TABLE'
    input.httpJsonConnection = null
    input.httpFieldProjections = []
    input.jdbcTableConnection = normalizeJdbcTableConnection({
      host: editor.jdbcHost,
      port: editor.jdbcPort,
      databaseName: editor.jdbcDatabaseName,
      tableName: editor.jdbcTableName,
      usernameSecretRef: editor.jdbcUsernameSecretRef,
      passwordSecretRef: editor.jdbcPasswordSecretRef,
      connectTimeoutSeconds: editor.jdbcConnectTimeoutSeconds,
      queryTimeoutSeconds: editor.jdbcQueryTimeoutSeconds,
    }, {
      username: editor.jdbcUsernameConfigured,
      password: editor.jdbcPasswordConfigured,
    })
    input.jdbcFieldProjections = validatedJdbcProjections()
    input.multiModuleJoin = null
  } else if (editor.sourceKind === 'MULTI_MODULE_JOIN') {
    input.outputFields = []
    input.fixedFilters = []
    input.defaultSort = null
    input.defaultTimeFieldCode = null
    input.sourceKind = 'MULTI_MODULE_JOIN'
    input.httpJsonConnection = null
    input.httpFieldProjections = []
    input.jdbcTableConnection = null
    input.jdbcFieldProjections = []
    input.multiModuleJoin = validatedJoinPlan(editor.multiModuleJoin)
  } else if (draftSourceKind(selected.value) !== 'NATIVE_MODULE') {
    input.sourceKind = 'NATIVE_MODULE'
    input.httpJsonConnection = null
    input.httpFieldProjections = []
    input.jdbcTableConnection = null
    input.jdbcFieldProjections = []
    input.multiModuleJoin = null
  }
  return input
}

async function saveDraft() {
  if (!selected.value) return false
  errorMessage.value = ''
  editorState.value = 'saving'
  try {
    const saved = await dataSourceAdminApi.saveDraft(systemId.value, selected.value.id, buildDraftInput())
    applyDetail(saved)
    return true
  } catch (error) {
    errorMessage.value = requestError(error, '草稿保存失败')
    editorState.value = 'error'
    return false
  }
}

async function checkDraft() {
  if (!selected.value) return false
  if (draftDirty.value && !await saveDraft()) return false
  errorMessage.value = ''
  try {
    const result = await dataSourceAdminApi.checkDraft(systemId.value, selected.value.id)
    checkResult.value = result
    editorState.value = blockerCount.value === 0 && result.valid !== false ? 'checked' : 'error'
    return editorState.value === 'checked'
  } catch (error) {
    errorMessage.value = requestError(error, '草稿检查失败')
    editorState.value = 'error'
    return false
  }
}

async function checkDraftConnection() {
  if (!selected.value || !isExternalDraft.value || connectionChecking.value) return
  connectionChecking.value = true
  connectionCheckResult.value = null
  connectionCheckError.value = ''
  errorMessage.value = ''
  try {
    if (draftDirty.value && !await saveDraft()) return
    const source = selected.value
    if (!source) return
    connectionCheckResult.value = await dataSourceAdminApi.checkDraftConnection(
      systemId.value,
      source.id,
      { expectedVersion: source.draftVersion },
    )
  } catch (error) {
    connectionCheckError.value = requestError(error, '连接检查失败')
  } finally {
    connectionChecking.value = false
  }
}

async function discoverDraftSchema() {
  if (!selected.value || !isExternalDraft.value || schemaDiscovering.value) return
  schemaDiscovering.value = true
  schemaDiscoveryResult.value = null
  schemaDiscoveryError.value = ''
  try {
    if (draftDirty.value && !await saveDraft()) {
      schemaDiscovering.value = false
      return
    }
    const source = selected.value
    if (!source || draftSourceKind(source) !== editor.sourceKind) {
      schemaDiscovering.value = false
      return
    }
    const sourceSelectionGeneration = selectionGeneration
    const sourceDraftVersion = source.draftVersion
    const requestGeneration = ++schemaDiscoveryGeneration
    schemaDiscovering.value = true
    try {
      const result = await dataSourceAdminApi.discoverDraftSchema(
        systemId.value,
        source.id,
        { expectedVersion: sourceDraftVersion },
      )
      if (requestGeneration !== schemaDiscoveryGeneration
          || sourceSelectionGeneration !== selectionGeneration
          || selected.value?.id !== source.id
          || selected.value.draftVersion !== sourceDraftVersion
          || result.checkedDraftVersion !== sourceDraftVersion) return
      schemaDiscoveryResult.value = result
      if (isHttpDraft.value) resetProjectionCandidate()
      if (isJdbcDraft.value) resetJdbcProjectionCandidate()
    } catch (error) {
      if (requestGeneration !== schemaDiscoveryGeneration
          || sourceSelectionGeneration !== selectionGeneration
          || selected.value?.id !== source.id) return
      schemaDiscoveryError.value = requestError(error, 'Schema 发现失败')
    } finally {
      if (requestGeneration === schemaDiscoveryGeneration) schemaDiscovering.value = false
    }
  } catch (error) {
    schemaDiscoveryError.value = requestError(error, 'Schema 发现失败')
    schemaDiscovering.value = false
  }
}

async function previewDraftRows() {
  if (!selected.value || !isExternalDraft.value || draftRowsPreviewing.value) return
  draftRowsPreviewing.value = true
  draftRowsPreviewResult.value = null
  draftRowsPreviewError.value = ''
  if (draftDirty.value && !await saveDraft()) {
    draftRowsPreviewing.value = false
    return
  }
  const source = selected.value
  if (!source || draftSourceKind(source) !== editor.sourceKind) {
    draftRowsPreviewing.value = false
    return
  }
  const sourceSelectionGeneration = selectionGeneration
  const sourceDraftVersion = source.draftVersion
  const requestGeneration = ++draftRowsPreviewGeneration
  draftRowsPreviewing.value = true
  try {
    const result = await dataSourceAdminApi.previewDraftRows(
      systemId.value,
      source.id,
      { expectedVersion: sourceDraftVersion },
    )
    if (requestGeneration !== draftRowsPreviewGeneration
        || sourceSelectionGeneration !== selectionGeneration
        || selected.value?.id !== source.id
        || selected.value?.draftVersion !== sourceDraftVersion
        || result.checkedDraftVersion !== sourceDraftVersion) return
    draftRowsPreviewResult.value = result
  } catch (error) {
    if (requestGeneration !== draftRowsPreviewGeneration
        || sourceSelectionGeneration !== selectionGeneration
        || selected.value?.id !== source.id) return
    draftRowsPreviewError.value = requestError(error, '草稿数据预览失败')
  } finally {
    if (requestGeneration === draftRowsPreviewGeneration) draftRowsPreviewing.value = false
  }
}

function draftPreviewValue(value: DataSourceDraftPreviewScalar | undefined) {
  if (value === null) return 'null'
  if (value === undefined) return '—'
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  return String(value)
}

function publishedHttpValue(value: PublishedHttpDataSourceScalar | undefined) {
  if (value === null) return 'null'
  if (value === undefined) return '—'
  if (typeof value === 'boolean') return value ? 'true' : 'false'
  return String(value)
}

async function loadPublishedHttpRows() {
  const source = selected.value
  const activeVersionId = source?.activeVersionId
  if (!source || !activeVersionId || !isPublishedHttp.value || publishedHttpRowsLoading.value) return
  const sourceSelectionGeneration = selectionGeneration
  const requestGeneration = ++publishedHttpRowsGeneration
  publishedHttpRowsLoading.value = true
  publishedHttpRowsResult.value = null
  publishedHttpRowsError.value = ''
  try {
    const result = await runtimeDataSourceApi.httpRows(systemId.value, source.code)
    if (requestGeneration !== publishedHttpRowsGeneration
        || sourceSelectionGeneration !== selectionGeneration
        || selected.value?.id !== source.id
        || selected.value?.activeVersionId !== activeVersionId
        || result.dataSourceId !== source.id
        || result.dataSourceCode !== source.code
        || result.dataSourceVersionId !== activeVersionId) return
    publishedHttpRowsResult.value = result
  } catch {
    if (requestGeneration !== publishedHttpRowsGeneration
        || sourceSelectionGeneration !== selectionGeneration
        || selected.value?.id !== source.id
        || selected.value?.activeVersionId !== activeVersionId) return
    publishedHttpRowsError.value = 'HTTP_PUBLISHED_ROWS_LOAD_FAILED'
  } finally {
    if (requestGeneration === publishedHttpRowsGeneration) publishedHttpRowsLoading.value = false
  }
}

async function loadPublishedJdbcRows() {
  const source = selected.value
  const activeVersionId = source?.activeVersionId
  if (!source || !activeVersionId || !isPublishedJdbc.value || publishedJdbcRowsLoading.value) return
  const sourceSelectionGeneration = selectionGeneration
  const requestGeneration = ++publishedJdbcRowsGeneration
  publishedJdbcRowsLoading.value = true
  publishedJdbcRowsResult.value = null
  publishedJdbcRowsError.value = ''
  try {
    const result = await runtimeDataSourceApi.jdbcRows(systemId.value, source.code)
    if (requestGeneration !== publishedJdbcRowsGeneration
        || sourceSelectionGeneration !== selectionGeneration
        || selected.value?.id !== source.id
        || selected.value?.activeVersionId !== activeVersionId
        || result.dataSourceId !== source.id
        || result.dataSourceCode !== source.code
        || result.dataSourceVersionId !== activeVersionId) return
    publishedJdbcRowsResult.value = result
  } catch {
    if (requestGeneration !== publishedJdbcRowsGeneration
        || sourceSelectionGeneration !== selectionGeneration
        || selected.value?.id !== source.id
        || selected.value?.activeVersionId !== activeVersionId) return
    publishedJdbcRowsError.value = 'JDBC_PUBLISHED_ROWS_LOAD_FAILED'
  } finally {
    if (requestGeneration === publishedJdbcRowsGeneration) publishedJdbcRowsLoading.value = false
  }
}

async function publishDraft() {
  if (!selected.value || publishing.value || !httpPublishReady.value) return
  const initialSelectionGeneration = selectionGeneration
  const initialSourceId = selected.value.id
  publishing.value = true
  const publishingHttp = isHttpDraft.value
  const publishingExternal = isExternalDraft.value
  if (publishingHttp) {
    if (draftDirty.value && !await saveDraft()) {
      publishing.value = false
      return
    }
  } else if (editorState.value !== 'checked' && !await checkDraft()) {
    publishing.value = false
    return
  }
  if (initialSelectionGeneration !== selectionGeneration
      || selected.value?.id !== initialSourceId) {
    publishing.value = false
    return
  }
  const source = selected.value
  if (!source) {
    publishing.value = false
    return
  }
  const sourceSelectionGeneration = selectionGeneration
  const sourceDraftVersion = source.draftVersion
  const requestGeneration = ++publishGeneration
  activePublishRequest = requestGeneration
  errorMessage.value = ''
  try {
    const result = await dataSourceAdminApi.publishDraft(systemId.value, source.id, {
      expectedVersion: sourceDraftVersion,
    })
    if (requestGeneration !== publishGeneration
        || sourceSelectionGeneration !== selectionGeneration
        || selected.value?.id !== source.id
        || selected.value?.draftVersion !== sourceDraftVersion
        || result.source.id !== source.id
        || result.source.draftVersion !== sourceDraftVersion) return
    const detail = result.source
    clearPublishedHttpRows()
    clearPublishedJdbcRows()
    applyDetail(detail)
    editorState.value = 'published'
    const history = await dataSourceAdminApi.versions(systemId.value, detail.id)
    if (requestGeneration !== publishGeneration
        || sourceSelectionGeneration !== selectionGeneration
        || selected.value?.id !== source.id
        || selected.value?.draftVersion !== sourceDraftVersion) return
    versions.value = history
    if (publishingExternal) await loadStatisticsCapabilities(sourceSelectionGeneration)
    else await loadPreview(1, sourceSelectionGeneration)
  } catch (error) {
    if (requestGeneration !== publishGeneration
        || sourceSelectionGeneration !== selectionGeneration
        || selected.value?.id !== source.id) return
    errorMessage.value = requestError(error, '数据源发布失败')
    editorState.value = 'error'
  } finally {
    if (activePublishRequest === requestGeneration) {
      activePublishRequest = 0
      publishing.value = false
    }
  }
}

async function restoreVersion(version: DataSourceVersion) {
  if (!selected.value || version.active || version.id === selected.value.activeVersionId) return
  if (draftDirty.value || editorState.value === 'dirty' || editorState.value === 'saving') {
    errorMessage.value = '请先保存或放弃当前本地修改，再恢复历史版本。'
    return
  }
  if (!window.confirm(`将数据源 v${version.versionNumber} 恢复为新草稿？当前运行版本不会改变。`)) return
  publishing.value = true
  errorMessage.value = ''
  try {
    const current = selected.value
    await configRecoveryApi.dataSource(systemId.value, current.id, version.versionNumber, {
      expectedVersion: current.draftVersion,
      reason: `恢复数据源 v${version.versionNumber} 为新草稿`,
    })
    await selectSource(current)
    message.success('历史版本已恢复为新草稿；请检查后显式发布。')
  } catch (error) {
    errorMessage.value = requestError(error, '数据源版本恢复失败')
    editorState.value = 'error'
  } finally {
    publishing.value = false
  }
}

async function loadPreview(page = previewPage.value, generation = selectionGeneration) {
  const requestGeneration = ++previewGeneration
  if (!selected.value?.activeVersionId) {
    runtimeMetadata.value = null
    runtimeRows.value = null
    previewLoading.value = false
    return
  }
  previewLoading.value = true
  errorMessage.value = ''
  try {
    const [metadata, rows, capabilities] = await Promise.all([
      runtimeDataSourceApi.metadata(systemId.value, selected.value.code),
      runtimeDataSourceApi.rows(systemId.value, selected.value.code, page, previewSize),
      runtimeDataSourceApi.statisticsCapabilities(systemId.value, selected.value.code),
    ])
    if (generation !== selectionGeneration || requestGeneration !== previewGeneration) return
    runtimeMetadata.value = metadata
    runtimeRows.value = rows
    runtimeStatisticsCapabilities.value = capabilities
    previewPage.value = rows.page || page
  } catch (error) {
    if (generation !== selectionGeneration || requestGeneration !== previewGeneration) return
    errorMessage.value = requestError(error, '已发布数据预览加载失败')
    editorState.value = 'error'
  } finally {
    if (generation === selectionGeneration && requestGeneration === previewGeneration) previewLoading.value = false
  }
}

async function loadStatisticsCapabilities(generation = selectionGeneration) {
  if (!selected.value?.activeVersionId) {
    runtimeStatisticsCapabilities.value = null
    return
  }
  try {
    const capabilities = await runtimeDataSourceApi.statisticsCapabilities(
      systemId.value,
      selected.value.code,
    )
    if (generation !== selectionGeneration) return
    runtimeStatisticsCapabilities.value = capabilities
  } catch (error) {
    if (generation !== selectionGeneration) return
    runtimeStatisticsCapabilities.value = null
    statisticsError.value = requestError(error, '统计能力加载失败')
  }
}

function previewValue(row: RuntimeDataSourceRow, fieldCode: string) {
  if (Array.isArray(row.values)) {
    const value = row.values.find((item) => item.fieldCode === fieldCode)
    return value?.displayValue ?? value?.value ?? '—'
  }
  return row.values[fieldCode] ?? '—'
}

function statisticsVisualizationChanged() {
  applyStatisticsVisualization(statisticsDraft, statisticsVisualization.value)
  statisticsResult.value = null
  statisticsError.value = ''
}

function statisticsAggregationChanged() {
  aggregationChanged(statisticsDraft)
  statisticsResult.value = null
  statisticsError.value = ''
}

function statisticsConfigurationChanged() {
  statisticsResult.value = null
  statisticsError.value = ''
}

async function runStatistics() {
  if (!selected.value?.activeVersionId || statisticsLoading.value) return
  const issue = statisticsIssues.value[0]
  if (issue) {
    statisticsError.value = `${issue.code}：${issue.message}`
    statisticsResult.value = null
    return
  }
  statisticsLoading.value = true
  statisticsError.value = ''
  try {
    statisticsResult.value = await runtimeDataSourceApi.statistics(
      systemId.value,
      selected.value.code,
      buildStatisticsRequest(statisticsDraft),
    )
  } catch (error) {
    statisticsResult.value = null
    statisticsError.value = requestError(error, '真实统计计算失败')
  } finally {
    statisticsLoading.value = false
  }
}

function formatTime(value?: string) {
  if (!value) return '—'
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

onMounted(load)
</script>

<template>
  <section class="admin-page data-sources-page">
    <AdminPageHeader title="数据源" :description="`统一管理 ${sources.length} 个 Native、HTTP JSON 与只读 MySQL 表数据源。`">
      <template v-if="!isMobile" #actions>
        <a-button aria-label="刷新数据源" :loading="loading" :disabled="loading" @click="load"><RefreshCw :size="16" />刷新</a-button>
        <a-button class="data-source-create" type="primary" @click="openCreate"><Plus :size="16" />新建数据源</a-button>
      </template>
    </AdminPageHeader>

    <a-alert v-if="errorMessage" class="admin-alert data-source-error" type="error" show-icon role="alert" aria-live="assertive" :message="errorMessage" />

    <div v-if="isMobile" class="data-source-mobile-gate">
      <AdminMobileNotice />
      <a-empty description="数据源连接、字段投影、预览与版本编辑器为桌面工作区，请在宽度大于 720px 的窗口中管理。" />
    </div>
    <div v-else class="data-source-workspace" :aria-busy="loading || detailLoading">
      <aside class="source-list-panel">
        <header class="source-list-heading"><div><strong>数据源目录</strong><span>{{ filteredSources.length }} / {{ sources.length }}</span></div></header>
        <div class="source-list-tools">
          <input v-model="sourceSearch" class="source-search" type="search" placeholder="搜索名称、编码或目标" aria-label="搜索数据源">
          <select v-model="sourceKindFilter" class="source-kind-filter" aria-label="按数据源类型筛选">
            <option value="ALL">全部类型</option><option value="NATIVE_MODULE">原生模块</option><option value="HTTP_JSON">HTTP JSON</option><option value="JDBC_TABLE">只读 MySQL 表</option><option value="MULTI_MODULE_JOIN">多模块 Join</option>
          </select>
          <select v-model="sourcePublishFilter" class="source-publish-filter" aria-label="按发布状态筛选">
            <option value="ALL">全部状态</option><option value="PUBLISHED">已发布</option><option value="DRAFT">未发布</option>
          </select>
        </div>
        <div class="source-list-table-wrap">
          <table class="source-list-table">
            <thead class="source-list-head"><tr><th>名称</th><th>类型</th><th>模块/目标</th><th>版本</th><th>状态</th><th>操作</th></tr></thead>
            <tbody>
              <tr
                v-for="source in filteredSources"
                :key="source.id"
                class="source-list-item source-list-row"
                :class="{ active: source.id === selected?.id }"
                tabindex="0"
                @click="selectSource(source)"
                @keydown.enter="selectSource(source)"
              >
                <td><strong>{{ source.name }}</strong><small>{{ source.code }}</small></td>
                <td><a-tag>{{ sourceKindLabel(summarySourceKind(source)) }}</a-tag></td>
                <td><span>{{ sourceTarget(source) }}</span></td>
                <td>{{ source.activeVersionId ? `v${source.activeVersionNumber}` : '—' }}</td>
                <td><a-tag :color="source.activeVersionId ? 'green' : 'default'">{{ source.activeVersionId ? '已发布' : '草稿' }}</a-tag></td>
                <td><button class="source-row-action" type="button" @click.stop="selectSource(source)">配置</button></td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="loading" class="source-list-loading">正在加载数据源…</div>
        <a-empty v-else-if="!filteredSources.length" :description="sources.length ? '没有符合筛选条件的数据源' : '暂无数据源'" />
      </aside>

      <main class="source-editor-panel" :aria-busy="detailLoading">
        <div v-if="!selected" class="editor-empty"><Database :size="34" /><p>创建或选择一个数据源开始配置。</p></div>
        <template v-else>
          <header class="editor-toolbar">
            <div>
              <div class="editor-title-line"><h2>{{ selected.name }}</h2><code>{{ selected.code }}</code></div>
              <p class="editor-context"><a-tag>{{ sourceKindLabel(editor.sourceKind) }}</a-tag><span>锚点模块：{{ selectedModule?.moduleName ?? selected.moduleId }}</span><span>草稿代次 {{ selected.draftVersion }}</span><a-tag :color="selected.activeVersionId ? 'green' : 'default'">{{ selected.activeVersionId ? `已发布 v${selected.activeVersionNumber}` : '尚未发布' }}</a-tag></p>
            </div>
            <div class="editor-actions">
              <span class="editor-state" :class="`state-${editorState}`" :data-state="editorState">{{ stateText[editorState] }}</span>
              <a-button class="draft-save" :loading="editorState === 'saving'" :disabled="publishing || editorState !== 'dirty' && editorState !== 'error'" @click="saveDraft"><Save :size="15" />保存草稿</a-button>
              <a-button class="draft-check" :disabled="publishing || editorState === 'saving'" @click="checkDraft"><CheckCircle2 :size="15" />检查</a-button>
              <a-button class="draft-publish" type="primary" :loading="publishing" :disabled="editorState === 'saving' || publishing || !httpPublishReady" @click="publishDraft"><Send :size="15" />发布</a-button>
            </div>
          </header>

          <nav class="data-source-tabs" aria-label="数据源详情">
            <button type="button" :class="{ active: activeTab === 'overview' }" @click="activeTab = 'overview'">概览</button>
            <button type="button" :class="{ active: activeTab === 'connection' }" @click="activeTab = 'connection'">连接与字段</button>
            <button type="button" :class="{ active: activeTab === 'preview' }" @click="activeTab = 'preview'">预览与运行</button>
            <button type="button" :class="{ active: activeTab === 'versions' }" @click="activeTab = 'versions'">版本记录</button>
            <button type="button" :class="{ active: activeTab === 'statistics' }" @click="activeTab = 'statistics'">统计</button>
          </nav>

          <div class="editor-content">
            <section v-show="activeTab === 'overview'" class="editor-card source-basics">
              <div class="card-heading"><div><strong>基础信息</strong><p>数据源编码和锚点模块创建后不可更改；来源类型属于草稿配置。</p></div></div>
              <div class="form-grid">
                <label>名称<input v-model="editor.name" maxlength="200" @input="markDirty"></label>
                <label>编码<input :value="selected.code" disabled></label>
                <label>来源类型<select v-model="editor.sourceKind" class="data-source-kind" @change="sourceKindChanged"><option value="NATIVE_MODULE">原生模块</option><option value="HTTP_JSON">HTTP JSON</option><option value="JDBC_TABLE">只读 MySQL 表</option><option value="MULTI_MODULE_JOIN">多模块 Join</option></select></label>
                <label>锚点模块<select :value="selected.moduleId" disabled><option :value="selected.moduleId">{{ selectedModule?.moduleName ?? selected.moduleId }}</option></select></label>
                <label class="span-two">描述<textarea v-model="editor.description" rows="2" maxlength="2000" @input="markDirty" /></label>
              </div>
            </section>

            <section v-show="activeTab === 'overview'" class="editor-card source-status-overview">
              <div class="card-heading"><div><strong>当前状态</strong><p>草稿修改、校验和发布状态在详情头持续可见。</p></div></div>
              <dl class="overview-grid">
                <div><dt>草稿状态</dt><dd>{{ stateText[editorState] }}</dd></div>
                <div><dt>草稿版本</dt><dd>{{ selected.draftVersion }}</dd></div>
                <div><dt>活动版本</dt><dd>{{ selected.activeVersionNumber ? `v${selected.activeVersionNumber}` : '尚未发布' }}</dd></div>
                <div><dt>字段映射</dt><dd>{{ isHttpDraft ? editor.httpFieldProjections.length : isJdbcDraft ? editor.jdbcFieldProjections.length : isJoinDraft ? editor.multiModuleJoin.projections.length : editor.outputFields.length }}</dd></div>
              </dl>
            </section>

            <section v-if="isHttpDraft" v-show="activeTab === 'connection'" class="editor-card http-json-connection">
              <div class="card-heading">
                <div><strong>HTTP JSON 连接</strong><p>连接检查固定发送 POST JSON <code>{ "page": 1, "size": 1 }</code>；不能自定义方法、请求头或正文。</p></div>
                <a-button
                  class="draft-connection-check"
                  :loading="connectionChecking"
                  :disabled="publishing || connectionChecking || editorState === 'saving'"
                  @click="checkDraftConnection"
                ><Play :size="15" />检查连接</a-button>
              </div>
              <a-alert
                type="info"
                show-icon
                message="仅提交 SecretRef"
                description="不要填写密码、Token 或其他明文凭据；目标、凭据作用域和响应契约最终由服务器校验。"
              />
              <div class="form-grid three-columns">
                <label class="span-two">HTTPS Endpoint<input v-model="editor.httpEndpoint" class="http-json-endpoint" maxlength="1024" placeholder="https://api.example.com/v1/rows" @input="markConnectionDirty"></label>
                <label>超时（秒）<input v-model.number="editor.httpTimeoutSeconds" class="http-json-timeout" type="number" min="1" max="10" step="1" @input="markConnectionDirty"></label>
                <label class="span-two">认证 SecretRef<input v-model="editor.httpAuthSecretRef" class="http-json-secret-ref" maxlength="512" autocomplete="off" placeholder="env://EXAMINE_DS_S{systemId}_T{tenantId}_API_V1" @input="markConnectionDirty"></label>
              </div>
              <a-alert
                class="http-publish-blocker"
                type="info"
                show-icon
                message="发布后进入统一运行时"
                description="草稿先执行安全预检；发布为不可变版本后，可用于运行预览、统计、仪表盘、KPI 与报表。"
              />
              <a-alert
                v-if="connectionCheckError"
                class="connection-check-error"
                type="error"
                show-icon
                :message="connectionCheckError"
              />
              <dl
                v-if="connectionCheckResult"
                class="connection-check-result"
                :data-code="connectionCheckResult.code"
                :data-reachable="connectionCheckResult.reachable"
                :data-contract-valid="connectionCheckResult.contractValid"
              >
                <div><dt>结果</dt><dd><strong>{{ connectionCheckResult.code }}</strong> · {{ connectionCheckResult.message }}</dd></div>
                <div><dt>可达</dt><dd>{{ connectionCheckResult.reachable ? '是' : '否' }}</dd></div>
                <div><dt>契约有效</dt><dd>{{ connectionCheckResult.contractValid ? '是' : '否' }}</dd></div>
                <div><dt>HTTP 状态</dt><dd>{{ connectionCheckResult.httpStatus ?? '—' }}</dd></div>
                <div><dt>耗时</dt><dd>{{ connectionCheckResult.durationMillis }} ms</dd></div>
              </dl>
            </section>

            <section v-if="isHttpDraft" v-show="activeTab === 'connection'" class="editor-card http-field-projection">
              <div class="card-heading">
                <div><strong>HTTP Schema 与字段投影</strong><p>显式发现顶层字段，再映射到类型兼容且可用的锚点模块字段；不会展示或保存样本值。</p></div>
                <div class="http-projection-actions">
                  <a-button
                    class="draft-schema-discover"
                    :loading="schemaDiscovering"
                    :disabled="publishing || schemaDiscovering || draftRowsPreviewing || editorState === 'saving'"
                    @click="discoverDraftSchema"
                  ><RefreshCw :size="15" />发现 Schema</a-button>
                </div>
              </div>
              <a-alert
                v-if="schemaDiscoveryError"
                class="schema-discovery-error"
                type="error"
                show-icon
                :message="schemaDiscoveryError"
              />
              <div
                v-if="schemaDiscoveryResult"
                class="schema-discovery-result"
                :data-code="schemaDiscoveryResult.code"
                :data-draft-version="schemaDiscoveryResult.checkedDraftVersion"
              >
                <dl class="connection-check-result">
                  <div><dt>结果</dt><dd><strong>{{ schemaDiscoveryResult.code }}</strong> · {{ schemaDiscoveryResult.message }}</dd></div>
                  <div><dt>可达</dt><dd>{{ schemaDiscoveryResult.reachable ? '是' : '否' }}</dd></div>
                  <div><dt>契约有效</dt><dd>{{ schemaDiscoveryResult.contractValid ? '是' : '否' }}</dd></div>
                  <div><dt>HTTP 状态</dt><dd>{{ schemaDiscoveryResult.httpStatus ?? '—' }}</dd></div>
                  <div><dt>耗时</dt><dd>{{ schemaDiscoveryResult.durationMillis }} ms</dd></div>
                </dl>
                <ul class="schema-candidate-list">
                  <li
                    v-for="field in schemaDiscoveryResult.fields"
                    :key="discoveredSourceName(field)"
                    class="schema-candidate"
                    :data-selectable="field.selectable"
                  >
                    <code>{{ discoveredSourceName(field) }}</code>
                    <a-tag>{{ field.inferredType }}</a-tag>
                    <span>{{ field.nullable ? '可空' : '非空' }}</span>
                    <a-tag v-if="!field.selectable" color="orange">{{ field.issueCode ?? 'SCHEMA_REVIEW_REQUIRED' }}</a-tag>
                  </li>
                </ul>
                <a-empty v-if="!schemaDiscoveryResult.fields.length" description="未发现可展示的 Schema 字段" />
              </div>
              <div v-if="schemaDiscoveryResult" class="field-picker http-projection-picker">
                <select v-model="projectionSourceCandidate" class="http-projection-source" aria-label="HTTP 来源字段" @change="projectionSourceChanged">
                  <option value="">选择可映射的来源字段</option>
                  <option v-for="field in projectionSourceOptions" :key="field.sourceField" :value="field.sourceField">{{ field.sourceField }}（{{ field.inferredType }}）</option>
                </select>
                <select v-model="projectionTargetCandidate" class="http-projection-field-code" aria-label="锚点模块字段">
                  <option value="">选择兼容的锚点字段</option>
                  <option v-for="field in projectionTargetOptions" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}（{{ field.fieldCode }} · {{ field.type }}）</option>
                </select>
                <a-button class="http-projection-add" :disabled="!projectionSourceCandidate || !projectionTargetCandidate || editor.httpFieldProjections.length >= 50" @click="addHttpProjection"><Plus :size="15" />添加映射</a-button>
              </div>
              <ol class="http-projection-list">
                <li v-for="(projection, index) in editor.httpFieldProjections" :key="projection.sourceField" class="http-projection-row">
                  <span class="field-order">{{ index + 1 }}</span>
                  <span class="field-summary"><strong class="http-projection-source-field">{{ projection.sourceField }}</strong><small class="http-projection-source-type">{{ projection.sourceType }}</small></span>
                  <span>→</span>
                  <select v-model="projection.fieldCode" class="http-projection-target" :aria-label="`投影锚点字段 ${index + 1}`" @change="projectionChanged">
                    <option v-for="field in projectionTargets(projection)" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}（{{ field.fieldCode }} · {{ field.type }}）</option>
                  </select>
                  <div class="row-actions">
                    <button type="button" class="http-projection-up" title="上移" :disabled="index === 0" @click="moveHttpProjection(index, -1)"><ChevronUp :size="15" /></button>
                    <button type="button" class="http-projection-down" title="下移" :disabled="index === editor.httpFieldProjections.length - 1" @click="moveHttpProjection(index, 1)"><ChevronDown :size="15" /></button>
                    <button type="button" class="http-projection-remove" title="移除映射" @click="removeHttpProjection(index)"><X :size="15" /></button>
                  </div>
                </li>
              </ol>
              <a-empty v-if="!editor.httpFieldProjections.length" description="可先保存空投影；完整草稿至少需要一个字段映射" />
            </section>

            <section v-if="isJdbcDraft" v-show="activeTab === 'connection'" class="editor-card jdbc-table-connection">
              <div class="card-heading">
                <div><strong>只读 MySQL 表连接</strong><p>仅配置结构化目标；服务器只允许命中 allowlist 的 MySQL 8 主机，并以只读连接执行有界操作。</p></div>
                <a-button class="draft-connection-check" :loading="connectionChecking" :disabled="publishing || connectionChecking || editorState === 'saving'" @click="checkDraftConnection"><Play :size="15" />检查连接</a-button>
              </div>
              <a-alert class="jdbc-security-boundary" type="warning" show-icon message="禁止 JDBC URL、驱动参数和任意 SQL；连接检查只执行一次有界 SELECT 1。" />
              <a-alert class="jdbc-secret-boundary" type="info" show-icon message="SecretRef 固定格式：env://EXAMINE_DS_S<systemId>_T<tenantId>_<ALIAS>_V<version>；已配置项留空即保留，页面不会自动构造或回显引用。" />
              <div class="form-grid three-columns jdbc-connection-grid">
                <label class="span-two">MySQL 主机<input v-model="editor.jdbcHost" class="jdbc-host" maxlength="253" placeholder="mysql.example.internal" @input="markConnectionDirty"></label>
                <label>端口<input v-model.number="editor.jdbcPort" class="jdbc-port" type="number" min="1" max="65535" step="1" @input="markConnectionDirty"></label>
                <label>数据库<input v-model="editor.jdbcDatabaseName" class="jdbc-database-name" maxlength="64" @input="markConnectionDirty"></label>
                <label>数据表<input v-model="editor.jdbcTableName" class="jdbc-table-name" maxlength="64" @input="markConnectionDirty"></label>
                <label>连接超时（秒）<input v-model.number="editor.jdbcConnectTimeoutSeconds" class="jdbc-connect-timeout" type="number" min="1" max="10" step="1" @input="markConnectionDirty"></label>
                <label>查询超时（秒）<input v-model.number="editor.jdbcQueryTimeoutSeconds" class="jdbc-query-timeout" type="number" min="1" max="10" step="1" @input="markConnectionDirty"></label>
                <label class="span-two">用户名 SecretRef <a-tag :color="editor.jdbcUsernameConfigured ? 'green' : 'default'">{{ editor.jdbcUsernameConfigured ? '已配置，留空保留' : '未配置' }}</a-tag><input v-model="editor.jdbcUsernameSecretRef" class="jdbc-username-secret-ref" maxlength="512" autocomplete="off" placeholder="env://EXAMINE_DS_S{systemId}_T{tenantId}_MYSQL_USER_V1" @input="markConnectionDirty"></label>
                <label class="span-two">密码 SecretRef <a-tag :color="editor.jdbcPasswordConfigured ? 'green' : 'default'">{{ editor.jdbcPasswordConfigured ? '已配置，留空保留' : '未配置' }}</a-tag><input v-model="editor.jdbcPasswordSecretRef" class="jdbc-password-secret-ref" maxlength="512" autocomplete="off" placeholder="env://EXAMINE_DS_S{systemId}_T{tenantId}_MYSQL_PASSWORD_V1" @input="markConnectionDirty"></label>
              </div>
              <a-alert v-if="connectionCheckError" class="connection-check-error" type="error" show-icon :message="connectionCheckError" />
              <dl v-if="connectionCheckResult" class="connection-check-result" :data-code="connectionCheckResult.code" :data-reachable="connectionCheckResult.reachable" :data-contract-valid="connectionCheckResult.contractValid">
                <div><dt>结果</dt><dd><strong>{{ connectionCheckResult.code }}</strong> · {{ connectionCheckResult.message }}</dd></div>
                <div><dt>可达</dt><dd>{{ connectionCheckResult.reachable ? '是' : '否' }}</dd></div>
                <div><dt>契约有效</dt><dd>{{ connectionCheckResult.contractValid ? '是' : '否' }}</dd></div>
                <div><dt>耗时</dt><dd>{{ connectionCheckResult.durationMillis }} ms</dd></div>
              </dl>
            </section>

            <section v-if="isJdbcDraft" v-show="activeTab === 'connection'" class="editor-card jdbc-field-projection">
              <div class="card-heading"><div><strong>MySQL Schema 与字段投影</strong><p>只读取精确目标表元数据，不读取样例值；最多映射 50 个受支持字段。</p></div><a-button class="draft-schema-discover" :loading="schemaDiscovering" :disabled="publishing || schemaDiscovering || draftRowsPreviewing || editorState === 'saving'" @click="discoverDraftSchema"><RefreshCw :size="15" />发现 Schema</a-button></div>
              <a-alert v-if="schemaDiscoveryError" class="schema-discovery-error" type="error" show-icon :message="schemaDiscoveryError" />
              <div v-if="schemaDiscoveryResult" class="schema-discovery-result" :data-code="schemaDiscoveryResult.code" :data-draft-version="schemaDiscoveryResult.checkedDraftVersion">
                <dl class="connection-check-result">
                  <div><dt>结果</dt><dd><strong>{{ schemaDiscoveryResult.code }}</strong> · {{ schemaDiscoveryResult.message }}</dd></div>
                  <div><dt>字段</dt><dd>{{ schemaDiscoveryResult.fields.length }}</dd></div>
                  <div><dt>耗时</dt><dd>{{ schemaDiscoveryResult.durationMillis }} ms</dd></div>
                </dl>
                <ul class="schema-candidate-list"><li v-for="field in schemaDiscoveryResult.fields" :key="discoveredSourceName(field)" class="schema-candidate" :data-selectable="field.selectable"><code>{{ discoveredSourceName(field) }}</code><a-tag>{{ field.inferredType }}</a-tag><span>{{ field.nullable ? '可空' : '非空' }}</span><a-tag v-if="!field.selectable" color="orange">{{ field.issueCode ?? 'SCHEMA_REVIEW_REQUIRED' }}</a-tag></li></ul>
                <a-empty v-if="!schemaDiscoveryResult.fields.length" description="目标表没有可映射字段" />
              </div>
              <div v-if="schemaDiscoveryResult" class="field-picker jdbc-projection-picker">
                <select v-model="jdbcProjectionSourceCandidate" class="jdbc-projection-source" aria-label="MySQL 来源列" @change="jdbcProjectionSourceChanged"><option value="">选择可映射的来源列</option><option v-for="field in jdbcProjectionSourceOptions" :key="field.sourceColumn" :value="field.sourceColumn">{{ field.sourceColumn }}（{{ field.inferredType }}）</option></select>
                <select v-model="jdbcProjectionTargetCandidate" class="jdbc-projection-field-code" aria-label="MySQL 锚点模块字段"><option value="">选择兼容的锚点字段</option><option v-for="field in jdbcProjectionTargetOptions" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}（{{ field.fieldCode }} · {{ field.type }}）</option></select>
                <a-button class="jdbc-projection-add" :disabled="!jdbcProjectionSourceCandidate || !jdbcProjectionTargetCandidate || editor.jdbcFieldProjections.length >= 50" @click="addJdbcProjection"><Plus :size="15" />添加映射</a-button>
              </div>
              <ol class="jdbc-projection-list">
                <li v-for="(projection, index) in editor.jdbcFieldProjections" :key="projection.sourceColumn" class="jdbc-projection-row">
                  <span class="field-order">{{ index + 1 }}</span><span class="field-summary"><strong class="jdbc-projection-source-column">{{ projection.sourceColumn }}</strong><small>{{ projection.sourceType }}</small></span><span>→</span>
                  <select v-model="projection.fieldCode" class="jdbc-projection-target" :aria-label="`MySQL 投影锚点字段 ${index + 1}`" @change="jdbcProjectionChanged"><option v-for="field in jdbcProjectionTargets(projection)" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}（{{ field.fieldCode }} · {{ field.type }}）</option></select>
                  <div class="row-actions"><button type="button" class="jdbc-projection-up" title="上移" :disabled="index === 0" @click="moveJdbcProjection(index, -1)"><ChevronUp :size="15" /></button><button type="button" class="jdbc-projection-down" title="下移" :disabled="index === editor.jdbcFieldProjections.length - 1" @click="moveJdbcProjection(index, 1)"><ChevronDown :size="15" /></button><button type="button" class="jdbc-projection-remove" title="移除映射" @click="removeJdbcProjection(index)"><X :size="15" /></button></div>
                </li>
              </ol>
              <a-empty v-if="!editor.jdbcFieldProjections.length" description="先发现 Schema，再将目标表列映射到锚点模块字段" />
            </section>

            <section v-if="isJoinDraft" v-show="activeTab === 'connection'" class="editor-card multi-module-join-card">
              <MultiModuleJoinEditor
                :system-id="systemId"
                :current-source-id="selected.id"
                :anchor-module-id="selected.moduleId"
                :sources="sources"
                :model-value="editor.multiModuleJoin"
                @update:model-value="updateEditorJoinPlan"
              />
            </section>

            <section v-if="isNativeDraft" v-show="activeTab === 'connection'" class="editor-card output-editor">
              <div class="card-heading"><div><strong>输出字段</strong><p>{{ isHttpDraft ? '连接检查阶段允许暂不选择；后续运行批次再冻结 HTTP 字段投影。' : '按以下顺序输出 1 至 50 个已发布且可用的字段。' }}</p></div><span>{{ editor.outputFields.length }}/50</span></div>
              <div class="field-picker">
                <select v-model="outputCandidate" aria-label="待添加输出字段">
                  <option value="">选择字段</option>
                  <option v-for="field in outputOptions" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}（{{ field.fieldCode }}）</option>
                </select>
                <a-button class="output-add" :disabled="!outputCandidate || editor.outputFields.length >= 50" @click="addOutputField"><Plus :size="15" />添加</a-button>
              </div>
              <ol class="ordered-fields">
                <li v-for="(code, index) in editor.outputFields" :key="code">
                  <span class="field-order">{{ index + 1 }}</span>
                  <span class="field-summary"><strong>{{ fieldByCode(code)?.fieldName ?? code }}</strong><small>{{ code }} · {{ fieldByCode(code)?.type }}</small></span>
                  <a-tag v-if="fieldByCode(code)?.sortable">可排序</a-tag><a-tag v-if="fieldByCode(code)?.temporal" color="blue">时间</a-tag>
                  <div class="row-actions">
                    <button type="button" title="上移" :disabled="index === 0" @click="moveOutput(index, -1)"><ChevronUp :size="15" /></button>
                    <button type="button" title="下移" :disabled="index === editor.outputFields.length - 1" @click="moveOutput(index, 1)"><ChevronDown :size="15" /></button>
                    <button type="button" title="移除" @click="removeOutput(index)"><X :size="15" /></button>
                  </div>
                </li>
              </ol>
              <a-empty v-if="!editor.outputFields.length" :description="isHttpDraft ? 'HTTP JSON 草稿可在本批保持空输出字段' : '至少添加一个输出字段'" />
            </section>

            <section v-if="isNativeDraft" v-show="activeTab === 'connection'" class="editor-card filter-editor">
              <div class="card-heading"><div><strong>固定筛选</strong><p>筛选值按字段类型输入，运行时调用方不能覆盖。</p></div><a-button class="filter-add" :disabled="editor.filters.length >= 20" @click="addFilter"><Plus :size="15" />添加条件</a-button></div>
              <div v-for="(row, index) in editor.filters" :key="row.key" class="filter-row">
                <span class="condition-index">{{ index + 1 }}</span>
                <label>字段<select v-model="row.fieldCode" :aria-label="`筛选字段 ${index + 1}`" @change="filterFieldChanged(row)"><option v-for="field in availableFields.filter((item) => item.operators.length)" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}</option></select></label>
                <label>操作符<select v-model="row.operator" :aria-label="`筛选操作符 ${index + 1}`" @change="filterOperatorChanged(row)"><option v-for="operator in fieldByCode(row.fieldCode)?.operators ?? []" :key="operator" :value="operator">{{ operator }}</option></select></label>
                <label v-if="!isNoValueOperator(row.operator)">值
                  <select v-if="isBooleanField(fieldByCode(row.fieldCode))" v-model="row.value" :aria-label="`筛选值 ${index + 1}`" @change="markDirty"><option value="">请选择</option><option value="true">是</option><option value="false">否</option></select>
                  <select v-else-if="fieldByCode(row.fieldCode)?.options?.length && !isCollectionOperator(row.operator)" v-model="row.value" :aria-label="`筛选值 ${index + 1}`" @change="markDirty"><option value="">请选择</option><option v-for="option in fieldByCode(row.fieldCode)?.options" :key="option.value" :value="option.value">{{ option.label }}</option></select>
                  <input v-else v-model="row.value" :type="inputTypeForField(fieldByCode(row.fieldCode))" :placeholder="isCollectionOperator(row.operator) ? '多个值以逗号分隔' : ''" :aria-label="`筛选值 ${index + 1}`" @input="markDirty">
                </label>
                <label v-if="isRangeOperator(row.operator)">结束值<input v-model="row.secondValue" :type="inputTypeForField(fieldByCode(row.fieldCode))" :aria-label="`筛选结束值 ${index + 1}`" @input="markDirty"></label>
                <button class="filter-remove" type="button" title="删除条件" @click="removeFilter(index)"><Trash2 :size="16" /></button>
              </div>
              <a-empty v-if="!editor.filters.length" description="无固定筛选，将读取权限范围内的全部当前记录" />
            </section>

            <section v-if="isNativeDraft" v-show="activeTab === 'connection'" class="editor-card query-defaults">
              <div class="card-heading"><div><strong>排序与时间字段</strong><p>仅能选择已经输出、且具备对应能力的字段。</p></div></div>
              <div class="form-grid three-columns">
                <label>默认排序字段<select v-model="editor.sortFieldCode" @change="markDirty"><option value="">不指定</option><option v-for="field in sortableFields" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}</option></select></label>
                <label>排序方向<select v-model="editor.sortDirection" :disabled="!editor.sortFieldCode" @change="markDirty"><option value="ASC">升序</option><option value="DESC">降序</option></select></label>
                <label>默认时间字段<select v-model="editor.timeFieldCode" @change="markDirty"><option value="">不指定</option><option v-for="field in temporalFields" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}</option></select></label>
              </div>
            </section>

            <section v-if="checkResult" v-show="activeTab === 'overview'" class="editor-card check-result" :class="{ blocked: blockerCount > 0 }">
              <div class="card-heading"><div><strong>草稿检查</strong><p>{{ blockerCount }} 个阻断，{{ warningCount }} 个警告</p></div><a-tag :color="blockerCount ? 'red' : 'green'">{{ blockerCount ? '不可发布' : '可以发布' }}</a-tag></div>
              <ul v-if="checkResult.issues.length"><li v-for="issue in checkResult.issues" :key="`${issue.code}:${issue.path}`"><a-tag :color="issue.severity === 'BLOCKER' ? 'red' : 'orange'">{{ issue.severity }}</a-tag><strong>{{ issue.code }}</strong><code>{{ issue.path }}</code><span>{{ issue.message }}</span></li></ul>
            </section>

            <section v-if="isExternalDraft" v-show="activeTab === 'preview'" class="editor-card external-draft-preview">
              <div class="card-heading">
                <div><strong>草稿数据预览</strong><p>显式读取已保存草稿的固定第一页；不支持自定义分页、查询、过滤、排序或后台刷新。</p></div>
                <a-button class="draft-rows-preview" :loading="draftRowsPreviewing" :disabled="publishing || draftRowsPreviewing || schemaDiscovering || editorState === 'saving'" @click="previewDraftRows"><Eye :size="15" />预览草稿数据</a-button>
              </div>
              <a-alert v-if="draftRowsPreviewError" class="draft-preview-error" type="error" show-icon :message="draftRowsPreviewError" />
              <div v-if="draftRowsPreviewResult" class="draft-preview-result" :data-code="draftRowsPreviewResult.code" :data-draft-version="draftRowsPreviewResult.checkedDraftVersion">
                <dl class="connection-check-result">
                  <div><dt>结果</dt><dd><strong>{{ draftRowsPreviewResult.code }}</strong> · {{ draftRowsPreviewResult.message }}</dd></div>
                  <div><dt>可达</dt><dd>{{ draftRowsPreviewResult.reachable ? '是' : '否' }}</dd></div>
                  <div><dt>契约有效</dt><dd>{{ draftRowsPreviewResult.contractValid ? '是' : '否' }}</dd></div>
                  <div><dt>耗时</dt><dd>{{ draftRowsPreviewResult.durationMillis }} ms</dd></div>
                </dl>
                <ul class="draft-preview-fields"><li v-for="field in draftRowsPreviewResult.fields" :key="field.fieldCode" class="draft-preview-field"><strong>{{ fieldByCode(field.fieldCode)?.fieldName ?? field.fieldCode }}</strong><code>{{ field.fieldCode }}</code><a-tag>{{ field.sourceType }}</a-tag></li></ul>
                <div class="preview-table-wrap"><table class="preview-table draft-preview-rows"><thead><tr><th>行</th><th v-for="field in draftRowsPreviewResult.fields" :key="field.fieldCode">{{ fieldByCode(field.fieldCode)?.fieldName ?? field.fieldCode }}<small>{{ field.fieldCode }} · {{ field.sourceType }}</small></th></tr></thead><tbody><tr v-for="row in draftRowsPreviewResult.rows" :key="row.rowIndex" class="draft-preview-row" :data-row-index="row.rowIndex"><td>{{ row.rowIndex }}</td><td v-for="field in draftRowsPreviewResult.fields" :key="field.fieldCode">{{ draftPreviewValue(row.values[field.fieldCode]) }}</td></tr></tbody></table></div>
                <a-empty v-if="!draftRowsPreviewResult.rows.length" description="第一页没有返回数据" />
              </div>
              <a-empty v-else-if="!draftRowsPreviewing && !draftRowsPreviewError" description="点击后才会读取外部草稿数据" />
            </section>

            <section v-if="isPublishedHttp" v-show="activeTab === 'preview'" class="editor-card published-http-rows">
              <div class="card-heading">
                <div><strong>已发布 HTTP 数据</strong><p>仅在明确操作后读取当前活动版本的固定第一页；不会自动刷新或用于原生记录能力。</p></div>
                <a-button
                  class="published-http-rows-load"
                  :loading="publishedHttpRowsLoading"
                  :disabled="publishedHttpRowsLoading || !selected.activeVersionId"
                  @click="loadPublishedHttpRows"
                ><Eye :size="15" />加载已发布数据</a-button>
              </div>
              <a-alert
                v-if="publishedHttpRowsError"
                class="published-http-rows-error"
                type="error"
                show-icon
                :message="publishedHttpRowsError"
              />
              <div v-if="publishedHttpRowsResult" class="published-http-rows-result preview-table-wrap">
                <table class="preview-table">
                  <thead><tr><th>行</th><th v-for="field in publishedHttpRowsResult.fields" :key="field.fieldCode" class="published-http-field"><code>{{ field.fieldCode }}</code><small>{{ field.sourceType }}</small></th></tr></thead>
                  <tbody><tr v-for="row in publishedHttpRowsResult.rows" :key="row.rowIndex" class="published-http-row" :data-row-index="row.rowIndex"><td>{{ row.rowIndex }}</td><td v-for="field in publishedHttpRowsResult.fields" :key="field.fieldCode">{{ publishedHttpValue(row.values[field.fieldCode]) }}</td></tr></tbody>
                </table>
                <a-empty v-if="!publishedHttpRowsResult.rows.length" description="第一页没有返回数据" />
              </div>
              <a-empty v-else-if="!publishedHttpRowsLoading && !publishedHttpRowsError" description="点击加载当前活动版本的固定第一页" />
            </section>

            <section v-if="isPublishedJdbc" v-show="activeTab === 'preview'" class="editor-card published-jdbc-rows">
              <div class="card-heading"><div><strong>已发布 MySQL 数据</strong><p>仅在明确操作后读取当前活动快照的固定第一页；不会自动查询或进入 Native 记录能力。</p></div><a-button class="published-jdbc-rows-load" :loading="publishedJdbcRowsLoading" :disabled="publishedJdbcRowsLoading || !selected.activeVersionId" @click="loadPublishedJdbcRows"><Eye :size="15" />加载已发布数据</a-button></div>
              <a-alert v-if="publishedJdbcRowsError" class="published-jdbc-rows-error" type="error" show-icon :message="publishedJdbcRowsError" />
              <div v-if="publishedJdbcRowsResult" class="published-jdbc-rows-result preview-table-wrap">
                <table class="preview-table"><thead><tr><th>行</th><th v-for="field in publishedJdbcRowsResult.fields" :key="field.fieldCode" class="published-jdbc-field"><code>{{ field.fieldCode }}</code><small>{{ field.sourceType }}</small></th></tr></thead><tbody><tr v-for="row in publishedJdbcRowsResult.rows" :key="row.rowIndex" class="published-jdbc-row" :data-row-index="row.rowIndex"><td>{{ row.rowIndex }}</td><td v-for="field in publishedJdbcRowsResult.fields" :key="field.fieldCode">{{ draftPreviewValue(row.values[field.fieldCode]) }}</td></tr></tbody></table>
                <a-empty v-if="!publishedJdbcRowsResult.rows.length" description="第一页没有返回数据" />
              </div>
              <a-empty v-else-if="!publishedJdbcRowsLoading && !publishedJdbcRowsError" description="点击加载当前活动版本的固定第一页" />
            </section>

            <section v-if="!isExternalDraft" v-show="activeTab === 'preview'" class="editor-card published-preview">
              <div class="card-heading"><div><strong>已发布数据预览</strong><p v-if="runtimeMetadata">运行版本 v{{ runtimeMetadata.versionNumber ?? runtimeMetadata.activeVersionNumber }} · Schema {{ runtimeMetadata.schemaVersionId }}</p><p v-else>草稿不会出现在这里；发布后才会调用真实运行端点。</p></div><a-button class="preview-refresh" :disabled="!selected.activeVersionId" :loading="previewLoading" @click="loadPreview(1)"><Eye :size="15" />刷新预览</a-button></div>
              <div v-if="runtimeMetadata" class="preview-table-wrap">
                <table class="preview-table"><thead><tr><th>记录</th><th v-for="field in previewFields" :key="field.fieldCode">{{ field.fieldName }}<small>{{ field.fieldCode }}</small></th></tr></thead><tbody><tr v-for="row in previewItems" :key="row.recordId"><td><code>{{ row.recordNo ?? row.recordId }}</code></td><td v-for="field in previewFields" :key="field.fieldCode">{{ previewValue(row, field.fieldCode) }}</td></tr></tbody></table>
                <a-empty v-if="!previewLoading && !previewItems.length" description="已发布数据源当前无可见记录" />
                <footer class="preview-pagination"><span>共 {{ runtimeRows?.total ?? 0 }} 条 · 第 {{ previewPage }} 页</span><div><a-button size="small" :disabled="!canPrevious" @click="loadPreview(previewPage - 1)">上一页</a-button><a-button size="small" :disabled="!canNext" @click="loadPreview(previewPage + 1)">下一页</a-button></div></footer>
              </div>
              <a-empty v-else description="尚无已发布版本" />
            </section>

            <section v-if="selected.activeVersionId" v-show="activeTab === 'statistics'" class="editor-card statistics-tester">
              <div class="card-heading"><div><strong>真实统计测试器</strong><p v-if="activePublishedVersion">严格读取数据源 v{{ activePublishedVersion.versionNumber }} / Schema {{ activePublishedVersion.schemaVersionId }}；草稿字段不会参与计算。</p><p v-else>发布数据源后可执行真实统计。</p></div><a-button class="statistics-run" type="primary" :disabled="!selected.activeVersionId || statisticsLoading" :loading="statisticsLoading" @click="runStatistics"><Play :size="15" />运行统计</a-button></div>
              <div class="statistics-controls">
                <label>展示<select v-model="statisticsVisualization" @change="statisticsVisualizationChanged"><option value="STAT_VALUE">STAT_VALUE</option><option value="BAR_CHART">BAR_CHART</option><option value="PIE_CHART">PIE_CHART</option><option value="LINE_TREND">LINE_TREND</option></select></label>
                <label>聚合<select v-model="statisticsDraft.aggregation" @change="statisticsAggregationChanged"><option value="COUNT">COUNT</option><option value="SUM">SUM</option><option value="AVG">AVG</option><option value="MIN">MIN</option><option value="MAX">MAX</option></select></label>
                <label v-if="statisticsDraft.aggregation !== 'COUNT'">度量字段<select v-model="statisticsDraft.measureFieldCode" @change="statisticsConfigurationChanged"><option value="">请选择数值字段</option><option v-for="field in statisticsCapability.measureFields" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}（{{ field.fieldCode }}）</option></select></label>
                <template v-if="statisticsVisualization === 'BAR_CHART' || statisticsVisualization === 'PIE_CHART'">
                  <label>分组字段<select v-model="statisticsDraft.groupFieldCode" @change="statisticsConfigurationChanged"><option value="">请选择输出字段</option><option v-for="field in statisticsCapability.groupFields" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}（{{ field.fieldCode }}）</option></select></label>
                  <label>分组上限<input v-model.number="statisticsDraft.bucketLimit" type="number" min="1" max="20" step="1" @input="statisticsConfigurationChanged"></label>
                </template>
                <template v-if="statisticsVisualization === 'LINE_TREND'">
                  <label>时间字段<select v-model="statisticsDraft.timeFieldCode" @change="statisticsConfigurationChanged"><option value="">请选择时间字段</option><option v-for="field in statisticsCapability.timeFields" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}（{{ field.fieldCode }}）</option></select></label>
                  <label>粒度<select v-model="statisticsDraft.grain" @change="statisticsConfigurationChanged"><option value="DAY">DAY</option><option value="WEEK">WEEK</option><option value="MONTH">MONTH</option></select></label>
                  <label>开始（含）<input v-model="statisticsDraft.startInclusive" type="date" @input="statisticsConfigurationChanged"></label>
                  <label>结束（不含）<input v-model="statisticsDraft.endExclusive" type="date" @input="statisticsConfigurationChanged"></label>
                </template>
              </div>
              <ul v-if="statisticsIssues.length" class="statistics-issues"><li v-for="issue in statisticsIssues" :key="`${issue.code}:${issue.path}`"><code>{{ issue.code }}</code><span>{{ issue.message }}</span></li></ul>
              <StatisticsResultView v-if="statisticsLoading || statisticsResult || statisticsError" class="statistics-tester-result" :visualization="statisticsVisualization" :result="statisticsResult" :loading="statisticsLoading" :error="statisticsError" />
              <a-empty v-else :description="selected.activeVersionId ? '配置统计能力后运行真实查询' : '尚无已发布版本'" />
            </section>

            <section v-show="activeTab === 'versions'" class="editor-card version-history">
              <div class="card-heading"><div><strong>发布版本</strong><p>每次有效发布生成不可变快照；相同草稿重复发布复用现有版本。</p></div></div>
              <table v-if="versions.length"><thead><tr><th>版本</th><th>模块</th><th>Schema</th><th>指纹</th><th>发布时间</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="version in versions" :key="version.id"><td>v{{ version.versionNumber }}</td><td>{{ version.moduleCode }}</td><td><code>{{ version.schemaVersionId }}</code></td><td><code>{{ version.fingerprint.slice(0, 12) }}</code></td><td>{{ formatTime(version.publishedAt) }}</td><td><a-tag :color="version.active || version.id === selected.activeVersionId ? 'green' : 'default'">{{ version.active || version.id === selected.activeVersionId ? '当前' : '历史' }}</a-tag></td><td><a-button v-if="!version.active && version.id !== selected.activeVersionId" size="small" :disabled="draftDirty || editorState === 'dirty' || editorState === 'saving'" @click="restoreVersion(version)"><RotateCcw :size="14" />恢复草稿</a-button></td></tr></tbody></table>
              <a-empty v-else description="暂无发布版本" />
            </section>
          </div>
        </template>
      </main>
    </div>

    <a-modal v-model:open="createOpen" title="新建数据源" :confirm-loading="creating" @ok="createSource">
      <a-form layout="vertical">
        <a-form-item label="名称" required><a-input v-model:value="createForm.name" class="create-source-name" :maxlength="200" /></a-form-item>
        <a-form-item label="编码" required><a-input v-model:value="createForm.code" class="create-source-code" :maxlength="64" placeholder="例如 overdue_orders" /></a-form-item>
        <a-form-item label="来源类型" required><a-select v-model:value="createForm.sourceKind" class="create-source-kind" @change="createSourceKindChanged"><a-select-option value="NATIVE_MODULE">原生模块</a-select-option><a-select-option value="HTTP_JSON">HTTP JSON</a-select-option><a-select-option value="JDBC_TABLE">只读 MySQL 表</a-select-option><a-select-option value="MULTI_MODULE_JOIN">多模块 Join</a-select-option></a-select></a-form-item>
        <a-form-item label="锚点模块" required><a-select v-model:value="createForm.moduleId" class="create-source-module" @change="createAnchorChanged"><a-select-option v-for="item in modules" :key="item.moduleId" :value="item.moduleId" :disabled="!item.available">{{ item.moduleName }}（{{ item.moduleCode }}）{{ item.available ? '' : ` · ${item.unavailableReason ?? '不可用'}` }}</a-select-option></a-select></a-form-item>
        <template v-if="createForm.sourceKind === 'HTTP_JSON'">
          <a-alert class="create-http-secret-boundary" type="info" show-icon message="仅提交 SecretRef，不填写任何明文凭据。" />
          <a-form-item label="HTTPS Endpoint" required><a-input v-model:value="createHttp.endpoint" class="create-http-json-endpoint" :maxlength="1024" /></a-form-item>
          <a-form-item label="认证 SecretRef"><a-input v-model:value="createHttp.authSecretRef" class="create-http-json-secret-ref" :maxlength="512" autocomplete="off" /></a-form-item>
          <a-form-item label="超时（秒）" required><a-input v-model:value="createHttp.timeoutSeconds" class="create-http-json-timeout" type="number" min="1" max="10" /></a-form-item>
        </template>
        <template v-if="createForm.sourceKind === 'JDBC_TABLE'">
          <a-alert class="create-jdbc-security-boundary" type="warning" show-icon message="只允许结构化 MySQL 8 目标；禁止 JDBC URL、驱动参数、连接属性和任意 SQL。" />
          <a-alert class="create-jdbc-secret-boundary" type="info" show-icon message="用户名和密码分别提交 SecretRef：env://EXAMINE_DS_S<systemId>_T<tenantId>_<ALIAS>_V<version>；不会自动构造或回显引用。" />
          <a-form-item label="MySQL 主机" required><a-input v-model:value="createJdbc.host" class="create-jdbc-host" :maxlength="253" /></a-form-item>
          <a-form-item label="端口" required><a-input v-model:value="createJdbc.port" class="create-jdbc-port" type="number" min="1" max="65535" /></a-form-item>
          <a-form-item label="数据库" required><a-input v-model:value="createJdbc.databaseName" class="create-jdbc-database-name" :maxlength="64" /></a-form-item>
          <a-form-item label="数据表" required><a-input v-model:value="createJdbc.tableName" class="create-jdbc-table-name" :maxlength="64" /></a-form-item>
          <a-form-item label="用户名 SecretRef" required><a-input v-model:value="createJdbc.usernameSecretRef" class="create-jdbc-username-secret-ref" :maxlength="512" autocomplete="off" /></a-form-item>
          <a-form-item label="密码 SecretRef" required><a-input v-model:value="createJdbc.passwordSecretRef" class="create-jdbc-password-secret-ref" :maxlength="512" autocomplete="off" /></a-form-item>
          <a-form-item label="连接超时（秒）" required><a-input v-model:value="createJdbc.connectTimeoutSeconds" class="create-jdbc-connect-timeout" type="number" min="1" max="10" /></a-form-item>
          <a-form-item label="查询超时（秒）" required><a-input v-model:value="createJdbc.queryTimeoutSeconds" class="create-jdbc-query-timeout" type="number" min="1" max="10" /></a-form-item>
        </template>
        <template v-if="createForm.sourceKind === 'MULTI_MODULE_JOIN'">
          <a-alert class="create-join-boundary" type="info" show-icon message="首个输入必须与锚点模块一致；所有输入都固定到不可变发布版本，发布检查会验证字段、类型和模块边界。" />
          <MultiModuleJoinEditor
            :system-id="systemId"
            current-source-id=""
            :anchor-module-id="createForm.moduleId"
            :sources="sources"
            :model-value="createJoinPlan"
            @update:model-value="updateCreateJoinPlan"
          />
        </template>
        <a-form-item label="描述"><a-textarea v-model:value="createForm.description" :rows="3" :maxlength="2000" /></a-form-item>
      </a-form>
      <a-alert v-if="createError" class="create-source-error" type="error" show-icon :message="createError" />
    </a-modal>
  </section>
</template>

<style scoped>
.data-source-mobile-gate{min-width:0;padding:4px 0 28px}.data-source-mobile-gate .ant-empty{padding:30px 12px;border:1px solid #dce3e7;border-radius:8px;background:#fff}.data-source-workspace { display: grid; grid-template-columns: minmax(540px, .9fr) minmax(640px, 1.1fr); min-width:0; min-height: 720px; border: 1px solid #dce3e7; border-radius: 10px; overflow: hidden; background: #fff; }
.source-list-panel { min-width: 0; border-right: 1px solid #dce3e7; background: #f7f9fa; }
.source-list-heading { padding: 15px 16px 9px; }.source-list-heading > div { display:flex;align-items:center;justify-content:space-between;gap:12px; }.source-list-heading span { color:#71808a;font-size:12px; }
.source-list-tools { display:grid;grid-template-columns:minmax(190px,1fr) minmax(130px,.65fr) minmax(120px,.55fr);gap:8px;padding:0 14px 13px;border-bottom:1px solid #dce3e7; }
.source-list-table-wrap { overflow:auto; }.source-list-table { width:100%;border-collapse:collapse;font-size:12px; }.source-list-table th { position:sticky;top:0;z-index:1;padding:9px 10px;border-bottom:1px solid #dce3e7;background:#eef3f5;color:#5a6871;text-align:left;white-space:nowrap; }.source-list-table td { padding:11px 10px;border-bottom:1px solid #e1e7ea;vertical-align:middle; }.source-list-table td:first-child { min-width:145px; }.source-list-table td:nth-child(2),.source-list-table td:nth-child(3) { min-width:110px; }.source-list-table td:first-child strong,.source-list-table td:first-child small { display:block;max-width:180px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }.source-list-table td:first-child small { margin-top:3px;color:#77858e; }
.source-list-item { cursor:pointer; }.source-list-item:hover { background:#f0f6f9; }.source-list-item.active { background:#e5f0f6;box-shadow:inset 3px 0 #24638c; }.source-list-item:focus-visible { outline:2px solid #24638c;outline-offset:-2px; }
.source-row-action { padding:4px 8px;border:1px solid #b9cbd5;border-radius:5px;background:#fff;color:#245e82;cursor:pointer; }.source-list-loading { padding:24px;text-align:center;color:#6f7d86; }
.field-summary { min-width: 0; display: flex; flex-direction: column; gap: 3px; }.field-summary small { color: #72808a; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.source-editor-panel { min-width: 0; }
.editor-empty { min-height: 500px; display: grid; place-content: center; justify-items: center; color: #76848d; }
.editor-toolbar { position: sticky; top: 0; z-index: 3; display: grid; grid-template-columns: minmax(0,1fr); gap: 12px; align-items: start; padding: 15px 18px; border-bottom: 1px solid #dce3e7; background: rgba(255,255,255,.97); }
.editor-toolbar > div:first-child { min-width: 0; }
.editor-toolbar h2, .editor-toolbar p { margin: 0; }
.editor-toolbar p { margin-top: 4px; color: #697781; font-size: 12px; }
.editor-title-line, .editor-actions, .field-picker, .row-actions, .preview-pagination, .preview-pagination > div { display: flex; align-items: center; gap: 8px; }
.editor-title-line { min-width: 0; flex-wrap: wrap; }
.editor-title-line h2 { min-width: 0; max-width: 100%; overflow: hidden; font-size: 18px; text-overflow: ellipsis; white-space: nowrap; }
.editor-title-line code { color: #64737d; white-space: nowrap; }
.editor-context { display:flex;align-items:center;flex-wrap:wrap;gap:7px; }
.editor-actions { flex-wrap: wrap; justify-content: flex-start; }
.data-source-tabs { display:flex;gap:2px;overflow-x:auto;padding:0 16px;border-bottom:1px solid #dce3e7;background:#fff;white-space:nowrap;scrollbar-width:thin; }.data-source-tabs button { flex:0 0 auto;padding:12px 14px;border:0;border-bottom:3px solid transparent;background:transparent;color:#5d6b74;cursor:pointer; }.data-source-tabs button.active { border-bottom-color:#24638c;color:#174f72;font-weight:600; }
.editor-state { padding: 5px 9px; border-radius: 999px; background: #edf1f3; color: #57636b; font-size: 12px; }
.state-dirty { color: #9b5700; background: #fff0d8; }.state-saving { color: #1b5f88; background: #e3f1fa; }.state-checked,.state-published { color: #23723f; background: #e4f5e9; }.state-error { color: #ad3232; background: #fae5e5; }
.editor-content { min-width:0;padding: 16px; background: #f4f7f8; }
.editor-card { min-width:0;margin-bottom: 14px; padding: 17px; border: 1px solid #dce3e7; border-radius: 9px; background: #fff; }
.overview-grid { display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px;margin:0; }.overview-grid div { padding:12px;border:1px solid #e0e7ea;border-radius:7px;background:#f8fafb; }.overview-grid dt { color:#71808a;font-size:12px; }.overview-grid dd { margin:5px 0 0;color:#253640;font-weight:600; }
.card-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 14px; }
.card-heading p { margin: 3px 0 0; color: #697781; font-size: 12px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 16px; }.form-grid.three-columns { grid-template-columns: repeat(3, minmax(0, 1fr)); }.span-two { grid-column: span 2; }
label { display: flex; flex-direction: column; gap: 6px; color: #56636c; font-size: 12px; }
input, select, textarea { width: 100%; min-height: 36px; box-sizing: border-box; padding: 7px 9px; border: 1px solid #cbd5da; border-radius: 6px; background: #fff; color: #26353f; font: inherit; } textarea { resize: vertical; } input:disabled, select:disabled { background: #f1f3f4; color: #748089; }
.field-picker select { max-width: 480px; }
.ordered-fields { margin: 12px 0 0; padding: 0; list-style: none; }
.ordered-fields li { display: grid; grid-template-columns: 30px minmax(0, 1fr) auto auto auto; align-items: center; gap: 10px; padding: 9px; border-top: 1px solid #e4e9ec; }
.schema-candidate-list, .http-projection-list, .jdbc-projection-list { margin: 12px 0; padding: 0; list-style: none; }
.schema-candidate { display: grid; grid-template-columns: minmax(0, 1fr) auto auto auto; align-items: center; gap: 10px; padding: 8px 0; border-top: 1px solid #e4e9ec; }
.http-projection-row, .jdbc-projection-row { display: grid; grid-template-columns: 30px minmax(0, 1fr) auto minmax(220px, 1fr) auto; align-items: center; gap: 10px; padding: 9px; border-top: 1px solid #e4e9ec; }
.schema-discovery-result { margin-top: 12px; }
.http-projection-actions, .draft-preview-fields, .draft-preview-field { display: flex; align-items: center; gap: 8px; }
.draft-preview-result { margin-top: 14px; padding-top: 14px; border-top: 1px solid #e0e7ea; }
.draft-preview-fields { margin: 10px 0; padding: 0; list-style: none; }
.draft-preview-field { padding: 6px 8px; border: 1px solid #e0e6e9; border-radius: 6px; }
.field-order, .condition-index { display: grid; place-items: center; width: 25px; height: 25px; border-radius: 50%; background: #edf2f5; color: #53636e; font-size: 12px; }
.row-actions button, .filter-remove { display: grid; place-items: center; width: 29px; height: 29px; padding: 0; border: 1px solid #d3dce0; border-radius: 5px; background: #fff; color: #5d6970; cursor: pointer; }.row-actions button:disabled { opacity: .35; cursor: default; }
.filter-row { display: grid; grid-template-columns: 30px minmax(150px, 1fr) minmax(130px, .7fr) minmax(150px, 1fr) minmax(150px, 1fr) 34px; gap: 10px; align-items: end; margin-top: 9px; padding: 10px; border: 1px solid #e0e6e9; border-radius: 7px; }.filter-remove { margin-bottom: 3px; color: #a63b3b; }
.check-result { border-left: 4px solid #2b8550; }.check-result.blocked { border-left-color: #b43d3d; }.check-result ul { margin: 0; padding: 0; list-style: none; }.check-result li { display: grid; grid-template-columns: auto auto minmax(100px,.5fr) minmax(0,1fr); gap: 9px; align-items: center; padding: 9px 0; border-top: 1px solid #e3e8ea; }
.preview-table-wrap { overflow-x: auto; }.preview-table,.version-history table { width: 100%; border-collapse: collapse; }.preview-table th,.preview-table td,.version-history th,.version-history td { padding: 9px 10px; border: 1px solid #dfe6e9; text-align: left; white-space: nowrap; }.preview-table th,.version-history th { background: #f5f7f8; color: #52616b; font-size: 12px; }.preview-table th small { display: block; color: #849099; font-weight: normal; }.preview-pagination { justify-content: space-between; margin-top: 12px; color: #687780; }
.statistics-controls { display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:11px; }.statistics-issues { margin:11px 0 0;padding:0;list-style:none; }.statistics-issues li { display:flex;gap:9px;padding:5px 0;color:#9c4c35; }.statistics-tester-result { margin-top:14px;padding-top:14px;border-top:1px solid #e0e7ea; }
@media(max-width:1199px){.data-source-workspace{grid-template-columns:320px minmax(0,1fr)}.source-list-tools{grid-template-columns:minmax(0,1fr)}.editor-card{overflow-x:auto}.overview-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.statistics-controls{grid-template-columns:repeat(2,minmax(0,1fr))}}
.create-source-error { margin-top: 12px; }
.jdbc-security-boundary,.jdbc-secret-boundary,.create-jdbc-security-boundary,.create-jdbc-secret-boundary { margin-bottom:12px; }
</style>
