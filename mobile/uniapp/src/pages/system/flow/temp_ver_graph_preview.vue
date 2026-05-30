<template>
  <Page title="流程图预览" subtitle="只读；编辑请用「图形画布」或「列表编辑」">
    <view class="u-card">
      <view v-if="!tempVerId" class="muted">缺少 tempVerId</view>
      <template v-else>
        <view class="hint">版本 #{{ tempVerId }} · 节点 {{ nodeList.length }} · 连线 {{ edgeList.length }}</view>
        <view v-if="parseError" class="err">{{ parseError }}</view>

        <view v-if="!parseError && nodeList.length" class="flow">
          <view v-for="(step, idx) in orderedSteps" :key="step.nodeKey" class="step">
            <view class="node-box">
              <text class="node-type">{{ step.nodeType }}</text>
              <text class="node-name">{{ step.nodeName || step.nodeKey }}</text>
              <text class="node-key muted">{{ step.nodeKey }}</text>
            </view>
            <view v-if="idx < orderedSteps.length - 1" class="arrow">↓</view>
          </view>
        </view>

        <view v-if="!parseError && nodeList.length && edgeList.length" class="section">
          <text class="section-title">连线明细</text>
          <view v-for="(e, i) in edgeList" :key="i" class="edge-row">
            <text>{{ e.from }} → {{ e.to }}</text>
            <text v-if="e.cond" class="cond">{{ e.cond }}</text>
          </view>
        </view>

        <view v-if="!parseError && !nodeList.length && !rawEmpty" class="muted">graphJson 无 nodes，可回版本页填写或点「填充默认流程」</view>
        <view v-if="rawEmpty" class="muted">当前版本无 graphJson</view>
      </template>
      <ActionBar>
        <uni-button @click="back">返回</uni-button>
        <uni-button v-if="tempVerId" type="primary" @click="goDesignerHint">Web 设计说明</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>
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
import { getTempVer } from '@/api/flow'
import { hasId, idToString } from '@/utils/id'

const tempVerId = ref('')
const error = ref<string | null>(null)
const parseError = ref<string | null>(null)
const rawGraph = ref<any>(null)
const rawEmpty = ref(true)

type NormNode = { nodeKey: string; nodeType: string; nodeName: string }
type NormEdge = { from: string; to: string; cond: string }

const nodeList = computed((): NormNode[] => {
  const g = rawGraph.value
  if (!g || !Array.isArray(g.nodes)) return []
  const out: NormNode[] = []
  for (const n of g.nodes) {
    const x = normalizeNode(n)
    if (x) out.push(x)
  }
  return out
})

const edgeList = computed((): NormEdge[] => {
  const g = rawGraph.value
  if (!g || !Array.isArray(g.edges)) return []
  const out: NormEdge[] = []
  for (const e of g.edges) {
    const x = normalizeEdge(e)
    if (x) out.push(x)
  }
  return out
})

const orderedSteps = computed((): NormNode[] => {
  const nodes = nodeList.value
  const edges = edgeList.value
  if (!nodes.length) return []
  if (!edges.length) return nodes

  const keys = new Set(nodes.map((n) => n.nodeKey))
  const indeg = new Map<string, number>()
  keys.forEach((k) => indeg.set(k, 0))
  const adj = new Map<string, string[]>()
  for (const e of edges) {
    if (!keys.has(e.from) || !keys.has(e.to)) continue
    indeg.set(e.to, (indeg.get(e.to) || 0) + 1)
    const list = adj.get(e.from) || []
    list.push(e.to)
    adj.set(e.from, list)
  }
  const starts: string[] = []
  indeg.forEach((v, k) => {
    if (v === 0) starts.push(k)
  })
  const order: string[] = []
  const seen = new Set<string>()
  function walk(k: string) {
    if (seen.has(k)) return
    seen.add(k)
    order.push(k)
    for (const t of adj.get(k) || []) walk(t)
  }
  for (const s of starts) walk(s)
  for (const n of nodes) {
    if (!seen.has(n.nodeKey)) walk(n.nodeKey)
  }
  const byKey = new Map(nodes.map((n) => [n.nodeKey, n]))
  return order.map((k) => byKey.get(k)).filter((x): x is NormNode => x != null)
})

function normalizeNode(n: any): NormNode | null {
  if (!n || typeof n !== 'object') return null
  if (n.nodeKey != null && String(n.nodeKey)) {
    return {
      nodeKey: String(n.nodeKey),
      nodeType: String(n.nodeType || n.type || ''),
      nodeName: String(n.nodeName || n.name || '')
    }
  }
  if (n.id != null) {
    return {
      nodeKey: String(n.id),
      nodeType: String(n.type || ''),
      nodeName: String(n.name || '')
    }
  }
  return null
}

function normalizeEdge(e: any): NormEdge | null {
  if (!e || typeof e !== 'object') return null
  const from = e.fromNodeKey != null ? String(e.fromNodeKey) : e.from != null ? String(e.from) : ''
  const to = e.toNodeKey != null ? String(e.toNodeKey) : e.to != null ? String(e.to) : ''
  if (!from || !to) return null
  return { from, to, cond: e.cond != null ? String(e.cond) : '' }
}

function back() {
  uni.navigateBack()
}

function goDesignerHint() {
  uni.showModal({
    title: '图形编辑',
    content: '可在本系统「流程版本」页打开「图形简易设计」维护节点与连线；画布拖拽请在电脑浏览器使用 Web「流程图设计器」。本页为只读预览。',
    showCancel: false
  })
}

async function load() {
  error.value = null
  parseError.value = null
  rawGraph.value = null
  rawEmpty.value = true
  if (!hasId(tempVerId.value)) return
  try {
    const r = await getTempVer(tempVerId.value)
    const v = r.data || {}
    const gj = v.graphJson
    if (gj == null || gj === '') {
      rawEmpty.value = true
      return
    }
    rawEmpty.value = false
    try {
      rawGraph.value = typeof gj === 'string' ? JSON.parse(String(gj).trim() || '{}') : gj
    } catch {
      parseError.value = 'graphJson 不是合法 JSON'
    }
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  }
}

onLoad((opts) => {
  tempVerId.value = idToString((opts as any)?.tempVerId)
})

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>

<style scoped>
.hint {
  font-size: 26rpx;
  color: #64748b;
  margin-bottom: 24rpx;
}
.err {
  color: #b91c1c;
  margin-bottom: 16rpx;
}
.muted {
  color: #94a3b8;
  font-size: 26rpx;
}
.flow {
  margin-bottom: 32rpx;
}
.step {
  display: flex;
  flex-direction: column;
  align-items: center;
}
.node-box {
  width: 100%;
  box-sizing: border-box;
  padding: 20rpx 24rpx;
  background: #f1f5f9;
  border-radius: 12rpx;
  border: 1px solid #e2e8f0;
}
.node-type {
  display: block;
  font-size: 22rpx;
  color: #64748b;
}
.node-name {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  margin-top: 8rpx;
}
.node-key {
  display: block;
  font-size: 22rpx;
  margin-top: 8rpx;
}
.arrow {
  padding: 12rpx 0;
  color: #94a3b8;
  font-size: 32rpx;
}
.section-title {
  display: block;
  font-weight: 600;
  margin-bottom: 16rpx;
  font-size: 28rpx;
}
.edge-row {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  padding: 12rpx 0;
  border-bottom: 1px solid #f1f5f9;
  font-size: 26rpx;
}
.cond {
  color: #64748b;
  max-width: 60%;
  text-align: right;
}
</style>
