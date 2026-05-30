<template>
  <AdminLayout>
    <div class="designer-head">
      <div>
        <h2>流程图设计器</h2>
        <p class="muted">模板 #{{ tempId }} · 版本 #{{ tempVerId }}</p>
      </div>
      <div class="toolbar">
        <button type="button" @click="save" :disabled="saving">保存</button>
        <button type="button" class="secondary" @click="reload">刷新</button>
        <button type="button" :class="{ secondary: mode !== 'select' }" @click="mode = 'select'">选择</button>
        <button type="button" :class="{ secondary: mode !== 'connect' }" @click="mode = 'connect'">连线</button>
        <button type="button" class="secondary" @click="deleteSelected">删除选中</button>
        <button type="button" class="secondary" @click="publish" :disabled="saving">发布</button>
        <router-link class="btn secondary" :to="`/flow/temps/${tempId}`">返回</router-link>
      </div>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="hint" class="hint">{{ hint }}</p>

    <div class="designer-body">
      <aside class="palette">
        <p class="palette__title">节点</p>
        <button v-for="t in nodeTypes" :key="t.type" type="button" @click="addNode(t)">{{ t.label }}</button>
      </aside>

      <div class="canvas-wrap" ref="canvasWrap">
        <svg class="canvas" :width="canvasW" :height="canvasH">
          <defs>
            <marker id="arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
              <path d="M0,0 L6,3 L0,6 z" fill="#64748b" />
            </marker>
          </defs>
          <g>
            <line
              v-for="e in edges"
              :key="edgeKey(e)"
              :x1="nodeCenter(e.fromNodeKey).x"
              :y1="nodeCenter(e.fromNodeKey).y"
              :x2="nodeCenter(e.toNodeKey).x"
              :y2="nodeCenter(e.toNodeKey).y"
              class="edge-line"
              :class="{ selected: selectedEdge === edgeKey(e) }"
              marker-end="url(#arrow)"
              @click.stop="selectEdge(e)"
            />
            <text
              v-for="e in edges"
              :key="'t-' + edgeKey(e)"
              :x="(nodeCenter(e.fromNodeKey).x + nodeCenter(e.toNodeKey).x) / 2"
              :y="(nodeCenter(e.fromNodeKey).y + nodeCenter(e.toNodeKey).y) / 2 - 6"
              class="edge-label"
            >
              {{ e.cond || '' }}
            </text>
          </g>
        </svg>
        <div
          v-for="n in nodes"
          :key="n.nodeKey"
          class="node"
          :class="[n.nodeType, { selected: selectedNodeKey === n.nodeKey, connectFrom: connectFrom === n.nodeKey }]"
          :style="{ left: n.x + 'px', top: n.y + 'px' }"
          @mousedown.stop="onNodeDown(n, $event)"
          @click.stop="onNodeClick(n)"
        >
          <div class="node__type">{{ n.nodeType }}</div>
          <div class="node__name">{{ n.nodeName || n.nodeKey }}</div>
        </div>
      </div>

      <aside class="props">
        <h3>属性</h3>
        <template v-if="selectedNode">
          <label>nodeKey<input v-model="selectedNode.nodeKey" /></label>
          <label>类型<input v-model="selectedNode.nodeType" /></label>
          <label>名称<input v-model="selectedNode.nodeName" /></label>
          <label>configJson<textarea v-model="selectedNode.configJson" rows="5" /></label>
        </template>
        <template v-else-if="selectedEdgeObj">
          <label>from<input v-model="selectedEdgeObj.fromNodeKey" readonly /></label>
          <label>to<input v-model="selectedEdgeObj.toNodeKey" readonly /></label>
          <label>priority<input v-model.number="selectedEdgeObj.priority" type="number" /></label>
          <label>条件 cond<input v-model="selectedEdgeObj.cond" placeholder='eq(amount, 100)' /></label>
        </template>
        <p v-else class="muted">选中节点或连线编辑属性</p>
      </aside>
    </div>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { loadGraphDesigner, publishTempVer, saveGraphDesigner } from '../api/flow.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const tempId = computed(() => String(route.params.tempId || ''))
const tempVerId = computed(() => String(route.params.tempVerId || ''))

const nodes = ref([])
const edges = ref([])
const error = ref('')
const hint = ref('')
const saving = ref(false)
const mode = ref('select')
const connectFrom = ref('')
const selectedNodeKey = ref('')
const selectedEdge = ref('')
const drag = ref(null)
const canvasWrap = ref(null)

