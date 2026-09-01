<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import { dashboardComponentSpan } from '../dashboard'
import { allowsPermission } from '../permissions'
import { dashboardComponentLabel, productStatus, userFacingDateTime } from '../presentation'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import type { DashboardRuntime, DashboardRuntimeComponent } from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system'; embedded?: boolean }>(), { context: 'system', embedded: false })
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

function chartWidth(component: DashboardRuntimeComponent, item: Record<string, unknown>) {
  const values = component.items.map(row => Number(row.value ?? row.progressPercent ?? 1)).filter(Number.isFinite)
  const current = Number(item.value ?? item.progressPercent ?? 1)
  const maximum = Math.max(...values, 1)
  return `${Math.max(8, Math.min(100, current / maximum * 100))}%`
}

onMounted(load)
onBeforeUnmount(() => refreshTimer && window.clearInterval(refreshTimer))
watch(() => props.context === 'platform' ? platformContext.value?.contextRevision
  : systemContext.value?.contextRevision, load)
</script>

<template>
  <section v-if="!embedded || dashboard?.configured" :class="['dashboard-runtime', { 'dashboard-runtime--embedded': embedded }]" aria-label="已发布仪表盘">
    <ProductPageHeader v-if="!embedded"
      :kicker="context === 'platform' ? '平台概览' : '系统概览'"
      :title="dashboard?.name || '工作概览'"
      :description="dashboard?.description || dashboard?.message || '集中查看当前工作范围内的重要进展和常用入口。'"
    >
      <template #actions>
        <a-tag v-if="dashboard?.configured" color="blue">发布版 v{{ dashboard.versionNumber }}</a-tag>
        <a-button :loading="loading" @click="load">刷新数据</a-button>
        <a-button v-if="canConfigure" type="primary" @click="router.push(configurationPath)">配置仪表盘</a-button>
      </template>
    </ProductPageHeader>

    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-skeleton v-else-if="loading && !dashboard" active :paragraph="{ rows: 6 }" />
    <a-empty v-else-if="!dashboard?.configured && !embedded" description="当前上下文尚未发布仪表盘">
      <template #description><p class="dashboard-empty-copy">{{ dashboard?.message }}</p></template>
      <a-button v-if="canConfigure" type="primary" @click="router.push(configurationPath)">创建并发布</a-button>
    </a-empty>
    <div v-else class="dashboard-grid">
      <article
        v-for="component in dashboard?.components || []"
        :key="component.componentKey"
        class="dashboard-card"
        :class="[`dashboard-card--${component.componentType.toLowerCase()}`, { 'dashboard-card--error': component.outcome === 'ERROR', 'dashboard-card--clickable': component.drillAvailable }]"
        :style="{ gridColumn: `span ${dashboardComponentSpan(component.layout)}`, '--dashboard-accent': String(component.displayConfig.accentColor || '#315efb') }"
        @click="drill(component)"
      >
        <header><div><small>{{ dashboardComponentLabel(component.componentType) }}</small><h2>{{ component.title }}</h2></div><ProductStatusTag :status="component.outcome" /></header>
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
          <div v-if="component.items.length" class="dashboard-bars">
            <div v-for="item in component.items" :key="String(item.id)"><span>{{ itemTitle(item) }}</span><i><b :style="{ width: chartWidth(component, item) }"></b></i><em>{{ itemStatus(item) || item.value || '' }}</em></div>
          </div>
          <a-empty v-else :image="false" description="当前权限范围内没有可展示数据" />
        </template>
        <template v-else-if="component.componentType === 'QUICK_ENTRY'">
          <a-button type="primary" :disabled="!component.drillAvailable">打开已授权页面</a-button>
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
        <footer><span>{{ updated(component.updatedAt) }}</span><span v-if="component.drillAvailable">可下钻</span></footer>
      </article>
    </div>
  </section>
</template>
