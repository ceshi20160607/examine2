<template>
  <view style="padding: 16px">
    <uni-card :title="`导出模板（modelId=${modelId}）`">
      <view style="display:flex; flex-direction: column; gap: 8px;">
        <view style="display:flex; gap: 8px; flex-wrap: wrap;">
          <uni-easyinput v-model="form.tplCode" placeholder="tplCode" />
          <uni-easyinput v-model="form.tplName" placeholder="tplName" />
        </view>
        <view style="display:flex; gap: 8px; flex-wrap: wrap;">
          <uni-easyinput v-model="form.fileType" placeholder="fileType(默认 csv)" />
          <uni-button type="primary" :disabled="saving || !appId || !modelId" @click="createTpl">创建模板</uni-button>
          <uni-button :disabled="loading" @click="load">刷新</uni-button>
        </view>
        <view style="color:#666">说明：当前后端仅支持 csv 导出/任务。</view>
      </view>
    </uni-card>

    <uni-card title="模板列表" style="margin-top: 12px">
      <uni-list v-if="rows.length">
        <uni-list-item
          v-for="t in rows"
          :key="String(t.id)"
          :title="t.tplName || t.tplCode || ('Tpl#' + t.id)"
          :note="`${t.tplCode || ''} / ${t.fileType || 'csv'}`"
          clickable
          @click="openTplActions(t)"
        />
      </uni-list>
      <view v-else style="color:#666">暂无模板</view>

      <view style="margin-top: 12px; display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button :disabled="!modelId" @click="goJobs">查看导出任务</uni-button>
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

type ExportTpl = {
  id: number | string
  tplCode?: string
  tplName?: string
  fileType?: string
  status?: number
}

const appId = ref<number>(0)
const modelId = ref<number>(0)

const loading = ref(false)
const saving = ref(false)
const rows = ref<ExportTpl[]>([])

const form = reactive<{ tplCode: string; tplName: string; fileType: string }>({
  tplCode: '',
  tplName: '',
  fileType: 'csv'
})

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
})

async function load() {
  if (!modelId.value) return
  loading.value = true
  try {
    const r = await httpGet<ExportTpl[]>(`/v1/system/module/exports/models/${modelId.value}/tpls`)
    rows.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function createTpl() {
  if (!appId.value || !modelId.value) return
  if (!form.tplCode.trim() || !form.tplName.trim()) {
    uni.showToast({ title: '请输入 tplCode/tplName', icon: 'none' })
    return
  }
  const ft = (form.fileType || 'csv').trim() || 'csv'
  if (ft !== 'csv') {
    uni.showToast({ title: 'fileType 仅支持 csv', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await httpPost('/v1/system/module/exports/tpls/upsert', {
      id: null,
      appId: appId.value,
      modelId: modelId.value,
      menuId: null,
      tplCode: form.tplCode.trim(),
      tplName: form.tplName.trim(),
      fileType: 'csv',
      status: 1
    })
    form.tplCode = ''
    form.tplName = ''
    form.fileType = 'csv'
    await load()
  } finally {
    saving.value = false
  }
}

function goJobs() {
  if (!modelId.value) return
  uni.navigateTo({ url: `/pages/system/module/export/jobs?modelId=${modelId.value}` })
}

function goFields(t: ExportTpl) {
  if (!t?.id) return
  uni.navigateTo({
    url: `/pages/system/module/export/tpl_fields?tplId=${t.id}&appId=${appId.value}&modelId=${modelId.value}`
  })
}

function openTplActions(t: ExportTpl) {
  if (!t?.id) return
  uni.showActionSheet({
    itemList: ['配置导出字段', '创建异步导出任务', '仅查看 job 列表'],
    success: (res) => {
      if (res.tapIndex === 0) {
        goFields(t)
        return
      }
      if (res.tapIndex === 1) {
        createJob(t.id)
        return
      }
      if (res.tapIndex === 2) {
        goJobs()
      }
    }
  })
}

async function createJob(tplId: string | number) {
  if (!appId.value || !modelId.value) return
  // 给后端一个最小 query（会走 prepareDslQuery 注入/校验作用域）
  const query = { appId: appId.value, modelId: modelId.value, limit: 200, page: 1 }
  const r = await httpPost<any>(`/v1/system/module/export-jobs/tpls/${tplId}`, query)
  const jobId = r.data?.jobId
  uni.showToast({ title: '任务已创建', icon: 'success' })
  if (jobId) {
    uni.navigateTo({ url: `/pages/system/module/export/job_detail?jobId=${encodeURIComponent(String(jobId))}` })
  } else {
    goJobs()
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  if (!appId.value || !modelId.value) {
    uni.showToast({ title: '缺少 appId/modelId', icon: 'none' })
    return
  }
  load()
})
</script>
