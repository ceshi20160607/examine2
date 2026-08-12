<script setup lang="ts">
import { computed } from 'vue'

import type { RuntimeFieldValue } from '@/types/config'
import { displayRuntimeValue } from './runtimeRecordModel'
import RuntimeFileFieldDisplay from './RuntimeFileFieldDisplay.vue'

const props = defineProps<{ field?: RuntimeFieldValue; systemId?: string }>()

const text = computed(() => displayRuntimeValue(props.field))
const richHtml = computed(() => props.field?.type === 'RICH_TEXT' && typeof props.field.value === 'string'
  ? props.field.value : '')
const jsonText = computed(() => {
  if (props.field?.type !== 'JSON' || props.field.value === null || props.field.value === undefined) return ''
  try { return JSON.stringify(props.field.value, null, 2) }
  catch { return text.value }
})
const url = computed(() => {
  if (props.field?.type !== 'URL' || typeof props.field.value !== 'string') return ''
  try {
    const parsed = new URL(props.field.value)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : ''
  } catch { return '' }
})
</script>

<template>
  <RuntimeFileFieldDisplay v-if="field && ['ATTACHMENT', 'IMAGE', 'FILE_GROUP', 'SIGNATURE'].includes(field.type)" :field="field" :system-id="systemId" />
  <span v-else-if="field?.type === 'SECRET'" class="sensitive-state" :data-has-value="Boolean(field.displayValue)">{{ field.displayValue ? '已设置' : '未设置' }}</span>
  <a v-else-if="url" class="safe-link" :href="url" target="_blank" rel="noopener noreferrer">{{ text }}</a>
  <div v-else-if="richHtml" class="runtime-rich-readback" v-html="richHtml" />
  <pre v-else-if="jsonText" class="runtime-json-readback">{{ jsonText }}</pre>
  <span v-else>{{ text }}</span>
</template>

<style scoped>
.sensitive-state{display:inline-flex;align-items:center;min-height:24px;color:#63717b}.sensitive-state[data-has-value="true"]{color:#087f73;font-weight:650}.safe-link{color:#0969a8;overflow-wrap:anywhere}.runtime-rich-readback{line-height:1.6;overflow-wrap:anywhere}.runtime-rich-readback :deep(p){margin:0 0 8px}.runtime-rich-readback :deep(p:last-child){margin-bottom:0}.runtime-rich-readback :deep(a){color:#0969a8;text-decoration:underline}.runtime-rich-readback :deep(ul),.runtime-rich-readback :deep(ol){padding-left:22px}.runtime-json-readback{max-height:260px;margin:0;overflow:auto;padding:10px;border:1px solid #dfe5e8;background:#f7f9fa;color:#273740;font:12px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace;white-space:pre-wrap;overflow-wrap:anywhere}
</style>
