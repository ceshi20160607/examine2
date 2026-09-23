<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import type { Dayjs } from 'dayjs'
import { ApiError, api } from '../api'
import { allowsPermission } from '../permissions'
import { productDateTime } from '../presentation'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { revisionDifference, sortWorkLogs } from '../work-log'
import type { BusinessRecordValue } from '../components/BusinessRecordPicker.vue'
import type {
  SystemPeopleDirectory, WorkConfigurationField, WorkLog, WorkLogMaterialCandidate,
  WorkLogOptions, WorkProject, WorkTask,
} from '../types'
import BusinessAttachmentsPanel from '../components/BusinessAttachmentsPanel.vue'
import BusinessRecordPicker from '../components/BusinessRecordPicker.vue'
import PersonSelect from '../components/PersonSelect.vue'
import ProductDetailDrawer from '../components/ProductDetailDrawer.vue'
import ProductFilterBar from '../components/ProductFilterBar.vue'
import ProductFormDrawer from '../components/ProductFormDrawer.vue'
import ProductListWorkspace from '../components/ProductListWorkspace.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import WorkConfiguredFields from '../components/WorkConfiguredFields.vue'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'platform' })
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken || '' : systemTokens.value?.accessToken || '')
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const logs = ref<WorkLog[]>([])
const projects = ref<WorkProject[]>([])
const ordinaryTasks = ref<WorkTask[]>([])
const directory = ref<SystemPeopleDirectory>({ departments: [], people: [], permissionVersion: 0 })
const logFields = ref<WorkConfigurationField[]>([])
const materialCandidates = ref<WorkLogMaterialCandidate[]>([])
const options = ref<WorkLogOptions>({ roles: [] })
const detail = ref<WorkLog>()
const loading = ref(false)
const busy = ref('')
const loadError = ref('')
const formOpen = ref(false)
const detailOpen = ref(false)
const editing = ref<WorkLog>()
const lifecycleReason = ref('')
const commentText = ref('')
const commentType = ref<'COMMENT' | 'FEEDBACK'>('COMMENT')
const filters = reactive({ date: undefined as string | undefined, status: undefined as string | undefined,
  projectId: undefined as number | undefined, keyword: '', authorId: undefined as number | undefined })
const form = reactive({
  workDate: today(), title: '', completedText: '', inProgressText: '', nextPlanText: '', riskText: '', assistanceText: '',
  durationMinutes: 60, projectId: undefined as number | undefined, taskIds: [] as number[],
  visibilityType: 'SELF' as WorkLog['visibilityType'], visibleRoleIds: [] as number[],
  materialKeys: [] as string[], business: {} as BusinessRecordValue, revisionReason: '',
  configuredValues: {} as Record<string, unknown>,
})

const allTasks = computed(() => {
  const map = new Map<number, WorkTask>()
  ordinaryTasks.value.forEach(task => map.set(task.id, task))
  projects.value.flatMap(project => project.tasks || []).forEach(task => map.set(task.id, task))
  return [...map.values()]
})
const selectableTasks = computed(() => allTasks.value.filter(task => !form.projectId || task.projectId === form.projectId))
const materialOptions = computed(() => {
  const map = new Map(materialCandidates.value.map(item => [item.key, { value: item.key, label: `[${item.typeName}] ${item.title}` }]))
  editing.value?.materials.forEach(item => {
    const key = `${item.materialType}:${item.materialId}`
    if (!map.has(key)) map.set(key, { value: key, label: `[已留档] ${item.title}` })
  })
  return [...map.values()]
})
const canSave = computed(() => Boolean(form.workDate && form.title.trim()) && (!editing.value || Boolean(form.revisionReason.trim())))

function today() {
  const date = new Date()
  const pad = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}
