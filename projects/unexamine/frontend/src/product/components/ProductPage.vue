<script setup lang="ts">
withDefaults(defineProps<{
  density?: 'runtime' | 'configuration'
}>(), {
  density: 'runtime',
})
</script>

<template>
  <main class="product-page" :data-density="density">
    <slot />
  </main>
</template>
