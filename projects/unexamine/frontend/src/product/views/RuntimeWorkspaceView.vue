<script setup lang="ts">
import { DeleteOutlined, EditOutlined, ExportOutlined, FilterOutlined, ImportOutlined, InboxOutlined, MoreOutlined, PlusOutlined, PrinterOutlined, ReloadOutlined, RobotOutlined, SaveOutlined, SearchOutlined, SettingOutlined, SwapOutlined, UndoOutlined } from '@ant-design/icons-vue'
import { Empty, message, Modal } from 'ant-design-vue'
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import { statusTone } from '../responsive'
import { systemContext, systemTokens } from '../session'
import { conversionTargetCodes } from '../runtime-actions'
import { authorizedBlob, saveBlob } from '../file'
import { allowsPermission } from '../permissions'
import ModuleImportDrawer from '../components/ModuleImportDrawer.vue'
import ModuleExportDrawer from '../components/ModuleExportDrawer.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductPage from '../components/ProductPage.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import PersonSelect from '../components/PersonSelect.vue'
import DepartmentSelect from '../components/DepartmentSelect.vue'
import BusinessAttachmentsPanel from '../components/BusinessAttachmentsPanel.vue'
import AiContextAssistantPanel from '../components/AiContextAssistantPanel.vue'
import { productDateTime, userFacingRecordNumber, userFacingWorkspaceName } from '../presentation'
import type {
  RuntimeField,
  RuntimeDictionary,
  RuntimeModuleCatalogItem,
  RuntimeModuleConfiguration,
  RuntimeRecord,
  RuntimeRecordLifecycleImpact,
  RuntimeRecordList,
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
const error = ref('')
const modules = ref<RuntimeModuleCatalogItem[]>([])
const activeCode = ref('')
const configuration = ref<RuntimeModuleConfiguration>()
const records = ref<RuntimeRecord[]>([])
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
const formError = ref('')
const fieldErrors = reactive<Record<string, string>>({})
const lifecycleOpen = ref(false)
const lifecycleSaving = ref(false)
const lifecycleAction = ref<'ARCHIVE' | 'DELETE' | 'RESTORE'>('ARCHIVE')
const lifecycleRecord = ref<RuntimeRecord>()
const lifecycleImpact = ref<RuntimeRecordLifecycleImpact>()
const lifecycleReason = ref('')
const transferOpen = ref(false)
const transferSaving = ref(false)
const transferRecord = ref<RuntimeRecord>()
const transferPreview = ref<RuntimeRecordTransferPreview>()
const peopleDirectory = ref<SystemPeopleDirectory>({ departments: [], people: [], permissionVersion: 0 })
const transferForm = reactive({ ownerMemberId: undefined as number | undefined, departmentId: undefined as number | undefined, participantMemberIds: [] as number[], reason: '' })
const conversionOpen = ref(false)
const conversionSaving = ref(false)
const conversionRecord = ref<RuntimeRecord>()
const conversionTargetCode = ref('')
const conversionPreview = ref<RuntimeRecordConversionPreview>()
const conversionLinks = ref<RuntimeRecordConversionLink[]>([])
const shareOpen = ref(false)
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
const referenceOptions = reactive<Record<string, Array<{ value: number; label: string; description: string }>>>({})
const referenceLoading = ref(false)

const token = computed(() => systemTokens.value?.accessToken || '')
const selectedPrintTemplate = computed(() => printTemplates.value.find(item => item.templateId === selectedPrintTemplateId.value))
const fields = computed(() => configuration.value?.configuration.fields.filter((field) =>
  field.status === 'ACTIVE' && (field.access?.readable !== false || field.access?.writable === true)) || [])
const fieldsForPage = (pageType: string) => {
  const page = configuration.value?.configuration.pages.find((item) => item.pageType === pageType)
  if (!page) return fields.value
  try {
    const layout = JSON.parse(page.layoutJson) as { fieldCodes?: string[] }
    if (!layout.fieldCodes) return fields.value
    const byCode = new Map(fields.value.map((field) => [field.code, field]))
    return layout.fieldCodes.map((code) => byCode.get(code)).filter((field): field is RuntimeField => Boolean(field))
  } catch { return fields.value }
}
const listFields = computed(() => fieldsForPage('LIST').filter((field) => field.access?.readable !== false))
const formFields = computed(() => fieldsForPage('FORM').filter((field) => field.access?.writable !== false))
const detailFields = computed(() => fieldsForPage('DETAIL').filter((field) => field.access?.readable !== false))
const attachmentFieldTypes = new Set(['ATTACHMENT', 'IMAGE', 'FILE', 'FILE_GROUP'])
const recordAttachmentFields = computed(() => detailFields.value.filter(field => attachmentFieldTypes.has(field.fieldType)))
const detailValueFields = computed(() => detailFields.value.filter(field => !attachmentFieldTypes.has(field.fieldType)))
const formValueFields = computed(() => formFields.value.filter(field => !attachmentFieldTypes.has(field.fieldType)))
const selectedAttachmentField = computed(() => recordAttachmentFields.value.find(field => field.code === attachmentFieldCode.value))
const canViewAttachments = computed(() => allowsPermission(systemContext.value?.permissions, 'FILE', 'OBJECT', 'VIEW')
  || allowsPermission(systemContext.value?.permissions, 'FILE', '*', 'VIEW'))
const canWriteAttachments = computed(() => (allowsPermission(systemContext.value?.permissions, 'FILE', 'OBJECT', 'UPLOAD')
  || allowsPermission(systemContext.value?.permissions, 'FILE', '*', 'UPLOAD'))
  && (allowsPermission(systemContext.value?.permissions, 'FILE', 'OBJECT', 'REFERENCE')
    || allowsPermission(systemContext.value?.permissions, 'FILE', '*', 'REFERENCE')))
const canUseAi = computed(() => allowsPermission(systemContext.value?.permissions, 'AI', 'SYSTEM', 'VIEW')
  || allowsPermission(systemContext.value?.permissions, 'AI', '*', 'VIEW'))
const recordAttachmentEndpoint = computed(() => detail.value && selectedAttachmentField.value
  ? `/api/business-attachments/records/${activeCode.value}/${detail.value.id}/fields/${selectedAttachmentField.value.code}` : '')
const recordAttachmentWritable = computed(() => Boolean(detail.value && selectedAttachmentField.value?.access?.writable !== false
  && canWriteAttachments.value && !detail.value.archived && !detail.value.deleted && recordAllowsAction(detail.value, 'UPDATE')))
const actions = computed(() => configuration.value?.configuration.actions || [])
const activeModule = computed(() => modules.value.find((item) => item.moduleCode === activeCode.value))
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

type RuntimeColumn = { code: string; label: string; width: number; field?: RuntimeField }
const availableColumns = computed<RuntimeColumn[]>(() => [
  { code: 'title', label: '标题', width: 210 },
  ...listFields.value.map((field) => ({ code: field.code, label: field.name, width: 140, field })),
  { code: 'ownerMemberId', label: '负责人', width: 120 },
  { code: 'dataTenantName', label: '数据归属', width: 140 },
  { code: 'status', label: '状态', width: 90 },
  { code: 'createdAt', label: '创建时间', width: 150 },
  { code: 'updatedAt', label: '更新时间', width: 150 },
].filter((column, index, source) => source.findIndex((item) => item.code === column.code) === index))
const displayedColumns = computed(() => {
  const byCode = new Map(availableColumns.value.map((column) => [column.code, column]))
  return visibleFieldCodes.value.map((code) => byCode.get(code)).filter((column): column is RuntimeColumn => Boolean(column))
})
const filterFieldOptions = computed(() => availableColumns.value
  .filter((column) => !['dataTenantName', 'createdAt', 'updatedAt'].includes(column.code))
  .map((column) => ({ value: column.code, label: column.label })))
const sortFieldOptions = computed(() => availableColumns.value.map((column) => ({ value: column.code, label: column.label })))
const activeSavedView = computed(() => savedViews.value.find((view) => view.code === activeViewCode.value))
const showTenantScope = computed(() => Boolean(systemContext.value?.tenantSwitchContext?.tenantSwitchable))
const queryCustomized = computed(() => Boolean(searchText.value.trim() || listFilters.value.length || activeViewCode.value
  || sortField.value !== 'updatedAt' || sortDirection.value !== 'DESC' || tenantScope.value !== 'ALL'))
const querySummary = computed(() => {
  const parts: string[] = []
  if (searchText.value.trim()) parts.push(`搜索“${searchText.value.trim()}”`)
  if (listFilters.value.length) parts.push(`${listFilters.value.length} 项筛选`)
  const label = availableColumns.value.find((item) => item.code === sortField.value)?.label || sortField.value
  parts.push(`${label}${sortDirection.value === 'ASC' ? '升序' : '降序'}`)
  if (tenantScope.value !== 'ALL') parts.push(tenantScope.value === 'OWN' ? '仅本租户' : '仅共享给我')
  return parts.join(' · ')
})
const rowSelection = computed(() => ({
  selectedRowKeys: selectedRowKeys.value,
  onChange: (keys: Array<string | number>) => { selectedRowKeys.value = keys.map(Number) },
}))
const formDirty = computed(() => formOpen.value && formSnapshot.value !== serializeForm())

function hasAction(code: string) {
  return actions.value.some((action) => action.code === code)
}

function openAiCreate() {
  void router.replace({ path: route.path, query: { ...route.query, workspace: 'ai', aiMode: 'write', module: activeCode.value } })
}

function recordAllowsAction(record: RuntimeRecord, code: string) {
  return hasAction(code) && (!record.shared || record.sharedActions.includes(code))
}

const shareActionOptions = computed(() => actions.value
  .filter((action) => !['LIST', 'DETAIL', 'CREATE', 'RESTORE', 'SHARE'].includes(action.code))
  .map((action) => ({ label: action.name, value: action.code })))

function actionNames(codes: string[]) {
  const byCode = new Map(actions.value.map(action => [action.code, action.name]))
  return codes.map(code => byCode.get(code) || '其他操作').join('、') || '仅查看'
}

function runPageUtility(key: string) {
  if (key === 'refresh') void loadModules()
  else if (key === 'import') importOpen.value = true
  else if (key === 'export') exportOpen.value = true
  else if (key === 'ai') openAiCreate()
}

function hasSecondaryRecordAction(record: RuntimeRecord) {
  return lifecycleState.value !== 'ACTIVE'
    || recordAllowsAction(record, 'TRANSFER')
    || recordAllowsAction(record, 'CONVERT') && conversionTargetOptions.value.length > 0
    || recordAllowsAction(record, 'ARCHIVE')
    || recordAllowsAction(record, 'DELETE')
    || record.ownedByCurrentTenant && hasAction('SHARE')
}

const showRecordActions = computed(() => records.value.some((record) =>
  lifecycleState.value === 'ACTIVE' && actionVisibleForRecord(record) || hasSecondaryRecordAction(record)))

function runRecordMenu(key: string, record: RuntimeRecord) {
  if (key === 'transfer') void openTransfer(record)
  else if (key === 'convert') void openConversion(record)
  else if (key === 'archive') void openLifecycle(record, 'ARCHIVE')
  else if (key === 'delete') void openLifecycle(record, 'DELETE')
  else if (key === 'restore') void openLifecycle(record, 'RESTORE')
  else if (key === 'share') void openShare(record)
}

function runDetailMenu(key: string) {
  if (!detail.value) return
  if (key === 'print') openPrint()
  else runRecordMenu(key, detail.value)
}

function actionConfiguration(code: string) {
  const action = actions.value.find((item) => item.code === code)
  try { return JSON.parse(action?.configJson || '{}') as { confirmation?: boolean; confirmationText?: string; targetModuleCode?: string; targets?: Array<{ moduleCode: string }> } }
  catch { return {} }
}

const conversionTargetOptions = computed(() => {
  const action = actions.value.find((item) => item.code === 'CONVERT')
  const codes = conversionTargetCodes(action?.configJson)
  return codes.map((code) => ({ value: code, label: modules.value.find((item) => item.moduleCode === code)?.moduleName || code }))
})

function runAction(code: string, callback: () => void) {
  const config = actionConfiguration(code)
  if (!config.confirmation) return callback()
  Modal.confirm({ title: config.confirmationText || '确认执行此操作？', onOk: callback })
}

async function loadModules() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    modules.value = await api<RuntimeModuleCatalogItem[]>('/api/runtime/modules', {}, token.value)
    const requestedCode = props.initialModuleCode?.trim()
    const code = modules.value.some((item) => item.moduleCode === activeCode.value)
      ? activeCode.value : modules.value.some((item) => item.moduleCode === requestedCode)
        ? requestedCode : modules.value[0]?.moduleCode
    if (code) await selectModule(code, true)
    else {
      activeCode.value = ''
      configuration.value = undefined
      records.value = []
    }
  } catch (reason) {
    error.value = readable(reason)
  } finally {
    loading.value = false
  }
}

