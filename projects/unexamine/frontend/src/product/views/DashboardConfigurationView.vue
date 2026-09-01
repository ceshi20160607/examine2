<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ApiError, api } from '../api'
import { dashboardSourceLabels, moveDashboardComponent, sourceDefinition } from '../dashboard'
import { platformTokens, systemContext, systemTokens } from '../session'
import ReportSourceEditor from './ReportSourceEditor.vue'
import KpiConfigurationView from './KpiConfigurationView.vue'
import type {
  DashboardAdminOverview, DashboardComponentInput, DashboardComponentType, DashboardDataSource,
  DashboardDefinition, DashboardPreview, DashboardSourceType, KpiOverview,
} from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'system' })
const token = computed(() => props.context === 'platform'
  ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const overview = ref<DashboardAdminOverview>()
const kpiOverview = ref<KpiOverview>({ kpis: [] })
const preview = ref<DashboardPreview>()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const selectedSourceId = ref<number>()
const selectedDashboardId = ref<number>()
const dragIndex = ref<number>()

const sourceTypes = computed<DashboardSourceType[]>(() => props.context === 'platform'
  ? ['PLATFORM_SYSTEMS', 'TODO_ITEMS', 'MESSAGE_ITEMS', 'WORK_PROJECTS']
  : ['MODULE_RECORDS', 'MODULE_REPORT', 'TODO_ITEMS', 'MESSAGE_ITEMS', 'WORK_PROJECTS'])
const sourceForm = reactive({
  code: '', name: '', sourceType: (props.context === 'platform' ? 'PLATFORM_SYSTEMS' : 'MODULE_RECORDS') as DashboardSourceType,
  moduleCode: '', limit: 5, reportDefinition: {} as Record<string, unknown>, expectedVersion: undefined as number | undefined,
})
const dashboardForm = reactive({
  code: '', name: '', description: '', expectedVersion: undefined as number | undefined,
  components: [] as DashboardComponentInput[],
})
const componentForm = reactive({
  componentKey: '', componentType: 'METRIC' as DashboardComponentType, title: '',
  dataSourceId: undefined as number | undefined, width: 1, refreshSeconds: 60,
  accentColor: '#315efb', drillRoute: '', drillModuleCode: '',
  kpiId: undefined as number | undefined,
})

function message(cause: unknown) {
  return cause instanceof ApiError ? `${cause.code}：${cause.message}` : '操作失败，请稍后重试'
}

async function load() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    overview.value = await api<DashboardAdminOverview>('/api/analytics/admin', {}, token.value)
    kpiOverview.value = props.context === 'system'
      ? await api<KpiOverview>('/api/analytics/admin/kpis', {}, token.value) : { kpis: [] }
    if (selectedSourceId.value) {
      const current = overview.value.dataSources.find(item => item.id === selectedSourceId.value)
      if (current) fillSource(current)
    }
    if (selectedDashboardId.value) {
      const current = overview.value.dashboards.find(item => item.id === selectedDashboardId.value)
      if (current) fillDashboard(current)
    }
  } catch (cause) { error.value = message(cause) } finally { loading.value = false }
}

function resetSource() {
  selectedSourceId.value = undefined
  Object.assign(sourceForm, { code: '', name: '', sourceType: props.context === 'platform' ? 'PLATFORM_SYSTEMS' : 'MODULE_RECORDS', moduleCode: '', limit: 5, reportDefinition: {}, expectedVersion: undefined })
}

function fillSource(source: DashboardDataSource) {
  selectedSourceId.value = source.id
  Object.assign(sourceForm, {
    code: source.code, name: source.name, sourceType: source.sourceType,
    moduleCode: String(source.definition.moduleCode || ''), limit: Number(source.definition.limit || 5),
    reportDefinition: source.sourceType === 'MODULE_REPORT' ? source.definition : {},
    expectedVersion: source.version,
  })
}

