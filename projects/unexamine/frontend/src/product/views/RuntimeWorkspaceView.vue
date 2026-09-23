<script setup lang="ts">
import { DeleteOutlined, EditOutlined, ExportOutlined, FilterOutlined, ImportOutlined, InboxOutlined, MoreOutlined, PlusOutlined, PrinterOutlined, ReloadOutlined, RobotOutlined, SaveOutlined, SearchOutlined, SettingOutlined, SwapOutlined, UndoOutlined } from '@ant-design/icons-vue'
import { Empty, message, Modal } from 'ant-design-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import { statusTone } from '../responsive'
import { systemContext, systemTokens } from '../session'
import { conversionTargetCodes, createRuntimeAction, createRuntimeBatchAction, businessActionMessage, type ActionDelivery, type BusinessActionResult, type RuntimeBatchItem } from '../runtime-actions'
import BusinessActionReceipt from '../components/BusinessActionReceipt.vue'
import RuntimeActionGroup from '../components/RuntimeActionGroup.vue'
import { actionsAtLocation, actionAllowsLifecycle, actionAllowsRecord, requiresRecord, selectionActionReason, supportsBatchAction } from '../action-placement'
import { authorizedBlob, saveBlob } from '../file'
import { allowsPermission } from '../permissions'
import ModuleImportDrawer from '../components/ModuleImportDrawer.vue'
import ModuleExportDrawer from '../components/ModuleExportDrawer.vue'
import ModuleRangeSharingDrawer from '../components/ModuleRangeSharingDrawer.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductPage from '../components/ProductPage.vue'
import ProductListWorkspace from '../components/ProductListWorkspace.vue'
import RuntimeFieldInput from '../components/RuntimeFieldInput.vue'
import RuntimeAttachmentFieldInput from '../components/RuntimeAttachmentFieldInput.vue'
import SchemaFormLayout from '../components/SchemaFormLayout.vue'
import SchemaRecordTable from '../components/SchemaRecordTable.vue'
import SchemaRecordDetail from '../components/SchemaRecordDetail.vue'
import { listPageSettings, type PageColumn } from '../page-layout'
import { parseFormLayout } from '../form-layout'
import { fieldFamily, isSharedRuntimeField } from '../field-capabilities'
import { richPlainText, structuredSummary, emptyJsonValue, newJsonSchema, type JsonSchema } from '../structured-content'
import ProductFilterBar from '../components/ProductFilterBar.vue'
import ProductDetailDrawer from '../components/ProductDetailDrawer.vue'
import ProductFormDrawer from '../components/ProductFormDrawer.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import PersonSelect from '../components/PersonSelect.vue'
import DepartmentSelect from '../components/DepartmentSelect.vue'
import BusinessAttachmentsPanel from '../components/BusinessAttachmentsPanel.vue'
import AiContextAssistantPanel from '../components/AiContextAssistantPanel.vue'
import { productDateTime, userFacingRecordNumber, userFacingWorkspaceName } from '../presentation'
import type {
  ConfiguredAction,
  RuntimeField,
  RuntimeReadField,
  RuntimeDictionary,
  RuntimeModuleCatalogItem,
  RuntimeModuleConfiguration,
  RuntimeRecord,
  RuntimeRecordEditContext,
  RuntimeRecordActionContext,
  RuntimeRecordLifecycleImpact,
  RuntimeRecordList,
  RuntimeListQueryField,
  RuntimeListFilter,
  RuntimeListView,
  RuntimeRecordTimeline,
  RuntimeRecordTransferPreview,
  RuntimeRecordConversionPreview,
  RuntimeRecordConversionExecution,
  RuntimeRecordConversionLink,
  RuntimeTenantShare,
  TenantShareTarget,
  CommandCenterItem,
  RuntimePrintTemplate,
  PrintJob,
  SystemPeopleDirectory,
} from '../types'

const props = defineProps<{ refreshKey?: number; initialModuleCode?: string }>()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const saving = ref(false)
const saveAction = createRuntimeAction()
const deleteAction = createRuntimeAction()
let routeInitializing = true
const conversionAction = createRuntimeAction()
const conversionUnlinkAction = createRuntimeAction()
const conversionUnlinkOpen = ref(false)
const conversionUnlinkSaving = ref(false)
const conversionUnlinkLink = ref<RuntimeRecordConversionLink>()
const conversionUnlinkSource = ref<{ id: number; moduleCode: string; version: number }>()
const conversionUnlinkReason = ref('')
const confirmedConversionRequest = ref<{ version: number; targetModuleCode: string }>()
const lastAction = ref<{ moduleCode: string; result: BusinessActionResult<unknown> }>()
const error = ref('')
const modules = ref<RuntimeModuleCatalogItem[]>([])
const activeCode = ref('')
const configuration = ref<RuntimeModuleConfiguration>()
const records = ref<RuntimeRecord[]>([])
const queryFields = ref<RuntimeListQueryField[]>([])
const queryDirectoryScope = ref('')
const queryRestoreError = ref('')
let queryFieldsRequest = 0
const queryOperatorLabels: Record<RuntimeListFilter['operator'], string> = {
  EQ: '等于', NE: '不等于', CONTAINS: '包含', GT: '大于', GTE: '大于等于',
  LT: '小于', LTE: '小于等于', EMPTY: '为空', NOT_EMPTY: '不为空',
}
let recordListRequest = 0
let moduleDirectoryRequest = 0
let moduleSelectionRequest = 0
let moduleInitializationSelection: number | undefined
let savedViewsRequest = 0
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const lifecycleState = ref<'ACTIVE' | 'ARCHIVED' | 'DELETED'>('ACTIVE')
const tenantScope = ref<'ALL' | 'OWN' | 'SHARED'>('ALL')
const searchText = ref('')
const listFilters = ref<RuntimeListFilter[]>([])
const sortField = ref('updatedAt')
const sortDirection = ref<'ASC' | 'DESC'>('DESC')
const filterOpen = ref(false)
const importOpen = ref(false)
const exportOpen = ref(false)
const columnOpen = ref(false)
const visibleFieldCodes = ref<string[]>([])
const fixedFieldCodes = ref<string[]>(['title'])
const draggingColumn = ref('')
const selectedRowKeys = ref<number[]>([])
const focusedRowIndex = ref(-1)
const savedViews = ref<RuntimeListView[]>([])
const activeViewCode = ref('')
const saveViewOpen = ref(false)
const saveViewSaving = ref(false)
const viewForm = reactive({ name: '', defaultView: false })
const dashboardReturn = computed(() => {
  const value = Array.isArray(route.query.dashboardReturn) ? route.query.dashboardReturn[0] : route.query.dashboardReturn
  const expectedPrefix = `/systems/${systemContext.value?.systemId}`
  return typeof value === 'string' && value.startsWith(expectedPrefix) ? value : ''
})
const detailOpen = ref(false)
let detailRequest = 0
const assistantOpen = ref(false)
const attachmentFieldCode = ref('')
const lastOpenedRouteRecordId = ref<number>()
const formOpen = ref(false)
const detail = ref<RuntimeRecord>()
const printTemplates = ref<RuntimePrintTemplate[]>([])
const printOpen = ref(false)
const printing = ref(false)
const selectedPrintTemplateId = ref<number>()
const printJob = ref<PrintJob>()
const printPreviewUrl = ref('')
const timeline = ref<RuntimeRecordTimeline['entries']>([])
const editing = ref<RuntimeRecord>()
const formConfiguration = ref<RuntimeModuleConfiguration>()
const formError = ref('')
const fieldErrors = reactive<Record<string, string>>({})
const lifecycleOpen = ref(false)
let lifecycleDialogRequest = 0
const lifecycleSaving = ref(false)
const lifecycleAction = ref<'ARCHIVE' | 'DELETE' | 'RESTORE'>('ARCHIVE')
const lifecycleRecord = ref<RuntimeRecord>()
const lifecycleImpact = ref<RuntimeRecordLifecycleImpact>()
const lifecycleReason = ref('')
const batchDeleteTargets = ref<RuntimeRecord[]>([])
const batchDeleteResults = ref<RuntimeBatchItem<RuntimeRecord>[]>([])
const batchDeleteAction = createRuntimeBatchAction<RuntimeRecord>()
const transferOpen = ref(false)
const transferSaving = ref(false)
const transferRecord = ref<RuntimeRecord>()
const transferPreview = ref<RuntimeRecordTransferPreview>()
const transferAction = createRuntimeAction()
const confirmedTransferRequest = ref<{ version: number; ownerMemberId: number; departmentId?: number; participantMemberIds: number[]; reason: string }>()
const peopleDirectory = ref<SystemPeopleDirectory>({ departments: [], people: [], permissionVersion: 0 })
const formOrganizationOptions = reactive<Record<string, SystemPeopleDirectory>>({})
const transferOrganizationOptions = ref<SystemPeopleDirectory>()
const manualOrganizationOptions = reactive<Record<string, SystemPeopleDirectory>>({})
let formOrganizationRequest = 0
let transferOrganizationRequest = 0
let manualOrganizationRequest = 0
const transferForm = reactive({ ownerMemberId: undefined as number | undefined, departmentId: undefined as number | undefined, participantMemberIds: [] as number[], reason: '' })
const conversionOpen = ref(false)
let conversionDialogRequest = 0
const conversionSaving = ref(false)
const conversionRecord = ref<RuntimeRecord>()
const conversionTargetCode = ref('')
const conversionPreview = ref<RuntimeRecordConversionPreview>()
const conversionLinks = ref<RuntimeRecordConversionLink[]>([])
const batchConversionTargets = ref<RuntimeRecord[]>([])
const batchConversionModule = ref('')
const batchConversionPreviews = ref<Array<{ recordId: number; title: string; preview?: RuntimeRecordConversionPreview; error?: string }>>([])
const batchConversionResults = ref<RuntimeBatchItem<RuntimeRecordConversionExecution>[]>([])
const batchConversionAction = createRuntimeBatchAction<RuntimeRecord, RuntimeRecordConversionExecution>()
const batchConversionRetryPreviewed = ref(false)
const batchConversionFailed = computed(() => batchConversionResults.value.filter(item => item.status === 'FAILED'))
const shareOpen = ref(false)
const rangeSharingOpen = ref(false)
const shareSaving = ref(false)
const shareRecord = ref<RuntimeRecord>()
const shareTargets = ref<TenantShareTarget[]>([])
const shareRows = ref<RuntimeTenantShare[]>([])
const shareForm = reactive({ targetTenantId: undefined as number | undefined, allowedActions: [] as string[], expiresAt: '' })
const form = reactive({
  title: '', recordNumber: '', status: 'ACTIVE', ownerMemberId: undefined as number | undefined,
  departmentId: undefined as number | undefined, participantText: '', fields: {} as Record<string, unknown>,
})
const formSnapshot = ref('')
const formDraftRestored = ref(false)
type CascadeOption = { value: number; label: string; children?: CascadeOption[] }
const cascadeOptions = reactive<Record<string, CascadeOption[]>>({})
const dictionaryOptions = reactive<Record<string, Array<{ value: string; label: string }>>>({})
const referenceOptions = reactive<Record<string, Array<{ value: number; label: string; description: string }>>>({})
const referenceLoading = ref(false)
const formCascadeOptions = reactive<Record<string, CascadeOption[]>>({})
const formDictionaryOptions = reactive<Record<string, Array<{ value: string; label: string }>>>({})
const formReferenceOptions = reactive<Record<string, Array<{ value: number; label: string; description: string }>>>({})
const formCatalogLoading = ref(false)
const formReferenceLoading = reactive<Record<string, boolean>>({})
const formReferenceSearchRequest = new Map<string, number>()
const formReferenceSearchTimers = new Map<string, ReturnType<typeof setTimeout>>()
let referenceRequest = 0
let configurationRequest = 0
type ManualActionConfig = { operation?: string; parameterFieldCodes?: string[]; confirmation?: boolean; confirmationText?: string }
const manualActionOpen = ref(false)
const manualActionSaving = ref(false)
const manualAction = ref<import('../types').ConfiguredAction>()
const manualActionTargets = ref<RuntimeRecord[]>([])
const manualActionContexts = ref<RuntimeRecordActionContext[]>([])
const manualDictionaryOptions = reactive<Record<string, Array<{ value: string; label: string }>>>({})
const manualCascadeOptions = reactive<Record<string, CascadeOption[]>>({})
const manualReferenceOptions = reactive<Record<string, Array<{ value: number; label: string; description: string }>>>({})
const manualReferenceLoading = reactive<Record<string, boolean>>({})
const manualReferenceSearchRequest = new Map<string, number>()
const manualReferenceSearchTimers = new Map<string, ReturnType<typeof setTimeout>>()
const manualReferenceUnavailable = reactive<Record<string, boolean>>({})
type ManualRequestContext = { generation: number; moduleCode: string; accessToken: string;
  systemId?: number | null; tenantId?: number | null; actionCode: string; recordKey: string }
let manualRequestContext: ManualRequestContext | undefined
const manualActionValues = reactive<Record<string, unknown>>({})
const manualActionResults = ref<RuntimeBatchItem<RuntimeRecord>[]>([])
let manualBatchAction = createRuntimeBatchAction<RuntimeRecord>()

const token = computed(() => systemTokens.value?.accessToken || '')
const selectedPrintTemplate = computed(() => printTemplates.value.find(item => item.templateId === selectedPrintTemplateId.value))
function configurationFields(current: RuntimeModuleConfiguration | undefined) {
  return current?.configuration.fields.filter(field => field.status === 'ACTIVE'
    && (field.access?.readable !== false || field.access?.writable === true)) || []
}
const fields = computed(() => configurationFields(configuration.value))
const fieldsForPage = (pageType: string, current = configuration.value) => {
  const available = configurationFields(current)
  const page = current?.configuration.pages.find((item) => item.pageType === pageType)
  if (!page) return available
  try {
    const layout = JSON.parse(page.layoutJson) as { fieldCodes?: string[] }
    if (!layout.fieldCodes) return available
    const byCode = new Map(available.map((field) => [field.code, field]))
    return layout.fieldCodes.map((code) => byCode.get(code)).filter((field): field is RuntimeField => Boolean(field))
  } catch { return available }
}
function writableFormFields(current: RuntimeModuleConfiguration | undefined) {
  if (!current) return []
  return fieldsForPage('FORM', current).filter(field => pageWritable(field)
    && !field.fieldType.startsWith('SYSTEM_') && !Boolean(fieldConfig(field).readOnly))
}
const listFields = computed(() => fieldsForPage('LIST').filter((field) => field.access?.readable !== false))
const formFields = computed(() => writableFormFields(formConfiguration.value))
const detailFields = computed(() => fieldsForPage('DETAIL')
  .filter(field => field.access?.readable !== false && recordFieldVisible(detail.value, field.code))
  .map(field => recordDisplayField(field, detail.value)))
const attachmentFieldTypes = new Set(['ATTACHMENT', 'IMAGE', 'FILE', 'FILE_GROUP'])
const recordAttachmentFields = computed(() => detailFields.value.filter(field => attachmentFieldTypes.has(field.fieldType)))
const detailValueFields = computed(() => detailFields.value.filter(field => !attachmentFieldTypes.has(field.fieldType)))
const detailReadFields = computed(() => detail.value?.fieldPresentation ? recordReadFields(detail.value) : undefined)
const formLayout = computed(() => formConfiguration.value?.configuration.pages.find(page => page.pageType === 'FORM')?.layoutJson)
const listLayout = computed(() => configuration.value?.configuration.pages.find(page => page.pageType === 'LIST')?.layoutJson)
const detailLayout = computed(() => configuration.value?.configuration.pages.find(page => page.pageType === 'DETAIL')?.layoutJson)
const listCaption = computed(() => String(parseFormLayout(listLayout.value).components?.find(component => component.type === 'TABLE')?.title || ''))
const formValueFields = computed(() => formFields.value)
const recordTitleField = computed(() => formValueFields.value.find(field => field.required
  && ['TEXT', 'MULTILINE_TEXT', 'PHONE', 'EMAIL', 'URL'].includes(field.fieldType)
  && !Boolean(fieldConfig(field).sensitive)))
const selectedAttachmentField = computed(() => recordAttachmentFields.value.find(field => field.code === attachmentFieldCode.value))
const canViewAttachments = computed(() => allowsPermission(systemContext.value?.permissions, 'FILE', 'OBJECT', 'VIEW')
  || allowsPermission(systemContext.value?.permissions, 'FILE', '*', 'VIEW'))
const canUseAi = computed(() => allowsPermission(systemContext.value?.permissions, 'AI', 'SYSTEM', 'VIEW')
  || allowsPermission(systemContext.value?.permissions, 'AI', '*', 'VIEW'))
const recordAttachmentEndpoint = computed(() => detail.value && selectedAttachmentField.value
  ? `/api/business-attachments/records/${activeCode.value}/${detail.value.id}/fields/${selectedAttachmentField.value.code}` : '')
const actions = computed(() => sharedRecordsMode.value ? [] : configuration.value?.configuration.actions || [])
const manualActionFields = computed(() => manualActionContexts.value[0]?.parameters || [])
const activeModule = computed(() => modules.value.find((item) => item.moduleCode === activeCode.value))
const sharedRecordsMode = computed(() => activeModule.value?.configurationMode === 'SHARED_RECORDS')
const groupedModules = computed(() => {
  const groups = new Map<number, { id: number; name: string; modules: RuntimeModuleCatalogItem[] }>()
  modules.value.forEach((item) => {
    if (!groups.has(item.groupId)) groups.set(item.groupId, { id: item.groupId, name: item.groupName, modules: [] })
    groups.get(item.groupId)?.modules.push(item)
  })
  return [...groups.values()]
})
const activeGroup = computed(() => groupedModules.value.find(group =>
  group.modules.some(module => module.moduleCode === activeCode.value)) || groupedModules.value[0])

function selectGroup(groupId: number) {
  const group = groupedModules.value.find(item => item.id === groupId)
  const firstModule = group?.modules[0]
  if (firstModule && firstModule.moduleCode !== activeCode.value) void selectModule(firstModule.moduleCode)
}

type RuntimeColumn = PageColumn & { readOnlySource?: boolean }
const configuredColumns = computed<RuntimeColumn[]>(() => listPageSettings(listLayout.value, [
  { code: 'title', label: '标题', width: 210 },
  ...(sharedRecordsMode.value ? [{ code: 'recordNumber', label: '业务编号', width: 150 }, { code: 'departmentId', label: '负责部门', width: 120 }] : []),
  ...listFields.value.map((field) => ({ code: field.code, label: field.name, width: 140, field })),
  { code: 'ownerMemberId', label: '负责人', width: 120 },
  { code: 'dataTenantName', label: '数据归属', width: 140 },
  { code: 'status', label: '状态', width: 90 },
  { code: 'createdAt', label: '创建时间', width: 150 },
  { code: 'updatedAt', label: '更新时间', width: 150 },
].filter((column, index, source) => source.findIndex((item) => item.code === column.code) === index)).columns)
// 来源查询列只使用可信窄描述，不构造来源 ConfiguredField 或进入表单配置。
const currentColumns = computed<RuntimeColumn[]>(() => {
  const configured = new Map(configuredColumns.value.map(column => [column.code, column]))
  const source = new Map(sourceColumns(records.value, configuredColumns.value).map(column => [column.code, column]))
  const builtins = new Set(['title', 'recordNumber', 'status', 'ownerMemberId', 'departmentId', 'createdAt', 'updatedAt', 'dataTenantName'])
  return queryFields.value.map(field => configured.get(field.code) || source.get(field.code)
    || { code: field.code, label: field.name, width: 140, readOnlySource: !builtins.has(field.code) })
})
// 可展示但不可查询的保护后字段只随当前页存在，不进入个人列持久化。
const sourceReadColumns = computed(() => sourceColumns(records.value, currentColumns.value))
const listSettings = computed(() => listPageSettings(listLayout.value, currentColumns.value))
const displayedColumns = computed(() => {
  const byCode = new Map(currentColumns.value.map((column) => [column.code, column]))
  return [...visibleFieldCodes.value.map((code) => byCode.get(code)).filter((column): column is RuntimeColumn => Boolean(column)), ...sourceReadColumns.value]
})
const filterFieldOptions = computed(() => queryFields.value.filter(field => field.operators.length)
  .map(field => ({ value: field.code, label: field.name })))
const sortFieldOptions = computed(() => queryFields.value.filter(field => field.sortable)
  .map(field => ({ value: field.code, label: field.name })))
const searchPlaceholder = computed(() => queryFields.value.some(field => field.searchable)
  ? '搜索标题、编号和可查询字段' : '搜索标题和业务编号')
const activeSavedView = computed(() => savedViews.value.find((view) => view.code === activeViewCode.value))
const showTenantScope = computed(() => Boolean(systemContext.value?.tenantSwitchContext?.tenantSwitchable))
const queryCustomized = computed(() => Boolean(searchText.value.trim() || listFilters.value.length || activeViewCode.value
  || sortField.value !== 'updatedAt' || sortDirection.value !== 'DESC' || tenantScope.value !== 'ALL'))
const querySummary = computed(() => {
  const parts: string[] = []
  if (searchText.value.trim()) parts.push(`搜索“${searchText.value.trim()}”`)
  if (listFilters.value.length) parts.push(`${listFilters.value.length} 项筛选`)
  const label = queryFields.value.find(item => item.code === sortField.value)?.name || '排序字段已不可用'
  parts.push(`${label}${sortDirection.value === 'ASC' ? '升序' : '降序'}`)
  if (tenantScope.value !== 'ALL') parts.push(tenantScope.value === 'OWN' ? '仅本租户' : '仅共享给我')
  return parts.join(' · ')
})
const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: Array<string | number>) => { selectedRowKeys.value = keys.map(Number) },
}))
const formDirty = computed(() => formOpen.value && (formSnapshot.value !== serializeForm()
  || formFields.value.some(field => field.fieldType === 'SECRET' && form.fields[field.code] !== undefined && form.fields[field.code] !== '')))

function hasAction(code: string) {
  if (sharedRecordsMode.value) return false
  return actions.value.some((action) => action.code === code && action.status === 'ACTIVE')
    && allowsPermission(systemContext.value?.permissions, 'MODULE', activeCode.value, code)
}

function openAiCreate() {
  void router.replace({ path: route.path, query: { ...route.query, workspace: 'ai', aiMode: 'write', module: activeCode.value } })
}

function recordAllowsAction(record: RuntimeRecord, code: string) {
  if (Array.isArray(record.actions)) return record.actions.some(action => action.code === code && action.status === 'ACTIVE')
  return record.ownedByCurrentTenant && !record.shared && hasAction(code)
}
const canManageRangeShares = computed(() => Boolean(activeCode.value && configuration.value
  && !sharedRecordsMode.value && hasAction('SHARE')))

function recordActions(record: RuntimeRecord): ConfiguredAction[] {
  if (Array.isArray(record.actions)) return record.actions.filter(action => action.status === 'ACTIVE')
  // 旧共享回执不能借用接收方的动作定义；旧本租户记录保留配置兼容。
  return record.ownedByCurrentTenant && !record.shared ? actions.value : []
}

function commonBatchActions(selected: RuntimeRecord[]): ConfiguredAction[] {
  if (!selected.length) return []
  return recordActions(selected[0]!).filter(action => action.location === 'BATCH' && supportsBatchAction(action)
    && selected.every(record => recordActions(record).some(other => other.code === action.code
      && other.actionType === action.actionType && other.location === action.location && canRecordAction(other.code, record))))
    .map(action => selected.every(record => recordActions(record).find(other => other.code === action.code)?.name === action.name)
      ? action : { ...action, name: '共同操作（来源名称不同）' })
}

const batchActionList = computed(() => {
  const selected = records.value.filter(record => selectedRowKeys.value.includes(record.id))
  return selected.length === selectedRowKeys.value.length ? commonBatchActions(selected) : []
})

const shareActionOptions = computed(() => actions.value
  .filter((action) => !['LIST', 'DETAIL', 'CREATE', 'RESTORE', 'SHARE'].includes(action.code))
  .map((action) => ({ label: action.name, value: action.code })))

function actionNames(codes: string[]) {
  const byCode = new Map(actions.value.map(action => [action.code, action.name]))
  return codes.map(code => byCode.get(code) || '其他操作').join('、') || '仅查看'
}

function runPageUtility(key: string) {
  if (key === 'refresh') void loadModules()
  else if (key === 'ai') openAiCreate()
}

