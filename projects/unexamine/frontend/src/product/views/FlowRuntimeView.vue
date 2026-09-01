<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { ApiError, api } from '../api'
import { productDateTime } from '../presentation'
import { allowsPermission } from '../permissions'
import { activeRuntimeTask, parseManualStatusMappings, parseRuntimeVariables, runtimeIdempotency, runtimeStatusLabel } from '../flow-runtime'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import type { FlowDefinitionView, FlowManualNodePreview, FlowManualNodeResult, FlowRuntimeActionResult, FlowRuntimeInstance, FlowRuntimeTask } from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system'; initialInstanceId?: number; initialTaskId?: number }>(), { context: 'system' })
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const flows = ref<FlowDefinitionView[]>([])
const instances = ref<FlowRuntimeInstance[]>([])
const selected = ref<FlowRuntimeInstance>()
const loading = ref(false)
const busy = ref('')
const startOpen = ref(false)
const actionOpen = ref(false)
const manualOpen = ref(false)
const manualPreview = ref<FlowManualNodePreview>()
const selectedAction = ref('APPROVE')
const startForm = reactive({ flowId: undefined as number | undefined, title: '', businessType: '', businessId: '', variables: '{}' })
const actionForm = reactive({ comment: '', targetAccountId: undefined as number | undefined })
const manualForm = reactive({ assigneeAccountId: undefined as number | undefined, reason: '', statusMappings: '{}' })
const manualIdempotencyKey = ref('')
const activeView = ref<'pending' | 'mine' | 'instances' | 'records'>('pending')

const publishedFlows = computed(() => flows.value.filter(flow => !!flow.currentVersionId))
const currentTask = computed(() => activeRuntimeTask(selected.value))
const visibleInstances = computed(() => instances.value.filter(instance => {
  if (activeView.value === 'mine') return instance.startedByAccountId === current.value?.accountId
  if (activeView.value === 'pending') return instance.tasks.some(task =>
    task.assigneeAccountId === current.value?.accountId && !task.completedAt && task.status !== 'COMPLETED')
  if (activeView.value === 'records') return Boolean(instance.finishedAt || instance.actions.length || instance.exceptions.length)
  return true
}))

function can(action: string) {
  return allowsPermission(current.value?.permissions, 'FLOW', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'FLOW', '*', action)
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
  return ({ PENDING: 'blue', SUSPENDED: 'orange', COMPLETED: 'green', APPROVE: 'green', REJECT: 'red', RETURN: 'purple' } as Record<string, string>)[status] || 'default'
}
function taskStatusLabel(status: string) {
  return ({ PENDING: '待处理', SUSPENDED: '已暂停', COMPLETED: '已完成', APPROVE: '已同意', REJECT: '已拒绝', RETURN: '已退回' } as Record<string, string>)[status] || status
}
function manualReason(task: FlowRuntimeTask) {
  return String(task.assigneeSnapshot.reason || '')
}
function publishedVersionNumber(flow: FlowDefinitionView) {
  return flow.versions.find(version => version.id === flow.currentVersionId)?.versionNumber
    || flow.versions[flow.versions.length - 1]?.versionNumber || 1
}
function flowName(flowId: number) {
  return flows.value.find(flow => flow.id === flowId)?.name || '业务流程'
}
function accountDisplayName(accountId?: number) {
  if (!accountId) return '未指定'
  return accountId === current.value?.accountId ? current.value?.displayName || '我' : '其他成员'
}
function actionLabel(action: string) {
  return ({ APPROVE: '同意', REJECT: '拒绝', RETURN: '退回', TRANSFER: '转交', WITHDRAW: '撤回', TERMINATE: '终止' } as Record<string, string>)[action] || '处理'
}

