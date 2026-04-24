<template>
  <view style="color: var(--u-text-muted); padding: 12px 0;">
    {{ text }}
  </view>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    text?: string
  }>(),
  {
    text: '暂无数据'
  }
)
</script>

