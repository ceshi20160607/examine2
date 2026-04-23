<template>
  <view style="padding: 16px">
    <uni-card :title="`导出字段（tplId=${tplId}）`">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-easyinput v-model="form.fieldId" placeholder="fieldId" />
        <uni-easyinput v-model="form.colTitle" placeholder="colTitle" />
        <uni-button type="primary" :disabled="saving" @click="upsert">新增字段</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
        <uni-button :disabled="!modelId" @click="goFields">查看模型字段</uni-button>
      </view>
    </uni-card>

    <uni-card title="字段列表" style="margin-top: 12px">
      <uni-list v-if="rows.length">
        <uni-list-item
          v-for="f in rows"
          :key="String(f.id)"
          :title="f.colTitle || ('Field#' + f.id)"
          :note="`fieldId=${f.fieldId || ''}`"
        />
      </uni-list>
      <view v-else style="color:#666">暂无字段</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

type FieldRow = {
  id: number | string
  tplId?: number | string
  fieldId?: number | string
  colTitle?: string
  sortNo?: number
}

const tplId = ref<number>(0)
const appId = ref<number>(0)
const modelId = ref<number>(0)

const loading = ref(false)
const saving = ref(false)
const rows = ref<FieldRow[]>([])

const form = reactive<{ fieldId: string; colTitle: string }>({ fieldId: '', colTitle: '' })

onLoad((opts) => {
  tplId.value = Number((opts as any)?.tplId || 0) || 0
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
})

async function load() {
  if (!tplId.value) return
  loading.value = true
  try {
    const r = await httpGet<FieldRow[]>(`/v1/system/module/exports/tpls/${tplId.value}/fields`)
    rows.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function upsert() {
  if (!tplId.value) return
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
    await httpPost('/v1/system/module/exports/fields/upsert', {
      id: null,
      tplId: tplId.value,
      fieldId,
      colTitle: form.colTitle.trim(),
      sortNo: 0,
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
    uni.showToast({ title: '缺少 appId/modelId（请从 Models 进入）', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/system/module/meta/fields?appId=${appId.value}&modelId=${modelId.value}` })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  if (!tplId.value) {
    uni.showToast({ title: '缺少 tplId', icon: 'none' })
    return
  }
  load()
})
</script>
