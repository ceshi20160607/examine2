<template>
  <Page title="加载页面…" subtitle="解析低代码页面配置">
    <view class="u-card">
      <ErrorBlock :text="error" />
      <EmptyState v-if="!loading && !error" text="正在跳转…" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Page from '@/ui/Page.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { ensureSystemContext } from '@/utils/guard'
import { getPageRuntime } from '@/api/pages'
import { pageQuerySuffix } from '@/utils/pageRuntime'
import { hasId, idToString } from '@/utils/id'

const loading = ref(true)
const error = ref<string | null>(null)

onLoad(async (opts) => {
  if (!ensureSystemContext()) {
    loading.value = false
    return
  }
  const pageId = idToString((opts as any)?.pageId)
  if (!hasId(pageId)) {
    error.value = '缺少 pageId'
    loading.value = false
    return
  }
  try {
    const r = await getPageRuntime(pageId)
    const rt = r.data
    if (!hasId(rt?.appId)) {
      error.value = '页面配置无效'
      return
    }
    const pq = pageQuerySuffix(pageId)
    const appId = idToString(rt.appId)
    const modelId = idToString(rt.modelId)
    const type = String(rt.pageType || 'list').toLowerCase()

    if (type === 'form') {
      if (!hasId(modelId)) {
        error.value = '表单页未配置 modelId（请在页面 config_json 或区块中设置）'
        return
      }
      uni.redirectTo({ url: `/pages/system/records/form?appId=${encodeURIComponent(appId)}&modelId=${encodeURIComponent(modelId)}${pq}` })
      return
    }
    if (type === 'detail') {
      const recordId = idToString((opts as any)?.recordId)
      if (!hasId(recordId)) {
        error.value = '详情页需要 recordId 参数'
        return
      }
      uni.redirectTo({ url: `/pages/system/records/detail?recordId=${encodeURIComponent(recordId)}${pq}` })
      return
    }
    if (type === 'list' || type === 'custom') {
      if (!hasId(modelId)) {
        error.value = '列表页未配置 modelId'
        return
      }
      uni.redirectTo({ url: `/pages/system/records/list?appId=${encodeURIComponent(appId)}&modelId=${encodeURIComponent(modelId)}${pq}` })
      return
    }
    error.value = `未知 pageType: ${type}`
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
})
</script>
