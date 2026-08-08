<script setup lang="ts">
import { Blocks, CheckSquare, FilePlus2, LayoutGrid, Menu, Search, Server, Zap } from 'lucide-vue-next'
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { runtimeApi } from '@/services/config'
import { globalSearchApi } from '@/services/globalSearch'
import { todoApi } from '@/services/todo'
import { useSessionStore } from '@/stores/session'
import type { RuntimeNavigation, RuntimeModule } from '@/types/config'
import type { RuntimeGlobalSearchItem } from '@/types/globalSearch'
import type { TodoItem } from '@/types/todo'

type CommandKind = 'MENU' | 'SYSTEM' | 'MODULE' | 'TODO' | 'RECORD' | 'ACTION'

interface CommandResult {
  id: string
  kind: CommandKind
  title: string
  description: string
  keywords: string
  path: string | { path: string; query?: Record<string, string> }
}

interface MenuDefinition {
  id: string
  title: string
  description: string
  path: string
  permission?: string
  anyPermissions?: string[]
  allPermissions?: string[]
  admin?: boolean
}

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()
const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId || session.context?.systemId || ''))
const keyword = ref('')
const searchInput = ref<HTMLInputElement | null>(null)
const loading = ref(false)
const catalogLoading = ref(false)
const error = ref('')
const catalogError = ref('')
const navigation = ref<RuntimeNavigation | null>(null)
const todos = ref<TodoItem[]>([])
const records = ref<RuntimeGlobalSearchItem[]>([])
const searched = ref(false)
let loadGeneration = 0
let searchGeneration = 0

const menuDefinitions: MenuDefinition[] = [
  { id: 'workbench', title: '业务工作台', description: '模块导航和业务数据', path: 'workbench' },
  { id: 'dashboard', title: '系统仪表盘', description: '配置化统计和趋势', path: 'dashboard' },
  { id: 'kpis', title: '我的 KPI', description: '目标、达成率和预警', path: 'kpis' },
  { id: 'reports', title: '报表中心', description: '运行报表和导出', path: 'reports' },
  { id: 'operations', title: '运营看板', description: 'Work、Flow 和待办活动', path: 'operations' },
  { id: 'todos', title: '待办中心', description: '审批、任务、提醒和抄送', path: 'todos' },
  { id: 'tasks', title: '任务与日报', description: '项目、任务、日历和日报', path: 'tasks', anyPermissions: ['work.task.access', 'work.report.access', 'work.report.create', 'work.report.manage'] },
  { id: 'messages', title: '消息中心', description: '业务消息和投递偏好', path: 'messages', permission: 'event.message.access' },
  { id: 'files', title: '文件中心', description: '上传、预览和引用', path: 'files', anyPermissions: ['file.create', 'file.read', 'file.reference', 'file.manage'] },
  { id: 'flows', title: 'Flow 审批', description: '流程、实例和审批任务', path: 'flows', anyPermissions: ['flow.definition.manage', 'flow.instance.start', 'flow.instance.decide', 'flow.instance.read', 'flow.external-task.work'] },
  { id: 'agent', title: '系统 Agent', description: '授权查询和确认写入', path: 'agent', permission: 'ai.agent.use' },
  { id: 'admin-onboarding', title: '系统上线引导', description: '最低配置、成员预览和发布检查', path: 'admin/onboarding', admin: true, allPermissions: ['system.admin.access', 'module.config.manage', 'system.member.manage'] },
]

const visibleMenus = computed(() => menuDefinitions.filter(item => {
  if (item.admin && !session.hasShell('SYSTEM_ADMIN')) return false
  if (item.permission && !session.hasPermission(item.permission)) return false
  if (item.allPermissions?.some(permission => !session.hasPermission(permission))) return false
  if (item.anyPermissions?.length && !item.anyPermissions.some(session.hasPermission)) return false
  return true
}))

const modules = computed(() => navigation.value?.groups.flatMap(group => group.modules.map(module => ({
  ...module,
  groupName: group.name,
}))) ?? [])

const normalizedKeyword = computed(() => keyword.value.trim().toLocaleLowerCase())

