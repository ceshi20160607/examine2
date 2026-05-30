<template>
  <Page :title="`导出字段 tplId=${tplId}`" subtitle="配置导出列、字段顺序和输出格式">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item label="模型字段">
          <uni-data-select
            v-if="fieldOptions.length"
            v-model="form.fieldId"
            :localdata="fieldOptions"
            placeholder="选择模型字段"
            @change="onFieldChange"
          />
          <uni-easyinput v-else v-model="form.fieldId" placeholder="fieldId" />
        </uni-forms-item>
        <uni-forms-item label="导出列标题">
          <uni-easyinput v-model="form.colTitle" placeholder="默认使用字段名称" />
        </uni-forms-item>
        <uni-forms-item label="排序号">
          <uni-easyinput v-model="form.sortNo" type="number" placeholder="数字越小越靠前" />
        </uni-forms-item>
        <uni-forms-item label="格式配置 JSON">
          <uni-easyinput
            v-model="form.formatJson"
            type="textarea"
            autoHeight
            placeholder='例如 {"trim":true,"emptyText":"-","dateOnly":true,"numberScale":2}'
          />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="upsert">{{ editingId ? '保存修改' : '新增字段' }}</uni-button>
        <uni-button v-if="editingId" :disabled="saving" @click="resetForm">取消编辑</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
        <uni-button :disabled="!modelId" @click="goFields">查看模型字段</uni-button>
      </ActionBar>
      <view class="quick-row">
        <uni-button size="mini" @click="applyFormatPreset('empty')">空值 -</uni-button>
        <uni-button size="mini" @click="applyFormatPreset('date')">日期</uni-button>
        <uni-button size="mini" @click="applyFormatPreset('bool')">布尔中文</uni-button>
        <uni-button size="mini" @click="applyFormatPreset('number')">数字2位</uni-button>
      </view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">字段列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="f in rows"
            :key="String(f.id)"
            :title="f.colTitle || fieldLabel(f.fieldId) || ('Field#' + f.id)"
            :note="fieldNote(f)"
            clickable
            @click="openFieldActions(f)"
          />
        </uni-list>
        <EmptyState v-else text="暂无字段" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import { listFieldsByModel, type ModuleField } from '@/api/meta'
import {
  deleteExportTplField,
  listExportTplFields,
  type ModuleExportTplFieldRow,
  upsertExportTplField
} from '@/api/module'
import { hasId, idToString, type IdValue } from '@/utils/id'

const tplId = ref('')
const appId = ref('')
const modelId = ref('')

const loading = ref(false)
const saving = ref(false)
const rows = ref<ModuleExportTplFieldRow[]>([])
const modelFields = ref<ModuleField[]>([])

const editingId = ref<string | number | null>(null)
const form = reactive<{ fieldId: string; colTitle: string; sortNo: string; formatJson: string }>({
  fieldId: '',
  colTitle: '',
  sortNo: '0',
  formatJson: ''
})

const fieldOptions = computed(() =>
  modelFields.value.map((f) => ({
    text: `${f.fieldName || f.fieldCode || f.id} (${f.fieldCode || f.id})`,
    value: String(f.id)
  }))
)

onLoad((opts) => {
  tplId.value = idToString((opts as any)?.tplId)
  appId.value = idToString((opts as any)?.appId)
  modelId.value = idToString((opts as any)?.modelId)
})

