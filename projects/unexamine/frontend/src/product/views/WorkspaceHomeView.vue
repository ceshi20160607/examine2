<script setup lang="ts">
import { BellOutlined, CheckSquareOutlined, DatabaseOutlined, ReloadOutlined, RightOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import { allowsPermission } from '../permissions'
import { isVerificationArtifactName, productDateTime, userFacingDateTime, userFacingWorkspaceName } from '../presentation'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { todoTypeLabel } from '../todo'
import type { MessageInboxView, RuntimeModuleCatalogItem, RuntimeRecord, RuntimeRecordList, TodoItemView } from '../types'
import DashboardRuntimeView from './DashboardRuntimeView.vue'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'system' })
const router = useRouter()
const route = useRoute()
const loading = ref(false)
const error = ref('')
const todos = ref<TodoItemView[]>([])
const unreadMessages = ref(0)
const recentMessages = ref<MessageInboxView['messages']>([])
const systems = ref<Array<{ systemId: number; systemName: string; defaultTenantName: string }>>([])
const modules = ref<RuntimeModuleCatalogItem[]>([])
const recentRecords = ref<Array<RuntimeRecord & { moduleCode: string; moduleName: string }>>([])

const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const canViewTodos = computed(() => allowsPermission(current.value?.permissions, 'TODO', props.context === 'platform' ? 'PLATFORM' : 'SYSTEM', 'VIEW')
  || allowsPermission(current.value?.permissions, 'TODO', '*', 'VIEW'))
const canConfigureModules = computed(() => props.context === 'system'
  && allowsPermission(systemContext.value?.permissions, 'CONFIG', 'MODULE', 'MANAGE'))
const greeting = computed(() => {
  const hour = new Date().getHours()
  return hour < 12 ? '早上好' : hour < 18 ? '下午好' : '晚上好'
})
const title = computed(() => `${greeting.value}，${current.value?.displayName || '欢迎回来'}`)
const scopeName = computed(() => props.context === 'platform'
  ? '平台工作首页'
  : `${systemContext.value?.systemName || '当前系统'} · ${userFacingWorkspaceName(systemContext.value?.tenantName)}`)

async function load() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    const todoRequest = canViewTodos.value
      ? api<TodoItemView[]>('/api/todos?status=PENDING&type=ALL', {}, token.value).catch(() => [])
      : Promise.resolve([])
    const messageRequest = api<MessageInboxView>('/api/messages?status=UNREAD&sourceType=ALL', {}, token.value)
      .catch(() => ({ messages: [], unreadCount: 0 }))
    if (props.context === 'platform') {
      const [todoRows, inbox, accessibleSystems] = await Promise.all([
        todoRequest,
        messageRequest,
        api<Array<{ systemId: number; systemName: string; defaultTenantName: string }>>('/api/systems', {}, token.value),
      ])
      todos.value = todoRows.filter(item => !isVerificationArtifactName(item.title)).slice(0, 5)
      unreadMessages.value = inbox.unreadCount
      recentMessages.value = inbox.messages.filter(item => !isVerificationArtifactName(item.subject)).slice(0, 4)
      systems.value = accessibleSystems.filter(item => !isVerificationArtifactName(item.systemName))
      modules.value = []
      recentRecords.value = []
    } else {
      const [todoRows, inbox, moduleRows] = await Promise.all([
        todoRequest,
        messageRequest,
        api<RuntimeModuleCatalogItem[]>('/api/runtime/modules', {}, token.value),
      ])
      todos.value = todoRows.filter(item => !isVerificationArtifactName(item.title)).slice(0, 5)
      unreadMessages.value = inbox.unreadCount
      recentMessages.value = inbox.messages.filter(item => !isVerificationArtifactName(item.subject)).slice(0, 4)
      modules.value = moduleRows
      systems.value = []
      const recordGroups = await Promise.all(moduleRows.slice(0, 3).map(async module => {
        const params = new URLSearchParams({ lifecycleState: 'ACTIVE', tenantScope: 'ALL', search: '', filters: '[]', sortField: 'updatedAt', sortDirection: 'DESC', page: '1', pageSize: '4' })
        const result = await api<RuntimeRecordList>(`/api/runtime/modules/${module.moduleCode}/records?${params}`, {}, token.value).catch(() => ({ records: [], total: 0, page: 1, pageSize: 4 }))
        return result.records.map(record => ({ ...record, moduleCode: module.moduleCode, moduleName: module.moduleName }))
      }))
      recentRecords.value = recordGroups.flat().sort((a, b) => String(b.updatedAt).localeCompare(String(a.updatedAt))).slice(0, 5)
    }
  } catch {
    error.value = '工作首页暂时无法完整加载，请稍后重试。'
  } finally {
    loading.value = false
  }
}

function openWorkspace(workspace: string) {
  if (props.context === 'platform') {
    const routes: Record<string, string> = { todos: '/platform/todos', messages: '/platform/messages', systems: '/platform' }
    void router.push(routes[workspace] || '/platform/dashboard')
    return
  }
  void router.replace({ path: route.path, query: { ...route.query, workspace } })
}

function openModule(moduleCode: string, recordId?: number) {
  void router.replace({ path: route.path, query: { ...route.query, workspace: 'runtime', module: moduleCode, ...(recordId ? { recordId: String(recordId) } : {}) } })
}

function openSystems() {
  void router.push('/platform')
}

function configureModules() {
  void router.push(`/systems/${systemContext.value?.systemId}/admin?section=templates`)
}

watch(() => current.value?.contextRevision, load)
onMounted(load)
</script>

