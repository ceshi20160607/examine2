<template>
  <view style="padding: 16px">
    <uni-card :title="`导出字段（tplId=${tplId}）`">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-easyinput v-model="form.fieldId" placeholder="fieldId" />
        <uni-easyinput v-model="form.colTitle" placeholder="colTitle" />
        <uni-easyinput v-model="form.sortNo" placeholder="sortNo(默认 0)" />
        <uni-button type="primary" :disabled="saving" @click="upsert">新增字段</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
        <uni-button :disabled="!modelId" @click="goFields">查看模型字段</uni-button>
      </view>
      <view style="margin-top: 8px; color:#666">formatJson：暂不做 UI 编辑，可后续增强。</view>
    </uni-card>

    <uni-card title="字段列表" style="margin-top: 12px">
      <uni-list v-if="rows.length">
        <uni-list-item
          v-for="f in rows"
          :key="String(f.id)"
          :title="f.colTitle || ('Field#' + f.id)"
          :note="`fieldId=${f.fieldId || ''} sortNo=${f.sortNo ?? ''}`"
          clickable
          @click="openFieldActions(f)"
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
  formatJson?: string
}

const tplId = ref<number>(0)
const appId = ref<number>(0)
const modelId = ref<number>(0)

const loading = ref(false)
const saving = ref(false)
const rows = ref<FieldRow[]>([])

const editingId = ref<string | number | null>(null)
const form = reactive<{ fieldId: string; colTitle: string; sortNo: string }>({ fieldId: '', colTitle: '', sortNo: '0' })

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
  const sortNo = Number((form.sortNo || '0').trim() || '0')
  if (Number.isNaN(sortNo)) {
    uni.showToast({ title: 'sortNo 非法', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await httpPost('/v1/system/module/exports/fields/upsert', {
      id: editingId.value ?? null,
      tplId: tplId.value,
      fieldId,
      colTitle: form.colTitle.trim(),
      sortNo,
      formatJson: null
    })
    editingId.value = null
    form.fieldId = ''
    form.colTitle = ''
    form.sortNo = '0'
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

function openFieldActions(f: FieldRow) {
  if (!f?.id) return
  uni.showActionSheet({
    itemList: ['编辑', '删除字段配置'],
    success: (res) => {
      if (res.tapIndex === 0) {
        editingId.value = f.id
        form.fieldId = String(f.fieldId || '')
        form.colTitle = String(f.colTitle || '')
        form.sortNo = String(f.sortNo ?? 0)
        return
      }
      if (res.tapIndex === 1) {
        deleteField(f.id)
      }
    }
  })
}

function deleteField(id: string | number) {
  uni.showModal({
    title: '确认删除？',
    content: '仅删除导出字段映射，不会删除真实字段元数据',
    success: async (m) => {
      if (!m.confirm) return
      try {
        await httpPost('/v1/system/module/exports/fields/delete', { ids: [id] })
        uni.showToast({ title: '已删除', icon: 'success' })
        await load()
      } catch {
        // http.ts 会 toast
      }
    }
  })
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
