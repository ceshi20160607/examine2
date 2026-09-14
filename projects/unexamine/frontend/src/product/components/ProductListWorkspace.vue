<script setup lang="ts">
import ProductState from './ProductState.vue'

const props = withDefaults(defineProps<{
  loading?: boolean
  hasContent?: boolean
  empty?: boolean
  error?: string
  emptyTitle?: string
  emptyDescription?: string
  loadingTitle?: string
}>(), {
  loading: false,
  hasContent: false,
  empty: false,
  error: '',
  emptyTitle: '当前条件下没有数据',
  emptyDescription: '可以调整搜索或筛选条件，也可以新建第一条数据。',
  loadingTitle: '正在加载数据',
})
</script>

<template>
  <div :class="['product-list-workspace', { 'product-list-workspace--with-navigation': $slots.navigation }]">
    <aside v-if="$slots.navigation" class="product-list-workspace__navigation panel-card">
      <slot name="navigation" />
    </aside>
    <section class="product-list-workspace__content panel-card" aria-live="polite" :aria-busy="loading">
      <slot name="notice" />
      <div v-if="$slots.toolbar" class="product-list-workspace__toolbar"><slot name="toolbar" /></div>
      <div v-if="$slots.summary" class="product-list-workspace__summary"><slot name="summary" /></div>
      <div v-if="$slots.batch" class="product-list-workspace__batch"><slot name="batch" /></div>
      <a-alert v-if="error && hasContent" type="error" show-icon :message="error" class="product-list-workspace__inline-error">
        <template v-if="$slots['state-actions']" #action><slot name="state-actions" state="error" /></template>
      </a-alert>
      <ProductState v-if="loading && !hasContent" state="loading" :title="loadingTitle" />
      <ProductState v-else-if="error && !hasContent" state="error" title="数据加载失败" :description="error">
        <template v-if="$slots['state-actions']" #actions><slot name="state-actions" state="error" /></template>
      </ProductState>
      <ProductState v-else-if="empty && !hasContent" state="empty" :title="emptyTitle" :description="emptyDescription">
        <template v-if="$slots['state-actions']" #actions><slot name="state-actions" state="empty" /></template>
      </ProductState>
      <div v-else class="product-list-workspace__body"><slot /></div>
      <div v-if="$slots.pagination && hasContent" class="product-list-workspace__pagination"><slot name="pagination" /></div>
    </section>
  </div>
</template>
