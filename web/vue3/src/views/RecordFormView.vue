<template>
  <AdminLayout>
    <h2>{{ title }}</h2>
    <p class="muted">
      appId={{ appId }} modelId={{ modelId }}
      <span v-if="embedMode"> · 嵌入模式（保存后返回）</span>
    </p>
    <div class="toolbar">
      <button type="button" :disabled="saving || !canManageRecords" @click="submit">
        {{ saving ? '保存中…' : (recordId ? '更新' : '创建') }}
      </button>
      <button type="button" class="secondary" @click="goBack">返回</button>
      <button v-if="!embedMode" type="button" class="secondary" @click="toggleAdvanced">
        {{ advancedJson ? '切回表单' : '高级 JSON' }}
      </button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <template v-if="advancedJson && !embedMode">
      <textarea v-model="jsonText" class="json-area" rows="16" />
    </template>
    <template v-else>
      <p v-if="loadingFields" class="muted">加载字段…</p>
      <div v-else class="form-fields">
        <div v-for="f in visibleFields" :key="f.id || f.fieldCode" class="form-row">
          <template v-if="isTitleField(f)">
            <h3 class="form-title">{{ f.fieldName || f.fieldCode }}</h3>
          </template>

          <template v-else>
            <label class="form-label">
              {{ f.fieldName || f.fieldCode }}
              <span v-if="f.requiredFlag === 1" class="req">*</span>
            </label>

            <input
              v-if="isPlainTextField(f) || isValidatedTextField(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
              :type="inputTypeForField(f) || 'text'"
              :placeholder="f.tips || ''"
            />

            <input
              v-else-if="isPasswordField(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
              type="password"
            />

            <textarea
              v-else-if="isTextareaField(f) || isRichTextField(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
              rows="4"
            />

            <input
              v-else-if="isNumberField(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
              type="number"
            />

            <input
              v-else-if="isDateField(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
              type="datetime-local"
            />

            <div v-else-if="isDateRangeField(f)" class="date-range">
              <input v-model="dateRangeStart[f.fieldCode]" type="datetime-local" class="field-input" />
              <span>至</span>
              <input v-model="dateRangeEnd[f.fieldCode]" type="datetime-local" class="field-input" />
            </div>

            <label v-else-if="isBooleanField(f)" class="check-row">
              <input type="checkbox" :checked="boolValue(f)" @change="setBool(f, $event.target.checked)" />
              是
            </label>

            <select
              v-else-if="isDictSingle(f) || isRadioSelectField(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
            >
              <option value="">请选择</option>
              <option v-for="o in dictOptionsByCode[f.dictCode] || []" :key="o.value" :value="o.value">
                {{ o.text }}
              </option>
            </select>

            <select
              v-else-if="isDictMulti(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
              multiple
            >
              <option v-for="o in dictOptionsByCode[f.dictCode] || []" :key="o.value" :value="o.value">
                {{ o.text }}
              </option>
            </select>

            <select
              v-else-if="isRefField(f) && !isRefMultiField(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
            >
              <option value="">请选择</option>
              <option
                v-for="o in refOptionsByCode[f.fieldCode] || []"
                :key="o.value"
                :value="o.value"
              >
                {{ o.text }}
              </option>
            </select>

            <select
              v-else-if="isRefMultiField(f) && !isRefTableField(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
              multiple
            >
              <option
                v-for="o in refOptionsByCode[f.fieldCode] || []"
                :key="o.value"
                :value="o.value"
              >
                {{ o.text }}
              </option>
            </select>

            <RefSubTableField
              v-else-if="isRefTableField(f)"
              :field="f"
              :app-id="appId"
              :parent-record-id="recordId"
              :parent-model-id="modelId"
              v-model="formData[f.fieldCode]"
              :options="refOptionsByCode[f.fieldCode] || []"
            />

            <select
              v-else-if="isPersonField(f) && !isPersonMulti(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
            >
              <option value="">选择人员</option>
              <option v-for="o in memberOptionsFor(f)" :key="o.value" :value="o.value">{{ o.text }}</option>
            </select>

            <select
              v-else-if="isPersonField(f) && isPersonMulti(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
              multiple
            >
              <option v-for="o in memberOptionsFor(f)" :key="o.value" :value="o.value">{{ o.text }}</option>
            </select>

            <select
              v-else-if="isDepartmentField(f) && !isDepartmentMulti(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
            >
              <option value="">选择部门</option>
              <option v-for="o in departmentOptions" :key="o.value" :value="o.value">{{ o.text }}</option>
            </select>

            <select
              v-else-if="isDepartmentField(f) && isDepartmentMulti(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
              multiple
            >
              <option v-for="o in departmentOptions" :key="o.value" :value="o.value">{{ o.text }}</option>
            </select>

            <div v-else-if="isFileField(f)" class="file-row">
              <span class="muted">fileId: {{ formData[f.fieldCode] || '-' }}</span>
              <input type="file" @change="(e) => uploadFor(f, e)" />
              <button type="button" class="secondary" @click="formData[f.fieldCode] = null">清除</button>
            </div>

            <input
              v-else-if="isSerialNoField(f)"
              v-model="formData[f.fieldCode]"
              class="field-input"
              :disabled="!recordId"
              :placeholder="recordId ? '' : '保存时自动生成'"
            />

            <RegionAddressField
              v-else-if="isAddressField(f)"
              v-model="formData[f.fieldCode]"
              :field="f"
            />

            <TagField v-else-if="isTagField(f)" v-model="formData[f.fieldCode]" :field="f" />

            <SignatureField
              v-else-if="isSignatureField(f)"
              v-model="formData[f.fieldCode]"
            />

            <RatingField
              v-else-if="isRatingSelectField(f)"
              v-model="formData[f.fieldCode]"
              :max="Number(configFromMeta(f).maxStars) || 5"
            />

            <input v-else v-model="formData[f.fieldCode]" class="field-input" />
          </template>
        </div>
      </div>
    </template>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import RefSubTableField from '../components/fields/RefSubTableField.vue'