const selectedActionRecord = computed(() => selectedRowKeys.value.length === 1 ? records.value.find(record => record.id === selectedRowKeys.value[0]) : undefined)
function canRecordAction(code: string, record: RuntimeRecord) {
  if (Array.isArray(record.actions)) return recordAllowsAction(record, code)
  if (!record.ownedByCurrentTenant || record.shared) return false
  const configured = actions.value.find(action => action.code === code)
  if (!hasAction(code)) return false
  if (code === 'LIST') return true
  if (['CREATE', 'IMPORT'].includes(code)) return lifecycleState.value === 'ACTIVE'
  if (!actionAllowsRecord(configured || code, record, conversionTargetOptions.value.length > 0)) return false
  if (configured?.actionType === 'CUSTOM' && !actionVisibleForRecord(record)) return false
  return code !== 'UPDATE' || actionVisibleForRecord(record)
}
function canPageAction(code: string) {
  const batch = batchActionList.value.find(action => action.code === code)
  if (batch) return true
  const configured = actions.value.find(action => action.code === code)
  if (configured && requiresRecord(configured) && selectedActionRecord.value) return canRecordAction(code, selectedActionRecord.value)
  if (!hasAction(code) || !actionAllowsLifecycle(configured || code, lifecycleState.value)) return false
  if (supportsBatchAction(configured)) {
    const selected = records.value.filter(record => selectedRowKeys.value.includes(record.id))
    return selected.length > 0 && selected.length === selectedRowKeys.value.length && selected.every(record => canRecordAction(code, record))
  }
  if (code === 'CONVERT' && !conversionTargetOptions.value.length) return false
  if (requiresRecord(configured || code) && selectedActionRecord.value) return canRecordAction(code, selectedActionRecord.value)
  if (code === 'EXPORT' && selectedRowKeys.value.length) return records.value.filter(record => selectedRowKeys.value.includes(record.id)).every(record => canRecordAction(code, record))
  return true
}
function pageActionReason(code: string) {
  const configured = batchActionList.value.find(action => action.code === code) || actions.value.find(action => action.code === code)
  return selectionActionReason(configured || code, selectedRowKeys.value.length, supportsBatchAction(configured))
}
// 默认编辑在详情顶部；只有编辑权限时提供行入口，避免要求额外的详情授权。
function rowActions(record: RuntimeRecord) {
  return recordActions(record).map(action => action.actionType === 'BUILTIN' && action.code === 'UPDATE'
    && action.location === 'DETAIL_HEADER' && !canRecordAction('DETAIL', record) ? { ...action, location: 'ROW' } : action)
}
const showRecordActions = computed(() => records.value.some(record => actionsAtLocation(rowActions(record), 'ROW', code => canRecordAction(code, record)).length))
const canSelectRecords = computed(() => hasAction('EXPORT') || actions.value.some(action => requiresRecord(action)
  && ['MODULE_ENTRY', 'LIST_TOOLBAR', 'BATCH'].includes(action.location) && hasAction(action.code))
  || records.value.some(record => recordActions(record).some(action => action.location === 'BATCH' && supportsBatchAction(action) && canRecordAction(action.code, record))))
function runConfiguredAction(code: string, explicitRecord?: RuntimeRecord) {
  const record = explicitRecord || selectedActionRecord.value
  const configured = explicitRecord ? recordActions(explicitRecord).find(action => action.code === code)
    : batchActionList.value.find(action => action.code === code)
      || (record && recordActions(record).find(action => action.code === code)) || actions.value.find(action => action.code === code)
  if (explicitRecord ? !canRecordAction(code, explicitRecord) : !canPageAction(code)) { message.warning('当前记录或身份不能执行此操作'); return }
  if (requiresRecord(configured || code) && !explicitRecord && pageActionReason(code)) { message.warning(pageActionReason(code)); return }
  if (configured?.actionType === 'CUSTOM') {
    const targets = explicitRecord ? [explicitRecord] : records.value.filter(record => selectedRowKeys.value.includes(record.id))
    void openManualAction(configured, targets)
    return
  }
  if (code === 'DELETE' && supportsBatchAction(configured) && !explicitRecord) {
    lifecycleDialogRequest++
    batchDeleteTargets.value = records.value.filter(record => selectedRowKeys.value.includes(record.id)).map(record => recordWithoutSecrets(record))
    batchDeleteResults.value = []
    batchDeleteAction.clear()
    lifecycleAction.value = 'DELETE'
    lifecycleImpact.value = undefined
    lifecycleReason.value = ''
    lifecycleOpen.value = true
    return
  }
  if (code === 'CONVERT' && supportsBatchAction(configured) && !explicitRecord) {
    openBatchConversion(records.value.filter(record => selectedRowKeys.value.includes(record.id)))
    return
  }
  const handlers: Record<string, () => void> = {
    LIST: () => { closeDetail(); void loadRecords() },
    CREATE: openCreate,
    IMPORT: () => { importOpen.value = true },
    EXPORT: () => { if (explicitRecord) selectedRowKeys.value = [explicitRecord.id]; exportOpen.value = true },
    DETAIL: () => { if (record) void openDetail(record) },
    UPDATE: () => { if (record) void openEdit(record) },
    TRANSFER: () => { if (record) void openTransfer(record) },
    CONVERT: () => { if (record) void openConversion(record) },
    SHARE: () => { if (record) void openShare(record) },
    ARCHIVE: () => { if (record) void openLifecycle(record, 'ARCHIVE') },
    DELETE: () => { if (record) void openLifecycle(record, 'DELETE') },
    RESTORE: () => { if (record) void openLifecycle(record, 'RESTORE') },
  }
  if (handlers[code]) runAction(code, handlers[code], configured)
}

function actionConfiguration(code: string): ManualActionConfig & { targetModuleCode?: string; targets?: Array<{ moduleCode: string }> } {
  const action = actions.value.find((item) => item.code === code)
  try { return JSON.parse(action?.configJson || '{}') as { confirmation?: boolean; confirmationText?: string; targetModuleCode?: string; targets?: Array<{ moduleCode: string }> } }
  catch { return {} }
}

function pageWritable(field: RuntimeField) {
  return !field.access || (field.access.channel === 'PAGE' && field.access.writable)
}

function organizationField(field: RuntimeField) {
  return ['MEMBER', 'DEPARTMENT'].includes(field.fieldType)
}

function clearFormOrganizationOptions() {
  // 草稿不包含秘密值，关闭表单也不保留读取记录、输入或请求指纹。
  form.fields = {}
  editing.value = undefined
  formSnapshot.value = ''
  saveAction.clear()
  saving.value = false
  formOrganizationRequest++
  Object.keys(formOrganizationOptions).forEach(code => delete formOrganizationOptions[code])
  formConfiguration.value = undefined
  Object.keys(formDictionaryOptions).forEach(code => delete formDictionaryOptions[code])
  Object.keys(formCascadeOptions).forEach(code => delete formCascadeOptions[code])
  Object.keys(formReferenceOptions).forEach(code => delete formReferenceOptions[code])
  Object.keys(formReferenceLoading).forEach(code => delete formReferenceLoading[code])
  formReferenceSearchTimers.forEach(timer => clearTimeout(timer))
  formReferenceSearchTimers.clear()
  formReferenceSearchRequest.clear()
  formCatalogLoading.value = false
}

function clearManualOrganizationOptions() {
  manualOrganizationRequest++
  manualRequestContext = undefined
  manualAction.value = undefined
  manualActionContexts.value = []
  manualActionTargets.value = []
  manualActionResults.value = []
  manualActionSaving.value = false
  manualBatchAction.clear()
  manualBatchAction = createRuntimeBatchAction<RuntimeRecord>()
  for (const choices of [manualOrganizationOptions, manualDictionaryOptions, manualCascadeOptions,
    manualReferenceOptions, manualReferenceLoading, manualReferenceUnavailable, manualActionValues]) {
    Object.keys(choices).forEach(code => delete choices[code])
  }
  manualReferenceSearchTimers.forEach(timer => clearTimeout(timer))
  manualReferenceSearchTimers.clear()
  manualReferenceSearchRequest.clear()
}

function clearTransferOrganizationOptions() {
  transferOrganizationRequest++
  transferOrganizationOptions.value = undefined
  transferPreview.value = undefined
  confirmedTransferRequest.value = undefined
  transferRecord.value = undefined
  transferAction.clear()
  transferSaving.value = false
}

function closeOrganizationDialogs() {
  detailRequest++
  referenceRequest++
  referenceLoading.value = false
  Object.keys(referenceOptions).forEach(code => delete referenceOptions[code])
  formOpen.value = false
  manualActionOpen.value = false
  transferOpen.value = false
  clearFormOrganizationOptions()
  clearManualOrganizationOptions()
  clearTransferOrganizationOptions()
}

/** 来源目录由服务端从已授权记录确定，调用凭证始终保留实际操作者身份。 */
function recordOrganizationOptions(moduleCode: string, recordId: number, actionCode: string, accessToken: string, fieldCode?: string) {
  const query = new URLSearchParams({ actionCode })
  if (fieldCode) query.set('fieldCode', fieldCode)
  return api<SystemPeopleDirectory>(`/api/runtime/modules/${moduleCode}/records/${recordId}/organization-options?${query}`, {}, accessToken)
}

function formFieldDirectory(fieldCode: string) {
  return editing.value ? formOrganizationOptions[fieldCode] : peopleDirectory.value
}

/** 批量参数只能使用每条记录都授权的 ID；人员跨租户的部门信息不同则不展示。 */
function intersectOrganizationOptions(directories: SystemPeopleDirectory[]): SystemPeopleDirectory {
  const first = directories[0]
  if (!first) return { people: [], departments: [], permissionVersion: 0 }
  return {
    permissionVersion: first.permissionVersion,
    people: first.people.filter(person => directories.every(directory => directory.people.some(candidate => candidate.systemMemberId === person.systemMemberId)))
      .map(person => {
        const sameDepartment = directories.every(directory => directory.people.some(candidate => candidate.systemMemberId === person.systemMemberId
          && candidate.departmentId === person.departmentId && candidate.departmentName === person.departmentName))
        return sameDepartment ? person : { ...person, departmentId: undefined, departmentName: undefined }
      }),
    departments: first.departments.filter(department => directories.every(directory => directory.departments.some(candidate => candidate.id === department.id))),
  }
}

function manualRequestCurrent(context: ManualRequestContext) {
  return context === manualRequestContext && context.generation === manualOrganizationRequest
    && context.moduleCode === activeCode.value && context.accessToken === token.value
    && context.systemId === systemContext.value?.systemId && context.tenantId === systemContext.value?.tenantId
    && context.actionCode === manualAction.value?.code
    && (!manualActionContexts.value.length || context.recordKey === manualActionContexts.value.map(item => item.record.id).join(','))
}

/** 比较可信逻辑定义，不以来源租户不同的物理字段 ID 或 JSON 属性顺序判定。 */
function canonicalDefinition(value: unknown): string {
  if (Array.isArray(value)) return `[${value.map(canonicalDefinition).join(',')}]`
  if (value !== null && typeof value === 'object') return `{${Object.entries(value).sort(([a], [b]) => a.localeCompare(b))
    .map(([key, item]) => `${JSON.stringify(key)}:${canonicalDefinition(item)}`).join(',')}}`
  return JSON.stringify(value) ?? 'null'
}

function manualDefinition(context: RuntimeRecordActionContext) {
  return canonicalDefinition({ actionCode: context.actionCode, name: context.name,
    confirmation: Boolean(context.confirmation), confirmationText: context.confirmationText || '', parameters: context.parameters.map(field => ({
    code: field.code, name: field.name, fieldType: field.fieldType, required: field.required,
    dictionaryId: field.dictionaryId ?? null, referenceModuleId: field.referenceModuleId ?? null,
    config: JSON.parse(field.configJson || '{}'),
  })) })
}

function intersectManualChoices<T extends { value: string | number; label: string }>(choices: T[][]): T[] {
  return (choices[0] || []).filter(option => choices.every(items => items.some(candidate => candidate.value === option.value && candidate.label === option.label)))
}

function intersectManualCascades(choices: CascadeOption[][], allowIntermediate: boolean): CascadeOption[] {
  return (choices[0] || []).flatMap(option => {
    const peers = choices.map(items => items.find(candidate => candidate.value === option.value && candidate.label === option.label))
    if (peers.some(peer => !peer)) return []
    const children = intersectManualCascades(peers.map(peer => peer?.children || []), allowIntermediate)
    if (!children.length && peers.some(peer => peer?.children?.length) && !allowIntermediate) return []
    return [{ value: option.value, label: option.label, ...(children.length ? { children } : {}) }]
  })
}

function manualOptions(field: RuntimeField) {
  if (field.dictionaryId) return manualDictionaryOptions[field.code] || []
  const configured = parseFormLayout(field.configJson).options
  return Array.isArray(configured) ? configured.filter(option => option && typeof option === 'object')
    .flatMap(option => {
      const item = option as { value?: string; code?: string; label?: string; status?: string; disabled?: boolean }
      const value = item.value ?? item.code
      return value !== undefined && item.status !== 'DISABLED' && !item.disabled ? [{ value, label: item.label || value }] : []
    }) : []
}

async function manualReferenceChoices(context: ManualRequestContext, contexts: RuntimeRecordActionContext[], field: RuntimeField, search = '') {
  const query = new URLSearchParams({ actionCode: context.actionCode })
  if (search.trim()) query.set('search', search.trim())
  const choices = await Promise.all(contexts.map(item => api<Array<{ value: number; label: string; description: string }>>(
    `/api/runtime/modules/${context.moduleCode}/records/${item.record.id}/fields/${encodeURIComponent(field.code)}/reference-options?${query}`, {}, context.accessToken)))
  return intersectManualChoices(choices)
}

async function loadManualOptions(context: ManualRequestContext, contexts: RuntimeRecordActionContext[]) {
  const organizations: Record<string, SystemPeopleDirectory> = {}
  const dictionaries: Record<string, Array<{ value: string; label: string }>> = {}
  const cascades: Record<string, CascadeOption[]> = {}
  const references: Record<string, Array<{ value: number; label: string; description: string }>> = {}
  await Promise.all((contexts[0]?.parameters || []).map(async field => {
    if (!manualRequestCurrent(context)) return
    if (organizationField(field)) {
      const directories = await Promise.all(contexts.map(item => recordOrganizationOptions(context.moduleCode, item.record.id,
        context.actionCode, context.accessToken, field.code)))
      organizations[field.code] = intersectOrganizationOptions(directories)
    } else if (field.fieldType === 'CASCADE') {
      const maximumDepth = Math.max(1, fieldConfig(field).maxDepth || 20)
      const trees = await Promise.all(contexts.map(async item => {
        const loadLevel = async (parentId: number | undefined, depth: number): Promise<CascadeOption[]> => {
          if (!manualRequestCurrent(context)) return []
          const query = new URLSearchParams({ actionCode: context.actionCode })
          if (parentId !== undefined) query.set('parentId', String(parentId))
          const dictionary = await api<RuntimeDictionary>(`/api/runtime/modules/${context.moduleCode}/records/${item.record.id}/fields/${encodeURIComponent(field.code)}/dictionary?${query}`, {}, context.accessToken)
          if (!manualRequestCurrent(context)) return []
          return Promise.all(dictionary.items.map(async entry => {
            const children = depth < maximumDepth ? await loadLevel(entry.id, depth + 1) : []
            return { value: entry.id, label: entry.label, ...(children.length ? { children } : {}) }
          }))
        }
        return loadLevel(undefined, 1)
      }))
      cascades[field.code] = intersectManualCascades(trees, Boolean(fieldConfig(field).allowIntermediate))
    } else if (field.dictionaryId && ['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS'].includes(field.fieldType)) {
      const choices = await Promise.all(contexts.map(async item => {
        const query = new URLSearchParams({ actionCode: context.actionCode })
        const dictionary = await api<RuntimeDictionary>(`/api/runtime/modules/${context.moduleCode}/records/${item.record.id}/fields/${encodeURIComponent(field.code)}/dictionary?${query}`, {}, context.accessToken)
        return dictionary.items.map(entry => ({ value: entry.code, label: entry.label }))
      }))
      dictionaries[field.code] = intersectManualChoices(choices)
    } else if (field.fieldType === 'REFERENCE') references[field.code] = await manualReferenceChoices(context, contexts, field)
  }))
  return { organizations, dictionaries, cascades, references }
}

async function rejectManualContext(context: ManualRequestContext, reason: unknown) {
  if (!manualRequestCurrent(context)) return
  manualActionOpen.value = false
  clearManualOrganizationOptions()
  message.error(`动作参数无法加载：${readable(reason)}`)
  if (reason instanceof ApiError && [403, 404].includes(reason.httpStatus || 0)) await loadRecords()
}

async function openManualAction(action: import('../types').ConfiguredAction, targets: RuntimeRecord[]) {
  if (!targets.length) { message.warning('请先选择需要处理的记录'); return }
  closeOrganizationDialogs()
  manualAction.value = action
  const context: ManualRequestContext = { generation: manualOrganizationRequest, moduleCode: activeCode.value,
    accessToken: token.value, systemId: systemContext.value?.systemId, tenantId: systemContext.value?.tenantId,
    actionCode: action.code, recordKey: targets.map(record => record.id).join(',') }
  manualRequestContext = context
  try {
    const contexts = await Promise.all(targets.map(record => api<RuntimeRecordActionContext>(
      `/api/runtime/modules/${context.moduleCode}/records/${record.id}/actions/${encodeURIComponent(action.code)}/context`, {}, context.accessToken)))
    if (!manualRequestCurrent(context)) return
    if (contexts.some((item, index) => item.actionCode !== action.code || item.record.id !== targets[index]?.id)
      || !contexts[0]?.parameters.length) throw new Error('动作上下文无效')
    const definition = manualDefinition(contexts[0])
    if (contexts.some(item => manualDefinition(item) !== definition)) {
      clearManualOrganizationOptions()
      message.warning('这些记录的来源动作字段或选项配置不同，请按来源分开处理记录')
      return
    }
    const catalogs = await loadManualOptions(context, contexts)
    if (!manualRequestCurrent(context)) return
    manualAction.value = { ...action, name: contexts[0].name,
      configJson: JSON.stringify({ operation: 'UPDATE_FIELDS', parameterFieldCodes: contexts[0].parameters.map(field => field.code),
        confirmation: Boolean(contexts[0].confirmation), confirmationText: contexts[0].confirmationText || '' }) }
    manualActionContexts.value = contexts.map(item => ({ ...item, record: recordWithoutSecrets(item.record, item.parameters) }))
    manualActionTargets.value = manualActionContexts.value.map(item => item.record)
    Object.assign(manualOrganizationOptions, catalogs.organizations)
    Object.assign(manualDictionaryOptions, catalogs.dictionaries)
    Object.assign(manualCascadeOptions, catalogs.cascades)
    Object.assign(manualReferenceOptions, catalogs.references)
    if (contexts.length === 1) for (const field of contexts[0].parameters) {
      const value = contexts[0].record.fields[field.code]
      if (field.fieldType !== 'SECRET' && value !== undefined) manualActionValues[field.code] = value !== null && typeof value === 'object' ? JSON.parse(JSON.stringify(value)) : value
      if (field.fieldType === 'REFERENCE' && value != null) {
        manualReferenceUnavailable[field.code] = !manualReferenceOptions[field.code]?.some(option => option.value === Number(value))
      }
    }
    manualActionOpen.value = true
  } catch (reason) { await rejectManualContext(context, reason) }
}

function manualReferenceDisplayOptions(field: RuntimeField) {
  const choices: Array<{ value: number; label: string; description: string; disabled?: boolean }> = [...(manualReferenceOptions[field.code] || [])]
  const value = manualActionValues[field.code]
  if (typeof value === 'number' && !choices.some(option => option.value === value)) {
    choices.push({ value, label: '当前关联不可访问', description: '仅保留已返回的关联值；可以清空或重新选择', disabled: true })
  }
  return choices
}

function selectManualReference(field: RuntimeField, value: unknown) {
  manualActionValues[field.code] = value
  manualReferenceUnavailable[field.code] = value != null && !manualReferenceOptions[field.code]?.some(option => option.value === value)
}

function manualOrganizationValue(field: RuntimeField) {
  const value = manualActionValues[field.code]
  const allowed = field.fieldType === 'MEMBER' ? manualOrganizationOptions[field.code]?.people.some(person => person.systemMemberId === value)
    : manualOrganizationOptions[field.code]?.departments.some(department => department.id === value)
  return typeof value === 'number' && allowed ? value : undefined
}

function manualDisplayOptions(field: RuntimeField) {
  const choices: Array<{ value: string; label: string; disabled?: boolean }> = [...manualOptions(field)]
  const value = manualActionValues[field.code]
  if (manualActionTargets.value.length === 1 && typeof value === 'string' && !choices.some(option => option.value === value)) {
    choices.push({ value, label: '当前选项不可用', disabled: true })
  }
  return choices
}

function manualCascadeValue(field: RuntimeField) {
  const value = manualActionValues[field.code]
  if (typeof value === 'number') return cascadePath(manualCascadeOptions[field.code] || [], value)
  if (!Array.isArray(value)) return undefined
  let choices = manualCascadeOptions[field.code] || []
  for (const selected of value) {
    const option = choices.find(item => item.value === Number(selected))
    if (!option) return undefined
    choices = option.children || []
  }
  return value.map(Number)
}

function manualChoiceHint(field: RuntimeField) {
  const empty = organizationField(field) ? (field.fieldType === 'MEMBER'
    ? !manualOrganizationOptions[field.code]?.people.length : !manualOrganizationOptions[field.code]?.departments.length)
    : field.fieldType === 'CASCADE' ? !manualCascadeOptions[field.code]?.length
    : field.fieldType === 'REFERENCE' ? !manualReferenceOptions[field.code]?.length
    : ['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS'].includes(field.fieldType) ? !manualOptions(field).length : false
  if (empty) return manualActionTargets.value.length > 1
    ? '这些记录暂无共同可选项；需要选择新值时请按来源分开处理记录'
    : '当前暂无授权可选项；可以保留已返回值或清空非必填字段'
  if (field.fieldType === 'CASCADE' && manualActionValues[field.code] != null && !manualCascadeValue(field)) return '当前层级项不可用，请重新选择'
  return ''
}

function searchManualReferenceOptions(field: RuntimeField, search: string) {
  const context = manualRequestContext
  if (!context || !manualActionOpen.value || !manualRequestCurrent(context)) return
  const priorTimer = manualReferenceSearchTimers.get(field.code)
  if (priorTimer) clearTimeout(priorTimer)
  const fieldRequest = (manualReferenceSearchRequest.get(field.code) || 0) + 1
  manualReferenceSearchRequest.set(field.code, fieldRequest)
  const contexts = [...manualActionContexts.value]
  const current = () => manualActionOpen.value && manualRequestCurrent(context)
    && fieldRequest === manualReferenceSearchRequest.get(field.code)
  manualReferenceLoading[field.code] = true
  manualReferenceOptions[field.code] = []
  manualReferenceSearchTimers.set(field.code, setTimeout(() => {
    if (!current()) return
    manualReferenceSearchTimers.delete(field.code)
    void (async () => {
      if (!current()) return
      try {
        const choices = await manualReferenceChoices(context, contexts, field, search)
        if (current()) manualReferenceOptions[field.code] = choices
      } catch (reason) {
        if (current()) {
          if (reason instanceof ApiError && [403, 404].includes(reason.httpStatus || 0)) await rejectManualContext(context, reason)
          else message.warning(`关联记录搜索暂不可用：${readable(reason)}`)
        }
      } finally { if (current()) manualReferenceLoading[field.code] = false }
    })()
  }, 500))
}

function manualSubmissionValues() {
  return Object.fromEntries(manualActionFields.value.flatMap(field => {
    let value = manualActionValues[field.code]
    if (field.fieldType === 'SECRET' && (value === undefined || value === '')) return []
    if (field.fieldType === 'CASCADE' && Array.isArray(value) && fieldConfig(field).saveMode !== 'PATH') value = value[value.length - 1]
    return [[field.code, value === undefined || value === '' ? null : value]]
  }))
}