async function load(preferId?: number) {
  if (!token.value || !can('VIEW_RUNTIME')) return
  loading.value = true
  try {
    const [flowRows, instanceRows] = await Promise.all([
      api<FlowDefinitionView[]>('/api/flows', {}, token.value),
      api<FlowRuntimeInstance[]>('/api/flow-runtime/instances', {}, token.value),
    ])
    flows.value = flowRows
    instances.value = instanceRows
    const targetFromTask = props.initialTaskId
      ? instanceRows.find(instance => instance.tasks.some(task => task.id === props.initialTaskId))?.id : undefined
    const preferred = preferId ?? props.initialInstanceId ?? targetFromTask ?? selected.value?.id
    const id = preferred && visibleInstances.value.some(instance => instance.id === preferred)
      ? preferred : visibleInstances.value[0]?.id
    selected.value = id ? await api<FlowRuntimeInstance>(`/api/flow-runtime/instances/${id}`, {}, token.value) : undefined
  } catch (error) {
    message.error(describeError(error))
  } finally { loading.value = false }
}

function openStart() {
  Object.assign(startForm, { flowId: publishedFlows.value[0]?.id, title: '', businessType: '', businessId: '', variables: '{}' })
  startOpen.value = true
}

async function startFlow() {
  if (!token.value || !startForm.flowId || !startForm.title.trim()) return
  busy.value = 'start'
  try {
    const variables = parseRuntimeVariables(startForm.variables)
    const result = await api<FlowRuntimeActionResult>('/api/flow-runtime/instances', {
      method: 'POST', body: JSON.stringify({
        flowId: startForm.flowId, title: startForm.title,
        businessType: startForm.businessType || undefined, businessId: startForm.businessId || undefined,
        businessSnapshot: startForm.businessType ? { label: startForm.title } : {}, variables,
        idempotencyKey: runtimeIdempotency('flow-start'),
      }),
    }, token.value)
    startOpen.value = false
    message.success(result.instance.status === 'WAITING' ? '流程已发起并生成待办' : `流程已发起：${runtimeStatusLabel(result.instance.status)}`)
    await load(result.instance.id)
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function selectInstance(id: number) {
  if (!token.value) return
  try { selected.value = await api<FlowRuntimeInstance>(`/api/flow-runtime/instances/${id}`, {}, token.value) }
  catch (error) { message.error(describeError(error)) }
}

function openAction(action: string) {
  selectedAction.value = action
  Object.assign(actionForm, { comment: '', targetAccountId: undefined })
  actionOpen.value = true
}

async function handleAction() {
  if (!token.value || !selected.value) return
  const action = selectedAction.value
  const needsReason = ['REJECT', 'RETURN', 'WITHDRAW', 'TERMINATE'].includes(action)
  if (needsReason && !actionForm.comment.trim()) return message.warning('该动作必须填写原因')
  if (action === 'TRANSFER' && !actionForm.targetAccountId) return message.warning('转交必须填写目标账号')
  busy.value = 'action'
  try {
    const path = ['WITHDRAW', 'TERMINATE'].includes(action)
      ? `/api/flow-runtime/instances/${selected.value.id}/actions`
      : `/api/flow-runtime/tasks/${currentTask.value?.id}/actions`
    const result = await api<FlowRuntimeActionResult>(path, {
      method: 'POST', body: JSON.stringify({ actionCode: action, comment: actionForm.comment || undefined,
        idempotencyKey: runtimeIdempotency(`flow-${action.toLowerCase()}`), variables: {},
        targetAccountId: actionForm.targetAccountId }),
    }, token.value)
    actionOpen.value = false
    selected.value = result.instance
    await load(result.instance.id)
    message.success(`${actionLabel(action)}已保存，流程当前为${runtimeStatusLabel(result.instance.status)}`)
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

function openManualNode() {
  Object.assign(manualForm, { assigneeAccountId: current.value?.accountId, reason: '', statusMappings: '{}' })
  manualIdempotencyKey.value = runtimeIdempotency('flow-manual-node')
  manualPreview.value = undefined
  manualOpen.value = true
}

function manualRequest() {
  if (!manualForm.assigneeAccountId) throw new Error('请选择当前上下文内的目标处理人账号')
  if (!manualForm.reason.trim()) throw new Error('手动加签必须填写可追溯原因')
  return {
    assigneeAccountId: manualForm.assigneeAccountId,
    position: 'BEFORE_CURRENT',
    reason: manualForm.reason.trim(),
    statusMappings: parseManualStatusMappings(manualForm.statusMappings),
    idempotencyKey: manualIdempotencyKey.value,
  }
}

async function previewManualNode() {
  if (!token.value || !selected.value) return
  busy.value = 'manual-preview'
  try {
    manualPreview.value = await api<FlowManualNodePreview>(`/api/flow-runtime/instances/${selected.value.id}/manual-node-preview`, {
      method: 'POST', body: JSON.stringify(manualRequest()),
    }, token.value)
    message.success('加签预检查通过，确认后才会暂停当前审批节点')
  } catch (error) {
    manualPreview.value = undefined
    message.error(describeError(error))
  } finally { busy.value = '' }
}

async function addManualNode() {
  if (!token.value || !selected.value || !manualPreview.value?.allowed) return
  busy.value = 'manual-add'
  try {
    const result = await api<FlowManualNodeResult>(`/api/flow-runtime/instances/${selected.value.id}/manual-nodes`, {
      method: 'POST', body: JSON.stringify(manualRequest()),
    }, token.value)
    manualOpen.value = false
    activeView.value = 'instances'
    await load(result.instance.id)
    message.success('手动加签已生成，原审批节点已暂停')
  } catch (error) {
    manualPreview.value = undefined
    message.error(describeError(error))
  } finally { busy.value = '' }
}

onMounted(() => {
  if (props.initialInstanceId || props.initialTaskId) activeView.value = 'instances'
  void load(props.initialInstanceId)
})
watch(() => [props.initialInstanceId, props.initialTaskId], () => {
  if (!props.initialInstanceId && !props.initialTaskId) return
  activeView.value = 'instances'
  void load(props.initialInstanceId)
})
watch(() => [manualForm.assigneeAccountId, manualForm.reason, manualForm.statusMappings], () => {
  if (manualOpen.value) manualPreview.value = undefined
})
watch([activeView, visibleInstances], () => {
  const stillVisible = visibleInstances.value.some(instance => instance.id === selected.value?.id)
  if (!stillVisible) {
    const first = visibleInstances.value[0]
    if (first) void selectInstance(first.id)
    else selected.value = undefined
  }
})
</script>

<template>
  <div class="flow-runtime-page">
    <div class="page-heading compact-heading">
      <div><p class="eyebrow">流程 · 运行记录</p><h1>发起、审批与路径说明</h1><p>流程固定使用发起时的发布版本；当前处理人、处理原因和下一步均来自实际运行记录。</p></div>
      <a-button v-if="can('START')" type="primary" :disabled="!publishedFlows.length" @click="openStart">发起流程</a-button>
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
          <button v-for="item in visibleInstances" :key="item.id" :class="['runtime-instance-item', { active: selected?.id === item.id }]" @click="selectInstance(item.id)">
            <span><strong>{{ item.title }}</strong><small>{{ flowName(item.flowId) }} · 版本 V{{ item.flowVersionNumber }}</small></span>
            <a-tag :color="statusColor(item.status)">{{ runtimeStatusLabel(item.status) }}</a-tag>
          </button>
          <a-empty v-if="!loading && !visibleInstances.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" :description="activeView === 'pending' ? '当前没有需要你处理的流程' : activeView === 'mine' ? '你还没有发起流程' : '当前分类没有运行记录'" />
        </a-spin>
      </section>

      <main v-if="selected" class="runtime-instance-detail">
        <section class="panel-card runtime-instance-hero">
          <div><p class="eyebrow">流程实例</p><h2>{{ selected.title }}</h2><span><a-tag :color="statusColor(selected.status)">{{ runtimeStatusLabel(selected.status) }}</a-tag><a-tag>流程版本 V{{ selected.flowVersionNumber }}</a-tag></span></div>
          <div class="runtime-actions"><a-button v-if="can('ADD_MANUAL_NODE') && selected.status === 'WAITING'" type="dashed" @click="openManualNode">手动加签</a-button><a-button v-for="action in selected.allowedActions" :key="action" :type="action === 'APPROVE' ? 'primary' : 'default'" @click="openAction(action)">{{ actionLabel(action) }}</a-button></div>
        </section>
        <section class="runtime-explain-grid">
          <article class="panel-card"><small>当前节点</small><strong>{{ selected.currentNodeName || '流程已结束' }}</strong><span>按当前流程版本推进</span></article>
          <article class="panel-card"><small>当前处理人</small><strong>{{ currentTask?.assigneeAccountId ? accountDisplayName(currentTask.assigneeAccountId) : '无需人工处理' }}</strong><span>{{ currentTask?.candidates[0]?.resolutionReason || '按发布版本自动推进' }}</span></article>
          <article class="panel-card"><small>下一步说明</small><strong>{{ selected.nextStep }}</strong><span>由运行引擎根据当前唯一状态计算</span></article>
        </section>
        <section class="panel-card runtime-snapshot">
          <div class="panel-title"><strong>流程版本与业务关联</strong><a-tag color="purple">运行时已固定</a-tag></div>
          <a-descriptions bordered size="small" :column="2"><a-descriptions-item label="流程">{{ flowName(selected.flowId) }} · V{{ selected.flowVersionNumber }}</a-descriptions-item><a-descriptions-item label="发起人">{{ accountDisplayName(selected.startedByAccountId) }}</a-descriptions-item><a-descriptions-item label="关联业务">{{ selected.businessType ? `${selected.businessType} ${selected.businessId || ''}` : '未关联' }}</a-descriptions-item><a-descriptions-item label="发起时间">{{ productDateTime(selected.startedAt) }}</a-descriptions-item><a-descriptions-item label="发起参数" :span="2"><code>{{ JSON.stringify(selected.variables) }}</code></a-descriptions-item></a-descriptions>
        </section>
        <section class="panel-card runtime-task-map">
          <div class="panel-title"><strong>实例节点快照</strong><a-tag>{{ selected.tasks.length }} 个任务节点</a-tag></div>
          <div class="runtime-task-map__list">
            <article v-for="(task, index) in selected.tasks" :key="task.id" :class="{ manual: task.taskType === 'MANUAL_APPROVAL' }">
              <span class="runtime-task-map__index">{{ index + 1 }}</span>
              <div><span><strong>{{ task.taskType === 'MANUAL_APPROVAL' ? '手动加签审批' : `流程任务 ${index + 1}` }}</strong><a-tag v-if="task.taskType === 'MANUAL_APPROVAL'" color="purple">运行时新增</a-tag><a-tag :color="taskStatusColor(task.status)">{{ taskStatusLabel(task.status) }}</a-tag></span><p v-if="manualReason(task)">{{ manualReason(task) }}</p><small>处理人：{{ accountDisplayName(task.assigneeAccountId) }} · {{ task.candidates[0]?.resolutionReason || '按发布版本解析' }}</small></div>
            </article>
          </div>
        </section>
        <section v-if="selected.exceptions.length" class="panel-card runtime-exceptions"><div class="panel-title"><strong>处理异常</strong><a-tag color="red">{{ selected.exceptions.length }}</a-tag></div><a-alert v-for="item in selected.exceptions" :key="item.id" type="error" show-icon :message="item.errorMessage" description="流程已保留异常现场，请由管理员查看技术详情。" /></section>
        <section class="panel-card runtime-timeline"><div class="panel-title"><strong>处理时间线</strong><a-tag>{{ selected.actions.length }} 个动作</a-tag></div><a-timeline><a-timeline-item v-for="action in selected.actions" :key="action.id" :color="action.actionCode === 'APPROVE' ? 'green' : 'blue'"><strong>{{ actionLabel(action.actionCode) }}</strong><p>{{ action.comment || '系统自动推进并保存结果' }}</p><small>{{ accountDisplayName(action.actedByAccountId) }} · {{ productDateTime(action.actedAt) }}</small></a-timeline-item></a-timeline><a-empty v-if="!selected.actions.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="等待首个处理动作" /></section>
      </main>
    </div>
    </template>
  </div>

  <a-modal v-model:open="startOpen" title="发起已发布流程" width="680px" :confirm-loading="busy === 'start'" @ok="startFlow">
    <a-form layout="vertical"><a-form-item label="流程" required><a-select v-model:value="startForm.flowId" :options="publishedFlows.map(flow => ({ value: flow.id, label: `${flow.name} · V${publishedVersionNumber(flow)}` }))" /></a-form-item><a-form-item label="申请标题" required><a-input v-model:value="startForm.title" placeholder="例如：客户合同审批" /></a-form-item><div class="form-grid"><a-form-item label="关联业务类型"><a-input v-model:value="startForm.businessType" placeholder="可选" /></a-form-item><a-form-item label="关联业务编号"><a-input v-model:value="startForm.businessId" placeholder="可选" /></a-form-item></div><a-form-item label="高级参数（JSON 对象）"><a-textarea v-model:value="startForm.variables" :rows="5" /></a-form-item></a-form>
  </a-modal>
  <a-modal v-model:open="actionOpen" :title="`处理流程 · ${actionLabel(selectedAction)}`" :confirm-loading="busy === 'action'" @ok="handleAction"><a-form layout="vertical"><a-alert type="info" show-icon :message="selected?.nextStep" style="margin-bottom:16px" /><a-form-item label="处理意见" :required="['REJECT','RETURN','WITHDRAW','TERMINATE'].includes(selectedAction)"><a-textarea v-model:value="actionForm.comment" :placeholder="selectedAction === 'APPROVE' ? '可选填写审批意见' : '填写可追溯的动作原因'" /></a-form-item><a-form-item v-if="selectedAction === 'TRANSFER'" label="目标账号编号" required><a-input-number v-model:value="actionForm.targetAccountId" :min="1" style="width:100%" /></a-form-item></a-form></a-modal>
  <a-modal v-model:open="manualOpen" title="手动加签 · 当前审批节点之前" width="720px" :footer="null">
    <a-alert type="warning" show-icon message="加签只作用于当前运行流程，不会修改已发布流程" description="确认后当前审批任务会暂停；加签处理完成后恢复原审批任务。" style="margin-bottom:16px" />
    <a-form layout="vertical">
      <a-form-item label="目标处理人账号编号" required><a-input-number v-model:value="manualForm.assigneeAccountId" :min="1" style="width:100%" /></a-form-item>
      <a-form-item label="加签原因" required><a-textarea v-model:value="manualForm.reason" :rows="3" placeholder="说明为什么需要在当前节点前增加审批" /></a-form-item>
      <a-form-item label="业务状态映射（JSON，可选）" extra="仅支持 APPROVE、REJECT、RETURN；配置后仍会校验目标模块动作权限和当前业务状态。"><a-textarea v-model:value="manualForm.statusMappings" :rows="6" placeholder='{"APPROVE":{"businessAction":"APPROVE","expectedCurrentStatus":"ACTIVE","targetStatus":"APPROVED"}}' /></a-form-item>
    </a-form>
    <section v-if="manualPreview" class="manual-node-preview">
      <strong>预检查通过</strong><span>将暂停当前审批任务，由{{ accountDisplayName(manualPreview.assigneeAccountId) }}先处理。</span>
      <ul><li v-for="check in manualPreview.checks" :key="check">{{ check }}</li></ul>
    </section>
    <div class="modal-result-actions"><a-button @click="manualOpen = false">取消</a-button><a-button :loading="busy === 'manual-preview'" @click="previewManualNode">预检查</a-button><a-button type="primary" :disabled="!manualPreview?.allowed" :loading="busy === 'manual-add'" @click="addManualNode">确认加签</a-button></div>
  </a-modal>
</template>
