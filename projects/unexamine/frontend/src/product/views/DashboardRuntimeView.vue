<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import DashboardChart from '../components/DashboardChart.vue'
import { dashboardComponentSpan, dashboardMetricSummary, formatDashboardMetric } from '../dashboard'
import { kpiStatusLabel } from '../kpi'
import { allowsPermission } from '../permissions'
import { dashboardComponentLabel, productStatus, userFacingDateTime, versionLabel } from '../presentation'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import type { DashboardRuntime, DashboardRuntimeComponent, KpiDefinition, KpiOverview } from '../types'

const props = withDefaults(defineProps<{
  context?: 'platform' | 'system'
  variant?: 'PUBLIC' | 'MODULE' | 'PERSONAL'
  moduleCode?: string
  embedded?: boolean
}>(), { context: 'system', variant: 'PUBLIC', embedded: false })
const router = useRouter()
const dashboard = ref<DashboardRuntime>()
const kpiOverview = ref<KpiOverview>({ kpis: [] })
const loading = ref(false)
const error = ref('')
let refreshTimer: number | undefined

const token = computed(() => props.context === 'platform'
  ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const canConfigure = computed(() => props.context === 'platform'
  ? allowsPermission(platformContext.value?.permissions, 'PLATFORM', 'CONFIGURATION', 'MANAGE')
  : props.variant === 'PERSONAL' || allowsPermission(systemContext.value?.permissions, 'CONFIG', 'SYSTEM', 'MANAGE')
    || props.variant === 'MODULE' && allowsPermission(systemContext.value?.permissions, 'CONFIG', 'MODULE', 'MANAGE'))
const configurationPath = computed(() => props.variant === 'PERSONAL'
  ? props.context === 'platform' ? '/platform/my-dashboard?edit=1'
    : `/systems/${systemContext.value?.systemId}?workspace=personal&edit=1`
  : props.context === 'platform' ? '/platform/admin/dashboards'
    : `/systems/${systemContext.value?.systemId}/admin?section=dashboard`)
const runtimePath = computed(() => props.variant === 'PERSONAL' ? '/api/analytics/personal/runtime'
  : props.variant === 'MODULE' && props.moduleCode
    ? `/api/analytics/runtime?moduleCode=${encodeURIComponent(props.moduleCode)}`
    : '/api/analytics/runtime')
const visibleComponents = computed(() => dashboard.value?.components || [])

function resetRefreshTimer() {
  if (refreshTimer) window.clearInterval(refreshTimer)
  const seconds = dashboard.value?.components
    .map(item => Number(item.displayConfig.refreshSeconds || 0))
    .filter(value => Number.isFinite(value) && value >= 15)
    .sort((a, b) => a - b)[0]
  if (seconds) refreshTimer = window.setInterval(() => void load(), seconds * 1000)
}

async function load() {
  if (!token.value) { dashboard.value = undefined; return }
  loading.value = true
  error.value = ''
  try {
    dashboard.value = await api<DashboardRuntime>(runtimePath.value, {}, token.value)
    if (props.context === 'system' && dashboard.value.components.some(item => item.componentType === 'KPI')) {
      // 指标状态以同一权限上下文的 KPI 运行接口为准，不从展示文本推断。
      try { kpiOverview.value = await api<KpiOverview>('/api/analytics/kpis', {}, token.value) }
      catch { kpiOverview.value = { kpis: [] } }
    } else kpiOverview.value = { kpis: [] }
    resetRefreshTimer()
  } catch (cause) {
    dashboard.value = undefined
    error.value = cause instanceof ApiError ? cause.message : '仪表盘加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

function openTarget(target: unknown) {
  const route = typeof target === 'string' ? target : ''
  if (route) void router.push(route)
}

function drill(component: DashboardRuntimeComponent) {
  if (!component.drillAvailable) return
  openTarget(component.drillTarget.route)
}

function chartType(component: DashboardRuntimeComponent): 'BAR' | 'LINE' | 'PIE' {
  const type = String(component.displayConfig.chartType || '').toUpperCase()
  if (type === 'LINE' || type === 'PIE') return type
  return 'BAR'
}

function drillItem(component: DashboardRuntimeComponent, item: Record<string, unknown>) {
  if (!component.drillAvailable) return
  const target = typeof item.route === 'string' ? item.route : component.drillTarget.route
  if (typeof target !== 'string' || !target) return
  const resolved = router.resolve(target)
  const filter = item.filter && typeof item.filter === 'object' && !Array.isArray(item.filter)
    ? item.filter : undefined
  const filters = Array.isArray(item.filters) ? item.filters : filter ? [filter] : []
  void router.push({
    path: resolved.path,
    query: {
      ...resolved.query,
      ...(filters.length ? { filters: JSON.stringify(filters) } : {}),
      dashboardReturn: router.currentRoute.value.fullPath,
    },
  })
}

function itemTitle(item: Record<string, unknown>) {
  return String(item.title || item.name || item.id || '未命名项')
}

function updated(value?: string) {
  return userFacingDateTime(value)
}

function itemStatus(item: Record<string, unknown>) {
  return item.status ? productStatus(String(item.status)).label : ''
}

function kpiFor(component: DashboardRuntimeComponent): KpiDefinition | undefined {
  const route = String(component.drillTarget.route || '')
  const id = Number(route.match(/[?&]kpiId=(\d+)/)?.[1])
  return kpiOverview.value.kpis.find(item => item.id === id)
}

function kpiColor(component: DashboardRuntimeComponent) {
  const result = kpiFor(component)?.latestResult
  return result?.stale ? 'orange' : result?.status === 'ACHIEVED' ? 'green' : 'red'
}

/** 报表列表展示所有已授权的显式输出值，并避免重复标题和状态。 */
function itemSubtitle(item: Record<string, unknown>) {
  const title = itemTitle(item)
  const status = itemStatus(item)
  const rawStatus = String(item.status || '').trim()
  const fields = item.fields && typeof item.fields === 'object' && !Array.isArray(item.fields)
    ? Object.values(item.fields as Record<string, unknown>) : []
  const details = fields.map(value => value === null || value === undefined ? '' : String(value).trim())
    .filter(value => value && value !== title && value !== status && value !== rawStatus)
  const distinct = [...new Set(details)]
  const subtitle = String(item.subtitle || '').trim()
  return distinct.length ? distinct.join(' · ')
    : subtitle && subtitle !== title && subtitle !== status && subtitle !== rawStatus ? subtitle : ''
}

onMounted(load)
onBeforeUnmount(() => refreshTimer && window.clearInterval(refreshTimer))
watch(() => props.context === 'platform' ? platformContext.value?.contextRevision
  : systemContext.value?.contextRevision, () => { dashboard.value = undefined; void load() })
watch(token, () => { dashboard.value = undefined; void load() })
watch(() => [props.variant, props.moduleCode], () => { dashboard.value = undefined; void load() })
</script>

<template>
  <!-- 空租户首页也要呈现未配置状态，避免用户误以为仪表盘加载失败。 -->
  <section v-if="!embedded || loading || error || dashboard?.configured && visibleComponents.length || dashboard && !dashboard.configured" :class="['dashboard-runtime', { 'dashboard-runtime--embedded': embedded }]" aria-label="业务指标">
    <ProductPageHeader v-if="!embedded"
      :kicker="variant === 'MODULE' ? '模块首页' : variant === 'PERSONAL' ? '我的首页' : context === 'platform' ? '平台概览' : '系统概览'"
      :title="dashboard?.name || '工作概览'"
      :description="dashboard?.description || dashboard?.message || '集中查看当前工作范围内的重要进展和常用入口。'"
    >
      <template #actions>
        <a-tag v-if="dashboard?.configured" color="blue">{{ variant === 'PERSONAL' ? '已保存' : versionLabel(dashboard.versionNumber) }}</a-tag>
        <a-button :loading="loading" @click="load">刷新数据</a-button>
      </template>
      <template #primary><a-button v-if="canConfigure" type="primary" @click="router.push(configurationPath)">{{ variant === 'PERSONAL' ? '设置我的首页' : '配置仪表盘' }}</a-button></template>
    </ProductPageHeader>
    <header v-else-if="dashboard?.configured" class="dashboard-runtime__embedded-heading">
      <div><small>{{ context === 'platform' ? '平台公共首页' : '系统公共首页' }}</small><h2>{{ dashboard.name || '工作概览' }}</h2><p>{{ dashboard.description || '当前权限范围内的业务进展' }}</p></div>
      <a-button :loading="loading" @click="load">刷新数据</a-button>
    </header>

    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-skeleton v-else-if="loading && !dashboard" active :paragraph="{ rows: 6 }" />
    <a-empty v-else-if="!dashboard?.configured" :description="variant === 'PERSONAL' ? '你还没有保存个人首页' : variant === 'MODULE' ? '当前模块尚未发布首页' : context === 'system' ? '当前租户尚未发布仪表盘' : '当前平台尚未发布仪表盘'">
      <template #description><p class="dashboard-empty-copy">{{ dashboard?.message }}</p></template>
      <a-button v-if="canConfigure" type="primary" @click="router.push(configurationPath)">{{ variant === 'PERSONAL' ? '创建我的首页' : '创建并发布' }}</a-button>
    </a-empty>
    <div v-else class="dashboard-grid">
      <article
        v-for="component in visibleComponents"
        :key="component.componentKey"
        class="dashboard-card"
        :class="[`dashboard-card--${component.componentType.toLowerCase()}`, { 'dashboard-card--error': component.outcome === 'ERROR', 'dashboard-card--clickable': component.drillAvailable }]"
        :style="{ gridColumn: `span ${dashboardComponentSpan(component.layout)}`, '--dashboard-accent': String(component.displayConfig.accentColor || '#315efb') }"
        @click="drill(component)"
      >
        <header><div><small v-if="!embedded">{{ dashboardComponentLabel(component.componentType) }}</small><h2>{{ component.title }}</h2></div><ProductStatusTag v-if="!['READY', 'SUCCESS', 'SUCCEEDED'].includes(component.outcome)" :status="component.outcome" /></header>
        <a-alert
          v-if="component.outcome === 'ERROR'"
          type="error"
          show-icon
          message="数据暂时无法加载"
          :description="`${component.message} · ${updated(component.updatedAt)}`"
        />
        <template v-else-if="component.componentType === 'METRIC'">
          <strong class="dashboard-metric" :class="{ 'dashboard-metric--compact': formatDashboardMetric(component.value).length > 10 }">{{ formatDashboardMetric(component.value) }}</strong>
          <p class="dashboard-definition" :title="component.metricDefinition">{{ dashboardMetricSummary(component.metricDefinition) }}</p>
        </template>
        <template v-else-if="component.componentType === 'KPI'">
          <div v-if="kpiFor(component)?.latestResult" class="dashboard-kpi-state"><a-tag :color="kpiColor(component)">{{ kpiStatusLabel(kpiFor(component)?.latestResult?.status || '') }}</a-tag><span>{{ kpiFor(component)?.latestResult?.periodKey }}</span></div>
          <strong class="dashboard-metric" :class="{ 'dashboard-metric--compact': formatDashboardMetric(component.value).length > 10 }">{{ formatDashboardMetric(component.value) }}</strong>
          <div v-if="kpiFor(component)?.latestResult?.achievementRate != null" class="dashboard-kpi-facts"><span>目标 <b>{{ kpiFor(component)?.targetOperator === 'LTE' ? '≤' : '≥' }} {{ formatDashboardMetric(kpiFor(component)?.latestResult?.targetValue) }}</b></span><span>达成率 <b>{{ Number(kpiFor(component)?.latestResult?.achievementRate).toFixed(2) }}%</b></span></div>
          <a-progress v-if="kpiFor(component)?.latestResult?.achievementRate != null" :percent="Math.max(0, Math.min(100, Number(kpiFor(component)?.latestResult?.achievementRate)))" :show-info="false" :status="kpiFor(component)?.latestResult?.stale ? 'normal' : kpiFor(component)?.latestResult?.status === 'ACHIEVED' ? 'success' : 'exception'" />
          <p v-if="kpiFor(component)?.latestResult?.stale" class="dashboard-definition">{{ kpiFor(component)?.latestResult?.actualValue === null ? '当前权限下无法读取指标数据' : '数据源本次不可用，显示上次成功结果' }}</p>
          <p v-else-if="!kpiFor(component)?.latestResult" class="dashboard-definition" :title="component.metricDefinition">{{ dashboardMetricSummary(component.metricDefinition) }}</p>
        </template>
        <template v-else-if="component.componentType === 'PROGRESS'">
          <a-progress :percent="Number(component.value || 0)" />
          <p class="dashboard-definition" :title="component.metricDefinition">{{ dashboardMetricSummary(component.metricDefinition) }}</p>
        </template>
        <template v-else-if="component.componentType === 'CHART' || component.componentType === 'RANKING'">
          <DashboardChart v-if="component.items.length" :items="component.items" :chart-type="chartType(component)"
            :accent-color="String(component.displayConfig.accentColor || '#315efb')" @click.stop @select="drillItem(component, $event)" />
          <div v-if="component.items.length && chartType(component) === 'LINE'" class="dashboard-chart-points">
            <button v-for="item in component.items" :key="String(item.id)" type="button"
              :disabled="!component.drillAvailable" @click.stop="drillItem(component, item)">
              <span>{{ itemTitle(item) }}</span><strong>{{ formatDashboardMetric(item.value) }}</strong>
            </button>
          </div>
          <a-empty v-if="!component.items.length" :image="false" description="当前权限范围内没有可展示数据" />
        </template>
        <template v-else-if="component.componentType === 'QUICK_ENTRY'">
          <a-button :disabled="!component.drillAvailable">打开{{ component.title }}</a-button>
          <p class="dashboard-definition">{{ component.message }}</p>
        </template>
        <template v-else>
          <div v-if="component.items.length" class="dashboard-list">
            <button v-for="item in component.items" :key="String(item.id)" type="button" :disabled="!item.route" @click.stop="openTarget(item.route)">
              <span><strong>{{ itemTitle(item) }}</strong><small v-if="itemSubtitle(item)" :title="itemSubtitle(item)">{{ itemSubtitle(item) }}</small></span><em>{{ itemStatus(item) || '打开' }}</em>
            </button>
          </div>
          <a-empty v-else :image="false" description="当前权限范围内没有可展示数据" />
        </template>
        <footer><span>{{ updated(component.updatedAt) }}</span><span v-if="component.drillAvailable">查看详情</span></footer>
      </article>
    </div>
  </section>
</template>

<style scoped>
.dashboard-runtime__embedded-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin: 22px 0 14px; }
.dashboard-runtime__embedded-heading small { color: #315efb; font-weight: 700; }
.dashboard-runtime__embedded-heading h2 { margin: 5px 0; font-size: 22px; }
.dashboard-runtime__embedded-heading p { margin: 0; color: #66758a; }
.dashboard-metric {
  min-width: 0;
  max-width: 100%;
  font-size: clamp(28px, 3vw, 46px);
  line-height: 1.12;
  overflow-wrap: anywhere;
}
.dashboard-metric--compact { font-size: clamp(24px, 2.3vw, 34px); }
.dashboard-definition { overflow-wrap: anywhere; }
.dashboard-kpi-state { display: flex; align-items: center; justify-content: space-between; gap: 8px; color: #66758a; font-size: 12px; }
.dashboard-kpi-facts { display: grid; gap: 6px; color: #66758a; font-size: 13px; }
.dashboard-kpi-facts span { display: flex; justify-content: space-between; gap: 8px; }
.dashboard-kpi-facts b { color: #243b66; overflow-wrap: anywhere; text-align: right; }
.dashboard-chart-points { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
.dashboard-chart-points button { display: flex; gap: 12px; align-items: center; border: 1px solid #dce5f4; border-radius: 8px; padding: 6px 10px; background: #f9fbff; color: #315079; }
.dashboard-chart-points button:not(:disabled):hover { border-color: var(--dashboard-accent); }
.dashboard-chart-points strong { color: #1e3a6e; }
</style>
