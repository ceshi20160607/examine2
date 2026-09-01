<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ApiError, api } from '../api'
import { buildReportDefinition } from '../report'
import type { ReportMetadata, ReportPreview, ReportResult } from '../types'

const props = defineProps<{ token?: string; sourceId?: number; initialDefinition?: Record<string, unknown> }>()
const emit = defineEmits<{ definition: [value: Record<string, unknown>] }>()
const metadata = ref<ReportMetadata>()
const preview = ref<ReportPreview>()
const published = ref<ReportResult>()
const loading = ref(false)
const error = ref('')

function objects(value: unknown): Record<string, unknown>[] {
  return Array.isArray(value) ? value.filter(item => item && typeof item === 'object') as Record<string, unknown>[] : []
}
function object(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}
function qualified(value: Record<string, unknown>): string {
  return value.alias && value.fieldCode ? `${value.alias}.${value.fieldCode}` : ''
}

const initial = props.initialDefinition || {}
const initialModules = objects(initial.modules)
const initialRelation = objects(initial.relations)[0] || {}
const initialMetric = object(initial.metric)
const initialDimension = object(initial.dimension)
const initialFilter = objects(initial.filters)[0] || {}
const initialSort = object(initial.sort)
const initialTime = object(initial.timeField)
const form = reactive({
  primaryModuleCode: String(initialModules[0]?.moduleCode || ''),
  relatedModuleCode: String(initialModules[1]?.moduleCode || ''),
  relationType: String(initialRelation.relationType || 'REFERENCE') as 'REFERENCE' | 'SUBTABLE' | 'CASCADE',
  joinType: String(initialRelation.joinType || 'INNER') as 'INNER' | 'LEFT',
  primaryRelationField: String(initialRelation.leftField || 'id'),
  relatedRelationField: String(initialRelation.rightField || ''),
  outputFields: objects(initial.outputFields).map(qualified).filter(Boolean),
  metricOperation: String(initialMetric.operation || 'COUNT') as 'COUNT' | 'SUM' | 'AVG' | 'MIN' | 'MAX',
  metricField: qualified(initialMetric),
  dimensionType: String(initialDimension.type || 'STATUS') as 'FIELD' | 'TIME' | 'PERSON' | 'DEPARTMENT' | 'STATUS',
  dimensionField: qualified(initialDimension),
  filterField: qualified(initialFilter), filterOperator: String(initialFilter.operator || 'EQ'),
  filterValue: String(initialFilter.value || ''), sortField: qualified(initialSort),
  sortDirection: String(initialSort.direction || 'DESC') as 'ASC' | 'DESC',
  timeField: qualified(initialTime), timeFrom: String(initialTime.from || ''), timeTo: String(initialTime.to || ''),
  maxScanRows: Number(initial.maxScanRows || 500), limit: Number(initial.limit || 20),
})

const primary = computed(() => metadata.value?.modules.find(item => item.moduleCode === form.primaryModuleCode))
const related = computed(() => metadata.value?.modules.find(item => item.moduleCode === form.relatedModuleCode))
const primaryFields = computed(() => primary.value?.fields.filter(field => field.readable) || [])
const relatedFields = computed(() => related.value?.fields.filter(field => field.readable) || [])
const fieldOptions = computed(() => [
  ...primaryFields.value.map(field => ({ value: `primary.${field.code}`, label: `主模块 · ${field.name} (${field.code})`, field })),
  ...relatedFields.value.map(field => ({ value: `related.${field.code}`, label: `关联模块 · ${field.name} (${field.code})`, field })),
])
const indexedOptions = computed(() => fieldOptions.value.filter(option => option.field.indexed))
const numericOptions = computed(() => fieldOptions.value.filter(option =>
  ['NUMBER', 'DECIMAL', 'INTEGER', 'LONG', 'MONEY', 'PERCENT'].includes(option.field.fieldType)))

function emitDefinition() {
  emit('definition', buildReportDefinition(form))
}
watch(form, emitDefinition, { deep: true, immediate: true })

