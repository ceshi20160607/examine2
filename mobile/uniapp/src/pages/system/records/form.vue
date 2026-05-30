<template>
  <Page :title="title" :subtitle="formSubtitle">
    <view class="u-card">
      <uni-forms labelPosition="top">
        <uni-forms-item v-if="!advancedJson" label="字段表单">
          <view v-if="fields.length === 0" style="color: var(--u-text-muted)">加载字段中...</view>
          <view v-else style="display:flex; flex-direction: column; gap: 12px;">
            <view v-for="f in visibleFields" :key="String(f.id)">
              <view style="margin-bottom: 6px; color:#333">
                {{ f.fieldName || f.fieldCode }}
                <text v-if="f.requiredFlag === 1" style="color:#d00"> *</text>
              </view>

              <uni-easyinput
                v-if="isPlainTextField(f)"
                v-model="formData[f.fieldCode]"
                :type="inputTypeForField(f)"
                :placeholder="f.tips || placeholderFor(f)"
              />

              <uni-easyinput
                v-else-if="isValidatedTextField(f)"
                v-model="formData[f.fieldCode]"
                :placeholder="f.tips || placeholderFor(f)"
              />

              <uni-easyinput
                v-else-if="isPasswordField(f)"
                v-model="formData[f.fieldCode]"
                type="password"
                :placeholder="f.tips || '请输入密码'"
              />

              <uni-easyinput
                v-else-if="isJsonField(f)"
                v-model="formData[f.fieldCode]"
                type="textarea"
                :autoHeight="true"
                :placeholder="f.tips || 'JSON 对象或数组'"
              />

              <uni-easyinput
                v-else-if="isTextareaField(f)"
                v-model="formData[f.fieldCode]"
                type="textarea"
                :autoHeight="true"
                :placeholder="f.tips || ''"
              />

              <uni-easyinput
                v-else-if="isRichTextField(f)"
                v-model="formData[f.fieldCode]"
                type="textarea"
                class="rich-text-input"
                :autoHeight="true"
                :placeholder="f.tips || '富文本（HTML/长文）'"
              />

              <SignatureUpload
                v-else-if="isSignatureField(f)"
                v-model="formData[f.fieldCode]"
              />

              <RatingStars
                v-else-if="isRatingSelectField(f)"
                v-model="formData[f.fieldCode]"
              />

              <uni-easyinput
                v-else-if="isNumberField(f)"
                v-model="formData[f.fieldCode]"
                type="number"
                :placeholder="f.tips || ''"
              />

              <uni-datetime-picker
                v-else-if="isDateField(f)"
                v-model="formData[f.fieldCode]"
                :type="datePickerType(f)"
              />

              <view v-else-if="isDateRangeField(f)" style="display:flex; flex-direction:column; gap:8px;">
                <view class="u-subtitle">开始</view>
                <uni-datetime-picker v-model="dateRangeStart[f.fieldCode]" :type="datePickerType(f)" />
                <view class="u-subtitle">结束</view>
                <uni-datetime-picker v-model="dateRangeEnd[f.fieldCode]" :type="datePickerType(f)" />
              </view>

              <uni-easyinput
                v-else-if="isSerialNoField(f)"
                v-model="formData[f.fieldCode]"
                :disabled="!recordId"
                :placeholder="recordId ? '' : '保存时自动生成编号'"
              />

              <uni-data-select
                v-else-if="isPersonField(f) && !isPersonMulti(f)"
                v-model="formData[f.fieldCode]"
                :localdata="memberOptionsFor(f)"
                placeholder="选择人员"
              />

              <uni-data-checkbox
                v-else-if="isPersonField(f) && isPersonMulti(f)"
                v-model="formData[f.fieldCode]"
                multiple
                :localdata="memberOptionsFor(f)"
              />

              <uni-data-select
                v-else-if="isDepartmentField(f) && !isDepartmentMulti(f)"
                v-model="formData[f.fieldCode]"
                :localdata="departmentOptions"
                placeholder="选择部门"
              />

              <uni-data-checkbox
                v-else-if="isDepartmentField(f) && isDepartmentMulti(f)"
                v-model="formData[f.fieldCode]"
                multiple
                :localdata="departmentOptions"
              />

              <switch
                v-else-if="isBooleanField(f)"
                :checked="boolValue(f)"
                @change="(e: any) => setBool(f, e.detail.value)"
              />

              <view v-else-if="isFileField(f)" style="display:flex; flex-direction:column; gap:8px;">
                <view class="u-subtitle">fileId: {{ fileIdLabel(f) }}</view>
                <ActionBar>
                  <uni-button size="mini" @click="pickFileFor(f)">选择并上传</uni-button>
                  <uni-button size="mini" @click="clearFileFor(f)">清除</uni-button>
                </ActionBar>
              </view>

              <uni-data-checkbox
                v-else-if="isRadioSelectField(f)"
                v-model="formData[f.fieldCode]"
                :localdata="dictOptionsByCode[f.dictCode || ''] || []"
              />

              <uni-data-select
                v-else-if="isDictSingle(f)"
                v-model="formData[f.fieldCode]"
                :localdata="dictOptionsByCode[f.dictCode || ''] || []"
                placeholder="请选择"
              />

              <uni-data-checkbox
                v-else-if="isDictMulti(f)"
                v-model="formData[f.fieldCode]"
                multiple
                :localdata="dictOptionsByCode[f.dictCode || ''] || []"
              />

              <uni-data-select
                v-else-if="isRefField(f) && !isRefMultiField(f)"
                v-model="formData[f.fieldCode]"
                :localdata="refOptionsByCode[f.fieldCode || ''] || []"
                placeholder="选择关联记录"
              />

              <RefSubTableField
                v-else-if="isRefTableField(f)"
                :field="f"
                :app-id="appId"
                :parent-record-id="recordId"
                :parent-model-id="modelId"
                v-model="formData[f.fieldCode]"
                :options="refOptionsByCode[f.fieldCode || ''] || []"
              />

              <uni-data-checkbox
                v-else-if="isRefMultiField(f) && !isRefTableField(f)"
                v-model="formData[f.fieldCode]"
                multiple
                :localdata="refOptionsByCode[f.fieldCode || ''] || []"
              />

              <RegionAddressField
                v-else-if="isAddressField(f)"
                v-model="formData[f.fieldCode]"
                :field="f"
              />

              <TagField
                v-else-if="isTagField(f)"
                v-model="formData[f.fieldCode]"
                :field="f"
              />
            </view>
          </view>
        </uni-forms-item>

        <uni-forms-item v-else label="JSON data（对象）">
          <uni-easyinput v-model="jsonText" type="textarea" :autoHeight="true" placeholder="请输入 JSON 对象" />
        </uni-forms-item>
      </uni-forms>

      <ActionBar>
        <uni-button type="primary" :disabled="saving" @click="submit">{{ recordId ? '更新' : '创建' }}</uni-button>
        <uni-button :disabled="saving" @click="back">返回</uni-button>
        <uni-button :disabled="saving" @click="toggleAdvanced">{{ advancedJson ? '切回表单' : '高级 JSON' }}</uni-button>
      </ActionBar>

      <ErrorBlock :text="error" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import RefSubTableField from '@/components/fields/RefSubTableField.vue'
