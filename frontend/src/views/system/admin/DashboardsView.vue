<script setup lang="ts">
import { message } from 'ant-design-vue'
import { BarChart3, CheckCircle2, ChevronDown, ChevronUp, Eye, Gauge, LayoutDashboard, LineChart, List, PieChart, Plus, RefreshCw, RotateCcw, Save, Send, Sigma, Trash2 } from 'lucide-vue-next'
import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import RuntimeDashboardCanvas from '@/components/dashboard/RuntimeDashboardCanvas.vue'
import { ApiRequestError } from '@/services/api'
import { dashboardAdminApi, runtimeDashboardApi } from '@/services/dashboard'
import { configRecoveryApi } from '@/services/configRecovery'
import { dataSourceAdminApi, runtimeDataSourceApi } from '@/services/dataSource'
import { kpiAdminApi } from '@/services/kpi'
import type { DataSourceSummary } from '@/types/dataSource'
import type { KpiSummary } from '@/types/kpi'
import type { DataSourceStatisticsCapabilities } from '@/types/statistics'
import type {
  CreateScopedDashboardInput,
  DashboardCheckResult,
  DashboardDetail,
  DashboardSummary,
  DashboardPlacement,
  DashboardVersion,
  DashboardWidgetDraft,
  DashboardWidgetType,
  RuntimeDashboard,
  SaveDashboardDraftInput,
} from '@/types/dashboard'
import {
  cloneWidget,
  DASHBOARD_GRID_COLUMNS,
  DASHBOARD_GRID_ROWS,
  DASHBOARD_MAX_WIDGETS,
  defaultWidgetGrid,
  defaultWidgetBehavior,
  defaultWidgetStatistics,
  gridStyle,
  isKpiWidget,
  isListWidget,
  isStatisticsWidget,
  validateDashboardWidgets,
} from './dashboardEditorModel'

import { statisticsCapabilities, type StatisticsCapabilities } from './statisticsEditorModel'
import { useAdminViewport } from '@/composables/useAdminViewport'

type EditorState = 'clean' | 'dirty' | 'saving' | 'checked' | 'published' | 'error'

const { isMobile } = useAdminViewport()
const route = useRoute()
const systemId = computed(() => String(route.params.systemId))
const dashboards = ref<DashboardSummary[]>([])
const sources = ref<DataSourceSummary[]>([])
const kpis = ref<KpiSummary[]>([])
const sourceStatisticsCapabilities = ref<Record<string, DataSourceStatisticsCapabilities>>({})
const selected = ref<DashboardDetail | null>(null)
const versions = ref<DashboardVersion[]>([])
const preview = ref<RuntimeDashboard | null>(null)
const loading = ref(false)
const detailLoading = ref(false)
const previewLoading = ref(false)
const editorState = ref<EditorState>('clean')
const errorMessage = ref('')
const createOpen = ref(false)
const creating = ref(false)
const createError = ref('')
const checkResult = ref<DashboardCheckResult | null>(null)
let selectionGeneration = 0
let previewGeneration = 0

const editor = reactive({ name: '', description: '', widgets: [] as DashboardWidgetDraft[] })
const createForm = reactive<CreateScopedDashboardInput>({ code: 'system_home', placement: 'SYSTEM_HOME', scopeKey: '', name: '', description: '' })
const publishedSources = computed(() => sources.value.filter(source => source.activeVersionId))
const publishedKpis = computed(() => kpis.value.filter(kpi => kpi.activeVersionId))
const editorStatisticsCapabilities = computed<Record<string, StatisticsCapabilities>>(() => Object.fromEntries(
  Object.entries(sourceStatisticsCapabilities.value).map(([sourceId, capability]) => [sourceId, statisticsCapabilities(capability.fields)]),
))
const clientIssues = computed(() => validateDashboardWidgets(editor.widgets, editorStatisticsCapabilities.value))
const blockerCount = computed(() => checkResult.value?.blockerCount ?? 0)
const warningCount = computed(() => checkResult.value?.warningCount ?? 0)
const stateText: Record<EditorState, string> = {
  clean: '草稿已同步', dirty: '有未保存修改', saving: '正在保存', checked: '检查已通过', published: '已发布', error: '操作失败',
}

function requestError(error: unknown, fallback: string) {
  return error instanceof ApiRequestError
    ? error.message || error.code
    : error instanceof Error
      ? error.message
      : fallback
}

function replaceDashboard(value: DashboardSummary) {
  const existing = dashboards.value.find(item => item.id === value.id)
  const normalized = { ...value, scopeKey: value.scopeKey ?? existing?.scopeKey ?? null }
  const exists = Boolean(existing)
  dashboards.value = exists
    ? dashboards.value.map(item => item.id === value.id ? normalized : item)
    : [normalized, ...dashboards.value]
}

function placementLabel(value: DashboardPlacement) {
  if (value === 'APPLICATION_HOME') return '应用首页'
  if (value === 'MODULE_HOME') return '模块首页'
  if (value === 'PERSONAL_HOME') return '个人首页'
  return '系统首页'
}

function requireScopeKey(item: DashboardSummary) {
  const key = item.scopeKey?.trim() ?? ''
  if (!key) throw new Error(`${placementLabel(item.placement)}缺少作用域标识，请刷新目录后重试`)
  return key
}

function detailRequest(item: DashboardSummary) {
  if (item.placement === 'SYSTEM_HOME') return dashboardAdminApi.detail(systemId.value, item.id)
  if (item.placement === 'PERSONAL_HOME') return dashboardAdminApi.personalDetail(systemId.value, item.id)
  return dashboardAdminApi.scopedDetail(systemId.value, item.placement, requireScopeKey(item))
}

