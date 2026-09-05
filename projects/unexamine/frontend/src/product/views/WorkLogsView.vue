<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Empty, message } from 'ant-design-vue'
import type { Dayjs } from 'dayjs'
import { ApiError, api } from '../api'
import { allowsPermission } from '../permissions'
import { productDateTime } from '../presentation'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { canEditLog, revisionDifference, sortWorkLogs } from '../work-log'
import type { SystemPeopleDirectory, WorkConfigurationField, WorkLog, WorkProject } from '../types'
import PersonSelect from '../components/PersonSelect.vue'
import WorkConfiguredFields from '../components/WorkConfiguredFields.vue'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const logs = ref<WorkLog[]>([])
const projects = ref<WorkProject[]>([])
const directory = ref<SystemPeopleDirectory>({ departments: [], people: [], permissionVersion: 0 })
const logFields = ref<WorkConfigurationField[]>([])
const detail = ref<WorkLog>()
const loading = ref(false)
const busy = ref('')
const editorOpen = ref(false)
const detailOpen = ref(false)
const editing = ref<WorkLog>()
const dateFilter = ref<string>()
const authorFilter = ref<number>()
const form = reactive({
  workDate: today(), title: '', content: '', durationMinutes: 60,
  projectId: undefined as number | undefined, taskIds: [] as number[], revisionReason: '',
  configuredValues: {} as Record<string, unknown>,
})

const projectTasks = computed(() => projects.value.find(project => project.id === form.projectId)?.tasks || [])

function today() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function disabledFuture(currentDate: Dayjs) {
  return currentDate.isAfter(today(), 'day')
}

function can(action: string) {
  return allowsPermission(current.value?.permissions, 'WORK', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'WORK', '*', action)
}

function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
}

function statusLabel(status: WorkLog['status']) {
  return ({ DRAFT: '草稿', SUBMITTED: '已提交', WITHDRAWN: '已撤回' })[status]
}

function statusColor(status: WorkLog['status']) {
  return ({ DRAFT: 'orange', SUBMITTED: 'green', WITHDRAWN: 'default' })[status]
}

function projectName(log: WorkLog) {
  return log.projectName || projects.value.find(project => project.id === log.projectId)?.name
}

function linkedTaskNames(log: WorkLog) {
  return log.tasks.map(task => task.title).join('、') || '未关联'
}

async function loadLogs() {
  if (!token.value || !can('VIEW')) return
  loading.value = true
  try {
    const params = new URLSearchParams()
    if (dateFilter.value) params.set('workDate', dateFilter.value)
    if (authorFilter.value) params.set('tenantMemberId', String(authorFilter.value))
    logs.value = sortWorkLogs(await api<WorkLog[]>(`/api/work/logs${params.size ? `?${params}` : ''}`, {}, token.value))
  } catch (error) {
    message.error(describeError(error))
  } finally {
    loading.value = false
  }
}

async function loadProjects() {
  if (!token.value) return
  try {
    const summaries = await api<WorkProject[]>('/api/work/projects', {}, token.value)
    projects.value = await Promise.all(summaries.map(project =>
      api<WorkProject>(`/api/work/projects/${project.id}`, {}, token.value)))
  } catch {
    projects.value = []
  }
}

async function loadDirectory() {
  if (!token.value || props.context !== 'system') return
  try { directory.value = await api<SystemPeopleDirectory>('/api/system-directory', {}, token.value) }
  catch (error) { message.error(describeError(error)) }
}

async function loadLogFields() {
  if (!token.value) return
  try { logFields.value = await api<WorkConfigurationField[]>('/api/work/configuration/effective/LOG', {}, token.value) }
  catch (error) { message.error(describeError(error)) }
}

function openCreate() {
  editing.value = undefined
  Object.assign(form, { workDate: today(), title: '', content: '', durationMinutes: 60,
    projectId: undefined, taskIds: [], revisionReason: '', configuredValues: {} })
  editorOpen.value = true
}

function openEdit(log: WorkLog) {
  editing.value = log
  Object.assign(form, {
    workDate: log.workDate, title: log.title, content: log.content, durationMinutes: log.durationMinutes || 60,
    projectId: log.projectId,
    taskIds: log.tasks.map(task => task.id),
    revisionReason: '', configuredValues: { ...log.configuredValues },
  })
  detailOpen.value = false
  editorOpen.value = true
}

