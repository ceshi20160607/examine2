<script setup lang="ts">
import ProductActionBar from './ProductActionBar.vue'
import ProductState from './ProductState.vue'

withDefaults(defineProps<{
  open: boolean
  title?: string
  width?: string | number
  loading?: boolean
  error?: string
}>(), {
  title: '详情',
  width: 520,
  loading: false,
  error: '',
})

const emit = defineEmits<{ close: [] }>()
</script>

<template>
  <a-drawer class="product-detail-drawer" :open="open" :title="title" :width="width" @close="emit('close')">
    <template v-if="$slots.extra" #extra><slot name="extra" /></template>
    <ProductState v-if="loading" state="loading" title="正在加载详情" />
    <ProductState v-else-if="error" state="error" title="详情加载失败" :description="error">
      <template v-if="$slots['error-actions']" #actions><slot name="error-actions" /></template>
    </ProductState>
    <slot v-else />
    <template v-if="$slots.actions || $slots.primary" #footer>
      <ProductActionBar>
        <a-button @click="emit('close')">关闭</a-button>
        <slot name="actions" />
        <template v-if="$slots.primary" #primary><slot name="primary" /></template>
      </ProductActionBar>
    </template>
  </a-drawer>
</template>
