<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, send, type Account, type System } from '../product/api'

interface Group { id: number; code: string; name: string; sortOrder: number }
interface Module { id: number; groupId: number; code: string; name: string; status: string; publishedVersionId: number | null; draftRevision: number }
interface Field { id: number; key: string; label: string; type: string; required: boolean; searchable: boolean; sortOrder: number; optionsJson: string | null }
interface ModuleAction { code: string; name: string; enabled: boolean; sortOrder: number }
interface Snapshot { moduleName: string; groupId: number; groupName: string; fields: Field[]; menus: { name: string }[]; actions: { code: string; name: string }[] }
interface Published { moduleId: number; versionId: number; versionNumber: number; snapshot: Snapshot; canCreate: boolean; canUpdate: boolean; canExport: boolean; canImport: boolean; canSubmitApproval: boolean }
interface BusinessRecord { id: number; recordNo: string; title: string; recordStatus: string; version: number; createTime: string; updateTime: string; values: Record<string, string | number | boolean> }
interface RecordPage { items: BusinessRecord[]; total: number; page: number; size: number }
interface Role { id: number; code: string; name: string }
interface Member { id: number; username: string; displayName: string }
interface Grant { moduleId: number; canView: boolean; canCreate: boolean; canUpdate: boolean; canDelete: boolean; canExport: boolean }
interface FlowConfig { id: number; name: string; nodeName: string; approverMemberId: number; publishedVersionNumber: number | null; status: string }
interface FlowTask { taskId: number; recordId: number; moduleId: number; title: string; nodeName: string; status: string; attemptNumber: number }
interface TaskDetail { task: FlowTask; record: BusinessRecord; history: { eventType: string; opinion: string | null; createTime: string }[] }
interface FileItem { id: number; name: string; contentType: string; sizeBytes: number; createTime: string }
interface ExportJob { id: number; status: string; rowCount: number; failureReason: string | null; createTime: string; finishTime: string | null }
interface ImportRow { line: number; recordNo: string; action: string; message: string | null }
interface ImportJob { id: number; mode: string; status: string; totalRows: number; validRows: number; importedRows: number; failureReason: string | null; rows: ImportRow[] }
interface InboxMessage { id: number; kind: string; title: string; moduleId: number; recordId: number; taskId: number; createTime: string; readTime: string | null }

const route = useRoute()
const router = useRouter()
const systemId = Number(route.params.id)
const base = `/systems/${systemId}`
const account = ref<Account | null>(null)
const system = ref<System | null>(null)
const groups = ref<Group[]>([])
const modules = ref<Module[]>([])
const fields = ref<Field[]>([])
const moduleActions = ref<ModuleAction[]>([])
const menuName = ref('')
const published = ref<Published[]>([])
const roles = ref<Role[]>([])
const members = ref<Member[]>([])
const selectedRoleId = ref<number | null>(null)
const selectedGrantModuleId = ref<number | null>(null)
const canView = ref(false)
const canCreate = ref(false)
const canUpdate = ref(false)
const canExport = ref(false)
const roleCode = ref('')
const roleName = ref('')
const inviteUsername = ref('')
const inviteRoleId = ref<number | null>(null)
const selectedGroup = ref<Group | null>(null)
const selectedModule = ref<Module | null>(null)
const runtimeGroupId = ref<number | null>(null)
const runtimeModule = ref<Published | null>(null)
const recordPage = ref<RecordPage>({ items: [], total: 0, page: 1, size: 20 })
const recordSearch = ref('')
const filterKey = ref('')
const filterValue = ref('')
const recordForm = ref(false)
const editingRecord = ref<BusinessRecord | null>(null)
const recordDetail = ref<BusinessRecord | null>(null)
const recordValues = ref<Record<string, string | number | boolean>>({})
const recordBusy = ref(false)
const mode = ref<'config' | 'runtime' | 'access' | 'approval' | 'messages'>('config')
const inbox = ref<InboxMessage[]>([])
const unreadCount = ref(0)
const approvalModuleId = ref<number | null>(null)
const approvalConfig = ref<FlowConfig | null>(null)
const flowName = ref('')
const nodeName = ref('负责人审批')
const approverMemberId = ref<number | null>(null)
const tasks = ref<FlowTask[]>([])
const taskDetail = ref<TaskDetail | null>(null)
const decisionOpinion = ref('')
const recordFiles = ref<FileItem[]>([])
const taskFiles = ref<FileItem[]>([])
const exportOpen = ref(false)
const exportColumnKeys = ref<string[]>([])
const exportJobs = ref<ExportJob[]>([])
const importOpen = ref(false)
const importMode = ref<'INSERT' | 'UPSERT'>('INSERT')
const importJob = ref<ImportJob | null>(null)
const importHistory = ref<ImportJob[]>([])
const form = ref<'group' | 'module' | 'field' | 'role' | null>(null)
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const notice = ref('')
const groupCode = ref('')
const groupName = ref('')
const moduleCode = ref('')
const moduleName = ref('')
const fieldKey = ref('')
const fieldLabel = ref('')
const fieldType = ref('TEXT')
const fieldRequired = ref(false)
const fieldSearchable = ref(false)
const fieldOptions = ref('')
const runtimeGroups = computed(() => {
  const seen = new Set<number>()
  return published.value.filter(item => {
    if (seen.has(item.snapshot.groupId)) return false
    seen.add(item.snapshot.groupId)
    return true
  })
})
const runtimeModules = computed(() => published.value.filter(item => item.snapshot.groupId === runtimeGroupId.value))

async function boot() {
  try {
    account.value = await api<Account>('/auth/me')
    system.value = await api<System>(base)
    published.value = await api<Published[]>(`${base}/runtime-navigation`)
    if (published.value.length) approvalModuleId.value = published.value[0]!.moduleId
    if (system.value.owner) {
      groups.value = await api<Group[]>(`${base}/module-groups`)
      if (groups.value.length) await selectGroup(groups.value[0]!)
      await loadAccess()
    } else {
      mode.value = 'runtime'
    }
    if (runtimeGroups.value.length) runtimeGroupId.value = runtimeGroups.value[0]!.snapshot.groupId
    await loadUnreadCount()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '无法读取系统'
  } finally { loading.value = false }
}

async function loadUnreadCount() {
  unreadCount.value = (await api<{ count: number }>(`${base}/messages/unread-count`)).count
}

async function openMessages() {
  mode.value = 'messages'
  error.value = ''
  try {
    inbox.value = await api<InboxMessage[]>(`${base}/messages`)
    await loadUnreadCount()
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '读取消息失败' }
}