async function saveSource() {
  if (!token.value) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    const body = {
      code: sourceForm.code, name: sourceForm.name, sourceType: sourceForm.sourceType,
      definition: sourceForm.sourceType === 'MODULE_REPORT' ? sourceForm.reportDefinition
        : sourceDefinition(sourceForm.sourceType, sourceForm.moduleCode, sourceForm.limit),
      permissionPolicy: ['MODULE_RECORDS', 'MODULE_REPORT'].includes(sourceForm.sourceType)
        ? { resourceType: 'MODULE', resourceCode: sourceForm.sourceType === 'MODULE_REPORT'
          ? String((sourceForm.reportDefinition.modules as Record<string, unknown>[] | undefined)?.[0]?.moduleCode || '')
          : sourceForm.moduleCode.trim(), actionCode: 'LIST' } : {},
      expectedVersion: sourceForm.expectedVersion,
    }
    const path = selectedSourceId.value
      ? `/api/analytics/admin/data-sources/${selectedSourceId.value}` : '/api/analytics/admin/data-sources'
    const saved = await api<DashboardDataSource>(path, {
      method: selectedSourceId.value ? 'PUT' : 'POST', body: JSON.stringify(body),
    }, token.value)
    selectedSourceId.value = saved.id
    success.value = `数据源草稿“${saved.name}”已保存，修订 r${saved.draftRevision}。`
    await load()
  } catch (cause) { error.value = message(cause) } finally { saving.value = false }
}

async function publishSource(source: DashboardDataSource) {
  if (!token.value) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    await api(`/api/analytics/admin/data-sources/${source.id}/publish`, {
      method: 'POST', body: JSON.stringify({ expectedDraftRevision: source.draftRevision }),
    }, token.value)
    success.value = `数据源“${source.name}”已发布为不可变版本。`
    await load()
  } catch (cause) { error.value = message(cause) } finally { saving.value = false }
}

function resetDashboard() {
  selectedDashboardId.value = undefined
  preview.value = undefined
  Object.assign(dashboardForm, { code: '', name: '', description: '', expectedVersion: undefined, components: [] })
}

function fillDashboard(dashboard: DashboardDefinition) {
  selectedDashboardId.value = dashboard.id
  preview.value = undefined
  Object.assign(dashboardForm, {
    code: dashboard.code, name: dashboard.name, description: dashboard.description || '',
    expectedVersion: dashboard.version,
    components: dashboard.components.map(({ id: _id, version: _version, ...component }) => ({
      ...component, drillTarget: component.drillTarget || {},
    })),
  })
}

function addComponent() {
  if (!componentForm.componentKey.trim() || !componentForm.title.trim()) {
    error.value = '组件编码和标题不能为空。'; return
  }
  const selectedKpi = componentForm.componentType === 'KPI'
    ? kpiOverview.value.kpis.find(item => item.id === componentForm.kpiId) : undefined
  const kpiSource = selectedKpi ? overview.value?.dataSources.find(source => source.versions
    .some(version => version.id === selectedKpi.dataSourceVersionId)) : undefined
  if (componentForm.componentType === 'KPI' && (!selectedKpi || !kpiSource)) {
    error.value = 'KPI 组件必须选择一个可用 KPI，其精确报表版本也必须仍在当前数据源版本历史中。'; return
  }
  if (componentForm.componentType !== 'QUICK_ENTRY' && componentForm.componentType !== 'KPI' && !componentForm.dataSourceId) {
    error.value = '除快捷入口外，组件必须绑定一个数据源。'; return
  }
  if (dashboardForm.components.some(item => item.componentKey === componentForm.componentKey.trim())) {
    error.value = '组件编码不能重复。'; return
  }
  const drillTarget: Record<string, unknown> = {}
  if (selectedKpi) {
    drillTarget.route = `/systems/${systemContext.value?.systemId}?workspace=kpi&kpiId=${selectedKpi.id}`
  } else if (componentForm.drillModuleCode.trim()) {
    drillTarget.moduleCode = componentForm.drillModuleCode.trim()
    drillTarget.permission = { resourceType: 'MODULE', resourceCode: componentForm.drillModuleCode.trim(), actionCode: 'LIST' }
  } else if (componentForm.drillRoute.trim()) drillTarget.route = componentForm.drillRoute.trim()
  dashboardForm.components.push({
    componentKey: componentForm.componentKey.trim(), componentType: componentForm.componentType,
    title: componentForm.title.trim(), dataSourceId: componentForm.componentType === 'QUICK_ENTRY' ? undefined
      : selectedKpi ? kpiSource?.id : componentForm.dataSourceId,
    layout: { width: componentForm.width }, queryParameters: selectedKpi
      ? { kpiId: selectedKpi.id, kpiVersion: selectedKpi.version } : {},
    displayConfig: { accentColor: componentForm.accentColor, refreshSeconds: componentForm.refreshSeconds },
    drillTarget, sortOrder: dashboardForm.components.length * 10,
  })
  Object.assign(componentForm, { componentKey: '', title: '', dataSourceId: undefined, drillRoute: '', drillModuleCode: '', kpiId: undefined })
  error.value = ''
}

