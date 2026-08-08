<script setup lang="ts">
import { AlertCircle, Archive, ArchiveRestore, BookmarkPlus, Blocks, Check, ChevronLeft, ChevronRight, Clock3, Copy, Eye, FileDown, FileUp, ListFilter, Pencil, Plus, Printer, RefreshCw, Rocket, RotateCcw, Save, Search, Send, Star, Trash2, UserRoundCheck, X } from 'lucide-vue-next'
import { Modal } from 'ant-design-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate, useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { runtimeApi } from '@/services/config'
import { evaluatePublishedRules, orderedPublishedFields, publishedPage } from '@/services/publishedRuntime'
import { favoriteApi } from '@/services/favorites'
import { globalSearchApi } from '@/services/globalSearch'
import { quickCreateApi } from '@/services/quickCreate'
import { recentRecordApi } from '@/services/recentRecords'
import { useSessionStore } from '@/stores/session'
import type { RuntimeBatchCommandItemInput, RuntimeDefinition, RuntimeFieldCapability, RuntimeFieldValue, RuntimeFilterNode, RuntimeFilterPredicate, RuntimeLifecycleCommand, RuntimeNavigation, RuntimePublishedPage, RuntimeRecordDetail, RuntimeRecordFlowState, RuntimeRecordPage, RuntimeRecordQuery, RuntimeRecordSchema, RuntimeRecordScope, RuntimeRelationInput, RuntimeRelationItem, RuntimeSavedView, RuntimeSortAnchor, RuntimeSortItem, RuntimeSubRowInput, RuntimeSubtableInput, RuntimeVersionConflictData } from '@/types/config'
import type { RuntimeFavoriteItem, RuntimeFavoriteType } from '@/types/favorites'
import type { RuntimeGlobalSearchItem } from '@/types/globalSearch'
import type { RuntimeQuickCreateModule } from '@/types/quickCreate'
import type { RuntimeRecentRecordItem } from '@/types/recentRecords'
import type { AiFillMaterializationResult } from '@/types/aiFill'
import MemberPicker from '@/components/runtime/MemberPicker.vue'
import RuntimeFavoritesPanel from '@/components/runtime/RuntimeFavoritesPanel.vue'
import RuntimeFavoriteToggle from '@/components/runtime/RuntimeFavoriteToggle.vue'
import RuntimeGlobalSearchPanel from '@/components/runtime/RuntimeGlobalSearchPanel.vue'
import RuntimeExportDrawer from '@/components/runtime/RuntimeExportDrawer.vue'
import RuntimeImportDrawer from '@/components/runtime/RuntimeImportDrawer.vue'
import RuntimePrintDrawer from '@/components/runtime/RuntimePrintDrawer.vue'
import RuntimeQuickCreatePanel from '@/components/runtime/RuntimeQuickCreatePanel.vue'
import RuntimeRecentRecordsPanel from '@/components/runtime/RuntimeRecentRecordsPanel.vue'
import RecordCommentPanel from './RecordCommentPanel.vue'
import RecordFilePanel from './RecordFilePanel.vue'
import RecordHistoryPanel from './RecordHistoryPanel.vue'
import RecordTeamPanel from './RecordTeamPanel.vue'
import RuntimeAiFillField from './RuntimeAiFillField.vue'
import RuntimeDerivedField from './RuntimeDerivedField.vue'
import RuntimeFieldDisplay from './RuntimeFieldDisplay.vue'
import RuntimeFieldInput from './RuntimeFieldInput.vue'
import RuntimeReferenceField from './RuntimeReferenceField.vue'
import RuntimeRelationField from './RuntimeRelationField.vue'
import RuntimeSubtableField from './RuntimeSubtableField.vue'
import { isBlankRuntimeValue, isRuntimeCollectionOperator, isRuntimeRangeOperator, readRuntimeFilterDraft, runtimeCurrencies, runtimeFixedCurrency, runtimeJsonQueryPaths, runtimePredicateValue, type RuntimeDraftScalar } from './runtimeFieldModel'
import { createSingleFlightSaveCoordinator, runtimeErrorMessage, runtimeStatusLabel, runtimeValueByCode } from './runtimeRecordModel'
import { canonicalJson, defaultRuntimeListState, parseRuntimeListQuery, runtimeListRouteQuery, type RuntimeListState } from './runtimeQueryModel'
import { copyScenarioQuery, sharedFilterScenarioConfig, sharedFilterScenarioSupported } from './filterScenarioModel'

const BATCH_EDIT_EXCLUDED_TYPES = new Set([
  'IDENTITY', 'SECRET',
  'RELATION', 'REFERENCE', 'SUBTABLE',
  'FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE',
  'AI_FILL',
  'TENANT', 'AUTO_NUMBER', 'CREATED_BY', 'CREATED_AT', 'UPDATED_BY', 'UPDATED_AT',
])

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const selectedCode = computed(() => String(route.query.module ?? ''))
const selectedRecord = computed(() => String(route.query.record ?? ''))
const selectedMode = computed(() => String(route.query.mode ?? ''))
const selectedDraft = computed(() => String(route.query.draft ?? ''))
const formOpen = computed(() => selectedMode.value === 'create' || selectedMode.value === 'edit')
const detailOpen = computed(() => Boolean(selectedRecord.value) && selectedMode.value !== 'edit')
const navigation = ref<RuntimeNavigation | null>(null)
const definition = ref<RuntimeDefinition | null>(null)
const schema = ref<RuntimeRecordSchema | null>(null)
const recordPage = ref<RuntimeRecordPage | null>(null)
const detail = ref<RuntimeRecordDetail | null>(null)
const detailFlowStates = ref<RuntimeRecordFlowState[]>([])
const navigationLoading = ref(false)
const contentLoading = ref(false)
const detailLoading = ref(false)
const currentSortAnchor = ref<RuntimeSortAnchor | null>(null)
const neighborLoading = ref<'PREVIOUS' | 'NEXT' | ''>('')
const neighborBoundary = ref({ PREVIOUS: false, NEXT: false })
const neighborError = ref('')
const selectedBatchRecords = ref(new Map<string, number>())
const batchCommandLoading = ref<'archive' | 'trash' | 'transfer' | 'edit' | ''>('')
const batchCommandError = ref('')
const batchEditModalOpen = ref(false)
const batchEditFieldCode = ref('')
const batchEditOperation = ref<'SET' | 'CLEAR'>('SET')
const batchEditValue = ref<unknown>()
const batchEditError = ref('')
const transferModalOpen = ref(false)
const transferMemberId = ref('')
const transferError = ref('')
const importDrawerOpen = ref(false)
const exportDrawerOpen = ref(false)
const printDrawerOpen = ref(false)
const resultTaskId = computed(() => String(route.query.task ?? ''))
const favoritesPanelOpen = ref(false)
const favoriteItems = ref<RuntimeFavoriteItem[]>([])
const knownFavorites = ref(new Map<string, RuntimeFavoriteItem>())
const favoritePage = ref(1)
const favoriteSize = 20
const favoriteTotal = ref(0)
const favoritesLoading = ref(false)
const favoriteMutationKey = ref('')
const favoriteError = ref('')
const recentPanelOpen = ref(false)
const recentItems = ref<RuntimeRecentRecordItem[]>([])
const recentPage = ref(1)
const recentSize = 20
const recentTotal = ref(0)
const recentLoading = ref(false)
const recentError = ref('')
const globalSearchPanelOpen = ref(false)
const globalSearchKeyword = ref('')
const globalSearchItems = ref<RuntimeGlobalSearchItem[]>([])
const globalSearchPage = ref(1)
const globalSearchSize = 20
const globalSearchTotal = ref(0)
const globalSearchLoading = ref(false)
const globalSearchError = ref('')
const quickCreatePanelOpen = ref(false)
const quickCreateItems = ref<RuntimeQuickCreateModule[]>([])
const quickCreateLoading = ref(false)
const quickCreateError = ref('')
const navigationError = ref('')
const contentError = ref('')
const detailError = ref('')
const detailFlowStateError = ref('')
const selectedGroup = ref('')
const selectedModule = ref('')
const queryPage = ref(1)
const pageSize = ref(50)
const recordScope = ref<RuntimeRecordScope>('active')
const searchText = ref('')
const filterNode = ref<RuntimeFilterNode | null>(null)
const sortItems = ref<RuntimeSortItem[]>([])
const selectedFilterScenarioCode = ref('')
const selectedColumns = ref<string[]>([])
const appliedViewId = ref('')
const managedViewId = ref('')
const savedViews = ref<RuntimeSavedView[]>([])
const savedViewsAvailable = ref(false)
const savedViewsLoading = ref(false)
const savedViewError = ref('')
const invalidNodes = ref<string[]>([])
const urlWarning = ref('')
const queryDrawerOpen = ref(false)
const filterJoin = ref<'AND' | 'OR'>('AND')
const filterRows = ref<FilterDraftRow[]>([])
const sortDraft = ref<RuntimeSortItem[]>([])
const columnDraft = ref<string[]>([])
const queryBuilderError = ref('')
const savedViewModalOpen = ref(false)
const savedViewMode = ref<'create' | 'update'>('create')
const savedViewName = ref('')
const savedViewSaving = ref(false)
const formTitle = ref('')
const formValues = ref<Record<string, unknown>>({})
const formStoredValues = ref<Record<string, RuntimeFieldValue>>({})
const relationDrafts = ref<Record<string, RuntimeRelationItem[]>>({})
const subtableDrafts = ref<Record<string, RuntimeSubRowInput[]>>({})
const explicitNullFields = ref(new Set<string>())
const fieldErrors = ref<Record<string, string>>({})
const createError = ref('')
const createLoading = ref(false)
const activateLoading = ref(false)
const lifecycleLoading = ref<RuntimeLifecycleCommand | ''>('')
const formRecord = ref<RuntimeRecordDetail | null>(null)
const saveState = ref<'clean' | 'dirty' | 'saving' | 'saved' | 'error' | 'conflict'>('clean')
const lastSavedAt = ref<Date | null>(null)
const restoredDraft = ref(false)
const conflictData = ref<RuntimeVersionConflictData | null>(null)
const formRevision = ref(0)
const savedRevision = ref(0)
let autosaveTimer: ReturnType<typeof setTimeout> | undefined
let suppressFormWatch = false
let internalNavigation = false
let activeSavePromise: Promise<void> | null = null

type RuntimeLifecycleAction = 'ARCHIVE' | 'UNARCHIVE' | 'TRASH' | 'RESTORE_TRASH' | 'DISCARD' | 'RECOVER_DRAFT'

interface PendingLifecycle {
  action: RuntimeLifecycleAction
  record: RuntimeRecordDetail
  title: string
  message: string
  danger: boolean
}

interface FilterDraftRow {
  id: string
  fieldCode: string
  operator: string
  value: RuntimeDraftScalar
  secondValue: RuntimeDraftScalar
  values: string[]
  currency: string
  empty: boolean
  pathSnapshotId: string
  params: Record<string, RuntimeDraftScalar>
  negate: boolean
}

const pendingLifecycle = ref<PendingLifecycle | null>(null)

const moduleName = computed(() => String(definition.value?.module.module_name ?? ''))
const moduleCode = computed(() => String(definition.value?.module.module_code ?? selectedModule.value))
const listPage = computed(() => publishedPage(schema.value, 'LIST'))
const formPage = computed(() => publishedPage(schema.value, 'FORM'))
const detailPage = computed(() => publishedPage(schema.value, 'DETAIL'))
const publishedListFields = computed(() => orderedPublishedFields(schema.value, 'LIST', (field) => field.showInList))
const displayedListFields = computed(() => selectedColumns.value.length
  ? publishedListFields.value.filter((field) => selectedColumns.value.includes(field.fieldCode))
  : publishedListFields.value)
const formRuleDecision = computed(() => evaluatePublishedRules(schema.value, formValues.value))
const detailRuleValues = computed(() => Object.fromEntries(
  detail.value?.values.map((value) => [value.fieldCode, value.value]) ?? [],
))
const detailRuleDecision = computed(() => evaluatePublishedRules(schema.value, detailRuleValues.value))
const detailFields = computed(() => orderedPublishedFields(schema.value, 'DETAIL', (field) => field.showInDetail)
  .filter((field) => !detailRuleDecision.value.hiddenFields.has(field.fieldCode)))
const formFields = computed(() => orderedPublishedFields(schema.value, 'FORM', () => true)
  .filter((field) => field.type !== 'AI_FILL' && !formRuleDecision.value.hiddenFields.has(field.fieldCode))
  .filter((field) => field.writable || field.type === 'REFERENCE' || formRuleDecision.value.readonlyFields.has(field.fieldCode)))
const formFieldGroups = computed(() => publishedFieldGroups(formPage.value, formFields.value))
const detailFieldGroups = computed(() => publishedFieldGroups(detailPage.value, detailFields.value))
const filterableFields = computed(() => schema.value?.fields.filter((field) => field.operators.length) ?? [])
const sortableFields = computed(() => schema.value?.fields.filter((field) => field.sortable) ?? [])
const filterScenarioConfig = computed(() => sharedFilterScenarioConfig(definition.value))
const sharedFilterScenarios = computed(() => filterScenarioConfig.value.scenarios
  .filter((scenario) => sharedFilterScenarioSupported(scenario, schema.value)))
const canCreate = computed(() => schema.value?.actions.includes('CREATE') ?? false)
const canImport = computed(() => session.hasPermission(`module.${selectedModule.value}.import`))
const canExport = computed(() => session.hasPermission(`module.${selectedModule.value}.export`))
const canBatchArchive = computed(() =>
  recordScope.value === 'active'
  && session.hasPermission(`module.${selectedModule.value}.action.archive`))
const canBatchTrash = computed(() =>
  ['active', 'archived', 'draft'].includes(recordScope.value)
  && session.hasPermission(`module.${selectedModule.value}.delete`))
const canBatchTransfer = computed(() =>
  recordScope.value === 'active'
  && session.hasPermission(`module.${selectedModule.value}.action.transfer`))
const canBatchEdit = computed(() =>
  recordScope.value === 'active'
  && session.hasPermission(`module.${selectedModule.value}.update`))
const canBatchSelect = computed(() =>
  canBatchArchive.value || canBatchTrash.value || canBatchTransfer.value || canBatchEdit.value)
const canStartRecordFlow = computed(() =>
  detail.value?.status === 'ACTIVE'
  && session.hasPermission('flow.instance.start')
  && (!(schema.value?.publishedRuntime?.rules.some((rule) => rule.type === 'APPROVAL_REQUIRED') ?? false)
    || detail.value.actions.includes('START_APPROVAL')))
const recordFlowPending = computed(() =>
  detailFlowStates.value.some((flowState) => flowState.status === 'PENDING'))
const selectedBatchItems = computed<RuntimeBatchCommandItemInput[]>(() =>
  [...selectedBatchRecords.value.entries()].map(([recordId, expectedVersion]) => ({
    recordId,
    expectedVersion,
  })))
const selectableRecordStatus = computed(() => ({
  active: 'ACTIVE',
  archived: 'ARCHIVED',
  draft: 'DRAFT',
  trash: 'TRASHED',
})[recordScope.value])
const visibleBatchRows = computed(() =>
  recordPage.value?.rows.filter((record) => record.status === selectableRecordStatus.value) ?? [])
const allVisibleBatchRowsSelected = computed(() =>
  visibleBatchRows.value.length > 0
  && visibleBatchRows.value.every((record) => selectedBatchRecords.value.has(record.recordId)))
const canManageFavorites = computed(() => session.hasPermission('runtime.favorite.manage'))
const currentModuleFavorite = computed(() =>
  favoriteFor('MODULE', selectedModule.value))
const currentRecordFavorite = computed(() =>
  detail.value ? favoriteFor('RECORD', selectedModule.value, detail.value.recordId) : null)
const eligibleBatchEditFields = computed(() => schema.value?.fields.filter((field) =>
  field.writable
  && !field.mode.startsWith('SENSITIVE')
  && !BATCH_EDIT_EXCLUDED_TYPES.has(field.type)) ?? [])
const batchEditSelectedField = computed(() =>
  eligibleBatchEditFields.value.find((field) => field.fieldCode === batchEditFieldCode.value) ?? null)
const batchSelectionContext = computed(() => canonicalJson({
  systemId: systemId.value,
  tenantId: session.context?.tenantId ?? '',
  moduleCode: selectedModule.value,
  schemaVersionId: schema.value?.schemaVersionId ?? '',
  recordScope: recordScope.value,
  q: searchText.value,
  filter: filterNode.value,
  sort: sortItems.value,
  viewId: appliedViewId.value,
}))
const hasCompositionFields = computed(() => schema.value?.fields.some((field) =>
  ['RELATION', 'REFERENCE', 'SUBTABLE', 'AI_FILL'].includes(field.type)) ?? false)
const formDrawerWidth = computed(() => hasCompositionFields.value ? 'min(1040px, 100vw)' : 'min(620px, 100vw)')
const detailDrawerWidth = computed(() => hasCompositionFields.value ? 'min(1040px, 100vw)' : 'min(520px, 100vw)')
const activeSavedView = computed(() => savedViews.value.find((view) => view.viewId === managedViewId.value))
const scopeOptions = computed(() => {
  const options = [{ label: '使用中', value: 'active' }]
  if (session.hasPermission(`module.${selectedModule.value}.update`)) options.push({ label: '我的草稿', value: 'draft' })
  if (schema.value?.actions.includes('VIEW_ARCHIVE')) options.push({ label: '已归档', value: 'archived' })
  if (schema.value?.actions.includes('VIEW_TRASH')) options.push({ label: '回收站', value: 'trash' })
  return options
})
const formDirty = computed(() => formRevision.value > savedRevision.value)
const autosaveEligible = computed(() => !formRecord.value || formRecord.value.status === 'DRAFT')
const stagedCreateReady = computed(() => {
  if (formRecord.value || !schema.value) return true
  return schema.value.fields.every((field) => {
    if (field.type === 'AI_FILL') return true
    if (!field.writable) return true
    if (field.type === 'RELATION') {
      return field.schema.required !== true || (relationDrafts.value[field.fieldCode]?.length ?? 0) > 0
    }
    if (field.type === 'SUBTABLE') {
      const minimum = Number(field.schema.minRows ?? (field.schema.required === true ? 1 : 0))
      return (subtableDrafts.value[field.fieldCode]?.length ?? 0) >= minimum
    }
    return field.schema.required !== true || !isBlankRuntimeValue(field, formValues.value[field.fieldCode])
  })
})
const formReady = computed(() => {
  if (selectedMode.value === 'create') return true
  const expected = selectedDraft.value || selectedRecord.value
  return Boolean(expected && formRecord.value?.recordId === expected)
})
const formHeading = computed(() => formRecord.value
  ? `${formRecord.value.status === 'DRAFT' ? '编辑草稿' : '编辑记录'} · ${formRecord.value.recordNo}`
  : `新建${moduleName.value || '记录'}`)