import RegionAddressField from '@/components/fields/RegionAddressField.vue'
import TagField from '@/components/fields/TagField.vue'
import RatingStars from '@/components/fields/RatingStars.vue'
import SignatureUpload from '@/components/fields/SignatureUpload.vue'
import { listFieldsByModel, type ModuleField } from '@/api/meta'
import { listDepartmentPickerOptions, listMemberPickerOptions } from '@/api/rbac'
import { listDictItems, listDictsByApp } from '@/api/module'
import { createRecord, getRecord, updateRecord } from '@/api/records'
import { pickSingleFilePath, uploadOneFile } from '@/api/upload'
import {
  datePickerType,
  inputTypeForField,
  isBooleanField,
  isDateField,
  isDateRangeField,
  isDepartmentField,
  isDictMulti,
  isDictSingle,
  isFileField,
  isJsonField,
  isNumberField,
  isPasswordField,
  isPlainTextField,
  isPersonField,
  isSerialNoField,
  configFromMeta,
  isRefField,
  isRefMultiField,
  isRefTableField,
  isRichTextField,
  isRadioSelectField,
  isRatingSelectField,
  isSignatureField,
  isTextareaField,
  isTitleField,
  isValidatedTextField,
  isAddressField,
  isTagField
} from '@/utils/fieldTypes'
import { loadRefSelectOptions } from '@/utils/refPicker'
import { getPageRuntime } from '@/api/pages'
import { applyPageFieldOverrides, pageQuerySuffix, type PageRuntime } from '@/utils/pageRuntime'
import { hasId, idToString, uniqueIds, type IdValue } from '@/utils/id'

