<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { ApiError, api } from '../api'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { localFlowIssues, sequentialEdges } from '../flow-designer'
import FlowBindingsView from './FlowBindingsView.vue'
import FlowRuntimeView from './FlowRuntimeView.vue'
import type {
  FlowDefinitionView, FlowEdgeInput, FlowIssue, FlowNodeInput, FlowNodeType,
  FlowPublicationCheck, FlowPublicationResult, FlowSimulationResult,
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
const publicationCheck = ref<FlowPublicationCheck>()
const simulation = ref<FlowSimulationResult>()
const loading = ref(false)
const busy = ref('')
const createOpen = ref(false)
const publishOpen = ref(false)
const sampleAmount = ref(8000)
const createForm = reactive({ code: '', name: '', description: '' })
const publishForm = reactive({ changeSummary: '' })
let nodeSequence = 0

const palette: { type: FlowNodeType; label: string; hint: string }[] = [
  { type: 'START', label: '开始', hint: '唯一入口' },
  { type: 'GATEWAY', label: '条件', hint: '按样例变量选路' },
  { type: 'APPROVAL', label: '审批', hint: '审批人和字段权限' },
  { type: 'WEBHOOK', label: '服务', hint: '受控 Webhook 调用' },
  { type: 'NOTIFICATION', label: '通知', hint: '发送业务通知' },
  { type: 'WAIT_TIMER', label: '等待', hint: '定时恢复' },
  { type: 'AI', label: 'AI', hint: '受控模型节点' },
  { type: 'END', label: '结束', hint: '流程终点' },
]

const localIssues = computed(() => localFlowIssues(nodes.value, edges.value))
const selectedNode = computed(() => nodes.value.find(node => node.nodeKey === selectedNodeKey.value))
const issueCount = computed(() => localIssues.value.length)

function can(action: string) {
  return allowsPermission(current.value?.permissions, 'FLOW', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'FLOW', '*', action)
}

function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
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

async function selectFlow(id: number) {
  if (!token.value) return
  try {
    const detail = await api<FlowDefinitionView>(`/api/flows/${id}`, {}, token.value)
    selected.value = detail
    nodes.value = detail.nodes.map(({ id: _id, version: _version, ...node }) => ({ ...node, config: node.config || {} }))
    edges.value = detail.edges.map(({ id: _id, version: _version, ...edge }) => ({ ...edge, config: edge.config || {} }))
    selectedNodeKey.value = nodes.value[0]?.nodeKey
    publicationCheck.value = undefined
    simulation.value = undefined
  } catch (error) {
    message.error(describeError(error))
  }
}

async function createFlow() {
  if (!token.value || !createForm.code.trim() || !createForm.name.trim()) return
  busy.value = 'create'
  try {
    const flow = await api<FlowDefinitionView>('/api/flows', {
      method: 'POST', body: JSON.stringify({
        code: createForm.code, name: createForm.name, description: createForm.description || undefined,
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
  nodeSequence += 1
  const key = `${type.toLowerCase()}_${Date.now().toString(36)}_${nodeSequence}`
  const input: FlowNodeInput = {
    nodeKey: key, nodeType: type, name: palette.find(item => item.type === type)?.label || type,
    positionX: 80 + nodes.value.length * 180, positionY: 80, config: {},
  }
  if (type === 'GATEWAY') input.name = '金额条件'
  if (type === 'APPROVAL') input.name = '负责人审批'
  if (type === 'WEBHOOK') input.name = '调用业务服务'
  if (type === 'NOTIFICATION') input.name = '结果通知'
  const endIndex = nodes.value.findIndex(node => node.nodeType === 'END')
  if (type !== 'END' && endIndex >= 0) nodes.value.splice(endIndex, 0, input)
  else nodes.value.push(input)
  rebuildEdges()
  selectedNodeKey.value = key
}

function removeNode(key: string) {
  nodes.value = nodes.value.filter(node => node.nodeKey !== key)
  rebuildEdges()
  selectedNodeKey.value = nodes.value[0]?.nodeKey
}

function rebuildEdges() {
  const existing = new Map(edges.value.map(edge => [`${edge.sourceNodeKey}:${edge.targetNodeKey}`, edge]))
  edges.value = sequentialEdges(nodes.value).map(edge => existing.get(`${edge.sourceNodeKey}:${edge.targetNodeKey}`) || edge)
  publicationCheck.value = undefined
  simulation.value = undefined
}

function setApprovers(raw: string) {
  if (!selectedNode.value) return
  const ids = raw.split(',').map(value => Number(value.trim())).filter(value => Number.isInteger(value) && value > 0)
  selectedNode.value.assigneePolicy = ids.length ? { type: 'ACCOUNT', accountIds: ids } : undefined
}

function approvers(node: FlowNodeInput | undefined) {
  return ((node?.assigneePolicy?.accountIds as number[] | undefined) || []).join(',')
}

function setConfig(key: string, value: unknown) {
  if (!selectedNode.value) return
  selectedNode.value.config = { ...selectedNode.value.config, [key]: value }
}

function outgoingEdge(nodeKey?: string) {
  return edges.value.find(edge => edge.sourceNodeKey === nodeKey)
}

function setCondition(value: string) {
  const edge = outgoingEdge(selectedNode.value?.nodeKey)
  if (edge) edge.conditionExpression = value
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
    if (!silent) message.success(`草稿修订 ${detail.draftRevision} 已保存`)
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
    message.success(`版本 V${result.versionNumber} 已发布，运行实例将固定使用该快照`)
    await loadFlows(selected.value.id)
  } catch (error) {
    message.error(describeError(error))
  } finally {
    busy.value = ''
  }
}

onMounted(() => loadFlows())
</script>

<template>
  <div class="flow-designer-page">
    <div class="page-heading compact-heading">
      <div><p class="eyebrow">{{ context === 'platform' ? '平台' : '系统后台' }} · 流程</p><h1>{{ activeSection === 'bindings' ? '流程触发绑定' : activeSection === 'runtime' ? '流程运行实例' : '流程设计与发布' }}</h1><p>{{ activeSection === 'bindings' ? '把系统内已发布模块的业务事件连接到已发布流程；工作空间覆盖优先于系统默认。' : activeSection === 'runtime' ? '从主入口发起并处理流程，查看固定版本、当前处理人、处理理由和下一步。' : '草稿、模拟和发布分别授权；发布后生成不可变版本，后续草稿不会改写运行记录。' }}</p></div>
      <a-button v-if="activeSection === 'designer' && can('DESIGN')" type="primary" @click="createOpen = true">新建流程</a-button>
    </div>

    <a-segmented v-model:value="activeSection" class="flow-config-tabs" :options="context === 'system' ? [{ value: 'designer', label: '设计与发布' }, { value: 'bindings', label: '触发绑定' }, { value: 'runtime', label: '运行实例' }] : [{ value: 'designer', label: '设计与发布' }, { value: 'runtime', label: '运行实例' }]" />
    <FlowBindingsView v-if="context === 'system' && activeSection === 'bindings'" />
    <FlowRuntimeView v-if="activeSection === 'runtime'" :context="context" />
    <a-alert v-if="activeSection === 'designer' && !can('VIEW')" type="warning" show-icon message="当前工作范围没有流程查看权限" />
    <div v-if="activeSection === 'designer' && can('VIEW')" class="flow-designer-layout">
      <section class="panel-card flow-list-panel">
        <div class="panel-title"><strong>流程列表</strong><a-tag>{{ context === 'platform' ? '平台' : '当前系统' }}</a-tag></div>
        <a-spin :spinning="loading">
          <button v-for="flow in flows" :key="flow.id" :class="['flow-list-item', { active: selected?.id === flow.id }]" @click="selectFlow(flow.id)">
            <span><strong>{{ flow.name }}</strong><small>{{ flow.code }}</small></span>
            <span><a-tag :color="flow.status === 'PUBLISHED' ? 'green' : 'orange'">{{ flow.status }}</a-tag><small>草稿 r{{ flow.draftRevision }}</small></span>
          </button>
          <a-empty v-if="!loading && !flows.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="尚未创建流程" />
        </a-spin>
      </section>

      <template v-if="selected">
        <section class="panel-card flow-canvas-panel">
          <div class="flow-toolbar">
            <span><strong>{{ selected.name }}</strong><small>草稿第 {{ selected.draftRevision }} 次修订</small></span>
            <div>
              <a-badge :count="issueCount" :number-style="{ backgroundColor: issueCount ? '#ff4d4f' : '#52c41a' }"><a-button @click="checkPublish">发布检查</a-button></a-badge>
              <a-button v-if="can('DESIGN')" :loading="busy === 'save'" @click="saveDraft()">保存草稿</a-button>
              <a-button v-if="can('SIMULATE')" :loading="busy === 'simulate'" @click="simulateFlow">模拟路径</a-button>
              <a-button v-if="can('PUBLISH')" type="primary" :disabled="!!issueCount" @click="publishOpen = true">发布版本</a-button>
            </div>
          </div>
          <div v-if="can('DESIGN')" class="flow-palette">
            <button v-for="item in palette" :key="item.type" @click="addNode(item.type)"><strong>{{ item.label }}</strong><small>{{ item.hint }}</small></button>
          </div>
          <div class="flow-canvas" data-testid="flow-canvas">
            <template v-for="(node, index) in nodes" :key="node.nodeKey">
              <button :class="['flow-node', `flow-node--${node.nodeType.toLowerCase()}`, { active: selectedNodeKey === node.nodeKey, invalid: nodeIssues(node.nodeKey).length }]" @click="selectedNodeKey = node.nodeKey">
                <span>{{ palette.find(item => item.type === node.nodeType)?.label || node.nodeType }}</span>
                <strong>{{ node.name }}</strong>
                <small>{{ node.nodeKey }}</small>
                <em v-if="nodeIssues(node.nodeKey).length">{{ nodeIssues(node.nodeKey).length }} 项待处理</em>
              </button>
              <div v-if="index < nodes.length - 1" class="flow-connector"><span>↓</span><small>{{ outgoingEdge(node.nodeKey)?.conditionExpression || '继续' }}</small></div>
            </template>
            <a-empty v-if="!nodes.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="从上方节点库添加开始节点，按业务顺序完成画布" />
          </div>
          <div v-if="localIssues.length" class="flow-issue-panel">
            <strong>画布即时检查</strong>
            <div v-for="issue in localIssues" :key="`${issue.code}:${issue.location}`"><a-tag color="red">{{ issue.code }}</a-tag><span>{{ issue.message }}</span><small>{{ issue.location }}</small></div>
          </div>
          <div v-if="simulation" :class="['flow-simulation', { passed: simulation.successful }]">
            <div><strong>样例模拟{{ simulation.successful ? '通过' : '未通过' }}</strong><span>amount = {{ simulation.variables.amount }}</span></div>
            <ol><li v-for="step in simulation.steps" :key="step.nodeKey"><span>{{ step.nodeKey }}</span><small>{{ step.nodeType }}</small></li></ol>
          </div>
        </section>

        <aside class="panel-card flow-inspector">
          <div class="panel-title"><strong>节点属性</strong><a-tag v-if="selectedNode">{{ selectedNode.nodeType }}</a-tag></div>
          <a-form v-if="selectedNode" layout="vertical">
            <a-form-item label="节点名称"><a-input v-model:value="selectedNode.name" /></a-form-item>
            <a-form-item label="稳定标识"><a-input :value="selectedNode.nodeKey" disabled /></a-form-item>
            <a-form-item v-if="selectedNode.nodeType === 'APPROVAL'" label="审批人账号 ID" required extra="多个账号用逗号分隔；留空会阻止发布">
              <a-input :value="approvers(selectedNode)" placeholder="例如 1,2" @input="setApprovers(($event.target as HTMLInputElement).value)" />
            </a-form-item>
            <a-form-item v-if="selectedNode.nodeType === 'APPROVAL'" label="字段权限">
              <a-select mode="tags" style="width:100%" :value="(selectedNode.formPolicy?.editable as string[] | undefined) || []" @change="selectedNode!.formPolicy = { editable: $event }" />
            </a-form-item>
            <a-form-item v-if="selectedNode.nodeType === 'GATEWAY'" label="命中条件" required extra="支持变量 == 值、变量 != 值">
              <a-input :value="outgoingEdge(selectedNode.nodeKey)?.conditionExpression" placeholder="amount != 0" @input="setCondition(($event.target as HTMLInputElement).value)" />
            </a-form-item>
            <a-form-item v-if="selectedNode.nodeType === 'WEBHOOK'" label="服务地址" required><a-input :value="selectedNode.config.url" @input="setConfig('url', ($event.target as HTMLInputElement).value)" /></a-form-item>
            <a-form-item v-if="selectedNode.nodeType === 'WAIT_TIMER'" label="等待时长" required><a-input :value="selectedNode.config.duration" placeholder="PT2H" @input="setConfig('duration', ($event.target as HTMLInputElement).value)" /></a-form-item>
            <template v-if="selectedNode.nodeType === 'AI'">
              <a-form-item label="受控模型" required><a-input :value="selectedNode.config.model" @input="setConfig('model', ($event.target as HTMLInputElement).value)" /></a-form-item>
              <a-form-item label="提示模板" required><a-textarea :value="selectedNode.config.prompt" @input="setConfig('prompt', ($event.target as HTMLTextAreaElement).value)" /></a-form-item>
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
        <span><strong>V{{ version.versionNumber }} · {{ version.changeSummary }}</strong><small>基于草稿 r{{ version.draftRevision }} · {{ version.publishedAt }}</small></span>
        <span>{{ version.changeSummary || '流程版本' }}</span><a-tag :color="selected.currentVersionId === version.id ? 'green' : 'default'">{{ selected.currentVersionId === version.id ? '当前运行版本' : '历史版本' }}</a-tag>
      </div>
    </section>
  </div>

  <a-modal v-model:open="createOpen" title="新建流程" :confirm-loading="busy === 'create'" @ok="createFlow">
    <a-form layout="vertical"><a-form-item label="名称" required><a-input v-model:value="createForm.name" placeholder="例如：合同审批" /></a-form-item><a-form-item label="稳定编码" required extra="小写字母开头，可使用数字、下划线和横线"><a-input v-model:value="createForm.code" placeholder="contract_approval" /></a-form-item><a-form-item label="说明"><a-textarea v-model:value="createForm.description" /></a-form-item></a-form>
  </a-modal>
  <a-modal v-model:open="publishOpen" title="发布流程版本" :confirm-loading="busy === 'publish'" :ok-button-props="{ disabled: !publishForm.changeSummary.trim() }" @ok="publishFlow">
    <a-alert type="info" show-icon message="发布后，新实例固定使用该版本快照；后续草稿不会修改已有实例。" />
    <a-form layout="vertical" style="margin-top:16px"><a-form-item label="变更摘要" required><a-input v-model:value="publishForm.changeSummary" placeholder="例如：首版合同审批" /></a-form-item><a-form-item label="模拟金额"><a-input-number v-model:value="sampleAmount" :min="0" style="width:100%" /></a-form-item></a-form>
  </a-modal>
</template>
