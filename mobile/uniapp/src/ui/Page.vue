<template>
  <view class="u-page">
    <view class="u-container">
      <view v-if="title" class="u-section">
        <view class="u-card">
          <view class="u-title">{{ title }}</view>
          <view v-if="subtitle" class="u-subtitle">{{ subtitle }}</view>
          <view v-if="$slots.header" style="margin-top: 12px">
            <slot name="header" />
          </view>
        </view>
      </view>

      <view class="u-section">
        <slot />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title?: string
    subtitle?: string
  }>(),
  {
    title: '',
    subtitle: ''
  }
)
</script>