async function openMessage(message: InboxMessage) {
  error.value = ''
  try {
    await send<InboxMessage>(`${base}/messages/${message.id}/read`)
    inbox.value = await api<InboxMessage[]>(`${base}/messages`)
    await loadUnreadCount()
    if (message.kind === 'APPROVAL_TODO') {
      await openApproval()
      await openTask({ taskId: message.taskId })
    } else {
      const target = published.value.find(item => item.moduleId === message.moduleId)
      if (!target) throw new Error('当前无权打开这条消息对应的模块')
      mode.value = 'runtime'
      runtimeGroupId.value = target.snapshot.groupId
      await showRuntime(target)
      const record = await api<BusinessRecord>(`${base}/modules/${message.moduleId}/records/${message.recordId}`)
      await openRecord(record)
    }
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '无法打开消息目标' }
}

async function loadAccess() {
  roles.value = await api<Role[]>(`${base}/roles`)
  members.value = await api<Member[]>(`${base}/members`)
  if (!selectedRoleId.value && roles.value.length) selectedRoleId.value = roles.value[0]!.id
  if (!inviteRoleId.value && roles.value.length) inviteRoleId.value = roles.value[0]!.id
  if (!selectedGrantModuleId.value && published.value.length) selectedGrantModuleId.value = published.value[0]!.moduleId
  await loadGrant()
}

async function loadGrant() {
  canView.value = canCreate.value = canUpdate.value = canExport.value = false
  if (!selectedRoleId.value || !selectedGrantModuleId.value) return
  const grants = await api<Grant[]>(`${base}/roles/${selectedRoleId.value}/grants`)
  const selected = grants.find(item => item.moduleId === selectedGrantModuleId.value)
  if (!selected) return
  canView.value = selected.canView
  canCreate.value = selected.canCreate
  canUpdate.value = selected.canUpdate
  canExport.value = selected.canExport
}

async function saveGrant() {
  if (!selectedRoleId.value || !selectedGrantModuleId.value) return
  saving.value = true
  error.value = ''
  try {
    await send<Grant>(`${base}/roles/${selectedRoleId.value}/grants`, {
      moduleId: selectedGrantModuleId.value, canView: canView.value,
      canCreate: canCreate.value, canUpdate: canUpdate.value, canDelete: false, canExport: canExport.value,
    })
    notice.value = '角色菜单权限已保存，立即生效。'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '授权失败' }
  finally { saving.value = false }
}

async function saveRole() {
  saving.value = true
  error.value = ''
  try {
    const role = await send<Role>(`${base}/roles`, { code: roleCode.value.trim(), name: roleName.value.trim() })
    roles.value = await api<Role[]>(`${base}/roles`)
    selectedRoleId.value = role.id
    inviteRoleId.value = role.id
    roleCode.value = ''
    roleName.value = ''
    form.value = null
    await loadGrant()
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '创建角色失败' }
  finally { saving.value = false }
}

async function invite() {
  if (!inviteRoleId.value) return
  saving.value = true
  error.value = ''
  try {
    await send<Member>(`${base}/members/invite`, { username: inviteUsername.value.trim(), roleId: inviteRoleId.value })
    members.value = await api<Member[]>(`${base}/members`)
    inviteUsername.value = ''
    notice.value = '账号已加入系统主租户。'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '添加成员失败' }
  finally { saving.value = false }
}

async function selectGroup(group: Group) {
  selectedGroup.value = group
  selectedModule.value = null
  fields.value = []
  modules.value = await api<Module[]>(`${base}/modules?groupId=${group.id}`)
}

async function selectModule(module: Module) {
  selectedModule.value = module
  fields.value = await api<Field[]>(`${base}/modules/${module.id}/fields`)
  moduleActions.value = await api<ModuleAction[]>(`${base}/modules/${module.id}/actions`)
  menuName.value = (await api<{ name: string }>(`${base}/modules/${module.id}/menu`)).name
}

async function setModuleAction(item: ModuleAction, enabled: boolean) {
  if (!selectedModule.value) return
  saving.value = true
  error.value = ''
  try {
    const saved = await api<ModuleAction>(`${base}/modules/${selectedModule.value.id}/actions/${item.code}`, { method: 'PUT', body: JSON.stringify({ enabled }) })
    moduleActions.value = moduleActions.value.map(action => action.code === saved.code ? saved : action)
    notice.value = '动作草稿已保存，发布新版本后生效。'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '动作配置失败' }
  finally { saving.value = false }
}

async function saveMenuName() {
  if (!selectedModule.value) return
  saving.value = true
  error.value = ''
  try {
    await api(`${base}/modules/${selectedModule.value.id}/menu`, { method: 'PUT', body: JSON.stringify({ name: menuName.value.trim() }) })
    notice.value = '菜单名称草稿已保存，发布新版本后生效。'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '菜单配置失败' }
  finally { saving.value = false }
}

function openForm(kind: 'group' | 'module' | 'field' | 'role') {
  error.value = ''
  notice.value = ''
  form.value = kind
}

async function saveGroup() {
  saving.value = true
  error.value = ''
  try {
    const created = await send<Group>(`${base}/module-groups`, { code: groupCode.value.trim(), name: groupName.value.trim() })
    groups.value = await api<Group[]>(`${base}/module-groups`)
    await selectGroup(created)
    form.value = null
    groupCode.value = ''
    groupName.value = ''
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '创建模块组失败' }
  finally { saving.value = false }
}

async function saveModule() {
  if (!selectedGroup.value) return
  saving.value = true
  error.value = ''
  try {
    const created = await send<Module>(`${base}/modules`, { groupId: selectedGroup.value.id, code: moduleCode.value.trim(), name: moduleName.value.trim() })
    modules.value = await api<Module[]>(`${base}/modules?groupId=${selectedGroup.value.id}`)
    await selectModule(created)
    form.value = null
    moduleCode.value = ''
    moduleName.value = ''
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '创建模块失败' }
  finally { saving.value = false }
}

async function saveField() {
  if (!selectedModule.value) return
  saving.value = true
  error.value = ''
  try {
    await send<Field>(`${base}/modules/${selectedModule.value.id}/fields`, {
      key: fieldKey.value.trim(), label: fieldLabel.value.trim(), type: fieldType.value,
      required: fieldRequired.value, searchable: fieldSearchable.value,
      optionsJson: fieldType.value === 'SELECT' ? JSON.stringify(fieldOptions.value.split('\n').map(item => item.trim()).filter(Boolean)) : null,
    })
    fields.value = await api<Field[]>(`${base}/modules/${selectedModule.value.id}/fields`)
    form.value = null
    fieldKey.value = ''
    fieldLabel.value = ''
    fieldType.value = 'TEXT'
    fieldRequired.value = false
    fieldSearchable.value = false
    fieldOptions.value = ''
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '保存字段失败' }
  finally { saving.value = false }
}