<template>
  <div class="workspace-home">
    <ProductPageHeader :kicker="scopeName" :title="title" description="先处理需要关注的事项，再从最近工作继续。">
      <template #actions>
        <a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button>
        <a-button v-if="context === 'platform'" type="primary" @click="openWorkspace('systems')">进入系统</a-button>
        <a-button v-else-if="modules.length" type="primary" @click="openWorkspace('runtime')">进入业务模块</a-button>
        <a-button v-else-if="canConfigureModules" type="primary" @click="configureModules">配置第一个模块</a-button>
      </template>
    </ProductPageHeader>

    <a-alert v-if="error" type="warning" show-icon :message="error" class="section-alert"><template #action><a-button size="small" @click="load">重新加载</a-button></template></a-alert>

    <section class="home-metrics" aria-label="工作摘要">
      <button type="button" @click="openWorkspace('todos')"><CheckSquareOutlined /><span><strong>{{ todos.length }}</strong><small>待处理事项</small></span><RightOutlined /></button>
      <button type="button" @click="openWorkspace('messages')"><BellOutlined /><span><strong>{{ unreadMessages }}</strong><small>未读消息</small></span><RightOutlined /></button>
      <button v-if="context === 'platform'" type="button" @click="openWorkspace('systems')"><DatabaseOutlined /><span><strong>{{ systems.length }}</strong><small>可进入系统</small></span><RightOutlined /></button>
      <button v-else type="button" @click="openWorkspace('runtime')"><DatabaseOutlined /><span><strong>{{ modules.length }}</strong><small>可用业务模块</small></span><RightOutlined /></button>
    </section>

    <div class="home-content-grid">
      <section class="panel-card home-panel">
        <header class="home-panel__heading"><div><strong>现在需要处理</strong><small>按当前权限汇总的待办</small></div><a-button type="link" @click="openWorkspace('todos')">查看全部</a-button></header>
        <div v-if="todos.length" class="home-item-list">
          <button v-for="todo in todos" :key="todo.id" type="button" @click="openWorkspace('todos')"><span><strong>{{ todo.title }}</strong><small>{{ todoTypeLabel(todo.todoType) }}<template v-if="todo.dueAt"> · 截止 {{ productDateTime(todo.dueAt) }}</template></small></span><ProductStatusTag :status="todo.status" /></button>
        </div>
        <a-empty v-else :image="false" description="当前没有待处理事项" />
      </section>

      <section class="panel-card home-panel">
        <header class="home-panel__heading"><div><strong>{{ context === 'platform' ? '我的系统' : '业务快捷入口' }}</strong><small>{{ context === 'platform' ? '最近可继续的工作空间' : '当前可用的已发布模块' }}</small></div><a-button type="link" @click="openWorkspace(context === 'platform' ? 'systems' : 'runtime')">查看全部</a-button></header>
        <div v-if="context === 'platform' && systems.length" class="home-shortcuts">
          <button v-for="system in systems.slice(0, 6)" :key="system.systemId" type="button" @click="openSystems"><span class="home-shortcut-mark">{{ system.systemName.slice(0, 1) }}</span><span><strong>{{ system.systemName }}</strong><small>{{ userFacingWorkspaceName(system.defaultTenantName) }}</small></span><RightOutlined /></button>
        </div>
        <div v-else-if="context === 'system' && modules.length" class="home-shortcuts">
          <button v-for="module in modules.slice(0, 6)" :key="module.moduleId" type="button" @click="openModule(module.moduleCode)"><span class="home-shortcut-mark">{{ module.moduleName.slice(0, 1) }}</span><span><strong>{{ module.moduleName }}</strong><small>{{ module.groupName }}</small></span><RightOutlined /></button>
        </div>
        <a-empty v-else :image="false" :description="context === 'platform' ? '还没有可进入的系统' : '还没有已发布的业务模块'" />
      </section>

      <section v-if="context === 'system'" class="panel-card home-panel home-panel--wide">
        <header class="home-panel__heading"><div><strong>最近业务记录</strong><small>按更新时间汇总最近处理内容</small></div><a-button type="link" @click="openWorkspace('runtime')">进入业务模块</a-button></header>
        <div v-if="recentRecords.length" class="home-records">
          <button v-for="record in recentRecords" :key="`${record.moduleCode}-${record.id}`" type="button" @click="openModule(record.moduleCode, record.id)"><span><strong>{{ record.title }}</strong><small>{{ record.moduleName }} · {{ userFacingDateTime(record.updatedAt) }}</small></span><ProductStatusTag :status="record.status" /><RightOutlined /></button>
        </div>
        <a-empty v-else :image="false" description="发布模块并开始处理业务后，最近记录会显示在这里" />
      </section>

      <section class="panel-card home-panel" :class="{ 'home-panel--wide': context === 'platform' }">
        <header class="home-panel__heading"><div><strong>最新消息</strong><small>只显示当前工作范围内的消息</small></div><a-button type="link" @click="openWorkspace('messages')">查看全部</a-button></header>
        <div v-if="recentMessages.length" class="home-item-list"><button v-for="item in recentMessages" :key="item.id" type="button" @click="openWorkspace('messages')"><span><strong>{{ item.subject }}</strong><small>{{ userFacingDateTime(item.createdAt) }}</small></span><span class="home-unread-dot" aria-label="未读" /></button></div>
        <a-empty v-else :image="false" description="目前没有未读消息" />
      </section>
    </div>

    <DashboardRuntimeView :context="context" embedded />
  </div>
</template>