const results = computed<CommandResult[]>(() => {
  const q = normalizedKeyword.value
  const matches = (value: string) => !q || value.toLocaleLowerCase().includes(q)
  const items: CommandResult[] = []

  visibleMenus.value.filter(item => matches(`${item.title} ${item.description}`)).forEach(item => items.push({
    id: `menu:${item.id}`,
    kind: 'MENU',
    title: item.title,
    description: item.description,
    keywords: `${item.title} ${item.description}`,
    path: `/systems/${systemId.value}/${item.path}`,
  }))

  session.systems.filter(system => system.status === 'ACTIVE' && system.memberStatus === 'ACTIVE'
    && matches(`${system.name} ${system.code}`)).forEach(system => items.push({
    id: `system:${system.id}`,
    kind: 'SYSTEM',
    title: system.name,
    description: `切换系统 · ${system.code}`,
    keywords: `${system.name} ${system.code}`,
    path: `/systems/${system.id}/workbench`,
  }))

  modules.value.filter(module => matches(`${module.name} ${module.code} ${module.groupName}`))
    .forEach(module => items.push(moduleResult(module)))

  todos.value.filter(todo => matches(`${todo.title} ${todo.category} ${todo.sourceType}`))
    .forEach(todo => items.push({
      id: `todo:${todo.id}`,
      kind: 'TODO',
      title: todo.title,
      description: `${todo.category} · ${todo.dueAt ? `截止 ${todo.dueAt}` : '无截止时间'}`,
      keywords: `${todo.title} ${todo.category} ${todo.sourceType}`,
      path: todo.routeHint || `/systems/${systemId.value}/todos`,
    }))

  records.value.forEach(record => items.push({
    id: `record:${record.moduleCode}:${record.recordId}`,
    kind: 'RECORD',
    title: record.displayLabel,
    description: `${record.moduleName} · ${record.recordNo} · ${record.status}`,
    keywords: `${record.displayLabel} ${record.moduleName} ${record.recordNo}`,
    path: {
      path: `/systems/${systemId.value}/workbench`,
      query: { module: record.moduleCode, mode: 'view', record: record.recordId },
    },
  }))

  modules.value.filter(module => session.hasPermission(`module.${module.code}.create`)
    && matches(`新建 创建 ${module.name} ${module.code}`)).forEach(module => items.push({
      id: `action:create:${module.code}`,
      kind: 'ACTION',
      title: `新建${module.name}`,
      description: `快捷进入 ${module.groupName} / ${module.name} 的真实新建表单`,
      keywords: `新建 创建 ${module.name} ${module.code}`,
      path: {
        path: `/systems/${systemId.value}/workbench`,
        query: { module: module.code, mode: 'create' },
      },
    }))

  return items
})

const groupedResults = computed(() => {
  const order: CommandKind[] = ['ACTION', 'MENU', 'SYSTEM', 'MODULE', 'TODO', 'RECORD']
  return order.map(kind => ({ kind, items: results.value.filter(item => item.kind === kind) }))
    .filter(group => group.items.length)
})

function moduleResult(module: RuntimeModule & { groupName: string }): CommandResult {
  return {
    id: `module:${module.code}`,
    kind: 'MODULE',
    title: module.name,
    description: `${module.groupName} · ${module.code}`,
    keywords: `${module.name} ${module.code} ${module.groupName}`,
    path: { path: `/systems/${systemId.value}/workbench`, query: { module: module.code } },
  }
}

function categoryLabel(kind: CommandKind) {
  return ({ ACTION: '快捷动作', MENU: '菜单', SYSTEM: '系统', MODULE: '模块', TODO: '待办', RECORD: '记录' })[kind]
}

function categoryIcon(kind: CommandKind) {
  return ({ ACTION: Zap, MENU: Menu, SYSTEM: Server, MODULE: Blocks, TODO: CheckSquare, RECORD: LayoutGrid })[kind]
}

async function loadCatalog() {
  const generation = ++loadGeneration
  catalogLoading.value = true
  catalogError.value = ''
  const [navigationResult, todoResult] = await Promise.allSettled([
    runtimeApi.navigation(systemId.value),
    todoApi.list(systemId.value, { state: 'OPEN', page: 1, size: 100 }),
  ])
  if (generation !== loadGeneration) return
  if (navigationResult.status === 'fulfilled') navigation.value = navigationResult.value
  else navigation.value = null
  if (todoResult.status === 'fulfilled') todos.value = todoResult.value.items
  else todos.value = []
  const failed = [navigationResult, todoResult].filter(result => result.status === 'rejected').length
  catalogError.value = failed ? `${failed} 个目录暂时不可用，其他命令仍可使用。` : ''
  catalogLoading.value = false
}

async function runSearch() {
  const q = keyword.value.trim()
  searched.value = true
  error.value = ''
  records.value = []
  if (!q) return
  if (q.length < 2) {
    error.value = '请输入至少 2 个字符；菜单、系统、模块和待办仍会本地筛选。'
    return
  }
  const generation = ++searchGeneration
  loading.value = true
  try {
    const page = await globalSearchApi.search(systemId.value, { q, page: 1, size: 20 })
    if (generation === searchGeneration) records.value = page.items
  } catch (cause) {
    if (generation === searchGeneration) {
      error.value = cause instanceof Error ? cause.message : '业务记录搜索暂时不可用'
    }
  } finally {
    if (generation === searchGeneration) loading.value = false
  }
}