import RegionAddressField from '../components/fields/RegionAddressField.vue'
import TagField from '../components/fields/TagField.vue'
import SignatureField from '../components/fields/SignatureField.vue'
import RatingField from '../components/fields/RatingField.vue'
import { listDictItems, listDictsByApp } from '../api/module.js'
import { listFieldsByModel } from '../api/meta.js'
import { createRecord, getRecord, updateRecord } from '../api/records.js'
import { listDepartmentPickerOptions, listMemberPickerOptions } from '../api/rbac.js'
import { uploadFile } from '../api/upload.js'
import { setEmbedRecordCreated } from '../utils/embedRecord.js'
import { loadRefSelectOptions } from '../utils/refPicker.js'
import { uniqueIds } from '../utils/id.js'
import { notify } from '../utils/notify.js'
import { createModulePermState, MODULE_PERMS } from '../utils/modulePerms.js'
import {
  configFromMeta,
  inputTypeForField,
  isAddressField,
  isBooleanField,
  isDateField,
  isDateRangeField,
  isDepartmentField,
  isDictMulti,
  isDictSingle,
  isFileField,
  isNumberField,
  isPasswordField,
  isPersonField,
  isPlainTextField,
  isRadioSelectField,
  isRatingSelectField,
  isRefField,
  isSignatureField,
  isRefMultiField,
  isRefTableField,
  isRichTextField,
  isSerialNoField,
  isTagField,
  isTextareaField,
  isTitleField,
  isValidatedTextField
} from '../utils/fieldTypes.js'

const route = useRoute()
const router = useRouter()

const appId = computed(() => String(route.query.appId || ''))
const modelId = computed(() => String(route.query.modelId || ''))
const pageId = computed(() => String(route.query.pageId || ''))
const recordId = computed(() => String(route.query.recordId || ''))
const embedMode = computed(() => route.query.embed === '1')