/** 明确拒绝的重试重新读取当前来源版本；未知结果继续使用执行器保留的原请求身份。 */
async function refreshRejectedManualContexts(context: ManualRequestContext) {
  const rejected = manualActionResults.value.filter(item => item.status === 'FAILED' && item.failureKind === 'REJECTED')
  const refreshed = await Promise.all(rejected.map(item => api<RuntimeRecordActionContext>(
    `/api/runtime/modules/${context.moduleCode}/records/${item.recordId}/actions/${encodeURIComponent(context.actionCode)}/context`, {}, context.accessToken)))
  if (!manualRequestCurrent(context)) return false
  const definition = manualActionContexts.value[0] && manualDefinition(manualActionContexts.value[0])
  if (refreshed.some(item => manualDefinition(item) !== definition)) {
    manualActionOpen.value = false
    clearManualOrganizationOptions()
    message.warning('来源动作配置已经变化，请重新打开动作；不同来源需分开处理记录')
    return false
  }
  const byId = new Map(refreshed.map(item => [item.record.id, item]))
  manualActionContexts.value = manualActionContexts.value.map(item => {
    const latest = byId.get(item.record.id) || item
    return { ...latest, record: recordWithoutSecrets(latest.record, latest.parameters) }
  })
  manualActionTargets.value = manualActionContexts.value.map(item => item.record)
  return true
}

async function submitManualAction(retryFailed = false) {
  const context = manualRequestContext
  if (!context || !manualActionOpen.value || !manualRequestCurrent(context) || manualActionSaving.value) return
  const fields = manualActionFields.value
  if (!fields.length) { message.error('动作字段已经不可用，请联系管理员重新发布'); return }
  const missing = fields.find(field => field.required && !(field.fieldType === 'SECRET' && manualActionValues[field.code] === undefined)
    && (manualActionValues[field.code] === undefined || manualActionValues[field.code] === null || manualActionValues[field.code] === ''))
  if (missing) { message.warning(`请填写${missing.name}`); return }
  manualActionSaving.value = true
  try {
    if (retryFailed && !await refreshRejectedManualContexts(context)) return
    if (!manualRequestCurrent(context)) return
    const targets = [...manualActionTargets.value]
    const values = manualSubmissionValues()
    const batchAction = manualBatchAction
    const result = await batchAction.submit(targets, manualActionResults.value, {
      retryFailed, token: context.accessToken, completed: '动作已执行',
      current: () => manualRequestCurrent(context),
      request: record => {
        if (!manualRequestCurrent(context)) throw new Error('动作上下文已关闭')
        return { path: `/api/runtime/modules/${context.moduleCode}/records/${record.id}/actions/${encodeURIComponent(context.actionCode)}`,
          body: { version: record.version, values } }
      },
    })
    if (!manualRequestCurrent(context)) return
    manualActionResults.value = result
    if (targets.length === 1 && result[0]?.result) lastAction.value = { moduleCode: context.moduleCode, result: result[0].result }
    // 执行器保留原回执格式；明确拒绝后用受限上下文确认是否撤权，不猜测错误文案。
    await Promise.all(result.filter(item => item.status === 'FAILED' && item.failureKind === 'REJECTED').map(async item => {
      try {
        await api<RuntimeRecordActionContext>(`/api/runtime/modules/${context.moduleCode}/records/${item.recordId}/actions/${encodeURIComponent(context.actionCode)}/context`, {}, context.accessToken)
      } catch (reason) {
        if (reason instanceof ApiError && [403, 404].includes(reason.httpStatus || 0)) await rejectManualContext(context, reason)
      }
    }))
    if (!manualRequestCurrent(context)) return
    await loadRecords().catch(() => undefined)
    if (!manualRequestCurrent(context)) return
    const current = new Map(records.value.map(record => [record.id, record]))
    // 已打开的详情同步最新版本，避免动作成功后继续展示旧值或用旧版本转交。
    if (detailOpen.value && detail.value && manualActionResults.value.some(item => item.status !== 'FAILED'
      && item.recordId === detail.value?.id)) {
      await openDetail(current.get(detail.value.id) || detail.value)
    }
    if (!manualRequestCurrent(context)) return
    const failed = manualActionResults.value.filter(item => item.status === 'FAILED').length
    selectedRowKeys.value = manualActionResults.value.filter(item => item.status === 'FAILED').map(item => item.recordId)
    if (failed) message.warning(`${manualActionResults.value.length - failed} 条成功，${failed} 条失败，可只重试失败项`)
    else message.success(`${manualActionResults.value.length} 条记录已提交`)
  } catch (reason) { await rejectManualContext(context, reason) }
  finally { if (manualRequestCurrent(context)) manualActionSaving.value = false }
}

function requestManualAction(retryFailed = false) {
  const context = manualRequestContext
  if (!context || !manualRequestCurrent(context)) return
  const config = JSON.parse(manualAction.value?.configJson || '{}') as ManualActionConfig
  const execute = () => submitManualAction(retryFailed)
  if (!config.confirmation) { void execute(); return }
  Modal.confirm({
    title: config.confirmationText || `确认执行“${manualAction.value?.name || '当前动作'}”？`,
    content: retryFailed ? '本次只重新处理失败记录，已成功记录不会重复执行。' : `将处理 ${manualActionTargets.value.length} 条记录。`,
    okText: retryFailed ? '重试失败项' : '确认执行', cancelText: '取消', onOk: execute,
  })
}

const conversionTargetOptions = computed(() => {
  const action = actions.value.find((item) => item.code === 'CONVERT')
  const codes = conversionTargetCodes(action?.configJson)
  return codes.map((code) => ({ value: code, label: modules.value.find((item) => item.moduleCode === code)?.moduleName || code }))
})

function runAction(code: string, callback: () => void, configured?: ConfiguredAction) {
  let config: ManualActionConfig
  try { config = configured ? JSON.parse(configured.configJson || '{}') as ManualActionConfig : actionConfiguration(code) }
  catch { config = {} }
  if (!config.confirmation) return callback()
  Modal.confirm({ title: config.confirmationText || '确认执行此操作？', onOk: callback })
}

function workspaceIdentity() {
  const context = systemContext.value
  return JSON.stringify([context?.accountId, context?.memberId, context?.tenantMemberId, context?.contextRevision,
    context?.roleIds, context?.permissions, context?.dataScopes, context?.tenantSwitchContext])
}

function workspaceReadContext() {
  return { selection: moduleSelectionRequest, moduleCode: activeCode.value, accessToken: token.value,
    systemId: systemContext.value?.systemId, tenantId: systemContext.value?.tenantId,
    identity: workspaceIdentity(),
    configurationMode: activeModule.value?.configurationMode || 'CURRENT' }
}

function workspaceReadCurrent(context: ReturnType<typeof workspaceReadContext>) {
  return context.selection === moduleSelectionRequest && context.moduleCode === activeCode.value && context.accessToken === token.value
    && context.systemId === systemContext.value?.systemId && context.tenantId === systemContext.value?.tenantId
    && context.identity === workspaceIdentity()
    && context.configurationMode === (activeModule.value?.configurationMode || 'CURRENT')
}

/** 清除已经失效的入口及只读投影；来源数据、业务审计和已提交请求由服务端保留。 */
function clearModuleWorkspace() {
  moduleSelectionRequest++
  moduleInitializationSelection = undefined
  rangeSharingOpen.value = false
  configurationRequest++
  savedViewsRequest++
  recordListRequest++
  queryFieldsRequest++
  loading.value = false
  activeCode.value = ''
  configuration.value = undefined
  records.value = []
  queryFields.value = []
  queryDirectoryScope.value = ''
  queryRestoreError.value = ''
  total.value = 0
  selectedRowKeys.value = []
  savedViews.value = []
  activeViewCode.value = ''
  visibleFieldCodes.value = []
  fixedFieldCodes.value = ['title']
  closeOrganizationDialogs()
  closeDetail()
  editing.value = undefined
  form.fields = {}
  timeline.value = []
  conversionLinks.value = []
  conversionRecord.value = undefined
  conversionPreview.value = undefined
  confirmedConversionRequest.value = undefined
  conversionUnlinkSource.value = undefined
  conversionUnlinkLink.value = undefined
  conversionAction.clear()
  conversionUnlinkAction.clear()
  batchConversionAction.clear()
  batchConversionTargets.value = []
  batchConversionPreviews.value = []
  batchConversionResults.value = []
  batchDeleteAction.clear()
  batchDeleteTargets.value = []
  batchDeleteResults.value = []
  lifecycleRecord.value = undefined
  deleteAction.clear()
  conversionSaving.value = false
  conversionUnlinkSaving.value = false
  lifecycleSaving.value = false
  printTemplates.value = []
  assistantOpen.value = false
  attachmentFieldCode.value = ''
  printOpen.value = false
  clearPrintPreview()
  lifecycleOpen.value = false
  conversionOpen.value = false
  conversionUnlinkOpen.value = false
  shareOpen.value = false
  importOpen.value = false
  exportOpen.value = false
  saveViewOpen.value = false
  saveViewSaving.value = false
  columnOpen.value = false
  filterOpen.value = false
  lastAction.value = undefined
  Object.keys(dictionaryOptions).forEach(code => delete dictionaryOptions[code])
  Object.keys(cascadeOptions).forEach(code => delete cascadeOptions[code])
}

function moduleEntryDenied(reason: unknown) {
  return reason instanceof ApiError && ([403, 404].includes(reason.httpStatus || 0) || recordAccessDenied(reason))
}

function removeModuleEntry(moduleCode: string) {
  modules.value = modules.value.filter(module => module.moduleCode !== moduleCode)
  if (activeCode.value === moduleCode) clearModuleWorkspace()
}

async function loadModules(reloadWorkspace = true) {
  if (!token.value) return
  const request = ++moduleDirectoryRequest
  let context = workspaceReadContext()
  const current = () => request === moduleDirectoryRequest && workspaceReadCurrent(context)
  loading.value = true
  if (reloadWorkspace) error.value = ''
  try {
    const catalog = await api<RuntimeModuleCatalogItem[]>('/api/runtime/modules', {}, context.accessToken)
    if (!current()) return
    modules.value = catalog
    if (!reloadWorkspace) {
      if (activeCode.value && (!catalog.some(module => module.moduleCode === activeCode.value)
        || context.configurationMode !== (activeModule.value?.configurationMode || 'CURRENT'))) clearModuleWorkspace()
      context = workspaceReadContext()
      return
    }
    const requestedCode = props.initialModuleCode?.trim()
    const code = modules.value.some((item) => item.moduleCode === activeCode.value)
      ? activeCode.value : modules.value.some((item) => item.moduleCode === requestedCode)
        ? requestedCode : modules.value[0]?.moduleCode
    if (code) {
      const selected = selectModule(code, true)
      context = workspaceReadContext()
      await selected
    }
    else {
      clearModuleWorkspace()
      context = workspaceReadContext()
    }
  } catch (reason) {
    if (!current()) return
    if (moduleEntryDenied(reason)) {
      modules.value = []
      clearModuleWorkspace()
      context = workspaceReadContext()
    }
    error.value = readable(reason)
    syncListRoute()
  } finally {
    if (current()) loading.value = false
  }
}

async function loadPeopleDirectory() {
  if (!token.value) return
  const accessToken = token.value
  const tenantId = systemContext.value?.tenantId
  try {
    const directory = await api<SystemPeopleDirectory>('/api/system-directory', {}, accessToken)
    if (accessToken === token.value && tenantId === systemContext.value?.tenantId) peopleDirectory.value = directory
  } catch (reason) {
    if (accessToken === token.value && tenantId === systemContext.value?.tenantId && !error.value) error.value = readable(reason)
  }
}

function applyRouteFilters() {
  const raw = Array.isArray(route.query.filters) ? route.query.filters[0] : route.query.filters
  if (typeof raw !== 'string' || !raw) return
  try {
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) throw new Error('invalid filters')
    const allowedOperators = new Set(Object.keys(queryOperatorLabels))
    if (!parsed.every((item): item is RuntimeListFilter => {
      if (!item || typeof item !== 'object' || Array.isArray(item)) return false
      const filter = item as Record<string, unknown>
      return typeof filter.fieldCode === 'string'
        && typeof filter.operator === 'string' && allowedOperators.has(filter.operator)
    })) throw new Error('invalid filters')
    listFilters.value = parsed.map(item => ({ fieldCode: item.fieldCode, operator: item.operator,
      value: item.value === undefined || item.value === null ? '' : String(item.value) }))
  } catch { queryRestoreError.value = '链接中的筛选条件无法恢复，请重新配置筛选条件。' }
}

function routeValue(key: string) {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] : value
}

function applyRouteListState() {
  const stateKeys = ['search', 'filters', 'lifecycle', 'tenantScope', 'sort', 'direction', 'page', 'pageSize']
  if (!stateKeys.some(key => routeValue(key) !== undefined && routeValue(key) !== null)) return
  queryRestoreError.value = ''
  searchText.value = String(routeValue('search') || '').slice(0, 200)
  const requestedLifecycle = String(routeValue('lifecycle') || '')
  if (['ACTIVE', 'ARCHIVED', 'DELETED'].includes(requestedLifecycle)) lifecycleState.value = requestedLifecycle as typeof lifecycleState.value
  const requestedTenantScope = String(routeValue('tenantScope') || '')
  if (['ALL', 'OWN', 'SHARED'].includes(requestedTenantScope)) tenantScope.value = requestedTenantScope as typeof tenantScope.value
  const requestedSort = String(routeValue('sort') || '')
  if (requestedSort) sortField.value = requestedSort
  const requestedDirection = String(routeValue('direction') || '')
  if (requestedDirection === 'ASC' || requestedDirection === 'DESC') sortDirection.value = requestedDirection
  const requestedPage = Number(routeValue('page'))
  if (Number.isInteger(requestedPage) && requestedPage > 0) page.value = requestedPage
  const requestedPageSize = Number(routeValue('pageSize'))
  if (Number.isInteger(requestedPageSize) && requestedPageSize >= 10 && requestedPageSize <= 200) pageSize.value = requestedPageSize
  applyRouteFilters()
}

function syncListRoute(recordId?: number) {
  if (routeInitializing || !activeCode.value) return
  const query = { ...route.query, workspace: 'runtime', module: activeCode.value } as Record<string, string | undefined>
  const setOptional = (key: string, value: string, ordinary: string) => {
    if (value && value !== ordinary) query[key] = value
    else delete query[key]
  }
  setOptional('search', searchText.value.trim(), '')
  setOptional('filters', listFilters.value.length ? JSON.stringify(listFilters.value) : '', '')
  setOptional('lifecycle', lifecycleState.value, 'ACTIVE')
  setOptional('tenantScope', tenantScope.value, 'ALL')
  setOptional('sort', sortField.value, 'updatedAt')
  setOptional('direction', sortDirection.value, 'DESC')
  setOptional('page', String(page.value), '1')
  setOptional('pageSize', String(pageSize.value), '20')
  const openRecordId = recordId || (detailOpen.value ? detail.value?.id : undefined)
  if (openRecordId) query.recordId = String(openRecordId)
  else delete query.recordId
  const keys = new Set([...Object.keys(route.query), ...Object.keys(query)])
  const unchanged = [...keys].every(key => String(route.query[key] ?? '') === String(query[key] ?? ''))
  if (!unchanged) return router.replace({ path: route.path, query })
}

async function selectModule(code: string, fromRoute = false) {
  const selected = modules.value.find(module => module.moduleCode === code)
  if (!selected) return false
  moduleSelectionRequest++
  moduleInitializationSelection = moduleSelectionRequest
  rangeSharingOpen.value = false
  configurationRequest++
  savedViewsRequest++
  recordListRequest++
  queryFieldsRequest++
  queryFields.value = []
  queryDirectoryScope.value = ''
  queryRestoreError.value = ''
  if (code !== activeCode.value) {
    records.value = []
    total.value = 0
    closeOrganizationDialogs()
    detailOpen.value = false; detail.value = undefined; lastOpenedRouteRecordId.value = undefined
  }
  activeCode.value = code
  if (selected.configurationMode === 'SHARED_RECORDS') closeOrganizationDialogs()
  configuration.value = undefined
  savedViews.value = []
  Object.keys(dictionaryOptions).forEach(fieldCode => delete dictionaryOptions[fieldCode])
  Object.keys(cascadeOptions).forEach(fieldCode => delete cascadeOptions[fieldCode])
  const context = workspaceReadContext()
  const current = () => workspaceReadCurrent(context)
  loading.value = true
  error.value = ''
  lifecycleState.value = 'ACTIVE'
  tenantScope.value = 'ALL'
  searchText.value = ''
  listFilters.value = []
  sortField.value = 'updatedAt'
  sortDirection.value = 'DESC'
  visibleFieldCodes.value = []
  fixedFieldCodes.value = ['title']
  selectedRowKeys.value = []
  activeViewCode.value = ''
  page.value = 1
  try {
    // 可信目录明确区分共享记录入口；不把 configuration 的失败变成来源配置回退。
    if (selected.configurationMode !== 'SHARED_RECORDS' && !await loadConfiguration()) return false
    if (!current()) return false
    // 先恢复个人视图范围，再取得对应查询目录；归档/仅共享视图不能先按默认范围裁剪。
    if (!await loadSavedViews() || !current()) return false
    const preferred = savedViews.value.find((view) => view.defaultView)
    if (preferred) applySavedView(preferred, false)
    if (fromRoute) applyRouteListState()
    if (!await loadQueryFields() || !current()) return false
    // 个人视图明确保存空列时保留其选择；只有无默认视图才初始化布局默认列。
    if (!preferred) initializeColumns()
    moduleInitializationSelection = undefined
    await loadRecords()
    return current()
  } catch (reason) {
    if (!current()) return false
    if (moduleEntryDenied(reason)) {
      removeModuleEntry(code)
      error.value = readable(reason)
      await loadModules(false)
    } else error.value = readable(reason)
    return false
  } finally {
    if (moduleInitializationSelection === context.selection) moduleInitializationSelection = undefined
    if (current()) loading.value = false
  }
}

function returnToDashboard() {
  if (dashboardReturn.value) void router.push(dashboardReturn.value)
}

async function loadConfiguration() {
  if (sharedRecordsMode.value) return false
  const request = ++configurationRequest
  const context = workspaceReadContext()
  const loaded = await api<RuntimeModuleConfiguration>(
    `/api/runtime/modules/${context.moduleCode}/configuration`, {}, context.accessToken)
  if (request !== configurationRequest || !workspaceReadCurrent(context)) return false
  configuration.value = loaded
  await loadDictionaryOptions()
  return request === configurationRequest && workspaceReadCurrent(context)
}

function fieldConfig(field: RuntimeField) {
  try { return JSON.parse(field.configJson || '{}') as {
    maxDepth?: number
    allowIntermediate?: boolean
    saveMode?: 'FINAL' | 'PATH'
    defaultValue?: unknown
    jsonSchema?: JsonSchema
    sensitive?: boolean
    readOnly?: boolean
  } }
  catch { return {} }
}

function initialFieldValue(field: RuntimeField) {
  if (field.fieldType === 'SECRET') return undefined
  const configured = fieldConfig(field).defaultValue
  if (configured === undefined || configured === null || configured === '') {
    if (field.fieldType === 'JSON' && field.required) return emptyJsonValue(fieldConfig(field).jsonSchema || newJsonSchema())
    return field.fieldType === 'BOOLEAN' ? false : undefined
  }
  if (field.fieldType === 'BOOLEAN') return configured === true || String(configured).toLowerCase() === 'true'
  if (['NUMBER', 'MONEY', 'MEMBER', 'DEPARTMENT', 'REFERENCE'].includes(field.fieldType)) {
    const numeric = Number(configured)
    return Number.isFinite(numeric) ? numeric : configured
  }
  return configured
}

/** 字典字段统一读取当前发布的选项；单选、状态和级联不回退到已过期的手动配置。 */
async function loadDictionaryOptions() {
  if (sharedRecordsMode.value) return
  const context = workspaceReadContext()
  const currentConfiguration = configuration.value
  const accessToken = token.value
  const moduleCode = activeCode.value
  const isCurrent = () => configuration.value === currentConfiguration && workspaceReadCurrent(context)
  Object.keys(dictionaryOptions).forEach(code => delete dictionaryOptions[code])
  Object.keys(cascadeOptions).forEach((code) => delete cascadeOptions[code])
  const selectionFields = currentConfiguration?.configuration.fields
    .filter(field => ['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS'].includes(field.fieldType) && field.dictionaryId) || []
  await Promise.all(selectionFields.map(async field => {
    const dictionary = await api<RuntimeDictionary>(`/api/runtime/dictionaries/by-id/${field.dictionaryId}`, {}, accessToken)
    if (isCurrent()) {
      dictionaryOptions[field.code] = dictionary.items.map(item => ({ value: item.code, label: item.label }))
    }
  }))
  const cascadeFields = currentConfiguration?.configuration.fields
    .filter((field) => field.fieldType === 'CASCADE' && field.dictionaryId) || []
  await Promise.all(cascadeFields.map(async (field) => {
    const maximumDepth = Math.max(1, fieldConfig(field).maxDepth || 20)
    const loadLevel = async (parentId: number | undefined, depth: number): Promise<CascadeOption[]> => {
      if (!isCurrent()) return []
      const query = parentId ? `?parentId=${parentId}` : ''
      const dictionary = await api<RuntimeDictionary>(
        `/api/runtime/dictionaries/by-id/${field.dictionaryId}${query}`, {}, accessToken)
      if (!isCurrent()) return []
      return Promise.all(dictionary.items.map(async (item) => {
        const children = depth < maximumDepth ? await loadLevel(item.id, depth + 1) : []
        return { value: item.id, label: item.label, ...(children.length ? { children } : {}) }
      }))
    }
    const loaded = await loadLevel(undefined, 1)
    if (isCurrent()) cascadeOptions[field.code] = loaded
  }))
}

function cascadePath(optionsValue: CascadeOption[], selectedId: number, prefix: number[] = []): number[] | undefined {
  for (const option of optionsValue) {
    const path = [...prefix, option.value]
    if (option.value === selectedId) return path
    const nested = option.children ? cascadePath(option.children, selectedId, path) : undefined
    if (nested) return nested
  }
  return undefined
}

function cascadeEditValue(field: RuntimeField, value: unknown, choices = cascadeOptions) {
  if (Array.isArray(value)) return value.map(Number)
  if (typeof value === 'number') return cascadePath(choices[field.code] || [], value) || [value]
  return value
}

function cascadeLabels(optionsValue: CascadeOption[], selected: number[]) {
  const labels: string[] = []
  let current = optionsValue
  for (const id of selected) {
    const option = current.find((item) => item.value === id)
    if (!option) break
    labels.push(option.label)
    current = option.children || []
  }
  return labels
}

function listQueryScope() {
  return `${lifecycleState.value}:${tenantScope.value}`
}

function clearListData() {
  records.value = []
  total.value = 0
  selectedRowKeys.value = []
  closeOrganizationDialogs()
  closeDetail()
}

function queryValidationError(filters: RuntimeListFilter[], orderField: string, fieldsToCheck = queryFields.value) {
  const byCode = new Map(fieldsToCheck.map(field => [field.code, field]))
  for (const condition of filters) {
    const field = byCode.get(condition.fieldCode)
    if (!field) return '原筛选字段在当前范围内已不可用，请移除该条件或重新选择字段。'
    if (!field.operators.includes(condition.operator)) return `“${field.name}”不支持原筛选方式，请重新选择。`
    if (!['EMPTY', 'NOT_EMPTY'].includes(condition.operator) && !condition.value.trim()) return `请填写“${field.name}”的筛选值。`
  }
  if (!byCode.get(orderField)?.sortable) return '原排序字段在当前范围内已不可用，请重新选择排序字段。'
  return ''
}

/** 服务端已按各视图自己的范围裁剪，不能用当前页面目录再次删除其他范围的列。 */
function reconcileSavedViews(views: RuntimeListView[]) {
  return views.map(view => ({ ...view, queryInvalid: Boolean(view.queryInvalid) }))
}

function updateQueryDirectory(fields: RuntimeListQueryField[], scope: string) {
  const previousCodes = new Set(queryFields.value.map(field => field.code))
  queryFields.value = fields
  queryDirectoryScope.value = scope
  const codes = new Set(fields.map(field => field.code))
  visibleFieldCodes.value = visibleFieldCodes.value.filter(code => codes.has(code))
  if (previousCodes.size && !activeViewCode.value) {
    const addedSourceCodes = currentColumns.value.filter(column => column.readOnlySource && !previousCodes.has(column.code)).map(column => column.code)
    visibleFieldCodes.value = [...new Set([...visibleFieldCodes.value, ...addedSourceCodes])]
  }
  fixedFieldCodes.value = fixedFieldCodes.value.filter(code => visibleFieldCodes.value.includes(code))
  savedViews.value = reconcileSavedViews(savedViews.value)
}

