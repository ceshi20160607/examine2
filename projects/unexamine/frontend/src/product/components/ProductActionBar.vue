<template>
  <div class="product-action-bar">
    <div v-if="$slots.default" class="product-action-bar__secondary"><slot /></div>
    <div v-if="$slots.primary" class="product-action-bar__primary"><slot name="primary" /></div>
  </div>
</template>
