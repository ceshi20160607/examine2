<script setup lang="ts">
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { init, use, type ECharts, type EChartsCoreOption } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

use([BarChart, LineChart, PieChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

const props = withDefaults(defineProps<{
  items: Record<string, unknown>[]
  chartType?: 'BAR' | 'LINE' | 'PIE'
  accentColor?: string
}>(), { chartType: 'BAR', accentColor: '#315efb' })
const emit = defineEmits<{ select: [item: Record<string, unknown>] }>()
const host = ref<HTMLDivElement>()
let chart: ECharts | undefined
let observer: ResizeObserver | undefined

const rows = computed(() => props.items.map((item, index) => ({
  ...item,
  name: String(item.title || item.name || item.label || item.id || `第 ${index + 1} 项`),
  metricValue: Number(item.value ?? item.progressPercent ?? item.total ?? 0),
})).filter(item => Number.isFinite(item.metricValue)))

function option(): EChartsCoreOption {
  const colors = [props.accentColor, '#59a6ff', '#52c7a5', '#f4b557', '#8b7cf6', '#ef7c8e']
  if (props.chartType === 'PIE') {
    return {
      color: colors,
      tooltip: { trigger: 'item', valueFormatter: (value: unknown) => new Intl.NumberFormat('zh-CN').format(Number(value)) },
      legend: { bottom: 0, type: 'scroll', textStyle: { color: '#667085' } },
      series: [{ type: 'pie', radius: ['42%', '70%'], center: ['50%', '43%'],
        label: { formatter: '{b}\n{d}%' }, data: rows.value.map(item => ({ name: item.name, value: item.metricValue })) }],
    }
  }
  return {
    color: colors,
    tooltip: { trigger: 'axis', axisPointer: { type: props.chartType === 'BAR' ? 'shadow' : 'line' } },
    grid: { top: 18, right: 18, bottom: 42, left: 48 },
    xAxis: { type: 'category', data: rows.value.map(item => item.name), axisLabel: { color: '#667085', overflow: 'truncate', width: 90 } },
    yAxis: { type: 'value', minInterval: 1, axisLabel: { color: '#98a2b3' }, splitLine: { lineStyle: { color: '#edf1f7' } } },
    series: [{ type: props.chartType === 'LINE' ? 'line' : 'bar', smooth: true,
      barMaxWidth: 36, symbolSize: 7, areaStyle: props.chartType === 'LINE' ? { opacity: .08 } : undefined,
      data: rows.value.map(item => item.metricValue), itemStyle: { borderRadius: props.chartType === 'BAR' ? [5, 5, 0, 0] : 0 } }],
  }
}

async function render() {
  await nextTick()
  if (!host.value) return
  chart ||= init(host.value, undefined, { renderer: 'canvas' })
  chart.setOption(option(), true)
}

onMounted(async () => {
  await render()
  if (host.value) {
    observer = new ResizeObserver(() => chart?.resize())
    observer.observe(host.value)
  }
  chart?.on('click', parameters => {
    const row = rows.value[Number(parameters.dataIndex)]
    if (row) emit('select', row)
  })
})
watch(() => [props.items, props.chartType, props.accentColor], render, { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>

<template><div ref="host" class="dashboard-echart" role="img" aria-label="业务统计图表" /></template>