async function loadQueryFields() {
  const request = ++queryFieldsRequest
  const context = workspaceReadContext()
  const scope = listQueryScope()
  const params = new URLSearchParams({ lifecycleState: lifecycleState.value, tenantScope: tenantScope.value })
  const fields = await api<RuntimeListQueryField[]>(
    `/api/runtime/modules/${context.moduleCode}/records/query-fields?${params}`, {}, context.accessToken)
  if (request !== queryFieldsRequest || !workspaceReadCurrent(context) || scope !== listQueryScope()) return false
  updateQueryDirectory(fields, scope)
  return true
}

/** 失权后重新获取可信目录与个人视图，但保留筛选条件，不自动重发宽查询。 */
async function recoverListAccess(context: ReturnType<typeof workspaceReadContext>, reason: unknown, current: () => boolean) {
  if (!current()) return
  clearListData()
  queryFieldsRequest++
  queryFields.value = []
  queryDirectoryScope.value = ''
  savedViews.value = []
  error.value = readable(reason)
  try {
    if (!await loadQueryFields() || !current()) return
    await loadSavedViews()
    if (!current()) return
    error.value = queryValidationError(listFilters.value, sortField.value)
      || '查询权限或共享范围已变化，旧结果已清除。请确认当前条件后重试。'
  } catch (directoryReason) {
    if (!current()) return
    if (moduleEntryDenied(directoryReason)) {
      removeModuleEntry(context.moduleCode)
      error.value = readable(directoryReason)
      await loadModules(false)
    } else error.value = readable(directoryReason)
  }
}

async function loadRecords() {
  // 已生效回执可能在冷进入时恢复；首次查询必须等待范围、目录和显示列初始化。
  if (moduleInitializationSelection === moduleSelectionRequest) return
  if (!activeCode.value || !token.value || !activeModule.value) return
  const request = ++recordListRequest
  const context = workspaceReadContext()
  const moduleCode = activeCode.value
  const accessToken = token.value
  const scope = listQueryScope()
  const current = () => request === recordListRequest && workspaceReadCurrent(context) && scope === listQueryScope()
  loading.value = true
  error.value = ''
  try {
    if (queryDirectoryScope.value !== listQueryScope() && (!await loadQueryFields() || !current())) return
    let invalid = queryRestoreError.value || queryValidationError(listFilters.value, sortField.value)
    if (invalid) {
      // 重新授权后允许保留原条件重试；刷新可信目录，不能靠删除失效条件扩大结果。
      if (!await loadQueryFields() || !current() || !await loadSavedViews() || !current()) return
      if (queryRestoreError.value && activeViewCode.value) {
        const restored = savedViews.value.find(view => view.code === activeViewCode.value)
        if (restored && !restored.queryInvalid
          && `${restored.lifecycleState}:${restored.tenantScope}` === scope) applySavedView(restored, false)
      }
      invalid = queryRestoreError.value || queryValidationError(listFilters.value, sortField.value)
    }
    if (invalid) { clearListData(); error.value = invalid; return }
    const params = new URLSearchParams({
      lifecycleState: lifecycleState.value,
      tenantScope: tenantScope.value,
      search: searchText.value.trim(),
      filters: JSON.stringify(listFilters.value),
      sortField: sortField.value,
      sortDirection: sortDirection.value,
      page: String(page.value),
      pageSize: String(pageSize.value),
    })
    const result = await api<RuntimeRecordList>(
      `/api/runtime/modules/${moduleCode}/records?${params}`, {}, accessToken)
    if (!current()) return
    queryFieldsRequest++
    updateQueryDirectory(result.queryFields, scope)
    records.value = result.records
    total.value = result.total
    selectedRowKeys.value = selectedRowKeys.value.filter((id) => result.records.some((record) => record.id === id))
    if (detailOpen.value && detail.value) {
      const refreshed = result.records.find(record => record.id === detail.value?.id)
      if (!refreshed || !canRecordAction('DETAIL', refreshed)) closeDetail()
      else {
        // LIST 与 DETAIL 的字段裁剪不同，刷新时重新取得详情投影，不能用列表描述代替。
        detail.value = undefined
        detailOpen.value = false
        await openDetail(refreshed)
        if (!current()) return
      }
    }
    // 先完成当前列表的地址同步，再允许调用方切换转化目标模块。
    await syncListRoute()
  } catch (reason) {
    if (!current()) return
    if (moduleEntryDenied(reason)) { await recoverListAccess(context, reason, current); return }
    error.value = readable(reason)
    await syncListRoute()
  } finally {
    if (current()) loading.value = false
  }
}

function initializeColumns() {
  visibleFieldCodes.value = [...new Set([...listSettings.value.visible,
    ...currentColumns.value.filter(column => column.readOnlySource).map(column => column.code)])]
  fixedFieldCodes.value = [...listSettings.value.fixed]
}

async function loadSavedViews() {
  const request = ++savedViewsRequest
  const context = workspaceReadContext()
  const current = () => request === savedViewsRequest && workspaceReadCurrent(context)
  try {
    const views = await api<RuntimeListView[]>(
      `/api/runtime/modules/${context.moduleCode}/list-views`, {}, context.accessToken)
    if (!current()) return false
    savedViews.value = reconcileSavedViews(views)
    return true
  } catch (reason) {
    if (!current()) return false
    throw reason
  }
}

function applySavedView(view: RuntimeListView, reload = true) {
  clearListData()
  lifecycleState.value = view.lifecycleState
  tenantScope.value = view.tenantScope
  searchText.value = view.search || ''
  listFilters.value = view.filters.map(filter => ({ ...filter }))
  sortField.value = view.sortField
  sortDirection.value = view.sortDirection
  // 列先保留服务端按视图范围裁剪的结果，待对应目录返回后再统一核对。
  visibleFieldCodes.value = [...view.visibleFieldCodes]
  fixedFieldCodes.value = view.fixedFieldCodes.filter((code) => visibleFieldCodes.value.includes(code))
  pageSize.value = view.pageSize
  activeViewCode.value = view.code
  page.value = 1
  queryRestoreError.value = view.queryInvalid ? '此常用视图的原筛选或排序已失效，请重新配置后保存。' : ''
  if (queryRestoreError.value) {
    clearListData()
    error.value = queryRestoreError.value
    message.warning(queryRestoreError.value)
  }
  if (reload) void loadRecords()
}

function selectSavedView(code: string) {
  if (!code) {
    activeViewCode.value = ''
    return
  }
  const view = savedViews.value.find((item) => item.code === code)
  if (view) applySavedView(view)
}

function openSaveView() {
  viewForm.name = activeSavedView.value?.name || ''
  viewForm.defaultView = activeSavedView.value?.defaultView || false
  saveViewOpen.value = true
}

async function saveCurrentView() {
  if (!viewForm.name.trim()) {
    message.warning('请填写视图名称')
    return
  }
  const invalid = queryRestoreError.value || queryValidationError(listFilters.value, sortField.value)
  if (invalid) { message.warning(invalid); return }
  saveViewSaving.value = true
  const context = workspaceReadContext()
  const scope = listQueryScope()
  const current = () => workspaceReadCurrent(context) && scope === listQueryScope()
  try {
    const allowedColumns = new Set(currentColumns.value.map(column => column.code))
    const code = activeSavedView.value?.code || `view_${Date.now().toString(36)}`
    const saved = await api<RuntimeListView>(`/api/runtime/modules/${context.moduleCode}/list-views/${code}`, {
      method: 'PUT',
      body: JSON.stringify({
        name: viewForm.name.trim(), search: searchText.value.trim(), filters: listFilters.value,
        sortField: sortField.value, sortDirection: sortDirection.value,
        visibleFieldCodes: visibleFieldCodes.value.filter(code => allowedColumns.has(code)), fixedFieldCodes: fixedFieldCodes.value.filter(code => allowedColumns.has(code)),
        pageSize: pageSize.value, defaultView: viewForm.defaultView,
        lifecycleState: lifecycleState.value, tenantScope: tenantScope.value,
        ...(activeSavedView.value ? { expectedVersion: activeSavedView.value.version } : {}),
      }),
    }, context.accessToken)
    if (!current() || !await loadSavedViews() || !current()) return
    activeViewCode.value = saved.code
    saveViewOpen.value = false
    message.success('常用视图已保存并可在下次进入时恢复')
  } catch (reason) {
    if (!current()) return
    if (moduleEntryDenied(reason)) await recoverListAccess(context, reason, current)
    message.error(readable(reason))
  }
  finally { if (workspaceReadCurrent(context)) saveViewSaving.value = false }
}

function deleteCurrentView() {
  const current = activeSavedView.value
  if (!current) return
  const context = workspaceReadContext()
  Modal.confirm({
    title: `删除常用视图“${current.name}”？`,
    async onOk() {
      if (!workspaceReadCurrent(context)) return
      try {
        await api(`/api/runtime/modules/${context.moduleCode}/list-views/${current.code}`, {
          method: 'DELETE', body: JSON.stringify({ expectedVersion: current.version }),
        }, context.accessToken)
        if (!workspaceReadCurrent(context) || !await loadSavedViews() || !workspaceReadCurrent(context)) return
        activeViewCode.value = ''
        message.success('常用视图已删除')
      } catch (reason) {
        if (!workspaceReadCurrent(context)) return
        if (context.configurationMode === 'SHARED_RECORDS' && moduleEntryDenied(reason)) {
          removeModuleEntry(context.moduleCode)
          await loadModules(false)
        }
        message.error(readable(reason))
      }
    },
  })
}

function addFilter() {
  const field = queryFields.value.find(item => item.operators.length)
  if (!field) { message.info('当前范围内没有可筛选字段。'); return }
  const condition: RuntimeListFilter = { fieldCode: field.code, operator: 'EQ', value: '' }
  resetFilterCondition(condition)
  listFilters.value.push(condition)
}

function filterRuntimeField(condition: RuntimeListFilter) {
  return queryFields.value.find(field => field.code === condition.fieldCode)
}

function conditionOperators(condition: RuntimeListFilter) {
  return (filterRuntimeField(condition)?.operators || []).flatMap(operator =>
    Object.prototype.hasOwnProperty.call(queryOperatorLabels, operator)
      ? [{ value: operator, label: queryOperatorLabels[operator as RuntimeListFilter['operator']] }] : [])
}

function resetFilterCondition(condition: RuntimeListFilter) {
  const operators = conditionOperators(condition)
  const preferred = operators.find(item => item.value === 'CONTAINS') || operators.find(item => item.value === 'EQ') || operators[0]
  if (preferred) condition.operator = preferred.value as RuntimeListFilter['operator']
  condition.value = ''
}

function filterValueType(condition: RuntimeListFilter) {
  const type = filterRuntimeField(condition)?.fieldType
  if (['SYSTEM_CREATED_BY', 'SYSTEM_UPDATED_BY'].includes(type || '')) return 'MEMBER'
  if (['SYSTEM_CREATED_AT', 'SYSTEM_UPDATED_AT'].includes(type || '')) return 'DATETIME'
  return type
}

function filterUsesOptions(condition: RuntimeListFilter) {
  return ['SINGLE_SELECT', 'STATUS', 'CASCADE', 'REFERENCE', 'MEMBER', 'DEPARTMENT'].includes(filterValueType(condition) || '')
}

function filterValueOptions(condition: RuntimeListFilter) {
  const options = filterRuntimeField(condition)?.options || []
  // 历史选择已不在当前记录目录时仍保留原条件，可查询零条，但不回显内部 ID 或猜测名称。
  return condition.value && !options.some(option => option.value === condition.value)
    ? [...options, { value: condition.value, label: '原选择（当前选项中不可用）', disabled: true }] : options
}

function numericFilterValue(condition: RuntimeListFilter) {
  return condition.value || undefined
}

function setNumericFilterValue(condition: RuntimeListFilter, value: number | string | null) {
  condition.value = value === null || value === undefined ? '' : String(value)
}

function applyListQuery() {
  const invalid = queryValidationError(listFilters.value, sortField.value)
  if (invalid) { message.warning(invalid); return }
  queryRestoreError.value = ''
  page.value = 1
  filterOpen.value = false
  activeViewCode.value = ''
  void loadRecords()
}

function clearListQuery() {
  queryRestoreError.value = ''
  searchText.value = ''
  listFilters.value = []
  sortField.value = 'updatedAt'
  sortDirection.value = 'DESC'
  applyListQuery()
}

function toggleColumn(code: string, checked: boolean) {
  if (checked) visibleFieldCodes.value = [...visibleFieldCodes.value, code]
  else {
    visibleFieldCodes.value = visibleFieldCodes.value.filter((item) => item !== code)
    fixedFieldCodes.value = fixedFieldCodes.value.filter((item) => item !== code)
  }
  activeViewCode.value = ''
}

function toggleFixedColumn(code: string, checked: boolean) {
  fixedFieldCodes.value = checked
    ? [...new Set([...fixedFieldCodes.value, code])].slice(0, 3)
    : fixedFieldCodes.value.filter((item) => item !== code)
  activeViewCode.value = ''
}

function moveColumn(targetCode: string) {
  const source = draggingColumn.value
  draggingColumn.value = ''
  if (!source || source === targetCode) return
  const next = [...visibleFieldCodes.value]
  const sourceIndex = next.indexOf(source)
  const targetIndex = next.indexOf(targetCode)
  if (sourceIndex < 0 || targetIndex < 0) return
  const [moved] = next.splice(sourceIndex, 1)
  if (!moved) return
  next.splice(targetIndex, 0, moved)
  visibleFieldCodes.value = next
  activeViewCode.value = ''
}

function rowHandlers(record: RuntimeRecord, index?: number) {
  return {
    tabindex: 0,
    class: index === focusedRowIndex.value ? 'runtime-row-focused' : '',
    onClick: (event: MouseEvent) => {
      const target = event.target as HTMLElement
      if (target.closest('button,a,input,textarea,select,.ant-checkbox,.ant-select,.ant-picker,.ant-dropdown')) return
      if (canRecordAction('DETAIL', record)) void openDetail(record)
      else if (canRecordAction('UPDATE', record)) void openEdit(record)
    },
    onFocus: () => { focusedRowIndex.value = index ?? -1 },
    onKeydown: (event: KeyboardEvent) => {
      const current = index ?? 0
      if (event.key === 'Enter' && (canRecordAction('DETAIL', record) || canRecordAction('UPDATE', record))) {
        event.preventDefault()
        if (canRecordAction('DETAIL', record)) void openDetail(record)
        else void openEdit(record)
        return
      }
      if (!['ArrowUp', 'ArrowDown'].includes(event.key)) return
      event.preventDefault()
      focusedRowIndex.value = Math.max(0, Math.min(records.value.length - 1,
        current + (event.key === 'ArrowDown' ? 1 : -1)))
      document.querySelectorAll<HTMLElement>('.record-table .ant-table-row')[focusedRowIndex.value]?.focus()
    },
  }
}

function columnValue(column: RuntimeColumn, record: RuntimeRecord) {
  const presentation = record.fieldPresentation?.[column.code]
  if (presentation && (column.field || column.readOnlySource)) return readFieldValue({ code: column.code, ...presentation }, record.fields[column.code], record)
  if (column.readOnlySource) return '—'
  if (column.field) return displayValue(column.field, record.fields[column.code], record)
  if (column.code === 'ownerMemberId') return ownerDisplayName(record)
  if (column.code === 'departmentId') return record.departmentId ? organizationDepartmentName(record, record.departmentId) : '未设置'
  if (column.code === 'recordNumber') return userFacingRecordNumber(record.recordNumber, '未设置业务编号')
  if (column.code === 'dataTenantName') return userFacingWorkspaceName(record.dataTenantName)
  if (column.code === 'createdAt' || column.code === 'updatedAt') return productDateTime(record[column.code])
  return record[column.code as 'title' | 'status' | 'createdAt' | 'updatedAt'] || '—'
}

function recordReadFields(record: RuntimeRecord): RuntimeReadField[] {
  return Object.entries(record.fieldPresentation || {}).map(([code, presentation]) => ({ code, ...presentation }))
    .sort((left, right) => left.sortOrder - right.sortOrder || left.code.localeCompare(right.code))
}

/** 表单与动作缓存只保留非秘密读取值，秘密更新由用户重新输入。 */
function recordWithoutSecrets(record: RuntimeRecord, actualFields: RuntimeField[] = []): RuntimeRecord {
  const codes = new Set([...actualFields.filter(field => field.fieldType === 'SECRET').map(field => field.code),
    ...Object.entries(record.fieldPresentation || {}).filter(([, field]) => field.fieldType === 'SECRET').map(([code]) => code)])
  return { ...record, fields: Object.fromEntries(Object.entries(record.fields).filter(([code]) => !codes.has(code))),
    ...(record.fieldPresentation ? { fieldPresentation: Object.fromEntries(Object.entries(record.fieldPresentation).filter(([code]) => !codes.has(code))) } : {}) }
}

function sourceColumns(currentRecords: RuntimeRecord[], columns: RuntimeColumn[]): RuntimeColumn[] {
  const codes = new Set(columns.map(column => column.code))
  const descriptors = currentRecords.flatMap(recordReadFields).sort((left, right) => left.sortOrder - right.sortOrder || left.code.localeCompare(right.code))
  return descriptors.flatMap(field => {
    if (codes.has(field.code)) return []
    codes.add(field.code)
    // 名称一致时使用已授权的业务名称；多来源异名时由每行标明，列头不暴露内部编码。
    const names = new Set(descriptors.filter(item => item.code === field.code).map(item => item.name))
    const label = names.size === 1 ? field.name : '共享来源字段'
    return [{ code: field.code, label, width: 140, readOnlySource: true }]
  })
}

function readFieldValue(field: RuntimeReadField, value: unknown, record: RuntimeRecord) {
  if (typeof field.displayValue === 'string') return field.displayValue
  if (value === undefined || value === null || value === '') return '—'
  if (value === '***') return '••••（已脱敏）'
  if (value === '[已散列]') return '不可回显（已散列）'
  if (field.fieldType === 'SECRET') return '••••（已脱敏）'
  if (field.fieldType === 'BOOLEAN') return value ? '是' : '否'
  if (['SYSTEM_CREATED_AT', 'SYSTEM_UPDATED_AT'].includes(field.fieldType)) return productDateTime(String(value))
  if (['MEMBER', 'SYSTEM_CREATED_BY', 'SYSTEM_UPDATED_BY'].includes(field.fieldType)) return organizationMemberName(record, value)
  if (field.fieldType === 'DEPARTMENT') return organizationDepartmentName(record, value)
  if (attachmentFieldTypes.has(field.fieldType)) return Array.isArray(value) ? `已上传 ${value.length} 个文件` : '已上传文件'
  // 没有可信展示文本时，不读取接收方字典或按当前配置重建关联名称。
  if (['SINGLE_SELECT', 'STATUS', 'CASCADE', 'REFERENCE'].includes(field.fieldType)) return '名称暂不可用'
  if (field.fieldType === 'RICH_TEXT') return richPlainText(String(value)) || '—'
  if (field.fieldType === 'JSON') return structuredSummary(value)
  return String(value)
}

function recordFieldVisible(record: RuntimeRecord | undefined, code: string) {
  if (record?.fieldPresentation) return Object.prototype.hasOwnProperty.call(record.fieldPresentation, code)
  return !record?.shared || Object.prototype.hasOwnProperty.call(record.fields, code)
}

function recordDisplayField(field: RuntimeField, record?: RuntimeRecord): RuntimeField {
  const presentation = record?.fieldPresentation?.[field.code]
  if (presentation) return { ...field, name: presentation.name, fieldType: presentation.fieldType }
  // 旧共享回执没有可信来源描述时，仅显示已返回的值，不冒用接收方名称或类型。
  return record?.shared && !record.fieldPresentation
    ? { ...field, name: '共享字段（来源名称暂不可用）', fieldType: 'TEXT' } : field
}

function columnSourceName(column: RuntimeColumn, record: RuntimeRecord) {
  if (column.field && record.shared && !record.fieldPresentation && recordFieldVisible(record, column.code)) return '名称暂不可用'
  const name = (column.field || column.readOnlySource) && record.fieldPresentation?.[column.code]?.name
  return name && name !== column.label ? name : ''
}

function ownerDisplayName(record: RuntimeRecord) {
  if (!record.ownerMemberId) return '未分配'
  return organizationMemberName(record, record.ownerMemberId)
}

function organizationMemberName(record: RuntimeRecord | undefined, memberId: unknown) {
  return record?.organizationLabels?.members[String(memberId)] || '不可用成员'
}

function organizationDepartmentName(record: RuntimeRecord | undefined, departmentId: unknown) {
  return record?.organizationLabels?.departments[String(departmentId)] || '不可用部门'
}

async function openShare(record: RuntimeRecord) {
  shareSaving.value = true
  try {
    const [targets, existing] = await Promise.all([
      api<TenantShareTarget[]>(`/api/runtime/modules/${activeCode.value}/records/share-targets`, {}, token.value),
      api<RuntimeTenantShare[]>(`/api/runtime/modules/${activeCode.value}/records/${record.id}/shares`, {}, token.value),
    ])
    shareRecord.value = record
    shareTargets.value = targets
    shareRows.value = existing
    Object.assign(shareForm, { targetTenantId: targets[0]?.tenantId, allowedActions: [], expiresAt: '' })
    shareOpen.value = true
  } catch (reason) { message.error(readable(reason)) } finally { shareSaving.value = false }
}

async function grantShare() {
  if (!shareRecord.value || !shareForm.targetTenantId) {
    message.warning('请选择目标租户')
    return
  }
  shareSaving.value = true
  try {
    const current = shareRows.value.find((item) => item.targetTenantId === shareForm.targetTenantId && item.status === 'ACTIVE')
    await api<RuntimeTenantShare>(`/api/runtime/modules/${activeCode.value}/records/${shareRecord.value.id}/shares`, {
      method: 'POST',
      body: JSON.stringify({ targetTenantId: shareForm.targetTenantId, allowedActions: shareForm.allowedActions,
        expiresAt: shareForm.expiresAt || null, version: current?.version }),
    }, token.value)
    shareRows.value = await api<RuntimeTenantShare[]>(
      `/api/runtime/modules/${activeCode.value}/records/${shareRecord.value.id}/shares`, {}, token.value)
    message.success('共享授权已生效，正文仍保留在来源租户')
  } catch (reason) { message.error(readable(reason)) } finally { shareSaving.value = false }
}

function revokeShare(row: RuntimeTenantShare) {
  Modal.confirm({
    title: `撤销对“${row.targetTenantName}”的共享？`,
    content: '撤销后目标租户立即失去新的访问权限，来源数据和历史记录不会删除。',
    okText: '确认撤销', okType: 'danger',
    async onOk() {
      if (!shareRecord.value) return
      const revoked = await api<RuntimeTenantShare>(
        `/api/runtime/modules/${activeCode.value}/records/${shareRecord.value.id}/shares/${row.id}/revoke`,
        { method: 'POST', body: JSON.stringify({ version: row.version, reason: '来源租户页面撤销共享' }) }, token.value)
      shareRows.value = shareRows.value.map((item) => item.id === revoked.id ? revoked : item)
      message.success('共享已撤销')
    },
  })
}

function recordAccessDenied(reason: unknown) {
  return reason instanceof ApiError && ['PERMISSION_DENIED', 'RECORD_NOT_FOUND', 'TENANT_SHARE_NOT_ACTIVE',
    'TENANT_SHARE_ACTION_DENIED', 'RECORD_DATA_SCOPE_DENIED'].includes(reason.code)
}

/** 服务端确认记录失去访问权后，关闭旧表单、详情及缓存的来源选项。 */
async function dismissInaccessibleRecord() {
  if (editing.value) localStorage.removeItem(formDraftKey())
  closeOrganizationDialogs()
  closeDetail()
  editing.value = undefined
  form.fields = {}
  await loadRecords()
  message.warning('记录已不可访问，已关闭表单和详情并刷新列表')
}

