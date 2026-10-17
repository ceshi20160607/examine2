<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { useRoute, useRouter, type LocationQueryRaw } from 'vue-router'
import SystemShell from '../components/SystemShell.vue'
import ProductPage from '../components/ProductPage.vue'
import ProductState from '../components/ProductState.vue'
import { clearSession, platformTokens, systemContext, systemTokens } from '../session'
import { api, ApiError } from '../api'
import { establishTenantSession } from '../system-entry'
import RuntimeWorkspaceView from './RuntimeWorkspaceView.vue'
import FlowRuntimeView from './FlowRuntimeView.vue'
import WorkHubView from './WorkHubView.vue'
import TodoWorkspaceView from './TodoWorkspaceView.vue'
import MessageWorkspaceView from './MessageWorkspaceView.vue'
import FileWorkspaceView from './FileWorkspaceView.vue'
import AiWorkspaceView from './AiWorkspaceView.vue'
import WorkspaceHomeView from './WorkspaceHomeView.vue'
import ModuleDashboardView from './ModuleDashboardView.vue'
import PersonalDashboardView from './PersonalDashboardView.vue'
import KpiWorkspaceView from './KpiWorkspaceView.vue'
import MyApplicationsView from './MyApplicationsView.vue'
import { allowsPermission, hasModuleShareManagementAccess, hasResourceAccess, hasRuntimeModuleAccess, hasWorkConfigurationAccess, hasWorkRuntimeAccess } from '../permissions'
import type { CommandCenterItem } from '../types'

const router = useRouter()
const route = useRoute()
const initialWorkspace = String(route.query.workspace || '')
const workspaceKeys = new Set(['runtime', 'module-home', 'personal', 'flow', 'applications', 'tasks', 'todos', 'messages', 'files', 'ai', 'kpi'])
const active = ref(initialWorkspace || 'home')
const runtimeRefreshKey = ref(0)
const accessibleTenants = ref<Array<{ id: number; name: string; main: boolean }>>([])
const tenantSwitching = ref(false)
const tenantSwitchable = computed(() => Boolean(systemContext.value?.tenantSwitchContext?.tenantSwitchable))
const runtimeWorkspace = ref<{ executeCommand: (command: CommandCenterItem) => Promise<void> }>()
const shellKey = computed(() => systemContext.value?.contextRevision ?? `${systemContext.value?.systemId}:${systemContext.value?.tenantId}`)
const showRuntime = computed(() => hasRuntimeModuleAccess(systemContext.value?.permissions))
const showFlow = computed(() => hasResourceAccess(systemContext.value?.permissions, 'FLOW'))
const showApplications = computed(() => Boolean(systemTokens.value?.accessToken
  && systemContext.value?.systemId && systemContext.value?.tenantMemberId))
const showTasks = computed(() => hasWorkRuntimeAccess(systemContext.value?.permissions, 'SYSTEM'))
const showTodos = computed(() => hasResourceAccess(systemContext.value?.permissions, 'TODO'))
const showMessages = computed(() => hasResourceAccess(systemContext.value?.permissions, 'MESSAGE'))
const showFiles = computed(() => hasResourceAccess(systemContext.value?.permissions, 'FILE'))
const showAi = computed(() => hasResourceAccess(systemContext.value?.permissions, 'AI'))
const showKpi = computed(() => systemContext.value?.systemId !== undefined)
const initialModuleCode = computed(() => String(route.query.module || ''))
const flowTarget = computed<{ taskId?: number; instanceId?: number }>(() => route.query.workspace === 'flow'
  ? { taskId: targetId(route.query.taskId), instanceId: targetId(route.query.instanceId) } : {})
