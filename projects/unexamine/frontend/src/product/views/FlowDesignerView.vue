<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
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
import ConditionGroupEditor from '../components/ConditionGroupEditor.vue'
import ConditionValueInput from '../components/ConditionValueInput.vue'
import FlowNodeFieldPolicyEditor, { nodeFieldPolicyIssues } from '../components/FlowNodeFieldPolicyEditor.vue'
import { bindingCondition, conditionFieldCodes, conditionIssues, emptyCondition, isConditionGroup,
  type ConditionField, type ConditionGroup, type ConditionNode } from '../conditions'
import type {
  FlowDefinitionView, FlowEdgeInput, FlowIssue, FlowNodeInput, FlowNodeType, FlowNodeFormField,
  FlowPublicationCheck, FlowPublicationResult, FlowSimulationResult, FlowParticipantDirectory,
  RuntimeModuleCatalogItem, RuntimeRecordList, RuntimeRecord, FlowCallbackOption,
} from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system' }>(), { context: 'system' })
const route = useRoute()
const activeSection = ref<'designer' | 'bindings' | 'runtime'>(props.context === 'system' && route.query.flowSection === 'bindings' ? 'bindings' : 'designer')
watch(() => route.query.flowSection, section => { if (props.context === 'system' && section === 'bindings') activeSection.value = 'bindings' })
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
const directory = ref<FlowParticipantDirectory>({ departments: [], people: [], permissionVersion: 0 })
const loading = ref(false)
const busy = ref('')
const pageError = ref('')
const createOpen = ref(false)
const publishOpen = ref(false)
const modules = ref<RuntimeModuleCatalogItem[]>([])
const conditionFields = ref<ConditionField[]>([])
const fieldsLoading = ref(false)
const fieldsReady = ref(false)
const conditionError = ref('')
const nodeFormFields = ref<FlowNodeFormField[]>([])
const nodeFieldsReady = ref(false)
const nodeFieldsLoading = ref(false)
const nodeFieldsError = ref('')
const automaticFields = ref<FlowNodeFormField[]>([])
const automaticConditionFields = ref<ConditionField[]>([])
const automaticFieldsLoading = ref(false)
const automaticFieldsError = ref('')
const callbackCatalog = ref<FlowCallbackOption[]>([])
const callbackCatalogLoading = ref(false)
const callbackCatalogError = ref('')
const sampleError = ref('')
const sampleRecords = ref<RuntimeRecord[]>([])
const sampleRecordId = ref<number>()
const sampleValues = reactive<Record<string, unknown>>({})
const samplesLoading = ref(false)
let active = true
let generation = 0
let flowGeneration = 0
let listGeneration = 0
let fieldGeneration = 0
let nodeFieldGeneration = 0
let automaticFieldGeneration = 0
let sampleGeneration = 0
let catalogReady = false
const selectingFlowId = ref<number>()
const identity = computed(() => JSON.stringify([props.context, current.value?.platformId, current.value?.accountId,
  current.value?.systemId, current.value?.tenantId, current.value?.memberId, current.value?.tenantMemberId,
  current.value?.contextRevision, current.value?.roleIds, current.value?.permissions, current.value?.dataScopes,
  current.value?.tenantSwitchContext]))
const businessModuleId = computed(() => {
  const id = nodes.value.find(node => node.nodeType === 'START')?.config.moduleId
  return typeof id === 'number' && Number.isSafeInteger(id) && id > 0 ? id : undefined
})
const businessModule = computed(() => modules.value.find(module => module.moduleId === businessModuleId.value))
const moduleOptions = computed(() => modules.value.map(module => ({ value: module.moduleId, label: module.moduleName })))
const referencedConditionFields = computed(() => {
  const repeatCodes = nodes.value.flatMap(node => {
    const policy = repeatPolicy(node), condition = policy.condition
    return policy.enabled === true && supportedCondition(condition) && isConditionGroup(condition) ? conditionFieldCodes(condition) : []
  })
  const codes = new Set([...edges.value.flatMap(edge => conditionFieldCodes(edgeCondition(edge))), ...repeatCodes])
  return conditionFields.value.filter(field => codes.has(field.code))
})
const simulationFields = computed(() => referencedConditionFields.value)
const callbackOptions = computed(() => callbackCatalog.value.map(item => ({
  value: `${item.applicationId}:${item.callbackId}`,
  label: `${item.applicationName} · 业务事件回调 #${item.callbackId} · V${item.applicationVersionNumber}`,
})))
const selectedCallbackKey = computed(() => {
  const applicationId = Number(selectedNode.value?.config.applicationId || 0)
  const callbackId = Number(selectedNode.value?.config.callbackId || 0)
  return applicationId > 0 && callbackId > 0 ? `${applicationId}:${callbackId}` : undefined
})
const selectedCallback = computed(() => callbackCatalog.value.find(item =>
  `${item.applicationId}:${item.callbackId}` === selectedCallbackKey.value))
const connectionTargetKey = ref<string>()
const createForm = reactive({ code: '', name: '', description: '' })
const publishForm = reactive({ changeSummary: '' })
let nodeSequence = 0

type GatewayMode = 'EXCLUSIVE' | 'PARALLEL' | 'INCLUSIVE' | 'MERGE'
const palette: { key: string; type: FlowNodeType; label: string; hint: string; gatewayMode?: GatewayMode }[] = [
  { key: 'start', type: 'START', label: '开始', hint: '唯一入口' },
  { key: 'exclusive', type: 'GATEWAY', gatewayMode: 'EXCLUSIVE', label: '条件分支', hint: '按条件只走一路' },
  { key: 'parallel', type: 'GATEWAY', gatewayMode: 'PARALLEL', label: '并行网关', hint: '全部分支同时执行' },
  { key: 'inclusive', type: 'GATEWAY', gatewayMode: 'INCLUSIVE', label: '包容网关', hint: '执行所有命中分支' },
  { key: 'merge', type: 'GATEWAY', gatewayMode: 'MERGE', label: '汇合网关', hint: '等待配对分支到齐' },
  { key: 'approval', type: 'APPROVAL', label: '审批', hint: '审批人和字段权限' },
  { key: 'form', type: 'FORM_TASK', label: '填写表单', hint: '填写人和节点字段权限' },
  { key: 'subflow', type: 'SUBFLOW', label: '子流程', hint: '等待另一条流程完成' },
  { key: 'update', type: 'UPDATE_FIELD', label: '字段更新', hint: '更新当前业务记录' },
  { key: 'data', type: 'DATA_ACTION', label: '新建/更新数据', hint: '操作有权访问的模块数据' },
  { key: 'webhook', type: 'WEBHOOK', label: '服务', hint: '受控 Webhook 调用' },
  { key: 'cc', type: 'CC', label: '抄送', hint: '同步流程进展给相关人员' },
  { key: 'notification', type: 'NOTIFICATION', label: '通知', hint: '提醒人员关注流程状态' },
  { key: 'message', type: 'MESSAGE', label: '消息', hint: '发送一条流程业务消息' },
  { key: 'wait', type: 'WAIT', label: '等待', hint: '按分钟、小时或天后继续' },
  { key: 'timer', type: 'TIMER', label: '定时器', hint: '到指定日期时间继续' },
  { key: 'ai', type: 'AI', label: 'AI', hint: '受控模型节点' },
  { key: 'end', type: 'END', label: '结束', hint: '流程终点' },
]
const defaultManualActionCodes = ['APPROVE', 'REJECT', 'RETURN', 'TRANSFER']
const manualActionCodes = [...defaultManualActionCodes, 'DELEGATE', 'ADD_SIGN', 'REMOVE_SIGN']
const forcedOpinionActions = ['REJECT', 'RETURN', 'DELEGATE']

function defaultManualOperationPolicy() {
  // 新增可配置动作不扩大旧缺省和新节点的默认处理权限。
  return { allowedActions: [...defaultManualActionCodes], opinionRequiredActions: [...forcedOpinionActions], requireClaim: false }
}
function manualOperationPolicy(node: FlowNodeInput | undefined): Record<string, unknown> {
  const configured = node?.config.manualOperationPolicy
  if (configured === undefined) return defaultManualOperationPolicy()
  return configured && typeof configured === 'object' && !Array.isArray(configured) ? configured as Record<string, unknown> : {}
}
function manualPolicyActions(key: 'allowedActions' | 'opinionRequiredActions') {
  const actions = manualOperationPolicy(selectedNode.value)[key]
  return Array.isArray(actions) ? actions.filter((action): action is string => typeof action === 'string') : []
}
function manualOperationIssues(node: FlowNodeInput) {
  if (!Object.prototype.hasOwnProperty.call(node.config, 'manualOperationPolicy')) return []
  if (!['APPROVAL', 'FORM_TASK'].includes(node.nodeType)) return ['人工操作仅适用于审批和填写节点。']
  const policy = manualOperationPolicy(node), allowed = policy.allowedActions, required = policy.opinionRequiredActions
  const validActions = (value: unknown): value is string[] => Array.isArray(value)
    && value.every(action => typeof action === 'string' && manualActionCodes.includes(action))
    && new Set(value).size === value.length
  const keys = Object.keys(policy), hasClaimRule = Object.prototype.hasOwnProperty.call(policy, 'requireClaim')
  if (![2, 3].includes(keys.length) || keys.some(key => !['allowedActions', 'opinionRequiredActions', 'requireClaim'].includes(key))
    || !validActions(allowed) || !validActions(required) || (hasClaimRule && typeof policy.requireClaim !== 'boolean')) {
    return ['原人工操作配置无效，已保留；请明确设置允许操作、必填意见及领取规则后再发布。']
  }
  if (!allowed.length) return ['至少选择一种允许操作。']
  if (required.some(action => !allowed.includes(action))) return ['必填意见只能配置在已允许的操作上。']
  if (forcedOpinionActions.some(action => allowed.includes(action) && !required.includes(action))) return ['拒绝、退回和委托必须填写意见，不能取消。']
  if (policy.requireClaim === true && (node.config.approvalMode ?? 'OR_SIGN') !== 'OR_SIGN') {
    return ['处理前必须领取仅适用于或签；请关闭领取要求或切回或签后再发布，原配置不会自动清除。']
  }
  return []
}
function allSignCompletionIssues(node: FlowNodeInput) {
  if (!Object.prototype.hasOwnProperty.call(node.config, 'allSignCompletionPolicy')) return []
  const policy = node.config.allSignCompletionPolicy
  if (node.nodeType !== 'APPROVAL' || node.config.approvalMode !== 'ALL_SIGN'
    || !policy || typeof policy !== 'object' || Array.isArray(policy)) return ['会签完成条件仅适用于审批会签。']
  const value = policy as Record<string, unknown>, keys = Object.keys(value)
  if (value.mode === 'ALL' && keys.length === 1) return []
  if (value.mode === 'RATIO' && keys.length === 2 && keys.includes('ratioPercent')
    && Number.isInteger(value.ratioPercent) && Number(value.ratioPercent) >= 1 && Number(value.ratioPercent) <= 100) return []
  return ['请选择全部同意，或设置1至100的整数通过比例。']
}

function attachmentPolicyIssues(node: FlowNodeInput) {
  if (!Object.prototype.hasOwnProperty.call(node.config, 'attachmentPolicy')) return []
  const policy = node.config.attachmentPolicy
  if (!['APPROVAL', 'FORM_TASK'].includes(node.nodeType) || !policy || typeof policy !== 'object' || Array.isArray(policy)) {
    return ['附件必填只能配置在审批或填写节点。']
  }
  const value = policy as Record<string, unknown>
  return Object.keys(value).length === 2 && value.required === true && value.minimumCount === 1
    ? [] : ['请使用附件必填开关重新配置该节点。']
}

const localIssues = computed(() => [...localFlowIssues(nodes.value, edges.value.map(edge => {
  const { conditionExpression: _legacy, ...rest } = edge
  return rest
})), ...edges.value.flatMap(edge => edgeIssues(edge).map(text => ({
  code: 'BUSINESS_CONDITION_INVALID', location: `edge:${edge.edgeKey}`, message: text,
}))), ...nodes.value.flatMap(node => repeatApprovalIssues(node).map(text => ({
  code: 'FLOW_REPEAT_APPROVAL_INVALID', location: `node:${node.nodeKey}`, message: `${node.name}：${text}`,
}))), ...nodes.value.flatMap(node => nodePolicyIssues(node).map(text => ({
  code: 'NODE_FIELD_POLICY_INVALID', location: `node:${node.nodeKey}`, message: `${node.name}：${text}`,
}))), ...nodes.value.flatMap(node => manualOperationIssues(node).map(text => ({
  code: 'FLOW_MANUAL_OPERATION_INVALID', location: `node:${node.nodeKey}`, message: `${node.name}：${text}`,
}))), ...nodes.value.flatMap(node => allSignCompletionIssues(node).map(text => ({
  code: 'FLOW_ALL_SIGN_COMPLETION_INVALID', location: `node:${node.nodeKey}`, message: `${node.name}：${text}`,
}))), ...nodes.value.flatMap(node => attachmentPolicyIssues(node).map(text => ({
  code: 'FLOW_APPROVAL_ATTACHMENT_POLICY_INVALID', location: `node:${node.nodeKey}`, message: `${node.name}：${text}`,
}))), ...nodes.value.flatMap(node => returnPolicyIssues(node).map(text => ({
  code: 'FLOW_RETURN_POLICY_INVALID', location: `node:${node.nodeKey}`, message: `${node.name}：${text}`,
}))), ...nodes.value.flatMap(node => subflowIssues(node).map(text => ({
  code: 'FLOW_SUBFLOW_CONFIG_INVALID', location: `node:${node.nodeKey}`, message: `${node.name}：${text}` ,
})))])
const selectedNode = computed(() => nodes.value.find(node => node.nodeKey === selectedNodeKey.value))
type AutomaticValue = { source: 'FIXED' | 'VARIABLE'; value?: unknown; variable?: string }
type AutomaticAssignment = { fieldCode: string; value: AutomaticValue }
function automaticAssignments(node = selectedNode.value): AutomaticAssignment[] {
  if (!node || !['UPDATE_FIELD', 'DATA_ACTION'].includes(node.nodeType)) return []
  if (Array.isArray(node.config.assignments)) return node.config.assignments.map(item => {
    const row = item as Record<string, unknown>, value = (row.value || {}) as Record<string, unknown>
    return { fieldCode: String(row.fieldCode || ''), value: {
      source: value.source === 'VARIABLE' ? 'VARIABLE' : 'FIXED', value: value.value,
      variable: typeof value.variable === 'string' ? value.variable : undefined,
    } }
  })
  const legacy = (node.config.fieldUpdates || {}) as Record<string, unknown>
  return Object.entries(legacy).map(([fieldCode, raw]) => {
    const matched = typeof raw === 'string' ? /^\$\{([A-Za-z_][A-Za-z0-9_.-]{0,99})}$/.exec(raw) : undefined
    return { fieldCode, value: matched ? { source: 'VARIABLE', variable: matched[1] }
      : { source: 'FIXED', value: raw } }
  })
}
const automaticOperation = computed<'CREATE' | 'UPDATE'>(() => selectedNode.value?.nodeType === 'UPDATE_FIELD'
  ? 'UPDATE' : String(selectedNode.value?.config.operation || 'CREATE') === 'UPDATE' ? 'UPDATE' : 'CREATE')
const automaticModuleId = computed(() => selectedNode.value?.nodeType === 'UPDATE_FIELD'
  ? businessModuleId.value : Number(selectedNode.value?.config.moduleId) || undefined)
