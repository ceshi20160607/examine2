<template>
  <Page :title="`字段 Fields（modelId=${modelId}）`" subtitle="配置类型、字典、必填与排序">
    <view class="u-card u-section">
      <view class="u-title">{{ editingId ? '编辑字段' : '新建字段' }}</view>
      <uni-forms labelPosition="top">
        <uni-forms-item label="fieldCode">
          <uni-easyinput v-model="form.fieldCode" :disabled="!!editingId" placeholder="如 title" />
        </uni-forms-item>
        <uni-forms-item label="fieldName">
          <uni-easyinput v-model="form.fieldName" placeholder="显示名称" />
        </uni-forms-item>
        <uni-forms-item label="fieldType">
          <uni-data-select v-model="form.fieldType" :localdata="fieldTypeOptions" @change="onFieldTypeChange" />
        </uni-forms-item>
        <uni-forms-item v-if="needsDict" label="dictCode">
          <uni-data-select v-model="form.dictCode" :localdata="dictCodeOptions" placeholder="选择字典" />
        </uni-forms-item>
        <uni-forms-item v-if="needsRef" label="关联模型">
          <uni-data-select v-model="form.refModelId" :localdata="modelOptions" placeholder="选择已建模块" @change="onRefModelChange" />
        </uni-forms-item>
        <uni-forms-item v-if="needsRef && form.refModelId" label="关联模块展示名">
          <uni-easyinput v-model="form.relationModuleLabel" placeholder="子表/选择器标题，默认可取目标模型名称" />
        </uni-forms-item>
        <uni-forms-item v-if="needsRef && form.refModelId" label="展示字段">
          <uni-data-select
            v-model="form.refDisplayField"
            :localdata="refDisplayFieldOptions"
            placeholder="目标记录用于展示的字段"
          />
        </uni-forms-item>
        <!-- 类型扩展配置 -->
        <uni-forms-item v-if="form.fieldType === 'TEXT'" label="输入样式">
          <uni-data-select v-model="typeConfig.inputStyle" :localdata="textStyleOptions" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'TEXT'" label="最大长度">
          <uni-easyinput v-model="typeConfig.maxLength" type="number" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'MONEY' || form.fieldType === 'PERCENT'" label="小数位数">
          <uni-easyinput v-model="typeConfig.decimalPlaces" type="number" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'DATETIME' || form.fieldType === 'DATE_RANGE'" label="时间格式">
          <uni-data-select v-model="typeConfig.pickerMode" :localdata="pickerModeOptions" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'DATETIME' || form.fieldType === 'DATE_RANGE'" label="最早时间">
          <uni-datetime-picker v-model="typeConfig.min" :type="datePickerTypeForConfig" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'DATETIME' || form.fieldType === 'DATE_RANGE'" label="最晚时间">
          <uni-datetime-picker v-model="typeConfig.max" :type="datePickerTypeForConfig" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'SELECT' || form.fieldType === 'MULTI_SELECT'" label="展示样式">
          <uni-data-select v-model="typeConfig.displayStyle" :localdata="selectStyleOptions" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'REF_MODULE'" label="展示样式">
          <uni-data-select v-model="typeConfig.displayStyle" :localdata="refStyleOptions" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'REF_MODULE'" label="子表/明细模式">
          <switch :checked="!!typeConfig.subTable" @change="(e: any) => onSubTableToggle(e)" />
          <view class="u-subtitle">开启后等同 displayStyle=table，存多条关联 recordId</view>
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'REF_MODULE'" label="多选关联">
          <switch :checked="!!typeConfig.multi" @change="(e: any) => (typeConfig.multi = e.detail.value)" />
        </uni-forms-item>
        <uni-forms-item
          v-if="form.fieldType === 'REF_MODULE' && refNeedsListFields"
          label="列表/子表展示列"
        >
          <uni-data-checkbox v-model="typeConfig.listFields" multiple :localdata="refDisplayFieldOptions" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'ADDRESS'" label="地区样式">
          <uni-data-select v-model="typeConfig.regionStyle" :localdata="regionStyleOptions" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'ADDRESS'" label="地图选点">
          <switch :checked="!!typeConfig.includeLocation" @change="(e: any) => onAddressMapToggle(e)" />
          <view class="u-subtitle">开启后填写时可调起地图选点（含经纬度）</view>
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'PERSON' || form.fieldType === 'DEPARTMENT'" label="可选范围">
          <uni-data-select v-model="typeConfig.scope" :localdata="scopeOptions" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'PERSON' || form.fieldType === 'DEPARTMENT'" label="允许多选">
          <switch :checked="!!typeConfig.multi" @change="(e: any) => (typeConfig.multi = e.detail.value)" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'ADDRESS'" label="详细地址">
          <uni-data-select v-model="typeConfig.detailMode" :localdata="detailModeOptions" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'TITLE'" label="标题内容">
          <uni-easyinput v-model="typeConfig.content" placeholder="占位标题文案" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'TAG'" label="预设标签">
          <uni-easyinput v-model="typeConfig.tagsText" placeholder="逗号分隔，如 紧急,待办" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'TAG'" label="允许自定义">
          <switch :checked="typeConfig.allowCustom !== false" @change="(e: any) => (typeConfig.allowCustom = e.detail.value)" />
        </uni-forms-item>
        <uni-forms-item v-if="form.fieldType === 'SERIAL_NO'" label="编号规则">
          <SerialSegmentBuilder v-model="typeConfig.segments" />
        </uni-forms-item>
        <uni-forms-item label="sortNo">
          <uni-easyinput v-model="form.sortNo" type="number" placeholder="排序，越小越靠前" />
        </uni-forms-item>
        <uni-forms-item label="tips">
          <uni-easyinput v-model="form.tips" placeholder="填写提示（可选）" />
        </uni-forms-item>
        <uni-forms-item label="defaultValue">
          <uni-easyinput v-model="form.defaultValue" placeholder="默认值（可选）" />
        </uni-forms-item>
        <uni-forms-item label="属性">
          <uni-data-checkbox v-model="form.flags" multiple :localdata="flagOptions" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="save">{{ editingId ? '保存' : '创建' }}</uni-button>
        <uni-button v-if="editingId" :disabled="saving" @click="resetForm">取消编辑</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
        <uni-button :disabled="!appId || !modelId" @click="goRecords">Records</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">字段列表</view>
      <view style="margin-top: 12px">
        <uni-list v-if="fields.length">
          <uni-list-item
            v-for="f in fields"
            :key="f.id"
            :title="f.fieldName || f.fieldCode || ('Field#' + f.id)"
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
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import { listDictsByApp } from '@/api/module'
import {
  deleteFields,
  listFieldsByModel,
  listModelsByApp,
  type ModuleField,
  type ModuleModel,
  upsertField
} from '@/api/meta'
import { defaultConfigFor, type ModuleFieldTypeCode } from '@/utils/fieldTypeEnum'
import SerialSegmentBuilder from '@/components/fields/SerialSegmentBuilder.vue'
import {
  FIELD_TYPE_OPTIONS,
  buildConfigJson,
  configFromMeta,
  fieldTypeNeedsDict,
  fieldTypeNeedsRef,
  multiFlagForFieldType,
  storageFieldType,
  uiFieldTypeFromMeta,
  validateTypeForFieldType
} from '@/utils/fieldTypes'

