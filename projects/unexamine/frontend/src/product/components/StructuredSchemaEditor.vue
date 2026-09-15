<script setup lang="ts">
import { jsonTypeOptions, newJsonSchema, type JsonSchema, type JsonValueType } from '../structured-content'
const props = withDefaults(defineProps<{ modelValue: JsonSchema; depth?: number }>(), { depth: 0 })
const emit = defineEmits<{ 'update:modelValue': [value: JsonSchema] }>()
function update(changes: Partial<JsonSchema>) { emit('update:modelValue', { ...props.modelValue, ...changes }) }
function property(index: number, changes: Record<string, unknown>) {
  update({ properties: (props.modelValue.properties || []).map((item, position) => position === index ? { ...item, ...changes } : item) })
}
</script>
<template>
  <section class="schema-node">
    <a-select aria-label="数据类型" :value="modelValue.type" :options="jsonTypeOptions" style="width:150px" @update:value="emit('update:modelValue', newJsonSchema($event as JsonValueType))" />
    <template v-if="modelValue.type === 'OBJECT'">
      <a-checkbox :checked="modelValue.allowExtraProperties" @update:checked="update({ allowExtraProperties: $event })">允许填写额外属性</a-checkbox>
      <div v-for="(item, index) in modelValue.properties || []" :key="index" class="schema-property">
        <div class="schema-property__header"><a-input aria-label="属性名称" :value="item.key" placeholder="属性名称，如联系方式" @update:value="property(index, { key: $event })" />
          <a-input aria-label="属性显示名称" :value="item.label" placeholder="显示名称（可选）" @update:value="property(index, { label: $event })" />
          <a-checkbox :checked="item.required" @update:checked="property(index, { required: $event })">必填</a-checkbox>
          <a-button danger size="small" @click="update({ properties: modelValue.properties?.filter((_, position) => position !== index) })">移除</a-button></div>
        <StructuredSchemaEditor :model-value="item.schema" :depth="depth + 1" @update:model-value="property(index, { schema: $event })" />
      </div>
      <a-button v-if="depth < 8" size="small" @click="update({ properties: [...(modelValue.properties || []), { key: '', required: false, schema: { type: 'STRING' } }] })">添加属性</a-button>
    </template>
    <template v-else-if="modelValue.type === 'ARRAY'">
      <label>最大行数 <a-input-number :value="modelValue.maxItems" :min="1" :max="1000" placeholder="最多1000行" @update:value="update({ maxItems: $event ?? undefined })" /></label>
      <div v-if="depth < 8" class="schema-property"><strong>每行的数据结构</strong><StructuredSchemaEditor :model-value="modelValue.items || { type: 'STRING' }" :depth="depth + 1" @update:model-value="update({ items: $event })" /></div>
    </template>
    <div v-else-if="modelValue.type === 'STRING'" class="schema-bounds"><label>最短长度 <a-input-number :value="modelValue.minLength" :min="0" :max="100000" @update:value="update({ minLength: $event ?? undefined })" /></label><label>最长长度 <a-input-number :value="modelValue.maxLength" :min="1" :max="100000" @update:value="update({ maxLength: $event ?? undefined })" /></label></div>
    <div v-else-if="modelValue.type === 'NUMBER'" class="schema-bounds"><label>最小值 <a-input-number :value="modelValue.minimum" @update:value="update({ minimum: $event ?? undefined })" /></label><label>最大值 <a-input-number :value="modelValue.maximum" @update:value="update({ maximum: $event ?? undefined })" /></label></div>
  </section>
</template>
<style scoped>
.schema-node { display: grid; gap: 10px; }
.schema-property { border-left: 2px solid #dbe4ef; padding: 10px 0 10px 12px; display: grid; gap: 10px; }
.schema-property__header { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.schema-property__header .ant-input { flex: 1; min-width: 100px; }
.schema-bounds { display: flex; gap: 12px; flex-wrap: wrap; }
.schema-bounds label { display: grid; gap: 4px; }
</style>
