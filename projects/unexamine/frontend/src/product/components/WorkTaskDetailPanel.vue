<script setup lang="ts">
import { message } from 'ant-design-vue'
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, api } from '../api'
import { productDateTime } from '../presentation'
import type { PeopleDirectoryOption, WorkTask } from '../types'
import BusinessAttachmentsPanel from './BusinessAttachmentsPanel.vue'
import PersonSelect from './PersonSelect.vue'
import ProductStatusTag from './ProductStatusTag.vue'

const props = withDefaults(defineProps<{
  task: WorkTask
  token: string
  context: 'platform' | 'system'
  people: PeopleDirectoryOption[]
  canFileView?: boolean
  canFileWrite?: boolean
}>(), { canFileView: false, canFileWrite: false })
const router = useRouter()
const emit = defineEmits<{
  updated: [task: WorkTask]
  deleted: [taskId: number]
  edit: [task: WorkTask]
  addSubtask: [task: WorkTask]
  openSubtask: [taskId: number]
}>()

type ActionKind = 'transfer' | 'delay' | 'submit' | 'accept' | 'reopen' | 'archive' | 'delete'
const actionOpen = ref(false)
const actionKind = ref<ActionKind>('transfer')
const busy = ref(false)
const progressBusy = ref(false)
const commentBusy = ref(false)
const actionForm = reactive({ memberId: undefined as number | undefined, dueTime: '', comment: '', actualHours: undefined as number | undefined })
const progressForm = reactive({ progressPercent: 0, actualHours: undefined as number | undefined, comment: '' })
const commentText = ref('')
const linkedModuleCode = computed(() => props.task.businessType?.startsWith('MODULE:')
  ? props.task.businessType.slice(7) : '')

/** 关联任务不授予业务详情权限；跳转前以当前身份重新读取目标记录。 */
async function openLinkedBusiness() {
  const moduleCode = linkedModuleCode.value
  const recordId = Number(props.task.businessId)
  const systemId = props.task.systemId
  if (props.context !== 'system' || !systemId || !moduleCode || !Number.isInteger(recordId) || recordId <= 0) return
  try {
    await api(`/api/runtime/modules/${encodeURIComponent(moduleCode)}/records/${recordId}`, {}, props.token)
    await router.push({ path: `/systems/${systemId}`, query: {
      workspace: 'runtime', module: moduleCode, recordId: String(recordId),
    } })
  } catch (reason) { message.error(readable(reason)) }
}

watch(() => props.task, (task) => {
  progressForm.progressPercent = Number(task.progressPercent || 0)
  progressForm.actualHours = task.actualHours
  progressForm.comment = ''
}, { immediate: true })

const actionTitle = computed(() => ({
  transfer: '转派任务', delay: '申请延期', submit: '提交完成', accept: '确认验收',
  reopen: '重新打开', archive: '归档任务', delete: '删除任务',
})[actionKind.value])

function readable(reason: unknown) {
  if (reason instanceof ApiError) return reason.traceId ? `${reason.message}（追踪号：${reason.traceId}）` : reason.message
  return '任务操作失败，请稍后重试'
}

function historyLabel(code: string) {
  return ({
    CREATED: '创建任务', UPDATED: '编辑任务', STATUS_CHANGED: '更新状态', TRANSFERRED: '转派任务',
    DELAY_REQUESTED: '申请延期', COMPLETION_SUBMITTED: '提交完成', COMPLETION_ACCEPTED: '确认验收',
    REOPENED: '重新打开', ARCHIVED: '归档任务', DELETED: '删除任务', COMMENTED: '添加评论',
    PROGRESS_RECORDED: '记录进度', ATTACHMENT_ADDED: '添加附件', ATTACHMENT_REMOVED: '移除附件',
  } as Record<string, string>)[code] || '更新任务'
}

function openAction(kind: ActionKind) {
  actionKind.value = kind
  Object.assign(actionForm, {
    memberId: undefined,
    dueTime: kind === 'delay' ? props.task.dueAt || '' : '',
    comment: '',
    actualHours: props.task.actualHours,
  })
  actionOpen.value = true
}

