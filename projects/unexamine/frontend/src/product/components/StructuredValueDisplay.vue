<script setup lang="ts">
import { computed } from 'vue'
import { inferJsonSchema, type JsonSchema } from '../structured-content'
const props = withDefaults(defineProps<{ modelValue?: unknown; schema?: JsonSchema; depth?: number }>(), { depth: 0 })
const entries = computed(() => props.modelValue && typeof props.modelValue === 'object' && !Array.isArray(props.modelValue) ? Object.entries(props.modelValue) : [])
function property(key: string) { return props.schema?.properties?.find(item => item.key.trim() === key) }
</script>
<template>
  <span v-if="depth > 8 || modelValue === undefined || modelValue === null">—</span>
  <ol v-else-if="Array.isArray(modelValue)" class="structured-display"><li v-for="(value, index) in modelValue" :key="index"><StructuredValueDisplay :model-value="value" :schema="schema?.items || inferJsonSchema(value)" :depth="depth + 1" /></li></ol>
  <dl v-else-if="typeof modelValue === 'object'" class="structured-display"><div v-for="[key, value] in entries" :key="key"><dt>{{ property(key)?.label || key }}</dt><dd><StructuredValueDisplay :model-value="value" :schema="property(key)?.schema" :depth="depth + 1" /></dd></div><span v-if="!entries.length">未填写属性</span></dl>
  <span v-else>{{ typeof modelValue === 'boolean' ? (modelValue ? '是' : '否') : modelValue }}</span>
</template>
<style scoped>
.structured-display { margin: 0; padding-left: 16px; display: grid; gap: 6px; }
dl.structured-display { padding-left: 0; }
dt { color: #64748b; font-size: 12px; }
dd { margin: 2px 0 0; padding-left: 10px; border-left: 2px solid #e2e8f0; }
</style>
