<script setup lang="ts">
import { computed } from 'vue'
import type { RuntimeField } from '../types'

const props = defineProps<{ field: RuntimeField; modelValue?: unknown }>()
const emit = defineEmits<{ 'update:modelValue': [value: unknown] }>()

const placeholder = computed(() => {
  try { return (JSON.parse(props.field.configJson || '{}') as { placeholder?: string }).placeholder }
  catch { return undefined }
})
</script>

<template>
  <a-textarea v-if="field.fieldType === 'MULTILINE_TEXT'" :value="modelValue as string" :rows="4" :placeholder="placeholder" @update:value="emit('update:modelValue', $event)" />
  <a-input-number v-else-if="field.fieldType === 'NUMBER'" :value="modelValue as number" style="width:100%" :placeholder="placeholder" @update:value="emit('update:modelValue', $event)" />
  <a-date-picker v-else-if="field.fieldType === 'DATE'" :value="modelValue as string" value-format="YYYY-MM-DD" style="width:100%" :placeholder="placeholder || '选择日期'" @update:value="emit('update:modelValue', $event)" />
  <a-date-picker v-else-if="field.fieldType === 'DATETIME'" :value="modelValue as string" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" :placeholder="placeholder || '选择日期和时间'" @update:value="emit('update:modelValue', $event)" />
  <a-switch v-else-if="field.fieldType === 'BOOLEAN'" :checked="Boolean(modelValue)" checked-children="是" un-checked-children="否" @update:checked="emit('update:modelValue', $event)" />
  <a-input v-else :value="modelValue as string" :placeholder="placeholder" @update:value="emit('update:modelValue', $event)" />
</template>