function versionsRequest(item: DashboardSummary) {
  if (item.placement === 'SYSTEM_HOME') return dashboardAdminApi.versions(systemId.value, item.id)
  if (item.placement === 'PERSONAL_HOME') return dashboardAdminApi.personalVersions(systemId.value, item.id)
  return dashboardAdminApi.scopedVersions(systemId.value, item.placement, requireScopeKey(item))
}

function applyDetail(value: DashboardDetail) {
  const scopeKey = value.scopeKey ?? (selected.value?.id === value.id ? selected.value.scopeKey : null)
  selected.value = { ...value, scopeKey }
  editor.name = value.name
  editor.description = value.description ?? ''
  editor.widgets = value.draft.widgets.map(cloneWidget)
  replaceDashboard(value)
  checkResult.value = null
  editorState.value = 'clean'
}

function markDirty() {
  if (!selected.value || editorState.value === 'saving') return
  editorState.value = 'dirty'
  checkResult.value = null
  errorMessage.value = ''
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const scopedRequest = typeof dashboardAdminApi.scopedList === 'function'
      ? dashboardAdminApi.scopedList(systemId.value).catch(() => [])
      : Promise.resolve([])
    const personalRequest = typeof dashboardAdminApi.personal === 'function'
      ? dashboardAdminApi.personal(systemId.value).catch(() => [])
      : Promise.resolve([])
    const [systemItems, scopedItems, personalItems, sourceItems, kpiItems] = await Promise.all([
      dashboardAdminApi.list(systemId.value),
      scopedRequest,
      personalRequest,
      dataSourceAdminApi.list(systemId.value), kpiAdminApi.list(systemId.value),
    ])
    const items = [...systemItems, ...scopedItems, ...personalItems]
    dashboards.value = items
    sources.value = sourceItems
    kpis.value = kpiItems
    await loadSourceStatisticsCapabilities(sourceItems)
    if (items[0]) await selectDashboard(items[0])
  } catch (error) {
    errorMessage.value = requestError(error, '仪表盘目录加载失败')
    editorState.value = 'error'
  } finally {
    loading.value = false
  }
}

async function loadSourceStatisticsCapabilities(sourceItems: DataSourceSummary[]) {
  const published = sourceItems.filter((source): source is DataSourceSummary & { activeVersionId: string } => Boolean(source.activeVersionId))
  const entries = await Promise.all(published.map(async source => {
    try {
      const capability = await runtimeDataSourceApi.statisticsCapabilities(
        systemId.value, source.code,
      )
      return [source.id, capability] as const
    } catch {
      return null
    }
  }))
  sourceStatisticsCapabilities.value = Object.fromEntries(entries.filter((entry): entry is readonly [string, DataSourceStatisticsCapabilities] => entry !== null))
}

async function selectDashboard(item: DashboardSummary) {
  const generation = ++selectionGeneration
  previewGeneration += 1
  detailLoading.value = true
  previewLoading.value = false
  errorMessage.value = ''
  preview.value = null
  try {
    const [detail, history] = await Promise.all([
      detailRequest(item), versionsRequest(item),
    ])
    if (generation !== selectionGeneration) return
    applyDetail(detail)
    versions.value = history
    if (detail.activeVersionId) await loadPreview(generation)
  } catch (error) {
    if (generation !== selectionGeneration) return
    errorMessage.value = requestError(error, '仪表盘详情加载失败')
    editorState.value = 'error'
  } finally {
    if (generation === selectionGeneration) detailLoading.value = false
  }
}

function openCreate() {
  Object.assign(createForm, { code: 'system_home', placement: 'SYSTEM_HOME', scopeKey: '', name: '', description: '' })
  createError.value = ''
  createOpen.value = true
}

async function createDashboard() {
  const input: CreateScopedDashboardInput = {
    code: createForm.code.trim(), placement: createForm.placement, scopeKey: createForm.scopeKey.trim(), name: createForm.name.trim(),
    description: createForm.description?.trim() || null,
  }
  if (!input.code || !input.name || input.placement !== 'SYSTEM_HOME' && !/^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$/u.test(input.scopeKey)) {
    createError.value = input.placement === 'SYSTEM_HOME' ? '名称和编码均为必填项' : '名称、编码和合法作用域标识均为必填项'
    return
  }
  creating.value = true
  createError.value = ''
  try {
    const created = input.placement === 'SYSTEM_HOME'
      ? await dashboardAdminApi.create(systemId.value, input)
      : await dashboardAdminApi.createScoped(systemId.value, input)
    const scopedCreated = { ...created, scopeKey: input.placement === 'SYSTEM_HOME' ? null : input.scopeKey }
    createOpen.value = false
    replaceDashboard(scopedCreated)
    await selectDashboard(scopedCreated)
  } catch (error) {
    createError.value = requestError(error, '仪表盘创建失败')
  } finally {
    creating.value = false
  }
}

function nextWidgetCode(type: DashboardWidgetType) {
  const prefix: Record<DashboardWidgetType, string> = {
    STAT_COUNT: 'count', DATA_LIST: 'list', STAT_VALUE: 'value', BAR_CHART: 'bar', PIE_CHART: 'pie', LINE_TREND: 'trend',
    RANKING: 'ranking', PROGRESS: 'progress', TODO_LIST: 'todo', QUICK_ENTRY: 'quick', KPI_VALUE: 'kpi',
  }
  let sequence = editor.widgets.length + 1
  while (editor.widgets.some(widget => widget.code === `${prefix[type]}_${sequence}`)) sequence += 1
  return `${prefix[type]}_${sequence}`
}

