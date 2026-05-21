<template>
  <Page title="流程图画布" subtitle="拖拽节点 · 点选连线 · 与 Web 同源 graph-designer API">
    <view class="u-card">
      <view v-if="!tempVerId" class="muted">缺少 tempVerId</view>
      <template v-else>
        <view v-if="error" class="err">{{ error }}</view>
        <view v-if="hint" class="hint-txt">{{ hint }}</view>

        <view class="toolbar">
          <uni-button size="mini" type="primary" :disabled="busy" @click="doSave(false)">保存</uni-button>
          <uni-button size="mini" :disabled="busy" @click="doReload">刷新</uni-button>
          <uni-button size="mini" :class="{ active: mode === 'select' }" @click="setMode('select')">选择</uni-button>
          <uni-button size="mini" :class="{ active: mode === 'connect' }" @click="setMode('connect')">连线</uni-button>
          <uni-button size="mini" @click="deleteSelected">删除</uni-button>
          <uni-button size="mini" @click="openProps">属性</uni-button>
        </view>

        <scroll-view scroll-x scroll-y class="palette-scroll">
          <view class="palette">
            <uni-button v-for="t in nodeTypes" :key="t.type" size="mini" @click="addNode(t)">{{ t.label }}</uni-button>
          </view>
        </scroll-view>

        <scroll-view scroll-x scroll-y class="canvas-scroll" :style="{ height: canvasViewH + 'px' }">
          <view class="canvas-inner" :style="{ width: canvasW + 'px', height: canvasH + 'px' }">
            <svg class="edge-svg" :width="canvasW" :height="canvasH">
              <defs>
                <marker id="arrowM" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto">
                  <path d="M0,0 L6,3 L0,6 z" fill="#64748b" />
                </marker>
              </defs>
              <line
                v-for="e in edges"
                :key="edgeKey(e)"
                :x1="nodeCenter(e.fromNodeKey).x"
                :y1="nodeCenter(e.fromNodeKey).y"
                :x2="nodeCenter(e.toNodeKey).x"
                :y2="nodeCenter(e.toNodeKey).y"
                class="edge-line"
                :class="{ selected: selectedEdge === edgeKey(e) }"
                marker-end="url(#arrowM)"
                @click.stop="selectEdge(e)"
              />
            </svg>
            <view
              v-for="n in nodes"
              :key="n.nodeKey"
              class="g-node"
              :class="[n.nodeType, { selected: selectedNodeKey === n.nodeKey, connectFrom: connectFrom === n.nodeKey }]"
              :style="{ left: n.x + 'px', top: n.y + 'px', width: NODE_W + 'px' }"
              @touchstart.stop="onNodeTouchStart(n, $event)"
              @touchmove.stop.prevent="onNodeTouchMove"
              @touchend.stop="onNodeTouchEnd"
              @click.stop="onNodeTap(n)"
            >
              <text class="g-node-type">{{ n.nodeType }}</text>
              <text class="g-node-name">{{ n.nodeName || n.nodeKey }}</text>
            </view>
          </view>
        </scroll-view>

        <ActionBar>
          <uni-button :disabled="busy" @click="goListEdit">列表编辑</uni-button>
          <uni-button :disabled="busy" @click="goPreview">预览</uni-button>
          <uni-button type="warn" :disabled="busy || publishing" @click="doPublish">发布</uni-button>
          <uni-button @click="back">返回</uni-button>
        </ActionBar>
      </template>
      <ErrorBlock :text="loadError" />
    </view>

    <uni-popup ref="propsRef" type="bottom">
      <view class="props-panel u-card">
        <text class="props-title">属性</text>
        <template v-if="selectedNode">
          <uni-forms labelPosition="top">
            <uni-forms-item label="nodeKey"><uni-easyinput v-model="selectedNode.nodeKey" /></uni-forms-item>
            <uni-forms-item label="nodeName"><uni-easyinput v-model="selectedNode.nodeName" /></uni-forms-item>
            <uni-forms-item label="nodeType"><uni-easyinput v-model="selectedNode.nodeType" /></uni-forms-item>
            <uni-forms-item label="configJson"><uni-easyinput v-model="selectedNode.configJson" type="textarea" :autoHeight="true" /></uni-forms-item>
          </uni-forms>
        </template>
        <template v-else-if="selectedEdgeObj">
          <uni-forms labelPosition="top">
            <uni-forms-item label="from"><uni-easyinput :value="selectedEdgeObj.fromNodeKey" disabled /></uni-forms-item>
            <uni-forms-item label="to"><uni-easyinput :value="selectedEdgeObj.toNodeKey" disabled /></uni-forms-item>
            <uni-forms-item label="cond"><uni-easyinput v-model="selectedEdgeObj.cond" /></uni-forms-item>
          </uni-forms>
        </template>
        <text v-else class="muted">选中节点或连线后编辑</text>
        <uni-button @click="closeProps">关闭</uni-button>
      </view>
    </uni-popup>
  </Page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { onMounted } from 'vue'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { loadGraphDesigner, publishTempVer, saveGraphDesigner } from '@/api/flow'

