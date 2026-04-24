<template>
  <view v-if="text" style="margin-top: 12px; color: #d00; white-space: pre-wrap;">
    {{ text }}
  </view>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    text?: string | null
  }>(),
  {
    text: null
  }
)
</script>

