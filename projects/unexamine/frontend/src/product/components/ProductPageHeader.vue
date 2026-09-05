<script setup lang="ts">
import { useId } from 'vue'

defineProps<{
  kicker?: string
  title: string
  description?: string
  density?: 'runtime' | 'configuration'
}>()

const titleId = useId()
</script>

<template>
  <header class="product-page-header" :data-density="density || 'runtime'" :aria-labelledby="titleId">
    <div class="product-page-header__copy">
      <p v-if="kicker" class="product-page-header__kicker">{{ kicker }}</p>
      <h1 :id="titleId">{{ title }}</h1>
      <p v-if="description" class="product-page-header__description">{{ description }}</p>
      <slot name="meta" />
    </div>
    <div v-if="$slots.actions || $slots.primary" class="product-page-header__actions">
      <div v-if="$slots.actions" class="product-page-header__secondary"><slot name="actions" /></div>
      <div v-if="$slots.primary" class="product-page-header__primary"><slot name="primary" /></div>
    </div>
  </header>
</template>
