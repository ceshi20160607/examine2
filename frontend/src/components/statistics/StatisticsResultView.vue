<script setup lang="ts">
import { AlertTriangle, LineChart, LoaderCircle, PieChart, Sigma } from 'lucide-vue-next'
import { computed } from 'vue'

import type { DataSourceStatisticsResult, StatisticsVisualization } from '@/types/statistics'

const props = withDefaults(defineProps<{
  visualization: StatisticsVisualization
  result?: DataSourceStatisticsResult | null
  loading?: boolean
  error?: string
}>(), { result: null, loading: false, error: '' })

const groupBuckets = computed(() => props.result?.groupBuckets ?? [])
const trendBuckets = computed(() => props.result?.trendBuckets ?? [])
const empty = computed(() => {
  if (!props.result) return true
  if (props.visualization === 'STAT_VALUE') return props.result.value == null
  if (props.visualization === 'LINE_TREND') return trendBuckets.value.length === 0
  return groupBuckets.value.length === 0
})
const state = computed(() => props.loading ? 'loading' : props.error ? 'error' : empty.value ? 'empty' : 'ready')
const maxGroupValue = computed(() => Math.max(0, ...groupBuckets.value.map(bucket => Math.abs(numericValue(bucket.value)))))
const pieStops = computed(() => {
  const values = groupBuckets.value.map(bucket => Math.abs(numericValue(bucket.value)))
  const total = values.reduce((sum, value) => sum + value, 0)
  if (!total) return '#dce4e8 0 100%'
  let offset = 0
  return values.map((value, index) => {
    const start = offset
    offset += value / total * 100
    return `${pieColor(index)} ${start}% ${offset}%`
  }).join(', ')
})
const linePoints = computed(() => {
  const values = trendBuckets.value.map(bucket => numericValue(bucket.value))
  if (!values.length) return ''
  const min = Math.min(...values)
  const max = Math.max(...values)
  const span = max - min || 1
  return values.map((value, index) => {
    const x = values.length === 1 ? 50 : index / (values.length - 1) * 100
    const y = 88 - (value - min) / span * 76
    return `${x},${y}`
  }).join(' ')
})

