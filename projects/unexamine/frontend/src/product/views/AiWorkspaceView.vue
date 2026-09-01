<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AiContextQueryView from './AiContextQueryView.vue'
import AiConfirmedWriteView from './AiConfirmedWriteView.vue'

defineProps<{ initialModuleCode?: string }>()
const route = useRoute()
const router = useRouter()
const active = ref(route.query.aiMode === 'write' ? 'write' : 'query')

watch(active, (value) => void router.replace({ path: route.path, query: { ...route.query, aiMode: value } }))
watch(() => route.query.aiMode, (value) => { active.value = value === 'write' ? 'write' : 'query' })
</script>

<template>
  <div class="ai-workspace-shell">
    <a-segmented v-model:value="active" :options="[{ value: 'query', label: '权限内查询' }, { value: 'write', label: 'AI 创建（需确认）' }]" />
    <AiContextQueryView v-if="active === 'query'" :initial-module-code="initialModuleCode" />
    <AiConfirmedWriteView v-else :initial-module-code="initialModuleCode" />
  </div>
</template>