async function loadPeopleDirectory() {
  if (!token.value) return
  peopleDirectory.value = await api<SystemPeopleDirectory>('/api/system-directory', {}, token.value)
}

function applyRouteFilters() {
  const raw = Array.isArray(route.query.filters) ? route.query.filters[0] : route.query.filters
  if (typeof raw !== 'string' || !raw) return
  try {
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) return
    const allowedFields = new Set(filterFieldOptions.value.map(option => option.value))
    const allowedOperators = new Set(['EQ', 'NE', 'CONTAINS', 'GT', 'GTE', 'LT', 'LTE', 'EMPTY', 'NOT_EMPTY'])
    listFilters.value = parsed.filter((item): item is RuntimeListFilter => {
      if (!item || typeof item !== 'object' || Array.isArray(item)) return false
      const filter = item as Record<string, unknown>
      return typeof filter.fieldCode === 'string' && allowedFields.has(filter.fieldCode)
        && typeof filter.operator === 'string' && allowedOperators.has(filter.operator)
    }).map(item => ({ fieldCode: item.fieldCode, operator: item.operator,
      value: item.value === undefined || item.value === null ? '' : String(item.value) }))
  } catch { /* Ignore malformed URL state and keep the ordinary unfiltered list. */ }
}

async function selectModule(code: string, fromRoute = false) {
  activeCode.value = code
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
  await loadConfiguration()
  initializeColumns()
  await loadSavedViews()
  const preferred = savedViews.value.find((view) => view.defaultView)
  if (preferred) applySavedView(preferred, false)
  if (fromRoute) applyRouteFilters()
  await loadRecords()
}

function returnToDashboard() {
  if (dashboardReturn.value) void router.push(dashboardReturn.value)
}

async function loadConfiguration() {
  configuration.value = await api<RuntimeModuleConfiguration>(
    `/api/runtime/modules/${activeCode.value}/configuration`, {}, token.value)
  await loadCascadeOptions()
}

function fieldConfig(field: RuntimeField) {
  try { return JSON.parse(field.configJson || '{}') as { maxDepth?: number; allowIntermediate?: boolean; saveMode?: 'FINAL' | 'PATH' } }
  catch { return {} }
}