function disabledFuture(value: Dayjs) { return value.isAfter(today(), 'day') }
function can(action: string) {
  return allowsPermission(current.value?.permissions, 'WORK', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'WORK', '*', action)
}
function canFile(action: string) {
  return allowsPermission(current.value?.permissions, 'FILE', 'OBJECT', action)
    || allowsPermission(current.value?.permissions, 'FILE', '*', action)
}
function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
}
function statusLabel(status: WorkLog['status']) { return ({ DRAFT: '草稿', SUBMITTED: '已提交', WITHDRAWN: '已撤回' })[status] }
function statusColor(status: WorkLog['status']) { return ({ DRAFT: 'orange', SUBMITTED: 'green', WITHDRAWN: 'default' })[status] }
function visibilityLabel(value: WorkLog['visibilityType']) {
  return ({ SELF: '仅本人', MANAGER: '直属管理', PROJECT: '项目成员', TEAM: '同部门', ROLE: '指定角色' })[value]
}
function materialTypeLabel(type: string) {
  return ({ TASK: '任务', TODO: '待办', MESSAGE: '消息', BUSINESS_OPERATION: '业务操作' } as Record<string, string>)[type] || type
}

async function loadLogs() {
  if (!token.value || !can('VIEW')) return
  loading.value = true
  loadError.value = ''
  try {
    const params = new URLSearchParams()
    if (filters.date) params.set('workDate', filters.date)
    if (filters.status) params.set('status', filters.status)
    if (filters.projectId) params.set('projectId', String(filters.projectId))
    if (filters.keyword.trim()) params.set('keyword', filters.keyword.trim())
    if (filters.authorId) params.set('authorTenantMemberId', String(filters.authorId))
    logs.value = sortWorkLogs(await api<WorkLog[]>(`/api/work/logs${params.size ? `?${params}` : ''}`, {}, token.value))
  } catch (error) { loadError.value = describeError(error) }
  finally { loading.value = false }
}
async function loadReferences() {
  if (!token.value) return
  const results = await Promise.allSettled([
    api<WorkProject[]>('/api/work/projects', {}, token.value),
    api<WorkTask[]>('/api/work/tasks', {}, token.value),
    api<WorkLogMaterialCandidate[]>('/api/work/logs/materials', {}, token.value),
    api<WorkLogOptions>('/api/work/logs/options', {}, token.value),
    api<WorkConfigurationField[]>('/api/work/configuration/effective/LOG', {}, token.value),
  ])
  if (results[0].status === 'fulfilled') {
    projects.value = await Promise.all(results[0].value.map(project => api<WorkProject>(`/api/work/projects/${project.id}`, {}, token.value)))
  }
  if (results[1].status === 'fulfilled') ordinaryTasks.value = results[1].value
  if (results[2].status === 'fulfilled') materialCandidates.value = results[2].value
  if (results[3].status === 'fulfilled') options.value = results[3].value
  if (results[4].status === 'fulfilled') logFields.value = results[4].value
}
async function loadDirectory() {
  if (!token.value || props.context !== 'system') return
  try { directory.value = await api<SystemPeopleDirectory>('/api/system-directory', {}, token.value) }
  catch (error) { message.error(describeError(error)) }
}
function resetFilters() {
  Object.assign(filters, { date: undefined, status: undefined, projectId: undefined, keyword: '', authorId: undefined })
  void loadLogs()
}
function openCreate() {
  editing.value = undefined
  Object.assign(form, { workDate: today(), title: '', completedText: '', inProgressText: '', nextPlanText: '', riskText: '',
    assistanceText: '', durationMinutes: 60, projectId: undefined, taskIds: [], visibilityType: 'SELF',
    visibleRoleIds: [], materialKeys: [], business: {}, revisionReason: '', configuredValues: {} })
  formOpen.value = true
}
function openEdit(log: WorkLog) {
  editing.value = log
  Object.assign(form, { workDate: log.workDate, title: log.title, completedText: log.completedText || log.content || '',
    inProgressText: log.inProgressText || '', nextPlanText: log.nextPlanText || '', riskText: log.riskText || '',
    assistanceText: log.assistanceText || '', durationMinutes: log.durationMinutes || 60, projectId: log.projectId,
    taskIds: log.tasks.map(task => task.id), visibilityType: log.visibilityType, visibleRoleIds: [...log.visibleRoleIds],
    materialKeys: log.materials.map(item => `${item.materialType}:${item.materialId}`),
    business: { businessType: log.businessType, businessId: log.businessId, businessTitle: log.businessTitle },
    revisionReason: '', configuredValues: { ...log.configuredValues } })
  detailOpen.value = false
  formOpen.value = true
}
function selectedMaterials() {
  return form.materialKeys.map(key => {
    const split = key.indexOf(':')
    return { materialType: key.slice(0, split), materialId: key.slice(split + 1) }
  })
}
async function saveLog() {
  if (!token.value || !canSave.value) return
  busy.value = 'save'
  try {
    const payload = { workDate: form.workDate, title: form.title, completedText: form.completedText,
      inProgressText: form.inProgressText, nextPlanText: form.nextPlanText, riskText: form.riskText,
      assistanceText: form.assistanceText, durationMinutes: form.durationMinutes, projectId: form.projectId,
      taskIds: form.taskIds, ...form.business, visibilityType: form.visibilityType,
      visibleRoleIds: form.visibilityType === 'ROLE' ? form.visibleRoleIds : [], materials: selectedMaterials(),
      configuredValues: form.configuredValues }
    const saved = editing.value
      ? await api<WorkLog>(`/api/work/logs/${editing.value.id}`, { method: 'PUT', body: JSON.stringify({
        ...payload, expectedVersion: editing.value.version, revisionReason: form.revisionReason }) }, token.value)
      : await api<WorkLog>('/api/work/logs', { method: 'POST', body: JSON.stringify(payload) }, token.value)
    formOpen.value = false
    await Promise.all([loadLogs(), loadReferences()])
    await openDetail(saved.id)
    message.success(editing.value ? '日志已修订并保留版本' : '工作日志已保存为草稿')
  } catch (error) { message.error(describeError(error)) }
  finally { busy.value = '' }
}
async function openDetail(id: number) {
  if (!token.value) return
  detailOpen.value = true
  busy.value = 'detail'
  try { detail.value = await api<WorkLog>(`/api/work/logs/${id}`, {}, token.value) }
  catch (error) { message.error(describeError(error)); detailOpen.value = false }
  finally { busy.value = '' }
}
async function lifecycle(action: 'submit' | 'withdraw' | 'restore-draft') {
  if (!token.value || !detail.value) return
  const defaultReason = action === 'submit' ? '用户确认提交日志' : action === 'withdraw' ? '用户撤回日志进行修订' : '用户恢复日志草稿'
  busy.value = action
  try {
    detail.value = await api<WorkLog>(`/api/work/logs/${detail.value.id}/${action}`, { method: 'POST',
      body: JSON.stringify({ expectedVersion: detail.value.version, reason: lifecycleReason.value.trim() || defaultReason }) }, token.value)
    lifecycleReason.value = ''
    await loadLogs()
    message.success(action === 'submit' ? '日志已提交并锁定正文' : action === 'withdraw' ? '日志已撤回，可继续修订' : '日志已恢复为草稿')
  } catch (error) { message.error(describeError(error)) }
  finally { busy.value = '' }
}
async function addComment() {
  if (!token.value || !detail.value || !commentText.value.trim()) return
  busy.value = 'comment'
  try {
    detail.value = await api<WorkLog>(`/api/work/logs/${detail.value.id}/comments`, { method: 'POST',
      body: JSON.stringify({ commentType: commentType.value, comment: commentText.value.trim() }) }, token.value)
    commentText.value = ''
    message.success(commentType.value === 'FEEDBACK' ? '管理反馈已保存' : '评论已保存')
  } catch (error) { message.error(describeError(error)) }
  finally { busy.value = '' }
}

