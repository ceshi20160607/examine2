<script setup lang="ts">
import { computed } from 'vue'
import type { RuntimeField } from '../types'
import RichTextEditor from './RichTextEditor.vue'
import StructuredValueEditor from './StructuredValueEditor.vue'
import { emptyJsonValue, newJsonSchema, type JsonSchema } from '../structured-content'
import { dateTimeInputChange, dateTimeInputValue } from '../date-time-input'

const props = defineProps<{ field: Pick<RuntimeField, 'fieldType' | 'configJson' | 'required'>; modelValue?: unknown; preserveExisting?: boolean; options?: Array<{ value: string; label: string }> }>()
const emit = defineEmits<{ 'update:modelValue': [value: unknown] }>()

const configuration = computed(() => {
  try { return JSON.parse(props.field.configJson || '{}') as { placeholder?: string; maxLength?: number; richTextMode?: string; jsonSchema?: JsonSchema; options?: Array<{ code?: string; value?: string; label?: string; status?: string; disabled?: boolean }> } }
  catch { return {} }
})
const selectionOptions = computed(() => props.options || (configuration.value.options || []).filter(option => option.status !== 'DISABLED' && !option.disabled).flatMap(option => {
  const value = option.value || option.code
  return value ? [{ value, label: option.label || value }] : []
}))
const placeholder = computed(() => configuration.value.placeholder || ({
  PHONE: '请输入电话号码', EMAIL: 'name@example.com', URL: 'https://example.com', ID_CARD: '请输入身份证号',
} as Record<string, string>)[props.field.fieldType])
const maxLength = computed(() => Number.isInteger(configuration.value.maxLength) ? configuration.value.maxLength : undefined)
const inputType = computed(() => ({ EMAIL: 'email', URL: 'url' } as Record<string, string>)[props.field.fieldType] || 'text')
const inputMode = computed(() => ({ PHONE: 'tel', EMAIL: 'email', URL: 'url' } as Record<string, string>)[props.field.fieldType])
const dateTimeValue = computed(() => dateTimeInputValue(props.modelValue))
function changeDateTime(value: unknown) {
  const changed = dateTimeInputChange(props.modelValue, value)
  // 控件同秒事件不是实际修改，避免触发上层草稿改写旧表达式或丢失原精度。
  if (changed !== props.modelValue) emit('update:modelValue', changed)
}
</script>

<template>
  <div v-if="field.fieldType === 'SECRET'">
    <!-- 不绑定读取值；空输入表示保留，只有用户明确清空才提交 null。 -->
    <a-input-password :value="typeof modelValue === 'string' ? modelValue : ''" :visibility-toggle="false" autocomplete="new-password" :maxlength="maxLength" :placeholder="placeholder || (preserveExisting ? '输入新密码或密钥；留空保留原值' : '输入密码或密钥')" @update:value="emit('update:modelValue', $event === '' ? undefined : $event)" />
    <a-button v-if="preserveExisting && !field.required" type="link" size="small" @click="emit('update:modelValue', modelValue === null ? undefined : null)">{{ modelValue === null ? '取消清空' : '清空原值' }}</a-button>
    <small v-if="modelValue === null">保存后清空原值</small>
  </div>
  <RichTextEditor v-else-if="field.fieldType === 'RICH_TEXT'" :model-value="modelValue" :mode="configuration.richTextMode" :placeholder="placeholder" :max-length="maxLength" @update:model-value="emit('update:modelValue', $event)" />
  <div v-else-if="field.fieldType === 'JSON'">
    <a-button v-if="!field.required && modelValue === undefined" @click="emit('update:modelValue', emptyJsonValue(configuration.jsonSchema || newJsonSchema()))">填写内容</a-button>
    <template v-else><StructuredValueEditor :schema="configuration.jsonSchema || newJsonSchema()" :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)" /><a-button v-if="!field.required" type="link" size="small" @click="emit('update:modelValue', undefined)">清空内容</a-button></template>
  </div>
  <a-textarea v-else-if="field.fieldType === 'MULTILINE_TEXT'" :value="modelValue as string" :rows="4" :placeholder="placeholder" :maxlength="maxLength" :show-count="Boolean(maxLength)" @update:value="emit('update:modelValue', $event)" />
  <a-input-number v-else-if="['NUMBER', 'MONEY'].includes(field.fieldType)" :value="modelValue as number" style="width:100%" :placeholder="placeholder" @update:value="emit('update:modelValue', $event)" />
  <a-select v-else-if="['SINGLE_SELECT', 'STATUS'].includes(field.fieldType)" :value="modelValue as string" :options="selectionOptions" allow-clear @update:value="emit('update:modelValue', $event)" />
  <a-select v-else-if="field.fieldType === 'MULTI_SELECT'" :value="modelValue as string[]" :options="selectionOptions" mode="multiple" allow-clear @update:value="emit('update:modelValue', $event)" />
  <a-date-picker v-else-if="field.fieldType === 'DATE'" :value="modelValue as string" value-format="YYYY-MM-DD" style="width:100%" :placeholder="placeholder || '选择日期'" @update:value="emit('update:modelValue', $event)" />
  <div v-else-if="field.fieldType === 'DATETIME'">
    <a-date-picker :value="dateTimeValue" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" :placeholder="placeholder || '选择日期和时间'" @update:value="changeDateTime" />
    <small>北京时间（UTC+08:00）</small>
  </div>
  <a-switch v-else-if="field.fieldType === 'BOOLEAN'" :checked="Boolean(modelValue)" checked-children="是" un-checked-children="否" @update:checked="emit('update:modelValue', $event)" />
  <a-input v-else :value="modelValue as string" :type="inputType" :inputmode="inputMode" :maxlength="maxLength" :placeholder="placeholder" :autocomplete="field.fieldType === 'ID_CARD' ? 'off' : undefined" @update:value="emit('update:modelValue', $event)" />
</template>