const appId = ref('')
const modelId = ref('')
const recordId = ref('')
const pageId = ref('')
const pageRuntime = ref<PageRuntime | null>(null)
const embedMode = ref(false)

const advancedJson = ref(false)
const jsonText = ref('{}')
const saving = ref(false)
const error = ref<string | null>(null)

const title = computed(() => {
  if (pageRuntime.value?.pageName) {
    return recordId.value ? `编辑 · ${pageRuntime.value.pageName}` : pageRuntime.value.pageName
  }
  return recordId.value ? `编辑 Record #${recordId.value}` : '新建 Record'
})
const formSubtitle = computed(() =>
  pageRuntime.value?.pageCode
    ? `page=${pageRuntime.value.pageCode} · 可按页面字段配置渲染`
    : '按字段元数据生成表单；复杂场景可切换高级 JSON'
)

onLoad((opts) => {
  appId.value = idToString((opts as any)?.appId)
  modelId.value = idToString((opts as any)?.modelId)
  recordId.value = idToString((opts as any)?.recordId)
  pageId.value = idToString((opts as any)?.pageId)
  embedMode.value = String((opts as any)?.embed || '') === '1'
  const linkFk = String((opts as any)?.linkFkField || '').trim()
  const linkPid = idToString((opts as any)?.linkParentId)
  if (linkFk && hasId(linkPid)) {
    formData[linkFk] = linkPid
  }
})

onMounted(() => {
  ensureSystemContext()
  bootstrap()
})

function back() {
  uni.navigateBack()
}

const fields = ref<ModuleField[]>([])
const formData = reactive<Record<string, any>>({})
const dictOptionsByCode = reactive<Record<string, Array<{ value: any; text: string }>>>({})
const refOptionsByCode = reactive<Record<string, Array<{ value: any; text: string }>>>({})
const memberOptions = ref<Array<{ value: any; text: string }>>([])
const memberOptionsByCode = reactive<Record<string, Array<{ value: any; text: string }>>>({})
const departmentOptions = ref<Array<{ value: any; text: string }>>([])
const dateRangeStart = reactive<Record<string, string>>({})
const dateRangeEnd = reactive<Record<string, string>>({})

function isPersonMulti(f: ModuleField) {
  return isPersonField(f) && (configFromMeta(f).multi === true || (f.multiFlag ?? 0) === 1)
}

function isDepartmentMulti(f: ModuleField) {
  return isDepartmentField(f) && (configFromMeta(f).multi === true || (f.multiFlag ?? 0) === 1)
}

function memberOptionsFor(f: ModuleField) {
  const code = f.fieldCode || ''
  return memberOptionsByCode[code]?.length ? memberOptionsByCode[code] : memberOptions.value
}

function syncDateRangeFromForm() {
  for (const mf of fields.value || []) {
    const code = mf?.fieldCode
    if (!code || !isDateRangeField(mf)) continue
    const start = dateRangeStart[code] || ''
    const end = dateRangeEnd[code] || ''
    if (!start && !end) {
      formData[code] = ''
      continue
    }
    formData[code] = JSON.stringify({ start, end })
  }
}

function parseDateRangeToPickers() {
  for (const mf of fields.value || []) {
    const code = mf?.fieldCode
    if (!code || !isDateRangeField(mf)) continue
    const raw = formData[code]
    if (!raw) continue
    try {
      const o = typeof raw === 'string' ? JSON.parse(raw) : raw
      if (o && typeof o === 'object') {
        dateRangeStart[code] = o.start || ''
        dateRangeEnd[code] = o.end || ''
      }
    } catch {
      // ignore
    }
  }
}