function widgetTitle(type: DashboardWidgetType) {
  const titles: Record<DashboardWidgetType, string> = {
    STAT_COUNT: '记录总数', DATA_LIST: '最新记录', STAT_VALUE: '统计指标', BAR_CHART: '分组柱状图', PIE_CHART: '分组饼图', LINE_TREND: '时间趋势',
    RANKING: '排行榜', PROGRESS: '进度指标', TODO_LIST: '待办列表', QUICK_ENTRY: '快捷入口', KPI_VALUE: 'KPI 指标',
  }
  return titles[type]
}

async function addWidget(type: DashboardWidgetType) {
  if (editor.widgets.length >= DASHBOARD_MAX_WIDGETS) return
  const common = { code: nextWidgetCode(type), type, title: widgetTitle(type), behavior: defaultWidgetBehavior(), grid: defaultWidgetGrid(type, editor.widgets) }
  if (isKpiWidget(type)) {
    editor.widgets.push({ ...common, kpiId: publishedKpis.value[0]?.id ?? '' })
  } else {
    editor.widgets.push({
      ...common, dataSourceId: publishedSources.value[0]?.id ?? '',
      rowLimit: isListWidget(type) ? 10 : null, statistics: defaultWidgetStatistics(type),
    })
  }
  markDirty()
}

function moveWidget(index: number, offset: -1 | 1) {
  const target = index + offset
  if (target < 0 || target >= editor.widgets.length) return
  const [widget] = editor.widgets.splice(index, 1)
  if (widget) editor.widgets.splice(target, 0, widget)
  markDirty()
}

function removeWidget(index: number) {
  editor.widgets.splice(index, 1)
  markDirty()
}

function sourceChanged(widget: DashboardWidgetDraft) {
  if (isStatisticsWidget(widget.type)) widget.statistics = defaultWidgetStatistics(widget.type)
  markDirty()
}

function kpiChanged(_widget: DashboardWidgetDraft) {
  markDirty()
}

function widgetTypeChanged(widget: DashboardWidgetDraft) {
  if (isKpiWidget(widget.type)) {
    delete widget.dataSourceId
    delete widget.rowLimit
    delete widget.statistics
    widget.kpiId = publishedKpis.value[0]?.id ?? ''
  } else {
    delete widget.kpiId
    widget.dataSourceId = publishedSources.value[0]?.id ?? ''
    widget.rowLimit = isListWidget(widget.type) ? 10 : null
    widget.statistics = defaultWidgetStatistics(widget.type)
  }
  widget.behavior = widget.behavior ?? defaultWidgetBehavior()
  markDirty()
}

function widgetCapability(widget: DashboardWidgetDraft) {
  return editorStatisticsCapabilities.value[widget.dataSourceId ?? '']
    ?? { fields: [], measureFields: [], groupFields: [], timeFields: [] }
}

function sourceCapabilityPin(widget: DashboardWidgetDraft) {
  const capability = widget.dataSourceId ? sourceStatisticsCapabilities.value[widget.dataSourceId] : undefined
  return capability ? `v${capability.dataSourceVersionNumber} · ${capability.schemaVersionId}` : '统计能力暂不可用'
}

function statisticsChanged(widget: DashboardWidgetDraft) {
  if (widget.statistics?.aggregation === 'COUNT') widget.statistics.measureFieldCode = null
  if (widget.statistics?.aggregation !== 'COUNT' && !widget.statistics?.measureFieldCode) {
    widget.statistics!.measureFieldCode = widgetCapability(widget).measureFields[0]?.fieldCode ?? ''
  }
  markDirty()
}

function activeSourceVersion(sourceId: string) {
  const source = sources.value.find(item => item.id === sourceId)
  return source?.activeVersionNumber ? `v${source.activeVersionNumber}（发布时固定）` : '无已发布版本'
}

function activeKpiVersion(kpiId: string | null | undefined) {
  const kpi = kpis.value.find(item => item.id === kpiId)
  return kpi?.activeVersionNumber ? `${kpi.code} v${kpi.activeVersionNumber}（发布时固定）` : '无已发布版本'
}

function versionWidgetPin(widget: DashboardVersion['widgets'][number]) {
  return widget.type === 'KPI_VALUE'
    ? `${widget.kpiCode ?? 'KPI'} v${widget.kpiVersionNumber ?? '—'}`
    : `${widget.dataSourceCode ?? '数据源'} v${widget.dataSourceVersionNumber ?? '—'}`
}

function buildInput(): SaveDashboardDraftInput {
  if (!selected.value) throw new Error('请先选择仪表盘')
  if (!editor.name.trim()) throw new Error('仪表盘名称不能为空')
  const issues = validateDashboardWidgets(editor.widgets)
  if (issues[0]) throw new Error(issues[0].message)
  return {
    expectedVersion: selected.value.draftVersion,
    name: editor.name.trim(), description: editor.description.trim() || null,
    widgets: editor.widgets.map(cloneWidget),
  }
}

async function saveDraft() {
  if (!selected.value) return false
  editorState.value = 'saving'
  errorMessage.value = ''
  try {
    const input = buildInput()
    const saved = selected.value.placement === 'SYSTEM_HOME'
      ? await dashboardAdminApi.saveDraft(systemId.value, selected.value.id, input)
      : selected.value.placement === 'PERSONAL_HOME'
        ? await dashboardAdminApi.personalSaveDraft(systemId.value, selected.value.id, input)
        : await dashboardAdminApi.scopedSaveDraft(systemId.value, selected.value.placement, requireScopeKey(selected.value), input)
    applyDetail(saved)
    return true
  } catch (error) {
    errorMessage.value = requestError(error, '仪表盘草稿保存失败')
    editorState.value = 'error'
    return false
  }
}

