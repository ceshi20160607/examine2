<script setup lang="ts">
import { computed } from 'vue'
import type { RuntimeField } from '../types'

const props = defineProps<{ field: RuntimeField; modelValue?: unknown }>()
const emit = defineEmits<{ 'update:modelValue': [value: unknown] }>()

const configuration = computed(() => {
  try { return JSON.parse(props.field.configJson || '{}') as { placeholder?: string; maxLength?: number } }
  catch { return {} }
})
const placeholder = computed(() => configuration.value.placeholder || ({
  PHONE: '请输入电话号码', EMAIL: 'name@example.com', URL: 'https://example.com', ID_CARD: '请输入身份证号',
} as Record<string, string>)[props.field.fieldType])
const maxLength = computed(() => Number.isInteger(configuration.value.maxLength) ? configuration.value.maxLength : undefined)
const inputType = computed(() => ({ EMAIL: 'email', URL: 'url' } as Record<string, string>)[props.field.fieldType] || 'text')
const inputMode = computed(() => ({ PHONE: 'tel', EMAIL: 'email', URL: 'url' } as Record<string, string>)[props.field.fieldType])
</script>

<template>
  <a-textarea v-if="field.fieldType === 'MULTILINE_TEXT'" :value="modelValue as string" :rows="4" :placeholder="placeholder" :maxlength="maxLength" :show-count="Boolean(maxLength)" @update:value="emit('update:modelValue', $event)" />
  <a-input-number v-else-if="field.fieldType === 'NUMBER'" :value="modelValue as number" style="width:100%" :placeholder="placeholder" @update:value="emit('update:modelValue', $event)" />
  <a-date-picker v-else-if="field.fieldType === 'DATE'" :value="modelValue as string" value-format="YYYY-MM-DD" style="width:100%" :placeholder="placeholder || '选择日期'" @update:value="emit('update:modelValue', $event)" />
  <a-date-picker v-else-if="field.fieldType === 'DATETIME'" :value="modelValue as string" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" :placeholder="placeholder || '选择日期和时间'" @update:value="emit('update:modelValue', $event)" />
  <a-switch v-else-if="field.fieldType === 'BOOLEAN'" :checked="Boolean(modelValue)" checked-children="是" un-checked-children="否" @update:checked="emit('update:modelValue', $event)" />
  <a-input v-else :value="modelValue as string" :type="inputType" :inputmode="inputMode" :maxlength="maxLength" :placeholder="placeholder" :autocomplete="field.fieldType === 'ID_CARD' ? 'off' : undefined" @update:value="emit('update:modelValue', $event)" />
</template>