onMounted(() => Promise.all([loadLogs(), loadReferences(), loadDirectory()]))
</script>

<template>
  <div class="work-logs-page">
    <ProductPageHeader kicker="任务 · 日志" title="工作日志" description="主动整理当天成果、计划与风险；任务状态变化不会代替你生成或改写日志。">
      <template #primary><a-button v-if="can('CREATE_LOG')" type="primary" @click="openCreate">新建日志</a-button></template>
    </ProductPageHeader>
    <ProductFilterBar label="工作日志查询">
      <a-input v-model:value="filters.keyword" allow-clear placeholder="搜索标题、成果、计划或风险" style="width:240px" @press-enter="loadLogs" />
      <a-date-picker v-model:value="filters.date" value-format="YYYY-MM-DD" placeholder="全部日期" allow-clear />
      <a-select v-model:value="filters.status" allow-clear placeholder="全部状态" style="width:130px" :options="[{value:'DRAFT',label:'草稿'},{value:'SUBMITTED',label:'已提交'},{value:'WITHDRAWN',label:'已撤回'}]" />
      <a-select v-model:value="filters.projectId" allow-clear show-search option-filter-prop="label" placeholder="全部项目" style="width:200px" :options="projects.map(item => ({value:item.id,label:item.name}))" />
      <PersonSelect v-if="can('VIEW_OTHERS') && context === 'system'" v-model="filters.authorId" :people="directory.people" value-key="tenantMemberId" placeholder="日志作者" style="width:210px" />
      <template #tools><a-button type="primary" @click="loadLogs">查询</a-button><a-button @click="resetFilters">重置</a-button></template>
    </ProductFilterBar>
    <ProductListWorkspace :loading="loading" :has-content="logs.length > 0" :empty="!logs.length" :error="loadError"
      empty-title="当前条件下没有工作日志" empty-description="新建日志后可提交、撤回、关联任务和接收反馈。">
      <template #state-actions><a-button @click="loadLogs">重新加载</a-button></template>
      <div class="work-log-list">
        <article v-for="log in logs" :key="log.id" class="work-log-row" @click="openDetail(log.id)">
          <time><strong>{{ log.workDate.slice(8, 10) }}</strong><small>{{ log.workDate.slice(0, 7) }}</small></time>
          <div><span><strong>{{ log.title }}</strong><a-tag :color="statusColor(log.status)">{{ statusLabel(log.status) }}</a-tag><a-tag>{{ visibilityLabel(log.visibilityType) }}</a-tag></span><p>{{ log.completedText || log.content || '尚未填写工作内容' }}</p><small>{{ log.author.displayName }}<template v-if="log.projectName"> · {{ log.projectName }}</template> · {{ productDateTime(log.updatedAt) }}</small></div>
          <span class="work-log-duration">{{ log.durationMinutes || '—' }}<small>分钟</small></span>
        </article>
      </div>
    </ProductListWorkspace>
  </div>

  <ProductFormDrawer :open="formOpen" :title="editing ? '修订工作日志' : '新建工作日志'" width="820" :saving="busy === 'save'" :save-disabled="!canSave" :save-label="editing ? '保存修订' : '保存草稿'" @close="formOpen = false" @save="saveLog">
    <a-form layout="vertical">
      <div class="form-grid"><a-form-item label="工作日期" required extra="可以补写过去日期，不能填写未来日期"><a-date-picker v-model:value="form.workDate" value-format="YYYY-MM-DD" :disabled-date="disabledFuture" style="width:100%" /></a-form-item><a-form-item label="投入时间" required><a-input-number v-model:value="form.durationMinutes" :min="1" :max="1440" addon-after="分钟" style="width:100%" /></a-form-item></div>
      <a-form-item label="日志标题" required><a-input v-model:value="form.title" placeholder="例如：客户资料模块联调与验收" /></a-form-item>
      <a-form-item label="今日已完成"><a-textarea v-model:value="form.completedText" :rows="4" placeholder="写清成果、范围和验证结果" /></a-form-item>
      <div class="form-grid"><a-form-item label="当前进行中"><a-textarea v-model:value="form.inProgressText" :rows="3" placeholder="正在处理及当前进度" /></a-form-item><a-form-item label="下一工作日计划"><a-textarea v-model:value="form.nextPlanText" :rows="3" placeholder="下一步具体动作" /></a-form-item></div>
      <div class="form-grid"><a-form-item label="风险与阻塞"><a-textarea v-model:value="form.riskText" :rows="3" placeholder="无风险可留空" /></a-form-item><a-form-item label="需要协助"><a-textarea v-model:value="form.assistanceText" :rows="3" placeholder="需要谁提供什么帮助" /></a-form-item></div>
      <div class="form-grid"><a-form-item label="关联项目"><a-select v-model:value="form.projectId" allow-clear show-search option-filter-prop="label" placeholder="可选" :options="projects.map(item => ({value:item.id,label:item.name}))" @change="form.taskIds = form.taskIds.filter(id => selectableTasks.some(task => task.id === id))" /></a-form-item><a-form-item label="关联任务"><a-select v-model:value="form.taskIds" mode="multiple" allow-clear show-search option-filter-prop="label" placeholder="可跨普通任务或所选项目多选" :options="selectableTasks.map(task => ({value:task.id,label:task.title}))" /></a-form-item></div>
      <a-form-item label="关联业务记录"><BusinessRecordPicker v-model="form.business" :token="token" /></a-form-item>
      <a-form-item label="从现有工作中选材" extra="选择只会形成来源快照，不会自动写入或覆盖日志内容"><a-select v-model:value="form.materialKeys" mode="multiple" allow-clear show-search option-filter-prop="label" placeholder="选择任务、待办、消息或本人业务操作" :options="materialOptions" /></a-form-item>
      <div class="form-grid"><a-form-item label="可见范围" required><a-select v-model:value="form.visibilityType" :options="[{value:'SELF',label:'仅本人'},{value:'MANAGER',label:'直属管理'},{value:'PROJECT',label:'所选项目成员'},{value:'TEAM',label:'同部门'},{value:'ROLE',label:'指定角色'}].filter(item => context === 'system' || ['SELF','ROLE'].includes(item.value))" /></a-form-item><a-form-item v-if="form.visibilityType === 'ROLE'" label="可见角色" required><a-select v-model:value="form.visibleRoleIds" mode="multiple" show-search option-filter-prop="label" :options="options.roles.map(role => ({value:role.id,label:role.name}))" /></a-form-item></div>
      <WorkConfiguredFields v-model="form.configuredValues" :fields="logFields" />
      <a-form-item v-if="editing" label="修订原因" required><a-input v-model:value="form.revisionReason" placeholder="说明本次修改内容，历史版本会保留" /></a-form-item>
    </a-form>
  </ProductFormDrawer>

  <ProductDetailDrawer :open="detailOpen" title="日志详情" width="820" :loading="busy === 'detail'" @close="detailOpen = false">
    <template #default><template v-if="detail">
      <div class="work-log-detail-heading"><span><p class="eyebrow">{{ detail.workDate }} · {{ detail.author.displayName }}</p><h2>{{ detail.title }}</h2></span><span><a-tag :color="statusColor(detail.status)">{{ statusLabel(detail.status) }}</a-tag><a-tag>{{ visibilityLabel(detail.visibilityType) }}</a-tag></span></div>
      <section class="work-log-sections">
        <article><strong>今日已完成</strong><p>{{ detail.completedText || detail.content || '未填写' }}</p></article>
        <article><strong>当前进行中</strong><p>{{ detail.inProgressText || '未填写' }}</p></article>
        <article><strong>下一工作日计划</strong><p>{{ detail.nextPlanText || '未填写' }}</p></article>
        <article><strong>风险与阻塞</strong><p>{{ detail.riskText || '无' }}</p></article>
        <article><strong>需要协助</strong><p>{{ detail.assistanceText || '无' }}</p></article>
      </section>
      <a-descriptions bordered size="small" :column="2">
        <a-descriptions-item label="投入">{{ detail.durationMinutes || '—' }} 分钟</a-descriptions-item><a-descriptions-item label="关联项目">{{ detail.projectName || '未关联' }}</a-descriptions-item>
        <a-descriptions-item label="关联任务">{{ detail.tasks.map(task => task.title).join('、') || '未关联' }}</a-descriptions-item><a-descriptions-item label="关联业务">{{ detail.businessTitle || '未关联' }}</a-descriptions-item>
        <a-descriptions-item label="更新时间">{{ productDateTime(detail.updatedAt) }}</a-descriptions-item><a-descriptions-item label="提交时间">{{ detail.submittedTime ? productDateTime(detail.submittedTime) : '尚未提交' }}</a-descriptions-item>
        <a-descriptions-item v-for="field in logFields.filter(item => detail?.configuredValues[item.fieldCode] !== undefined)" :key="field.fieldCode" :label="field.fieldName">{{ detail.configuredValues[field.fieldCode] }}</a-descriptions-item>
      </a-descriptions>
      <a-divider>选材来源</a-divider>
      <div v-if="detail.materials.length" class="work-log-materials"><a-tag v-for="item in detail.materials" :key="`${item.materialType}:${item.materialId}`">{{ materialTypeLabel(item.materialType) }} · {{ item.title }}</a-tag></div><a-empty v-else :image="false" description="本日志未引用候选材料" />
      <a-divider v-if="canFile('VIEW')">附件</a-divider>
      <BusinessAttachmentsPanel v-if="canFile('VIEW')" :endpoint="`/api/business-attachments/work-logs/${detail.id}`" :context="context" :writable="detail.editable && detail.status !== 'SUBMITTED' && canFile('UPLOAD') && canFile('REFERENCE')" compact description="日志附件遵循相同可见范围；已提交时需先撤回才能增删。" />
      <a-divider>评论与反馈</a-divider>
      <a-timeline v-if="detail.comments.length"><a-timeline-item v-for="item in detail.comments" :key="item.id" :color="item.commentType === 'FEEDBACK' ? 'blue' : 'gray'"><strong>{{ item.commentType === 'FEEDBACK' ? '管理反馈' : '评论' }} · {{ item.author.displayName }}</strong><p>{{ item.comment }}</p><small>{{ productDateTime(item.createdTime) }}</small></a-timeline-item></a-timeline><a-empty v-else :image="false" description="暂无评论或反馈" />
      <div class="work-log-comment"><a-select v-model:value="commentType" style="width:120px" :options="[{value:'COMMENT',label:'评论'},{value:'FEEDBACK',label:'管理反馈'}]" /><a-input v-model:value="commentText" placeholder="填写评论或反馈" @press-enter="addComment" /><a-button :loading="busy === 'comment'" :disabled="!commentText.trim()" @click="addComment">发送</a-button></div>
      <a-divider>修订记录</a-divider>
      <a-timeline><a-timeline-item v-for="(revision, index) in detail.revisions" :key="revision.id" :color="index === 0 ? 'blue' : 'gray'"><strong>版本 {{ revision.revisionNumber }} · {{ revision.revisionReason }}</strong><p>{{ revisionDifference(revision, detail.revisions[index + 1]) }}</p><small>{{ revision.revisedByName }} · {{ productDateTime(revision.revisedAt) }}</small></a-timeline-item></a-timeline>
    </template></template>
    <template #actions><a-button v-if="detail?.editable && detail.status !== 'SUBMITTED'" @click="openEdit(detail)">修订内容</a-button></template>
    <template #primary><template v-if="detail?.editable"><a-input v-model:value="lifecycleReason" placeholder="状态操作说明（可选）" style="width:220px" /><a-button v-if="detail.status === 'DRAFT'" type="primary" :loading="busy === 'submit'" @click="lifecycle('submit')">提交并锁定</a-button><a-button v-else-if="detail.status === 'SUBMITTED'" :loading="busy === 'withdraw'" @click="lifecycle('withdraw')">撤回修订</a-button><a-button v-else type="primary" :loading="busy === 'restore-draft'" @click="lifecycle('restore-draft')">恢复草稿</a-button></template></template>
  </ProductDetailDrawer>
</template>

<style scoped>
.work-log-sections{display:grid;grid-template-columns:1fr 1fr;gap:10px;margin-bottom:16px}.work-log-sections article{padding:13px;border:1px solid var(--ux-color-border);border-radius:8px;background:#fafbfd}.work-log-sections article:first-child{grid-column:1/-1}.work-log-sections p{margin:7px 0 0;white-space:pre-wrap;color:var(--ux-color-text-secondary)}.work-log-materials{display:flex;flex-wrap:wrap;gap:8px}.work-log-comment{display:grid;grid-template-columns:120px 1fr auto;gap:8px;margin:12px 0 20px}@media(max-width:720px){.work-log-sections{grid-template-columns:1fr}.work-log-sections article:first-child{grid-column:auto}.work-log-comment{grid-template-columns:1fr}.work-log-filter{align-items:stretch}}
</style>
