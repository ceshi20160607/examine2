<script setup lang="ts">
import type { ConfiguredField } from '../types'
import { pageDensity } from '../page-layout'
import SchemaFormLayout from './SchemaFormLayout.vue'
import FieldValueDisplay from './FieldValueDisplay.vue'
defineProps<{ layout?: unknown; fields: ConfiguredField[]; values: Record<string, unknown>; mobile?: boolean; design?: boolean; selectedCode?: string; text?: (field: ConfiguredField, value: unknown) => string }>()
const emit = defineEmits<{ select: [code: string]; section: [id: string] }>()
</script>
<template>
  <div :class="['schema-record-detail', { 'schema-record-detail--compact': pageDensity(layout) === 'compact' }]">
    <SchemaFormLayout :layout="layout" :fields="fields" :mobile="mobile" :design="design" :selected-code="selectedCode" drop-hint="拖入已有字段，或从左侧勾选显示字段" @select="emit('select', $event)" @section="emit('section', $event)">
      <template #field-tools="{ field }"><slot name="field-tools" :field="field" /></template>
      <template #field="{ field }"><div class="record-field-label">{{ field.name }}<slot name="tools" :field="field" /></div><div class="record-field-value"><FieldValueDisplay :field="field" :value="values[field.code]" :text="text?.(field, values[field.code])" /></div></template>
    </SchemaFormLayout>
  </div>
</template>
<style scoped>
.record-field-label { display: flex; justify-content: space-between; align-items: center; color: #64748b; font-size: 12px; margin-bottom: 5px; gap: 8px; }
.record-field-value { min-height: 32px; padding: 8px 10px; background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 5px; min-width: 0; }
.schema-record-detail--compact .record-field-value { padding: 4px 8px; }.schema-record-detail--compact :deep(.schema-form-grid) { gap: 8px 12px; }
</style>