const appId = ref(0)
const modelId = ref(0)
const fields = ref<ModuleField[]>([])
const saving = ref(false)
const editingId = ref<number | null>(null)
const { loading, error, run, capture, clearError } = usePageRequest()

const fieldTypeOptions = FIELD_TYPE_OPTIONS
const flagOptions = [
  { value: 'required', text: '必填' },
  { value: 'hidden', text: '隐藏' }
]

const form = reactive({
  fieldCode: '',
  fieldName: '',
  fieldType: 'TEXT' as ModuleFieldTypeCode,
  dictCode: '',
  refModelId: '',
  refDisplayField: '',
  relationModuleLabel: '',
  sortNo: '0',
  tips: '',
  defaultValue: '',
  flags: [] as string[]
})

const dictCodeOptions = ref<Array<{ value: string; text: string }>>([])

const needsDict = computed(() => fieldTypeNeedsDict(form.fieldType))
const needsRef = computed(() => fieldTypeNeedsRef(form.fieldType))

const modelOptions = ref<Array<{ value: string; text: string }>>([])
const refDisplayFieldOptions = ref<Array<{ value: string; text: string }>>([])

const typeConfig = reactive<Record<string, any>>({})

const textStyleOptions = [
  { value: 'normal', text: '普通' },
  { value: 'password', text: '密码' },
  { value: 'phone', text: '手机' },
  { value: 'email', text: '邮箱' },
  { value: 'url', text: '网址' }
]
const pickerModeOptions = [
  { value: 'date', text: '日期' },
  { value: 'datetime', text: '日期时间' },
  { value: 'time', text: '时间' }
]
const selectStyleOptions = [
  { value: 'dropdown', text: '下拉' },
  { value: 'radio', text: '单选框' },
  { value: 'rating', text: '评分' },
  { value: 'tag', text: '标签' }
]
const refStyleOptions = [
  { value: 'inline', text: '单行选择' },
  { value: 'list', text: '列表多选' },
  { value: 'table', text: '子表/明细' }
]
const scopeOptions = [
  { value: 'system', text: '本系统成员' },
  { value: 'app', text: '本应用' }
]

