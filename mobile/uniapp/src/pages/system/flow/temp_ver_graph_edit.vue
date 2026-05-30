<template>
  <Page title="流程图列表编辑" subtitle="列表维护节点/连线；画布拖拽请用「图形画布」">
    <view class="u-card">
      <view v-if="!tempVerId" class="muted">缺少 tempVerId</view>
      <template v-else>
        <view class="hint">版本 #{{ tempVerId }} · 节点 {{ nodes.length }} · 连线 {{ edges.length }}</view>
        <view v-if="error" class="err">{{ error }}</view>

        <view class="section">
          <text class="section-title">添加节点</text>
          <view class="btn-row">
            <uni-button v-for="t in nodeTypes" :key="t.type" size="mini" @click="addNode(t)">{{ t.label }}</uni-button>
          </view>
        </view>

        <view class="section">
          <text class="section-title">节点列表</text>
          <view v-for="(n, idx) in nodes" :key="n.nodeKey + '-' + idx" class="node-card">
            <view class="row-top">
              <text class="badge">{{ n.nodeType }}</text>
              <view class="row-actions">
                <uni-button size="mini" :disabled="idx === 0" @click="moveNode(idx, -1)">↑</uni-button>
                <uni-button size="mini" :disabled="idx === nodes.length - 1" @click="moveNode(idx, 1)">↓</uni-button>
                <uni-button size="mini" type="warn" @click="removeNode(idx)">删</uni-button>
              </view>
            </view>
            <uni-forms labelPosition="top">
              <uni-forms-item label="nodeKey">
                <uni-easyinput v-model="n.nodeKey" placeholder="唯一键" />
              </uni-forms-item>
              <uni-forms-item label="nodeName">
                <uni-easyinput v-model="n.nodeName" />
              </uni-forms-item>
              <uni-forms-item label="nodeType">
                <uni-easyinput v-model="n.nodeType" placeholder="start/approve/cc/subflow/end..." />
              </uni-forms-item>
              <uni-forms-item label="configJson">
                <uni-easyinput v-model="n.configJson" type="textarea" :autoHeight="true" placeholder="{}" />
              </uni-forms-item>
            </uni-forms>
          </view>
          <view v-if="!nodes.length" class="muted">暂无节点；可添加或通过「填充」生成起始链</view>
        </view>

        <view class="section">
          <text class="section-title">连线</text>
          <uni-forms labelPosition="top" class="pick-row">
            <uni-forms-item label="从">
              <uni-data-select v-model="newEdge.fromNodeKey" :localdata="keyOptions" placeholder="来源节点 key" />
            </uni-forms-item>
            <uni-forms-item label="到">
              <uni-data-select v-model="newEdge.toNodeKey" :localdata="keyOptions" placeholder="目标节点 key" />
            </uni-forms-item>
            <uni-forms-item label="条件 cond">
              <uni-easyinput v-model="newEdge.cond" placeholder="可选，例如 eq(amount,100)" />
            </uni-forms-item>
          </uni-forms>
          <uni-button size="mini" type="primary" :disabled="!canAddEdge" @click="addEdge">添加连线</uni-button>

          <view v-for="(e, ei) in edges" :key="ei" class="edge-card">
            <text class="edge-txt">{{ e.fromNodeKey }} → {{ e.toNodeKey }}</text>
            <text v-if="e.cond" class="cond">{{ e.cond }}</text>
            <uni-button size="mini" type="warn" @click="removeEdge(ei)">删</uni-button>
          </view>
        </view>

        <ActionBar>
          <uni-button type="primary" :disabled="busy" @click="doSave(false)">保存</uni-button>
          <uni-button :disabled="busy" @click="goCanvas">画布</uni-button>
          <uni-button :disabled="busy" @click="doReload">刷新</uni-button>
          <uni-button :disabled="busy" @click="goPreview">只读预览</uni-button>
          <uni-button v-if="tempVerId" :disabled="busy || publishing" type="warn" @click="doPublish">发布</uni-button>
          <uni-button @click="back">返回</uni-button>
        </ActionBar>
      </template>
      <ErrorBlock :text="loadError" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { onMounted } from 'vue'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { loadGraphDesigner, publishTempVer, saveGraphDesigner } from '@/api/flow'
import { hasId, idToString } from '@/utils/id'

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

const tempVerId = ref('')
const nodes = ref<DesNode[]>([])
const edges = ref<DesEdge[]>([])
const error = ref<string | null>(null)
const loadError = ref<string | null>(null)
const busy = ref(false)
const publishing = ref(false)

const newEdge = reactive({ fromNodeKey: '', toNodeKey: '', cond: '' })

const nodeTypes = [
  { type: 'start', label: '+ 开始' },
  { type: 'approve', label: '+ 审批' },
  { type: 'cc', label: '+ 抄送' },
  { type: 'subflow', label: '+ 子流程' },
  { type: 'end', label: '+ 结束' }
]

