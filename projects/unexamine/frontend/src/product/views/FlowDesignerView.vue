<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { ApiError, api } from '../api'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { localFlowIssues } from '../flow-designer'
import { productDateTime, versionLabel } from '../presentation'
import PersonSelect from '../components/PersonSelect.vue'
import FlowGraphCanvas from '../components/FlowGraphCanvas.vue'
import FlowBindingsView from './FlowBindingsView.vue'
import FlowRuntimeView from './FlowRuntimeView.vue'
import type {
  FlowDefinitionView, FlowEdgeInput, FlowIssue, FlowNodeInput, FlowNodeType,
  FlowPublicationCheck, FlowPublicationResult, FlowSimulationResult, SystemPeopleDirectory,
} from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'system' })
const activeSection = ref<'designer' | 'bindings' | 'runtime'>('designer')
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const flows = ref<FlowDefinitionView[]>([])
const selected = ref<FlowDefinitionView>()
const nodes = ref<FlowNodeInput[]>([])
const edges = ref<FlowEdgeInput[]>([])
const selectedNodeKey = ref<string>()
const selectedEdgeKey = ref<string>()
const graphCanvas = ref<InstanceType<typeof FlowGraphCanvas>>()
const publicationCheck = ref<FlowPublicationCheck>()
const simulation = ref<FlowSimulationResult>()
const directory = ref<SystemPeopleDirectory>({ departments: [], people: [], permissionVersion: 0 })
const loading = ref(false)
const busy = ref('')
const pageError = ref('')
const createOpen = ref(false)
const publishOpen = ref(false)
const sampleAmount = ref(8000)
const connectionTargetKey = ref<string>()
const createForm = reactive({ code: '', name: '', description: '' })
const publishForm = reactive({ changeSummary: '' })
let nodeSequence = 0

const palette: { type: FlowNodeType; label: string; hint: string }[] = [
  { type: 'START', label: '开始', hint: '唯一入口' },
  { type: 'GATEWAY', label: '条件', hint: '按样例变量选路' },
  { type: 'APPROVAL', label: '审批', hint: '审批人和字段权限' },
  { type: 'UPDATE_FIELD', label: '回写', hint: '更新业务状态和字段' },
  { type: 'WEBHOOK', label: '服务', hint: '受控 Webhook 调用' },
  { type: 'NOTIFICATION', label: '通知', hint: '发送业务通知' },
  { type: 'WAIT_TIMER', label: '等待', hint: '定时恢复' },
  { type: 'AI', label: 'AI', hint: '受控模型节点' },
  { type: 'END', label: '结束', hint: '流程终点' },
]

const localIssues = computed(() => localFlowIssues(nodes.value, edges.value))
const selectedNode = computed(() => nodes.value.find(node => node.nodeKey === selectedNodeKey.value))
const selectedEdge = computed(() => edges.value.find(edge => edge.edgeKey === selectedEdgeKey.value))
const issueCount = computed(() => localIssues.value.length)
const connectionTargets = computed(() => {
  if (!selectedNode.value || selectedNode.value.nodeType === 'END') return []
  const connected = new Set(edges.value.filter(edge => edge.sourceNodeKey === selectedNode.value?.nodeKey)
    .map(edge => edge.targetNodeKey))
  return nodes.value.filter(node => node.nodeKey !== selectedNode.value?.nodeKey
      && node.nodeType !== 'START' && !connected.has(node.nodeKey))
    .map(node => ({ value: node.nodeKey, label: `${node.name}（${nodeTypeLabel(node.nodeType)}）` }))
})
const approverType = computed({
  get: () => String(selectedNode.value?.assigneePolicy?.type || ''),
  set: (type: string) => setApproverType(type),
})
const selectedApproverIds = computed({
  get: () => (selectedNode.value?.assigneePolicy?.tenantMemberIds as number[] | undefined) || [],
  set: (ids: number[]) => {
    if (selectedNode.value) {
      graphCanvas.value?.checkpoint()
      selectedNode.value.assigneePolicy = { type: 'PERSON', tenantMemberIds: ids }
    }
  },
})
const selectedDepartmentIds = computed({
  get: () => (selectedNode.value?.assigneePolicy?.departmentIds as number[] | undefined) || [],
  set: (ids: number[]) => {
    if (!selectedNode.value) return
    graphCanvas.value?.checkpoint()
    selectedNode.value.assigneePolicy = { type: approverType.value, departmentIds: ids, includeDescendants: true }
  },
})
const approvalMode = computed({
  get: () => String(selectedNode.value?.config.approvalMode || 'OR_SIGN'),
  set: (value: string) => {
    graphCanvas.value?.checkpoint()
    setConfig('approvalMode', value)
  },
})
const personFieldCode = computed({
  get: () => String(selectedNode.value?.assigneePolicy?.fieldCode || ''),
  set: (fieldCode: string) => {
    if (selectedNode.value) selectedNode.value.assigneePolicy = { type: 'PERSON_FIELD', fieldCode }
  },
})

