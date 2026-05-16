<template>
  <view class="rating-stars">
    <text
      v-for="n in max"
      :key="n"
      class="rating-stars__star"
      :class="{ 'rating-stars__star--on': n <= current }"
      @click="pick(n)"
    >★</text>
    <text v-if="clearable && current > 0" class="rating-stars__clear" @click="pick(0)">清除</text>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    modelValue?: number | string | null
    max?: number
    clearable?: boolean
  }>(),
  { max: 5, clearable: true }
)

const emit = defineEmits<{ (e: 'update:modelValue', v: number | ''): void }>()

const current = computed(() => {
  const n = Number(props.modelValue)
  return Number.isFinite(n) && n > 0 ? n : 0
})

function pick(n: number) {
  emit('update:modelValue', n > 0 ? n : '')
}
</script>

<style scoped>
.rating-stars {
  display: flex;
  align-items: center;
  gap: 4px;
}
.rating-stars__star {
  font-size: 28px;
  color: #ccc;
  line-height: 1;
}
.rating-stars__star--on {
  color: #f5a623;
}
.rating-stars__clear {
  margin-left: 8px;
  font-size: 13px;
  color: #888;
}
</style>
