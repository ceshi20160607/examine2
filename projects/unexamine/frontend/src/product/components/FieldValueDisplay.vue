<script setup lang="ts">
import { computed } from 'vue'
import type { ConfiguredField } from '../types'
import { parseFormLayout } from '../form-layout'
import { productDateTime } from '../presentation'
import { formatMoneyValue, formatPlaceFieldValue, formatTemporalFieldValue } from '../field-capabilities'
import { sanitizeRichMarkup, richPlainText, structuredSummary, type JsonSchema } from '../structured-content'
import StructuredValueDisplay from './StructuredValueDisplay.vue'
const props = defineProps<{ field: ConfiguredField; value: unknown; text?: string; summary?: boolean }>()
const config = computed(() => parseFormLayout(props.field.configJson))
const masked = computed(() => props.value === '***' || props.value === '[已散列]')
const empty = computed(() => props.value === undefined || props.value === null || props.value === '')
const optionLabels = computed(() => {
  const options = Array.isArray(config.value.options) ? config.value.options as Array<{ value?: unknown; code?: unknown; label?: string }> : []
  // 标签保存的是选项编码，读取时仍按字段配置显示标签名称。
  const label = (value: unknown) => options.find(option => (option.value ?? option.code) === value)?.label || String(value)
  return Array.isArray(props.value) ? props.value.map(label) : [label(props.value)]
})
const display = computed(() => {
  if (empty.value) return '—'
  if (masked.value) return props.value === '***' ? '••••（已脱敏）' : '不可回显（已散列）'
  // 显式可读明文由可信 presentation 单独渲染；缺描述的通用路径保持默认保护。
  if (props.field.fieldType === 'SECRET') return '••••（已脱敏）'
  if (props.text !== undefined) return props.text
  if (props.field.fieldType === 'MONEY') return formatMoneyValue(props.value, props.field.configJson)
  if (props.field.fieldType === 'PERCENT') return `${props.value}%`
  if (props.field.fieldType === 'RATING') return `${props.value} / 5`
  if (props.field.fieldType === 'PROGRESS') return `${props.value}%`
  const place = formatPlaceFieldValue(props.field.fieldType, props.value)
  if (place !== undefined) return place
  const temporal = formatTemporalFieldValue(props.field.fieldType, props.value)
  if (temporal !== undefined) return temporal
  if (props.field.fieldType === 'RICH_TEXT') return richPlainText(String(props.value)) || '—'
  if (props.field.fieldType === 'BOOLEAN') return props.value ? '是' : '否'
  if (['ATTACHMENT', 'IMAGE', 'FILE', 'FILE_GROUP'].includes(props.field.fieldType)) {
    return Array.isArray(props.value) ? `已上传 ${props.value.length} 个文件` : '已上传文件'
  }
  if (['SINGLE_SELECT', 'STATUS', 'MULTI_SELECT', 'TAG'].includes(props.field.fieldType)) {
    return optionLabels.value.join('、') || '—'
  }
  if (props.field.fieldType.includes('DATETIME') || ['SYSTEM_CREATED_AT', 'SYSTEM_UPDATED_AT'].includes(props.field.fieldType)) return productDateTime(String(props.value))
  return typeof props.value === 'object' ? structuredSummary(props.value) : String(props.value)
})
</script>
<template>
  <span v-if="empty || masked || summary" class="field-value-display">{{ display }}</span>
  <div v-else-if="field.fieldType === 'RICH_TEXT'" class="field-value-display field-value-display--rich" v-html="sanitizeRichMarkup(String(value))" />
  <StructuredValueDisplay v-else-if="field.fieldType === 'JSON'" :model-value="value" :schema="config.jsonSchema as JsonSchema | undefined" />
  <div v-else-if="field.fieldType === 'TAG' && text === undefined && optionLabels.length" class="field-value-display field-value-display--tags"><a-tag v-for="(label, index) in optionLabels" :key="`${index}-${label}`">{{ label }}</a-tag></div>
  <div v-else-if="field.fieldType === 'RATING' && text === undefined" class="field-value-display field-value-display--rating"><a-rate :value="Number(value)" :count="5" allow-half disabled /><span>{{ display }}</span></div>
  <div v-else-if="field.fieldType === 'PROGRESS' && text === undefined" class="field-value-display field-value-display--progress"><a-progress :percent="Number(value)" size="small" :show-info="false" /><span>{{ display }}</span></div>
  <span v-else class="field-value-display">{{ display }}</span>
</template>
<style scoped>
.field-value-display { overflow-wrap: anywhere; white-space: pre-wrap; }
.field-value-display--rich :deep(p:first-child) { margin-top: 0; }.field-value-display--rich :deep(p:last-child) { margin-bottom: 0; }
.field-value-display--rich :deep(img) { max-width: 100%; }
.field-value-display--tags { display: flex; flex-wrap: wrap; gap: 4px; }
.field-value-display--tags :deep(.ant-tag) { margin-inline-end: 0; }
.field-value-display--rating { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; }
.field-value-display--progress { display: flex; align-items: center; gap: 8px; }
.field-value-display--progress :deep(.ant-progress) { max-width: 180px; min-width: 80px; margin-bottom: 0; }
</style>