async function checkDraft() {
  if (!selected.value) return false
  if (editorState.value === 'dirty' && !await saveDraft()) return false
  errorMessage.value = ''
  try {
    checkResult.value = selected.value.placement === 'SYSTEM_HOME'
      ? await dashboardAdminApi.checkDraft(systemId.value, selected.value.id)
      : selected.value.placement === 'PERSONAL_HOME'
        ? await dashboardAdminApi.personalCheckDraft(systemId.value, selected.value.id)
        : await dashboardAdminApi.scopedCheckDraft(systemId.value, selected.value.placement, requireScopeKey(selected.value))
    editorState.value = checkResult.value.valid && checkResult.value.blockerCount === 0 ? 'checked' : 'error'
    return editorState.value === 'checked'
  } catch (error) {
    errorMessage.value = requestError(error, '仪表盘草稿检查失败')
    editorState.value = 'error'
    return false
  }
}

async function publishDraft() {
  if (!selected.value) return
  if (editorState.value !== 'checked' && !await checkDraft()) return
  errorMessage.value = ''
  try {
    const result = selected.value.placement === 'SYSTEM_HOME'
      ? await dashboardAdminApi.publishDraft(systemId.value, selected.value.id, selected.value.draftVersion)
      : selected.value.placement === 'PERSONAL_HOME'
        ? await dashboardAdminApi.personalPublishDraft(systemId.value, selected.value.id, selected.value.draftVersion)
        : await dashboardAdminApi.scopedPublishDraft(systemId.value, selected.value.placement, requireScopeKey(selected.value), selected.value.draftVersion)
    applyDetail(result.dashboard)
    editorState.value = 'published'
    versions.value = await versionsRequest(selected.value)
    await loadPreview(selectionGeneration)
  } catch (error) {
    errorMessage.value = requestError(error, '仪表盘发布失败')
    editorState.value = 'error'
  }
}

async function restoreVersion(version: DashboardVersion) {
  if (!selected.value || version.active) return
  if (editorState.value === 'dirty' || editorState.value === 'saving') {
    errorMessage.value = '请先保存或放弃当前本地修改，再恢复历史版本。'
    return
  }
  if (!window.confirm(`将仪表盘 v${version.versionNumber} 恢复为新草稿？当前运行版本不会改变。`)) return
  detailLoading.value = true
  errorMessage.value = ''
  try {
    const current = selected.value
    await configRecoveryApi.dashboard(systemId.value, current.id, version.versionNumber, {
      expectedVersion: current.draftVersion,
      reason: `恢复仪表盘 v${version.versionNumber} 为新草稿`,
    })
    await selectDashboard(current)
    message.success('历史版本已恢复为新草稿；请检查后显式发布。')
  } catch (error) {
    errorMessage.value = requestError(error, '仪表盘版本恢复失败')
    editorState.value = 'error'
  } finally {
    detailLoading.value = false
  }
}

async function loadPreview(sourceGeneration = selectionGeneration) {
  const requestGeneration = ++previewGeneration
  if (!selected.value?.activeVersionId) {
    preview.value = null
    previewLoading.value = false
    return
  }
  previewLoading.value = true
  errorMessage.value = ''
  try {
    const result = selected.value.placement === 'SYSTEM_HOME'
      ? await runtimeDashboardApi.byCode(systemId.value, selected.value.code)
      : await runtimeDashboardApi.scoped(systemId.value, selected.value.placement, requireScopeKey(selected.value))
    if (sourceGeneration === selectionGeneration && requestGeneration === previewGeneration) preview.value = result
  } catch (error) {
    if (sourceGeneration !== selectionGeneration || requestGeneration !== previewGeneration) return
    errorMessage.value = requestError(error, '已发布仪表盘预览加载失败')
    editorState.value = 'error'
  } finally {
    if (sourceGeneration === selectionGeneration && requestGeneration === previewGeneration) previewLoading.value = false
  }
}

function formatTime(value: string) {
  const parsed = new Date(value)
  return Number.isNaN(parsed.valueOf()) ? value : parsed.toLocaleString('zh-CN')
}

void load()
</script>