function memberPayload(memberId: number | undefined) {
  return props.context === 'system' ? { ownerTenantMemberId: memberId } : { ownerAccountId: memberId }
}

async function runAction() {
  if (!props.token) return
  const endpoint = ({
    transfer: 'transfer', delay: 'delay', submit: 'submit-completion', accept: 'accept-completion',
    reopen: 'reopen', archive: 'archive', delete: 'delete',
  } as const)[actionKind.value]
  let body: Record<string, unknown> = { expectedVersion: props.task.version }
  if (actionKind.value === 'transfer') {
    if (!actionForm.memberId) return message.warning('请选择新负责人')
    body = { ...body, ...memberPayload(actionForm.memberId), comment: actionForm.comment || undefined }
  } else if (actionKind.value === 'delay') {
    if (!actionForm.dueTime || !actionForm.comment.trim()) return message.warning('请填写新的截止时间和延期原因')
    body = { ...body, dueTime: actionForm.dueTime, reason: actionForm.comment.trim() }
  } else if (actionKind.value === 'submit') {
    if (!actionForm.comment.trim()) return message.warning('请填写完成说明')
    body = { ...body, completionNote: actionForm.comment.trim(), actualHours: actionForm.actualHours }
  } else {
    if (!actionForm.comment.trim()) return message.warning('请填写操作说明')
    body = { ...body, comment: actionForm.comment.trim() }
  }
  busy.value = true
  try {
    if (actionKind.value === 'delete') {
      await api<boolean>(`/api/work/tasks/${props.task.id}/${endpoint}`, { method: 'POST', body: JSON.stringify(body) }, props.token)
      message.success('任务已删除，历史记录仍保留')
      actionOpen.value = false
      emit('deleted', props.task.id)
      return
    }
    const updated = await api<WorkTask>(`/api/work/tasks/${props.task.id}/${endpoint}`, {
      method: 'POST', body: JSON.stringify(body),
    }, props.token)
    message.success(`${actionTitle.value}成功`)
    actionOpen.value = false
    emit('updated', updated)
  } catch (reason) { message.error(readable(reason)) }
  finally { busy.value = false }
}

async function saveProgress() {
  progressBusy.value = true
  try {
    const updated = await api<WorkTask>(`/api/work/tasks/${props.task.id}/progress`, {
      method: 'POST', body: JSON.stringify({ expectedVersion: props.task.version, ...progressForm }),
    }, props.token)
    message.success('任务进度已记录')
    emit('updated', updated)
  } catch (reason) { message.error(readable(reason)) }
  finally { progressBusy.value = false }
}

async function addComment() {
  if (!commentText.value.trim()) return
  commentBusy.value = true
  try {
    const updated = await api<WorkTask>(`/api/work/tasks/${props.task.id}/comments`, {
      method: 'POST', body: JSON.stringify({ comment: commentText.value.trim() }),
    }, props.token)
    commentText.value = ''
    message.success('评论已添加')
    emit('updated', updated)
  } catch (reason) { message.error(readable(reason)) }
  finally { commentBusy.value = false }
}
</script>