const advancedJson = ref(false)
const jsonText = ref('{}')
const loadingFields = ref(false)
const saving = ref(false)
const error = ref('')
const fields = ref([])
const formData = reactive({})
const dictOptionsByCode = reactive({})
const refOptionsByCode = reactive({})
const memberOptions = ref([])
const memberOptionsByCode = reactive({})
const departmentOptions = ref([])
const dateRangeStart = reactive({})
const dateRangeEnd = reactive({})
const { loadModulePerms, hasModulePerm } = createModulePermState()

const title = computed(() => (recordId.value ? `编辑记录 #${recordId.value}` : '新建记录'))

const canManageRecords = computed(() => hasModulePerm(MODULE_PERMS.records))

const visibleFields = computed(() =>
  (fields.value || []).filter((f) => f.hiddenFlag !== 1).sort((a, b) => (a.sortNo || 0) - (b.sortNo || 0))
)

function isPersonMulti(f) {
  return isPersonField(f) && (configFromMeta(f).multi === true || (f.multiFlag ?? 0) === 1)
}
function isDepartmentMulti(f) {
  return isDepartmentField(f) && (configFromMeta(f).multi === true || (f.multiFlag ?? 0) === 1)
}
function memberOptionsFor(f) {
  const code = f.fieldCode || ''
  return memberOptionsByCode[code]?.length ? memberOptionsByCode[code] : memberOptions.value
}

function boolValue(f) {
  const v = formData[f.fieldCode]
  return v === true || v === 1 || v === '1' || v === 'true'
}
function setBool(f, checked) {
  formData[f.fieldCode] = checked
}

function normalizeMultiValue(v) {
  if (v == null || v === '') return []
  if (Array.isArray(v)) return v
  if (typeof v === 'string') {
    const t = v.trim()
    if (!t) return []
    if (t.startsWith('[')) {
      try {
        const arr = JSON.parse(t)
        return Array.isArray(arr) ? arr : []
      } catch {
        return t.split(',').map((x) => x.trim()).filter(Boolean)
      }
    }
    return t.split(',').map((x) => x.trim()).filter(Boolean)
  }
  return [v]
}

function syncDateRangeFromForm() {
  for (const mf of fields.value || []) {
    const code = mf?.fieldCode
    if (!code || !isDateRangeField(mf)) continue
    const start = dateRangeStart[code] || ''
    const end = dateRangeEnd[code] || ''
    formData[code] = start || end ? JSON.stringify({ start, end }) : ''
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
      /* ignore */
    }
  }
}

function applyEmbedLinkDefaults() {
  const pid = route.query.linkParentId
  const fk = route.query.linkFkField
  if (!pid || !fk) return
  const code = String(fk).trim()
  if (!code) return
  const existing = formData[code]
  if (existing != null && existing !== '') return
  formData[code] = String(pid)
}

function applyDefaults() {
  for (const mf of fields.value || []) {
    const code = mf?.fieldCode
    if (!code || formData[code] != null) continue
    if (mf.defaultValue != null && mf.defaultValue !== '') {
      formData[code] = mf.defaultValue
    }
  }
}

async function loadDictOptions() {
  if (!appId.value) return
  const codes = [...new Set((fields.value || []).map((x) => x.dictCode).filter(Boolean))]
  const dictsResp = await listDictsByApp(appId.value)
  const codeToId = {}
  for (const d of dictsResp.data || []) {
    if (d.dictCode) codeToId[d.dictCode] = d.id
  }
  for (const code of codes) {
    if (dictOptionsByCode[code]?.length) continue
    const dictId = codeToId[code]
    if (!dictId) continue
    const itemsResp = await listDictItems(dictId)
    dictOptionsByCode[code] = (itemsResp.data || []).map((it) => ({
      value: it.itemValue ?? '',
      text: it.itemLabel || String(it.itemValue ?? '')
    }))
  }
}

async function loadRefOptions() {
  for (const f of fields.value || []) {
    if (!isRefField(f) || !f.fieldCode) continue
    refOptionsByCode[f.fieldCode] = await loadRefSelectOptions({ appId: appId.value, field: f })
  }
}

