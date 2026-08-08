<script setup lang="ts">
import { LayoutDashboard, RefreshCw } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import RuntimeDashboardCanvas from '@/components/dashboard/RuntimeDashboardCanvas.vue'
import { ApiRequestError } from '@/services/api'
import { runtimeDashboardApi } from '@/services/dashboard'
import { useSessionStore } from '@/stores/session'
import type { RuntimeDashboard } from '@/types/dashboard'

const route = useRoute()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const tenantId = computed(() => session.context?.tenantId ?? '')
const dashboard = ref<RuntimeDashboard | null>(null)
const loading = ref(false)
const unavailableMessage = ref('')
let generation = 0

const partialErrors = computed(() => dashboard.value?.widgets.filter(widget => widget.status === 'ERROR') ?? [])

function errorMessage(error: unknown) {
  return error instanceof ApiRequestError
    ? error.message || error.code
    : error instanceof Error
      ? error.message
      : '系统首页仪表盘暂时不可用'
}

async function loadDashboard() {
  const current = ++generation
  loading.value = true
  unavailableMessage.value = ''
  try {
    const result = await runtimeDashboardApi.systemHome(systemId.value)
    if (current === generation) dashboard.value = result
  } catch (error) {
    if (current !== generation) return
    dashboard.value = null
    unavailableMessage.value = errorMessage(error)
  } finally {
    if (current === generation) loading.value = false
  }
}

watch([systemId, tenantId], loadDashboard, { immediate: true })
</script>

<template>
  <section class="system-dashboard-page">
    <header class="system-dashboard-heading">
      <div><h1><LayoutDashboard :size="24" />{{ dashboard?.name ?? '系统仪表盘' }}</h1><p>{{ dashboard?.description ?? '查看当前权限和数据范围内的实时业务概览。' }}</p></div>
      <a-button class="dashboard-refresh" :loading="loading" @click="loadDashboard"><RefreshCw :size="16" />刷新</a-button>
    </header>

    <div v-if="loading" class="dashboard-loading" data-state="loading" role="status" aria-live="polite"><a-spin /><span>正在加载已发布仪表盘…</span></div>
    <a-alert v-else-if="unavailableMessage" class="dashboard-unavailable" data-state="unavailable" type="warning" show-icon message="仪表盘不可用" :description="unavailableMessage" />
    <div v-else-if="dashboard && !dashboard.widgets.length" class="dashboard-empty" data-state="empty"><LayoutDashboard :size="34" /><strong>仪表盘暂时没有组件</strong><span>管理员发布组件后会显示在这里。</span></div>
    <template v-else-if="dashboard">
      <a-alert v-if="partialErrors.length" class="dashboard-partial-error" data-state="partial-error" type="warning" show-icon :message="`${partialErrors.length} 个组件加载失败，其余组件仍可使用`" />
      <RuntimeDashboardCanvas :dashboard="dashboard" @refresh="loadDashboard" />
    </template>
  </section>
</template>

<style scoped>
.system-dashboard-page { display: grid; gap: 18px; max-width: 1440px; min-width: 0; margin: 0 auto; padding: 28px; background:#f4f6f8; }
.system-dashboard-heading { display: flex; justify-content: space-between; align-items: center; gap: 20px; }.system-dashboard-heading h1 { display: flex; align-items: center; gap: 9px; margin: 0; overflow-wrap:anywhere; }.system-dashboard-heading p { margin: 6px 0 0; color: #687781; overflow-wrap:anywhere; }
.dashboard-loading,.dashboard-empty { min-height: 340px; display: grid; place-content: center; justify-items: center; gap: 9px; border: 1px dashed #d4dde1; border-radius: 10px; background: #fff; color: #74818a; }
@media(max-width:900px){.system-dashboard-page{padding:20px}.system-dashboard-heading{align-items:flex-start}}
@media(max-width:560px){.system-dashboard-page{gap:14px;padding:14px}.system-dashboard-heading{align-items:stretch;flex-direction:column}.dashboard-refresh{align-self:flex-start}.dashboard-loading,.dashboard-empty{min-height:260px;padding:24px;text-align:center}}
</style>