const saveStateText = computed(() => {
  if (saveState.value === 'saving') return '正在保存'
  if (saveState.value === 'dirty') return '未保存'
  if (saveState.value === 'error') return lastSavedAt.value ? `保存失败 · 上次保存 ${formatTime(lastSavedAt.value)}` : '保存失败'
  if (saveState.value === 'conflict') return '发现版本冲突'
  if (lastSavedAt.value) return `${formRecord.value?.status === 'DRAFT' ? '草稿已保存' : '已保存'} ${formatTime(lastSavedAt.value)}`
  if (restoredDraft.value) return '已恢复服务端草稿'
  return formRecord.value ? '已保存' : '尚未创建草稿'
})
const savedViewModalTitle = computed(() => savedViewMode.value === 'create' ? '保存当前视图' : '更新保存视图')

interface FormSnapshot {
  revision: number
  title: string
  values: Record<string, unknown>
  relations: RuntimeRelationInput[]
  subtables: RuntimeSubtableInput[]
  intent: 'manual' | 'auto'
}

function statusColor(value: string) {
  if (value === 'ACTIVE') return 'green'
  if (value === 'EXPIRED') return 'orange'
  if (value === 'TRASHED') return 'red'
  return 'default'
}

function detailValue(fieldCode: string) {
  return detail.value?.values.find((item) => item.fieldCode === fieldCode)
}

function targetModuleCode(field: RuntimeFieldCapability) {
  const targetId = String(field.schema.targetModuleId ?? field.schema.referenceTargetModuleId ?? '')
  for (const group of navigation.value?.groups ?? []) {
    const target = group.modules.find((item) => item.id === targetId)
    if (target) return target.code
  }
  return String(field.schema.referenceTargetModuleCode ?? '')
}

function currentListState(): RuntimeListState {
  return {
    page: queryPage.value,
    size: pageSize.value,
    recordScope: recordScope.value,
    q: searchText.value,
    filter: filterNode.value,
    sort: sortItems.value,
    columns: selectedColumns.value,
    viewId: appliedViewId.value,
  }
}

function filterContainsSensitive(node: RuntimeFilterNode | null): boolean {
  if (!node) return false
  if (node.kind === 'PREDICATE') {
    const field = schema.value?.fields.find((item) => item.fieldCode === node.fieldCode)
    return Boolean(field && (field.mode.startsWith('SENSITIVE') || field.type === 'IDENTITY' || field.type === 'SECRET'))
  }
  return node.children.some(filterContainsSensitive)
}

function assignListState(state: RuntimeListState) {
  queryPage.value = state.page
  pageSize.value = state.size
  recordScope.value = state.recordScope
  searchText.value = state.q
  filterNode.value = state.filter
  sortItems.value = state.sort
  selectedColumns.value = state.columns
  appliedViewId.value = state.viewId
  if (state.viewId) managedViewId.value = state.viewId
}

function restoreListState(query: Record<string, unknown>) {
  const parsed = parseRuntimeListQuery(query)
  assignListState(parsed.state)
  selectedFilterScenarioCode.value = ''
  managedViewId.value = parsed.state.viewId
  if (parsed.invalid) urlWarning.value = '部分无效的列表参数已移除，请检查后重试。'
  return parsed.invalid
}

function currentRouteContext() {
  const context: Record<string, string> = {}
  if (selectedMode.value === 'create') context.mode = 'create'
  else if (selectedMode.value === 'edit' && selectedDraft.value) Object.assign(context, { mode: 'edit', draft: selectedDraft.value })
  else if (selectedMode.value === 'edit' && selectedRecord.value) Object.assign(context, { mode: 'edit', record: selectedRecord.value })
  else if (selectedRecord.value) Object.assign(context, { mode: 'view', record: selectedRecord.value })
  return context
}

async function replaceQuery(query: Record<string, string>) {
  internalNavigation = true
  try {
    await router.replace({ query })
  } finally {
    internalNavigation = false
  }
}

async function pushQuery(query: Record<string, string>) {
  internalNavigation = true
  try {
    await router.push({ query })
  } finally {
    internalNavigation = false
  }
}

async function syncRoute(context = currentRouteContext(), history: 'replace' | 'push' = 'replace') {
  try {
    const state = currentListState()
    if (filterContainsSensitive(state.filter)) state.filter = null
    const query = runtimeListRouteQuery(selectedModule.value, state, context)
    await (history === 'push' ? pushQuery(query) : replaceQuery(query))
  } catch (error) {
    if (error instanceof Error && error.message === 'URL_QUERY_TOO_LONG') {
      urlWarning.value = '当前条件超过 4096 字符，保存为视图后再分享。'
      return
    }
    throw error
  }
}

function recordQuery(viewId: string | null = appliedViewId.value || null): RuntimeRecordQuery {
  if (!schema.value) throw new Error('Runtime schema is unavailable')
  return {
    schemaVersionId: schema.value.schemaVersionId,
    page: queryPage.value,
    size: pageSize.value,
    recordScope: recordScope.value,
    q: searchText.value || null,
    filter: filterNode.value,
    sort: sortItems.value,
    columns: selectedColumns.value,
    viewId,
  }
}

function queryForSave(): RuntimeRecordQuery {
  if (filterContainsSensitive(filterNode.value)) throw new Error('敏感字段查询不能保存为视图或写入浏览器地址。')
  return { ...recordQuery(null), page: 1, viewId: null }
}

function fieldNodeSupported(node: RuntimeFilterNode): boolean {
  if (node.kind === 'PREDICATE') {
    const field = schema.value?.fields.find((item) => item.fieldCode === node.fieldCode)
    return Boolean(field?.operators.includes(node.operator))
  }
  return node.children.every(fieldNodeSupported)
}

function sanitizeListStateForSchema() {
  if (!schema.value) return
  let changed = false
  if (filterContainsSensitive(filterNode.value)) {
    filterNode.value = null
    changed = true
    urlWarning.value = '浏览器地址中的敏感字段条件已移除，请在筛选面板中重新输入。'
  }
  const allowedScopes = new Set(scopeOptions.value.map((option) => option.value))
  if (!allowedScopes.has(recordScope.value)) {
    recordScope.value = 'active'
    changed = true
  }
  if (recordScope.value === 'draft') {
    if (filterNode.value) { filterNode.value = null; changed = true }
    if (sortItems.value.length) { sortItems.value = []; changed = true }
    if (appliedViewId.value || managedViewId.value) {
      appliedViewId.value = ''
      managedViewId.value = ''
      changed = true
    }
    selectedFilterScenarioCode.value = ''
  }
  if (!appliedViewId.value) {
    const readable = new Set(schema.value.fields.map((field) => field.fieldCode))
    const sortable = new Set(sortableFields.value.map((field) => field.fieldCode))
    const columns = selectedColumns.value.filter((code) => readable.has(code))
    const sorts = sortItems.value.filter((item) => sortable.has(item.fieldCode))
    if (columns.length !== selectedColumns.value.length) { selectedColumns.value = columns; changed = true }
    if (sorts.length !== sortItems.value.length) { sortItems.value = sorts; changed = true }
    if (filterNode.value && !fieldNodeSupported(filterNode.value)) { filterNode.value = null; changed = true }
  }
  if (selectedFilterScenarioCode.value
    && !sharedFilterScenarios.value.some((scenario) => scenario.code === selectedFilterScenarioCode.value)) {
    selectedFilterScenarioCode.value = ''
  }
  if (changed) urlWarning.value = '当前身份或模块不支持部分列表条件，相关参数已移除。'
}

function hasExplicitScenarioQuery(query: Record<string, unknown>) {
  return ['viewId', 'filter', 'sort'].some((key) => {
    const value = query[key]
    return Array.isArray(value) ? value.length > 0 : value !== undefined && value !== null && String(value) !== ''
  })
}

function applyDefaultFilterScenario(query: Record<string, unknown>) {
  selectedFilterScenarioCode.value = ''
  if (recordScope.value === 'draft' || hasExplicitScenarioQuery(query)) return
  const scenario = sharedFilterScenarios.value.find((item) => item.code === filterScenarioConfig.value.defaultCode)
  if (!scenario) return
  const copied = copyScenarioQuery(scenario)
  filterNode.value = copied.filter
  sortItems.value = copied.sort
  appliedViewId.value = ''
  managedViewId.value = ''
  queryPage.value = 1
  selectedFilterScenarioCode.value = scenario.code
}

function hydrateSavedView(view: RuntimeSavedView, routeQuery?: Record<string, unknown>) {
  const explicit = routeQuery ?? {}
  const query = view.query
  if (!('q' in explicit)) searchText.value = query.q ?? ''
  if (!('filter' in explicit)) filterNode.value = query.filter
  if (!('sort' in explicit)) sortItems.value = query.sort
  if (!('columns' in explicit)) selectedColumns.value = view.columns
  if (!('recordScope' in explicit)) recordScope.value = query.recordScope
  appliedViewId.value = view.viewId
  managedViewId.value = view.viewId
}

async function loadSavedViews() {
  if (!selectedModule.value) return
  savedViewsLoading.value = true
  savedViewError.value = ''
  try {
    const response = await runtimeApi.savedViews(systemId.value, selectedModule.value)
    savedViews.value = response.items.filter((view) => !filterContainsSensitive(view.query.filter))
    if (savedViews.value.length !== response.items.length) {
      savedViewError.value = '检测到包含敏感字段条件的旧视图，已停止在客户端加载。'
    }
    savedViewsAvailable.value = true
  } catch (error) {
    savedViews.value = []
    savedViewsAvailable.value = false
    if (!(error instanceof ApiRequestError && error.code === 'PERMISSION_DENIED')) {
      savedViewError.value = runtimeErrorMessage(error, '保存视图加载失败。')
    }
  } finally {
    savedViewsLoading.value = false
  }
}

async function loadRecords(page = queryPage.value, updateUrl = true, history: 'replace' | 'push' = 'replace') {
  if (!selectedModule.value) return
  queryPage.value = page
  if (updateUrl) await syncRoute(currentRouteContext(), history)
  contentLoading.value = true
  contentError.value = ''
  try {
    recordPage.value = recordScope.value === 'draft'
      ? await runtimeApi.myDrafts(systemId.value, selectedModule.value, {
          page: queryPage.value,
          size: pageSize.value,
          q: searchText.value || null,
        })
      : await runtimeApi.queryRecords(systemId.value, selectedModule.value, recordQuery())
    invalidNodes.value = recordPage.value.invalidNodes ?? []
    const selectedRow = recordPage.value.rows.find((row) => row.recordId === selectedRecord.value)
    currentSortAnchor.value = selectedRow?.sortAnchor ?? null
    neighborBoundary.value = { PREVIOUS: false, NEXT: false }
    neighborError.value = ''
  } catch (error) {
    recordPage.value = null
    currentSortAnchor.value = null
    invalidNodes.value = []
    contentError.value = runtimeErrorMessage(error, '记录列表加载失败。')
  } finally {
    contentLoading.value = false
  }
}

async function loadDetail(recordId: string) {
  if (!recordId || !selectedModule.value) return
  detailLoading.value = true
  detailError.value = ''
  detail.value = null
  detailFlowStates.value = []
  detailFlowStateError.value = ''
  try {
    const [loaded, flowStates] = await Promise.all([
      runtimeApi.record(systemId.value, selectedModule.value, recordId),
      runtimeApi.flowStates(systemId.value, selectedModule.value, recordId)
        .catch((error) => {
          detailFlowStateError.value = runtimeErrorMessage(error, '审批状态加载失败。')
          return []
        }),
    ])
    detail.value = loaded
    detailFlowStates.value = flowStates
    void touchRecentRecord(loaded)
  } catch (error) {
    detailError.value = runtimeErrorMessage(error, '记录详情加载失败。')
  } finally {
    detailLoading.value = false
  }
}

function toggleBatchRecord(recordId: string, expectedVersion: number, selected: boolean) {
  const next = new Map(selectedBatchRecords.value)
  if (selected) {
    if (!next.has(recordId) && next.size >= 200) {
      batchCommandError.value = '一次最多选择 200 条记录。'
      return
    }
    next.set(recordId, expectedVersion)
  } else {
    next.delete(recordId)
  }
  selectedBatchRecords.value = next
}

function toggleVisibleBatchRecords(selected: boolean) {
  const next = new Map(selectedBatchRecords.value)
  visibleBatchRows.value.forEach((record) => {
    if (selected && (next.has(record.recordId) || next.size < 200)) next.set(record.recordId, record.version)
    else next.delete(record.recordId)
  })
  if (selected && visibleBatchRows.value.some((record) => !next.has(record.recordId))) {
    batchCommandError.value = '一次最多选择 200 条记录，已保留前 200 条明确选择。'
  }
  selectedBatchRecords.value = next
}

function requestBatchCommand(command: 'archive' | 'trash') {
  const items = selectedBatchItems.value
  if (!items.length || batchCommandLoading.value) return
  const archive = command === 'archive'
  const actionLabel = archive ? '归档' : '移入回收站'
  Modal.confirm({
    title: `批量${actionLabel}记录`,
    content: `确认原子${actionLabel}已选择的 ${items.length} 条记录吗？任一记录不满足条件时整批都不会修改。`,
    okText: actionLabel,
    cancelText: '取消',
    okButtonProps: archive ? undefined : { danger: true },
    async onOk() {
      batchCommandLoading.value = command
      batchCommandError.value = ''
      try {
        if (archive) await runtimeApi.batchArchiveRecords(systemId.value, selectedModule.value, { items })
        else await runtimeApi.batchTrashRecords(systemId.value, selectedModule.value, { items })
        selectedBatchRecords.value = new Map()
        await loadRecords(queryPage.value)
      } catch (error) {
        batchCommandError.value = runtimeErrorMessage(error, `批量${actionLabel}失败，整批记录均未修改。`)
      } finally {
        batchCommandLoading.value = ''
      }
    },
  })
}

function openBatchEdit() {
  if (!selectedBatchItems.value.length || batchCommandLoading.value) return
  batchEditFieldCode.value = eligibleBatchEditFields.value[0]?.fieldCode ?? ''
  batchEditOperation.value = 'SET'
  batchEditValue.value = undefined
  batchEditError.value = ''
  batchEditModalOpen.value = true
}

function closeBatchEdit() {
  if (batchCommandLoading.value === 'edit') return
  batchEditModalOpen.value = false
  batchEditFieldCode.value = ''
  batchEditOperation.value = 'SET'
  batchEditValue.value = undefined
  batchEditError.value = ''
}

function selectBatchEditField(fieldCode: string) {
  batchEditFieldCode.value = fieldCode
  batchEditValue.value = undefined
  batchEditError.value = ''
}

function selectBatchEditOperation(operation: 'SET' | 'CLEAR') {
  batchEditOperation.value = operation
  batchEditError.value = ''
}

function requestBatchEditConfirmation() {
  const field = batchEditSelectedField.value
  const items = selectedBatchItems.value
  if (!field) {
    batchEditError.value = '请选择一个可批量编辑的普通字段。'
    return
  }
  if (batchEditOperation.value === 'SET' && (batchEditValue.value === undefined || batchEditValue.value === null)) {
    batchEditError.value = 'SET 操作必须填写字段值。'
    return
  }
  if (!items.length || batchCommandLoading.value) return
  const operation = batchEditOperation.value
  const value = batchEditValue.value
  const operationLabel = operation === 'SET' ? '设置' : '清空'
  Modal.confirm({
    title: '再次确认批量编辑',
    content: `确认对已选择的 ${items.length} 条记录${operationLabel}字段“${field.fieldName}”吗？任一记录校验失败时整批都不会修改。`,
    okText: '确认编辑',
    cancelText: '返回检查',
    async onOk() {
      batchCommandLoading.value = 'edit'
      batchEditError.value = ''
      try {
        await runtimeApi.batchEditRecords(systemId.value, selectedModule.value, {
          items,
          changes: operation === 'SET'
            ? [{ fieldCode: field.fieldCode, operation: 'SET', value }]
            : [{ fieldCode: field.fieldCode, operation: 'CLEAR' }],
        })
        batchEditModalOpen.value = false
        batchEditFieldCode.value = ''
        batchEditValue.value = undefined
        selectedBatchRecords.value = new Map()
        await loadRecords(queryPage.value)
      } catch (error) {
        batchEditError.value = runtimeErrorMessage(error, '批量编辑失败，整批记录均未修改。')
      } finally {
        batchCommandLoading.value = ''
      }
    },
  })
}

function openBatchTransfer() {
  if (!selectedBatchItems.value.length || batchCommandLoading.value) return
  transferMemberId.value = ''
  transferError.value = ''
  transferModalOpen.value = true
}

function closeBatchTransfer() {
  if (batchCommandLoading.value === 'transfer') return
  transferModalOpen.value = false
  transferMemberId.value = ''
  transferError.value = ''
}

function requestBatchTransferConfirmation() {
  const targetMemberId = transferMemberId.value.trim()
  const items = selectedBatchItems.value
  if (!targetMemberId) {
    transferError.value = '请选择新的记录负责人。'
    return
  }
  if (!items.length || batchCommandLoading.value) return
  Modal.confirm({
    title: '再次确认批量转交',
    content: `确认将已选择的 ${items.length} 条记录原子转交给成员 ${targetMemberId} 吗？任一记录或团队不满足条件时整批都不会修改。`,
    okText: '确认转交',
    cancelText: '返回检查',
    async onOk() {
      batchCommandLoading.value = 'transfer'
      transferError.value = ''
      try {
        await runtimeApi.batchTransferRecords(systemId.value, selectedModule.value, {
          items,
          targetMemberId,
        })
        transferModalOpen.value = false
        transferMemberId.value = ''
        selectedBatchRecords.value = new Map()
        await loadRecords(queryPage.value)
      } catch (error) {
        transferError.value = runtimeErrorMessage(error, '批量转交失败，整批记录及协作团队均未修改。')
      } finally {
        batchCommandLoading.value = ''
      }
    },
  })
}

function favoriteTargetKey(type: RuntimeFavoriteType, targetModuleCode: string, recordId = '') {
  return `${type}:${targetModuleCode}:${type === 'RECORD' ? recordId : ''}`
}

function favoriteFor(type: RuntimeFavoriteType, targetModuleCode: string, recordId = '') {
  return knownFavorites.value.get(favoriteTargetKey(type, targetModuleCode, recordId)) ?? null
}

function rememberFavorite(favorite: RuntimeFavoriteItem) {
  const next = new Map(knownFavorites.value)
  next.set(favoriteTargetKey(favorite.type, favorite.moduleCode, favorite.recordId), favorite)
  knownFavorites.value = next
}

function forgetFavorite(favorite: RuntimeFavoriteItem) {
  const next = new Map(knownFavorites.value)
  next.delete(favoriteTargetKey(favorite.type, favorite.moduleCode, favorite.recordId))
  knownFavorites.value = next
}

