<script setup lang="ts">
import { computed, ref } from 'vue'
import { ChevronDown, ChevronUp } from 'lucide-vue-next'

import type { KpiCalculation } from '@/types/kpi'
import { kpiStatusColor, kpiStatusLabel } from '@/views/system/admin/kpiEditorModel'

const props = withDefaults(defineProps<{
  calculation: KpiCalculation | null
  expanded?: boolean
}>(), { expanded: false })
const open = ref(props.expanded)
const explanation = computed(() => props.calculation?.explanation)

function formatTime(value: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}
</script>

<template>
  <div class="kpi-calculation-explanation">
    <a-empty v-if="!calculation" class="kpi-no-calculation" description="尚未计算" />
    <template v-else>
      <div class="kpi-calculation-summary">
        <a-tag :color="kpiStatusColor(calculation.status)">{{ kpiStatusLabel(calculation.status) }}</a-tag>
        <span>目标 <strong>{{ calculation.targetValue }}</strong></span>
        <span>实际 <strong>{{ calculation.actualValue ?? '—' }}</strong></span>
        <span v-if="calculation.attainment !== null">达成值 <strong>{{ calculation.attainment }}</strong></span>
        <time>{{ formatTime(calculation.calculatedAt) }}</time>
        <button class="kpi-explanation-toggle" type="button" @click="open = !open">
          {{ open ? '收起依据' : '查看依据' }}<ChevronUp v-if="open" :size="14" /><ChevronDown v-else :size="14" />
        </button>
      </div>
      <a-alert
        v-if="calculation.status === 'CALCULATION_FAILED'"
        class="kpi-calculation-error"
        type="error"
        show-icon
        :message="`本次计算失败：${calculation.errorCode || 'CALCULATION_FAILED'}`"
        description="其他 KPI 结果不受影响；请由管理员核对当前权限、主体状态与已发布字段。"
      />
      <div v-if="open" class="kpi-explanation-detail">
        <dl>
          <div><dt>统计查询</dt><dd><code>{{ explanation?.statisticsQueryId || '—' }}</code></dd></div>
          <div><dt>命中记录</dt><dd>{{ explanation?.matchedCount || '0' }}</dd></div>
          <div><dt>数据源版本</dt><dd>{{ explanation?.dataSourceCode || '—' }} v{{ explanation?.dataSourceVersionNumber || '—' }}</dd></div>
          <div><dt>聚合</dt><dd>{{ explanation?.aggregation || '—' }}</dd></div>
          <div><dt>时间字段</dt><dd>{{ explanation?.timeField?.name || explanation?.timeField?.code || '—' }}</dd></div>
          <div><dt>度量字段</dt><dd>{{ explanation?.measureField?.name || explanation?.measureField?.code || 'COUNT' }}</dd></div>
          <div><dt>授权代次</dt><dd><code>{{ explanation?.authorizationEpoch || '—' }}</code></dd></div>
          <div v-if="explanation?.subjectMemberIds.length"><dt>角色成员快照</dt><dd>{{ explanation.subjectMemberIds.join('、') }}</dd></div>
        </dl>
        <table v-if="explanation?.trend.length" class="kpi-trend-table">
          <thead><tr><th>月份区间</th><th>实际值</th><th>记录数</th></tr></thead>
          <tbody><tr v-for="bucket in explanation.trend" :key="`${bucket.startInclusive}:${bucket.endExclusive}`"><td>{{ bucket.startInclusive }} ～ {{ bucket.endExclusive }}</td><td>{{ bucket.value }}</td><td>{{ bucket.matchedCount }}</td></tr></tbody>
        </table>
        <a-empty v-else-if="calculation.status !== 'CALCULATION_FAILED'" description="本次计算未返回趋势明细" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.kpi-calculation-explanation{display:grid;gap:10px}.kpi-calculation-summary{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.kpi-calculation-summary time{margin-left:auto;color:#74818a;font-size:12px}.kpi-explanation-toggle{display:flex;align-items:center;gap:4px;padding:5px 8px;border:1px solid #cdd8dd;border-radius:5px;background:#fff;color:#35647b;cursor:pointer}.kpi-explanation-detail{display:grid;gap:12px;padding:12px;border:1px solid #dce5e9;border-radius:7px;background:#f8fafb}.kpi-explanation-detail dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:9px;margin:0}.kpi-explanation-detail dl>div{display:grid;grid-template-columns:90px minmax(0,1fr);gap:8px}.kpi-explanation-detail dt{color:#74818a}.kpi-explanation-detail dd{margin:0;overflow-wrap:anywhere}.kpi-trend-table{width:100%;border-collapse:collapse;background:#fff}.kpi-trend-table th,.kpi-trend-table td{padding:8px;border:1px solid #dde5e8;text-align:left}.kpi-trend-table th{background:#eef3f5;color:#53636c;font-size:12px}.kpi-no-calculation{margin:8px 0}.kpi-calculation-error{margin-top:2px}
</style>