const canvasW = 2400
const canvasH = 1600
const NODE_W = 128
const NODE_H = 52

const nodeTypes = [
  { type: 'start', label: '+ 开始' },
  { type: 'approve', label: '+ 审批' },
  { type: 'cc', label: '+ 抄送' },
  { type: 'subflow', label: '+ 子流程' },
  { type: 'end', label: '+ 结束' }
]

const selectedNode = computed(() => nodes.value.find((n) => n.nodeKey === selectedNodeKey.value) || null)
const selectedEdgeObj = computed(() => {
  if (!selectedEdge.value) return null
  const [from, to] = selectedEdge.value.split('->')
  return edges.value.find((e) => e.fromNodeKey === from && e.toNodeKey === to) || null
})

function edgeKey(e) {
  return `${e.fromNodeKey}->${e.toNodeKey}`
}

function nodeCenter(key) {
  const n = nodes.value.find((x) => x.nodeKey === key)
  if (!n) return { x: 0, y: 0 }
  return { x: n.x + NODE_W / 2, y: n.y + NODE_H / 2 }
}

function selectEdge(e) {
  selectedEdge.value = edgeKey(e)
  selectedNodeKey.value = ''
}

function selectNode(n) {
  selectedNodeKey.value = n.nodeKey
  selectedEdge.value = ''
}

function onNodeClick(n) {
  if (mode.value === 'connect') {
    if (!connectFrom.value) {
      connectFrom.value = n.nodeKey
      hint.value = `从 ${n.nodeKey} 连线，请点击目标节点`
      return
    }
    if (connectFrom.value === n.nodeKey) {
      connectFrom.value = ''
      hint.value = ''
      return
    }
    const exists = edges.value.some((e) => e.fromNodeKey === connectFrom.value && e.toNodeKey === n.nodeKey)
    if (!exists) {
      edges.value.push({
        fromNodeKey: connectFrom.value,
        toNodeKey: n.nodeKey,
        priority: edges.value.length,
        isDefault: 0,
        cond: ''
      })
    }
    connectFrom.value = ''
    hint.value = ''
    return
  }
  selectNode(n)
}

function onNodeDown(n, ev) {
  if (mode.value === 'connect') {
    onNodeClick(n)
    return
  }
  selectNode(n)
  drag.value = {
    key: n.nodeKey,
    ox: ev.clientX,
    oy: ev.clientY,
    startX: n.x,
    startY: n.y
  }
}

function onMouseMove(ev) {
  if (!drag.value) return
  const n = nodes.value.find((x) => x.nodeKey === drag.value.key)
  if (!n) return
  n.x = Math.max(0, drag.value.startX + (ev.clientX - drag.value.ox))
  n.y = Math.max(0, drag.value.startY + (ev.clientY - drag.value.oy))
}

function onMouseUp() {
  drag.value = null
}

function addNode(t) {
  const key = `${t.type}_${nodes.value.length + 1}`
  nodes.value.push({
    nodeKey: key,
    nodeType: t.type,
    nodeName: t.label.replace('+ ', ''),
    x: 120 + (nodes.value.length % 3) * 200,
    y: 120 + Math.floor(nodes.value.length / 3) * 120,
    configJson: t.type === 'subflow' ? '{"sub_temp_code":"demo_subflow_child"}' : '{}'
  })
  selectNode(nodes.value[nodes.value.length - 1])
}

function deleteSelected() {
  if (selectedEdge.value) {
    const [from, to] = selectedEdge.value.split('->')
    edges.value = edges.value.filter((e) => !(e.fromNodeKey === from && e.toNodeKey === to))
    selectedEdge.value = ''
    return
  }
  if (selectedNodeKey.value) {
    const k = selectedNodeKey.value
    nodes.value = nodes.value.filter((n) => n.nodeKey !== k)
    edges.value = edges.value.filter((e) => e.fromNodeKey !== k && e.toNodeKey !== k)
    selectedNodeKey.value = ''
  }
}

