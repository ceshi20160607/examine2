<template>
  <view style="padding: 16px">
    <uni-card :title="title">
      <uni-forms labelPosition="top">
        <uni-forms-item v-if="!advancedJson" label="字段表单">
          <view v-if="fields.length === 0" style="color:#666">加载字段中...</view>
          <view v-else style="display:flex; flex-direction: column; gap: 12px;">
            <view v-for="f in visibleFields" :key="String(f.id)">
              <view style="margin-bottom: 6px; color:#333">
                {{ f.fieldName || f.fieldCode }}
                <text v-if="f.requiredFlag === 1" style="color:#d00"> *</text>
              </view>

              <uni-easyinput
                v-if="isTextField(f)"
                v-model="formData[f.fieldCode]"
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

              <uni-easyinput
                v-else
                v-model="formData[f.fieldCode]"
                :placeholder="`${f.fieldType || 'text'}（暂按文本处理）`"
              />
            </view>
          </view>
        </uni-forms-item>

        <uni-forms-item v-else label="JSON data（对象）">
          <uni-easyinput v-model="jsonText" type="textarea" :autoHeight="true" placeholder="请输入 JSON 对象" />
        </uni-forms-item>
      </uni-forms>

      <view style="display:flex; gap: 8px;">
        <uni-button type="primary" :disabled="saving" @click="submit">{{ recordId ? '更新' : '创建' }}</uni-button>
        <uni-button @click="back">返回</uni-button>
        <uni-button @click="toggleAdvanced">{{ advancedJson ? '切回表单' : '高级 JSON' }}</uni-button>
      </view>

      <view v-if="error" style="margin-top: 12px; color:#d00">{{ error }}</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet, httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

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

type ModuleField = {
  id: number | string
  fieldCode: string
  fieldName?: string
  fieldType?: string
  requiredFlag?: number
  hiddenFlag?: number
  tips?: string
  dictCode?: string
  multiFlag?: number
  defaultValue?: string
  sortNo?: number
}

const fields = ref<ModuleField[]>([])
const formData = reactive<Record<string, any>>({})
const dictOptionsByCode = reactive<Record<string, Array<{ value: any; text: string }>>>({})

const visibleFields = computed(() => {
  return (fields.value || [])
    .filter((f) => f && f.fieldCode && f.hiddenFlag !== 1)
    .slice()
    .sort((a, b) => Number(a.sortNo ?? 0) - Number(b.sortNo ?? 0))
})

function isNumberField(f: ModuleField) {
  const t = String(f.fieldType || '').toLowerCase()
  return t === 'number' || t === 'int' || t === 'integer' || t === 'long' || t === 'decimal' || t === 'double' || t === 'float'
}
function isDateField(f: ModuleField) {
  const t = String(f.fieldType || '').toLowerCase()
  return t === 'date' || t === 'datetime' || t === 'time'
}
function datePickerType(f: ModuleField): 'date' | 'datetime' | 'time' {
  const t = String(f.fieldType || '').toLowerCase()
  if (t === 'datetime') return 'datetime'
  if (t === 'time') return 'time'
  return 'date'
}
function isTextField(f: ModuleField) {
  return !isNumberField(f) && !isDateField(f) && !f.dictCode
}
function isDictSingle(f: ModuleField) {
  return !!f.dictCode && (f.multiFlag ?? 0) !== 1
}
function isDictMulti(f: ModuleField) {
  return !!f.dictCode && (f.multiFlag ?? 0) === 1
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
      const d = await httpGet<any>(`/v1/system/records/${recordId.value}`)
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
    const f = await httpGet<ModuleField[]>(`/v1/system/module/meta/models/${modelId.value}/fields`)
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
        } else {
          formData[mf.fieldCode] = ''
        }
      }
    }

    // 兼容：把多选字典值规范成数组；日期时间值规范成字符串
    normalizeFormDataValues()

    // 字典选项
    await loadDictOptionsIfNeeded()
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
    if (isDictMulti(mf)) {
      formData[code] = normalizeMultiValue(formData[code])
      continue
    }
    if (isDateField(mf)) {
      const nv = normalizeDateValue(formData[code])
      formData[code] = nv ?? ''
    }
  }
}

async function loadDictOptionsIfNeeded() {
  if (!appId.value) return
  const dictCodes = Array.from(
    new Set((fields.value || []).map((x) => String(x?.dictCode || '')).filter((x) => !!x))
  )
  if (dictCodes.length === 0) return

  const dictsResp = await httpGet<any>(`/v1/system/module/dicts/apps/${appId.value}`)
  const dicts = (dictsResp.data || []) as Array<{ id: number | string; dictCode?: string }>
  const codeToId: Record<string, any> = {}
  for (const d of dicts) {
    if (d?.dictCode) codeToId[String(d.dictCode)] = d.id
  }

  for (const code of dictCodes) {
    if (dictOptionsByCode[code]?.length) continue
    const dictId = codeToId[code]
    if (!dictId) continue
    const itemsResp = await httpGet<any>(`/v1/system/module/dicts/${dictId}/items`)
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
    if (isDictMulti(mf)) {
      v = normalizeMultiValue(v)
    }
    if (isDateField(mf)) {
      v = normalizeDateValue(v)
    }
    out[code] = v
  }
  return out
}

function validateRequired(dataObj: any): string | null {
  for (const mf of visibleFields.value) {
    if (mf.requiredFlag !== 1) continue
    const v = dataObj[mf.fieldCode]
    const empty = v == null || v === '' || (Array.isArray(v) && v.length === 0)
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
      await httpPost(`/v1/system/records/${recordId.value}/update`, { data: dataObj })
      uni.showToast({ title: '更新成功', icon: 'success' })
      back()
      return
    }
    const r = await httpPost<any>('/v1/system/records', { appId: appId.value, modelId: modelId.value, data: dataObj })
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

