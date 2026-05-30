<template>
  <Page :title="`模型 Models（appId=${appId}）`" subtitle="创建模型后可配置字段、视图与导出">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item :label="editingId ? '编辑模型' : '创建模型'">
          <view style="display:flex; gap: 8px; flex-wrap: wrap; align-items: center;">
            <uni-easyinput v-model="form.modelCode" placeholder="modelCode（如 order）" style="flex:1; min-width: 160px" />
            <uni-easyinput v-model="form.modelName" placeholder="modelName" style="flex:1; min-width: 160px" />
          </view>
        </uni-forms-item>
        <uni-forms-item label="状态">
          <uni-data-select v-model="form.status" :localdata="statusOptions" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="save">{{ editingId ? '保存' : '创建' }}</uni-button>
        <uni-button v-if="editingId" @click="resetForm">取消编辑</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="models.length">
          <uni-list-item
            v-for="m in models"
            :key="m.id"
            :title="(m.modelName || m.modelCode || ('Model#' + m.id))"
            :note="`${m.modelCode || ''} · ${m.status === 1 ? '启用' : '停用'}`"
            clickable
            @click="openActions(m)"
          />
        </uni-list>
        <EmptyState v-else text="暂无模型" />
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
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import { deleteModels, listModelsByApp, type ModuleModel, upsertModel } from '@/api/meta'
import { hasId, idToString, type IdValue } from '@/utils/id'

const appId = ref('')
const models = ref<ModuleModel[]>([])
const saving = ref(false)
const editingId = ref<string | null>(null)
const { loading, error, run, capture, clearError } = usePageRequest()
const form = reactive<{ modelCode: string; modelName: string; status: number }>({ modelCode: '', modelName: '', status: 1 })

const statusOptions = [
  { value: 1, text: '启用' },
  { value: 2, text: '停用' }
]

onLoad((opts) => {
  appId.value = idToString((opts as any)?.appId)
})

async function load() {
  if (!hasId(appId.value)) {
    uni.showToast({ title: '缺少 appId', icon: 'none' })
    return
  }
  await run(async () => {
    const r = await listModelsByApp(appId.value)
    models.value = r.data || []
  })
}

function resetForm() {
  editingId.value = null
  form.modelCode = ''
  form.modelName = ''
  form.status = 1
}

function fillForm(m: ModuleModel) {
  editingId.value = idToString(m.id as IdValue)
  form.modelCode = m.modelCode || ''
  form.modelName = m.modelName || ''
  form.status = m.status === 2 ? 2 : 1
}

async function save() {
  if (!hasId(appId.value)) return
  if (!form.modelCode.trim() || !form.modelName.trim()) {
    uni.showToast({ title: '请输入 modelCode/modelName', icon: 'none' })
    return
  }
  saving.value = true
  clearError()
  try {
    await upsertModel({
      id: editingId.value,
      appId: appId.value,
      modelCode: form.modelCode.trim(),
      modelName: form.modelName.trim(),
      status: form.status,
      remark: null
    })
    resetForm()
    uni.showToast({ title: '已保存', icon: 'success' })
    await load()
  } catch (e: unknown) {
    capture(e)
  } finally {
    saving.value = false
  }
}

function goFields(m: ModuleModel) {
  uni.navigateTo({ url: `/pages/system/module/meta/fields?appId=${encodeURIComponent(appId.value)}&modelId=${encodeURIComponent(idToString(m.id as IdValue))}` })
}

function remove(m: ModuleModel) {
  const id = idToString(m.id as IdValue)
  if (!hasId(id)) return
  uni.showModal({
    title: '删除模型？',
    content: m.modelName || m.modelCode || id,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteModels([id])
        if (editingId.value === id) resetForm()
        uni.showToast({ title: '已删除', icon: 'success' })
        await load()
      } catch (e: unknown) {
        capture(e)
      }
    }
  })
}

function openActions(m: ModuleModel) {
  const modelId = idToString(m?.id as IdValue)
  if (!hasId(modelId)) return
  uni.showActionSheet({
    itemList: ['Fields', 'List Views', 'Exports', 'Flow Bindings', 'Records', 'Relations', '编辑', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) {
        goFields(m)
        return
      }
      if (res.tapIndex === 1) {
        uni.navigateTo({ url: `/pages/system/module/listviews/views?appId=${encodeURIComponent(appId.value)}&modelId=${encodeURIComponent(modelId)}` })
        return
      }
      if (res.tapIndex === 2) {
        uni.navigateTo({ url: `/pages/system/module/export/tpls?appId=${encodeURIComponent(appId.value)}&modelId=${encodeURIComponent(modelId)}` })
        return
      }
      if (res.tapIndex === 3) {
        uni.navigateTo({ url: `/pages/system/module/flow/bindings?appId=${encodeURIComponent(appId.value)}&modelId=${encodeURIComponent(modelId)}` })
        return
      }
      if (res.tapIndex === 4) {
        uni.navigateTo({ url: `/pages/system/records/list?appId=${encodeURIComponent(appId.value)}&modelId=${encodeURIComponent(modelId)}` })
        return
      }
      if (res.tapIndex === 5) {
        uni.navigateTo({ url: `/pages/system/module/meta/relations?appId=${encodeURIComponent(appId.value)}` })
        return
      }
      if (res.tapIndex === 6) {
        fillForm(m)
        return
      }
      if (res.tapIndex === 7) {
        remove(m)
      }
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>