async function reload() {
  error.value = ''
  hint.value = ''
  try {
    const r = await loadGraphDesigner(tempVerId.value)
    const data = r.data || {}
    nodes.value = (data.nodes || []).map((n) => ({
      ...n,
      x: Number(n.x) || 0,
      y: Number(n.y) || 0,
      configJson: n.configJson || '{}'
    }))
    edges.value = (data.edges || []).map((e) => ({
      fromNodeKey: e.fromNodeKey,
      toNodeKey: e.toNodeKey,
      priority: e.priority ?? 0,
      isDefault: e.isDefault ?? 0,
      cond: e.cond || ''
    }))
    if (!nodes.value.length) {
      addNode(nodeTypes[0])
      addNode(nodeTypes[1])
      addNode(nodeTypes[4])
      edges.value = [
        { fromNodeKey: nodes.value[0].nodeKey, toNodeKey: nodes.value[1].nodeKey, priority: 1, cond: '', isDefault: 0 },
        { fromNodeKey: nodes.value[1].nodeKey, toNodeKey: nodes.value[2].nodeKey, priority: 1, cond: '', isDefault: 0 }
      ]
    }
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function publish() {
  await save()
  if (error.value) return
  saving.value = true
  try {
    await publishTempVer(tempVerId.value)
    notify.success('已发布')
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    saving.value = false
  }
}

async function save() {
  saving.value = true
  error.value = ''
  try {
    await saveGraphDesigner(tempVerId.value, {
      nodes: nodes.value.map((n) => ({
        nodeKey: n.nodeKey,
        nodeType: n.nodeType,
        nodeName: n.nodeName,
        x: n.x,
        y: n.y,
        configJson: n.configJson || '{}'
      })),
      edges: edges.value
    })
    notify.success('已保存并生成 graphJson')
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  window.addEventListener('mousemove', onMouseMove)
  window.addEventListener('mouseup', onMouseUp)
  reload()
})
onUnmounted(() => {
  window.removeEventListener('mousemove', onMouseMove)
  window.removeEventListener('mouseup', onMouseUp)
})
</script>

<style scoped>
.designer-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 1rem;
  flex-wrap: wrap;
}
.designer-body {
  display: grid;
  grid-template-columns: 140px 1fr 260px;
  gap: 0.75rem;
  min-height: 70vh;
}
.palette {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 0.75rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.palette button {
  text-align: left;
  padding: 0.4rem 0.6rem;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #f9fafb;
  cursor: pointer;
}
.palette__title {
  font-weight: 600;
  margin: 0 0 0.25rem;
  font-size: 0.85rem;
}
.canvas-wrap {
  position: relative;
  overflow: auto;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  min-height: 520px;
}
.canvas {
  position: absolute;
  left: 0;
  top: 0;
  z-index: 0;
}
.edge-line {
  stroke: #94a3b8;
  stroke-width: 2;
  pointer-events: stroke;
}
.edge-line.selected {
  stroke: #1677ff;
  stroke-width: 3;
}
.edge-label {
  font-size: 11px;
  fill: #64748b;
  text-anchor: middle;
}
.node {
  position: absolute;
  z-index: 1;
  width: 128px;
  min-height: 52px;
  padding: 6px 8px;
  border-radius: 8px;
  border: 2px solid #cbd5e1;
  background: #fff;
  cursor: grab;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
  user-select: none;
}
.node.selected {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.25);
}
.node.connectFrom {
  border-color: #f59e0b;
}
.node.start {
  border-color: #22c55e;
}
.node.end {
  border-color: #ef4444;
}
.node.approve {
  border-color: #3b82f6;
}
.node.cc {
  border-color: #f97316;
}
.node.subflow {
  border-color: #8b5cf6;
}
.node__type {
  font-size: 10px;
  color: #64748b;
  text-transform: uppercase;
}
.node__name {
  font-size: 13px;
  font-weight: 600;
  margin-top: 2px;
}
.props {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 0.75rem;
  font-size: 0.88rem;
}
.props label {
  display: block;
  margin-bottom: 0.65rem;
}
.props input,
.props textarea {
  display: block;
  width: 100%;
  margin-top: 0.25rem;
  box-sizing: border-box;
  padding: 0.35rem;
  border: 1px solid #d1d5db;
  border-radius: 4px;
}
.muted {
  color: #666;
  font-size: 0.85rem;
}
.hint {
  color: #b45309;
  font-size: 0.9rem;
}
.error {
  color: #c00;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}
.toolbar button,
.toolbar .btn {
  padding: 0.4rem 0.75rem;
  border-radius: 6px;
  border: none;
  background: #1677ff;
  color: #fff;
  cursor: pointer;
  text-decoration: none;
  font-size: 0.88rem;
}
.toolbar button.secondary,
.toolbar .btn.secondary {
  background: #fff;
  color: #333;
  border: 1px solid #d1d5db;
}
</style>