const refNeedsListFields = computed(() => {
  const s = typeConfig.displayStyle
  return s === 'list' || s === 'table' || !!typeConfig.subTable
})

const datePickerTypeForConfig = computed(() => {
  const m = String(typeConfig.pickerMode || 'datetime')
  if (m === 'date') return 'date'
  if (m === 'time') return 'time'
  return 'datetime'
})

function onSubTableToggle(e: any) {
  typeConfig.subTable = e.detail.value
  if (typeConfig.subTable) {
    typeConfig.displayStyle = 'table'
    typeConfig.multi = true
  }
}

function onAddressMapToggle(e: any) {
  typeConfig.includeLocation = e.detail.value
  typeConfig.mapPicker = e.detail.value
}
const regionStyleOptions = [
  { value: 'cascade', text: '级联' },
  { value: 'picker', text: '选择器' }
]
const detailModeOptions = [
  { value: 'manual', text: '手动填写' },
  { value: 'optional', text: '可选' }
]

function resetTypeConfig(code: ModuleFieldTypeCode) {
  const d = defaultConfigFor(code) as Record<string, any>
  Object.keys(typeConfig).forEach((k) => delete typeConfig[k])
  Object.assign(typeConfig, d)
  if (code === 'TAG') typeConfig.tagsText = (d.tags as string[])?.join(',') || ''
  if (code === 'SERIAL_NO') typeConfig.segments = Array.isArray(d.segments) ? [...(d.segments as any[])] : []
}

function buildTypeConfigPayload(): Record<string, unknown> {
  const code = form.fieldType as ModuleFieldTypeCode
  const cfg: Record<string, unknown> = { ...typeConfig }
  if (code === 'TAG' && typeof typeConfig.tagsText === 'string') {
    cfg.tags = typeConfig.tagsText.split(',').map((s: string) => s.trim()).filter(Boolean)
    delete cfg.tagsText
  }
  if (code === 'REF_MODULE') {
    if (typeConfig.subTable) {
      cfg.displayStyle = 'table'
      cfg.multi = true
    }
    if (!Array.isArray(cfg.listFields)) {
      cfg.listFields = []
    }
  }
  if (code === 'ADDRESS') {
    cfg.mapPicker = cfg.includeLocation === true
  }
  if (code === 'SERIAL_NO') {
    cfg.segments = Array.isArray(typeConfig.segments) ? typeConfig.segments : []
    delete cfg.segmentsJson
  }
  if (code === 'TEXT' && typeConfig.maxLength != null) {
    cfg.maxLength = Number(typeConfig.maxLength) || 200
  }
  if ((code === 'MONEY' || code === 'PERCENT') && typeConfig.decimalPlaces != null) {
    cfg.decimalPlaces = Number(typeConfig.decimalPlaces)
  }
  return cfg
}

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
})