function placeholderFor(f: ModuleField) {
  const t = String(f.fieldType || '').toLowerCase()
  if (t === 'email') return 'name@example.com'
  if (t === 'phone') return '11 位手机号'
  if (t === 'url') return 'https://'
  if (t === 'idcard') return '18 位身份证号'
  if (t === 'color') return '#RRGGBB'
  if (t === 'region') return '省/市/区'
  return f.tips || ''
}

const visibleFields = computed(() => {
  const base = (fields.value || []).filter(
    (f): f is ModuleField & { fieldCode: string } => !!(f && f.fieldCode && f.hiddenFlag !== 1 && !isTitleField(f))
  )
  return applyPageFieldOverrides(base, pageRuntime.value)
})

function boolValue(f: ModuleField) {
  const v = formData[f.fieldCode!]
  if (v === true || v === 1 || v === '1' || v === 'true') return true
  return false
}

function setBool(f: ModuleField, checked: boolean) {
  if (!f.fieldCode) return
  formData[f.fieldCode] = checked
}

function fileIdLabel(f: ModuleField) {
  const v = formData[f.fieldCode!]
  if (v == null || v === '') return '-'
  return String(v)
}

async function pickFileFor(f: ModuleField) {
  if (!f.fieldCode) return
  try {
    const path = await pickSingleFilePath()
    const r = await uploadOneFile(path)
    const id = r.data?.fileId
    if (id) {
      formData[f.fieldCode] = id
      uni.showToast({ title: '上传成功', icon: 'success' })
    }
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  }
}

function clearFileFor(f: ModuleField) {
  if (!f.fieldCode) return
  formData[f.fieldCode] = null
}

function toggleAdvanced() {
  advancedJson.value = !advancedJson.value
  if (advancedJson.value) {
    jsonText.value = JSON.stringify(toSubmitData(), null, 2)
  }
}

async function loadPageRuntime() {
  if (!hasId(pageId.value)) return
  try {
    const r = await getPageRuntime(pageId.value)
    pageRuntime.value = r.data || null
    if (pageRuntime.value?.appId && !appId.value) appId.value = idToString(pageRuntime.value.appId)
    if (pageRuntime.value?.modelId && !modelId.value) modelId.value = idToString(pageRuntime.value.modelId)
  } catch {
    pageRuntime.value = null
  }
}

async function bootstrap() {
  try {
    await loadPageRuntime()
    // 编辑场景：先拿 record，补齐 appId/modelId，并回填 data
    if (hasId(recordId.value)) {
      const d = await getRecord(recordId.value)
      const rec = d.data?.record
      const data = d.data?.data
      if (!appId.value) appId.value = idToString(rec?.appId)
      if (!modelId.value) modelId.value = idToString(rec?.modelId)
      if (data && typeof data === 'object') {
        for (const k of Object.keys(data)) {
          formData[k] = data[k]
        }
      }
    }

    if (!hasId(modelId.value)) return
    const f = await listFieldsByModel(modelId.value)
    fields.value = (f.data || []) as any

    // 新建：填默认值
    if (!hasId(recordId.value)) {
      for (const mf of fields.value) {
        if (!mf?.fieldCode) continue
        if (formData[mf.fieldCode] != null) continue
        if (mf.defaultValue != null && String(mf.defaultValue).length > 0) {
          formData[mf.fieldCode] = mf.defaultValue
        } else if (isDictMulti(mf)) {
          formData[mf.fieldCode] = []
        } else if (isBooleanField(mf)) {
          formData[mf.fieldCode] = false
        } else if (isFileField(mf)) {
          formData[mf.fieldCode] = null
        } else if (isRefMultiField(mf)) {
          formData[mf.fieldCode] = []
        } else if (isRefField(mf)) {
          formData[mf.fieldCode] = ''
        } else {
          formData[mf.fieldCode] = ''
        }
      }
    }

    // 兼容：把多选字典值规范成数组；日期时间值规范成字符串
    normalizeFormDataValues()

    // 字典选项
    await loadDictOptionsIfNeeded()
    await loadRefOptionsIfNeeded()
    await loadPickerOptionsIfNeeded()
    parseDateRangeToPickers()
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  }
}

