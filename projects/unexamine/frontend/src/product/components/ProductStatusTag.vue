<script setup lang="ts">
import { computed } from 'vue'
import { productStatus } from '../presentation'

const props = defineProps<{ status?: string | null; label?: string }>()
const presentation = computed(() => productStatus(props.status))
const displayLabel = computed(() => props.label?.trim() || presentation.value.label)
</script>

<template>
  <a-tag :color="presentation.color" :data-status-tone="presentation.tone" :aria-label="`状态：${displayLabel}`">
    {{ displayLabel }}
  </a-tag>
</template>
