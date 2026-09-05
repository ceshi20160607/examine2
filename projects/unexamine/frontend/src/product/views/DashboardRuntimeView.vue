<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import DashboardChart from '../components/DashboardChart.vue'
import { dashboardComponentSpan, isCoreWorkspaceSummary } from '../dashboard'
import { allowsPermission } from '../permissions'
import { dashboardComponentLabel, productStatus, userFacingDateTime, versionLabel } from '../presentation'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import type { DashboardRuntime, DashboardRuntimeComponent } from '../types'

const props = withDefaults(defineProps<{
  context?: 'platform' | 'system'
  embedded?: boolean
  excludeCoreSummary?: boolean
}>(), { context: 'system', embedded: false, excludeCoreSummary: false })
const router = useRouter()
const dashboard = ref<DashboardRuntime>()
const loading = ref(false)
const error = ref('')
let refreshTimer: number | undefined

const token = computed(() => props.context === 'platform'
  ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const canConfigure = computed(() => props.context === 'platform'
  ? allowsPermission(platformContext.value?.permissions, 'PLATFORM', 'CONFIGURATION', 'MANAGE')
  : allowsPermission(systemContext.value?.permissions, 'CONFIG', 'SYSTEM', 'MANAGE'))
const configurationPath = computed(() => props.context === 'platform'
  ? '/platform/admin/dashboards'
  : `/systems/${systemContext.value?.systemId}/admin?section=dashboard`)
const visibleComponents = computed(() => (dashboard.value?.components || []).filter((component) =>
  !props.excludeCoreSummary || !isCoreWorkspaceSummary(component.title)))

function resetRefreshTimer() {
  if (refreshTimer) window.clearInterval(refreshTimer)
  const seconds = dashboard.value?.components
    .map(item => Number(item.displayConfig.refreshSeconds || 0))
    .filter(value => Number.isFinite(value) && value >= 15)
    .sort((a, b) => a - b)[0]
  if (seconds) refreshTimer = window.setInterval(() => void load(), seconds * 1000)
}

async function load() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    dashboard.value = await api<DashboardRuntime>('/api/analytics/runtime', {}, token.value)
    resetRefreshTimer()
  } catch (cause) {
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
  void router.push({
    path: resolved.path,
    query: {
      ...resolved.query,
      ...(filter ? { filters: JSON.stringify([filter]) } : {}),
      dashboardReturn: router.currentRoute.value.fullPath,
    },
  })
}

function itemTitle(item: Record<string, unknown>) {
  return String(item.title || item.name || item.id || '未命名项')
}

function formatValue(value: unknown) {
  if (typeof value === 'number') return new Intl.NumberFormat('zh-CN').format(value)
  return value === null || value === undefined ? '—' : String(value)
}

function updated(value?: string) {
  return userFacingDateTime(value)
}

function itemStatus(item: Record<string, unknown>) {
  return item.status ? productStatus(String(item.status)).label : ''
}

onMounted(load)
onBeforeUnmount(() => refreshTimer && window.clearInterval(refreshTimer))
watch(() => props.context === 'platform' ? platformContext.value?.contextRevision
  : systemContext.value?.contextRevision, load)
</script>

<template>
  <section v-if="!embedded || dashboard?.configured && visibleComponents.length" :class="['dashboard-runtime', { 'dashboard-runtime--embedded': embedded }]" aria-label="业务指标">
    <ProductPageHeader v-if="!embedded"
      :kicker="context === 'platform' ? '平台概览' : '系统概览'"
      :title="dashboard?.name || '工作概览'"
      :description="dashboard?.description || dashboard?.message || '集中查看当前工作范围内的重要进展和常用入口。'"
    >
      <template #actions>
        <a-tag v-if="dashboard?.configured" color="blue">{{ versionLabel(dashboard.versionNumber) }}</a-tag>
        <a-button :loading="loading" @click="load">刷新数据</a-button>
      </template>
      <template #primary><a-button v-if="canConfigure" type="primary" @click="router.push(configurationPath)">配置仪表盘</a-button></template>
    </ProductPageHeader>

    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-skeleton v-else-if="loading && !dashboard" active :paragraph="{ rows: 6 }" />
    <a-empty v-else-if="!dashboard?.configured && !embedded" description="当前上下文尚未发布仪表盘">
      <template #description><p class="dashboard-empty-copy">{{ dashboard?.message }}</p></template>
      <a-button v-if="canConfigure" type="primary" @click="router.push(configurationPath)">创建并发布</a-button>
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
        <template v-else-if="component.componentType === 'METRIC' || component.componentType === 'KPI'">
          <strong class="dashboard-metric">{{ formatValue(component.value) }}</strong>
          <p class="dashboard-definition">{{ component.metricDefinition }}</p>
        </template>
        <template v-else-if="component.componentType === 'PROGRESS'">
          <a-progress :percent="Number(component.value || 0)" />
          <p class="dashboard-definition">{{ component.metricDefinition }}</p>
        </template>
        <template v-else-if="component.componentType === 'CHART' || component.componentType === 'RANKING'">
          <DashboardChart v-if="component.items.length" :items="component.items" :chart-type="chartType(component)"
            :accent-color="String(component.displayConfig.accentColor || '#315efb')" @click.stop @select="drillItem(component, $event)" />
          <a-empty v-else :image="false" description="当前权限范围内没有可展示数据" />
        </template>
        <template v-else-if="component.componentType === 'QUICK_ENTRY'">
          <a-button :disabled="!component.drillAvailable">打开{{ component.title }}</a-button>
          <p class="dashboard-definition">{{ component.message }}</p>
        </template>
        <template v-else>
          <div v-if="component.items.length" class="dashboard-list">
            <button v-for="item in component.items" :key="String(item.id)" type="button" :disabled="!item.route" @click.stop="openTarget(item.route)">
              <span><strong>{{ itemTitle(item) }}</strong><small>{{ item.subtitle || itemStatus(item) }}</small></span><em>{{ itemStatus(item) || '打开' }}</em>
            </button>
          </div>
          <a-empty v-else :image="false" description="当前权限范围内没有可展示数据" />
        </template>
        <footer><span>{{ updated(component.updatedAt) }}</span><span v-if="component.drillAvailable">查看详情</span></footer>
      </article>
    </div>
  </section>
</template>
