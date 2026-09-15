<script setup lang="ts">
import { computed, ref } from 'vue'
import { emptyJsonValue, inferJsonSchema, jsonTypeOptions, newJsonSchema, type JsonSchema, type JsonValueType } from '../structured-content'
const props = withDefaults(defineProps<{ schema: JsonSchema; modelValue?: unknown; depth?: number }>(), { depth: 0 })
const emit = defineEmits<{ 'update:modelValue': [value: unknown] }>()
const extraName = ref('')
const extraType = ref<JsonValueType>('STRING')
const rowType = ref<JsonValueType>('STRING')
const error = ref('')
const object = computed<Record<string, unknown>>(() => props.modelValue && typeof props.modelValue === 'object' && !Array.isArray(props.modelValue) ? props.modelValue as Record<string, unknown> : {})
const rows = computed<unknown[]>(() => Array.isArray(props.modelValue) ? props.modelValue : [])
const extras = computed(() => Object.entries(object.value).filter(([key]) => !props.schema.properties?.some(property => property.key.trim() === key)))
function setProperty(key: string, value: unknown) {
  const result = { ...object.value }
  if (value === undefined) delete result[key]
  else Object.defineProperty(result, key, { value, enumerable: true, configurable: true, writable: true })
  emit('update:modelValue', result)
}
function addProperty() {
  const key = extraName.value.trim()
  if (!key || key.length > 64 || ['__proto__', 'constructor', 'prototype'].includes(key) || Object.prototype.hasOwnProperty.call(object.value, key) || props.schema.properties?.some(property => property.key.trim() === key)) { error.value = '请填写不重复的属性名称'; return }
  setProperty(key, emptyJsonValue(newJsonSchema(extraType.value)) ?? (extraType.value === 'NUMBER' ? 0 : null))
  extraName.value = ''; error.value = ''
}
</script>
<template>
  <section v-if="depth <= 8" class="structured-value">
    <template v-if="schema.type === 'OBJECT'">
      <div v-for="property in schema.properties || []" :key="property.key" class="structured-entry">
        <label>{{ property.label || property.key }}<em v-if="property.required"> *</em><a-button v-else type="link" size="small" @click="setProperty(property.key.trim(), undefined)">清空</a-button></label>
        <StructuredValueEditor :schema="property.schema" :model-value="object[property.key.trim()]" :depth="depth + 1" @update:model-value="setProperty(property.key.trim(), $event)" />
      </div>
      <div v-for="[key, value] in extras" :key="key" class="structured-entry"><label>{{ key }} <a-button type="link" danger size="small" @click="setProperty(key, undefined)">移除</a-button></label><StructuredValueEditor :schema="inferJsonSchema(value)" :model-value="value" :depth="depth + 1" @update:model-value="setProperty(key, $event)" /></div>
      <div v-if="schema.allowExtraProperties && depth < 8" class="structured-add"><a-input v-model:value="extraName" aria-label="新增属性名称" placeholder="新增属性名称" /><a-select v-model:value="extraType" :options="jsonTypeOptions" style="width:110px" /><a-button @click="addProperty">添加属性</a-button></div>
      <small v-if="error" class="structured-error">{{ error }}</small>
      <small v-if="!schema.properties?.length && !extras.length">添加属性后填写对应内容。</small>
    </template>
    <template v-else-if="schema.type === 'ARRAY'">
      <div v-for="(value, index) in rows" :key="index" class="structured-entry"><label>第 {{ index + 1 }} 项 <a-button type="link" danger size="small" @click="emit('update:modelValue', rows.filter((_, position) => position !== index))">移除</a-button></label><StructuredValueEditor :schema="schema.items || inferJsonSchema(value)" :model-value="value" :depth="depth + 1" @update:model-value="emit('update:modelValue', rows.map((item, position) => position === index ? $event : item))" /></div>
      <a-select v-if="!schema.items" v-model:value="rowType" :options="jsonTypeOptions" style="width:150px" aria-label="新增项目类型" />
      <a-button v-if="depth < 8" :disabled="rows.length >= (schema.maxItems || 1000)" @click="emit('update:modelValue', [...rows, emptyJsonValue(schema.items || newJsonSchema(rowType)) ?? (rowType === 'NUMBER' ? 0 : null)])">添加一项</a-button>
    </template>
    <a-input v-else-if="schema.type === 'STRING'" :value="modelValue as string" :maxlength="schema.maxLength" @update:value="emit('update:modelValue', $event)" />
    <a-input-number v-else-if="schema.type === 'NUMBER'" :value="modelValue as number" :min="schema.minimum" :max="schema.maximum" style="width:100%" @update:value="emit('update:modelValue', $event ?? undefined)" />
    <a-select v-else-if="schema.type === 'BOOLEAN'" :value="modelValue as boolean" allow-clear placeholder="请选择是或否" :options="[{ value: true, label: '是' }, { value: false, label: '否' }]" @update:value="emit('update:modelValue', $event)" />
    <a-tag v-else>空值</a-tag>
  </section>
</template>
<style scoped>
.structured-value { display: grid; gap: 10px; }
.structured-entry { border-left: 2px solid #dbe4ef; padding-left: 12px; display: grid; gap: 5px; }
.structured-entry label { display: flex; align-items: center; gap: 5px; color: #475569; }
.structured-entry em, .structured-error { color: #c62828; }
.structured-add { display: flex; gap: 8px; flex-wrap: wrap; }
.structured-add .ant-input { flex: 1; min-width: 120px; }
</style>