async function load() {
  if (!hasId(tplId.value)) return
  loading.value = true
  try {
    const r = await listExportTplFields(tplId.value)
    rows.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function loadModelFields() {
  if (!hasId(modelId.value)) return
  try {
    const r = await listFieldsByModel(modelId.value)
    modelFields.value = r.data || []
  } catch {
    modelFields.value = []
  }
}

async function upsert() {
  if (!hasId(tplId.value)) return
  const fieldId = idToString(form.fieldId)
  if (!hasId(fieldId)) {
    uni.showToast({ title: '请选择字段', icon: 'none' })
    return
  }
  const selected = findField(fieldId)
  const title = form.colTitle.trim() || selected?.fieldName || selected?.fieldCode || ''
  if (!title) {
    uni.showToast({ title: '请输入导出列标题', icon: 'none' })
    return
  }
  const sortNo = Number((form.sortNo || '0').trim() || '0')
  if (Number.isNaN(sortNo)) {
    uni.showToast({ title: '排序号不合法', icon: 'none' })
    return
  }
  const formatJson = normalizeFormatJson()
  if (formatJson === false) return
  saving.value = true
  try {
    await upsertExportTplField({
      id: editingId.value ?? null,
      tplId: tplId.value,
      fieldId,
      colTitle: title,
      sortNo,
      formatJson
    })
    resetForm()
    uni.showToast({ title: '已保存', icon: 'success' })
    await load()
  } finally {
    saving.value = false
  }
}

function normalizeFormatJson(): string | null | false {
  const raw = form.formatJson.trim()
  if (!raw) return null
  try {
    const parsed = JSON.parse(raw)
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      throw new Error('formatJson 必须是 JSON 对象')
    }
    return JSON.stringify(parsed)
  } catch (e: any) {
    uni.showToast({ title: e?.message || 'formatJson 不合法', icon: 'none' })
    return false
  }
}

function resetForm() {
  editingId.value = null
  form.fieldId = ''
  form.colTitle = ''
  form.sortNo = String(rows.value.length + 1)
  form.formatJson = ''
}

function fillForm(f: ModuleExportTplFieldRow) {
  editingId.value = idToString(f.id as IdValue)
  form.fieldId = idToString(f.fieldId as IdValue)
  form.colTitle = String(f.colTitle || '')
  form.sortNo = String(f.sortNo ?? 0)
  form.formatJson = prettyJson(f.formatJson)
}

function onFieldChange(value: string | number) {
  const selected = findField(value)
  if (!selected) return
  if (!form.colTitle.trim()) {
    form.colTitle = selected.fieldName || selected.fieldCode || ''
  }
}

function findField(id: IdValue | null | undefined) {
  const text = idToString(id)
  return modelFields.value.find((f) => String(f.id) === text)
}

function fieldLabel(fieldId: IdValue | null | undefined) {
  const f = findField(fieldId)
  return f ? `${f.fieldName || f.fieldCode} (${f.fieldCode || f.id})` : ''
}

function fieldNote(f: ModuleExportTplFieldRow) {
  const parts = [`fieldId=${f.fieldId || ''}`, `sortNo=${f.sortNo ?? ''}`]
  if (f.formatJson) parts.push('已配置格式')
  const label = fieldLabel(f.fieldId)
  if (label) parts.unshift(label)
  return parts.join(' / ')
}

function prettyJson(value: any) {
  if (!value) return ''
  if (typeof value !== 'string') return JSON.stringify(value, null, 2)
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function applyFormatPreset(type: 'empty' | 'date' | 'bool' | 'number') {
  const current = normalizeFormatJsonForMerge()
  if (current === false) return
  const presets = {
    empty: { emptyText: '-', trim: true },
    date: { dateOnly: true },
    bool: { mappings: { '1': '是', '0': '否', true: '是', false: '否' } },
    number: { numberScale: 2 }
  }
  form.formatJson = JSON.stringify({ ...current, ...presets[type] }, null, 2)
}

function normalizeFormatJsonForMerge(): Record<string, any> | false {
  const raw = form.formatJson.trim()
  if (!raw) return {}
  try {
    const parsed = JSON.parse(raw)
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      throw new Error('formatJson 必须是 JSON 对象')
    }
    return parsed
  } catch (e: any) {
    uni.showToast({ title: e?.message || 'formatJson 不合法', icon: 'none' })
    return false
  }
}

function goFields() {
  if (!hasId(appId.value) || !hasId(modelId.value)) {
    uni.showToast({ title: '缺少 appId/modelId', icon: 'none' })
    return
  }
  uni.navigateTo({ url: `/pages/system/module/meta/fields?appId=${encodeURIComponent(appId.value)}&modelId=${encodeURIComponent(modelId.value)}` })
}

function openFieldActions(f: ModuleExportTplFieldRow) {
  const id = idToString(f?.id as IdValue)
  if (!hasId(id)) return
  uni.showActionSheet({
    itemList: ['编辑', '删除字段配置'],
    success: (res) => {
      if (res.tapIndex === 0) {
        fillForm(f)
        return
      }
      if (res.tapIndex === 1) {
        deleteField(id)
      }
    }
  })
}

function deleteField(id: string | number) {
  uni.showModal({
    title: '确认删除？',
    content: '只删除导出字段映射，不会删除真实字段元数据。',
    success: async (m) => {
      if (!m.confirm) return
      try {
        await deleteExportTplField([id])
        uni.showToast({ title: '已删除', icon: 'success' })
        if (editingId.value && String(editingId.value) === String(id)) resetForm()
        await load()
      } catch {
        // http.ts shows toast
      }
    }
  })
}

onMounted(async () => {
  if (!ensureSystemContext()) return
  if (!hasId(tplId.value)) {
    uni.showToast({ title: '缺少 tplId', icon: 'none' })
    return
  }
  resetForm()
  await Promise.all([loadModelFields(), load()])
})
</script>

<style scoped>
.quick-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}
</style>