async function openDetail(record: RuntimeRecord) {
  const request = ++detailRequest
  const moduleCode = activeCode.value
  const accessToken = token.value
  const systemId = systemContext.value?.systemId
  const tenantId = systemContext.value?.tenantId
  const current = () => request === detailRequest && moduleCode === activeCode.value && accessToken === token.value
    && systemId === systemContext.value?.systemId && tenantId === systemContext.value?.tenantId
  try {
    const [recordDetail, recordTimeline, recordConversions, availablePrintTemplates] = await Promise.all([
      api<RuntimeRecord>(`/api/runtime/modules/${moduleCode}/records/${record.id}`, {}, accessToken),
      api<RuntimeRecordTimeline>(`/api/runtime/modules/${moduleCode}/records/${record.id}/timeline`, {}, accessToken),
      api<{ links: RuntimeRecordConversionLink[] }>(`/api/runtime/modules/${moduleCode}/records/${record.id}/conversions`, {}, accessToken),
      api<RuntimePrintTemplate[]>(`/api/print/runtime/modules/${moduleCode}/templates`, {}, accessToken).catch(() => []),
    ])
    if (!current()) return
    detail.value = recordDetail
    if (!recordDetail.fieldPresentation && !recordDetail.shared && fields.value.some(field => field.fieldType === 'REFERENCE'
      && recordDetail.fields[field.code] != null
      && !referenceOptions[field.code]?.some(option => option.value === Number(recordDetail.fields[field.code])))) {
      loadReferenceOptions()
    }
    if (!recordAttachmentFields.value.some(field => field.code === attachmentFieldCode.value)) {
      attachmentFieldCode.value = recordAttachmentFields.value[0]?.code || ''
    }
    timeline.value = recordTimeline.entries
    conversionLinks.value = recordConversions.links
    printTemplates.value = availablePrintTemplates
    detailOpen.value = true
    lastOpenedRouteRecordId.value = record.id
    syncListRoute(record.id)
  } catch (reason) {
    if (!current()) return
    if (recordAccessDenied(reason)) {
      await dismissInaccessibleRecord()
      return
    }
    message.error(readable(reason))
  }
}

function closeDetail() {
  detailRequest++
  detailOpen.value = false
  detail.value = undefined
  lastOpenedRouteRecordId.value = undefined
  syncListRoute()
}

async function handleAiBusinessWritten(value: { moduleCode: string; recordId: number; recordPath?: string }) {
  await loadRecords()
  if (detail.value) await openDetail(detail.value)
  message.success('助手已按确认内容写入业务记录，当前详情与列表已刷新')
}

function clearPrintPreview() {
  if (printPreviewUrl.value) URL.revokeObjectURL(printPreviewUrl.value)
  printPreviewUrl.value = ''
}

function openPrint() {
  if (!detail.value || !printTemplates.value.length) return
  clearPrintPreview()
  printJob.value = undefined
  selectedPrintTemplateId.value = printTemplates.value[0]?.templateId
  printOpen.value = true
}

async function generatePrint() {
  if (!detail.value || !selectedPrintTemplateId.value) return
  printing.value = true
  clearPrintPreview()
  try {
    const template = printTemplates.value.find(item => item.templateId === selectedPrintTemplateId.value)
    printJob.value = await api<PrintJob>(`/api/print/runtime/modules/${activeCode.value}/records/${detail.value.id}/templates/${selectedPrintTemplateId.value}`, {
      method: 'POST', body: JSON.stringify({ templateVersionId: template?.versionId }),
    }, token.value)
    const blob = await authorizedBlob(printJob.value.previewPath, token.value)
    printPreviewUrl.value = URL.createObjectURL(blob)
    message.success(`PDF 已生成，共 ${printJob.value.pageCount} 页`)
  } catch (reason) { message.error(readable(reason)) } finally { printing.value = false }
}

async function downloadPrint() {
  if (!printJob.value) return
  try {
    const blob = await authorizedBlob(printJob.value.downloadPath, token.value)
    saveBlob(blob, printJob.value.outputFileName)
  } catch (reason) { message.error(readable(reason)) }
}

function openOriginalPrint() {
  if (printPreviewUrl.value) window.open(printPreviewUrl.value, '_blank', 'noopener,noreferrer')
}

async function openRouteRecordDetail() {
  const rawId = Array.isArray(route.query.recordId) ? route.query.recordId[0] : route.query.recordId
  const recordId = Number(rawId)
  if (!Number.isInteger(recordId) || recordId <= 0 || !activeCode.value || lastOpenedRouteRecordId.value === recordId) return
  lastOpenedRouteRecordId.value = recordId
  await openDetail({ id: recordId } as RuntimeRecord)
}

async function openTransfer(record: RuntimeRecord) {
  closeOrganizationDialogs()
  transferRecord.value = undefined
  const request = transferOrganizationRequest
  const moduleCode = activeCode.value
  const accessToken = token.value
  try {
    const directory = await recordOrganizationOptions(moduleCode, record.id, 'TRANSFER', accessToken)
    if (request !== transferOrganizationRequest || moduleCode !== activeCode.value || accessToken !== token.value) return
    transferRecord.value = recordWithoutSecrets(record)
    transferOrganizationOptions.value = directory
    Object.assign(transferForm, { ownerMemberId: record.ownerMemberId, departmentId: record.departmentId,
      participantMemberIds: [...record.participantMemberIds], reason: '' })
    transferOpen.value = true
  } catch (reason) {
    if (request === transferOrganizationRequest) {
      clearTransferOrganizationOptions()
      message.error(`转交选项无法加载：${readable(reason)}`)
    }
  }
}

async function previewTransfer() {
  const context = workspaceReadContext()
  let generation = transferOrganizationRequest
  const current = () => workspaceReadCurrent(context) && generation === transferOrganizationRequest
  if (!transferRecord.value || !transferForm.ownerMemberId || !transferForm.reason.trim()) {
    message.warning('请填写新负责人和转交原因')
    return
  }
  transferSaving.value = true
  transferPreview.value = undefined
  try {
    const body = { version: transferRecord.value.version, ownerMemberId: transferForm.ownerMemberId,
      departmentId: transferForm.departmentId, participantMemberIds: transferParticipantIds(), reason: transferForm.reason.trim() }
    const preview = await api<RuntimeRecordTransferPreview>(
      `/api/runtime/modules/${activeCode.value}/records/${transferRecord.value.id}/transfer-preview`,
      { method: 'POST', body: JSON.stringify(body) }, token.value)
    if (!current()) return
    transferPreview.value = preview
    confirmedTransferRequest.value = body
  } catch (reason) {
    if (current()) message.error(readable(reason))
  } finally { if (current()) transferSaving.value = false }
}

async function submitTransfer() {
  const context = workspaceReadContext()
  let generation = transferOrganizationRequest
  const current = () => workspaceReadCurrent(context) && generation === transferOrganizationRequest
  if (transferSaving.value || !transferRecord.value || !transferPreview.value || !confirmedTransferRequest.value) return
  transferSaving.value = true
  try {
    const result = await transferAction<RuntimeRecord>(
      `/api/runtime/modules/${activeCode.value}/records/${transferRecord.value.id}/transfer`,
      'POST', confirmedTransferRequest.value, context.accessToken, current)
    if (!current()) return
    lastAction.value = { moduleCode: activeCode.value, result }
    transferOpen.value = false
    generation = transferOrganizationRequest
    detailOpen.value = false
    message.success(businessActionMessage(result, '负责人和部门已转交'))
    await loadRecords().catch(() => message.warning('操作已提交，请刷新列表查看最新数据'))
    if (!current()) return
    if (result.value) await openDetail(result.value).catch(() => message.warning('请重新打开详情查看转交结果'))
  } catch (reason) {
    if (!current()) return
    if (recordAccessDenied(reason)) {
      await dismissInaccessibleRecord()
      return
    }
    message.error(readable(reason))
    if (reason instanceof ApiError && reason.code === 'RECORD_VERSION_CONFLICT') {
      transferPreview.value = undefined
      await loadRecords().catch(() => undefined)
      if (!current()) return
      const currentRecord = records.value.find(record => record.id === transferRecord.value?.id)
      if (currentRecord) transferRecord.value = recordWithoutSecrets(currentRecord)
    }
  } finally { if (current()) transferSaving.value = false }
}

function transferParticipantIds() {
  return [...new Set(transferForm.participantMemberIds)]
}

function openConversion(record: RuntimeRecord) {
  conversionDialogRequest++
  batchConversionTargets.value = []
  confirmedConversionRequest.value = undefined
  conversionRecord.value = recordWithoutSecrets(record)
  conversionPreview.value = undefined
  conversionTargetCode.value = conversionTargetOptions.value[0]?.value || ''
  conversionOpen.value = true
}

/** 批量只组织同一模块的逐条公共动作；版本来自用户选择的记录，不自动偷换提交数据。 */
function openBatchConversion(targets: RuntimeRecord[]) {
  conversionDialogRequest++
  batchConversionModule.value = activeCode.value
  batchConversionTargets.value = targets.map(record => recordWithoutSecrets(record))
  batchConversionPreviews.value = []
  batchConversionResults.value = []
  batchConversionAction.clear()
  batchConversionRetryPreviewed.value = false
  conversionPreview.value = undefined
  conversionTargetCode.value = conversionTargetOptions.value[0]?.value || ''
  conversionOpen.value = true
}

function resetConversionPreview() {
  conversionDialogRequest++
  confirmedConversionRequest.value = undefined
  conversionPreview.value = undefined
  batchConversionPreviews.value = []
}

async function previewBatchConversion(refreshFailed = false) {
  const context = workspaceReadContext()
  let generation = conversionDialogRequest
  const current = () => workspaceReadCurrent(context) && generation === conversionDialogRequest
  if (conversionSaving.value || !conversionTargetCode.value) return
  conversionSaving.value = true
  batchConversionRetryPreviewed.value = false
  const targets = [...batchConversionTargets.value]
  const moduleCode = batchConversionModule.value
  const targetModuleCode = conversionTargetCode.value
  const previous = new Map(batchConversionPreviews.value.map(item => [item.recordId, item]))
  try {
    for (let index = 0; index < targets.length; index++) {
      let record = targets[index]!
      const failed = batchConversionFailed.value.find(item => item.recordId === record.id)
      if (refreshFailed && !failed) continue
      try {
        // 未知结果保留原请求的来源版本；只有明确拒绝的记录才由用户要求刷新和重新确认。
        if (refreshFailed && failed?.failureKind === 'REJECTED') {
          record = await api<RuntimeRecord>(`/api/runtime/modules/${moduleCode}/records/${record.id}`, {}, token.value)
          if (!current()) return
          batchConversionTargets.value[index] = recordWithoutSecrets(record)
        }
        const preview = await api<RuntimeRecordConversionPreview>(
          `/api/runtime/modules/${moduleCode}/records/${record.id}/conversion-preview`,
          { method: 'POST', body: JSON.stringify({ version: record.version, targetModuleCode }) }, token.value)
        if (!current()) return
        previous.set(record.id, { recordId: record.id, title: record.title, preview })
      } catch (reason) {
        if (!current()) return
        previous.set(record.id, { recordId: record.id, title: record.title, error: readable(reason) })
      }
    }
    if (!current()) return
    batchConversionPreviews.value = batchConversionTargets.value.map(record => previous.get(record.id)!)
    batchConversionRetryPreviewed.value = refreshFailed
  } finally { if (current()) conversionSaving.value = false }
}

async function submitBatchConversion(retryFailed = false) {
  const context = workspaceReadContext()
  let generation = conversionDialogRequest
  const current = () => workspaceReadCurrent(context) && generation === conversionDialogRequest
  if (conversionSaving.value || !batchConversionTargets.value.length) return
  conversionSaving.value = true
  batchConversionRetryPreviewed.value = false
  const targets = [...batchConversionTargets.value]
  const moduleCode = batchConversionModule.value
  const targetModuleCode = conversionTargetCode.value
  try {
    const results = await batchConversionAction.submit(targets, batchConversionResults.value, {
      retryFailed, token: context.accessToken, current, completed: '转化已完成',
      request: record => ({ path: `/api/runtime/modules/${moduleCode}/records/${record.id}/convert`,
        body: { version: record.version, targetModuleCode } }),
    })
    if (!current()) return
    batchConversionResults.value = results
    if (activeCode.value === moduleCode) {
      await loadRecords().catch(() => message.warning('请求已处理，请刷新列表查看数据'))
      if (!current()) return
      selectedRowKeys.value = batchConversionFailed.value.map(item => item.recordId)
    }
    message.info(`${batchConversionResults.value.length - batchConversionFailed.value.length} 条已受理，${batchConversionFailed.value.length} 条失败；具体生效状态见逐条结果`)
  } finally { if (current()) conversionSaving.value = false }
}

async function refreshBatchConversionResults() {
  const context = workspaceReadContext()
  let generation = conversionDialogRequest
  const current = () => workspaceReadCurrent(context) && generation === conversionDialogRequest
  if (conversionSaving.value) return
  conversionSaving.value = true
  try {
    for (const item of [...batchConversionResults.value]) {
      if (!current()) return
      if (!item.result) continue
      try {
        const delivery = await api<ActionDelivery>(`/api/runtime/modules/${batchConversionModule.value}/records/action-results/${item.result.idempotencyRecordId}`, {}, token.value)
        if (!current()) return
        item.result.delivery = delivery
        item.message = businessActionMessage(item.result, '转化已完成')
        if (item.result.delivery?.message) item.message += `；${item.result.delivery.message}`
      } catch (reason) {
        if (current()) message.warning(`${item.title}：${readable(reason)}`)
      }
    }
  } finally { if (current()) conversionSaving.value = false }
}

function batchConversionState(item: RuntimeBatchItem<RuntimeRecordConversionExecution>) {
  if (item.status === 'FAILED') return item.failureKind === 'UNKNOWN_RESULT' ? '响应未确认' : '失败'
  const result = item.result!
  if (result.mode === 'FLOW_ONLY') return '仅发起流程'
  if (result.mode === 'AFTER_APPROVAL') {
    const status = result.delivery?.businessStatus || result.status
    if (status === 'EXECUTED') return '已转化'
    if (['CONFLICT', 'EXECUTION_FAILED'].includes(status)) return '执行未生效'
    if (['REJECTED', 'WITHDRAWN', 'TERMINATED', 'RETURNED'].includes(status)) return '审批已结束'
    return '待审批'
  }
  return '已转化'
}

async function openBatchConversionRecord(item: RuntimeBatchItem<RuntimeRecordConversionExecution>) {
  const context = workspaceReadContext()
  conversionOpen.value = false
  const result = item.result?.value
  try {
    if (!await selectModule(result?.targetModuleCode || batchConversionModule.value)
      || context.accessToken !== token.value || context.identity !== workspaceIdentity()) return
    await openDetail(result?.targetRecord || { id: item.recordId } as RuntimeRecord)
  } catch (reason) { message.error(readable(reason)) }
}

async function previewConversion() {
  const context = workspaceReadContext()
  let generation = conversionDialogRequest
  const current = () => workspaceReadCurrent(context) && generation === conversionDialogRequest
  if (!conversionRecord.value || !conversionTargetCode.value) {
    message.warning('当前转化动作尚未配置目标模块')
    return
  }
  conversionSaving.value = true
  conversionPreview.value = undefined
  try {
    const body = { version: conversionRecord.value.version, targetModuleCode: conversionTargetCode.value }
    const preview = await api<RuntimeRecordConversionPreview>(
      `/api/runtime/modules/${activeCode.value}/records/${conversionRecord.value.id}/conversion-preview`,
      { method: 'POST', body: JSON.stringify(body) }, token.value)
    if (!current()) return
    conversionPreview.value = preview
    confirmedConversionRequest.value = body
  } catch (reason) {
    if (current()) message.error(readable(reason))
  } finally { if (current()) conversionSaving.value = false }
}

async function submitConversion() {
  const context = workspaceReadContext()
  let generation = conversionDialogRequest
  const current = () => workspaceReadCurrent(context) && generation === conversionDialogRequest
  if (conversionSaving.value || !conversionRecord.value || !conversionPreview.value?.executable || !confirmedConversionRequest.value) return
  conversionSaving.value = true
  const submittedModule = activeCode.value
  try {
    const result = await conversionAction<RuntimeRecordConversionExecution>(
      `/api/runtime/modules/${submittedModule}/records/${conversionRecord.value.id}/convert`,
      'POST', confirmedConversionRequest.value, context.accessToken, current)
    if (!current()) return
    lastAction.value = { moduleCode: submittedModule, result }
    conversionOpen.value = false
    generation = conversionDialogRequest
    detailOpen.value = false
    message.success(businessActionMessage(result, '转化已完成'))
    await loadRecords().catch(() => message.warning('转化请求已接收，列表暂未刷新'))
    if (!current()) return
    if (result.value) {
      try {
        if (!await selectModule(result.value.targetModuleCode) || context.accessToken !== token.value || context.identity !== workspaceIdentity()) return
        await openDetail(result.value.targetRecord)
      }
      catch { message.warning('转化已完成，目标详情暂未打开；可从来源记录的转化页查看') }
    }
  } catch (reason) {
    if (!current()) return
    if (reason instanceof ApiError && ['RECORD_VERSION_CONFLICT', 'RECORD_ALREADY_CONVERTED'].includes(reason.code)) {
      conversionPreview.value = undefined
      await loadRecords()
      if (!current()) return
      const currentRecord = records.value.find(record => record.id === conversionRecord.value?.id)
      if (currentRecord) conversionRecord.value = recordWithoutSecrets(currentRecord)
    }
    message.error(readable(reason))
  } finally { if (current()) conversionSaving.value = false }
}

async function openConversionLink(link: RuntimeRecordConversionLink) {
  const context = workspaceReadContext()
  try {
    const linked = await api<RuntimeRecord>(`/api/runtime/modules/${link.moduleCode}/records/${link.recordId}`, {}, token.value)
    if (!workspaceReadCurrent(context) || !await selectModule(link.moduleCode)
      || context.accessToken !== token.value || context.identity !== workspaceIdentity()) return
    await openDetail(linked)
  } catch (reason) { message.error(readable(reason)) }
}

function openConversionUnlink(link: RuntimeRecordConversionLink) {
  if (!detail.value || !link.canUnlink) return
  conversionUnlinkLink.value = { ...link }
  conversionUnlinkSource.value = { id: detail.value.id, moduleCode: activeCode.value, version: detail.value.version }
  conversionUnlinkReason.value = ''
  conversionUnlinkOpen.value = true
}

async function submitConversionUnlink() {
  const context = workspaceReadContext()
  let generation = conversionDialogRequest
  const current = () => workspaceReadCurrent(context) && generation === conversionDialogRequest
  const source = conversionUnlinkSource.value, link = conversionUnlinkLink.value
  if (conversionUnlinkSaving.value || !source || !link) return
  if (!conversionUnlinkReason.value.trim()) return message.warning('请填写解除原因')
  conversionUnlinkSaving.value = true
  try {
    const result = await conversionUnlinkAction<{ conversionId: number; conversionVersion: number; relationStatus: string }>(
      `/api/runtime/modules/${source.moduleCode}/records/${source.id}/conversions/unlink`, 'POST',
      { conversionId: link.conversionId, conversionVersion: link.conversionVersion, version: source.version, reason: conversionUnlinkReason.value.trim() }, context.accessToken, current)
    if (!current()) return
    lastAction.value = { moduleCode: source.moduleCode, result }
    conversionUnlinkOpen.value = false
    generation = conversionDialogRequest
    message.success(businessActionMessage(result, '转化关系已解除，目标数据和历史保留'))
    if (activeCode.value === source.moduleCode) await openDetail({ id: source.id } as RuntimeRecord)
  } catch (reason) {
    if (!current()) return
    message.error(readable(reason))
  } finally { if (current()) conversionUnlinkSaving.value = false }
}

function loadReferenceOptions() {
  const request = ++referenceRequest
  const moduleCode = activeCode.value
  const accessToken = token.value
  Object.keys(referenceOptions).forEach(code => delete referenceOptions[code])
  const referenceFields = fields.value.filter((field) => field.fieldType === 'REFERENCE' && field.referenceModuleId)
  if (!referenceFields.length) { referenceLoading.value = false; return }
  referenceLoading.value = true
  void Promise.all(referenceFields.map(async (field) => {
    const module = modules.value.find((item) => item.moduleId === field.referenceModuleId)
    if (!module) return
    const params = new URLSearchParams({ lifecycleState: 'ACTIVE', tenantScope: 'ALL', search: '', filters: '[]', sortField: 'updatedAt', sortDirection: 'DESC', page: '1', pageSize: '50' })
    const result = await api<RuntimeRecordList>(`/api/runtime/modules/${module.moduleCode}/records?${params}`, {}, accessToken)
    if (request !== referenceRequest || moduleCode !== activeCode.value || accessToken !== token.value) return
    referenceOptions[field.code] = result.records.map((record) => ({
      value: record.id,
      label: record.title,
      description: userFacingRecordNumber(record.recordNumber),
    }))
  })).catch(() => { if (request === referenceRequest) message.warning('部分关联记录暂时无法加载，可稍后重试。') })
    .finally(() => { if (request === referenceRequest) referenceLoading.value = false })
}

/** 来源字段选项只暂存在本次编辑请求中，全部授权成功后才填入独立表单缓存。 */
async function loadEditFormOptions(writableFields: RuntimeField[], recordId: number, request: number, moduleCode: string, accessToken: string) {
  const organizations: Record<string, SystemPeopleDirectory> = {}
  const dictionaries: Record<string, Array<{ value: string; label: string }>> = {}
  const cascades: Record<string, CascadeOption[]> = {}
  const references: Record<string, Array<{ value: number; label: string; description: string }>> = {}
  const isCurrent = () => request === formOrganizationRequest && moduleCode === activeCode.value && accessToken === token.value
  await Promise.all(writableFields.map(async field => {
    if (!isCurrent()) return
    const endpoint = `/api/runtime/modules/${moduleCode}/records/${recordId}/fields/${encodeURIComponent(field.code)}`
    if (organizationField(field)) {
      organizations[field.code] = await recordOrganizationOptions(moduleCode, recordId, 'UPDATE', accessToken, field.code)
    } else if (field.fieldType === 'CASCADE' && field.dictionaryId) {
      const maximumDepth = Math.max(1, fieldConfig(field).maxDepth || 20)
      const loadLevel = async (parentId: number | undefined, depth: number): Promise<CascadeOption[]> => {
        if (!isCurrent()) return []
        const query = parentId === undefined ? '' : `?parentId=${parentId}`
        const dictionary = await api<RuntimeDictionary>(`${endpoint}/dictionary${query}`, {}, accessToken)
        if (!isCurrent()) return []
        return Promise.all(dictionary.items.map(async item => {
          const children = depth < maximumDepth ? await loadLevel(item.id, depth + 1) : []
          return { value: item.id, label: item.label, ...(children.length ? { children } : {}) }
        }))
      }
      cascades[field.code] = await loadLevel(undefined, 1)
    } else if (field.dictionaryId && ['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS'].includes(field.fieldType)) {
      const dictionary = await api<RuntimeDictionary>(`${endpoint}/dictionary`, {}, accessToken)
      dictionaries[field.code] = dictionary.items.map(item => ({ value: item.code, label: item.label }))
    } else if (field.fieldType === 'REFERENCE' && field.referenceModuleId) {
      references[field.code] = await api<Array<{ value: number; label: string; description: string }>>(`${endpoint}/reference-options`, {}, accessToken)
    }
  }))
  return { organizations, dictionaries, cascades, references }
}

async function createReferenceChoices(field: RuntimeField, search: string, accessToken: string, tenantId: number | null | undefined) {
  const module = modules.value.find(item => item.moduleId === field.referenceModuleId)
  if (!module) return []
  const query = new URLSearchParams({ lifecycleState: 'ACTIVE', tenantScope: 'OWN', search, filters: '[]', sortField: 'updatedAt', sortDirection: 'DESC', page: '1', pageSize: '50' })
  const result = await api<RuntimeRecordList>(`/api/runtime/modules/${module.moduleCode}/records?${query}`, {}, accessToken)
  return result.records.filter(record => record.ownedByCurrentTenant && record.dataTenantId === tenantId)
    .map(record => ({ value: record.id, label: record.title, description: userFacingRecordNumber(record.recordNumber) }))
}

