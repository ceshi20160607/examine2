<script setup lang="ts">
import { CalendarDays, CheckCircle2, Columns3, FileText, FolderKanban, List, Plus, RefreshCw } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'

import { ApiRequestError } from '@/services/api'
import { platformTaskApi } from '@/services/platformTask'
import { platformWorkApi } from '@/services/platformWork'
import { useSessionStore } from '@/stores/session'
import type { PlatformDailyReport, PlatformWorkOverview, PlatformWorkProject, PlatformWorkTask, PlatformWorkTaskKind } from '@/types/platformWork'

type Section = 'PROJECTS' | 'TASKS' | 'REPORTS'
type TaskView = 'LIST' | 'BOARD' | 'CALENDAR'
const session = useSessionStore()
const canManage = computed(() => session.hasPermission('platform.work.manage'))
const canTransition = computed(() => session.hasPermission('platform.task.manage'))
const section = ref<Section>('TASKS')
const taskView = ref<TaskView>('LIST')
const taskKind = ref<'ALL' | PlatformWorkTaskKind>('ALL')
const overview = ref<PlatformWorkOverview>({ activeProjects: 0, openTasks: 0, overdueTasks: 0, todayTasks: 0, todayReportSubmitted: false })
const projects = ref<PlatformWorkProject[]>([])
const tasks = ref<PlatformWorkTask[]>([])
const reports = ref<PlatformDailyReport[]>([])
const loading = ref(false)
const mutation = ref('')
const error = ref('')
const projectOpen = ref(false)
const taskOpen = ref(false)
const reportOpen = ref(false)
const projectForm = reactive({ code: '', name: '', startDate: '', dueDate: '' })
const taskForm = reactive({ kind: 'GENERAL' as PlatformWorkTaskKind, projectId: '', title: '', description: '', dueAt: '', priority: 'NORMAL' as PlatformWorkTask['priority'], labels: '' })
const reportForm = reactive({ reportDate: new Date().toISOString().slice(0, 10), completed: '', plan: '', risks: '', projectId: '' })

const visibleTasks = computed(() => taskKind.value === 'ALL' ? tasks.value : tasks.value.filter(item => item.kind === taskKind.value))
const board = computed(() => ['OPEN', 'COMPLETED', 'CANCELLED'].map(status => ({ status, items: visibleTasks.value.filter(item => item.status === status) })))
const calendar = computed(() => {
  const groups = new Map<string, PlatformWorkTask[]>()
  visibleTasks.value.forEach(item => {
    const day = item.dueAt?.slice(0, 10) ?? '未设置日期'
    groups.set(day, [...(groups.get(day) ?? []), item])
  })
  return [...groups.entries()].sort(([left], [right]) => left.localeCompare(right))
})

function message(cause: unknown) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code
  return cause instanceof Error ? cause.message : '平台工作请求失败'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [summary, projectRows, taskPage, reportRows] = await Promise.all([
      platformWorkApi.overview(), platformWorkApi.projects(), platformWorkApi.tasks({ page: 1, size: 100 }), platformWorkApi.reports(),
    ])
    overview.value = summary
    projects.value = projectRows
    tasks.value = taskPage.items
    reports.value = reportRows
  } catch (cause) { error.value = message(cause) } finally { loading.value = false }
}

async function createProject() {
  mutation.value = 'project'; error.value = ''
  try {
    await platformWorkApi.createProject({ code: projectForm.code, name: projectForm.name, startDate: projectForm.startDate || null, dueDate: projectForm.dueDate || null })
    projectOpen.value = false; Object.assign(projectForm, { code: '', name: '', startDate: '', dueDate: '' }); await load()
  } catch (cause) { error.value = message(cause) } finally { mutation.value = '' }
}

