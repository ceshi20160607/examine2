<script setup lang="ts">
import { CloudUploadOutlined, DeleteOutlined, PlusOutlined, ReloadOutlined, RollbackOutlined, SaveOutlined } from '@ant-design/icons-vue'
import { Empty, message } from 'ant-design-vue'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import { actionLabel, fieldTypeLabel, productDateTime, productStatus, userFacingWorkspaceName, versionLabel } from '../presentation'
import { systemContext, systemTokens } from '../session'
import { fieldTypes } from '../field-capabilities'
import { actionLocations } from '../action-placement'
import type { DictionaryDraft, ModuleOverview, RuntimeModuleCatalogItem, TenantExtensionAction, TenantExtensionApplicationGrant, TenantExtensionCheck, TenantExtensionField, TenantExtensionModule } from '../types'

const token = computed(() => systemTokens.value?.accessToken || '')
const modules = ref<TenantExtensionModule[]>([])
const selectedId = ref<number>()
const selected = computed(() => modules.value.find(item => item.moduleId === selectedId.value))
const extensionFields = ref<TenantExtensionField[]>([])
const extensionActions = ref<TenantExtensionAction[]>([])
const actionsLoaded = ref(false)
const actionDrawer = ref(false)
const editingActionCode = ref<string>()
const usedActionCodes = new Set<string>()
const actionForm = reactive({ name: '', location: 'ROW', parameterFieldCodes: [] as string[], sortOrder: 10 })
const actionLocationOptions = actionLocations.filter(item => ['ROW', 'DETAIL_HEADER', 'BATCH'].includes(item.value))
const formFieldCodes = ref<string[]>([])
const listFieldCodes = ref<string[]>([])
const selectedGrantIds = ref<number[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const saving = ref(false)
const unavailable = ref(false)
const error = ref('')
const fieldDrawer = ref(false)
const checkModal = ref(false)
const publication = ref<TenantExtensionCheck>()
const editingFieldCode = ref<string>()
const retainedFieldConfig = ref<Record<string, unknown>>({})
const dictionaries = ref<DictionaryDraft[]>([])
const managedModules = ref<ModuleOverview>({ groups: [], modules: [] })
const runtimeModules = ref<RuntimeModuleCatalogItem[]>([])
const directoryLoading = ref(false)
const dictionaryError = ref('')
const moduleDirectoryError = ref('')
let directoryRequest = 0
let moduleRequest = 0
let loadRequest = 0
const fieldForm = reactive({
  code: '', name: '', fieldType: 'TEXT', required: false, sortOrder: 20, placeholder: '',
  dictionaryId: undefined as number | undefined, referenceModuleId: undefined as number | undefined,
  maxDepth: 3, allowIntermediate: false, saveMode: 'FINAL',
})
// 本包选择现有字段协议中的六种基础类型及四种已准入类型，不定义另一套类型名称或能力。
const fieldTypeOptions = fieldTypes.filter(item => ['TEXT', 'MULTILINE_TEXT', 'NUMBER', 'DATE', 'DATETIME', 'BOOLEAN', 'SINGLE_SELECT', 'STATUS', 'CASCADE', 'REFERENCE'].includes(item.type))
  .map(item => ({ value: item.type, label: item.name }))
const dictionaryOptions = computed(() => {
  const choices = dictionaries.value.filter(entry => entry.dictionary.status === 'ACTIVE'
    && (fieldForm.fieldType !== 'CASCADE' || entry.dictionary.hierarchical))
    .map(entry => ({ value: entry.dictionary.id, label: `${entry.dictionary.name}${entry.currentVersionId ? ' · 已发布' : ' · 未发布'}`, disabled: false }))
  if (fieldForm.dictionaryId && !choices.some(item => item.value === fieldForm.dictionaryId)) {
    choices.push({ value: fieldForm.dictionaryId, label: '原绑定字典暂不可选', disabled: true })
  }
  return choices
})
const referenceModuleOptions = computed(() => {
  const choices = new Map<number, { value: number; label: string; disabled: boolean }>()
  for (const module of modules.value) choices.set(module.moduleId, { value: module.moduleId, label: `${module.moduleName} · 系统公共`, disabled: false })
  for (const module of managedModules.value.modules.filter(item => item.status === 'ACTIVE')) {
    choices.set(module.id, { value: module.id, label: `${module.name} · 当前工作空间`, disabled: false })
  }
  for (const moduleId of new Set(runtimeModules.value.map(module => module.moduleId))) {
    const known = choices.get(moduleId)
    if (known) known.label = `${known.label} · 已发布`
  }
  choices.delete(selectedId.value || 0)
  if (fieldForm.referenceModuleId && !choices.has(fieldForm.referenceModuleId)) {
    choices.set(fieldForm.referenceModuleId, { value: fieldForm.referenceModuleId, label: '原绑定模块暂不可选', disabled: true })
  }
  return [...choices.values()]
})
const fieldBindingIssue = computed(() => {
  const original = extensionFields.value.find(field => field.code === editingFieldCode.value)
  if (['SINGLE_SELECT', 'STATUS'].includes(fieldForm.fieldType) && !fieldForm.dictionaryId) {
    if (original && Array.isArray(retainedFieldConfig.value.options) && retainedFieldConfig.value.options.length) return ''
    return dictionaryError.value || '请选择当前工作空间字典'
  }
  if (fieldForm.fieldType === 'CASCADE') {
    if (!fieldForm.dictionaryId && !(original?.referenceModuleId && original.referenceModuleId === fieldForm.referenceModuleId)) return dictionaryError.value || '请选择当前工作空间层级字典'
    if (!Number.isInteger(fieldForm.maxDepth) || fieldForm.maxDepth < 1 || fieldForm.maxDepth > 20) return '最大层级应为 1 到 20'
  }
  if (fieldForm.fieldType === 'REFERENCE' && !fieldForm.referenceModuleId) return '请选择当前可配置的关联模块'
  if (fieldForm.dictionaryId !== original?.dictionaryId && fieldForm.dictionaryId
    && !dictionaries.value.some(entry => entry.dictionary.id === fieldForm.dictionaryId && entry.dictionary.status === 'ACTIVE'
      && (fieldForm.fieldType !== 'CASCADE' || entry.dictionary.hierarchical))) return dictionaryError.value || '当前字典不可选择，请刷新后重新选择'
  if (fieldForm.referenceModuleId !== original?.referenceModuleId && fieldForm.referenceModuleId
    && !referenceModuleOptions.value.some(item => item.value === fieldForm.referenceModuleId && !item.disabled)) return '当前模块不可选择，请刷新后重新选择'
  return ''
})

const previewFields = computed(() => [
  ...(selected.value?.mergedPreview.fields || []).filter(field => field.source === 'MAIN'),
  ...extensionFields.value,
])
const previewActions = computed(() => [
  ...(selected.value?.mergedPreview.actions || []).filter(action => action.source === 'MAIN'),
  ...extensionActions.value,
])
// 仅开放本包后端已校验的手动填写类型，名称与展示仍复用公共字段协议。
const writableActionFields = computed(() => previewFields.value.filter(field => field.status === 'ACTIVE'
  && !field.fieldType.startsWith('SYSTEM_')
  && ['TEXT', 'MULTILINE_TEXT', 'NUMBER', 'MONEY', 'DATE', 'DATETIME', 'BOOLEAN', 'SINGLE_SELECT', 'STATUS',
    'RICH_TEXT', 'JSON', 'MEMBER', 'DEPARTMENT', 'CASCADE', 'REFERENCE'].includes(field.fieldType))
  .filter(field => { try { return !JSON.parse(field.configJson || '{}').readOnly } catch { return false } }))
const actionFieldOptions = computed(() => {
  const options = writableActionFields.value.map(field => ({ value: field.code,
    label: `${field.name}（${field.source === 'MAIN' ? '系统公共配置' : '当前工作空间'}）` }))
  for (const code of actionForm.parameterFieldCodes) {
    if (!options.some(option => option.value === code)) options.push({ value: code,
      label: `${previewFields.value.find(field => field.code === code)?.name || '已移除字段'}（不可填写，请取消勾选）` })
  }
  return options
})
const actionValidationIssue = computed(() => {
  if (!actionForm.parameterFieldCodes.length) return '请至少勾选一个执行时填写的字段'
  if (actionForm.parameterFieldCodes.some(code => !writableActionFields.value.some(field => field.code === code))) {
    return '部分字段已移除、只读或不支持填写，请取消这些字段的勾选后保存动作'
  }
  return ''
})
const fieldOptions = computed(() => previewFields.value.map(field => ({
  value: field.code,
  label: `${field.name}（${field.source === 'MAIN' ? '系统公共配置' : '当前工作空间'}）`,
})))
const applicationGroups = computed(() => {
  const groups = new Map<number, { id: number; name: string; code: string; grants: TenantExtensionApplicationGrant[] }>()
  for (const grant of selected.value?.availableApplicationGrants || []) {
    const group = groups.get(grant.applicationId) || {
      id: grant.applicationId, name: grant.applicationName, code: grant.applicationCode, grants: [],
    }
    group.grants.push(grant)
    groups.set(grant.applicationId, group)
  }
  return [...groups.values()]
})

async function load(preferred?: number) {
  if (!token.value) return
  const request = ++loadRequest
  const accessToken = token.value
  loading.value = true
  error.value = ''
  unavailable.value = false
  try {
    const loaded = await api<TenantExtensionModule[]>('/api/admin/tenant-extensions', {}, accessToken)
    if (request !== loadRequest || accessToken !== token.value) return
    modules.value = loaded
    const requested = preferred ?? selectedId.value
    const target = modules.value.some(item => item.moduleId === requested) ? requested : modules.value[0]?.moduleId
    await Promise.all([loadDirectories(), target ? choose(target) : Promise.resolve()])
  } catch (cause) {
    if (request !== loadRequest || accessToken !== token.value) return
    if (cause instanceof ApiError && cause.code === 'TENANT_EXTENSION_NOT_AVAILABLE') unavailable.value = true
    else error.value = explain(cause)
  } finally { if (request === loadRequest) loading.value = false }
}

/** 管理目录始终使用当前凭证；目录拒绝不改用其他租户或更高权限读取。 */
async function loadDirectories() {
  const request = ++directoryRequest
  const accessToken = token.value
  dictionaries.value = []
  managedModules.value = { groups: [], modules: [] }
  runtimeModules.value = []
  dictionaryError.value = ''
  moduleDirectoryError.value = ''
  directoryLoading.value = true
  const [dictionaryResult, overviewResult, runtimeResult] = await Promise.allSettled([
    api<DictionaryDraft[]>('/api/admin/dictionaries', {}, accessToken),
    api<ModuleOverview>('/api/admin/module-config', {}, accessToken),
    api<RuntimeModuleCatalogItem[]>('/api/runtime/modules', {}, accessToken),
  ])
  if (request !== directoryRequest || accessToken !== token.value) return
  if (dictionaryResult.status === 'fulfilled') dictionaries.value = dictionaryResult.value
  else dictionaryError.value = dictionaryResult.reason instanceof ApiError && dictionaryResult.reason.code === 'PERMISSION_DENIED'
    ? '当前身份无权读取工作空间字典目录' : '字典目录暂不可用，请刷新后重试'
  if (overviewResult.status === 'fulfilled') managedModules.value = overviewResult.value
  else moduleDirectoryError.value = overviewResult.reason instanceof ApiError && overviewResult.reason.code === 'PERMISSION_DENIED'
    ? '当前身份无权读取工作空间模块管理目录，仅显示可配置的公共模块' : '工作空间模块目录暂不可用，仅显示已读取的公共模块'
  if (runtimeResult.status === 'fulfilled') runtimeModules.value = runtimeResult.value
  // 运行目录只补充管理目录已授权的模块，不扩大目标身份集合。
  else if (!(runtimeResult.reason instanceof ApiError) || runtimeResult.reason.code !== 'PERMISSION_DENIED') {
    moduleDirectoryError.value ||= '模块发布信息暂不可用，保存和发布时仍会校验目标模块'
  }
  directoryLoading.value = false
}

async function choose(moduleId: number) {
  const request = ++moduleRequest
  const accessToken = token.value
  fieldDrawer.value = false
  actionDrawer.value = false
  editingActionCode.value = undefined
  editingFieldCode.value = undefined
  retainedFieldConfig.value = {}
  checkModal.value = false
  publication.value = undefined
  selectedId.value = moduleId
  extensionFields.value = []
  extensionActions.value = []
  actionsLoaded.value = false
  usedActionCodes.clear()
  saving.value = false
  formFieldCodes.value = []
  listFieldCodes.value = []
  selectedGrantIds.value = []
  detailLoading.value = true
  try {
    const detail = await api<TenantExtensionModule>(`/api/admin/tenant-extensions/modules/${moduleId}`, {}, accessToken)
    if (request !== moduleRequest || moduleId !== selectedId.value || accessToken !== token.value) return
    modules.value = modules.value.map(item => item.moduleId === moduleId ? detail : item)
    hydrate(detail)
  } catch (cause) {
    if (request === moduleRequest) { selectedId.value = undefined; message.error(explain(cause)) }
  }
  finally { if (request === moduleRequest) detailLoading.value = false }
}

function hydrate(detail: TenantExtensionModule) {
  extensionFields.value = (detail.draft.fields || []).map(field => ({ ...field }))
  actionsLoaded.value = Array.isArray(detail.draft.actions)
  extensionActions.value = (detail.draft.actions || []).filter(action => action.source === 'TENANT').map(action => ({ ...action }))
  usedActionCodes.clear()
  for (const action of [...(detail.mergedPreview.actions || []), ...extensionActions.value]) usedActionCodes.add(action.code)
  const allCodes = detail.mergedPreview.fields.map(field => field.code)
  const form = detail.draft.pages?.find(page => page.pageType === 'FORM')
  const list = detail.draft.pages?.find(page => page.pageType === 'LIST')
  formFieldCodes.value = form?.fieldCodes?.length ? [...form.fieldCodes] : [...allCodes]
  listFieldCodes.value = list?.fieldCodes?.length ? [...list.fieldCodes] : [...allCodes]
  selectedGrantIds.value = detail.draft.applicationBindings?.flatMap(binding => binding.grantIds) || []
}

function beginField() {
  if (!selected.value || detailLoading.value || saving.value) return
  editingFieldCode.value = undefined
  retainedFieldConfig.value = {}
  Object.assign(fieldForm, {
    code: '', name: '', fieldType: 'TEXT', required: false,
    sortOrder: (extensionFields.value.length + previewFields.value.filter(field => field.source === 'MAIN').length + 1) * 10,
    placeholder: '', dictionaryId: undefined, referenceModuleId: undefined,
    maxDepth: 3, allowIntermediate: false, saveMode: 'FINAL',
  })
  fieldDrawer.value = true
}

function editField(field: TenantExtensionField) {
  if (field.source !== 'TENANT' || detailLoading.value || saving.value) return
  const currentConfig = config(field) as Record<string, unknown>
  editingFieldCode.value = field.code
  retainedFieldConfig.value = { ...currentConfig }
  Object.assign(fieldForm, {
    code: field.code, name: field.name, fieldType: field.fieldType, required: field.required,
    sortOrder: field.sortOrder, placeholder: typeof currentConfig.placeholder === 'string' ? currentConfig.placeholder : '',
    dictionaryId: field.dictionaryId ?? undefined, referenceModuleId: field.referenceModuleId ?? undefined,
    maxDepth: Number(currentConfig.maxDepth ?? 3), allowIntermediate: Boolean(currentConfig.allowIntermediate),
    saveMode: currentConfig.saveMode === 'PATH' ? 'PATH' : 'FINAL',
  })
  fieldDrawer.value = true
}

function generatedFieldCode(name: string) {
  const latin = name.trim().toLowerCase().replace(/[^a-z0-9_]+/g, '_').replace(/^_+|_+$/g, '')
  return `extension_${latin || Date.now().toString(36)}`.slice(0, 96)
}

function grantFieldNames(codes: string[]) {
  return codes.map(code => previewFields.value.find(field => field.code === code)?.name).filter(Boolean).join('、') || '不包含字段'
}

function addField() {
  if (!fieldDrawer.value || detailLoading.value || saving.value) return
  const existing = extensionFields.value.find(field => field.code === editingFieldCode.value)
  const code = existing?.code || generatedFieldCode(fieldForm.name)
  if (!fieldForm.name.trim()) return message.warning('请填写字段名称')
  if (fieldBindingIssue.value) return message.warning(fieldBindingIssue.value)
  if (!existing && previewFields.value.some(field => field.code === code)) {
    return message.warning('已经存在同名或冲突字段，请更换字段名称')
  }
  // 未在本页编辑的默认值、安全属性和类型配置保留，避免名称或绑定调整误清既有配置。
  const nextConfig = { ...retainedFieldConfig.value, placeholder: fieldForm.placeholder }
  if (fieldForm.fieldType === 'CASCADE') Object.assign(nextConfig, {
    maxDepth: fieldForm.maxDepth, allowIntermediate: fieldForm.allowIntermediate, saveMode: fieldForm.saveMode,
  })
  const next: TenantExtensionField = {
    id: 0, version: 0, source: 'TENANT', sourceTenantId: 0, mandatory: false, status: 'ACTIVE', ...existing,
    code, name: fieldForm.name.trim(), fieldType: existing?.fieldType || fieldForm.fieldType,
    required: fieldForm.required, sortOrder: fieldForm.sortOrder,
    dictionaryId: fieldForm.dictionaryId, referenceModuleId: fieldForm.referenceModuleId,
    configJson: JSON.stringify(nextConfig),
  }
  if (existing) extensionFields.value = extensionFields.value.map(field => field.code === code ? next : field)
  else {
    extensionFields.value.push(next)
    formFieldCodes.value.push(code)
    listFieldCodes.value.push(code)
  }
  fieldDrawer.value = false
}

function removeDraftField(code: string) {
  if (detailLoading.value || saving.value || !extensionFields.value.some(field => field.code === code && field.source === 'TENANT')) return
  if (!actionsLoaded.value) return message.warning('动作引用信息尚未加载，请刷新后再移除字段')
  const references = previewActions.value.filter(action => actionParameterCodes(action).includes(code))
  if (references.length) return message.warning(`该字段仍被动作“${references.map(action => action.name).join('、')}”使用，请先调整或移除本工作空间动作；系统公共动作不可修改`)
  extensionFields.value = extensionFields.value.filter(field => field.code !== code)
  formFieldCodes.value = formFieldCodes.value.filter(item => item !== code)
  listFieldCodes.value = listFieldCodes.value.filter(item => item !== code)
}

function actionParameterCodes(action: TenantExtensionAction): string[] {
  try {
    const parsed = JSON.parse(action.configJson || '{}') as { parameterFieldCodes?: unknown }
    return Array.isArray(parsed.parameterFieldCodes) ? parsed.parameterFieldCodes.filter((code): code is string => typeof code === 'string') : []
  } catch { return [] }
}

function actionFieldNames(action: TenantExtensionAction) {
  return actionParameterCodes(action).map(code => previewFields.value.find(field => field.code === code)?.name || '已移除字段').join('、') || '无需填写字段'
}

function actionLocationName(location: string) {
  return actionLocations.find(item => item.value === location)?.label || '已配置位置'
}

function beginAction() {
  if (!selected.value || detailLoading.value || saving.value || !actionsLoaded.value) return
  editingActionCode.value = undefined
  Object.assign(actionForm, { name: '', location: 'ROW', parameterFieldCodes: [], sortOrder: (extensionActions.value.length + 1) * 10 })
  actionDrawer.value = true
}

function editAction(action: TenantExtensionAction) {
  if (action.source !== 'TENANT' || action.actionType !== 'CUSTOM' || detailLoading.value || saving.value) return
  editingActionCode.value = action.code
  Object.assign(actionForm, { name: action.name, location: action.location,
    parameterFieldCodes: [...actionParameterCodes(action)], sortOrder: action.sortOrder })
  actionDrawer.value = true
}

function addAction() {
  if (!actionDrawer.value || detailLoading.value || saving.value || !actionsLoaded.value) return
  if (!actionForm.name.trim()) return message.warning('请填写动作名称')
  if (actionValidationIssue.value) return message.warning(actionValidationIssue.value)
  if (!actionLocationOptions.some(item => item.value === actionForm.location)) return message.warning('请选择列表行、详情头部或批量操作区')
  const existing = extensionActions.value.find(action => action.code === editingActionCode.value)
  let sequence = 1
  while (usedActionCodes.has(`ACTION_TENANT_${sequence}`)) sequence++
  const code = existing?.code || `ACTION_TENANT_${sequence}`
  usedActionCodes.add(code)
  const next: TenantExtensionAction = { id: 0, version: 0, source: 'TENANT', sourceTenantId: 0,
    mandatory: false, status: 'ACTIVE', ...existing, code, name: actionForm.name.trim(), actionType: 'CUSTOM',
    location: actionForm.location, sortOrder: actionForm.sortOrder,
    configJson: JSON.stringify({ operation: 'UPDATE_FIELDS', parameterFieldCodes: [...new Set(actionForm.parameterFieldCodes)] }) }
  if (existing) extensionActions.value = extensionActions.value.map(action => action.code === code ? next : action)
  else extensionActions.value.push(next)
  publication.value = undefined
  actionDrawer.value = false
}

function removeDraftAction(code: string) {
  if (detailLoading.value || saving.value || !extensionActions.value.some(action => action.code === code && action.source === 'TENANT')) return
  extensionActions.value = extensionActions.value.filter(action => action.code !== code)
  publication.value = undefined
}

function config(field: TenantExtensionField) {
  try { return JSON.parse(field.configJson || '{}') }
  catch { return {} }
}

function bindings() {
  return applicationGroups.value.map(group => ({
    applicationId: group.id,
    grantIds: group.grants
      .filter((grant): grant is typeof grant & { grantId: number } => typeof grant.grantId === 'number')
      .filter(grant => selectedGrantIds.value.includes(grant.grantId))
      .map(grant => grant.grantId),
  })).filter(binding => binding.grantIds.length)
}

/** 切换模块、身份或上下文后，旧读写结果不能覆盖新草稿。 */
function moduleCurrent(request: number, moduleId: number, accessToken: string) {
  return request === moduleRequest && moduleId === selectedId.value && accessToken === token.value
}

async function saveDraft() {
  if (!selected.value || detailLoading.value || saving.value) return
  if (extensionActions.value.some(action => !actionParameterCodes(action).length
    || actionParameterCodes(action).some(code => !writableActionFields.value.some(field => field.code === code)))) {
    return message.warning('部分工作空间动作引用了已移除、只读或不可填写字段，请先编辑这些动作')
  }
  const moduleId = selected.value.moduleId
  const accessToken = token.value
  const request = moduleRequest
  saving.value = true
  try {
    const result = await api<TenantExtensionModule>(`/api/admin/tenant-extensions/modules/${moduleId}`, {
      method: 'PUT', body: JSON.stringify({
        fields: extensionFields.value.map(field => ({
          fieldId: field.id || null, code: field.code, name: field.name, fieldType: field.fieldType,
          required: field.required, sortOrder: field.sortOrder, config: config(field),
          dictionaryId: field.dictionaryId ?? null, referenceModuleId: field.referenceModuleId ?? null,
        })),
        pages: [
          { pageType: 'FORM', fieldCodes: formFieldCodes.value },
          { pageType: 'LIST', fieldCodes: listFieldCodes.value },
        ],
        applicationBindings: bindings(), expectedVersion: selected.value.extension?.version || 0,
        // 旧响应未提供动作时保留服务端草稿；已加载的空数组明确删除本租户动作。
        ...(actionsLoaded.value ? { actions: extensionActions.value.map(action => ({
          ...(action.id ? { actionId: action.id } : {}), code: action.code, name: action.name,
          location: action.location, parameterFieldCodes: actionParameterCodes(action), sortOrder: action.sortOrder,
        })) } : {}),
      }),
    }, accessToken)
    if (!moduleCurrent(request, moduleId, accessToken)) return
    modules.value = modules.value.map(item => item.moduleId === result.moduleId ? result : item)
    hydrate(result)
    publication.value = undefined
    checkModal.value = false
    message.success('租户扩展草稿已保存，运行态仍使用当前发布版本')
  } catch (cause) { if (moduleCurrent(request, moduleId, accessToken)) message.error(explain(cause)) }
  finally { if (moduleCurrent(request, moduleId, accessToken)) saving.value = false }
}

async function checkPublication() {
  if (!selected.value || detailLoading.value || saving.value) return
  const moduleId = selected.value.moduleId
  const accessToken = token.value
  const request = moduleRequest
  saving.value = true
  try {
    const result = await api<TenantExtensionCheck>(
      `/api/admin/tenant-extensions/modules/${moduleId}/publication-check`, {}, accessToken)
    if (!moduleCurrent(request, moduleId, accessToken)) return
    publication.value = result
    checkModal.value = true
  } catch (cause) { if (moduleCurrent(request, moduleId, accessToken)) message.error(explain(cause)) }
  finally { if (moduleCurrent(request, moduleId, accessToken)) saving.value = false }
}

async function publish() {
  if (!selected.value || !publication.value?.valid || detailLoading.value || saving.value) return
  const moduleId = selected.value.moduleId
  const accessToken = token.value
  const request = moduleRequest
  saving.value = true
  try {
    const result = await api<TenantExtensionModule>(
      `/api/admin/tenant-extensions/modules/${moduleId}/publish`, {
        method: 'POST', body: JSON.stringify({ expectedDraftRevision: publication.value.draftRevision }),
      }, accessToken)
    if (!moduleCurrent(request, moduleId, accessToken)) return
    modules.value = modules.value.map(item => item.moduleId === result.moduleId ? result : item)
    hydrate(result)
    checkModal.value = false
    message.success('租户扩展已发布，运行态已切换到合并配置')
  } catch (cause) { if (moduleCurrent(request, moduleId, accessToken)) message.error(explain(cause)) }
  finally { if (moduleCurrent(request, moduleId, accessToken)) saving.value = false }
}

async function removePublished() {
  if (!selected.value?.extension || detailLoading.value || saving.value) return
  const moduleId = selected.value.moduleId
  const accessToken = token.value
  const request = moduleRequest
  saving.value = true
  try {
    await api(`/api/admin/tenant-extensions/modules/${moduleId}/remove`, {
      method: 'POST', body: JSON.stringify({ expectedVersion: selected.value.extension.version }),
    }, accessToken)
    if (!moduleCurrent(request, moduleId, accessToken)) return
    message.success('本租户扩展已移除，运行态已自动回退主配置；导航层级保持不变')
    await choose(moduleId)
  } catch (cause) { if (moduleCurrent(request, moduleId, accessToken)) message.error(explain(cause)) }
  finally { if (moduleCurrent(request, moduleId, accessToken)) saving.value = false }
}

async function rollback(versionId: number) {
  if (!selected.value?.extension || detailLoading.value || saving.value) return
  const moduleId = selected.value.moduleId
  const accessToken = token.value
  const request = moduleRequest
  saving.value = true
  try {
    await api(`/api/admin/tenant-extensions/modules/${moduleId}/rollback`, {
      method: 'POST', body: JSON.stringify({
        targetVersionId: versionId, expectedVersion: selected.value.extension.version,
      }),
    }, accessToken)
    if (!moduleCurrent(request, moduleId, accessToken)) return
    message.success('租户扩展发布指针已回滚')
    await choose(moduleId)
  } catch (cause) { if (moduleCurrent(request, moduleId, accessToken)) message.error(explain(cause)) }
  finally { if (moduleCurrent(request, moduleId, accessToken)) saving.value = false }
}

function explain(cause: unknown) {
  return cause instanceof ApiError
    ? `${cause.message}（${cause.code}${cause.traceId ? `，追踪号 ${cause.traceId}` : ''}）`
    : '租户扩展操作失败'
}

watch(() => fieldForm.fieldType, () => {
  if (!editingFieldCode.value) { fieldForm.dictionaryId = undefined; fieldForm.referenceModuleId = undefined }
}, { flush: 'sync' })
watch(fieldDrawer, open => {
  if (!open) { editingFieldCode.value = undefined; retainedFieldConfig.value = {}; fieldForm.code = '' }
}, { flush: 'sync' })
watch(actionDrawer, open => {
  if (!open) { editingActionCode.value = undefined; Object.assign(actionForm, { name: '', location: 'ROW', parameterFieldCodes: [], sortOrder: 10 }) }
}, { flush: 'sync' })

function clearContext() {
  loadRequest++; directoryRequest++; moduleRequest++
  fieldDrawer.value = false
  actionDrawer.value = false
  checkModal.value = false
  publication.value = undefined
  selectedId.value = undefined
  modules.value = []
  extensionFields.value = []
  extensionActions.value = []
  actionsLoaded.value = false
  usedActionCodes.clear()
  formFieldCodes.value = []
  listFieldCodes.value = []
  selectedGrantIds.value = []
  dictionaries.value = []
  managedModules.value = { groups: [], modules: [] }
  runtimeModules.value = []
  dictionaryError.value = ''; moduleDirectoryError.value = ''
  loading.value = false; detailLoading.value = false; directoryLoading.value = false; saving.value = false
}
watch(() => [systemContext.value?.systemId, systemContext.value?.tenantId, token.value], () => {
  clearContext()
  if (token.value) void load()
}, { flush: 'sync' })
onMounted(load)
onBeforeUnmount(clearContext)
</script>

<template>
  <section class="tenant-extension panel-card">
    <div class="page-heading compact-heading">
      <div><p class="eyebrow">系统后台 · 模板配置</p><h2>工作空间模板扩展</h2><p>系统公共配置保持只读；当前工作空间可增加自己的字段、手动动作、页面内容和应用权限绑定。</p></div>
      <a-button :loading="loading" @click="load()"><ReloadOutlined />刷新</a-button>
    </div>
    <a-alert v-if="unavailable" type="info" show-icon message="当前是主工作空间" description="请在“模块配置”维护系统公共模板；切换到其他工作空间后可增加专属字段、动作和页面内容。" />
    <a-alert v-else-if="error" type="error" show-icon :message="error" />
    <div v-else class="extension-layout">
      <aside class="extension-modules">
        <button v-for="item in modules" :key="item.moduleId" :class="{ active: selectedId === item.moduleId }" @click="choose(item.moduleId)">
          <span><strong>{{ item.moduleName }}</strong><small>{{ versionLabel(item.baseVersionNumber) }}公共配置</small></span>
          <a-tag :color="item.extension ? productStatus(item.extension.status).color : 'default'">{{ item.extension ? productStatus(item.extension.status).label : '使用公共配置' }}</a-tag>
        </button>
        <a-empty v-if="!modules.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="系统还没有已发布模块" />
      </aside>
      <main v-if="selected" class="extension-editor">
        <div class="extension-summary">
          <span><strong>{{ selected.moduleName }}</strong><small>公共来源：{{ userFacingWorkspaceName(selected.baseTenantName) }} · {{ versionLabel(selected.baseVersionNumber) }}</small></span>
          <span><a-tag color="blue">系统公共</a-tag><a-tag color="purple">当前工作空间</a-tag></span>
        </div>
        <a-tabs>
          <a-tab-pane key="fields" tab="字段与来源">
            <div class="section-action"><p>系统公共字段的类型、安全规则和必填属性不可修改。</p><a-button :disabled="detailLoading" @click="beginField"><PlusOutlined />添加专属字段</a-button></div>
            <div class="source-list">
              <div v-for="field in previewFields" :key="`${field.source}-${field.code}`">
                <a-tag :color="field.source === 'MAIN' ? 'blue' : 'purple'">{{ field.source === 'MAIN' ? '系统公共' : '当前工作空间' }}</a-tag>
                <span><strong>{{ field.name }}</strong><small>{{ fieldTypeLabel(field.fieldType) }}</small></span>
                <a-tag v-if="field.mandatory" color="red">公共必填 · 只读</a-tag>
                <a-tag v-else-if="field.required" color="orange">当前工作空间必填</a-tag>
                <a-button v-if="field.source === 'TENANT'" type="link" :disabled="detailLoading" @click="editField(field)">编辑</a-button><a-button v-if="field.source === 'TENANT'" type="link" danger :disabled="detailLoading" @click="removeDraftField(field.code)">移除草稿</a-button>
              </div>
            </div>
          </a-tab-pane>
          <a-tab-pane key="actions" tab="手动动作">
            <div class="section-action"><p>执行时填写所选字段；保存草稿并发布后生效。系统公共动作不可替换或移除。</p><a-button :disabled="detailLoading || saving || !actionsLoaded" @click="beginAction"><PlusOutlined />添加专属动作</a-button></div>
            <a-alert v-if="!detailLoading && !actionsLoaded" type="warning" show-icon message="动作配置暂不可用，请刷新后重试；保存其他配置会保留已有动作。" />
            <div class="source-list">
              <div v-for="action in previewActions" :key="`${action.source}-${action.code}`">
                <a-tag :color="action.source === 'MAIN' ? 'blue' : 'purple'">{{ action.source === 'MAIN' ? '系统公共' : '当前工作空间' }}</a-tag>
                <span><strong>{{ action.name }}</strong><small>{{ actionLocationName(action.location) }} · {{ actionFieldNames(action) }}</small></span>
                <a-tag v-if="action.source === 'MAIN'">公共动作 · 只读</a-tag>
                <a-button v-if="action.source === 'TENANT' && action.actionType === 'CUSTOM'" type="link" :disabled="detailLoading || saving" @click="editAction(action)">编辑</a-button>
                <a-button v-if="action.source === 'TENANT'" type="link" danger :disabled="detailLoading || saving" @click="removeDraftAction(action.code)">移除草稿</a-button>
              </div>
            </div>
            <a-empty v-if="actionsLoaded && !previewActions.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="尚未配置手动动作" />
          </a-tab-pane>
          <a-tab-pane key="pages" tab="页面与视图">
            <a-alert type="info" show-icon message="系统公共导航、模块分组和菜单层级不会被工作空间配置覆盖。" />
            <a-form layout="vertical" class="page-overrides">
              <a-form-item label="表单页面字段" extra="主配置强制字段必须保留"><a-select v-model:value="formFieldCodes" mode="multiple" :options="fieldOptions" /></a-form-item>
              <a-form-item label="列表视图字段"><a-select v-model:value="listFieldCodes" mode="multiple" :options="fieldOptions" /></a-form-item>
            </a-form>
          </a-tab-pane>
          <a-tab-pane key="applications" tab="应用绑定">
            <a-alert type="warning" show-icon message="这里只能选择应用已经授予当前工作空间和当前模块的操作；绑定不会新增权限，也不能扩大字段范围。" />
            <div class="grant-groups">
              <section v-for="application in applicationGroups" :key="application.id">
                <strong>{{ application.name }}</strong><small>{{ application.grants.length }} 项可用权限</small>
                <a-checkbox-group v-model:value="selectedGrantIds">
                  <a-checkbox v-for="grant in application.grants" :key="grant.grantId" :value="grant.grantId">{{ actionLabel(grant.actionCode) }} · {{ grantFieldNames(grant.fieldCodes) }}</a-checkbox>
                </a-checkbox-group>
              </section>
              <a-empty v-if="!applicationGroups.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前工作空间没有可绑定的有效模块权限" />
            </div>
          </a-tab-pane>
          <a-tab-pane key="versions" tab="发布历史">
            <div class="version-list"><div v-for="version in selected.versions" :key="version.versionId"><span><strong>{{ versionLabel(version.versionNumber) }}</strong><small>{{ productDateTime(version.publishedAt) }}发布</small></span><a-tag v-if="version.current" color="green">当前使用</a-tag><a-button v-else size="small" @click="rollback(version.versionId)"><RollbackOutlined />恢复此版本</a-button></div><a-empty v-if="!selected.versions.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="尚未发布工作空间扩展" /></div>
          </a-tab-pane>
        </a-tabs>
        <div class="extension-footer">
          <span>{{ selected.extension ? `当前配置：${productStatus(selected.extension.status).label}` : '尚未创建专属配置' }}</span>
          <div><a-button v-if="selected.extension?.currentVersionId" danger :loading="saving" :disabled="detailLoading" @click="removePublished"><DeleteOutlined />删除并回退主配置</a-button><a-button :loading="saving" :disabled="detailLoading" @click="saveDraft"><SaveOutlined />保存草稿</a-button><a-button type="primary" :loading="saving" :disabled="detailLoading" @click="checkPublication"><CloudUploadOutlined />检查并发布</a-button></div>
        </div>
      </main>
      <a-empty v-else class="extension-editor" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="选择一个已发布模块" />
    </div>
  </section>

  <a-drawer v-model:open="fieldDrawer" :title="editingFieldCode ? '编辑当前工作空间字段' : '添加当前工作空间字段'" width="500">
    <a-form layout="vertical">
      <a-form-item label="字段名称" required><a-input v-model:value="fieldForm.name" placeholder="例如：客户等级" /></a-form-item>
      <a-form-item label="字段类型"><a-select v-if="!editingFieldCode" v-model:value="fieldForm.fieldType" :options="fieldTypeOptions" /><a-input v-else :value="fieldTypeLabel(fieldForm.fieldType)" disabled /></a-form-item>
      <a-form-item label="占位提示"><a-input v-model:value="fieldForm.placeholder" /></a-form-item>
      <div class="form-grid"><a-form-item label="排序"><a-input-number v-model:value="fieldForm.sortOrder" :min="0" /></a-form-item><a-form-item label="当前工作空间必填"><a-switch v-model:checked="fieldForm.required" /></a-form-item></div>
      <template v-if="['SINGLE_SELECT', 'STATUS', 'CASCADE'].includes(fieldForm.fieldType)">
        <a-alert v-if="dictionaryError" type="warning" show-icon :message="dictionaryError" />
        <a-form-item :label="fieldForm.fieldType === 'CASCADE' ? '层级字典' : '工作空间字典'" required extra="只读取当前工作空间的管理目录；发布时检查绑定字典有效且已发布。">
          <a-select v-model:value="fieldForm.dictionaryId" show-search allow-clear option-filter-prop="label" :loading="directoryLoading" :disabled="Boolean(dictionaryError) || directoryLoading" :options="dictionaryOptions" placeholder="选择字典" @change="fieldForm.referenceModuleId = undefined" />
        </a-form-item>
        <a-alert v-if="editingFieldCode && !fieldForm.dictionaryId && Array.isArray(retainedFieldConfig.options) && retainedFieldConfig.options.length" type="info" show-icon message="原有选项将保留；选择字典后改用字典选项。" />
        <a-alert v-if="fieldForm.fieldType === 'CASCADE' && fieldForm.referenceModuleId && !fieldForm.dictionaryId" type="info" show-icon message="原有模块层级绑定将保留；选择层级字典会替换数据源。" />
      </template>
      <template v-if="fieldForm.fieldType === 'CASCADE'">
        <div class="form-grid"><a-form-item label="最大层级"><a-input-number v-model:value="fieldForm.maxDepth" :min="1" :max="20" /></a-form-item><a-form-item label="允许选中间层"><a-switch v-model:checked="fieldForm.allowIntermediate" /></a-form-item></div>
        <a-form-item label="保存方式"><a-radio-group v-model:value="fieldForm.saveMode"><a-radio value="FINAL">最终项</a-radio><a-radio value="PATH">完整路径</a-radio></a-radio-group></a-form-item>
      </template>
      <template v-if="fieldForm.fieldType === 'REFERENCE'">
        <a-alert v-if="moduleDirectoryError" type="warning" show-icon :message="moduleDirectoryError" />
        <a-form-item label="关联模块" required extra="可绑定当前工作空间模块或已发布的系统公共模块；保存和发布时检查目标有效性。"><a-select v-model:value="fieldForm.referenceModuleId" show-search allow-clear option-filter-prop="label" :loading="directoryLoading" :disabled="directoryLoading" :options="referenceModuleOptions" placeholder="选择关联模块" /></a-form-item>
      </template>
      <a-button v-if="['SINGLE_SELECT', 'STATUS', 'CASCADE', 'REFERENCE'].includes(fieldForm.fieldType)" type="link" :loading="directoryLoading" @click="loadDirectories">刷新可选目录</a-button>
      <a-alert v-if="fieldBindingIssue" type="warning" show-icon :message="fieldBindingIssue" />
    </a-form>
    <template #footer><div class="drawer-footer"><a-button @click="fieldDrawer = false">取消</a-button><a-button type="primary" :disabled="Boolean(fieldBindingIssue)" @click="addField">{{ editingFieldCode ? '保存到草稿' : '加入草稿' }}</a-button></div></template>
  </a-drawer>
  <a-drawer v-model:open="actionDrawer" :title="editingActionCode ? '编辑当前工作空间动作' : '添加当前工作空间动作'" width="500">
    <a-form layout="vertical" :disabled="saving || detailLoading">
      <a-form-item label="动作名称" required><a-input v-model:value="actionForm.name" placeholder="例如：调整客户等级" /></a-form-item>
      <a-form-item label="出现位置" required><a-select v-model:value="actionForm.location" :options="actionLocationOptions" /></a-form-item>
      <a-form-item label="执行时填写" required extra="选择本模块可填写的字段；执行时仍会校验操作者的动作及字段权限。">
        <a-checkbox-group v-model:value="actionForm.parameterFieldCodes" :options="actionFieldOptions" class="action-field-options" />
        <a-empty v-if="!actionFieldOptions.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前模块没有可填写字段" />
      </a-form-item>
      <a-form-item label="排序"><a-input-number v-model:value="actionForm.sortOrder" :min="0" :precision="0" /></a-form-item>
      <a-alert v-if="actionValidationIssue" type="warning" show-icon :message="actionValidationIssue" />
    </a-form>
    <template #footer><div class="drawer-footer"><a-button @click="actionDrawer = false">取消</a-button><a-button type="primary" :disabled="saving || detailLoading || Boolean(actionValidationIssue)" @click="addAction">{{ editingActionCode ? '保存到草稿' : '加入草稿' }}</a-button></div></template>
  </a-drawer>
  <a-modal v-model:open="checkModal" title="租户扩展发布检查" :confirm-loading="saving" :ok-text="publication?.valid ? '确认发布' : '返回修改'" @ok="publication?.valid ? publish() : (checkModal = false)">
    <a-result v-if="publication?.valid" status="success" title="检查通过" sub-title="将生成不可变租户版本并原子切换运行指针；主导航不变。" />
    <a-result v-else status="error" title="发布被阻止"><template #subTitle><ul><li v-for="issue in publication?.issues" :key="`${issue.path}-${issue.code}`">{{ issue.message }}</li></ul></template></a-result>
  </a-modal>
</template>

<style scoped>
.action-field-options{display:flex;flex-direction:column;gap:10px}
.extension-layout{display:grid;grid-template-columns:270px 1fr;min-height:590px;border-top:1px solid #edf0f5}.extension-modules{padding:16px;border-right:1px solid #edf0f5}.extension-modules>button{width:100%;display:flex;align-items:center;justify-content:space-between;text-align:left;border:0;background:transparent;padding:12px;border-radius:10px;margin-bottom:8px;cursor:pointer}.extension-modules>button.active{background:#eef5ff;color:#0958d9}.extension-modules span,.extension-summary>span,.source-list span,.version-list span{display:flex;flex-direction:column}.extension-modules small,.extension-summary small,.source-list small,.version-list small,.grant-groups small{color:#8792a5;margin-top:3px}.extension-editor{padding:18px;min-width:0}.extension-summary,.section-action,.extension-footer{display:flex;align-items:center;justify-content:space-between;gap:16px}.section-action p{color:#667085}.source-list>div,.version-list>div{display:flex;align-items:center;gap:12px;padding:12px;border-bottom:1px solid #edf0f5}.source-list span,.version-list span{flex:1}.page-overrides{padding:18px 4px}.grant-groups section{display:grid;grid-template-columns:180px 1fr;gap:4px 18px;padding:16px 0;border-bottom:1px solid #edf0f5}.grant-groups .ant-checkbox-group{grid-column:2;display:flex;flex-direction:column;gap:10px}.extension-footer{position:sticky;bottom:0;background:#fff;border-top:1px solid #e8edf4;padding:14px 0 2px;margin-top:16px}.extension-footer>div{display:flex;gap:8px;flex-wrap:wrap}@media(max-width:900px){.extension-layout{grid-template-columns:1fr}.extension-modules{border-right:0;border-bottom:1px solid #edf0f5}.grant-groups section{grid-template-columns:1fr}.grant-groups .ant-checkbox-group{grid-column:1}.extension-summary,.section-action,.extension-footer,.source-list>div,.version-list>div{align-items:flex-start;flex-wrap:wrap}.extension-editor{padding:14px}.extension-footer{position:static}}
</style>