function fieldNote(f: ModuleField) {
  const parts = [f.fieldCode, f.fieldType]
  if (f.dictCode) parts.push(`dict=${f.dictCode}`)
  if (f.refModelId) parts.push(`ref→model#${f.refModelId}`)
  if (f.relationModuleLabel) parts.push(`「${f.relationModuleLabel}」`)
  if (f.refDisplayField) parts.push(`show=${f.refDisplayField}`)
  if (f.requiredFlag === 1) parts.push('必填')
  if (f.hiddenFlag === 1) parts.push('隐藏')
  if (f.multiFlag === 1) parts.push('多选')
  return parts.filter(Boolean).join(' · ')
}

function onFieldTypeChange() {
  resetTypeConfig(form.fieldType as ModuleFieldTypeCode)
  if (!needsDict.value) form.dictCode = ''
  if (!needsRef.value) {
    form.refModelId = ''
    form.refDisplayField = ''
    form.relationModuleLabel = ''
    refDisplayFieldOptions.value = []
  }
}

async function loadModelOptions() {
  if (!appId.value) return
  try {
    const r = await listModelsByApp(appId.value)
    modelOptions.value = (r.data || [])
      .filter((m: ModuleModel) => m?.id)
      .map((m: ModuleModel) => ({
        value: String(m.id),
        text: `${m.modelName || m.modelCode || 'Model'} (#${m.id})`
      }))
  } catch {
    modelOptions.value = []
  }
}

async function loadRefDisplayFieldOptions(targetModelId: number) {
  if (!targetModelId) {
    refDisplayFieldOptions.value = []
    return
  }
  try {
    const r = await listFieldsByModel(targetModelId)
    refDisplayFieldOptions.value = (r.data || [])
      .filter((x) => x?.fieldCode)
      .map((x) => ({
        value: String(x.fieldCode),
        text: `${x.fieldName || x.fieldCode} (${x.fieldCode})`
      }))
  } catch {
    refDisplayFieldOptions.value = []
  }
}

