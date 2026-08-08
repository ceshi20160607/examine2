<script setup lang="ts">
import { ArrowRight, Building2, CheckCircle2, CircleAlert, RefreshCw, RotateCcw, XCircle } from 'lucide-vue-next'
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { platformTaskApi } from '@/services/platformTask'
import { useSessionStore } from '@/stores/session'
import type { PlatformTask, PlatformTaskStatusFilter } from '@/types/platformTask'

const session = useSessionStore()
const router = useRouter()
const activeSystems = computed(() =>
  session.systems.filter(
    (system) => ['INITIALIZING', 'ACTIVE'].includes(system.status) && system.memberStatus === 'ACTIVE',
  ),
)
const canReadTasks = computed(() => session.hasPermission('platform.task.read'))
const canManageTasks = computed(() => session.hasPermission('platform.task.manage'))
const taskStatus = ref<PlatformTaskStatusFilter>('ALL')
const taskPage = ref(1)
const taskPageSize = ref(20)
const taskTotal = ref(0)
const tasks = ref<PlatformTask[]>([])
const tasksLoading = ref(false)
const tasksError = ref('')
const taskAction = ref('')
let taskRequestSequence = 0

const taskPageCount = computed(() => Math.max(1, Math.ceil(taskTotal.value / taskPageSize.value)))

onMounted(() => session.loadSystems())

watch([canReadTasks, taskStatus, taskPage], () => loadTasks(), { immediate: true })

async function enterSystem(systemId: string) {
  await session.switchSystem(systemId)
  await router.push(`/systems/${systemId}/workbench`)
}

async function loadTasks() {
  const requestSequence = ++taskRequestSequence
  if (!canReadTasks.value) {
    tasks.value = []
    taskTotal.value = 0
    tasksError.value = ''
    tasksLoading.value = false
    return
  }
  tasksLoading.value = true
  tasksError.value = ''
  try {
    const result = await platformTaskApi.list({
      status: taskStatus.value,
      page: taskPage.value,
      size: taskPageSize.value,
    })
    if (requestSequence !== taskRequestSequence) return
    tasks.value = result.items
    taskPage.value = result.page
    taskPageSize.value = result.size
    taskTotal.value = result.total
  } catch (error) {
    if (requestSequence !== taskRequestSequence) return
    tasksError.value = errorMessage(error)
  } finally {
    if (requestSequence === taskRequestSequence) tasksLoading.value = false
  }
}

function changeStatus(event: Event) {
  taskPage.value = 1
  taskStatus.value = (event.target as HTMLSelectElement).value as PlatformTaskStatusFilter
}

function previousTaskPage() {
  if (taskPage.value > 1) taskPage.value -= 1
}

function nextTaskPage() {
  if (taskPage.value < taskPageCount.value) taskPage.value += 1
}

async function changeTask(task: PlatformTask, action: 'complete' | 'reopen' | 'cancel') {
  if (!canManageTasks.value || taskAction.value) return
  taskAction.value = `${task.taskId}:${action}`
  tasksError.value = ''
  try {
    await platformTaskApi[action](task.taskId, { version: task.version })
    await loadTasks()
  } catch (error) {
    tasksError.value = errorMessage(error)
  } finally {
    taskAction.value = ''
  }
}

function errorMessage(error: unknown) {
  if (error instanceof ApiRequestError) {
    return error.code === error.message ? error.code : `${error.code}：${error.message}`
  }
  return error instanceof Error ? error.message : '个人任务请求失败'
}