async function loadMemberDeptOptions() {
  try {
    const [mr, dr] = await Promise.all([
      listMemberPickerOptions(appId.value),
      listDepartmentPickerOptions(appId.value)
    ])
    memberOptions.value = mr.data || []
    departmentOptions.value = dr.data || []
    for (const f of fields.value || []) {
      if (!isPersonField(f) || !f.fieldCode) continue
      const scope = configFromMeta(f).scope
      if (scope) {
        try {
          const r = await listMemberPickerOptions(appId.value, scope)
          memberOptionsByCode[f.fieldCode] = r.data || []
        } catch {
          memberOptionsByCode[f.fieldCode] = memberOptions.value
        }
      }
    }
  } catch {
    /* optional */
  }
}

async function bootstrap() {
  if (!modelId.value) return
  loadingFields.value = true
  error.value = ''
  try {
    const r = await listFieldsByModel(modelId.value)
    fields.value = r.data || []
    await Promise.all([loadDictOptions(), loadRefOptions(), loadMemberDeptOptions()])
    applyDefaults()
    if (recordId.value) await loadRecord()
    else {
      Object.keys(formData).forEach((k) => delete formData[k])
      applyDefaults()
    }
    applyEmbedLinkDefaults()
    parseDateRangeToPickers()
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    loadingFields.value = false
  }
}

async function loadRecord() {
  const r = await getRecord(recordId.value)
  const data = r.data?.data || {}
  Object.keys(formData).forEach((k) => delete formData[k])
  Object.assign(formData, data)
  for (const mf of fields.value || []) {
    const code = mf.fieldCode
    if (!code) continue
    if (isDictMulti(mf) || (isRefMultiField(mf) && !isRefTableField(mf)) || isPersonMulti(mf) || isDepartmentMulti(mf)) {
      formData[code] = normalizeMultiValue(data[code])
    }
    if (isRefTableField(mf)) {
      formData[code] = uniqueIds(normalizeMultiValue(data[code]))
    }
  }
  jsonText.value = JSON.stringify(data, null, 2)
  parseDateRangeToPickers()
}

function toSubmitData() {
  syncDateRangeFromForm()
  const out = {}
  for (const mf of fields.value || []) {
    const code = mf?.fieldCode
    if (!code || isTitleField(mf)) continue
    let v = formData[code]
    if (isNumberField(mf)) {
      const t = String(v ?? '').trim()
      v = t === '' ? null : Number.isNaN(Number(t)) ? t : Number(t)
    }
    if (isRefTableField(mf) || (isRefMultiField(mf) && !isRefTableField(mf))) {
      v = uniqueIds(normalizeMultiValue(v))
    }
    if (isDictMulti(mf)) {
      v = normalizeMultiValue(v)
    }
    if (isFileField(mf) || (isRefField(mf) && !isRefMultiField(mf))) {
      const t = String(v ?? '').trim()
      if (t === '') v = null
      else v = t
    }
    if (isBooleanField(mf)) {
      v = v === true || v === 1 || v === '1' || v === 'true'
    }
    if (isDateRangeField(mf)) {
      const start = dateRangeStart[code] || ''
      const end = dateRangeEnd[code] || ''
      v = start || end ? { start, end } : null
    }
  if (isSerialNoField(mf) && !recordId.value) {
      const t = String(v ?? '').trim()
      v = t === '' ? null : t
    }
    out[code] = v
  }
  return out
}

function validateRequired(dataObj) {
  for (const mf of visibleFields.value) {
    if (mf.requiredFlag !== 1 || isTitleField(mf)) continue
    const v = dataObj[mf.fieldCode]
    let empty = v == null || v === '' || (Array.isArray(v) && !v.length)
    if (isBooleanField(mf)) empty = false
    if (empty) return `${mf.fieldName || mf.fieldCode} 不能为空`
  }
  return null
}