type DesNode = {
  nodeKey: string
  nodeType: string
  nodeName: string
  x: number
  y: number
  configJson: string
}

type DesEdge = {
  fromNodeKey: string
  toNodeKey: string
  priority: number
  isDefault: number
  cond: string
}

const canvasW = 1200
const canvasH = 800
const canvasViewH = 420
const NODE_W = 110
const NODE_H = 48

const tempVerId = ref(0)
const nodes = ref<DesNode[]>([])
const edges = ref<DesEdge[]>([])
const error = ref<string | null>(null)
const loadError = ref<string | null>(null)
const hint = ref('')
const busy = ref(false)
const publishing = ref(false)
const mode = ref<'select' | 'connect'>('select')
const connectFrom = ref('')
const selectedNodeKey = ref('')
const selectedEdge = ref('')
const propsRef = ref<any>(null)

const drag = ref<{
  key: string
  startX: number
  startY: number
  touchX: number
  touchY: number
  moved: boolean
} | null>(null)

const nodeTypes = [
  { type: 'start', label: '+开始' },
  { type: 'approve', label: '+审批' },
  { type: 'cc', label: '+抄送' },
  { type: 'subflow', label: '+子流程' },
  { type: 'end', label: '+结束' }
]

const selectedNode = computed(() => nodes.value.find((n) => n.nodeKey === selectedNodeKey.value) || null)
const selectedEdgeObj = computed(() => {
  if (!selectedEdge.value) return null
  const [from, to] = selectedEdge.value.split('->')
  return edges.value.find((e) => e.fromNodeKey === from && e.toNodeKey === to) || null
})

function edgeKey(e: DesEdge) {
  return `${e.fromNodeKey}->${e.toNodeKey}`
}

function nodeCenter(key: string) {
  const n = nodes.value.find((x) => x.nodeKey === key)
  if (!n) return { x: 0, y: 0 }
  return { x: n.x + NODE_W / 2, y: n.y + NODE_H / 2 }
}

function setMode(m: 'select' | 'connect') {
  mode.value = m
  connectFrom.value = ''
  hint.value = m === 'connect' ? '连线模式：先点源节点，再点目标节点' : ''
}

function selectEdge(e: DesEdge) {
  selectedEdge.value = edgeKey(e)
  selectedNodeKey.value = ''
}

function selectNode(n: DesNode) {
  selectedNodeKey.value = n.nodeKey
  selectedEdge.value = ''
}

function onNodeTap(n: DesNode) {
  if (drag.value?.moved) return
  if (mode.value === 'connect') {
    if (!connectFrom.value) {
      connectFrom.value = n.nodeKey
      hint.value = `从 ${n.nodeKey} 连线，请点目标节点`
      return
    }
    if (connectFrom.value === n.nodeKey) {
      connectFrom.value = ''
      hint.value = '连线模式：先点源节点，再点目标节点'
      return
    }
    const exists = edges.value.some((e) => e.fromNodeKey === connectFrom.value && e.toNodeKey === n.nodeKey)
    if (!exists) {
      edges.value.push({
        fromNodeKey: connectFrom.value,
        toNodeKey: n.nodeKey,
        priority: edges.value.length + 1,
        isDefault: 0,
        cond: ''
      })
    }
    connectFrom.value = ''
    hint.value = '连线模式：先点源节点，再点目标节点'
    return
  }
  selectNode(n)
}