<template>
  <div class="work-task-detail">
    <header class="work-task-detail__heading">
      <div><p class="eyebrow">{{ task.taskType === 'PROJECT' ? '项目任务' : '普通任务' }}</p><h2>{{ task.title }}</h2><p>{{ task.description || '没有补充说明' }}</p></div>
      <ProductStatusTag :status="task.overdue ? 'OVERDUE' : task.status" :label="task.statusName" />
    </header>

    <div class="work-task-detail__actions">
      <a-button v-if="task.actions.canEdit" @click="emit('edit', task)">编辑</a-button>
      <a-button v-if="task.actions.canTransfer" @click="openAction('transfer')">转派</a-button>
      <a-button v-if="task.actions.canDelay" @click="openAction('delay')">申请延期</a-button>
      <a-button v-if="task.actions.canSubmitCompletion" type="primary" @click="openAction('submit')">提交完成</a-button>
      <a-button v-if="task.actions.canAcceptCompletion" type="primary" @click="openAction('accept')">确认验收</a-button>
      <a-button v-if="task.actions.canReopen" @click="openAction('reopen')">重新打开</a-button>
      <a-button v-if="task.actions.canArchive" @click="openAction('archive')">归档</a-button>
      <a-button v-if="task.actions.canDelete" danger @click="openAction('delete')">删除</a-button>
    </div>

    <a-descriptions bordered :column="2" size="small">
      <a-descriptions-item label="负责人">{{ task.owner.displayName }}</a-descriptions-item>
      <a-descriptions-item label="优先级">{{ ({ LOW: '低', NORMAL: '普通', HIGH: '高', URGENT: '紧急' })[task.priority] }}</a-descriptions-item>
      <a-descriptions-item label="协作人">{{ task.collaborators.map(item => item.displayName).join('、') || '无' }}</a-descriptions-item>
      <a-descriptions-item label="关注人">{{ task.followers.map(item => item.displayName).join('、') || '无' }}</a-descriptions-item>
      <a-descriptions-item label="验收人">{{ task.acceptors.map(item => item.displayName).join('、') || '创建人或项目管理者' }}</a-descriptions-item>
      <a-descriptions-item label="进度">{{ task.progressPercent }}%</a-descriptions-item>
      <a-descriptions-item label="预计 / 实际工时">{{ task.estimatedHours ?? '—' }} / {{ task.actualHours ?? '—' }} 小时</a-descriptions-item>
      <a-descriptions-item label="截止时间">{{ task.dueAt ? productDateTime(task.dueAt) : '未设置' }}</a-descriptions-item>
      <a-descriptions-item v-if="task.completionNote" label="完成说明" :span="2">{{ task.completionNote }}</a-descriptions-item>
      <a-descriptions-item v-if="task.businessTitle" label="关联业务" :span="2">
        {{ task.businessTitle }}
        <a-button v-if="context === 'system' && linkedModuleCode && task.businessId" type="link" size="small" @click="openLinkedBusiness">打开业务记录</a-button>
      </a-descriptions-item>
    </a-descriptions>

    <div v-if="task.labels.length" class="work-task-detail__tags"><a-tag v-for="label in task.labels" :key="label">{{ label }}</a-tag></div>
    <section v-if="task.checklist.length" class="work-task-section">
      <div class="panel-title"><strong>检查清单</strong><span>{{ task.checklist.filter(item => item.completed).length }} / {{ task.checklist.length }}</span></div>
      <a-checkbox v-for="item in task.checklist" :key="item.id" :checked="item.completed" disabled>{{ item.text }}</a-checkbox>
    </section>

    <section v-if="task.actions.canEdit" class="work-task-section">
      <div class="panel-title"><strong>记录进度</strong><span>完成操作需提交验收</span></div>
      <div class="work-task-detail__progress-form">
        <a-input-number v-model:value="progressForm.progressPercent" :min="0" :max="100" addon-after="%" />
        <a-input-number v-model:value="progressForm.actualHours" :min="0" :precision="2" addon-after="小时" placeholder="实际工时" />
        <a-input v-model:value="progressForm.comment" placeholder="本次进展说明（可选）" />
        <a-button type="primary" :loading="progressBusy" @click="saveProgress">保存进度</a-button>
      </div>
    </section>

    <BusinessAttachmentsPanel v-if="canFileView" :endpoint="`/api/business-attachments/work-tasks/${task.id}`" :context="context" :writable="canFileWrite" title="任务附件" description="说明文档、交付物和处理结果保存在当前任务内。" compact />

    <section class="work-task-section">
      <div class="panel-title"><strong>子任务</strong><a-button v-if="task.actions.canEdit" type="link" @click="emit('addSubtask', task)">新建子任务</a-button></div>
      <button v-for="subtask in task.subtasks" :key="subtask.id" class="work-task-subtask" @click="emit('openSubtask', subtask.id)">
        <span><strong>{{ subtask.title }}</strong><small>{{ subtask.owner.displayName }} · {{ subtask.statusName }}</small></span><span>{{ subtask.progressPercent }}%</span>
      </button>
      <a-empty v-if="!task.subtasks.length" description="暂无子任务" :image="false" />
    </section>

    <section v-if="task.actions.canComment" class="work-task-section">
      <div class="panel-title"><strong>评论</strong></div>
      <div class="work-task-detail__comment"><a-textarea v-model:value="commentText" :rows="2" placeholder="写下协作说明或处理结论" /><a-button type="primary" :loading="commentBusy" @click="addComment">添加评论</a-button></div>
    </section>

    <section v-if="task.linkedLogs.length" class="work-task-section">
      <div class="panel-title"><strong>关联工作日志</strong><span>{{ task.linkedLogs.length }} 条</span></div>
      <article v-for="log in task.linkedLogs" :key="log.id" class="work-task-log"><strong>{{ log.title }}</strong><small>{{ log.authorName }} · {{ log.workDate }}</small><p>{{ log.content }}</p></article>
    </section>

    <section class="work-task-section">
      <div class="panel-title"><strong>处理记录</strong><span>{{ task.history.length }} 条</span></div>
      <a-timeline v-if="task.history.length"><a-timeline-item v-for="item in task.history" :key="item.id"><strong>{{ historyLabel(item.actionCode) }}</strong><p>{{ item.comment || '任务信息已保存' }}</p><small>{{ item.changedByName }} · {{ productDateTime(item.changedAt) }}</small></a-timeline-item></a-timeline>
      <a-empty v-else description="暂无处理记录" :image="false" />
    </section>
  </div>

  <a-modal v-model:open="actionOpen" :title="actionTitle" :confirm-loading="busy" @ok="runAction">
    <a-form layout="vertical">
      <a-form-item v-if="actionKind === 'transfer'" label="新负责人" required><PersonSelect v-model="actionForm.memberId" :people="people" :value-key="context === 'system' ? 'tenantMemberId' : 'accountId'" :allow-clear="false" /></a-form-item>
      <a-form-item v-if="actionKind === 'delay'" label="新的截止时间" required><a-date-picker v-model:value="actionForm.dueTime" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" /></a-form-item>
      <a-form-item v-if="actionKind === 'submit'" label="实际工时"><a-input-number v-model:value="actionForm.actualHours" :min="0" :precision="2" addon-after="小时" style="width:100%" /></a-form-item>
      <a-form-item :label="actionKind === 'submit' ? '完成说明' : actionKind === 'delay' ? '延期原因' : '操作说明'" required><a-textarea v-model:value="actionForm.comment" :rows="3" /></a-form-item>
      <a-alert v-if="actionKind === 'delete'" type="warning" show-icon message="删除后任务从列表隐藏，审计和历史记录继续保留。" />
    </a-form>
  </a-modal>
