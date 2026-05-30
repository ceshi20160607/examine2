<template>
  <Page :title="`变更历史 #${recordId}`" subtitle="create / update / delete 快照">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="load">刷新</uni-button>
        <uni-button @click="goDetail">返回详情</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">历史列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="h in rows"
            :key="String(h.id)"
            :title="`${h.action || '-'} · ${h.createTime || ''}`"
            :note="noteOf(h)"
            clickable
            @click="showJson(h)"
          />
        </uni-list>
        <EmptyState v-else text="暂无变更历史" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import { listRecordHistory, type RecordHistoryRow } from '@/api/records'
import { hasId, idToString } from '@/utils/id'

const recordId = ref('')
const rows = ref<RecordHistoryRow[]>([])
const { loading, error, run } = usePageRequest()

onLoad((opts) => {
  recordId.value = idToString((opts as any)?.recordId)
})

function noteOf(h: RecordHistoryRow) {
  const parts: string[] = []
  if (h.createUserId) parts.push(`user=${h.createUserId}`)
  if (h.dataJson) parts.push(`data ${h.dataJson.length} chars`)
  return parts.join(' · ') || `id=${h.id}`
}

async function load() {
  if (!hasId(recordId.value)) return
  await run(async () => {
    const r = await listRecordHistory(recordId.value, 50)
    rows.value = r.data || []
  })
}

function showJson(h: RecordHistoryRow) {
  const text = h.dataJson || h.diffJson || '{}'
  let pretty = text
  try {
    pretty = JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    /* keep raw */
  }
  uni.showModal({
    title: `${h.action || 'snapshot'}`,
    content: pretty.length > 800 ? pretty.slice(0, 800) + '…' : pretty,
    showCancel: false
  })
}

function goDetail() {
  uni.navigateBack({
    fail: () => {
      uni.redirectTo({ url: `/pages/system/records/detail?recordId=${encodeURIComponent(recordId.value)}` })
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>
