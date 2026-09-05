<script setup lang="ts">
import { computed, ref, shallowRef, watch } from 'vue'
import {
  Handle, MarkerType, Position, VueFlow,
  type Connection, type Edge, type EdgeMouseEvent, type Node, type NodeDragEvent, type NodeMouseEvent,
} from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import '@vue-flow/minimap/dist/style.css'
import type { FlowEdgeInput, FlowIssue, FlowNodeInput, FlowNodeType } from '../types'

const props = defineProps<{
  modelValue: FlowNodeInput[]
  edges: FlowEdgeInput[]
  issues: FlowIssue[]
  editable: boolean
}>()
const emit = defineEmits<{
  'update:modelValue': [nodes: FlowNodeInput[]]
  'update:edges': [edges: FlowEdgeInput[]]
  'select-node': [nodeKey: string]
  'select-edge': [edgeKey: string]
}>()

interface GraphSnapshot { nodes: FlowNodeInput[]; edges: FlowEdgeInput[] }

const canvasNodes = shallowRef<Node[]>([])
const canvasEdges = shallowRef<Edge[]>([])
const undoStack = ref<GraphSnapshot[]>([])
const redoStack = ref<GraphSnapshot[]>([])
const restoring = ref(false)

const nodeLabels: Record<FlowNodeType, string> = {
  START: '开始', END: '结束', APPROVAL: '审批', GATEWAY: '条件', SUBFLOW: '子流程',
  FORM_TASK: '表单', NOTIFICATION: '通知', UPDATE_FIELD: '回写', WAIT_TIMER: '等待',
  WEBHOOK: '服务', EXTERNAL: '外部', AI: 'AI',
}

const canUndo = computed(() => undoStack.value.length > 0)
const canRedo = computed(() => redoStack.value.length > 0)

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value)) as T
}

function snapshot(): GraphSnapshot {
  return { nodes: clone(props.modelValue), edges: clone(props.edges) }
}

function same(left: GraphSnapshot, right: GraphSnapshot) {
  return JSON.stringify(left) === JSON.stringify(right)
}

function checkpoint() {
  if (restoring.value) return
  const current = snapshot()
  if (!undoStack.value.length || !same(undoStack.value[undoStack.value.length - 1]!, current)) {
    undoStack.value.push(current)
    if (undoStack.value.length > 50) undoStack.value.shift()
  }
  redoStack.value = []
}

function restore(target: GraphSnapshot) {
  restoring.value = true
  emit('update:modelValue', clone(target.nodes))
  emit('update:edges', clone(target.edges))
  queueMicrotask(() => { restoring.value = false })
}

function undo() {
  const target = undoStack.value.pop()
  if (!target) return
  redoStack.value.push(snapshot())
  restore(target)
}

function redo() {
  const target = redoStack.value.pop()
  if (!target) return
  undoStack.value.push(snapshot())
  restore(target)
}

function clearHistory() {
  undoStack.value = []
  redoStack.value = []
}

function domainNode(nodeKey: string) {
  return props.modelValue.find(node => node.nodeKey === nodeKey)
}

function domainEdge(edgeKey: string) {
  return props.edges.find(edge => edge.edgeKey === edgeKey)
}

function nodeIssues(nodeKey: string) {
  return props.issues.filter(issue => issue.location.includes(nodeKey))
}

function syncCanvas() {
  canvasNodes.value = props.modelValue.map(node => ({
    id: node.nodeKey,
    type: 'business',
    position: { x: Number(node.positionX), y: Number(node.positionY) },
    draggable: props.editable,
    selectable: true,
    connectable: props.editable,
    deletable: false,
  }))
  canvasEdges.value = props.edges.map(edge => ({
    id: edge.edgeKey,
    source: edge.sourceNodeKey,
    target: edge.targetNodeKey,
    type: 'smoothstep',
    label: edge.conditionExpression || (edge.priorityOrder ? `优先级 ${edge.priorityOrder}` : '继续'),
    markerEnd: MarkerType.ArrowClosed,
    animated: false,
    selectable: true,
    deletable: false,
  }))
}