function normalizeMultiValue(v: any): any[] {
  if (v == null || v === '') return []
  if (Array.isArray(v)) return v
  if (typeof v === 'string') {
    const t = v.trim()
    if (!t) return []
    if (t.startsWith('[') && t.endsWith(']')) {
      try {
        const arr = JSON.parse(t)
        return Array.isArray(arr) ? arr : []
      } catch {
        // fall through
      }
    }
    return t.split(',').map((x) => x.trim()).filter((x) => x.length > 0)
  }
  return [v]
}

function normalizeIdMultiValue(v: any): string[] {
  return uniqueIds(normalizeMultiValue(v) as IdValue[])
}

function normalizeDateValue(v: any): string | null {
  if (v == null) return null
  if (Array.isArray(v)) {
    const first = v[0]
    return first == null ? null : String(first)
  }
  const s = String(v)
  return s.trim() ? s : null
}

function normalizeFormDataValues() {
  for (const mf of fields.value || []) {
    const code = mf?.fieldCode
    if (!code) continue
    if (isRefTableField(mf)) {
      formData[code] = normalizeIdMultiValue(formData[code])
      continue
    }
    if (isDictMulti(mf) || isRefMultiField(mf)) {
      formData[code] = normalizeMultiValue(formData[code])
      continue
    }
    if (isRefField(mf) && !isRefMultiField(mf)) {
      const raw = formData[code]
      if (raw != null && raw !== '') {
        const id = idToString(raw)
        formData[code] = hasId(id) ? id : raw
      }
      continue
    }
    if (isDateField(mf)) {
      const nv = normalizeDateValue(formData[code])
      formData[code] = nv ?? ''
      continue
    }
    if (isDateRangeField(mf)) {
      parseDateRangeToPickers()
      continue
    }
    if (isPersonField(mf) || isDepartmentField(mf)) {
      if (isPersonMulti(mf) || isDepartmentMulti(mf)) {
        formData[code] = normalizeMultiValue(formData[code])
      }
      continue
    }
    if (isBooleanField(mf)) {
      const v = formData[code]
      formData[code] = v === true || v === 1 || v === '1' || v === 'true'
    }
  }
}

async function loadPickerOptionsIfNeeded() {
  if (!hasId(appId.value)) return
  try {
    const d = await listDepartmentPickerOptions(appId.value)
    departmentOptions.value = (d.data || []).map((x) => ({ value: x.value, text: x.text }))
    const m = await listMemberPickerOptions(appId.value)
    memberOptions.value = (m.data || []).map((x) => ({ value: x.value, text: x.text }))
    for (const mf of fields.value || []) {
      if (!mf?.fieldCode || !isPersonField(mf)) continue
      const cfg = configFromMeta(mf)
      const scope = String(cfg.scope || 'system')
      const deptId = cfg.deptId != null ? idToString(cfg.deptId as IdValue) : undefined
      const pr =
        scope === 'app' && deptId
          ? await listMemberPickerOptions(appId.value, scope, deptId)
          : scope === 'app'
            ? await listMemberPickerOptions(appId.value, scope)
            : await listMemberPickerOptions(appId.value)
      memberOptionsByCode[mf.fieldCode] = (pr.data || []).map((x) => ({ value: x.value, text: x.text }))
    }
  } catch {
    memberOptions.value = []
    departmentOptions.value = []
  }
}

async function loadRefOptionsIfNeeded() {
  if (!hasId(appId.value)) return
  for (const mf of fields.value || []) {
    const code = mf?.fieldCode
    if (!code || !isRefField(mf)) continue
    if (refOptionsByCode[code]?.length) continue
    try {
      refOptionsByCode[code] = await loadRefSelectOptions({ appId: appId.value, field: mf })
    } catch {
      refOptionsByCode[code] = []
    }
  }
}