async function loadFavorites(page = favoritePage.value) {
  if (!canManageFavorites.value) {
    favoriteItems.value = []
    knownFavorites.value = new Map()
    favoriteTotal.value = 0
    favoriteError.value = ''
    return
  }
  favoritePage.value = page
  favoritesLoading.value = true
  favoriteError.value = ''
  try {
    const result = await favoriteApi.list(systemId.value, page, favoriteSize)
    favoriteItems.value = result.items
    favoritePage.value = result.page
    favoriteTotal.value = result.total
    result.items.forEach(rememberFavorite)
  } catch (error) {
    favoriteItems.value = []
    favoriteError.value = runtimeErrorMessage(error, '收藏列表加载失败。')
  } finally {
    favoritesLoading.value = false
  }
}

async function createFavorite(type: RuntimeFavoriteType) {
  const targetModuleCode = selectedModule.value
  const recordId = type === 'RECORD' ? detail.value?.recordId ?? '' : ''
  if (!targetModuleCode || type === 'RECORD' && !recordId || favoriteMutationKey.value) return
  const mutationKey = favoriteTargetKey(type, targetModuleCode, recordId)
  favoriteMutationKey.value = mutationKey
  favoriteError.value = ''
  try {
    const created = await favoriteApi.create(systemId.value, type === 'MODULE'
      ? { type, moduleCode: targetModuleCode }
      : { type, moduleCode: targetModuleCode, recordId })
    rememberFavorite(created)
    favoriteItems.value = [
      created,
      ...favoriteItems.value.filter((item) => item.favoriteId !== created.favoriteId),
    ].slice(0, favoriteSize)
    if (!favoriteItems.value.some((item) => item.favoriteId === created.favoriteId)) {
      await loadFavorites(favoritePage.value)
    } else if (favoriteTotal.value < favoriteItems.value.length) {
      favoriteTotal.value = favoriteItems.value.length
    }
  } catch (error) {
    favoriteError.value = runtimeErrorMessage(error, '收藏失败。')
  } finally {
    favoriteMutationKey.value = ''
  }
}

async function removeFavorite(favorite: RuntimeFavoriteItem) {
  if (favoriteMutationKey.value) return
  favoriteMutationKey.value = `remove:${favorite.favoriteId}`
  favoriteError.value = ''
  try {
    await favoriteApi.remove(systemId.value, favorite.favoriteId, favorite.version)
    forgetFavorite(favorite)
    favoriteItems.value = favoriteItems.value.filter((item) => item.favoriteId !== favorite.favoriteId)
    favoriteTotal.value = Math.max(0, favoriteTotal.value - 1)
    await loadFavorites(Math.min(favoritePage.value, Math.max(1, Math.ceil(favoriteTotal.value / favoriteSize))))
  } catch (error) {
    favoriteError.value = runtimeErrorMessage(error, '取消收藏失败。')
  } finally {
    favoriteMutationKey.value = ''
  }
}

async function toggleFavorite(type: RuntimeFavoriteType) {
  const favorite = type === 'MODULE' ? currentModuleFavorite.value : currentRecordFavorite.value
  if (favorite) await removeFavorite(favorite)
  else await createFavorite(type)
}

async function openFavoriteTarget(favorite: RuntimeFavoriteItem) {
  if (!confirmUnsavedChanges()) return
  favoritesPanelOpen.value = false
  if (favorite.moduleCode === selectedModule.value) {
    if (favorite.type === 'RECORD' && favorite.recordId) await openRecord(favorite.recordId)
    else await selectModule(favorite.moduleCode)
    return
  }
  assignListState(defaultRuntimeListState())
  managedViewId.value = ''
  invalidNodes.value = []
  const context: Record<string, string> = {}
  if (favorite.type === 'RECORD' && favorite.recordId) {
    Object.assign(context, { mode: 'view', record: favorite.recordId })
  }
  await replaceQuery({ module: favorite.moduleCode, ...context })
  await loadModule(favorite.moduleCode, favorite.recordId ?? '')
}

function touchRecentRecord(record: RuntimeRecordDetail) {
  return recentRecordApi.touch(systemId.value, {
    moduleCode: selectedModule.value,
    recordId: record.recordId,
  }).catch(() => undefined)
}

async function loadRecentRecords(page = recentPage.value) {
  recentPage.value = page
  recentLoading.value = true
  recentError.value = ''
  try {
    const result = await recentRecordApi.list(systemId.value, page, recentSize)
    recentItems.value = result.items
    recentPage.value = result.page
    recentTotal.value = result.total
  } catch (error) {
    recentItems.value = []
    recentError.value = runtimeErrorMessage(error, '最近访问加载失败。')
  } finally {
    recentLoading.value = false
  }
}

function openRecentPanel() {
  recentPanelOpen.value = true
  void loadRecentRecords(1)
}

async function openRecentRecord(record: RuntimeRecentRecordItem) {
  if (!confirmUnsavedChanges()) return
  recentPanelOpen.value = false
  if (record.moduleCode === selectedModule.value) {
    await openRecord(record.recordId)
    return
  }
  assignListState(defaultRuntimeListState())
  managedViewId.value = ''
  invalidNodes.value = []
  await replaceQuery({ module: record.moduleCode, mode: 'view', record: record.recordId })
  await loadModule(record.moduleCode, record.recordId)
}

function openGlobalSearchPanel() {
  globalSearchPanelOpen.value = true
}

async function searchGlobalRecords(page = 1) {
  const keyword = globalSearchKeyword.value.trim()
  globalSearchKeyword.value = keyword
  if (keyword.length < 2 || keyword.length > 100) {
    globalSearchError.value = '搜索关键词长度必须为 2–100 个字符。'
    return
  }
  globalSearchLoading.value = true
  globalSearchError.value = ''
  try {
    const result = await globalSearchApi.search(systemId.value, {
      q: keyword,
      page,
      size: globalSearchSize,
    })
    globalSearchItems.value = result.items
    globalSearchPage.value = result.page
    globalSearchTotal.value = result.total
  } catch (error) {
    globalSearchError.value = runtimeErrorMessage(error, '全局搜索失败，请稍后重试。')
  } finally {
    globalSearchLoading.value = false
  }
}

async function openGlobalSearchRecord(record: RuntimeGlobalSearchItem) {
  if (!confirmUnsavedChanges()) return
  globalSearchPanelOpen.value = false
  if (record.moduleCode === selectedModule.value) {
    await openRecord(record.recordId)
    return
  }
  assignListState(defaultRuntimeListState())
  managedViewId.value = ''
  invalidNodes.value = []
  await replaceQuery({ module: record.moduleCode, mode: 'view', record: record.recordId })
  await loadModule(record.moduleCode, record.recordId)
}

async function loadModule(code: string, recordId = '') {
  selectedModule.value = code
  const ownerGroup = navigation.value?.groups.find((group) => group.modules.some((item) => item.code === code))
  if (ownerGroup) selectedGroup.value = ownerGroup.id
  definition.value = null
  schema.value = null
  recordPage.value = null
  currentSortAnchor.value = null
  neighborBoundary.value = { PREVIOUS: false, NEXT: false }
  neighborError.value = ''
  detail.value = null
  detailFlowStates.value = []
  detailError.value = ''
  detailFlowStateError.value = ''
  contentError.value = ''
  selectedFilterScenarioCode.value = ''
  contentLoading.value = true
  try {
    definition.value = await runtimeApi.definition(systemId.value, code)
    if (!definition.value.recordsAvailable) return
    schema.value = await runtimeApi.recordSchema(systemId.value, code)
    const configuredListPage = publishedPage(schema.value, 'LIST')
    if (configuredListPage && route.query.size === undefined) pageSize.value = configuredListPage.pageSize
    applyDefaultFilterScenario(route.query as Record<string, unknown>)
    sanitizeListStateForSchema()
    await loadSavedViews()
    const requestedView = savedViews.value.find((view) => view.viewId === appliedViewId.value)
    if (requestedView) hydrateSavedView(requestedView, route.query as Record<string, unknown>)
    await loadRecords(queryPage.value)
    if (recordId) await loadDetail(recordId)
  } catch (error) {
    contentError.value = runtimeErrorMessage(error, '模块记录加载失败。')
  } finally {
    contentLoading.value = false
  }
}

async function loadNavigation() {
  navigationLoading.value = true
  navigationError.value = ''
  try {
    restoreListState(route.query as Record<string, unknown>)
    navigation.value = await runtimeApi.navigation(systemId.value)
    const firstGroup = navigation.value.groups[0]
    const requestedGroup = navigation.value.groups.find((group) => group.modules.some((item) => item.code === selectedCode.value))
    const requested = requestedGroup?.modules.find((item) => item.code === selectedCode.value)
    const first = requested ?? firstGroup?.modules[0]
    selectedGroup.value = requestedGroup?.id ?? firstGroup?.id ?? ''
    if (first) {
      const preserveRoute = first.code === selectedCode.value
      const recordId = preserveRoute ? selectedRecord.value : ''
      const draftId = preserveRoute ? selectedDraft.value : ''
      const mode = preserveRoute ? selectedMode.value : ''
      if (!preserveRoute) assignListState(defaultRuntimeListState())
      selectedModule.value = first.code
      const context: Record<string, string> = {}
      if (mode === 'edit' && draftId) Object.assign(context, { mode: 'edit', draft: draftId })
      else if (mode === 'edit' && recordId) Object.assign(context, { mode: 'edit', record: recordId })
      else if (mode === 'create') context.mode = 'create'
      else if (recordId) Object.assign(context, { mode: 'view', record: recordId })
      await syncRoute(context)
      await loadModule(first.code, context.mode === 'view' ? recordId : '')
    }
  } catch (error) {
    navigation.value = null
    if (error instanceof ApiRequestError && error.code === 'MODULE_NOT_PUBLISHED') navigationError.value = '系统管理员尚未发布模块配置。'
    else navigationError.value = runtimeErrorMessage(error, '运行导航加载失败。')
  } finally {
    navigationLoading.value = false
  }
}

async function switchModule(
  code: string,
  context: Record<string, string> = {},
) {
  if (!confirmUnsavedChanges()) return
  assignListState(defaultRuntimeListState())
  managedViewId.value = ''
  invalidNodes.value = []
  if (context.mode === 'create') resetForm()
  await replaceQuery({ module: code, ...context })
  await loadModule(code)
  return true
}

async function selectModule(code: string) {
  await switchModule(code)
}

function selectMobileGroup(groupId: string) {
  selectedGroup.value = groupId
  const group = navigation.value?.groups.find((item) => item.id === groupId)
  if (group?.modules[0]) selectModule(group.modules[0].code)
}

async function openRecord(recordId: string) {
  currentSortAnchor.value = recordPage.value?.rows.find((row) => row.recordId === recordId)?.sortAnchor ?? null
  neighborBoundary.value = { PREVIOUS: false, NEXT: false }
  neighborError.value = ''
  if (recordId === selectedRecord.value && detailOpen.value) await loadDetail(recordId)
  else await syncRoute({ mode: 'view', record: recordId }, 'push')
}

async function closeDetail() {
  await syncRoute({})
}

async function navigateNeighbor(direction: 'PREVIOUS' | 'NEXT') {
  const token = recordPage.value?.querySnapshotToken
  const anchor = currentSortAnchor.value
  if (!selectedRecord.value || !token || !anchor || neighborLoading.value) return
  neighborLoading.value = direction
  neighborError.value = ''
  try {
    const response = await runtimeApi.recordNeighbor(
      systemId.value,
      selectedModule.value,
      selectedRecord.value,
      { direction, querySnapshotToken: token, sortAnchor: anchor },
    )
    if (response.querySnapshotToken && recordPage.value) {
      recordPage.value = { ...recordPage.value, querySnapshotToken: response.querySnapshotToken }
    }
    if (!response.neighbor) {
      neighborBoundary.value = { ...neighborBoundary.value, [direction]: true }
      return
    }
    currentSortAnchor.value = response.neighbor.sortAnchor ?? null
    neighborBoundary.value = { PREVIOUS: false, NEXT: false }
    await syncRoute({ mode: 'view', record: response.neighbor.recordId }, 'push')
  } catch (error) {
    neighborError.value = runtimeErrorMessage(error, '无法在当前查询中切换记录，请刷新列表后重试。')
  } finally {
    neighborLoading.value = ''
  }
}

function clearAutosaveTimer() {
  if (autosaveTimer) clearTimeout(autosaveTimer)
  autosaveTimer = undefined
}

function resetForm() {
  clearAutosaveTimer()
  suppressFormWatch = true
  formTitle.value = ''
  formValues.value = {}
  formStoredValues.value = {}
  relationDrafts.value = {}
  subtableDrafts.value = {}
  explicitNullFields.value = new Set()
  fieldErrors.value = {}
  createError.value = ''
  formRecord.value = null
  conflictData.value = null
  restoredDraft.value = false
  lastSavedAt.value = null
  formRevision.value = 0
  savedRevision.value = 0
  saveState.value = 'clean'
  void nextTick(() => { suppressFormWatch = false })
}

async function openCreate() {
  if (!confirmUnsavedChanges()) return
  resetForm()
  await syncRoute({ mode: 'create' })
}

async function loadQuickCreateModules() {
  quickCreateLoading.value = true
  quickCreateError.value = ''
  try {
    const result = await quickCreateApi.list(systemId.value)
    quickCreateItems.value = result.items
  } catch (error) {
    quickCreateError.value = runtimeErrorMessage(error, '可新建模块加载失败，请稍后重试。')
  } finally {
    quickCreateLoading.value = false
  }
}

function openQuickCreatePanel() {
  quickCreatePanelOpen.value = true
  void loadQuickCreateModules()
}

async function openQuickCreateModule(item: RuntimeQuickCreateModule) {
  const opened = await switchModule(item.moduleCode, { mode: 'create' })
  if (opened) quickCreatePanelOpen.value = false
}

async function openEdit(record: RuntimeRecordDetail) {
  if (!record.actions.includes('UPDATE') || !confirmUnsavedChanges()) return
  await syncRoute({ mode: 'edit', record: record.recordId })
}

async function openRecordFlowStart() {
  const record = detail.value
  if (!record || !canStartRecordFlow.value || recordFlowPending.value) return
  await router.push({
    name: 'system-flows',
    params: { systemId: systemId.value },
    query: {
      flowStart: '1',
      moduleCode: selectedModule.value,
      recordId: record.recordId,
      businessKey: record.recordNo,
    },
  })
}

async function closeForm() {
  if (activeSavePromise) {
    try { await activeSavePromise } catch { /* keep the local form open decision below */ }
  }
  if (!confirmUnsavedChanges()) return
  await syncRoute({})
  resetForm()
}

function formPayload() {
  return Object.fromEntries(Object.entries(formValues.value).filter(([fieldCode, value]) => {
    if (formRuleDecision.value.hiddenFields.has(fieldCode) || formRuleDecision.value.readonlyFields.has(fieldCode)) return false
    if (explicitNullFields.value.has(fieldCode)) return true
    const field = schema.value?.fields.find((item) => item.fieldCode === fieldCode)
    if (field && !field.writable) return false
    return field ? !isBlankRuntimeValue(field, value) : value !== undefined && value !== null && value !== ''
  }))
}

function relationInputs(): RuntimeRelationInput[] {
  return (schema.value?.fields.filter((field) => field.type === 'RELATION' && field.writable
    && !formRuleDecision.value.hiddenFields.has(field.fieldCode)
    && !formRuleDecision.value.readonlyFields.has(field.fieldCode)) ?? []).map((field) => ({
    fieldCode: field.fieldCode,
    targets: (relationDrafts.value[field.fieldCode] ?? []).map((item, ordinal) => ({
      targetRecordId: item.targetRecordId,
      targetExpectedVersion: item.targetVersion,
      ordinal,
    })),
  }))
}

function subtableInputs(): RuntimeSubtableInput[] {
  return (schema.value?.fields.filter((field) => field.type === 'SUBTABLE' && field.writable
    && !formRuleDecision.value.hiddenFields.has(field.fieldCode)
    && !formRuleDecision.value.readonlyFields.has(field.fieldCode)) ?? []).map((field) => ({
    fieldCode: field.fieldCode,
    rows: (subtableDrafts.value[field.fieldCode] ?? []).map((row, ordinal) => ({ ...row, ordinal })),
  }))
}

function updateRelationDraft(fieldCode: string, value: RuntimeRelationItem[]) {
  if (formRecord.value || JSON.stringify(relationDrafts.value[fieldCode] ?? []) === JSON.stringify(value)) return
  relationDrafts.value[fieldCode] = value
}

function updateSubtableDraft(fieldCode: string, value: RuntimeSubRowInput[]) {
  if (formRecord.value || JSON.stringify(subtableDrafts.value[fieldCode] ?? []) === JSON.stringify(value)) return
  subtableDrafts.value[fieldCode] = value
}

async function compositionMutated(version: number) {
  if (!formRecord.value) return
  formRecord.value = { ...formRecord.value, version }
  try {
    const canonical = await runtimeApi.record(systemId.value, selectedModule.value, formRecord.value.recordId)
    formRecord.value = canonical
    for (const value of canonical.values.filter((item) => item.type === 'REFERENCE')) {
      formStoredValues.value[value.fieldCode] = value
    }
  } catch (cause) {
    createError.value = runtimeErrorMessage(cause, '组合字段已保存，但最新引用状态读取失败。')
  }
}

function referenceRefreshed(value: RuntimeFieldValue | undefined, version: number) {
  if (formRecord.value) formRecord.value = { ...formRecord.value, version }
  if (detail.value) detail.value = { ...detail.value, version }
  if (!value) return
  formStoredValues.value[value.fieldCode] = value
  if (detail.value) {
    detail.value = { ...detail.value, values: [...detail.value.values.filter((item) => item.fieldCode !== value.fieldCode), value] }
  }
}

async function aiFillMaterialized(result: AiFillMaterializationResult) {
  if (!detail.value || detail.value.recordId !== result.recordId) return
  detail.value = {
    ...detail.value,
    version: result.recordVersion,
    values: detail.value.values.map(value => value.fieldCode === result.fieldCode
      ? { ...value, displayValue: result.displayValue, value: result.displayValue }
      : value),
  }
  await loadDetail(result.recordId)
}

function updateFormValue(fieldCode: string, value: unknown) {
  explicitNullFields.value.delete(fieldCode)
  formValues.value[fieldCode] = value
}

function clearStoredField(fieldCode: string) {
  explicitNullFields.value.add(fieldCode)
  formValues.value[fieldCode] = null
}

function captureFormError(error: unknown, fallback: string) {
  fieldErrors.value = {}
  if (error instanceof ApiRequestError) {
    for (const item of error.errors) {
      if (item.path?.startsWith('values.')) fieldErrors.value[item.path.slice(7)] = item.message
      if (item.path === 'title') fieldErrors.value.title = item.message
    }
    if (error.code === 'RECORD_VERSION_CONFLICT' && isConflictData(error.data)) {
      conflictData.value = error.data
      saveState.value = 'conflict'
    }
  }
  createError.value = runtimeErrorMessage(error, fallback)
}

