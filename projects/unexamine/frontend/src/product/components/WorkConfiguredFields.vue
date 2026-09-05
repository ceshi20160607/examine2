<script setup lang="ts">
import type { WorkConfigurationField } from '../types'

const props = defineProps<{
  fields: WorkConfigurationField[]
  modelValue: Record<string, unknown>
}>()
const emit = defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()

function value(field: WorkConfigurationField) {
  return props.modelValue[field.fieldCode]
}

function update(field: WorkConfigurationField, next: unknown) {
  emit('update:modelValue', { ...props.modelValue, [field.fieldCode]: next })
}

function dictionaryOptions(field: WorkConfigurationField) {
  const options = Array.isArray(field.settings.options) ? field.settings.options : []
  return options.map((option) => {
    if (option && typeof option === 'object') {
      const entry = option as Record<string, unknown>
      return { value: entry.value, label: String(entry.label ?? entry.value ?? '') }
    }
    return { value: option, label: String(option) }
  })
}
</script>

<template>
  <a-form-item v-for="field in fields" :key="field.fieldCode" :label="field.fieldName" :required="field.required">
    <a-textarea v-if="field.fieldType === 'TEXTAREA'" :value="value(field)" :rows="3" @update:value="update(field, $event)" />
    <a-input-number v-else-if="field.fieldType === 'NUMBER'" :value="value(field) as number" style="width:100%" @update:value="update(field, $event)" />
    <a-date-picker v-else-if="field.fieldType === 'DATE'" :value="value(field) as string" value-format="YYYY-MM-DD" style="width:100%" @update:value="update(field, $event)" />
    <a-date-picker v-else-if="field.fieldType === 'DATETIME'" :value="value(field) as string" value-format="YYYY-MM-DDTHH:mm:ss" show-time style="width:100%" @update:value="update(field, $event)" />
    <a-switch v-else-if="field.fieldType === 'BOOLEAN'" :checked="Boolean(value(field))" @update:checked="update(field, $event)" />
    <a-select v-else-if="field.fieldType === 'DICTIONARY'" :value="value(field)" allow-clear :options="dictionaryOptions(field)" @update:value="update(field, $event)" />
    <a-input v-else :value="value(field) as string" @update:value="update(field, $event)" />
    <small v-if="field.source === 'PLATFORM_DEFAULT'">平台默认字段</small>
  </a-form-item>
</template>