async function uploadFor(f, ev) {
  const file = ev.target?.files?.[0]
  if (!file) return
  try {
    const r = await uploadFile(file)
    const id = r.data?.id ?? r.data?.fileId
    formData[f.fieldCode] = id != null ? id : null
    notify.success('上传成功')
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

function toggleAdvanced() {
  if (!advancedJson.value) {
    jsonText.value = JSON.stringify(toSubmitData(), null, 2)
    advancedJson.value = true
    return
  }
  try {
    Object.assign(formData, JSON.parse(jsonText.value || '{}'))
    parseDateRangeToPickers()
    advancedJson.value = false
  } catch {
    error.value = 'JSON 格式错误'
  }
}

function goBack() {
  if (embedMode.value) router.back()
  else router.push({ path: '/records', query: { appId: appId.value, modelId: modelId.value, pageId: pageId.value } })
}

async function submit() {
  error.value = ''
  if (!canManageRecords.value) {
    error.value = '当前账号没有记录维护权限'
    return
  }
  let dataObj
  if (advancedJson.value && !embedMode.value) {
    try {
      dataObj = JSON.parse(jsonText.value || '{}')
    } catch {
      error.value = 'JSON 非法'
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
      notify.success('更新成功')
      if (!embedMode.value) goBack()
      return
    }
    const r = await createRecord({ appId: appId.value, modelId: modelId.value, data: dataObj })
    const id = r.data?.recordId || r.data?.record?.id || r.data?.id
    notify.success('创建成功')
    if (embedMode.value && id) {
      setEmbedRecordCreated(id)
      router.back()
      return
    }
    if (id) {
      router.replace({ path: '/records/form', query: { ...route.query, recordId: id } })
    }
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    saving.value = false
  }
}

watch(
  () => [route.query.appId, route.query.modelId, route.query.recordId],
  () => bootstrap(),
  { deep: true }
)
onMounted(async () => {
  await loadModulePerms()
  await bootstrap()
})
</script>

<style scoped>
.form-fields {
  max-width: 860px;
  display: grid;
  gap: 0.95rem;
  padding: 1rem;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
}
.form-row {
  display: grid;
  grid-template-columns: minmax(140px, 200px) minmax(0, 1fr);
  gap: 0.8rem;
  align-items: start;
}
.form-label {
  display: block;
  font-weight: 500;
  padding-top: 0.5rem;
  color: #43524b;
}
.form-title {
  grid-column: 1 / -1;
  margin: 0.35rem 0 0;
  font-size: 1rem;
  color: var(--color-text);
  border-bottom: 1px solid var(--color-border);
  padding-bottom: 0.55rem;
}
.req {
  color: var(--color-danger);
}
.field-input {
  width: 100%;
  min-height: 38px;
  padding: 0.48rem 0.6rem;
  border: 1px solid var(--color-border-strong);
  border-radius: 7px;
  box-sizing: border-box;
  background: #fff;
}
.date-range {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
}
.check-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.file-row {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}
.json-area {
  width: 100%;
  min-height: 320px;
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
  padding: 0.75rem;
  border: 1px solid var(--color-border-strong);
  border-radius: 7px;
  box-sizing: border-box;
}
.muted {
  color: var(--color-muted);
  font-size: 0.9rem;
}
.error {
  color: var(--color-danger);
  background: var(--color-danger-soft);
  border: 1px solid #ffd3cf;
  border-radius: 7px;
  padding: 0.55rem 0.7rem;
}
.toolbar {
  display: flex;
  gap: 0.65rem;
  margin: 1rem 0;
  flex-wrap: wrap;
}
.toolbar button {
  min-height: 36px;
  padding: 0.45rem 0.85rem;
  background: var(--color-primary);
  color: #fff;
  border: 1px solid var(--color-primary);
  border-radius: 7px;
  cursor: pointer;
  font-weight: 700;
}
.toolbar button.secondary {
  background: #fff;
  color: var(--color-text);
  border: 1px solid var(--color-border-strong);
}

@media (max-width: 760px) {
  .form-fields {
    padding: 0.85rem;
  }
  .form-row {
    grid-template-columns: 1fr;
    gap: 0.35rem;
  }
  .form-label {
    padding-top: 0;
  }
}
</style>
