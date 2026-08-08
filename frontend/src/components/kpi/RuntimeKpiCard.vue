<script setup lang="ts">
import { computed } from 'vue'

import KpiCalculationExplanation from './KpiCalculationExplanation.vue'
import type { RuntimeKpiTarget } from '@/types/kpi'
import { kpiStatusColor, kpiStatusLabel } from '@/views/system/admin/kpiEditorModel'

const props = defineProps<{ target: RuntimeKpiTarget }>()
const calculation = computed(() => props.target.latestCalculation)
</script>

<template>
  <article class="runtime-kpi-card" :class="calculation?.status.toLowerCase()">
    <header>
      <div><span class="kpi-code">{{ target.kpiCode }}</span><h2>{{ target.kpiName }}</h2><p>{{ target.subjectName }} · {{ target.periodStart }} ～ {{ target.periodEndExclusive }}</p></div>
      <a-tag v-if="calculation" :color="kpiStatusColor(calculation.status)">{{ kpiStatusLabel(calculation.status) }}</a-tag>
      <a-tag v-else>未计算</a-tag>
    </header>
    <div class="runtime-kpi-values">
      <div><span>目标</span><strong>{{ target.targetValue }}</strong></div>
      <div><span>实际</span><strong>{{ calculation?.actualValue ?? '—' }}</strong></div>
      <div><span>达成值</span><strong>{{ calculation?.attainment ?? '—' }}</strong></div>
    </div>
    <KpiCalculationExplanation :calculation="calculation" />
  </article>
</template>

<style scoped>
.runtime-kpi-card{display:grid;gap:14px;padding:18px;border:1px solid #dde5e8;border-top:4px solid #86949c;border-radius:9px;background:#fff}.runtime-kpi-card.achieved{border-top-color:#2d8a53}.runtime-kpi-card.at_risk{border-top-color:#d18a18}.runtime-kpi-card.missed,.runtime-kpi-card.calculation_failed{border-top-color:#bf4646}.runtime-kpi-card>header{display:flex;justify-content:space-between;align-items:flex-start;gap:14px}.runtime-kpi-card h2{margin:2px 0 0;font-size:18px}.runtime-kpi-card header p{margin:5px 0 0;color:#74818a;font-size:12px}.kpi-code{color:#487185;font:12px ui-monospace,monospace}.runtime-kpi-values{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px}.runtime-kpi-values>div{display:grid;gap:4px;padding:11px;border-radius:7px;background:#f4f7f8}.runtime-kpi-values span{color:#74818a;font-size:12px}.runtime-kpi-values strong{font-size:22px;font-variant-numeric:tabular-nums}
</style>