function can(action: string) {
  return allowsPermission(current.value?.permissions, 'FLOW', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'FLOW', '*', action)
}

function describeError(error: unknown) {
  const text = error instanceof ApiError ? `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
    : error instanceof Error ? error.message : '操作失败'
  pageError.value = text
  return text
}

function nodeTypeLabel(type: FlowNodeType) {
  return palette.find(item => item.type === type)?.label || '业务节点'
}

function flowStatusLabel(status: string) {
  return status === 'PUBLISHED' ? '已发布' : status === 'DRAFT' ? '草稿' : status
}

function simulationStepName(nodeKey: string) {
  return nodes.value.find(node => node.nodeKey === nodeKey)?.name || '流程步骤'
}

function simulationStepType(nodeKey: string, fallback: FlowNodeType) {
  return nodeTypeLabel(nodes.value.find(node => node.nodeKey === nodeKey)?.nodeType || fallback)
}

async function loadFlows(preferId?: number) {
  if (!token.value || !can('VIEW')) return
  loading.value = true
  try {
    flows.value = await api<FlowDefinitionView[]>('/api/flows', {}, token.value)
    const id = preferId ?? selected.value?.id ?? flows.value[0]?.id
    if (id) await selectFlow(id)
    else selected.value = undefined
  } catch (error) {
    message.error(describeError(error))
  } finally {
    loading.value = false
  }
}

async function loadDirectory() {
  if (props.context !== 'system' || !token.value) return
  try { directory.value = await api<SystemPeopleDirectory>('/api/system-directory', {}, token.value) }
  catch (error) { message.error(describeError(error)) }
}

async function selectFlow(id: number) {
  if (!token.value) return
  try {
    const detail = await api<FlowDefinitionView>(`/api/flows/${id}`, {}, token.value)
    selected.value = detail
    nodes.value = detail.nodes.map(({ id: _id, version: _version, ...node }) => ({ ...node, config: node.config || {} }))
    edges.value = detail.edges.map(({ id: _id, version: _version, ...edge }) => ({ ...edge, config: edge.config || {} }))
    selectedNodeKey.value = nodes.value[0]?.nodeKey
    selectedEdgeKey.value = undefined
    connectionTargetKey.value = undefined
    graphCanvas.value?.clearHistory()
    publicationCheck.value = undefined
    simulation.value = undefined
  } catch (error) {
    message.error(describeError(error))
  }
}

async function createFlow() {
  if (!token.value || !createForm.name.trim()) return
  busy.value = 'create'
  try {
    const flow = await api<FlowDefinitionView>('/api/flows', {
      method: 'POST', body: JSON.stringify({
        code: createForm.code || undefined, name: createForm.name, description: createForm.description || undefined,
      }),
    }, token.value)
    createOpen.value = false
    Object.assign(createForm, { code: '', name: '', description: '' })
    message.success('流程草稿已创建，请继续配置节点和处理规则')
    await loadFlows(flow.id)
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

function addNode(type: FlowNodeType) {
  if (!can('DESIGN')) return
  if (type === 'START' && nodes.value.some(node => node.nodeType === 'START')) return message.warning('开始节点只能有一个')
  if (type === 'END' && nodes.value.some(node => node.nodeType === 'END')) return message.warning('当前画布已有结束节点')
  graphCanvas.value?.checkpoint()
  nodeSequence += 1
  const key = `${type.toLowerCase()}_${Date.now().toString(36)}_${nodeSequence}`
  const input: FlowNodeInput = {
    nodeKey: key, nodeType: type, name: palette.find(item => item.type === type)?.label || type,
    positionX: 80 + (nodes.value.length % 4) * 220,
    positionY: 80 + Math.floor(nodes.value.length / 4) * 150,
    config: {},
  }
  if (type === 'GATEWAY') input.name = '金额条件'
  if (type === 'APPROVAL') {
    input.name = '负责人审批'
    input.config = { approvalMode: 'OR_SIGN' }
    input.assigneePolicy = { type: 'PERSON', tenantMemberIds: [] }
  }
  if (type === 'WEBHOOK') input.name = '调用业务服务'
  if (type === 'NOTIFICATION') {
    input.name = '结果通知'
    input.assigneePolicy = { type: 'PERSON', tenantMemberIds: [] }
    input.config = { subject: '流程通知：${instanceTitle}', content: '流程已更新，请打开消息查看详情。' }
  }
  if (type === 'UPDATE_FIELD') {
    input.name = '回写业务结果'
    input.config = { businessAction: 'UPDATE', fieldUpdates: {} }
  }
  nodes.value = [...nodes.value, input]
  selectedNodeKey.value = key
  selectedEdgeKey.value = undefined
}

function removeNode(key: string) {
  graphCanvas.value?.checkpoint()
  nodes.value = nodes.value.filter(node => node.nodeKey !== key)
  edges.value = edges.value.filter(edge => edge.sourceNodeKey !== key && edge.targetNodeKey !== key)
  selectedNodeKey.value = nodes.value[0]?.nodeKey
  selectedEdgeKey.value = undefined
}

function selectCanvasNode(nodeKey: string) {
  selectedNodeKey.value = nodeKey
  selectedEdgeKey.value = undefined
  connectionTargetKey.value = undefined
}

function selectCanvasEdge(edgeKey: string) {
  selectedEdgeKey.value = edgeKey
  selectedNodeKey.value = undefined
  connectionTargetKey.value = undefined
}

function connectSelectedNode() {
  const source = selectedNode.value
  const target = nodes.value.find(node => node.nodeKey === connectionTargetKey.value)
  if (!source || !target || source.nodeType === 'END' || target.nodeType === 'START') return
  if (edges.value.some(edge => edge.sourceNodeKey === source.nodeKey && edge.targetNodeKey === target.nodeKey)) return
  graphCanvas.value?.checkpoint()
  const edge: FlowEdgeInput = {
    edgeKey: `edge_${source.nodeKey}_${target.nodeKey}_${Date.now().toString(36)}`,
    sourceNodeKey: source.nodeKey,
    targetNodeKey: target.nodeKey,
    priorityOrder: (edges.value.filter(item => item.sourceNodeKey === source.nodeKey).length + 1) * 10,
    config: {},
  }
  edges.value = [...edges.value, edge]
  selectedNodeKey.value = undefined
  selectedEdgeKey.value = edge.edgeKey
  connectionTargetKey.value = undefined
}

function removeSelectedEdge() {
  if (!selectedEdge.value) return
  graphCanvas.value?.checkpoint()
  edges.value = edges.value.filter(edge => edge.edgeKey !== selectedEdge.value?.edgeKey)
  selectedEdgeKey.value = undefined
}

function updateEdgeCondition(value: string) {
  if (!selectedEdge.value) return
  selectedEdge.value.conditionExpression = value.trim() || undefined
  publicationCheck.value = undefined
  simulation.value = undefined
}

function updateEdgePriority(value: number | null) {
  if (selectedEdge.value) selectedEdge.value.priorityOrder = value || 10
}

function autoLayout() {
  if (!nodes.value.length) return
  graphCanvas.value?.checkpoint()
  const incoming = new Map<string, number>()
  nodes.value.forEach(node => incoming.set(node.nodeKey, 0))
  edges.value.forEach(edge => incoming.set(edge.targetNodeKey, (incoming.get(edge.targetNodeKey) || 0) + 1))
  const roots = nodes.value.filter(node => node.nodeType === 'START' || !incoming.get(node.nodeKey))
  const levels = new Map<string, number>(roots.map(node => [node.nodeKey, 0]))
  const queue = roots.map(node => node.nodeKey)
  while (queue.length) {
    const source = queue.shift()!
    const nextLevel = (levels.get(source) || 0) + 1
    edges.value.filter(edge => edge.sourceNodeKey === source).forEach(edge => {
      if (!levels.has(edge.targetNodeKey) || (levels.get(edge.targetNodeKey) || 0) < nextLevel) {
        levels.set(edge.targetNodeKey, nextLevel)
        queue.push(edge.targetNodeKey)
      }
    })
  }
  const byLevel = new Map<number, FlowNodeInput[]>()
  nodes.value.forEach(node => {
    const level = levels.get(node.nodeKey) ?? 0
    byLevel.set(level, [...(byLevel.get(level) || []), node])
  })
  nodes.value = nodes.value.map(node => {
    const level = levels.get(node.nodeKey) ?? 0
    const siblings = byLevel.get(level) || []
    const index = siblings.findIndex(item => item.nodeKey === node.nodeKey)
    return { ...node, positionX: 70 + level * 230, positionY: 70 + index * 145 }
  })
}

function setApproverType(type: string) {
  if (!selectedNode.value) return
  graphCanvas.value?.checkpoint()
  if (type === 'PERSON') selectedNode.value.assigneePolicy = { type, tenantMemberIds: [] }
  else if (['DEPARTMENT', 'DEPARTMENT_MANAGER'].includes(type)) {
    selectedNode.value.assigneePolicy = { type, departmentIds: [], includeDescendants: true }
  } else if (type === 'PERSON_FIELD') selectedNode.value.assigneePolicy = { type, fieldCode: '' }
  else selectedNode.value.assigneePolicy = { type }
}

function setConfig(key: string, value: unknown) {
  if (!selectedNode.value) return
  selectedNode.value.config = { ...selectedNode.value.config, [key]: value }
}

function setTimeoutPolicy(key: string, value: unknown) {
  if (!selectedNode.value) return
  selectedNode.value.timeoutPolicy = { ...(selectedNode.value.timeoutPolicy || {}), [key]: value }
}

function setExceptionPolicy(key: string, value: unknown) {
  if (!selectedNode.value) return
  selectedNode.value.exceptionPolicy = { ...(selectedNode.value.exceptionPolicy || {}), [key]: value }
}

function writebackEntries() {
  return Object.entries((selectedNode.value?.config.fieldUpdates as Record<string, unknown> | undefined) || {})
}

function addWritebackEntry() {
  graphCanvas.value?.checkpoint()
  const current = { ...((selectedNode.value?.config.fieldUpdates as Record<string, unknown> | undefined) || {}) }
  let index = Object.keys(current).length + 1
  while (`field_${index}` in current) index += 1
  current[`field_${index}`] = ''
  setConfig('fieldUpdates', current)
}

function renameWritebackEntry(previous: string, next: string) {
  const code = next.trim()
  if (!selectedNode.value || !code || code === previous) return
  const current = { ...((selectedNode.value.config.fieldUpdates as Record<string, unknown> | undefined) || {}) }
  if (code in current) return message.warning('同一业务字段只能配置一次')
  const value = current[previous]
  delete current[previous]
  current[code] = value
  setConfig('fieldUpdates', current)
}

function setWritebackValue(code: string, value: string) {
  const current = { ...((selectedNode.value?.config.fieldUpdates as Record<string, unknown> | undefined) || {}) }
  current[code] = value
  setConfig('fieldUpdates', current)
}

function removeWritebackEntry(code: string) {
  graphCanvas.value?.checkpoint()
  const current = { ...((selectedNode.value?.config.fieldUpdates as Record<string, unknown> | undefined) || {}) }
  delete current[code]
  setConfig('fieldUpdates', current)
}

function nodeIssues(key: string) {
  return localIssues.value.filter(issue => issue.location.includes(key))
}

async function saveDraft(silent = false) {
  if (!token.value || !selected.value) return false
  busy.value = 'save'
  try {
    const detail = await api<FlowDefinitionView>(`/api/flows/${selected.value.id}/draft`, {
      method: 'PUT', body: JSON.stringify({ expectedVersion: selected.value.version, nodes: nodes.value, edges: edges.value }),
    }, token.value)
    selected.value = detail
    nodes.value = detail.nodes.map(({ id: _id, version: _version, ...node }) => ({ ...node, config: node.config || {} }))
    edges.value = detail.edges.map(({ id: _id, version: _version, ...edge }) => ({ ...edge, config: edge.config || {} }))
    await refreshListOnly()
    if (!silent) message.success('流程草稿已保存')
    return true
  } catch (error) {
    message.error(describeError(error))
    return false
  } finally {
    busy.value = ''
  }
}

async function refreshListOnly() {
  if (!token.value) return
  flows.value = await api<FlowDefinitionView[]>('/api/flows', {}, token.value)
}

async function checkPublish() {
  if (!token.value || !selected.value) return
  if (!(await saveDraft(true))) return
  busy.value = 'check'
  try {
    publicationCheck.value = await api<FlowPublicationCheck>(
      `/api/flows/${selected.value.id}/publication-check`, {}, token.value)
    publicationCheck.value.valid ? message.success('发布检查通过') : message.warning(`还有 ${publicationCheck.value.issues.length} 项需要处理`)
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

async function simulateFlow() {
  if (!token.value || !selected.value) return
  if (!(await saveDraft(true))) return
  busy.value = 'simulate'
  try {
    simulation.value = await api<FlowSimulationResult>(`/api/flows/${selected.value.id}/simulate`, {
      method: 'POST', body: JSON.stringify({ variables: { amount: sampleAmount.value } }),
    }, token.value)
    simulation.value.successful ? message.success('样例路径模拟通过') : message.warning('模拟未能到达结束节点')
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

async function publishFlow() {
  if (!token.value || !selected.value || !publishForm.changeSummary.trim()) return
  busy.value = 'publish'
  try {
    const result = await api<FlowPublicationResult>(`/api/flows/${selected.value.id}/publish`, {
      method: 'POST', body: JSON.stringify({
        expectedDraftRevision: selected.value.draftRevision,
        changeSummary: publishForm.changeSummary,
        simulationVariables: { amount: sampleAmount.value },
      }),
    }, token.value)
    publishOpen.value = false
    publishForm.changeSummary = ''
    message.success(`流程${versionLabel(result.versionNumber)}已发布，新运行实例将使用该版本`)
    await loadFlows(selected.value.id)
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

onMounted(() => { void Promise.all([loadFlows(), loadDirectory()]) })
</script>

<template>
  <div class="flow-designer-page">
    <div class="page-heading compact-heading">
      <div><p class="eyebrow">{{ context === 'platform' ? '平台' : '系统后台' }} · 流程</p><h1>{{ activeSection === 'bindings' ? '流程触发绑定' : activeSection === 'runtime' ? '流程运行实例' : '流程设计与发布' }}</h1><p>{{ activeSection === 'bindings' ? '把系统内已发布模块的业务事件连接到已发布流程；工作空间覆盖优先于系统默认。' : activeSection === 'runtime' ? '从主入口发起并处理流程，查看固定版本、当前处理人、处理理由和下一步。' : '草稿、模拟和发布分别授权；发布后生成不可变版本，后续草稿不会改写运行记录。' }}</p></div>
      <a-button v-if="activeSection === 'designer' && can('DESIGN')" type="primary" @click="createOpen = true">新建流程</a-button>
    </div>

    <a-segmented v-model:value="activeSection" class="flow-config-tabs" :options="context === 'system' ? [{ value: 'designer', label: '设计与发布' }, { value: 'bindings', label: '触发绑定' }, { value: 'runtime', label: '运行实例' }] : [{ value: 'designer', label: '设计与发布' }, { value: 'runtime', label: '运行实例' }]" />
    <a-alert v-if="pageError" type="error" show-icon closable :message="pageError" @close="pageError = ''" />
    <FlowBindingsView v-if="context === 'system' && activeSection === 'bindings'" />
    <FlowRuntimeView v-if="activeSection === 'runtime'" :context="context" />
    <a-alert v-if="activeSection === 'designer' && !can('VIEW')" type="warning" show-icon message="当前工作范围没有流程查看权限" />
    <div v-if="activeSection === 'designer' && can('VIEW')" class="flow-designer-layout">
      <section class="panel-card flow-list-panel">
        <div class="panel-title"><strong>流程列表</strong><a-tag>{{ context === 'platform' ? '平台' : '当前系统' }}</a-tag></div>
        <a-spin :spinning="loading">
          <button v-for="flow in flows" :key="flow.id" :class="['flow-list-item', { active: selected?.id === flow.id }]" @click="selectFlow(flow.id)">
            <span><strong>{{ flow.name }}</strong><small>{{ flow.description || '未填写流程说明' }}</small></span>
            <span><a-tag :color="flow.status === 'PUBLISHED' ? 'green' : 'orange'">{{ flowStatusLabel(flow.status) }}</a-tag><small>{{ flow.currentVersionId ? '已有运行版本' : '尚未发布' }}</small></span>
          </button>
          <a-empty v-if="!loading && !flows.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="尚未创建流程" />
        </a-spin>
      </section>

      <template v-if="selected">
        <section class="panel-card flow-canvas-panel">
          <div class="flow-toolbar">
            <span><strong>{{ selected.name }}</strong><small>当前正在编辑草稿</small></span>
            <div>
              <a-badge :count="issueCount" :number-style="{ backgroundColor: issueCount ? '#ff4d4f' : '#52c41a' }"><a-button @click="checkPublish">发布检查</a-button></a-badge>
              <a-button v-if="can('DESIGN')" @click="autoLayout">自动布局</a-button>
              <a-button v-if="can('DESIGN')" :loading="busy === 'save'" @click="saveDraft()">保存草稿</a-button>
              <a-button v-if="can('SIMULATE')" :loading="busy === 'simulate'" @click="simulateFlow">模拟路径</a-button>
              <a-button v-if="can('PUBLISH')" type="primary" :disabled="!!issueCount" @click="publishOpen = true">发布版本</a-button>
            </div>
          </div>
          <div v-if="can('DESIGN')" class="flow-palette">
            <button v-for="item in palette" :key="item.type" @click="addNode(item.type)"><strong>{{ item.label }}</strong><small>{{ item.hint }}</small></button>
          </div>
          <FlowGraphCanvas ref="graphCanvas" v-model="nodes" :edges="edges" :issues="localIssues" :editable="can('DESIGN')"
            @update:edges="edges = $event" @select-node="selectCanvasNode" @select-edge="selectCanvasEdge" />
          <div v-if="localIssues.length" class="flow-issue-panel">
            <strong>画布即时检查</strong>
            <div v-for="issue in localIssues" :key="`${issue.code}:${issue.location}`"><a-tag color="red">需处理</a-tag><span>{{ issue.message }}</span></div>
          </div>
          <div v-if="simulation" :class="['flow-simulation', { passed: simulation.successful }]">
            <div><strong>样例模拟{{ simulation.successful ? '通过' : '未通过' }}</strong><span>测试金额：{{ simulation.variables.amount }}</span></div>
            <ol><li v-for="step in simulation.steps" :key="step.nodeKey"><span>{{ simulationStepName(step.nodeKey) }}</span><small>{{ simulationStepType(step.nodeKey, step.nodeType) }}</small></li></ol>
          </div>
        </section>

        <aside class="panel-card flow-inspector">
          <div class="panel-title"><strong>{{ selectedEdge ? '连线规则' : '节点属性' }}</strong><a-tag v-if="selectedNode">{{ nodeTypeLabel(selectedNode.nodeType) }}</a-tag></div>
          <a-form v-if="selectedEdge" layout="vertical">
            <a-form-item label="连接路径"><a-input :value="`${nodes.find(item => item.nodeKey === selectedEdge?.sourceNodeKey)?.name || '上一步'} → ${nodes.find(item => item.nodeKey === selectedEdge?.targetNodeKey)?.name || '下一步'}`" disabled /></a-form-item>
            <a-form-item label="命中条件" extra="普通连线可留空；条件分支支持“变量 == 值”或“变量 != 值”"><a-input :value="selectedEdge.conditionExpression" placeholder="例如 amount == 10000" @focus="graphCanvas?.checkpoint()" @input="updateEdgeCondition(($event.target as HTMLInputElement).value)" /></a-form-item>
            <a-form-item label="判断顺序"><a-input-number :value="selectedEdge.priorityOrder" :min="1" style="width:100%" @focus="graphCanvas?.checkpoint()" @update:value="updateEdgePriority" /></a-form-item>
            <a-button v-if="can('DESIGN')" danger block @click="removeSelectedEdge">删除这条连线</a-button>
          </a-form>
          <a-form v-else-if="selectedNode" layout="vertical">
            <a-form-item label="节点名称"><a-input v-model:value="selectedNode.name" @focus="graphCanvas?.checkpoint()" /></a-form-item>
            <a-form-item v-if="can('DESIGN') && selectedNode.nodeType !== 'END'" label="连接下一步" extra="可从画布端点拖线，也可以在这里选择下一步，便于键盘操作。">
              <a-input-group compact>
                <a-select v-model:value="connectionTargetKey" style="width:calc(100% - 88px)" :options="connectionTargets" placeholder="选择下一步节点" />
                <a-button type="primary" :disabled="!connectionTargetKey" style="width:88px" @click="connectSelectedNode">连接</a-button>
              </a-input-group>
            </a-form-item>
            <template v-if="selectedNode.nodeType === 'APPROVAL'">
              <a-form-item label="审批方式" required>
                <a-segmented v-model:value="approvalMode" :options="[
                  { value: 'OR_SIGN', label: '或签' },
                  { value: 'ALL_SIGN', label: '会签' },
                  { value: 'SEQUENTIAL', label: '顺序会签' },
                ]" block />
              </a-form-item>
              <a-form-item label="审批人来源" required>
                <a-select v-model:value="approverType" :options="[
                  { value: 'PERSON', label: '指定人员' },
                  { value: 'DEPARTMENT', label: '部门成员' },
                  { value: 'DEPARTMENT_MANAGER', label: '部门负责人' },
                  { value: 'RECORD_OWNER', label: '记录负责人' },
                  { value: 'DIRECT_MANAGER', label: '直属上级' },
                  { value: 'INITIATOR_MANAGER', label: '发起人上级' },
                  { value: 'PERSON_FIELD', label: '业务人员字段' },
                  { value: 'STARTER_SELECTED', label: '发起时选择' },
                  { value: 'PREVIOUS_HANDLER', label: '上一处理人' },
                ]" />
              </a-form-item>
              <a-form-item v-if="approverType === 'PERSON'" label="处理人" required extra="人员来自当前系统组织，离职或移出工作空间会在发布时被拦截">
                <PersonSelect v-model="selectedApproverIds" :people="directory.people" value-key="tenantMemberId" multiple placeholder="搜索姓名、部门或岗位" />
              </a-form-item>
              <a-form-item v-if="['DEPARTMENT','DEPARTMENT_MANAGER'].includes(approverType)" label="部门" required>
                <a-select v-model:value="selectedDepartmentIds" mode="multiple" show-search option-filter-prop="label"
                  :options="directory.departments.map(item => ({ value: item.id, label: item.fullName }))" placeholder="选择部门" />
              </a-form-item>
              <a-form-item v-if="approverType === 'PERSON_FIELD'" label="人员字段编码" required extra="运行时从关联业务记录的人员字段解析">
                <a-input v-model:value="personFieldCode" placeholder="例如 owner_assistant" />
              </a-form-item>
              <a-alert v-if="context === 'platform'" type="warning" show-icon message="审批人员规则需要在具体系统中配置" style="margin-bottom:16px" />
            </template>
            <a-form-item v-if="selectedNode.nodeType === 'APPROVAL'" label="字段权限">
              <a-select mode="tags" style="width:100%" :value="(selectedNode.formPolicy?.editable as string[] | undefined) || []" @change="selectedNode!.formPolicy = { editable: $event }" />
            </a-form-item>
            <template v-if="selectedNode.nodeType === 'APPROVAL'">
              <div class="form-grid"><a-form-item label="处理时限（分钟）"><a-input-number :value="selectedNode.timeoutPolicy?.timeoutMinutes as number | undefined" :min="1" style="width:100%" @update:value="setTimeoutPolicy('timeoutMinutes', $event)" /></a-form-item><a-form-item label="超时处理"><a-select :value="String(selectedNode.timeoutPolicy?.action || 'INCIDENT')" :options="[{value:'INCIDENT',label:'转异常人工处理'},{value:'ESCALATE_MANAGER',label:'升级直属上级'},{value:'AUTO_APPROVE',label:'自动同意'},{value:'AUTO_REJECT',label:'自动拒绝'}]" @change="setTimeoutPolicy('action', $event)" /></a-form-item></div>
            </template>
            <a-alert v-if="selectedNode.nodeType === 'GATEWAY'" type="info" show-icon message="请在画布中点击每条出线，分别配置条件和判断顺序。" style="margin-bottom:16px" />
            <a-form-item v-if="selectedNode.nodeType === 'WEBHOOK'" label="服务地址" required><a-input :value="selectedNode.config.url" @input="setConfig('url', ($event.target as HTMLInputElement).value)" /></a-form-item>
            <a-form-item v-if="selectedNode.nodeType === 'WAIT_TIMER'" label="等待时长" required><a-input :value="selectedNode.config.duration" placeholder="PT2H" @input="setConfig('duration', ($event.target as HTMLInputElement).value)" /></a-form-item>
            <template v-if="selectedNode.nodeType === 'NOTIFICATION'">
              <a-form-item label="接收人" required extra="人员来自当前系统组织"><PersonSelect v-model="selectedApproverIds" :people="directory.people" value-key="tenantMemberId" multiple placeholder="搜索姓名、部门或岗位" /></a-form-item>
              <a-form-item label="消息标题"><a-input :value="selectedNode.config.subject" placeholder="支持 ${instanceTitle}" @input="setConfig('subject', ($event.target as HTMLInputElement).value)" /></a-form-item>
              <a-form-item label="消息内容" required extra="支持 ${instanceTitle}、${businessId} 和流程变量"><a-textarea :value="selectedNode.config.content" :rows="4" @input="setConfig('content', ($event.target as HTMLTextAreaElement).value)" /></a-form-item>
            </template>
            <template v-if="selectedNode.nodeType === 'UPDATE_FIELD'">
              <a-form-item label="业务动作" required extra="按模块动作权限校验，不绕过数据范围"><a-input :value="selectedNode.config.businessAction" placeholder="例如 UPDATE 或 APPROVE" @input="setConfig('businessAction', ($event.target as HTMLInputElement).value.toUpperCase())" /></a-form-item>
              <div class="form-grid"><a-form-item label="前置状态"><a-input :value="selectedNode.config.expectedCurrentStatus" placeholder="可选" @input="setConfig('expectedCurrentStatus', ($event.target as HTMLInputElement).value.toUpperCase())" /></a-form-item><a-form-item label="目标状态"><a-input :value="selectedNode.config.targetStatus" placeholder="可选，例如 APPROVED" @input="setConfig('targetStatus', ($event.target as HTMLInputElement).value.toUpperCase())" /></a-form-item></div>
              <a-form-item label="字段回写" required extra="逐项填写业务字段与写入值；值可使用 ${变量名}，执行时仍按发布字段和 Flow 渠道权限校验">
                <div class="flow-writeback-list">
                  <div v-for="([code, value], index) in writebackEntries()" :key="`${code}-${index}`">
                    <a-input :value="code" placeholder="业务字段" @focus="graphCanvas?.checkpoint()" @change="renameWritebackEntry(code, ($event.target as HTMLInputElement).value)" />
                    <a-input :value="String(value ?? '')" placeholder="写入常量或 ${变量名}" @focus="graphCanvas?.checkpoint()" @input="setWritebackValue(code, ($event.target as HTMLInputElement).value)" />
                    <a-button danger @click="removeWritebackEntry(code)">移除</a-button>
                  </div>
                  <a-button block @click="addWritebackEntry">添加字段回写</a-button>
                </div>
              </a-form-item>
            </template>
            <template v-if="selectedNode.nodeType === 'AI'">
              <a-form-item label="受控模型" required><a-input :value="selectedNode.config.model" @input="setConfig('model', ($event.target as HTMLInputElement).value)" /></a-form-item>
              <a-form-item label="提示模板" required><a-textarea :value="selectedNode.config.prompt" @input="setConfig('prompt', ($event.target as HTMLTextAreaElement).value)" /></a-form-item>
            </template>
            <template v-if="['WEBHOOK','AI','UPDATE_FIELD','NOTIFICATION'].includes(selectedNode.nodeType)">
              <a-divider>失败补偿</a-divider>
              <a-form-item label="异常策略"><a-select :value="String(selectedNode.exceptionPolicy?.action || 'MANUAL')" :options="[{value:'MANUAL',label:'进入异常中心人工处理'},{value:'RETRY',label:'自动重试后人工处理'}]" @change="setExceptionPolicy('action', $event)" /></a-form-item>
              <div v-if="selectedNode.exceptionPolicy?.action === 'RETRY'" class="form-grid"><a-form-item label="最多尝试"><a-input-number :value="Number(selectedNode.exceptionPolicy?.maxAttempts || 3)" :min="1" :max="10" @update:value="setExceptionPolicy('maxAttempts', $event)" /></a-form-item><a-form-item label="重试间隔（分钟）"><a-input-number :value="Number(selectedNode.exceptionPolicy?.retryDelayMinutes || 5)" :min="1" @update:value="setExceptionPolicy('retryDelayMinutes', $event)" /></a-form-item></div>
            </template>
            <a-button v-if="can('DESIGN') && !['START','END'].includes(selectedNode.nodeType)" danger block @click="removeNode(selectedNode.nodeKey)">移除节点</a-button>
          </a-form>
          <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="选择画布节点查看专属属性" />
        </aside>
      </template>
    </div>

    <section v-if="activeSection === 'designer' && selected?.versions.length" class="panel-card flow-version-panel">
      <div class="panel-title"><strong>发布版本</strong><span>{{ selected.currentVersionId ? '已有生效版本' : '尚未发布' }}</span></div>
      <div v-for="version in selected.versions" :key="version.id" class="flow-version-row">
        <span><strong>{{ versionLabel(version.versionNumber) }} · {{ version.changeSummary }}</strong><small>{{ productDateTime(version.publishedAt) }}发布</small></span>
        <span>{{ version.changeSummary || '流程版本' }}</span><a-tag :color="selected.currentVersionId === version.id ? 'green' : 'default'">{{ selected.currentVersionId === version.id ? '当前运行版本' : '历史版本' }}</a-tag>
      </div>
    </section>
  </div>

  <a-modal v-model:open="createOpen" title="新建流程" :confirm-loading="busy === 'create'" @ok="createFlow">
    <a-form layout="vertical"><a-form-item label="名称" required><a-input v-model:value="createForm.name" placeholder="例如：合同审批" /></a-form-item><a-form-item label="说明"><a-textarea v-model:value="createForm.description" /></a-form-item></a-form>
  </a-modal>
  <a-modal v-model:open="publishOpen" title="发布流程版本" :confirm-loading="busy === 'publish'" :ok-button-props="{ disabled: !publishForm.changeSummary.trim() }" @ok="publishFlow">
    <a-alert type="info" show-icon message="发布后，新实例固定使用该版本快照；后续草稿不会修改已有实例。" />
    <a-form layout="vertical" style="margin-top:16px"><a-form-item label="变更摘要" required><a-input v-model:value="publishForm.changeSummary" placeholder="例如：首版合同审批" /></a-form-item><a-form-item label="模拟金额"><a-input-number v-model:value="sampleAmount" :min="0" style="width:100%" /></a-form-item></a-form>
  </a-modal>
</template>