function onNodeTouchStart(n: DesNode, ev: TouchEvent) {
  const t = ev.touches?.[0]
  if (!t) return
  selectNode(n)
  drag.value = {
    key: n.nodeKey,
    startX: n.x,
    startY: n.y,
    touchX: t.clientX,
    touchY: t.clientY,
    moved: false
  }
}

function onNodeTouchMove(ev: TouchEvent) {
  if (!drag.value) return
  const t = ev.touches?.[0]
  if (!t) return
  const n = nodes.value.find((x) => x.nodeKey === drag.value!.key)
  if (!n) return
  const dx = t.clientX - drag.value.touchX
  const dy = t.clientY - drag.value.touchY
  if (Math.abs(dx) > 4 || Math.abs(dy) > 4) drag.value.moved = true
  n.x = Math.max(0, Math.min(canvasW - NODE_W, drag.value.startX + dx))
  n.y = Math.max(0, Math.min(canvasH - NODE_H, drag.value.startY + dy))
}

function onNodeTouchEnd() {
  drag.value = null
}

function uniqKey(prefix: string) {
  let i = nodes.value.length + 1
  let k = `${prefix}_${i}`
  while (nodes.value.some((n) => n.nodeKey === k)) {
    i++
    k = `${prefix}_${i}`
  }
  return k
}

