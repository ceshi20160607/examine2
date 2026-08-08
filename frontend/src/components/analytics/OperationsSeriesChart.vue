<script setup lang="ts">
import { LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import * as echarts from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'

echarts.use([LineChart, GridComponent, LegendComponent, TooltipComponent, CanvasRenderer])

export interface OperationsChartSeries {
  name: string
  data: number[]
}

const props = defineProps<{
  categories: string[]
  series: OperationsChartSeries[]
}>()

const element = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function render() {
  if (!element.value) return
  chart ??= echarts.init(element.value)
  chart.setOption({
    animation: false,
    color: ['#0f766e', '#2563eb', '#d97706', '#7c3aed'],
    tooltip: { trigger: 'axis' },
    legend: { top: 0 },
    grid: { top: 42, right: 20, bottom: 32, left: 48 },
    xAxis: {
      type: 'category',
      data: props.categories,
      axisLabel: { hideOverlap: true },
    },
    yAxis: { type: 'value', minInterval: 1 },
    series: props.series.map(item => ({
      name: item.name,
      type: 'line',
      smooth: false,
      symbolSize: 6,
      data: item.data,
    })),
  }, true)
}

onMounted(render)
watch(() => [props.categories, props.series], () => void nextTick(render), { deep: true })
onBeforeUnmount(() => {
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div ref="element" class="operations-series-chart" />
</template>

<style scoped>
.operations-series-chart{width:100%;height:300px}
</style>