async function publish() {
  if (!selectedModule.value || !selectedGroup.value) return
  saving.value = true
  error.value = ''
  try {
    const version = await send<{ id: number; number: number }>(`${base}/modules/${selectedModule.value.id}/publish`)
    modules.value = await api<Module[]>(`${base}/modules?groupId=${selectedGroup.value.id}`)
    selectedModule.value = modules.value.find(item => item.id === selectedModule.value?.id) || null
    published.value = await api<Published[]>(`${base}/runtime-navigation`)
    if (!approvalModuleId.value && published.value.length) approvalModuleId.value = published.value[0]!.moduleId
    if (!runtimeGroupId.value && runtimeGroups.value.length) runtimeGroupId.value = runtimeGroups.value[0]!.snapshot.groupId
    if (!selectedGrantModuleId.value && published.value.length) selectedGrantModuleId.value = published.value[0]!.moduleId
    notice.value = `发布成功，当前为第 ${version.number} 版。`
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '发布失败' }
  finally { saving.value = false }
}

async function showRuntime(item: Published) {
  try {
    runtimeModule.value = await api<Published>(`${base}/modules/${item.moduleId}/runtime`)
    recordSearch.value = ''
    filterKey.value = ''
    filterValue.value = ''
    recordDetail.value = null
    recordFiles.value = []
    recordForm.value = false
    exportOpen.value = false
    importOpen.value = false
    importMode.value = 'INSERT'
    importJob.value = null
    exportColumnKeys.value = runtimeModule.value.snapshot.fields.map(field => field.key)
    await loadRecords(1)
    if (runtimeModule.value.canExport) await loadExportJobs()
    else exportJobs.value = []
    if (runtimeModule.value.canImport) await loadImportHistory()
    else importHistory.value = []
  }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '无法打开模块' }
}

async function loadRecords(page = 1) {
  if (!runtimeModule.value) return
  error.value = ''
  try {
    const params = new URLSearchParams({ page: String(page), size: '20' })
    if (recordSearch.value.trim()) params.set('search', recordSearch.value.trim())
    if (filterKey.value && filterValue.value.trim()) {
      params.set('filterKey', filterKey.value)
      params.set('filterValue', filterValue.value.trim())
    }
    recordPage.value = await api<RecordPage>(`${base}/modules/${runtimeModule.value.moduleId}/records?${params}`)
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '查询记录失败' }
}

function openNewRecord() {
  if (!runtimeModule.value) return
  editingRecord.value = null
  recordValues.value = {}
  for (const field of runtimeModule.value.snapshot.fields) {
    recordValues.value[field.key] = field.type === 'BOOLEAN' ? false : ''
  }
  recordForm.value = true
  error.value = ''
}

async function openRecord(record: BusinessRecord) {
  if (!runtimeModule.value) return
  error.value = ''
  recordDetail.value = null
  recordFiles.value = []
  recordForm.value = false
  try {
    const detail = await api<BusinessRecord>(`${base}/modules/${runtimeModule.value.moduleId}/records/${record.id}`)
    recordDetail.value = detail
    const files = await api<FileItem[]>(`${base}/modules/${runtimeModule.value.moduleId}/records/${record.id}/files`)
    if (recordDetail.value?.id === record.id) recordFiles.value = files
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '读取记录失败' }
}

function editRecord() {
  if (!recordDetail.value || !runtimeModule.value) return
  editingRecord.value = recordDetail.value
  recordValues.value = {}
  for (const field of runtimeModule.value.snapshot.fields) {
    recordValues.value[field.key] = recordDetail.value.values[field.key] ?? (field.type === 'BOOLEAN' ? false : '')
  }
  recordForm.value = true
  error.value = ''
}

function choices(field: Field): string[] {
  if (!field.optionsJson) return []
  try {
    const parsed: unknown = JSON.parse(field.optionsJson)
    return Array.isArray(parsed) ? parsed.filter((item): item is string => typeof item === 'string') : []
  } catch { return [] }
}

async function saveRecord() {
  if (!runtimeModule.value) return
  recordBusy.value = true
  error.value = ''
  try {
    const values: Record<string, string | number | boolean> = {}
    for (const field of runtimeModule.value.snapshot.fields) {
      const value = recordValues.value[field.key]
      if (value === '' || value === undefined) continue
      values[field.key] = field.type === 'NUMBER' ? Number(value) : value
    }
    const path = `${base}/modules/${runtimeModule.value.moduleId}/records`
    const saved = editingRecord.value
      ? await api<BusinessRecord>(`${path}/${editingRecord.value.id}`, {
          method: 'PUT', body: JSON.stringify({ expectedVersion: editingRecord.value.version, values }),
        })
      : await send<BusinessRecord>(path, { values })
    recordForm.value = false
    editingRecord.value = null
    recordDetail.value = saved
    recordFiles.value = await api<FileItem[]>(`${base}/modules/${runtimeModule.value.moduleId}/records/${saved.id}/files`)
    await loadRecords(1)
    notice.value = '记录已保存。'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '保存记录失败' }
  finally { recordBusy.value = false }
}

async function loadApprovalConfig() {
  if (!approvalModuleId.value || !system.value?.owner) return
  try {
    approvalConfig.value = (await api<FlowConfig | undefined>(`${base}/modules/${approvalModuleId.value}/approval`)) ?? null
    flowName.value = approvalConfig.value?.name ?? `${published.value.find(item => item.moduleId === approvalModuleId.value)?.snapshot.moduleName ?? '业务'}审批`
    nodeName.value = approvalConfig.value?.nodeName ?? '负责人审批'
    approverMemberId.value = approvalConfig.value?.approverMemberId ?? members.value[0]?.id ?? null
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '读取审批配置失败' }
}

async function openApproval() {
  mode.value = 'approval'
  error.value = ''
  taskDetail.value = null
  try {
    tasks.value = await api<FlowTask[]>(`${base}/approval/tasks`)
    if (system.value?.owner) await loadApprovalConfig()
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '读取待办失败' }
}

async function saveApprovalDraft() {
  if (!approvalModuleId.value || !approverMemberId.value) return
  saving.value = true
  error.value = ''
  try {
    approvalConfig.value = await api<FlowConfig>(`${base}/modules/${approvalModuleId.value}/approval`, {
      method: 'PUT', body: JSON.stringify({ name: flowName.value.trim(), nodeName: nodeName.value.trim(), approverMemberId: approverMemberId.value }),
    })
    notice.value = '审批草稿已保存，发布后新申请才会使用。'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '保存审批草稿失败' }
  finally { saving.value = false }
}

