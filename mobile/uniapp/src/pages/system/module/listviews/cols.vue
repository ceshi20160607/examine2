<template>
  <Page :title="`列配置 Cols（viewId=${viewId}）`" subtitle="配置列表展示的列（需要 fieldId）">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item label="fieldId">
          <uni-easyinput v-model="form.fieldId" placeholder="fieldId" />
        </uni-forms-item>
        <uni-forms-item label="colTitle">
          <uni-easyinput v-model="form.colTitle" placeholder="colTitle" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="upsert">新增列</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
        <uni-button :disabled="!appId || !modelId" @click="goFields">查看字段</uni-button>
      </ActionBar>
      <view class="u-subtitle">提示：fieldId 可在 Fields 页面对照列表中的 id。</view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="c in rows"
            :key="c.id"
            :title="c.colTitle || ('Col#' + c.id)"
            :note="`fieldId=${c.fieldId || ''}`"
          />
        </uni-list>
        <EmptyState v-else text="暂无列配置" />
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
import { listViewCols, type ModuleListViewColRow, upsertViewCol } from '@/api/module'

const viewId = ref<number>(0)
const appId = ref<number>(0)
const modelId = ref<number>(0)

const loading = ref(false)
const saving = ref(false)
const rows = ref<ModuleListViewColRow[]>([])

const form = reactive<{ fieldId: string; colTitle: string }>({ fieldId: '', colTitle: '' })

onLoad((opts) => {
  viewId.value = Number((opts as any)?.viewId || 0) || 0
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
})

async function load() {
  if (!viewId.value) return
  loading.value = true
  try {
    const r = await listViewCols(viewId.value)
    rows.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function upsert() {
  if (!viewId.value) return
  const fieldId = Number(form.fieldId.trim())
  if (!fieldId || Number.isNaN(fieldId)) {
    uni.showToast({ title: '请输入合法 fieldId', icon: 'none' })
    return
  }
  if (!form.colTitle.trim()) {
    uni.showToast({ title: '请输入 colTitle', icon: 'none' })
    return
  }

  saving.value = true
  try {
    await upsertViewCol({
      id: null,
      viewId: viewId.value,
      fieldId,
      colTitle: form.colTitle.trim(),
      width: null,
      sortNo: 0,
      visibleFlag: 1,
      fixedType: null,
      formatJson: null
    })
    form.fieldId = ''
    form.colTitle = ''
    await load()
  } finally {
    saving.value = false
  }
}

function goFields() {
  if (!appId.value || !modelId.value) {
    uni.showToast({ title: '缺少 appId/modelId', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/system/module/meta/fields?appId=${appId.value}&modelId=${modelId.value}` })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  if (!viewId.value) {
    uni.showToast({ title: '缺少 viewId', icon: 'none' })
    return
  }
  load()
})
</script>
