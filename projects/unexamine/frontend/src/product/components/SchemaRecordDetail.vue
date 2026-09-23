<script setup lang="ts">
import type { ConfiguredField, RuntimeFieldPresentation, RuntimeReadField } from '../types'
import { fieldTypes } from '../field-capabilities'
import { pageDensity } from '../page-layout'
import SchemaFormLayout from './SchemaFormLayout.vue'
import FieldValueDisplay from './FieldValueDisplay.vue'
defineProps<{ layout?: unknown; fields: ConfiguredField[]; values: Record<string, unknown>; mobile?: boolean; design?: boolean; selectedCode?: string; text?: (field: ConfiguredField, value: unknown) => string; presentation?: Record<string, RuntimeFieldPresentation>; readFields?: RuntimeReadField[]; readText?: (field: RuntimeReadField, value: unknown) => string }>()
const emit = defineEmits<{ select: [code: string]; section: [id: string] }>()
const fieldTypeName = (type: string) => fieldTypes.find(field => field.type === type)?.name || '字段'
</script>
<template>
  <div :class="['schema-record-detail', { 'schema-record-detail--compact': pageDensity(layout) === 'compact' }]">
    <!-- 来源只读描述不具备表单配置，不伪造 required、config 或字段 ID。 -->
    <div v-if="readFields !== undefined" class="record-read-grid">
      <div v-for="field in readFields" :key="field.code" :data-field-id="field.fieldId ?? undefined" class="record-read-field">
        <div class="record-field-label"><span>{{ field.name }}<small class="record-field-type">{{ fieldTypeName(field.fieldType) }}</small></span><slot name="read-tools" :field="field" /></div>
        <div class="record-field-value"><span class="record-field-text">{{ typeof field.displayValue === 'string' ? field.displayValue : readText?.(field, values[field.code]) ?? '—' }}</span></div>
      </div>
    </div>
    <SchemaFormLayout v-else :layout="layout" :fields="fields" :mobile="mobile" :design="design" :selected-code="selectedCode" drop-hint="拖入已有字段，或从左侧勾选显示字段" @select="emit('select', $event)" @section="emit('section', $event)">
      <template #field-tools="{ field }"><slot name="field-tools" :field="field" /></template>
      <template #field="{ field }"><div class="record-field-label"><span>{{ field.name }}<small v-if="presentation?.[field.code]" class="record-field-type">{{ fieldTypeName(presentation[field.code]!.fieldType) }}</small></span><slot name="tools" :field="field" /></div><div class="record-field-value"><!-- 服务端文本已完成权限与脱敏处理，不能再由类型渲染器恢复原值。 --><span v-if="typeof presentation?.[field.code]?.displayValue === 'string'" class="record-field-text">{{ presentation![field.code]!.displayValue }}</span><FieldValueDisplay v-else :field="field" :value="values[field.code]" :text="text?.(field, values[field.code])" /></div></template>
    </SchemaFormLayout>
  </div>
</template>
<style scoped>
.record-field-label { display: flex; justify-content: space-between; align-items: center; color: #64748b; font-size: 12px; margin-bottom: 5px; gap: 8px; }
.record-field-value { min-height: 32px; padding: 8px 10px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 5px; min-width: 0; }
.record-field-type { margin-left: 8px; color: #94a3b8; }.record-field-text { overflow-wrap: anywhere; white-space: pre-wrap; }
.record-read-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }.record-read-field { min-width: 0; }
.schema-record-detail--compact .record-read-grid { gap: 8px 12px; }
@media (max-width: 640px) { .record-read-grid { grid-template-columns: minmax(0, 1fr); } }
.schema-record-detail--compact .record-field-value { padding: 4px 8px; }.schema-record-detail--compact :deep(.schema-form-grid) { gap: 8px 12px; }
</style>
