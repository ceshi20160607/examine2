<script setup lang="ts">
withDefaults(defineProps<{
  label?: string
}>(), {
  label: '列表查询与筛选',
})
</script>

<template>
  <section class="product-filter-bar" role="search" :aria-label="label">
    <div class="product-filter-bar__query"><slot /></div>
    <div v-if="$slots.tools" class="product-filter-bar__tools"><slot name="tools" /></div>
  </section>
</template>