function move(from: number, to: number) {
  dashboardForm.components = moveDashboardComponent(dashboardForm.components, from, to)
}

function drop(to: number) {
  if (dragIndex.value !== undefined) move(dragIndex.value, to)
  dragIndex.value = undefined
}

async function saveDashboard() {
  if (!token.value) return
  saving.value = true; error.value = ''; success.value = ''; preview.value = undefined
  try {
    const body = { code: dashboardForm.code, name: dashboardForm.name, description: dashboardForm.description,
      components: dashboardForm.components, expectedVersion: dashboardForm.expectedVersion }
    const path = selectedDashboardId.value
      ? `/api/analytics/admin/dashboards/${selectedDashboardId.value}` : '/api/analytics/admin/dashboards'
    const saved = await api<DashboardDefinition>(path, {
      method: selectedDashboardId.value ? 'PUT' : 'POST', body: JSON.stringify(body),
    }, token.value)
    selectedDashboardId.value = saved.id
    success.value = `仪表盘草稿“${saved.name}”已保存，修订 r${saved.draftRevision}。`
    await load()
  } catch (cause) { error.value = message(cause) } finally { saving.value = false }
}

async function previewDashboard() {
  if (!token.value || !selectedDashboardId.value) return
  loading.value = true; error.value = ''; success.value = ''
  try {
    preview.value = await api<DashboardPreview>(`/api/analytics/admin/dashboards/${selectedDashboardId.value}/preview`, {}, token.value)
    success.value = preview.value.valid ? '预览执行成功，所有可见组件均返回真实结果。' : '预览发现无效组件，修复前不能发布。'
  } catch (cause) { error.value = message(cause) } finally { loading.value = false }
}

async function publishDashboard() {
  if (!token.value || !selectedDashboardId.value) return
  const current = overview.value?.dashboards.find(item => item.id === selectedDashboardId.value)
  if (!current) return
  saving.value = true; error.value = ''; success.value = ''
  try {
    await api(`/api/analytics/admin/dashboards/${current.id}/publish`, {
      method: 'POST', body: JSON.stringify({ expectedDraftRevision: current.draftRevision }),
    }, token.value)
    success.value = `仪表盘“${current.name}”已发布；运行页已切换到新的不可变版本。`
    await load(); await previewDashboard()
  } catch (cause) { error.value = message(cause) } finally { saving.value = false }
}

onMounted(load)
watch(() => props.context, () => { resetSource(); resetDashboard(); void load() })
</script>