const showConfig = computed(() => allowsPermission(systemContext.value?.permissions, 'CONFIG', 'MODULE', 'MANAGE'))
const showShareSettings = computed(() => hasModuleShareManagementAccess(systemContext.value?.permissions))
const showAudit = computed(() => allowsPermission(systemContext.value?.permissions, 'AUDIT', 'EVENT', 'VIEW'))
const showSystemSettings = computed(() => allowsPermission(systemContext.value?.permissions, 'CONFIG', 'SYSTEM', 'MANAGE'))
const showAdmin = computed(() => showConfig.value || showShareSettings.value || showAudit.value || showSystemSettings.value
  || allowsPermission(systemContext.value?.permissions, 'FLOW', 'SYSTEM', 'DESIGN')
  || allowsPermission(systemContext.value?.permissions, 'FLOW', 'SYSTEM', 'PUBLISH')
  || allowsPermission(systemContext.value?.permissions, 'APPLICATION', 'SYSTEM', 'VIEW')
  || allowsPermission(systemContext.value?.permissions, 'APPLICATION', 'SYSTEM', 'MANAGE')
  || allowsPermission(systemContext.value?.permissions, 'APPLICATION', '*', 'VIEW')
  || allowsPermission(systemContext.value?.permissions, 'APPLICATION', '*', 'MANAGE')
  || allowsPermission(systemContext.value?.permissions, 'AI', 'SYSTEM', 'MANAGE')
  || allowsPermission(systemContext.value?.permissions, 'MESSAGE', 'SYSTEM', 'MANAGE')
  || allowsPermission(systemContext.value?.permissions, 'MESSAGE', '*', 'MANAGE')
  || hasWorkConfigurationAccess(systemContext.value?.permissions, 'SYSTEM'))
const unavailableTitle = computed(() => workspaceKeys.has(active.value) ? '当前身份不能打开这个工作区' : '这个工作区不存在或已下线')

/** 租户选项来自当前账号的有效成员映射，不能使用后台的全部租户目录。 */
async function loadAccessibleTenants() {
  const systemId = systemContext.value?.systemId
  const token = platformTokens.value?.accessToken
  if (!tenantSwitchable.value || !systemId || !token) { accessibleTenants.value = []; return }
  try {
    const result = await api<Array<{ id: number; name: string; main: boolean }>>(
      `/api/systems/${systemId}/tenants/access`, {}, token)
    if (systemContext.value?.systemId === systemId && tenantSwitchable.value) accessibleTenants.value = result
  } catch (reason) {
    accessibleTenants.value = []
    message.error(reason instanceof ApiError ? reason.message : '可进入的租户读取失败')
  }
}

/** 切换后重新挂载业务工作区，使模块目录和数据都按新租户权限读取。 */
async function switchTenant(tenantId: number) {
  const systemId = systemContext.value?.systemId
  if (!systemId || tenantSwitching.value || tenantId === systemContext.value?.tenantId
    || !accessibleTenants.value.some((tenant) => tenant.id === tenantId)) return
  tenantSwitching.value = true
  try {
    await establishTenantSession(systemId, tenantId, 'MULTI')
    runtimeRefreshKey.value += 1
    active.value = 'runtime'
    await router.replace({ path: `/systems/${systemId}`, query: { workspace: 'runtime' } })
    await loadAccessibleTenants()
    message.success('已切换租户，业务模块已按当前权限重新加载')
  } catch (reason) {
    message.error(reason instanceof ApiError ? reason.message : reason instanceof Error ? reason.message : '租户切换失败')
  } finally { tenantSwitching.value = false }
}

watch(() => [systemContext.value?.systemId, tenantSwitchable.value], () => { void loadAccessibleTenants() }, { immediate: true })

async function openAdmin(section: string) {
  await router.push({ path: `/systems/${systemContext.value?.systemId}/admin`, query: { section } })
}

function navigate(key: string) {
  active.value = key
  const query = { ...route.query }
  delete query.notice
  delete query.instanceId
  delete query.taskId
  delete query.actionId
  if (route.query.workspace === 'applications') delete query.module
  if (key !== 'home') query.workspace = key
  else delete query.workspace
  return router.replace({ path: route.path, query })
}

watch(() => route.query.workspace, (workspace) => {
  active.value = String(workspace || '') || 'home'
  // 新工作区从标题开始阅读；仅打开详情或审批抽屉的 query 变化仍保留列表位置。
  void nextTick(() => window.scrollTo({ top: 0, left: 0 }))
})

function targetId(value: unknown) {
  if (typeof value !== 'string' || !/^\d+$/.test(value)) return undefined
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
}

