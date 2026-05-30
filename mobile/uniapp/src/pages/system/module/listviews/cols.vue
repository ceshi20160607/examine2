<template>
  <Page :title="`列配置 viewId=${viewId}`" subtitle="配置列表展示字段、宽度、固定列和格式">
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
        <uni-forms-item label="列标题">
          <uni-easyinput v-model="form.colTitle" placeholder="默认使用字段名称" />
        </uni-forms-item>
        <uni-forms-item label="宽度(px，可空)">
          <uni-easyinput v-model="form.width" type="number" placeholder="如 160" />
        </uni-forms-item>
        <uni-forms-item label="排序号">
          <uni-easyinput v-model="form.sortNo" type="number" placeholder="数字越小越靠前" />
        </uni-forms-item>
        <uni-forms-item label="是否显示">
          <uni-data-select v-model="form.visibleFlag" :localdata="flagOptions" />
        </uni-forms-item>
        <uni-forms-item label="固定列">
          <uni-data-select v-model="form.fixedType" :localdata="fixedOptions" />
        </uni-forms-item>
        <uni-forms-item label="格式配置 JSON">
          <uni-easyinput
            v-model="form.formatJson"
            type="textarea"
            autoHeight
            placeholder='例如 {"emptyText":"-","dateOnly":true,"numberScale":2}'
          />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="upsert">{{ editingId ? '保存修改' : '新增列' }}</uni-button>
        <uni-button v-if="editingId" :disabled="saving" @click="resetForm">取消编辑</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
        <uni-button :disabled="!appId || !modelId" @click="goFields">查看字段</uni-button>
      </ActionBar>
      <view class="quick-row">
        <uni-button size="mini" @click="applyFormatPreset('empty')">空值 -</uni-button>
        <uni-button size="mini" @click="applyFormatPreset('date')">日期</uni-button>
        <uni-button size="mini" @click="applyFormatPreset('number')">数字2位</uni-button>
      </view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="rows.length">
          <uni-list-item
            v-for="c in rows"
            :key="c.id"
            :title="c.colTitle || fieldLabel(c.fieldId) || ('Col#' + c.id)"
            :note="colNote(c)"
            clickable
            @click="openColActions(c)"
          />
        </uni-list>
        <EmptyState v-else text="暂无列配置" />
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
import { deleteViewCols, listViewCols, type ModuleListViewColRow, upsertViewCol } from '@/api/module'
import { hasId, idToString, type IdValue } from '@/utils/id'

const viewId = ref('')
const appId = ref('')
const modelId = ref('')

const loading = ref(false)
const saving = ref(false)
const editingId = ref<string | number | null>(null)
const rows = ref<ModuleListViewColRow[]>([])
const modelFields = ref<ModuleField[]>([])

const form = reactive<{
  fieldId: string
  colTitle: string
  width: string
  sortNo: string
  visibleFlag: number
  fixedType: string
  formatJson: string
}>({
  fieldId: '',
  colTitle: '',
  width: '',
  sortNo: '0',
  visibleFlag: 1,
  fixedType: '',
  formatJson: ''
})

const flagOptions = [
  { value: 1, text: '显示' },
  { value: 0, text: '隐藏' }
]

const fixedOptions = [
  { value: '', text: '不固定' },
  { value: 'left', text: '左固定' },
  { value: 'right', text: '右固定' }
]

const fieldOptions = computed(() =>
  modelFields.value.map((f) => ({
    text: `${f.fieldName || f.fieldCode || f.id} (${f.fieldCode || f.id})`,
    value: String(f.id)
  }))
)

onLoad((opts) => {
  viewId.value = idToString((opts as any)?.viewId)
  appId.value = idToString((opts as any)?.appId)
  modelId.value = idToString((opts as any)?.modelId)
})

