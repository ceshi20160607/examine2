<template>
  <div class="rating-field">
    <button
      v-for="n in max"
      :key="n"
      type="button"
      class="rating-field__star"
      :class="{ 'rating-field__star--on': n <= current }"
      @click="pick(n)"
    >
      ★
    </button>
    <button v-if="clearable && current > 0" type="button" class="link secondary" @click="pick(0)">
      清除
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  modelValue: { type: [Number, String], default: null },
  max: { type: Number, default: 5 },
  clearable: { type: Boolean, default: true }
})
const emit = defineEmits(['update:modelValue'])

const current = computed(() => {
  const n = Number(props.modelValue)
  return Number.isFinite(n) && n > 0 ? n : 0
})

function pick(n) {
  emit('update:modelValue', n > 0 ? n : '')
}
</script>

<style scoped>
.rating-field {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  flex-wrap: wrap;
}
.rating-field__star {
  background: none;
  border: none;
  font-size: 1.75rem;
  line-height: 1;
  color: #ccc;
  cursor: pointer;
  padding: 0 0.1rem;
}
.rating-field__star--on {
  color: #f5a623;
}
.link.secondary {
  font-size: 0.85rem;
  margin-left: 0.35rem;
}
</style>