<template>
  <section class="admin-page dashboards-page">
    <AdminPageHeader title="仪表盘" :description="`管理 ${dashboards.length} 个系统、应用、模块或个人首页；运行态只读取已发布版本及固定的数据源或 KPI 版本。`">
      <template v-if="!isMobile" #actions><a-button aria-label="刷新仪表盘" :loading="loading" :disabled="loading" @click="load"><RefreshCw :size="16" />刷新</a-button><a-button class="dashboard-create" type="primary" @click="openCreate"><Plus :size="16" />新建仪表盘</a-button></template>
    </AdminPageHeader>
    <a-alert v-if="errorMessage" class="admin-alert dashboard-error" type="error" show-icon role="alert" aria-live="assertive" :message="errorMessage" />

    <div v-if="isMobile" class="dashboard-mobile-gate">
      <AdminMobileNotice />
      <a-empty description="仪表盘使用 12 列桌面网格和多字段组件编辑器，请在宽度大于 720px 的桌面窗口中管理。" />
    </div>
    <div v-else class="dashboard-studio" :aria-busy="loading || detailLoading">
      <aside class="dashboard-list"><header><strong>仪表盘目录</strong><span>{{ dashboards.length }}</span></header><button v-for="item in dashboards" :key="item.id" type="button" :class="{ active: item.id === selected?.id }" @click="selectDashboard(item)"><LayoutDashboard :size="18" /><span><strong>{{ item.name }}</strong><small>{{ placementLabel(item.placement) }} · {{ item.scopeKey || item.code }}</small></span><a-tag :color="item.activeVersionId ? 'green' : 'default'">{{ item.activeVersionId ? `v${item.activeVersionNumber}` : '未发布' }}</a-tag></button><a-empty v-if="!loading && !dashboards.length" description="暂无仪表盘" /></aside>

      <main class="dashboard-editor" :aria-busy="detailLoading">
        <div v-if="!selected" class="dashboard-editor-empty"><LayoutDashboard :size="36" /><span>创建或选择仪表盘开始配置。</span></div>
        <template v-else>
          <header class="dashboard-toolbar"><div><h2>{{ selected.name }}</h2><p>{{ selected.code }} · {{ selected.placement }} · 草稿代次 {{ selected.draftVersion }}</p></div><div class="dashboard-actions"><span class="editor-state" :class="`state-${editorState}`" :data-state="editorState">{{ stateText[editorState] }}</span><a-button class="dashboard-save" :disabled="editorState !== 'dirty' && editorState !== 'error'" :loading="editorState === 'saving'" @click="saveDraft"><Save :size="15" />保存</a-button><a-button class="dashboard-check" @click="checkDraft"><CheckCircle2 :size="15" />检查</a-button><a-button class="dashboard-publish" type="primary" @click="publishDraft"><Send :size="15" />发布</a-button></div></header>

          <div class="dashboard-editor-content">
            <section class="dashboard-card dashboard-basics"><div class="card-heading"><div><strong>基础信息</strong><p>编码、位置和作用域创建后不可修改。</p></div></div><div class="form-grid"><label>名称<input v-model="editor.name" maxlength="200" @input="markDirty"></label><label>编码<input :value="selected.code" disabled></label><label>位置<input :value="placementLabel(selected.placement)" disabled></label><label v-if="selected.scopeKey">作用域<input :value="selected.scopeKey" disabled></label><label class="span-two">描述<textarea v-model="editor.description" rows="2" maxlength="2000" @input="markDirty" /></label></div></section>

            <section class="dashboard-card widgets-editor"><div class="card-heading"><div><strong>组件</strong><p>组件顺序决定接口和辅助展示顺序；桌面位置由数字网格控制。</p></div><div class="add-widget-actions"><span>{{ editor.widgets.length }}/20</span><a-button class="add-kpi-widget" :disabled="editor.widgets.length >= 20" @click="addWidget('KPI_VALUE')"><Gauge :size="15" />KPI</a-button><a-button class="add-value-widget" :disabled="editor.widgets.length >= 20" @click="addWidget('STAT_VALUE')"><Sigma :size="15" />指标</a-button><a-button class="add-ranking-widget" :disabled="editor.widgets.length >= 20" @click="addWidget('RANKING')"><BarChart3 :size="15" />排行</a-button><a-button class="add-progress-widget" :disabled="editor.widgets.length >= 20" @click="addWidget('PROGRESS')"><Gauge :size="15" />进度</a-button><a-button class="add-todo-widget" :disabled="editor.widgets.length >= 20" @click="addWidget('TODO_LIST')"><List :size="15" />待办</a-button><a-button class="add-quick-widget" :disabled="editor.widgets.length >= 20" @click="addWidget('QUICK_ENTRY')"><Plus :size="15" />快捷</a-button><a-button class="add-bar-widget" :disabled="editor.widgets.length >= 20" @click="addWidget('BAR_CHART')"><BarChart3 :size="15" />柱状</a-button><a-button class="add-pie-widget" :disabled="editor.widgets.length >= 20" @click="addWidget('PIE_CHART')"><PieChart :size="15" />饼图</a-button><a-button class="add-line-widget" :disabled="editor.widgets.length >= 20" @click="addWidget('LINE_TREND')"><LineChart :size="15" />趋势</a-button><a-button class="add-list-widget" :disabled="editor.widgets.length >= 20" @click="addWidget('DATA_LIST')"><List :size="15" />数据列表</a-button></div></div>
              <article v-for="(widget, index) in editor.widgets" :key="`${index}:${widget.code}`" class="widget-editor-card">
                <header><span class="widget-order">{{ index + 1 }}</span><div><strong>{{ widget.title || '未命名组件' }}</strong><small>{{ widget.type }}</small></div><div class="widget-row-actions"><button type="button" title="上移" :disabled="index === 0" @click="moveWidget(index, -1)"><ChevronUp :size="15" /></button><button type="button" title="下移" :disabled="index === editor.widgets.length - 1" @click="moveWidget(index, 1)"><ChevronDown :size="15" /></button><button type="button" title="删除" @click="removeWidget(index)"><Trash2 :size="15" /></button></div></header>
                <div class="widget-form-grid">
                  <label>类型<select v-model="widget.type" @change="widgetTypeChanged(widget)"><option value="STAT_COUNT">STAT_COUNT（兼容）</option><option value="DATA_LIST">DATA_LIST</option><option value="STAT_VALUE">STAT_VALUE</option><option value="BAR_CHART">BAR_CHART</option><option value="PIE_CHART">PIE_CHART</option><option value="LINE_TREND">LINE_TREND</option><option value="RANKING">RANKING</option><option value="PROGRESS">PROGRESS</option><option value="TODO_LIST">TODO_LIST</option><option value="QUICK_ENTRY">QUICK_ENTRY</option><option value="KPI_VALUE">KPI_VALUE</option></select></label>
                  <label>组件编码<input v-model="widget.code" maxlength="64" @input="markDirty"></label>
                  <label>标题<input v-model="widget.title" maxlength="200" @input="markDirty"></label>
                  <label v-if="!isKpiWidget(widget.type)">已发布数据源<select v-model="widget.dataSourceId" @change="sourceChanged(widget)"><option value="">请选择</option><option v-for="source in publishedSources" :key="source.id" :value="source.id">{{ source.name }}（{{ source.code }}）</option></select></label>
                  <label v-if="!isKpiWidget(widget.type)">发布时固定版本<input :value="activeSourceVersion(widget.dataSourceId ?? '')" disabled></label>
                  <label v-if="isKpiWidget(widget.type)">已发布 KPI<select v-model="widget.kpiId" @change="kpiChanged(widget)"><option value="">请选择</option><option v-for="kpi in publishedKpis" :key="kpi.id" :value="kpi.id">{{ kpi.name }}（{{ kpi.code }}）</option></select></label>
                  <label v-if="isKpiWidget(widget.type)">发布时固定版本<input :value="activeKpiVersion(widget.kpiId)" disabled></label>
                  <label v-if="isListWidget(widget.type)">行数上限<input v-model.number="widget.rowLimit" type="number" min="1" max="20" step="1" @input="markDirty"></label>
                </div>
                <fieldset v-if="isStatisticsWidget(widget.type) && widget.statistics" class="statistics-widget-controls"><legend>统计能力（精确发布版本）</legend>
                  <label>聚合<select v-model="widget.statistics.aggregation" @change="statisticsChanged(widget)"><option value="COUNT">COUNT</option><option value="SUM">SUM</option><option value="AVG">AVG</option><option value="MIN">MIN</option><option value="MAX">MAX</option></select></label>
                  <label v-if="widget.statistics.aggregation !== 'COUNT'">度量字段<select v-model="widget.statistics.measureFieldCode" @change="statisticsChanged(widget)"><option value="">请选择数值字段</option><option v-for="field in widgetCapability(widget).measureFields" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}（{{ field.fieldCode }}）</option></select></label>
                  <template v-if="(widget.type === 'BAR_CHART' || widget.type === 'PIE_CHART' || widget.type === 'RANKING') && widget.statistics.grouping">
                    <label>分组字段<select v-model="widget.statistics.grouping.fieldCode" @change="statisticsChanged(widget)"><option value="">请选择可分组字段</option><option v-for="field in widgetCapability(widget).groupFields" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}（{{ field.fieldCode }}）</option></select></label>
                    <label>分组上限<input v-model.number="widget.statistics.grouping.bucketLimit" type="number" min="1" max="20" step="1" @input="statisticsChanged(widget)"></label>
                  </template>
                  <template v-if="widget.type === 'LINE_TREND' && widget.statistics.trend">
                    <label>时间字段<select v-model="widget.statistics.trend.fieldCode" @change="statisticsChanged(widget)"><option value="">请选择时间字段</option><option v-for="field in widgetCapability(widget).timeFields" :key="field.fieldCode" :value="field.fieldCode">{{ field.fieldName }}（{{ field.fieldCode }}）</option></select></label>
                    <label>粒度<select v-model="widget.statistics.trend.grain" @change="statisticsChanged(widget)"><option value="DAY">DAY</option><option value="WEEK">WEEK</option><option value="MONTH">MONTH</option></select></label>
                    <label>开始（含）<input v-model="widget.statistics.trend.startInclusive" type="date" @input="statisticsChanged(widget)"></label>
                    <label>结束（不含）<input v-model="widget.statistics.trend.endExclusive" type="date" @input="statisticsChanged(widget)"></label>
                  </template>
                  <small class="capability-version">{{ sourceCapabilityPin(widget) }}</small>
                </fieldset>
                <fieldset v-if="widget.behavior" class="behavior-controls"><legend>交互与刷新</legend><label>自动刷新（秒）<input v-model.number="widget.behavior.refreshSeconds" type="number" min="0" max="3600" step="15" @input="markDirty"><small>0 为关闭；启用时至少 15 秒</small></label><label>点击跳转<input v-model="widget.behavior.clickThrough" maxlength="300" placeholder="/systems/..." @input="markDirty"></label><label>视觉强调<select v-model="widget.behavior.styleVariant" @change="markDirty"><option value="STANDARD">标准</option><option value="COMPACT">紧凑</option><option value="EMPHASIS">强调</option></select></label></fieldset>
                <fieldset class="grid-controls"><legend>桌面网格（12 列）</legend><label>X<input v-model.number="widget.grid.x" type="number" min="0" :max="DASHBOARD_GRID_COLUMNS - 1" step="1" @input="markDirty"></label><label>Y<input v-model.number="widget.grid.y" type="number" min="0" :max="DASHBOARD_GRID_ROWS - 1" step="1" @input="markDirty"></label><label>宽度<input v-model.number="widget.grid.width" type="number" min="1" :max="DASHBOARD_GRID_COLUMNS" step="1" @input="markDirty"></label><label>高度<input v-model.number="widget.grid.height" type="number" min="1" :max="DASHBOARD_GRID_ROWS" step="1" @input="markDirty"></label></fieldset>
              </article><a-empty v-if="!editor.widgets.length" description="至少添加一个统计卡或数据列表" />
            </section>

            <section class="dashboard-card layout-preview"><div class="card-heading"><div><strong>桌面布局预览</strong><p>数字坐标直接映射到 12 列网格；本批不提供拖拽。</p></div><a-tag :color="clientIssues.length ? 'red' : 'green'">{{ clientIssues.length ? `${clientIssues.length} 个问题` : '布局有效' }}</a-tag></div><div class="numeric-grid"><div v-for="(widget, index) in editor.widgets" :key="`${widget.code}:${index}`" class="numeric-widget" :style="gridStyle(widget.grid)"><strong>{{ index + 1 }}. {{ widget.title }}</strong><small>{{ widget.grid.x }},{{ widget.grid.y }} · {{ widget.grid.width }}×{{ widget.grid.height }}</small></div></div><ul v-if="clientIssues.length" class="client-issues"><li v-for="issue in clientIssues" :key="`${issue.code}:${issue.path}`"><code>{{ issue.code }}</code><span>{{ issue.message }}</span></li></ul></section>

            <section v-if="checkResult" class="dashboard-card check-result" :class="{ blocked: blockerCount > 0 }"><div class="card-heading"><div><strong>发布检查</strong><p>{{ blockerCount }} 个阻断，{{ warningCount }} 个警告</p></div><a-tag :color="blockerCount ? 'red' : 'green'">{{ blockerCount ? '不可发布' : '可以发布' }}</a-tag></div><ul v-if="checkResult.issues.length"><li v-for="issue in checkResult.issues" :key="`${issue.code}:${issue.path}`"><a-tag :color="issue.severity === 'BLOCKER' ? 'red' : 'orange'">{{ issue.severity }}</a-tag><strong>{{ issue.code }}</strong><code>{{ issue.path }}</code><span>{{ issue.message }}</span></li></ul></section>

            <section class="dashboard-card dashboard-real-preview"><div class="card-heading"><div><strong>已发布真实预览</strong><p v-if="preview">运行版本 v{{ preview.versionNumber }}；组件使用发布时固定的数据源版本。</p><p v-else>草稿不会进入预览，发布后调用真实 runtime endpoint。</p></div><a-button class="dashboard-preview-refresh" :disabled="!selected.activeVersionId" :loading="previewLoading" @click="loadPreview()"><Eye :size="15" />刷新预览</a-button></div><RuntimeDashboardCanvas v-if="preview" :dashboard="preview" @refresh="loadPreview()" /><a-empty v-else description="尚无已发布版本" /></section>

            <section class="dashboard-card dashboard-version-history"><div class="card-heading"><div><strong>发布版本</strong><p>不可变仪表盘快照固定组件顺序、布局和数据源或 KPI 版本；恢复只创建草稿。</p></div></div><table v-if="versions.length"><thead><tr><th>版本</th><th>组件数</th><th>固定版本</th><th>草稿代次</th><th>指纹</th><th>发布时间</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="version in versions" :key="version.id"><td>v{{ version.versionNumber }}</td><td>{{ version.widgetCount }}</td><td><span v-for="widget in version.widgets" :key="widget.id" class="pinned-source">{{ versionWidgetPin(widget) }}</span></td><td>{{ version.sourceDraftVersion }}</td><td><code>{{ version.fingerprint.slice(0, 12) }}</code></td><td>{{ formatTime(version.publishedAt) }}</td><td><a-tag :color="version.active ? 'green' : 'default'">{{ version.active ? '当前' : '历史' }}</a-tag></td><td><a-button v-if="!version.active" size="small" :disabled="editorState === 'dirty' || editorState === 'saving'" @click="restoreVersion(version)"><RotateCcw :size="14" />恢复草稿</a-button></td></tr></tbody></table><a-empty v-else description="暂无发布版本" /></section>
          </div>
        </template>
      </main>
    </div>

    <a-modal v-model:open="createOpen" title="新建仪表盘" :confirm-loading="creating" @ok="createDashboard"><a-form layout="vertical"><a-form-item label="名称" required><a-input v-model:value="createForm.name" class="create-dashboard-name" maxlength="200" /></a-form-item><a-form-item label="编码" required><a-input v-model:value="createForm.code" class="create-dashboard-code" maxlength="64" /></a-form-item><a-form-item label="位置" required><a-select v-model:value="createForm.placement"><a-select-option value="SYSTEM_HOME">系统首页</a-select-option><a-select-option value="APPLICATION_HOME">应用首页</a-select-option><a-select-option value="MODULE_HOME">模块首页</a-select-option><a-select-option value="PERSONAL_HOME">个人首页</a-select-option></a-select></a-form-item><a-form-item v-if="createForm.placement !== 'SYSTEM_HOME'" label="作用域标识" required><a-input v-model:value="createForm.scopeKey" class="create-dashboard-scope" maxlength="128" placeholder="例如 sales-app、module:orders 或 home" /></a-form-item><a-form-item label="描述"><a-textarea v-model:value="createForm.description" :rows="3" maxlength="2000" /></a-form-item></a-form><a-alert v-if="createError" class="create-dashboard-error" type="error" show-icon :message="createError" /></a-modal>
  </section>
