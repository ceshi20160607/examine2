<script setup lang="ts">
defineProps<{ moduleName?: string; task: string; loading?: boolean }>()
const emit = defineEmits<{ back: [] }>()
</script>
<template>
  <div class="module-context-banner"><span>{{ moduleName ? `当前模块：${moduleName}` : loading ? '正在加载当前模块…' : '当前模块尚未发布或不可访问' }}</span><a-button size="small" @click="emit('back')">返回模块{{ task }}</a-button></div>
</template>
<style scoped>
.module-context-banner { display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; padding: 10px 14px; border: 1px solid #dbeafe; background: #eff6ff; border-radius: 6px; color: #334155; }
</style>