async function loadDictOptionsIfNeeded() {
  if (!hasId(appId.value)) return
  const dictCodes = Array.from(
    new Set((fields.value || []).map((x) => String(x?.dictCode || '')).filter((x) => !!x))
  )
  if (dictCodes.length === 0) return

  const dictsResp = await listDictsByApp(appId.value)
  const dicts = (dictsResp.data || []) as Array<{ id: IdValue; dictCode?: string }>
  const codeToId: Record<string, any> = {}
  for (const d of dicts) {
    if (d?.dictCode) codeToId[String(d.dictCode)] = d.id
  }

  for (const code of dictCodes) {
    if (dictOptionsByCode[code]?.length) continue
    const dictId = codeToId[code]
    if (!dictId) continue
    const itemsResp = await listDictItems(dictId)
    const items = (itemsResp.data || []) as Array<{ itemValue?: any; itemLabel?: string }>
    dictOptionsByCode[code] = items.map((it) => ({ value: it.itemValue ?? '', text: it.itemLabel || String(it.itemValue ?? '') }))
  }
}

function toSubmitData(): any {
  syncDateRangeFromForm()
  const out: any = {}
  for (const mf of fields.value || []) {
    const code = mf?.fieldCode
    if (!code) continue
    let v = formData[code]
    if (isNumberField(mf)) {
      const t = String(v ?? '').trim()
      if (t === '') {
        v = null
      } else {
        const n = Number(t)
        v = Number.isNaN(n) ? t : n
      }
    }
    if (isRefTableField(mf) || isDictMulti(mf) || isRefMultiField(mf)) {
      v = isDictMulti(mf) ? normalizeMultiValue(v) : normalizeIdMultiValue(v)
    }
    if (isSignatureField(mf) || isFileField(mf)) {
      const t = String(v ?? '').trim()
      if (t === '') v = null
      else v = t
    }
    if (isRefField(mf) && !isRefMultiField(mf)) {
      const t = String(v ?? '').trim()
      if (t === '') v = null
      else v = t
    }
    if (isDateField(mf)) {
      v = normalizeDateValue(v)
    }
    if (isDateRangeField(mf)) {
      const start = dateRangeStart[mf.fieldCode!] || ''
      const end = dateRangeEnd[mf.fieldCode!] || ''
      v = start || end ? { start, end } : null
    }
    if (isSerialNoField(mf) && !hasId(recordId.value)) {
      const t = String(v ?? '').trim()
      v = t === '' ? null : t
    }
    if (isBooleanField(mf)) {
      v = v === true || v === 1 || v === '1' || v === 'true'
    }
    out[code] = v
  }
  return out
}

function validateRequired(dataObj: any): string | null {
  for (const mf of visibleFields.value) {
    if (mf.requiredFlag !== 1) continue
    const v = dataObj[mf.fieldCode]
    let empty = v == null || v === '' || (Array.isArray(v) && v.length === 0)
    if (isBooleanField(mf)) empty = false
    if (empty) return `${mf.fieldName || mf.fieldCode} 不能为空`
  }
  return null
}

async function submit() {
  error.value = null
  let dataObj: any
  if (advancedJson.value) {
    try {
      dataObj = JSON.parse(jsonText.value || '{}')
      if (!dataObj || Array.isArray(dataObj) || typeof dataObj !== 'object') {
        throw new Error('data 必须是 JSON 对象')
      }
    } catch (e: any) {
      error.value = e?.message ?? 'JSON 非法'
      return
    }
  } else {
    dataObj = toSubmitData()
    const msg = validateRequired(dataObj)
    if (msg) {
      error.value = msg
      return
    }
  }

  saving.value = true
  try {
    if (hasId(recordId.value)) {
      await updateRecord(recordId.value, dataObj)
      uni.showToast({ title: '更新成功', icon: 'success' })
      back()
      return
    }
    const r = await createRecord({ appId: appId.value, modelId: modelId.value, data: dataObj })
    const id = r.data?.recordId
    uni.showToast({ title: '创建成功', icon: 'success' })
    if (embedMode.value && id) {
      const ec = (uni as any).getOpenerEventChannel?.()
      ec?.emit?.('recordCreated', { recordId: id })
      uni.navigateBack()
      return
    }
    if (id) {
      uni.redirectTo({ url: `/pages/system/records/detail?recordId=${encodeURIComponent(idToString(id))}${pageQuerySuffix(pageId.value)}` })
    } else {
      back()
    }
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
:deep(.rich-text-input textarea) {
  min-height: 120px;
}
</style>