</template>

<style scoped>
.dashboard-mobile-gate{min-width:0;padding:4px 0 28px}.dashboard-mobile-gate .ant-empty{padding:30px 12px;border:1px solid #dce3e7;border-radius:8px;background:#fff}.dashboard-studio { display: grid; grid-template-columns: 280px minmax(0,1fr); min-width:0; min-height: 760px; border: 1px solid #dce3e7; border-radius: 10px; overflow: hidden; background: #fff; }.dashboard-list { min-width:0;border-right: 1px solid #dce3e7; background: #f7f9fa; }.dashboard-list > header { display:flex;justify-content:space-between;padding:16px;border-bottom:1px solid #dce3e7; }.dashboard-list > button { width:100%;display:grid;grid-template-columns:24px minmax(0,1fr) auto;gap:9px;align-items:center;padding:13px;border:0;border-bottom:1px solid #e4e9ec;background:transparent;text-align:left;cursor:pointer; }.dashboard-list > button.active { background:#e8f2f7;box-shadow:inset 3px 0 #24638c; }.dashboard-list button > span:nth-child(2) { display:flex;min-width:0;flex-direction:column;gap:3px; }.dashboard-list small { color:#74818a; }
.dashboard-editor { min-width:0; }.dashboard-editor-empty { min-height:520px;display:grid;place-content:center;justify-items:center;gap:8px;color:#74818a; }.dashboard-toolbar { position:sticky;top:0;z-index:3;display:flex;justify-content:space-between;align-items:center;gap:16px;padding:14px 17px;border-bottom:1px solid #dce3e7;background:rgba(255,255,255,.97); }.dashboard-toolbar h2,.dashboard-toolbar p { margin:0; }.dashboard-toolbar p { margin-top:4px;color:#6c7a83;font-size:12px; }.dashboard-actions,.add-widget-actions,.widget-row-actions { display:flex;align-items:center;gap:8px; }.editor-state { padding:5px 9px;border-radius:999px;background:#edf1f3;color:#57636b;font-size:12px; }.state-dirty{color:#9b5700;background:#fff0d8}.state-saving{color:#1b5f88;background:#e3f1fa}.state-checked,.state-published{color:#23723f;background:#e4f5e9}.state-error{color:#ad3232;background:#fae5e5}
.dashboard-editor-content { min-width:0;padding:15px;background:#f4f7f8; }.dashboard-card { min-width:0;margin-bottom:14px;padding:16px;border:1px solid #dce3e7;border-radius:9px;background:#fff; }.card-heading { display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:13px; }.card-heading p { margin:3px 0 0;color:#697781;font-size:12px; }.form-grid { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px 16px; }.span-two{grid-column:span 2} label { display:flex;flex-direction:column;gap:6px;color:#56636c;font-size:12px; } input,select,textarea { width:100%;min-height:36px;box-sizing:border-box;padding:7px 9px;border:1px solid #cbd5da;border-radius:6px;background:#fff;color:#26353f;font:inherit; } input:disabled{background:#f1f3f4;color:#748089}
.widget-editor-card { margin-top:11px;border:1px solid #dce4e8;border-radius:8px;overflow:hidden; }.widget-editor-card > header { display:grid;grid-template-columns:30px minmax(0,1fr) auto;gap:10px;align-items:center;padding:10px 12px;background:#f5f8f9; }.widget-editor-card > header > div:nth-child(2){display:flex;flex-direction:column}.widget-editor-card small{color:#74818a}.widget-order{display:grid;place-items:center;width:26px;height:26px;border-radius:50%;background:#dceaf1;color:#245f83}.widget-row-actions button{display:grid;place-items:center;width:29px;height:29px;padding:0;border:1px solid #d1dbdf;border-radius:5px;background:#fff;cursor:pointer}.widget-form-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:11px;padding:12px}.grid-controls,.statistics-widget-controls,.behavior-controls{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:11px;margin:0 12px 12px;padding:10px 12px 12px;border:1px solid #dce4e8;border-radius:7px}.grid-controls legend,.statistics-widget-controls legend,.behavior-controls legend{padding:0 6px;color:#697781;font-size:12px}.statistics-widget-controls{border-color:#b9d4df;background:#f7fbfc}.behavior-controls{grid-template-columns:repeat(3,minmax(0,1fr));border-color:#d8c9a8;background:#fffaf0}.capability-version{align-self:end;padding-bottom:8px;color:#557582}
.numeric-grid{display:grid;grid-template-columns:repeat(12,minmax(0,1fr));grid-auto-rows:26px;gap:4px;min-height:90px;padding:8px;border:1px dashed #bdcbd2;background:#f6f9fa}.numeric-widget{min-width:0;overflow:hidden;display:flex;flex-direction:column;justify-content:center;padding:7px;border:1px solid #8fb2c5;border-radius:5px;background:#dcecf5;color:#245f83}.numeric-widget small{color:#587888}.client-issues{margin:12px 0 0;padding:0;list-style:none}.client-issues li{display:flex;gap:9px;padding:6px 0;color:#a33d3d}.check-result{border-left:4px solid #2b8550}.check-result.blocked{border-left-color:#b43d3d}.check-result ul{margin:0;padding:0;list-style:none}.check-result li{display:grid;grid-template-columns:auto auto minmax(100px,.5fr) minmax(0,1fr);gap:9px;align-items:center;padding:8px 0;border-top:1px solid #e3e8ea}.dashboard-version-history{overflow-x:auto}.dashboard-version-history table{min-width:760px;width:100%;border-collapse:collapse}.dashboard-version-history th,.dashboard-version-history td{padding:9px;border:1px solid #dfe6e9;text-align:left}.dashboard-version-history th{background:#f5f7f8;color:#52616b;font-size:12px}.pinned-source{display:block;white-space:nowrap}.create-dashboard-error{margin-top:12px}@media(max-width:1100px){.dashboard-studio{grid-template-columns:220px minmax(0,1fr)}.dashboard-toolbar{align-items:flex-start;flex-direction:column}.dashboard-actions,.add-widget-actions{width:100%;flex-wrap:wrap}.widget-form-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.grid-controls,.statistics-widget-controls,.behavior-controls{grid-template-columns:repeat(2,minmax(0,1fr))}}
</style>