async function createTask() {
  mutation.value = 'task'; error.value = ''
  try {
    await platformWorkApi.createTask({ kind: taskForm.kind, projectId: taskForm.kind === 'PROJECT' ? taskForm.projectId : null, title: taskForm.title, description: taskForm.description || null, dueAt: taskForm.dueAt ? new Date(taskForm.dueAt).toISOString() : null, priority: taskForm.priority, labels: taskForm.labels.split(',').map(value => value.trim()).filter(Boolean) })
    taskOpen.value = false; Object.assign(taskForm, { kind: 'GENERAL', projectId: '', title: '', description: '', dueAt: '', priority: 'NORMAL', labels: '' }); await load()
  } catch (cause) { error.value = message(cause) } finally { mutation.value = '' }
}

async function saveReport() {
  mutation.value = 'report'; error.value = ''
  try {
    await platformWorkApi.saveReport({ ...reportForm, risks: reportForm.risks || null, projectId: reportForm.projectId || null })
    reportOpen.value = false; Object.assign(reportForm, { completed: '', plan: '', risks: '', projectId: '' }); await load()
  } catch (cause) { error.value = message(cause) } finally { mutation.value = '' }
}

async function transition(task: PlatformWorkTask, action: 'complete' | 'reopen') {
  if (!canTransition.value) return
  mutation.value = `${action}:${task.taskId}`; error.value = ''
  try { await platformTaskApi[action](task.taskId, { version: task.version }); await load() }
  catch (cause) { error.value = message(cause) } finally { mutation.value = '' }
}

async function submitReport(report: PlatformDailyReport) {
  mutation.value = `submit:${report.id}`; error.value = ''
  try { await platformWorkApi.submitReport(report.id, report.version); await load() }
  catch (cause) { error.value = message(cause) } finally { mutation.value = '' }
}

function format(value: string | null) { return value ? new Date(value).toLocaleString('zh-CN') : '未设置' }
onMounted(load)
</script>