function searchFormReferenceOptions(field: RuntimeField, search: string) {
  if (!formOpen.value) return
  const priorTimer = formReferenceSearchTimers.get(field.code)
  if (priorTimer) clearTimeout(priorTimer)
  const fieldRequest = (formReferenceSearchRequest.get(field.code) || 0) + 1
  formReferenceSearchRequest.set(field.code, fieldRequest)
  const request = formOrganizationRequest
  const moduleCode = activeCode.value
  const accessToken = token.value
  const recordId = editing.value?.id
  const tenantId = systemContext.value?.tenantId
  const isCurrent = () => request === formOrganizationRequest && fieldRequest === formReferenceSearchRequest.get(field.code)
    && moduleCode === activeCode.value && accessToken === token.value && formOpen.value
  formReferenceLoading[field.code] = true
  formReferenceOptions[field.code] = []
  formReferenceSearchTimers.set(field.code, setTimeout(() => {
    formReferenceSearchTimers.delete(field.code)
    if (!isCurrent()) return
    void (async () => {
      try {
        const query = new URLSearchParams()
        if (search.trim()) query.set('search', search.trim())
        const queryString = query.toString()
        const choices = recordId
          ? await api<Array<{ value: number; label: string; description: string }>>(`/api/runtime/modules/${moduleCode}/records/${recordId}/fields/${encodeURIComponent(field.code)}/reference-options${queryString ? `?${queryString}` : ''}`, {}, accessToken)
          : await createReferenceChoices(field, search.trim(), accessToken, tenantId)
        if (isCurrent()) formReferenceOptions[field.code] = choices
      } catch (reason) {
        if (isCurrent()) {
          if (recordId && recordAccessDenied(reason)) {
            formOpen.value = false
            clearFormOrganizationOptions()
          }
          message.warning(`关联记录搜索暂时无法加载：${readable(reason)}`)
        }
      } finally {
        if (isCurrent()) formReferenceLoading[field.code] = false
      }
    })()
  }, 500))
}

async function loadCreateReferenceOptions() {
  const request = formOrganizationRequest
  const moduleCode = activeCode.value
  const accessToken = token.value
  const currentConfiguration = formConfiguration.value
  const tenantId = systemContext.value?.tenantId
  const referenceFields = formFields.value.filter(field => field.fieldType === 'REFERENCE' && field.referenceModuleId)
  const isCurrent = () => request === formOrganizationRequest && moduleCode === activeCode.value && accessToken === token.value
    && formConfiguration.value === currentConfiguration && !editing.value
  formCatalogLoading.value = true
  try {
    const loaded = await Promise.all(referenceFields.map(async field => {
      const fieldRequest = formReferenceSearchRequest.get(field.code) || 0
      return [field.code, await createReferenceChoices(field, '', accessToken, tenantId), fieldRequest] as const
    }))
    if (isCurrent()) loaded.forEach(([code, choices, fieldRequest]) => {
      if (fieldRequest === (formReferenceSearchRequest.get(code) || 0)) formReferenceOptions[code] = choices
    })
  } catch (reason) {
    if (isCurrent()) message.warning(`关联记录暂时无法加载：${readable(reason)}`)
  } finally {
    if (isCurrent()) formCatalogLoading.value = false
  }
}

function openCreate() {
  if (sharedRecordsMode.value || !hasAction('CREATE')) { message.warning('当前入口不能新建记录'); return }
  closeOrganizationDialogs()
  editing.value = undefined
  formConfiguration.value = configuration.value
  Object.assign(formDictionaryOptions, dictionaryOptions)
  Object.assign(formCascadeOptions, cascadeOptions)
  clearFormErrors()
  Object.assign(form, {
    title: '', recordNumber: '', status: 'ACTIVE', ownerMemberId: systemContext.value?.memberId || undefined,
    departmentId: undefined, participantText: '', fields: {},
  })
  formFields.value.forEach((field) => { form.fields[field.code] = initialFieldValue(field) })
  formSnapshot.value = serializeForm()
  restoreFormDraft()
  formOpen.value = true
  void loadCreateReferenceOptions()
}

async function openEdit(record: RuntimeRecord) {
  closeOrganizationDialogs()
  const request = formOrganizationRequest
  const moduleCode = activeCode.value
  const accessToken = token.value
  formCatalogLoading.value = true
  try {
    const context = await api<RuntimeRecordEditContext>(`/api/runtime/modules/${moduleCode}/records/${record.id}/edit-context`, {}, accessToken)
    if (request !== formOrganizationRequest || moduleCode !== activeCode.value || accessToken !== token.value) return
    const latest = context.record
    const writableFields = writableFormFields(context.configuration)
    const catalogs = await loadEditFormOptions(writableFields, latest.id, request, moduleCode, accessToken)
    if (request !== formOrganizationRequest || moduleCode !== activeCode.value || accessToken !== token.value) return
    formConfiguration.value = context.configuration
    Object.assign(formOrganizationOptions, catalogs.organizations)
    Object.assign(formDictionaryOptions, catalogs.dictionaries)
    Object.assign(formCascadeOptions, catalogs.cascades)
    Object.assign(formReferenceOptions, catalogs.references)
    formCatalogLoading.value = false
    editing.value = recordWithoutSecrets(latest, writableFields)
    clearFormErrors()
    Object.assign(form, {
      title: latest.title,
      recordNumber: latest.recordNumber || '',
      status: latest.status,
      ownerMemberId: latest.ownerMemberId,
      departmentId: latest.departmentId,
      participantText: latest.participantMemberIds.join(','),
      fields: Object.fromEntries(writableFields.filter(field => field.fieldType !== 'SECRET' && Object.prototype.hasOwnProperty.call(latest.fields, field.code)).map(field => {
        const value = latest.fields[field.code]
        return [field.code, field.fieldType === 'CASCADE' ? cascadeEditValue(field, value, formCascadeOptions) : value]
      })),
    })
    formSnapshot.value = serializeForm()
    restoreFormDraft()
    formOpen.value = true
    detailOpen.value = false
    syncListRoute()
  } catch (reason) {
    if (request === formOrganizationRequest) {
      clearFormOrganizationOptions()
      message.error(`编辑选项无法加载：${readable(reason)}`)
    }
  }
}

async function changeLifecycleState() {
  page.value = 1
  detailOpen.value = false
  await loadRecords()
}

async function openLifecycle(record: RuntimeRecord, action: 'ARCHIVE' | 'DELETE' | 'RESTORE') {
  const context = workspaceReadContext()
  const generation = ++lifecycleDialogRequest
  const current = () => workspaceReadCurrent(context) && generation === lifecycleDialogRequest
  try {
    batchDeleteTargets.value = []
    lifecycleAction.value = action
    lifecycleRecord.value = recordWithoutSecrets(record)
    lifecycleReason.value = ''
    const impact = await api<RuntimeRecordLifecycleImpact>(
      `/api/runtime/modules/${activeCode.value}/records/${record.id}/lifecycle-impact?action=${action}`, {}, token.value)
    if (!current()) return
    lifecycleImpact.value = impact
    lifecycleOpen.value = true
  } catch (reason) {
    if (current()) message.error(readable(reason))
  }
}

async function submitBatchDelete(retryFailed = false) {
  const context = workspaceReadContext()
  let generation = lifecycleDialogRequest
  const current = () => workspaceReadCurrent(context) && generation === lifecycleDialogRequest
  if (lifecycleSaving.value || !batchDeleteTargets.value.length) return
  if (!lifecycleReason.value.trim()) { message.warning('请填写操作原因'); return }
  lifecycleSaving.value = true
  try {
    const results = await batchDeleteAction.submit(batchDeleteTargets.value, batchDeleteResults.value, {
      retryFailed, token: context.accessToken, current, completed: '记录已移入回收站',
      request: record => ({ path: `/api/runtime/modules/${activeCode.value}/records/${record.id}/delete`,
        body: { version: record.version, reason: lifecycleReason.value.trim() } }),
    })
    if (!current()) return
    batchDeleteResults.value = results
    await loadRecords().catch(() => message.warning('请刷新列表查看最新数据'))
    if (!current()) return
    const currentRecords = new Map(records.value.map(record => [record.id, record]))
    batchDeleteTargets.value = batchDeleteTargets.value.map(record => recordWithoutSecrets(currentRecords.get(record.id) || record))
    selectedRowKeys.value = batchDeleteResults.value.filter(item => item.status === 'FAILED').map(item => item.recordId)
  } finally { if (current()) lifecycleSaving.value = false }
}

async function submitLifecycle() {
  const context = workspaceReadContext()
  let generation = lifecycleDialogRequest
  const current = () => workspaceReadCurrent(context) && generation === lifecycleDialogRequest
  if (lifecycleSaving.value) return
  if (!lifecycleRecord.value || !lifecycleReason.value.trim()) {
    message.warning('请填写操作原因')
    return
  }
  lifecycleSaving.value = true
  try {
    const action = lifecycleAction.value.toLowerCase()
    const submittedModule = activeCode.value
    const path = `/api/runtime/modules/${submittedModule}/records/${lifecycleRecord.value.id}/${action}`
    const body = { reason: lifecycleReason.value.trim(), version: lifecycleRecord.value.version }
    const result = action === 'delete' ? await deleteAction<RuntimeRecord>(path, 'POST', body, context.accessToken, current) : undefined
    if (!current()) return
    if (result) lastAction.value = { moduleCode: submittedModule, result }
    if (!result) await api<RuntimeRecord>(path, { method: 'POST', body: JSON.stringify(body) }, token.value)
    if (!current()) return
    lifecycleOpen.value = false
    generation = lifecycleDialogRequest
    detailOpen.value = false
    const completed = lifecycleAction.value === 'ARCHIVE' ? '记录已归档' : lifecycleAction.value === 'DELETE' ? '记录已移入回收站' : '记录已恢复'
    message.success(result ? businessActionMessage(result, completed) : completed)
    await loadRecords().catch(() => message.warning('操作已成功，请刷新列表查看最新结果'))
  } catch (reason) {
    if (!current()) return
    message.error(readable(reason))
  } finally {
    if (current()) lifecycleSaving.value = false
  }
}

function options(field: RuntimeField, dictionaries = dictionaryOptions) {
  if (field.dictionaryId) return dictionaries[field.code] || []
  try {
    const config = JSON.parse(field.configJson) as { options?: Array<{ value?: string; code?: string; label?: string; status?: string; disabled?: boolean }> }
    return (config.options || []).filter((option) => option.status !== 'DISABLED' && !option.disabled)
      .flatMap((option) => { const value = option.value || option.code; return value ? [{ value, label: option.label || value }] : [] })
  } catch { return [] }
}

function formOptions(field: RuntimeField) {
  return options(field, formDictionaryOptions)
}

function participantIds() {
  return [...new Set(form.participantText.split(',').map((item) => Number(item.trim())).filter((item) => Number.isInteger(item) && item > 0))]
}

function normalizedFields() {
  // 旧草稿中的隐藏、只读字段不回传；仅在可写字段内用 null 表达清空。
  const writableCodes = new Set(formFields.value.map((field) => field.code))
  const secretCodes = new Set(formFields.value.filter(field => field.fieldType === 'SECRET').map(field => field.code))
  return Object.fromEntries(Object.entries(form.fields)
    .filter(([code]) => writableCodes.has(code))
    .filter(([code, value]) => !secretCodes.has(code) || (value !== undefined && value !== ''))
    .map(([code, value]) => [code, value === undefined || value === '' ? null : value]))
}

function numericFieldValue(fieldCode: string) {
  const value = form.fields[fieldCode]
  return typeof value === 'number' ? value : undefined
}

function attachmentFieldValue(fieldCode: string) {
  const value = form.fields[fieldCode]
  return typeof value === 'number' || Array.isArray(value) ? value as number | number[] : undefined
}

async function saveRecord() {
  if (saving.value) return
  const context = workspaceReadContext()
  let generation = formOrganizationRequest
  const current = () => workspaceReadCurrent(context) && generation === formOrganizationRequest
  const titleField = recordTitleField.value
  const title = titleField ? String(form.fields[titleField.code] ?? '').trim() : form.title.trim()
  if (!title) {
    formError.value = titleField ? `${titleField.name}不能为空` : '标题不能为空'
    if (titleField) fieldErrors[titleField.code] = '请填写用于标识记录的内容'
    await nextTick()
    const target = titleField
      ? document.querySelector<HTMLElement>(`[data-runtime-field="${titleField.code}"] input, [data-runtime-field="${titleField.code}"] textarea`)
      : document.querySelector<HTMLElement>('#runtime-record-title')
    target?.focus()
    return
  }
  saving.value = true
  clearFormErrors()
  try {
    const body = {
      title,
      recordNumber: form.recordNumber || null,
      status: form.status,
      ownerMemberId: form.ownerMemberId || null,
      departmentId: form.departmentId || null,
      participantMemberIds: participantIds(),
      fields: normalizedFields(),
      ...(editing.value ? { version: editing.value.version } : {}),
    }
    const submittedModule = context.moduleCode
    const path = editing.value
      ? `/api/runtime/modules/${activeCode.value}/records/${editing.value.id}`
      : `/api/runtime/modules/${activeCode.value}/records`
    const wasEditing = Boolean(editing.value)
    const draftKey = formDraftKey()
    const result = await saveAction<RuntimeRecord>(path, wasEditing ? 'PUT' : 'POST', body, context.accessToken, current)
    if (!current()) return
    lastAction.value = { moduleCode: submittedModule, result }
    localStorage.removeItem(draftKey)
    formSnapshot.value = serializeForm()
    formOpen.value = false
    generation = formOrganizationRequest
    message.success(businessActionMessage(result, wasEditing ? '业务数据已更新' : '业务数据已创建'))
    page.value = 1
    await loadRecords().catch(() => message.warning('操作已成功，请刷新列表查看最新结果'))
    if (!current()) return
    if (wasEditing && result.value && canRecordAction('DETAIL', result.value)) {
      await openDetail(result.value).catch(() => message.warning('操作已成功，请重新打开详情'))
    }
  } catch (reason) {
    if (!current()) return
    if (editing.value && recordAccessDenied(reason)) {
      await dismissInaccessibleRecord()
      return
    }
    applyFormError(reason)
    message.error(formError.value)
  } finally {
    if (current()) saving.value = false
  }
}

function clearFormErrors() {
  formError.value = ''
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
}

function applyFormError(reason: unknown) {
  formError.value = readable(reason)
  if (!(reason instanceof ApiError) || !['FIELD_VALIDATION_FAILED', 'UNIQUE_RULE_VIOLATION'].includes(reason.code)) return
  const summaries: string[] = []
  reason.message.split('；').forEach((item) => {
    const separator = item.indexOf(':')
    if (separator <= 0) return
    const code = item.slice(0, separator).trim()
    const message = item.slice(separator + 1).trim()
    if (code) {
      fieldErrors[code] = message
      summaries.push(`${formFields.value.find((field) => field.code === code)?.name || '字段'}：${message}`)
    }
  })
  if (summaries.length) formError.value = `请检查填写内容：${summaries.join('；')}`
  const first = Object.keys(fieldErrors)[0]
  if (first) void nextTick(() => {
    const container = document.querySelector<HTMLElement>(`[data-runtime-field="${first}"]`)
    container?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    container?.querySelector<HTMLElement>('input,textarea,[tabindex]')?.focus()
  })
}

function serializeForm() {
  // 内存比较快照与浏览器草稿都只保留当前可写的非秘密字段。
  const draftCodes = new Set(formFields.value.filter(field => field.fieldType !== 'SECRET').map(field => field.code))
  return JSON.stringify({
    title: form.title, recordNumber: form.recordNumber, status: form.status,
    ownerMemberId: form.ownerMemberId, departmentId: form.departmentId,
    participantText: form.participantText,
    fields: Object.fromEntries(Object.entries(form.fields)
      .filter(([code]) => draftCodes.has(code))),
  })
}

function formDraftKey() {
  const context = systemContext.value
  return `unexamine.form-draft.${context?.accountId || 0}.${context?.systemId || 0}.${context?.tenantId || 0}.${activeCode.value}.${editing.value?.id || 'new'}`
}

function restoreFormDraft() {
  formDraftRestored.value = false
  try {
    const stored = localStorage.getItem(formDraftKey())
    if (!stored) return
    const draft = JSON.parse(stored) as { value?: typeof form; updatedAt?: string }
    if (!draft.value || typeof draft.value !== 'object' || Array.isArray(draft.value)) return
    const draftCodes = new Set(formFields.value.filter(field => field.fieldType !== 'SECRET').map(field => field.code))
    const safeFields = Object.fromEntries(Object.entries(draft.value.fields || {}).filter(([code]) => draftCodes.has(code)))
    const metadata = Object.fromEntries(['title', 'recordNumber', 'status', 'ownerMemberId', 'departmentId', 'participantText']
      .filter(key => Object.prototype.hasOwnProperty.call(draft.value, key))
      .map(key => [key, draft.value![key as keyof typeof form]]))
    Object.assign(form, metadata, { fields: { ...form.fields, ...safeFields } })
    localStorage.setItem(formDraftKey(), JSON.stringify({ value: JSON.parse(serializeForm()), updatedAt: draft.updatedAt }))
    formDraftRestored.value = true
    message.info(`已恢复${draft.updatedAt ? ` ${draft.updatedAt} ` : ''}自动保存的表单草稿`)
  } catch { localStorage.removeItem(formDraftKey()) }
}

function requestCloseForm() {
  if (!formDirty.value) {
    formOpen.value = false
    return
  }
  Modal.confirm({
    title: '当前表单有未提交的修改',
    content: formFields.value.some(field => field.fieldType === 'SECRET')
      ? '其他内容已保存为草稿；密码和密钥不会保存在浏览器，重新打开需要重新填写。确认离开？'
      : '草稿已经自动保存在当前浏览器。确认离开后可以再次进入此记录继续填写。',
    okText: '保留草稿并离开', cancelText: '继续编辑',
    onOk: () => { formOpen.value = false },
  })
}