async function publishApproval() {
  if (!approvalModuleId.value) return
  saving.value = true
  error.value = ''
  try {
    approvalConfig.value = await send<FlowConfig>(`${base}/modules/${approvalModuleId.value}/approval/publish`)
    notice.value = `审批已发布第 ${approvalConfig.value.publishedVersionNumber} 版。`
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '发布审批失败' }
  finally { saving.value = false }
}

async function submitApproval() {
  if (!runtimeModule.value || !recordDetail.value) return
  recordBusy.value = true
  error.value = ''
  try {
    await send<FlowTask>(`${base}/modules/${runtimeModule.value.moduleId}/records/${recordDetail.value.id}/submit-approval`)
    recordDetail.value = await api<BusinessRecord>(`${base}/modules/${runtimeModule.value.moduleId}/records/${recordDetail.value.id}`)
    await loadRecords(recordPage.value.page)
    notice.value = '审批已发起，记录处于待审批状态。'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '发起审批失败' }
  finally { recordBusy.value = false }
}

async function openTask(task: { taskId: number }) {
  error.value = ''
  taskDetail.value = null
  taskFiles.value = []
  try {
    taskDetail.value = await api<TaskDetail>(`${base}/approval/tasks/${task.taskId}`)
    taskFiles.value = await api<FileItem[]>(`${base}/approval/tasks/${task.taskId}/files`)
  }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '读取待办详情失败' }
}

async function decide(approve: boolean) {
  if (!taskDetail.value) return
  saving.value = true
  error.value = ''
  try {
    await send<FlowTask>(`${base}/approval/tasks/${taskDetail.value.task.taskId}/decision`, {
      approve, opinion: decisionOpinion.value.trim(),
    })
    notice.value = approve ? '已同意审批。' : '已拒绝审批。'
    decisionOpinion.value = ''
    taskDetail.value = null
    tasks.value = await api<FlowTask[]>(`${base}/approval/tasks`)
    if (runtimeModule.value) await loadRecords(recordPage.value.page)
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '办理失败' }
  finally { saving.value = false }
}

function taskFieldLabel(key: string): string {
  const fields = published.value.find(item => item.moduleId === taskDetail.value?.task.moduleId)?.snapshot.fields
  return fields?.find(item => item.key === key)?.label ?? key
}

function eventName(type: string): string {
  return ({ SUBMIT: '发起审批', APPROVE: '同意', REJECT: '拒绝' } as Record<string, string>)[type] ?? type
}

async function uploadSelected(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !runtimeModule.value || !recordDetail.value) return
  recordBusy.value = true
  error.value = ''
  try {
    const form = new FormData()
    form.append('file', file)
    const path = `${base}/modules/${runtimeModule.value.moduleId}/records/${recordDetail.value.id}/files`
    await api<FileItem>(path, { method: 'POST', body: form })
    recordFiles.value = await api<FileItem[]>(path)
    recordDetail.value = await api<BusinessRecord>(`${base}/modules/${runtimeModule.value.moduleId}/records/${recordDetail.value.id}`)
    notice.value = '附件已上传。'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '上传附件失败' }
  finally { input.value = ''; recordBusy.value = false }
}

async function removeFile(fileId: number) {
  if (!runtimeModule.value || !recordDetail.value) return
  recordBusy.value = true
  error.value = ''
  try {
    const path = `${base}/modules/${runtimeModule.value.moduleId}/records/${recordDetail.value.id}/files`
    await api<void>(`${path}/${fileId}`, { method: 'DELETE' })
    recordFiles.value = await api<FileItem[]>(path)
    recordDetail.value = await api<BusinessRecord>(`${base}/modules/${runtimeModule.value.moduleId}/records/${recordDetail.value.id}`)
    notice.value = '附件已移除。'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '移除附件失败' }
  finally { recordBusy.value = false }
}

function recordFileUrl(fileId: number, action: 'download' | 'preview' | 'thumbnail'): string {
  return `/api${base}/modules/${runtimeModule.value?.moduleId}/records/${recordDetail.value?.id}/files/${fileId}/${action}`
}

function taskFileUrl(fileId: number, action: 'download' | 'preview'): string {
  return `/api${base}/approval/tasks/${taskDetail.value?.task.taskId}/files/${fileId}/${action}`
}

async function loadExportJobs() {
  if (!runtimeModule.value) return
  exportJobs.value = await api<ExportJob[]>(`${base}/modules/${runtimeModule.value.moduleId}/export-jobs`)
}

async function submitExport() {
  if (!runtimeModule.value) return
  saving.value = true
  error.value = ''
  try {
    await send<ExportJob>(`${base}/modules/${runtimeModule.value.moduleId}/export-jobs`, {
      search: recordSearch.value.trim(), filterKey: filterKey.value, filterValue: filterValue.value.trim(),
      columnKeys: exportColumnKeys.value,
    })
    await loadExportJobs()
    notice.value = '导出任务已提交，可在下方刷新状态并下载。'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : '提交导出失败' }
  finally { saving.value = false }
}

function exportUrl(jobId: number): string {
  return `/api${base}/modules/${runtimeModule.value?.moduleId}/export-jobs/${jobId}/download`
}

function importBase(): string {
  return `${base}/modules/${runtimeModule.value?.moduleId}/import-jobs`
}

async function loadImportHistory() {
  if (!runtimeModule.value?.canImport) return
  importHistory.value = await api<ImportJob[]>(importBase())
}

async function previewImport(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || !runtimeModule.value) return
  saving.value = true
  error.value = ''
  try {
    const form = new FormData()
    form.append('mode', importMode.value)
    form.append('file', file)
    importJob.value = await api<ImportJob>(`${importBase()}/preview`, { method: 'POST', body: form })
    await loadImportHistory()
    notice.value = importJob.value.status === 'READY' ? '预检查通过，可以确认导入。' : '预检查发现错误，请下载错误行并修正。'
  } catch (cause) { error.value = cause instanceof Error ? cause.message : 'Excel 预检查失败' }
  finally { input.value = ''; saving.value = false }
}

async function commitImport() {
  if (!importJob.value || !runtimeModule.value) return
  saving.value = true
  error.value = ''
  try {
    importJob.value = await send<ImportJob>(`${importBase()}/${importJob.value.id}/commit`)
    await loadImportHistory()
    await loadRecords(1)
    notice.value = `已导入 ${importJob.value.importedRows} 行。`
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '确认导入失败'
    await loadImportHistory()
  } finally { saving.value = false }
}