function openFlowTarget(target: { taskId?: number; instanceId?: number }) {
  const query: LocationQueryRaw = { ...route.query, workspace: 'flow' }
  delete query.notice
  delete query.instanceId
  delete query.taskId
  delete query.actionId
  if (route.query.workspace === 'applications') delete query.module
  if (target.taskId) query.taskId = String(target.taskId)
  if (target.instanceId) query.instanceId = String(target.instanceId)
  void router.push({ path: route.path, query })
}

function openWorkTarget(target: { taskId: number }) {
  // 待办已校验当前任务权限；保留目标标识，直接打开任务详情而非只进入任务列表。
  void router.push({ path: route.path, query: { workspace: 'tasks', taskId: String(target.taskId) } })
}

function openMessageTarget(target: { route: string; targetType: string; targetId: string }) {
  // 消息打开接口已验证当前所有权与目标权限，使用其生成的地址而非客户端目标 ID。
  void router.push(target.route)
}

async function executeCommand(command: CommandCenterItem) {
  if (command.target === 'HOME') {
    navigate('home')
    return
  }
  if (command.target === 'MODULE_CONFIG') {
    await openAdmin('templates')
    return
  }
  if (command.target === 'AUDIT') {
    await openAdmin('audit')
    return
  }
  if (command.target === 'SYSTEM_SETTINGS') {
    await openAdmin('system-info')
    return
  }
  await navigate('runtime')
  await nextTick()
  await runtimeWorkspace.value?.executeCommand(command)
}

if (!systemTokens.value?.accessToken || !systemContext.value?.systemId) {
  clearSession()
  void router.replace('/login')
}
</script>

<template>
  <SystemShell
    :key="shellKey"
    :system-name="systemContext?.systemName"
    :tenant-name="systemContext?.tenantName"
    :active-key="active === 'module-home' ? 'runtime' : active"
    :show-runtime="showRuntime"
    :show-flow="showFlow"
    :show-applications="showApplications"
    :show-tasks="showTasks"
    :show-todos="showTodos"
    :show-messages="showMessages"
    :show-files="showFiles"
    :show-ai="showAi"
    :show-admin="showAdmin"
    :tenant-switchable="tenantSwitchable"
    :tenant-options="accessibleTenants"
    :tenant-switching="tenantSwitching"
    @navigate="navigate"
    @command="executeCommand"
    @switch-tenant="switchTenant"
  >
    <template v-if="active === 'home'">
      <WorkspaceHomeView context="system" />
    </template>
    <PersonalDashboardView v-else-if="active === 'personal'" />
    <ModuleDashboardView v-else-if="active === 'module-home' && showRuntime" />
    <RuntimeWorkspaceView v-else-if="active === 'runtime' && showRuntime" ref="runtimeWorkspace" :key="`${shellKey}:${runtimeRefreshKey}:${initialModuleCode}`" :refresh-key="runtimeRefreshKey" :initial-module-code="initialModuleCode" />
    <FlowRuntimeView v-else-if="active === 'flow' && showFlow" context="system" :initial-task-id="flowTarget.taskId" :initial-instance-id="flowTarget.instanceId" />
    <MyApplicationsView v-else-if="active === 'applications' && showApplications" />
    <WorkHubView v-else-if="active === 'tasks' && showTasks" context="system" />
    <TodoWorkspaceView v-else-if="active === 'todos' && showTodos" context="system" @open-flow="openFlowTarget" @open-work="openWorkTarget" />
    <MessageWorkspaceView v-else-if="active === 'messages' && showMessages" context="system" @open-target="openMessageTarget" />
    <FileWorkspaceView v-else-if="active === 'files' && showFiles" />
    <AiWorkspaceView v-else-if="active === 'ai' && showAi" :initial-module-code="initialModuleCode" />
    <KpiWorkspaceView v-else-if="active === 'kpi' && showKpi" />
    <ProductPage v-else>
      <ProductState state="forbidden" :title="unavailableTitle" description="请从左侧选择当前身份可以使用的功能；如需新的业务权限，请联系系统管理员。">
        <template #actions><a-button type="primary" @click="navigate('home')">返回系统首页</a-button></template>
      </ProductState>
    </ProductPage>
  </SystemShell>
</template>