function beforeWindowUnload(event: BeforeUnloadEvent) {
  if (!formDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

function displayValue(field: RuntimeField, value: unknown, record?: RuntimeRecord, usePresentation = true) {
  if (usePresentation) {
    if (!recordFieldVisible(record, field.code)) return '—'
    const presentation = record?.fieldPresentation?.[field.code]
    // 空字符串也是服务端最终展示结果，不再由当前租户目录或原值补回。
    if (typeof presentation?.displayValue === 'string') return presentation.displayValue
    field = recordDisplayField(field, record)
  }
  if (value === undefined || value === null || value === '') return '—'
  if (value === '***') return '••••（已脱敏）'
  if (value === '[已散列]') return '不可回显（已散列）'
  if (field.fieldType === 'SECRET') return '••••（已脱敏）'
  if (field.fieldType === 'RICH_TEXT') return richPlainText(String(value)) || '—'
  if (field.fieldType === 'JSON') return structuredSummary(value)
  if (field.fieldType === 'BOOLEAN') return value ? '是' : '否'
  if (['SYSTEM_CREATED_AT', 'SYSTEM_UPDATED_AT'].includes(field.fieldType)) return productDateTime(String(value))
  if (['SYSTEM_CREATED_BY', 'SYSTEM_UPDATED_BY'].includes(field.fieldType)) {
    return organizationMemberName(record, value)
  }
  if (['SINGLE_SELECT', 'STATUS'].includes(field.fieldType)) {
    return record?.shared ? String(value) : options(field).find((item) => item.value === value)?.label || String(value)
  }
  if (field.fieldType === 'CASCADE') {
    if (record?.shared) return '层级名称暂不可用'
    const selected = Array.isArray(value) ? value.map(Number)
      : typeof value === 'number' ? (cascadePath(cascadeOptions[field.code] || [], value) || [value]) : []
    return cascadeLabels(cascadeOptions[field.code] || [], selected).join(' / ') || String(value)
  }
  if (field.fieldType === 'REFERENCE') {
    if (record?.shared) return '关联记录名称暂不可用'
    return referenceOptions[field.code]?.find((item) => item.value === Number(value))?.label || '关联记录'
  }
  if (field.fieldType === 'MEMBER') {
    return organizationMemberName(record, value)
  }
  if (field.fieldType === 'DEPARTMENT') {
    return organizationDepartmentName(record, value)
  }
  if (attachmentFieldTypes.has(field.fieldType)) {
    return Array.isArray(value) ? `已上传 ${value.length} 个文件` : '已上传文件'
  }
  return String(value)
}

function timelineValue(value: unknown) {
  if (value === '***') return '••••（已脱敏）'
  if (value === '[已散列]') return '不可回显（已散列）'
  if (value === undefined || value === null || value === '') return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function timelineFieldValue(fieldCode: string, value: unknown) {
  const field = fields.value.find((item) => item.code === fieldCode)
  if (field?.fieldType === 'SECRET' || detail.value?.fieldPresentation?.[fieldCode]?.fieldType === 'SECRET') return '••••（已脱敏）'
  // 当前记录的展示描述不代表历史 before/after 的字典或关联名称。
  return field ? displayValue(field, value, detail.value, false) : timelineValue(value)
}

async function copyFieldValue(field: Pick<RuntimeField, 'name' | 'code' | 'fieldType'>, value: unknown) {
  if (field.fieldType === 'SECRET') value = detail.value?.fieldPresentation?.[field.code]?.displayValue ?? '***'
  if (value === undefined || value === null || value === '' || value === '***' || value === '[已散列]' || value === '••••（已脱敏）') {
    message.warning('当前字段没有可复制的明文值')
    return
  }
  try {
    await navigator.clipboard.writeText(String(value))
    message.success(`${field.name}已复制`)
  } catch { message.error('浏览器未允许写入剪贴板') }
}

function copyReadFieldValue(field: RuntimeReadField, record: RuntimeRecord) {
  const value = record.fields[field.code]
  // 复制同样使用可信展示文本；脱敏原值不能经剪贴板重新暴露。
  return copyFieldValue(field, value === '***' || value === '[已散列]' ? value : readFieldValue(field, value, record))
}

function actionVisibleForRecord(record: RuntimeRecord) {
  if (!recordAllowsAction(record, 'UPDATE')) return false
  const scope = systemContext.value?.dataScopes?.[`MODULE:${activeCode.value}:UPDATE`]
  if (!scope || scope.terms.some((term) => term.type === 'ALL')) return true
  return scope.terms.some((term) => term.type === 'SELF' && record.ownerMemberId === systemContext.value?.memberId)
}

function readable(reason: unknown) {
  if (reason instanceof ApiError) return reason.traceId ? `${reason.message}（追踪号：${reason.traceId}）` : reason.message
  return '请求失败，请稍后重试'
}

async function executeCommand(command: CommandCenterItem) {
  if (!command.moduleCode) return
  if (!modules.value.length) await loadModules()
  if (activeCode.value !== command.moduleCode && !await selectModule(command.moduleCode)) return
  if (command.target === 'RUNTIME_CREATE') {
    if (!hasAction('CREATE')) {
      message.warning('快捷创建权限已变化，请刷新命令中心')
      return
    }
    openCreate()
    return
  }
  if (command.target === 'RUNTIME_RECORD' && command.recordId) {
    const row = records.value.find((record) => record.id === command.recordId)
      || ({ id: command.recordId } as RuntimeRecord)
    await openDetail(row)
  }
}

watch(form, () => {
  if (!formOpen.value || !formDirty.value) return
  localStorage.setItem(formDraftKey(), JSON.stringify({ value: JSON.parse(serializeForm()), updatedAt: new Date().toLocaleString() }))
}, { deep: true })

watch(formOpen, open => { if (!open) clearFormOrganizationOptions() }, { flush: 'sync' })
watch(manualActionOpen, open => { if (!open) clearManualOrganizationOptions() }, { flush: 'sync' })
watch(transferOpen, open => { if (!open) clearTransferOrganizationOptions() }, { flush: 'sync' })
watch(conversionOpen, open => {
  if (open) return
  conversionDialogRequest++
  conversionRecord.value = undefined
  conversionPreview.value = undefined
  confirmedConversionRequest.value = undefined
  batchConversionTargets.value = []
  batchConversionPreviews.value = []
  batchConversionResults.value = []
  batchConversionAction.clear()
  conversionAction.clear()
  conversionSaving.value = false
}, { flush: 'sync' })
watch(conversionUnlinkOpen, open => {
  if (open) return
  conversionDialogRequest++
  conversionUnlinkSource.value = undefined
  conversionUnlinkLink.value = undefined
  conversionUnlinkAction.clear()
  conversionUnlinkSaving.value = false
}, { flush: 'sync' })
watch(lifecycleOpen, open => {
  if (open) return
  lifecycleDialogRequest++
  lifecycleRecord.value = undefined
  batchDeleteTargets.value = []
  batchDeleteResults.value = []
  batchDeleteAction.clear()
  deleteAction.clear()
  lifecycleSaving.value = false
}, { flush: 'sync' })
watch(() => [systemContext.value?.systemId, systemContext.value?.tenantId, token.value, workspaceIdentity()], () => {
  moduleDirectoryRequest++
  modules.value = []
  clearModuleWorkspace()
  loading.value = false
  peopleDirectory.value = { people: [], departments: [], permissionVersion: 0 }
  if (token.value) { void loadPeopleDirectory(); void loadModules() }
}, { flush: 'sync' })
watch(canManageRangeShares, allowed => { if (!allowed) rangeSharingOpen.value = false }, { flush: 'sync' })

watch(() => [route.query.module, route.query.recordId], async () => {
  if (routeInitializing) return
  const code = String(routeValue('module') || '')
  if (code && code !== activeCode.value && modules.value.some(module => module.moduleCode === code)) {
    routeInitializing = true
    try { await selectModule(code, true) } finally { routeInitializing = false }
  }
  await openRouteRecordDetail()
})

onMounted(() => {
  window.addEventListener('beforeunload', beforeWindowUnload)
  void (async () => {
    try { await Promise.all([loadModules(), loadPeopleDirectory()]) } finally { routeInitializing = false }
    await openRouteRecordDetail()
    syncListRoute()
  })()
})
onBeforeUnmount(() => {
  moduleDirectoryRequest++
  moduleSelectionRequest++
  savedViewsRequest++
  configurationRequest++
  recordListRequest++
  queryFieldsRequest++
  closeOrganizationDialogs()
  conversionAction.clear()
  conversionUnlinkAction.clear()
  deleteAction.clear()
  batchConversionAction.clear()
  batchDeleteAction.clear()
  conversionRecord.value = undefined
  conversionPreview.value = undefined
  batchConversionTargets.value = []
  batchConversionPreviews.value = []
  batchConversionResults.value = []
  batchDeleteTargets.value = []
  batchDeleteResults.value = []
  window.removeEventListener('beforeunload', beforeWindowUnload)
  clearPrintPreview()
})
onBeforeRouteLeave(() => !formDirty.value || window.confirm(formFields.value.some(field => field.fieldType === 'SECRET')
  ? '当前表单有未提交修改，其他内容已保存草稿，密码和密钥不会保留。确认离开？'
  : '当前表单有未提交修改，草稿已自动保存。确认离开？'))
defineExpose({ refresh: loadModules, executeCommand })
</script>

<template>
  <ProductPage class="runtime-page" :data-refresh-key="props.refreshKey">
    <ProductPageHeader kicker="业务工作区" :title="activeModule?.moduleName || '业务模块'" description="查看、筛选并维护当前模块中的业务记录。">
      <template #actions>
        <RuntimeActionGroup :actions="actions" location="MODULE_ENTRY" :can="canPageAction" :disabled-reason="pageActionReason" :module-name="activeModule?.moduleName" @run="runConfiguredAction" />
        <a-button v-if="canManageRangeShares" @click="rangeSharingOpen = true">范围共享</a-button>
        <a-dropdown>
          <a-button><MoreOutlined />更多</a-button>
          <template #overlay><a-menu @click="runPageUtility(String($event.key))">
            <a-menu-item key="refresh"><ReloadOutlined />刷新数据</a-menu-item>
            <a-menu-item v-if="canUseAi && hasAction('CREATE') && lifecycleState === 'ACTIVE'" key="ai"><RobotOutlined />使用智能助手创建</a-menu-item>
          </a-menu></template>
        </a-dropdown>
      </template>

    </ProductPageHeader>
    <BusinessActionReceipt :module-code="activeCode" :result="lastAction?.moduleCode === activeCode ? lastAction.result : undefined"
      @executed="loadRecords" />
    <a-alert v-if="dashboardReturn" type="info" show-icon class="section-alert"
      message="当前列表来自经营看板下钻" :description="listFilters.length ? `已应用 ${listFilters.length} 个看板筛选条件。` : '看板筛选字段在当前权限范围内不可用，已显示普通列表。'">
      <template #action><a-button type="link" @click="returnToDashboard">返回经营看板</a-button></template>
    </a-alert>
    <nav v-if="groupedModules.length > 1" class="runtime-group-tabs" aria-label="业务模块组">
      <button v-for="group in groupedModules" :key="group.id" type="button" :class="{ active: activeGroup?.id === group.id }" @click="selectGroup(group.id)">
        <strong>{{ group.name }}</strong><small>{{ group.modules.length }} 个模块</small>
      </button>
    </nav>
    <ProductListWorkspace :loading="loading" :has-content="Boolean(activeCode && records.length)" :empty="!loading && !records.length" :error="error"
      :empty-title="activeCode ? '当前条件下没有记录' : '当前没有可用的业务模块'"
      :empty-description="activeCode ? (sharedRecordsMode ? '可以调整搜索或筛选条件；共享记录仅提供当前已授权的操作。' : '可以调整搜索或筛选条件，也可以新建第一条记录。') : '系统管理员发布并授权模块后，业务入口会显示在这里。'">
      <template #navigation>
        <div class="panel-title"><strong>{{ activeGroup?.name || '业务模块' }}</strong><span>{{ activeGroup?.modules.length || 0 }}</span></div>
        <section v-if="activeGroup" class="runtime-group">
          <button v-for="module in activeGroup.modules" :key="module.moduleId" type="button"
            :class="['module-tree-item', { active: activeCode === module.moduleCode }]" @click="selectModule(module.moduleCode)">
            <span>{{ (module.menuIcon || module.menuName || module.moduleName).slice(0, 1).toUpperCase() }}</span><strong>{{ module.menuName || module.moduleName }}</strong>
          </button></section>
        <a-empty v-if="!modules.length && !loading" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="没有可见的已发布模块" />
      </template>
      <template v-if="activeCode" #toolbar>
        <ProductFilterBar label="业务记录搜索与筛选">
            <a-input-search v-model:value="searchText" allow-clear :placeholder="searchPlaceholder" class="record-search" @search="applyListQuery"><template #prefix><SearchOutlined /></template></a-input-search>
            <a-radio-group v-model:value="lifecycleState" button-style="solid" size="small" @change="changeLifecycleState">
              <a-radio-button value="ACTIVE">正常</a-radio-button><a-radio-button value="ARCHIVED">已归档</a-radio-button><a-radio-button value="DELETED">回收站</a-radio-button>
            </a-radio-group>
          <template #tools>
            <RuntimeActionGroup :actions="actions" location="LIST_TOOLBAR" :can="canPageAction" :disabled-reason="pageActionReason" :module-name="activeModule?.moduleName" @run="runConfiguredAction" />
            <a-button :class="{ 'record-filter-active': listFilters.length }" @click="filterOpen = true"><FilterOutlined />筛选<span v-if="listFilters.length">（{{ listFilters.length }}）</span></a-button>
            <a-select v-if="savedViews.length" :value="activeViewCode || undefined" allow-clear placeholder="常用视图" style="width:160px" :options="savedViews.map(view => ({ value: view.code, label: `${view.name}${view.defaultView ? ' · 默认' : ''}` }))" @change="selectSavedView" />
            <a-popover v-model:open="columnOpen" trigger="click" placement="bottomRight">
              <template #content><div class="runtime-column-settings"><p>调整列表显示方式；最多固定三列。</p>
                <a-button type="link" block @click="columnOpen = false; openSaveView()"><SaveOutlined />保存当前视图</a-button>
                <div v-for="column in currentColumns" :key="column.code" :draggable="visibleFieldCodes.includes(column.code)" @dragstart="draggingColumn = column.code" @dragover.prevent @drop.prevent="moveColumn(column.code)">
                  <span class="column-drag">⋮⋮</span><a-checkbox :checked="visibleFieldCodes.includes(column.code)" @change="toggleColumn(column.code, $event.target.checked)">{{ column.label }}</a-checkbox>
                  <a-checkbox :checked="fixedFieldCodes.includes(column.code)" :disabled="!visibleFieldCodes.includes(column.code) || (!fixedFieldCodes.includes(column.code) && fixedFieldCodes.length >= 3)" @change="toggleFixedColumn(column.code, $event.target.checked)">固定</a-checkbox>
                </div>
                <p v-if="sourceReadColumns.length">当前只读来源字段随记录展示，不用于查询或保存视图。</p>
              </div></template>
              <a-button aria-label="列表设置" title="调整列表字段与保存视图"><SettingOutlined /></a-button>
            </a-popover>
          </template>
        </ProductFilterBar>
      </template>
      <template v-if="activeCode" #summary><div class="record-summary"><div><a-segmented v-if="showTenantScope" v-model:value="tenantScope" size="small" :options="[{ label: '全部', value: 'ALL' }, { label: '当前工作空间', value: 'OWN' }, { label: '共享给我', value: 'SHARED' }]" @change="page = 1; activeViewCode = ''; loadRecords()" /><strong>{{ loading ? '正在加载…' : `共 ${total} 条记录` }}</strong><small v-if="queryCustomized">{{ querySummary }}</small></div></div></template>
      <template v-if="selectedRowKeys.length" #batch><div class="runtime-batch-bar"><strong>已选择 {{ selectedRowKeys.length }} 条</strong><RuntimeActionGroup :actions="batchActionList" location="BATCH" :can="canPageAction" :disabled-reason="pageActionReason" size="small" @run="runConfiguredAction" /><a-button type="link" size="small" @click="selectedRowKeys = []">清除选择</a-button></div></template>
      <template #state-actions="{ state }">
        <a-button v-if="state === 'error'" type="primary" @click="activeCode ? loadRecords() : loadModules()">保留条件重试</a-button>
        <a-button v-else-if="activeCode && hasAction('CREATE') && lifecycleState === 'ACTIVE'" type="primary" @click="runAction('CREATE', openCreate)">新建第一条记录</a-button>
      </template>
      <template v-if="activeCode && records.length">
          <SchemaRecordTable class="record-table" :records="records" :columns="displayedColumns" :fixed-codes="fixedFieldCodes" :density="listSettings.density" :caption="listCaption" :loading="loading" :row-selection="canSelectRecords ? rowSelection : undefined" :custom-row="rowHandlers" :scroll="{ y: pageSize >= 100 ? 560 : undefined }" :virtual="pageSize >= 100">
            <template #value="{ column, record }"><template v-if="column.code === 'title'"><strong class="record-title">{{ record.title }}</strong><small>{{ userFacingRecordNumber(record.recordNumber, '未设置业务编号') }} · {{ userFacingWorkspaceName(record.dataTenantName) }}</small></template>
                <a-tag v-else-if="column.code === 'dataTenantName'" :color="record.shared ? 'orange' : 'blue'">{{ record.shared ? `${userFacingWorkspaceName(record.dataTenantName)}共享` : '当前工作空间' }}</a-tag>
                <ProductStatusTag v-else-if="column.code === 'status'" :status="record.status" /><template v-else>{{ columnValue(column, record) }}<small v-if="columnSourceName(column, record)" class="source-field-label">来源字段：{{ columnSourceName(column, record) }}</small></template></template>
            <template v-if="showRecordActions" #actions="{ record }"><RuntimeActionGroup :actions="rowActions(record)" location="ROW" :can="code => canRecordAction(code, record)" size="small" @run="runConfiguredAction($event, record)" /></template>
          </SchemaRecordTable>
          <div class="mobile-record-list" aria-label="业务记录卡片列表" :aria-busy="loading">
            <div v-for="record in records" :key="record.id" class="mobile-record-item">
            <a-checkbox v-if="canSelectRecords" :checked="selectedRowKeys.includes(record.id)" @change="selectedRowKeys = $event.target.checked ? [...new Set([...selectedRowKeys, record.id])] : selectedRowKeys.filter(id => id !== record.id)">选择此记录</a-checkbox>
            <button type="button" :data-status-tone="statusTone(record.status)" :disabled="!canRecordAction('DETAIL', record) && !canRecordAction('UPDATE', record)"
              @click="canRecordAction('DETAIL', record) ? openDetail(record) : openEdit(record)">
              <header><span><strong>{{ record.title }}</strong><small>{{ userFacingRecordNumber(record.recordNumber, '未设置业务编号') }} · {{ userFacingWorkspaceName(record.dataTenantName) }}</small></span><ProductStatusTag :status="record.status" /></header>
              <dl>
                <div v-for="column in displayedColumns.filter(item => !['title', 'status', 'dataTenantName'].includes(item.code) && ((!item.field && !item.readOnlySource) || recordFieldVisible(record, item.code))).slice(0, 3)" :key="column.code">
                  <dt>{{ columnSourceName(column, record) || column.label }}<small v-if="columnSourceName(column, record)">（来源字段）</small></dt><dd>{{ columnValue(column, record) }}</dd>
                </div>
              </dl>
              <footer><span>{{ record.shared ? `${userFacingWorkspaceName(record.dataTenantName)}共享` : '当前工作空间记录' }}</span><strong>{{ canRecordAction('DETAIL', record) ? '查看详情 →' : canRecordAction('UPDATE', record) ? '编辑记录 →' : '仅列表可见' }}</strong></footer>
            </button>
            <RuntimeActionGroup :actions="rowActions(record)" location="ROW" :can="code => canRecordAction(code, record)" size="small" @run="runConfiguredAction($event, record)" />
            </div>
          </div>
      </template>
      <template v-if="activeCode" #pagination><a-pagination v-model:current="page" v-model:page-size="pageSize" :total="total" show-size-changer @change="loadRecords" /></template>
    </ProductListWorkspace>

    <a-drawer v-model:open="filterOpen" title="高级筛选与服务端排序" width="520" :mask-closable="false">
      <a-alert type="info" show-icon class="section-alert" message="所有条件均由服务端执行权限校验" description="不可读或已脱敏字段不能用于筛选、排序或保存视图。查询失败不会清空当前条件。" />
      <a-alert v-if="queryRestoreError" type="warning" show-icon class="section-alert" :message="queryRestoreError" />
      <div class="runtime-filter-list">
        <div v-for="(condition, index) in listFilters" :key="index" class="runtime-filter-row">
          <a-select v-model:value="condition.fieldCode" show-search option-filter-prop="label" :options="filterFieldOptions.some(option => option.value === condition.fieldCode) ? filterFieldOptions : [...filterFieldOptions, { value: condition.fieldCode, label: '原筛选字段（当前不可用）', disabled: true }]" @change="resetFilterCondition(condition)" />
          <a-select v-model:value="condition.operator" :options="conditionOperators(condition).some(option => option.value === condition.operator) ? conditionOperators(condition) : [...conditionOperators(condition), { value: condition.operator, label: '原筛选方式（当前不支持）', disabled: true }]" />
          <template v-if="!['EMPTY', 'NOT_EMPTY'].includes(condition.operator)">
            <a-input-number v-if="fieldFamily(filterValueType(condition)) === 'NUMBER'" :value="numericFilterValue(condition)" string-mode style="width:100%" placeholder="输入数字" @update:value="setNumericFilterValue(condition, $event)" />
            <a-date-picker v-else-if="filterValueType(condition) === 'DATE'" v-model:value="condition.value" value-format="YYYY-MM-DD" style="width:100%" placeholder="选择日期" />
            <a-date-picker v-else-if="filterValueType(condition) === 'DATETIME'" v-model:value="condition.value" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" placeholder="选择日期和时间" />
            <a-select v-else-if="filterValueType(condition) === 'BOOLEAN'" v-model:value="condition.value" :options="[{ value: 'true', label: '是' }, { value: 'false', label: '否' }]" placeholder="选择是或否" />
            <a-select v-else-if="filterUsesOptions(condition)" v-model:value="condition.value" show-search option-filter-prop="label" :options="filterValueOptions(condition)" placeholder="选择当前授权记录的值" not-found-content="当前范围内暂无可选值" />
            <a-input v-else-if="filterRuntimeField(condition) && filterValueType(condition) !== 'JSON'" v-model:value="condition.value" placeholder="条件值" />
            <span v-else>请重新选择字段或筛选方式</span>
          </template>
          <a-button danger type="text" @click="listFilters.splice(index, 1)">移除</a-button>
        </div>
        <a-button block type="dashed" @click="addFilter"><PlusOutlined />增加条件</a-button>
      </div>
      <a-divider>排序</a-divider>
      <div class="form-grid"><a-form-item label="排序字段"><a-select v-model:value="sortField" show-search option-filter-prop="label" :options="sortFieldOptions.some(option => option.value === sortField) ? sortFieldOptions : [...sortFieldOptions, { value: sortField, label: '原排序字段（当前不可用）', disabled: true }]" /></a-form-item><a-form-item label="方向"><a-radio-group v-model:value="sortDirection" button-style="solid"><a-radio-button value="ASC">升序</a-radio-button><a-radio-button value="DESC">降序</a-radio-button></a-radio-group></a-form-item></div>
      <template #footer><div class="drawer-footer"><a-button @click="clearListQuery">清除条件</a-button><a-button @click="filterOpen = false">取消</a-button><a-button type="primary" @click="applyListQuery">应用并查询</a-button></div></template>
    </a-drawer>

    <a-modal v-model:open="saveViewOpen" :title="activeSavedView ? '更新常用视图' : '保存常用视图'" :confirm-loading="saveViewSaving" ok-text="保存" @ok="saveCurrentView">
      <a-form layout="vertical"><a-form-item label="视图名称" required><a-input v-model:value="viewForm.name" :maxlength="80" placeholder="例如：本月重点客户" /></a-form-item><a-form-item label="进入模块时默认恢复"><a-switch v-model:checked="viewForm.defaultView" /></a-form-item></a-form>
      <a-alert type="info" show-icon message="将保存当前记录状态、租户范围、搜索、组合筛选、排序、列顺序、固定列和分页大小。旧视图未设置范围时使用有效记录、全部租户。" />
      <template v-if="activeSavedView" #footer><div class="drawer-footer"><a-button danger @click="deleteCurrentView">删除视图</a-button><span></span><a-button @click="saveViewOpen = false">取消</a-button><a-button type="primary" :loading="saveViewSaving" @click="saveCurrentView">更新视图</a-button></div></template>
    </a-modal>

    <ProductDetailDrawer :open="detailOpen" :title="detail?.title" :width="assistantOpen ? 'min(980px, 100vw)' : 'min(760px, 100vw)'" class="detail-drawer" @close="closeDetail">
      <template #extra><a-space wrap><RuntimeActionGroup v-if="detail" :actions="recordActions(detail)" location="DETAIL_HEADER" :can="code => canRecordAction(code, detail!)" @run="runConfiguredAction($event, detail)" /><a-button v-if="detail && canUseAi" @click="assistantOpen = !assistantOpen"><RobotOutlined />{{ assistantOpen ? '收起助手' : '询问当前记录' }}</a-button></a-space></template>
      <template v-if="detail"><div :class="['record-detail-shell', { 'record-detail-shell--assistant': assistantOpen && canUseAi }]"><section class="record-detail-main"><div class="detail-meta"><span>{{ userFacingRecordNumber(detail.recordNumber, '未设置业务编号') }}</span><ProductStatusTag :status="detail.status" /><a-tag :color="detail.shared ? 'orange' : 'blue'">数据归属：{{ userFacingWorkspaceName(detail.dataTenantName) }}</a-tag><span v-if="detail.shared" class="detail-share-status">共享状态：<ProductStatusTag :status="detail.shareStatus" /></span></div>
        <a-tabs class="detail-tabs-runtime">
          <a-tab-pane key="basic" tab="概览"><SchemaRecordDetail :layout="detailLayout" :fields="detailValueFields" :values="detail.fields" :presentation="detail.fieldPresentation" :read-fields="detailReadFields" :read-text="(field, value) => readFieldValue(field, value, detail!)" :text="(field, value) => displayValue(field, value, detail)"><template #tools="{ field }"><a-button type="link" size="small" @click="copyFieldValue(field, detail.fields[field.code])">复制</a-button></template><template #read-tools="{ field }"><a-button type="link" size="small" @click="copyReadFieldValue(field, detail!)">复制</a-button></template></SchemaRecordDetail><a-descriptions class="detail-system-meta" :column="1" bordered size="small">
            <a-descriptions-item label="负责人">{{ ownerDisplayName(detail) }}</a-descriptions-item><a-descriptions-item label="负责部门">{{ detail.departmentId ? organizationDepartmentName(detail, detail.departmentId) : '未设置' }}</a-descriptions-item><a-descriptions-item label="相关人">{{ detail.participantMemberIds.length ? detail.participantMemberIds.map(id => organizationMemberName(detail, id)).join('、') : '—' }}</a-descriptions-item><a-descriptions-item label="创建时间">{{ productDateTime(detail.createdAt) }}</a-descriptions-item><a-descriptions-item label="更新时间">{{ productDateTime(detail.updatedAt) }}</a-descriptions-item></a-descriptions></a-tab-pane>
          <a-tab-pane key="relations" tab="转化"><a-list v-if="conversionLinks.length" size="small" bordered :data-source="conversionLinks"><template #renderItem="{ item }"><a-list-item><a-button type="link" @click="openConversionLink(item)">{{ item.direction === 'SOURCE' ? '来源' : '目标' }} · {{ item.moduleName }} · {{ item.recordTitle }}</a-button><ProductStatusTag :status="item.recordStatus" /><a-tag v-if="item.relationStatus === 'INVALID'">关系已解除</a-tag><a-button v-if="item.canUnlink" type="link" danger @click="openConversionUnlink(item)">解除关联</a-button><span>{{ productDateTime(item.convertedAt) }}</span></a-list-item></template></a-list><a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无有权查看的关联数据" /></a-tab-pane>
          <a-tab-pane v-if="recordAttachmentFields.length && canViewAttachments" key="attachments" tab="附件"><a-select v-if="recordAttachmentFields.length > 1" v-model:value="attachmentFieldCode" :options="recordAttachmentFields.map(field => ({ value: field.code, label: field.name }))" style="width:100%;margin-bottom:12px" placeholder="选择附件分类" /><BusinessAttachmentsPanel v-if="recordAttachmentEndpoint" :endpoint="recordAttachmentEndpoint" :writable="false" :title="selectedAttachmentField?.name || '附件'" description="附件按当前字段权限展示；请通过编辑记录替换或调整，必填校验与记录保存会在同一事务完成。" compact /></a-tab-pane>
          <a-tab-pane key="timeline" tab="历史"><a-timeline v-if="timeline.length"><a-timeline-item v-for="entry in timeline" :key="entry.eventId">
              <div class="timeline-heading"><strong>{{ entry.label }}</strong><span>{{ productDateTime(entry.occurredAt) }}</span></div><p>{{ entry.actorDisplayName }}<template v-if="entry.summary"> · {{ entry.summary }}</template></p>
              <ul v-if="entry.changes.length" class="timeline-changes"><li v-for="change in entry.changes" :key="`${entry.eventId}-${change.fieldCode}`"><strong>{{ change.fieldName }}</strong>：<template v-if="change.masked">无查看权限</template><template v-else>{{ timelineFieldValue(change.fieldCode, change.beforeValue) }} → {{ timelineFieldValue(change.fieldCode, change.afterValue) }}</template></li></ul>
            </a-timeline-item></a-timeline><a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无变更记录" /></a-tab-pane>
        </a-tabs></section><AiContextAssistantPanel v-if="assistantOpen && canUseAi" :key="`${activeCode}-${detail.id}`" :module-code="activeCode" :module-name="activeModule?.moduleName || activeCode" :record="detail" :visible-fields="detailValueFields" :modules="modules" @business-written="handleAiBusinessWritten" /></div></template>
      <template #actions><RuntimeActionGroup v-if="detail" :actions="recordActions(detail)" location="DETAIL_MORE" :can="code => canRecordAction(code, detail!)" menu @run="runConfiguredAction($event, detail)" /><a-button v-if="printTemplates.length" @click="openPrint()"><PrinterOutlined />打印</a-button></template>
    </ProductDetailDrawer>

    <a-modal v-model:open="printOpen" title="记录打印与 PDF 预览" width="920" :footer="null" @cancel="clearPrintPreview">
      <div class="print-runtime-toolbar"><a-select v-model:value="selectedPrintTemplateId" style="min-width:320px" :options="printTemplates.map(item => ({ value: item.templateId, label: `${item.name} · v${item.versionNumber} · ${item.pageSize}` }))" /><a-button type="primary" :loading="printing" @click="generatePrint"><PrinterOutlined />按发布版本生成</a-button><a-button :disabled="!printPreviewUrl" @click="openOriginalPrint">打开原始 PDF</a-button><a-button :disabled="!printJob" @click="downloadPrint"><ExportOutlined />下载 PDF</a-button></div>
      <a-alert v-if="printJob" type="success" show-icon :message="`打印文件已生成，共 ${printJob.pageCount} 页`" :description="`使用模板版本 v${printJob.templateVersionNumber} · 完成于 ${productDateTime(printJob.finishedAt)}`" />
      <section v-if="printJob?.pages.length" class="print-runtime-preview" aria-label="PDF 内容在线预览">
        <a-tag color="blue">PDF 内容预览 · 与下载文件使用同一打印任务快照</a-tag>
        <div class="print-page-stack">
          <article v-for="page in printJob.pages" :key="page.pageNumber" :class="['print-page', `print-page--${selectedPrintTemplate?.orientation.toLowerCase() || 'portrait'}`, `print-page--${selectedPrintTemplate?.pageSize.toLowerCase() || 'a4'}`]" :style="{ '--print-offset-x': `${(selectedPrintTemplate?.layout.offsetXmm || 0) * 3}px`, '--print-offset-y': `${(selectedPrintTemplate?.layout.offsetYmm || 0) * 3}px` }">
            <header><h2>{{ printJob.templateName }}</h2><p>{{ page.header }}</p><small>{{ page.recordNumber || '业务记录' }} · {{ page.recordTitle }}</small></header>
            <dl><div v-for="field in page.fields" :key="`${page.pageNumber}-${field.code}`" :class="{ detail: field.detail }"><dt>{{ field.detail ? '明细 · ' : '' }}{{ field.name }}</dt><dd>{{ field.value }}</dd></div></dl>
            <div v-if="page.signatureLabel" class="print-signature">{{ page.signatureLabel }}：________________</div>
            <footer><span>{{ page.footer }}</span><b>第 {{ page.pageNumber }} / {{ page.pageCount }} 页</b></footer>
          </article>
        </div>
      </section>
      <a-empty v-else description="选择已发布模板后生成 PDF；系统会在此刻重新校验记录和字段权限" />
    </a-modal>
    <ProductFormDrawer :open="formOpen" :title="`${editing ? '编辑' : '新建'}${formConfiguration?.configuration.module.name || activeModule?.moduleName || ''}`" :saving="saving" :error="formError" @close="requestCloseForm" @save="saveRecord">
      <a-alert v-if="formDraftRestored" type="info" show-icon message="已恢复自动保存的草稿" class="section-alert" />
      <div class="form-change-state"><a-tag :color="formDirty ? 'orange' : 'green'">{{ formDirty ? (formFields.some(field => field.fieldType === 'SECRET') ? '有未提交修改 · 其他内容已保存草稿' : '有未提交修改 · 已自动保存草稿') : '尚未修改' }}</a-tag><small v-if="formFields.some(field => field.fieldType === 'SECRET')">密码和密钥不保存到草稿</small></div>
      <a-alert v-if="recordTitleField" type="info" show-icon class="section-alert" :message="`记录名称使用“${recordTitleField.name}”，无需重复填写标题`" />
      <a-form layout="vertical"><div class="form-grid"><a-form-item v-if="!recordTitleField" label="标题" required><a-input id="runtime-record-title" v-model:value="form.title" /></a-form-item><a-form-item label="业务编号"><a-input v-model:value="form.recordNumber" /></a-form-item></div>
        <a-divider>模块字段</a-divider>
        <SchemaFormLayout :layout="formLayout" :fields="formValueFields"><template #field="{ field }"><a-form-item :label="field.name" :required="field.required" :data-runtime-field="field.code"
          :validate-status="fieldErrors[field.code] ? 'error' : undefined" :help="fieldErrors[field.code]">
          <RuntimeAttachmentFieldInput v-if="attachmentFieldTypes.has(field.fieldType)" :model-value="attachmentFieldValue(field.code)" :field="field" :token="token" @update:model-value="form.fields[field.code] = $event" />
          <RuntimeFieldInput v-else-if="isSharedRuntimeField(field.fieldType)" v-model="form.fields[field.code]" :field="field" :preserve-existing="Boolean(editing)" :options="formOptions(field)" />
          <a-input-number v-else-if="field.fieldType === 'MONEY'" v-model:value="form.fields[field.code]" style="width:100%" />
          <a-select v-else-if="['SINGLE_SELECT', 'STATUS'].includes(field.fieldType)" v-model:value="form.fields[field.code]" :options="formOptions(field)" allow-clear />
          <a-cascader v-else-if="field.fieldType === 'CASCADE'" v-model:value="form.fields[field.code]" :options="formCascadeOptions[field.code] || []"
            :change-on-select="Boolean(fieldConfig(field).allowIntermediate)" allow-clear show-search placeholder="请选择级联项" />
          <PersonSelect v-else-if="field.fieldType === 'MEMBER'" :model-value="numericFieldValue(field.code)" :people="formFieldDirectory(field.code)?.people || []" value-key="systemMemberId" placeholder="搜索并选择成员" @update:model-value="form.fields[field.code] = $event" />
          <DepartmentSelect v-else-if="field.fieldType === 'DEPARTMENT'" :model-value="numericFieldValue(field.code)" :departments="formFieldDirectory(field.code)?.departments || []" @update:model-value="form.fields[field.code] = $event" />
          <a-select v-else-if="field.fieldType === 'REFERENCE'" v-model:value="form.fields[field.code]" show-search allow-clear :loading="formCatalogLoading || formReferenceLoading[field.code]" :filter-option="false" :options="formReferenceOptions[field.code] || []" placeholder="搜索并选择业务记录" @search="(value: string) => searchFormReferenceOptions(field, value)"><template #option="{ label, description }"><span class="reference-option"><strong>{{ label }}</strong><small>{{ description }}</small></span></template></a-select>
          <a-input v-else v-model:value="form.fields[field.code]" />
        </a-form-item></template></SchemaFormLayout>
      </a-form>
    </ProductFormDrawer>
    <a-modal v-model:open="manualActionOpen" :title="manualAction?.name || '执行业务动作'" :footer="null" :mask-closable="false" :closable="!manualActionSaving" :keyboard="!manualActionSaving" width="720px">
      <a-alert type="info" show-icon class="section-alert"
        :message="manualActionTargets.length === 1 ? `处理“${manualActionTargets[0]?.title}”` : `批量处理 ${manualActionTargets.length} 条记录`"
        description="填写业务字段后统一执行；批量处理中每条记录独立校验、审批和记录结果。" />
      <a-form layout="vertical" :disabled="manualActionSaving || manualActionResults.length > 0">
        <a-form-item v-for="field in manualActionFields" :key="field.code" :label="field.name" :required="field.required">
          <PersonSelect v-if="field.fieldType === 'MEMBER'" :model-value="manualOrganizationValue(field)" :people="manualOrganizationOptions[field.code]?.people || []" value-key="systemMemberId" placeholder="选择各记录共同可用的成员" @update:model-value="manualActionValues[field.code] = $event" />
          <DepartmentSelect v-else-if="field.fieldType === 'DEPARTMENT'" :model-value="manualOrganizationValue(field)" :departments="manualOrganizationOptions[field.code]?.departments || []" @update:model-value="manualActionValues[field.code] = $event" />
          <a-cascader v-else-if="field.fieldType === 'CASCADE'" :value="manualCascadeValue(field)" :options="manualCascadeOptions[field.code] || []" :change-on-select="Boolean(fieldConfig(field).allowIntermediate)" allow-clear show-search placeholder="选择共同可用的层级项" @update:value="manualActionValues[field.code] = $event" />
          <a-select v-else-if="field.fieldType === 'REFERENCE'" :value="typeof manualActionValues[field.code] === 'number' ? manualActionValues[field.code] as number : undefined" show-search allow-clear :loading="manualReferenceLoading[field.code]" :filter-option="false" :options="manualReferenceDisplayOptions(field)" placeholder="搜索并选择共同可用的业务记录" @update:value="selectManualReference(field, $event)" @search="(value: string) => searchManualReferenceOptions(field, value)"><template #option="{ label, description }"><span class="reference-option"><strong>{{ label }}</strong><small>{{ description }}</small></span></template></a-select>
          <RuntimeFieldInput v-else v-model="manualActionValues[field.code]" :field="field" preserve-existing :options="manualDisplayOptions(field)" />
          <small v-if="manualReferenceUnavailable[field.code]" class="manual-choice-hint">当前关联不可访问；已保留返回值，可以清空或重新选择。</small>
          <small v-if="manualChoiceHint(field)" class="manual-choice-hint">{{ manualChoiceHint(field) }}</small>
        </a-form-item>
      </a-form>
      <a-table v-if="manualActionResults.length" size="small" :pagination="false" :data-source="manualActionResults" row-key="recordId" :scroll="{ y: 240 }">
        <a-table-column title="记录" data-index="title" :ellipsis="true" />
        <a-table-column title="结果" width="90"><template #default="{ record }"><a-tag :color="record.status === 'SUCCESS' ? 'green' : 'red'">{{ record.status === 'SUCCESS' ? '成功' : '失败' }}</a-tag></template></a-table-column>
        <a-table-column title="说明" data-index="message" />
        <a-table-column title="处理详情" width="100"><template #default="{ record }"><a-button v-if="record.result" type="link" size="small" @click="lastAction = { moduleCode: activeCode, result: record.result }; manualActionOpen = false">查看结果</a-button></template></a-table-column>
      </a-table>
      <div class="drawer-footer">
        <a-button :disabled="manualActionSaving" @click="manualActionOpen = false">关闭</a-button>
        <a-button v-if="manualActionResults.some(item => item.status === 'FAILED')" :loading="manualActionSaving" @click="requestManualAction(true)">只重试失败项</a-button>
        <a-button v-if="!manualActionResults.length" type="primary" :loading="manualActionSaving" @click="requestManualAction(false)">确认执行</a-button>
      </div>
    </a-modal>
    <a-modal v-model:open="lifecycleOpen" :title="batchDeleteTargets.length ? '批量删除记录' : lifecycleAction === 'ARCHIVE' ? '确认归档记录' : lifecycleAction === 'DELETE' ? '确认删除记录' : '确认恢复记录'"
      :footer="batchDeleteTargets.length ? null : undefined" :mask-closable="false" :closable="!lifecycleSaving" :keyboard="!lifecycleSaving"
      :confirm-loading="lifecycleSaving" ok-text="确认执行" cancel-text="取消" @ok="submitLifecycle">
      <a-alert v-if="batchDeleteTargets.length" type="warning" show-icon :message="`将处理 ${batchDeleteTargets.length} 条记录`"
        description="删除会移入回收站，可通过恢复操作找回；关联数据保留。每条记录独立校验权限、版本和审批配置，请确认后提交。" />
      <a-alert v-if="lifecycleImpact" type="warning" show-icon
        :message="`${lifecycleAction === 'ARCHIVE' ? '归档' : lifecycleAction === 'DELETE' ? '删除' : '恢复'}“${lifecycleRecord?.title}”`"
        class="section-alert">
        <template #description>{{ lifecycleImpact.warning }} 影响范围：出向关联 {{ lifecycleImpact.outgoingRelationCount }} 条，入向关联 {{ lifecycleImpact.incomingRelationCount }} 条，相关人 {{ lifecycleImpact.participantCount }} 人。</template>
      </a-alert>
      <a-form layout="vertical" :disabled="lifecycleSaving || batchDeleteResults.length > 0 && batchDeleteTargets.length > 0"><a-form-item label="操作原因" required><a-textarea v-model:value="lifecycleReason" :rows="3" :maxlength="1000" show-count /></a-form-item></a-form>
      <template v-if="batchDeleteTargets.length">
        <ul v-if="!batchDeleteResults.length"><li v-for="record in batchDeleteTargets" :key="record.id">{{ record.title }}</li></ul>
        <a-table v-else size="small" :pagination="false" :data-source="batchDeleteResults" row-key="recordId" :scroll="{ y: 240 }">
          <a-table-column title="记录" data-index="title" />
          <a-table-column title="结果" width="75"><template #default="{ record }"><a-tag :color="record.status === 'SUCCESS' ? 'green' : 'red'">{{ record.status === 'SUCCESS' ? '已提交' : '失败' }}</a-tag></template></a-table-column>
          <a-table-column title="说明" data-index="message" />
          <a-table-column title="处理详情" width="95"><template #default="{ record }"><a-button v-if="record.result" type="link" size="small" @click="lastAction = { moduleCode: activeCode, result: record.result }; lifecycleOpen = false">查看结果</a-button></template></a-table-column>
        </a-table>
        <div class="drawer-footer"><a-button :disabled="lifecycleSaving" @click="lifecycleOpen = false">关闭</a-button>
          <a-button v-if="batchDeleteResults.some(item => item.status === 'FAILED')" :loading="lifecycleSaving" @click="submitBatchDelete(true)">只重试失败项</a-button>
          <a-button v-if="!batchDeleteResults.length" type="primary" danger :loading="lifecycleSaving" @click="submitBatchDelete(false)">确认删除</a-button>
        </div>
      </template>
    </a-modal>
    <a-modal v-model:open="shareOpen" title="共享给同系统租户" :footer="null" :mask-closable="false" width="680px">
      <a-alert type="info" show-icon class="section-alert" message="共享不会复制正文，也不会改变数据归属" description="目标租户默认只能查看；额外动作必须同时通过共享授权、目标角色权限和数据范围，撤销或到期后新访问立即失效。" />
      <a-form layout="vertical"><div class="form-grid"><a-form-item label="目标工作空间" required><a-select v-model:value="shareForm.targetTenantId" :options="shareTargets.map((item) => ({ value: item.tenantId, label: userFacingWorkspaceName(item.tenantName) }))" /></a-form-item><a-form-item label="失效时间"><a-date-picker v-model:value="shareForm.expiresAt" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></a-form-item></div>
        <a-form-item label="额外允许动作" extra="查看列表和详情始终随授权加入；这里只选择需要额外开放的写动作。"><a-checkbox-group v-model:value="shareForm.allowedActions" :options="shareActionOptions" /></a-form-item></a-form>
      <div class="drawer-footer"><a-button @click="shareOpen = false">关闭</a-button><a-button type="primary" :loading="shareSaving" @click="grantShare">授权共享</a-button></div>
      <a-divider>授权记录</a-divider>
      <a-table size="small" :pagination="false" :data-source="shareRows" row-key="id">
        <a-table-column title="目标租户" data-index="targetTenantName" /><a-table-column title="动作"><template #default="{ record }">{{ actionNames(record.allowedActions) }}</template></a-table-column><a-table-column title="状态"><template #default="{ record }"><ProductStatusTag :status="record.status" /></template></a-table-column><a-table-column title="失效时间"><template #default="{ record }">{{ record.expiresAt || '长期有效' }}</template></a-table-column><a-table-column title="操作"><template #default="{ record }"><a-button v-if="record.status === 'ACTIVE'" danger type="link" size="small" @click="revokeShare(record)">撤销</a-button></template></a-table-column>
      </a-table>
    </a-modal>
    <a-modal v-model:open="transferOpen" title="转交负责人和部门" :footer="null" :mask-closable="false" :closable="!transferSaving" :keyboard="!transferSaving">
      <a-form layout="vertical" :disabled="transferSaving"><div class="form-grid"><a-form-item label="新负责人" required><PersonSelect :disabled="transferSaving" v-model="transferForm.ownerMemberId" :people="transferOrganizationOptions?.people || []" placeholder="搜索并选择来源成员" @change="transferPreview = undefined" /></a-form-item><a-form-item label="负责部门"><DepartmentSelect :disabled="transferSaving" v-model="transferForm.departmentId" :departments="transferOrganizationOptions?.departments || []" @change="transferPreview = undefined" /></a-form-item></div><a-form-item label="相关成员"><PersonSelect :disabled="transferSaving" v-model="transferForm.participantMemberIds" multiple :people="transferOrganizationOptions?.people || []" placeholder="搜索并选择来源协作成员" @change="transferPreview = undefined" /></a-form-item><a-form-item label="转交原因" required><a-textarea v-model:value="transferForm.reason" :rows="3" :maxlength="1000" show-count @change="transferPreview = undefined" /></a-form-item></a-form>
      <a-alert v-if="transferPreview" type="info" show-icon class="section-alert" message="归属变化预览"><template #description>{{ transferPreview.fromOwnerName || '不可用成员' }} → {{ transferPreview.toOwnerName || '不可用成员' }}；{{ transferPreview.fromDepartmentName || '无部门' }} → {{ transferPreview.toDepartmentName || '无部门' }}；相关人 {{ transferPreview.fromParticipantNames.map(name => name || '不可用成员').join('、') || '无' }} → {{ transferPreview.toParticipantNames.map(name => name || '不可用成员').join('、') || '无' }}</template></a-alert>
      <a-alert v-if="transferPreview" type="info" :message="`关联影响：出向 ${transferPreview.outgoingRelationCount} 条，入向 ${transferPreview.incomingRelationCount} 条`" description="仅变更当前记录的负责人、部门及相关人；模块、数据工作空间和关联记录归属保留。" />
      <div class="drawer-footer"><a-button :disabled="transferSaving" @click="transferOpen = false">取消</a-button><a-button :loading="transferSaving" @click="previewTransfer">预览变化</a-button><a-button type="primary" :disabled="!transferPreview" :loading="transferSaving" @click="submitTransfer">确认转交</a-button></div>
    </a-modal>
    <a-modal v-model:open="conversionOpen" centered :title="batchConversionTargets.length ? '批量转化记录' : '转化为目标模块记录'" :footer="null" :mask-closable="false" :closable="!conversionSaving" :keyboard="!conversionSaving" :width="batchConversionTargets.length ? 860 : 640">
      <a-form layout="vertical"><a-form-item label="目标模块" required><a-select :disabled="conversionSaving || batchConversionResults.length > 0 && batchConversionTargets.length > 0" v-model:value="conversionTargetCode" :options="conversionTargetOptions" @change="resetConversionPreview" /></a-form-item></a-form>
      <template v-if="batchConversionTargets.length">
        <a-alert type="info" show-icon class="section-alert" :message="`本次处理 ${batchConversionTargets.length} 条来源记录`" description="每条记录独立校验和执行，已转化和待审批项不会重复提交。明确失败可修正来源后刷新预览再重试；响应未确认会重试原请求。" />
        <ul v-if="!batchConversionPreviews.length"><li v-for="record in batchConversionTargets" :key="record.id">{{ record.title }}</li></ul>
        <a-table v-else :key="batchConversionResults.length ? 'results' : 'preview'" size="small" :pagination="false" :data-source="batchConversionPreviews" row-key="recordId" :scroll="{ y: 320 }">
          <a-table-column title="来源记录" data-index="title" />
          <a-table-column title="预校验"><template #default="{ record }">{{ record.error || (record.preview?.executable ? '映射可执行' : record.preview?.issues.join('；')) }}</template></a-table-column>
          <a-table-column v-if="batchConversionResults.length" title="处理结果"><template #default="{ record }"><template v-for="item in batchConversionResults.filter(item => item.recordId === record.recordId)" :key="item.recordId"><a-tag>{{ batchConversionState(item) }}</a-tag><div>{{ item.message }}</div></template></template></a-table-column>
          <a-table-column v-if="batchConversionResults.length" title="数据"><template #default="{ record }"><a-button v-for="item in batchConversionResults.filter(item => item.recordId === record.recordId && item.status === 'SUCCESS')" :key="item.recordId" type="link" size="small" :disabled="conversionSaving" @click="openBatchConversionRecord(item)">{{ item.result?.value ? '打开目标' : '查看来源' }}</a-button></template></a-table-column>
          <template #expandedRowRender="{ record }"><template v-if="record.preview"><p>目标标题：{{ record.preview.targetTitle }}</p><a-table size="small" :pagination="false" :data-source="record.preview.mappings" row-key="targetFieldCode"><a-table-column title="目标字段" data-index="targetFieldName" /><a-table-column title="来源字段" data-index="sourceFieldName" /><a-table-column title="映射结果"><template #default="{ record: mapping }">{{ typeof mapping.value === 'boolean' ? (mapping.value ? '是' : '否') : timelineValue(mapping.value) }}</template></a-table-column><a-table-column title="校验" data-index="message" /></a-table></template><p v-else>{{ record.error }}</p></template>
        </a-table>
        <div class="drawer-footer">
          <a-button :disabled="conversionSaving" @click="conversionOpen = false">关闭</a-button>
          <a-button v-if="batchConversionResults.length" :loading="conversionSaving" @click="refreshBatchConversionResults">刷新处理结果</a-button>
          <a-button v-if="batchConversionFailed.length" :loading="conversionSaving" @click="previewBatchConversion(true)">刷新失败项并预览</a-button>
          <a-button v-if="batchConversionFailed.length" type="primary" :disabled="!batchConversionRetryPreviewed && batchConversionFailed.some(item => item.failureKind !== 'UNKNOWN_RESULT')" :loading="conversionSaving" @click="submitBatchConversion(true)">只重试失败项</a-button>
          <template v-if="!batchConversionResults.length"><a-button :loading="conversionSaving" @click="previewBatchConversion(false)">预览全部映射</a-button><a-button type="primary" :disabled="!batchConversionPreviews.some(item => item.preview?.executable)" :loading="conversionSaving" @click="submitBatchConversion(false)">确认批量转化</a-button></template>
        </div>
      </template>
      <template v-else><template v-if="conversionPreview"><a-alert :type="conversionPreview.executable ? 'success' : 'error'" show-icon class="section-alert" :message="conversionPreview.executable ? '映射校验通过，可以执行转化' : '映射校验未通过'" :description="conversionPreview.issues.join('；') || `目标标题：${conversionPreview.targetTitle}`" />
        <a-table size="small" :pagination="false" :data-source="conversionPreview.mappings" row-key="targetFieldCode"><a-table-column title="目标字段" data-index="targetFieldName" /><a-table-column title="来源字段" data-index="sourceFieldName" /><a-table-column title="映射结果"><template #default="{ record }">{{ typeof record.value === 'boolean' ? (record.value ? '是' : '否') : timelineValue(record.value) }}</template></a-table-column><a-table-column title="校验" data-index="message" /></a-table></template>
      <div class="drawer-footer"><a-button :disabled="conversionSaving" @click="conversionOpen = false">取消</a-button><a-button :loading="conversionSaving" @click="previewConversion">预览映射</a-button><a-button type="primary" :disabled="!conversionPreview?.executable" :loading="conversionSaving" @click="submitConversion">确认并打开目标</a-button></div></template>
    </a-modal>
    <a-modal v-model:open="conversionUnlinkOpen" title="解除转化关联" :footer="null" :mask-closable="false" :closable="!conversionUnlinkSaving" :keyboard="!conversionUnlinkSaving">
      <a-alert type="info" show-icon :message="`解除与${conversionUnlinkLink?.moduleName} · ${conversionUnlinkLink?.recordTitle}的关系`" description="目标数据和转化历史会保留；解除后仍不能再次转化到同一目标模块。" />
      <a-form layout="vertical"><a-form-item label="解除原因" required><a-textarea v-model:value="conversionUnlinkReason" :disabled="conversionUnlinkSaving" :maxlength="1000" :rows="3" placeholder="说明为什么解除这条转化关联" /></a-form-item></a-form>
      <div class="drawer-footer"><a-button :disabled="conversionUnlinkSaving" @click="conversionUnlinkOpen = false">取消</a-button><a-button type="primary" danger :loading="conversionUnlinkSaving" @click="submitConversionUnlink">确认解除</a-button></div>
    </a-modal>
    <ModuleImportDrawer v-if="activeCode" v-model:open="importOpen" :module-code="activeCode" :module-name="activeModule?.moduleName" @records-changed="loadRecords" />
    <ModuleRangeSharingDrawer v-if="activeCode" v-model:open="rangeSharingOpen" :module-code="activeCode"
      :module-name="activeModule?.moduleName" :actions="actions" :enabled="canManageRangeShares" @changed="loadRecords" />
    <ModuleExportDrawer v-if="activeCode" v-model:open="exportOpen" :module-code="activeCode" :module-name="activeModule?.moduleName"
      :lifecycle-state="lifecycleState" :tenant-scope="tenantScope" :search="searchText" :filters="listFilters"
      :sort-field="sortField" :sort-direction="sortDirection" :selected-record-ids="selectedRowKeys" :visible-field-codes="visibleFieldCodes" />
  </ProductPage>
</template>
<style scoped>
.source-field-label { display: block; margin-top: 3px; color: #64748b; font-size: 11px; }
.manual-choice-hint { display: block; margin-top: 6px; color: #64748b; }
</style>