async function load() {
  if (!hasId(viewId.value)) return
  loading.value = true
  try {
    const r = await listViewCols(viewId.value)
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
  if (!hasId(viewId.value)) return
  const fieldId = idToString(form.fieldId)
  if (!hasId(fieldId)) {
    uni.showToast({ title: '请选择字段', icon: 'none' })
    return
  }
  const selected = findField(fieldId)
  const title = form.colTitle.trim() || selected?.fieldName || selected?.fieldCode || ''
  if (!title) {
    uni.showToast({ title: '请输入列标题', icon: 'none' })
    return
  }
  const sortNo = parseNumber(form.sortNo, '排序号')
  if (sortNo === false) return
  const width = parseOptionalNumber(form.width, '宽度')
  if (width === false) return
  const formatJson = normalizeFormatJson()
  if (formatJson === false) return

  saving.value = true
  try {
    await upsertViewCol({
      id: editingId.value ?? null,
      viewId: viewId.value,
      fieldId,
      colTitle: title,
      width,
      sortNo,
      visibleFlag: form.visibleFlag,
      fixedType: form.fixedType || null,
      formatJson
    })
    resetForm()
    uni.showToast({ title: '已保存', icon: 'success' })
    await load()
  } finally {
    saving.value = false
  }
}

function parseNumber(raw: string, label: string): number | false {
  const n = Number((raw || '0').trim() || '0')
  if (Number.isNaN(n)) {
    uni.showToast({ title: `${label}不合法`, icon: 'none' })
    return false
  }
  return n
}

function parseOptionalNumber(raw: string, label: string): number | null | false {
  const text = (raw || '').trim()
  if (!text) return null
  const n = Number(text)
  if (Number.isNaN(n)) {
    uni.showToast({ title: `${label}不合法`, icon: 'none' })
    return false
  }
  return n
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
  form.width = ''
  form.sortNo = String(rows.value.length + 1)
  form.visibleFlag = 1
  form.fixedType = ''
  form.formatJson = ''
}

function fillForm(c: ModuleListViewColRow) {
  editingId.value = idToString(c.id as IdValue)
  form.fieldId = idToString(c.fieldId as IdValue)
  form.colTitle = c.colTitle || ''
  form.width = c.width == null ? '' : String(c.width)
  form.sortNo = String(c.sortNo ?? 0)
  form.visibleFlag = c.visibleFlag ?? 1
  form.fixedType = c.fixedType || ''
  form.formatJson = prettyJson(c.formatJson)
}

function onFieldChange(value: string | number) {
  const selected = findField(value)
  if (selected && !form.colTitle.trim()) {
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

function colNote(c: ModuleListViewColRow) {
  const parts = [fieldLabel(c.fieldId) || `fieldId=${c.fieldId || ''}`, `sortNo=${c.sortNo ?? ''}`]
  if (c.width) parts.push(`width=${c.width}`)
  if (c.visibleFlag === 0) parts.push('隐藏')
  if (c.fixedType) parts.push(`固定=${c.fixedType}`)
  if (c.formatJson) parts.push('已配置格式')
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

function applyFormatPreset(type: 'empty' | 'date' | 'number') {
  const current = normalizeFormatJsonForMerge()
  if (current === false) return
  const presets = {
    empty: { emptyText: '-' },
    date: { dateOnly: true },
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

function openColActions(c: ModuleListViewColRow) {
  const id = idToString(c?.id as IdValue)
  if (!hasId(id)) return
  uni.showActionSheet({
    itemList: ['编辑', '删除列配置'],
    success: (res) => {
      if (res.tapIndex === 0) {
        fillForm(c)
        return
      }
      if (res.tapIndex === 1) {
        deleteCol(id)
      }
    }
  })
}

function deleteCol(id: string | number) {
  uni.showModal({
    title: '确认删除？',
    content: '只删除列表列配置，不会删除模型字段。',
    success: async (m) => {
      if (!m.confirm) return
      try {
        await deleteViewCols([id])
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
  if (!hasId(viewId.value)) {
    uni.showToast({ title: '缺少 viewId', icon: 'none' })
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
