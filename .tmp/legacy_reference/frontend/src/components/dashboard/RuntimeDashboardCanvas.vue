<script setup lang="ts">
import { AlertTriangle, BarChart3, ExternalLink, Gauge, LineChart, List, PieChart, Sigma } from 'lucide-vue-next'
import { computed, onUnmounted, watch } from 'vue'

import StatisticsResultView from '@/components/statistics/StatisticsResultView.vue'
import RuntimeKpiDashboardWidget from './RuntimeKpiDashboardWidget.vue'
import type { RuntimeDataSourceRow } from '@/types/dataSource'
import type { RuntimeDashboard, RuntimeDashboardWidget } from '@/types/dashboard'
import type { StatisticsVisualization } from '@/types/statistics'
import { gridStyle } from '@/views/system/admin/dashboardEditorModel'

const props = defineProps<{ dashboard: RuntimeDashboard }>()
const emit = defineEmits<{ refresh: [] }>()
const orderedWidgets = computed(() => [...props.dashboard.widgets].sort((left, right) => left.ordinal - right.ordinal))
const hasPartialError = computed(() => orderedWidgets.value.some(widget => widget.status === 'ERROR'))
const refreshSeconds = computed(() => Math.min(...orderedWidgets.value
  .map(widget => widget.behavior?.refreshSeconds ?? 0)
  .filter(seconds => seconds >= 15), Number.POSITIVE_INFINITY))
let refreshTimer: ReturnType<typeof setInterval> | undefined

function rowValue(row: RuntimeDataSourceRow, fieldCode: string) {
  if (Array.isArray(row.values)) {
    const value = row.values.find(item => item.fieldCode === fieldCode)
    return value?.displayValue ?? value?.value ?? '—'
  }
  return row.values[fieldCode] ?? '—'
}

function rows(widget: RuntimeDashboardWidget) {
  return widget.rows ?? []
}

function statisticsVisualization(widget: RuntimeDashboardWidget): StatisticsVisualization | null {
  if (widget.type === 'RANKING') return 'BAR_CHART'
  if (widget.type === 'PROGRESS') return 'STAT_VALUE'
  if (widget.type === 'STAT_VALUE' || widget.type === 'BAR_CHART'
      || widget.type === 'PIE_CHART' || widget.type === 'LINE_TREND') return widget.type
  return null
}

function widgetPin(widget: RuntimeDashboardWidget) {
  return widget.type === 'KPI_VALUE'
    ? `${widget.kpiCode ?? 'KPI'} · v${widget.kpiVersionNumber ?? '—'}`
    : `${widget.dataSourceCode ?? '数据源'} · v${widget.dataSourceVersionNumber ?? '—'}`
}

watch(refreshSeconds, (seconds) => {
  if (refreshTimer) clearInterval(refreshTimer)
  refreshTimer = Number.isFinite(seconds)
    ? setInterval(() => emit('refresh'), seconds * 1_000)
    : undefined
}, { immediate: true })
onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer)
})
</script>

