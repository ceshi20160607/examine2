<template>
  <Page :title="`List Views（modelId=${modelId}）`" subtitle="列表视图 + 列配置 + 筛选模板">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item :label="editingId ? '编辑视图' : '新增视图'">
          <uni-easyinput v-model="form.viewCode" placeholder="viewCode" />
          <uni-easyinput v-model="form.viewName" placeholder="viewName" />
        </uni-forms-item>
        <uni-forms-item label="默认视图">
          <uni-data-select v-model="form.defaultFlag" :localdata="flagOptions" />
        </uni-forms-item>
        <uni-forms-item label="状态">
          <uni-data-select v-model="form.status" :localdata="statusOptions" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="upsert">{{ editingId ? '保存' : '创建' }}</uni-button>
        <uni-button v-if="editingId" @click="resetForm">取消编辑</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
        <uni-button :disabled="!modelId" @click="goFilterTpls">筛选模板</uni-button>
      </ActionBar>
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="v in rows"
            :key="v.id"
            :title="v.viewName || v.viewCode || ('View#' + v.id)"
            :note="`${v.viewCode || ''} · ${v.defaultFlag === 1 ? '默认' : '普通'} · ${v.status === 1 ? '启用' : '停用'}`"
            clickable
            @click="openViewActions(v)"
          />
        </uni-list>
        <EmptyState v-else text="暂无视图" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import { deleteListViews, listViewsByModel, type ModuleListViewRow, upsertListView } from '@/api/module'
import { hasId, idToString, type IdValue } from '@/utils/id'

const appId = ref('')
const modelId = ref('')
const loading = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const rows = ref<ModuleListViewRow[]>([])

const form = reactive<{ viewCode: string; viewName: string; defaultFlag: number; status: number }>({
  viewCode: '',
  viewName: '',
  defaultFlag: 0,
  status: 1
})
const flagOptions = [
  { value: 0, text: '否' },
  { value: 1, text: '是' }
]
const statusOptions = [
  { value: 1, text: '启用' },
  { value: 2, text: '停用' }
]

onLoad((opts) => {
  appId.value = idToString((opts as any)?.appId)
  modelId.value = idToString((opts as any)?.modelId)
})

async function load() {
  if (!hasId(modelId.value)) return
  loading.value = true
  try {
    const r = await listViewsByModel(modelId.value)
    rows.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function upsert() {
  if (!hasId(appId.value) || !hasId(modelId.value)) return
  if (!form.viewCode.trim() || !form.viewName.trim()) {
    uni.showToast({ title: '请输入 viewCode/viewName', icon: 'none' })
    return
  }
  saving.value = true
  try {
  await upsertListView({
    id: editingId.value,
      appId: appId.value,
      modelId: modelId.value,
      platId: null,
      viewCode: form.viewCode.trim(),
      viewName: form.viewName.trim(),
      defaultFlag: form.defaultFlag,
      status: form.status
    })
    resetForm()
    uni.showToast({ title: '已保存', icon: 'success' })
    await load()
  } finally {
    saving.value = false
  }
}

function resetForm() {
  editingId.value = null
  form.viewCode = ''
  form.viewName = ''
  form.defaultFlag = 0
  form.status = 1
}

function fillForm(v: ModuleListViewRow) {
  editingId.value = idToString(v.id as IdValue)
  form.viewCode = v.viewCode || ''
  form.viewName = v.viewName || ''
  form.defaultFlag = v.defaultFlag === 1 ? 1 : 0
  form.status = v.status === 2 ? 2 : 1
}

function goFilterTpls() {
  if (!hasId(appId.value) || !hasId(modelId.value)) return
  uni.navigateTo({ url: `/pages/system/module/listviews/filter_tpls?appId=${encodeURIComponent(appId.value)}&modelId=${encodeURIComponent(modelId.value)}` })
}

function openViewActions(v: ModuleListViewRow) {
  const viewId = idToString(v?.id as IdValue)
  if (!hasId(viewId)) return
  uni.showActionSheet({
    itemList: ['Cols（列配置）', '编辑', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) {
        if (!hasId(appId.value) || !hasId(modelId.value)) return
        uni.navigateTo({
          url: `/pages/system/module/listviews/cols?viewId=${encodeURIComponent(viewId)}&appId=${encodeURIComponent(appId.value)}&modelId=${encodeURIComponent(modelId.value)}`
        })
        return
      }
      if (res.tapIndex === 1) {
        fillForm(v)
        return
      }
      if (res.tapIndex === 2) {
        remove(v)
      }
    }
  })
}

function remove(v: ModuleListViewRow) {
  const id = idToString(v.id as IdValue)
  if (!hasId(id)) return
  uni.showModal({
    title: '删除视图？',
    content: v.viewName || v.viewCode || id,
    success: async (m) => {
      if (!m.confirm) return
      await deleteListViews([id])
      if (editingId.value === id) resetForm()
      uni.showToast({ title: '已删除', icon: 'success' })
      await load()
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>

