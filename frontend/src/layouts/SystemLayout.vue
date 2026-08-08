<script setup lang="ts">
import { Blocks, Bot, Check, ChevronDown, Command, FileText, Gauge, LayoutDashboard, LogOut, RotateCcw, Settings, Settings2, UserRound } from 'lucide-vue-next'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AccountSecurityDialog from '@/components/AccountSecurityDialog.vue'
import SystemCommandCenter from '@/components/SystemCommandCenter.vue'
import { TODO_COUNTS_CHANGED_EVENT, todoApi } from '@/services/todo'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const router = useRouter()
const route = useRoute()
const systemId = computed(() => String(route.params.systemId))
const tenantId = computed(() => session.context?.tenantId ?? '')
const openTodoCount = ref(0)
const accountSecurityOpen = ref(false)
const commandCenterOpen = ref(false)
const commandCenterTrigger = ref<HTMLButtonElement | null>(null)
let todoCountGeneration = 0

async function loadTenants() {
  try {
    await session.loadTenants()
  } catch {
    // The active context remains usable even when the optional switcher cannot load.
  }
}

onMounted(loadTenants)
watch(systemId, loadTenants)

async function loadOpenTodoCount() {
  const generation = ++todoCountGeneration
  try {
    const result = await todoApi.counts(systemId.value)
    if (generation === todoCountGeneration) openTodoCount.value = result.openCount
  } catch {
    if (generation === todoCountGeneration) openTodoCount.value = 0
  }
}

function receiveTodoCounts(event: Event) {
  const next = (event as CustomEvent<{ openCount?: number }>).detail?.openCount
  if (typeof next === 'number') openTodoCount.value = next
  else void loadOpenTodoCount()
}

onMounted(() => window.addEventListener(TODO_COUNTS_CHANGED_EVENT, receiveTodoCounts))
onBeforeUnmount(() => window.removeEventListener(TODO_COUNTS_CHANGED_EVENT, receiveTodoCounts))
watch([systemId, tenantId], () => void loadOpenTodoCount(), { immediate: true })

function openCommandCenter(event?: KeyboardEvent) {
  if (!event || !(event.ctrlKey || event.metaKey) || event.key.toLocaleLowerCase() !== 'k') return
  event?.preventDefault()
  commandCenterOpen.value = true
}

onMounted(() => window.addEventListener('keydown', openCommandCenter))
onBeforeUnmount(() => window.removeEventListener('keydown', openCommandCenter))
watch(commandCenterOpen, (open, wasOpen) => {
  if (!open && wasOpen) void nextTick(() => commandCenterTrigger.value?.focus())
})

async function backToPlatform() {
  await session.switchPlatform()
  await router.push('/platform/workbench')
}

async function logout() {
  await session.logout()
  await router.replace('/auth/login')
}

async function switchTenant(tenantId: string) {
  await session.switchTenant(tenantId)
}
</script>

<template>
  <div class="app-shell">
    <a class="skip-link" href="#system-main">跳到主要内容</a>
    <header class="topbar">
      <RouterLink class="topbar-brand" to="/platform/workbench" aria-label="examine2 平台首页"><Blocks :size="22" /><strong>examine2</strong></RouterLink>
      <nav class="topnav" aria-label="系统主导航">
        <RouterLink :to="`/systems/${session.context?.systemId}/workbench`">工作台</RouterLink>
        <RouterLink class="dashboard-top-entry" :to="`/systems/${systemId}/dashboard`"><LayoutDashboard :size="15" />仪表盘</RouterLink>
        <RouterLink class="kpi-top-entry" :to="`/systems/${systemId}/kpis`"><Gauge :size="15" />KPI</RouterLink>
        <RouterLink class="report-top-entry" :to="`/systems/${systemId}/reports`"><FileText :size="15" />报表</RouterLink>
        <RouterLink class="operations-top-entry" :to="`/systems/${systemId}/operations`">运营</RouterLink>
        <RouterLink class="todo-top-entry" :to="`/systems/${systemId}/todos`">
          <a-badge :count="openTodoCount" :overflow-count="99">待办</a-badge>
        </RouterLink>
        <RouterLink v-if="['work.task.access', 'work.report.access', 'work.report.create', 'work.report.manage'].some(session.hasPermission)" :to="`/systems/${systemId}/tasks`">任务 / 日报</RouterLink>
        <RouterLink v-if="session.hasPermission('event.message.access')" :to="`/systems/${systemId}/messages`">消息</RouterLink>
        <RouterLink v-if="['file.create', 'file.read', 'file.reference', 'file.manage'].some(session.hasPermission)" :to="`/systems/${systemId}/files`">文件</RouterLink>
        <RouterLink v-if="['flow.definition.manage', 'flow.instance.start', 'flow.instance.decide', 'flow.instance.read', 'flow.external-task.work'].some(session.hasPermission)" :to="`/systems/${systemId}/flows`">审批</RouterLink>
        <RouterLink v-if="session.hasPermission('ai.agent.use')" class="agent-top-entry" :to="`/systems/${systemId}/agent`"><Bot :size="15" />Agent</RouterLink>
      </nav>
      <div class="topbar-tools">
        <button ref="commandCenterTrigger" class="icon-command command-center-trigger" type="button" title="统一命令中心（Ctrl/⌘ K）" aria-label="打开统一命令中心" @click="commandCenterOpen = true"><Command :size="18" /></button>
        <span class="context-chip system">{{ session.context?.systemName }}</span>
        <a-dropdown v-if="session.tenants.length > 1" :trigger="['click']" placement="bottomRight">
          <button class="tenant-switcher" type="button" title="切换租户" aria-label="切换当前租户">
            <span>{{ session.context?.tenantName || session.tenants.find((tenant) => tenant.id === session.context?.tenantId)?.name }}</span><ChevronDown :size="14" />
          </button>
          <template #overlay>
            <a-menu>
              <a-menu-item v-for="tenant in session.tenants" :key="tenant.id" :disabled="tenant.status !== 'ACTIVE'" @click="switchTenant(tenant.id)">
                <Check v-if="tenant.id === session.context?.tenantId" :size="15" /><span v-else class="menu-icon-placeholder" />{{ tenant.name }}
              </a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <span v-else-if="session.context?.tenantId" class="tenant-label">{{ session.context?.tenantName || session.tenants[0]?.name || session.context.tenantId }}</span>
        <a-dropdown :trigger="['click']" placement="bottomRight">
          <button class="icon-command" type="button" title="个人菜单" aria-label="打开个人菜单">
            <UserRound :size="18" /><span>{{ session.context?.account.displayName }}</span><ChevronDown :size="14" />
          </button>
          <template #overlay>
            <a-menu>
              <a-menu-item v-if="session.hasShell('SYSTEM_ADMIN')" @click="router.push(`/systems/${systemId}/admin`)"><Settings2 :size="15" /> 系统后台</a-menu-item>
              <a-menu-item @click="backToPlatform"><RotateCcw :size="15" /> 返回平台</a-menu-item>
              <a-menu-item class="account-security-menu-trigger" @click="accountSecurityOpen = true"><Settings :size="15" /> 个人设置</a-menu-item>
              <a-menu-divider />
              <a-menu-item @click="logout"><LogOut :size="15" /> 退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </header>
    <main id="system-main" class="page-surface" tabindex="-1"><RouterView /></main>
    <SystemCommandCenter v-model:open="commandCenterOpen" />
    <AccountSecurityDialog v-model:open="accountSecurityOpen" />
  </div>
</template>
