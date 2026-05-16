<template>
  <Page :title="title" subtitle="按字段元数据生成表单；复杂场景可切换高级 JSON">
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

              <uni-data-checkbox
                v-else-if="isRefMultiField(f)"
                v-model="formData[f.fieldCode]"
                multiple
                :localdata="refOptionsByCode[f.fieldCode || ''] || []"
              />

              <uni-easyinput
                v-else
                v-model="formData[f.fieldCode]"
                :placeholder="`${f.fieldType || 'text'}（暂按文本）`"
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
import { listFieldsByModel, type ModuleField } from '@/api/meta'
import { listDictItems, listDictsByApp } from '@/api/module'
import { createRecord, getRecord, updateRecord } from '@/api/records'
import { pickSingleFilePath, uploadOneFile } from '@/api/upload'
import {
  datePickerType,
  inputTypeForField,
  isBooleanField,
  isDateField,
  isDictMulti,
  isDictSingle,
  isFileField,
  isJsonField,
  isNumberField,
  isPasswordField,
  isPlainTextField,
  isRefField,
  isRefMultiField,
  isTextareaField,
  isValidatedTextField
} from '@/utils/fieldTypes'
import { loadRefSelectOptions } from '@/utils/refPicker'

const appId = ref(0)
const modelId = ref(0)
const recordId = ref(0)

const advancedJson = ref(false)
const jsonText = ref('{}')
const saving = ref(false)
const error = ref<string | null>(null)

const title = computed(() => (recordId.value ? `编辑 Record #${recordId.value}` : '新建 Record'))

onLoad((opts) => {
  appId.value = Number((opts as any)?.appId || 0) || 0
  modelId.value = Number((opts as any)?.modelId || 0) || 0
  recordId.value = Number((opts as any)?.recordId || 0) || 0
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
  return (fields.value || [])
    .filter((f): f is ModuleField & { fieldCode: string } => !!(f && f.fieldCode && f.hiddenFlag !== 1))
    .slice()
    .sort((a, b) => Number(a.sortNo ?? 0) - Number(b.sortNo ?? 0))
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

async function bootstrap() {
  try {
    // 编辑场景：先拿 record，补齐 appId/modelId，并回填 data
    if (recordId.value) {
      const d = await getRecord(recordId.value)
      const rec = d.data?.record
      const data = d.data?.data
      if (!appId.value) appId.value = Number(rec?.appId || 0) || 0
      if (!modelId.value) modelId.value = Number(rec?.modelId || 0) || 0
      if (data && typeof data === 'object') {
        for (const k of Object.keys(data)) {
          formData[k] = data[k]
        }
      }
    }

    if (!modelId.value) return
    const f = await listFieldsByModel(modelId.value)
    fields.value = (f.data || []) as any

    // 新建：填默认值
    if (!recordId.value) {
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
    if (isDictMulti(mf) || isRefMultiField(mf)) {
      formData[code] = normalizeMultiValue(formData[code])
      continue
    }
    if (isRefField(mf) && !isRefMultiField(mf)) {
      const raw = formData[code]
      if (raw != null && raw !== '') {
        const n = Number(raw)
        formData[code] = Number.isFinite(n) && n > 0 ? n : raw
      }
      continue
    }
    if (isDateField(mf)) {
      const nv = normalizeDateValue(formData[code])
      formData[code] = nv ?? ''
      continue
    }
    if (isBooleanField(mf)) {
      const v = formData[code]
      formData[code] = v === true || v === 1 || v === '1' || v === 'true'
    }
  }
}

async function loadRefOptionsIfNeeded() {
  if (!appId.value) return
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
  if (!appId.value) return
  const dictCodes = Array.from(
    new Set((fields.value || []).map((x) => String(x?.dictCode || '')).filter((x) => !!x))
  )
  if (dictCodes.length === 0) return

  const dictsResp = await listDictsByApp(appId.value)
  const dicts = (dictsResp.data || []) as Array<{ id: number | string; dictCode?: string }>
  const codeToId: Record<string, any> = {}
  for (const d of dicts) {
    if (d?.dictCode) codeToId[String(d.dictCode)] = d.id
  }

  for (const code of dictCodes) {
    if (dictOptionsByCode[code]?.length) continue
    const dictId = codeToId[code]
    if (!dictId) continue
    const itemsResp = await listDictItems(Number(dictId))
    const items = (itemsResp.data || []) as Array<{ itemValue?: any; itemLabel?: string }>
    dictOptionsByCode[code] = items.map((it) => ({ value: it.itemValue ?? '', text: it.itemLabel || String(it.itemValue ?? '') }))
  }
}

function toSubmitData(): any {
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
    if (isDictMulti(mf) || isRefMultiField(mf)) {
      v = normalizeMultiValue(v).map((x) => {
        const n = Number(x)
        return Number.isFinite(n) && n > 0 ? n : x
      })
    }
    if (isRefField(mf) && !isRefMultiField(mf)) {
      const t = String(v ?? '').trim()
      if (t === '') v = null
      else {
        const n = Number(t)
        v = Number.isFinite(n) && n > 0 ? n : t
      }
    }
    if (isDateField(mf)) {
      v = normalizeDateValue(v)
    }
    if (isBooleanField(mf)) {
      v = v === true || v === 1 || v === '1' || v === 'true'
    }
    if (isFileField(mf)) {
      const t = String(v ?? '').trim()
      if (t === '') v = null
      else {
        const n = Number(t)
        v = Number.isNaN(n) ? t : n
      }
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
    if (recordId.value) {
      await updateRecord(recordId.value, dataObj)
      uni.showToast({ title: '更新成功', icon: 'success' })
      back()
      return
    }
    const r = await createRecord({ appId: appId.value, modelId: modelId.value, data: dataObj })
    const id = r.data?.recordId
    uni.showToast({ title: '创建成功', icon: 'success' })
    if (id) {
      uni.redirectTo({ url: `/pages/system/records/detail?recordId=${id}` })
    } else {
      back()
    }
  } finally {
    saving.value = false
  }
}
</script>