async function saveLog() {
  if (!token.value || !form.workDate || !form.title.trim() || !form.content.trim()) return
  if (editing.value && !form.revisionReason.trim()) return message.warning('修订日志必须填写修订原因')
  busy.value = 'save'
  try {
    const payload = {
      workDate: form.workDate, title: form.title, content: form.content,
      durationMinutes: form.durationMinutes, projectId: form.projectId,
      taskIds: form.taskIds, businessType: editing.value?.businessType,
      businessId: editing.value?.businessId, businessTitle: editing.value?.businessTitle,
      configuredValues: form.configuredValues,
    }
    const saved = editing.value
      ? await api<WorkLog>(`/api/work/logs/${editing.value.id}`, {
        method: 'PUT', body: JSON.stringify({ ...payload, expectedVersion: editing.value.version,
          status: editing.value.status, revisionReason: form.revisionReason }),
      }, token.value)
      : await api<WorkLog>('/api/work/logs', { method: 'POST', body: JSON.stringify(payload) }, token.value)
    editorOpen.value = false
    message.success(editing.value ? '日志已修订并保留历史版本' : '今日日志已创建并排在列表首位')
    await loadLogs()
    await openDetail(saved.id)
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

async function openDetail(id: number) {
  if (!token.value) return
  try {
    detail.value = await api<WorkLog>(`/api/work/logs/${id}`, {}, token.value)
    detailOpen.value = true
  } catch (error) {
    message.error(describeError(error))
  }
}

async function changeStatus(log: WorkLog, status: WorkLog['status'], revisionReason: string) {
  if (!token.value) return
  busy.value = `status:${log.id}`
  try {
    const updated = await api<WorkLog>(`/api/work/logs/${log.id}`, {
      method: 'PUT', body: JSON.stringify({
        expectedVersion: log.version, workDate: log.workDate, title: log.title, content: log.content,
        durationMinutes: log.durationMinutes, status, projectId: log.projectId,
        taskIds: log.tasks.map(task => task.id), businessType: log.businessType,
        businessId: log.businessId, businessTitle: log.businessTitle,
        configuredValues: log.configuredValues, revisionReason,
      }),
    }, token.value)
    detail.value = updated
    await loadLogs()
    message.success(status === 'SUBMITTED' ? '日志已提交，正文已锁定' : status === 'WITHDRAWN' ? '日志已撤回，可继续修订' : '日志已恢复为草稿')
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

onMounted(() => Promise.all([loadLogs(), loadProjects(), loadDirectory(), loadLogFields()]))
</script>

<template>
  <div class="work-logs-page">
    <div class="page-heading compact-heading">
      <div><p class="eyebrow">任务 · 日志</p><h1>手工工作日志</h1><p>日志只在本人点击新建并保存时产生；项目任务状态变化不会自动创建日志。</p></div>
      <a-button v-if="can('CREATE_LOG')" type="primary" @click="openCreate">新建日志</a-button>
    </div>
    <a-alert type="info" show-icon message="任务与日志相互引用但不互相代写：完成任务不会生成日志，日志可主动关联项目和任务。" />
    <a-alert v-if="!can('VIEW')" type="warning" show-icon message="当前工作范围没有工作日志查看权限" />
    <template v-else>
      <section class="panel-card work-log-filter">
        <a-date-picker v-model:value="dateFilter" value-format="YYYY-MM-DD" placeholder="全部日期" allow-clear />
        <PersonSelect v-if="can('VIEW_OTHERS') && context === 'system'" v-model="authorFilter" :people="directory.people" value-key="tenantMemberId" placeholder="按作者筛选" style="width:240px" />
        <a-button @click="loadLogs">筛选</a-button>
        <a-button v-if="dateFilter || authorFilter" @click="dateFilter = undefined; authorFilter = undefined; loadLogs()">清空</a-button>
      </section>
      <a-spin :spinning="loading">
        <section v-if="logs.length" class="work-log-list">
          <article v-for="log in logs" :key="log.id" class="panel-card work-log-row" @click="openDetail(log.id)">
            <time><strong>{{ log.workDate.slice(8, 10) }}</strong><small>{{ log.workDate.slice(0, 7) }}</small></time>
            <div><span><strong>{{ log.title }}</strong><a-tag :color="statusColor(log.status)">{{ statusLabel(log.status) }}</a-tag></span><p>{{ log.content }}</p><small>{{ log.author.displayName }}<template v-if="projectName(log)"> · {{ projectName(log) }}</template> · {{ productDateTime(log.updatedAt) }}</small></div>
            <span class="work-log-duration">{{ log.durationMinutes || '—' }}<small>分钟</small></span>
          </article>
        </section>
        <section v-else class="panel-card project-empty"><a-empty :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无日志；日志不会由任务状态自动生成" /></section>
      </a-spin>
    </template>
  </div>

  <a-modal v-model:open="editorOpen" :title="editing ? '修订工作日志' : '新建工作日志'" width="720px" :confirm-loading="busy === 'save'" :ok-button-props="{ disabled: !form.title.trim() || !form.content.trim() || (!!editing && !form.revisionReason.trim()) }" @ok="saveLog">
    <a-form layout="vertical">
      <div class="form-grid"><a-form-item label="工作日期" required extra="不能填写未来日期"><a-date-picker v-model:value="form.workDate" value-format="YYYY-MM-DD" :disabled-date="disabledFuture" style="width:100%" /></a-form-item><a-form-item label="投入分钟" required><a-input-number v-model:value="form.durationMinutes" :min="1" :max="1440" style="width:100%" /></a-form-item></div>
      <a-form-item label="标题" required><a-input v-model:value="form.title" placeholder="今天完成了什么" /></a-form-item>
      <a-form-item label="日志正文" required><a-textarea v-model:value="form.content" :rows="6" placeholder="写明完成结果、验证情况和后续事项" /></a-form-item>
      <div class="form-grid"><a-form-item label="关联项目"><a-select v-model:value="form.projectId" allow-clear placeholder="可选" :options="projects.map(project => ({ value: project.id, label: project.name }))" @change="form.taskIds = []" /></a-form-item><a-form-item label="关联任务"><a-select v-model:value="form.taskIds" mode="multiple" allow-clear placeholder="可多选" :disabled="!form.projectId" :options="projectTasks.map(task => ({ value: task.id, label: task.title }))" /></a-form-item></div>
      <WorkConfiguredFields v-model="form.configuredValues" :fields="logFields" />
      <a-form-item v-if="editing" label="修订原因" required><a-input v-model:value="form.revisionReason" placeholder="例如：补充页面验收结果" /></a-form-item>
    </a-form>
  </a-modal>

  <a-drawer v-model:open="detailOpen" title="日志详情与不可变修订记录" width="680">
    <template v-if="detail">
      <div class="work-log-detail-heading"><span><p class="eyebrow">{{ detail.workDate }} · 工作日志</p><h2>{{ detail.title }}</h2></span><a-tag :color="statusColor(detail.status)">{{ statusLabel(detail.status) }}</a-tag></div>
      <p class="work-log-content">{{ detail.content }}</p>
      <a-descriptions bordered size="small" :column="2"><a-descriptions-item label="作者">{{ detail.author.displayName }}<small v-if="detail.author.departmentName"> · {{ detail.author.departmentName }}</small></a-descriptions-item><a-descriptions-item label="投入">{{ detail.durationMinutes || '—' }} 分钟</a-descriptions-item><a-descriptions-item label="关联项目">{{ projectName(detail) || '未关联' }}</a-descriptions-item><a-descriptions-item label="关联任务">{{ linkedTaskNames(detail) }}</a-descriptions-item><a-descriptions-item label="更新时间">{{ productDateTime(detail.updatedAt) }}</a-descriptions-item><a-descriptions-item v-for="field in logFields.filter(item => detail?.configuredValues[item.fieldCode] !== undefined)" :key="field.fieldCode" :label="field.fieldName">{{ detail.configuredValues[field.fieldCode] }}</a-descriptions-item></a-descriptions>
      <div v-if="(context === 'platform' || detail.author.tenantMemberId === current?.tenantMemberId) && can('UPDATE_LOG')" class="work-log-actions">
        <a-button v-if="canEditLog(detail.status)" @click="openEdit(detail)">修订内容</a-button>
        <a-button v-if="detail.status === 'DRAFT'" type="primary" :loading="busy === `status:${detail.id}`" @click="changeStatus(detail, 'SUBMITTED', '用户确认提交日志')">提交并锁定</a-button>
        <a-button v-if="detail.status === 'SUBMITTED'" :loading="busy === `status:${detail.id}`" @click="changeStatus(detail, 'WITHDRAWN', '用户撤回已提交日志')">撤回后修订</a-button>
        <a-button v-if="detail.status === 'WITHDRAWN'" type="primary" :loading="busy === `status:${detail.id}`" @click="changeStatus(detail, 'DRAFT', '用户恢复为草稿')">恢复草稿</a-button>
      </div>
      <a-divider>修订时间线</a-divider>
      <a-timeline>
        <a-timeline-item v-for="(revision, index) in detail.revisions" :key="revision.id" :color="index === 0 ? 'blue' : 'gray'">
          <strong>修订 {{ revision.revisionNumber }} · {{ revision.revisionReason }}</strong>
          <p>{{ revisionDifference(revision, detail.revisions[index + 1]) }}</p>
          <small>{{ revision.revisedByName }} · {{ productDateTime(revision.revisedAt) }} · {{ statusLabel(revision.snapshot.status as WorkLog['status']) }}</small>
        </a-timeline-item>
      </a-timeline>
    </template>
  </a-drawer>
</template>
