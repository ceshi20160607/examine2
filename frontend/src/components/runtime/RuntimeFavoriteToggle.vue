<script setup lang="ts">
import { Star } from 'lucide-vue-next'

import type { RuntimeFavoriteItem } from '@/types/favorites'

const props = withDefaults(defineProps<{
  favorite?: RuntimeFavoriteItem | null
  loading?: boolean
  disabled?: boolean
  label: string
}>(), {
  favorite: null,
  loading: false,
  disabled: false,
})

defineEmits<{
  toggle: []
}>()
</script>

<template>
  <a-button
    class="runtime-favorite-toggle"
    :class="{ active: Boolean(props.favorite) }"
    :loading="loading"
    :disabled="disabled"
    :aria-label="favorite ? `取消收藏${label}` : `收藏${label}`"
    :title="favorite ? `取消收藏${label}` : `收藏${label}`"
    @click="$emit('toggle')"
  >
    <Star :size="16" :fill="favorite ? 'currentColor' : 'none'" />
    <span>{{ favorite ? '已收藏' : '收藏' }}</span>
  </a-button>
</template>

<style scoped>
.runtime-favorite-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.runtime-favorite-toggle.active {
  color: #a96500;
  border-color: #e0b45d;
  background: #fff9e8;
}
</style>