<template>
  <section class="workspace platform-work-page" :aria-busy="loading">
    <header class="workspace-heading"><div><h1><FolderKanban :size="23" />平台工作</h1><p>项目、项目任务、普通任务与我的日报使用同一平台账户边界。</p></div><a-button :loading="loading" @click="load"><RefreshCw :size="16" />刷新</a-button></header>
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <div class="work-overview"><article><span>进行中项目</span><strong>{{ overview.activeProjects }}</strong></article><article><span>待办任务</span><strong>{{ overview.openTasks }}</strong></article><article><span>逾期风险</span><strong>{{ overview.overdueTasks }}</strong></article><article><span>今日任务</span><strong>{{ overview.todayTasks }}</strong></article><article><span>今日日报</span><strong>{{ overview.todayReportSubmitted ? '已提交' : '待提交' }}</strong></article></div>
    <nav class="work-sections" aria-label="平台工作分类"><button :class="{ active: section === 'PROJECTS' }" @click="section = 'PROJECTS'">项目</button><button :class="{ active: section === 'TASKS' }" @click="section = 'TASKS'">项目任务 / 普通任务</button><button :class="{ active: section === 'REPORTS' }" @click="section = 'REPORTS'">日报</button></nav>

    <section v-if="section === 'PROJECTS'" class="work-panel"><header><div><h2>项目列表</h2><span>项目进度来自同一批真实任务。</span></div><a-button v-if="canManage" class="platform-project-create" type="primary" @click="projectOpen = true"><Plus :size="15" />新建项目</a-button></header><table><thead><tr><th>项目</th><th>状态</th><th>周期</th><th>任务进度</th></tr></thead><tbody><tr v-for="project in projects" :key="project.id"><td><strong>{{ project.name }}</strong><small>{{ project.code }}</small></td><td>{{ project.status }}</td><td>{{ project.startDate || '—' }} — {{ project.dueDate || '—' }}</td><td>{{ project.completedTaskCount }} / {{ project.taskCount }}</td></tr></tbody></table><a-empty v-if="!projects.length && !loading" description="暂无项目" /></section>

    <section v-else-if="section === 'TASKS'" class="work-panel"><header><div><h2>任务</h2><span>列表、看板、日历是同一任务集合的三种视图。</span></div><div class="work-tools"><select v-model="taskKind"><option value="ALL">全部任务</option><option value="PROJECT">项目任务</option><option value="GENERAL">普通任务</option></select><button class="view-mode" :class="{ active: taskView === 'LIST' }" @click="taskView = 'LIST'"><List :size="15" />列表</button><button class="view-mode" :class="{ active: taskView === 'BOARD' }" @click="taskView = 'BOARD'"><Columns3 :size="15" />看板</button><button class="view-mode" :class="{ active: taskView === 'CALENDAR' }" @click="taskView = 'CALENDAR'"><CalendarDays :size="15" />日历</button><a-button v-if="canManage" class="platform-work-task-create" type="primary" @click="taskOpen = true"><Plus :size="15" />新建任务</a-button></div></header>
      <table v-if="taskView === 'LIST'"><thead><tr><th>任务</th><th>类型 / 项目</th><th>状态</th><th>截止</th><th>操作</th></tr></thead><tbody><tr v-for="task in visibleTasks" :key="task.taskId"><td><strong>{{ task.title }}</strong><small>{{ task.labels.join(' · ') || '无标签' }}</small></td><td>{{ task.kind }}<small>{{ task.projectName || '不关联项目' }}</small></td><td>{{ task.status }}</td><td>{{ format(task.dueAt) }}</td><td><a-button v-if="canTransition && task.status === 'OPEN'" size="small" @click="transition(task, 'complete')">完成</a-button><a-button v-if="canTransition && task.status !== 'OPEN'" size="small" @click="transition(task, 'reopen')">重开</a-button></td></tr></tbody></table>
      <div v-else-if="taskView === 'BOARD'" class="task-board"><section v-for="column in board" :key="column.status"><h3>{{ column.status }} <span>{{ column.items.length }}</span></h3><article v-for="task in column.items" :key="task.taskId"><strong>{{ task.title }}</strong><small>{{ task.projectName || '普通任务' }}</small><time>{{ format(task.dueAt) }}</time></article></section></div>
      <div v-else class="task-calendar"><section v-for="[day, dayTasks] in calendar" :key="day"><h3>{{ day }}</h3><article v-for="task in dayTasks" :key="task.taskId"><span>{{ task.title }}</span><a-tag>{{ task.status }}</a-tag></article></section></div>
      <a-empty v-if="!visibleTasks.length && !loading" description="暂无任务" />
    </section>

    <section v-else class="work-panel"><header><div><h2>我的日报</h2><span>草稿需要人工提交，不会自动发布。</span></div><a-button v-if="canManage" class="platform-report-create" type="primary" @click="reportOpen = true"><FileText :size="15" />写日报</a-button></header><table><thead><tr><th>日期</th><th>状态</th><th>今日完成</th><th>明日计划</th><th>风险</th><th>操作</th></tr></thead><tbody><tr v-for="report in reports" :key="report.id"><td>{{ report.reportDate }}</td><td>{{ report.status }}</td><td>{{ report.completed }}</td><td>{{ report.plan }}</td><td>{{ report.risks || '无' }}</td><td><a-button v-if="canManage && report.status === 'DRAFT'" size="small" :loading="mutation === `submit:${report.id}`" @click="submitReport(report)"><CheckCircle2 :size="15" />提交</a-button></td></tr></tbody></table><a-empty v-if="!reports.length && !loading" description="暂无日报" /></section>

    <a-modal v-model:open="projectOpen" title="新建平台项目" :confirm-loading="mutation === 'project'" @ok="createProject"><div class="work-form"><label>项目编码<input v-model="projectForm.code" placeholder="RELEASE_2026" /></label><label>项目名称<input v-model="projectForm.name" /></label><label>开始日期<input v-model="projectForm.startDate" type="date" /></label><label>截止日期<input v-model="projectForm.dueDate" type="date" /></label></div></a-modal>
    <a-modal v-model:open="taskOpen" title="新建平台任务" :confirm-loading="mutation === 'task'" @ok="createTask"><div class="work-form"><label>任务类型<select v-model="taskForm.kind"><option value="GENERAL">普通任务</option><option value="PROJECT">项目任务</option></select></label><label v-if="taskForm.kind === 'PROJECT'">归属项目<select v-model="taskForm.projectId"><option value="">请选择</option><option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option></select></label><label>标题<input v-model="taskForm.title" /></label><label>描述<textarea v-model="taskForm.description" /></label><label>截止时间<input v-model="taskForm.dueAt" type="datetime-local" /></label><label>优先级<select v-model="taskForm.priority"><option>LOW</option><option>NORMAL</option><option>HIGH</option><option>URGENT</option></select></label><label>标签（逗号分隔）<input v-model="taskForm.labels" /></label></div></a-modal>
    <a-modal v-model:open="reportOpen" title="写日报草稿" :confirm-loading="mutation === 'report'" @ok="saveReport"><div class="work-form"><label>日期<input v-model="reportForm.reportDate" type="date" /></label><label>今日完成<textarea v-model="reportForm.completed" /></label><label>明日计划<textarea v-model="reportForm.plan" /></label><label>问题风险<textarea v-model="reportForm.risks" /></label><label>关联项目<select v-model="reportForm.projectId"><option value="">不关联</option><option v-for="project in projects" :key="project.id" :value="project.id">{{ project.name }}</option></select></label></div></a-modal>
  </section>
