<template>
  <view style="padding: 16px">
    <uni-card :title="`筛选模板（modelId=${modelId}）`">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-easyinput v-model="form.tplCode" placeholder="tplCode" />
        <uni-easyinput v-model="form.tplName" placeholder="tplName" />
        <uni-easyinput v-model="form.menuId" placeholder="menuId(可选)" />
        <uni-button type="primary" :disabled="saving" @click="upsert">创建</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </view>
    </uni-card>

    <uni-card title="列表" style="margin-top: 12px">
      <uni-list v-if="rows.length">
        <uni-list-item
          v-for="t in rows"
          :key="t.id"
          :title="t.tplName || t.tplCode || ('Tpl#' + t.id)"
          :note="t.tplCode || ''"
        />
      </uni-list>
      <view v-else style="color:#666">暂无模板</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

type FilterTplRow = { id: number; tplCode?: string; tplName?: string; menuId?: number; status?: number }

const appId = ref<number>(0)
const modelId = ref<number>(0)
const loading = ref(false)
const saving = ref(false)
const rows = ref<FilterTplRow[]>([])

const form = reactive<{ tplCode: string; tplName: string; menuId: string }>({ tplCode: '', tplName: '', menuId: '' })

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
})

async function load() {
  if (!modelId.value) return
  loading.value = true
  try {
    const r = await httpGet<FilterTplRow[]>(`/v1/system/module/list-views/models/${modelId.value}/filter-tpls`)
    rows.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function upsert() {
  if (!appId.value || !modelId.value) return
  if (!form.tplCode.trim() || !form.tplName.trim()) {
    uni.showToast({ title: '请输入 tplCode/tplName', icon: 'none' })
    return
  }
  const menuIdRaw = form.menuId.trim()
  const menuId = menuIdRaw ? Number(menuIdRaw) : null
  if (menuIdRaw && (!menuId || Number.isNaN(menuId))) {
    uni.showToast({ title: 'menuId 非法', icon: 'none' })
    return
  }

  saving.value = true
  try {
    await httpPost('/v1/system/module/list-views/filter-tpls/upsert', {
      id: null,
      appId: appId.value,
      modelId: modelId.value,
      menuId,
      tplCode: form.tplCode.trim(),
      tplName: form.tplName.trim(),
      status: 1
    })
    form.tplCode = ''
    form.tplName = ''
    form.menuId = ''
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>
