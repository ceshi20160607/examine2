<script setup lang="ts" generic="T extends { id: string | number }">
import { computed } from 'vue'
import type { PageColumn } from '../page-layout'
const props = defineProps<{ records: T[]; columns: PageColumn[]; fixedCodes?: string[]; density?: string; caption?: string; scroll?: { x?: number | string; y?: number | string }; actionWidth?: number }>()
defineSlots<{ value(props: { column: PageColumn; record: T }): unknown; actions(props: { record: T }): unknown }>()
const totalWidth = computed(() => props.columns.reduce((width, column) => width + column.width, 0))
</script>
<template>
  <a-table :data-source="records" row-key="id" :pagination="false" :size="density === 'compact' ? 'small' : 'middle'" :scroll="{ x: totalWidth + ($slots.actions ? (actionWidth || 140) : 0), ...scroll }">
    <template v-if="caption" #title><strong>{{ caption }}</strong></template>
    <a-table-column v-for="column in columns" :key="column.code" :data-index="column.code" :title="column.label" :custom-header-cell="() => ({ 'data-page-field': column.code })" :width="column.width" :fixed="fixedCodes?.includes(column.code) ? 'left' : undefined">
      <template #default="{ record }"><slot name="value" :column="column" :record="record as T" /></template>
    </a-table-column>
    <a-table-column v-if="$slots.actions" title="操作" fixed="right" :width="actionWidth || 140"><template #default="{ record }"><slot name="actions" :record="record as T" /></template></a-table-column>
  </a-table>
</template>
