<script setup lang="ts">
import { CalendarRange, Gauge, RefreshCw } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import RuntimeKpiCard from '@/components/kpi/RuntimeKpiCard.vue'
import { runtimeKpiApi } from '@/services/kpi'
import type { KpiPeriodType, RuntimeKpiTarget } from '@/types/kpi'
import { alignedPeriodStart, defaultPeriodStart, KPI_PERIOD_OPTIONS } from './admin/kpiEditorModel'

const route = useRoute()
const systemId = computed(() => String(route.params.systemId))
const periodType = ref<KpiPeriodType>('MONTH')
const periodStart = ref(defaultPeriodStart('MONTH'))
const targets = ref<RuntimeKpiTarget[]>([])
const loading = ref(false)
const loaded = ref(false)
const errorMessage = ref('')
let generation = 0

const failedCount = computed(() => targets.value.filter(target => target.latestCalculation?.status === 'CALCULATION_FAILED').length)
const achievedCount = computed(() => targets.value.filter(target => target.latestCalculation?.status === 'ACHIEVED').length)
const periodError = computed(() => alignedPeriodStart(periodType.value, periodStart.value) ? '' : '请选择与周期边界对齐的开始日期')

function periodTypeChanged() {
  periodStart.value = defaultPeriodStart(periodType.value)
}

async function load() {
  const aligned = alignedPeriodStart(periodType.value, periodStart.value)
  if (!aligned) return
  const current = ++generation
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await runtimeKpiApi.list(systemId.value, { periodType: periodType.value, periodStart: aligned })
    if (current === generation) targets.value = result
  } catch (error) {
    if (current === generation) {
      targets.value = []
      errorMessage.value = error instanceof Error && error.message ? error.message : 'KPI 数据暂时不可用'
    }
  } finally {
    if (current === generation) {
      loading.value = false
      loaded.value = true
    }
  }
}

watch(systemId, load)
onMounted(load)
</script>

<template>
  <section class="system-kpi-page">
    <header class="runtime-kpi-heading">
      <div><h1><Gauge :size="24" />我的 KPI</h1><p>只显示当前适用于你的成员、部门和角色目标；结果、达成值与状态均由后端权威计算。</p></div>
      <a-button :loading="loading" :disabled="Boolean(periodError)" @click="load"><RefreshCw :size="15" />刷新</a-button>
    </header>

    <section class="kpi-period-bar">
      <CalendarRange :size="19" />
      <label>周期类型<select v-model="periodType" class="runtime-kpi-period-type" @change="periodTypeChanged"><option v-for="option in KPI_PERIOD_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option></select></label>
      <label>周期开始<input v-model="periodStart" class="runtime-kpi-period-start" type="date"></label>
      <a-button type="primary" :disabled="Boolean(periodError)" :loading="loading" @click="load">查询</a-button>
      <span v-if="periodError" class="period-error">{{ periodError }}</span>
    </section>

    <a-alert v-if="errorMessage" class="runtime-kpi-error" type="error" show-icon message="KPI 数据暂时不可用" :description="errorMessage" action="请稍后重试" />
    <a-alert v-if="failedCount" class="runtime-kpi-partial-error" type="warning" show-icon :message="`${failedCount} 个目标计算失败`" description="成功目标仍可正常查看；展开失败目标可查看稳定错误码。" />

    <section v-if="targets.length" class="kpi-runtime-summary">
      <div><span>适用目标</span><strong>{{ targets.length }}</strong></div><div><span>已达成</span><strong>{{ achievedCount }}</strong></div><div><span>计算失败</span><strong>{{ failedCount }}</strong></div>
    </section>
    <a-spin :spinning="loading">
      <div v-if="targets.length" class="runtime-kpi-grid"><RuntimeKpiCard v-for="target in targets" :key="target.id" :target="target" /></div>
      <a-empty v-else-if="loaded && !errorMessage" class="runtime-kpi-empty" description="当前周期没有适用于你的 KPI 目标" />
      <div v-else-if="loading" class="runtime-kpi-loading" role="status" aria-live="polite">正在加载 KPI 目标…</div>
    </a-spin>
  </section>
</template>

<style scoped>
.system-kpi-page{display:grid;gap:18px;max-width:1360px;min-width:0;margin:0 auto;padding:28px;background:#f4f6f8}.runtime-kpi-heading,.runtime-kpi-heading h1,.kpi-period-bar{display:flex;align-items:center}.runtime-kpi-heading{justify-content:space-between;gap:20px}.runtime-kpi-heading h1{gap:9px;margin:0}.runtime-kpi-heading p{margin:6px 0 0;color:#64748b;overflow-wrap:anywhere}.kpi-period-bar{gap:14px;padding:14px 16px;border:1px solid #dce5e9;border-radius:9px;background:#fff;flex-wrap:wrap}.kpi-period-bar label{display:flex;align-items:center;gap:7px;color:#596871;font-size:13px}.kpi-period-bar select,.kpi-period-bar input{min-height:34px;max-width:100%;padding:6px 9px;border:1px solid #cbd5da;border-radius:6px;background:#fff}.period-error{color:#b43d3d;font-size:12px}.runtime-kpi-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.kpi-runtime-summary{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.kpi-runtime-summary>div{display:flex;justify-content:space-between;align-items:center;padding:14px 16px;border:1px solid #dce5e9;border-radius:8px;background:#fff}.kpi-runtime-summary span{color:#64748b}.kpi-runtime-summary strong{font-size:24px}.runtime-kpi-empty,.runtime-kpi-loading{min-height:280px;display:grid;place-content:center}.runtime-kpi-loading{color:#74818a}
@media(max-width:900px){.system-kpi-page{padding:20px}.runtime-kpi-heading{align-items:flex-start}.runtime-kpi-grid{grid-template-columns:minmax(0,1fr)}}
@media(max-width:560px){.system-kpi-page{gap:14px;padding:14px}.runtime-kpi-heading{align-items:stretch;flex-direction:column}.runtime-kpi-heading>.ant-btn{align-self:flex-start}.kpi-period-bar{display:grid;gap:10px;padding:12px}.kpi-period-bar>svg{display:none}.kpi-period-bar label{align-items:stretch;flex-direction:column}.kpi-period-bar select,.kpi-period-bar input{width:100%}.kpi-runtime-summary{grid-template-columns:minmax(0,1fr)}.runtime-kpi-empty,.runtime-kpi-loading{min-height:220px}}
</style>
