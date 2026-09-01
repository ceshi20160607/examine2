<script setup lang="ts">
import { computed } from 'vue'
import { productStatus } from '../presentation'

const props = defineProps<{ status?: string | null; label?: string }>()
const presentation = computed(() => productStatus(props.status))
</script>

<template>
  <a-tag :color="presentation.color" :data-status-tone="presentation.tone">
    {{ props.label || presentation.label }}
  </a-tag>
</template>
