<script setup lang="ts">
import { computed } from 'vue'

import type { DataSourceSummary } from '@/types/dataSource'
import type { DataSourceStatisticsCapabilities } from '@/types/statistics'
import {
  capabilityFields,
  KPI_AGGREGATION_OPTIONS,
  KPI_DIRECTION_OPTIONS,
  KPI_PERIOD_OPTIONS,
  KPI_SUBJECT_OPTIONS,
  kpiAggregationChanged,
  type KpiEditorDraft,
} from '@/views/system/admin/kpiEditorModel'

const props = defineProps<{
  sources: DataSourceSummary[]
  capabilities: DataSourceStatisticsCapabilities | null
  capabilitiesLoading?: boolean
  disabled?: boolean
}>()
const emit = defineEmits<{ sourceChange: [dataSourceId: string] }>()
const editor = defineModel<KpiEditorDraft>({ required: true })

const fields = computed(() => capabilityFields(props.capabilities))
const numericFields = computed(() => fields.value.filter(field => field.numeric))
const temporalFields = computed(() => fields.value.filter(field => field.temporal))

function sourceChanged(event: Event) {
  editor.value.dataSourceId = (event.target as HTMLSelectElement).value
  editor.value.measureFieldCode = null
  editor.value.timeFieldCode = ''
  emit('sourceChange', editor.value.dataSourceId)
}

function aggregationChanged() {
  kpiAggregationChanged(editor.value)
}
</script>

<template>
  <section class="kpi-definition-editor">
    <div class="kpi-form-grid">
      <label>名称<input v-model="editor.name" class="kpi-name-input" maxlength="200" :disabled="disabled"></label>
      <label>考核对象
        <select v-model="editor.subjectType" class="kpi-subject-select" :disabled="disabled">
          <option v-for="option in KPI_SUBJECT_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
      </label>
      <label class="span-two">描述<textarea v-model="editor.description" :disabled="disabled" rows="2" maxlength="2000" /></label>
      <label>数据源
        <select :value="editor.dataSourceId" class="kpi-source-select" :disabled="disabled || capabilitiesLoading" @change="sourceChanged">
          <option value="">请选择已发布数据源</option>
          <option v-for="source in sources.filter(item => item.activeVersionId)" :key="source.id" :value="source.id">
            {{ source.name }}（{{ source.code }} v{{ source.activeVersionNumber }}）
          </option>
        </select>
      </label>
      <label>周期
        <select v-model="editor.periodType" class="kpi-period-select" :disabled="disabled">
          <option v-for="option in KPI_PERIOD_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
      </label>
      <label>聚合方式
        <select v-model="editor.aggregation" class="kpi-aggregation-select" :disabled="disabled" @change="aggregationChanged">
          <option v-for="option in KPI_AGGREGATION_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
      </label>
      <label>度量字段
        <select v-model="editor.measureFieldCode" class="kpi-measure-select" :disabled="disabled || editor.aggregation === 'COUNT' || capabilitiesLoading">
          <option :value="null">{{ editor.aggregation === 'COUNT' ? 'COUNT 不使用度量字段' : '请选择数值字段' }}</option>
          <option v-for="field in numericFields" :key="field.code" :value="field.code">{{ field.name }}（{{ field.code }} · {{ field.type }}）</option>
        </select>
      </label>
      <label>时间字段
        <select v-model="editor.timeFieldCode" class="kpi-time-select" :disabled="disabled || capabilitiesLoading">
          <option value="">请选择时间字段</option>
          <option v-for="field in temporalFields" :key="field.code" :value="field.code">{{ field.name }}（{{ field.code }} · {{ field.type }}）</option>
        </select>
      </label>
      <label>达成方向
        <select v-model="editor.direction" class="kpi-direction-select" :disabled="disabled">
          <option v-for="option in KPI_DIRECTION_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
        </select>
      </label>
      <label>预警阈值
        <input v-model="editor.warningThreshold" class="kpi-warning-input" inputmode="decimal" :disabled="disabled" placeholder="0 < 比率 ≤ 1，例如 0.8">
      </label>
    </div>
    <a-alert
      v-if="editor.dataSourceId && !capabilitiesLoading && !capabilities"
      class="kpi-capability-unavailable"
      type="warning"
      show-icon
      message="当前数据源的统计能力不可用，无法检查度量与时间字段。"
    />
    <p v-else-if="capabilities" class="kpi-capability-pin">
      发布时将固定数据源 {{ capabilities.dataSourceCode }} v{{ capabilities.dataSourceVersionNumber }} 及字段元数据；后续数据源草稿不会改变该版本。
    </p>
  </section>
</template>

<style scoped>
.kpi-form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:13px 16px}.kpi-form-grid label{display:flex;flex-direction:column;gap:6px;color:#52616b;font-size:12px}.kpi-form-grid .span-two{grid-column:span 2}.kpi-form-grid input,.kpi-form-grid select,.kpi-form-grid textarea{width:100%;box-sizing:border-box;min-height:36px;padding:7px 9px;border:1px solid #cbd5da;border-radius:6px;background:#fff;color:#26353f;font:inherit}.kpi-form-grid textarea{resize:vertical}.kpi-form-grid :disabled{background:#f1f3f4;color:#748089}.kpi-capability-unavailable,.kpi-capability-pin{margin-top:12px}.kpi-capability-pin{padding:9px 11px;border-radius:6px;background:#eef7fa;color:#35647b;font-size:12px}
</style>