function importUrl(suffix: string): string {
  return `/api${importBase()}${suffix}`
}

async function logout() {
  await send<void>('/auth/logout')
  await router.push('/')
}

function switchRuntime() {
  mode.value = 'runtime'
  error.value = ''
  runtimeModule.value = null
  if (runtimeGroups.value.length) runtimeGroupId.value = runtimeGroups.value[0]!.snapshot.groupId
  if (runtimeModules.value.length) void showRuntime(runtimeModules.value[0]!)
}

onMounted(boot)
</script>

<template>
  <div class="shell">
    <aside class="sidebar"><div class="brand"><span class="brand-mark">U</span><div><strong>Unexamine</strong><small>业务系统工作台</small></div></div><div class="side-label">{{ system?.name || '系统' }}</div><RouterLink class="side-link" to="/"><span class="side-icon">▦</span> 返回我的系统</RouterLink><div class="sidebar-bottom">{{ system?.owner ? '完成业务建模和授权后，成员即可使用已发布模块。' : '这里展示你有权限使用的业务模块和记录。' }}</div></aside>
    <main class="main-area"><header class="topbar"><span>我的系统 <span class="topbar-slash">/</span> {{ system?.name || '加载中' }}</span><div class="top-actions"><span class="account-badge">{{ account?.displayName || '系统成员' }}</span><button class="text-button" @click="logout">退出登录</button></div></header>
      <div v-if="loading" class="content">正在读取系统…</div>
      <div v-else class="content system-content">
        <div class="page-head"><button class="back-link" @click="router.push('/')">← 返回系统列表</button><div class="page-kicker">SYSTEM / {{ system?.code }}</div><h1>{{ system?.name || '系统不可访问' }}</h1><p>{{ system?.description || (system?.owner ? '从模块组开始组织当前系统的业务能力。' : '打开有权限的业务菜单，处理当前业务记录。') }}</p></div>
        <p v-if="error" class="form-error">{{ error }}</p><p v-if="notice" class="success-note">{{ notice }}</p>
        <div v-if="system" class="mode-tabs"><button v-if="system.owner" :class="{ active: mode === 'config' }" @click="mode = 'config'">业务建模</button><button :class="{ active: mode === 'runtime' }" @click="switchRuntime">{{ system.owner ? '运行预览' : '业务系统' }} <span class="tab-count">{{ published.length }}</span></button><button v-if="system.owner" :class="{ active: mode === 'access' }" @click="mode = 'access'; error = ''; notice = ''">成员与权限</button><button :class="{ active: mode === 'approval' }" @click="openApproval">{{ system.owner ? '审批配置与待办' : '我的审批待办' }}</button><button :class="{ active: mode === 'messages' }" @click="openMessages">消息 <span v-if="unreadCount" class="tab-count">{{ unreadCount }}</span></button></div>
        <div v-if="system && mode === 'config' && system.owner" class="model-layout">
          <section class="model-column group-column"><div class="column-head"><div><div class="page-kicker">01 / GROUPS</div><h2>模块组</h2></div><button class="icon-button" title="创建模块组" @click="openForm('group')">＋</button></div><p class="column-hint">运行态顶栏的业务分组</p><button v-for="group in groups" :key="group.id" class="list-item" :class="{ active: selectedGroup?.id === group.id }" @click="selectGroup(group)"><strong>{{ group.name }}</strong><small>{{ group.code }}</small></button><div v-if="!groups.length" class="column-empty">先创建一个模块组</div></section>
          <section class="model-column module-column"><div class="column-head"><div><div class="page-kicker">02 / MODULES</div><h2>业务模块</h2></div><button class="icon-button" title="创建业务模块" :disabled="!selectedGroup" @click="openForm('module')">＋</button></div><p class="column-hint">{{ selectedGroup ? `${selectedGroup.name} · 组内模块` : '请先选择模块组' }}</p><button v-for="module in modules" :key="module.id" class="list-item" :class="{ active: selectedModule?.id === module.id }" @click="selectModule(module)"><strong>{{ module.name }}</strong><small>{{ module.status === 'PUBLISHED' ? '已发布' : '草稿' }} · {{ module.code }}</small></button><div v-if="selectedGroup && !modules.length" class="column-empty">这个组还没有模块</div></section>
          <section class="model-detail"><template v-if="selectedModule"><div class="detail-header"><div><div class="page-kicker">03 / MODULE CONFIG</div><h2>{{ selectedModule.name }}</h2><p>字段、页面动作和菜单名称在发布后成为运行态版本。</p></div><button class="secondary-button" @click="openForm('field')">＋ 添加字段</button></div><div v-if="fields.length" class="field-list"><div v-for="field in fields" :key="field.id" class="field-row"><span class="field-symbol">▤</span><div><strong>{{ field.label }}</strong><small>{{ field.key }} · {{ field.type }}</small></div><span v-if="field.required" class="field-tag">必填</span><span v-if="field.searchable" class="field-tag">可搜索</span></div></div><div v-else class="detail-empty">还没有字段。先添加一个字段，再发布模块。</div><div class="module-draft"><h3>页面动作</h3><p>启用后还需角色授权；关闭的动作发布后在页面和接口同时失效。</p><div class="grant-options"><label v-for="action in moduleActions.filter(item => item.code !== 'view')" :key="action.code"><input type="checkbox" :checked="action.enabled" :disabled="saving" @change="setModuleAction(action, !action.enabled)" /> {{ action.name }}</label></div><h3>列表菜单</h3><p>名称将在下一次模块发布后显示在业务菜单中。</p><form class="menu-draft" @submit.prevent="saveMenuName"><input v-model="menuName" required maxlength="120" aria-label="列表菜单名称" /><button class="secondary-button" :disabled="saving">保存菜单名称</button></form></div><div class="publish-bar"><span>当前状态：{{ selectedModule.status === 'PUBLISHED' ? '已发布，可继续编辑草稿' : '草稿' }}</span><button class="primary-button" :disabled="saving || !fields.length" @click="publish">{{ saving ? '发布中…' : selectedModule.status === 'PUBLISHED' ? '发布新版本' : '发布模块' }}</button></div></template><div v-else class="detail-empty large">选择一个业务模块，配置字段并发布。</div></section>
        </div>
        <div v-if="system && mode === 'access' && system.owner" class="access-layout">
          <section class="access-card"><div class="column-head"><div><div class="page-kicker">ROLES</div><h2>角色</h2></div><button class="icon-button" title="创建角色" @click="openForm('role')">＋</button></div><p class="column-hint">主租户内的业务权限角色</p><div v-for="role in roles" :key="role.id" class="access-row"><strong>{{ role.name }}</strong><small>{{ role.code }}</small></div></section>
          <section class="access-card"><div class="page-kicker">MENU ACCESS</div><h2>菜单授权</h2><p class="column-hint">选择角色和已发布模块，保存后立即生效。</p><div v-if="published.length && roles.length"><label>角色<select v-model="selectedRoleId" @change="loadGrant"><option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}</option></select></label><label>业务模块<select v-model="selectedGrantModuleId" @change="loadGrant"><option v-for="item in published" :key="item.moduleId" :value="item.moduleId">{{ item.snapshot.moduleName }}</option></select></label><div class="grant-options"><label><input v-model="canView" type="checkbox" /> 查看</label><label><input v-model="canCreate" type="checkbox" /> 新增</label><label><input v-model="canUpdate" type="checkbox" /> 编辑</label><label><input v-model="canExport" type="checkbox" /> 导出</label></div><button class="primary-button" :disabled="saving" @click="saveGrant">保存菜单权限</button></div><p v-else class="column-empty">请先发布模块。</p></section>
          <section class="access-card"><div class="page-kicker">MEMBERS</div><h2>系统成员</h2><p class="column-hint">添加已注册的平台账号进入当前主租户。</p><form @submit.prevent="invite"><label>平台用户名<input v-model="inviteUsername" required placeholder="输入已有用户名" /></label><label>授予角色<select v-model="inviteRoleId" required><option v-for="role in roles" :key="role.id" :value="role.id">{{ role.name }}</option></select></label><button class="primary-button" :disabled="saving">添加成员</button></form><div class="member-list"><div v-for="member in members" :key="member.id"><strong>{{ member.displayName }}</strong><small>{{ member.username }}</small></div></div></section>
        </div>
        <div v-if="system && mode === 'approval'" class="access-layout approval-layout" :class="{ 'member-approval': !system.owner, 'with-detail': !!taskDetail }">
          <section v-if="system.owner" class="access-card"><div class="page-kicker">APPROVAL CONFIG</div><h2>模块审批</h2><p class="column-hint">当前支持一个指定成员审批节点。保存草稿后发布，新申请才采用新版本。</p><div v-if="published.length"><label>已发布模块<select v-model="approvalModuleId" @change="loadApprovalConfig"><option v-for="item in published" :key="item.moduleId" :value="item.moduleId">{{ item.snapshot.moduleName }}</option></select></label><form @submit.prevent="saveApprovalDraft"><label>流程名称<input v-model="flowName" required maxlength="120" /></label><label>节点名称<input v-model="nodeName" required maxlength="120" /></label><label>审批人<select v-model="approverMemberId" required><option v-for="member in members" :key="member.id" :value="member.id">{{ member.displayName }} · {{ member.username }}</option></select></label><button class="secondary-button" :disabled="saving">保存草稿</button></form><div class="publish-bar"><span>{{ approvalConfig?.publishedVersionNumber ? `已发布第 ${approvalConfig.publishedVersionNumber} 版` : '尚未发布' }}</span><button class="primary-button" :disabled="saving || !approvalConfig" @click="publishApproval">发布审批</button></div></div><p v-else class="column-empty">请先发布业务模块。</p></section>
          <section class="access-card"><div class="page-kicker">MY TASKS</div><h2>待我审批</h2><p class="column-hint">只显示当前账号有权查看并处理的业务记录。</p><div v-for="task in tasks" :key="task.taskId" class="access-row"><strong>{{ task.title }}</strong><small>{{ task.nodeName }} · 第 {{ task.attemptNumber }} 次申请</small><button class="secondary-button" @click="openTask(task)">查看办理</button></div><p v-if="!tasks.length" class="column-empty">当前没有待办。</p></section>
          <section v-if="taskDetail" class="access-card"><div class="page-kicker">TASK DETAIL</div><h2>{{ taskDetail.record.title }}</h2><p class="column-hint">{{ taskDetail.task.nodeName }} · {{ taskDetail.record.recordNo }}</p><div class="record-detail-fields"><div v-for="(value, key) in taskDetail.record.values" :key="key"><small>{{ taskFieldLabel(key) }}</small><strong>{{ value }}</strong></div></div><div v-if="taskFiles.length" class="file-list"><div v-for="file in taskFiles" :key="file.id" class="file-row"><strong>{{ file.name }}</strong><span>{{ Math.ceil(file.sizeBytes / 1024) }} KB</span><a v-if="file.contentType.startsWith('image/') || file.contentType === 'application/pdf'" :href="taskFileUrl(file.id, 'preview')" target="_blank" rel="noopener">预览</a><a :href="taskFileUrl(file.id, 'download')">下载</a></div></div><div class="member-list"><div v-for="(event, index) in taskDetail.history" :key="index"><strong>{{ eventName(event.eventType) }}</strong><small>{{ event.createTime?.slice(0, 16) }} {{ event.opinion || '' }}</small></div></div><label>办理意见<textarea v-model="decisionOpinion" rows="3" maxlength="1000" placeholder="拒绝时必须填写原因"></textarea></label><div class="grant-options"><button class="primary-button" :disabled="saving" @click="decide(true)">同意</button><button class="secondary-button" :disabled="saving || !decisionOpinion.trim()" @click="decide(false)">拒绝</button></div></section>
        </div>
        <section v-if="system && mode === 'messages'" class="access-card message-card"><div class="record-editor-head"><div><div class="page-kicker">MESSAGES</div><h2>我的消息</h2><p class="column-hint">只显示当前系统发给你的通知；打开目标时会重新检查业务权限。</p></div><button class="secondary-button" @click="openMessages">刷新消息</button></div><button v-for="message in inbox" :key="message.id" class="message-item" @click="openMessage(message)"><span><strong>{{ message.title }}</strong><small>{{ message.kind === 'APPROVAL_TODO' ? '审批待办' : '审批结果' }} · {{ message.createTime?.slice(0, 16) }}</small></span><span v-if="!message.readTime" class="field-tag">未读</span><span>查看目标 →</span></button><p v-if="!inbox.length" class="column-empty">当前系统还没有消息。</p></section>
        <div v-if="system && mode === 'runtime'" class="runtime-shell">
          <div v-if="published.length" class="runtime-top"><button v-for="group in runtimeGroups" :key="group.snapshot.groupId" :class="{ active: runtimeGroupId === group.snapshot.groupId }" @click="runtimeGroupId = group.snapshot.groupId; runtimeModule = null">{{ group.snapshot.groupName }}</button></div>
          <div v-if="published.length" class="runtime-body">
            <nav class="runtime-side"><div class="page-kicker">业务菜单</div><button v-for="item in runtimeModules" :key="item.moduleId" :class="{ active: runtimeModule?.moduleId === item.moduleId }" @click="showRuntime(item)">{{ item.snapshot.menus[0]?.name || item.snapshot.moduleName }}</button></nav>
            <section class="runtime-main"><template v-if="runtimeModule">
              <div class="page-kicker">已发布 · 第 {{ runtimeModule.versionNumber }} 版</div><h2>{{ runtimeModule.snapshot.moduleName }}</h2>
              <div class="record-toolbar"><div><strong>业务记录</strong><small>共 {{ recordPage.total }} 条</small></div><div class="toolbar-actions"><button v-if="runtimeModule.canImport" class="secondary-button" @click="importOpen = !importOpen">导入 Excel</button><button v-if="runtimeModule.canExport" class="secondary-button" @click="exportOpen = !exportOpen">导出数据</button><button v-if="runtimeModule.canCreate" class="primary-button" @click="openNewRecord">＋ 新增记录</button></div></div>
              <form class="record-search" @submit.prevent="loadRecords(1)"><input v-model="recordSearch" placeholder="搜索记录标题" /><select v-model="filterKey"><option value="">不按字段筛选</option><option v-for="field in runtimeModule.snapshot.fields.filter(item => item.searchable)" :key="field.key" :value="field.key">{{ field.label }}</option></select><input v-model="filterValue" :disabled="!filterKey" placeholder="字段精确值" /><button class="secondary-button">查询</button></form>
              <div v-if="exportOpen" class="export-panel"><div class="record-editor-head"><div><h3>导出当前筛选</h3><p>只包含你当前有权查看的记录。选择 CSV 中的业务字段。</p></div><button class="text-button" @click="loadExportJobs">刷新状态</button></div><div class="export-columns"><label v-for="field in runtimeModule.snapshot.fields" :key="field.key"><input v-model="exportColumnKeys" type="checkbox" :value="field.key" /> {{ field.label }}</label></div><button class="primary-button" :disabled="saving || !exportColumnKeys.length" @click="submitExport">提交导出任务</button><div v-if="exportJobs.length" class="file-list"><div v-for="job in exportJobs" :key="job.id" class="file-row"><strong>任务 #{{ job.id }}</strong><span>{{ job.status === 'SUCCEEDED' ? `已完成 · ${job.rowCount} 行` : job.status === 'FAILED' ? `失败 · ${job.failureReason}` : '正在处理' }}</span><a v-if="job.status === 'SUCCEEDED'" :href="exportUrl(job.id)">下载 CSV</a></div></div></div>
              <div v-if="importOpen && runtimeModule.canImport" class="export-panel"><div class="record-editor-head"><div><h3>导入 Excel</h3><p>先下载当前字段模板，上传后预检查；确认前不会写入记录。单次最多 1000 行、2 MB。</p></div><button class="text-button" @click="loadImportHistory">刷新历史</button></div><div class="import-actions"><a class="secondary-button" :href="importUrl('/template')">下载模板</a><label>导入方式<select v-model="importMode"><option value="INSERT">只新增</option><option v-if="runtimeModule.canUpdate" value="UPSERT">按记录编号新增或更新</option></select></label><label class="file-upload">选择 Excel 并预检查<input type="file" accept=".xlsx" :disabled="saving" @change="previewImport" /></label></div><p class="column-hint">更新时在模板第一列填写系统生成的记录编号；空白编号新增。仅可更新本人有权编辑的记录。</p><div v-if="importJob" class="import-result"><strong>预检查：{{ importJob.validRows }} / {{ importJob.totalRows }} 行通过</strong><div v-for="row in importJob.rows" :key="row.line" class="file-row"><span>第 {{ row.line }} 行</span><span>{{ row.message || (row.action === 'UPDATE' ? '可更新' : '可新增') }}</span></div><div class="toolbar-actions"><button v-if="importJob.status === 'READY'" class="primary-button" :disabled="saving" @click="commitImport">确认整批导入</button><a v-if="importJob.status === 'INVALID'" :href="importUrl(`/${importJob.id}/errors`)">下载错误行</a></div></div><div v-if="importHistory.length" class="file-list"><div v-for="job in importHistory" :key="job.id" class="file-row"><strong>导入 #{{ job.id }}</strong><span>{{ job.status === 'IMPORTED' ? `已导入 ${job.importedRows} 行` : job.status === 'INVALID' ? `${job.totalRows - job.validRows} 行错误` : job.status === 'FAILED' ? `失败：${job.failureReason}` : '待确认' }}</span><a v-if="job.status === 'INVALID'" :href="importUrl(`/${job.id}/errors`)">下载错误行</a></div></div></div>
              <div v-if="recordForm" class="record-editor"><div class="record-editor-head"><h3>{{ editingRecord ? '编辑记录' : '新增记录' }}</h3><button class="text-button" @click="recordForm = false">取消</button></div><form @submit.prevent="saveRecord"><div class="record-form-grid"><label v-for="field in runtimeModule.snapshot.fields" :key="field.key">{{ field.label }}<span v-if="field.required"> *</span><select v-if="field.type === 'SELECT'" v-model="recordValues[field.key]" :required="field.required"><option value="">请选择</option><option v-for="option in choices(field)" :key="option" :value="option">{{ option }}</option></select><input v-else-if="field.type === 'BOOLEAN'" v-model="recordValues[field.key]" type="checkbox" /><input v-else v-model="recordValues[field.key]" :type="field.type === 'NUMBER' ? 'number' : field.type === 'DATE' ? 'date' : 'text'" :required="field.required" :step="field.type === 'NUMBER' ? 'any' : undefined" /></label></div><button class="primary-button" :disabled="recordBusy">{{ recordBusy ? '保存中…' : '保存记录' }}</button></form></div>
              <div v-if="recordPage.items.length" class="record-list"><button v-for="item in recordPage.items" :key="item.id" class="record-row" @click="openRecord(item)"><span><strong>{{ item.title }}</strong><small>{{ item.recordNo }} · {{ item.recordStatus === 'PENDING' ? '待审批' : item.recordStatus === 'APPROVED' ? '已通过' : item.recordStatus === 'REJECTED' ? '已拒绝' : '未提交' }}</small></span><span>{{ item.updateTime?.slice(0, 10) }}</span><span>查看详情 →</span></button></div><div v-else class="record-empty">当前范围内没有记录。{{ runtimeModule.canCreate ? '可以新增第一条业务数据。' : '' }}</div>
              <div v-if="recordPage.total > recordPage.size" class="record-pagination"><button :disabled="recordPage.page <= 1" @click="loadRecords(recordPage.page - 1)">上一页</button><span>第 {{ recordPage.page }} 页</span><button :disabled="recordPage.page * recordPage.size >= recordPage.total" @click="loadRecords(recordPage.page + 1)">下一页</button></div>
              <div v-if="recordDetail" class="record-detail"><div class="record-editor-head"><div><div class="page-kicker">RECORD DETAIL / {{ recordDetail.recordNo }} · {{ recordDetail.recordStatus === 'PENDING' ? '待审批' : recordDetail.recordStatus === 'APPROVED' ? '已通过' : recordDetail.recordStatus === 'REJECTED' ? '已拒绝' : '未提交' }}</div><h3>{{ recordDetail.title }}</h3></div><div class="toolbar-actions"><button v-if="runtimeModule.canUpdate && ['ACTIVE', 'REJECTED'].includes(recordDetail.recordStatus)" class="secondary-button" @click="editRecord">编辑</button><button v-if="runtimeModule.canSubmitApproval && ['ACTIVE', 'REJECTED'].includes(recordDetail.recordStatus)" class="primary-button" :disabled="recordBusy" @click="submitApproval">发起审批</button><button class="text-button" @click="recordDetail = null">关闭</button></div></div><div class="record-detail-fields"><div v-for="field in runtimeModule.snapshot.fields" :key="field.key"><small>{{ field.label }}</small><strong>{{ recordDetail.values[field.key] ?? '—' }}</strong></div></div><div class="record-files"><div class="record-editor-head"><div><h3>附件</h3><p>文件关联当前业务记录，审批人可以在待办中查看。</p></div><label v-if="runtimeModule.canUpdate && ['ACTIVE', 'REJECTED'].includes(recordDetail.recordStatus)" class="file-upload">上传附件<input type="file" @change="uploadSelected" /></label></div><div v-if="recordFiles.length" class="file-list"><div v-for="file in recordFiles" :key="file.id" class="file-row"><img v-if="file.contentType.startsWith('image/')" :src="recordFileUrl(file.id, 'thumbnail')" alt="" /><strong>{{ file.name }}</strong><span>{{ Math.ceil(file.sizeBytes / 1024) }} KB</span><a v-if="file.contentType.startsWith('image/') || file.contentType === 'application/pdf'" :href="recordFileUrl(file.id, 'preview')" target="_blank" rel="noopener">预览</a><a :href="recordFileUrl(file.id, 'download')">下载</a><button v-if="runtimeModule.canUpdate && ['ACTIVE', 'REJECTED'].includes(recordDetail.recordStatus)" class="text-button" :disabled="recordBusy" @click="removeFile(file.id)">移除</button></div></div><p v-else class="column-hint">这条记录还没有附件。</p></div></div>
            </template><p v-else>从左侧选择模块。</p></section>
          </div>
          <div v-else class="empty-state"><div class="empty-shape">◇</div><h3>还没有已发布模块</h3><p>完成模块字段配置并发布后，这里会出现模块组和业务菜单。</p><button v-if="system.owner" class="secondary-button" @click="mode = 'config'">返回业务建模</button></div>
        </div>
      </div>
    </main>
    <div v-if="form" class="modal-backdrop" @click.self="form = null"><div class="modal"><button class="modal-close" aria-label="关闭" @click="form = null">×</button><div class="page-kicker">{{ form === 'group' ? 'MODULE GROUP' : form === 'module' ? 'BUSINESS MODULE' : form === 'role' ? 'SYSTEM ROLE' : 'MODULE FIELD' }}</div><h2>{{ form === 'group' ? '创建模块组' : form === 'module' ? '创建业务模块' : form === 'role' ? '创建角色' : '添加业务字段' }}</h2><p class="muted">{{ form === 'group' ? '模块组会成为发布后的顶栏导航。' : form === 'module' ? `归属模块组：${selectedGroup?.name}` : form === 'role' ? '角色用于给系统成员授予模块菜单权限。' : `归属模块：${selectedModule?.name}` }}</p>
      <form v-if="form === 'group'" @submit.prevent="saveGroup"><label>模块组名称<input v-model="groupName" required maxlength="120" placeholder="例如：销售业务" /></label><label>模块组编码<input v-model="groupCode" required pattern="[a-z][a-z0-9_]{2,39}" placeholder="例如：sales" /></label><p v-if="error" class="form-error">{{ error }}</p><div class="modal-actions"><button type="button" class="secondary-button" @click="form = null">取消</button><button class="primary-button" :disabled="saving">创建模块组</button></div></form>
      <form v-else-if="form === 'module'" @submit.prevent="saveModule"><label>模块名称<input v-model="moduleName" required maxlength="160" placeholder="例如：客户" /></label><label>模块编码<input v-model="moduleCode" required pattern="[a-z][a-z0-9_]{2,39}" placeholder="例如：customer" /></label><p v-if="error" class="form-error">{{ error }}</p><div class="modal-actions"><button type="button" class="secondary-button" @click="form = null">取消</button><button class="primary-button" :disabled="saving">创建模块</button></div></form>
      <form v-else-if="form === 'role'" @submit.prevent="saveRole"><label>角色名称<input v-model="roleName" required maxlength="120" placeholder="例如：业务操作员" /></label><label>角色编码<input v-model="roleCode" required pattern="[a-z][a-z0-9_]{2,39}" placeholder="例如：operator" /></label><p v-if="error" class="form-error">{{ error }}</p><div class="modal-actions"><button type="button" class="secondary-button" @click="form = null">取消</button><button class="primary-button" :disabled="saving">创建角色</button></div></form>
      <form v-else @submit.prevent="saveField"><label>字段名称<input v-model="fieldLabel" required maxlength="160" placeholder="例如：客户名称" /></label><label>字段编码<input v-model="fieldKey" required pattern="[a-z][a-z0-9_]{1,39}" placeholder="例如：customer_name" /></label><label>字段类型<select v-model="fieldType"><option value="TEXT">文本</option><option value="NUMBER">数字</option><option value="DATE">日期</option><option value="BOOLEAN">开关</option><option value="SELECT">单选</option></select></label><label v-if="fieldType === 'SELECT'">选项（每行一个）<textarea v-model="fieldOptions" rows="3" required placeholder="潜在客户&#10;正式客户"></textarea></label><div class="check-row"><label><input v-model="fieldRequired" type="checkbox" /> 必填</label><label><input v-model="fieldSearchable" type="checkbox" /> 可搜索</label></div><p v-if="error" class="form-error">{{ error }}</p><div class="modal-actions"><button type="button" class="secondary-button" @click="form = null">取消</button><button class="primary-button" :disabled="saving">保存字段</button></div></form></div></div>
  </div>
</template>