const automaticModule = computed(() => modules.value.find(module => module.moduleId === automaticModuleId.value))
const automaticModuleOptions = computed(() => modules.value.filter(module => allowsPermission(current.value?.permissions,
  'MODULE', module.moduleCode, automaticOperation.value)).map(module => ({ value: module.moduleId, label: module.moduleName })))
const automaticVariableOptions = computed(() => nodeFormFields.value.map(field => ({ value: field.code, label: field.name })))
const automaticFieldOptions = computed(() => automaticFields.value.filter(field => field.editable)
  .map(field => ({ value: field.code, label: `${field.name}��${field.fieldType}��` })))
const automaticReferenceOptions = computed(() => nodeFormFields.value
  .filter(field => field.referenceModuleId === automaticModuleId.value)
  .map(field => ({ value: field.code, label: field.name })))
function reachesNode(sourceNodeKey: string, targetNodeKey: string) {
  const pending = [sourceNodeKey], visited = new Set<string>()
  while (pending.length) {
    const current = pending.shift()!
    if (visited.has(current)) continue
    visited.add(current)
    if (current === targetNodeKey) return true
    edges.value.filter(edge => edge.sourceNodeKey === current).forEach(edge => pending.push(edge.targetNodeKey))
  }
  return false
}
const automaticPriorNodeOptions = computed(() => nodes.value.filter(node => node.nodeType === 'DATA_ACTION'
  && node.nodeKey !== selectedNode.value?.nodeKey && Number(node.config.moduleId) === automaticModuleId.value)
  .filter(node => Boolean(selectedNode.value) && reachesNode(node.nodeKey, selectedNode.value!.nodeKey))
  .map(node => ({ value: node.nodeKey, label: node.name })))
function automaticConditionField(code: string): ConditionField {
  const option = automaticConditionFields.value.find(field => field.code === code)
  if (option) return option
  const field = automaticFields.value.find(item => item.code === code)
  return { code, name: field?.name || code, fieldType: field?.fieldType || 'TEXT', configJson: '{}' }
}
const selectedGatewayMode = computed<GatewayMode>(() => {
  const value = String(selectedNode.value?.config.gatewayMode || 'EXCLUSIVE').toUpperCase()
  return ['EXCLUSIVE', 'PARALLEL', 'INCLUSIVE', 'MERGE'].includes(value) ? value as GatewayMode : 'EXCLUSIVE'
})
const gatewayPairOptions = computed(() => {
  if (!selectedNode.value || selectedNode.value.nodeType !== 'GATEWAY') return []
  const targetMode = selectedGatewayMode.value === 'MERGE' ? ['PARALLEL', 'INCLUSIVE'] : ['MERGE']
  return nodes.value.filter(node => node.nodeType === 'GATEWAY' && node.nodeKey !== selectedNode.value?.nodeKey
    && targetMode.includes(String(node.config.gatewayMode || 'EXCLUSIVE').toUpperCase()))
    .map(node => ({ value: node.nodeKey, label: node.name }))
})
const selectedGatewayPair = computed(() => String(selectedNode.value?.config[
  selectedGatewayMode.value === 'MERGE' ? 'splitNodeKey' : 'mergeNodeKey'] || ''))

type SubflowMappingKey = 'inputMappings' | 'outputMappings'
type SubflowMapping = { sourceKey: string; targetKey: string }
function subflowMappings(node: FlowNodeInput | undefined, key: SubflowMappingKey): SubflowMapping[] {
  const value = node?.config[key]
  if (!Array.isArray(value)) return []
  return value.filter(item => item && typeof item === 'object' && !Array.isArray(item))
    .map(item => ({ sourceKey: String((item as Record<string, unknown>).sourceKey || ''),
      targetKey: String((item as Record<string, unknown>).targetKey || '') }))
}
function subflowIssues(node: FlowNodeInput) {
  if (node.nodeType !== 'SUBFLOW') return []
  const targetId = Number(node.config.flowId)
  const target = flows.value.find(flow => flow.id === targetId)
  const messages: string[] = []
  if (!Number.isSafeInteger(targetId) || targetId <= 0) messages.push('请选择已发布的目标子流程。')
  else if (targetId === selected.value?.id) messages.push('子流程不能选择当前流程自身。')
  else if (!target?.currentVersionId) messages.push('目标子流程不存在、已失效或尚未发布。')
  for (const [label, key] of [['传入', 'inputMappings'], ['返回', 'outputMappings']] as const) {
    const mappings = subflowMappings(node, key)
    if (mappings.length > 100 || mappings.some(item => !item.sourceKey.trim() || !item.targetKey.trim()
      || /\s/.test(item.sourceKey) || /\s/.test(item.targetKey))) messages.push(`${label}变量映射需要填写无空格的来源和目标变量。`)
    if (new Set(mappings.map(item => item.targetKey.trim())).size !== mappings.length) messages.push(`${label}变量的目标名称不能重复。`)
  }
  return messages
}
const publishedSubflowOptions = computed(() => flows.value.filter(flow => flow.id !== selected.value?.id && flow.currentVersionId)
  .map(flow => ({ value: flow.id, label: flow.name })))
const subflowTargetOptions = computed(() => {
  const options = [...publishedSubflowOptions.value]
  const currentId = Number(selectedNode.value?.config.flowId)
  if (currentId > 0 && !options.some(option => option.value === currentId)) {
    options.push({ value: currentId, label: '原目标已失效或未发布', disabled: true } as typeof options[number] & { disabled: boolean })
  }
  return options
})
const subflowInheritAll = computed({
  get: () => selectedNode.value?.config.inheritAllVariables !== false,
  set: (value: boolean) => setSubflowConfig('inheritAllVariables', value),
})
function setSubflowConfig(key: string, value: unknown) {
  const node = selectedNode.value
  if (!node || node.nodeType !== 'SUBFLOW' || !can('DESIGN') || busy.value) return
  graphCanvas.value?.checkpoint()
  const config = { ...node.config }
  if (value === undefined || value === null || value === '') delete config[key]
  else config[key] = value
  node.config = config
  publicationCheck.value = undefined; simulation.value = undefined
}
function setSubflowTarget(value?: number) { setSubflowConfig('flowId', value) }
function addSubflowMapping(key: SubflowMappingKey) {
  setSubflowConfig(key, [...subflowMappings(selectedNode.value, key), { sourceKey: '', targetKey: '' }])
}
function updateSubflowMapping(key: SubflowMappingKey, index: number, field: keyof SubflowMapping, value: string) {
  const mappings = subflowMappings(selectedNode.value, key)
  if (!mappings[index]) return
  mappings[index] = { ...mappings[index], [field]: value }
  setSubflowConfig(key, mappings)
}
function removeSubflowMapping(key: SubflowMappingKey, index: number) {
  setSubflowConfig(key, subflowMappings(selectedNode.value, key).filter((_, position) => position !== index))
}
const approvalAttachmentRequired = computed({
  get: () => (selectedNode.value?.config.attachmentPolicy as Record<string, unknown> | undefined)?.required === true,
  set: (required: boolean) => {
    const node = selectedNode.value
    if (!node || !['APPROVAL', 'FORM_TASK'].includes(node.nodeType) || !can('DESIGN') || busy.value) return
    graphCanvas.value?.checkpoint()
    const config = { ...node.config }
    if (required) config.attachmentPolicy = { required: true, minimumCount: 1 }
    else delete config.attachmentPolicy
    node.config = config
    publicationCheck.value = undefined; simulation.value = undefined
  },
})
const manualAllowedActions = computed({
  get: () => manualPolicyActions('allowedActions'),
  set: (actions: string[]) => updateManualOperationPolicy(actions, manualPolicyActions('opinionRequiredActions')),
})
const manualOpinionActions = computed({
  get: () => manualPolicyActions('opinionRequiredActions'),
  set: (actions: string[]) => updateManualOperationPolicy(manualPolicyActions('allowedActions'), actions),
})
const manualRequireClaim = computed({
  get: () => manualOperationPolicy(selectedNode.value).requireClaim === true,
  set: (requireClaim: boolean) => {
    if (requireClaim && approvalMode.value !== 'OR_SIGN') return
    updateManualOperationPolicy(manualPolicyActions('allowedActions'), manualPolicyActions('opinionRequiredActions'), requireClaim)
  },
})
const manualActionOptions = computed(() => [
  { value: 'APPROVE', label: selectedNode.value?.nodeType === 'FORM_TASK' ? '完成填写' : '同意' },
  { value: 'REJECT', label: '拒绝' }, { value: 'RETURN', label: '退回' }, { value: 'TRANSFER', label: '转交' },
  { value: 'DELEGATE', label: '委托代办' },
  { value: 'ADD_SIGN', label: '加签' }, { value: 'REMOVE_SIGN', label: '减签' },
])
const manualOpinionOptions = computed(() => manualActionOptions.value.map(option => ({ ...option,
  disabled: !manualAllowedActions.value.includes(option.value) || forcedOpinionActions.includes(option.value),
})))
function updateManualOperationPolicy(allowed: string[], required: string[], claimRule?: boolean) {
  const node = selectedNode.value
  if (!node || !['APPROVAL', 'FORM_TASK'].includes(node.nodeType) || !can('DESIGN') || busy.value) return
  graphCanvas.value?.checkpoint()
  const allowedActions = manualActionCodes.filter(action => allowed.includes(action))
  const opinionRequiredActions = allowedActions.filter(action => required.includes(action) || forcedOpinionActions.includes(action))
  const configured = manualOperationPolicy(node)
  const requireClaim = claimRule === undefined
    ? Object.prototype.hasOwnProperty.call(configured, 'requireClaim') ? configured.requireClaim : false : claimRule
  // 仅明确编辑时写入；旧缺省保留原样，其他冻结配置不随按钮选择改变。
  node.config = { ...node.config, manualOperationPolicy: { allowedActions, opinionRequiredActions, requireClaim } }
  publicationCheck.value = undefined; simulation.value = undefined
}
const selectedEdge = computed(() => edges.value.find(edge => edge.edgeKey === selectedEdgeKey.value))
const selectedEdgeSourceMode = computed<GatewayMode | undefined>(() => {
  const source = nodes.value.find(node => node.nodeKey === selectedEdge.value?.sourceNodeKey)
  if (source?.nodeType !== 'GATEWAY') return undefined
  const value = String(source.config.gatewayMode || 'EXCLUSIVE').toUpperCase()
  return ['EXCLUSIVE', 'PARALLEL', 'INCLUSIVE', 'MERGE'].includes(value) ? value as GatewayMode : 'EXCLUSIVE'
})
const selectedEdgeAllowsCondition = computed(() => !['PARALLEL', 'MERGE'].includes(selectedEdgeSourceMode.value || ''))
const selectedCondition = computed({ get: () => selectedEdge.value ? edgeCondition(selectedEdge.value) : emptyCondition(),
  set: (value: ConditionGroup) => updateEdgeCondition(value) })
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
const participantValueKey = computed(() => props.context === 'platform' ? 'accountId' : 'tenantMemberId')
const participantHelp = computed(() => props.context === 'platform'
  ? '仅可选择当前平台的有效人员；停用或移出平台后不能继续作为审批处理人。'
  : '人员来自当前系统组织，离职或移出工作空间会在发布时被拦截。')
