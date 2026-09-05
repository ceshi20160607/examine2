<script setup lang="ts">
defineProps<{
  title?: string
  description?: string
  flush?: boolean
}>()
</script>

<template>
  <section class="product-section" :data-flush="Boolean(flush)">
    <header v-if="title || description || $slots.actions" class="product-section__header">
      <div class="product-section__copy">
        <h2 v-if="title">{{ title }}</h2>
        <p v-if="description">{{ description }}</p>
      </div>
      <div v-if="$slots.actions" class="product-section__actions"><slot name="actions" /></div>
    </header>
    <div class="product-section__body"><slot /></div>
  </section>
</template>
