<script setup lang="ts">
import { computed } from 'vue'
import type { ConfiguredField } from '../types'
import { parseFormLayout } from '../form-layout'
import { productDateTime } from '../presentation'
import { sanitizeRichMarkup, richPlainText, structuredSummary, type JsonSchema } from '../structured-content'
import StructuredValueDisplay from './StructuredValueDisplay.vue'
const props = defineProps<{ field: ConfiguredField; value: unknown; text?: string; summary?: boolean }>()
const config = computed(() => parseFormLayout(props.field.configJson))
const masked = computed(() => props.value === '***' || props.value === '[已散列]')
const empty = computed(() => props.value === undefined || props.value === null || props.value === '')
const display = computed(() => {
  if (empty.value) return '—'
  if (masked.value) return props.value === '***' ? '••••（已脱敏）' : '不可回显（已散列）'
  if (props.text !== undefined) return props.text
  if (props.field.fieldType === 'RICH_TEXT') return richPlainText(String(props.value)) || '—'
  if (props.field.fieldType === 'BOOLEAN') return props.value ? '是' : '否'
  if (['SINGLE_SELECT', 'STATUS', 'MULTI_SELECT', 'TAG'].includes(props.field.fieldType)) {
    const options = Array.isArray(config.value.options) ? config.value.options as Array<{ value?: unknown; code?: unknown; label?: string }> : []
    const label = (value: unknown) => options.find(option => (option.value ?? option.code) === value)?.label || String(value)
    return Array.isArray(props.value) ? props.value.map(label).join('、') : label(props.value)
  }
  if (props.field.fieldType.includes('DATETIME') || ['SYSTEM_CREATED_AT', 'SYSTEM_UPDATED_AT'].includes(props.field.fieldType)) return productDateTime(String(props.value))
  return typeof props.value === 'object' ? structuredSummary(props.value) : String(props.value)
})
</script>
<template>
  <span v-if="empty || masked || summary" class="field-value-display">{{ display }}</span>
  <div v-else-if="field.fieldType === 'RICH_TEXT'" class="field-value-display field-value-display--rich" v-html="sanitizeRichMarkup(String(value))" />
  <StructuredValueDisplay v-else-if="field.fieldType === 'JSON'" :model-value="value" :schema="config.jsonSchema as JsonSchema | undefined" />
  <span v-else class="field-value-display">{{ display }}</span>
</template>
<style scoped>
.field-value-display { overflow-wrap: anywhere; white-space: pre-wrap; }
.field-value-display--rich :deep(p:first-child) { margin-top: 0; }.field-value-display--rich :deep(p:last-child) { margin-bottom: 0; }
.field-value-display--rich :deep(img) { max-width: 100%; }
</style>
