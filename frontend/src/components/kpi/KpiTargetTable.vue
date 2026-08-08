<script setup lang="ts">
import { Calculator, History, Pencil } from 'lucide-vue-next'

import type { KpiTarget } from '@/types/kpi'
import { kpiStatusColor, kpiStatusLabel } from '@/views/system/admin/kpiEditorModel'

withDefaults(defineProps<{
  targets: KpiTarget[]
  calculatingId?: string
  historyLoadingId?: string
}>(), { calculatingId: '', historyLoadingId: '' })
const emit = defineEmits<{
  edit: [target: KpiTarget]
  calculate: [target: KpiTarget]
  history: [target: KpiTarget]
}>()

function formatTime(value: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <div class="kpi-target-table-wrap">
    <table v-if="targets.length" class="kpi-target-table">
      <thead><tr><th>对象</th><th>周期</th><th>目标</th><th>实际</th><th>达成值</th><th>结果</th><th>计算时间</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="target in targets" :key="target.id" :class="{ 'calculation-failed': target.latestCalculation?.status === 'CALCULATION_FAILED' }">
          <td><strong>{{ target.subjectName || target.subjectId }}</strong><small>{{ target.subjectType }} · {{ target.subjectId }}</small></td>
          <td>{{ target.periodStart }} ～ {{ target.periodEndExclusive }}</td>
          <td class="decimal-value">{{ target.targetValue }}</td>
          <td class="decimal-value">{{ target.latestCalculation?.actualValue ?? '—' }}</td>
          <td class="decimal-value">{{ target.latestCalculation?.attainment ?? '—' }}</td>
          <td><a-tag v-if="target.latestCalculation" :color="kpiStatusColor(target.latestCalculation.status)">{{ kpiStatusLabel(target.latestCalculation.status) }}</a-tag><span v-else>未计算</span><code v-if="target.latestCalculation?.errorCode">{{ target.latestCalculation.errorCode }}</code></td>
          <td>{{ formatTime(target.latestCalculation?.calculatedAt || '') }}</td>
          <td><div class="target-actions"><button type="button" title="编辑目标" @click="emit('edit', target)"><Pencil :size="15" /></button><button type="button" title="立即计算" :disabled="calculatingId === target.id" @click="emit('calculate', target)"><Calculator :size="15" /></button><button type="button" title="计算历史" :disabled="historyLoadingId === target.id" @click="emit('history', target)"><History :size="15" /></button></div></td>
        </tr>
      </tbody>
    </table>
    <a-empty v-else description="尚未为该 KPI 创建目标" />
  </div>
</template>

<style scoped>
.kpi-target-table{width:100%;border-collapse:collapse}.kpi-target-table th,.kpi-target-table td{padding:9px;border:1px solid #dfe6e9;text-align:left;vertical-align:middle}.kpi-target-table th{background:#f5f7f8;color:#52616b;font-size:12px}.kpi-target-table td:first-child{display:grid;gap:3px}.kpi-target-table small,.kpi-target-table code{display:block;color:#74818a;font-size:11px}.kpi-target-table tr.calculation-failed{background:#fff6f5}.decimal-value{font-variant-numeric:tabular-nums}.target-actions{display:flex;gap:6px}.target-actions button{display:grid;place-items:center;width:30px;height:30px;padding:0;border:1px solid #ced9de;border-radius:5px;background:#fff;color:#35647b;cursor:pointer}.target-actions button:disabled{opacity:.5;cursor:wait}
</style>