const keyOptions = computed(() =>
  nodes.value.map((n) => ({ value: n.nodeKey, text: `${n.nodeKey} (${n.nodeType})` })).filter((o) => o.value)
)

const canAddEdge = computed(() => {
  const f = (newEdge.fromNodeKey || '').trim()
  const t = (newEdge.toNodeKey || '').trim()
  if (!f || !t || f === t) return false
  return !edges.value.some((e) => e.fromNodeKey === f && e.toNodeKey === t)
})

function uniqKey(prefix: string) {
  let i = nodes.value.length + 1
  let k = `${prefix}_${i}`
  while (nodes.value.some((n) => n.nodeKey === k)) {
    i++
    k = `${prefix}_${i}`
  }
  return k
}

function gridPos(i: number) {
  return {
    x: 120 + (i % 3) * 200,
    y: 120 + Math.floor(i / 3) * 120
  }
}

function addNode(t: { type: string; label: string }) {
  const label = t.label.replace('+ ', '')
  const i = nodes.value.length
  const { x, y } = gridPos(i)
  nodes.value.push({
    nodeKey: uniqKey(t.type),
    nodeType: t.type,
    nodeName: label,
    x,
    y,
    configJson: t.type === 'subflow' ? '{"sub_temp_code":"demo_subflow_child"}' : '{}'
  })
}

function moveNode(index: number, delta: number) {
  const j = index + delta
  if (j < 0 || j >= nodes.value.length) return
  const arr = nodes.value
  const removed = arr.splice(index, 1)
  arr.splice(j, 0, ...removed)
}

function removeNode(index: number) {
  const k = (nodes.value[index]?.nodeKey || '').trim()
  if (!k) return
  nodes.value.splice(index, 1)
  edges.value = edges.value.filter((e) => e.fromNodeKey !== k && e.toNodeKey !== k)
}

function addEdge() {
  if (!canAddEdge.value) return
  edges.value.push({
    fromNodeKey: newEdge.fromNodeKey.trim(),
    toNodeKey: newEdge.toNodeKey.trim(),
    priority: edges.value.length + 1,
    isDefault: 0,
    cond: (newEdge.cond || '').trim()
  })
  newEdge.cond = ''
}

function removeEdge(i: number) {
  edges.value.splice(i, 1)
}

function seedMinimal() {
  if (nodes.value.length) return
  addNode({ type: 'start', label: '+ 开始' })
  addNode({ type: 'approve', label: '+ 审批' })
  addNode({ type: 'end', label: '+ 结束' })
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
    if (!keys.has(e.fromNodeKey) || !keys.has(e.toNodeKey)) {
      return `连线 #${i + 1} 引用了不存在的节点`
    }
  }
  return null
}

function back() {
  uni.navigateBack()
}

function goPreview() {
  if (!hasId(tempVerId.value)) return
  uni.navigateTo({ url: `/pages/system/flow/temp_ver_graph_preview?tempVerId=${encodeURIComponent(tempVerId.value)}` })
}
function goCanvas() {
  if (!hasId(tempVerId.value)) return
  uni.navigateTo({ url: `/pages/system/flow/temp_ver_graph_designer?tempVerId=${encodeURIComponent(tempVerId.value)}` })
}

async function doReload() {
  if (!hasId(tempVerId.value)) return
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
  if (!hasId(tempVerId.value)) return false
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
  if (!hasId(tempVerId.value)) return
  publishing.value = true
  error.value = null
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
  tempVerId.value = idToString((opts as any)?.tempVerId)
})

onMounted(() => {
  if (!ensureSystemContext()) return
  doReload()
})
</script>

<style scoped>
.hint {
  font-size: 26rpx;
  color: #64748b;
  margin-bottom: 20rpx;
}
.err {
  color: #b91c1c;
  margin-bottom: 16rpx;
}
.section {
  margin-bottom: 28rpx;
}
.section-title {
  font-weight: 600;
  font-size: 28rpx;
  margin-bottom: 12rpx;
  display: block;
}
.btn-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}
.muted {
  color: #94a3b8;
  font-size: 26rpx;
}
.node-card {
  border: 1px solid #e2e8f0;
  border-radius: 12rpx;
  padding: 16rpx;
  margin-bottom: 16rpx;
}
.row-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}
.row-actions {
  display: flex;
  gap: 8rpx;
  flex-shrink: 0;
}
.badge {
  font-size: 22rpx;
  background: #f1f5f9;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}
.pick-row {
  margin-bottom: 12rpx;
}
.edge-card {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12rpx;
  padding: 12rpx 0;
  border-bottom: 1px solid #f1f5f9;
}
.edge-txt {
  font-size: 26rpx;
}
.cond {
  font-size: 24rpx;
  color: #64748b;
  flex: 1;
}
</style>
