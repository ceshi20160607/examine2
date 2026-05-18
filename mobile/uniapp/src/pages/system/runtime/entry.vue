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

const loading = ref(true)
const error = ref<string | null>(null)

onLoad(async (opts) => {
  if (!ensureSystemContext()) {
    loading.value = false
    return
  }
  const pageId = Number((opts as any)?.pageId || 0) || 0
  if (!pageId) {
    error.value = '缺少 pageId'
    loading.value = false
    return
  }
  try {
    const r = await getPageRuntime(pageId)
    const rt = r.data
    if (!rt?.appId) {
      error.value = '页面配置无效'
      return
    }
    const pq = pageQuerySuffix(pageId)
    const appId = rt.appId
    const modelId = Number(rt.modelId || 0)
    const type = String(rt.pageType || 'list').toLowerCase()

    if (type === 'form') {
      if (!modelId) {
        error.value = '表单页未配置 modelId（请在页面 config_json 或区块中设置）'
        return
      }
      uni.redirectTo({ url: `/pages/system/records/form?appId=${appId}&modelId=${modelId}${pq}` })
      return
    }
    if (type === 'detail') {
      const recordId = Number((opts as any)?.recordId || 0) || 0
      if (!recordId) {
        error.value = '详情页需要 recordId 参数'
        return
      }
      uni.redirectTo({ url: `/pages/system/records/detail?recordId=${recordId}${pq}` })
      return
    }
    if (type === 'list' || type === 'custom') {
      if (!modelId) {
        error.value = '列表页未配置 modelId'
        return
      }
      uni.redirectTo({ url: `/pages/system/records/list?appId=${appId}&modelId=${modelId}${pq}` })
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