function isConflictData(value: unknown): value is RuntimeVersionConflictData {
  if (!value || typeof value !== 'object') return false
  const data = value as Partial<RuntimeVersionConflictData>
  return typeof data.currentVersion === 'number'
    && Boolean(data.currentSnapshot?.recordId)
    && Array.isArray(data.conflictFields)
}

function formatTime(value: Date) {
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
    .format(value)
}

async function populateForm(record: RuntimeRecordDetail, restored = false) {
  suppressFormWatch = true
  formRecord.value = record
  formTitle.value = record.title ?? ''
  formValues.value = Object.fromEntries(record.values.map((item) => [item.fieldCode, item.value]))
  formStoredValues.value = Object.fromEntries(record.values.map((item) => [item.fieldCode, item]))
  relationDrafts.value = {}
  subtableDrafts.value = {}
  explicitNullFields.value = new Set()
  fieldErrors.value = {}
  createError.value = ''
  conflictData.value = null
  restoredDraft.value = restored && record.status === 'DRAFT'
  formRevision.value = 0
  savedRevision.value = 0
  saveState.value = 'saved'
  lastSavedAt.value = null
  await nextTick()
  suppressFormWatch = false
}

async function loadFormFromRoute() {
  if (!formOpen.value || !selectedModule.value || !schema.value) return
  const recordId = selectedDraft.value || (selectedMode.value === 'edit' ? selectedRecord.value : '')
  if (!recordId) {
    if (selectedMode.value === 'create' && formRecord.value) resetForm()
    return
  }
  if (formRecord.value?.recordId === recordId) return
  createLoading.value = true
  try {
    const record = await runtimeApi.record(systemId.value, selectedModule.value, recordId)
    await populateForm(record, Boolean(selectedDraft.value))
  } catch (error) {
    captureFormError(error, '服务端草稿或记录加载失败。')
  } finally {
    createLoading.value = false
  }
}

async function persistSnapshot(snapshot: FormSnapshot) {
  if (!schema.value || !selectedModule.value) return
  createLoading.value = true
  saveState.value = 'saving'
  createError.value = ''
  fieldErrors.value = {}
  conflictData.value = null
  try {
    let saved: RuntimeRecordDetail
    if (!formRecord.value) {
      saved = await runtimeApi.createRecord(systemId.value, selectedModule.value, {
        schemaVersionId: schema.value.schemaVersionId,
        title: snapshot.title || undefined,
        values: snapshot.values,
        relations: snapshot.relations,
        subtables: snapshot.subtables,
      })
      formRecord.value = saved
      await syncRoute({ mode: 'edit', draft: saved.recordId })
    } else if (snapshot.intent === 'auto' && formRecord.value.status === 'DRAFT') {
      const response = await runtimeApi.autosaveRecord(
        systemId.value,
        selectedModule.value,
        formRecord.value.recordId,
        {
          schemaVersionId: schema.value.schemaVersionId,
          title: snapshot.title || undefined,
          expectedVersion: formRecord.value.version,
          values: snapshot.values,
        },
      )
      saved = response.record
      lastSavedAt.value = new Date(response.lastSavedAt)
    } else {
      saved = await runtimeApi.updateRecord(
        systemId.value,
        selectedModule.value,
        formRecord.value.recordId,
        {
          schemaVersionId: schema.value.schemaVersionId,
          title: snapshot.title || undefined,
          expectedVersion: formRecord.value.version,
          values: snapshot.values,
          relations: [],
          subtables: [],
        },
      )
    }
    formRecord.value = saved
    savedRevision.value = Math.max(savedRevision.value, snapshot.revision)
    lastSavedAt.value ??= new Date()
    restoredDraft.value = false
    saveState.value = formDirty.value ? 'dirty' : 'saved'
  } catch (error) {
    const conflicted = error instanceof ApiRequestError
      && error.code === 'RECORD_VERSION_CONFLICT'
      && isConflictData(error.data)
    captureFormError(error, snapshot.intent === 'auto' ? '自动保存失败，本地输入已保留。' : '记录保存失败，本地输入已保留。')
    if (!conflicted) saveState.value = 'error'
    throw error
  } finally {
    createLoading.value = false
  }
}

const saveCoordinator = createSingleFlightSaveCoordinator<FormSnapshot>(persistSnapshot)

function snapshot(intent: 'manual' | 'auto'): FormSnapshot {
  return {
    revision: formRevision.value,
    title: formTitle.value,
    values: { ...formPayload() },
    relations: relationInputs(),
    subtables: subtableInputs(),
    intent,
  }
}

async function requestSave(intent: 'manual' | 'auto') {
  if (!schema.value || !selectedModule.value || conflictData.value) return
  if (formRecord.value && !formDirty.value) return
  clearAutosaveTimer()
  const promise = saveCoordinator.request(snapshot(intent))
  activeSavePromise = promise
  try {
    await promise
  } catch {
    // The form keeps the error and local values; callers only need completion.
  } finally {
    if (activeSavePromise === promise) activeSavePromise = null
  }
}

function scheduleAutosave() {
  clearAutosaveTimer()
  if (!formOpen.value || !formDirty.value || !autosaveEligible.value || !stagedCreateReady.value || conflictData.value) return
  autosaveTimer = setTimeout(() => { void requestSave('auto') }, 3000)
}

function autosaveOnBlur() {
  if (formDirty.value && autosaveEligible.value && stagedCreateReady.value) void requestSave('auto')
}

function preventTagFormSubmit(event: KeyboardEvent) {
  const target = event.target
  if (event.key === 'Enter' && target instanceof HTMLElement && target.closest('.tag-control')) {
    event.preventDefault()
  }
}

function retrySave() {
  void requestSave(autosaveEligible.value ? 'auto' : 'manual')
}

async function activateDraft() {
  if (!formRecord.value || formRecord.value.status !== 'DRAFT' || !selectedModule.value) return
  activateLoading.value = true
  createError.value = ''
  try {
    if (activeSavePromise) await activeSavePromise
    if (formDirty.value) await requestSave('manual')
    if (formDirty.value || saveState.value === 'error' || conflictData.value) return
    const activated = await runtimeApi.activateRecord(
      systemId.value,
      selectedModule.value,
      formRecord.value.recordId,
      formRecord.value.version,
    )
    formRecord.value = activated
    await loadRecords(1)
    await syncRoute({ mode: 'view', record: activated.recordId })
  } catch (error) {
    captureFormError(error, '记录激活失败。')
  } finally {
    activateLoading.value = false
  }
}

const lifecycleCommands: Record<RuntimeLifecycleAction, RuntimeLifecycleCommand> = {
  ARCHIVE: 'archive',
  UNARCHIVE: 'unarchive',
  TRASH: 'trash',
  RESTORE_TRASH: 'restore-from-trash',
  DISCARD: 'discard',
  RECOVER_DRAFT: 'recover',
}

function lifecyclePrompt(action: RuntimeLifecycleAction, record: RuntimeRecordDetail): PendingLifecycle {
  const prompts: Record<RuntimeLifecycleAction, Omit<PendingLifecycle, 'action' | 'record'>> = {
    ARCHIVE: {
      title: '归档记录',
      message: `归档 ${record.recordNo} 后，它将移入已归档列表，并可随时取消归档。`,
      danger: false,
    },
    UNARCHIVE: {
      title: '取消归档',
      message: `将 ${record.recordNo} 恢复为使用中记录。`,
      danger: false,
    },
    TRASH: {
      title: '移入回收站',
      message: `将 ${record.recordNo} 移入回收站。字段数据会保留，可按原状态恢复。`,
      danger: true,
    },
    RESTORE_TRASH: {
      title: '恢复记录',
      message: `将 ${record.recordNo} 恢复到移入回收站前的状态。`,
      danger: false,
    },
    DISCARD: {
      title: '放弃草稿',
      message: `放弃 ${record.recordNo} 并移入回收站。已保存的字段数据仍可恢复。`,
      danger: true,
    },
    RECOVER_DRAFT: {
      title: '恢复过期草稿',
      message: `恢复 ${record.recordNo} 为可编辑草稿，并重新保留 30 天。`,
      danger: false,
    },
  }
  return { action, record, ...prompts[action] }
}

async function requestLifecycle(action: RuntimeLifecycleAction, record: RuntimeRecordDetail) {
  if (lifecycleLoading.value) return
  let current = record
  if (action === 'DISCARD') {
    clearAutosaveTimer()
    if (activeSavePromise) {
      try { await activeSavePromise } catch { /* the form error below remains actionable */ }
    }
    if (formDirty.value) await requestSave('manual')
    if (formDirty.value || saveState.value === 'error' || conflictData.value || !formRecord.value) return
    current = formRecord.value
  }
  pendingLifecycle.value = lifecyclePrompt(action, current)
}

function cancelLifecycle() {
  if (!lifecycleLoading.value) pendingLifecycle.value = null
}

async function applyLifecycleResult(record: RuntimeRecordDetail) {
  await loadRecords(1, true, 'push')
  if (record.status === 'DRAFT') {
    await populateForm(record, true)
    await syncRoute({ mode: 'edit', draft: record.recordId })
    return
  }
  detail.value = record
  await syncRoute({ mode: 'view', record: record.recordId })
}

async function executeLifecycle() {
  const pending = pendingLifecycle.value
  if (!pending || !selectedModule.value || lifecycleLoading.value) return
  const command = lifecycleCommands[pending.action]
  lifecycleLoading.value = command
  createError.value = ''
  detailError.value = ''
  try {
    const result = await runtimeApi.lifecycleRecord(
      systemId.value,
      selectedModule.value,
      pending.record.recordId,
      command,
      pending.record.version,
    )
    pendingLifecycle.value = null
    await applyLifecycleResult(result)
  } catch (error) {
    pendingLifecycle.value = null
    if (formOpen.value) {
      captureFormError(error, '草稿操作失败，已保存的内容仍然保留。')
    } else {
      if (error instanceof ApiRequestError && error.code === 'RECORD_VERSION_CONFLICT' && isConflictData(error.data)) {
        detail.value = error.data.currentSnapshot
      }
      detailError.value = runtimeErrorMessage(error, '记录操作失败，请重试。')
    }
  } finally {
    lifecycleLoading.value = ''
  }
}

async function reloadConflict() {
  if (!conflictData.value) return
  const current = conflictData.value.currentSnapshot
  await populateForm(current, current.status === 'DRAFT')
  await syncRoute(current.status === 'DRAFT'
    ? { mode: 'edit', draft: current.recordId }
    : { mode: 'edit', record: current.recordId })
}

async function copyConflictAsDraft() {
  if (!conflictData.value || !canCreate.value) return
  const localRevision = formRevision.value
  formRecord.value = null
  conflictData.value = null
  createError.value = ''
  savedRevision.value = Math.min(savedRevision.value, localRevision - 1)
  saveState.value = 'dirty'
  await syncRoute({ mode: 'create' })
  await requestSave('manual')
}

function fieldRequired(field: RuntimeRecordSchema['fields'][number]) {
  return formRuleDecision.value.requiredFields.has(field.fieldCode)
}

function fieldReadonly(field: RuntimeFieldCapability) {
  return formRuleDecision.value.readonlyFields.has(field.fieldCode)
}

function publishedFieldGroups(page: RuntimePublishedPage | null, fields: RuntimeFieldCapability[]) {
  const fieldByCode = new Map(fields.map((field) => [field.fieldCode, field]))
  const placementByCode = new Map(page?.fields.map((placement) => [placement.fieldCode, placement]) ?? [])
  const result = (page?.sections ?? []).map((section) => ({
    key: section.sectionCode,
    title: section.title,
    collapsible: section.collapsible,
    collapsed: section.collapsed,
    columns: section.columns,
    fields: (page?.fields ?? [])
      .filter((placement) => placement.sectionCode === section.sectionCode)
      .map((placement) => fieldByCode.get(placement.fieldCode))
      .filter(Boolean) as RuntimeFieldCapability[],
  })).filter((group) => group.fields.length)
  const grouped = new Set(result.flatMap((group) => group.fields.map((field) => field.fieldCode)))
  const remaining = fields.filter((field) => !grouped.has(field.fieldCode))
  if (remaining.length) result.unshift({
    key: '__general__',
    title: '',
    collapsible: false,
    collapsed: false,
    columns: page?.columns ?? 24,
    fields: remaining,
  })
  return result.map((group) => ({ ...group, placements: placementByCode }))
}

function formFieldStyle(field: RuntimeFieldCapability, columns: number) {
  const placement = formPage.value?.fields.find((item) => item.fieldCode === field.fieldCode)
  const span = Math.min(columns, Math.max(1, placement?.gridSpan ?? Math.ceil(columns / 2)))
  return { gridColumn: `span ${span}`, gridRow: placement?.gridRow ? String(placement.gridRow) : 'auto' }
}

function detailFieldStyle(field: RuntimeFieldCapability, columns: number) {
  const placement = detailPage.value?.fields.find((item) => item.fieldCode === field.fieldCode)
  const span = Math.min(columns, Math.max(1, placement?.gridSpan ?? Math.ceil(columns / 2)))
  return { gridColumn: `span ${span}`, gridRow: placement?.gridRow ? String(placement.gridRow) : 'auto' }
}

function listColumnStyle(field: RuntimeFieldCapability) {
  const placement = listPage.value?.fields.find((item) => item.fieldCode === field.fieldCode)
  if (!placement) return undefined
  const style: Record<string, string> = { width: `${placement.width}px`, minWidth: `${placement.width}px` }
  if (placement.fixed !== 'NONE') {
    style.position = 'sticky'
    style.zIndex = '2'
    style.background = '#fff'
    style[placement.fixed === 'LEFT' ? 'left' : 'right'] = '0'
  }
  return style
}

function detachAppliedView() {
  appliedViewId.value = ''
  invalidNodes.value = []
}

async function executeSearch() {
  const normalized = searchText.value.trim().replace(/\s+/gu, ' ')
  if (normalized && (normalized.length < 2 || normalized.length > 100)) {
    urlWarning.value = '搜索词需要 2 到 100 个字符。'
    return
  }
  searchText.value = normalized
  detachAppliedView()
  await loadRecords(1, true, 'push')
}

async function changeScope(value: string | number) {
  recordScope.value = String(value) as RuntimeRecordScope
  detachAppliedView()
  managedViewId.value = ''
  if (recordScope.value === 'draft') {
    filterNode.value = null
    sortItems.value = []
    queryDrawerOpen.value = false
    savedViewModalOpen.value = false
  }
  await loadRecords(1, true, 'push')
}

async function changePage(page: number) {
  await loadRecords(page, true, 'push')
}

async function changePageSize(current: number, size: number) {
  const firstVisible = (current - 1) * pageSize.value
  pageSize.value = size
  await loadRecords(Math.floor(firstVisible / size) + 1, true, 'push')
}

const operatorLabels: Record<string, string> = {
  EQ: '等于', NE: '不等于', CONTAINS: '包含', PREFIX: '开头是', EMPTY: '空值状态', GT: '大于', GTE: '大于等于',
  LT: '小于', LTE: '小于等于', BETWEEN: '介于', BEFORE: '早于', AFTER: '晚于', IN: '属于任一',
  HAS_ANY: '包含任一', HAS_ALL: '包含全部', NOT_ANY: '不含任一', OVERLAPS: '范围重叠',
  CONTAINS_NODE: '包含节点', LEAF_EQ: '末级等于', EQ_REGION: '地区等于', WITHIN_BOX: '矩形范围内',
  NEAR: '附近', DECLARED_PATH_EQ: '声明路径等于', DECLARED_PATH_EXISTS: '声明路径存在',
}

function newFilterRow(): FilterDraftRow {
  const field = filterableFields.value[0]
  const operator = field?.operators[0] ?? ''
  const draft = field ? readRuntimeFilterDraft(field, operator, undefined) : {
    value: '', secondValue: '', values: [], currency: 'CNY', empty: true, pathSnapshotId: '', params: {},
  }
  return {
    id: crypto.randomUUID(), fieldCode: field?.fieldCode ?? '', operator, ...draft, negate: false,
  }
}

function filterField(row: FilterDraftRow) {
  return schema.value?.fields.find((field) => field.fieldCode === row.fieldCode)
}

function filterFieldChanged(row: FilterDraftRow, code: string) {
  row.fieldCode = code
  const field = filterField(row)
  row.operator = field?.operators[0] ?? ''
  if (field) Object.assign(row, readRuntimeFilterDraft(field, row.operator, undefined))
}

function filterOperatorChanged(row: FilterDraftRow, operator: string) {
  row.operator = operator
  const field = filterField(row)
  if (field) Object.assign(row, readRuntimeFilterDraft(field, operator, undefined))
}

function multiValueOperator(row: FilterDraftRow) {
  return isRuntimeCollectionOperator(row.operator)
}

function rangeValueOperator(row: FilterDraftRow) {
  return isRuntimeRangeOperator(row.operator)
}

function optionValueOperator(row: FilterDraftRow) {
  const field = filterField(row)
  return ['EQ', 'CONTAINS_NODE', 'LEAF_EQ'].includes(row.operator) && Boolean(field?.options.length)
}

function tagValueOperator(row: FilterDraftRow) {
  return filterField(row)?.type === 'TAG' && multiValueOperator(row)
}

function moneyValueOperator(row: FilterDraftRow) {
  return filterField(row)?.type === 'MONEY'
}

function switchValueOperator(row: FilterDraftRow) {
  return filterField(row)?.type === 'SWITCH' && row.operator !== 'EMPTY'
}

function sensitiveValueOperator(row: FilterDraftRow) {
  const field = filterField(row)
  return row.operator !== 'EMPTY' && Boolean(field?.mode.startsWith('SENSITIVE'))
}

function numericValueOperator(row: FilterDraftRow) {
  return ['NUMBER', 'PERCENT', 'RATING', 'PROGRESS'].includes(filterField(row)?.type ?? '')
}

function addressRegionOperator(row: FilterDraftRow) {
  return filterField(row)?.type === 'ADDRESS' && row.operator === 'EQ_REGION'
}

function geoBoxOperator(row: FilterDraftRow) {
  return filterField(row)?.type === 'GEO' && row.operator === 'WITHIN_BOX'
}

function geoNearOperator(row: FilterDraftRow) {
  return filterField(row)?.type === 'GEO' && row.operator === 'NEAR'
}

function barcodeValueOperator(row: FilterDraftRow) {
  return filterField(row)?.type === 'BARCODE' && row.operator !== 'EMPTY'
}

function barcodeSymbologies(row: FilterDraftRow) {
  const configured = filterField(row)?.schema.symbologies
  return Array.isArray(configured) ? configured.map(String) : ['CODE128']
}

function jsonPathOperator(row: FilterDraftRow) {
  return filterField(row)?.type === 'JSON'
}

function jsonQueryPaths(row: FilterDraftRow) {
  const field = filterField(row)
  return field ? runtimeJsonQueryPaths(field) : []
}

function selectedJsonPath(row: FilterDraftRow) {
  return jsonQueryPaths(row).find((path) => path.pathSnapshotId === row.pathSnapshotId)
}