function addNode(t: { type: string; label: string }) {
  const label = t.label.replace('+', '')
  const i = nodes.value.length
  nodes.value.push({
    nodeKey: uniqKey(t.type),
    nodeType: t.type,
    nodeName: label,
    x: 80 + (i % 3) * 180,
    y: 80 + Math.floor(i / 3) * 100,
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

function seedMinimal() {
  if (nodes.value.length) return
  addNode({ type: 'start', label: '+开始' })
  addNode({ type: 'approve', label: '+审批' })
  addNode({ type: 'end', label: '+结束' })
  const a = nodes.value[0]?.nodeKey
  const b = nodes.value[1]?.nodeKey
  const c = nodes.value[2]?.nodeKey
  if (a && b && c) {
    edges.value = [
      { fromNodeKey: a, toNodeKey: b, priority: 1, isDefault: 0, cond: '' },
      { fromNodeKey: b, toNodeKey: c, priority: 1, isDefault: 0, cond: '' }
    ]
  }
}

function validate(): string | null {
  const keys = new Set<string>()
  for (let i = 0; i < nodes.value.length; i++) {
    const k = (nodes.value[i]?.nodeKey || '').trim()
    if (!k) return `节点 #${i + 1} nodeKey 不能为空`
    if (keys.has(k)) return `nodeKey 重复：${k}`
    keys.add(k)
  }
  for (let i = 0; i < edges.value.length; i++) {
    const e = edges.value[i]
    if (!keys.has(e.fromNodeKey) || !keys.has(e.toNodeKey)) return `连线 #${i + 1} 引用了不存在的节点`
  }
  return null
}

function openProps() {
  propsRef.value?.open()
}
function closeProps() {
  propsRef.value?.close()
}

function back() {
  uni.navigateBack()
}
function goListEdit() {
  uni.navigateTo({ url: `/pages/system/flow/temp_ver_graph_edit?tempVerId=${tempVerId.value}` })
}
function goPreview() {
  uni.navigateTo({ url: `/pages/system/flow/temp_ver_graph_preview?tempVerId=${tempVerId.value}` })
}

async function doReload() {
  if (!tempVerId.value) return
  busy.value = true
  error.value = null
  loadError.value = null
  try {
    const r = await loadGraphDesigner(tempVerId.value)
    const data = r.data || {}
    nodes.value = (data.nodes || []).map((n: any) => ({
      nodeKey: String(n.nodeKey || ''),
      nodeType: String(n.nodeType || 'approve'),
      nodeName: String(n.nodeName || n.nodeKey || ''),
      x: Number(n.x) || 0,
      y: Number(n.y) || 0,
      configJson: n.configJson != null && String(n.configJson).trim() ? String(n.configJson) : '{}'
    }))
    edges.value = (data.edges || []).map((e: any) => ({
      fromNodeKey: String(e.fromNodeKey || ''),
      toNodeKey: String(e.toNodeKey || ''),
      priority: e.priority ?? 0,
      isDefault: e.isDefault ?? 0,
      cond: String(e.cond || '')
    }))
    seedMinimal()
  } catch (e: any) {
    loadError.value = e?.message ?? String(e)
  } finally {
    busy.value = false
  }
}

async function doSave(silentToast: boolean) {
  error.value = null
  const v = validate()
  if (v) {
    error.value = v
    uni.showToast({ title: v, icon: 'none' })
    return false
  }
  if (!tempVerId.value) return false
  busy.value = true
  try {
    await saveGraphDesigner(tempVerId.value, {
      nodes: nodes.value.map((n) => ({
        nodeKey: n.nodeKey.trim(),
        nodeType: n.nodeType.trim() || 'approve',
        nodeName: (n.nodeName || '').trim() || n.nodeKey.trim(),
        x: Number(n.x) || 0,
        y: Number(n.y) || 0,
        configJson: (n.configJson || '').trim() || '{}'
      })),
      edges: edges.value.map((e) => ({
        fromNodeKey: e.fromNodeKey.trim(),
        toNodeKey: e.toNodeKey.trim(),
        priority: e.priority ?? 0,
        isDefault: e.isDefault ?? 0,
        cond: (e.cond || '').trim()
      }))
    })
    if (!silentToast) uni.showToast({ title: '已保存', icon: 'success' })
    return true
  } catch (e: any) {
    error.value = e?.message ?? String(e)
    uni.showToast({ title: error.value ?? '', icon: 'none' })
    return false
  } finally {
    busy.value = false
  }
}

async function doPublish() {
  if (!tempVerId.value) return
  publishing.value = true
  try {
    const ok = await doSave(true)
    if (!ok) return
    await publishTempVer(tempVerId.value)
    uni.showToast({ title: '已发布', icon: 'success' })
  } catch (e: any) {
    error.value = e?.message ?? String(e)
    uni.showToast({ title: error.value ?? '', icon: 'none' })
  } finally {
    publishing.value = false
  }
}

onLoad((opts) => {
  tempVerId.value = Number((opts as any)?.tempVerId || 0) || 0
})

onMounted(() => {
  if (!ensureSystemContext()) return
  doReload()
})
</script>

<style scoped>
.err {
  color: #b91c1c;
  margin-bottom: 12rpx;
}
.hint-txt {
  color: #b45309;
  font-size: 24rpx;
  margin-bottom: 12rpx;
}
.muted {
  color: #94a3b8;
  font-size: 26rpx;
}
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-bottom: 12rpx;
}
.toolbar .active {
  background: #1677ff;
  color: #fff;
}
.palette-scroll {
  white-space: nowrap;
  margin-bottom: 12rpx;
}
.palette {
  display: inline-flex;
  gap: 8rpx;
  padding: 4rpx 0;
}
.canvas-scroll {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12rpx;
}
.canvas-inner {
  position: relative;
}
.edge-svg {
  position: absolute;
  left: 0;
  top: 0;
  z-index: 0;
}
.edge-line {
  stroke: #94a3b8;
  stroke-width: 2;
}
.edge-line.selected {
  stroke: #1677ff;
  stroke-width: 3;
}
.g-node {
  position: absolute;
  z-index: 1;
  min-height: 48px;
  padding: 6px 8px;
  border-radius: 8px;
  border: 2px solid #cbd5e1;
  background: #fff;
  box-sizing: border-box;
}
.g-node.selected {
  border-color: #1677ff;
  box-shadow: 0 0 0 2px rgba(22, 119, 255, 0.25);
}
.g-node.connectFrom {
  border-color: #f59e0b;
}
.g-node.start {
  border-color: #22c55e;
}
.g-node.end {
  border-color: #ef4444;
}
.g-node.approve {
  border-color: #3b82f6;
}
.g-node.cc {
  border-color: #f97316;
}
.g-node.subflow {
  border-color: #8b5cf6;
}
.g-node-type {
  font-size: 20rpx;
  color: #64748b;
  display: block;
}
.g-node-name {
  font-size: 24rpx;
  font-weight: 600;
  display: block;
}
.props-panel {
  max-height: 70vh;
  overflow-y: auto;
}
.props-title {
  font-weight: 600;
  margin-bottom: 16rpx;
  display: block;
}
</style>