</template>

<style scoped>
.work-task-detail{display:grid;gap:16px}.work-task-detail__heading{display:flex;justify-content:space-between;gap:16px}.work-task-detail__heading h2{margin:0 0 6px}.work-task-detail__heading p{margin:0;color:var(--text-secondary)}.work-task-detail__actions{display:flex;flex-wrap:wrap;gap:8px}.work-task-detail__tags{display:flex;gap:6px;flex-wrap:wrap}.work-task-section{display:grid;gap:10px;padding-top:8px}.work-task-detail__progress-form{display:grid;grid-template-columns:120px 150px minmax(180px,1fr) auto;gap:8px}.work-task-detail__comment{display:grid;grid-template-columns:1fr auto;gap:8px;align-items:end}.work-task-subtask{display:flex;justify-content:space-between;align-items:center;width:100%;padding:10px 12px;border:1px solid var(--border-color);border-radius:8px;background:var(--surface);text-align:left}.work-task-subtask span:first-child{display:grid}.work-task-subtask small,.work-task-log small{color:var(--text-secondary)}.work-task-log{padding:10px 0;border-bottom:1px solid var(--border-color)}.work-task-log p{margin:6px 0 0}@media(max-width:760px){.work-task-detail__progress-form{grid-template-columns:1fr 1fr}.work-task-detail__comment{grid-template-columns:1fr}}
</style>
