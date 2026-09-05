<script setup lang="ts">
import { ExclamationCircleOutlined, InboxOutlined, LockOutlined } from '@ant-design/icons-vue'

withDefaults(defineProps<{
  state: 'loading' | 'empty' | 'error' | 'forbidden'
  title?: string
  description?: string
  requestId?: string | null
}>(), {
  title: '',
  description: '',
  requestId: null,
})
</script>

<template>
  <section class="product-state" :data-state="state" role="status" aria-live="polite">
    <div v-if="state === 'loading'" class="product-state__skeleton">
      <a-skeleton active :paragraph="{ rows: 4 }" />
    </div>
    <div v-else class="product-state__content">
      <span class="product-state__icon" aria-hidden="true">
        <LockOutlined v-if="state === 'forbidden'" />
        <ExclamationCircleOutlined v-else-if="state === 'error'" />
        <InboxOutlined v-else />
      </span>
      <h2>{{ title || (state === 'forbidden' ? '暂时无法访问' : state === 'error' ? '内容加载失败' : '这里还没有内容') }}</h2>
      <p>{{ description || (state === 'forbidden' ? '请返回上一页或联系管理员确认访问范围。' : state === 'error' ? '请稍后重试；如果问题持续，可将请求编号提供给管理员。' : '完成首项业务操作后，内容会显示在这里。') }}</p>
      <small v-if="requestId" class="product-state__request">请求编号：{{ requestId }}</small>
      <div v-if="$slots.actions" class="product-state__actions"><slot name="actions" /></div>
    </div>
  </section>
</template>