async function loadMetadata() {
  if (!props.token) return
  loading.value = true; error.value = ''
  try {
    metadata.value = await api<ReportMetadata>('/api/analytics/admin/report-metadata', {}, props.token)
  } catch (cause) {
    error.value = cause instanceof ApiError ? `${cause.code}：${cause.message}` : '无法读取已发布模块字段'
  } finally { loading.value = false }
}

async function previewDraft() {
  if (!props.token || !props.sourceId) return
  loading.value = true; error.value = ''; published.value = undefined
  try {
    preview.value = await api<ReportPreview>(`/api/analytics/admin/data-sources/${props.sourceId}/report-preview`, {}, props.token)
  } catch (cause) {
    error.value = cause instanceof ApiError ? `${cause.code}：${cause.message}` : '报表预览失败'
  } finally { loading.value = false }
}

async function executePublished() {
  if (!props.token || !props.sourceId) return
  loading.value = true; error.value = ''; preview.value = undefined
  try {
    published.value = await api<ReportResult>(`/api/analytics/report/data-sources/${props.sourceId}`, {}, props.token)
  } catch (cause) {
    error.value = cause instanceof ApiError ? `${cause.code}：${cause.message}` : '已发布版本执行失败'
  } finally { loading.value = false }
}

onMounted(loadMetadata)
</script>