function onRefModelChange() {
  form.refDisplayField = ''
  const mid = Number(form.refModelId) || 0
  if (mid) {
    const m = modelOptions.value.find((o) => o.value === String(mid))
    if (m && !form.relationModuleLabel.trim()) {
      form.relationModuleLabel = String(m.text).replace(/\s*\(#\d+\)\s*$/, '')
    }
    loadRefDisplayFieldOptions(mid)
  } else {
    refDisplayFieldOptions.value = []
  }
}

async function loadDictOptions() {
  if (!appId.value) return
  try {
    const r = await listDictsByApp(appId.value)
    dictCodeOptions.value = (r.data || [])
      .filter((d) => d?.dictCode)
      .map((d) => ({ value: String(d.dictCode), text: `${d.dictName || d.dictCode} (${d.dictCode})` }))
  } catch {
    dictCodeOptions.value = []
  }
}

async function load() {
  if (!modelId.value) {
    uni.showToast({ title: '缺少 modelId', icon: 'none' })
    return
  }
  await run(async () => {
    const r = await listFieldsByModel(modelId.value)
    fields.value = (r.data || []).slice().sort((a, b) => Number(a.sortNo ?? 0) - Number(b.sortNo ?? 0))
  })
}

function resetForm() {
  editingId.value = null
  form.fieldCode = ''
  form.fieldName = ''
  form.fieldType = 'TEXT'
  resetTypeConfig('TEXT')
  form.dictCode = ''
  form.refModelId = ''
  form.refDisplayField = ''
  form.relationModuleLabel = ''
  refDisplayFieldOptions.value = []
  form.sortNo = '0'
  form.tips = ''
  form.defaultValue = ''
  form.flags = []
}

function fillForm(f: ModuleField) {
  editingId.value = Number(f.id)
  form.fieldCode = f.fieldCode || ''
  form.fieldName = f.fieldName || ''
  form.fieldType = uiFieldTypeFromMeta(f)
  const cfg = configFromMeta(f)
  resetTypeConfig(form.fieldType)
  Object.assign(typeConfig, cfg)
  if (form.fieldType === 'REF_MODULE' && !Array.isArray(typeConfig.listFields)) {
    typeConfig.listFields = []
  }
  if (form.fieldType === 'TAG') typeConfig.tagsText = ((cfg.tags as string[]) || []).join(',')
  if (form.fieldType === 'SERIAL_NO') typeConfig.segments = Array.isArray(cfg.segments) ? [...cfg.segments] : []
  form.dictCode = f.dictCode || ''
  form.refModelId = f.refModelId ? String(f.refModelId) : ''
  form.refDisplayField = f.refDisplayField || ''
  form.relationModuleLabel = f.relationModuleLabel || ''
  if (f.refModelId) loadRefDisplayFieldOptions(Number(f.refModelId))
  form.sortNo = String(f.sortNo ?? 0)
  form.tips = f.tips || ''
  form.defaultValue = f.defaultValue || ''
  const flags: string[] = []
  if (f.requiredFlag === 1) flags.push('required')
  if (f.hiddenFlag === 1) flags.push('hidden')
  form.flags = flags
}

async function save() {
  if (!appId.value || !modelId.value) return
  if (!form.fieldCode.trim() || !form.fieldName.trim()) {
    uni.showToast({ title: '请填写 fieldCode/fieldName', icon: 'none' })
    return
  }
  if (needsDict.value && !form.dictCode.trim()) {
    uni.showToast({ title: '请选择 dictCode', icon: 'none' })
    return
  }
  if (needsRef.value && !form.refModelId) {
    uni.showToast({ title: '请选择关联模型', icon: 'none' })
    return
  }

  saving.value = true
  clearError()
  try {
    const cfgPayload = buildTypeConfigPayload()
    const storedType = storageFieldType(form.fieldType)
    await upsertField({
      id: editingId.value,
      appId: appId.value,
      modelId: modelId.value,
      fieldCode: form.fieldCode.trim(),
      fieldName: form.fieldName.trim(),
      fieldType: storedType,
      requiredFlag: form.flags.includes('required') ? 1 : 0,
      uniqueFlag: 0,
      hiddenFlag: form.flags.includes('hidden') ? 1 : 0,
      tips: form.tips.trim() || null,
      maxLength: null,
      minLength: null,
      validateType: validateTypeForFieldType(form.fieldType, cfgPayload),
      dateFormat: null,
      dictCode: needsDict.value ? form.dictCode.trim() : null,
      refModelId: needsRef.value ? Number(form.refModelId) || null : null,
      refDisplayField: needsRef.value ? form.refDisplayField.trim() || null : null,
      relationModuleLabel: needsRef.value ? form.relationModuleLabel.trim() || null : null,
      configJson: buildConfigJson(form.fieldType, cfgPayload),
      multiFlag: multiFlagForFieldType(form.fieldType, cfgPayload),
      defaultValue: form.defaultValue.trim() || null,
      sortNo: Number(form.sortNo) || 0,
      status: 1
    })
    uni.showToast({ title: editingId.value ? '已保存' : '已创建', icon: 'success' })
    resetForm()
    await load()
  } catch (e: unknown) {
    capture(e)
  } finally {
    saving.value = false
  }
}

function openFieldActions(f: ModuleField) {
  uni.showActionSheet({
    itemList: ['编辑', '删除'],
    success: (res) => {
      if (res.tapIndex === 0) {
        fillForm(f)
        return
      }
      if (res.tapIndex === 1) {
        uni.showModal({
          title: '删除字段？',
          content: f.fieldCode || '',
          success: async (m) => {
            if (!m.confirm || !f.id) return
            clearError()
            try {
              await deleteFields([Number(f.id)])
              uni.showToast({ title: '已删除', icon: 'success' })
              if (editingId.value === f.id) resetForm()
              await load()
            } catch (e: unknown) {
              capture(e)
            }
          }
        })
      }
    }
  })
}

function goRecords() {
  uni.navigateTo({ url: `/pages/system/records/list?appId=${appId.value}&modelId=${modelId.value}` })
}

onMounted(async () => {
  if (!ensureSystemContext()) return
  resetTypeConfig('TEXT')
  await loadDictOptions()
  await loadModelOptions()
  await load()
})
</script>
