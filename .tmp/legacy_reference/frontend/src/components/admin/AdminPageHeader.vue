<script setup lang="ts">
defineProps<{ title: string; description: string }>()
</script>

<template>
  <header class="admin-page-heading">
    <div><h1>{{ title }}</h1><p>{{ description }}</p></div>
    <div v-if="$slots.actions" class="admin-page-actions"><slot name="actions" /></div>
  </header>
</template>
