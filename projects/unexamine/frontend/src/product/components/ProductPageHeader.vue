<script setup lang="ts">
defineProps<{
  kicker: string
  title: string
  description?: string
}>()
</script>

<template>
  <header class="product-page-header">
    <div class="product-page-header__copy">
      <p class="product-page-header__kicker">{{ kicker }}</p>
      <h1>{{ title }}</h1>
      <p v-if="description" class="product-page-header__description">{{ description }}</p>
      <slot name="meta" />
    </div>
    <div v-if="$slots.actions" class="product-page-header__actions">
      <slot name="actions" />
    </div>
  </header>
</template>
