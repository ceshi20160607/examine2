<template>
  <el-tag :type="tagType" effect="plain">{{ text }}</el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  value?: unknown;
}>();

const text = computed(() => String(props.value || '-'));
const tagType = computed(() => {
  const value = text.value.toUpperCase();
  if (['ENABLED', 'PUBLISHED', 'SUCCESS', 'APPROVED', 'UP', 'OK'].includes(value)) return 'success';
  if (['DISABLED', 'FAILED', 'REJECTED', 'DOWN', 'ERROR', 'DELETED'].includes(value)) return 'danger';
  if (['RUNNING', 'SUBMITTED', 'PROCESSING'].includes(value)) return 'warning';
  return 'info';
});
</script>