function formatTime(value: string | null) {
  if (!value) return '未设置'
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
</script>

<template>
  <section class="workspace">
    <header class="workspace-heading">
      <div><h1>平台工作台</h1><p>选择一个系统继续工作</p></div>
      <a-button aria-label="刷新系统列表" :loading="session.loading" :disabled="session.loading" @click="session.loadSystems"><RefreshCw :size="16" />刷新</a-button>
    </header>
    <a-alert v-if="!activeSystems.length" type="info" show-icon>
      <template #icon><CircleAlert :size="18" /></template>
      <template #message>当前没有可进入的系统</template>
      <template #description>系统被停用或成员关系失效时，不会建立业务上下文。</template>
    </a-alert>
    <div v-else class="system-list">
      <button v-for="system in activeSystems" :key="system.id" class="system-row" type="button" @click="enterSystem(system.id)">
        <span class="system-icon"><Building2 :size="20" /></span>
        <span class="system-copy"><strong>{{ system.name }}</strong><small>{{ system.code }}</small></span>
        <span class="status-text">可进入</span><ArrowRight :size="18" />
      </button>
    </div>

    <section v-if="canReadTasks" class="platform-personal-tasks" aria-labelledby="platform-task-title" :aria-busy="tasksLoading">
      <header class="platform-task-heading">
        <div><h2 id="platform-task-title">个人任务</h2><p>查看并处理当前账户的平台任务</p></div>
        <div class="platform-task-tools">
          <label for="platform-task-status">状态</label>
          <select id="platform-task-status" class="platform-task-status-filter" :value="taskStatus" @change="changeStatus">
            <option value="ALL">全部</option>
            <option value="OPEN">进行中</option>
            <option value="COMPLETED">已完成</option>
            <option value="CANCELLED">已取消</option>
          </select>
          <a-button class="platform-task-refresh" aria-label="刷新个人任务" :loading="tasksLoading" :disabled="tasksLoading" @click="loadTasks">
            <RefreshCw :size="16" />刷新任务
          </a-button>
        </div>
      </header>

      <a-alert v-if="tasksError" class="platform-task-error" type="error" show-icon role="alert" aria-live="assertive" :message="tasksError" />
      <div v-if="tasksLoading" class="platform-task-loading"><a-spin />正在加载个人任务…</div>
      <a-empty v-else-if="!tasks.length" class="platform-task-empty" description="当前筛选条件下没有个人任务" />
      <div v-else class="platform-task-list">
        <article v-for="task in tasks" :key="task.taskId" class="platform-task-card">
          <header>
            <div><h3>{{ task.title }}</h3><small>{{ task.taskId }}</small></div>
            <a-tag>{{ task.status }}</a-tag>
          </header>
          <p v-if="task.description" class="platform-task-description">{{ task.description }}</p>
          <p v-else class="platform-task-description muted">无任务描述</p>
          <dl class="platform-task-metadata">
            <div><dt>来源</dt><dd>{{ task.source }}</dd></div>
            <div><dt>优先级</dt><dd>{{ task.priority }}</dd></div>
            <div><dt>截止时间</dt><dd>{{ formatTime(task.dueAt) }}</dd></div>
            <div><dt>更新时间</dt><dd>{{ formatTime(task.updatedAt) }}</dd></div>
          </dl>
          <footer v-if="canManageTasks" class="platform-task-actions">
            <a-button
              v-if="task.status === 'OPEN'"
              class="platform-task-complete"
              :disabled="Boolean(taskAction)"
              :loading="taskAction === `${task.taskId}:complete`"
              @click="changeTask(task, 'complete')"
            ><CheckCircle2 :size="16" />完成</a-button>
            <a-button
              v-if="task.status === 'OPEN'"
              class="platform-task-cancel"
              danger
              :disabled="Boolean(taskAction)"
              :loading="taskAction === `${task.taskId}:cancel`"
              @click="changeTask(task, 'cancel')"
            ><XCircle :size="16" />取消</a-button>
            <a-button
              v-if="task.status === 'COMPLETED' || task.status === 'CANCELLED'"
              class="platform-task-reopen"
              :disabled="Boolean(taskAction)"
              :loading="taskAction === `${task.taskId}:reopen`"
              @click="changeTask(task, 'reopen')"
            ><RotateCcw :size="16" />重新打开</a-button>
          </footer>
        </article>
      </div>

      <footer v-if="taskTotal > taskPageSize" class="platform-task-pagination">
        <a-button class="platform-task-previous" :disabled="taskPage <= 1 || tasksLoading" @click="previousTaskPage">上一页</a-button>
        <span>第 {{ taskPage }} / {{ taskPageCount }} 页，共 {{ taskTotal }} 条</span>
        <a-button class="platform-task-next" :disabled="taskPage >= taskPageCount || tasksLoading" @click="nextTaskPage">下一页</a-button>
      </footer>
    </section>
  </section>
</template>

<style scoped>
.platform-personal-tasks{display:grid;gap:14px;margin-top:24px;padding-top:20px;border-top:1px solid #d9dfe5}.platform-task-heading,.platform-task-tools,.platform-task-card>header,.platform-task-actions,.platform-task-pagination{display:flex;align-items:center;gap:10px}.platform-task-heading{justify-content:space-between}.platform-task-heading h2,.platform-task-heading p,.platform-task-card h3{margin:0}.platform-task-heading p{margin-top:4px;color:#596576}.platform-task-tools label{color:#596576;font-size:13px}.platform-task-status-filter{min-width:110px;padding:7px 9px;border:1px solid #cbd4dc;border-radius:6px;background:#fff}.platform-task-loading{display:flex;align-items:center;justify-content:center;gap:9px;min-height:120px;color:#596576}.platform-task-list{display:grid;gap:10px}.platform-task-card{display:grid;gap:12px;padding:15px;border:1px solid #d9e0e5;border-radius:7px;background:#fff}.platform-task-card>header{justify-content:space-between}.platform-task-card>header>div{display:grid;gap:3px}.platform-task-card small,.platform-task-description.muted{color:#616d79}.platform-task-description{margin:0;white-space:pre-wrap}.platform-task-metadata{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:10px;margin:0}.platform-task-metadata>div{display:grid;gap:3px}.platform-task-metadata dt{color:#596576;font-size:12px}.platform-task-metadata dd{margin:0}.platform-task-actions{justify-content:flex-end;padding-top:2px}.platform-task-pagination{justify-content:flex-end}.platform-task-pagination span{color:#596576;font-size:13px}
@media(max-width:720px){.platform-task-heading{align-items:flex-start;flex-direction:column}.platform-task-tools{width:100%;flex-wrap:wrap}.platform-task-status-filter{min-width:0;flex:1}.platform-task-metadata{grid-template-columns:repeat(2,minmax(0,1fr))}.platform-task-actions,.platform-task-pagination{justify-content:flex-start;flex-wrap:wrap}.platform-task-pagination span{width:100%;order:-1}.platform-task-card{min-width:0}.platform-task-description,.platform-task-metadata dd{overflow-wrap:anywhere}}
@media(max-width:400px){.platform-task-metadata{grid-template-columns:minmax(0,1fr)}}
</style>