<template>
  <div class="runtime-dashboard-canvas" :data-partial-error="hasPartialError">
    <article
      v-for="widget in orderedWidgets"
      :key="widget.code"
      class="runtime-widget"
      :class="[`widget-${widget.type.toLowerCase().replaceAll('_', '-')}`, `style-${(widget.behavior?.styleVariant ?? 'STANDARD').toLowerCase()}`, { 'widget-error': widget.status === 'ERROR' }]"
      :style="gridStyle(widget.grid)"
      :data-widget-code="widget.code"
      :data-widget-status="widget.status"
    >
      <header><div><Gauge v-if="widget.type === 'KPI_VALUE' || widget.type === 'PROGRESS'" :size="17" /><Sigma v-else-if="widget.type === 'STAT_COUNT' || widget.type === 'STAT_VALUE'" :size="17" /><BarChart3 v-else-if="widget.type === 'BAR_CHART' || widget.type === 'RANKING'" :size="17" /><PieChart v-else-if="widget.type === 'PIE_CHART'" :size="17" /><LineChart v-else-if="widget.type === 'LINE_TREND'" :size="17" /><List v-else :size="17" /><strong>{{ widget.title }}</strong></div><span class="widget-meta"><small v-if="widget.behavior?.refreshSeconds">{{ widget.behavior.refreshSeconds }}s</small><a v-if="widget.behavior?.clickThrough" class="widget-click-through" :href="widget.behavior.clickThrough" :aria-label="`打开${widget.title}`"><ExternalLink :size="14" /></a><small>{{ widgetPin(widget) }}</small></span></header>
      <div v-if="widget.status === 'ERROR'" class="widget-error-body"><AlertTriangle :size="22" /><strong>{{ widget.error?.code ?? 'WIDGET_FAILED' }}</strong><span>{{ widget.error?.message ?? '组件数据暂时不可用' }}</span></div>
      <RuntimeKpiDashboardWidget v-else-if="widget.type === 'KPI_VALUE'" :targets="widget.kpiTargets" />
      <div v-else-if="widget.type === 'STAT_COUNT'" class="stat-count-value"><strong>{{ widget.total ?? 0 }}</strong><span>条记录</span></div>
      <StatisticsResultView v-else-if="statisticsVisualization(widget)" class="runtime-statistics-result" :visualization="statisticsVisualization(widget) ?? 'STAT_VALUE'" :result="widget.statisticsResult" />
      <div v-else class="data-list-result">
        <div class="data-list-scroll">
          <table v-if="widget.fields?.length && rows(widget).length"><thead><tr><th v-for="field in widget.fields" :key="field.fieldCode">{{ field.fieldName }}</th></tr></thead><tbody><tr v-for="row in rows(widget)" :key="row.recordId"><td v-for="field in widget.fields" :key="field.fieldCode">{{ rowValue(row, field.fieldCode) }}</td></tr></tbody></table>
        </div>
        <div v-if="!rows(widget).length" class="widget-empty">当前权限范围内暂无记录</div>
        <footer v-else>显示 {{ rows(widget).length }} / {{ widget.total ?? rows(widget).length }} 条</footer>
      </div>
    </article>
  </div>
</template>

<style scoped>
.runtime-dashboard-canvas { display: grid; grid-template-columns: repeat(12, minmax(0, 1fr)); grid-auto-rows: 42px; gap: 12px; align-items: stretch; }
.runtime-widget { min-width: 0; overflow: hidden; display: flex; flex-direction: column; border: 1px solid #dce4e8; border-radius: 10px; background: #fff; box-shadow: 0 2px 8px rgba(30,54,67,.05); }
.runtime-widget.style-emphasis{border-color:#78a9bd;box-shadow:0 5px 18px rgba(36,95,131,.16)}.runtime-widget.style-compact>header{padding:8px 10px}.runtime-widget.style-compact .data-list-result th,.runtime-widget.style-compact .data-list-result td{padding:5px 8px}
.runtime-widget > header { display: flex; justify-content: space-between; align-items: center; gap: 12px; padding: 12px 14px; border-bottom: 1px solid #e4eaed; }
.runtime-widget > header > div { display: flex; align-items: center; gap: 7px; color: #245f83; }.runtime-widget > header small { color: #7a8790; }
.widget-meta{display:flex;align-items:center;gap:7px}.widget-click-through{display:grid;place-items:center;color:#24638c}
.stat-count-value { flex: 1; display: flex; align-items: baseline; justify-content: center; gap: 8px; padding: 12px; }.stat-count-value strong { color: #146c72; font-size: 34px; }.stat-count-value span { color: #74818a; }
.runtime-statistics-result { min-height:0;flex:1;padding:13px;overflow:auto; }
.data-list-result { min-height: 0; flex: 1; display: flex; flex-direction: column; }.data-list-scroll { min-height: 0; flex: 1; overflow: auto; }.data-list-result table { width: 100%; border-collapse: collapse; }.data-list-result th,.data-list-result td { padding: 8px 10px; border-bottom: 1px solid #e6ecef; text-align: left; white-space: nowrap; }.data-list-result th { position: sticky; top: 0; background: #f5f8f9; color: #5d6b74; font-size: 12px; }.data-list-result footer { padding: 7px 10px; color: #75828a; font-size: 12px; text-align: right; }
.widget-error { border-color: #e4b6b6; }.widget-error-body,.widget-empty { flex: 1; display: grid; place-content: center; justify-items: center; gap: 5px; padding: 14px; color: #a03f3f; text-align: center; }.widget-empty { color: #78858d; }
</style>