function jsonPathInputType(row: FilterDraftRow) {
  const type = selectedJsonPath(row)?.type
  if (type === 'DATE') return 'date'
  if (type === 'DATETIME') return 'datetime-local'
  if (type === 'DECIMAL' || type === 'INTEGER') return 'number'
  return 'text'
}

function filterCurrencies(row: FilterDraftRow) {
  const field = filterField(row)
  return field ? runtimeCurrencies(field) : []
}

function filterInputType(row: FilterDraftRow) {
  const type = filterField(row)?.type
  if (type === 'NUMBER') return 'number'
  if (['PERCENT', 'RATING', 'PROGRESS'].includes(type ?? '')) return 'number'
  if (type === 'DATE') return 'date'
  if (type === 'DATETIME') return 'datetime-local'
  if (type === 'TIME') return 'time'
  if (type === 'DATE_RANGE') return 'date'
  if (type === 'TIME_RANGE') return 'time'
  return 'text'
}

function rowFromPredicate(predicate: RuntimeFilterPredicate, negate = false): FilterDraftRow {
  const field = schema.value?.fields.find((item) => item.fieldCode === predicate.fieldCode)
  const draft = field ? readRuntimeFilterDraft(field, predicate.operator, predicate.value) : {
    value: '', secondValue: '', values: [], currency: 'CNY', empty: predicate.value !== false, pathSnapshotId: '', params: {},
  }
  return {
    id: crypto.randomUUID(),
    fieldCode: predicate.fieldCode,
    operator: predicate.operator,
    ...draft,
    negate,
  }
}

function flattenFilter(node: RuntimeFilterNode | null): FilterDraftRow[] | null {
  if (!node) return []
  if (node.kind === 'PREDICATE') return [rowFromPredicate(node)]
  if (node.kind === 'NOT' && node.children[0]?.kind === 'PREDICATE') {
    return [rowFromPredicate(node.children[0], true)]
  }
  if (node.kind !== 'AND' && node.kind !== 'OR') return null
  filterJoin.value = node.kind
  const rows: FilterDraftRow[] = []
  for (const child of node.children) {
    if (child.kind === 'PREDICATE') rows.push(rowFromPredicate(child))
    else if (child.kind === 'NOT' && child.children[0]?.kind === 'PREDICATE') rows.push(rowFromPredicate(child.children[0], true))
    else return null
  }
  return rows
}

function openQueryBuilder() {
  queryBuilderError.value = ''
  const flattened = flattenFilter(filterNode.value)
  if (flattened === null) {
    queryBuilderError.value = '当前视图包含更深的嵌套条件；应用此面板会用新条件替换它。'
    filterRows.value = []
  } else {
    filterRows.value = flattened
  }
  sortDraft.value = sortItems.value.map((item) => ({ ...item }))
  columnDraft.value = selectedColumns.value.length
    ? [...selectedColumns.value]
    : publishedListFields.value.map((field) => field.fieldCode)
  queryDrawerOpen.value = true
}

function addFilterRow() {
  if (filterRows.value.length < 20) filterRows.value.push(newFilterRow())
}

function removeFilterRow(id: string) {
  filterRows.value = filterRows.value.filter((row) => row.id !== id)
}

function addSortItem() {
  const used = new Set(sortDraft.value.map((item) => item.fieldCode))
  const field = sortableFields.value.find((item) => !used.has(item.fieldCode))
  if (field && sortDraft.value.length < (schema.value?.queryLimits.maxSorts ?? 3)) {
    const currency = field.type === 'MONEY' ? runtimeFixedCurrency(field) || runtimeCurrencies(field)[0] : undefined
    sortDraft.value.push({ fieldCode: field.fieldCode, direction: 'ASC', nulls: 'LAST', ...(currency ? { currency } : {}) })
  }
}

function sortField(item: RuntimeSortItem) {
  return sortableFields.value.find((field) => field.fieldCode === item.fieldCode)
}

function sortFieldChanged(item: RuntimeSortItem, fieldCode: string) {
  item.fieldCode = fieldCode
  const field = sortField(item)
  if (field?.type === 'MONEY') item.currency = runtimeFixedCurrency(field) || runtimeCurrencies(field)[0]
  else delete item.currency
}

function removeSortItem(index: number) {
  sortDraft.value.splice(index, 1)
}

function predicateFromRow(row: FilterDraftRow): RuntimeFilterNode {
  const field = filterField(row)
  if (!field || !field.operators.includes(row.operator)) throw new Error('筛选字段或操作符无效')
  const value = runtimePredicateValue(field, row.operator, row)
  const predicate: RuntimeFilterPredicate = { kind: 'PREDICATE', fieldCode: row.fieldCode, operator: row.operator, value }
  return row.negate ? { kind: 'NOT', children: [predicate] } : predicate
}

async function applyQueryBuilder() {
  queryBuilderError.value = ''
  try {
    const children = filterRows.value.map(predicateFromRow)
    filterNode.value = children.length === 0 ? null : children.length === 1 ? children[0]! : { kind: filterJoin.value, children }
    const uniqueSorts = new Set(sortDraft.value.map((item) => item.fieldCode))
    if (uniqueSorts.size !== sortDraft.value.length) throw new Error('排序字段不能重复')
    for (const item of sortDraft.value) {
      const field = sortField(item)
      if (!field) throw new Error('排序字段无效')
      if (field.type === 'MONEY') {
        const currencies = runtimeCurrencies(field)
        if (!item.currency || !currencies.includes(item.currency)) throw new Error(`${field.fieldName} 需要选择排序币种`)
      } else delete item.currency
    }
    if (!columnDraft.value.length) throw new Error('至少保留一个显示字段')
    sortItems.value = sortDraft.value.map((item) => ({ ...item }))
    selectedColumns.value = [...columnDraft.value]
    detachAppliedView()
    selectedFilterScenarioCode.value = ''
    queryDrawerOpen.value = false
    await loadRecords(1, true, 'push')
  } catch (error) {
    queryBuilderError.value = error instanceof Error ? error.message : '查询条件无效'
  }
}

async function selectSavedView(value: string | number | undefined) {
  selectedFilterScenarioCode.value = ''
  const viewId = String(value ?? '')
  if (!viewId) {
    const size = pageSize.value
    assignListState(defaultRuntimeListState(size))
    managedViewId.value = ''
    await loadRecords(1, true, 'push')
    return
  }
  const view = savedViews.value.find((item) => item.viewId === viewId)
  if (!view) return
  queryPage.value = 1
  pageSize.value = view.query.size
  hydrateSavedView(view)
  await loadRecords(1, true, 'push')
}

async function selectSharedFilterScenario(value: string | number | undefined) {
  const code = String(value ?? '')
  const scenario = sharedFilterScenarios.value.find((item) => item.code === code)
  if (code && !scenario) return
  const copied = scenario ? copyScenarioQuery(scenario) : { filter: null, sort: [] as RuntimeSortItem[] }
  filterNode.value = copied.filter
  sortItems.value = copied.sort
  selectedFilterScenarioCode.value = scenario?.code ?? ''
  appliedViewId.value = ''
  managedViewId.value = ''
  invalidNodes.value = []
  sanitizeListStateForSchema()
  await loadRecords(1, true, 'push')
}

function openSaveView(mode: 'create' | 'update') {
  savedViewError.value = filterContainsSensitive(filterNode.value) ? '敏感字段查询不能保存为视图或写入浏览器地址。' : ''
  savedViewMode.value = mode
  savedViewName.value = mode === 'update' ? activeSavedView.value?.name ?? '' : ''
  savedViewModalOpen.value = true
}

async function persistSavedView() {
  const name = savedViewName.value.trim()
  if (!name) { savedViewError.value = '视图名称不能为空。'; return }
  if (filterContainsSensitive(filterNode.value)) { savedViewError.value = '敏感字段查询不能保存为视图或写入浏览器地址。'; return }
  savedViewSaving.value = true
  savedViewError.value = ''
  try {
    const query = queryForSave()
    let saved: RuntimeSavedView
    if (savedViewMode.value === 'update') {
      const current = activeSavedView.value
      if (!current) throw new Error('要更新的保存视图不存在')
      saved = await runtimeApi.updateSavedView(systemId.value, current.viewId, {
        expectedVersion: current.version, name, query, columns: query.columns,
      })
      savedViews.value = savedViews.value.map((item) => item.viewId === saved.viewId ? saved : item)
    } else {
      saved = await runtimeApi.createSavedView(systemId.value, {
        moduleCode: selectedModule.value, name, query, columns: query.columns,
      })
      savedViews.value = [...savedViews.value, saved]
    }
    appliedViewId.value = saved.viewId
    managedViewId.value = saved.viewId
    savedViewModalOpen.value = false
    await loadRecords(1, true, 'push')
  } catch (error) {
    savedViewError.value = runtimeErrorMessage(error, '保存视图操作失败。')
  } finally {
    savedViewSaving.value = false
  }
}

function deleteSavedView() {
  const current = activeSavedView.value
  if (!current) return
  Modal.confirm({
    title: '删除保存视图',
    content: `确定删除“${current.name}”吗？`,
    okText: '删除',
    cancelText: '取消',
    okButtonProps: { danger: true },
    async onOk() {
      await runtimeApi.deleteSavedView(systemId.value, current.viewId, current.version)
      savedViews.value = savedViews.value.filter((item) => item.viewId !== current.viewId)
      assignListState(defaultRuntimeListState(pageSize.value))
      managedViewId.value = ''
      await loadRecords(1, true, 'push')
    },
  })
}

function repairFilter(node: RuntimeFilterNode | null): RuntimeFilterNode | null {
  if (!node) return null
  if (node.kind === 'PREDICATE') return fieldNodeSupported(node) ? node : null
  const children = node.children.map(repairFilter).filter((item): item is RuntimeFilterNode => Boolean(item))
  if (!children.length || node.kind === 'NOT' && children.length !== 1) return null
  return { kind: node.kind, children }
}

async function repairInvalidView() {
  const current = activeSavedView.value
  if (!current || !invalidNodes.value.length) return
  if (invalidNodes.value.includes('/q')) searchText.value = ''
  filterNode.value = repairFilter(filterNode.value)
  sortItems.value = sortItems.value.filter((item) => sortableFields.value.some((field) => field.fieldCode === item.fieldCode))
  selectedColumns.value = selectedColumns.value.filter((code) => schema.value?.fields.some((field) => field.fieldCode === code))
  appliedViewId.value = ''
  const query = queryForSave()
  try {
    const saved = await runtimeApi.updateSavedView(systemId.value, current.viewId, {
      expectedVersion: current.version, name: current.name, query, columns: query.columns,
    })
    savedViews.value = savedViews.value.map((item) => item.viewId === saved.viewId ? saved : item)
    appliedViewId.value = saved.viewId
    managedViewId.value = saved.viewId
    invalidNodes.value = []
    await loadRecords(1, true, 'push')
  } catch (error) {
    savedViewError.value = runtimeErrorMessage(error, '失效条件修复失败。')
  }
}

async function resetQuery() {
  const size = pageSize.value
  const scope = recordScope.value
  assignListState(defaultRuntimeListState(size))
  recordScope.value = scope
  managedViewId.value = ''
  selectedFilterScenarioCode.value = ''
  invalidNodes.value = []
  await loadRecords(1, true, 'push')
}

function confirmUnsavedChanges() {
  return !formDirty.value || window.confirm('当前有尚未保存的修改，确定离开吗？')
}