<template>
  <section class="report-source-editor">
    <a-alert type="info" show-icon message="结构化查询计划"
      description="这里只选择已发布模块、字段、关系、指标与筛选；不提供任意 SQL 输入。系统、租户、数据范围和字段权限会在关联与聚合前执行。" />
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <div class="form-grid">
      <a-form-item label="主模块"><a-select v-model:value="form.primaryModuleCode" show-search :loading="loading"
        :options="metadata?.modules.map(item => ({ value: item.moduleCode, label: `${item.moduleName} · ${item.moduleCode} · v${item.versionNumber}` }))" /></a-form-item>
      <a-form-item label="可选关联模块"><a-select v-model:value="form.relatedModuleCode" allow-clear show-search
        :options="metadata?.modules.filter(item => item.moduleCode !== form.primaryModuleCode).map(item => ({ value: item.moduleCode, label: `${item.moduleName} · ${item.moduleCode} · v${item.versionNumber}` }))" /></a-form-item>
    </div>
    <div v-if="form.relatedModuleCode" class="report-relation-box">
      <strong>有界关系树</strong>
      <div class="form-grid form-grid--three"><a-form-item label="关系语义"><a-select v-model:value="form.relationType" :options="metadata?.relationTypes.map(value => ({ value, label: value }))" /></a-form-item><a-form-item label="连接方式"><a-select v-model:value="form.joinType" :options="['INNER','LEFT'].map(value => ({ value, label: value }))" /></a-form-item><span /></div>
      <div class="form-grid"><a-form-item label="主模块关联字段"><a-select v-model:value="form.primaryRelationField" show-search :options="primaryFields.filter(field => field.indexed).map(field => ({ value: field.code, label: `${field.name} · ${field.code}` }))" /></a-form-item><a-form-item label="关联模块关联字段"><a-select v-model:value="form.relatedRelationField" show-search :options="relatedFields.filter(field => field.indexed).map(field => ({ value: field.code, label: `${field.name} · ${field.code}` }))" /></a-form-item></div>
    </div>
    <a-form-item label="显式输出字段（未选择和无权字段不会返回）"><a-select v-model:value="form.outputFields" mode="multiple" show-search :options="fieldOptions" /></a-form-item>
    <div class="form-grid form-grid--three"><a-form-item label="指标"><a-select v-model:value="form.metricOperation" :options="metadata?.metricOperations.map(value => ({ value, label: value }))" /></a-form-item><a-form-item label="指标字段"><a-select v-model:value="form.metricField" allow-clear show-search :disabled="form.metricOperation === 'COUNT'" :options="numericOptions" /></a-form-item><a-form-item label="样例/下钻条数"><a-input-number v-model:value="form.limit" :min="1" :max="50" /></a-form-item></div>
    <div class="form-grid"><a-form-item label="分组维度"><a-select v-model:value="form.dimensionType" :options="metadata?.dimensionTypes.map(value => ({ value, label: value }))" /></a-form-item><a-form-item label="维度字段"><a-select v-model:value="form.dimensionField" allow-clear show-search :options="fieldOptions" /></a-form-item></div>
    <div class="form-grid form-grid--three"><a-form-item label="固定筛选字段"><a-select v-model:value="form.filterField" allow-clear show-search :options="indexedOptions" /></a-form-item><a-form-item label="运算符"><a-select v-model:value="form.filterOperator" :options="['EQ','NE','CONTAINS','GT','GTE','LT','LTE','EMPTY','NOT_EMPTY'].map(value => ({ value, label: value }))" /></a-form-item><a-form-item label="筛选值"><a-input v-model:value="form.filterValue" /></a-form-item></div>
    <div class="form-grid form-grid--three"><a-form-item label="默认排序字段"><a-select v-model:value="form.sortField" allow-clear show-search :options="indexedOptions" /></a-form-item><a-form-item label="排序方向"><a-select v-model:value="form.sortDirection" :options="['ASC','DESC'].map(value => ({ value, label: value }))" /></a-form-item><a-form-item label="最大授权扫描行数"><a-input-number v-model:value="form.maxScanRows" :min="1" :max="metadata?.maximumScanRows || 1000" /></a-form-item></div>
    <div class="form-grid form-grid--three"><a-form-item label="时间字段"><a-select v-model:value="form.timeField" allow-clear show-search :options="indexedOptions.filter(option => ['DATE','DATETIME'].includes(option.field.fieldType))" /></a-form-item><a-form-item label="开始"><a-input v-model:value="form.timeFrom" placeholder="2026-01-01" /></a-form-item><a-form-item label="结束"><a-input v-model:value="form.timeTo" placeholder="2026-12-31" /></a-form-item></div>
    <div v-if="sourceId" class="form-actions"><a-button :loading="loading" @click="previewDraft">预览草稿计划与样例</a-button><a-button :loading="loading" @click="executePublished">执行已发布版本</a-button></div>

    <section v-if="preview" class="report-plan-panel">
      <div class="panel-title"><strong>草稿预览 · r{{ preview.draftRevision }}</strong><a-tag :color="preview.valid ? 'green' : 'red'">{{ preview.valid ? '可发布' : '已阻止' }}</a-tag></div>
      <a-alert v-for="issue in preview.issues" :key="issue.code" type="error" show-icon :message="issue.code" :description="issue.message" />
      <template v-if="preview.sampleResult"><div class="report-result-summary"><article><small>授权聚合值</small><strong>{{ preview.sampleResult.value ?? '无结果' }}</strong></article><article><small>权限内下钻</small><strong>{{ preview.sampleResult.items.length }} 条</strong></article><article><small>预计聚合行</small><strong>{{ preview.sampleResult.queryPlan.estimatedRows }}</strong></article></div><p>{{ preview.sampleResult.metricDefinition }}</p></template>
      <ol v-if="preview.queryPlan"><li v-for="step in preview.queryPlan.steps" :key="step">{{ step }}</li></ol>
      <ul v-if="preview.queryPlan"><li v-for="filter in preview.queryPlan.permissionFilters" :key="filter">{{ filter }}</li></ul>
    </section>
    <section v-if="published" class="report-plan-panel"><div class="panel-title"><strong>已发布 v{{ published.dataSourceVersionNumber }}</strong><a-tag color="blue">不可变口径</a-tag></div><div class="report-result-summary"><article><small>聚合值</small><strong>{{ published.value ?? '无结果' }}</strong></article><article><small>下钻记录</small><strong>{{ published.items.length }}</strong></article><article><small>定义哈希</small><strong>{{ published.definitionHash?.slice(0, 8) }}</strong></article></div><p>{{ published.metricDefinition }}</p></section>
  </section>
</template>
