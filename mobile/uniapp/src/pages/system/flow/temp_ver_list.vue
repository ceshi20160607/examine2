<template>
  <Page :title="`模板版本（tempId=${tempId}）`" subtitle="创建/编辑/发布/填充 MVP">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="createNew">新建版本</uni-button>
        <uni-button :disabled="loading" @click="reload">刷新</uni-button>
        <uni-button :disabled="loading || page<=1" @click="prev">上一页</uni-button>
        <uni-button :disabled="loading || !hasNext" @click="next">下一页</uni-button>
      </ActionBar>
      <view class="u-subtitle">page={{ page }} size={{ size }} total={{ total }}</view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="v in rows"
            :key="String(v.id)"
            :title="`verNo=${v.verNo ?? ''} ${publishText(v.publishStatus)}`"
            :note="`id=${v.id}`"
            clickable
            @click="openActions(v)"
          />
        </uni-list>
        <EmptyState v-else text="暂无版本" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import { deleteTempVers, getTempVer, pageTempVers, publishTempVer, upsertTempVer } from '@/api/flow'

type TempVer = { id: number | string; verNo?: number; publishStatus?: number }

const tempId = ref<number>(0)
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const rows = ref<TempVer[]>([])

const hasNext = computed(() => page.value * size.value < total.value)

onLoad((opts) => {
  tempId.value = Number((opts as any)?.tempId || 0) || 0
})

function publishText(st: any) {
  if (st === 1) return 'draft'
  if (st === 2) return 'published'
  if (st === 3) return 'deprecated'
  return String(st ?? '')
}

async function load() {
  if (!tempId.value) return
  loading.value = true
  try {
    const r = await pageTempVers(tempId.value, page.value, size.value)
    const d = r.data || {}
    total.value = Number(d.total || 0)
    rows.value = (d.records || []) as TempVer[]
  } finally {
    loading.value = false
  }
}

async function reload() {
  page.value = 1
  await load()
}
async function prev() {
  if (page.value <= 1) return
  page.value -= 1
  await load()
}
async function next() {
  if (!hasNext.value) return
  page.value += 1
  await load()
}

function createNew() {
  if (!tempId.value) return
  uni.navigateTo({ url: `/pages/system/flow/temp_ver_edit?tempId=${tempId.value}` })
}

function edit(id: any) {
  uni.navigateTo({ url: `/pages/system/flow/temp_ver_edit?id=${encodeURIComponent(String(id))}&tempId=${tempId.value}` })
}

function openActions(v: TempVer) {
  if (!v?.id) return
  uni.showActionSheet({
    itemList: ['编辑', '发布', '填充MVP并发布', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) {
        edit(v.id)
        return
      }
      if (res.tapIndex === 1) return publish(v.id)
      if (res.tapIndex === 2) return fillMvpAndPublish(v.id)
      if (res.tapIndex === 3) return del(v.id)
    }
  })
}

async function publish(id: any) {
  await publishTempVer(id)
  uni.showToast({ title: '已发布', icon: 'success' })
  reload()
}

async function fillMvpAndPublish(id: any) {
  // 先读详情（拿到 tempId/verNo 等），补 graphJson/formJson，再发布
  const r = await getTempVer(id)
  const v = r.data || {}
  const g = String(v.graphJson || '').trim()
  const f = String(v.formJson || '').trim()
  const hasG = !!g
  const hasF = !!f
  if (hasG) {
    await publish(id)
    return
  }
  const mvp = {
    nodes: [{ id: 'approve-1', name: '审批', type: 'approve' }],
    edges: []
  }
  await upsertTempVer({
    id,
    tempId: v.tempId,
    verNo: v.verNo ?? null,
    publishStatus: v.publishStatus ?? 1,
    graphJson: JSON.stringify(mvp, null, 2),
    formJson: hasF ? f : JSON.stringify({ fields: [] }, null, 2)
  })
  await publish(id)
}

function del(id: any) {
  uni.showModal({
    title: '确认删除？',
    content: `将删除版本 #${id}`,
    success: async (m) => {
      if (!m.confirm) return
      await deleteTempVers([id])
      uni.showToast({ title: '已删除', icon: 'success' })
      reload()
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  if (!tempId.value) {
    uni.showToast({ title: '缺少 tempId', icon: 'none' })
    return
  }
  load()
})
</script>

