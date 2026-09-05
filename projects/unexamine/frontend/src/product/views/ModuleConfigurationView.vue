<script setup lang="ts">
import {
  AppstoreAddOutlined,
  ApartmentOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  DragOutlined,
  HistoryOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { Empty, message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import { productDateTime } from '../presentation'
import { systemContext, systemTokens } from '../session'
import type {
  ConfiguredField,
  ConfiguredAction,
  ConfiguredModuleMenu,
  ConfiguredModuleRule,
  ConfiguredModule,
  ModuleDraft,
  ModuleOverview,
  DictionaryDraft,
  PublicationCheck,
  QueryIndexDraft,
  RuleIndexDraft,
  RuleTestResult,
  PublishedModuleVersion,
  RuntimeModuleConfiguration,
  AiSystemOverview,
} from '../types'

const emit = defineEmits<{ published: [] }>()
const router = useRouter()

const fieldTypes = [
  { type: 'TEXT', name: '单行文本', hint: '名称、编号等短文本' },
  { type: 'MULTILINE_TEXT', name: '多行文本', hint: '备注和较长说明' },
  { type: 'PHONE', name: '手机号', hint: '手机号格式校验' },
  { type: 'EMAIL', name: '邮箱', hint: '邮箱格式校验' },
  { type: 'URL', name: 'URL', hint: '网页地址' },
  { type: 'ID_CARD', name: '身份证号', hint: '证件格式与脱敏' },
  { type: 'NUMBER', name: '数字', hint: '普通数值' },
  { type: 'PERCENT', name: '百分比', hint: '百分比与精度' },
  { type: 'MONEY', name: '金额', hint: '精确金额数值' },
  { type: 'DATE', name: '日期', hint: '年月日' },
  { type: 'DATETIME', name: '日期时间', hint: '日期和具体时间' },
  { type: 'DATE_RANGE', name: '日期范围', hint: '开始和结束日期' },
  { type: 'TIME', name: '时间', hint: '时分秒' },
  { type: 'TIME_RANGE', name: '时间范围', hint: '开始和结束时间' },
  { type: 'SINGLE_SELECT', name: '单选', hint: '配置固定选项' },
  { type: 'MULTI_SELECT', name: '多选', hint: '选择多个字典项' },
  { type: 'CASCADE', name: '级联选择', hint: '树形字典或模块层级' },
  { type: 'STATUS', name: '状态', hint: '业务状态选项' },
  { type: 'BOOLEAN', name: '是否', hint: '是或否' },
  { type: 'MEMBER', name: '成员', hint: '关联当前租户成员' },
  { type: 'DEPARTMENT', name: '部门', hint: '关联当前租户部门' },
  { type: 'ORGANIZATION', name: '组织/租户', hint: '当前系统组织范围' },
  { type: 'ATTACHMENT', name: '附件', hint: '通用附件引用' },
  { type: 'IMAGE', name: '图片', hint: '图片预览与附件引用' },
  { type: 'FILE', name: '文件', hint: '单个文件引用' },
  { type: 'FILE_GROUP', name: '文件组', hint: '文件夹或多文件' },
  { type: 'AUTO_NUMBER', name: '自动编号', hint: '并发安全编号规则' },
  { type: 'REFERENCE', name: '关联数据', hint: '关联另一模块的业务记录' },
  { type: 'LOOKUP', name: '引用字段', hint: '从关联记录读取字段' },
  { type: 'SUBTABLE', name: '子表', hint: '一主多明细事务' },
  { type: 'ADDRESS', name: '地址', hint: '结构化地址' },
  { type: 'LOCATION', name: '地理位置', hint: '经纬度与名称' },
  { type: 'RATING', name: '评分', hint: '星级或分值' },
  { type: 'PROGRESS', name: '进度', hint: '0 到 100 进度' },
  { type: 'TAG', name: '标签', hint: '带颜色的多标签' },
  { type: 'BARCODE', name: '条码', hint: '条码内容与展示' },
  { type: 'QRCODE', name: '二维码', hint: '二维码内容与展示' },
  { type: 'SIGNATURE', name: '签名', hint: '签名文件引用' },
  { type: 'RICH_TEXT', name: '富文本', hint: '受控富文本内容' },
  { type: 'JSON', name: '结构化数据', hint: '保存成组的结构化内容' },
  { type: 'SECRET', name: '密码/密钥', hint: '敏感值与脱敏' },
  { type: 'SYSTEM_CREATED_BY', name: '创建人', hint: '只读系统字段' },
  { type: 'SYSTEM_CREATED_AT', name: '创建时间', hint: '只读系统字段' },
  { type: 'SYSTEM_UPDATED_BY', name: '更新人', hint: '只读系统字段' },
  { type: 'SYSTEM_UPDATED_AT', name: '更新时间', hint: '只读系统字段' },
  { type: 'FORMULA', name: '公式', hint: '表达式计算结果' },
  { type: 'SUMMARY', name: '汇总', hint: '关联记录汇总' },
  { type: 'CALCULATION', name: '计算', hint: '业务计算表达式' },
  { type: 'AGGREGATE', name: '聚合', hint: '关联模块聚合' },
  { type: 'AI_FILL', name: 'AI 填充', hint: '授权模型且人工确认' },
]

const loading = ref(false)
const saving = ref(false)
const overview = ref<ModuleOverview>({ groups: [], modules: [] })
const dictionaries = ref<DictionaryDraft[]>([])
const availableAiModels = ref<AiSystemOverview['availableModels']>([])
const selectedModuleId = ref<number>()
const draft = ref<ModuleDraft>()
const ruleIndexDraft = ref<RuleIndexDraft>({ rules: [], indexes: [] })
const error = ref('')
const draggingType = ref('')
const groupModal = ref(false)
const moduleModal = ref(false)
const fieldDrawer = ref(false)
const activeDesignerTab = ref('FIELDS')
type ModuleConfigTask = 'INFO' | 'FIELDS' | 'ACTIONS' | 'FLOW' | 'APPLICATION'

const activeConfigTask = ref<ModuleConfigTask>('INFO')
const infoSection = ref<'OVERVIEW' | 'PUBLICATION'>('OVERVIEW')
const actionDrawer = ref(false)
const menuDrawer = ref(false)
const pageModal = ref(false)
const ruleDrawer = ref(false)
const indexDrawer = ref(false)
const publishModal = ref(false)
const publication = ref<PublicationCheck>()
const versions = ref<PublishedModuleVersion[]>([])
const editingField = ref<ConfiguredField>()
const editingAction = ref<ConfiguredAction>()
const editingMenu = ref<ConfiguredModuleMenu>()
const editingRule = ref<ConfiguredModuleRule>()
const editingIndex = ref<QueryIndexDraft>()
const rulePreview = ref<RuleTestResult>()
const draggingPageField = ref('')
const previewDevice = ref<'DESKTOP' | 'MOBILE'>('DESKTOP')
const groupForm = reactive({ name: '', sortOrder: 10 })
const moduleForm = reactive({
  groupMode: 'existing' as 'existing' | 'new',
  groupId: undefined as number | undefined,
  groupName: '',
  name: '',
})
const fieldForm = reactive({
  code: '', name: '', fieldType: 'TEXT', required: false, uniqueValue: false, searchable: false,
  dictionaryId: undefined as number | undefined, referenceModuleId: undefined as number | undefined,
  sortOrder: 10, status: 'ACTIVE', optionsText: '', defaultValue: '', placeholder: '',
  currency: 'CNY', precision: 2, maxDepth: 3, allowIntermediate: false, saveMode: 'FINAL',
  sequenceCode: '', expression: '', modelCode: '', humanConfirmation: true, sensitive: false, readOnly: false,
  version: 0,
})
const actionForm = reactive({ location: 'ROW', confirmation: false, confirmationText: '', version: 0 })
type ConversionMapping = { targetCode: string; sourceCode: string }
type ConversionTarget = { moduleCode: string; mappings: ConversionMapping[] }
const conversionTargets = ref<ConversionTarget[]>([])
const conversionTargetDrafts = reactive<Record<string, ModuleDraft>>({})
const pageForm = reactive({ pageType: 'CUSTOM', name: '自定义页面' })
const menuForm = reactive({
  parentId: undefined as number | undefined, name: '', icon: 'appstore', routePath: '',
  sortOrder: 10, visible: true, status: 'ACTIVE' as 'ACTIVE' | 'DISABLED', version: 0,
})
type RuleConditionForm = { field: string; operator: string; value: string }
const ruleConditions = ref<RuleConditionForm[]>([])
const sampleValues = reactive<Record<string, string>>({})
const ruleForm = reactive({
  code: '', name: '', ruleType: 'VALIDATION', triggerEvent: 'CREATE', mode: 'ALL',
  effectType: 'BLOCK', targetField: '', message: '', sortOrder: 10,
  status: 'ACTIVE' as 'ACTIVE' | 'DISABLED', version: 0,
})
const indexFieldIds = ref<number[]>([])
const indexForm = reactive({
  code: '', name: '', uniqueIndex: false, status: 'ACTIVE' as 'ACTIVE' | 'DISABLED', version: 0,
})

const ruleTypeOptions = [
  { value: 'VALIDATION', label: '数据校验' }, { value: 'REQUIRED', label: '动态必填' },
  { value: 'VISIBILITY', label: '动态可见' }, { value: 'EDITABLE', label: '动态可编辑' },
  { value: 'APPROVAL', label: '进入审批' }, { value: 'DELETE_ROLE', label: '指定角色删除' },
]
const triggerEventOptions = [
  { value: 'CREATE', label: '新建记录时' }, { value: 'UPDATE', label: '更新记录时' },
  { value: 'DELETE', label: '删除记录时' }, { value: 'ALWAYS', label: '每次操作时' },
]
const conditionOperatorOptions = [
  { value: 'EQ', label: '等于' }, { value: 'NE', label: '不等于' },
  { value: 'GT', label: '大于' }, { value: 'GTE', label: '大于等于' },
  { value: 'LT', label: '小于' }, { value: 'LTE', label: '小于等于' },
  { value: 'IN', label: '属于' }, { value: 'CONTAINS', label: '包含' },
  { value: 'EMPTY', label: '为空' }, { value: 'NOT_EMPTY', label: '不为空' },
]

const token = computed(() => systemTokens.value?.accessToken || '')
const selectedModule = computed(() => overview.value.modules.find((item) => item.id === selectedModuleId.value))
const groupedModules = computed(() => overview.value.groups.map((group) => ({
  ...group,
  modules: overview.value.modules.filter((module) => module.groupId === group.id),
})))
const selectedPage = computed(() => draft.value?.pages.find((page) => page.pageType === activeDesignerTab.value))
const rawSelectedPageFieldCodes = computed(() => {
  if (!selectedPage.value) return [] as string[]
  try {
    const layout = JSON.parse(selectedPage.value.layoutJson) as { fieldCodes?: string[] }
    return layout.fieldCodes || []
  } catch { return [] }
})
const selectedPageFieldCodes = computed(() => {
  if (!selectedPage.value) return [] as string[]
  try {
    const layout = JSON.parse(selectedPage.value.layoutJson) as { fieldCodes?: string[] }
    return layout.fieldCodes || draft.value?.fields.filter((field) => field.status === 'ACTIVE').map((field) => field.code) || []
  } catch { return [] }
})
const selectedPageLayout = computed(() => {
  const fallback = {
    schemaVersion: 1,
    layoutType: selectedPage.value?.pageType || 'CUSTOM',
    fieldCodes: selectedPageFieldCodes.value,
    components: [] as Array<Record<string, unknown>>,
  }
  if (!selectedPage.value) return fallback
  try { return { ...fallback, ...JSON.parse(selectedPage.value.layoutJson) } }
  catch { return fallback }
})
const pageColumns = computed(() => Number(selectedPageLayout.value.components?.[0]?.columns || 2))
const pageSectionTitle = computed(() => String(selectedPageLayout.value.components?.[0]?.title || selectedPage.value?.name || '主要信息'))
const pageDensity = computed(() => String(selectedPageLayout.value.components?.[0]?.density || 'comfortable'))
const standardPageType = computed(() => Boolean(selectedPage.value && ['LIST', 'FORM', 'DETAIL'].includes(selectedPage.value.pageType)))
const pagePreviewFields = computed(() => {
  const byCode = new Map((draft.value?.fields || []).map(field => [field.code, field]))
  return selectedPageFieldCodes.value.map(code => byCode.get(code)).filter((field): field is ConfiguredField => Boolean(field))
})
const pageLayoutIssues = computed(() => {
  if (!selectedPage.value) return [] as string[]
  const issues: string[] = []
  try { JSON.parse(selectedPage.value.layoutJson) } catch { issues.push('页面布局配置无法读取，请重新保存当前页面') }
  const activeCodes = new Set((draft.value?.fields || []).filter(field => field.status === 'ACTIVE').map(field => field.code))
  rawSelectedPageFieldCodes.value.filter(code => !activeCodes.has(code)).forEach(() => issues.push('页面包含已移除或停用的字段'))
  if (standardPageType.value && !pagePreviewFields.value.length) issues.push(`${selectedPage.value.name}至少需要一个有效字段`)
  if (!standardPageType.value && !selectedPageLayout.value.components?.some((component: Record<string, unknown>) => component.type && component.type !== 'SECTION')) {
    issues.push(`${selectedPage.value.name}必须先配置专属内容，不能使用通用表单占位`)
  }
  return issues
})
const dictionaryOptions = computed(() => dictionaries.value.map(entry => ({
  value: entry.dictionary.id,
  label: `${entry.dictionary.name}${entry.currentVersionId ? ` · 已发布版本 ${entry.currentVersionNumber}` : ' · 未发布'}`,
})))
const referenceModuleOptions = computed(() => overview.value.modules
  .filter(module => module.id !== selectedModuleId.value)
  .map(module => ({ value: module.id, label: module.name })))
const conversionModuleOptions = computed(() => overview.value.modules
  .filter(module => module.id !== selectedModuleId.value)
  .map(module => ({ value: module.code, label: module.name })))
const activeFieldOptions = computed(() => (draft.value?.fields || []).filter(field => field.status === 'ACTIVE')
  .map(field => ({ value: field.code, label: field.name })))
const indexFieldOptions = computed(() => (draft.value?.fields || []).filter(field => field.status === 'ACTIVE'
  && !['RICH_TEXT', 'JSON', 'SECRET', 'ATTACHMENT', 'IMAGE', 'FILE', 'FILE_GROUP', 'SUBTABLE', 'SIGNATURE'].includes(field.fieldType))
  .map(field => ({ value: field.id, label: field.name })))
const ruleNeedsTarget = computed(() => ['REQUIRE_FIELD', 'SET_VISIBILITY', 'SET_EDITABLE', 'REQUIRE_APPROVAL'].includes(ruleForm.effectType))
const fieldConfigurationIssue = computed(() => {
  if (!fieldForm.name.trim()) return '请填写字段名称'
  if (['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS', 'TAG'].includes(fieldForm.fieldType)
      && !fieldForm.dictionaryId && !fieldForm.optionsText.trim()) return '选项字段必须绑定字典或配置手动选项'
  if (fieldForm.fieldType === 'CASCADE') {
    if (Boolean(fieldForm.dictionaryId) === Boolean(fieldForm.referenceModuleId)) return '级联字段必须且只能选择层级字典或模块数据源之一'
    if (fieldForm.maxDepth < 1) return '最大层级必须大于 0'
  }
  if (fieldForm.fieldType === 'MONEY' && (!fieldForm.currency.trim() || fieldForm.precision < 0 || fieldForm.precision > 6)) return '金额字段需要币种，精度范围为 0 到 6'
  if (['REFERENCE', 'LOOKUP', 'SUBTABLE', 'SUMMARY', 'AGGREGATE'].includes(fieldForm.fieldType) && !fieldForm.referenceModuleId) return '该字段类型必须选择目标模块'
  if (fieldForm.fieldType === 'AUTO_NUMBER' && !fieldForm.sequenceCode.trim()) return '自动编号字段必须配置编号规则'
  if (['FORMULA', 'CALCULATION'].includes(fieldForm.fieldType) && !fieldForm.expression.trim()) return '公式或计算字段必须配置表达式'
  if (fieldForm.fieldType === 'AI_FILL' && (!fieldForm.modelCode.trim() || !fieldForm.humanConfirmation)) return 'AI 填充必须选择授权模型并启用人工确认'
  if (fieldForm.fieldType.startsWith('SYSTEM_') && !fieldForm.readOnly) return '系统字段必须只读'
  return ''
})

async function loadOverview(preferredModuleId?: number) {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    overview.value = await api<ModuleOverview>('/api/admin/module-config', {}, token.value)
    try {
      dictionaries.value = await api<DictionaryDraft[]>('/api/admin/dictionaries', {}, token.value)
    } catch (cause) {
      if (!(cause instanceof ApiError) || cause.code !== 'PERMISSION_DENIED') throw cause
      dictionaries.value = []
    }
    try {
      availableAiModels.value = (await api<AiSystemOverview>('/api/admin/system/ai', {}, token.value)).availableModels
    } catch {
      availableAiModels.value = []
    }
    const target = preferredModuleId ?? selectedModuleId.value ?? overview.value.modules[0]?.id
    if (target) await selectModule(target)
    else draft.value = undefined
  } catch (reason) {
    error.value = readable(reason)
  } finally {
    loading.value = false
  }
}

async function selectModule(moduleId: number) {
  selectedModuleId.value = moduleId
  error.value = ''
  try {
    const [currentDraft, history, rulesIndexes] = await Promise.all([
      api<ModuleDraft>(`/api/admin/module-config/modules/${moduleId}/draft`, {}, token.value),
      api<PublishedModuleVersion[]>(`/api/admin/module-config/modules/${moduleId}/versions`, {}, token.value),
      api<RuleIndexDraft>(`/api/admin/module-config/modules/${moduleId}/rules-indexes`, {}, token.value),
    ])
    draft.value = currentDraft
    versions.value = history
    ruleIndexDraft.value = rulesIndexes
  } catch (reason) {
    error.value = readable(reason)
  }
}

async function createGroup() {
  saving.value = true
  try {
    await api('/api/admin/module-config/groups', {
      method: 'POST', body: JSON.stringify(groupForm),
    }, token.value)
    groupModal.value = false
    Object.assign(groupForm, { name: '', sortOrder: 10 })
    message.success('模块组已创建')
    await loadOverview()
  } catch (reason) {
    message.error(readable(reason))
  } finally {
    saving.value = false
  }
}

function beginModule() {
  Object.assign(moduleForm, {
    groupMode: overview.value.groups.length ? 'existing' : 'new',
    groupId: overview.value.groups[0]?.id,
    groupName: '',
    name: '',
  })
  moduleModal.value = true
}

async function createModule() {
  if (!moduleForm.name.trim()) return message.warning('请填写模块名称')
  if (moduleForm.groupMode === 'existing' && !moduleForm.groupId) return message.warning('请选择业务分组')
  if (moduleForm.groupMode === 'new' && !moduleForm.groupName.trim()) return message.warning('请填写新业务分组名称')
  saving.value = true
  try {
    const result = await api<ModuleDraft>('/api/admin/module-config/modules', {
      method: 'POST', body: JSON.stringify({
        name: moduleForm.name.trim(),
        groupId: moduleForm.groupMode === 'existing' ? moduleForm.groupId : undefined,
        groupName: moduleForm.groupMode === 'new' ? moduleForm.groupName.trim() : undefined,
      }),
    }, token.value)
    moduleModal.value = false
    Object.assign(moduleForm, { groupMode: overview.value.groups.length ? 'existing' : 'new', groupId: overview.value.groups[0]?.id, groupName: '', name: '' })
    activeConfigTask.value = 'FIELDS'
    activeDesignerTab.value = 'FIELDS'
    message.success('模块骨架已创建，列表、表单、详情和常用动作已准备好')
    await loadOverview(result.module.id)
  } catch (reason) {
    message.error(readable(reason))
  } finally {
    saving.value = false
  }
}

function beginField(type: string) {
  if (!selectedModuleId.value) {
    message.warning('请先创建并选择一个模块')
    return
  }
  editingField.value = undefined
  Object.assign(fieldForm, {
    code: '', name: '', fieldType: type, required: false, uniqueValue: false, searchable: false,
    dictionaryId: undefined, referenceModuleId: undefined,
    sortOrder: (draft.value?.fields.length || 0) * 10 + 10,
    status: 'ACTIVE', optionsText: ['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS', 'TAG'].includes(type) ? 'active|启用\ninactive|停用' : '',
    defaultValue: '', placeholder: '', currency: 'CNY', precision: 2, maxDepth: 3, allowIntermediate: false,
    saveMode: 'FINAL', sequenceCode: '', expression: '', modelCode: '', humanConfirmation: true,
    sensitive: type === 'SECRET', readOnly: type.startsWith('SYSTEM_'), version: 0,
  })
  fieldDrawer.value = true
}

function editField(field: ConfiguredField) {
  editingField.value = field
  let optionsText = ''
  let config: Record<string, any> = {}
  try {
    config = JSON.parse(field.configJson) as Record<string, any>
    optionsText = config.options?.map((option: { value?: string; code?: string; label?: string }) => `${option.value || option.code}|${option.label || option.value || option.code}`).join('\n') || ''
  } catch { /* invalid drafts are reported by publication check */ }
  Object.assign(fieldForm, {
    code: field.code, name: field.name, fieldType: field.fieldType, required: field.required,
    uniqueValue: Boolean(field.uniqueValue), searchable: Boolean(field.searchable),
    dictionaryId: field.dictionaryId, referenceModuleId: field.referenceModuleId,
    sortOrder: field.sortOrder, status: field.status, optionsText,
    defaultValue: config.defaultValue ?? '', placeholder: config.placeholder ?? '', currency: config.currency || 'CNY',
    precision: config.precision ?? 2, maxDepth: config.maxDepth ?? 3,
    allowIntermediate: Boolean(config.allowIntermediate), saveMode: config.saveMode || 'FINAL',
    sequenceCode: config.sequenceCode || '', expression: config.expression || '', modelCode: config.modelCode || '',
    humanConfirmation: config.humanConfirmation !== false, sensitive: Boolean(config.sensitive) || field.fieldType === 'SECRET',
    readOnly: Boolean(config.readOnly) || field.fieldType.startsWith('SYSTEM_'), version: field.version,
  })
  fieldDrawer.value = true
}

function fieldConfig() {
  const config: Record<string, unknown> = {
    placeholder: fieldForm.placeholder || undefined,
    defaultValue: fieldForm.defaultValue || undefined,
    sensitive: fieldForm.sensitive,
    readOnly: fieldForm.readOnly,
  }
  if (['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS', 'TAG'].includes(fieldForm.fieldType) && !fieldForm.dictionaryId) {
    config.options = fieldForm.optionsText.split('\n').map((line) => line.trim()).filter(Boolean).map((line) => {
      const [value, label] = line.split('|').map((item) => item?.trim())
      return { value, label: label || value, status: 'ACTIVE' }
    })
  }
  if (fieldForm.fieldType === 'MONEY') Object.assign(config, { currency: fieldForm.currency, precision: fieldForm.precision })
  if (fieldForm.fieldType === 'CASCADE') Object.assign(config, { maxDepth: fieldForm.maxDepth, allowIntermediate: fieldForm.allowIntermediate, saveMode: fieldForm.saveMode })
  if (fieldForm.fieldType === 'AUTO_NUMBER') config.sequenceCode = fieldForm.sequenceCode
  if (['FORMULA', 'CALCULATION'].includes(fieldForm.fieldType)) config.expression = fieldForm.expression
  if (fieldForm.fieldType === 'AI_FILL') Object.assign(config, { modelCode: fieldForm.modelCode, humanConfirmation: fieldForm.humanConfirmation })
  return config
}

async function saveField() {
  if (!selectedModuleId.value) return
  if (fieldConfigurationIssue.value) return message.warning(fieldConfigurationIssue.value)
  saving.value = true
  try {
    if (editingField.value) {
      await api(`/api/admin/module-config/modules/${selectedModuleId.value}/fields/${editingField.value.id}`, {
        method: 'PUT', body: JSON.stringify({
          name: fieldForm.name, required: fieldForm.required, sortOrder: fieldForm.sortOrder,
          uniqueValue: fieldForm.uniqueValue, searchable: fieldForm.searchable,
          dictionaryId: fieldForm.dictionaryId, referenceModuleId: fieldForm.referenceModuleId,
          status: fieldForm.status, config: fieldConfig(), version: fieldForm.version,
        }),
      }, token.value)
    } else {
      await api(`/api/admin/module-config/modules/${selectedModuleId.value}/fields`, {
        method: 'POST', body: JSON.stringify({
          name: fieldForm.name, fieldType: fieldForm.fieldType,
          required: fieldForm.required, uniqueValue: fieldForm.uniqueValue, searchable: fieldForm.searchable,
          dictionaryId: fieldForm.dictionaryId, referenceModuleId: fieldForm.referenceModuleId,
          sortOrder: fieldForm.sortOrder, config: fieldConfig(),
        }),
      }, token.value)
    }
    fieldDrawer.value = false
    message.success(editingField.value ? '字段配置已更新' : '字段已加入草稿')
    await loadOverview(selectedModuleId.value)
  } catch (reason) {
    message.error(readable(reason))
  } finally {
    saving.value = false
  }
}

async function savePageLayout(fieldCodes: string[], columns = pageColumns.value, properties: Record<string, unknown> = {}) {
  if (!selectedModuleId.value || !selectedPage.value) return
  const activeCodes = new Set((draft.value?.fields || []).filter(field => field.status === 'ACTIVE').map(field => field.code))
  const invalid = fieldCodes.find(code => !activeCodes.has(code))
  if (invalid) {
    message.error(`字段引用 ${invalid} 不存在或已停用，请先移除后再保存`)
    return
  }
  saving.value = true
  try {
    const layout = {
      ...selectedPageLayout.value,
      schemaVersion: 1,
      layoutType: selectedPage.value.pageType,
      fieldCodes,
      components: [{
        ...(selectedPageLayout.value.components?.[0] || {}),
        id: 'main',
        type: selectedPage.value.pageType === 'LIST' ? 'TABLE' : 'SECTION',
        title: pageSectionTitle.value,
        columns,
        fieldCodes,
        density: pageDensity.value,
        ...properties,
      }],
    }
    await api(`/api/admin/module-config/modules/${selectedModuleId.value}/pages/${selectedPage.value.pageType}`, {
      method: 'PUT', body: JSON.stringify({ layout, version: selectedPage.value.version }),
    }, token.value)
    message.success(`${selectedPage.value.name}已更新`)
    await loadOverview(selectedModuleId.value)
  } catch (reason) {
    message.error(readable(reason))
  } finally { saving.value = false }
}

async function savePageProperty(property: string, value: unknown) {
  await savePageLayout(selectedPageFieldCodes.value, pageColumns.value, { [property]: value })
}

async function togglePageField(code: string, checked: boolean) {
  const next = checked
    ? [...new Set([...selectedPageFieldCodes.value, code])]
    : selectedPageFieldCodes.value.filter((item) => item !== code)
  await savePageLayout(next)
}

async function movePageField(targetCode: string) {
  const source = draggingPageField.value
  draggingPageField.value = ''
  if (!source || source === targetCode) return
  const next = [...selectedPageFieldCodes.value]
  const sourceIndex = next.indexOf(source)
  const targetIndex = next.indexOf(targetCode)
  if (sourceIndex < 0 || targetIndex < 0) return
  const [moved] = next.splice(sourceIndex, 1)
  if (!moved) return
  next.splice(targetIndex, 0, moved)
  await savePageLayout(next)
}

async function createPage() {
  if (!selectedModuleId.value) return
  saving.value = true
  try {
    await api(`/api/admin/module-config/modules/${selectedModuleId.value}/pages`, {
      method: 'POST', body: JSON.stringify({
        pageType: pageForm.pageType,
        name: pageForm.name,
        layout: { schemaVersion: 1, layoutType: pageForm.pageType, fieldCodes: [], components: [] },
      }),
    }, token.value)
    pageModal.value = false
    activeDesignerTab.value = pageForm.pageType
    message.success(`${pageForm.name}已加入页面草稿`)
    await loadOverview(selectedModuleId.value)
  } catch (reason) { message.error(readable(reason)) }
  finally { saving.value = false }
}

async function deleteSelectedPage() {
  if (!selectedModuleId.value || !selectedPage.value || standardPageType.value) return
  saving.value = true
  const deletedName = selectedPage.value.name
  try {
    await api(`/api/admin/module-config/modules/${selectedModuleId.value}/pages/${selectedPage.value.pageType}?version=${selectedPage.value.version}`, {
      method: 'DELETE',
    }, token.value)
    activeDesignerTab.value = 'FIELDS'
    message.success(`${deletedName}已从页面草稿删除`)
    await loadOverview(selectedModuleId.value)
  } catch (reason) { message.error(readable(reason)) }
  finally { saving.value = false }
}

function editMenu(menu: ConfiguredModuleMenu) {
  editingMenu.value = menu
  Object.assign(menuForm, {
    parentId: menu.parentId, name: menu.name, icon: menu.icon || '', routePath: menu.routePath,
    sortOrder: menu.sortOrder, visible: menu.visible, status: menu.status, version: menu.version,
  })
  menuDrawer.value = true
}

async function saveMenu() {
  if (!selectedModuleId.value || !editingMenu.value) return
  saving.value = true
  try {
    await api(`/api/admin/module-config/modules/${selectedModuleId.value}/menus/${editingMenu.value.id}`, {
      method: 'PUT', body: JSON.stringify(menuForm),
    }, token.value)
    menuDrawer.value = false
    message.success('运行菜单草稿已更新；发布前不影响普通用户导航')
    await loadOverview(selectedModuleId.value)
  } catch (reason) { message.error(readable(reason)) }
  finally { saving.value = false }
}

function editAction(action: ConfiguredAction) {
  editingAction.value = action
  let config: { confirmation?: boolean; confirmationText?: string; targetModuleCode?: string; fieldMappings?: Record<string, string>; targets?: Array<{ moduleCode: string; fieldMappings?: Record<string, string> }> } = {}
  try { config = JSON.parse(action.configJson) } catch { /* publication check reports invalid JSON */ }
  Object.assign(actionForm, {
    location: action.location,
    confirmation: Boolean(config.confirmation),
    confirmationText: config.confirmationText || '',
    version: action.version,
  })
  const targets = config.targets || (config.targetModuleCode ? [{ moduleCode: config.targetModuleCode, fieldMappings: config.fieldMappings }] : [])
  conversionTargets.value = targets.map(target => ({
    moduleCode: target.moduleCode,
    mappings: Object.entries(target.fieldMappings || {}).map(([targetCode, sourceCode]) => ({ targetCode, sourceCode })),
  }))
  conversionTargets.value.forEach(target => void loadConversionTarget(target.moduleCode))
  if (action.code === 'CONVERT' && !conversionTargets.value.length) addConversionTarget()
  actionDrawer.value = true
}

function addConversionTarget() {
  conversionTargets.value.push({ moduleCode: '', mappings: [{ targetCode: '', sourceCode: '' }] })
}

async function loadConversionTarget(moduleCode: string) {
  if (!moduleCode || conversionTargetDrafts[moduleCode]) return
  const module = overview.value.modules.find(item => item.code === moduleCode)
  if (!module) return
  try {
    conversionTargetDrafts[moduleCode] = await api<ModuleDraft>(`/api/admin/module-config/modules/${module.id}/draft`, {}, token.value)
  } catch (reason) {
    message.error(readable(reason))
  }
}

function conversionTargetFieldOptions(moduleCode: string) {
  return (conversionTargetDrafts[moduleCode]?.fields || [])
    .filter(field => field.status === 'ACTIVE')
    .map(field => ({ value: field.code, label: field.name }))
}

function addConversionMapping(target: ConversionTarget) {
  target.mappings.push({ targetCode: '', sourceCode: '' })
}

function conversionMappings(target: ConversionTarget) {
  if (!target.mappings.length || target.mappings.some(mapping => !mapping.targetCode || !mapping.sourceCode)) {
    throw new Error('请完成目标字段与来源字段的对应关系')
  }
  if (new Set(target.mappings.map(mapping => mapping.targetCode)).size !== target.mappings.length) {
    throw new Error('同一个目标字段只能配置一次')
  }
  return Object.fromEntries(target.mappings.map(mapping => [mapping.targetCode, mapping.sourceCode]))
}

async function saveAction() {
  if (!selectedModuleId.value || !editingAction.value) return
  saving.value = true
  try {
    const config: Record<string, unknown> = {
      confirmation: actionForm.confirmation, confirmationText: actionForm.confirmationText,
    }
    if (editingAction.value.code === 'CONVERT') {
      if (!conversionTargets.value.length || conversionTargets.value.some(target => !target.moduleCode)) {
        throw new Error('请至少配置一个完整的目标模块')
      }
      if (new Set(conversionTargets.value.map(target => target.moduleCode)).size !== conversionTargets.value.length) {
        throw new Error('同一目标模块只能配置一次')
      }
      config.targets = conversionTargets.value.map(target => ({
        moduleCode: target.moduleCode, fieldMappings: conversionMappings(target),
      }))
    }
    await api(`/api/admin/module-config/modules/${selectedModuleId.value}/actions/${editingAction.value.code}`, {
      method: 'PUT', body: JSON.stringify({
        location: actionForm.location,
        config,
        version: actionForm.version,
      }),
    }, token.value)
    actionDrawer.value = false
    message.success('动作配置已更新')
    await loadOverview(selectedModuleId.value)
  } catch (reason) {
    message.error(reason instanceof Error ? reason.message : readable(reason))
  } finally { saving.value = false }
}

function addRuleCondition() {
  ruleConditions.value.push({ field: activeFieldOptions.value[0]?.value || '', operator: 'EQ', value: '' })
}

function beginRule() {
  editingRule.value = undefined
  rulePreview.value = undefined
  Object.keys(sampleValues).forEach(key => delete sampleValues[key])
  Object.assign(ruleForm, {
    code: '', name: '', ruleType: 'VALIDATION', triggerEvent: 'CREATE', mode: 'ALL',
    effectType: 'BLOCK', targetField: '', message: '', sortOrder: 10, status: 'ACTIVE', version: 0,
  })
  ruleConditions.value = []
  addRuleCondition()
  ruleDrawer.value = true
}

function editRule(rule: ConfiguredModuleRule) {
  editingRule.value = rule
  rulePreview.value = undefined
  Object.keys(sampleValues).forEach(key => delete sampleValues[key])
  let definition: { mode?: string; conditions?: Array<{ field?: string; operator?: string; value?: unknown }>; effect?: { type?: string; field?: string } } = {}
  try { definition = JSON.parse(rule.expressionText) } catch { /* 发布检查会阻止无效历史草稿 */ }
  Object.assign(ruleForm, {
    code: rule.code, name: rule.name, ruleType: rule.ruleType, triggerEvent: rule.triggerEvent,
    mode: definition.mode || 'ALL', effectType: definition.effect?.type || 'BLOCK',
    targetField: definition.effect?.field || '', message: rule.messageTemplate || '',
    sortOrder: rule.sortOrder, status: rule.status, version: rule.version,
  })
  ruleConditions.value = (definition.conditions || []).map(condition => ({
    field: condition.field || '', operator: condition.operator || 'EQ',
    value: condition.value == null ? '' : typeof condition.value === 'string' ? condition.value : JSON.stringify(condition.value),
  }))
  if (!ruleConditions.value.length) addRuleCondition()
  ruleConditions.value.forEach(condition => { sampleValues[condition.field] = '' })
  ruleDrawer.value = true
}

function typedRuleValue(value: string) {
  const trimmed = value.trim()
  if (trimmed === 'true') return true
  if (trimmed === 'false') return false
  if (trimmed && !Number.isNaN(Number(trimmed))) return Number(trimmed)
  if ((trimmed.startsWith('[') && trimmed.endsWith(']')) || (trimmed.startsWith('{') && trimmed.endsWith('}'))) {
    try { return JSON.parse(trimmed) } catch { return value }
  }
  return value
}

function structuredRuleDefinition() {
  return {
    mode: ruleForm.mode,
    conditions: ruleConditions.value.map(condition => ({
      field: condition.field, operator: condition.operator,
      ...(['EMPTY', 'NOT_EMPTY'].includes(condition.operator) ? {} : { value: typedRuleValue(condition.value) }),
    })),
    effect: { type: ruleForm.effectType, ...(ruleNeedsTarget.value ? { field: ruleForm.targetField } : {}) },
  }
}

async function saveRule() {
  if (!selectedModuleId.value || !ruleConditions.value.length) return
  if (ruleConditions.value.some(condition => !condition.field || !condition.operator)) return message.warning('每个条件都必须选择字段和运算符')
  if (ruleNeedsTarget.value && !ruleForm.targetField) return message.warning('请选择规则结果作用字段')
  saving.value = true
  try {
    const body = {
      name: ruleForm.name, ruleType: ruleForm.ruleType, triggerEvent: ruleForm.triggerEvent,
      definition: structuredRuleDefinition(), message: ruleForm.message || undefined,
      sortOrder: ruleForm.sortOrder,
      ...(editingRule.value ? { status: ruleForm.status, version: ruleForm.version } : {}),
    }
    await api(`/api/admin/module-config/modules/${selectedModuleId.value}/rules-indexes/rules${editingRule.value ? `/${editingRule.value.id}` : ''}`, {
      method: editingRule.value ? 'PUT' : 'POST', body: JSON.stringify(body),
    }, token.value)
    ruleDrawer.value = false
    message.success('结构化规则草稿已保存；发布前不影响业务数据')
    await selectModule(selectedModuleId.value)
  } catch (reason) { message.error(readable(reason)) }
  finally { saving.value = false }
}

async function previewRule() {
  if (!selectedModuleId.value || !editingRule.value) return message.warning('请先保存规则，再使用已保存草稿预演')
  saving.value = true
  try {
    const sampleFields = Object.fromEntries(ruleConditions.value.map(condition => [condition.field, typedRuleValue(sampleValues[condition.field] || '')]))
    rulePreview.value = await api<RuleTestResult>(
      `/api/admin/module-config/modules/${selectedModuleId.value}/rules-indexes/rules/${editingRule.value.id}/test`, {
        method: 'POST', body: JSON.stringify({ sampleFields }),
      }, token.value)
  } catch (reason) { message.error(readable(reason)) }
  finally { saving.value = false }
}

function beginIndex() {
  editingIndex.value = undefined
  Object.assign(indexForm, { code: '', name: '', uniqueIndex: false, status: 'ACTIVE', version: 0 })
  indexFieldIds.value = []
  indexDrawer.value = true
}

function editIndex(index: QueryIndexDraft) {
  editingIndex.value = index
  Object.assign(indexForm, {
    code: index.index.code, name: index.index.name, uniqueIndex: index.index.uniqueIndex,
    status: index.index.status, version: index.index.version,
  })
  indexFieldIds.value = [...index.fields].sort((a, b) => a.sortOrder - b.sortOrder).map(field => field.fieldId)
  indexDrawer.value = true
}

async function saveIndex() {
  if (!selectedModuleId.value || !indexFieldIds.value.length) return message.warning('至少选择一个索引字段')
  saving.value = true
  try {
    const body = {
      name: indexForm.name, uniqueIndex: indexForm.uniqueIndex,
      fields: indexFieldIds.value.map((fieldId, sortOrder) => ({ fieldId, sortOrder, sortDirection: 'ASC' })),
      ...(editingIndex.value ? { status: indexForm.status, version: indexForm.version } : {}),
    }
    await api(`/api/admin/module-config/modules/${selectedModuleId.value}/rules-indexes/indexes${editingIndex.value ? `/${editingIndex.value.index.id}` : ''}`, {
      method: editingIndex.value ? 'PUT' : 'POST', body: JSON.stringify(body),
    }, token.value)
    indexDrawer.value = false
    message.success('查询索引草稿已保存；投影和唯一约束会随发布版本生效')
    await selectModule(selectedModuleId.value)
  } catch (reason) { message.error(readable(reason)) }
  finally { saving.value = false }
}

async function checkPublication(openModal = true) {
  if (!selectedModuleId.value) return
  saving.value = true
  try {
    publication.value = await api<PublicationCheck>(
      `/api/admin/module-config/modules/${selectedModuleId.value}/publication-check`, {}, token.value)
    publishModal.value = openModal
  } catch (reason) {
    message.error(readable(reason))
  } finally {
    saving.value = false
  }
}

async function publish() {
  if (!selectedModuleId.value || !publication.value?.valid) return
  saving.value = true
  try {
    const result = await api<{ versionNumber: number }>(`/api/admin/module-config/modules/${selectedModuleId.value}/publish`, {
      method: 'POST', body: JSON.stringify({ expectedDraftRevision: publication.value.draftRevision }),
    }, token.value)
    publishModal.value = false
    message.success(`版本 ${result.versionNumber} 已发布，业务运行页已经切换并读回`)
    await loadOverview(selectedModuleId.value)
    emit('published')
    await router.push({
      path: `/systems/${systemContext.value?.systemId}`,
      query: { workspace: 'runtime', module: draft.value?.module.code },
    })
  } catch (reason) {
    message.error(readable(reason))
  } finally {
    saving.value = false
  }
}

async function rollbackVersion(version: PublishedModuleVersion) {
  if (!selectedModuleId.value || version.current) return
  saving.value = true
  try {
    const result = await api<RuntimeModuleConfiguration>(
      `/api/admin/module-config/modules/${selectedModuleId.value}/rollback`, {
        method: 'POST', body: JSON.stringify({
          targetVersionId: version.versionId,
          expectedPublicationVersion: version.publicationVersion,
        }),
      }, token.value)
    message.success(`已从历史快照生成版本 ${result.versionNumber}，目标与原版本均完整保留`)
    await loadOverview(selectedModuleId.value)
    emit('published')
  } catch (reason) {
    message.error(readable(reason))
  } finally { saving.value = false }
}

function onDrop() {
  if (draggingType.value) beginField(draggingType.value)
  draggingType.value = ''
}

function selectConfigTask(task: ModuleConfigTask) {
  activeConfigTask.value = task
  if (task === 'FIELDS') activeDesignerTab.value = 'FIELDS'
  if (task === 'ACTIONS') activeDesignerTab.value = 'ACTIONS'
}

function selectInfoSection(section: 'OVERVIEW' | 'PUBLICATION') {
  activeConfigTask.value = 'INFO'
  infoSection.value = section
  if (section === 'PUBLICATION') void checkPublication(false)
}

function openPublicationStep() {
  selectInfoSection('PUBLICATION')
}

function openRelatedConfiguration(section: 'flow' | 'applications') {
  void router.push(`/systems/${systemContext.value?.systemId}/admin?section=${section}`)
}

function readable(reason: unknown) {
  if (reason instanceof ApiError) return reason.traceId ? `${reason.message}（追踪号：${reason.traceId}）` : reason.message
  return '请求失败，请稍后重试'
}

function actionLocationName(location: string) {
  return ({
    MODULE_ENTRY: '模块入口', LIST_TOOLBAR: '列表上方', BATCH: '批量操作', ROW: '每行操作',
    DETAIL_HEADER: '详情页上方', DETAIL_MORE: '详情更多操作',
  } as Record<string, string>)[location] || location
}

function optionName(options: Array<{ value: string; label: string }>, value: string) {
  return options.find(option => option.value === value)?.label || '已配置'
}

function fieldName(code: string) {
  return draft.value?.fields.find(field => field.code === code)?.name || '已移除字段'
}

function fieldNames(codes: string[]) {
  return codes.map(fieldName).join(' → ')
}

onMounted(() => loadOverview())
</script>

<template>
  <div class="config-page">
    <div class="module-builder-toolbar">
      <div><strong>模板配置</strong><span>选择模块后，按模块信息、字段、动作、流程和应用五类任务完成配置。</span></div>
      <div class="heading-actions">
        <a-button :loading="loading" @click="loadOverview()"><ReloadOutlined />刷新</a-button>
        <a-button type="primary" @click="beginModule"><PlusOutlined />新建模块</a-button>
      </div>
    </div>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />

    <div :class="['config-layout', { 'config-layout--page': activeConfigTask === 'FIELDS' && selectedPage }]">
      <aside class="config-tree panel-card">
        <div class="panel-title"><strong>模块结构</strong><a-button type="text" size="small" @click="groupModal = true"><PlusOutlined />业务分组</a-button></div>
        <a-skeleton v-if="loading && !overview.groups.length" active :paragraph="{ rows: 5 }" />
        <template v-else>
          <section v-for="group in groupedModules" :key="group.id" class="module-group">
            <div class="module-group__name"><span>{{ group.name }}</span><small>{{ group.modules.length }}</small></div>
            <button v-for="module in group.modules" :key="module.id" type="button"
              :class="['module-tree-item', { active: selectedModuleId === module.id }]" @click="selectModule(module.id)">
              <AppstoreAddOutlined /><span>{{ module.name }}</span><a-badge :status="module.status === 'ACTIVE' ? 'success' : 'default'" />
            </button>
          </section>
          <a-empty v-if="!overview.groups.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="还没有业务模块" />
          <a-button block class="tree-create" @click="beginModule"><PlusOutlined />新建模块</a-button>
        </template>
      </aside>

      <main class="designer panel-card">
        <template v-if="draft">
          <div class="designer-heading">
            <div><span class="draft-state">{{ draft.published ? '已有运行版本' : '尚未发布' }}</span><h2>{{ draft.module.name }}</h2><p>{{ draft.published ? '当前修改保存在草稿，发布前不影响运行页' : '完成必要配置并通过检查后即可发布' }}</p></div>
            <a-tag :color="draft.published ? 'green' : 'default'">{{ draft.published ? '运行中' : '仅草稿' }}</a-tag>
          </div>
          <div class="config-task-tabs" role="tablist" aria-label="模块配置任务">
            <button :class="{ active: activeConfigTask === 'INFO' }" @click="selectConfigTask('INFO')"><strong>1</strong><span>模块信息<small>入口、状态、发布与版本</small></span></button>
            <button :class="{ active: activeConfigTask === 'FIELDS' }" @click="selectConfigTask('FIELDS')"><strong>2</strong><span>模块字段<small>字段与页面呈现</small></span></button>
            <button :class="{ active: activeConfigTask === 'ACTIONS' }" @click="selectConfigTask('ACTIONS')"><strong>3</strong><span>模块动作<small>动作、规则与索引</small></span></button>
            <button :class="{ active: activeConfigTask === 'FLOW' }" @click="selectConfigTask('FLOW')"><strong>4</strong><span>模块 Flow<small>触发、审批与异常</small></span></button>
            <button :class="{ active: activeConfigTask === 'APPLICATION' }" @click="selectConfigTask('APPLICATION')"><strong>5</strong><span>模块应用<small>受控开放与调用</small></span></button>
          </div>
          <div v-if="activeConfigTask === 'INFO'" class="designer-subtabs">
            <button :class="{ active: infoSection === 'OVERVIEW' }" @click="infoSection = 'OVERVIEW'">模块概览</button>
            <button :class="{ active: infoSection === 'PUBLICATION' }" @click="selectInfoSection('PUBLICATION')">发布与版本</button>
          </div>
          <div v-else-if="activeConfigTask === 'FIELDS'" class="designer-subtabs">
            <button :class="{ active: activeDesignerTab === 'FIELDS' }" @click="activeDesignerTab = 'FIELDS'">模块字段</button>
            <button v-for="page in draft.pages" :key="page.id" :class="{ active: activeDesignerTab === page.pageType }" @click="activeDesignerTab = page.pageType">{{ page.name }}</button>
            <button class="designer-tab-add" @click="pageModal = true"><PlusOutlined />新增页面</button>
          </div>
          <div v-else-if="activeConfigTask === 'ACTIONS'" class="designer-subtabs">
            <button :class="{ active: activeDesignerTab === 'ACTIONS' }" @click="activeDesignerTab = 'ACTIONS'">可用动作</button>
            <button :class="{ active: activeDesignerTab === 'RULES' }" @click="activeDesignerTab = 'RULES'">业务规则</button>
            <button :class="{ active: activeDesignerTab === 'INDEXES' }" @click="activeDesignerTab = 'INDEXES'">查询与唯一规则</button>
          </div>
          <section v-if="activeConfigTask === 'INFO' && infoSection === 'OVERVIEW'" class="module-task-overview">
            <div class="module-task-summary"><div><small>当前模块</small><strong>{{ draft.module.name }}</strong></div><div><small>字段</small><strong>{{ draft.fields.length }}</strong></div><div><small>页面</small><strong>{{ draft.pages.length }}</strong></div><div><small>动作</small><strong>{{ draft.actions.length }}</strong></div></div>
            <div class="module-task-guidance"><h3>模块骨架已准备好</h3><ol><li>列表、表单和详情页已经自动创建</li><li>新建、编辑、删除、导入和导出等常用功能已经准备好</li><li>内部稳定标识由系统维护，不需要用户记忆或复制</li></ol><a-button type="primary" @click="selectConfigTask('FIELDS')">下一步：添加业务字段</a-button></div>
            <div class="module-entry-list"><div class="panel-title"><strong>运行入口</strong><span>发布后显示在业务工作区</span></div><button v-for="menu in draft.menus" :key="menu.id" type="button" class="menu-config-row" @click="editMenu(menu)"><span class="menu-config-icon">{{ (menu.icon || 'M').slice(0, 1).toUpperCase() }}</span><span><strong>{{ menu.name }}</strong><small>{{ menu.visible && menu.status === 'ACTIVE' ? '发布后显示' : '当前隐藏' }}</small></span><span>配置</span></button></div>
          </section>
          <div v-else-if="activeConfigTask === 'FIELDS' && activeDesignerTab === 'FIELDS'" class="field-canvas" @dragover.prevent @drop="onDrop">
            <div class="field-task-toolbar"><div><strong>模块字段</strong><small>字段决定业务数据结构和页面可用内容</small></div><a-dropdown><a-button type="primary"><PlusOutlined />添加字段</a-button><template #overlay><a-menu class="field-type-menu" @click="beginField(String($event.key))"><a-menu-item v-for="field in fieldTypes" :key="field.type"><strong>{{ field.name }}</strong><small>{{ field.hint }}</small></a-menu-item></a-menu></template></a-dropdown></div>
            <button v-for="field in draft.fields" :key="field.id" type="button" class="configured-field" @click="editField(field)">
              <DragOutlined class="field-handle" />
              <span><strong>{{ field.name }}</strong><small>{{ field.searchable ? '可搜索' : '普通字段' }}</small></span>
              <a-tag>{{ fieldTypes.find((item) => item.type === field.fieldType)?.name || field.fieldType }}</a-tag>
              <em v-if="field.required">必填</em><em v-if="field.status !== 'ACTIVE'" class="muted">停用</em>
            </button>
            <button v-if="!draft.fields.length" type="button" class="empty-drop" @click="beginField('TEXT')">
              <PlusOutlined /><strong>添加第一个字段</strong><span>模块至少需要一个有效字段才能发布</span>
            </button>
          </div>
          <div v-else-if="activeConfigTask === 'FIELDS' && selectedPage" class="page-config-canvas">
            <div class="page-editor-toolbar">
              <div><strong>{{ selectedPage.name }} · 页面布局</strong><small>拖动已显示字段可调整顺序；发布前业务页面继续使用旧版本。</small></div>
              <div class="page-editor-actions"><a-segmented v-model:value="previewDevice" :options="[{ label: '桌面', value: 'DESKTOP' }, { label: '手机', value: 'MOBILE' }]" />
                <a-popconfirm v-if="!standardPageType" :title="`确认删除页面“${selectedPage.name}”？`" description="只删除当前页面草稿；已发布历史版本保持不变。" ok-text="确认删除" cancel-text="取消" @confirm="deleteSelectedPage">
                  <a-button danger :loading="saving"><DeleteOutlined />删除页面</a-button>
                </a-popconfirm>
                <a-button :disabled="saving || Boolean(pageLayoutIssues.length)" @click="savePageLayout(selectedPageFieldCodes)">保存草稿</a-button>
                <a-button type="primary" :disabled="Boolean(pageLayoutIssues.length)" @click="openPublicationStep">进入发布检查</a-button></div>
            </div>
            <a-alert v-if="pageLayoutIssues.length" type="error" show-icon class="section-alert" message="页面设计存在阻断项" :description="pageLayoutIssues.join('；')" />
            <div class="page-editor-grid">
              <section class="page-field-source">
                <p>页面字段</p>
                <label v-for="field in draft.fields.filter((item) => item.status === 'ACTIVE')" :key="field.id"
                  class="page-field-row" :class="{ selected: selectedPageFieldCodes.includes(field.code) }"
                  :draggable="selectedPageFieldCodes.includes(field.code)"
                  @dragstart="draggingPageField = field.code" @dragover.prevent @drop.prevent="movePageField(field.code)">
                  <DragOutlined />
                  <a-checkbox :checked="selectedPageFieldCodes.includes(field.code)" :disabled="saving" @change="togglePageField(field.code, $event.target.checked)" />
                  <span><strong>{{ field.name }}</strong><small>{{ field.required ? '必填' : '选填' }}</small></span>
                  <a-tag>{{ fieldTypes.find((item) => item.type === field.fieldType)?.name }}</a-tag>
                </label>
              </section>
              <section :class="['schema-preview', { 'schema-preview--mobile': previewDevice === 'MOBILE' }]">
                <div class="schema-preview__bar"><span></span><span></span><span></span><strong>实时预览</strong><a-tag>{{ previewDevice === 'MOBILE' ? '手机' : '桌面' }}</a-tag></div>
                <a-result v-if="!standardPageType" status="error" title="特殊页面尚未完成专属设计" sub-title="不能以通用表单或空抽屉替代；请先配置该页面所需的专属组件、状态和失败反馈。" />
                <div v-else-if="selectedPage.pageType === 'LIST'" class="preview-table">
                  <div class="preview-table__head"><span v-for="field in pagePreviewFields" :key="field.code">{{ field.name }}</span></div>
                  <div v-for="row in 3" :key="row" class="preview-table__row"><span v-for="field in pagePreviewFields" :key="field.code">{{ field.fieldType === 'MONEY' ? '¥ 0.00' : `示例${row}` }}</span></div>
                </div>
                <div v-else class="preview-form" :class="`preview-form--${pageDensity}`" :style="{ gridTemplateColumns: `repeat(${previewDevice === 'MOBILE' ? 1 : pageColumns}, minmax(0, 1fr))` }">
                  <h4>{{ pageSectionTitle }}</h4>
                  <label v-for="field in pagePreviewFields" :key="field.code"><span>{{ field.name }}<em v-if="field.required">*</em></span><div>{{ JSON.parse(field.configJson || '{}').placeholder || `请输入${field.name}` }}</div></label>
                </div>
                <a-empty v-if="!pagePreviewFields.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="从左侧选择字段生成实时预览" />
              </section>
            </div>
          </div>
          <div v-else-if="activeConfigTask === 'ACTIONS' && activeDesignerTab === 'RULES'" class="page-config-canvas">
            <div class="page-editor-toolbar"><div><strong>结构化业务规则</strong><small>只允许字段、运算符和受控结果；不执行任意脚本。草稿可预演，发布后前后端读取同一快照。</small></div><a-button type="primary" @click="beginRule"><PlusOutlined />新建规则</a-button></div>
            <button v-for="rule in ruleIndexDraft.rules" :key="rule.id" type="button" class="rule-config-card" @click="editRule(rule)">
              <span><strong>{{ rule.name }}</strong><small>{{ optionName(triggerEventOptions, rule.triggerEvent) }} · 顺序 {{ rule.sortOrder }}</small></span>
              <a-tag>{{ optionName(ruleTypeOptions, rule.ruleType) }}</a-tag><a-tag :color="rule.testStatus === 'EXECUTED' ? 'green' : 'orange'">{{ rule.testStatus === 'EXECUTED' ? '已预演' : '待预演' }}</a-tag>
              <p>{{ rule.messageTemplate || '命中后按配置结果执行' }}</p>
            </button>
            <a-empty v-if="!ruleIndexDraft.rules.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无规则；可从结构化条件开始配置" />
          </div>
          <div v-else-if="activeConfigTask === 'ACTIONS' && activeDesignerTab === 'INDEXES'" class="page-config-canvas">
            <div class="page-editor-toolbar"><div><strong>查询索引与唯一规则</strong><small>按选定字段生成运行态投影；唯一规则固定隔离到系统、租户、模块和未删除数据。</small></div><a-button type="primary" @click="beginIndex"><PlusOutlined />新建索引</a-button></div>
            <button v-for="item in ruleIndexDraft.indexes" :key="item.index.id" type="button" class="index-config-card" @click="editIndex(item)">
              <span><strong>{{ item.index.name }}</strong><small>{{ fieldNames(item.fieldCodes) }}</small></span>
              <a-tag :color="item.index.uniqueIndex ? 'volcano' : 'blue'">{{ item.index.uniqueIndex ? '唯一规则' : '查询投影' }}</a-tag>
              <p>{{ item.index.uniqueIndex ? '在当前系统、组织和模块内保证未归档数据不重复' : '提升所选字段的筛选与查询速度' }}</p>
            </button>
            <a-empty v-if="!ruleIndexDraft.indexes.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无索引；普通字段仍可使用基础查询" />
          </div>
          <div v-else-if="activeConfigTask === 'ACTIONS' && activeDesignerTab === 'ACTIONS'" class="page-config-canvas">
            <div class="page-editor-toolbar"><div><strong>模块功能</strong><small>常用功能默认可用；只有业务确实需要时再调整位置或二次确认。</small></div></div>
            <button v-for="action in draft.actions" :key="action.id" type="button" class="action-config-row" @click="editAction(action)">
              <span><strong>{{ action.name }}</strong><small>{{ actionLocationName(action.location) }}</small></span>
              <a-tag>{{ action.status === 'ACTIVE' ? '已启用' : '已停用' }}</a-tag><span>配置</span>
            </button>
          </div>
          <section v-else-if="activeConfigTask === 'FLOW'" class="related-module-task">
            <ApartmentOutlined />
            <div><small>当前模块</small><h3>{{ draft.module.name }}的流程</h3><p>为新建、更新、状态变化等业务事件绑定已发布流程；审批、自动流转和异常处理都在流程配置中完成。</p></div>
            <a-button type="primary" @click="openRelatedConfiguration('flow')">配置模块 Flow</a-button>
          </section>
          <section v-else-if="activeConfigTask === 'APPLICATION'" class="related-module-task">
            <AppstoreAddOutlined />
            <div><small>当前模块</small><h3>{{ draft.module.name }}的应用开放</h3><p>只把明确授权的模块动作开放给系统内外应用；凭证、签名、限流和调用日志统一在应用配置中维护。</p></div>
            <a-button type="primary" @click="openRelatedConfiguration('applications')">配置模块应用</a-button>
          </section>
          <section v-if="activeConfigTask === 'INFO' && infoSection === 'OVERVIEW'" class="default-config">
            <div><strong>页面</strong><span v-for="page in draft.pages" :key="page.id">{{ page.name }}</span></div>
            <div><strong>菜单</strong><span v-for="menu in draft.menus" :key="menu.id">{{ menu.name }}</span></div>
            <div><strong>动作</strong><span v-for="action in draft.actions" :key="action.id">{{ action.name }}</span></div>
          </section>
          <section v-if="activeConfigTask === 'INFO' && infoSection === 'PUBLICATION'" class="module-publish-step">
            <div class="publish-readiness" :class="{ ready: publication?.valid }">
              <CheckCircleOutlined v-if="publication?.valid" />
              <CloudUploadOutlined v-else />
              <div><h3>{{ publication?.valid ? '可以发布' : '发布前检查' }}</h3><p>{{ publication?.valid ? '字段、页面、动作和依赖均已通过检查。' : '系统会检查缺失配置并准确指出需要返回的步骤。' }}</p></div>
              <a-button :loading="saving" @click="checkPublication(false)">重新检查</a-button>
            </div>
            <a-alert v-if="publication && !publication.valid" type="warning" show-icon message="还有内容需要处理">
              <template #description><ul class="publication-issues"><li v-for="issue in publication.issues" :key="`${issue.path}-${issue.code}`"><strong>{{ issue.path }}</strong><span>{{ issue.message }}</span></li></ul></template>
            </a-alert>
            <div class="publish-actions">
              <span>发布后普通用户会立即在业务工作区看到这个模块。</span>
              <a-button type="primary" size="large" :disabled="!publication?.valid" :loading="saving" @click="publish"><CloudUploadOutlined />确认发布并进入运行页</a-button>
            </div>
          </section>
          <section v-if="activeConfigTask === 'INFO' && infoSection === 'PUBLICATION'" class="module-version-history">
            <div class="version-history-heading"><span><HistoryOutlined /><strong>发布历史</strong></span><small>已发布版本不可直接修改；回滚会生成一个新版本</small></div>
            <div v-for="version in versions" :key="version.versionId" class="module-version-row">
              <span><strong>版本 {{ version.versionNumber }}</strong><small>{{ version.changeSummary }} · 发布于 {{ productDateTime(version.publishedAt) }}</small></span>
              <a-tag :color="version.current ? 'green' : 'default'">{{ version.current ? '当前运行版本' : '历史快照' }}</a-tag>
              <a-popconfirm v-if="!version.current" title="确认把运行态切换到这个不可变版本？" ok-text="确认回滚" cancel-text="取消" @confirm="rollbackVersion(version)">
                <a-button size="small" :loading="saving">回滚到此版本</a-button>
              </a-popconfirm>
              <span v-else class="publication-pointer">当前生效</span>
            </div>
            <a-empty v-if="!versions.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="尚未发布；草稿不会出现在普通用户导航。" />
          </section>
        </template>
        <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="创建并选择模块后，在这里设计字段" />
      </main>

      <aside v-if="activeConfigTask === 'FIELDS' && selectedPage" class="field-palette panel-card">
        <template v-if="activeDesignerTab === 'FIELDS'">
          <div class="panel-title"><strong>字段类型</strong><span>拖拽添加</span></div>
          <button v-for="field in fieldTypes" :key="field.type" type="button" class="palette-field" draggable="true"
            @dragstart="draggingType = field.type" @dragend="draggingType = ''" @click="beginField(field.type)">
            <span>{{ field.name.slice(0, 1) }}</span><div><strong>{{ field.name }}</strong><small>{{ field.hint }}</small></div><DragOutlined />
          </button>
        </template>
        <template v-else-if="selectedPage">
          <div class="panel-title"><strong>页面属性</strong><span>{{ selectedPage.pageType }}</span></div>
          <div class="page-property-panel">
            <a-form layout="vertical"><a-form-item label="区块标题"><a-input :value="pageSectionTitle" :disabled="!standardPageType" @blur="savePageProperty('title', $event.target.value)" /></a-form-item>
              <a-form-item v-if="selectedPage.pageType !== 'LIST' && standardPageType" label="桌面每行列数"><a-select :value="pageColumns" :options="[1, 2, 3].map(value => ({ value, label: `${value} 列` }))" @change="savePageLayout(selectedPageFieldCodes, Number($event))" /></a-form-item>
              <a-form-item v-if="standardPageType" label="内容密度"><a-radio-group :value="pageDensity" @change="savePageProperty('density', $event.target.value)"><a-radio value="comfortable">舒适</a-radio><a-radio value="compact">紧凑</a-radio></a-radio-group></a-form-item>
            </a-form>
            <a-divider>引用检查</a-divider><p>已放置 {{ pagePreviewFields.length }} 个有效字段</p>
            <a-alert v-if="pageLayoutIssues.length" type="error" show-icon message="不可发布" :description="pageLayoutIssues.join('；')" />
            <a-alert v-else type="success" show-icon message="页面字段有效" description="保存后仍是草稿，发布成功后业务页面才切换到本版本。" />
          </div>
        </template>
        <template v-else><div class="panel-title"><strong>专属配置</strong><span>按当前标签</span></div><a-empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前能力在中间工作区配置" /></template>
      </aside>
    </div>

    <a-modal v-model:open="groupModal" title="新建模块组" :confirm-loading="saving" @ok="createGroup">
      <a-form layout="vertical"><a-form-item label="名称" required><a-input v-model:value="groupForm.name" placeholder="例如：销售管理" /></a-form-item>
        <a-form-item label="排序"><a-input-number v-model:value="groupForm.sortOrder" :min="0" /></a-form-item></a-form>
      <a-alert type="info" show-icon message="模块组用于整理运行导航；内部标识由系统自动维护。" />
    </a-modal>
    <a-modal v-model:open="moduleModal" title="新建业务模块" :confirm-loading="saving" @ok="createModule">
      <a-form layout="vertical">
        <a-form-item label="模块名称" required><a-input v-model:value="moduleForm.name" autofocus placeholder="例如：客户、订单、巡检记录" /></a-form-item>
        <a-form-item label="放在哪个业务分组" required>
          <a-radio-group v-model:value="moduleForm.groupMode" button-style="solid">
            <a-radio-button v-if="overview.groups.length" value="existing">已有分组</a-radio-button>
            <a-radio-button value="new">新建分组</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="moduleForm.groupMode === 'existing'" label="业务分组" required><a-select v-model:value="moduleForm.groupId" :options="overview.groups.map((item) => ({ value: item.id, label: item.name }))" /></a-form-item>
        <a-form-item v-else label="新分组名称" required><a-input v-model:value="moduleForm.groupName" placeholder="例如：客户运营" /></a-form-item>
        <a-alert type="success" show-icon message="创建后自动准备列表、表单、详情、运行入口和常用操作。" description="技术编码由系统生成；下一步只需要添加业务字段。" />
      </a-form>
    </a-modal>
    <a-modal v-model:open="pageModal" title="添加扩展页面" :confirm-loading="saving" @ok="createPage">
      <a-form layout="vertical">
        <a-form-item label="页面类型" required><a-select v-model:value="pageForm.pageType" :options="[
          { value: 'DASHBOARD', label: '模块仪表盘', disabled: draft?.pages.some(page => page.pageType === 'DASHBOARD') },
          { value: 'CUSTOM', label: '自定义页面', disabled: draft?.pages.some(page => page.pageType === 'CUSTOM') },
        ]" /></a-form-item>
        <a-form-item label="页面名称" required><a-input v-model:value="pageForm.name" /></a-form-item>
        <a-alert type="info" show-icon message="扩展页面与列表、表单、详情共用同一套字段配置和发布版本。" />
      </a-form>
    </a-modal>
    <a-drawer v-model:open="fieldDrawer" :title="editingField ? '编辑字段' : '添加字段'" width="520">
      <a-form layout="vertical">
        <a-form-item label="字段类型"><a-select v-if="!editingField" v-model:value="fieldForm.fieldType" show-search :options="fieldTypes.map(item => ({ value: item.type, label: `${item.name} · ${item.hint}` }))" /><a-input v-else :value="fieldTypes.find((item) => item.type === fieldForm.fieldType)?.name" disabled /></a-form-item>
        <a-form-item label="字段名称" required><a-input v-model:value="fieldForm.name" placeholder="例如：客户名称、联系电话、跟进结果" /></a-form-item>
        <div class="form-grid"><a-form-item label="排序"><a-input-number v-model:value="fieldForm.sortOrder" :min="0" /></a-form-item>
          <a-form-item label="是否必填"><a-switch v-model:checked="fieldForm.required" /></a-form-item></div>
        <div class="form-grid"><a-form-item label="可搜索"><a-switch v-model:checked="fieldForm.searchable" /></a-form-item>
          <a-form-item label="唯一值"><a-switch v-model:checked="fieldForm.uniqueValue" /></a-form-item></div>
        <a-form-item label="提示文案"><a-input v-model:value="fieldForm.placeholder" placeholder="页面输入提示" /></a-form-item>
        <a-form-item label="默认值"><a-input v-model:value="fieldForm.defaultValue" placeholder="可留空；高级默认值由后续规则配置" /></a-form-item>
        <a-form-item v-if="editingField" label="状态"><a-radio-group v-model:value="fieldForm.status"><a-radio value="ACTIVE">启用</a-radio><a-radio value="DISABLED">停用</a-radio></a-radio-group></a-form-item>
        <a-form-item v-if="['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS', 'TAG'].includes(fieldForm.fieldType)" label="发布字典" extra="绑定后模块发布会检查字典是否已经发布；也可以留空使用手动选项。">
          <a-select v-model:value="fieldForm.dictionaryId" allow-clear show-search :options="dictionaryOptions" />
        </a-form-item>
        <a-form-item v-if="['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS', 'TAG'].includes(fieldForm.fieldType) && !fieldForm.dictionaryId" label="手动选项" extra="每行一个，格式：值|显示名称">
          <a-textarea v-model:value="fieldForm.optionsText" :rows="7" />
        </a-form-item>
        <template v-if="fieldForm.fieldType === 'CASCADE'">
          <a-alert type="info" show-icon message="级联字段只能选择一种数据源" class="section-alert" />
          <a-form-item label="层级字典"><a-select v-model:value="fieldForm.dictionaryId" allow-clear :options="dictionaryOptions" @change="fieldForm.referenceModuleId = undefined" /></a-form-item>
          <a-form-item label="或模块数据源"><a-select v-model:value="fieldForm.referenceModuleId" allow-clear :options="referenceModuleOptions" @change="fieldForm.dictionaryId = undefined" /></a-form-item>
          <div class="form-grid"><a-form-item label="最大层级"><a-input-number v-model:value="fieldForm.maxDepth" :min="1" :max="20" /></a-form-item><a-form-item label="允许选中间层"><a-switch v-model:checked="fieldForm.allowIntermediate" /></a-form-item></div>
          <a-form-item label="保存方式"><a-radio-group v-model:value="fieldForm.saveMode"><a-radio value="FINAL">最终项</a-radio><a-radio value="PATH">完整路径</a-radio></a-radio-group></a-form-item>
        </template>
        <template v-if="fieldForm.fieldType === 'MONEY'">
          <div class="form-grid"><a-form-item label="币种" required><a-input v-model:value="fieldForm.currency" placeholder="CNY" /></a-form-item><a-form-item label="小数精度" required><a-input-number v-model:value="fieldForm.precision" :min="0" :max="6" /></a-form-item></div>
        </template>
        <a-form-item v-if="['REFERENCE', 'LOOKUP', 'SUBTABLE', 'SUMMARY', 'AGGREGATE'].includes(fieldForm.fieldType)" label="目标模块" required extra="选择要关联的业务模块，运行页展示目标记录的业务名称。">
          <a-select v-model:value="fieldForm.referenceModuleId" show-search :options="referenceModuleOptions" />
        </a-form-item>
        <a-form-item v-if="fieldForm.fieldType === 'AUTO_NUMBER'" label="编号规则" required><a-input v-model:value="fieldForm.sequenceCode" placeholder="例如：客户编号" /></a-form-item>
        <a-form-item v-if="['FORMULA', 'CALCULATION'].includes(fieldForm.fieldType)" label="表达式" required><a-textarea v-model:value="fieldForm.expression" :rows="4" placeholder="例如 amount * tax_rate" /></a-form-item>
        <template v-if="fieldForm.fieldType === 'AI_FILL'">
          <a-form-item label="授权模型" required><a-select v-model:value="fieldForm.modelCode" show-search :options="availableAiModels.map(model => ({ value: model.code, label: `${model.name} · ${model.provider}` }))" placeholder="仅显示当前系统已授权模型" /></a-form-item>
          <a-form-item label="写入控制"><a-checkbox v-model:checked="fieldForm.humanConfirmation">每次写入前必须人工确认</a-checkbox></a-form-item>
        </template>
        <a-form-item v-if="fieldForm.fieldType === 'SECRET'" label="敏感字段"><a-checkbox v-model:checked="fieldForm.sensitive">按敏感字段权限、脱敏和审计处理</a-checkbox></a-form-item>
        <a-form-item v-if="fieldForm.fieldType.startsWith('SYSTEM_')" label="系统字段"><a-checkbox v-model:checked="fieldForm.readOnly">只读，由系统维护</a-checkbox></a-form-item>
        <a-alert v-if="fieldConfigurationIssue" type="warning" show-icon :message="fieldConfigurationIssue" />
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="fieldDrawer = false">取消</a-button><a-button type="primary" :loading="saving" :disabled="Boolean(fieldConfigurationIssue)" @click="saveField">保存字段</a-button></div></template>
    </a-drawer>
    <a-drawer v-model:open="actionDrawer" :title="`配置动作：${editingAction?.name || ''}`" width="430">
      <a-form layout="vertical">
        <a-form-item label="出现位置"><a-select v-model:value="actionForm.location" :options="[
          { value: 'MODULE_ENTRY', label: '模块入口' }, { value: 'LIST_TOOLBAR', label: '列表工具栏' },
          { value: 'BATCH', label: '批量操作区' }, { value: 'ROW', label: '列表行' }, { value: 'DETAIL_HEADER', label: '详情头部' },
          { value: 'DETAIL_MORE', label: '详情更多操作' },
        ]" /></a-form-item>
        <a-form-item label="执行前二次确认"><a-switch v-model:checked="actionForm.confirmation" /></a-form-item>
        <a-form-item v-if="actionForm.confirmation" label="确认提示"><a-input v-model:value="actionForm.confirmationText" placeholder="确认执行此操作？" /></a-form-item>
        <template v-if="editingAction?.code === 'CONVERT'">
          <a-divider>转化目标与字段映射</a-divider>
          <a-alert type="info" show-icon class="section-alert" message="运行时只读取已发布映射；每个来源记录对同一目标模块永久只能成功转化一次。" />
          <section v-for="(target, index) in conversionTargets" :key="index" class="panel-card" style="padding:12px;margin-bottom:12px">
            <a-form-item label="目标模块" required><a-select v-model:value="target.moduleCode" show-search :options="conversionModuleOptions" @change="loadConversionTarget(String($event))" /></a-form-item>
            <a-form-item label="字段对应关系" required extra="选择新记录中的目标字段，以及从当前记录取值的来源字段。">
              <div v-for="(mapping, mappingIndex) in target.mappings" :key="mappingIndex" class="conversion-mapping-row">
                <a-select v-model:value="mapping.targetCode" show-search placeholder="目标字段" :options="conversionTargetFieldOptions(target.moduleCode)" />
                <span>取值自</span>
                <a-select v-model:value="mapping.sourceCode" show-search placeholder="当前模块字段" :options="activeFieldOptions" />
                <a-button danger type="text" :disabled="target.mappings.length === 1" @click="target.mappings.splice(mappingIndex, 1)">移除</a-button>
              </div>
              <a-button block @click="addConversionMapping(target)"><PlusOutlined />增加字段对应</a-button>
            </a-form-item>
            <a-button v-if="conversionTargets.length > 1" danger size="small" @click="conversionTargets.splice(index, 1)">移除目标</a-button>
          </section>
          <a-button block @click="addConversionTarget">增加目标模块</a-button>
        </template>
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="actionDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveAction">保存动作</a-button></div></template>
    </a-drawer>
    <a-drawer v-model:open="menuDrawer" title="配置运行菜单" width="460">
      <a-form layout="vertical">
        <a-form-item label="菜单名称" required><a-input v-model:value="menuForm.name" /></a-form-item>
        <div class="form-grid"><a-form-item label="图标标识"><a-input v-model:value="menuForm.icon" placeholder="例如 team" /></a-form-item>
          <a-form-item label="组内排序"><a-input-number v-model:value="menuForm.sortOrder" :min="0" /></a-form-item></div>
        <div class="form-grid"><a-form-item label="菜单可见"><a-switch v-model:checked="menuForm.visible" /></a-form-item>
          <a-form-item label="状态"><a-radio-group v-model:value="menuForm.status"><a-radio value="ACTIVE">启用</a-radio><a-radio value="DISABLED">停用</a-radio></a-radio-group></a-form-item></div>
        <a-alert type="warning" show-icon message="菜单配置不会授予任何业务权限；无模块 LIST 权限的用户仍不可见且不能直接访问。" />
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="menuDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveMenu">保存菜单草稿</a-button></div></template>
    </a-drawer>
    <a-drawer v-model:open="ruleDrawer" :title="editingRule ? '编辑结构化规则' : '新建结构化规则'" width="640">
      <a-form layout="vertical">
        <a-form-item label="规则名称" required><a-input v-model:value="ruleForm.name" placeholder="例如：高金额订单需要复核" /></a-form-item>
        <div class="form-grid"><a-form-item label="规则类型"><a-select v-model:value="ruleForm.ruleType" :options="ruleTypeOptions" /></a-form-item><a-form-item label="触发时机"><a-select v-model:value="ruleForm.triggerEvent" :options="triggerEventOptions" /></a-form-item></div>
        <a-form-item label="条件组合"><a-radio-group v-model:value="ruleForm.mode"><a-radio value="ALL">全部满足</a-radio><a-radio value="ANY">任一满足</a-radio></a-radio-group></a-form-item>
        <div class="rule-condition-list">
          <div v-for="(condition, index) in ruleConditions" :key="index" class="rule-condition-row">
            <a-select v-model:value="condition.field" show-search :options="activeFieldOptions" placeholder="字段" @change="sampleValues[condition.field] = sampleValues[condition.field] || ''" />
            <a-select v-model:value="condition.operator" :options="conditionOperatorOptions" />
            <a-input v-if="!['EMPTY', 'NOT_EMPTY'].includes(condition.operator)" v-model:value="condition.value" placeholder="比较值；数字自动识别" />
            <span v-else class="condition-no-value">无需比较值</span>
            <a-button danger type="text" :disabled="ruleConditions.length === 1" @click="ruleConditions.splice(index, 1)">移除</a-button>
          </div>
          <a-button block @click="addRuleCondition"><PlusOutlined />增加条件</a-button>
        </div>
        <div class="form-grid"><a-form-item label="命中结果"><a-select v-model:value="ruleForm.effectType" :options="[
          { value: 'BLOCK', label: '阻止保存' }, { value: 'REQUIRE_FIELD', label: '字段必填' }, { value: 'SET_VISIBILITY', label: '设置可见性' },
          { value: 'SET_EDITABLE', label: '设置可编辑' }, { value: 'REQUIRE_APPROVAL', label: '要求审批字段' }, { value: 'DELETE_ROLE', label: '指定角色可删除' },
        ]" /></a-form-item><a-form-item v-if="ruleNeedsTarget" label="作用字段" required><a-select v-model:value="ruleForm.targetField" :options="activeFieldOptions" /></a-form-item></div>
        <a-form-item label="命中提示"><a-input v-model:value="ruleForm.message" placeholder="例如：金额不能超过 1000" /></a-form-item>
        <div v-if="editingRule" class="rule-preview-box">
          <strong>条件预演</strong><small>输入样例值，服务端使用与发布运行态相同的规则解释器计算每个分支。</small>
          <label v-for="condition in ruleConditions" :key="condition.field"><span>{{ fieldName(condition.field) }}</span><a-input v-model:value="sampleValues[condition.field]" placeholder="样例值" /></label>
          <a-button :loading="saving" @click="previewRule">执行预演</a-button>
          <a-alert v-if="rulePreview" :type="rulePreview.matched ? 'warning' : 'success'" show-icon :message="rulePreview.matched ? `命中：${rulePreview.effectType}` : '未命中，数据可继续处理'" :description="rulePreview.message" />
        </div>
        <a-form-item v-if="editingRule" label="状态"><a-radio-group v-model:value="ruleForm.status"><a-radio value="ACTIVE">启用</a-radio><a-radio value="DISABLED">停用</a-radio></a-radio-group></a-form-item>
        <a-alert type="info" show-icon message="这里不会接收或执行 JavaScript、SQL、SpEL 等任意脚本。" />
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="ruleDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveRule">保存规则草稿</a-button></div></template>
    </a-drawer>
    <a-drawer v-model:open="indexDrawer" :title="editingIndex ? '编辑查询索引' : '新建查询索引'" width="520">
      <a-form layout="vertical">
        <a-form-item label="规则名称" required><a-input v-model:value="indexForm.name" placeholder="例如：客户手机号不可重复" /></a-form-item>
        <a-form-item label="索引字段" required extra="选择顺序就是组合索引顺序；字段变更会由发布检查阻断失效引用。"><a-select v-model:value="indexFieldIds" mode="multiple" show-search :options="indexFieldOptions" /></a-form-item>
        <a-form-item label="唯一规则"><a-switch v-model:checked="indexForm.uniqueIndex" /><span class="inline-help"> 开启后，由数据库并发约束保证唯一，不依赖先查后写。</span></a-form-item>
        <a-form-item v-if="editingIndex" label="状态"><a-radio-group v-model:value="indexForm.status"><a-radio value="ACTIVE">启用</a-radio><a-radio value="DISABLED">停用</a-radio></a-radio-group></a-form-item>
        <a-alert type="info" show-icon :message="indexForm.uniqueIndex ? '只在当前系统、组织和模块的未归档数据中判断重复；空值不冲突。' : '发布后会优化这些字段的查询，不会改变已有业务记录。'" />
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="indexDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveIndex">保存索引草稿</a-button></div></template>
    </a-drawer>
    <a-modal v-model:open="publishModal" title="发布检查" :ok-text="publication?.valid ? '确认发布' : '返回修改'"
      :confirm-loading="saving" @ok="publication?.valid ? publish() : (publishModal = false)">
      <template v-if="publication?.valid"><a-result status="success" title="配置检查通过" sub-title="发布会生成一个不可修改的版本，业务运行页随即使用它。"><template #icon><CheckCircleOutlined /></template></a-result>
        <a-alert v-if="publication.indexProjectionPlans.length" type="info" show-icon message="查询与唯一规则已准备就绪" :description="`共 ${publication.indexProjectionPlans.length} 项，将随本次发布生效。`" /></template>
      <template v-else><a-alert type="warning" show-icon message="当前配置还不能发布" description="请修正以下问题后重新检查。" />
        <ul class="publication-issues"><li v-for="issue in publication?.issues" :key="`${issue.path}-${issue.code}`"><strong>{{ issue.path }}</strong><span>{{ issue.message }}</span></li></ul></template>
    </a-modal>
  </div>
</template>