</template>

<style scoped>
.platform-work-page{display:grid;gap:16px}.workspace-heading h1{display:flex;align-items:center;gap:8px}.work-overview{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:10px}.work-overview article{display:grid;gap:6px;padding:14px;border:1px solid #dce3e7;border-radius:8px;background:#fff}.work-overview span,.work-panel header span{color:#6b7780}.work-overview strong{font-size:22px}.work-sections,.work-tools{display:flex;align-items:center;gap:7px}.work-sections{border-bottom:1px solid #dce3e7}.work-sections button,.work-tools>.view-mode{display:inline-flex;align-items:center;gap:5px;padding:9px 12px;border:0;border-bottom:2px solid transparent;background:transparent;cursor:pointer}.work-sections button.active,.work-tools>.view-mode.active{border-color:#0f766e;color:#0f766e}.work-panel{display:grid;gap:12px}.work-panel>header{display:flex;align-items:center;justify-content:space-between;gap:14px}.work-panel h2{margin:0}.work-panel table{width:100%;border-collapse:collapse;background:#fff}.work-panel th,.work-panel td{padding:11px;border:1px solid #e1e6e9;text-align:left;vertical-align:top}.work-panel th{background:#f5f7f8;color:#5f6c75}.work-panel td:first-child{display:grid;gap:3px}.work-panel td small{display:block;color:#74818a}.task-board{display:grid;grid-template-columns:repeat(3,minmax(240px,1fr));gap:12px;overflow:auto}.task-board>section{display:grid;align-content:start;gap:8px;padding:10px;border-radius:8px;background:#eef2f3}.task-board h3{display:flex;justify-content:space-between;margin:0}.task-board article,.task-calendar article{display:grid;gap:5px;padding:11px;border:1px solid #dce4e6;border-radius:6px;background:#fff}.task-board small,.task-board time{color:#74818a}.task-calendar{display:grid;gap:10px}.task-calendar section{display:grid;grid-template-columns:130px repeat(auto-fill,minmax(220px,1fr));gap:8px;align-items:start}.task-calendar h3{margin:10px 0}.task-calendar article{grid-template-columns:1fr auto;align-items:center}.work-form{display:grid;gap:12px}.work-form label{display:grid;gap:5px;color:#56636c}.work-form input,.work-form select,.work-form textarea,.work-tools select{padding:8px;border:1px solid #cbd5d9;border-radius:5px}.work-form textarea{min-height:76px;resize:vertical}@media(max-width:860px){.work-overview{grid-template-columns:repeat(2,minmax(0,1fr))}.work-panel{overflow-x:auto}.work-panel>header{align-items:flex-start;flex-direction:column;position:sticky;left:0}.work-panel table{min-width:680px}.work-tools{flex-wrap:wrap}.task-board{grid-template-columns:repeat(3,280px)}}
</style>