async function execute(item: CommandResult) {
  emit('update:open', false)
  await router.push(item.path)
}

function close() {
  emit('update:open', false)
}

watch(() => props.open, async open => {
  if (!open) return
  keyword.value = ''
  records.value = []
  searched.value = false
  error.value = ''
  void loadCatalog()
  await nextTick()
  window.setTimeout(() => searchInput.value?.focus(), 0)
}, { immediate: true })
</script>

<template>
  <a-modal :open="open" width="820px" :footer="null" :closable="false" @cancel="close">
    <section class="system-command-center" aria-label="统一命令中心">
      <header>
        <div><Search :size="21" /><strong>统一命令中心</strong><kbd>Ctrl / ⌘ K</kbd></div>
        <button type="button" aria-label="关闭命令中心" @click="close">Esc</button>
      </header>
      <form class="command-search" @submit.prevent="runSearch">
        <Search :size="18" />
        <input ref="searchInput" v-model="keyword" autofocus aria-label="搜索菜单、系统、模块、待办和记录" placeholder="搜索菜单、系统、模块、待办和业务记录…">
        <button type="submit" :disabled="loading">{{ loading ? '搜索中…' : '搜索' }}</button>
      </form>
      <a-alert v-if="catalogError" class="command-catalog-warning" type="warning" show-icon :message="catalogError" />
      <a-alert v-if="error" class="command-search-error" type="warning" show-icon :message="error" />
      <div v-if="catalogLoading && !groupedResults.length" class="command-loading">正在读取当前权限目录…</div>
      <div v-else-if="!groupedResults.length" class="command-empty">{{ searched ? '没有匹配结果' : '当前没有可用命令' }}</div>
      <div v-else class="command-results">
        <section v-for="group in groupedResults" :key="group.kind" :data-kind="group.kind">
          <h2><component :is="categoryIcon(group.kind)" :size="15" />{{ categoryLabel(group.kind) }}<span>{{ group.items.length }}</span></h2>
          <button v-for="item in group.items" :key="item.id" class="command-result" type="button" @click="execute(item)">
            <component :is="item.kind === 'ACTION' ? FilePlus2 : categoryIcon(item.kind)" :size="17" />
            <span><strong>{{ item.title }}</strong><small>{{ item.description }}</small></span>
            <kbd>Enter</kbd>
          </button>
        </section>
      </div>
    </section>
  </a-modal>
</template>

<style scoped>
.system-command-center{display:grid;gap:12px;max-height:min(78vh,760px)}.system-command-center>header{display:flex;align-items:center;justify-content:space-between;gap:16px}.system-command-center>header>div{display:flex;align-items:center;gap:9px}.system-command-center>header strong{font-size:17px}.system-command-center kbd{padding:2px 6px;border:1px solid #d8e0e4;border-radius:4px;background:#f5f7f8;color:#65737c;font:11px ui-monospace,monospace}.system-command-center>header>button{border:0;background:transparent;color:#70808a;cursor:pointer}.command-search{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:9px;padding:9px 10px;border:1px solid #93b4c4;border-radius:8px;box-shadow:0 0 0 3px rgba(55,116,145,.08)}.command-search input{min-width:0;border:0;outline:0;font:inherit}.command-search button{border:0;border-radius:5px;padding:6px 12px;background:#176b78;color:#fff;cursor:pointer}.command-search button:disabled{opacity:.65}.command-results{min-height:0;overflow:auto;display:grid;gap:14px;padding-right:3px}.command-results section{display:grid;gap:5px}.command-results h2{display:flex;align-items:center;gap:6px;margin:0;padding:5px 4px;color:#64737c;font-size:12px;text-transform:uppercase}.command-results h2 span{margin-left:auto}.command-result{display:grid;grid-template-columns:24px minmax(0,1fr) auto;align-items:center;gap:9px;width:100%;padding:9px 10px;border:0;border-radius:7px;background:#fff;text-align:left;color:#2d3e47;cursor:pointer}.command-result:hover,.command-result:focus-visible{background:#eef6f7;outline:0}.command-result>span{display:grid;gap:2px;min-width:0}.command-result strong,.command-result small{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.command-result small{color:#718089}.command-result>kbd{opacity:0}.command-result:hover>kbd,.command-result:focus-visible>kbd{opacity:1}.command-loading,.command-empty{min-height:180px;display:grid;place-items:center;color:#76858e}.command-catalog-warning,.command-search-error{margin:0}@media(max-width:640px){.system-command-center{max-height:82vh}.command-result>kbd{display:none}.command-search{grid-template-columns:auto minmax(0,1fr)}.command-search button{grid-column:1/-1}.command-result{grid-template-columns:22px minmax(0,1fr)}}
.system-command-center>header>button{color:#596873}
.command-result small{color:#596873}
</style>