watch(() => [props.modelValue, props.edges, props.editable], syncCanvas, { deep: true, immediate: true })

function selectNode(event: NodeMouseEvent) {
  emit('select-node', event.node.id)
}

function selectEdge(event: EdgeMouseEvent) {
  emit('select-edge', event.edge.id)
}

function dragStop(event: NodeDragEvent) {
  const positions = new Map(event.nodes.map(node => [node.id, node.position]))
  positions.set(event.node.id, event.node.position)
  emit('update:modelValue', props.modelValue.map(node => {
    const position = positions.get(node.nodeKey)
    return position ? { ...node, positionX: Math.round(position.x), positionY: Math.round(position.y) } : node
  }))
}

function connect(connection: Connection) {
  if (!props.editable || connection.source === connection.target) return
  if (props.edges.some(edge => edge.sourceNodeKey === connection.source
    && edge.targetNodeKey === connection.target)) return
  const source = domainNode(connection.source)
  const target = domainNode(connection.target)
  if (!source || !target || source.nodeType === 'END' || target.nodeType === 'START') return
  checkpoint()
  const suffix = Date.now().toString(36)
  emit('update:edges', [...props.edges, {
    edgeKey: `edge_${connection.source}_${connection.target}_${suffix}`,
    sourceNodeKey: connection.source,
    targetNodeKey: connection.target,
    priorityOrder: (props.edges.filter(edge => edge.sourceNodeKey === connection.source).length + 1) * 10,
    config: {},
  }])
}

function nodeClass(nodeKey: string) {
  const node = domainNode(nodeKey)
  return node ? [`flow-visual-node--${node.nodeType.toLowerCase()}`, { invalid: nodeIssues(nodeKey).length > 0 }] : []
}

defineExpose({ checkpoint, undo, redo, clearHistory })
</script>

<template>
  <div class="flow-graph-shell" data-testid="flow-canvas">
    <div class="flow-graph-history">
      <a-button size="small" :disabled="!canUndo" @click="undo">撤销</a-button>
      <a-button size="small" :disabled="!canRedo" @click="redo">重做</a-button>
      <span>拖动节点调整布局，从右侧端点拖线连接下一步；框选可批量移动。</span>
    </div>
    <VueFlow
      v-model:nodes="canvasNodes"
      v-model:edges="canvasEdges"
      class="flow-graph"
      :nodes-draggable="editable"
      :nodes-connectable="editable"
      :elements-selectable="true"
      :select-nodes-on-drag="false"
      :pan-on-drag="[1, 2]"
      :selection-key-code="true"
      :multi-selection-key-code="'Shift'"
      :delete-key-code="null"
      :min-zoom="0.35"
      :max-zoom="1.8"
      :fit-view-on-init="true"
      :default-edge-options="{ type: 'smoothstep', markerEnd: MarkerType.ArrowClosed }"
      @node-click="selectNode"
      @edge-click="selectEdge"
      @node-drag-start="checkpoint"
      @node-drag-stop="dragStop"
      @selection-drag-start="checkpoint"
      @selection-drag-stop="dragStop"
      @connect="connect"
    >
      <Background pattern-color="#d7dfeb" :gap="18" />
      <MiniMap pannable zoomable />
      <Controls />
      <template #node-business="{ id, selected }">
        <Handle v-if="domainNode(id)?.nodeType !== 'START'" type="target" :position="Position.Left" />
        <div :class="['flow-visual-node', ...nodeClass(id), { active: selected }]">
          <span>{{ domainNode(id) ? nodeLabels[domainNode(id)!.nodeType] : '节点' }}</span>
          <strong>{{ domainNode(id)?.name }}</strong>
          <em v-if="nodeIssues(id).length">{{ nodeIssues(id).length }} 项待处理</em>
        </div>
        <Handle v-if="domainNode(id)?.nodeType !== 'END'" type="source" :position="Position.Right" />
      </template>
    </VueFlow>
    <div v-if="!modelValue.length" class="flow-graph-empty"><a-empty description="从上方节点库添加开始、业务节点和结束节点，再在画布中连线" /></div>
  </div>
</template>