<template>
  <section class="dashboard-configuration">
    <div class="page-heading"><div><p class="eyebrow">{{ context === 'platform' ? '平台后台' : '系统后台' }} · 仪表盘</p><h1>可配置仪表盘</h1><p>数据源、组件可见性、查询、样式、刷新和下钻分别配置；预览通过后才生成不可变发布版本。</p></div><a-button :loading="loading" @click="load">刷新配置</a-button></div>
    <a-alert v-if="error" type="error" show-icon :message="error" closable @close="error = ''" />
    <a-alert v-if="success" type="success" show-icon :message="success" closable @close="success = ''" />

    <a-tabs default-active-key="sources" class="dashboard-config-tabs">
      <a-tab-pane key="sources" tab="1. 数据源">
        <div class="dashboard-config-layout">
          <aside class="panel-card dashboard-config-list">
            <div class="panel-title"><strong>当前上下文数据源</strong><a-button size="small" @click="resetSource">新建</a-button></div>
            <button v-for="source in overview?.dataSources" :key="source.id" type="button" :class="{ active: selectedSourceId === source.id }" @click="fillSource(source)">
              <span><strong>{{ source.name }}</strong><small>{{ dashboardSourceLabels[source.sourceType] }} · r{{ source.draftRevision }}</small></span><a-tag :color="source.status === 'PUBLISHED' ? 'green' : 'orange'">{{ source.status }}</a-tag>
            </button>
            <a-empty v-if="!overview?.dataSources.length" :image="false" description="尚未配置数据源" />
          </aside>
          <div class="panel-card dashboard-config-form">
            <div class="panel-title"><strong>{{ selectedSourceId ? '编辑数据源草稿' : '新建数据源' }}</strong><span>发布版本与后续草稿互不覆盖</span></div>
            <a-form layout="vertical">
              <div class="form-grid form-grid--three"><a-form-item label="编码"><a-input v-model:value="sourceForm.code" placeholder="customer_records" /></a-form-item><a-form-item label="名称"><a-input v-model:value="sourceForm.name" placeholder="客户记录" /></a-form-item><a-form-item label="类型"><a-select v-model:value="sourceForm.sourceType" :options="sourceTypes.map(value => ({ value, label: dashboardSourceLabels[value] }))" /></a-form-item></div>
              <ReportSourceEditor v-if="sourceForm.sourceType === 'MODULE_REPORT'" :key="selectedSourceId || 'new-report'" :token="token" :source-id="selectedSourceId" :initial-definition="sourceForm.reportDefinition" @definition="sourceForm.reportDefinition = $event" />
              <div v-else class="form-grid"><a-form-item v-if="sourceForm.sourceType === 'MODULE_RECORDS'" label="模块编码"><a-input v-model:value="sourceForm.moduleCode" placeholder="customer" /></a-form-item><a-form-item label="返回条数（1-20）"><a-input-number v-model:value="sourceForm.limit" :min="1" :max="20" /></a-form-item></div>
              <div class="form-actions"><a-button type="primary" :loading="saving" @click="saveSource">保存草稿</a-button><a-button v-if="selectedSourceId" :loading="saving" @click="publishSource(overview!.dataSources.find(item => item.id === selectedSourceId)!)">发布数据源</a-button></div>
            </a-form>
            <div v-if="selectedSourceId" class="dashboard-version-strip"><span v-for="version in overview?.dataSources.find(item => item.id === selectedSourceId)?.versions" :key="version.id">v{{ version.versionNumber }} · r{{ version.draftRevision }} · {{ version.definitionHash.slice(0, 8) }}</span></div>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane key="dashboard" tab="2. 布局与发布">
        <div class="dashboard-config-layout">
          <aside class="panel-card dashboard-config-list">
            <div class="panel-title"><strong>仪表盘草稿</strong><a-button size="small" @click="resetDashboard">新建</a-button></div>
            <button v-for="item in overview?.dashboards" :key="item.id" type="button" :class="{ active: selectedDashboardId === item.id }" @click="fillDashboard(item)"><span><strong>{{ item.name }}</strong><small>{{ item.components.length }} 组件 · r{{ item.draftRevision }}</small></span><a-tag :color="item.status === 'PUBLISHED' ? 'green' : 'orange'">{{ item.status }}</a-tag></button>
            <a-empty v-if="!overview?.dashboards.length" :image="false" description="尚未配置仪表盘" />
          </aside>
          <div class="dashboard-builder">
            <section class="panel-card dashboard-config-form">
              <div class="form-grid form-grid--three"><a-form-item label="编码"><a-input v-model:value="dashboardForm.code" placeholder="system_home" /></a-form-item><a-form-item label="名称"><a-input v-model:value="dashboardForm.name" placeholder="经营工作台" /></a-form-item><a-form-item label="说明"><a-input v-model:value="dashboardForm.description" placeholder="说明统计范围与用途" /></a-form-item></div>
            </section>
            <section class="panel-card dashboard-component-editor">
              <div class="panel-title"><strong>组件库</strong><span>拖动卡片调整顺序；宽度控制一至四列</span></div>
              <div class="form-grid form-grid--three"><a-form-item label="组件编码"><a-input v-model:value="componentForm.componentKey" placeholder="customer_count" /></a-form-item><a-form-item label="标题"><a-input v-model:value="componentForm.title" placeholder="客户总数" /></a-form-item><a-form-item label="类型"><a-select v-model:value="componentForm.componentType" :options="['METRIC','CHART','LIST','TODO','QUICK_ENTRY','KPI','PROGRESS','RANKING'].map(value => ({ value, label: value }))" /></a-form-item></div>
              <div class="form-grid form-grid--three"><a-form-item :label="componentForm.componentType === 'KPI' ? '绑定 KPI 配置版本' : '绑定数据源'"><a-select v-if="componentForm.componentType === 'KPI'" v-model:value="componentForm.kpiId" allow-clear :options="kpiOverview.kpis.map(item => ({ value: item.id, label: `${item.name} · 配置 v${item.version}` }))" /><a-select v-else v-model:value="componentForm.dataSourceId" allow-clear :disabled="componentForm.componentType === 'QUICK_ENTRY'" :options="overview?.dataSources.map(item => ({ value: item.id, label: `${item.name} · ${item.status}` }))" /></a-form-item><a-form-item label="卡片宽度"><a-input-number v-model:value="componentForm.width" :min="1" :max="4" /></a-form-item><a-form-item label="刷新秒数"><a-input-number v-model:value="componentForm.refreshSeconds" :min="15" :max="3600" /></a-form-item></div>
              <div class="form-grid form-grid--three"><a-form-item label="强调色"><a-input v-model:value="componentForm.accentColor" type="color" /></a-form-item><a-form-item label="下钻模块"><a-input v-model:value="componentForm.drillModuleCode" placeholder="customer" /></a-form-item><a-form-item label="或下钻路由"><a-input v-model:value="componentForm.drillRoute" placeholder="/platform/tasks" /></a-form-item></div>
              <a-button type="dashed" block @click="addComponent">加入布局</a-button>
            </section>
            <section class="dashboard-draft-grid">
              <article v-for="(component, index) in dashboardForm.components" :key="component.componentKey" class="dashboard-draft-card" draggable="true" @dragstart="dragIndex = index" @dragover.prevent @drop="drop(index)"><span class="dashboard-drag-handle">⋮⋮</span><div><small>{{ component.componentType }} · {{ component.componentKey }}</small><strong>{{ component.title }}</strong><em>{{ overview?.dataSources.find(item => item.id === component.dataSourceId)?.name || '无数据源快捷入口' }}</em></div><div><a-button size="small" :disabled="index === 0" @click="move(index, index - 1)">↑</a-button><a-button size="small" :disabled="index === dashboardForm.components.length - 1" @click="move(index, index + 1)">↓</a-button><a-button size="small" danger @click="dashboardForm.components.splice(index, 1); dashboardForm.components = moveDashboardComponent(dashboardForm.components, -1, -1)">删除</a-button></div></article>
              <a-empty v-if="!dashboardForm.components.length" description="从组件库加入至少一个组件" />
            </section>
            <div class="form-actions dashboard-publish-actions"><a-button type="primary" :loading="saving" @click="saveDashboard">保存草稿</a-button><a-button :disabled="!selectedDashboardId" :loading="loading" @click="previewDashboard">执行预览</a-button><a-button :disabled="!selectedDashboardId || preview?.valid === false" :loading="saving" danger @click="publishDashboard">发布新版本</a-button></div>
            <section v-if="preview" class="panel-card dashboard-preview-panel"><div class="panel-title"><strong>真实数据预览 · r{{ preview.draftRevision }}</strong><a-tag :color="preview.valid ? 'green' : 'red'">{{ preview.valid ? '可发布' : '不可发布' }}</a-tag></div><a-alert v-for="issue in preview.issues" :key="`${issue.componentKey}:${issue.code}`" type="error" show-icon :message="`${issue.componentKey} · ${issue.code}`" :description="issue.message" /><div class="dashboard-preview-grid"><article v-for="component in preview.components" :key="component.componentKey" :class="{ error: component.outcome === 'ERROR' }"><small>{{ component.componentType }} · {{ component.outcome }}</small><strong>{{ component.title }}</strong><b v-if="component.outcome === 'READY'">{{ component.value ?? `${component.items.length} 项` }}</b><p>{{ component.metricDefinition }}</p><em>{{ component.errorCode || (component.drillAvailable ? '下钻已授权' : '无下钻') }}</em></article></div></section>
            <div v-if="selectedDashboardId" class="dashboard-version-strip"><span v-for="version in overview?.dashboards.find(item => item.id === selectedDashboardId)?.versions" :key="version.id" :class="{ current: version.current }">v{{ version.versionNumber }} · r{{ version.draftRevision }} · {{ version.snapshotHash.slice(0, 8) }} <b v-if="version.current">当前</b></span></div>
          </div>
        </div>
      </a-tab-pane>

      <a-tab-pane v-if="context === 'system'" key="kpi" tab="3. KPI 闭环">
        <KpiConfigurationView :token="token" :data-sources="overview?.dataSources || []" />
      </a-tab-pane>
    </a-tabs>
  </section>
</template>