function beforeUnload(event: BeforeUnloadEvent) {
  if (!formDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

watch(selectedRecord, (recordId) => {
  if (!recordId || !detailOpen.value) {
    detail.value = null
    detailFlowStates.value = []
    detailError.value = ''
    detailFlowStateError.value = ''
    currentSortAnchor.value = null
    neighborBoundary.value = { PREVIOUS: false, NEXT: false }
    neighborError.value = ''
  } else if (recordId !== detail.value?.recordId && selectedModule.value) {
    if (currentSortAnchor.value?.recordId !== recordId) {
      currentSortAnchor.value = recordPage.value?.rows.find((row) => row.recordId === recordId)?.sortAnchor ?? null
      neighborBoundary.value = { PREVIOUS: false, NEXT: false }
    }
    loadDetail(recordId)
  }
})
watch([formOpen, selectedDraft, selectedRecord, schema], ([open]) => {
  if (open) void loadFormFromRoute()
  else resetForm()
})
watch([formTitle, formValues, relationDrafts, subtableDrafts], () => {
  if (suppressFormWatch || !formOpen.value) return
  formRevision.value += 1
  saveState.value = 'dirty'
  createError.value = ''
  conflictData.value = null
  scheduleAutosave()
}, { deep: true })
watch(detailOpen, (open) => {
  if (open && selectedRecord.value && selectedRecord.value !== detail.value?.recordId) {
    void loadDetail(selectedRecord.value)
  }
})
watch(batchSelectionContext, () => {
  selectedBatchRecords.value = new Map()
  batchCommandError.value = ''
  batchEditModalOpen.value = false
  batchEditFieldCode.value = ''
  batchEditValue.value = undefined
  batchEditError.value = ''
  transferModalOpen.value = false
  transferMemberId.value = ''
  transferError.value = ''
  importDrawerOpen.value = false
  exportDrawerOpen.value = false
  printDrawerOpen.value = false
  applyResultPanelFromRoute()
})
function applyResultPanelFromRoute() {
  const panel = String(route.query.panel ?? '')
  if (panel === 'import') importDrawerOpen.value = true
  if (panel === 'export') exportDrawerOpen.value = true
  if (panel === 'print') printDrawerOpen.value = true
}
watch(() => route.fullPath, async () => {
  applyResultPanelFromRoute()
  if (internalNavigation || !navigation.value) return
  const before = canonicalJson(currentListState())
  const targetModule = String(route.query.module ?? '')
  const invalid = restoreListState(route.query as Record<string, unknown>)
  if (targetModule && targetModule !== selectedModule.value) {
    await loadModule(targetModule, selectedMode.value === 'view' ? selectedRecord.value : '')
    return
  }
  if (!schema.value || before === canonicalJson(currentListState())) return
  const view = savedViews.value.find((item) => item.viewId === appliedViewId.value)
  if (view) hydrateSavedView(view, route.query as Record<string, unknown>)
  sanitizeListStateForSchema()
  await loadRecords(queryPage.value, false)
  if (invalid) await syncRoute()
})
watch(systemId, loadNavigation)
watch(systemId, () => {
  favoritePage.value = 1
  favoriteItems.value = []
  knownFavorites.value = new Map()
  recentPanelOpen.value = false
  recentPage.value = 1
  recentItems.value = []
  recentTotal.value = 0
  recentError.value = ''
  globalSearchPanelOpen.value = false
  globalSearchKeyword.value = ''
  globalSearchItems.value = []
  globalSearchPage.value = 1
  globalSearchTotal.value = 0
  globalSearchError.value = ''
  quickCreatePanelOpen.value = false
  quickCreateItems.value = []
  quickCreateError.value = ''
  void loadFavorites(1)
})
onBeforeRouteUpdate((to) => {
  if (internalNavigation || !formDirty.value) return true
  const sameForm = String(to.params.systemId) === systemId.value
    && String(to.query.module ?? '') === selectedModule.value
    && String(to.query.mode ?? '') === selectedMode.value
    && String(to.query.draft ?? '') === selectedDraft.value
    && String(to.query.record ?? '') === selectedRecord.value
  return sameForm || confirmUnsavedChanges()
})
onBeforeRouteLeave(() => confirmUnsavedChanges())
onMounted(() => {
  window.addEventListener('beforeunload', beforeUnload)
  applyResultPanelFromRoute()
  void loadNavigation()
  void loadFavorites(1)
})
onBeforeUnmount(() => {
  clearAutosaveTimer()
  window.removeEventListener('beforeunload', beforeUnload)
})
</script>

<template>
  <section class="runtime-workbench">
    <header class="runtime-heading">
      <div>
        <h1>{{ session.context?.systemName }}</h1>
        <span>{{ navigation ? `业务工作台 · 运行版本 V${navigation.versionNo}` : '业务工作台' }}</span>
      </div>
      <div class="runtime-heading-actions">
        <button class="quick-create-command" type="button" title="快速新建" aria-label="快速新建记录" @click="openQuickCreatePanel"><Plus :size="17" /></button>
        <button class="global-search-command" type="button" title="全局搜索" aria-label="打开全局搜索" @click="openGlobalSearchPanel"><Search :size="17" /></button>
        <button class="recent-panel-command" type="button" title="打开最近访问" aria-label="打开最近访问" @click="openRecentPanel"><Clock3 :size="17" /></button>
        <button v-if="canManageFavorites" class="favorites-panel-command" type="button" title="打开我的收藏" aria-label="打开我的收藏" @click="favoritesPanelOpen = true"><Star :size="17" /></button>
        <button type="button" title="刷新当前工作台" aria-label="刷新当前工作台" @click="loadNavigation"><RefreshCw :size="17" /></button>
      </div>
    </header>

    <a-alert v-if="navigationError" type="warning" show-icon :message="navigationError" />
    <a-spin :spinning="navigationLoading">
      <div v-if="navigation?.groups.length" class="runtime-mobile-selectors">
        <a-select :value="selectedGroup" aria-label="模块分组" @change="selectMobileGroup">
          <a-select-option v-for="group in navigation.groups" :key="group.id" :value="group.id">{{ group.name }}</a-select-option>
        </a-select>
        <a-select :value="selectedModule" aria-label="业务模块" @change="selectModule">
          <a-select-option v-for="item in navigation.groups.find((group) => group.id === selectedGroup)?.modules ?? []" :key="item.id" :value="item.code">{{ item.name }}</a-select-option>
        </a-select>
      </div>

      <div v-if="navigation?.groups.length" class="runtime-frame">
        <aside class="runtime-navigation">
          <div v-for="group in navigation.groups" :key="group.id" class="runtime-group">
            <h2>{{ group.name }}</h2>
            <button v-for="item in group.modules" :key="item.id" :class="{ active: selectedModule === item.code }" @click="selectModule(item.code)">
              <Blocks :size="16" /><span>{{ item.name }}</span><ChevronRight :size="14" />
            </button>
          </div>
        </aside>

        <main class="runtime-module">
          <template v-if="definition">
            <div class="runtime-module-title">
              <div><h2>{{ moduleName }}</h2><span>{{ moduleCode }}</span></div>
              <div class="module-facts">
                <RuntimeFavoriteToggle
                  v-if="canManageFavorites"
                  :favorite="currentModuleFavorite"
                  :loading="favoriteMutationKey === favoriteTargetKey('MODULE', selectedModule)"
                  label="当前模块"
                  @toggle="toggleFavorite('MODULE')"
                />
                <span>{{ schema?.fields.length ?? definition.fields.length }} 个可见字段</span>
                <a-tag color="green">已发布</a-tag>
              </div>
            </div>

            <a-alert v-if="contentError" class="content-alert" type="error" show-icon :message="contentError" />
            <a-alert v-if="urlWarning" class="content-alert" type="warning" show-icon closable :message="urlWarning" @close="urlWarning = ''" />
            <a-alert v-if="savedViewError && recordScope !== 'draft'" class="content-alert" type="error" show-icon closable :message="savedViewError" @close="savedViewError = ''" />
            <a-alert v-if="batchCommandError" class="content-alert" type="error" show-icon closable :message="batchCommandError" @close="batchCommandError = ''" />
            <a-alert v-if="favoriteError" class="content-alert" type="error" show-icon closable :message="favoriteError" @close="favoriteError = ''" />
            <a-alert v-if="invalidNodes.length" class="content-alert" type="warning" show-icon message="保存视图包含已失效条件" :description="`服务器已忽略 ${invalidNodes.length} 个失效节点，原视图尚未修改。`">
              <template #action><a-button v-if="activeSavedView" size="small" @click="repairInvalidView">确认修复并更新</a-button></template>
            </a-alert>
            <div v-if="!definition.recordsAvailable" class="runtime-empty-state">
              <AlertCircle :size="22" /><div><h3>记录读取暂不可用</h3><p>已发布结构包含当前运行版本尚未支持的必填字段。</p></div>
            </div>

            <template v-else>
              <div class="record-query-bar">
                <a-segmented :value="recordScope" :options="scopeOptions" @change="changeScope" />
                <a-input-search v-model:value="searchText" class="record-search" allow-clear :placeholder="recordScope === 'draft' ? '搜索草稿编号或标题' : '搜索可检索字段'" @search="executeSearch" />
                <a-select
                  v-if="sharedFilterScenarios.length && recordScope !== 'draft'"
                  :value="selectedFilterScenarioCode || undefined"
                  class="shared-filter-scenario-select"
                  allow-clear
                  placeholder="共享筛选方案"
                  aria-label="共享筛选方案"
                  @change="selectSharedFilterScenario"
                >
                  <a-select-option v-for="scenario in sharedFilterScenarios" :key="scenario.code" :value="scenario.code">{{ scenario.name }}</a-select-option>
                </a-select>
                <a-select
                  v-if="savedViewsAvailable && recordScope !== 'draft'"
                  :value="managedViewId || undefined"
                  class="saved-view-select"
                  allow-clear
                  :loading="savedViewsLoading"
                  placeholder="保存视图"
                  aria-label="保存视图"
                  @change="selectSavedView"
                >
                  <a-select-option v-for="view in savedViews" :key="view.viewId" :value="view.viewId">{{ view.name }}</a-select-option>
                </a-select>
                <div class="record-toolbar-actions">
                  <a-tooltip v-if="recordScope !== 'draft'" title="筛选、排序和显示列"><a-button aria-label="筛选、排序和显示列" @click="openQueryBuilder"><ListFilter :size="16" /></a-button></a-tooltip>
                  <a-tooltip v-if="savedViewsAvailable && recordScope !== 'draft'" title="保存为新视图"><a-button aria-label="保存为新视图" @click="openSaveView('create')"><BookmarkPlus :size="16" /></a-button></a-tooltip>
                  <a-tooltip v-if="activeSavedView && recordScope !== 'draft'" title="更新或重命名当前视图"><a-button aria-label="更新当前视图" @click="openSaveView('update')"><Save :size="16" /></a-button></a-tooltip>
                  <a-tooltip v-if="activeSavedView && recordScope !== 'draft'" title="删除当前视图"><a-button danger aria-label="删除当前视图" @click="deleteSavedView"><Trash2 :size="16" /></a-button></a-tooltip>
                  <a-button
                    v-if="canBatchEdit"
                    class="batch-edit-command"
                    :disabled="!selectedBatchItems.length || !eligibleBatchEditFields.length || Boolean(batchCommandLoading)"
                    :loading="batchCommandLoading === 'edit'"
                    @click="openBatchEdit"
                  >
                    <Pencil :size="16" />批量编辑{{ selectedBatchItems.length ? ` (${selectedBatchItems.length})` : '' }}
                  </a-button>
                  <a-button
                    v-if="canBatchTransfer"
                    class="batch-transfer-command"
                    :disabled="!selectedBatchItems.length || Boolean(batchCommandLoading)"
                    :loading="batchCommandLoading === 'transfer'"
                    @click="openBatchTransfer"
                  >
                    <UserRoundCheck :size="16" />批量转交{{ selectedBatchItems.length ? ` (${selectedBatchItems.length})` : '' }}
                  </a-button>
                  <a-button
                    v-if="canBatchArchive"
                    class="batch-archive-command"
                    :disabled="!selectedBatchItems.length || Boolean(batchCommandLoading)"
                    :loading="batchCommandLoading === 'archive'"
                    @click="requestBatchCommand('archive')"
                  >
                    <Archive :size="16" />批量归档{{ selectedBatchItems.length ? ` (${selectedBatchItems.length})` : '' }}
                  </a-button>
                  <a-button
                    v-if="canBatchTrash"
                    class="batch-trash-command"
                    danger
                    :disabled="!selectedBatchItems.length || Boolean(batchCommandLoading)"
                    :loading="batchCommandLoading === 'trash'"
                    @click="requestBatchCommand('trash')"
                  >
                    <Trash2 :size="16" />批量移入回收站{{ selectedBatchItems.length ? ` (${selectedBatchItems.length})` : '' }}
                  </a-button>
                  <a-button v-if="canImport" class="import-command" @click="importDrawerOpen = true">
                    <FileUp :size="16" />批量导入
                  </a-button>
                  <a-button v-if="canExport" class="export-command" @click="exportDrawerOpen = true">
                    <FileDown :size="16" />导出当前结果
                  </a-button>
                  <a-button v-if="canCreate" type="primary" @click="openCreate"><Plus :size="16" />新建记录</a-button>
                </div>
              </div>
              <div v-if="filterNode || sortItems.length || selectedColumns.length || searchText || managedViewId" class="query-summary">
                <a-tag v-if="activeSavedView" color="cyan">{{ activeSavedView.name }}</a-tag>
                <a-tag v-if="searchText">搜索：{{ searchText }}</a-tag>
                <a-tag v-if="filterNode">筛选已启用</a-tag>
                <a-tag v-if="sortItems.length">{{ sortItems.length }} 项排序</a-tag>
                <a-tag v-if="selectedColumns.length">{{ selectedColumns.length }} 个字段</a-tag>
                <a-button type="link" size="small" @click="resetQuery"><X :size="14" />清除条件</a-button>
              </div>

              <a-spin :spinning="contentLoading">
                <template v-if="recordPage?.rows.length">
                  <div class="record-table-wrap">
                    <table class="record-table" :class="`published-density-${listPage?.density?.toLowerCase() ?? 'default'}`" :data-published-page="listPage?.pageCode">
                      <thead><tr><th v-if="canBatchSelect" class="selection-cell"><input type="checkbox" aria-label="选择当前页全部记录" :checked="allVisibleBatchRowsSelected" @change="toggleVisibleBatchRecords(($event.target as HTMLInputElement).checked)"></th><th>记录编号</th><th>标题</th><th v-for="field in displayedListFields" :key="field.fieldCode" :style="listColumnStyle(field)" :data-fixed="listPage?.fields.find((item) => item.fieldCode === field.fieldCode)?.fixed">{{ field.fieldName }}</th><th>状态</th><th><span class="sr-only">操作</span></th></tr></thead>
                      <tbody>
                        <tr v-for="record in recordPage.rows" :key="record.recordId">
                          <td v-if="canBatchSelect" class="selection-cell"><input type="checkbox" :aria-label="`选择记录 ${record.recordNo}`" :checked="selectedBatchRecords.has(record.recordId)" @change="toggleBatchRecord(record.recordId, record.version, ($event.target as HTMLInputElement).checked)"></td>
                          <td><button class="record-link" @click="openRecord(record.recordId)">{{ record.recordNo }}</button></td>
                          <td>{{ record.title || '-' }}</td>
                          <td v-for="field in displayedListFields" :key="field.fieldCode" :style="listColumnStyle(field)" :data-fixed="listPage?.fields.find((item) => item.fieldCode === field.fieldCode)?.fixed">{{ runtimeValueByCode(record.values, field.fieldCode) }}</td>
                          <td><a-tag :color="statusColor(record.status)">{{ runtimeStatusLabel(record.status) }}</a-tag></td>
                          <td><button class="icon-command" type="button" title="查看详情" @click="openRecord(record.recordId)"><Eye :size="16" /></button></td>
                        </tr>
                      </tbody>
                    </table>
                  </div>

                  <div class="record-mobile-list">
                    <button v-for="record in recordPage.rows" :key="record.recordId" class="record-mobile-item" @click="openRecord(record.recordId)">
                      <span class="mobile-record-head"><strong>{{ record.recordNo }}</strong><a-tag :color="statusColor(record.status)">{{ runtimeStatusLabel(record.status) }}</a-tag></span>
                      <span class="mobile-record-title">{{ record.title || '-' }}</span>
                      <span v-for="field in displayedListFields.slice(0, 2)" :key="field.fieldCode" class="mobile-field"><small>{{ field.fieldName }}</small>{{ runtimeValueByCode(record.values, field.fieldCode) }}</span>
                    </button>
                  </div>

                  <a-pagination
                    class="record-pagination"
                    :current="recordPage.page"
                    :page-size="recordPage.size"
                    :total="recordPage.total"
                    :show-size-changer="true"
                    :page-size-options="['20', '50', '100', '200']"
                    :show-less-items="true"
                    @change="changePage"
                    @show-size-change="changePageSize"
                  />
                </template>
                <a-empty v-else-if="!contentLoading && !contentError" description="当前状态下还没有记录" />
              </a-spin>
            </template>
          </template>
          <a-empty v-else-if="!contentLoading" description="选择一个有权限的模块" />
        </main>
      </div>
      <a-empty v-else-if="!navigationLoading && !navigationError" description="当前身份没有可访问的已发布模块" />
    </a-spin>

    <a-drawer
      :open="queryDrawerOpen"
      title="筛选、排序和显示列"
      width="min(680px, 100vw)"
      class="query-drawer"
      @close="queryDrawerOpen = false"
    >
      <div class="query-builder">
        <a-alert v-if="queryBuilderError" type="warning" show-icon :message="queryBuilderError" />
        <section class="query-builder-section">
          <div class="query-section-heading">
            <div><h3>筛选条件</h3><span>最多 20 项，可组合 AND、OR 和单项 NOT</span></div>
            <a-segmented v-model:value="filterJoin" :options="[{ label: '全部满足', value: 'AND' }, { label: '任一满足', value: 'OR' }]" />
          </div>
          <div v-for="row in filterRows" :key="row.id" class="filter-row">
            <a-select :value="row.fieldCode" aria-label="筛选字段" @change="(value: string) => filterFieldChanged(row, value)">
              <a-select-option v-for="field in filterableFields" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}</a-select-option>
            </a-select>
            <a-select :value="row.operator" aria-label="筛选操作符" @change="(value: string) => filterOperatorChanged(row, value)">
              <a-select-option v-for="operator in filterField(row)?.operators ?? []" :key="operator" :value="operator">{{ operatorLabels[operator] ?? operator }}</a-select-option>
            </a-select>
            <a-select v-if="row.operator === 'EMPTY'" v-model:value="row.empty" class="filter-value" aria-label="空值匹配方式">
              <a-select-option :value="true">匹配空值</a-select-option>
              <a-select-option :value="false">匹配非空值</a-select-option>
            </a-select>
            <div v-else-if="addressRegionOperator(row)" class="filter-structured filter-address">
              <a-input v-model:value="row.value" :maxlength="2" aria-label="筛选国家代码" placeholder="国家代码" />
              <a-input v-model:value="row.secondValue" aria-label="筛选地区代码" placeholder="地区代码" />
            </div>
            <div v-else-if="geoBoxOperator(row)" class="filter-structured filter-geo-box">
              <a-input-number v-model:value="row.params.south" :min="-90" :max="90" aria-label="南纬边界" placeholder="南" />
              <a-input-number v-model:value="row.params.west" :min="-180" :max="180" aria-label="西经边界" placeholder="西" />
              <a-input-number v-model:value="row.params.north" :min="-90" :max="90" aria-label="北纬边界" placeholder="北" />
              <a-input-number v-model:value="row.params.east" :min="-180" :max="180" aria-label="东经边界" placeholder="东" />
            </div>
            <div v-else-if="geoNearOperator(row)" class="filter-structured filter-geo-near">
              <a-input-number v-model:value="row.params.lat" :min="-90" :max="90" aria-label="中心纬度" placeholder="纬度" />
              <a-input-number v-model:value="row.params.lng" :min="-180" :max="180" aria-label="中心经度" placeholder="经度" />
              <a-input-number v-model:value="row.params.radiusMeters" :min="1" :max="500000" aria-label="半径米" placeholder="半径(米)" />
            </div>
            <div v-else-if="barcodeValueOperator(row)" class="filter-structured filter-barcode">
              <a-select v-model:value="row.currency" aria-label="筛选条码制式"><a-select-option v-for="value in barcodeSymbologies(row)" :key="value" :value="value">{{ value }}</a-select-option></a-select>
              <a-input v-model:value="row.value" aria-label="筛选条码内容" placeholder="条码内容" />
            </div>
            <div v-else-if="jsonPathOperator(row)" class="filter-structured filter-json-path">
              <a-select v-model:value="row.pathSnapshotId" aria-label="JSON 声明路径"><a-select-option v-for="path in jsonQueryPaths(row)" :key="path.pathSnapshotId" :value="path.pathSnapshotId">{{ path.path }}</a-select-option></a-select>
              <a-select v-if="row.operator === 'DECLARED_PATH_EQ' && selectedJsonPath(row)?.type === 'BOOLEAN'" v-model:value="row.value" aria-label="JSON 路径值"><a-select-option :value="true">是</a-select-option><a-select-option :value="false">否</a-select-option></a-select>
              <input v-else-if="row.operator === 'DECLARED_PATH_EQ'" v-model="row.value" class="native-field" :type="jsonPathInputType(row)" aria-label="JSON 路径值">
            </div>
            <a-select v-else-if="tagValueOperator(row)" v-model:value="row.values" mode="tags" :token-separators="[',']" class="filter-value" placeholder="输入一个或多个标签" aria-label="筛选标签" />
            <a-select v-else-if="multiValueOperator(row)" v-model:value="row.values" mode="multiple" class="filter-value" placeholder="选择一个或多个值" aria-label="筛选多选值">
              <a-select-option v-for="option in filterField(row)?.options ?? []" :key="option.value" :value="option.value">{{ option.label }}</a-select-option>
            </a-select>
            <div v-else-if="moneyValueOperator(row) && rangeValueOperator(row)" class="filter-money filter-money-range">
              <a-select v-model:value="row.currency" aria-label="筛选币种"><a-select-option v-for="currency in filterCurrencies(row)" :key="currency" :value="currency">{{ currency }}</a-select-option></a-select>
              <a-input v-model:value="row.value" inputmode="decimal" aria-label="筛选起始金额" />
              <span>至</span>
              <a-input v-model:value="row.secondValue" inputmode="decimal" aria-label="筛选结束金额" />
            </div>
            <div v-else-if="moneyValueOperator(row)" class="filter-money">
              <a-select v-model:value="row.currency" aria-label="筛选币种"><a-select-option v-for="currency in filterCurrencies(row)" :key="currency" :value="currency">{{ currency }}</a-select-option></a-select>
              <a-input v-model:value="row.value" inputmode="decimal" aria-label="筛选金额" />
            </div>
            <a-select v-else-if="switchValueOperator(row)" v-model:value="row.value" class="filter-value" aria-label="筛选开关值">
              <a-select-option :value="true">是</a-select-option><a-select-option :value="false">否</a-select-option>
            </a-select>
            <a-input-password v-else-if="sensitiveValueOperator(row)" v-model:value="row.value" class="filter-value" autocomplete="off" :visibility-toggle="false" aria-label="敏感字段筛选值" />
            <a-select v-else-if="optionValueOperator(row)" v-model:value="row.value" class="filter-value" placeholder="选择值" aria-label="筛选值">
              <a-select-option v-for="option in filterField(row)?.options ?? []" :key="option.value" :value="option.value">{{ option.label }}</a-select-option>
            </a-select>
            <div v-else-if="rangeValueOperator(row)" class="filter-range">
              <input v-model="row.value" class="native-field" :type="filterInputType(row)" :step="filterInputType(row) === 'time' ? 1 : undefined" aria-label="筛选起始值">
              <span>至</span>
              <input v-model="row.secondValue" class="native-field" :type="filterInputType(row)" :step="filterInputType(row) === 'time' ? 1 : undefined" aria-label="筛选结束值">
            </div>
            <a-input-number v-else-if="numericValueOperator(row)" v-model:value="row.value" class="filter-value" aria-label="筛选值" />
            <input v-else-if="['date', 'datetime-local', 'time'].includes(filterInputType(row))" v-model="row.value" class="native-field filter-value" :type="filterInputType(row)" :step="filterInputType(row) === 'time' ? 1 : undefined" aria-label="筛选值">
            <a-input v-else v-model:value="row.value" class="filter-value" aria-label="筛选值" />
            <a-checkbox v-model:checked="row.negate">排除</a-checkbox>
            <a-tooltip title="删除条件"><a-button danger aria-label="删除筛选条件" @click="removeFilterRow(row.id)"><Trash2 :size="15" /></a-button></a-tooltip>
          </div>
          <a-button :disabled="filterRows.length >= 20 || !filterableFields.length" @click="addFilterRow"><Plus :size="15" />添加条件</a-button>
        </section>

        <section class="query-builder-section">
          <div class="query-section-heading"><div><h3>排序</h3><span>按顺序执行，最多 {{ schema?.queryLimits.maxSorts ?? 3 }} 项</span></div></div>
          <div v-for="(item, index) in sortDraft" :key="`${item.fieldCode}-${index}`" class="sort-row">
            <a-select :value="item.fieldCode" aria-label="排序字段" @change="(value: string) => sortFieldChanged(item, value)">
              <a-select-option v-for="field in sortableFields" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}</a-select-option>
            </a-select>
            <a-select v-model:value="item.direction" aria-label="排序方向"><a-select-option value="ASC">升序</a-select-option><a-select-option value="DESC">降序</a-select-option></a-select>
            <a-select v-model:value="item.nulls" aria-label="空值顺序"><a-select-option value="LAST">空值在后</a-select-option><a-select-option value="FIRST">空值在前</a-select-option></a-select>
            <a-select v-if="sortField(item)?.type === 'MONEY'" v-model:value="item.currency" aria-label="排序币种">
              <a-select-option v-for="currency in runtimeCurrencies(sortField(item)!)" :key="currency" :value="currency">{{ currency }}</a-select-option>
            </a-select>
            <a-tooltip title="删除排序"><a-button danger aria-label="删除排序" @click="removeSortItem(index)"><Trash2 :size="15" /></a-button></a-tooltip>
          </div>
          <a-button :disabled="sortDraft.length >= (schema?.queryLimits.maxSorts ?? 3) || sortDraft.length >= sortableFields.length" @click="addSortItem"><Plus :size="15" />添加排序</a-button>
        </section>

        <section class="query-builder-section">
          <div class="query-section-heading"><div><h3>显示列</h3><span>桌面表格和移动卡片使用同一字段集合</span></div></div>
          <a-checkbox-group v-model:value="columnDraft" class="column-options">
            <a-checkbox v-for="field in schema?.fields ?? []" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}</a-checkbox>
          </a-checkbox-group>
        </section>
      </div>
      <template #footer>
        <div class="query-drawer-footer"><a-button @click="queryDrawerOpen = false">取消</a-button><a-button type="primary" @click="applyQueryBuilder">应用查询</a-button></div>
      </template>
    </a-drawer>

    <a-modal
      :open="savedViewModalOpen"
      :title="savedViewModalTitle"
      :confirm-loading="savedViewSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="persistSavedView"
      @cancel="savedViewModalOpen = false"
    >
      <a-alert v-if="savedViewError" class="saved-view-modal-error" type="error" show-icon :message="savedViewError" />
      <label class="saved-view-name"><span>视图名称</span><a-input v-model:value="savedViewName" :maxlength="100" autofocus @press-enter="persistSavedView" /></label>
    </a-modal>

    <a-drawer :open="formOpen" :title="formHeading" :width="formDrawerWidth" @close="closeForm">
      <form class="runtime-form" @keydown="preventTagFormSubmit" @submit.prevent="requestSave('manual')">
        <a-alert v-if="createError" type="error" show-icon :message="createError" />
        <div class="runtime-save-state" :data-state="saveState" aria-live="polite">
          <RefreshCw v-if="saveState === 'saving'" :size="16" class="spin" />
          <AlertCircle v-else-if="saveState === 'error' || saveState === 'conflict'" :size="16" />
          <Check v-else-if="saveState === 'saved'" :size="16" />
          <span>{{ saveStateText }}</span>
        </div>

        <a-spin :spinning="!formReady">
        <div v-if="formReady" class="runtime-form-fields">
        <div v-if="conflictData" class="runtime-conflict" role="alert">
          <strong>服务端已有较新版本</strong>
          <span>冲突字段：{{ conflictData.conflictFields.join('、') }}</span>
          <div>
            <a-button type="button" @click="reloadConflict"><RotateCcw :size="16" />重新加载服务端草稿</a-button>
            <a-button v-if="canCreate" type="button" @click="copyConflictAsDraft"><Copy :size="16" />复制为新草稿</a-button>
          </div>
        </div>

        <div class="runtime-form-field">
          <label for="runtime-record-title">记录标题</label>
          <a-input
            id="runtime-record-title"
            v-model:value="formTitle"
            :maxlength="500"
            placeholder="记录标题"
            @blur="autosaveOnBlur"
          />
          <span v-if="fieldErrors.title" class="field-error">{{ fieldErrors.title }}</span>
        </div>

        <details
          v-for="group in formFieldGroups"
          :key="group.key"
          class="published-form-section"
          :open="!group.collapsible || !group.collapsed"
        >
          <summary v-if="group.title">{{ group.title }}</summary>
          <div
            class="published-form-grid"
            :style="{ gridTemplateColumns: `repeat(${group.columns}, minmax(0, 1fr))`, gap: `${formPage?.gap ?? 16}px` }"
          >
        <div v-for="field in group.fields" :key="field.fieldCode" class="runtime-form-field" :class="{ 'composition-form-field': ['RELATION', 'REFERENCE', 'SUBTABLE'].includes(field.type), 'published-rule-readonly': fieldReadonly(field) }" :style="formFieldStyle(field, group.columns)">
          <label :for="`runtime-field-${field.fieldCode}`">
            {{ field.fieldName }}<span v-if="fieldRequired(field)" class="required-mark">*</span>
          </label>
          <RuntimeRelationField
            v-if="field.type === 'RELATION'"
            :system-id="systemId"
            :module-code="selectedModule"
            :target-module-code="targetModuleCode(field)"
            :schema-version-id="schema?.schemaVersionId ?? ''"
            :field="field"
            :record-id="formRecord?.recordId"
            :record-version="formRecord?.version"
            :model-value="relationDrafts[field.fieldCode]"
            :disabled="createLoading || Boolean(conflictData) || fieldReadonly(field)"
            @update:model-value="(value) => updateRelationDraft(field.fieldCode, value)"
            @mutated="compositionMutated"
          />
          <RuntimeSubtableField
            v-else-if="field.type === 'SUBTABLE'"
            :system-id="systemId"
            :module-code="selectedModule"
            :target-module-code="targetModuleCode(field)"
            :field="field"
            :record-id="formRecord?.recordId"
            :record-version="formRecord?.version"
            :model-value="subtableDrafts[field.fieldCode]"
            :disabled="createLoading || Boolean(conflictData) || fieldReadonly(field)"
            @update:model-value="(value) => updateSubtableDraft(field.fieldCode, value)"
            @mutated="compositionMutated"
          />
          <RuntimeReferenceField
            v-else-if="field.type === 'REFERENCE'"
            :system-id="systemId"
            :module-code="selectedModule"
            :record-id="formRecord?.recordId"
            :record-version="formRecord?.version"
            :field="field"
            :value="formStoredValues[field.fieldCode]"
            :can-retry="Boolean(formStoredValues[field.fieldCode]?.value && typeof formStoredValues[field.fieldCode]?.value === 'object' && formRecord?.actions.includes('UPDATE'))"
            @refreshed="referenceRefreshed"
          />
          <RuntimeFieldDisplay
            v-else-if="fieldReadonly(field)"
            :field="formStoredValues[field.fieldCode]"
          />
          <RuntimeFieldInput
            v-else
            :input-id="`runtime-field-${field.fieldCode}`"
            :field="field"
            :model-value="formValues[field.fieldCode]"
            :stored-value="formStoredValues[field.fieldCode]"
            @update:model-value="(value) => updateFormValue(field.fieldCode, value)"
            @clear="clearStoredField(field.fieldCode)"
            @blur="autosaveOnBlur"
          />
          <span v-if="fieldErrors[field.fieldCode]" class="field-error">{{ fieldErrors[field.fieldCode] }}</span>
        </div>
          </div>
        </details>
        </div>
        </a-spin>

        <footer class="runtime-form-actions">
          <a-button
            v-if="formRecord?.actions.includes('DISCARD')"
            class="discard-action"
            type="button"
            danger
            :disabled="!formReady || createLoading || Boolean(lifecycleLoading) || Boolean(conflictData)"
            @click="requestLifecycle('DISCARD', formRecord)"
          >
            <Trash2 :size="16" />放弃草稿
          </a-button>
          <a-button type="button" :disabled="createLoading" @click="closeForm">取消</a-button>
          <a-button v-if="saveState === 'error'" type="button" :disabled="!formReady || createLoading" @click="retrySave">
            <RotateCcw :size="16" />重试
          </a-button>
          <a-button
            :type="!formRecord || formRecord.status === 'ACTIVE' ? 'primary' : 'default'"
            html-type="submit"
            :loading="createLoading"
            :disabled="!formReady || Boolean(conflictData)"
          >
            <Save :size="16" />{{ formRecord?.status === 'ACTIVE' ? '保存修改' : '保存草稿' }}
          </a-button>
          <a-button
            v-if="formRecord?.status === 'DRAFT'"
            type="primary"
            :loading="activateLoading"
            :disabled="!formReady || createLoading || Boolean(conflictData)"
            @click="activateDraft"
          >
            <Rocket :size="16" />激活记录
          </a-button>
        </footer>
      </form>
    </a-drawer>

    <a-drawer :open="detailOpen" :title="detail?.recordNo ?? '记录详情'" :width="detailDrawerWidth" @close="closeDetail">
      <a-spin :spinning="detailLoading">
        <a-alert v-if="detailError" type="error" show-icon :message="detailError" />
        <a-alert v-if="neighborError" type="error" show-icon closable :message="neighborError" @close="neighborError = ''" />
        <template v-if="detail">
          <a-alert
            v-if="detail.status === 'EXPIRED'"
            class="detail-state-alert"
            type="warning"
            show-icon
            message="草稿已过期"
            description="字段内容仍然保留。恢复后可继续编辑和自动保存。"
          />
          <a-alert
            v-else-if="detail.status === 'TRASHED'"
            class="detail-state-alert"
            type="info"
            show-icon
            message="记录在回收站中"
            description="恢复后将回到移入回收站前的状态。"
          />
          <div class="detail-heading">
            <h3>{{ detail.title || detail.recordNo }}</h3>
            <div class="detail-actions">
              <a-button
                size="small"
                title="当前查询中的上一条记录"
                :loading="neighborLoading === 'PREVIOUS'"
                :disabled="!currentSortAnchor || !recordPage?.querySnapshotToken || neighborBoundary.PREVIOUS || Boolean(neighborLoading)"
                @click="navigateNeighbor('PREVIOUS')"
              >
                <ChevronLeft :size="15" />上一条
              </a-button>
              <a-button
                size="small"
                title="当前查询中的下一条记录"
                :loading="neighborLoading === 'NEXT'"
                :disabled="!currentSortAnchor || !recordPage?.querySnapshotToken || neighborBoundary.NEXT || Boolean(neighborLoading)"
                @click="navigateNeighbor('NEXT')"
              >
                下一条<ChevronRight :size="15" />
              </a-button>
              <a-tag :color="statusColor(detail.status)">{{ runtimeStatusLabel(detail.status) }}</a-tag>
              <RuntimeFavoriteToggle
                v-if="canManageFavorites"
                :favorite="currentRecordFavorite"
                :loading="favoriteMutationKey === favoriteTargetKey('RECORD', selectedModule, detail.recordId)"
                label="当前记录"
                @toggle="toggleFavorite('RECORD')"
              />
              <button v-if="detail.actions.includes('UPDATE')" class="icon-command" type="button" title="编辑记录" @click="openEdit(detail)"><Pencil :size="16" /></button>
            </div>
          </div>
          <div
            v-if="canStartRecordFlow || detail.actions.some((action) => ['ARCHIVE', 'UNARCHIVE', 'TRASH', 'RESTORE_TRASH', 'RECOVER_DRAFT', 'PRINT'].includes(action))"
            class="detail-command-bar"
          >
            <a-button
              v-if="canStartRecordFlow"
              class="record-flow-start"
              type="primary"
              :disabled="recordFlowPending"
              :title="recordFlowPending ? '该记录已有待审批流程' : '发起审批'"
              @click="openRecordFlowStart"
            >
              <Send :size="16" />发起审批
            </a-button>
            <a-button v-if="detail.actions.includes('PRINT')" class="record-print-command" @click="printDrawerOpen = true">
              <Printer :size="16" />打印
            </a-button>
            <a-button v-if="detail.actions.includes('ARCHIVE')" :disabled="Boolean(lifecycleLoading)" @click="requestLifecycle('ARCHIVE', detail)">
              <Archive :size="16" />归档
            </a-button>
            <a-button v-if="detail.actions.includes('UNARCHIVE')" :disabled="Boolean(lifecycleLoading)" @click="requestLifecycle('UNARCHIVE', detail)">
              <ArchiveRestore :size="16" />取消归档
            </a-button>
            <a-button v-if="detail.actions.includes('RESTORE_TRASH')" type="primary" :disabled="Boolean(lifecycleLoading)" @click="requestLifecycle('RESTORE_TRASH', detail)">
              <RotateCcw :size="16" />恢复
            </a-button>
            <a-button v-if="detail.actions.includes('RECOVER_DRAFT')" type="primary" :disabled="Boolean(lifecycleLoading)" @click="requestLifecycle('RECOVER_DRAFT', detail)">
              <RotateCcw :size="16" />恢复草稿
            </a-button>
            <a-button v-if="detail.actions.includes('TRASH')" danger :disabled="Boolean(lifecycleLoading)" @click="requestLifecycle('TRASH', detail)">
              <Trash2 :size="16" />移入回收站
            </a-button>
          </div>
          <a-alert
            v-if="detailFlowStateError"
            class="record-flow-state-error"
            type="error"
            show-icon
            :message="detailFlowStateError"
          />
          <dl class="detail-list">
            <div><dt>记录编号</dt><dd>{{ detail.recordNo }}</dd></div>
            <template v-if="detailFlowStates.length">
              <template v-for="(flowState, index) in detailFlowStates" :key="flowState.instanceId">
                <div class="record-flow-state"><dt>审批 {{ index + 1 }} 状态</dt><dd>{{ flowState.status }}</dd></div>
                <div><dt>审批 {{ index + 1 }} 实例</dt><dd>{{ flowState.instanceId }}</dd></div>
                <div><dt>审批 {{ index + 1 }} 状态版本</dt><dd>{{ flowState.version }}</dd></div>
                <div><dt>审批 {{ index + 1 }} 状态更新时间</dt><dd>{{ flowState.updatedAt }}</dd></div>
              </template>
            </template>
            <div v-else-if="!detailFlowStateError" class="record-flow-state-empty">
              <dt>审批状态</dt><dd>未绑定审批流程</dd>
            </div>
            <details
              v-for="group in detailFieldGroups"
              :key="group.key"
              class="published-detail-section"
              :open="!group.collapsible || !group.collapsed"
            >
              <summary v-if="group.title">{{ group.title }}</summary>
              <div class="published-detail-grid" :style="{ gridTemplateColumns: `repeat(${group.columns}, minmax(0, 1fr))`, gap: `${detailPage?.gap ?? 16}px` }">
            <div v-for="field in group.fields" :key="field.fieldCode" :class="{ 'composition-detail': ['RELATION', 'SUBTABLE'].includes(field.type) }" :style="detailFieldStyle(field, group.columns)"><dt>{{ field.fieldName }}</dt><dd>
              <RuntimeRelationField v-if="field.type === 'RELATION'" :system-id="systemId" :module-code="selectedModule" :target-module-code="targetModuleCode(field)" :schema-version-id="schema?.schemaVersionId ?? ''" :field="field" :record-id="detail.recordId" :record-version="detail.version" readonly />
              <RuntimeSubtableField v-else-if="field.type === 'SUBTABLE'" :system-id="systemId" :module-code="selectedModule" :target-module-code="targetModuleCode(field)" :field="field" :record-id="detail.recordId" :record-version="detail.version" readonly />
              <RuntimeReferenceField v-else-if="field.type === 'REFERENCE'" :system-id="systemId" :module-code="selectedModule" :record-id="detail.recordId" :record-version="detail.version" :field="field" :value="detailValue(field.fieldCode)" :can-retry="detail.actions.includes('UPDATE')" @refreshed="referenceRefreshed" />
              <RuntimeDerivedField v-else-if="['FORMULA','SUMMARY','CALCULATED','LOOKUP','AGGREGATE'].includes(field.type)" :system-id="systemId" :module-code="selectedModule" :record-id="detail.recordId" :record-version="detail.version" :field="field" :value="detailValue(field.fieldCode)" :can-retry="detail.actions.includes('UPDATE')" @refreshed="referenceRefreshed" />
              <RuntimeAiFillField v-else-if="field.type === 'AI_FILL'" :system-id="systemId" :module-code="selectedModule" :record-id="detail.recordId" :record-version="detail.version" :field="field" :value="detailValue(field.fieldCode)" @materialized="aiFillMaterialized" />
              <RuntimeFieldDisplay v-else :field="detailValue(field.fieldCode)" />
            </dd></div>
              </div>
            </details>
          </dl>
          <RecordCommentPanel
            :system-id="systemId"
            :module-code="selectedModule"
            :record-id="detail.recordId"
          />
          <RecordHistoryPanel
            :system-id="systemId"
            :module-code="selectedModule"
            :record-id="detail.recordId"
          />
          <RecordFilePanel
            :system-id="systemId"
            :module-code="selectedModule"
            :record-id="detail.recordId"
          />
          <RecordTeamPanel
            :system-id="systemId"
            :module-code="selectedModule"
            :record-id="detail.recordId"
          />
        </template>
      </a-spin>
    </a-drawer>

    <a-modal
      :open="batchEditModalOpen"
      title="批量编辑记录"
      :confirm-loading="batchCommandLoading === 'edit'"
      :mask-closable="false"
      ok-text="下一步：确认"
      cancel-text="取消"
      @ok="requestBatchEditConfirmation"
      @cancel="closeBatchEdit"
    >
      <div class="batch-edit-form">
        <p>本次将原子修改已明确选择的 {{ selectedBatchItems.length }} 条活动记录，每次提交一个普通字段。</p>
        <template v-if="eligibleBatchEditFields.length">
          <label for="batch-edit-field">字段</label>
          <select
            id="batch-edit-field"
            class="native-field"
            aria-label="批量编辑字段"
            :value="batchEditFieldCode"
            :disabled="batchCommandLoading === 'edit'"
            @change="selectBatchEditField(($event.target as HTMLSelectElement).value)"
          >
            <option v-for="field in eligibleBatchEditFields" :key="field.fieldCode" :value="field.fieldCode">
              {{ field.fieldName }}
            </option>
          </select>

          <label for="batch-edit-operation">操作</label>
          <select
            id="batch-edit-operation"
            class="native-field"
            aria-label="批量编辑操作"
            :value="batchEditOperation"
            :disabled="batchCommandLoading === 'edit'"
            @change="selectBatchEditOperation(($event.target as HTMLSelectElement).value as 'SET' | 'CLEAR')"
          >
            <option value="SET">设置字段值</option>
            <option value="CLEAR">清空字段值</option>
          </select>

          <template v-if="batchEditOperation === 'SET' && batchEditSelectedField">
            <label :for="`batch-edit-value-${batchEditSelectedField.fieldCode}`">
              {{ batchEditSelectedField.fieldName }}的新值
            </label>
            <select
              v-if="batchEditSelectedField.type === 'STATUS'"
              :id="`batch-edit-value-${batchEditSelectedField.fieldCode}`"
              class="native-field"
              aria-label="批量编辑字段值"
              :value="typeof batchEditValue === 'string' ? batchEditValue : ''"
              @change="batchEditValue = ($event.target as HTMLSelectElement).value; batchEditError = ''"
            >
              <option value="" disabled>请选择状态</option>
              <option v-for="option in batchEditSelectedField.options" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
            <RuntimeFieldInput
              v-else
              :input-id="`batch-edit-value-${batchEditSelectedField.fieldCode}`"
              :field="batchEditSelectedField"
              :model-value="batchEditValue"
              @update:model-value="batchEditValue = $event; batchEditError = ''"
              @clear="batchEditValue = undefined"
            />
          </template>
          <a-alert
            v-else-if="batchEditSelectedField?.schema.required === true"
            type="warning"
            show-icon
            message="该字段为必填字段，清空操作将由服务器校验并可能拒绝整批修改。"
          />
        </template>
        <a-empty v-else description="当前模块没有可批量编辑的普通字段" />
        <a-alert v-if="batchEditError" type="error" show-icon :message="batchEditError" />
      </div>
    </a-modal>

    <a-modal
      :open="transferModalOpen"
      title="批量转交记录"
      :confirm-loading="batchCommandLoading === 'transfer'"
      :mask-closable="false"
      ok-text="下一步：确认"
      cancel-text="取消"
      :ok-button-props="{ disabled: !transferMemberId }"
      @ok="requestBatchTransferConfirmation"
      @cancel="closeBatchTransfer"
    >
      <div class="batch-transfer-form">
        <p>将原子转交已明确选择的 {{ selectedBatchItems.length }} 条活动记录。请选择新的记录负责人。</p>
        <MemberPicker
          :system-id="systemId"
          :value="transferMemberId"
          placeholder="搜索当前租户的活动成员"
          :disabled="batchCommandLoading === 'transfer'"
          @update:value="transferMemberId = $event"
        />
        <a-alert v-if="transferError" type="error" show-icon :message="transferError" />
      </div>
    </a-modal>

    <RuntimeImportDrawer
      v-if="canImport"
      :open="importDrawerOpen"
      :system-id="systemId"
      :module-code="selectedModule"
      :module-name="moduleName"
      :initial-task-id="resultTaskId"
      @close="importDrawerOpen = false"
      @changed="loadRecords"
    />

    <RuntimeExportDrawer
      v-if="canExport && schema"
      :open="exportDrawerOpen"
      :system-id="systemId"
      :module-code="selectedModule"
      :module-name="moduleName"
      :query="recordQuery(null)"
      :fields="schema.fields"
      :default-field-codes="displayedListFields.map((field) => field.fieldCode)"
      :initial-task-id="resultTaskId"
      @close="exportDrawerOpen = false"
    />

    <RuntimePrintDrawer
      v-if="detail?.actions.includes('PRINT')"
      :open="printDrawerOpen"
      :system-id="systemId"
      :module-code="selectedModule"
      :record-id="detail.recordId"
      :record-version="detail.version"
      :record-title="detail.title || detail.recordNo"
      :initial-task-id="resultTaskId"
      @close="printDrawerOpen = false"
    />

    <RuntimeFavoritesPanel
      v-if="canManageFavorites"
      :open="favoritesPanelOpen"
      :items="favoriteItems"
      :page="favoritePage"
      :size="favoriteSize"
      :total="favoriteTotal"
      :loading="favoritesLoading"
      :error="favoriteError"
      :removing-id="favoriteMutationKey.startsWith('remove:') ? favoriteMutationKey.slice(7) : ''"
      @close="favoritesPanelOpen = false"
      @refresh="loadFavorites(favoritePage)"
      @page-change="loadFavorites"
      @open-favorite="openFavoriteTarget"
      @remove="removeFavorite"
    />

    <RuntimeRecentRecordsPanel
      :open="recentPanelOpen"
      :items="recentItems"
      :page="recentPage"
      :size="recentSize"
      :total="recentTotal"
      :loading="recentLoading"
      :error="recentError"
      @close="recentPanelOpen = false"
      @refresh="loadRecentRecords(recentPage)"
      @page-change="loadRecentRecords"
      @open-record="openRecentRecord"
    />

    <RuntimeGlobalSearchPanel
      :open="globalSearchPanelOpen"
      :keyword="globalSearchKeyword"
      :items="globalSearchItems"
      :page="globalSearchPage"
      :size="globalSearchSize"
      :total="globalSearchTotal"
      :loading="globalSearchLoading"
      :error="globalSearchError"
      @close="globalSearchPanelOpen = false"
      @update:keyword="globalSearchKeyword = $event"
      @search="searchGlobalRecords(1)"
      @page-change="searchGlobalRecords"
      @open-record="openGlobalSearchRecord"
    />

    <RuntimeQuickCreatePanel
      :open="quickCreatePanelOpen"
      :items="quickCreateItems"
      :loading="quickCreateLoading"
      :error="quickCreateError"
      @close="quickCreatePanelOpen = false"
      @refresh="loadQuickCreateModules"
      @select="openQuickCreateModule"
    />

    <a-modal
      :open="Boolean(pendingLifecycle)"
      :title="pendingLifecycle?.title"
      :z-index="2000"
      :confirm-loading="Boolean(lifecycleLoading)"
      :ok-button-props="{ danger: pendingLifecycle?.danger }"
      :mask-closable="false"
      ok-text="确认"
      cancel-text="取消"
      @ok="executeLifecycle"
      @cancel="cancelLifecycle"
    >
      <p class="lifecycle-confirm-message">{{ pendingLifecycle?.message }}</p>
    </a-modal>
  </section>
