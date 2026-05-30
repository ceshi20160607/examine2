<template>
  <Page :title="`导出模板（modelId=${modelId}）`" subtitle="配置导出字段；支持同步下载与异步任务">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item :label="editingId ? '编辑导出模板' : '新增导出模板'">
          <uni-easyinput v-model="form.tplCode" placeholder="tplCode" />
          <uni-easyinput v-model="form.tplName" placeholder="tplName" />
        </uni-forms-item>
        <uni-forms-item label="fileType">
          <uni-data-select v-model="form.fileType" :localdata="fileTypeOptions" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving || !appId || !modelId" @click="createTpl">{{ editingId ? '保存模板' : '创建模板' }}</uni-button>
        <uni-button v-if="editingId" @click="resetForm">取消编辑</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
        <uni-button :disabled="!modelId" @click="goJobs">查看导出任务</uni-button>
      </ActionBar>
      <view class="u-subtitle">支持 csv / xlsx，同步下载与异步任务会按模板类型生成文件。</view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">模板列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="t in rows"
            :key="String(t.id)"
            :title="t.tplName || t.tplCode || ('Tpl#' + t.id)"
            :note="`${t.tplCode || ''} / ${t.fileType || 'csv'} · ${t.status === 1 ? '启用' : '停用'}`"
            clickable
            @click="openTplActions(t)"
          />
        </uni-list>
        <EmptyState v-else text="暂无模板" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { downloadAuthedToTemp } from '@/api/http'
import { ensureSystemContext, hasToken } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import {
  buildExportTplUrl,
  createExportJob,
  deleteExportTpl,
  listExportTplsByModel,
  type ModuleExportTplRow,
  upsertExportTpl
} from '@/api/module'
import { hasId, idToString, type IdValue } from '@/utils/id'

const appId = ref('')
const modelId = ref('')

const loading = ref(false)
const saving = ref(false)
const editingId = ref<string | null>(null)
const rows = ref<ModuleExportTplRow[]>([])

const form = reactive<{ tplCode: string; tplName: string; fileType: string }>({
  tplCode: '',
  tplName: '',
  fileType: 'csv'
})
const fileTypeOptions = [
  { value: 'csv', text: 'csv' },
  { value: 'xlsx', text: 'xlsx' }
]

onLoad((opts) => {
  appId.value = idToString((opts as any)?.appId)
  modelId.value = idToString((opts as any)?.modelId)
})

async function load() {
  if (!hasId(modelId.value)) return
  loading.value = true
  try {
    const r = await listExportTplsByModel(modelId.value)
    rows.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function createTpl() {
  if (!hasId(appId.value) || !hasId(modelId.value)) return
  if (!form.tplCode.trim() || !form.tplName.trim()) {
    uni.showToast({ title: '请输入 tplCode/tplName', icon: 'none' })
    return
  }
  const ft = (form.fileType || 'csv').trim() || 'csv'
  if (ft !== 'csv' && ft !== 'xlsx') {
    uni.showToast({ title: 'fileType 仅支持 csv/xlsx', icon: 'none' })
    return
  }
  saving.value = true
  try {
    await upsertExportTpl({
      id: editingId.value,
      appId: appId.value,
      modelId: modelId.value,
      menuId: null,
      tplCode: form.tplCode.trim(),
      tplName: form.tplName.trim(),
      fileType: ft,
      status: 1
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
  form.tplCode = ''
  form.tplName = ''
  form.fileType = 'csv'
}

function fillForm(t: ModuleExportTplRow) {
  editingId.value = idToString(t.id as IdValue)
  form.tplCode = t.tplCode || ''
  form.tplName = t.tplName || ''
  form.fileType = t.fileType || 'csv'
}

function goJobs() {
  if (!hasId(modelId.value)) return
  uni.navigateTo({ url: `/pages/system/module/export/jobs?modelId=${encodeURIComponent(modelId.value)}` })
}

function goFields(t: ModuleExportTplRow) {
  const tplId = idToString(t?.id as IdValue)
  if (!hasId(tplId)) return
  uni.navigateTo({
    url: `/pages/system/module/export/tpl_fields?tplId=${encodeURIComponent(tplId)}&appId=${encodeURIComponent(appId.value)}&modelId=${encodeURIComponent(modelId.value)}`
  })
}

function openTplActions(t: ModuleExportTplRow) {
  const tplId = idToString(t?.id as IdValue)
  if (!hasId(tplId)) return
  uni.showActionSheet({
    itemList: ['配置导出字段', `同步导出 ${t.fileType || 'csv'}（下载）`, '创建异步导出任务', '查看导出任务', '编辑模板', '删除模板'],
    success: (res) => {
      if (res.tapIndex === 0) {
        goFields(t)
        return
      }
      if (res.tapIndex === 1) {
        exportFile(t)
        return
      }
      if (res.tapIndex === 2) {
        createJob(tplId)
        return
      }
      if (res.tapIndex === 3) {
        goJobs()
        return
      }
      if (res.tapIndex === 4) {
        fillForm(t)
        return
      }
      if (res.tapIndex === 5) {
        deleteTpl(tplId)
      }
    }
  })
}

async function exportFile(t: ModuleExportTplRow) {
  if (!ensureSystemContext()) return
  if (!hasToken()) return
  const tplId = idToString(t?.id as IdValue)
  if (!hasId(tplId)) return
  const fileType = t.fileType === 'xlsx' ? 'xlsx' : 'csv'
  const url = buildExportTplUrl(tplId, 200, fileType)
  uni.showLoading({ title: '导出中...' })
  try {
    const tempFilePath = await downloadAuthedToTemp(url, '导出失败')
    uni.saveFile({
      tempFilePath,
      success: () => uni.showToast({ title: '已保存', icon: 'success' }),
      fail: () => uni.showToast({ title: '已下载（临时文件）', icon: 'none' })
    })
  } catch (e: any) {
    uni.showToast({ title: e?.message ?? '导出失败', icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

async function deleteTpl(tplId: string | number) {
  uni.showModal({
    title: '确认删除？',
    content: '会级联删除模板字段配置',
    success: async (m) => {
      if (!m.confirm) return
      try {
        await deleteExportTpl([tplId])
        uni.showToast({ title: '已删除', icon: 'success' })
        await load()
      } catch {
        // http.ts 会 toast
      }
    }
  })
}

async function createJob(tplId: string | number) {
  if (!hasId(appId.value) || !hasId(modelId.value)) return
  // 给后端一个最小 query（会走 prepareDslQuery 注入/校验作用域）
  const query = { appId: appId.value, modelId: modelId.value, limit: 200, page: 1 }
  const r = await createExportJob(tplId, query)
  const jobId = r.data?.jobId
  uni.showToast({ title: '任务已创建', icon: 'success' })
  const id = idToString(jobId as IdValue)
  if (id) {
    uni.navigateTo({ url: `/pages/system/module/export/job_detail?jobId=${encodeURIComponent(id)}` })
  } else {
    goJobs()
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  if (!hasId(appId.value) || !hasId(modelId.value)) {
    uni.showToast({ title: '缺少 appId/modelId', icon: 'none' })
    return
  }
  load()
})
</script>
