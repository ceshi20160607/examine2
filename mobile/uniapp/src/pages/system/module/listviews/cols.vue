<template>
  <view style="padding: 16px">
    <uni-card :title="`列配置 Cols（viewId=${viewId}）`">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-easyinput v-model="form.fieldId" placeholder="fieldId" />
        <uni-easyinput v-model="form.colTitle" placeholder="colTitle" />
        <uni-button type="primary" :disabled="saving" @click="upsert">新增列</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
        <uni-button :disabled="!appId || !modelId" @click="goFields">查看字段</uni-button>
      </view>
      <view style="margin-top: 12px; color:#666">
        提示：fieldId 可在 Fields 页面对照列表中的 id。
      </view>
    </uni-card>

    <uni-card title="列表" style="margin-top: 12px">
      <uni-list v-if="rows.length">
        <uni-list-item
          v-for="c in rows"
          :key="c.id"
          :title="c.colTitle || ('Col#' + c.id)"
          :note="`fieldId=${c.fieldId || ''}`"
        />
      </uni-list>
      <view v-else style="color:#666">暂无列配置</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

type ColRow = { id: number; viewId?: number; fieldId?: number; colTitle?: string; width?: number; sortNo?: number; visibleFlag?: number }

const viewId = ref<number>(0)
const appId = ref<number>(0)
const modelId = ref<number>(0)

const loading = ref(false)
const saving = ref(false)
const rows = ref<ColRow[]>([])

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
    const r = await httpGet<ColRow[]>(`/v1/system/module/list-views/${viewId.value}/cols`)
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
    await httpPost('/v1/system/module/list-views/cols/upsert', {
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
