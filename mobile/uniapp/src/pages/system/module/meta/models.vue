<template>
  <view style="padding: 16px">
    <uni-card :title="`模型 Models（appId=${appId}）`">
      <view style="display:flex; gap: 8px;">
        <uni-easyinput v-model="form.modelCode" placeholder="modelCode (如 order)" />
        <uni-easyinput v-model="form.modelName" placeholder="modelName" />
        <uni-button type="primary" :disabled="saving" @click="create">创建</uni-button>
      </view>
    </uni-card>

    <uni-card title="列表" style="margin-top: 12px">
      <uni-list v-if="models.length">
        <uni-list-item
          v-for="m in models"
          :key="m.id"
          :title="(m.modelName || m.modelCode || ('Model#' + m.id))"
          :note="m.modelCode || ''"
          clickable
          @click="openActions(m)"
        />
      </uni-list>
      <view v-else style="color:#666">暂无模型</view>
      <view style="margin-top: 12px">
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

type ModuleModel = { id: number; appId?: number; modelCode?: string; modelName?: string; status?: number }

const appId = ref<number>(0)
const models = ref<ModuleModel[]>([])
const loading = ref(false)
const saving = ref(false)
const form = reactive({ modelCode: '', modelName: '' })

onLoad((opts) => {
  const v = Number((opts as any)?.appId || 0)
  appId.value = Number.isFinite(v) ? v : 0
})

async function load() {
  if (!appId.value) {
    uni.showToast({ title: '缺少 appId', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const r = await httpGet<ModuleModel[]>(`/v1/system/module/meta/apps/${appId.value}/models`)
    models.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!appId.value) return
  if (!form.modelCode.trim() || !form.modelName.trim()) {
    uni.showToast({ title: '请输入 modelCode/modelName', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await httpPost('/v1/system/module/meta/models/upsert', {
      id: null,
      appId: appId.value,
      modelCode: form.modelCode.trim(),
      modelName: form.modelName.trim(),
      status: 1,
      remark: null
    })
    form.modelCode = ''
    form.modelName = ''
    await load()
  } finally {
    saving.value = false
  }
}

function goFields(m: ModuleModel) {
  uni.navigateTo({ url: `/pages/system/module/meta/fields?appId=${appId.value}&modelId=${m.id}` })
}

function openActions(m: ModuleModel) {
  if (!m?.id) return
  uni.showActionSheet({
    itemList: ['Fields', 'List Views', 'Exports'],
    success: (res) => {
      if (res.tapIndex === 0) {
        goFields(m)
        return
      }
      if (res.tapIndex === 1) {
        uni.navigateTo({ url: `/pages/system/module/listviews/views?appId=${appId.value}&modelId=${m.id}` })
        return
      }
      if (res.tapIndex === 2) {
        uni.navigateTo({ url: `/pages/system/module/export/tpls?appId=${appId.value}&modelId=${m.id}` })
      }
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>