function numericValue(value: string | null) {
  if (value == null) return 0
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

function barWidth(value: string | null) {
  const maximum = maxGroupValue.value
  return `${maximum > 0 ? Math.max(2, Math.abs(numericValue(value)) / maximum * 100) : 0}%`
}

function bucketLabel(label: string | null, nullBucket: boolean) {
  return nullBucket || label == null ? '空值' : label
}

function pieColor(index: number) {
  return ['#26769c', '#27a38d', '#e0a43b', '#8a72c4', '#dc6c6c', '#5e8f4a'][index % 6]
}
</script>

<template>
  <div class="statistics-result" :data-state="state" :data-visualization="visualization">
    <div v-if="loading" class="statistics-state statistics-loading">
      <LoaderCircle class="spin" :size="23" /><span>正在计算真实统计结果…</span>
    </div>
    <div v-else-if="error" class="statistics-state statistics-error">
      <AlertTriangle :size="23" /><strong>统计暂不可用</strong><span>{{ error }}</span>
    </div>
    <div v-else-if="empty" class="statistics-state statistics-empty">
      <Sigma :size="23" /><span>当前权限与条件下暂无统计结果</span>
    </div>

    <div v-else-if="visualization === 'STAT_VALUE'" class="stat-value">
      <Sigma :size="22" /><strong>{{ result?.value }}</strong><span>{{ result?.aggregation }}</span>
    </div>

    <div v-else-if="visualization === 'BAR_CHART'" class="bar-chart">
      <div v-for="bucket in groupBuckets" :key="`${bucket.nullBucket}:${bucket.key}`" class="bar-row">
        <span class="chart-label">{{ bucketLabel(bucket.label, bucket.nullBucket) }}</span>
        <span class="bar-track"><i :class="{ negative: numericValue(bucket.value) < 0 }" :style="{ width: barWidth(bucket.value) }" /></span>
        <strong>{{ bucket.value ?? '—' }}</strong>
      </div>
      <footer v-if="result?.truncated">仅展示前 {{ groupBuckets.length }} 组，共 {{ result.totalBucketCount }} 组</footer>
    </div>

    <div v-else-if="visualization === 'PIE_CHART'" class="pie-chart">
      <div class="pie-graphic" :style="{ background: `conic-gradient(${pieStops})` }"><PieChart :size="25" /></div>
      <ul><li v-for="(bucket, index) in groupBuckets" :key="`${bucket.nullBucket}:${bucket.key}`"><i :style="{ background: pieColor(index) }" /><span>{{ bucketLabel(bucket.label, bucket.nullBucket) }}</span><strong>{{ bucket.value ?? '—' }}</strong></li></ul>
      <footer v-if="result?.truncated">其余 {{ Math.max(0, result.totalBucketCount - groupBuckets.length) }} 组未展示</footer>
    </div>

    <div v-else class="line-trend">
      <div class="line-graphic"><LineChart :size="18" /><svg viewBox="0 0 100 100" preserveAspectRatio="none" role="img" aria-label="时间趋势折线"><polyline :points="linePoints" /></svg></div>
      <ol><li v-for="bucket in trendBuckets" :key="bucket.startInclusive" :class="{ empty: bucket.empty }"><span>{{ bucket.startInclusive }}</span><strong>{{ bucket.value ?? '—' }}</strong></li></ol>
    </div>

    <small v-if="result && state === 'ready'" class="statistics-meta">{{ result.dataSourceCode }} · v{{ result.dataSourceVersionNumber }} · {{ result.matchedRecordCount }} 条匹配记录 · {{ result.bucketCount }} 个桶 · {{ result.queryId }}</small>
  </div>
</template>

<style scoped>
.statistics-result{min-height:110px;display:flex;flex-direction:column;gap:10px}.statistics-state{min-height:100px;display:grid;place-content:center;justify-items:center;gap:6px;color:#75838c;text-align:center}.statistics-error{color:#a03f3f}.spin{animation:spin 1s linear infinite}@keyframes spin{to{transform:rotate(360deg)}}
.stat-value{flex:1;display:flex;align-items:baseline;justify-content:center;gap:9px;color:#287697}.stat-value strong{font-size:34px;line-height:1}.stat-value span{color:#71808a}
.bar-chart{display:flex;flex-direction:column;gap:8px}.bar-row{display:grid;grid-template-columns:minmax(70px,1fr) minmax(120px,4fr) minmax(65px,auto);align-items:center;gap:9px}.chart-label{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;color:#5d6b74}.bar-track{height:14px;overflow:hidden;border-radius:999px;background:#edf2f4}.bar-track i{display:block;height:100%;border-radius:inherit;background:#2d86aa}.bar-track i.negative{background:#d76b6b}.bar-chart footer,.pie-chart footer{color:#8b611e;font-size:12px}
.pie-chart{display:grid;grid-template-columns:112px minmax(0,1fr);gap:13px;align-items:center}.pie-graphic{display:grid;place-items:center;width:108px;height:108px;border-radius:50%;color:#fff}.pie-chart ul{margin:0;padding:0;list-style:none}.pie-chart li{display:grid;grid-template-columns:10px minmax(0,1fr) auto;gap:7px;align-items:center;padding:3px 0}.pie-chart li i{width:9px;height:9px;border-radius:2px}.pie-chart footer{grid-column:1/-1}
.line-trend{min-height:0}.line-graphic{position:relative;height:120px;border-left:1px solid #cbd6db;border-bottom:1px solid #cbd6db;background:linear-gradient(#edf2f4 1px,transparent 1px);background-size:100% 25%}.line-graphic>svg:first-child{position:absolute;top:5px;left:6px;color:#77858e}.line-graphic svg:last-child{width:100%;height:100%;overflow:visible}.line-graphic polyline{fill:none;stroke:#287fa3;stroke-width:2;vector-effect:non-scaling-stroke}.line-trend ol{display:flex;margin:7px 0 0;padding:0;list-style:none;overflow:hidden}.line-trend li{min-width:64px;flex:1;display:flex;flex-direction:column;gap:2px;font-size:11px;color:#6d7b84}.line-trend li.empty{opacity:.55}.line-trend li strong{color:#2f4653}
.statistics-meta{margin-top:auto;color:#849099;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
</style>