async function loadCascadeOptions() {
  Object.keys(cascadeOptions).forEach((code) => delete cascadeOptions[code])
  const cascadeFields = configuration.value?.configuration.fields
    .filter((field) => field.fieldType === 'CASCADE' && field.dictionaryId) || []
  await Promise.all(cascadeFields.map(async (field) => {
    const maximumDepth = Math.max(1, fieldConfig(field).maxDepth || 20)
    const loadLevel = async (parentId: number | undefined, depth: number): Promise<CascadeOption[]> => {
      const query = parentId ? `?parentId=${parentId}` : ''
      const dictionary = await api<RuntimeDictionary>(
        `/api/runtime/dictionaries/by-id/${field.dictionaryId}${query}`, {}, token.value)
      return Promise.all(dictionary.items.map(async (item) => {
        const children = depth < maximumDepth ? await loadLevel(item.id, depth + 1) : []
        return { value: item.id, label: item.label, ...(children.length ? { children } : {}) }
      }))
    }
    cascadeOptions[field.code] = await loadLevel(undefined, 1)
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

function cascadeEditValue(field: RuntimeField, value: unknown) {
  if (Array.isArray(value)) return value.map(Number)
  if (typeof value === 'number') return cascadePath(cascadeOptions[field.code] || [], value) || [value]
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

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
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
      `/api/runtime/modules/${activeCode.value}/records?${params}`, {}, token.value)
    records.value = result.records
    total.value = result.total
    selectedRowKeys.value = selectedRowKeys.value.filter((id) => result.records.some((record) => record.id === id))
    if (detailOpen.value && detail.value && !result.records.some((record) => record.id === detail.value?.id)) {
      detailOpen.value = false
      detail.value = undefined
    }
  } catch (reason) {
    error.value = readable(reason)
  } finally {
    loading.value = false
  }
}

function initializeColumns() {
  const defaults = ['title', ...listFields.value.slice(0, 2).map((field) => field.code), 'status']
  const allowed = new Set(availableColumns.value.map((column) => column.code))
  visibleFieldCodes.value = [...new Set(defaults)].filter((code) => allowed.has(code))
  fixedFieldCodes.value = visibleFieldCodes.value.includes('title') ? ['title'] : []
}

async function loadSavedViews() {
  savedViews.value = await api<RuntimeListView[]>(
    `/api/runtime/modules/${activeCode.value}/list-views`, {}, token.value)
}

function applySavedView(view: RuntimeListView, reload = true) {
  const allowed = new Set(availableColumns.value.map((column) => column.code))
  searchText.value = view.search || ''
  listFilters.value = view.filters.filter((filter) => allowed.has(filter.fieldCode))
  sortField.value = allowed.has(view.sortField) ? view.sortField : 'updatedAt'
  sortDirection.value = view.sortDirection
  visibleFieldCodes.value = view.visibleFieldCodes.filter((code) => allowed.has(code))
  if (!visibleFieldCodes.value.length) initializeColumns()
  fixedFieldCodes.value = view.fixedFieldCodes.filter((code) => visibleFieldCodes.value.includes(code))
  pageSize.value = view.pageSize
  activeViewCode.value = view.code
  page.value = 1
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
  saveViewSaving.value = true
  try {
    const code = activeSavedView.value?.code || `view_${Date.now().toString(36)}`
    const saved = await api<RuntimeListView>(`/api/runtime/modules/${activeCode.value}/list-views/${code}`, {
      method: 'PUT',
      body: JSON.stringify({
        name: viewForm.name.trim(), search: searchText.value.trim(), filters: listFilters.value,
        sortField: sortField.value, sortDirection: sortDirection.value,
        visibleFieldCodes: visibleFieldCodes.value, fixedFieldCodes: fixedFieldCodes.value,
        pageSize: pageSize.value, defaultView: viewForm.defaultView,
        ...(activeSavedView.value ? { expectedVersion: activeSavedView.value.version } : {}),
      }),
    }, token.value)
    await loadSavedViews()
    activeViewCode.value = saved.code
    saveViewOpen.value = false
    message.success('常用视图已保存并可在下次进入时恢复')
  } catch (reason) { message.error(readable(reason)) }
  finally { saveViewSaving.value = false }
}

function deleteCurrentView() {
  const current = activeSavedView.value
  if (!current) return
  Modal.confirm({
    title: `删除常用视图“${current.name}”？`,
    async onOk() {
      await api(`/api/runtime/modules/${activeCode.value}/list-views/${current.code}`, {
        method: 'DELETE', body: JSON.stringify({ expectedVersion: current.version }),
      }, token.value)
      activeViewCode.value = ''
      await loadSavedViews()
      message.success('常用视图已删除')
    },
  })
}

function addFilter() {
  listFilters.value.push({ fieldCode: filterFieldOptions.value[0]?.value || 'title', operator: 'CONTAINS', value: '' })
}

function applyListQuery() {
  page.value = 1
  filterOpen.value = false
  activeViewCode.value = ''
  void loadRecords()
}

function clearListQuery() {
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
      if (recordAllowsAction(record, 'DETAIL')) void openDetail(record)
    },
    onFocus: () => { focusedRowIndex.value = index ?? -1 },
    onKeydown: (event: KeyboardEvent) => {
      const current = index ?? 0
      if (event.key === 'Enter' && recordAllowsAction(record, 'DETAIL')) {
        event.preventDefault(); void openDetail(record); return
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
  if (column.field) return displayValue(column.field, record.fields[column.code])
  if (column.code === 'ownerMemberId') return ownerDisplayName(record)
  if (column.code === 'dataTenantName') return userFacingWorkspaceName(record.dataTenantName)
  if (column.code === 'createdAt' || column.code === 'updatedAt') return productDateTime(record[column.code])
  return record[column.code as 'title' | 'status' | 'createdAt' | 'updatedAt'] || '—'
}

function ownerDisplayName(record: RuntimeRecord) {
  if (!record.ownerMemberId) return '未分配'
  return peopleDirectory.value.people.find(person => person.systemMemberId === record.ownerMemberId)?.displayName
    || (record.ownerMemberId === systemContext.value?.memberId ? systemContext.value?.displayName || '我' : '其他负责人')
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

async function openDetail(record: RuntimeRecord) {
  try {
    const [recordDetail, recordTimeline, recordConversions, availablePrintTemplates] = await Promise.all([
      api<RuntimeRecord>(`/api/runtime/modules/${activeCode.value}/records/${record.id}`, {}, token.value),
      api<RuntimeRecordTimeline>(`/api/runtime/modules/${activeCode.value}/records/${record.id}/timeline`, {}, token.value),
      api<{ links: RuntimeRecordConversionLink[] }>(`/api/runtime/modules/${activeCode.value}/records/${record.id}/conversions`, {}, token.value),
      api<RuntimePrintTemplate[]>(`/api/print/runtime/modules/${activeCode.value}/templates`, {}, token.value).catch(() => []),
    ])
    detail.value = recordDetail
    if (!recordAttachmentFields.value.some(field => field.code === attachmentFieldCode.value)) {
      attachmentFieldCode.value = recordAttachmentFields.value[0]?.code || ''
    }
    timeline.value = recordTimeline.entries
    conversionLinks.value = recordConversions.links
    printTemplates.value = availablePrintTemplates
    detailOpen.value = true
  } catch (reason) {
    if (reason instanceof ApiError && ['PERMISSION_DENIED', 'RECORD_NOT_FOUND', 'TENANT_SHARE_NOT_ACTIVE',
      'TENANT_SHARE_ACTION_DENIED', 'RECORD_DATA_SCOPE_DENIED'].includes(reason.code)) {
      detailOpen.value = false
      detail.value = undefined
      await loadRecords()
      message.warning('记录已不可访问，详情已关闭并刷新列表')
      return
    }
    message.error(readable(reason))
  }
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

function openTransfer(record: RuntimeRecord) {
  transferRecord.value = record
  transferPreview.value = undefined
  Object.assign(transferForm, { ownerMemberId: record.ownerMemberId, departmentId: record.departmentId,
    participantMemberIds: [...record.participantMemberIds], reason: '' })
  transferOpen.value = true
}

async function previewTransfer() {
  if (!transferRecord.value || !transferForm.ownerMemberId || !transferForm.reason.trim()) {
    message.warning('请填写新负责人和转交原因')
    return
  }
  transferSaving.value = true
  try {
    transferPreview.value = await api<RuntimeRecordTransferPreview>(
      `/api/runtime/modules/${activeCode.value}/records/${transferRecord.value.id}/transfer-preview`,
      { method: 'POST', body: JSON.stringify({ version: transferRecord.value.version, ownerMemberId: transferForm.ownerMemberId, departmentId: transferForm.departmentId, participantMemberIds: transferParticipantIds(), reason: transferForm.reason.trim() }) }, token.value)
  } catch (reason) { message.error(readable(reason)) } finally { transferSaving.value = false }
}

async function submitTransfer() {
  if (!transferRecord.value || !transferPreview.value) return
  transferSaving.value = true
  try {
    const saved = await api<RuntimeRecord>(
      `/api/runtime/modules/${activeCode.value}/records/${transferRecord.value.id}/transfer`,
      { method: 'POST', body: JSON.stringify({ version: transferRecord.value.version, ownerMemberId: transferForm.ownerMemberId, departmentId: transferForm.departmentId, participantMemberIds: transferParticipantIds(), reason: transferForm.reason.trim() }) }, token.value)
    transferOpen.value = false
    detailOpen.value = false
    message.success('负责人已转交，列表已刷新')
    await loadRecords()
    await openDetail(saved)
  } catch (reason) { message.error(readable(reason)) } finally { transferSaving.value = false }
}

function transferParticipantIds() {
  return [...new Set(transferForm.participantMemberIds)]
}

function openConversion(record: RuntimeRecord) {
  conversionRecord.value = record
  conversionPreview.value = undefined
  conversionTargetCode.value = conversionTargetOptions.value[0]?.value || ''
  conversionOpen.value = true
}

async function previewConversion() {
  if (!conversionRecord.value || !conversionTargetCode.value) {
    message.warning('当前转化动作尚未配置目标模块')
    return
  }
  conversionSaving.value = true
  try {
    conversionPreview.value = await api<RuntimeRecordConversionPreview>(
      `/api/runtime/modules/${activeCode.value}/records/${conversionRecord.value.id}/conversion-preview`,
      { method: 'POST', body: JSON.stringify({ version: conversionRecord.value.version, targetModuleCode: conversionTargetCode.value }) }, token.value)
  } catch (reason) { message.error(readable(reason)) } finally { conversionSaving.value = false }
}

async function submitConversion() {
  if (!conversionRecord.value || !conversionPreview.value?.executable) return
  conversionSaving.value = true
  try {
    const result = await api<RuntimeRecordConversionExecution>(
      `/api/runtime/modules/${activeCode.value}/records/${conversionRecord.value.id}/convert`,
      { method: 'POST', body: JSON.stringify({ version: conversionRecord.value.version, targetModuleCode: conversionTargetCode.value }) }, token.value)
    conversionOpen.value = false
    detailOpen.value = false
    message.success('转化完成，正在打开目标记录')
    await selectModule(result.targetModuleCode)
    await openDetail(result.targetRecord)
  } catch (reason) { message.error(readable(reason)) } finally { conversionSaving.value = false }
}

async function openConversionLink(link: RuntimeRecordConversionLink) {
  await selectModule(link.moduleCode)
  const linked = records.value.find((record) => record.id === link.recordId)
    || await api<RuntimeRecord>(`/api/runtime/modules/${link.moduleCode}/records/${link.recordId}`, {}, token.value)
  await openDetail(linked)
}

function loadReferenceOptions() {
  const referenceFields = fields.value.filter((field) => field.fieldType === 'REFERENCE' && field.referenceModuleId)
  if (!referenceFields.length) return
  referenceLoading.value = true
  void Promise.all(referenceFields.map(async (field) => {
    const module = modules.value.find((item) => item.moduleId === field.referenceModuleId)
    if (!module) return
    const params = new URLSearchParams({ lifecycleState: 'ACTIVE', tenantScope: 'ALL', search: '', filters: '[]', sortField: 'updatedAt', sortDirection: 'DESC', page: '1', pageSize: '50' })
    const result = await api<RuntimeRecordList>(`/api/runtime/modules/${module.moduleCode}/records?${params}`, {}, token.value)
    referenceOptions[field.code] = result.records.map((record) => ({
      value: record.id,
      label: record.title,
      description: userFacingRecordNumber(record.recordNumber),
    }))
  })).catch(() => message.warning('部分关联记录暂时无法加载，可稍后重试。')).finally(() => { referenceLoading.value = false })
}

function openCreate() {
  editing.value = undefined
  clearFormErrors()
  Object.assign(form, {
    title: '', recordNumber: '', status: 'ACTIVE', ownerMemberId: systemContext.value?.memberId || undefined,
    departmentId: undefined, participantText: '', fields: {},
  })
  fields.value.forEach((field) => { form.fields[field.code] = field.fieldType === 'BOOLEAN' ? false : undefined })
  formSnapshot.value = serializeForm()
  restoreFormDraft()
  formOpen.value = true
  loadReferenceOptions()
}

function openEdit(record: RuntimeRecord) {
  editing.value = record
  clearFormErrors()
  Object.assign(form, {
    title: record.title,
    recordNumber: record.recordNumber || '',
    status: record.status,
    ownerMemberId: record.ownerMemberId,
    departmentId: record.departmentId,
    participantText: record.participantMemberIds.join(','),
    fields: Object.fromEntries(Object.entries(record.fields).map(([code, value]) => {
      const field = fields.value.find((item) => item.code === code)
      return [code, field?.fieldType === 'CASCADE' ? cascadeEditValue(field, value) : value]
    })),
  })
  formSnapshot.value = serializeForm()
  restoreFormDraft()
  formOpen.value = true
  detailOpen.value = false
  loadReferenceOptions()
}

async function changeLifecycleState() {
  page.value = 1
  detailOpen.value = false
  await loadRecords()
}

async function openLifecycle(record: RuntimeRecord, action: 'ARCHIVE' | 'DELETE' | 'RESTORE') {
  try {
    lifecycleAction.value = action
    lifecycleRecord.value = record
    lifecycleReason.value = ''
    lifecycleImpact.value = await api<RuntimeRecordLifecycleImpact>(
      `/api/runtime/modules/${activeCode.value}/records/${record.id}/lifecycle-impact?action=${action}`, {}, token.value)
    lifecycleOpen.value = true
  } catch (reason) {
    message.error(readable(reason))
  }
}

async function submitLifecycle() {
  if (!lifecycleRecord.value || !lifecycleReason.value.trim()) {
    message.warning('请填写操作原因')
    return
  }
  lifecycleSaving.value = true
  try {
    const action = lifecycleAction.value.toLowerCase()
    await api<RuntimeRecord>(
      `/api/runtime/modules/${activeCode.value}/records/${lifecycleRecord.value.id}/${action}`,
      { method: 'POST', body: JSON.stringify({ reason: lifecycleReason.value.trim(), version: lifecycleRecord.value.version }) },
      token.value,
    )
    lifecycleOpen.value = false
    detailOpen.value = false
    message.success(lifecycleAction.value === 'ARCHIVE' ? '记录已归档' : lifecycleAction.value === 'DELETE' ? '记录已移入回收站' : '记录已恢复')
    await loadRecords()
  } catch (reason) {
    message.error(readable(reason))
  } finally {
    lifecycleSaving.value = false
  }
}

function options(field: RuntimeField) {
  try {
    const config = JSON.parse(field.configJson) as { options?: Array<{ value?: string; code?: string; label?: string; status?: string; disabled?: boolean }> }
    return (config.options || []).filter((option) => option.status !== 'DISABLED' && !option.disabled)
      .map((option) => ({ value: option.value || option.code, label: option.label || option.value || option.code }))
  } catch { return [] }
}

function participantIds() {
  return [...new Set(form.participantText.split(',').map((item) => Number(item.trim())).filter((item) => Number.isInteger(item) && item > 0))]
}

function normalizedFields() {
  return Object.fromEntries(Object.entries(form.fields).filter(([, value]) => value !== undefined && value !== null && value !== ''))
}

function numericFieldValue(fieldCode: string) {
  const value = form.fields[fieldCode]
  return typeof value === 'number' ? value : undefined
}

async function saveRecord() {
  if (!form.title.trim()) {
    formError.value = '标题不能为空'
    await nextTick()
    document.querySelector<HTMLElement>('#runtime-record-title')?.focus()
    return
  }
  saving.value = true
  clearFormErrors()
  try {
    const body = {
      title: form.title,
      recordNumber: form.recordNumber || null,
      status: form.status,
      ownerMemberId: form.ownerMemberId || null,
      departmentId: form.departmentId || null,
      participantMemberIds: participantIds(),
      fields: normalizedFields(),
      ...(editing.value ? { version: editing.value.version } : {}),
    }
    const path = editing.value
      ? `/api/runtime/modules/${activeCode.value}/records/${editing.value.id}`
      : `/api/runtime/modules/${activeCode.value}/records`
    const wasEditing = Boolean(editing.value)
    const saved = await api<RuntimeRecord>(path, {
      method: wasEditing ? 'PUT' : 'POST', body: JSON.stringify(body),
    }, token.value)
    localStorage.removeItem(formDraftKey())
    formSnapshot.value = serializeForm()
    formOpen.value = false
    message.success(wasEditing ? '业务数据已更新' : '业务数据已创建')
    page.value = 1
    await loadRecords()
    if (wasEditing) await openDetail(saved)
  } catch (reason) {
    applyFormError(reason)
    message.error(readable(reason))
  } finally {
    saving.value = false
  }
}

function clearFormErrors() {
  formError.value = ''
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
}

function applyFormError(reason: unknown) {
  formError.value = readable(reason)
  if (!(reason instanceof ApiError) || reason.code !== 'FIELD_VALIDATION_FAILED') return
  reason.message.split('；').forEach((item) => {
    const separator = item.indexOf(':')
    if (separator <= 0) return
    const code = item.slice(0, separator).trim()
    const message = item.slice(separator + 1).trim()
    if (code) fieldErrors[code] = message
  })
  const first = Object.keys(fieldErrors)[0]
  if (first) void nextTick(() => {
    const container = document.querySelector<HTMLElement>(`[data-runtime-field="${first}"]`)
    container?.scrollIntoView({ behavior: 'smooth', block: 'center' })
    container?.querySelector<HTMLElement>('input,textarea,[tabindex]')?.focus()
  })
}

function serializeForm(forBrowserDraft = false) {
  const secretCodes = new Set(fields.value.filter((field) => field.fieldType === 'SECRET').map((field) => field.code))
  return JSON.stringify({
    title: form.title, recordNumber: form.recordNumber, status: form.status,
    ownerMemberId: form.ownerMemberId, departmentId: form.departmentId,
    participantText: form.participantText,
    fields: Object.fromEntries(Object.entries(form.fields)
      .filter(([code]) => !forBrowserDraft || !secretCodes.has(code))),
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
    if (!draft.value) return
    Object.assign(form, draft.value, { fields: { ...draft.value.fields } })
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
    content: '草稿已经自动保存在当前浏览器。确认离开后可以再次进入此记录继续填写。',
    okText: '保留草稿并离开', cancelText: '继续编辑',
    onOk: () => { formOpen.value = false },
  })
}

function beforeWindowUnload(event: BeforeUnloadEvent) {
  if (!formDirty.value) return
  event.preventDefault()
  event.returnValue = ''
}

function displayValue(field: RuntimeField, value: unknown) {
  if (value === undefined || value === null || value === '') return '—'
  if (value === '***') return '••••（已脱敏）'
  if (value === '[已散列]') return '不可回显（已散列）'
  if (field.fieldType === 'BOOLEAN') return value ? '是' : '否'
  if (['SINGLE_SELECT', 'STATUS'].includes(field.fieldType)) return options(field).find((item) => item.value === value)?.label || String(value)
  if (field.fieldType === 'CASCADE') {
    const selected = Array.isArray(value) ? value.map(Number)
      : typeof value === 'number' ? (cascadePath(cascadeOptions[field.code] || [], value) || [value]) : []
    return cascadeLabels(cascadeOptions[field.code] || [], selected).join(' / ') || String(value)
  }
  if (field.fieldType === 'REFERENCE') {
    return referenceOptions[field.code]?.find((item) => item.value === Number(value))?.label || '关联记录'
  }
  if (field.fieldType === 'MEMBER') {
    return peopleDirectory.value.people.find((item) => item.tenantMemberId === Number(value))?.displayName || '组织成员'
  }
  if (field.fieldType === 'DEPARTMENT') {
    return peopleDirectory.value.departments.find((item) => item.id === Number(value))?.name || '组织部门'
  }
  return String(value)
}

function timelineValue(value: unknown) {
  if (value === undefined || value === null || value === '') return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  return String(value)
}

function timelineFieldValue(fieldCode: string, value: unknown) {
  const field = fields.value.find((item) => item.code === fieldCode)
  return field ? displayValue(field, value) : timelineValue(value)
}

async function copyFieldValue(field: RuntimeField, value: unknown) {
  if (value === undefined || value === null || value === '' || value === '***' || value === '[已散列]') {
    message.warning('当前字段没有可复制的明文值')
    return
  }
  try {
    await navigator.clipboard.writeText(String(value))
    message.success(`${field.name}已复制`)
  } catch { message.error('浏览器未允许写入剪贴板') }
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
  if (activeCode.value !== command.moduleCode) await selectModule(command.moduleCode)
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
  localStorage.setItem(formDraftKey(), JSON.stringify({ value: JSON.parse(serializeForm(true)), updatedAt: new Date().toLocaleString() }))
}, { deep: true })

watch(() => route.query.recordId, () => { void openRouteRecordDetail() })

onMounted(() => {
  window.addEventListener('beforeunload', beforeWindowUnload)
  void (async () => {
    await Promise.all([loadModules(), loadPeopleDirectory()])
    await openRouteRecordDetail()
  })()
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeWindowUnload)
  clearPrintPreview()
})
onBeforeRouteLeave(() => !formDirty.value || window.confirm('当前表单有未提交修改，草稿已自动保存。确认离开？'))
defineExpose({ refresh: loadModules, executeCommand })
</script>

<template>
  <ProductPage class="runtime-page" :data-refresh-key="props.refreshKey">
    <ProductPageHeader kicker="业务工作区" :title="activeModule?.moduleName || '业务模块'" description="查看、筛选并维护当前模块中的业务记录。">
      <template #actions>
        <a-dropdown>
          <a-button><MoreOutlined />更多</a-button>
          <template #overlay><a-menu @click="runPageUtility(String($event.key))">
            <a-menu-item key="refresh"><ReloadOutlined />刷新数据</a-menu-item>
            <a-menu-item v-if="hasAction('IMPORT') && lifecycleState === 'ACTIVE'" key="import"><ImportOutlined />导入数据</a-menu-item>
            <a-menu-item v-if="hasAction('EXPORT')" key="export"><ExportOutlined />导出数据</a-menu-item>
            <a-menu-item v-if="canUseAi && hasAction('CREATE') && lifecycleState === 'ACTIVE'" key="ai"><RobotOutlined />使用智能助手创建</a-menu-item>
          </a-menu></template>
        </a-dropdown>
      </template>
      <template #primary><a-button v-if="hasAction('CREATE') && lifecycleState === 'ACTIVE'" type="primary" @click="runAction('CREATE', openCreate)"><PlusOutlined />新建{{ activeModule?.moduleName }}</a-button></template>
    </ProductPageHeader>
    <a-alert v-if="dashboardReturn" type="info" show-icon class="section-alert"
      message="当前列表来自经营看板下钻" :description="listFilters.length ? `已应用 ${listFilters.length} 个看板筛选条件。` : '看板筛选字段在当前权限范围内不可用，已显示普通列表。'">
      <template #action><a-button type="link" @click="returnToDashboard">返回经营看板</a-button></template>
    </a-alert>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert">
      <template #action><a-button size="small" @click="loadRecords">保留条件重试</a-button></template>
    </a-alert>
    <nav v-if="groupedModules.length > 1" class="runtime-group-tabs" aria-label="业务模块组">
      <button v-for="group in groupedModules" :key="group.id" type="button" :class="{ active: activeGroup?.id === group.id }" @click="selectGroup(group.id)">
        <strong>{{ group.name }}</strong><small>{{ group.modules.length }} 个模块</small>
      </button>
    </nav>
    <div class="runtime-layout">
      <aside class="runtime-modules panel-card">
        <div class="panel-title"><strong>{{ activeGroup?.name || '业务模块' }}</strong><span>{{ activeGroup?.modules.length || 0 }}</span></div>
        <section v-if="activeGroup" class="runtime-group">
          <button v-for="module in activeGroup.modules" :key="module.moduleId" type="button"
            :class="['module-tree-item', { active: activeCode === module.moduleCode }]" @click="selectModule(module.moduleCode)">
            <span>{{ (module.menuIcon || module.menuName || module.moduleName).slice(0, 1).toUpperCase() }}</span><strong>{{ module.menuName || module.moduleName }}</strong>
          </button></section>
        <a-empty v-if="!modules.length && !loading" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="没有可见的已发布模块" />
      </aside>
      <main class="record-panel panel-card">
        <template v-if="activeCode">
          <div class="record-query-bar">
            <a-input-search v-model:value="searchText" allow-clear placeholder="搜索标题、编号和业务字段" class="record-search" @search="applyListQuery"><template #prefix><SearchOutlined /></template></a-input-search>
            <a-radio-group v-model:value="lifecycleState" button-style="solid" size="small" @change="changeLifecycleState">
              <a-radio-button value="ACTIVE">正常</a-radio-button><a-radio-button value="ARCHIVED">已归档</a-radio-button><a-radio-button value="DELETED">回收站</a-radio-button>
            </a-radio-group>
            <a-button :class="{ 'record-filter-active': listFilters.length }" @click="filterOpen = true"><FilterOutlined />筛选<span v-if="listFilters.length">（{{ listFilters.length }}）</span></a-button>
            <a-select v-if="savedViews.length" :value="activeViewCode || undefined" allow-clear placeholder="常用视图" style="width:160px" :options="savedViews.map(view => ({ value: view.code, label: `${view.name}${view.defaultView ? ' · 默认' : ''}` }))" @change="selectSavedView" />
            <a-popover v-model:open="columnOpen" trigger="click" placement="bottomRight">
              <template #content><div class="runtime-column-settings"><p>调整列表显示方式；最多固定三列。</p>
                <a-button type="link" block @click="columnOpen = false; openSaveView()"><SaveOutlined />保存当前视图</a-button>
                <div v-for="column in availableColumns" :key="column.code" :draggable="visibleFieldCodes.includes(column.code)" @dragstart="draggingColumn = column.code" @dragover.prevent @drop.prevent="moveColumn(column.code)">
                  <span class="column-drag">⋮⋮</span><a-checkbox :checked="visibleFieldCodes.includes(column.code)" @change="toggleColumn(column.code, $event.target.checked)">{{ column.label }}</a-checkbox>
                  <a-checkbox :checked="fixedFieldCodes.includes(column.code)" :disabled="!visibleFieldCodes.includes(column.code) || (!fixedFieldCodes.includes(column.code) && fixedFieldCodes.length >= 3)" @change="toggleFixedColumn(column.code, $event.target.checked)">固定</a-checkbox>
                </div>
              </div></template>
              <a-button aria-label="列表设置" title="调整列表字段与保存视图"><SettingOutlined /></a-button>
            </a-popover>
          </div>
          <div class="record-summary"><div><a-segmented v-if="showTenantScope" v-model:value="tenantScope" size="small" :options="[{ label: '全部', value: 'ALL' }, { label: '当前工作空间', value: 'OWN' }, { label: '共享给我', value: 'SHARED' }]" @change="page = 1; activeViewCode = ''; loadRecords()" /><strong>{{ loading ? '正在加载…' : `共 ${total} 条记录` }}</strong><small v-if="queryCustomized">{{ querySummary }}</small></div></div>
          <div v-if="selectedRowKeys.length" class="runtime-batch-bar"><strong>已选择 {{ selectedRowKeys.length }} 条</strong><span>可以导出已选记录；修改、归档等操作请在对应记录中确认。</span><a-button v-if="hasAction('EXPORT')" size="small" @click="exportOpen = true">导出已选</a-button><a-button type="link" size="small" @click="selectedRowKeys = []">清除选择</a-button></div>
          <a-table class="record-table" :data-source="records" :loading="loading" row-key="id" :pagination="false" :row-selection="hasAction('EXPORT') ? rowSelection : undefined" :custom-row="rowHandlers" :scroll="{ y: pageSize >= 100 ? 560 : undefined }" :virtual="pageSize >= 100">
            <a-table-column v-for="column in displayedColumns" :key="column.code" :title="column.label" :width="column.width" :fixed="fixedFieldCodes.includes(column.code) ? 'left' : undefined">
              <template #default="{ record }"><template v-if="column.code === 'title'"><strong class="record-title">{{ record.title }}</strong><small>{{ userFacingRecordNumber(record.recordNumber, '未设置业务编号') }} · {{ userFacingWorkspaceName(record.dataTenantName) }}</small></template>
                <a-tag v-else-if="column.code === 'dataTenantName'" :color="record.shared ? 'orange' : 'blue'">{{ record.shared ? `${userFacingWorkspaceName(record.dataTenantName)}共享` : '当前工作空间' }}</a-tag>
                <ProductStatusTag v-else-if="column.code === 'status'" :status="record.status" /><template v-else>{{ columnValue(column, record) }}</template></template>
            </a-table-column>
            <a-table-column v-if="showRecordActions" title="操作" fixed="right" :width="140"><template #default="{ record }"><a-button v-if="lifecycleState === 'ACTIVE' && actionVisibleForRecord(record)" type="link" size="small" @click.stop="runAction('UPDATE', () => openEdit(record))"><EditOutlined />编辑</a-button><a-dropdown v-if="hasSecondaryRecordAction(record)" trigger="click"><a-button type="link" size="small" @click.stop><MoreOutlined />更多</a-button><template #overlay><a-menu @click="runRecordMenu(String($event.key), record)"><a-menu-item v-if="lifecycleState === 'ACTIVE' && recordAllowsAction(record, 'TRANSFER')" key="transfer">转交负责人</a-menu-item><a-menu-item v-if="lifecycleState === 'ACTIVE' && recordAllowsAction(record, 'CONVERT') && conversionTargetOptions.length" key="convert">转化记录</a-menu-item><a-menu-item v-if="record.ownedByCurrentTenant && hasAction('SHARE')" key="share">共享记录</a-menu-item><a-menu-item v-if="lifecycleState === 'ACTIVE' && recordAllowsAction(record, 'ARCHIVE')" key="archive">归档记录</a-menu-item><a-menu-item v-if="lifecycleState !== 'ACTIVE' && recordAllowsAction(record, 'RESTORE')" key="restore">恢复记录</a-menu-item><a-menu-divider /><a-menu-item v-if="lifecycleState !== 'DELETED' && recordAllowsAction(record, 'DELETE')" key="delete" danger>删除记录</a-menu-item></a-menu></template></a-dropdown></template></a-table-column>
          </a-table>
          <div class="mobile-record-list" aria-label="业务记录卡片列表" :aria-busy="loading">
            <button v-for="record in records" :key="record.id" type="button" :data-status-tone="statusTone(record.status)" @click="openDetail(record)">
              <header><span><strong>{{ record.title }}</strong><small>{{ userFacingRecordNumber(record.recordNumber, '未设置业务编号') }} · {{ userFacingWorkspaceName(record.dataTenantName) }}</small></span><ProductStatusTag :status="record.status" /></header>
              <dl>
                <div v-for="column in displayedColumns.filter(item => !['title', 'status', 'dataTenantName'].includes(item.code)).slice(0, 3)" :key="column.code">
                  <dt>{{ column.label }}</dt><dd>{{ columnValue(column, record) }}</dd>
                </div>
              </dl>
              <footer><span>{{ record.shared ? `${userFacingWorkspaceName(record.dataTenantName)}共享` : '当前工作空间记录' }}</span><strong>查看详情 →</strong></footer>
            </button>
            <a-empty v-if="!loading && !records.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前条件下没有记录" />
          </div>
          <div class="record-pagination"><a-pagination v-model:current="page" v-model:page-size="pageSize" :total="total" show-size-changer @change="loadRecords" /></div>
        </template>
        <a-empty v-else description="发布模块后，业务页面会在这里出现" />
      </main>
    </div>

    <a-drawer v-model:open="filterOpen" title="高级筛选与服务端排序" width="520" :mask-closable="false">
      <a-alert type="info" show-icon class="section-alert" message="所有条件均由服务端执行权限校验" description="不可读或已脱敏字段不能用于筛选、排序或保存视图。查询失败不会清空当前条件。" />
      <div class="runtime-filter-list">
        <div v-for="(condition, index) in listFilters" :key="index" class="runtime-filter-row">
          <a-select v-model:value="condition.fieldCode" show-search :options="filterFieldOptions" />
          <a-select v-model:value="condition.operator" :options="[
            { value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' }, { value: 'CONTAINS', label: '包含' },
            { value: 'GT', label: '大于' }, { value: 'GTE', label: '大于等于' }, { value: 'LT', label: '小于' },
            { value: 'LTE', label: '小于等于' }, { value: 'EMPTY', label: '为空' }, { value: 'NOT_EMPTY', label: '不为空' },
          ]" />
          <a-input v-if="!['EMPTY', 'NOT_EMPTY'].includes(condition.operator)" v-model:value="condition.value" placeholder="条件值" />
          <a-button danger type="text" @click="listFilters.splice(index, 1)">移除</a-button>
        </div>
        <a-button block type="dashed" @click="addFilter"><PlusOutlined />增加条件</a-button>
      </div>
      <a-divider>排序</a-divider>
      <div class="form-grid"><a-form-item label="排序字段"><a-select v-model:value="sortField" show-search :options="sortFieldOptions" /></a-form-item><a-form-item label="方向"><a-radio-group v-model:value="sortDirection" button-style="solid"><a-radio-button value="ASC">升序</a-radio-button><a-radio-button value="DESC">降序</a-radio-button></a-radio-group></a-form-item></div>
      <template #footer><div class="drawer-footer"><a-button @click="clearListQuery">清除条件</a-button><a-button @click="filterOpen = false">取消</a-button><a-button type="primary" @click="applyListQuery">应用并查询</a-button></div></template>
    </a-drawer>

    <a-modal v-model:open="saveViewOpen" :title="activeSavedView ? '更新常用视图' : '保存常用视图'" :confirm-loading="saveViewSaving" ok-text="保存" @ok="saveCurrentView">
      <a-form layout="vertical"><a-form-item label="视图名称" required><a-input v-model:value="viewForm.name" :maxlength="80" placeholder="例如：本月重点客户" /></a-form-item><a-form-item label="进入模块时默认恢复"><a-switch v-model:checked="viewForm.defaultView" /></a-form-item></a-form>
      <a-alert type="info" show-icon message="将保存当前搜索、组合筛选、排序、列顺序、固定列和分页大小。" />
      <template v-if="activeSavedView" #footer><div class="drawer-footer"><a-button danger @click="deleteCurrentView">删除视图</a-button><span></span><a-button @click="saveViewOpen = false">取消</a-button><a-button type="primary" :loading="saveViewSaving" @click="saveCurrentView">更新视图</a-button></div></template>
    </a-modal>

    <a-drawer v-model:open="detailOpen" :title="detail?.title" :width="assistantOpen ? 'min(980px, 100vw)' : 'min(520px, 100vw)'" class="detail-drawer">
      <template #extra><a-button v-if="detail && canUseAi" @click="assistantOpen = !assistantOpen"><RobotOutlined />{{ assistantOpen ? '收起助手' : '询问当前记录' }}</a-button></template>
      <template v-if="detail"><div :class="['record-detail-shell', { 'record-detail-shell--assistant': assistantOpen && canUseAi }]"><section class="record-detail-main"><div class="detail-meta"><span>{{ userFacingRecordNumber(detail.recordNumber, '未设置业务编号') }}</span><ProductStatusTag :status="detail.status" /><a-tag :color="detail.shared ? 'orange' : 'blue'">数据归属：{{ userFacingWorkspaceName(detail.dataTenantName) }}</a-tag><span v-if="detail.shared" class="detail-share-status">共享状态：<ProductStatusTag :status="detail.shareStatus" /></span></div>
        <a-tabs class="detail-tabs-runtime">
          <a-tab-pane key="basic" tab="概览"><a-descriptions :column="1" bordered size="small"><a-descriptions-item v-for="field in detailValueFields" :key="field.code" :label="field.name"><span class="detail-field-value">{{ displayValue(field, detail.fields[field.code]) }}<a-button type="link" size="small" @click="copyFieldValue(field, detail.fields[field.code])">复制</a-button></span></a-descriptions-item>
            <a-descriptions-item label="负责人">{{ ownerDisplayName(detail) }}</a-descriptions-item><a-descriptions-item label="相关人">{{ detail.participantMemberIds.length ? `${detail.participantMemberIds.length} 位相关人` : '—' }}</a-descriptions-item><a-descriptions-item label="创建时间">{{ productDateTime(detail.createdAt) }}</a-descriptions-item><a-descriptions-item label="更新时间">{{ productDateTime(detail.updatedAt) }}</a-descriptions-item></a-descriptions></a-tab-pane>
          <a-tab-pane key="relations" tab="关联"><a-list v-if="conversionLinks.length" size="small" bordered :data-source="conversionLinks"><template #renderItem="{ item }"><a-list-item><a-button type="link" @click="openConversionLink(item)">{{ item.direction === 'SOURCE' ? '来源' : '目标' }} · {{ item.moduleName }} · {{ item.recordTitle }}</a-button><span>{{ productDateTime(item.convertedAt) }}</span></a-list-item></template></a-list><a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无有权查看的关联数据" /></a-tab-pane>
          <a-tab-pane v-if="recordAttachmentFields.length && canViewAttachments" key="attachments" tab="附件"><a-select v-if="recordAttachmentFields.length > 1" v-model:value="attachmentFieldCode" :options="recordAttachmentFields.map(field => ({ value: field.code, label: field.name }))" style="width:100%;margin-bottom:12px" placeholder="选择附件分类" /><BusinessAttachmentsPanel v-if="recordAttachmentEndpoint" :endpoint="recordAttachmentEndpoint" :writable="recordAttachmentWritable" :title="selectedAttachmentField?.name || '附件'" description="附件按当前字段权限展示，并记录到业务时间线。" compact @changed="detail && openDetail(detail)" /></a-tab-pane>
          <a-tab-pane key="timeline" tab="历史"><a-timeline v-if="timeline.length"><a-timeline-item v-for="entry in timeline" :key="entry.eventId">
              <div class="timeline-heading"><strong>{{ entry.label }}</strong><span>{{ productDateTime(entry.occurredAt) }}</span></div><p>{{ entry.actorDisplayName }}<template v-if="entry.summary"> · {{ entry.summary }}</template></p>
              <ul v-if="entry.changes.length" class="timeline-changes"><li v-for="change in entry.changes" :key="`${entry.eventId}-${change.fieldCode}`"><strong>{{ change.fieldName }}</strong>：<template v-if="change.masked">无查看权限</template><template v-else>{{ timelineFieldValue(change.fieldCode, change.beforeValue) }} → {{ timelineFieldValue(change.fieldCode, change.afterValue) }}</template></li></ul>
            </a-timeline-item></a-timeline><a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无变更记录" /></a-tab-pane>
        </a-tabs></section><AiContextAssistantPanel v-if="assistantOpen && canUseAi" :key="`${activeCode}-${detail.id}`" :module-code="activeCode" :module-name="activeModule?.moduleName || activeCode" :record="detail" :visible-fields="detailValueFields" :modules="modules" @business-written="handleAiBusinessWritten" /></div></template>
      <template #footer><div class="drawer-footer"><a-button @click="detailOpen = false">关闭</a-button><a-dropdown v-if="detail" trigger="click"><a-button><MoreOutlined />更多操作</a-button><template #overlay><a-menu @click="runDetailMenu(String($event.key))"><a-menu-item v-if="printTemplates.length" key="print">打印</a-menu-item><a-menu-item v-if="detail.ownedByCurrentTenant && hasAction('SHARE')" key="share">共享</a-menu-item><a-menu-item v-if="!detail.archived && !detail.deleted && recordAllowsAction(detail, 'TRANSFER')" key="transfer">转交</a-menu-item><a-menu-item v-if="!detail.archived && !detail.deleted && recordAllowsAction(detail, 'CONVERT') && conversionTargetOptions.length" key="convert">转化</a-menu-item><a-menu-item v-if="!detail.archived && !detail.deleted && recordAllowsAction(detail, 'ARCHIVE')" key="archive">归档</a-menu-item><a-menu-item v-if="(detail.archived || detail.deleted) && recordAllowsAction(detail, 'RESTORE')" key="restore">恢复</a-menu-item><a-menu-divider /><a-menu-item v-if="!detail.deleted && recordAllowsAction(detail, 'DELETE')" key="delete" danger>删除</a-menu-item></a-menu></template></a-dropdown><a-button v-if="detail && !detail.archived && !detail.deleted && actionVisibleForRecord(detail)" type="primary" @click="openEdit(detail)">编辑</a-button></div></template>
    </a-drawer>

    <a-modal v-model:open="printOpen" title="记录打印与 PDF 预览" width="920" :footer="null" @cancel="clearPrintPreview">
      <div class="print-runtime-toolbar"><a-select v-model:value="selectedPrintTemplateId" style="min-width:320px" :options="printTemplates.map(item => ({ value: item.templateId, label: `${item.name} · v${item.versionNumber} · ${item.pageSize}` }))" /><a-button type="primary" :loading="printing" @click="generatePrint"><PrinterOutlined />按发布版本生成</a-button><a-button :disabled="!printPreviewUrl" @click="openOriginalPrint">打开原始 PDF</a-button><a-button :disabled="!printJob" @click="downloadPrint"><ExportOutlined />下载 PDF</a-button></div>
      <a-alert v-if="printJob" type="success" show-icon :message="`打印文件已生成，共 ${printJob.pageCount} 页`" :description="`使用模板版本 v${printJob.templateVersionNumber} · 完成于 ${productDateTime(printJob.finishedAt)}`" />
      <section v-if="printJob?.pages.length" class="print-runtime-preview" aria-label="PDF 内容在线预览">
        <a-tag color="blue">PDF 内容预览 · 与下载文件使用同一打印任务快照</a-tag>
        <div class="print-page-stack">
          <article v-for="page in printJob.pages" :key="page.pageNumber" :class="['print-page', `print-page--${selectedPrintTemplate?.orientation.toLowerCase() || 'portrait'}`, `print-page--${selectedPrintTemplate?.pageSize.toLowerCase() || 'a4'}`]">
            <header><h2>{{ printJob.templateName }}</h2><p>{{ page.header }}</p><small>{{ page.recordNumber || '业务记录' }} · {{ page.recordTitle }}</small></header>
            <dl><div v-for="field in page.fields" :key="`${page.pageNumber}-${field.code}`" :class="{ detail: field.detail }"><dt>{{ field.detail ? '明细 · ' : '' }}{{ field.name }}</dt><dd>{{ field.value }}</dd></div></dl>
            <div v-if="page.signatureLabel" class="print-signature">{{ page.signatureLabel }}：________________</div>
            <footer><span>{{ page.footer }}</span><b>第 {{ page.pageNumber }} / {{ page.pageCount }} 页</b></footer>
          </article>
        </div>
      </section>
      <a-empty v-else description="选择已发布模板后生成 PDF；系统会在此刻重新校验记录和字段权限" />
    </a-modal>
    <a-drawer :open="formOpen" :title="editing ? `编辑${activeModule?.moduleName}` : `新建${activeModule?.moduleName}`" width="560" :mask-closable="false" :keyboard="false" @close="requestCloseForm">
      <a-alert v-if="formError" type="error" show-icon :message="formError" class="section-alert" />
      <a-alert v-else-if="formDraftRestored" type="info" show-icon message="已恢复自动保存的草稿" class="section-alert" />
      <div class="form-change-state"><a-tag :color="formDirty ? 'orange' : 'green'">{{ formDirty ? '有未提交修改 · 已自动保存草稿' : '尚未修改' }}</a-tag></div>
      <a-form layout="vertical"><div class="form-grid"><a-form-item label="标题" required><a-input id="runtime-record-title" v-model:value="form.title" /></a-form-item><a-form-item label="业务编号"><a-input v-model:value="form.recordNumber" /></a-form-item></div>
        <a-divider>模块字段</a-divider>
        <a-form-item v-for="field in formValueFields" :key="field.code" :label="field.name" :required="field.required" :data-runtime-field="field.code"
          :validate-status="fieldErrors[field.code] ? 'error' : undefined" :help="fieldErrors[field.code]">
          <a-textarea v-if="field.fieldType === 'MULTILINE_TEXT'" v-model:value="form.fields[field.code]" :rows="4" />
          <a-input-number v-else-if="['NUMBER', 'MONEY'].includes(field.fieldType)" v-model:value="form.fields[field.code]" style="width:100%" />
          <a-date-picker v-else-if="field.fieldType === 'DATE'" v-model:value="form.fields[field.code]" value-format="YYYY-MM-DD" style="width:100%" />
          <a-date-picker v-else-if="field.fieldType === 'DATETIME'" v-model:value="form.fields[field.code]" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
          <a-select v-else-if="['SINGLE_SELECT', 'STATUS'].includes(field.fieldType)" v-model:value="form.fields[field.code]" :options="options(field)" allow-clear />
          <a-cascader v-else-if="field.fieldType === 'CASCADE'" v-model:value="form.fields[field.code]" :options="cascadeOptions[field.code] || []"
            :change-on-select="Boolean(fieldConfig(field).allowIntermediate)" allow-clear show-search placeholder="请选择级联项" />
          <a-switch v-else-if="field.fieldType === 'BOOLEAN'" v-model:checked="form.fields[field.code]" />
          <PersonSelect v-else-if="field.fieldType === 'MEMBER'" :model-value="numericFieldValue(field.code)" :people="peopleDirectory.people" value-key="tenantMemberId" placeholder="搜索姓名、岗位或部门" @update:model-value="form.fields[field.code] = $event" />
          <DepartmentSelect v-else-if="field.fieldType === 'DEPARTMENT'" :model-value="numericFieldValue(field.code)" :departments="peopleDirectory.departments" @update:model-value="form.fields[field.code] = $event" />
          <a-select v-else-if="field.fieldType === 'REFERENCE'" v-model:value="form.fields[field.code]" show-search allow-clear :loading="referenceLoading" :filter-option="(input: string, option: { label?: string; description?: string }) => `${option.label || ''} ${option.description || ''}`.toLowerCase().includes(input.toLowerCase())" :options="referenceOptions[field.code] || []" placeholder="搜索并选择业务记录"><template #option="{ label, description }"><span class="reference-option"><strong>{{ label }}</strong><small>{{ description }}</small></span></template></a-select>
          <a-input v-else-if="field.fieldType === 'PHONE'" v-model:value="form.fields[field.code]" inputmode="tel" placeholder="请输入手机号" />
          <a-input v-else v-model:value="form.fields[field.code]" />
        </a-form-item>
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="requestCloseForm">取消</a-button><a-button type="primary" :loading="saving" @click="saveRecord">保存</a-button></div></template>
    </a-drawer>
    <a-modal v-model:open="lifecycleOpen" :title="lifecycleAction === 'ARCHIVE' ? '确认归档记录' : lifecycleAction === 'DELETE' ? '确认删除记录' : '确认恢复记录'"
      :confirm-loading="lifecycleSaving" ok-text="确认执行" cancel-text="取消" @ok="submitLifecycle">
      <a-alert v-if="lifecycleImpact" type="warning" show-icon
        :message="`${lifecycleAction === 'ARCHIVE' ? '归档' : lifecycleAction === 'DELETE' ? '删除' : '恢复'}“${lifecycleRecord?.title}”`"
        class="section-alert">
        <template #description>{{ lifecycleImpact.warning }} 影响范围：出向关联 {{ lifecycleImpact.outgoingRelationCount }} 条，入向关联 {{ lifecycleImpact.incomingRelationCount }} 条，相关人 {{ lifecycleImpact.participantCount }} 人。</template>
      </a-alert>
      <a-form layout="vertical"><a-form-item label="操作原因" required><a-textarea v-model:value="lifecycleReason" :rows="3" :maxlength="1000" show-count /></a-form-item></a-form>
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
    <a-modal v-model:open="transferOpen" title="转交负责人" :footer="null" :mask-closable="false">
      <a-form layout="vertical"><div class="form-grid"><a-form-item label="新负责人" required><PersonSelect v-model="transferForm.ownerMemberId" :people="peopleDirectory.people" placeholder="搜索姓名、岗位或部门" @change="transferPreview = undefined" /></a-form-item><a-form-item label="负责部门"><DepartmentSelect v-model="transferForm.departmentId" :departments="peopleDirectory.departments" @change="transferPreview = undefined" /></a-form-item></div><a-form-item label="相关成员"><PersonSelect v-model="transferForm.participantMemberIds" multiple :people="peopleDirectory.people" placeholder="搜索并选择协作成员" @change="transferPreview = undefined" /></a-form-item><a-form-item label="转交原因" required><a-textarea v-model:value="transferForm.reason" :rows="3" :maxlength="1000" show-count @change="transferPreview = undefined" /></a-form-item></a-form>
      <a-alert v-if="transferPreview" type="info" show-icon class="section-alert" message="归属变化预览"><template #description>{{ transferPreview.fromOwnerName || `成员 ${transferPreview.fromOwnerMemberId || '—'}` }} → {{ transferPreview.toOwnerName || `成员 ${transferPreview.toOwnerMemberId}` }}；{{ transferPreview.fromDepartmentName || '无部门' }} → {{ transferPreview.toDepartmentName || '无部门' }}；相关人 {{ transferPreview.fromParticipantMemberIds.join('、') || '无' }} → {{ transferPreview.toParticipantMemberIds.join('、') || '无' }}</template></a-alert>
      <div class="drawer-footer"><a-button @click="transferOpen = false">取消</a-button><a-button :loading="transferSaving" @click="previewTransfer">预览变化</a-button><a-button type="primary" :disabled="!transferPreview" :loading="transferSaving" @click="submitTransfer">确认转交</a-button></div>
    </a-modal>
    <a-modal v-model:open="conversionOpen" title="转化为目标模块记录" :footer="null" :mask-closable="false" width="640px">
      <a-form layout="vertical"><a-form-item label="目标模块" required><a-select v-model:value="conversionTargetCode" :options="conversionTargetOptions" @change="conversionPreview = undefined" /></a-form-item></a-form>
      <template v-if="conversionPreview"><a-alert :type="conversionPreview.executable ? 'success' : 'error'" show-icon class="section-alert" :message="conversionPreview.executable ? '映射校验通过，可以执行转化' : '映射校验未通过'" :description="conversionPreview.issues.join('；') || `目标标题：${conversionPreview.targetTitle}`" />
        <a-table size="small" :pagination="false" :data-source="conversionPreview.mappings" row-key="targetFieldCode"><a-table-column title="目标字段" data-index="targetFieldName" /><a-table-column title="来源字段" data-index="sourceFieldCode" /><a-table-column title="映射结果"><template #default="{ record }">{{ timelineValue(record.value) }}</template></a-table-column><a-table-column title="校验" data-index="message" /></a-table></template>
      <div class="drawer-footer"><a-button @click="conversionOpen = false">取消</a-button><a-button :loading="conversionSaving" @click="previewConversion">预览映射</a-button><a-button type="primary" :disabled="!conversionPreview?.executable" :loading="conversionSaving" @click="submitConversion">确认并打开目标</a-button></div>
    </a-modal>
    <ModuleImportDrawer v-if="activeCode" v-model:open="importOpen" :module-code="activeCode" :module-name="activeModule?.moduleName" @records-changed="loadRecords" />
    <ModuleExportDrawer v-if="activeCode" v-model:open="exportOpen" :module-code="activeCode" :module-name="activeModule?.moduleName"
      :lifecycle-state="lifecycleState" :tenant-scope="tenantScope" :search="searchText" :filters="listFilters"
      :sort-field="sortField" :sort-direction="sortDirection" :selected-record-ids="selectedRowKeys" :visible-field-codes="visibleFieldCodes" />
  </ProductPage>
</template>