</template>

<style scoped>
.runtime-workbench{min-height:calc(100vh - 58px);background:#fff;overflow-x:hidden}.runtime-heading{height:64px;display:flex;align-items:center;justify-content:space-between;padding:0 22px;border-bottom:1px solid #dfe5e8}.runtime-heading h1{margin:0;font-size:20px}.runtime-heading span{font-size:12px;color:#75818a}.runtime-heading-actions{display:flex;align-items:center;gap:8px}.runtime-heading button,.icon-command{width:34px;height:34px;border:1px solid #d5dde1;background:#fff;color:#4f5e68;display:grid;place-items:center;cursor:pointer}.runtime-heading .favorites-panel-command{color:#a96500;border-color:#e0b45d;background:#fff9e8}.runtime-frame{display:grid;grid-template-columns:230px minmax(0,1fr);min-height:calc(100vh - 123px)}.runtime-navigation{border-right:1px solid #dde4e8;background:#f7f9fa;padding:10px}.runtime-group h2{margin:10px 8px 5px;color:#65737d;font-size:12px}.runtime-group button{width:100%;height:42px;display:grid;grid-template-columns:20px 1fr 16px;align-items:center;gap:6px;border:0;background:transparent;text-align:left;color:#35444e;cursor:pointer}.runtime-group button.active{background:#e0f0ed;color:#087f73;font-weight:700}.runtime-module{padding:0 24px;min-width:0}.runtime-module-title{min-height:72px;display:flex;justify-content:space-between;align-items:center;gap:16px;border-bottom:1px solid #e1e6e9}.runtime-module-title h2{margin:0;font-size:19px}.runtime-module-title span{font-size:12px;color:#79858e}.module-facts{display:flex;align-items:center;gap:12px;white-space:nowrap}.content-alert{margin-top:14px}.record-query-bar{min-height:64px;display:grid;grid-template-columns:auto minmax(180px,1fr) repeat(2,180px) auto;align-items:center;gap:10px}.record-search,.saved-view-select,.shared-filter-scenario-select{width:100%}.record-toolbar-actions{display:flex;align-items:center;justify-content:flex-end;gap:8px}.record-toolbar-actions .ant-btn,.query-summary .ant-btn,.query-builder .ant-btn{display:inline-flex;align-items:center;gap:6px}.query-summary{min-height:36px;display:flex;align-items:center;flex-wrap:wrap;gap:6px;padding:4px 0 10px}.record-table-wrap{width:100%;overflow:auto;border-top:1px solid #dce3e7}.record-table{width:100%;min-width:720px;border-collapse:collapse;table-layout:auto}.record-table th,.record-table td{height:50px;padding:8px 12px;border-bottom:1px solid #e3e8eb;text-align:left;white-space:nowrap}.record-table th{background:#f5f7f8;color:#64727c;font-size:12px;font-weight:700}.record-table td{color:#34434d}.record-table th:last-child,.record-table td:last-child{width:54px;text-align:center}.record-link{border:0;background:transparent;padding:0;color:#087f73;font-weight:700;cursor:pointer}.record-pagination{display:flex;justify-content:flex-end;flex-wrap:wrap;padding:18px 0}.record-mobile-list{display:none}.runtime-empty-state{display:flex;gap:12px;margin-top:18px;padding:18px;border-left:3px solid #2563a6;background:#f3f7fb}.runtime-empty-state h3,.runtime-empty-state p{margin:0}.runtime-empty-state p{margin-top:4px;color:#63717b}.runtime-mobile-selectors{display:none}.query-builder{display:grid;gap:24px;padding-bottom:16px}.query-builder-section{display:grid;gap:12px;padding-bottom:20px;border-bottom:1px solid #e3e8eb}.query-builder-section:last-child{border-bottom:0}.query-section-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.query-section-heading h3{margin:0;font-size:15px}.query-section-heading span{display:block;margin-top:3px;color:#75818a;font-size:12px}.filter-row{display:grid;grid-template-columns:130px 116px minmax(140px,1fr) auto 34px;align-items:center;gap:8px}.filter-value{width:100%}.filter-range{display:grid;grid-template-columns:minmax(0,1fr) auto minmax(0,1fr);align-items:center;gap:6px}.filter-money{display:grid;grid-template-columns:92px minmax(0,1fr);align-items:center;gap:6px}.filter-money-range{grid-template-columns:82px minmax(0,1fr) auto minmax(0,1fr)}.sort-row{display:grid;grid-template-columns:minmax(140px,1fr) 90px 110px 90px 34px;gap:8px}.column-options{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.query-drawer-footer{display:flex;justify-content:flex-end;gap:8px}.saved-view-name{display:grid;gap:8px;margin-top:16px}.saved-view-name span{font-weight:600}.saved-view-modal-error{margin-bottom:12px}.detail-state-alert{margin-bottom:14px}.detail-heading{display:flex;align-items:flex-start;justify-content:space-between;gap:16px;padding-bottom:18px;border-bottom:1px solid #e0e6e9}.detail-heading h3{margin:0;font-size:18px;overflow-wrap:anywhere}.detail-actions{display:flex;align-items:center;gap:8px;flex:none}.detail-command-bar{display:flex;flex-wrap:wrap;gap:8px;padding:14px 0;border-bottom:1px solid #e0e6e9}.detail-command-bar .ant-btn{display:inline-flex;align-items:center;gap:6px}.detail-list{margin:0}.detail-list>div{display:grid;grid-template-columns:130px minmax(0,1fr);gap:16px;padding:14px 0;border-bottom:1px solid #e5eaed}.detail-list dt{color:#697781}.detail-list dd{margin:0;color:#273740;overflow-wrap:anywhere}.runtime-form{display:grid;gap:18px;padding-bottom:74px}.runtime-save-state{min-height:34px;display:flex;align-items:center;gap:8px;padding:7px 10px;border-left:3px solid #8a969e;background:#f5f7f8;color:#4c5a64}.runtime-save-state[data-state="saved"]{border-color:#16856f;background:#eff8f5;color:#126b5d}.runtime-save-state[data-state="dirty"]{border-color:#b67814;background:#fff8e8;color:#875b13}.runtime-save-state[data-state="error"],.runtime-save-state[data-state="conflict"]{border-color:#c33b32;background:#fff3f2;color:#9d302a}.runtime-conflict{display:grid;gap:10px;padding:12px;border-left:3px solid #c07a0a;background:#fff8e8;color:#5e4a26}.runtime-conflict>span{overflow-wrap:anywhere}.runtime-conflict>div{display:flex;flex-wrap:wrap;gap:8px}.runtime-conflict .ant-btn{display:inline-flex;align-items:center;gap:6px}.spin{animation:runtime-spin 1s linear infinite}@keyframes runtime-spin{to{transform:rotate(360deg)}}.runtime-form-field{display:grid;gap:7px;min-width:0}.runtime-form-field label{font-weight:650;color:#34434d}.required-mark{margin-left:3px;color:#c33b32}.number-input,.runtime-form-field .ant-select{width:100%}.native-field{width:100%;height:32px;padding:4px 11px;border:1px solid #d9d9d9;background:#fff;color:#263640;font:inherit;outline:none}.native-field:focus{border-color:#4096ff;box-shadow:0 0 0 2px rgb(5 145 255 / 10%)}.native-field:disabled{background:#f5f5f5;color:#8c8c8c}.field-error{min-height:18px;color:#c33b32;font-size:12px;overflow-wrap:anywhere}.runtime-form-actions{position:absolute;right:24px;bottom:0;left:24px;display:flex;justify-content:flex-end;gap:10px;padding:15px 0 20px;border-top:1px solid #e3e8eb;background:#fff}.runtime-form-actions .ant-btn{display:inline-flex;align-items:center;gap:6px}.runtime-form-actions .discard-action{margin-right:auto}.batch-transfer-form{display:grid;gap:12px}.batch-transfer-form p{margin:0;color:#4f5e68;line-height:1.6}.lifecycle-confirm-message{margin:0;color:#35444e;line-height:1.65;overflow-wrap:anywhere}.sr-only{position:absolute;width:1px;height:1px;padding:0;margin:-1px;overflow:hidden;clip:rect(0,0,0,0);white-space:nowrap;border:0}
  @media(max-width:720px){.runtime-heading{height:56px;padding:0 14px}.runtime-heading h1{font-size:17px}.runtime-mobile-selectors{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:8px;padding:10px 12px;border-bottom:1px solid #dfe5e8}.runtime-frame{display:block;min-height:0}.runtime-navigation{display:none}.runtime-module{padding:0 14px}.runtime-module-title{min-height:66px}.module-facts>span{display:none}.record-query-bar{grid-template-columns:minmax(0,1fr);padding:12px 0}.record-query-bar>.ant-segmented,.record-search,.saved-view-select{width:100%}.record-toolbar-actions{display:flex;justify-content:flex-start;flex-wrap:wrap}.record-toolbar-actions .ant-btn-primary{margin-left:auto}.query-summary{padding-top:0}.record-table-wrap{display:none}.record-mobile-list{display:grid;border-top:1px solid #dce3e7}.record-mobile-item{width:100%;display:grid;gap:7px;padding:14px 2px;border:0;border-bottom:1px solid #e3e8eb;background:#fff;text-align:left;color:#33434d;cursor:pointer}.mobile-record-head{display:flex;align-items:center;justify-content:space-between;gap:10px}.mobile-record-title{font-weight:600;overflow-wrap:anywhere}.mobile-field{display:grid;grid-template-columns:90px minmax(0,1fr);gap:8px;overflow-wrap:anywhere}.mobile-field small{color:#75818a}.record-pagination{justify-content:center;padding:16px 0}.query-section-heading{align-items:flex-start;flex-direction:column}.filter-row{grid-template-columns:minmax(0,1fr) 34px}.filter-row>.ant-select:nth-child(1),.filter-row>.ant-select:nth-child(2),.filter-row>.filter-value,.filter-row>.filter-range,.filter-row>.filter-money{grid-column:1/-1}.filter-money-range{grid-template-columns:80px minmax(0,1fr);}.filter-money-range>span{display:none}.sort-row{grid-template-columns:minmax(0,1fr) 34px}.sort-row>.ant-select{grid-column:1}.sort-row>.ant-btn{grid-column:2;grid-row:1}.column-options{grid-template-columns:minmax(0,1fr)}.runtime-empty-state{margin-bottom:16px}.detail-list>div{grid-template-columns:100px minmax(0,1fr)}.runtime-form{padding-bottom:126px}.runtime-form-actions{right:16px;left:16px;flex-wrap:wrap}.runtime-form-actions .ant-btn{min-height:32px}}
.record-table .selection-cell{width:42px;padding-inline:10px;text-align:center}
.runtime-heading .recent-panel-command{color:#2563a6;border-color:#a8c4df;background:#f3f7fb}
.runtime-heading .global-search-command{color:#087f73;border-color:#99c8c1;background:#eff8f5}
.runtime-heading .quick-create-command{color:#16856f;border-color:#8fc1b7;background:#eff8f5}
.runtime-heading span{color:#5f6e78}
.batch-edit-form{display:grid;gap:10px}.batch-edit-form p{margin:0 0 4px;color:#4f5e68;line-height:1.6}.batch-edit-form>label{font-weight:650;color:#34434d}
.runtime-form-fields{display:grid;gap:18px}.filter-structured{display:grid;gap:6px;width:100%;min-width:0}.filter-address,.filter-barcode{grid-template-columns:110px minmax(0,1fr)}.filter-geo-box{grid-template-columns:repeat(4,minmax(76px,1fr))}.filter-geo-near{grid-template-columns:repeat(3,minmax(90px,1fr))}.filter-json-path{grid-template-columns:minmax(130px,1fr) minmax(110px,1fr)}.filter-structured .ant-input-number{width:100%}
@media(max-width:1200px){.runtime-module{padding-inline:18px}.record-query-bar{grid-template-columns:auto minmax(180px,1fr) repeat(2,minmax(140px,180px));padding-block:12px}.record-toolbar-actions{grid-column:1/-1;justify-content:flex-end;flex-wrap:wrap}.runtime-form-actions{right:18px;left:18px}}
@media(max-width:980px){.runtime-frame{grid-template-columns:200px minmax(0,1fr)}.runtime-navigation{padding-inline:8px}.record-query-bar{grid-template-columns:auto minmax(180px,1fr)}.record-query-bar>.shared-filter-scenario-select,.record-query-bar>.saved-view-select{grid-column:span 1}.record-toolbar-actions{justify-content:flex-start}.module-facts{gap:8px}}
@media(max-width:720px){.runtime-module{padding-inline:14px}.record-query-bar{grid-template-columns:minmax(0,1fr)}.runtime-form-actions{right:16px;left:16px}.filter-row>.filter-structured{grid-column:1/-1}.filter-address,.filter-barcode,.filter-geo-box,.filter-geo-near,.filter-json-path{grid-template-columns:minmax(0,1fr)}}
</style>

<style scoped>
.composition-form-field{padding:14px 0;border-top:1px solid #e3e8eb}.composition-form-field>label{font-size:14px}.detail-list>div.composition-detail{grid-template-columns:minmax(0,1fr);gap:8px}.composition-detail dt{font-weight:650;color:#34434d}.composition-detail dd{min-width:0}
.record-table.published-density-compact th,.record-table.published-density-compact td{height:38px;padding-block:5px}.published-form-section,.published-detail-section{min-width:0;border:0;border-top:1px solid #e1e7ea}.published-form-section>summary,.published-detail-section>summary{padding:13px 2px;color:#263740;font-weight:700;cursor:pointer}.published-form-grid,.published-detail-grid{display:grid;min-width:0;padding:14px 0}.published-detail-section{display:block}.published-detail-grid>div{display:grid;grid-template-columns:130px minmax(0,1fr);gap:16px;padding:12px 0;border-bottom:1px solid #e5eaed}.published-rule-readonly{padding:10px;border-left:3px solid #aab5bc;background:#f6f8f9}.published-rule-readonly>label::after{content:' · 只读';color:#75818a;font-size:12px;font-weight:500}
@media(max-width:720px){.composition-form-field{padding-block:12px}.detail-list>div.composition-detail{grid-template-columns:minmax(0,1fr)}.published-form-grid,.published-detail-grid{grid-template-columns:minmax(0,1fr)!important}.published-form-grid>*,.published-detail-grid>*{grid-column:1/-1!important}.published-detail-grid>div{grid-template-columns:100px minmax(0,1fr)}}
</style>
