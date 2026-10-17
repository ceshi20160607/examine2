<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Empty, message } from 'ant-design-vue'
import { ApiError, api } from '../api'
import { productDateTime } from '../presentation'
import { allowsPermission } from '../permissions'
import { activeRuntimeTask, runtimeIdempotency, runtimeStatusLabel } from '../flow-runtime'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import PersonSelect from '../components/PersonSelect.vue'
import BusinessAttachmentsPanel from '../components/BusinessAttachmentsPanel.vue'
import RuntimeBusinessViewPanel from '../components/RuntimeBusinessViewPanel.vue'
import FlowTaskFormEditor from '../components/FlowTaskFormEditor.vue'
import AiApprovalSummary from '../components/AiApprovalSummary.vue'
import type { BusinessAttachmentView, FlowRuntimeCatalogItem, FlowManualNodePreview, FlowManualNodeResult, FlowRuntimeActionResult, FlowRuntimeException, FlowRuntimeInstance, FlowRuntimeTask, FlowTaskDelegation, FlowParticipantDirectory, FlowRuntimeNodeRequest, FlowRuntimeNodePreview, PeopleDirectoryOption } from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system'; initialInstanceId?: number; initialTaskId?: number }>(), { context: 'system' })
const route = useRoute()
const router = useRouter()
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const flows = ref<FlowRuntimeCatalogItem[]>([])
const instances = ref<FlowRuntimeInstance[]>([])
const directory = ref<FlowParticipantDirectory>({ departments: [], people: [], permissionVersion: 0 })
const directoryError = ref('')
const selected = ref<FlowRuntimeInstance>()
function showSummarySource(eventId: number) {
  if (!selected.value?.history.some(event => event.id === eventId)) return
  document.getElementById(`flow-history-${selected.value.id}-${eventId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
const loading = ref(false)
const runtimeError = ref('')
const busy = ref('')
const startOpen = ref(false)
const actionOpen = ref(false)
const manualOpen = ref(false)
const runtimeNodeOpen = ref(false)
const incidentOpen = ref(false)
const manualPreview = ref<FlowManualNodePreview>()
const runtimeNodePreview = ref<FlowRuntimeNodePreview>()
const runtimeNodeSubmission = ref<FlowRuntimeNodeRequest>()
const runtimeNodeError = ref('')
const runtimeNodeNeedsRefresh = ref(false)
const selectedAction = ref('APPROVE')
const actionTaskId = ref<number>()
const actionTaskVersion = ref<number>()
const taskFormReady = ref(false)
const taskFormEditor = ref<InstanceType<typeof FlowTaskFormEditor>>()
const actionAttachmentIds = ref<number[]>([])
const collaborationText = ref('')
const startForm = reactive({ flowId: undefined as number | undefined, title: '' })
const actionForm = reactive({ comment: '', targetPersonId: undefined as number | undefined, delegationEndTime: '' })
const manualForm = reactive({ assigneeTenantMemberId: undefined as number | undefined, reason: '' })
const manualIdempotencyKey = ref('')
const runtimeNodeForm = reactive({ nodeType: 'APPROVAL' as 'APPROVAL' | 'NOTIFICATION', name: '', assigneeIds: [] as number[],
  position: 'AFTER_CURRENT' as FlowRuntimeNodeRequest['position'], anchorNodeKey: undefined as string | undefined,
  approvalMode: 'OR_SIGN' as 'OR_SIGN' | 'ALL_SIGN' | 'SEQUENTIAL', reason: '' })
const runtimeNodeIdempotencyKey = ref('')
const selectedIncident = ref<FlowRuntimeException>()
const incidentForm = reactive({ actionCode: 'RETRY', comment: '' })
const activeView = ref<'pending' | 'mine' | 'instances' | 'records'>('pending')
let active = true
let generation = 0
let loadGeneration = 0
let selectionGeneration = 0
let dialogGeneration = 0
let operationGeneration = 0
let manualPreviewGeneration = 0
let runtimeNodePreviewGeneration = 0
let changingViewForPreferredInstance = false
const identity = computed(() => {
  const context = current.value
  return JSON.stringify([props.context, context?.platformId, context?.accountId, context?.systemId,
    context?.tenantId, context?.memberId, context?.tenantMemberId, context?.contextRevision,
    context?.roleIds, context?.permissions, context?.dataScopes, context?.tenantSwitchContext])
})

const publishedFlows = computed(() => flows.value.filter(flow => !!flow.currentVersionId))
const currentTask = computed(() => activeRuntimeTask(selected.value))
// 会签同时有多名当前处理人，摘要不能只取首个待办而掩盖其他人的处理责任。
const currentTasks = computed(() => selected.value?.tasks.filter(task => task.status === 'PENDING' && !task.completedAt) || [])
const currentHandlerNames = computed(() => [...new Set(currentTasks.value.flatMap(task => {
  const delegation = taskDelegation(task)
  if (delegation?.effective) return [delegation.principalName, `${delegation.delegateName}（代理）`]
  return task.assigneeName ? [task.assigneeName] : task.candidates.map(candidate => candidate.displayName)
}))].join('、'))
const handlerTask = computed(() => {
  const tasks = selected.value?.tasks.filter(task => task.status === 'PENDING' && !task.completedAt && taskMine(task)) || []
  return tasks.find(task => task.id === props.initialTaskId)
    || tasks.find(task => task.nodeKey === selected.value?.currentNodeKey) || tasks[0]
})
const actionTask = computed(() => selected.value?.tasks.find(task => task.id === actionTaskId.value))
const actionTaskIsForm = computed(() => actionTask.value?.taskType === 'FORM_TASK')
const actionTaskIsReturn = computed(() => actionTask.value?.taskType === 'RETURN_TASK')
const actionIsClaim = computed(() => ['CLAIM', 'UNCLAIM'].includes(selectedAction.value))
const actionIsSignChange = computed(() => ['ADD_SIGN', 'REMOVE_SIGN'].includes(selectedAction.value))
const actionIsDelegation = computed(() => ['DELEGATE', 'REVOKE_DELEGATION'].includes(selectedAction.value))
const actionAttachmentReady = computed(() => !actionTask.value?.attachmentRequired
  || actionAttachmentIds.value.length >= actionTask.value.minimumAttachmentCount)
const comments = computed(() => selected.value?.actions.filter(action => action.actionCode === 'COMMENT') || [])
const branchExecutions = computed(() => selected.value?.executions.some(execution => execution.resultCode?.startsWith('SPLIT_')
  || execution.resultCode?.startsWith('JOIN')) ? selected.value.executions : [])
const subflowLinks = computed(() => selected.value?.subflows || [])
const signApprovalMode = computed(() => {
  // 只使用服务端公开的可信方式；旧响应缺少此字段时必须刷新，不从任务数量猜测。
  const mode = actionTask.value?.assigneeSnapshot.approvalMode
  return mode === 'OR_SIGN' || mode === 'ALL_SIGN' || mode === 'SEQUENTIAL' ? mode : undefined
})
const sameNodeTasks = computed(() => {
  const executionId = actionTask.value?.assigneeSnapshot.executionId
  return selected.value?.tasks.filter(task => task.nodeKey === actionTask.value?.nodeKey
    && (executionId === undefined || task.assigneeSnapshot.executionId === executionId)) || []
})
const signChangeHelp = computed(() => {
  if (selectedAction.value === 'REMOVE_SIGN') return '只移除同节点其他尚未完成的处理人或候选；已完成的审批保留，必须保留有效处理路径。'
  return signApprovalMode.value === 'OR_SIGN' ? '或签：新增人员进入当前候选池；启用领取时，取消领取后新增候选也可领取。'
    : signApprovalMode.value === 'ALL_SIGN' ? '会签：新增处理人也必须同意，其待办沿用当前任务的绝对截止时间。'
      : signApprovalMode.value === 'SEQUENTIAL' ? '顺序审批：新增处理人排在现有顺序末尾，当前处理任务保持不变。' : '当前响应没有可信审批方式，请刷新流程后重试。'
})
// 只消费服务端当前处理人的窄政策；旧响应仍保留拒绝、退回的必填保障。
const actionOpinionRequired = computed(() => !actionIsClaim.value && (['WITHDRAW', 'TERMINATE'].includes(selectedAction.value)
  || (selected.value?.opinionRequiredActions ?? ['REJECT', 'RETURN']).includes(selectedAction.value)))
const actionCommentPlaceholder = computed(() => {
  if (actionIsClaim.value) return selectedAction.value === 'CLAIM' ? '可选填写领取说明' : '可选填写取消领取说明'
  if (selectedAction.value === 'APPROVE') {
    return actionOpinionRequired.value ? actionTaskIsForm.value ? '请填写本次提交说明（必填）' : '请填写审批意见（必填）'
      : actionTaskIsForm.value ? '可选填写本次提交说明' : '可选填写审批意见'
  }
  if (selectedAction.value === 'RESUBMIT') return '可选填写本次修正说明，退回原因和字段修改会保留在完整历史中'
  return actionOpinionRequired.value ? '请填写可追溯的处理意见或原因（必填）' : '可选填写本次处理意见或说明'
})
const taskAndInstanceActions = computed(() => selected.value?.allowedActions
  .filter(action => !['ADD_RUNTIME_NODE', 'COMMENT', 'REMIND'].includes(action)) || [])
const canAddRuntimeNode = computed(() => can('ADD_MANUAL_NODE') && selected.value?.status === 'WAITING'
  && selected.value.allowedActions.includes('ADD_RUNTIME_NODE'))
// 仅服务端提供的尚未进入节点可选；不从公开快照推测哪些历史节点仍能插入。
const runtimeNodeAnchors = computed(() => (selected.value?.runtimeNodeAnchors || []).filter(anchor =>
  runtimeNodeForm.position === 'BEFORE_NODE' ? anchor.beforeAllowed
    : runtimeNodeForm.position === 'AFTER_NODE' ? anchor.afterAllowed : false))
const runtimeNodePositionValid = computed(() => runtimeNodeForm.position === 'AFTER_CURRENT'
  || runtimeNodeForm.position === 'BEFORE_CURRENT'
  || runtimeNodeAnchors.value.some(anchor => anchor.nodeKey === runtimeNodeForm.anchorNodeKey))
const participantValueKey = computed(() => props.context === 'platform' ? 'accountId' : 'tenantMemberId')
const excludedTransferIds = computed(() => {
  const id = props.context === 'platform' ? current.value?.accountId : current.value?.tenantMemberId
  return id ? [id] : []
})
const signTargetPeople = computed<PeopleDirectoryOption[]>(() => {
  if (selectedAction.value === 'ADD_SIGN') {
    // 已同意人员同样属于原参与范围；只有已取消任务不再占用加签候选。
    const existing = new Set(sameNodeTasks.value.filter(task => task.status !== 'CANCELLED')
      .flatMap(task => task.candidates.map(candidate => candidate[participantValueKey.value])))
    return directory.value.people.filter(person => {
      const id = person[participantValueKey.value]
      return typeof id === 'number' && Number.isSafeInteger(id) && id > 0 && !existing.has(id)
    })
  }
  const people = new Map<number, PeopleDirectoryOption>()
  for (const task of sameNodeTasks.value.filter(item => ['PENDING', 'QUEUED'].includes(item.status))) {
    for (const candidate of task.candidates) {
      const id = candidate[participantValueKey.value]
      if (typeof id !== 'number' || !Number.isSafeInteger(id) || id <= 0 || excludedTransferIds.value.includes(id)) continue
      // 减签使用既有任务候选，停用或目录暂不可用的原参与者仍可被明确移除。
      people.set(id, directory.value.people.find(person => person[participantValueKey.value] === id) || {
        ...(props.context === 'platform' ? { accountId: id } : { tenantMemberId: id }),
        displayName: candidate.displayName, departmentName: candidate.departmentName, positionTitle: candidate.positionTitle,
      })
    }
  }
  return [...people.values()]
})
const visibleInstances = computed(() => instances.value.filter(instance => {
  if (activeView.value === 'mine') return instance.startedByCurrentUser === true
  if (activeView.value === 'pending') return instance.tasks.some(task =>
    taskMine(task) && !task.completedAt && task.status === 'PENDING')
  if (activeView.value === 'records') return Boolean(instance.finishedAt || instance.actions.length || instance.exceptions.length)
  return true
}))

function can(action: string) {
  return allowsPermission(current.value?.permissions, 'FLOW', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'FLOW', '*', action)
}
function canFile(action: string) {
  return allowsPermission(current.value?.permissions, 'FILE', 'OBJECT', action)
    || allowsPermission(current.value?.permissions, 'FILE', '*', action)
}
function requestContext() {
  return { generation, token: token.value, identity: identity.value }
}
function requestCurrent(context: ReturnType<typeof requestContext>) {
  return active && context.generation === generation && context.token === token.value
    && context.identity === identity.value && can('VIEW_RUNTIME')
}
function resetDialogs() {
  dialogGeneration++
  operationGeneration++
  manualPreviewGeneration++
  runtimeNodePreviewGeneration++
  taskFormEditor.value?.clear()
  startOpen.value = actionOpen.value = manualOpen.value = runtimeNodeOpen.value = incidentOpen.value = false
  manualPreview.value = undefined
  manualIdempotencyKey.value = ''
  runtimeNodePreview.value = undefined
  runtimeNodeSubmission.value = undefined
  runtimeNodeIdempotencyKey.value = ''
  runtimeNodeError.value = ''
  runtimeNodeNeedsRefresh.value = false
  actionAttachmentIds.value = []
  collaborationText.value = ''
  selectedIncident.value = undefined
  Object.assign(startForm, { flowId: undefined, title: '' })
  Object.assign(actionForm, { comment: '', targetPersonId: undefined, delegationEndTime: '' })
  actionTaskId.value = actionTaskVersion.value = undefined
  taskFormReady.value = false
  Object.assign(manualForm, { assigneeTenantMemberId: undefined, reason: '' })
  Object.assign(runtimeNodeForm, { nodeType: 'APPROVAL', name: '', position: 'AFTER_CURRENT', anchorNodeKey: undefined, assigneeIds: [], approvalMode: 'OR_SIGN', reason: '' })
  Object.assign(incidentForm, { actionCode: 'RETRY', comment: '' })
  busy.value = ''
}
function clearRuntime() {
  // 当前身份失效时释放旧实例、候选人和意见；迟到请求不能重新写回。
  generation++
  loadGeneration++
  selectionGeneration++
  resetDialogs()
  flows.value = []
  instances.value = []
  directory.value = { departments: [], people: [], permissionVersion: 0 }
  directoryError.value = ''
  selected.value = undefined
  loading.value = false
  runtimeError.value = ''
}
function operationContext() {
  return { ...requestContext(), operation: ++operationGeneration,
    dialog: dialogGeneration, instanceId: selected.value?.id }
}
function operationCurrent(context: ReturnType<typeof operationContext>) {
  return requestCurrent(context) && context.operation === operationGeneration
    && context.dialog === dialogGeneration && context.instanceId === selected.value?.id
}
function handleError(error: unknown) {
  if (error instanceof ApiError && [401, 403, 404].includes(error.httpStatus || 0)) {
    clearRuntime()
    // 失权清空不等于业务列表为空；提示保留到真正成功重读或切换身份。
    runtimeError.value = '当前记录已清除，请刷新权限后重新进入。'
    message.error('当前身份无法查看或处理这些流程，已清除原记录，请刷新当前权限后重试。')
  } else {
    runtimeError.value = `流程读取或处理未成功：${describeError(error)}`
    message.error(describeError(error))
  }
}
function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
}
function statusColor(status: string) {
  if (status === 'COMPLETED') return 'green'
  if (status === 'WAITING') return 'blue'
  if (status === 'EXCEPTION' || status === 'REJECTED') return 'red'
  return 'default'
}
function taskStatusColor(status: string) {
  return ({ PENDING: 'blue', SUSPENDED: 'orange', COMPLETED: 'green', APPROVE: 'green', RESUBMIT: 'green', REJECT: 'red', RETURN: 'purple' } as Record<string, string>)[status] || 'default'
}
function taskStatusLabel(status: string, taskType?: string) {
  if (taskType === 'RETURN_TASK' && status === 'PENDING') return '待修正重提'
  if (taskType === 'RETURN_TASK' && status === 'RESUBMIT') return '已重新提交'
  if (taskType === 'FORM_TASK' && status === 'PENDING') return '待填写'
  if (taskType === 'FORM_TASK' && status === 'APPROVE') return '已完成填写'
  return ({ PENDING: '待处理', QUEUED: '待轮到', SUSPENDED: '已暂停', COMPLETED: '已完成', APPROVE: '已同意', REJECT: '已拒绝', RETURN: '已退回', RESUBMIT: '已重新提交', CANCELLED: '已取消' } as Record<string, string>)[status] || status
}
/** 使用当前节点的真实任务，避免填写任务被标成等待审批。 */
function instanceStatusLabel(instance: FlowRuntimeInstance) {
  if (instance.status === 'WAITING' && instance.jobs.some(job => job.status === 'PENDING'
    && job.nodeKey === instance.currentNodeKey && job.jobType === 'TIMER')) return '等待时间'
  if (instance.status === 'WAITING' && instance.jobs.some(job => job.status === 'PENDING'
    && job.nodeKey === instance.currentNodeKey && job.jobType === 'WEBHOOK_DELIVERY')) return '等待应用回调'
  if (instance.status === 'WAITING' && instance.tasks.some(task => task.status === 'PENDING'
    && task.nodeKey === instance.currentNodeKey && task.taskType === 'RETURN_TASK')) return '待发起人修正'
  if (instance.status === 'WAITING' && instance.tasks.some(task => task.status === 'PENDING'
    && task.nodeKey === instance.currentNodeKey && task.taskType === 'FORM_TASK')) return '待填写'
  return runtimeStatusLabel(instance.status)
}
function historyStatusLabel(status: string) {
  if (status === 'ACTIVE') return '执行中'
  return ['PENDING', 'QUEUED', 'SUSPENDED', 'APPROVE', 'REJECT', 'RETURN', 'RESUBMIT', 'CANCELLED'].includes(status)
    ? taskStatusLabel(status) : runtimeStatusLabel(status)
}
function historyTransition(event: FlowRuntimeInstance['history'][number]) {
  const label = (status: string) => status === 'WAITING' && event.eventType.startsWith('FLOW_WAIT_')
    ? '等待时间' : status === 'WAITING'
      && event.eventType.startsWith('FLOW_WEBHOOK_')
      ? '等待应用回调' : status === 'WAITING'
      && (event.eventType.startsWith('FLOW_MESSAGE_') || event.eventName.includes('站内消息'))
      ? '等待投递' : historyStatusLabel(status)
  if (event.beforeStatus && event.afterStatus) {
    if (event.beforeStatus === event.afterStatus) return `状态：${label(event.afterStatus)}`
    return `${label(event.beforeStatus)} → ${label(event.afterStatus)}`
  }
  if (event.afterStatus) return `状态：${label(event.afterStatus)}`
  if (event.beforeStatus) return `当时状态：${label(event.beforeStatus)}`
  return ''
}
function gatewayHistoryDescription(event: FlowRuntimeInstance['history'][number]) {
  if (!event.eventType.startsWith('GATEWAY_')) return ''
  const detail = event.detail || {}
  if (event.eventType === 'GATEWAY_SPLIT') {
    const mode = detail.gatewayMode === 'PARALLEL' ? '并行' : '包容'
    return `${mode}网关本次启动 ${Number(detail.selectedCount || 0)} 条分支`
  }
  const arrived = Number(detail.arrivedCount || 0), expected = Number(detail.expectedCount || 0)
  return expected ? `已到达 ${arrived} / ${expected} 条必需分支${detail.duplicate === true ? '，本次重复到达未重复推进' : ''}` : ''
}
function subflowHistoryDescription(event: FlowRuntimeInstance['history'][number]) {
  if (!event.eventType.startsWith('SUBFLOW_')) return ''
  const detail = event.detail || {}
  const childInstanceId = Number(detail.childInstanceId || 0)
  const child = childInstanceId > 0 ? `子实例 ${childInstanceId}` : '子流程'
  if (event.eventType === 'SUBFLOW_STARTED') return `${child}已启动，父流程等待其完成`
  if (event.eventType === 'SUBFLOW_COMPLETED') return `${child}已完成，返回 ${Number(detail.outputCount || 0)} 个变量并继续父流程`
  if (event.eventType === 'SUBFLOW_CHILD_EXCEPTION') return `${child}发生异常，父流程保持等待，可进入子流程恢复`
  return ''
}
function automaticHistoryDescription(event: FlowRuntimeInstance['history'][number]) {
  if (event.eventType !== 'BUSINESS_DATA_ACTION') return ''
  const detail = event.detail || {}, operation = detail.operation === 'CREATE' ? '新建' : '更新'
  const moduleName = String(detail.moduleName || detail.moduleCode || '业务模块')
  const recordId = Number(detail.recordId || 0), fields = Array.isArray(detail.fieldNames) ? detail.fieldNames : []
  return `${operation}${moduleName}${recordId > 0 ? `记录 #${recordId}` : '记录'}，处理 ${fields.length} 个字段${detail.replayed === true ? '；本次为安全重放，未重复写入' : ''}`
}
function messageHistoryDescription(event: FlowRuntimeInstance['history'][number]) {
  if (!event.eventType.startsWith('FLOW_MESSAGE_')) return ''
  const detail = event.detail || {}, kind = ({ CC: '抄送', NOTIFICATION: '通知', MESSAGE: '消息' } as Record<string, string>)[String(detail.messageKind)] || '站内消息'
  if (event.eventType === 'FLOW_MESSAGE_RETRY') {
    return `${kind}第 ${Number(detail.attemptCount || 0)} 次投递未完成，系统将按发布策略重试`
  }
  const count = Number(detail.recipientCount || 0)
  return `${kind}已送达 ${count} 人${detail.replayed === true ? '；本次为安全重放，未重复生成消息' : ''}`
}
function webhookHistoryDescription(event: FlowRuntimeInstance['history'][number]) {
  if (!event.eventType.startsWith('FLOW_WEBHOOK_')) return ''
  const detail = event.detail || {}, application = String(detail.applicationCode || '已发布应用')
  if (event.eventType === 'FLOW_WEBHOOK_RETRY') {
    return `${application}回调第 ${Number(detail.attemptCount || 0)} 次投递失败，已按冻结策略安排重试`
  }
  return `${application} V${Number(detail.applicationVersion || 0)} 回调已送达${detail.responseStatus ? `，响应 ${detail.responseStatus}` : ''}${detail.replayed === true ? '；本次为人工触发的安全重试' : ''}`
}
function waitHistoryDescription(event: FlowRuntimeInstance['history'][number]) {
  if (!event.eventType.startsWith('FLOW_WAIT_')) return ''
  const detail = event.detail || {}, absolute = detail.waitMode === 'ABSOLUTE'
  if (event.eventType === 'FLOW_WAIT_SCHEDULED') {
    const due = typeof detail.nextRunAt === 'string' ? productDateTime(detail.nextRunAt) : '配置时间'
    return absolute ? `将在 ${due} 到达后继续` : `等待 ${Number(detail.durationMinutes || 0)} 分钟，计划于 ${due} 继续`
  }
  return absolute ? '定时器已到达，本节点只恢复一次' : '等待时间已结束，本节点只恢复一次'
}
function subflowRelationLabel(relation: 'PARENT' | 'CHILD') {
  return relation === 'PARENT' ? '父流程' : '子流程'
}
function executionNodeName(nodeKey: string) {
  const nodes = selected.value?.definitionSnapshot.nodes
  if (!Array.isArray(nodes)) return nodeKey
  const node = nodes.find(item => item && typeof item === 'object' && !Array.isArray(item)
    && (item as Record<string, unknown>).nodeKey === nodeKey) as Record<string, unknown> | undefined
  return typeof node?.name === 'string' && node.name.trim() ? node.name : nodeKey
}
function executionResultLabel(result?: string) {
  if (!result) return '执行中'
  if (result.startsWith('SPLIT_')) return `已启动 ${result.slice(6)} 条分支`
  if (result === 'JOIN_WAITING') return '等待其他分支'
  if (result === 'JOINED') return '已汇合并继续'
  if (result === 'JOIN_DUPLICATE') return '重复到达已忽略'
  return ({ COMPLETED: '已完成', APPROVE: '已同意', REPEAT_APPROVAL: '自动通过', STARTED: '已发起' } as Record<string, string>)[result] || result
}
function manualReason(task: FlowRuntimeTask) {
  return String(task.assigneeSnapshot.reason || '')
}
function taskDelegation(task: FlowRuntimeTask | undefined): FlowTaskDelegation | undefined {
  const delegation = task?.assigneeSnapshot.delegation
  return delegation && typeof delegation === 'object' && !Array.isArray(delegation) ? delegation : undefined
}
function delegationStatusLabel(status: string) {
  return ({ ACTIVE: '代办生效中', REVOKED: '已撤销', EXPIRED: '已到期' } as Record<string, string>)[status] || status
}
function defaultDelegationEndTime() {
  const date = new Date(Date.now() + 24 * 60 * 60 * 1000)
  const part = (value: number) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${part(date.getMonth() + 1)}-${part(date.getDate())}T${part(date.getHours())}:${part(date.getMinutes())}:00`
}
/** 任务名称只来自服务器公开节点名称，不读取节点业务配置或流程变量。 */
function taskNodeName(task: FlowRuntimeTask, index: number) {
  const nodes = selected.value?.definitionSnapshot.nodes
  if (Array.isArray(nodes)) {
    const node = nodes.find(value => value && typeof value === 'object' && !Array.isArray(value)
      && (value as Record<string, unknown>).nodeKey === task.nodeKey) as Record<string, unknown> | undefined
    if (typeof node?.name === 'string' && node.name.trim()) return node.name.trim()
  }
  return task.taskType === 'MANUAL_APPROVAL' ? '手动加签审批'
    : task.taskType === 'FORM_TASK' ? '填写表单任务' : task.taskType === 'RETURN_TASK' ? '退回修正任务' : `流程任务 ${index + 1}`
}
function taskApprovalRule(task: FlowRuntimeTask) {
  if (task.assigneeSnapshot.approvalMode !== 'ALL_SIGN') return ''
  const policy = task.assigneeSnapshot.allSignCompletionPolicy
  if (policy && typeof policy === 'object' && !Array.isArray(policy)
    && (policy as Record<string, unknown>).mode === 'RATIO') {
    const ratio = (policy as Record<string, unknown>).ratioPercent
    return typeof ratio === 'number' ? `达到 ${ratio}% 同意后通过` : '按冻结比例通过'
  }
  return '全部处理人同意后通过'
}
function taskReturnRule(task: FlowRuntimeTask) {
  if (task.taskType !== 'RETURN_TASK') return ''
  const target = String(task.assigneeSnapshot.returnNodeName || '退回节点')
  return task.assigneeSnapshot.resubmitRoute === 'FROM_START' ? '修正后从头重新执行' : `修正后回到“${target}”继续`
}
function flowName(flowId: number) {
  return flows.value.find(flow => flow.id === flowId)?.name || '业务流程'
}
function taskMine(task: FlowRuntimeTask) {
  // 服务端包含可领取候选；具体领取或处理责任仍以当前 allowedActions 为准。
  return task.currentHandler === true
}
function actionLabel(action: string, task = handlerTask.value) {
  // 文案来自当前可信任务类型，提交协议仍为公共 APPROVE 动作。
  if (action === 'APPROVE' && task?.taskType === 'FORM_TASK') return '完成填写'
  return ({ APPROVE: '同意', REJECT: '拒绝', RETURN: '退回', RESUBMIT: '修正并重新提交', TRANSFER: '转交', DELEGATE: '委托代办', REVOKE_DELEGATION: '撤销委托', ADD_SIGN: '加签', REMOVE_SIGN: '减签', CLAIM: '领取', UNCLAIM: '取消领取', WITHDRAW: '撤回', TERMINATE: '终止' } as Record<string, string>)[action] || '处理'
}
function businessReferenceLabel(instance: FlowRuntimeInstance) {
  // 关联内容只通过服务器业务投影展示，不从原提交快照读取名称。
  return instance.businessView?.actionCode ? '已关联业务动作' : instance.businessType ? '已关联业务记录' : '未关联'
}
function jobTypeLabel(type: string) {
  return ({ TIMER: '等待/定时恢复', TASK_TIMEOUT: '审批超时检查', RETRY: '自动重试',
    MESSAGE_DELIVERY: '站内消息投递', WEBHOOK_DELIVERY: '应用回调投递' } as Record<string, string>)[type] || '流程作业'
}
function jobStatusLabel(status: string) {
  return ({ PENDING: '待执行', RUNNING: '执行中', SUSPENDED: '已暂停', COMPLETED: '已完成', FAILED: '执行失败', CANCELLED: '已取消' } as Record<string, string>)[status] || status
}

async function loadParticipants(context: ReturnType<typeof requestContext>, request: number): Promise<FlowParticipantDirectory> {
  const empty: FlowParticipantDirectory = { departments: [], people: [], permissionVersion: 0 }
  if (!can('HANDLE') && !can('ADD_MANUAL_NODE')) return empty
  try {
    return await api<FlowParticipantDirectory>('/api/flow-runtime/participants', {}, context.token)
  } catch (error) {
    // 人员目录失权仅影响转交/加签选择；查看实例不依赖通讯录权限。
    if (error instanceof ApiError && error.httpStatus === 401) throw error
    if (requestCurrent(context) && request === loadGeneration) directoryError.value = `处理人目录暂不可用：${describeError(error)}`
    return empty
  }
}

async function load(preferId?: number) {
  if (!active || !token.value || !can('VIEW_RUNTIME')) { clearRuntime(); return }
  const context = requestContext()
  const request = ++loadGeneration
  const selection = ++selectionGeneration
  const previousId = selected.value?.id
  resetDialogs()
  flows.value = []
  instances.value = []
  selected.value = undefined
  directory.value = { departments: [], people: [], permissionVersion: 0 }
  directoryError.value = ''
  loading.value = true
  try {
    const [flowRows, instanceRows, people] = await Promise.all([
      api<FlowRuntimeCatalogItem[]>('/api/flow-runtime/flows', {}, context.token),
      api<FlowRuntimeInstance[]>('/api/flow-runtime/instances', {}, context.token),
      loadParticipants(context, request),
    ])
    if (!requestCurrent(context) || request !== loadGeneration) return
    flows.value = flowRows
    instances.value = instanceRows
    directory.value = people
    // 用户在读取期间主动换分类或选记录时，不能被旧操作结果强行导航回来。
    if (selection !== selectionGeneration) return
    const targetFromTask = props.initialTaskId
      ? instanceRows.find(instance => instance.tasks.some(task => task.id === props.initialTaskId))?.id : undefined
    const preferred = preferId ?? props.initialInstanceId ?? targetFromTask ?? previousId
    const resultVisible = preferId !== undefined && instanceRows.some(instance => instance.id === preferId)
    if (resultVisible && !visibleInstances.value.some(instance => instance.id === preferId)) {
      // 同意或转交后可能离开“待我处理”，仍展示刚操作的授权实例，避免误选下一条待办。
      // 分类监听为同步执行，只在本次程序切换中保留读取代次，不屏蔽用户后续操作。
      changingViewForPreferredInstance = true
      try { activeView.value = 'instances' }
      finally { changingViewForPreferredInstance = false }
    }
    const id = preferId !== undefined ? resultVisible ? preferId : undefined
      : preferred && visibleInstances.value.some(instance => instance.id === preferred) ? preferred : visibleInstances.value[0]?.id
    if (selection !== selectionGeneration) return
    const detail = id ? await api<FlowRuntimeInstance>(`/api/flow-runtime/instances/${id}`, {}, context.token) : undefined
    if (!requestCurrent(context) || request !== loadGeneration || selection !== selectionGeneration) return
    selected.value = detail
    runtimeError.value = preferId !== undefined && !resultVisible ? '本次操作的流程已不在当前可查看范围，请刷新权限或返回流程列表。' : ''
  } catch (error) {
    if (!requestCurrent(context) || request !== loadGeneration || selection !== selectionGeneration) return
    clearRuntime()
    handleError(error)
  } finally { if (requestCurrent(context) && request === loadGeneration) loading.value = false }
}

function openStart() {
  Object.assign(startForm, { flowId: publishedFlows.value[0]?.id, title: '' })
  startOpen.value = true
}

async function startFlow() {
  if (!token.value || !can('START') || !startForm.flowId || !startForm.title.trim() || !startOpen.value) return
  const context = operationContext()
  busy.value = 'start'
  try {
    const result = await api<FlowRuntimeActionResult>('/api/flow-runtime/instances', {
      method: 'POST', body: JSON.stringify({
        flowId: startForm.flowId, title: startForm.title, businessSnapshot: {}, variables: {},
        idempotencyKey: runtimeIdempotency('flow-start'),
      }),
    }, context.token)
    if (!operationCurrent(context)) return
    startOpen.value = false
    message.success(result.instance.status === 'WAITING' ? '流程已发起并生成待办' : `流程已发起：${runtimeStatusLabel(result.instance.status)}`)
    await load(result.instance.id)
  } catch (error) { if (operationCurrent(context)) handleError(error) }
  finally { if (operationCurrent(context)) busy.value = '' }
}

async function selectInstance(id: number) {
  if (!active || !token.value || !can('VIEW_RUNTIME') || !visibleInstances.value.some(item => item.id === id)) return
  const context = requestContext()
  const selection = ++selectionGeneration
  resetDialogs()
  selected.value = undefined
  try {
    const detail = await api<FlowRuntimeInstance>(`/api/flow-runtime/instances/${id}`, {}, context.token)
    if (requestCurrent(context) && selection === selectionGeneration) {
      selected.value = detail
      runtimeError.value = ''
      // 列表选中的实例写回地址，刷新和消息分享后仍打开同一个审批结果。
      if (String(route.query.instanceId || '') !== String(id)) {
        await router.replace({ query: { ...route.query, instanceId: String(id), taskId: undefined } })
      }
    }
  } catch (error) {
    if (requestCurrent(context) && selection === selectionGeneration) handleError(error)
  }
}

function openAction(action: string) {
  if (!selected.value?.allowedActions.includes(action)) return
  const instanceAction = ['WITHDRAW', 'TERMINATE'].includes(action)
  const task = handlerTask.value
  if (!instanceAction && (!can('HANDLE') || !task)) {
    return message.warning('当前没有可由你处理的任务，请刷新流程后重试。')
  }
  dialogGeneration++
  operationGeneration++
  actionTaskId.value = instanceAction ? undefined : task?.id
  actionTaskVersion.value = instanceAction ? undefined : task?.version
  taskFormReady.value = !['APPROVE', 'RESUBMIT'].includes(action)
  selectedAction.value = action
  Object.assign(actionForm, { comment: '', targetPersonId: undefined,
    delegationEndTime: action === 'DELEGATE' ? defaultDelegationEndTime() : '' })
  actionAttachmentIds.value = []
  actionOpen.value = true
}

function updateActionAttachments(items: BusinessAttachmentView[]) {
  actionAttachmentIds.value = items.filter(item => item.attachmentId && item.status === 'ACTIVE'
    && item.scanStatus === 'CLEAN').map(item => item.attachmentId as number)
}

function taskFormDenied() {
  clearRuntime()
  runtimeError.value = '当前记录已清除，请刷新权限后重新进入。'
  message.error('当前身份已不能读取或处理该审批任务，已清除本次输入和原业务内容，请刷新权限后重试。')
}

async function handleAction() {
  if (!token.value || !selected.value || !actionOpen.value) return
  const action = selectedAction.value
  if (!selected.value.allowedActions.includes(action)) return message.warning('当前动作已不可用，请刷新流程。')
  const instanceAction = ['WITHDRAW', 'TERMINATE'].includes(action)
  const task = selected.value.tasks.find(item => item.id === actionTaskId.value)
  const label = actionLabel(action, task)
  if (!instanceAction && (!can('HANDLE') || !task || !taskMine(task) || task.status !== 'PENDING'
    || task.completedAt || task.version !== actionTaskVersion.value)) {
    actionOpen.value = false
    message.warning('当前任务或处理人已变化，请刷新流程后重试。')
    return
  }
  const formAction = ['APPROVE', 'RESUBMIT'].includes(action)
  if (action === 'APPROVE' && !actionAttachmentReady.value) {
    return message.warning(`当前节点要求至少上传 ${task?.minimumAttachmentCount || 1} 个有效附件。`)
  }
  const fieldValues = formAction ? taskFormEditor.value?.submission() : undefined
  if (formAction && (!taskFormReady.value || fieldValues === undefined)) return message.warning(`请等待节点表单加载完成后再${label}。`)
  const context = { ...operationContext(), taskId: actionTaskId.value, taskVersion: actionTaskVersion.value }
  if (actionOpinionRequired.value && !actionForm.comment.trim()) return message.warning('该动作必须填写处理意见或原因')
  if (action === 'TRANSFER' && !actionForm.targetPersonId) return message.warning('转交必须选择目标处理人')
  if (action === 'DELEGATE') {
    if (directoryError.value) return message.warning('有效人员目录暂不可用，请刷新流程后再委托。')
    if (!actionForm.targetPersonId || excludedTransferIds.value.includes(actionForm.targetPersonId)) return message.warning('委托必须选择其他有效成员')
    if (!actionForm.delegationEndTime || new Date(actionForm.delegationEndTime).getTime() <= Date.now()) return message.warning('委托截止时间必须晚于当前时间')
  }
  if (actionIsSignChange.value) {
    if (!signApprovalMode.value) return message.warning('当前响应没有可信审批方式，请刷新流程后重试。')
    if (action === 'ADD_SIGN' && directoryError.value) return message.warning('有效人员目录暂不可用，请刷新流程后再加签。')
    if (!actionForm.targetPersonId || !signTargetPeople.value.some(person => person[participantValueKey.value] === actionForm.targetPersonId)) {
      return message.warning(`${label}必须选择当前可操作的目标人员`)
    }
  }
  busy.value = 'action'
  try {
    const path = instanceAction
      ? `/api/flow-runtime/instances/${selected.value.id}/actions`
      : `/api/flow-runtime/tasks/${context.taskId}/actions`
    const result = await api<FlowRuntimeActionResult>(path, {
      method: 'POST', body: JSON.stringify({ actionCode: action, comment: actionForm.comment || undefined,
        idempotencyKey: runtimeIdempotency(`flow-${action.toLowerCase()}`), fieldValues,
        attachmentIds: action === 'APPROVE' ? actionAttachmentIds.value : undefined,
        expectedFormRevision: formAction ? taskFormEditor.value?.formRevision() : undefined,
        delegationEndTime: action === 'DELEGATE' ? actionForm.delegationEndTime : undefined,
        ...(props.context === 'platform' ? { targetPlatformAccountId: actionForm.targetPersonId }
          : { targetTenantMemberId: actionForm.targetPersonId }) }),
    }, context.token)
    if (!operationCurrent(context) || context.taskId !== actionTaskId.value || context.taskVersion !== actionTaskVersion.value) return
    actionOpen.value = false
    message.success(`${label}已保存，流程当前为${instanceStatusLabel(result.instance)}`)
    await load(result.instance.id)
  } catch (error) {
    if (!operationCurrent(context) || context.taskId !== actionTaskId.value) return
    if (error instanceof ApiError && error.httpStatus === 409 && error.code === 'FLOW_NODE_FORM_CONFLICT') {
      // 只有候选表单版本冲突保留当前任务及输入；其他任务状态冲突仍重新读取。
      taskFormEditor.value?.markConflict(error.message)
      message.warning('其他处理人已更新候选内容，请重新读取并保留修改，再核对提交。')
    } else if (error instanceof ApiError && error.httpStatus === 409) {
      actionOpen.value = false
      message.warning('当前任务已变化，已清除本次输入并重新读取流程。')
      await load(context.instanceId)
    } else handleError(error)
  }
  finally { if (operationCurrent(context)) busy.value = '' }
}

async function submitCollaboration(actionCode: 'COMMENT' | 'REMIND') {
  if (!token.value || !selected.value || busy.value || !selected.value.allowedActions.includes(actionCode)) return
  const comment = collaborationText.value.trim()
  if (!comment) return message.warning(actionCode === 'COMMENT' ? '请先填写评论内容。' : '请先填写催办说明。')
  const context = operationContext()
  busy.value = actionCode.toLowerCase()
  try {
    const result = await api<FlowRuntimeActionResult>(`/api/flow-runtime/instances/${selected.value.id}/actions`, {
      method: 'POST', body: JSON.stringify({ actionCode, comment,
        idempotencyKey: runtimeIdempotency(`flow-${actionCode.toLowerCase()}`) }),
    }, context.token)
    if (!operationCurrent(context)) return
    collaborationText.value = ''
    message.success(actionCode === 'COMMENT' ? '评论已保存。' : '已提醒当前待办接收人。')
    await load(result.instance.id)
  } catch (error) {
    if (operationCurrent(context)) handleError(error)
  } finally { if (operationCurrent(context)) busy.value = '' }
}

function openManualNode() {
  if (props.context !== 'system' || !canAddRuntimeNode.value || busy.value) return
  Object.assign(manualForm, { assigneeTenantMemberId: current.value?.tenantMemberId, reason: '' })
  manualIdempotencyKey.value = runtimeIdempotency('flow-manual-node')
  manualPreview.value = undefined
  manualOpen.value = true
}

function manualRequest() {
  if (!manualForm.assigneeTenantMemberId) throw new Error('请选择当前系统内的目标处理人')
  if (!manualForm.reason.trim()) throw new Error('手动加签必须填写可追溯原因')
  return {
    assigneeTenantMemberId: manualForm.assigneeTenantMemberId,
    position: 'BEFORE_CURRENT',
    reason: manualForm.reason.trim(),
    statusMappings: {},
    idempotencyKey: manualIdempotencyKey.value,
  }
}

async function previewManualNode() {
  if (!token.value || !selected.value || !manualOpen.value || !canAddRuntimeNode.value || busy.value) return
  const context = operationContext()
  const previewRequest = ++manualPreviewGeneration
  busy.value = 'manual-preview'
  try {
    const preview = await api<FlowManualNodePreview>(`/api/flow-runtime/instances/${selected.value.id}/manual-node-preview`, {
      method: 'POST', body: JSON.stringify(manualRequest()),
    }, context.token)
    if (!operationCurrent(context) || previewRequest !== manualPreviewGeneration) return
    manualPreview.value = preview
    message.success('加签预检查通过，确认后才会暂停当前审批节点')
  } catch (error) {
    if (!operationCurrent(context) || previewRequest !== manualPreviewGeneration) return
    manualPreview.value = undefined
    handleError(error)
  } finally { if (operationCurrent(context)) busy.value = '' }
}

async function addManualNode() {
  if (!token.value || !selected.value || !manualPreview.value?.allowed || !manualOpen.value || !canAddRuntimeNode.value || busy.value) return
  const context = operationContext()
  busy.value = 'manual-add'
  try {
    const result = await api<FlowManualNodeResult>(`/api/flow-runtime/instances/${selected.value.id}/manual-nodes`, {
      method: 'POST', body: JSON.stringify(manualRequest()),
    }, context.token)
    if (!operationCurrent(context)) return
    manualOpen.value = false
    activeView.value = 'instances'
    message.success('手动加签已生成，原审批节点已暂停')
    await load(result.instance.id)
  } catch (error) {
    if (!operationCurrent(context)) return
    manualPreview.value = undefined
    handleError(error)
  } finally { if (operationCurrent(context)) busy.value = '' }
}

function openRuntimeNode() {
  if (!canAddRuntimeNode.value || busy.value) return
  Object.assign(runtimeNodeForm, { nodeType: 'APPROVAL', name: '', position: 'AFTER_CURRENT', anchorNodeKey: undefined, assigneeIds: [], approvalMode: 'OR_SIGN', reason: '' })
  runtimeNodePreview.value = undefined
  runtimeNodeSubmission.value = undefined
  runtimeNodeError.value = ''
  runtimeNodeNeedsRefresh.value = false
  runtimeNodeIdempotencyKey.value = runtimeIdempotency('flow-runtime-node')
  runtimeNodeOpen.value = true
}

function runtimeNodeRequest(): FlowRuntimeNodeRequest {
  if (!selected.value || !canAddRuntimeNode.value) throw new Error('当前流程已不能添加节点，请重新读取流程。')
  if (!runtimeNodePositionValid.value) throw new Error('请选择当前仍可插入的待执行节点；原位置可能已进入，请重新读取流程。')
  if (!runtimeNodeForm.name.trim()) throw new Error(runtimeNodeForm.nodeType === 'NOTIFICATION' ? '请填写抄送标题' : '请填写审批节点名称')
  if (!runtimeNodeForm.reason.trim()) throw new Error('请填写可追溯的添加原因或抄送说明')
  if (directoryError.value) throw new Error('人员目录暂不可用，请重新读取流程后选择有效人员。')
  const ids = runtimeNodeForm.assigneeIds
  const available = new Set(directory.value.people.map(person => person[participantValueKey.value]))
  if (!ids.length || ids.some(id => !Number.isSafeInteger(id) || id <= 0 || !available.has(id)) || new Set(ids).size !== ids.length) {
    throw new Error('请选择当前范围内不重复的有效人员')
  }
  return { nodeType: runtimeNodeForm.nodeType, name: runtimeNodeForm.name.trim(), position: runtimeNodeForm.position,
    // 当前节点前后插的锚点均由服务端决定；后插保持既有幂等请求内容。
    ...(['BEFORE_NODE', 'AFTER_NODE'].includes(runtimeNodeForm.position) ? { anchorNodeKey: runtimeNodeForm.anchorNodeKey } : {}),
    assigneeIds: [...ids], approvalMode: runtimeNodeForm.nodeType === 'APPROVAL' ? runtimeNodeForm.approvalMode : 'OR_SIGN',
    reason: runtimeNodeForm.reason.trim(), expectedInstanceVersion: selected.value.version,
    expectedGraphRevision: selected.value.graphRevision, idempotencyKey: runtimeNodeIdempotencyKey.value }
}

function runtimeNodeFailure(error: unknown) {
  if (error instanceof ApiError && [401, 403, 404].includes(error.httpStatus || 0)) { handleError(error); return }
  runtimeNodeError.value = describeError(error)
  if (error instanceof ApiError && error.httpStatus === 409) {
    runtimeNodePreview.value = undefined
    runtimeNodeSubmission.value = undefined
    runtimeNodeNeedsRefresh.value = true
  }
  message.error(runtimeNodeError.value)
}

async function previewRuntimeNode() {
  if (!token.value || !selected.value || !runtimeNodeOpen.value || !canAddRuntimeNode.value || busy.value || runtimeNodeNeedsRefresh.value) return
  const context = operationContext(), previewRequest = ++runtimeNodePreviewGeneration
  runtimeNodePreview.value = undefined
  runtimeNodeSubmission.value = undefined
  runtimeNodeError.value = ''
  busy.value = 'runtime-node-preview'
  try {
    const request = runtimeNodeRequest()
    const preview = await api<FlowRuntimeNodePreview>(`/api/flow-runtime/instances/${context.instanceId}/runtime-node-preview`, {
      method: 'POST', body: JSON.stringify(request),
    }, context.token)
    if (!operationCurrent(context) || previewRequest !== runtimeNodePreviewGeneration || !runtimeNodeOpen.value) return
    if (preview.instanceId !== context.instanceId || preview.instanceVersion !== request.expectedInstanceVersion
      || preview.graphRevision !== request.expectedGraphRevision || preview.position !== request.position
      || (['BEFORE_NODE', 'AFTER_NODE'].includes(request.position) && preview.anchorNodeKey !== request.anchorNodeKey)) {
      runtimeNodeNeedsRefresh.value = true
      throw new Error('当前流程已变化，请重新读取后再次预览。')
    }
    runtimeNodePreview.value = preview
    runtimeNodeSubmission.value = preview.allowed ? request : undefined
    runtimeNodeNeedsRefresh.value = false
  } catch (error) {
    if (operationCurrent(context) && previewRequest === runtimeNodePreviewGeneration) runtimeNodeFailure(error)
  } finally { if (operationCurrent(context)) busy.value = '' }
}

async function addRuntimeNode() {
  if (!token.value || !selected.value || !runtimeNodeOpen.value || !canAddRuntimeNode.value || busy.value
    || !runtimeNodePreview.value?.allowed || !runtimeNodeSubmission.value) return
  const context = operationContext(), previewRequest = runtimeNodePreviewGeneration
  // 确认提交预览的同一请求及幂等键；网络失败后重试不重组或重复添加。
  const request = runtimeNodeSubmission.value
  busy.value = 'runtime-node-add'
  runtimeNodeError.value = ''
  try {
    const result = await api<FlowRuntimeActionResult>(`/api/flow-runtime/instances/${context.instanceId}/runtime-nodes`, {
      method: 'POST', body: JSON.stringify(request),
    }, context.token)
    if (!operationCurrent(context) || previewRequest !== runtimeNodePreviewGeneration || !runtimeNodeOpen.value) return
    runtimeNodeOpen.value = false
    activeView.value = 'instances'
    message.success('节点已加入本次流程，将按调整后的路径处理')
    await load(result.instance.id)
  } catch (error) {
    if (operationCurrent(context) && previewRequest === runtimeNodePreviewGeneration) runtimeNodeFailure(error)
  } finally { if (operationCurrent(context)) busy.value = '' }
}

async function refreshRuntimeNodeInstance() {
  if (!token.value || !selected.value || !runtimeNodeOpen.value || busy.value) return
  const context = operationContext()
  busy.value = 'runtime-node-refresh'
  try {
    const detail = await api<FlowRuntimeInstance>(`/api/flow-runtime/instances/${context.instanceId}`, {}, context.token)
    if (!operationCurrent(context) || !runtimeNodeOpen.value) return
    selected.value = detail
    instances.value = instances.value.map(instance => instance.id === detail.id ? detail : instance)
    if (!runtimeNodePositionValid.value) runtimeNodeForm.anchorNodeKey = undefined
    runtimeNodePreviewGeneration++
    runtimeNodePreview.value = undefined
    runtimeNodeSubmission.value = undefined
    runtimeNodeIdempotencyKey.value = runtimeIdempotency('flow-runtime-node')
    runtimeNodeNeedsRefresh.value = false
    runtimeNodeError.value = ''
    if (!canAddRuntimeNode.value) {
      runtimeNodeOpen.value = false
      message.warning('当前流程已不能添加节点，已重新读取流程。')
    } else message.info('已读取当前节点，请核对添加位置后重新预览。')
  } catch (error) { if (operationCurrent(context)) runtimeNodeFailure(error) }
  finally { if (operationCurrent(context)) busy.value = '' }
}

function openIncident(item: FlowRuntimeException, actionCode: 'RETRY' | 'RESUME') {
  selectedIncident.value = item
  Object.assign(incidentForm, { actionCode, comment: '' })
  incidentOpen.value = true
}

async function handleIncident() {
  if (!token.value || !selected.value || !selectedIncident.value || !incidentForm.comment.trim() || !incidentOpen.value) return
  const context = operationContext()
  const actionCode = incidentForm.actionCode
  busy.value = 'incident'
  try {
    const result = await api<FlowRuntimeActionResult>(`/api/flow-runtime/incidents/${selectedIncident.value.id}/actions`, {
      method: 'POST', body: JSON.stringify({ actionCode: incidentForm.actionCode,
        comment: incidentForm.comment.trim(), idempotencyKey: runtimeIdempotency('flow-incident') }),
    }, context.token)
    if (!operationCurrent(context)) return
    incidentOpen.value = false
    message.success(actionCode === 'RETRY' ? '已安排自动重试' : '已确认补偿并继续流程')
    await load(result.instance.id)
  } catch (error) { if (operationCurrent(context)) handleError(error) }
  finally { if (operationCurrent(context)) busy.value = '' }
}

watch([identity, token], () => {
  clearRuntime()
  if (props.initialInstanceId || props.initialTaskId) activeView.value = 'instances'
  if (token.value && can('VIEW_RUNTIME')) void load(props.initialInstanceId)
}, { immediate: true, flush: 'sync' })
watch(() => [props.initialInstanceId, props.initialTaskId], () => {
  if (!props.initialInstanceId && !props.initialTaskId) return
  activeView.value = 'instances'
  void load(props.initialInstanceId)
})
watch(() => [manualForm.assigneeTenantMemberId, manualForm.reason], () => {
  manualPreviewGeneration++
  if (manualOpen.value) manualPreview.value = undefined
}, { flush: 'sync' })
watch(() => runtimeNodeForm.position, () => { runtimeNodeForm.anchorNodeKey = undefined }, { flush: 'sync' })
watch(() => [runtimeNodeForm.nodeType, runtimeNodeForm.name, runtimeNodeForm.position, runtimeNodeForm.anchorNodeKey,
  runtimeNodeForm.assigneeIds.join(','), runtimeNodeForm.approvalMode, runtimeNodeForm.reason], () => {
  runtimeNodePreviewGeneration++
  runtimeNodePreview.value = undefined
  runtimeNodeSubmission.value = undefined
  if (!runtimeNodeNeedsRefresh.value) runtimeNodeError.value = ''
  if (runtimeNodeOpen.value) runtimeNodeIdempotencyKey.value = runtimeIdempotency('flow-runtime-node')
}, { flush: 'sync' })
watch([startOpen, actionOpen, manualOpen, runtimeNodeOpen, incidentOpen], (values, previous) => {
  if (!previous?.some((wasOpen, index) => wasOpen && !values[index])) return
  dialogGeneration++
  operationGeneration++
  busy.value = ''
  if (!startOpen.value) Object.assign(startForm, { flowId: undefined, title: '' })
  if (!actionOpen.value) {
    taskFormEditor.value?.clear()
    Object.assign(actionForm, { comment: '', targetPersonId: undefined, delegationEndTime: '' })
    actionTaskId.value = actionTaskVersion.value = undefined
    taskFormReady.value = false
  }
  if (!manualOpen.value) {
    manualPreviewGeneration++
    manualPreview.value = undefined
    manualIdempotencyKey.value = ''
    Object.assign(manualForm, { assigneeTenantMemberId: undefined, reason: '' })
  }
  if (!runtimeNodeOpen.value) {
    runtimeNodePreviewGeneration++
    runtimeNodePreview.value = undefined
    runtimeNodeSubmission.value = undefined
    runtimeNodeIdempotencyKey.value = ''
    runtimeNodeError.value = ''
    runtimeNodeNeedsRefresh.value = false
    Object.assign(runtimeNodeForm, { nodeType: 'APPROVAL', name: '', position: 'AFTER_CURRENT', anchorNodeKey: undefined, assigneeIds: [], approvalMode: 'OR_SIGN', reason: '' })
  }
  if (!incidentOpen.value) {
    selectedIncident.value = undefined
    Object.assign(incidentForm, { actionCode: 'RETRY', comment: '' })
  }
}, { flush: 'sync' })
watch(activeView, () => {
  if (changingViewForPreferredInstance) return
  if (loading.value) {
    selectionGeneration++
    resetDialogs()
    selected.value = undefined
    return
  }
  const stillVisible = visibleInstances.value.some(instance => instance.id === selected.value?.id)
  if (!stillVisible) {
    const first = visibleInstances.value[0]
    if (first) void selectInstance(first.id)
    else {
      selectionGeneration++
      resetDialogs()
      selected.value = undefined
    }
  }
}, { flush: 'sync' })
onBeforeUnmount(() => { active = false; clearRuntime() })
</script>

<template>
  <div class="flow-runtime-page">
    <div class="page-heading compact-heading">
      <div><p class="eyebrow">流程 · 运行记录</p><h1>发起、审批与路径说明</h1><p>流程固定使用发起时的发布版本；当前处理人、处理原因和下一步均来自实际运行记录。</p></div>
      <a-space>
        <a-button v-if="can('VIEW_RUNTIME')" :loading="loading" @click="load(selected?.id)">刷新流程</a-button>
        <a-button v-if="can('START')" type="primary" :disabled="!publishedFlows.length" @click="openStart">发起流程</a-button>
      </a-space>
    </div>
    <a-alert v-if="!can('VIEW_RUNTIME')" type="warning" show-icon message="当前工作范围没有流程运行查看权限" />
    <template v-else>
      <a-segmented v-model:value="activeView" class="flow-runtime-views" :options="[
        { value: 'pending', label: '待我处理' }, { value: 'mine', label: '我的申请' },
        { value: 'instances', label: '流程实例' }, { value: 'records', label: '运行记录' },
      ]" />
    <div class="flow-runtime-layout">
      <section class="panel-card runtime-instance-list">
        <div class="panel-title"><strong>{{ activeView === 'pending' ? '待我处理' : activeView === 'mine' ? '我的申请' : activeView === 'records' ? '运行记录' : '流程实例' }}</strong><a-tag>{{ visibleInstances.length }}</a-tag></div>
        <a-spin :spinning="loading">
          <a-alert v-if="runtimeError" type="error" show-icon :message="runtimeError" />
          <button v-for="item in visibleInstances" :key="item.id" :class="['runtime-instance-item', { active: selected?.id === item.id }]" @click="selectInstance(item.id)">
            <span><strong>{{ item.title }}</strong><small>{{ flowName(item.flowId) }} · 版本 V{{ item.flowVersionNumber }}</small></span>
            <a-tag :color="statusColor(item.status)">{{ instanceStatusLabel(item) }}</a-tag>
          </button>
          <a-empty v-if="!runtimeError && !loading && !visibleInstances.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" :description="activeView === 'pending' ? '当前没有需要你处理的流程' : activeView === 'mine' ? '你还没有发起流程' : '当前分类没有运行记录'" />
        </a-spin>
      </section>

      <main v-if="selected" class="runtime-instance-detail">
        <section class="panel-card runtime-instance-hero">
          <div><p class="eyebrow">流程实例</p><h2>{{ selected.title }}</h2><span><a-tag :color="statusColor(selected.status)">{{ instanceStatusLabel(selected) }}</a-tag><a-tag>流程版本 V{{ selected.flowVersionNumber }}</a-tag></span></div>
          <div class="runtime-actions">
            <a-button v-if="canAddRuntimeNode" type="dashed" :disabled="Boolean(busy)" @click="openRuntimeNode">添加审批或抄送</a-button>
            <a-button v-for="action in taskAndInstanceActions" :key="action" :type="['APPROVE','RESUBMIT'].includes(action) ? 'primary' : 'default'" :disabled="Boolean(busy)" @click="openAction(action)">{{ actionLabel(action) }}</a-button>
          </div>
        </section>
        <section class="runtime-explain-grid">
          <article class="panel-card"><small>当前节点</small><strong>{{ selected.currentNodeName || '流程已结束' }}</strong><span>按当前流程版本推进</span></article>
          <article class="panel-card"><small>{{ selected.allowedActions.includes('CLAIM') ? '候选处理人' : '当前处理人' }}</small><strong>{{ currentHandlerNames || '无需人工处理' }}</strong><span>{{ currentTasks.length > 1 ? `${currentTasks.length} 名处理人待处理` : currentTask?.assigneeDepartment || currentTask?.candidates[0]?.resolutionReason || '按发布版本自动推进' }}</span></article>
          <article class="panel-card"><small>下一步说明</small><strong>{{ selected.nextStep }}</strong><span>由运行引擎根据当前执行与待办计算</span></article>
        </section>
        <section class="panel-card runtime-snapshot">
          <div class="panel-title"><strong>流程版本与业务关联</strong><a-tag color="purple">运行时已固定</a-tag></div>
          <a-descriptions bordered size="small" :column="2"><a-descriptions-item label="流程">{{ flowName(selected.flowId) }} · V{{ selected.flowVersionNumber }}</a-descriptions-item><a-descriptions-item label="发起人">{{ selected.startedByName }}</a-descriptions-item><a-descriptions-item label="关联业务">{{ businessReferenceLabel(selected) }}</a-descriptions-item><a-descriptions-item label="发起时间">{{ productDateTime(selected.startedAt) }}</a-descriptions-item></a-descriptions>
        </section>
        <section class="panel-card"><RuntimeBusinessViewPanel :view="selected.businessView" /></section>
        <BusinessAttachmentsPanel v-if="canFile('VIEW')" :endpoint="`/api/business-attachments/flow-instances/${selected.id}`" :context="context" :writable="canFile('UPLOAD') && canFile('REFERENCE')" title="流程附件" description="审批材料和处理结果保存在当前流程实例内。" />
        <section class="panel-card runtime-collaboration">
          <div class="panel-title"><strong>评论与催办</strong><a-tag>{{ comments.length }} 条评论</a-tag></div>
          <a-textarea v-model:value="collaborationText" :rows="3" :maxlength="2000" show-count
            placeholder="填写当前流程的协作说明；评论会保留实际操作者和时间。" :disabled="Boolean(busy)" />
          <div class="modal-result-actions">
            <a-button type="primary" :loading="busy === 'comment'" :disabled="!selected.allowedActions.includes('COMMENT') || !collaborationText.trim() || Boolean(busy)" @click="submitCollaboration('COMMENT')">发表评论</a-button>
            <a-button v-if="selected.allowedActions.includes('REMIND')" :loading="busy === 'remind'" :disabled="!collaborationText.trim() || Boolean(busy)" @click="submitCollaboration('REMIND')">催办当前处理人</a-button>
          </div>
          <a-list v-if="comments.length" :data-source="comments" size="small">
            <template #renderItem="{ item }"><a-list-item><a-list-item-meta :description="`${item.actedByName} · ${productDateTime(item.actedAt)}`"><template #title>{{ item.comment }}</template></a-list-item-meta></a-list-item></template>
          </a-list>
          <a-empty v-else :image="false" description="暂无评论" />
        </section>
        <section class="panel-card runtime-task-map">
          <div class="panel-title"><strong>实例节点快照</strong><a-tag>{{ selected.tasks.length }} 个任务节点</a-tag></div>
          <div class="runtime-task-map__list">
            <article v-for="(task, index) in selected.tasks" :key="task.id" :class="{ manual: task.taskType === 'MANUAL_APPROVAL' }">
              <span class="runtime-task-map__index">{{ index + 1 }}</span>
              <div>
                <span><strong>{{ taskNodeName(task, index) }}</strong><a-tag v-if="task.taskType === 'MANUAL_APPROVAL'" color="purple">运行时新增</a-tag><a-tag v-if="task.taskType === 'RETURN_TASK'" color="purple">退回修正</a-tag><a-tag :color="taskStatusColor(task.status)">{{ taskStatusLabel(task.status, task.taskType) }}</a-tag><a-tag v-if="taskApprovalRule(task)" color="cyan">{{ taskApprovalRule(task) }}</a-tag><a-tag v-if="taskReturnRule(task)" color="orange">{{ taskReturnRule(task) }}</a-tag><a-tag v-if="taskDelegation(task)" :color="taskDelegation(task)?.effective ? 'blue' : 'default'">{{ delegationStatusLabel(taskDelegation(task)?.status || '') }}</a-tag></span>
                <p v-if="manualReason(task)">{{ manualReason(task) }}</p>
                <small>处理人：{{ task.assigneeName || task.candidates.map(item => item.displayName).join('、') || '待解析' }}<template v-if="task.assigneeDepartment"> · {{ task.assigneeDepartment }}</template> · {{ task.candidates[0]?.resolutionReason || '按发布版本解析' }}</small>
                <p v-if="taskDelegation(task)">原审批人 {{ taskDelegation(task)?.principalName }} · 代理人 {{ taskDelegation(task)?.delegateName }} · {{ taskDelegation(task)?.contextName }}/{{ taskDelegation(task)?.scopeName }} · {{ productDateTime(taskDelegation(task)?.startTime) }} 至 {{ productDateTime(taskDelegation(task)?.endTime) }}<template v-if="taskDelegation(task)?.reason"> · 原因：{{ taskDelegation(task)?.reason }}</template></p>
                <p><template v-if="task.dueAt">处理截止：{{ productDateTime(task.dueAt) }}</template><template v-else-if="task.status === 'PENDING' && task.taskType !== 'RETURN_TASK'">当前没有处理时限</template><template v-if="task.completedAt"> · 处理时间：{{ productDateTime(task.completedAt) }}</template></p>
              </div>
            </article>
          </div>
        </section>
        <section v-if="branchExecutions.length" class="panel-card runtime-task-map">
          <div class="panel-title"><strong>分支执行进度</strong><a-tag>{{ branchExecutions.length }} 个执行节点</a-tag></div>
          <div class="runtime-task-map__list">
            <article v-for="execution in branchExecutions" :key="execution.id">
              <span class="runtime-task-map__index">{{ execution.id }}</span>
              <div><span><strong>{{ executionNodeName(execution.nodeKey) }}</strong><a-tag :color="execution.status === 'ACTIVE' ? 'blue' : 'green'">{{ executionResultLabel(execution.resultCode) }}</a-tag></span>
                <small>执行号 {{ execution.id }}<template v-if="execution.parentExecutionId"> · 来源执行 {{ execution.parentExecutionId }}</template> · {{ productDateTime(execution.enteredAt) }}</small></div>
            </article>
          </div>
        </section>
        <section v-if="subflowLinks.length" class="panel-card runtime-task-map">
          <div class="panel-title"><strong>父子流程关系</strong><a-tag>{{ subflowLinks.length }} 条关联</a-tag></div>
          <div class="runtime-task-map__list">
            <article v-for="link in subflowLinks" :key="`${link.relation}-${link.instanceId}-${link.executionId}`">
              <span class="runtime-task-map__index">{{ link.relation === 'PARENT' ? '父' : '子' }}</span>
              <div>
                <span><strong>{{ link.flowName }} · V{{ link.flowVersionNumber || '-' }}</strong>
                  <a-tag color="purple">{{ subflowRelationLabel(link.relation) }}</a-tag>
                  <a-tag :color="statusColor(link.status)">{{ runtimeStatusLabel(link.status) }}</a-tag></span>
                <small>实例 {{ link.instanceId }} · 关联节点 {{ executionNodeName(link.nodeKey) }}<template v-if="link.errorCode"> · 异常 {{ link.errorCode }}</template></small>
              </div>
              <a-button v-if="link.viewable" :disabled="Boolean(busy)" @click="load(link.instanceId)">查看{{ subflowRelationLabel(link.relation) }}</a-button>
              <a-tag v-else>无查看权限</a-tag>
            </article>
          </div>
        </section>
        <section v-if="selected.exceptions.length" class="panel-card runtime-exceptions"><div class="panel-title"><strong>处理异常</strong><a-tag color="red">{{ selected.exceptions.length }}</a-tag></div><div v-for="item in selected.exceptions" :key="item.id" class="runtime-exception-row"><a-alert type="error" show-icon :message="item.errorMessage" :description="item.status === 'RESOLVED' ? `已由 ${item.resolvedByName || '系统'} 处理` : selected.tasks.some(task => task.nodeKey === item.nodeKey && ['PENDING','QUEUED','SUSPENDED'].includes(task.status)) ? '原待办已保留，请由当前处理人补充内容、审批或转交；不能通过异常补偿跳过审批节点。' : '运行现场已保留，可重试或人工确认补偿后继续。'" /><span v-if="['OPEN','RETRY_SCHEDULED'].includes(item.status) && !selected.tasks.some(task => task.nodeKey === item.nodeKey && ['PENDING','QUEUED','SUSPENDED'].includes(task.status))"><a-button @click="openIncident(item, 'RETRY')">重试</a-button><a-button type="primary" @click="openIncident(item, 'RESUME')">补偿后继续</a-button></span></div></section>
        <AiApprovalSummary v-if="context === 'system' && selected.history.length" :key="selected.id" :instance-id="selected.id" :instance-version="selected.version" :history-count="selected.history.length" @source="showSummarySource" />
        <section class="panel-card runtime-timeline"><div class="panel-title"><strong>完整处理历史</strong><a-tag>{{ selected.history.length }} 条</a-tag></div><a-timeline><a-timeline-item v-for="event in selected.history" :key="event.id" :id="`flow-history-${selected.id}-${event.id}`" :color="event.afterStatus === 'EXCEPTION' ? 'red' : event.afterStatus === 'COMPLETED' ? 'green' : 'blue'"><strong>{{ event.eventName }} · #{{ event.id }}</strong><p v-if="gatewayHistoryDescription(event)">{{ gatewayHistoryDescription(event) }}</p><p v-if="subflowHistoryDescription(event)">{{ subflowHistoryDescription(event) }}</p><p v-if="automaticHistoryDescription(event)">{{ automaticHistoryDescription(event) }}</p><p v-if="messageHistoryDescription(event)">{{ messageHistoryDescription(event) }}</p><p v-if="webhookHistoryDescription(event)">{{ webhookHistoryDescription(event) }}</p><p v-if="waitHistoryDescription(event)">{{ waitHistoryDescription(event) }}</p><p v-if="historyTransition(event)">{{ historyTransition(event) }}</p><small>{{ event.actorName }} · {{ productDateTime(event.occurredAt) }}</small></a-timeline-item></a-timeline><a-empty v-if="!selected.history.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="等待首个处理事件" /></section>
        <section v-if="selected.jobs.length" class="panel-card runtime-job-list"><div class="panel-title"><strong>后台作业</strong><a-tag>{{ selected.jobs.length }}</a-tag></div><a-descriptions v-for="job in selected.jobs" :key="job.id" bordered size="small" :column="3"><a-descriptions-item label="类型">{{ jobTypeLabel(job.jobType) }}</a-descriptions-item><a-descriptions-item label="状态">{{ jobStatusLabel(job.status) }}</a-descriptions-item><a-descriptions-item label="尝试">{{ job.attemptCount }} / {{ job.maxAttempts }}</a-descriptions-item><a-descriptions-item label="计划执行">{{ productDateTime(job.nextRunAt) }}</a-descriptions-item><a-descriptions-item label="结束时间">{{ job.completedAt ? productDateTime(job.completedAt) : '尚未结束' }}</a-descriptions-item><a-descriptions-item label="处理说明">{{ job.lastErrorMessage || '按发布时的策略执行' }}</a-descriptions-item></a-descriptions></section>
      </main>
    </div>
    </template>
  </div>

  <a-modal v-model:open="startOpen" title="发起已发布流程" width="680px" :confirm-loading="busy === 'start'" @ok="startFlow">
    <a-form layout="vertical"><a-form-item label="流程" required><a-select v-model:value="startForm.flowId" :options="publishedFlows.map(flow => ({ value: flow.id, label: `${flow.name} · V${flow.currentVersionNumber}` }))" /></a-form-item><a-form-item label="申请标题" required extra="需要关联客户等业务记录时，请从对应记录页发起，系统会自动带入关联关系。"><a-input v-model:value="startForm.title" placeholder="例如：客户合同审批" /></a-form-item></a-form>
  </a-modal>
  <!-- 长审批表单在内容区滚动，给标题与确认按钮保留视口空间。 -->
  <a-modal v-model:open="actionOpen" :title="`处理流程 · ${actionLabel(selectedAction, actionTask)}`" :ok-text="actionLabel(selectedAction, actionTask)" width="820px" :confirm-loading="busy === 'action'"
    :style="{ top: '24px' }" :body-style="{ maxHeight: 'calc(100dvh - 200px)', overflowY: 'auto', boxSizing: 'border-box' }"
    :ok-button-props="{ disabled: (['APPROVE','RESUBMIT'].includes(selectedAction) && !taskFormReady) || (selectedAction === 'APPROVE' && !actionAttachmentReady) || (actionIsSignChange && !signApprovalMode) || (selectedAction === 'DELEGATE' && (!actionForm.targetPersonId || !actionForm.delegationEndTime || Boolean(directoryError))) }" @ok="handleAction">
    <a-form layout="vertical">
      <a-alert type="info" show-icon :message="selected?.nextStep" style="margin-bottom:16px" />
      <a-alert v-if="actionIsClaim" type="info" show-icon :message="selectedAction === 'CLAIM' ? '领取同一条待办，由你继续处理' : '取消领取后，恢复当前候选人员领取'"
        description="领取和取消领取不创建新审批任务，不清除已有表单内容，也不重置处理时限。" style="margin-bottom:16px" />
      <a-alert v-if="actionIsSignChange" type="info" show-icon message="调整当前节点的参与人员，不新增流程节点" :description="signChangeHelp" style="margin-bottom:16px" />
      <a-alert v-if="actionIsSignChange && !signApprovalMode" type="warning" show-icon message="审批方式尚未读取，请刷新流程后再加签或减签。" style="margin-bottom:16px" />
      <a-alert v-if="selectedAction === 'DELEGATE'" type="info" show-icon message="代理人只在当前审批任务和设定时段内代办" description="原审批人仍保留处理权并可随时撤销；代理人遵守当前节点按钮、字段、业务权限和幂等规则，不能继续委托或转交。" style="margin-bottom:16px" />
      <a-alert v-if="selectedAction === 'REVOKE_DELEGATION'" type="warning" show-icon message="撤销后代理人立即失去当前任务处理权" :description="taskDelegation(actionTask) ? `${taskDelegation(actionTask)?.delegateName} 的代办原定于 ${productDateTime(taskDelegation(actionTask)?.endTime)} 结束，撤销不改写已有处理历史。` : '当前委托关系已变化，请刷新后重试。'" style="margin-bottom:16px" />
      <a-alert v-if="selectedAction === 'RESUBMIT'" type="warning" show-icon message="审批已退回，请核对原因并修正申请"
        :description="actionTask?.assigneeSnapshot.resubmitRoute === 'FROM_START' ? '重新提交后将从流程开始重新执行，退回前审批只保留为历史。' : `重新提交后将回到“${actionTask?.assigneeSnapshot.returnNodeName || '退回节点'}”继续，旧任务不会计入新一轮。`" style="margin-bottom:16px" />
      <a-form-item :label="actionIsClaim ? selectedAction === 'CLAIM' ? '领取说明' : '取消领取说明' : selectedAction === 'DELEGATE' ? '委托原因' : selectedAction === 'REVOKE_DELEGATION' ? '撤销说明' : actionTaskIsReturn ? '修正说明' : actionTaskIsForm ? '填写说明' : '处理意见'" :required="actionOpinionRequired"><a-textarea v-model:value="actionForm.comment" :placeholder="actionCommentPlaceholder" /></a-form-item>
      <template v-if="selectedAction === 'TRANSFER'">
        <a-alert v-if="directoryError" type="warning" show-icon :message="directoryError" />
        <a-form-item label="转交给" required><PersonSelect v-model="actionForm.targetPersonId" :people="directory.people" :value-key="participantValueKey" :excluded-values="excludedTransferIds" :disabled="Boolean(directoryError) || busy === 'action'" /></a-form-item>
      </template>
      <template v-if="selectedAction === 'DELEGATE'">
        <a-alert v-if="directoryError" type="warning" show-icon :message="directoryError" />
        <div class="form-grid">
          <a-form-item label="代理审批人" required><PersonSelect v-model="actionForm.targetPersonId" :people="directory.people" :value-key="participantValueKey" :excluded-values="excludedTransferIds" :disabled="Boolean(directoryError) || busy === 'action'" /></a-form-item>
          <a-form-item label="委托截止时间" required><a-date-picker v-model:value="actionForm.delegationEndTime" show-time value-format="YYYY-MM-DDTHH:mm:ss" :disabled="busy === 'action'" style="width:100%" /></a-form-item>
        </div>
      </template>
      <template v-if="actionIsSignChange">
        <a-alert v-if="selectedAction === 'ADD_SIGN' && directoryError" type="warning" show-icon :message="directoryError" />
        <a-form-item :label="selectedAction === 'ADD_SIGN' ? '新增参与人员' : '移除参与人员'" required
          :extra="selectedAction === 'ADD_SIGN' ? '选择有效新人员，已参与此节点的人员不会重复加入。' : '仅选择同节点尚未完成的其他人员；原参与者即使不在有效目录中也可选择。'">
          <PersonSelect v-model="actionForm.targetPersonId" :people="signTargetPeople" :value-key="participantValueKey"
            :disabled="Boolean(busy) || !signApprovalMode || (selectedAction === 'ADD_SIGN' && Boolean(directoryError))" placeholder="搜索并选择目标人员" />
        </a-form-item>
      </template>
      <FlowTaskFormEditor v-if="actionOpen && ['APPROVE','RESUBMIT'].includes(selectedAction) && actionTaskId && selected" ref="taskFormEditor"
        :task-id="actionTaskId" :instance-id="selected.id" :token="token" :identity="identity" :disabled="busy === 'action'"
        @ready="taskFormReady = $event" @denied="taskFormDenied" />
      <a-alert v-if="selectedAction === 'APPROVE' && actionTask?.attachmentRequired" type="warning" show-icon
        :message="`当前节点要求至少上传 ${actionTask.minimumAttachmentCount} 个有效附件`" description="附件必须属于当前流程且已通过安全扫描；删除或其他流程的附件不能满足要求。" style="margin-bottom:16px" />
      <BusinessAttachmentsPanel v-if="selected && !actionIsClaim && !actionIsSignChange && !actionIsDelegation && canFile('VIEW')" :endpoint="`/api/business-attachments/flow-instances/${selected.id}`" :context="context" :writable="canFile('UPLOAD') && canFile('REFERENCE')" :title="actionTaskIsReturn ? '申请修正材料' : actionTaskIsForm ? '本次填写材料' : '本次审批材料'" description="附件保存在当前流程实例，提交动作不会改变附件权限。" compact @loaded="updateActionAttachments" />
    </a-form>
  </a-modal>
  <a-modal v-model:open="manualOpen" title="手动加签 · 当前审批节点之前" width="720px" :footer="null">
    <a-alert type="warning" show-icon message="加签只作用于当前运行流程，不会修改已发布流程" description="确认后当前审批任务会暂停；加签处理完成后恢复原审批任务。" style="margin-bottom:16px" />
    <a-form layout="vertical">
      <a-alert v-if="directoryError" type="warning" show-icon :message="directoryError" />
      <a-form-item label="加签处理人" required><PersonSelect v-model="manualForm.assigneeTenantMemberId" :people="directory.people" value-key="tenantMemberId" :disabled="Boolean(directoryError) || Boolean(busy)" /></a-form-item>
      <a-form-item label="加签原因" required><a-textarea v-model:value="manualForm.reason" :disabled="Boolean(busy)" :rows="3" placeholder="说明为什么需要在当前节点前增加审批" /></a-form-item>
      <a-alert type="info" show-icon message="加签仅增加本次审批人，不修改业务状态映射" description="业务状态仍由已发布流程节点统一回写，避免运行时临时配置造成口径不一致。" />
    </a-form>
    <section v-if="manualPreview" class="manual-node-preview">
      <strong>预检查通过</strong><span>将暂停当前审批任务，由 {{ manualPreview.assigneeName }}<template v-if="manualPreview.assigneeDepartment">（{{ manualPreview.assigneeDepartment }}）</template> 先处理。</span>
      <ul><li v-for="check in manualPreview.checks" :key="check">{{ check }}</li></ul>
    </section>
    <div class="modal-result-actions"><a-button :disabled="Boolean(busy)" @click="manualOpen = false">取消</a-button><a-button :disabled="Boolean(busy) || !canAddRuntimeNode" :loading="busy === 'manual-preview'" @click="previewManualNode">预检查</a-button><a-button type="primary" :disabled="!manualPreview?.allowed || Boolean(busy) || !canAddRuntimeNode" :loading="busy === 'manual-add'" @click="addManualNode">确认加签</a-button></div>
  </a-modal>
  <a-modal v-model:open="runtimeNodeOpen" title="添加审批或抄送" width="760px" :mask-closable="false" :closable="!busy" :keyboard="!busy"
    :style="{ top: '24px' }" :body-style="{ maxHeight: 'calc(100dvh - 180px)', overflowY: 'auto', boxSizing: 'border-box' }">
    <a-alert type="info" show-icon message="只修改本次流程，已发布模板及已完成历史保持原样。"
      description="可在当前节点之前、之后，或尚未执行节点之前、之后添加。当前节点前插会暂停原待办，新增节点结束后恢复。" style="margin-bottom:16px" />
    <a-alert v-if="runtimeNodeError" type="error" show-icon :message="runtimeNodeError" />
    <a-alert v-if="directoryError" type="warning" show-icon :message="directoryError" />
    <a-button v-if="runtimeNodeNeedsRefresh" :disabled="Boolean(busy)" :loading="busy === 'runtime-node-refresh'" @click="refreshRuntimeNodeInstance">重新读取当前流程</a-button>
    <a-form layout="vertical">
      <fieldset :disabled="Boolean(busy) || !canAddRuntimeNode" :inert="Boolean(busy) || !canAddRuntimeNode" style="min-width:0; margin:0; padding:0; border:0">
        <a-form-item label="节点类型" required>
          <a-radio-group v-model:value="runtimeNodeForm.nodeType" :disabled="Boolean(busy)">
            <a-radio-button value="APPROVAL">审批</a-radio-button><a-radio-button value="NOTIFICATION">抄送</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item label="添加位置" required
          :extra="runtimeNodeForm.position === 'BEFORE_CURRENT' ? '暂停当前节点全部未完成任务及处理时限；新增节点结束后恢复同一组原任务和剩余时限，已完成的审批与填写结果保留。' : runtimeNodeForm.position === 'AFTER_CURRENT' ? '多次在当前节点后添加时，最新节点紧接当前节点，原动态节点依次顺延。' : '只能选择尚未进入的节点；结束节点仅能在其之前添加。'">
          <a-radio-group v-model:value="runtimeNodeForm.position" :disabled="Boolean(busy) || !canAddRuntimeNode">
            <a-radio-button value="BEFORE_CURRENT">当前节点之前</a-radio-button>
            <a-radio-button value="AFTER_CURRENT">当前节点之后</a-radio-button>
            <a-radio-button value="BEFORE_NODE">指定节点之前</a-radio-button>
            <a-radio-button value="AFTER_NODE">指定节点之后</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item v-if="runtimeNodeForm.position === 'BEFORE_NODE' || runtimeNodeForm.position === 'AFTER_NODE'" label="指定待执行节点" required
          :extra="runtimeNodeAnchors.length ? '位置以当前流程实际执行情况为准，请在预览中核对。' : '当前没有允许在此位置添加的节点，可改选其他位置。'">
          <a-select v-model:value="runtimeNodeForm.anchorNodeKey" :disabled="Boolean(busy) || !canAddRuntimeNode || !runtimeNodeAnchors.length" show-search option-filter-prop="label"
            :options="runtimeNodeAnchors.map(anchor => ({ value: anchor.nodeKey, label: anchor.name || '待执行节点' }))" placeholder="选择添加位置对应的节点" />
        </a-form-item>
        <a-form-item :label="runtimeNodeForm.nodeType === 'NOTIFICATION' ? '抄送标题' : '审批节点名称'" required>
          <a-input v-model:value="runtimeNodeForm.name" :disabled="Boolean(busy)" placeholder="填写清晰的节点名称或消息标题" />
        </a-form-item>
        <a-form-item :label="runtimeNodeForm.nodeType === 'NOTIFICATION' ? '抄送人员' : '审批人员'" required
          :extra="context === 'platform' ? '选择当前平台的有效人员。' : '选择当前工作空间的有效人员。'">
          <PersonSelect v-model="runtimeNodeForm.assigneeIds" :people="directory.people" :value-key="participantValueKey" multiple
            :disabled="Boolean(busy) || Boolean(directoryError)" placeholder="搜索并选择一名或多名人员" />
        </a-form-item>
        <a-form-item v-if="runtimeNodeForm.nodeType === 'APPROVAL'" label="审批方式" required extra="顺序审批按照选择人员的顺序逐个处理，请在预览中核对。">
          <a-select v-model:value="runtimeNodeForm.approvalMode" :disabled="Boolean(busy)"
            :options="[{ value: 'OR_SIGN', label: '或签，一人同意即可' }, { value: 'ALL_SIGN', label: '会签，全部人员处理' }, { value: 'SEQUENTIAL', label: '顺序审批，逐人处理' }]" />
        </a-form-item>
        <a-form-item :label="runtimeNodeForm.nodeType === 'NOTIFICATION' ? '添加原因与抄送说明' : '添加原因'" required>
          <a-textarea v-model:value="runtimeNodeForm.reason" :disabled="Boolean(busy)" :rows="3" placeholder="说明为何需要增加此节点；抄送时将作为消息内容发送" />
        </a-form-item>
      </fieldset>
      <a-alert v-if="runtimeNodeForm.nodeType === 'APPROVAL'" type="info" show-icon
        message="指定审批或填写节点时，新增审批继承该节点的冻结字段、处理时限及异常规则；其他位置继承当前人工节点的策略。同人自动同意规则不会继承，请核对预览说明。" />
    </a-form>
    <section v-if="runtimeNodePreview" class="manual-node-preview">
      <strong>{{ runtimeNodePreview.allowed ? '添加位置已确认' : '预览未通过' }}</strong>
      <span>当前等待节点：{{ runtimeNodePreview.currentNodeName }}。</span>
      <span>实际添加位置：在“{{ runtimeNodePreview.anchorNodeName }}”{{ ['BEFORE_CURRENT', 'BEFORE_NODE'].includes(runtimeNodePreview.position) ? '之前' : '之后' }}，添加“{{ runtimeNodePreview.name }}”。</span>
      <span>{{ runtimeNodePreview.nodeType === 'APPROVAL' ? '审批人员' : '抄送人员' }}：{{ runtimeNodePreview.assignees.map(person => person.displayName + (person.departmentName ? `（${person.departmentName}）` : '')).join('、') }}</span>
      <span v-if="runtimeNodePreview.nodeType === 'APPROVAL'">审批方式：{{ ({ OR_SIGN: '或签', ALL_SIGN: '会签', SEQUENTIAL: '顺序审批' })[runtimeNodePreview.approvalMode] }}</span>
      <span>添加说明：{{ runtimeNodePreview.reason }}</span>
      <ul><li v-for="check in runtimeNodePreview.checks" :key="check">{{ check }}</li></ul>
    </section>
    <template #footer>
      <a-button :disabled="Boolean(busy)" @click="runtimeNodeOpen = false">取消</a-button>
      <a-button :disabled="Boolean(busy) || !canAddRuntimeNode || !runtimeNodePositionValid || Boolean(directoryError) || runtimeNodeNeedsRefresh" :loading="busy === 'runtime-node-preview'" @click="previewRuntimeNode">预览添加位置</a-button>
      <a-button type="primary" :disabled="!runtimeNodePreview?.allowed || !runtimeNodeSubmission || Boolean(busy) || !canAddRuntimeNode" :loading="busy === 'runtime-node-add'" @click="addRuntimeNode">确认添加</a-button>
    </template>
  </a-modal>
  <a-modal v-model:open="incidentOpen" :title="incidentForm.actionCode === 'RETRY' ? '重试异常节点' : '确认补偿并继续'" :confirm-loading="busy === 'incident'" :ok-button-props="{ disabled: !incidentForm.comment.trim() }" @ok="handleIncident"><a-alert type="warning" show-icon :message="selectedIncident?.errorMessage" style="margin-bottom:16px" /><a-form layout="vertical"><a-form-item label="处理说明" required><a-textarea v-model:value="incidentForm.comment" :placeholder="incidentForm.actionCode === 'RETRY' ? '说明重试依据' : '说明已完成的人工补偿'" /></a-form-item></a-form></a-modal>
</template>
