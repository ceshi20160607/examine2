<script setup lang="ts">
import ProductActionBar from './ProductActionBar.vue'

withDefaults(defineProps<{
  open: boolean
  title: string
  width?: string | number
  saving?: boolean
  saveDisabled?: boolean
  saveLabel?: string
  error?: string
  maskClosable?: boolean
  keyboard?: boolean
}>(), {
  width: 560,
  saving: false,
  saveDisabled: false,
  saveLabel: '保存',
  error: '',
  maskClosable: false,
  keyboard: false,
})

const emit = defineEmits<{
  close: []
  save: []
}>()
</script>

<template>
  <a-drawer class="product-form-drawer" :open="open" :title="title" :width="width" :mask-closable="maskClosable" :keyboard="keyboard" @close="emit('close')">
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <slot />
    <template #footer>
      <ProductActionBar>
        <a-button @click="emit('close')">取消</a-button>
        <slot name="secondary" />
        <template #primary><a-button type="primary" :loading="saving" :disabled="saveDisabled" @click="emit('save')">{{ saveLabel }}</a-button></template>
      </ProductActionBar>
    </template>
  </a-drawer>
</template>
