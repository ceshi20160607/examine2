<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PlatformShell from '../components/PlatformShell.vue'
import ProductPage from '../components/ProductPage.vue'
import ProductState from '../components/ProductState.vue'
import { allowsPermission } from '../permissions'
import { platformContext, systemContext } from '../session'
import WorkLogsView from './WorkLogsView.vue'
import WorkProjectsView from './WorkProjectsView.vue'
import WorkConfigurationView from './WorkConfigurationView.vue'
import OrdinaryTasksView from './OrdinaryTasksView.vue'

type WorkTab = 'ordinary' | 'projects' | 'logs' | 'calendar'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const route = useRoute()
const router = useRouter()
const requestedTab = String(route.query.workTab || '')
const active = ref<WorkTab>(['ordinary', 'projects', 'logs', 'calendar'].includes(requestedTab) ? requestedTab as WorkTab : 'ordinary')
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const pageShell = computed(() => props.context === 'platform' ? PlatformShell : 'div')
function can(action: string) {
  return allowsPermission(current.value?.permissions, 'WORK', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'WORK', '*', action)
}
const tabs = computed<Array<{ value: WorkTab; label: string }>>(() => [
  ...(can('VIEW') || can('CREATE') || can('UPDATE') ? [
    { value: 'ordinary' as const, label: '普通任务' },
    { value: 'projects' as const, label: '项目任务' },
    { value: 'logs' as const, label: '每日日志' },
  ] : []),
  ...(can('VIEW_CALENDAR') ? [{ value: 'calendar' as const, label: '日历统计' }] : []),
])

watch(active, (tab) => {
  const query = { ...route.query }
  if (tab === 'ordinary') delete query.workTab
  else query.workTab = tab
  void router.replace({ path: route.path, query })
})

watch(tabs, (available) => {
  if (!available.some(item => item.value === active.value) && available[0]) active.value = available[0].value
}, { immediate: true })

function returnToWorkspace() {
  void router.push(props.context === 'platform' ? '/platform/dashboard' : `/systems/${systemContext.value?.systemId}`)
}
</script>

<template>
  <component :is="pageShell">
    <ProductPage class="work-hub-page">
      <a-segmented v-if="tabs.length" v-model:value="active" class="work-hub-tabs" :options="tabs" />
      <OrdinaryTasksView v-if="active === 'ordinary' && tabs.some(item => item.value === 'ordinary')" :context="context" />
      <WorkProjectsView v-else-if="active === 'projects' && tabs.some(item => item.value === 'projects')" :context="context" />
      <WorkLogsView v-else-if="active === 'logs' && tabs.some(item => item.value === 'logs')" :context="context" />
      <WorkConfigurationView v-else-if="active === 'calendar' && tabs.some(item => item.value === 'calendar')" :key="`${context}:calendar`" :context="context" mode="calendar" />
      <ProductState v-else state="forbidden" title="当前工作范围没有任务权限" description="请返回工作区使用其他已授权功能，或联系管理员调整工作权限。">
        <template #actions><a-button type="primary" @click="returnToWorkspace">返回工作区</a-button></template>
      </ProductState>
    </ProductPage>
  </component>
</template>