const approverTypeOptions = computed(() => {
  const options = [
    { value: 'PERSON', label: '指定人员' },
    { value: 'INITIATOR', label: '发起人' },
    { value: 'DEPARTMENT', label: '部门成员' },
    { value: 'DEPARTMENT_MANAGER', label: '部门负责人' },
    { value: 'RECORD_OWNER', label: '记录负责人' },
    { value: 'DIRECT_MANAGER', label: '直属上级' },
    { value: 'INITIATOR_MANAGER', label: '发起人上级' },
    { value: 'PERSON_FIELD', label: '业务人员字段' },
    { value: 'STARTER_SELECTED', label: '发起时选择' },
    { value: 'PREVIOUS_HANDLER', label: '上一处理人' },
  ].map(option => ({ ...option, disabled: props.context === 'platform' && option.value !== 'PERSON' }))
  if (approverType.value && !options.some(option => option.value === approverType.value)) {
    options.push({ value: approverType.value, label: '原人员来源暂不可用', disabled: true })
  }
  return options
})
const platformParticipantWarning = computed(() => {
  if (props.context !== 'platform' || !selectedNode.value) return ''
  if (selectedNode.value.nodeType === 'FORM_TASK') return '平台填写节点的业务处理尚未接通，原配置保留；当前不能发布此填写节点。'
  if (approverType.value !== 'PERSON') return '平台当前支持指定人员审批；此节点原有人员来源尚未接通，配置仍保留，请明确选择有效指定人员后再发布。'
  const policy = selectedNode.value.assigneePolicy
  return Array.isArray(policy?.tenantMemberIds) && !Array.isArray(policy?.platformAccountIds)
    ? '此平台节点仍使用原系统成员配置，配置已保留；请重新选择平台有效人员，不能直接把系统成员当作平台审批人。' : ''
})
/** 人员身份始终按流程所在范围保存，平台账号不能伪装成工作空间成员。 */
function personPolicy(ids: number[] = []) {
  return props.context === 'platform' ? { type: 'PERSON', platformAccountIds: ids } : { type: 'PERSON', tenantMemberIds: ids }
}
const selectedApproverIds = computed({
  get: () => (selectedNode.value?.assigneePolicy?.[props.context === 'platform' ? 'platformAccountIds' : 'tenantMemberIds'] as number[] | undefined) || [],
  set: (ids: number[]) => {
    if (selectedNode.value && can('DESIGN') && !busy.value) {
      graphCanvas.value?.checkpoint()
      selectedNode.value.assigneePolicy = personPolicy(ids)
    }
  },
})
const selectedDepartmentIds = computed({
  get: () => (selectedNode.value?.assigneePolicy?.departmentIds as number[] | undefined) || [],
  set: (ids: number[]) => {
    if (!selectedNode.value || props.context !== 'system' || !can('DESIGN') || busy.value) return
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
const allSignCompletionMode = computed({
  get: () => {
    const policy = selectedNode.value?.config.allSignCompletionPolicy
    return policy && typeof policy === 'object' && !Array.isArray(policy)
      && (policy as Record<string, unknown>).mode === 'RATIO' ? 'RATIO' : 'ALL'
  },
  set: (mode: string) => {
    graphCanvas.value?.checkpoint()
    setConfig('allSignCompletionPolicy', mode === 'RATIO' ? { mode, ratioPercent: 100 } : { mode: 'ALL' })
  },
})
const allSignRatioPercent = computed({
  get: () => Number((selectedNode.value?.config.allSignCompletionPolicy as Record<string, unknown> | undefined)?.ratioPercent ?? 100),
  set: (ratioPercent: number | null) => {
    graphCanvas.value?.checkpoint()
    setConfig('allSignCompletionPolicy', { mode: 'RATIO', ratioPercent })
  },
})
const personFieldCode = computed({
  get: () => String(selectedNode.value?.assigneePolicy?.fieldCode || ''),
  set: (fieldCode: string) => {
    if (selectedNode.value && props.context === 'system' && can('DESIGN') && !busy.value) {
      selectedNode.value.assigneePolicy = { type: 'PERSON_FIELD', fieldCode }
    }
  },
})

const allowRuntimeNodes = computed({
  get: () => {
    const rules = selectedNode.value?.config.runtimeRules
    return !rules || typeof rules !== 'object' || Array.isArray(rules)
      || (rules as Record<string, unknown>).allowAddNodes !== false
  },
  set: (allowAddNodes: boolean) => {
    if (!selectedNode.value || selectedNode.value.nodeType !== 'START' || !can('DESIGN') || busy.value) return
    graphCanvas.value?.checkpoint()
    const configured = selectedNode.value.config.runtimeRules
    const rules = configured && typeof configured === 'object' && !Array.isArray(configured) ? configured as Record<string, unknown> : {}
    selectedNode.value.config = { ...selectedNode.value.config, runtimeRules: { ...rules, allowAddNodes } }
    publicationCheck.value = undefined; simulation.value = undefined
  },
})
const runtimeRulesWarning = computed(() => {
  const rules = selectedNode.value?.config.runtimeRules
  if (rules === undefined) return ''
  return !rules || typeof rules !== 'object' || Array.isArray(rules)
    || Object.prototype.hasOwnProperty.call(rules, 'allowAddNodes') && typeof (rules as Record<string, unknown>).allowAddNodes !== 'boolean'
    ? '原运行加节点配置无效，已保留；请明确重新设置开关后再发布。' : ''
})

function repeatPolicy(node: FlowNodeInput | undefined): Record<string, unknown> {
  const configured = node?.config.repeatApproval
  return configured && typeof configured === 'object' && !Array.isArray(configured) ? configured as Record<string, unknown> : {}
}
/** 与发布检查一致，只选择图中可达前序审批；循环或后续关系不能作为既有同意事实。 */
function graphReaches(source: string, target: string) {
  const visited = new Set<string>(), pending = [source]
  while (pending.length) {
    const key = pending.shift()!
    if (visited.has(key)) continue
    visited.add(key)
    if (key === target) return true
    pending.push(...edges.value.filter(edge => edge.sourceNodeKey === key).map(edge => edge.targetNodeKey))
  }
  return false
}
function priorApprovalNodes(node: FlowNodeInput) {
  return nodes.value.filter(source => source.nodeType === 'APPROVAL' && source.nodeKey !== node.nodeKey
    && graphReaches(source.nodeKey, node.nodeKey) && !graphReaches(node.nodeKey, source.nodeKey))
}
const repeatEnabled = computed({
  get: () => repeatPolicy(selectedNode.value).enabled === true,
  set: (enabled: boolean) => updateRepeatPolicy(enabled
    ? { enabled, sourceNodeKeys: repeatPolicy(selectedNode.value).sourceNodeKeys ?? [], formChangeRule: 'UNCHANGED' }
    : { enabled }),
})
const repeatSourceKeys = computed({
  get: () => {
    const keys = repeatPolicy(selectedNode.value).sourceNodeKeys
    return Array.isArray(keys) ? keys.filter((key): key is string => typeof key === 'string') : []
  },
  set: (sourceNodeKeys: string[]) => updateRepeatPolicy({ sourceNodeKeys }),
})
const repeatSourceOptions = computed(() => {
  const choices = selectedNode.value ? priorApprovalNodes(selectedNode.value).map(node => ({ value: node.nodeKey, label: node.name, disabled: false })) : []
  for (const key of new Set(repeatSourceKeys.value)) {
    if (!choices.some(choice => choice.value === key)) choices.push({ value: key,
      label: `${nodes.value.find(node => node.nodeKey === key)?.name || '原范围节点'}（当前不可用）`, disabled: true })
  }
  return choices
})
const repeatHasCondition = computed({
  get: () => Object.prototype.hasOwnProperty.call(repeatPolicy(selectedNode.value), 'condition'),
  set: (enabled: boolean) => {
    if (enabled && (props.context !== 'system' || !businessModuleId.value || !fieldsReady.value)) return
    updateRepeatPolicy(enabled ? { condition: emptyCondition() } : {}, !enabled)
  },
})
const selectedRepeatCondition = computed({
  get: () => {
    const condition = repeatPolicy(selectedNode.value).condition
    return supportedCondition(condition) && isConditionGroup(condition) ? bindingCondition(condition) : emptyCondition()
  },
  set: (condition: ConditionGroup) => { if (repeatEnabled.value && fieldsReady.value) updateRepeatPolicy({ condition }) },
})
function updateRepeatPolicy(changes: Record<string, unknown>, removeCondition = false) {
  if (!selectedNode.value || selectedNode.value.nodeType !== 'APPROVAL' || !can('DESIGN') || busy.value) return
  graphCanvas.value?.checkpoint()
  const policy = { ...repeatPolicy(selectedNode.value), ...changes }
  if (removeCondition) delete policy.condition
  selectedNode.value.config = { ...selectedNode.value.config, repeatApproval: policy }
  publicationCheck.value = undefined; simulation.value = undefined
}
function repeatApprovalIssues(node: FlowNodeInput): string[] {
  const configured = node.config.repeatApproval
  if (configured == null) return []
  const policy = repeatPolicy(node)
  if (typeof configured !== 'object' || Array.isArray(configured)
    || Object.prototype.hasOwnProperty.call(policy, 'enabled') && typeof policy.enabled !== 'boolean') return ['原同人后续审批设置无效，请明确重新配置。']
  if (policy.enabled !== true) return []
  const issues: string[] = []
  if (node.nodeType !== 'APPROVAL') return ['同人后续审批仅适用于审批节点，不能自动完成填写任务。']
  if (policy.formChangeRule !== 'UNCHANGED') issues.push('必须核验候选和变量未变化；旧规则已保留，请关闭后重新启用当前规则。')
  const sources = policy.sourceNodeKeys, valid = new Set(priorApprovalNodes(node).map(source => source.nodeKey))
  if (!Array.isArray(sources) || !sources.length) issues.push('请选择至少一个可达的前序审批节点作为适用范围。')
  else if (sources.some(key => typeof key !== 'string' || !valid.has(key)) || new Set(sources).size !== sources.length) issues.push('原适用范围含重复或失效节点，请重新选择前序审批；不能选择自身、后续或填写节点。')
  if (!Object.prototype.hasOwnProperty.call(policy, 'condition')) return issues
  const condition = policy.condition
  if (!supportedCondition(condition) || !isConditionGroup(condition)) return [...issues, '原附加条件格式不可用，已保留；请明确重新配置或取消附加条件。']
  if (!conditionFieldCodes(condition).some(Boolean)) return [...issues, '附加业务条件不能留空；请添加条件，或取消附加条件使用必选安全规则。']
  if (props.context === 'platform') return [...issues, '平台业务字段附加条件尚未接通，原条件保留；可明确取消附加条件。']
  if (!businessModuleId.value) return [...issues, '请先选择业务对象，再配置附加业务条件。']
  if (!fieldsReady.value) return [...issues, conditionError.value || '业务字段目录尚未就绪，请读取后确认附加条件。']
  return [...issues, ...conditionIssues(condition, conditionFields.value), ...typedConditionIssues(condition)]
}

function returnPolicy(node: FlowNodeInput | undefined): Record<string, unknown> {
  const configured = node?.config.returnPolicy
  if (configured === undefined) return { target: 'STARTER', resubmitRoute: 'FROM_START' }
  return configured && typeof configured === 'object' && !Array.isArray(configured)
    ? configured as Record<string, unknown> : {}
}
function priorHumanNodes(node: FlowNodeInput) {
  return nodes.value.filter(source => ['APPROVAL', 'FORM_TASK'].includes(source.nodeType) && source.nodeKey !== node.nodeKey
    && graphReaches(source.nodeKey, node.nodeKey) && !graphReaches(node.nodeKey, source.nodeKey))
}
const selectedReturnTarget = computed({
  get: () => String(returnPolicy(selectedNode.value).target || 'STARTER'),
  set: (target: string) => updateReturnPolicy({ target }, target !== 'SPECIFIED'),
})
const selectedReturnNodeKey = computed({
  get: () => String(returnPolicy(selectedNode.value).targetNodeKey || ''),
  set: (targetNodeKey: string) => updateReturnPolicy({ targetNodeKey }),
})
const selectedResubmitRoute = computed({
  get: () => String(returnPolicy(selectedNode.value).resubmitRoute || 'FROM_START'),
  set: (resubmitRoute: string) => updateReturnPolicy({ resubmitRoute }),
})
const returnNodeOptions = computed(() => selectedNode.value ? priorHumanNodes(selectedNode.value)
  .map(node => ({ value: node.nodeKey, label: `${node.name}（${node.nodeType === 'FORM_TASK' ? '填写' : '审批'}）` })) : [])
function updateReturnPolicy(changes: Record<string, unknown>, removeTarget = false) {
  const node = selectedNode.value
  if (!node || !['APPROVAL', 'FORM_TASK'].includes(node.nodeType) || !can('DESIGN') || busy.value) return
  graphCanvas.value?.checkpoint()
  const policy = { ...returnPolicy(node), ...changes }
  if (removeTarget) delete policy.targetNodeKey
  node.config = { ...node.config, returnPolicy: policy }
  publicationCheck.value = undefined; simulation.value = undefined
}
function returnPolicyIssues(node: FlowNodeInput): string[] {
  const configured = node.config.returnPolicy
  if (configured === undefined) return []
  if (!['APPROVAL', 'FORM_TASK'].includes(node.nodeType) || !configured || typeof configured !== 'object' || Array.isArray(configured)) {
    return ['退回策略只适用于审批或填写节点，请用页面选项重新配置。']
  }
  const policy = configured as Record<string, unknown>, target = policy.target, route = policy.resubmitRoute
  if (!['STARTER', 'PREVIOUS', 'SPECIFIED'].includes(String(target))
    || !['FROM_START', 'RETURN_NODE'].includes(String(route))) return ['请选择有效的退回目标和重新提交路径。']
  const keys = Object.keys(policy)
  if (target === 'SPECIFIED') {
    const valid = new Set(priorHumanNodes(node).map(item => item.nodeKey))
    if (keys.length !== 3 || !keys.includes('targetNodeKey') || typeof policy.targetNodeKey !== 'string'
      || !valid.has(policy.targetNodeKey)) return ['指定退回目标必须是可到达当前节点的前序审批或填写节点。']
  } else if (keys.length !== 2 || keys.includes('targetNodeKey')) return ['当前退回方式不能保留指定节点，请重新选择。']
  if (target === 'PREVIOUS' && !priorHumanNodes(node).length) return ['当前节点之前没有可退回的人工节点。']
  return []
}

function timeoutDurationValue(node: FlowNodeInput | undefined) {
  const policy = node?.timeoutPolicy || {}
  return Object.prototype.hasOwnProperty.call(policy, 'timeoutMinutes') ? policy.timeoutMinutes : policy.minutes
}
const timeoutMinutes = computed(() => {
  const value = timeoutDurationValue(selectedNode.value)
  return typeof value === 'number' ? value : undefined
})
const timeoutEnabled = computed(() => {
  const value = timeoutDurationValue(selectedNode.value)
  return value !== undefined && value !== null && !(typeof value === 'string' && !value.trim())
})
const selectedTimeoutAction = computed({
  get: () => String(selectedNode.value?.timeoutPolicy?.action ?? 'INCIDENT').trim().toUpperCase() || 'INCIDENT',
  set: (action: string) => setTimeoutPolicy('action', action),
})
const timeoutTargetId = computed({
  get: () => {
    const value = selectedNode.value?.timeoutPolicy?.[props.context === 'platform' ? 'targetPlatformAccountId' : 'targetTenantMemberId']
    return typeof value === 'number' ? value : undefined
  },
  set: (value: number | undefined) => setTimeoutPolicy(props.context === 'platform' ? 'targetPlatformAccountId' : 'targetTenantMemberId', value ?? null),
})
const timeoutActionOptions = computed(() => {
  const options = [
    { value: 'INCIDENT', label: '进入异常，人工处理', disabled: false },
    { value: 'REMIND', label: '提醒原处理人一次', disabled: false },
    { value: 'TRANSFER', label: '转交指定人员一次', disabled: false },
    { value: 'ESCALATE_MANAGER', label: '转交直属上级一次', disabled: props.context === 'platform' },
    { value: 'AUTO_APPROVE', label: '自动同意', disabled: selectedNode.value?.nodeType === 'FORM_TASK' },
    { value: 'AUTO_REJECT', label: '自动拒绝', disabled: false },
  ]
  if (!options.some(option => option.value === selectedTimeoutAction.value)) {
    options.push({ value: selectedTimeoutAction.value, label: '原超时方式暂不可用', disabled: true })
  }
  return options
})
const timeoutHelp = computed(() => {
  if (!timeoutEnabled.value) return '未设置时限，超时处理未启用；原处理方式和目标配置保留。'
  if (selectedTimeoutAction.value === 'REMIND') return '到期提醒一次，任务仍由原处理人处理，不自动推进流程。'
  if (['TRANSFER', 'ESCALATE_MANAGER'].includes(selectedTimeoutAction.value)) return '到期只转交一次，原处理人失去该任务处理权；转交后清除时限，不循环转交。'
  if (selectedTimeoutAction.value === 'AUTO_APPROVE') return '自动同意仍须通过节点必填、当前授权及最终业务动作校验；失败保留现场并进入异常。'
  if (selectedTimeoutAction.value === 'AUTO_REJECT') return '到期自动拒绝，原待审批业务动作不生效。'
  return '到期进入异常，保留任务现场，需要有权人员处理。'
})
const timeoutWarnings = computed(() => {
  const warnings: string[] = [], policy = selectedNode.value?.timeoutPolicy || {}
  if (Object.prototype.hasOwnProperty.call(policy, 'timeoutMinutes') && policy.minutes != null
    && policy.timeoutMinutes !== policy.minutes) warnings.push('新旧时限配置不一致，请重新设置处理时限，统一为当前分钟配置。')
  if (!timeoutEnabled.value) return warnings
  const value = timeoutDurationValue(selectedNode.value)
  if (typeof value !== 'number' || !Number.isSafeInteger(value) || value < 1 || value > 525600) warnings.push('原处理时限不是有效的整数分钟，请重新设置为 1 至 525600 分钟。')
  if (!['INCIDENT', 'REMIND', 'TRANSFER', 'ESCALATE_MANAGER', 'AUTO_APPROVE', 'AUTO_REJECT'].includes(selectedTimeoutAction.value)) warnings.push('原超时处理方式尚未接通，配置已保留，请重新选择有效处理方式。')
  if (props.context === 'platform' && selectedTimeoutAction.value === 'ESCALATE_MANAGER') warnings.push('平台的直属上级来源尚未接通，请选择指定平台人员转交。')
  if (selectedTimeoutAction.value === 'TRANSFER') {
    const otherKey = props.context === 'platform' ? 'targetTenantMemberId' : 'targetPlatformAccountId'
    if (Object.prototype.hasOwnProperty.call(policy, otherKey)
      || ['targetTenantMemberIds', 'targetPlatformAccountIds', 'targetIds'].some(key => Object.prototype.hasOwnProperty.call(policy, key))) {
      warnings.push('原转交目标使用其他范围或多人配置，配置已保留，请重新选择当前范围的一名有效人员。')
    }
  }
  return warnings
})

function can(action: string) {
  return allowsPermission(current.value?.permissions, 'FLOW', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'FLOW', '*', action)
}

function describeError(error: unknown, invalidateDesigner = true) {
  if (error instanceof ApiError && [401, 403, 404].includes(error.httpStatus || 0)
    && (invalidateDesigner || error.httpStatus === 401)) {
    generation++; listGeneration++; loading.value = false
    clearDesigner()
    flows.value = []; modules.value = []; catalogReady = false
    directory.value = { departments: [], people: [], permissionVersion: 0 }
  }
  const text = error instanceof ApiError ? `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
    : error instanceof Error ? error.message : '操作失败'
  pageError.value = text
  return text
}

function requestContext() { return { generation, flowGeneration, flowId: selectingFlowId.value,
  token: token.value, identity: identity.value, moduleId: businessModuleId.value } }
function requestCurrent(context: ReturnType<typeof requestContext>, flowBound = false, moduleBound = false) {
  return active && activeSection.value === 'designer' && context.generation === generation
    && context.token === token.value && context.identity === identity.value
    && (!flowBound || context.flowGeneration === flowGeneration && context.flowId === selectingFlowId.value)
    && (!moduleBound || context.moduleId === businessModuleId.value)
}
function clearSamples() {
  sampleGeneration++
  sampleRecords.value = []; sampleRecordId.value = undefined; sampleError.value = ''; samplesLoading.value = false
  Object.keys(sampleValues).forEach(code => delete sampleValues[code])
  simulation.value = undefined
}
function clearConditions() {
  fieldGeneration++; conditionFields.value = []; fieldsReady.value = false; fieldsLoading.value = false
  conditionError.value = ''; clearSamples()
}
function clearNodeFields() {
  nodeFieldGeneration++; nodeFormFields.value = []; nodeFieldsReady.value = false
  nodeFieldsLoading.value = false; nodeFieldsError.value = ''
}
function clearAutomaticFields() {
  automaticFieldGeneration++; automaticFields.value = []; automaticConditionFields.value = []
  automaticFieldsLoading.value = false; automaticFieldsError.value = ''
}
function nodePolicyIssues(node: FlowNodeInput) {
  return nodeFieldPolicyIssues(node.formPolicy, nodeFormFields.value, nodeFieldsReady.value, nodeFieldsError.value)
}
function updateNodeFormPolicy(value: Record<string, unknown>) {
  if (!selectedNode.value || !can('DESIGN') || busy.value || !nodeFieldsReady.value) return
  graphCanvas.value?.checkpoint()
  selectedNode.value.formPolicy = value
  publicationCheck.value = undefined; simulation.value = undefined
}
function clearDesigner() {
  flowGeneration++; selectingFlowId.value = undefined; selected.value = undefined
  nodes.value = []; edges.value = []; selectedNodeKey.value = selectedEdgeKey.value = undefined
  connectionTargetKey.value = undefined; publicationCheck.value = undefined
  publishOpen.value = false; createOpen.value = false; busy.value = ''; clearConditions(); clearNodeFields(); clearAutomaticFields()
  graphCanvas.value?.clearHistory()
}
function supportedCondition(value: unknown): value is ConditionNode {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return false
  const node = value as Record<string, unknown>
  if ('conditions' in node) return ['ALL', 'ANY'].includes(String(node.mode)) && Array.isArray(node.conditions)
    && node.conditions.every(supportedCondition)
  return typeof node.field === 'string' && typeof node.operator === 'string'
}
function edgeCondition(edge: FlowEdgeInput): ConditionGroup {
  const stored = edge.config.condition
  if (stored !== undefined && !supportedCondition(stored)) return emptyCondition()
  const result = bindingCondition(stored as ConditionNode | undefined, edge.conditionExpression)
  // 旧表达式没有布尔类型信息；只按可信发布目录恢复明确的布尔常量。
  if (stored === undefined && /(?:==|!=)\s*(?:true|false)\s*$/.test(edge.conditionExpression || '')) result.conditions.forEach(node => {
    if (!isConditionGroup(node) && conditionFields.value.find(field => field.code === node.field)?.fieldType === 'BOOLEAN'
      && ['true', 'false'].includes(String(node.value))) node.value = node.value === 'true'
  })
  return result
}
function edgeIssues(edge: FlowEdgeInput) {
  if (edge.config.condition === undefined && !edge.conditionExpression) return []
  if (edge.config.condition !== undefined && !supportedCondition(edge.config.condition)) return ['原有条件格式不可用，请明确重新配置；原条件尚未删除。']
  const condition = edgeCondition(edge)
  if (edge.conditionExpression && !conditionFieldCodes(condition).every(Boolean)) return ['原有表达式无法转换，请明确重新配置；原表达式尚未删除。']
  if (!businessModuleId.value) return ['请先为开始节点选择业务对象，再确认原有条件。']
  if (props.context === 'platform') return ['业务字段条件需进入对应系统配置；平台原有条件仍保留。']
  if (!fieldsReady.value) return [conditionError.value || '业务字段目录尚未就绪，请读取后再确认条件。']
  return [...conditionIssues(condition, conditionFields.value), ...typedConditionIssues(condition)]
}
function typedConditionIssues(node: ConditionNode): string[] {
  if (isConditionGroup(node)) return node.conditions.flatMap(typedConditionIssues)
  const field = conditionFields.value.find(field => field.code === node.field)
  if (!field || ['EMPTY', 'NOT_EMPTY'].includes(node.operator)) return []
  const choices = ['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS'].includes(field.fieldType)
  let options: Array<{ value?: string; code?: string }> = []
  try { options = JSON.parse(field.configJson || '{}').options || [] } catch { /* 服务端失效配置不推断选项。 */ }
  const valid = (value: unknown) => {
    if (['NUMBER', 'MONEY'].includes(field.fieldType)) return typeof value === 'number' && Number.isFinite(value)
    if (field.fieldType === 'BOOLEAN') return typeof value === 'boolean'
    if (typeof value !== 'string') return false
    if (field.fieldType === 'DATE' && !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false
    if (field.fieldType === 'DATETIME' && !Number.isFinite(Date.parse(/[Zz]|[+-]\d{2}:\d{2}$/.test(value) ? value : `${value}+08:00`))) return false
    return !choices || options.some(option => (option.value ?? option.code) === value)
  }
  const multiple = node.operator === 'IN' || field.fieldType === 'MULTI_SELECT' && node.operator !== 'CONTAINS'
  const validValue = multiple ? Array.isArray(node.value) && node.value.every(valid) : valid(node.value)
  return validValue ? [] : [`${field.name}的比较值类型或选项已失效，请重新配置。`]
}
function changeBusinessModule(id?: number) {
  const start = nodes.value.find(node => node.nodeType === 'START')
  if (!start || !can('DESIGN')) return
  graphCanvas.value?.checkpoint()
  const config = { ...start.config }
  if (id) config.moduleId = id
  else delete config.moduleId
  start.config = config
  publicationCheck.value = undefined
}
function testVariables() { return Object.fromEntries(simulationFields.value.filter(field => sampleValues[field.code] !== undefined)
  .map(field => [field.code, sampleValues[field.code]])) }
function setSampleValue(code: string, value: unknown) {
  if (!fieldsReady.value || !simulationFields.value.some(field => field.code === code)) return
  sampleValues[code] = value; sampleRecordId.value = undefined; simulation.value = undefined
}
function useSample(id?: number) {
  sampleRecordId.value = id
  Object.keys(sampleValues).forEach(code => delete sampleValues[code])
  const record = sampleRecords.value.find(record => record.id === id)
  simulationFields.value.forEach(field => {
    if (record && Object.prototype.hasOwnProperty.call(record.fields, field.code)) sampleValues[field.code] = record.fields[field.code]
  })
  simulation.value = undefined
}
async function loadModules() {
  if (!token.value || props.context !== 'system' || !can('VIEW')) return
  const context = requestContext()
  try {
    const result = await api<RuntimeModuleCatalogItem[]>('/api/runtime/modules', {}, context.token)
    if (!requestCurrent(context)) return
    modules.value = [...new Map(result.filter(module => (module.configurationMode || 'CURRENT') === 'CURRENT')
      .map(module => [module.moduleId, module])).values()]
    catalogReady = true
    await refreshBusinessFields()
    await loadAutomaticFields()
  } catch (error) {
    if (!requestCurrent(context)) return
    modules.value = []; catalogReady = false; clearConditions(); clearNodeFields()
    conditionError.value = '当前身份无法读取业务对象目录，请重新确认模块权限。'
    message.error(describeError(error, false))
  }
}
async function loadNodeFormFields() {
  clearNodeFields()
  const context = requestContext(), request = nodeFieldGeneration
  if (!businessModuleId.value || !selected.value) return
  if (props.context === 'platform') { nodeFieldsError.value = '节点业务字段策略需要进入对应系统配置。'; return }
  if (!catalogReady) return
  if (!businessModule.value) { nodeFieldsError.value = '原业务对象当前不可用，请重新选择有权访问的模块；旧节点策略仍保留。'; return }
  nodeFieldsLoading.value = true
  try {
    const fields = await api<FlowNodeFormField[]>(`/api/flows/node-form-fields?moduleId=${context.moduleId}`, {}, context.token)
    if (!requestCurrent(context, true, true) || request !== nodeFieldGeneration) return
    nodeFormFields.value = fields; nodeFieldsReady.value = true
  } catch (error) {
    if (!requestCurrent(context, true, true) || request !== nodeFieldGeneration) return
    clearNodeFields()
    nodeFieldsError.value = '节点字段目录已失效或当前无权读取，请刷新并主动处理旧策略。'
    message.error(describeError(error, error instanceof ApiError && error.code === 'PERMISSION_DENIED'))
  } finally { if (requestCurrent(context, true, true) && request === nodeFieldGeneration) nodeFieldsLoading.value = false }
}
async function loadAutomaticFields() {
  clearAutomaticFields()
  const moduleId = automaticModuleId.value, nodeKey = selectedNode.value?.nodeKey
  const nodeType = selectedNode.value?.nodeType, request = automaticFieldGeneration, requestToken = token.value
  if (!moduleId || !nodeKey || !['UPDATE_FIELD', 'DATA_ACTION'].includes(nodeType || '') || !requestToken) return
  if (!automaticModule.value) { automaticFieldsError.value = '目标模块已失效或当前不可访问，请重新选择。'; return }
  automaticFieldsLoading.value = true
  try {
    const [fields, conditions] = await Promise.all([
      api<FlowNodeFormField[]>(`/api/flows/node-form-fields?moduleId=${moduleId}`, {}, requestToken),
      api<ConditionField[]>(`/api/flows/condition-fields?moduleId=${moduleId}`, {}, requestToken),
    ])
    if (!active || request !== automaticFieldGeneration || requestToken !== token.value
      || selectedNode.value?.nodeKey !== nodeKey || automaticModuleId.value !== moduleId) return
    automaticFields.value = fields; automaticConditionFields.value = conditions.filter(field => field.fieldType !== 'SECRET')
  } catch (error) {
    if (!active || request !== automaticFieldGeneration || selectedNode.value?.nodeKey !== nodeKey) return
    clearAutomaticFields(); automaticFieldsError.value = '无法读取目标模块字段，请确认模块已发布且拥有相应数据权限。'
    message.error(describeError(error, false))
  } finally {
    if (active && request === automaticFieldGeneration && selectedNode.value?.nodeKey === nodeKey) automaticFieldsLoading.value = false
  }
}
async function refreshBusinessFields() {
  await Promise.all([loadConditionFields(), loadNodeFormFields()])
}
async function loadConditionFields() {
  clearConditions()
  const context = requestContext(), fieldRequest = fieldGeneration
  if (!businessModuleId.value || !selected.value) return
  if (!catalogReady) return
  if (!businessModule.value) { conditionError.value = '原业务对象当前不可用，请重新选择有权访问的模块；原条件仍保留。'; return }
  fieldsLoading.value = true
  try {
    const fields = await api<ConditionField[]>(`/api/flows/condition-fields?moduleId=${context.moduleId}`, {}, context.token)
    if (!requestCurrent(context, true, true) || fieldRequest !== fieldGeneration) return
    conditionFields.value = fields.filter(field => field.fieldType !== 'SECRET'); fieldsReady.value = true
    await loadSampleRecords()
  } catch (error) {
    if (!requestCurrent(context, true, true) || fieldRequest !== fieldGeneration) return
    clearConditions()
    conditionError.value = '业务字段目录已失效或当前无权读取，请刷新并重新确认原条件。'
    // 流程配置权限整体撤销时清空草稿和目录；单个业务字段失效仍保留可修正的图。
    message.error(describeError(error, error instanceof ApiError && error.code === 'PERMISSION_DENIED'))
  } finally { if (requestCurrent(context, true, true) && fieldRequest === fieldGeneration) fieldsLoading.value = false }
}
async function loadSampleRecords() {
  clearSamples()
  const context = requestContext(), sampleRequest = sampleGeneration, module = businessModule.value
  if (!module || !fieldsReady.value || !allowsPermission(current.value?.permissions, 'MODULE', module.moduleCode, 'LIST')) return
  samplesLoading.value = true
  try {
    const result = await api<RuntimeRecordList>(`/api/runtime/modules/${encodeURIComponent(module.moduleCode)}/records?page=1&pageSize=20&tenantScope=OWN`, {}, context.token)
    if (!requestCurrent(context, true, true) || sampleRequest !== sampleGeneration) return
    sampleRecords.value = result.records.filter(record => record.ownedByCurrentTenant)
  } catch (error) {
    if (!requestCurrent(context, true, true) || sampleRequest !== sampleGeneration) return
    if (error instanceof ApiError && [401, 403, 404].includes(error.httpStatus || 0)) {
      clearConditions(); conditionError.value = '模块或记录读取权限已变化，请刷新业务字段并重新确认条件。'
      message.error(describeError(error, false))
    } else { clearSamples(); sampleError.value = '当前无法读取实际记录样本，可继续填写类型化模拟值。' }
  } finally { if (requestCurrent(context, true, true) && sampleRequest === sampleGeneration) samplesLoading.value = false }
}

function nodeTypeLabel(type: FlowNodeType, node?: FlowNodeInput) {
  if (type === 'GATEWAY') return ({ EXCLUSIVE: '条件分支', PARALLEL: '并行网关', INCLUSIVE: '包容网关', MERGE: '汇合网关' } as Record<string, string>)[
    String(node?.config.gatewayMode || 'EXCLUSIVE').toUpperCase()] || '条件分支'
  if (type === 'WAIT_TIMER') return '等待（旧版）'
  return palette.find(item => item.type === type)?.label || '业务节点'
}
async function loadCallbackCatalog() {
  callbackCatalog.value = []; callbackCatalogError.value = ''
  if (!token.value || props.context !== 'system' || !can('DESIGN')) return
  const context = requestContext()
  callbackCatalogLoading.value = true
  try {
    const result = await api<FlowCallbackOption[]>('/api/flows/callback-catalog', {}, context.token)
    if (!requestCurrent(context)) return
    callbackCatalog.value = result
    if (!result.length) callbackCatalogError.value = '当前工作空间没有可用的已发布 Webhook 应用业务事件回调。'
  } catch (error) {
    if (!requestCurrent(context)) return
    callbackCatalogError.value = '无法读取已发布应用回调，请检查应用发布状态和 Webhook 密钥引用。'
    message.error(describeError(error, false))
  } finally { if (requestCurrent(context)) callbackCatalogLoading.value = false }
}

function flowStatusLabel(status: string) {
  return status === 'PUBLISHED' ? '已发布' : status === 'DRAFT' ? '草稿' : status
}

function simulationStepName(nodeKey: string) {
  return nodes.value.find(node => node.nodeKey === nodeKey)?.name || '流程步骤'
}

function simulationStepType(nodeKey: string, fallback: FlowNodeType) {
  const node = nodes.value.find(item => item.nodeKey === nodeKey)
  return nodeTypeLabel(node?.nodeType || fallback, node)
}

async function loadFlows(preferId?: number) {
  if (!token.value || !can('VIEW')) return
  const context = requestContext(), listRequest = ++listGeneration
  const previousId = selectingFlowId.value
  loading.value = true
  try {
    const result = await api<FlowDefinitionView[]>('/api/flows', {}, context.token)
    if (!requestCurrent(context) || listRequest !== listGeneration) return
    flows.value = result
    if (selectingFlowId.value !== previousId) return
    const id = preferId ?? selectingFlowId.value ?? flows.value[0]?.id
    if (id) await selectFlow(id)
    else clearDesigner()
  } catch (error) {
    if (!requestCurrent(context) || listRequest !== listGeneration) return
    message.error(describeError(error))
  } finally {
    if (requestCurrent(context) && listRequest === listGeneration) loading.value = false
  }
}

async function loadDirectory() {
  if (!token.value || !can('DESIGN')) return
  const context = requestContext()
  try {
    const result = await api<FlowParticipantDirectory>('/api/flow-runtime/participants', {}, context.token)
    if (requestCurrent(context)) directory.value = result
  } catch (error) {
    if (!requestCurrent(context)) return
    directory.value = { departments: [], people: [], permissionVersion: 0 }
    message.error(describeError(error, false))
  }
}

async function selectFlow(id: number) {
  if (!token.value || !can('VIEW')) return
  clearDesigner()
  selectingFlowId.value = id
  const context = requestContext()
  try {
    const detail = await api<FlowDefinitionView>(`/api/flows/${id}`, {}, context.token)
    if (!requestCurrent(context, true)) return
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
    if (!requestCurrent(context, true)) return
    message.error(describeError(error))
  }
}

async function createFlow() {
  if (!token.value || busy.value || !can('DESIGN') || !createForm.name.trim()) return
  const context = requestContext()
  busy.value = 'create'
  try {
    const flow = await api<FlowDefinitionView>('/api/flows', {
      method: 'POST', body: JSON.stringify({
        code: createForm.code || undefined, name: createForm.name, description: createForm.description || undefined,
      }),
    }, context.token)
    if (!requestCurrent(context)) return
    createOpen.value = false
    Object.assign(createForm, { code: '', name: '', description: '' })
    message.success('流程草稿已创建，请继续配置节点和处理规则')
    await loadFlows(flow.id)
  } catch (error) {
    if (!requestCurrent(context)) return
    message.error(describeError(error))
  } finally {
    if (requestCurrent(context)) busy.value = ''
  }
}

function addNode(type: FlowNodeType, gatewayMode?: GatewayMode) {
  if (!can('DESIGN')) return
  if (type === 'START' && nodes.value.some(node => node.nodeType === 'START')) return message.warning('开始节点只能有一个')
  if (type === 'END' && nodes.value.some(node => node.nodeType === 'END')) return message.warning('当前画布已有结束节点')
  graphCanvas.value?.checkpoint()
  nodeSequence += 1
  const key = `${type.toLowerCase()}_${Date.now().toString(36)}_${nodeSequence}`
  const input: FlowNodeInput = {
    nodeKey: key, nodeType: type, name: palette.find(item => item.type === type && item.gatewayMode === gatewayMode)?.label
      || palette.find(item => item.type === type)?.label || type,
    positionX: 80 + (nodes.value.length % 4) * 220,
    positionY: 80 + Math.floor(nodes.value.length / 4) * 150,
    config: {},
  }
  if (type === 'GATEWAY') {
    input.config = { gatewayMode: gatewayMode || 'EXCLUSIVE' }
    input.name = ({ EXCLUSIVE: '业务条件', PARALLEL: '并行处理', INCLUSIVE: '可选并行处理', MERGE: '分支汇合' })[gatewayMode || 'EXCLUSIVE']
  }
  if (type === 'WAIT') input.config = { schemaVersion: 1, waitMode: 'DURATION', durationValue: 1, durationUnit: 'HOUR' }
  if (type === 'TIMER') input.config = { schemaVersion: 1, waitMode: 'ABSOLUTE' }
  if (type === 'APPROVAL' || type === 'FORM_TASK') {
    input.name = '负责人审批'
    input.config = { approvalMode: 'OR_SIGN', manualOperationPolicy: defaultManualOperationPolicy(),
      returnPolicy: { target: 'STARTER', resubmitRoute: 'FROM_START' } }
    input.assigneePolicy = personPolicy()
    if (type === 'FORM_TASK') input.name = '填写业务表单'
  }
  if (type === 'SUBFLOW') {
    input.name = '调用子流程'
    input.config = { inheritAllVariables: true, inputMappings: [], outputMappings: [] }
  }
  if (type === 'WEBHOOK') input.name = '调用业务服务'
  if (['CC', 'NOTIFICATION', 'MESSAGE'].includes(type)) {
    input.name = type === 'CC' ? '流程抄送' : type === 'MESSAGE' ? '业务消息' : '结果通知'
    input.assigneePolicy = personPolicy()
    input.config = { schemaVersion: 1, subject: input.name, content: '流程状态已更新，请打开消息查看详情。',
      appendInstanceTitle: true, destinationType: 'FLOW_INSTANCE' }
  }
  if (type === 'UPDATE_FIELD') {
    input.name = '更新当前业务字段'
    input.config = { schemaVersion: 1, operation: 'UPDATE', targetRecordSource: 'CURRENT_RECORD', assignments: [] }
  }
  if (type === 'DATA_ACTION') {
    input.name = '新建模块数据'
    input.config = { schemaVersion: 1, operation: 'CREATE', assignments: [], title: { source: 'FIXED', value: '' } }
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

function updateEdgeCondition(value: ConditionGroup) {
  if (!selectedEdge.value || !can('DESIGN') || !fieldsReady.value) return
  graphCanvas.value?.checkpoint()
  selectedEdge.value.config = { ...selectedEdge.value.config, condition: value }
  delete selectedEdge.value.conditionExpression
  publicationCheck.value = undefined
  simulation.value = undefined
}

function updateEdgePriority(value: number | null) {
  if (selectedEdge.value) selectedEdge.value.priorityOrder = value ?? 10
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
  void graphCanvas.value?.fitView()
}

function setApproverType(type: string) {
  if (!selectedNode.value || !can('DESIGN') || busy.value || type === approverType.value) return
  if (props.context === 'platform' && type !== 'PERSON') return
  graphCanvas.value?.checkpoint()
  if (type === 'PERSON') selectedNode.value.assigneePolicy = personPolicy()
  else if (['DEPARTMENT', 'DEPARTMENT_MANAGER'].includes(type)) {
    selectedNode.value.assigneePolicy = { type, departmentIds: [], includeDescendants: true }
  } else if (type === 'PERSON_FIELD') selectedNode.value.assigneePolicy = { type, fieldCode: '' }
  else selectedNode.value.assigneePolicy = { type }
}

function setConfig(key: string, value: unknown) {
  if (!selectedNode.value) return
  selectedNode.value.config = { ...selectedNode.value.config, [key]: value }
}

function setWebhookCallback(value?: string) {
  if (!selectedNode.value || !isExternalNode(selectedNode.value.nodeType) || !can('DESIGN') || busy.value) return
  graphCanvas.value?.checkpoint()
  const selected = callbackCatalog.value.find(item => `${item.applicationId}:${item.callbackId}` === value)
  const config: Record<string, unknown> = {}
  if (selected) {
    config.applicationId = selected.applicationId
    config.callbackId = selected.callbackId
  }
  selectedNode.value.config = config
  publicationCheck.value = undefined
  simulation.value = undefined
}

function isExternalNode(type?: FlowNodeType) {
  return type === 'WEBHOOK' || type === 'EXTERNAL'
}

function setGatewayMode(value: GatewayMode) {
  const node = selectedNode.value
  if (!node || node.nodeType !== 'GATEWAY' || !can('DESIGN') || busy.value) return
  graphCanvas.value?.checkpoint()
  const oldMode = selectedGatewayMode.value
  const oldPairKey = oldMode === 'MERGE' ? 'splitNodeKey' : 'mergeNodeKey'
  const oldPair = String(node.config[oldPairKey] || '')
  if (oldPair) {
    const paired = nodes.value.find(candidate => candidate.nodeKey === oldPair)
    if (paired) {
      const reverse = oldMode === 'MERGE' ? 'mergeNodeKey' : 'splitNodeKey'
      if (paired.config[reverse] === node.nodeKey) {
        const next = { ...paired.config }; delete next[reverse]; paired.config = next
      }
    }
  }
  const config: Record<string, unknown> = { ...node.config, gatewayMode: value }
  delete config.mergeNodeKey; delete config.splitNodeKey
  node.config = config
  if (value === 'PARALLEL' || value === 'MERGE') {
    edges.value.filter(edge => edge.sourceNodeKey === node.nodeKey).forEach(edge => {
      const edgeConfig = { ...edge.config }; delete edgeConfig.condition; edge.config = edgeConfig
      delete edge.conditionExpression
    })
  }
  node.name = ({ EXCLUSIVE: '业务条件', PARALLEL: '并行处理', INCLUSIVE: '可选并行处理', MERGE: '分支汇合' })[value]
}

function setGatewayPair(value?: string) {
  const node = selectedNode.value
  if (!node || node.nodeType !== 'GATEWAY' || !can('DESIGN') || busy.value) return
  graphCanvas.value?.checkpoint()
  const pairKey = selectedGatewayMode.value === 'MERGE' ? 'splitNodeKey' : 'mergeNodeKey'
  const reverseKey = selectedGatewayMode.value === 'MERGE' ? 'mergeNodeKey' : 'splitNodeKey'
  const oldPair = String(node.config[pairKey] || '')
  if (oldPair) {
    const old = nodes.value.find(candidate => candidate.nodeKey === oldPair)
    if (old?.config[reverseKey] === node.nodeKey) {
      const next = { ...old.config }; delete next[reverseKey]; old.config = next
    }
  }
  const next = { ...node.config }
  if (value) next[pairKey] = value; else delete next[pairKey]
  node.config = next
  const paired = value ? nodes.value.find(candidate => candidate.nodeKey === value) : undefined
  if (paired) paired.config = { ...paired.config, [reverseKey]: node.nodeKey }
}

function setTimeoutPolicy(key: string, value: unknown) {
  if (!selectedNode.value || !can('DESIGN') || busy.value) return
  graphCanvas.value?.checkpoint()
  const policy = { ...(selectedNode.value.timeoutPolicy || {}) }
  // 明确修改期限或目标时才规范旧字段；读取和保存不静默删除旧配置。
  if (key === 'timeoutMinutes') delete policy.minutes
  if (['targetTenantMemberId', 'targetPlatformAccountId'].includes(key)) {
    for (const targetKey of ['targetTenantMemberId', 'targetPlatformAccountId', 'targetTenantMemberIds', 'targetPlatformAccountIds', 'targetIds']) delete policy[targetKey]
  }
  selectedNode.value.timeoutPolicy = { ...policy, [key]: value }
  publicationCheck.value = undefined; simulation.value = undefined
}

function setExceptionPolicy(key: string, value: unknown) {
  if (!selectedNode.value) return
  selectedNode.value.exceptionPolicy = { ...(selectedNode.value.exceptionPolicy || {}), [key]: value }
}

function commitAutomaticAssignments(assignments: AutomaticAssignment[]) {
  if (!selectedNode.value || !can('DESIGN') || busy.value) return
  graphCanvas.value?.checkpoint()
  selectedNode.value.config = { ...selectedNode.value.config, schemaVersion: 1,
    operation: automaticOperation.value, assignments }
  publicationCheck.value = undefined; simulation.value = undefined
}

function setAutomaticOperation(operation: 'CREATE' | 'UPDATE') {
  const node = selectedNode.value
  if (!node || node.nodeType !== 'DATA_ACTION' || !can('DESIGN') || busy.value) return
  graphCanvas.value?.checkpoint()
  const config: Record<string, unknown> = { ...node.config, schemaVersion: 1, operation }
  if (operation === 'CREATE') {
    delete config.targetRecordSource; delete config.recordIdField; delete config.recordNodeKey
    config.title = config.title || { source: 'FIXED', value: '' }
    node.name = '新建模块数据'
  } else {
    config.targetRecordSource = Number(config.moduleId) === businessModuleId.value ? 'CURRENT_RECORD' : 'BUSINESS_FIELD'
    delete config.title; node.name = '更新模块数据'
  }
  node.config = config; publicationCheck.value = undefined; simulation.value = undefined
  void loadAutomaticFields()
}

function setAutomaticModule(moduleId?: number) {
  const node = selectedNode.value
  if (!node || node.nodeType !== 'DATA_ACTION' || !can('DESIGN') || busy.value) return
  graphCanvas.value?.checkpoint()
  const config: Record<string, unknown> = { ...node.config, schemaVersion: 1, moduleId, assignments: [] }
  delete config.recordIdField; delete config.recordNodeKey
  if (automaticOperation.value === 'UPDATE') config.targetRecordSource = moduleId === businessModuleId.value
    ? 'CURRENT_RECORD' : 'BUSINESS_FIELD'
  node.config = config; publicationCheck.value = undefined; simulation.value = undefined
}

function setAutomaticTarget(source: 'CURRENT_RECORD' | 'BUSINESS_FIELD' | 'NODE_RESULT') {
  if (!selectedNode.value || !can('DESIGN') || busy.value) return
  graphCanvas.value?.checkpoint()
  const config: Record<string, unknown> = { ...selectedNode.value.config, targetRecordSource: source }
  delete config.recordIdField; delete config.recordNodeKey
  selectedNode.value.config = config; publicationCheck.value = undefined; simulation.value = undefined
}

function automaticTitle(): AutomaticValue {
  const title = selectedNode.value?.config.title
  if (!title || typeof title !== 'object' || Array.isArray(title)) return { source: 'FIXED', value: '' }
  const value = title as Record<string, unknown>
  return { source: value.source === 'VARIABLE' ? 'VARIABLE' : 'FIXED', value: value.value,
    variable: typeof value.variable === 'string' ? value.variable : undefined }
}
function setAutomaticTitle(source: 'FIXED' | 'VARIABLE', value?: unknown) {
  if (!selectedNode.value || !can('DESIGN') || busy.value) return
  const title = source === 'VARIABLE' ? { source, variable: String(value || '') } : { source, value: value ?? '' }
  setConfig('title', title)
}

function addAutomaticAssignment() {
  const assignments = automaticAssignments(), used = new Set(assignments.map(item => item.fieldCode))
  const field = automaticFields.value.find(item => item.editable && !used.has(item.code))
  if (!field) { message.warning('没有可继续添加的目标字段'); return }
  commitAutomaticAssignments([...assignments, { fieldCode: field.code, value: { source: 'FIXED', value: '' } }])
}
function setAutomaticAssignmentField(index: number, fieldCode: string) {
  const assignments = automaticAssignments()
  const current = assignments[index]
  if (!current) return
  if (assignments.some((item, current) => current !== index && item.fieldCode === fieldCode)) {
    message.warning('同一目标字段只能配置一次'); return
  }
  assignments[index] = { ...current, fieldCode }
  commitAutomaticAssignments(assignments)
}
function setAutomaticAssignmentSource(index: number, source: 'FIXED' | 'VARIABLE') {
  const assignments = automaticAssignments()
  const current = assignments[index]
  if (!current) return
  assignments[index] = { ...current, value: source === 'VARIABLE'
    ? { source, variable: automaticVariableOptions.value[0]?.value }
    : { source, value: '' } }
  commitAutomaticAssignments(assignments)
}
function setAutomaticAssignmentValue(index: number, value: unknown) {
  const assignments = automaticAssignments()
  const current = assignments[index]
  if (!current) return
  assignments[index] = { ...current, value: { ...current.value, value } }
  commitAutomaticAssignments(assignments)
}
function setAutomaticAssignmentVariable(index: number, variable?: string) {
  const assignments = automaticAssignments()
  const current = assignments[index]
  if (!current) return
  assignments[index] = { ...current, value: { source: 'VARIABLE', variable } }
  commitAutomaticAssignments(assignments)
}
function removeAutomaticAssignment(index: number) {
  commitAutomaticAssignments(automaticAssignments().filter((_, current) => current !== index))
}

function nodeIssues(key: string) {
  return localIssues.value.filter(issue => issue.location.includes(key))
}

async function saveDraft(silent = false) {
  if (!token.value || busy.value || !selected.value || !can('DESIGN')) return false
  const conditionIssue = edges.value.flatMap(edgeIssues)[0]
  if (conditionIssue) { message.warning(conditionIssue); return false }
  const context = requestContext()
  const draft = JSON.stringify([nodes.value, edges.value])
  busy.value = 'save'
  try {
    const detail = await api<FlowDefinitionView>(`/api/flows/${selected.value.id}/draft`, {
      method: 'PUT', body: JSON.stringify({ expectedVersion: selected.value.version, nodes: nodes.value, edges: edges.value }),
    }, context.token)
    if (!requestCurrent(context, true, true) || draft !== JSON.stringify([nodes.value, edges.value])) return false
    selected.value = detail
    nodes.value = detail.nodes.map(({ id: _id, version: _version, ...node }) => ({ ...node, config: node.config || {} }))
    edges.value = detail.edges.map(({ id: _id, version: _version, ...edge }) => ({ ...edge, config: edge.config || {} }))
    await refreshListOnly()
    if (!requestCurrent(context, true, true)) return false
    if (!silent) message.success('流程草稿已保存')
    return true
  } catch (error) {
    if (!requestCurrent(context, true, true)) return false
    message.error(describeError(error))
    return false
  } finally {
    if (requestCurrent(context, true, true)) busy.value = ''
  }
}

async function refreshListOnly() {
  if (!token.value) return
  const context = requestContext(), listRequest = ++listGeneration
  const result = await api<FlowDefinitionView[]>('/api/flows', {}, context.token)
  if (requestCurrent(context, true, true) && listRequest === listGeneration) flows.value = result
}

async function checkPublish() {
  if (!token.value || busy.value || !selected.value) return
  const context = requestContext()
  if (can('DESIGN') && !(await saveDraft(true)) || !requestCurrent(context, true, true)) return
  busy.value = 'check'
  try {
    const result = await api<FlowPublicationCheck>(
      `/api/flows/${context.flowId}/publication-check`, {}, context.token)
    if (!requestCurrent(context, true, true)) return
    publicationCheck.value = result
    publicationCheck.value.valid ? message.success('发布检查通过') : message.warning(`还有 ${publicationCheck.value.issues.length} 项需要处理`)
  } catch (error) {
    if (!requestCurrent(context, true, true)) return
    message.error(describeError(error))
  } finally {
    if (requestCurrent(context, true, true)) busy.value = ''
  }
}

async function simulateFlow() {
  if (!token.value || busy.value || !selected.value || !can('SIMULATE')) return
  const issue = edges.value.flatMap(edgeIssues)[0]
  if (issue) return message.warning(issue)
  const context = requestContext()
  if (can('DESIGN') && !(await saveDraft(true)) || !requestCurrent(context, true, true)) return
  const variables = testVariables(), sample = JSON.stringify(variables)
  const definition = JSON.stringify([nodes.value, edges.value])
  busy.value = 'simulate'
  try {
    const result = await api<FlowSimulationResult>(`/api/flows/${context.flowId}/simulate`, {
      method: 'POST', body: JSON.stringify({ variables }),
    }, context.token)
    if (!requestCurrent(context, true, true) || sample !== JSON.stringify(testVariables())
      || definition !== JSON.stringify([nodes.value, edges.value])) return
    simulation.value = result
    simulation.value.successful ? message.success('样例路径模拟通过') : message.warning('模拟未能到达结束节点')
  } catch (error) {
    if (!requestCurrent(context, true, true)) return
    message.error(describeError(error))
  } finally {
    if (requestCurrent(context, true, true)) busy.value = ''
  }
}

async function publishFlow() {
  if (!token.value || busy.value || !selected.value || !can('PUBLISH') || !publishForm.changeSummary.trim()) return
  const issue = edges.value.flatMap(edgeIssues)[0] || nodes.value.flatMap(nodePolicyIssues)[0]
  if (issue) return message.warning(issue)
  const context = requestContext()
  if (can('DESIGN') && !(await saveDraft(true)) || !requestCurrent(context, true, true) || !selected.value) return
  busy.value = 'publish'
  try {
    const result = await api<FlowPublicationResult>(`/api/flows/${selected.value.id}/publish`, {
      method: 'POST', body: JSON.stringify({
        expectedDraftRevision: selected.value.draftRevision,
        changeSummary: publishForm.changeSummary,
        simulationVariables: testVariables(),
      }),
    }, context.token)
    if (!requestCurrent(context, true, true)) return
    publishOpen.value = false
    publishForm.changeSummary = ''
    message.success(`流程${versionLabel(result.versionNumber)}已发布，新运行实例将使用该版本`)
    await loadFlows(selected.value.id)
  } catch (error) {
    if (!requestCurrent(context, true, true)) return
    message.error(describeError(error))
  } finally {
    if (requestCurrent(context, true, true)) busy.value = ''
  }
}

watch(businessModuleId, () => { void refreshBusinessFields() }, { flush: 'sync' })
watch(() => [selectedNodeKey.value, automaticModuleId.value, automaticOperation.value, token.value],
  () => { void loadAutomaticFields() }, { flush: 'sync' })
watch(referencedConditionFields, fields => {
  if (sampleRecordId.value !== undefined) useSample(sampleRecordId.value)
  else {
    const visible = new Set(fields.map(field => field.code))
    Object.keys(sampleValues).forEach(code => { if (!visible.has(code)) delete sampleValues[code] })
  }
}, { flush: 'sync' })
watch(publishOpen, (open, previous) => { if (!open && previous) clearSamples() }, { flush: 'sync' })
watch(() => [activeSection.value, props.context, token.value, identity.value], () => {
  generation++; listGeneration++; clearDesigner(); modules.value = []; catalogReady = false
  callbackCatalog.value = []; callbackCatalogError.value = ''; callbackCatalogLoading.value = false
  flows.value = []; loading.value = false; pageError.value = ''
  directory.value = { departments: [], people: [], permissionVersion: 0 }
  if (activeSection.value === 'designer' && can('VIEW')) void Promise.all([
    loadFlows(), loadDirectory(), loadModules(), loadCallbackCatalog(),
  ])
}, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => {
  active = false; generation++; clearDesigner(); modules.value = []; flows.value = []
  callbackCatalog.value = []
  directory.value = { departments: [], people: [], permissionVersion: 0 }
})
const formPersonFieldOptions = computed(() => nodeFormFields.value.filter(field => field.fieldType === 'MEMBER')
  .map(field => ({ value: field.code, label: field.name })))
const formPersonFieldUnavailable = computed(() => selectedNode.value?.nodeType === 'FORM_TASK'
  && approverType.value === 'PERSON_FIELD' && Boolean(personFieldCode.value)
  && nodeFieldsReady.value && !formPersonFieldOptions.value.some(field => field.value === personFieldCode.value))
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
          <a-empty v-if="!loading && !flows.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" :description="pageError ? '当前无法读取流程，请确认权限后重新进入。' : '尚未创建流程'" />
        </a-spin>
      </section>

      <template v-if="selected">
        <section class="panel-card flow-canvas-panel">
          <div class="flow-toolbar">
            <span><strong>{{ selected.name }}</strong><small>当前正在编辑草稿</small></span>
            <div>
              <a-badge :count="issueCount" :number-style="{ backgroundColor: issueCount ? '#ff4d4f' : '#52c41a' }"><a-button @click="checkPublish">发布检查</a-button></a-badge>
              <a-button v-if="can('DESIGN')" @click="autoLayout">自动布局</a-button>
              <a-button v-if="can('DESIGN')" :disabled="Boolean(busy) || fieldsLoading || nodeFieldsLoading" :loading="busy === 'save'" @click="saveDraft()">保存草稿</a-button>
              <a-button v-if="can('SIMULATE')" :disabled="Boolean(busy) || fieldsLoading" :loading="busy === 'simulate'" @click="simulateFlow">模拟路径</a-button>
              <a-button v-if="can('PUBLISH')" type="primary" :disabled="!!issueCount" @click="publishOpen = true">发布版本</a-button>
            </div>
          </div>
          <div v-if="can('DESIGN')" class="flow-palette">
            <button v-for="item in palette" :key="item.key" @click="addNode(item.type, item.gatewayMode)"><strong>{{ item.label }}</strong><small>{{ item.hint }}</small></button>
          </div>
          <a-alert v-if="context === 'platform'" type="info" show-icon message="业务字段条件需进入对应系统配置；平台画布和原有无条件流程继续保留。" />
          <a-form v-else layout="vertical">
            <a-form-item label="流程业务对象" extra="选择当前有权访问的模块，保存到开始节点；更换对象后须明确处理原有失效条件。">
              <a-select :value="businessModule?.moduleId" :disabled="!can('DESIGN') || !nodes.some(node => node.nodeType === 'START') || Boolean(busy)"
                :options="moduleOptions" show-search option-filter-prop="label" allow-clear placeholder="选择业务模块" @update:value="changeBusinessModule" />
            </a-form-item>
            <a-alert v-if="!businessModuleId" type="info" show-icon message="尚未选择业务对象；默认连线可以无条件，业务字段条件需先选择模块。" />
            <a-alert v-if="conditionError" type="warning" show-icon :message="conditionError" />
            <a-button v-if="businessModuleId" size="small" :loading="fieldsLoading || nodeFieldsLoading" :disabled="Boolean(busy)" @click="refreshBusinessFields">刷新业务字段与样本</a-button>
          </a-form>
          <section v-if="simulationFields.length" class="flow-sample-panel">
            <strong>模拟业务数据</strong>
            <a-select :value="sampleRecordId" :loading="samplesLoading" :disabled="!fieldsReady || Boolean(busy)"
              :options="sampleRecords.map(record => ({ value: record.id, label: record.title || '未命名记录' }))"
              show-search option-filter-prop="label" allow-clear placeholder="可选：使用当前自有记录作为样本" style="width:100%" @update:value="useSample" />
            <a-alert v-if="sampleError" type="info" show-icon :message="sampleError" />
            <details class="flow-sample-values">
              <summary>编辑模拟值（{{ simulationFields.length }} 个字段）</summary>
              <p>只填写条件使用的业务字段；未填写表示空值，样本仅使用当前可读数据，不保存到浏览器缓存。</p>
            <fieldset :disabled="!fieldsReady || Boolean(busy)">
              <a-form layout="vertical"><a-form-item v-for="field in simulationFields" :key="field.code" :label="field.name">
                <ConditionValueInput :field="field" :model-value="sampleValues[field.code]" @update:model-value="setSampleValue(field.code, $event)" />
              </a-form-item></a-form>
            </fieldset>
            </details>
          </section>
          <FlowGraphCanvas ref="graphCanvas" v-model="nodes" :edges="edges" :issues="localIssues" :editable="can('DESIGN') && !busy" :condition-fields="conditionFields"
            @update:edges="edges = $event" @select-node="selectCanvasNode" @select-edge="selectCanvasEdge" />
          <div v-if="localIssues.length" class="flow-issue-panel">
            <strong>画布即时检查</strong>
            <div v-for="issue in localIssues" :key="`${issue.code}:${issue.location}`"><a-tag color="red">需处理</a-tag><span>{{ issue.message }}</span></div>
          </div>
          <div v-if="simulation" :class="['flow-simulation', { passed: simulation.successful }]">
            <div><strong>样例模拟{{ simulation.successful ? '通过' : '未通过' }}</strong><span>根据当前业务字段样本判断路径</span></div>
            <ol><li v-for="step in simulation.steps" :key="step.nodeKey"><span>{{ simulationStepName(step.nodeKey) }}</span><small>{{ simulationStepType(step.nodeKey, step.nodeType) }}</small></li></ol>
          </div>
        </section>

        <aside class="panel-card flow-inspector">
          <div class="panel-title"><strong>{{ selectedEdge ? '连线规则' : '节点属性' }}</strong><a-tag v-if="selectedNode">{{ nodeTypeLabel(selectedNode.nodeType, selectedNode) }}</a-tag></div>
          <a-form v-if="selectedEdge" layout="vertical">
            <a-form-item label="连接路径"><a-input :value="`${nodes.find(item => item.nodeKey === selectedEdge?.sourceNodeKey)?.name || '上一步'} → ${nodes.find(item => item.nodeKey === selectedEdge?.targetNodeKey)?.name || '下一步'}`" disabled /></a-form-item>
            <a-form-item v-if="selectedEdgeAllowsCondition" label="命中条件" extra="默认连线无需条件；选择真实业务字段和比较方式，不填写表达式。旧条件仅在明确修改后替换。">
              <a-alert v-for="(issue, index) in edgeIssues(selectedEdge)" :key="index" type="warning" show-icon :message="issue" />
              <fieldset :disabled="!can('DESIGN') || !fieldsReady || fieldsLoading || Boolean(busy)">
                <ConditionGroupEditor v-model="selectedCondition" :fields="conditionFields" />
              </fieldset>
            </a-form-item>
            <a-alert v-else type="info" show-icon :message="selectedEdgeSourceMode === 'PARALLEL' ? '并行网关会执行全部出线，无需配置条件。' : '汇合网关只有一条继续路径，无需配置条件。'" style="margin-bottom:16px" />
            <a-form-item label="判断顺序"><a-input-number :value="selectedEdge.priorityOrder" :min="0" style="width:100%" @focus="graphCanvas?.checkpoint()" @update:value="updateEdgePriority" /></a-form-item>
            <a-button v-if="can('DESIGN')" danger block @click="removeSelectedEdge">删除这条连线</a-button>
          </a-form>
          <a-form v-else-if="selectedNode" layout="vertical">
            <a-form-item label="节点名称"><a-input v-model:value="selectedNode.name" @focus="graphCanvas?.checkpoint()" /></a-form-item>
            <template v-if="selectedNode.nodeType === 'START'">
              <a-form-item label="允许运行中添加审批或抄送"
                extra="未配置时按权限允许；关闭后禁止运行中添加，包括前置加签。发布仅约束新发起实例，在途实例沿用发起时规则。">
                <a-switch v-model:checked="allowRuntimeNodes" :disabled="!can('DESIGN') || Boolean(busy)" checked-children="允许" un-checked-children="禁用" />
              </a-form-item>
              <a-alert v-if="runtimeRulesWarning" type="warning" show-icon :message="runtimeRulesWarning" />
            </template>
            <a-form-item v-if="edges.some(edge => edge.sourceNodeKey === selectedNode?.nodeKey)" label="已连接的路径" extra="选择路径配置条件和判断顺序。">
              <div class="flow-path-options">
                <a-button v-for="edge in edges.filter(item => item.sourceNodeKey === selectedNode?.nodeKey)" :key="edge.edgeKey" block
                  @click="selectCanvasEdge(edge.edgeKey)">{{ nodes.find(item => item.nodeKey === edge.targetNodeKey)?.name || '下一步' }} · {{ edge.config.condition || edge.conditionExpression ? '条件路径' : '默认路径' }}</a-button>
              </div>
            </a-form-item>
            <a-form-item v-if="can('DESIGN') && selectedNode.nodeType !== 'END'" label="连接下一步" extra="可从画布端点拖线，也可以在这里选择下一步，便于键盘操作。">
              <a-input-group compact>
                <a-select v-model:value="connectionTargetKey" style="width:calc(100% - 88px)" :options="connectionTargets" placeholder="选择下一步节点" />
                <a-button type="primary" :disabled="!connectionTargetKey" style="width:88px" @click="connectSelectedNode">连接</a-button>
              </a-input-group>
            </a-form-item>
            <template v-if="['APPROVAL', 'FORM_TASK'].includes(selectedNode.nodeType)">
              <a-form-item label="允许处理人执行的操作" required extra="至少选择一种。保存发布后随流程版本冻结，不改变已经发起的实例。">
                <a-checkbox-group v-model:value="manualAllowedActions" :options="manualActionOptions" :disabled="!can('DESIGN') || Boolean(busy)" />
              </a-form-item>
              <a-form-item label="必须填写意见的操作" extra="拒绝、退回和委托启用时必须填写意见；未启用的操作不能设置意见要求。">
                <a-checkbox-group v-model:value="manualOpinionActions" :options="manualOpinionOptions" :disabled="!can('DESIGN') || Boolean(busy)" />
              </a-form-item>
              <a-form-item label="处理前必须领取" extra="仅适用于或签，单人也可启用。候选人员先领取同一待办再处理；取消领取恢复当前候选，不重置处理时限。">
                <a-switch v-model:checked="manualRequireClaim" :disabled="!can('DESIGN') || Boolean(busy) || (!manualRequireClaim && approvalMode !== 'OR_SIGN')" checked-children="必须领取" un-checked-children="直接处理" />
              </a-form-item>
              <a-alert v-if="manualOperationIssues(selectedNode).length" type="warning" show-icon :message="manualOperationIssues(selectedNode).join('；')" style="margin-bottom:16px" />
              <a-alert type="info" show-icon message="委托只允许原审批人为当前任务选择一名限时代办人；加签、减签调整同一节点参与人；新增节点使用运行页的“添加审批或抄送”。" style="margin-bottom:16px" />
              <a-form-item label="审批附件" extra="开启后，处理人必须先在运行页上传至少一个已通过安全扫描的当前流程附件，才能同意或完成填写。">
                <a-switch v-model:checked="approvalAttachmentRequired" :disabled="!can('DESIGN') || Boolean(busy)" checked-children="必须上传" un-checked-children="不要求" />
              </a-form-item>
              <a-alert v-if="attachmentPolicyIssues(selectedNode).length" type="warning" show-icon :message="attachmentPolicyIssues(selectedNode).join('；')" style="margin-bottom:16px" />
              <template v-if="manualAllowedActions.includes('RETURN')">
                <a-form-item label="退回目标" required extra="实际业务内容统一由原发起人修正；上一节点或指定节点决定重新提交时可返回的位置。">
                  <a-select v-model:value="selectedReturnTarget" :disabled="!can('DESIGN') || Boolean(busy)" :options="[
                    { value: 'STARTER', label: '退回发起人' },
                    { value: 'PREVIOUS', label: '退回上一人工节点', disabled: !priorHumanNodes(selectedNode).length },
                    { value: 'SPECIFIED', label: '退回指定前序节点', disabled: !returnNodeOptions.length },
                  ]" />
                </a-form-item>
                <a-form-item v-if="selectedReturnTarget === 'SPECIFIED'" label="指定退回节点" required extra="只能选择当前节点之前、且能沿流程路径到达当前节点的审批或填写节点。">
                  <a-select v-model:value="selectedReturnNodeKey" :options="returnNodeOptions" show-search option-filter-prop="label"
                    :disabled="!can('DESIGN') || Boolean(busy)" placeholder="选择前序人工节点" />
                </a-form-item>
                <a-form-item label="重新提交后" required>
                  <a-segmented v-model:value="selectedResubmitRoute" :disabled="!can('DESIGN') || Boolean(busy)" :options="[
                    { value: 'FROM_START', label: '从头重新执行' },
                    { value: 'RETURN_NODE', label: '回到退回节点继续' },
                  ]" block />
                </a-form-item>
                <a-alert v-if="returnPolicyIssues(selectedNode).length" type="warning" show-icon :message="returnPolicyIssues(selectedNode).join('；')" style="margin-bottom:16px" />
              </template>
              <a-form-item v-if="selectedNode.nodeType === 'APPROVAL'" label="审批方式" required>
                <a-segmented v-model:value="approvalMode" :options="[
                  { value: 'OR_SIGN', label: '或签' },
                  { value: 'ALL_SIGN', label: '会签' },
                  { value: 'SEQUENTIAL', label: '顺序会签' },
                ]" block />
              </a-form-item>
              <template v-if="selectedNode.nodeType === 'APPROVAL' && approvalMode === 'ALL_SIGN'">
                <a-form-item label="会签完成条件" required extra="发布后随版本冻结；加减签会按当前有效参与人数重新计算分母。">
                  <a-segmented v-model:value="allSignCompletionMode" :options="[
                    { value: 'ALL', label: '全部同意' },
                    { value: 'RATIO', label: '达到比例' },
                  ]" :disabled="!can('DESIGN') || Boolean(busy)" />
                </a-form-item>
                <a-form-item v-if="allSignCompletionMode === 'RATIO'" label="通过比例" required extra="按已同意人数 ÷ 当前有效参与人数计算，结果向上取整。">
                  <a-input-number v-model:value="allSignRatioPercent" :min="1" :max="100" :precision="0" :disabled="!can('DESIGN') || Boolean(busy)" /> %
                </a-form-item>
              </template>
              <template v-if="selectedNode.nodeType === 'APPROVAL'">
                <a-form-item label="同一人已审批后，后续自动同意" extra="默认关闭。仅引用本次流程中所选前序节点的真实人工同意，仍须核验当前人员、候选内容及授权。">
                  <a-switch v-model:checked="repeatEnabled" :disabled="!can('DESIGN') || Boolean(busy)" checked-children="开启" un-checked-children="关闭" />
                </a-form-item>
                <template v-if="repeatEnabled">
                  <a-form-item label="适用前序审批范围" required extra="只能选择已连线可到达当前节点的前序审批；不能选择自身、后续或填写节点。">
                    <a-select v-model:value="repeatSourceKeys" mode="multiple" :options="repeatSourceOptions" show-search option-filter-prop="label"
                      :disabled="!can('DESIGN') || Boolean(busy)" placeholder="选择前序审批节点" />
                  </a-form-item>
                  <a-alert type="info" show-icon message="候选内容或流程变量变化后，需重新人工审批。"
                    description="此规则始终生效；附加业务条件不能替代未变化校验，也不会跳过当前必填及业务权限检查。" style="margin-bottom:16px" />
                  <a-form-item v-if="(context === 'system' && businessModuleId) || repeatHasCondition" label="附加业务条件（可选）"
                    extra="不需要时取消勾选；勾选后必须配置至少一项有效业务条件，空条件不能发布。">
                    <a-checkbox v-model:checked="repeatHasCondition" :disabled="!can('DESIGN') || Boolean(busy) || (!repeatHasCondition && !fieldsReady)">同时满足业务条件</a-checkbox>
                  </a-form-item>
                  <fieldset v-if="repeatHasCondition" :disabled="context !== 'system' || !can('DESIGN') || !fieldsReady || fieldsLoading || Boolean(busy)"
                    :inert="context !== 'system' || !can('DESIGN') || !fieldsReady || fieldsLoading || Boolean(busy)">
                    <ConditionGroupEditor v-model="selectedRepeatCondition" :fields="conditionFields" />
                  </fieldset>
                </template>
              </template>
              <a-form-item :label="selectedNode.nodeType === 'FORM_TASK' ? '填写人来源' : '审批人来源'" required>
                <a-select v-model:value="approverType" :options="approverTypeOptions" :disabled="!can('DESIGN') || Boolean(busy)" />
              </a-form-item>
              <a-form-item v-if="approverType === 'PERSON'" :label="selectedNode.nodeType === 'FORM_TASK' ? '填写人' : '处理人'" required :extra="participantHelp">
                <PersonSelect v-model="selectedApproverIds" :people="directory.people" :value-key="participantValueKey" :disabled="!can('DESIGN') || Boolean(busy)" multiple placeholder="搜索姓名、部门或岗位" />
              </a-form-item>
              <a-form-item v-if="['DEPARTMENT','DEPARTMENT_MANAGER'].includes(approverType)" label="部门" required>
                <a-select v-model:value="selectedDepartmentIds" mode="multiple" show-search option-filter-prop="label" :disabled="context === 'platform' || !can('DESIGN') || Boolean(busy)"
                  :options="directory.departments.map(item => ({ value: item.id, label: item.fullName }))" placeholder="选择部门" />
              </a-form-item>
              <a-form-item v-if="approverType === 'PERSON_FIELD'" :label="selectedNode.nodeType === 'FORM_TASK' ? '业务人员字段' : '人员字段编码'" required extra="运行时从关联业务记录的人员字段解析">
                <!-- 填写人只能选择真实授权成员字段，旧失效选择保留到明确重新选择。 -->
                <template v-if="selectedNode.nodeType === 'FORM_TASK'">
                  <a-select v-model:value="personFieldCode" :disabled="!nodeFieldsReady || nodeFieldsLoading || !can('DESIGN') || Boolean(busy)"
                    :options="personFieldCode && !formPersonFieldOptions.some(field => field.value === personFieldCode) ? [...formPersonFieldOptions, { value: personFieldCode, label: nodeFieldsReady ? '原人员字段已失效' : '人员字段暂不可用', disabled: true }] : formPersonFieldOptions"
                    placeholder="选择业务成员字段" />
                  <a-alert v-if="formPersonFieldUnavailable" type="warning" show-icon message="原人员字段已失效，请重新选择有效业务成员字段。" />
                </template>
                <a-input v-else v-model:value="personFieldCode" :disabled="context === 'platform' || !can('DESIGN') || Boolean(busy)" placeholder="例如 owner_assistant" />
              </a-form-item>
              <a-alert v-if="platformParticipantWarning" type="warning" show-icon :message="platformParticipantWarning" style="margin-bottom:16px" />
            </template>
            <FlowNodeFieldPolicyEditor v-if="context === 'system' && ['APPROVAL', 'FORM_TASK'].includes(selectedNode.nodeType)" :model-value="selectedNode.formPolicy"
              :fields="nodeFormFields" :ready="nodeFieldsReady" :loading="nodeFieldsLoading" :error="nodeFieldsError"
              :disabled="!can('DESIGN') || Boolean(busy)" @update:model-value="updateNodeFormPolicy" />
            <a-alert v-if="context === 'platform' && selectedNode.nodeType === 'APPROVAL'" type="info" show-icon
              message="平台审批在平台范围内运行；系统业务数据需通过已授权应用接入。" style="margin-bottom:16px" />
            <a-alert v-if="context === 'platform' && ['APPROVAL', 'FORM_TASK'].includes(selectedNode.nodeType) && Object.keys(selectedNode.formPolicy || {}).length" type="warning" show-icon
              message="此节点原有业务字段策略已保留；平台业务字段策略尚未接通，当前不能作为有效业务配置使用。" style="margin-bottom:16px" />
            <template v-if="['APPROVAL', 'FORM_TASK'].includes(selectedNode.nodeType)">
              <div class="form-grid">
                <a-form-item label="处理时限（分钟）" extra="留空表示不启用；可设置 1 至 525600 整数分钟。">
                  <a-input-number :value="timeoutMinutes" :min="1" :max="525600" :precision="0" :step="1" :disabled="!can('DESIGN') || Boolean(busy)" style="width:100%" placeholder="不启用时留空" @update:value="setTimeoutPolicy('timeoutMinutes', $event ?? null)" />
                </a-form-item>
                <a-form-item label="超时处理">
                  <a-select v-model:value="selectedTimeoutAction" :options="timeoutActionOptions" :disabled="!timeoutEnabled || !can('DESIGN') || Boolean(busy)" />
                </a-form-item>
              </div>
              <a-form-item v-if="selectedTimeoutAction === 'TRANSFER'" label="超时转交给" :required="timeoutEnabled" :extra="participantHelp">
                <PersonSelect v-model="timeoutTargetId" :people="directory.people" :value-key="participantValueKey" :disabled="!timeoutEnabled || !can('DESIGN') || Boolean(busy)" placeholder="选择一名有效处理人" />
              </a-form-item>
              <a-alert type="info" show-icon :message="timeoutHelp" style="margin-bottom:16px" />
              <a-alert v-for="warning in timeoutWarnings" :key="warning" type="warning" show-icon :message="warning" style="margin-bottom:16px" />
            </template>
            <a-alert v-if="selectedNode.nodeType === 'FORM_TASK' && timeoutEnabled && selectedTimeoutAction === 'AUTO_APPROVE'" type="warning" show-icon
              message="此填写节点仍含超时自动完成配置，不能绕过必填提交，当前不可发布；需先处理旧策略。" />
            <template v-if="selectedNode.nodeType === 'SUBFLOW'">
              <a-form-item label="目标子流程" required extra="只可选择当前平台、系统和租户内已发布的流程；发布后固定其版本。">
                <a-select :value="Number(selectedNode.config.flowId) || undefined" :options="subflowTargetOptions"
                  show-search option-filter-prop="label" placeholder="选择已发布子流程"
                  :disabled="!can('DESIGN') || Boolean(busy)" @change="setSubflowTarget" />
              </a-form-item>
              <a-form-item label="继承父流程变量" extra="开启后先复制全部父流程变量，再应用下面的重命名映射。">
                <a-switch v-model:checked="subflowInheritAll" :disabled="!can('DESIGN') || Boolean(busy)"
                  checked-children="继承" un-checked-children="仅映射" />
              </a-form-item>
              <a-form-item label="传入变量映射" extra="把父流程变量传给子流程；来源和目标使用变量标识，不需要填写 JSON。">
                <div class="flow-writeback-list">
                  <div v-for="(mapping, index) in subflowMappings(selectedNode, 'inputMappings')" :key="`subflow-input-${index}`">
                    <a-input :value="mapping.sourceKey" placeholder="父流程变量" @focus="graphCanvas?.checkpoint()"
                      @input="updateSubflowMapping('inputMappings', index, 'sourceKey', ($event.target as HTMLInputElement).value)" />
                    <a-input :value="mapping.targetKey" placeholder="子流程变量" @focus="graphCanvas?.checkpoint()"
                      @input="updateSubflowMapping('inputMappings', index, 'targetKey', ($event.target as HTMLInputElement).value)" />
                    <a-button danger @click="removeSubflowMapping('inputMappings', index)">移除</a-button>
                  </div>
                  <a-button block @click="addSubflowMapping('inputMappings')">添加传入映射</a-button>
                </div>
              </a-form-item>
              <a-form-item label="返回变量映射" extra="子流程成功后才按映射写回父流程，不会覆盖未选择的变量。">
                <div class="flow-writeback-list">
                  <div v-for="(mapping, index) in subflowMappings(selectedNode, 'outputMappings')" :key="`subflow-output-${index}`">
                    <a-input :value="mapping.sourceKey" placeholder="子流程变量" @focus="graphCanvas?.checkpoint()"
                      @input="updateSubflowMapping('outputMappings', index, 'sourceKey', ($event.target as HTMLInputElement).value)" />
                    <a-input :value="mapping.targetKey" placeholder="父流程变量" @focus="graphCanvas?.checkpoint()"
                      @input="updateSubflowMapping('outputMappings', index, 'targetKey', ($event.target as HTMLInputElement).value)" />
                    <a-button danger @click="removeSubflowMapping('outputMappings', index)">移除</a-button>
                  </div>
                  <a-button block @click="addSubflowMapping('outputMappings')">添加返回映射</a-button>
                </div>
              </a-form-item>
              <a-alert v-if="subflowIssues(selectedNode).length" type="warning" show-icon
                :message="subflowIssues(selectedNode).join('；')" style="margin-bottom:16px" />
              <a-alert v-else type="info" show-icon message="父流程会等待子流程完成；子流程异常可在其运行详情恢复，终止或拒绝会让父流程进入可处理异常。" style="margin-bottom:16px" />
            </template>
            <template v-if="selectedNode.nodeType === 'GATEWAY'">
              <a-form-item label="网关方式" required>
                <a-select :value="selectedGatewayMode" :disabled="!can('DESIGN') || Boolean(busy)"
                  :options="[{value:'EXCLUSIVE',label:'条件分支（只走一路）'},{value:'PARALLEL',label:'并行（全部执行）'},{value:'INCLUSIVE',label:'包容（命中的都执行）'},{value:'MERGE',label:'汇合（等待分支到齐）'}]"
                  @change="setGatewayMode" />
              </a-form-item>
              <a-form-item v-if="selectedGatewayMode === 'PARALLEL' || selectedGatewayMode === 'INCLUSIVE'" label="配对汇合网关" required
                extra="运行时只等待本次实际启动的分支；配对关系会保存到发布版本。">
                <a-select :value="selectedGatewayPair || undefined" :options="gatewayPairOptions" allow-clear placeholder="选择汇合网关"
                  :disabled="!can('DESIGN') || Boolean(busy)" @change="setGatewayPair" />
              </a-form-item>
              <a-form-item v-if="selectedGatewayMode === 'MERGE'" label="配对分支网关" required
                extra="汇合网关只放行一次，重复到达会被忽略并保留记录。">
                <a-select :value="selectedGatewayPair || undefined" :options="gatewayPairOptions" allow-clear placeholder="选择并行或包容网关"
                  :disabled="!can('DESIGN') || Boolean(busy)" @change="setGatewayPair" />
              </a-form-item>
              <a-alert v-if="selectedGatewayMode === 'EXCLUSIVE'" type="info" show-icon message="请点击每条出线配置条件和判断顺序；第一条命中的连线生效。" style="margin-bottom:16px" />
              <a-alert v-else-if="selectedGatewayMode === 'PARALLEL'" type="info" show-icon message="所有出线都会执行，请不要为出线配置条件。" style="margin-bottom:16px" />
              <a-alert v-else-if="selectedGatewayMode === 'INCLUSIVE'" type="info" show-icon message="所有命中条件的出线都会执行；必须保留一条无条件兜底出线。" style="margin-bottom:16px" />
              <a-alert v-else type="info" show-icon message="至少连接两条入线和一条无条件出线；系统会等待配对分支全部到达。" style="margin-bottom:16px" />
            </template>
            <template v-if="isExternalNode(selectedNode.nodeType)">
              <a-alert type="info" show-icon message="外部节点只选择当前工作空间已发布的 Webhook 应用回调。地址、签名密钥、超时和重试次数随应用版本冻结，不需要填写 JSON。" style="margin-bottom:16px" />
              <a-form-item label="已发布应用回调" required :validate-status="selectedCallbackKey && !selectedCallback ? 'warning' : undefined" :help="selectedCallbackKey && !selectedCallback ? '原选择已经失效，请重新选择后发布。' : callbackCatalogError">
                <a-select :value="selectedCallbackKey" :options="callbackOptions" :loading="callbackCatalogLoading" :disabled="!can('DESIGN') || Boolean(busy)" show-search option-filter-prop="label" placeholder="选择应用和业务事件回调" @update:value="setWebhookCallback" />
              </a-form-item>
              <a-descriptions v-if="selectedCallback" size="small" bordered :column="2" style="margin-bottom:16px">
                <a-descriptions-item label="应用版本">V{{ selectedCallback.applicationVersionNumber }}</a-descriptions-item>
                <a-descriptions-item label="回调类型">业务事件</a-descriptions-item>
                <a-descriptions-item label="响应超时">{{ selectedCallback.timeoutMillis }} 毫秒</a-descriptions-item>
                <a-descriptions-item label="最多投递">{{ selectedCallback.maxAttempts }} 次</a-descriptions-item>
              </a-descriptions>
            </template>
            <template v-if="selectedNode.nodeType === 'WAIT'">
              <a-alert type="info" show-icon message="流程会持久化等待，到期或后端重启后只继续一次。" style="margin-bottom:16px" />
              <div class="form-grid">
                <a-form-item label="等待数值" required><a-input-number :value="Number(selectedNode.config.durationValue || 1)" :min="1" :max="525600" :precision="0" style="width:100%" :disabled="!can('DESIGN') || Boolean(busy)" @update:value="setConfig('durationValue', $event)" /></a-form-item>
                <a-form-item label="时间单位" required><a-select :value="String(selectedNode.config.durationUnit || 'HOUR')" :options="[{value:'MINUTE',label:'分钟'},{value:'HOUR',label:'小时'},{value:'DAY',label:'天'}]" :disabled="!can('DESIGN') || Boolean(busy)" @update:value="setConfig('durationUnit', $event)" /></a-form-item>
              </div>
              <small>最长等待一年；发布后会冻结成标准分钟数，草稿后续修改不会影响运行中的实例。</small>
            </template>
            <template v-else-if="selectedNode.nodeType === 'TIMER'">
              <a-alert type="info" show-icon message="请选择明确的恢复日期和时间；到期前不会提前继续。" style="margin-bottom:16px" />
              <a-form-item label="恢复时间" required><a-date-picker :value="typeof selectedNode.config.resumeTime === 'string' ? selectedNode.config.resumeTime : undefined" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" :disabled="!can('DESIGN') || Boolean(busy)" placeholder="选择晚于当前时间的日期和时间" @update:value="setConfig('resumeTime', $event)" /></a-form-item>
            </template>
            <a-alert v-else-if="selectedNode.nodeType === 'WAIT_TIMER'" type="warning" show-icon message="这是旧版等待节点，系统仍会按已保存配置运行；新流程请分别使用等待或定时器节点。" style="margin-bottom:16px" />
            <template v-if="['CC', 'NOTIFICATION', 'MESSAGE'].includes(selectedNode.nodeType)">
              <a-alert v-if="platformParticipantWarning" type="warning" show-icon :message="platformParticipantWarning" style="margin-bottom:16px" />
              <a-alert type="info" show-icon :message="`${nodeTypeLabel(selectedNode.nodeType)}使用站内消息可靠投递；接收人点击后进入当前流程运行页。`" style="margin-bottom:16px" />
              <a-form-item :label="`${nodeTypeLabel(selectedNode.nodeType)}接收人`" required :extra="participantHelp"><PersonSelect v-model="selectedApproverIds" :people="directory.people" :value-key="participantValueKey" :disabled="!can('DESIGN') || Boolean(busy)" multiple placeholder="搜索姓名、部门或岗位" /></a-form-item>
              <a-form-item :label="`${nodeTypeLabel(selectedNode.nodeType)}标题`" required><a-input :value="selectedNode.config.subject" :maxlength="200" placeholder="填写接收人能够直接理解的标题" @input="setConfig('subject', ($event.target as HTMLInputElement).value)" /></a-form-item>
              <a-form-item label="标题附带流程名称"><a-switch :checked="selectedNode.config.appendInstanceTitle === true" @change="setConfig('appendInstanceTitle', $event)" /></a-form-item>
              <a-form-item :label="`${nodeTypeLabel(selectedNode.nodeType)}内容`" required extra="填写接收人需要了解的业务说明，不需要模板代码或 JSON。"><a-textarea :value="selectedNode.config.content" :maxlength="2000" show-count :rows="4" @input="setConfig('content', ($event.target as HTMLTextAreaElement).value)" /></a-form-item>
              <a-form-item label="打开位置"><a-select :value="String(selectedNode.config.destinationType || 'FLOW_INSTANCE')" :options="[{value:'FLOW_INSTANCE',label:'当前流程运行页'}]" disabled /></a-form-item>
            </template>
            <template v-if="['UPDATE_FIELD', 'DATA_ACTION'].includes(selectedNode.nodeType)">
              <a-alert type="info" show-icon message="此节点通过统一动作执行器写入，自动校验当前权限、字段类型、版本和重复执行；不需要填写动作代码或 JSON。" style="margin-bottom:16px" />
              <template v-if="selectedNode.nodeType === 'DATA_ACTION'">
                <a-form-item label="数据动作" required>
                  <a-segmented :value="automaticOperation" :options="[{value:'CREATE',label:'新建数据'},{value:'UPDATE',label:'更新数据'}]"
                    :disabled="!can('DESIGN') || Boolean(busy)" @update:value="setAutomaticOperation($event as 'CREATE' | 'UPDATE')" />
                </a-form-item>
                <a-form-item label="目标模块" required extra="只显示当前有相应新建或更新权限的已发布模块。">
                  <a-select :value="automaticModuleId" :options="automaticModuleOptions" show-search option-filter-prop="label"
                    :disabled="!can('DESIGN') || Boolean(busy)" placeholder="选择目标模块" @update:value="setAutomaticModule" />
                </a-form-item>
              </template>
              <a-form-item v-else label="目标模块"><a-input :value="businessModule?.moduleName || '请先为开始节点选择业务模块'" disabled /></a-form-item>
              <template v-if="automaticOperation === 'UPDATE' && selectedNode.nodeType === 'DATA_ACTION'">
                <a-form-item label="目标记录来源" required extra="选择当前业务记录、业务引用字段或前序数据节点的结果，不填写记录 ID。">
                  <a-select :value="selectedNode.config.targetRecordSource || (automaticModuleId === businessModuleId ? 'CURRENT_RECORD' : 'BUSINESS_FIELD')"
                    :options="[{value:'CURRENT_RECORD',label:'当前业务记录',disabled:automaticModuleId !== businessModuleId},{value:'BUSINESS_FIELD',label:'业务引用字段',disabled:!automaticReferenceOptions.length},{value:'NODE_RESULT',label:'前序数据节点结果',disabled:!automaticPriorNodeOptions.length}]"
                    @update:value="setAutomaticTarget" />
                </a-form-item>
                <a-form-item v-if="selectedNode.config.targetRecordSource === 'BUSINESS_FIELD'" label="引用记录字段" required>
                  <a-select :value="selectedNode.config.recordIdField" :options="automaticReferenceOptions" placeholder="选择引用目标模块的字段" @update:value="setConfig('recordIdField', $event)" />
                </a-form-item>
                <a-form-item v-if="selectedNode.config.targetRecordSource === 'NODE_RESULT'" label="前序数据节点" required>
                  <a-select :value="selectedNode.config.recordNodeKey" :options="automaticPriorNodeOptions" placeholder="选择已创建或更新记录的节点" @update:value="setConfig('recordNodeKey', $event)" />
                </a-form-item>
              </template>
              <template v-if="automaticOperation === 'CREATE'">
                <a-form-item label="新记录标题来源" required>
                  <a-segmented :value="automaticTitle().source" :options="[{value:'FIXED',label:'固定标题'},{value:'VARIABLE',label:'来自业务字段'}]"
                    @update:value="setAutomaticTitle($event as 'FIXED' | 'VARIABLE')" />
                </a-form-item>
                <a-form-item label="新记录标题" required>
                  <a-select v-if="automaticTitle().source === 'VARIABLE'" :value="automaticTitle().variable" :options="automaticVariableOptions"
                    show-search option-filter-prop="label" placeholder="选择业务字段" @update:value="setAutomaticTitle('VARIABLE', $event)" />
                  <a-input v-else :value="String(automaticTitle().value ?? '')" placeholder="输入新记录标题"
                    @input="setAutomaticTitle('FIXED', ($event.target as HTMLInputElement).value)" />
                </a-form-item>
              </template>
              <a-alert v-if="automaticFieldsError" type="warning" show-icon :message="automaticFieldsError" style="margin-bottom:16px" />
              <a-form-item label="字段赋值" required extra="目标字段从已发布模块读取；每个值选择固定值或已有业务字段，运行时仍按字段权限和类型校验。">
                <div class="flow-writeback-list">
                  <div v-for="(assignment, index) in automaticAssignments()" :key="`${assignment.fieldCode}-${index}`">
                    <a-select :value="assignment.fieldCode" :options="automaticFieldOptions" show-search option-filter-prop="label"
                      placeholder="选择目标字段" @update:value="setAutomaticAssignmentField(index, $event)" />
                    <a-segmented :value="assignment.value.source" :options="[{value:'FIXED',label:'固定值'},{value:'VARIABLE',label:'业务字段'}]"
                      @update:value="setAutomaticAssignmentSource(index, $event as 'FIXED' | 'VARIABLE')" />
                    <a-select v-if="assignment.value.source === 'VARIABLE'" :value="assignment.value.variable" :options="automaticVariableOptions"
                      show-search option-filter-prop="label" placeholder="选择来源字段" @update:value="setAutomaticAssignmentVariable(index, $event)" />
                    <ConditionValueInput v-else :field="automaticConditionField(assignment.fieldCode)" :model-value="assignment.value.value"
                      @update:model-value="setAutomaticAssignmentValue(index, $event)" />
                    <a-button danger @click="removeAutomaticAssignment(index)">移除</a-button>
                  </div>
                  <a-button block :loading="automaticFieldsLoading" :disabled="!automaticFieldOptions.length" @click="addAutomaticAssignment">添加字段赋值</a-button>
                </div>
              </a-form-item>
            </template>
            <template v-if="selectedNode.nodeType === 'AI'">
              <a-form-item label="受控模型" required><a-input :value="selectedNode.config.model" @input="setConfig('model', ($event.target as HTMLInputElement).value)" /></a-form-item>
              <a-form-item label="提示模板" required><a-textarea :value="selectedNode.config.prompt" @input="setConfig('prompt', ($event.target as HTMLTextAreaElement).value)" /></a-form-item>
            </template>
            <template v-if="['WEBHOOK','AI','UPDATE_FIELD','DATA_ACTION','CC','NOTIFICATION','MESSAGE'].includes(selectedNode.nodeType)">
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
    <a-form layout="vertical" style="margin-top:16px"><a-form-item label="变更摘要" required><a-input v-model:value="publishForm.changeSummary" placeholder="例如：首版合同审批" /></a-form-item>
      <a-form-item v-for="field in simulationFields" :key="field.code" :label="`${field.name} · 模拟值`">
        <fieldset :disabled="Boolean(busy)"><ConditionValueInput :field="field" :model-value="sampleValues[field.code]" @update:model-value="setSampleValue(field.code, $event)" /></fieldset>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<style scoped>
/* 按实际工作区宽度布局，系统侧栏不能挤出横向滚动的设计器。 */
.flow-designer-page { container-type: inline-size; min-width: 0; }
.flow-designer-layout { grid-template-columns: 210px minmax(0, 1fr) 360px; }
.flow-designer-layout > * { min-width: 0; }
.flow-inspector > .ant-form { display: block; }
.flow-sample-panel { display: grid; gap: 10px; padding: 12px 16px; border-bottom: 1px solid #e8edf4; }
.flow-sample-values summary { cursor: pointer; color: #326be6; }
.flow-sample-values p { margin: 10px 0; color: #7d899a; font-size: 12px; }
.flow-sample-values .ant-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 12px; }
.flow-sample-values .ant-form-item { min-width: 0; margin-bottom: 12px; }
.flow-path-options { display: grid; gap: 6px; }
@container (max-width: 1100px) {
  .flow-designer-layout { grid-template-columns: minmax(0, 1fr) 340px; }
  .flow-list-panel { grid-column: 1 / -1; max-height: 190px; overflow: auto; }
  .flow-list-panel :deep(.ant-spin-container) { display: grid; grid-template-columns: repeat(auto-fit, minmax(230px, 1fr)); }
  .flow-inspector { grid-column: auto; }
}
@container (max-width: 760px) {
  .flow-designer-layout { grid-template-columns: minmax(0, 1fr); }
  .flow-inspector { grid-column: 1; }
  .flow-sample-values .ant-form { grid-template-columns: minmax(0, 1fr); }
}
</style>
