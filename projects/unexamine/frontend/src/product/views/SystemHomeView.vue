<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SystemShell from '../components/SystemShell.vue'
import ProductPage from '../components/ProductPage.vue'
import ProductState from '../components/ProductState.vue'
import { clearSession, systemContext, systemTokens } from '../session'
import RuntimeWorkspaceView from './RuntimeWorkspaceView.vue'
import FlowRuntimeView from './FlowRuntimeView.vue'
import WorkHubView from './WorkHubView.vue'
import TodoWorkspaceView from './TodoWorkspaceView.vue'
import MessageWorkspaceView from './MessageWorkspaceView.vue'
import FileWorkspaceView from './FileWorkspaceView.vue'
import AiWorkspaceView from './AiWorkspaceView.vue'
import WorkspaceHomeView from './WorkspaceHomeView.vue'
import KpiWorkspaceView from './KpiWorkspaceView.vue'
import { allowsPermission, hasResourceAccess, hasRuntimeModuleAccess, hasWorkConfigurationAccess, hasWorkRuntimeAccess } from '../permissions'
import type { CommandCenterItem } from '../types'

const router = useRouter()
const route = useRoute()
const initialWorkspace = String(route.query.workspace || '')
const workspaceKeys = new Set(['runtime', 'flow', 'tasks', 'todos', 'messages', 'files', 'ai', 'kpi'])
const active = ref(initialWorkspace || 'home')
const runtimeRefreshKey = ref(0)
const runtimeWorkspace = ref<{ executeCommand: (command: CommandCenterItem) => Promise<void> }>()
const shellKey = computed(() => systemContext.value?.contextRevision ?? `${systemContext.value?.systemId}:${systemContext.value?.tenantId}`)
const showRuntime = computed(() => hasRuntimeModuleAccess(systemContext.value?.permissions))
const showFlow = computed(() => hasResourceAccess(systemContext.value?.permissions, 'FLOW'))
const showTasks = computed(() => hasWorkRuntimeAccess(systemContext.value?.permissions, 'SYSTEM'))
const showTodos = computed(() => hasResourceAccess(systemContext.value?.permissions, 'TODO'))
const showMessages = computed(() => hasResourceAccess(systemContext.value?.permissions, 'MESSAGE'))
const showFiles = computed(() => hasResourceAccess(systemContext.value?.permissions, 'FILE'))
const showAi = computed(() => hasResourceAccess(systemContext.value?.permissions, 'AI'))
const showKpi = computed(() => systemContext.value?.systemId !== undefined)
const initialModuleCode = computed(() => String(route.query.module || ''))
const flowTarget = ref<{ taskId?: number; instanceId?: number }>({})
const showConfig = computed(() => allowsPermission(systemContext.value?.permissions, 'CONFIG', 'MODULE', 'MANAGE'))
const showAudit = computed(() => allowsPermission(systemContext.value?.permissions, 'AUDIT', 'EVENT', 'VIEW'))
const showSystemSettings = computed(() => allowsPermission(systemContext.value?.permissions, 'CONFIG', 'SYSTEM', 'MANAGE'))
const showAdmin = computed(() => showConfig.value || showAudit.value || showSystemSettings.value
  || allowsPermission(systemContext.value?.permissions, 'FLOW', 'SYSTEM', 'DESIGN')
  || allowsPermission(systemContext.value?.permissions, 'FLOW', 'SYSTEM', 'PUBLISH')
  || allowsPermission(systemContext.value?.permissions, 'APPLICATION', 'SYSTEM', 'VIEW')
  || allowsPermission(systemContext.value?.permissions, 'APPLICATION', 'SYSTEM', 'MANAGE')
  || allowsPermission(systemContext.value?.permissions, 'APPLICATION', '*', 'VIEW')
  || allowsPermission(systemContext.value?.permissions, 'APPLICATION', '*', 'MANAGE')
  || allowsPermission(systemContext.value?.permissions, 'AI', 'SYSTEM', 'VIEW')
  || allowsPermission(systemContext.value?.permissions, 'AI', 'SYSTEM', 'MANAGE')
  || allowsPermission(systemContext.value?.permissions, 'AI', 'SYSTEM', 'PUBLISH')
  || allowsPermission(systemContext.value?.permissions, 'MESSAGE', 'SYSTEM', 'MANAGE')
  || allowsPermission(systemContext.value?.permissions, 'MESSAGE', '*', 'MANAGE')
  || hasWorkConfigurationAccess(systemContext.value?.permissions, 'SYSTEM'))
const unavailableTitle = computed(() => workspaceKeys.has(active.value) ? '当前身份不能打开这个工作区' : '这个工作区不存在或已下线')

async function openAdmin(section: string) {
  await router.push({ path: `/systems/${systemContext.value?.systemId}/admin`, query: { section } })
}

function navigate(key: string) {
  active.value = key
  if (key !== 'flow') flowTarget.value = {}
  const query = { ...route.query }
  delete query.notice
  if (key !== 'home') query.workspace = key
  else delete query.workspace
  void router.replace({ path: route.path, query })
}

watch(() => route.query.workspace, (workspace) => {
  active.value = String(workspace || '') || 'home'
})

function openFlowTarget(target: { taskId?: number; instanceId?: number }) {
  flowTarget.value = target
  active.value = 'flow'
}

function openWorkTarget() {
  navigate('tasks')
}

function openMessageTarget(target: { route: string; targetType: string; targetId: string }) {
  if (target.targetType === 'FLOW_INSTANCE' && Number(target.targetId)) {
    openFlowTarget({ instanceId: Number(target.targetId) })
    return
  }
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
  active.value = 'runtime'
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
    :active-key="active"
    :show-runtime="showRuntime"
    :show-flow="showFlow"
    :show-tasks="showTasks"
    :show-todos="showTodos"
    :show-messages="showMessages"
    :show-files="showFiles"
    :show-ai="showAi"
    :show-admin="showAdmin"
    @navigate="navigate"
    @command="executeCommand"
  >
    <template v-if="active === 'home'">
      <WorkspaceHomeView context="system" />
    </template>
    <RuntimeWorkspaceView v-else-if="active === 'runtime' && showRuntime" ref="runtimeWorkspace" :key="`${runtimeRefreshKey}:${initialModuleCode}`" :refresh-key="runtimeRefreshKey" :initial-module-code="initialModuleCode" />
    <FlowRuntimeView v-else-if="active === 'flow' && showFlow" context="system" :initial-task-id="flowTarget.taskId" :initial-instance-id="flowTarget.instanceId" />
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
