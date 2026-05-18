<template>
  <Page :title="`页面编辑 #${pageId}`" subtitle="页面属性 + 区块 blocks">
    <view class="u-card u-section" v-if="page">
      <uni-forms labelPosition="top">
        <uni-forms-item label="pageCode">
          <uni-easyinput v-model="pageForm.pageCode" />
        </uni-forms-item>
        <uni-forms-item label="pageName">
          <uni-easyinput v-model="pageForm.pageName" />
        </uni-forms-item>
        <uni-forms-item label="pageType">
          <uni-data-select v-model="pageForm.pageType" :localdata="pageTypeOptions" />
        </uni-forms-item>
        <uni-forms-item label="routePath">
          <uni-easyinput v-model="pageForm.routePath" />
        </uni-forms-item>
        <uni-forms-item label="configJson">
          <uni-easyinput v-model="pageForm.configJson" type="textarea" :autoHeight="true" placeholder="布局/数据源 JSON" />
        </uni-forms-item>
        <uni-forms-item label="formFieldsJson">
          <uni-easyinput v-model="pageForm.formFieldsJson" type="textarea" :autoHeight="true" placeholder="字段级覆盖 JSON" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="savePage">保存页面</uni-button>
      </ActionBar>
    </view>

    <view class="u-card u-section">
      <view class="u-title">新增区块</view>
      <uni-forms labelPosition="top">
        <uni-forms-item label="blockType">
          <uni-data-select v-model="blockForm.blockType" :localdata="blockTypeOptions" />
        </uni-forms-item>
        <uni-forms-item label="sortNo">
          <uni-easyinput v-model="blockForm.sortNo" type="number" />
        </uni-forms-item>
        <uni-forms-item label="configJson">
          <uni-easyinput v-model="blockForm.configJson" type="textarea" :autoHeight="true" placeholder='如 {"modelId":123}' />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="savingBlock" @click="saveBlock">添加区块</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">区块列表</view>
      <uni-list v-if="blocks.length">
        <uni-list-item
          v-for="b in blocks"
          :key="b.id"
          :title="`${b.blockType || 'block'} · sort=${b.sortNo ?? 0}`"
          :note="truncate(b.configJson)"
          clickable
          @click="editBlock(b)"
        />
      </uni-list>
      <EmptyState v-else text="暂无区块" />
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
import {
  deletePageBlocks,
  getPageDetail,
  type ModulePage,
  type ModulePageBlock,
  upsertPage,
  upsertPageBlock
} from '@/api/pages'

const appId = ref(0)
const pageId = ref(0)
const page = ref<ModulePage | null>(null)
const blocks = ref<ModulePageBlock[]>([])
const saving = ref(false)
const savingBlock = ref(false)
const { loading, error, run, capture, clearError } = usePageRequest()

const pageTypeOptions = [
  { value: 'list', text: 'list' },
  { value: 'form', text: 'form' },
  { value: 'detail', text: 'detail' },
  { value: 'custom', text: 'custom' }
]

const blockTypeOptions = [
  { value: 'form', text: 'form' },
  { value: 'table', text: 'table' },
  { value: 'chart', text: 'chart' },
  { value: 'text', text: 'text' },
  { value: 'custom', text: 'custom' }
]

const pageForm = reactive({
  pageCode: '',
  pageName: '',
  pageType: 'list',
  routePath: '',
  configJson: '',
  formFieldsJson: ''
})

const blockForm = reactive({
  id: null as number | null,
  blockType: 'form',
  sortNo: '0',
  configJson: ''
})

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  pageId.value = Number((opts as any)?.pageId || 0) || 0
})

function truncate(s?: string | null) {
  if (!s) return ''
  return s.length > 80 ? s.slice(0, 80) + '…' : s
}

async function load() {
  if (!pageId.value) return
  await run(async () => {
    const r = await getPageDetail(pageId.value)
    page.value = r.data?.page || null
    blocks.value = r.data?.blocks || []
    if (page.value) {
      pageForm.pageCode = page.value.pageCode || ''
      pageForm.pageName = page.value.pageName || ''
      pageForm.pageType = page.value.pageType || 'list'
      pageForm.routePath = page.value.routePath || ''
      pageForm.configJson = page.value.configJson || ''
      pageForm.formFieldsJson = page.value.formFieldsJson || ''
    }
  })
}

async function savePage() {
  if (!appId.value || !page.value?.id) return
  saving.value = true
  clearError()
  try {
    await upsertPage({
      id: page.value.id,
      appId: appId.value,
      pageCode: pageForm.pageCode.trim(),
      pageName: pageForm.pageName.trim(),
      pageType: pageForm.pageType,
      routePath: pageForm.routePath.trim() || null,
      configJson: pageForm.configJson.trim() || null,
      formFieldsJson: pageForm.formFieldsJson.trim() || null,
      status: page.value.status ?? 1
    })
    uni.showToast({ title: '页面已保存', icon: 'success' })
    await load()
  } catch (e: unknown) {
    capture(e)
  } finally {
    saving.value = false
  }
}

function resetBlockForm() {
  blockForm.id = null
  blockForm.blockType = 'form'
  blockForm.sortNo = '0'
  blockForm.configJson = ''
}

async function saveBlock() {
  if (!appId.value || !pageId.value) return
  savingBlock.value = true
  clearError()
  try {
    await upsertPageBlock({
      id: blockForm.id,
      appId: appId.value,
      pageId: pageId.value,
      blockType: blockForm.blockType,
      sortNo: Number(blockForm.sortNo) || 0,
      configJson: blockForm.configJson.trim() || null
    })
    resetBlockForm()
    uni.showToast({ title: '区块已保存', icon: 'success' })
    await load()
  } catch (e: unknown) {
    capture(e)
  } finally {
    savingBlock.value = false
  }
}

function editBlock(b: ModulePageBlock) {
  uni.showActionSheet({
    itemList: ['编辑', '删除'],
    success: async (res) => {
      if (res.tapIndex === 0) {
        blockForm.id = b.id
        blockForm.blockType = b.blockType || 'form'
        blockForm.sortNo = String(b.sortNo ?? 0)
        blockForm.configJson = b.configJson || ''
        return
      }
      if (res.tapIndex === 1) {
        try {
          await deletePageBlocks([b.id])
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
