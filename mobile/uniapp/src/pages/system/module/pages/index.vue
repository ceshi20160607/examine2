<template>
  <Page :title="`页面设计 appId=${appId}`" subtitle="list 页 config_json 示例：{&quot;modelId&quot;:1,&quot;searchFieldCode&quot;:&quot;name&quot;,&quot;listViewId&quot;:2}">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item label="pageCode">
          <uni-easyinput v-model="form.pageCode" placeholder="如 order_list" />
        </uni-forms-item>
        <uni-forms-item label="pageName">
          <uni-easyinput v-model="form.pageName" placeholder="页面名称" />
        </uni-forms-item>
        <uni-forms-item label="pageType">
          <uni-data-select v-model="form.pageType" :localdata="pageTypeOptions" />
        </uni-forms-item>
        <uni-forms-item label="routePath（可选）">
          <uni-easyinput v-model="form.routePath" placeholder="/pages/..." />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="create">创建页面</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">页面列表</view>
      <uni-list v-if="rows.length">
        <uni-list-item
          v-for="p in rows"
          :key="p.id"
          :title="p.pageName || p.pageCode || `Page#${p.id}`"
          :note="`${p.pageCode || ''} · ${p.pageType || ''}`"
          clickable
          @click="openPage(p)"
        />
      </uni-list>
      <EmptyState v-else text="暂无页面" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import { ensureSystemContext } from '@/utils/guard'
import { deletePages, listPagesByApp, type ModulePage, upsertPage } from '@/api/pages'

const appId = ref(0)
const rows = ref<ModulePage[]>([])
const saving = ref(false)
const { loading, error, run, capture, clearError } = usePageRequest()

const pageTypeOptions = [
  { value: 'list', text: 'list 列表' },
  { value: 'form', text: 'form 表单' },
  { value: 'detail', text: 'detail 详情' },
  { value: 'custom', text: 'custom 自定义' }
]

const form = reactive({
  pageCode: '',
  pageName: '',
  pageType: 'list',
  routePath: ''
})

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
})

async function load() {
  if (!appId.value) return
  await run(async () => {
    const r = await listPagesByApp(appId.value)
    rows.value = r.data || []
  })
}

async function create() {
  if (!appId.value) return
  if (!form.pageCode.trim() || !form.pageName.trim()) {
    uni.showToast({ title: '请输入 pageCode/pageName', icon: 'none' })
    return
  }
  saving.value = true
  clearError()
  try {
    await upsertPage({
      id: null,
      appId: appId.value,
      pageCode: form.pageCode.trim(),
      pageName: form.pageName.trim(),
      pageType: form.pageType,
      routePath: form.routePath.trim() || null,
      status: 1
    })
    form.pageCode = ''
    form.pageName = ''
    form.routePath = ''
    uni.showToast({ title: '已创建', icon: 'success' })
    await load()
  } catch (e: unknown) {
    capture(e)
  } finally {
    saving.value = false
  }
}

function openPage(p: ModulePage) {
  uni.showActionSheet({
    itemList: ['预览运行', '编辑区块', '删除'],
    success: async (res) => {
      if (res.tapIndex === 0) {
        uni.navigateTo({ url: `/pages/system/runtime/entry?pageId=${p.id}` })
        return
      }
      if (res.tapIndex === 1) {
        uni.navigateTo({ url: `/pages/system/module/pages/edit?appId=${appId.value}&pageId=${p.id}` })
        return
      }
      if (res.tapIndex === 2) {
        try {
          await deletePages([p.id])
          uni.showToast({ title: '已删除', icon: 'success' })
          await load()
        } catch (e: unknown) {
          capture(e)
        }
      }
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>
