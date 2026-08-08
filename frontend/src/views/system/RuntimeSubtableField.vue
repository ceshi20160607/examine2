<script setup lang="ts">
import { ChevronDown, ChevronUp, Pencil, Plus, RefreshCw, Trash2 } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

import { runtimeApi } from '@/services/config'
import type { RuntimeFieldCapability, RuntimeFieldValue, RuntimeSubRow, RuntimeSubRowInput, RuntimeSubtablePage } from '@/types/config'
import RuntimeFieldDisplay from './RuntimeFieldDisplay.vue'
import RuntimeFieldInput from './RuntimeFieldInput.vue'
import { isBlankRuntimeValue } from './runtimeFieldModel'
import { runtimeErrorMessage } from './runtimeRecordModel'

interface WorkingRow {
  rowId?: string
  clientRowKey: string
  version?: number
  ordinal: number
  values: Record<string, unknown>
  storedValues: Record<string, RuntimeFieldValue>
}

const props = defineProps<{
  systemId: string
  moduleCode: string
  targetModuleCode: string
  field: RuntimeFieldCapability
  recordId?: string
  recordVersion?: number
  modelValue?: RuntimeSubRowInput[]
  readonly?: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: RuntimeSubRowInput[]]
  mutated: [version: number]
}>()

const page = ref<RuntimeSubtablePage | null>(null)
const sourceFields = ref<RuntimeFieldCapability[]>([])
const rows = ref<WorkingRow[]>([])
const loading = ref(false)
const mutating = ref(false)
const error = ref('')
const rowModalOpen = ref(false)
const editingIndex = ref<number | null>(null)
const rowValues = ref<Record<string, unknown>>({})
const rowStoredValues = ref<Record<string, RuntimeFieldValue>>({})
const rowErrors = ref<Record<string, string>>({})

const columnIds = computed(() => Array.isArray(props.field.schema.columnFieldIds) ? props.field.schema.columnFieldIds.map(String) : [])
const columns = computed(() => columnIds.value.flatMap((id) => {
  const field = sourceFields.value.find((candidate) => candidate.logicalFieldId === id)
  return field ? [field] : []
}))
const minRows = computed(() => Number(props.field.schema.minRows ?? 0))
const maxRows = computed(() => Number(props.field.schema.maxRows ?? 200))
const partialColumns = computed(() => columns.value.length < columnIds.value.length)
const canAdd = computed(() => rows.value.length < maxRows.value && !props.readonly && !props.disabled
  && (props.recordId ? page.value?.capabilities.subtableAdd.enabled === true : props.field.writable && props.field.schema.allowRowCreate !== false))
const canUpdate = computed(() => !props.readonly && !props.disabled
  && (props.recordId ? page.value?.capabilities.subtableUpdate.enabled === true : props.field.writable && props.field.schema.allowRowUpdate !== false))
const canRemove = computed(() => rows.value.length > minRows.value && !props.readonly && !props.disabled
  && (props.recordId ? page.value?.capabilities.subtableRemove.enabled === true : props.field.writable && props.field.schema.allowRowDelete !== false))
const canReorder = computed(() => rows.value.length > 1 && !props.readonly && !props.disabled
  && (props.recordId ? page.value?.capabilities.subtableReorder.enabled === true : props.field.writable && props.field.schema.allowRowReorder !== false))

function storedByCode(row: RuntimeSubRow) {
  return Object.fromEntries(row.values.map((value) => [value.fieldCode, value]))
}

function fromServer(row: RuntimeSubRow): WorkingRow {
  return {
    rowId: row.rowId,
    clientRowKey: row.rowId,
    version: row.version,
    ordinal: row.ordinal,
    values: Object.fromEntries(row.values.map((value) => [value.fieldCode, value.value])),
    storedValues: storedByCode(row),
  }
}

function fromDraft(row: RuntimeSubRowInput): WorkingRow {
  const storedValues = Object.fromEntries(columns.value.flatMap((field) => Object.hasOwn(row.values, field.fieldCode)
    ? [[field.fieldCode, { fieldCode: field.fieldCode, fieldName: field.fieldName, type: field.type, value: row.values[field.fieldCode] } as RuntimeFieldValue]] : []))
  return { ...row, storedValues }
}

function publish(next: WorkingRow[]) {
  rows.value = next.map((row, ordinal) => ({ ...row, ordinal }))
  emit('update:modelValue', rows.value.map((row) => ({
    clientRowKey: row.clientRowKey,
    ...(row.rowId ? { rowId: row.rowId } : {}),
    ...(row.version !== undefined ? { expectedVersion: row.version } : {}),
    ordinal: row.ordinal,
    values: { ...row.values },
  })))
}

async function loadSchema() {
  if (!props.targetModuleCode) return
  try {
    const schema = await runtimeApi.recordSchema(props.systemId, props.targetModuleCode)
    sourceFields.value = schema.fields
    if (!props.recordId) publish((props.modelValue ?? []).map(fromDraft))
  } catch (cause) {
    error.value = runtimeErrorMessage(cause, '子表列结构加载失败，请重试。')
  }
}

async function load() {
  if (!props.recordId) {
    if (sourceFields.value.length) publish((props.modelValue ?? []).map(fromDraft))
    return
  }
  loading.value = true
  error.value = ''
  try {
    const firstPage = await runtimeApi.subtable(props.systemId, props.moduleCode, props.recordId, props.field.fieldCode, 1, 100)
    const items = [...firstPage.items]
    const pageCount = Math.ceil(Math.min(firstPage.total, maxRows.value) / firstPage.size)
    for (let pageNumber = 2; pageNumber <= pageCount; pageNumber += 1) {
      const nextPage = await runtimeApi.subtable(
        props.systemId,
        props.moduleCode,
        props.recordId,
        props.field.fieldCode,
        pageNumber,
        firstPage.size,
      )
      items.push(...nextPage.items)
    }
    page.value = { ...firstPage, items }
    publish(items.map(fromServer))
  } catch (cause) {
    error.value = runtimeErrorMessage(cause, `${props.field.fieldName}加载失败，请重试。`)
  } finally {
    loading.value = false
  }
}

function openRow(index?: number) {
  editingIndex.value = index ?? null
  const row = index === undefined ? undefined : rows.value[index]
  rowValues.value = row ? { ...row.values } : {}
  rowStoredValues.value = row ? { ...row.storedValues } : {}
  rowErrors.value = {}
  rowModalOpen.value = true
}

function updateRowValue(fieldCode: string, value: unknown) {
  rowValues.value[fieldCode] = value
  delete rowErrors.value[fieldCode]
}

function validateRow() {
  const invalid: Record<string, string> = {}
  for (const field of columns.value) {
    if (field.schema.required === true && isBlankRuntimeValue(field, rowValues.value[field.fieldCode])) {
      invalid[field.fieldCode] = `${field.fieldName}不能为空`
    }
  }
  rowErrors.value = invalid
  return Object.keys(invalid).length === 0
}

async function mutate(body: Parameters<typeof runtimeApi.mutateSubtable>[4]) {
  if (!props.recordId || props.recordVersion === undefined) return false
  mutating.value = true
  error.value = ''
  try {
    const result = await runtimeApi.mutateSubtable(props.systemId, props.moduleCode, props.recordId, props.field.fieldCode, body)
    emit('mutated', result.version)
    await load()
    return true
  } catch (cause) {
    const message = runtimeErrorMessage(cause, `${props.field.fieldName}保存失败，已恢复服务端内容。`)
    await load()
    error.value = message
    return false
  } finally {
    mutating.value = false
  }
}

async function saveRow() {
  if (!validateRow()) return
  const index = editingIndex.value
  if (props.recordId) {
    const body = { expectedVersion: props.recordVersion!, add: [], update: [], remove: [], order: [] } as Parameters<typeof runtimeApi.mutateSubtable>[4]
    if (index === null) {
      body.add = [{ clientRowKey: crypto.randomUUID(), ordinal: rows.value.length, values: { ...rowValues.value } }]
    } else {
      const row = rows.value[index]!
      body.update = [{ rowId: row.rowId!, expectedVersion: row.version!, ordinal: row.ordinal, values: { ...rowValues.value } }]
    }
    if (await mutate(body)) rowModalOpen.value = false
    return
  }
  if (index === null) {
    publish([...rows.value, { clientRowKey: crypto.randomUUID(), ordinal: rows.value.length, values: { ...rowValues.value }, storedValues: {} }])
  } else {
    const next = [...rows.value]
    next[index] = { ...next[index]!, values: { ...rowValues.value }, storedValues: { ...rowStoredValues.value } }
    publish(next)
  }
  rowModalOpen.value = false
}

async function removeRow(index: number) {
  const row = rows.value[index]!
  if (props.recordId) {
    await mutate({ expectedVersion: props.recordVersion!, add: [], update: [], remove: [{ rowId: row.rowId!, expectedVersion: row.version! }], order: [] })
  } else publish(rows.value.filter((_, candidate) => candidate !== index))
}

async function move(index: number, offset: number) {
  const target = index + offset
  if (target < 0 || target >= rows.value.length) return
  const next = [...rows.value]
  const [row] = next.splice(index, 1)
  next.splice(target, 0, row!)
  if (props.recordId) {
    await mutate({ expectedVersion: props.recordVersion!, add: [], update: [], remove: [], order: next.map((item) => item.rowId!) })
  } else publish(next)
}

function rowFieldValue(row: WorkingRow, field: RuntimeFieldCapability): RuntimeFieldValue | undefined {
  return row.storedValues[field.fieldCode] ?? (Object.hasOwn(row.values, field.fieldCode)
    ? { fieldCode: field.fieldCode, fieldName: field.fieldName, type: field.type, value: row.values[field.fieldCode] }
    : undefined)
}

watch(() => props.targetModuleCode, () => { void loadSchema() }, { immediate: true })
watch(() => [props.recordId, props.field.fieldCode], () => { void load() }, { immediate: true })
watch(() => props.modelValue, (value) => { if (!props.recordId && sourceFields.value.length) rows.value = (value ?? []).map(fromDraft) }, { deep: true })
</script>

<template>
  <section class="subtable-field" :aria-label="field.fieldName">
    <a-alert v-if="!targetModuleCode" type="warning" show-icon message="子表来源模块当前不可用" />
    <a-alert v-else-if="error" type="error" show-icon :message="error"><template #action><a-button size="small" @click="load"><RefreshCw :size="14" />重试</a-button></template></a-alert>
    <a-alert v-if="partialColumns" type="info" show-icon message="部分子表列因当前权限不可见" />
    <div class="subtable-toolbar">
      <span>{{ rows.length }} / {{ maxRows }} 行<span v-if="minRows">，至少 {{ minRows }} 行</span></span>
      <a-button v-if="canAdd" type="button" :disabled="mutating" @click="openRow()"><Plus :size="15" />新增行</a-button>
    </div>
    <a-spin :spinning="loading || mutating">
      <div v-if="rows.length" class="subtable-grid-wrap">
        <table class="subtable-grid">
          <thead><tr><th>序号</th><th v-for="column in columns" :key="column.fieldCode">{{ column.fieldName }}</th><th v-if="!readonly">操作</th></tr></thead>
          <tbody><tr v-for="(row, index) in rows" :key="row.rowId || row.clientRowKey"><td>{{ index + 1 }}</td><td v-for="column in columns" :key="column.fieldCode"><RuntimeFieldDisplay :field="rowFieldValue(row, column)" /></td><td v-if="!readonly"><div class="row-actions"><button v-if="canReorder" type="button" title="上移" :disabled="index === 0 || mutating" @click="move(index, -1)"><ChevronUp :size="15" /></button><button v-if="canReorder" type="button" title="下移" :disabled="index === rows.length - 1 || mutating" @click="move(index, 1)"><ChevronDown :size="15" /></button><button v-if="canUpdate" type="button" title="编辑行" :disabled="mutating" @click="openRow(index)"><Pencil :size="15" /></button><button v-if="canRemove" type="button" title="删除行" :disabled="mutating" @click="removeRow(index)"><Trash2 :size="15" /></button></div></td></tr></tbody>
        </table>
      </div>
      <div v-if="rows.length" class="subtable-mobile-list">
        <article v-for="(row, index) in rows" :key="row.rowId || row.clientRowKey" class="subtable-card"><header><strong>第 {{ index + 1 }} 行</strong><div v-if="!readonly" class="row-actions"><button v-if="canReorder" type="button" title="上移" :disabled="index === 0 || mutating" @click="move(index, -1)"><ChevronUp :size="15" /></button><button v-if="canReorder" type="button" title="下移" :disabled="index === rows.length - 1 || mutating" @click="move(index, 1)"><ChevronDown :size="15" /></button><button v-if="canUpdate" type="button" title="编辑行" @click="openRow(index)"><Pencil :size="15" /></button><button v-if="canRemove" type="button" title="删除行" @click="removeRow(index)"><Trash2 :size="15" /></button></div></header><dl><div v-for="column in columns" :key="column.fieldCode"><dt>{{ column.fieldName }}</dt><dd><RuntimeFieldDisplay :field="rowFieldValue(row, column)" /></dd></div></dl></article>
      </div>
      <div v-else-if="!loading" class="subtable-empty">暂无明细行</div>
    </a-spin>

    <a-modal :open="rowModalOpen" :title="editingIndex === null ? '新增明细行' : '编辑明细行'" width="min(620px, calc(100vw - 24px))" :confirm-loading="mutating" ok-text="保存行" cancel-text="取消" @ok="saveRow" @cancel="rowModalOpen = false">
      <div class="row-editor">
        <div v-for="column in columns.filter((item) => item.writable)" :key="column.fieldCode" class="row-editor-field">
          <label :for="`subtable-${field.fieldCode}-${column.fieldCode}`">{{ column.fieldName }}<span v-if="column.schema.required === true">*</span></label>
          <RuntimeFieldInput :input-id="`subtable-${field.fieldCode}-${column.fieldCode}`" :field="column" :model-value="rowValues[column.fieldCode]" :stored-value="rowStoredValues[column.fieldCode]" @update:model-value="(value) => updateRowValue(column.fieldCode, value)" />
          <small v-if="rowErrors[column.fieldCode]">{{ rowErrors[column.fieldCode] }}</small>
        </div>
      </div>
    </a-modal>
  </section>
</template>

<style scoped>
.subtable-field{display:grid;gap:10px;min-width:0}.subtable-toolbar{display:flex;align-items:center;justify-content:space-between;gap:12px;color:#687680;font-size:12px}.subtable-toolbar .ant-btn,.ant-alert .ant-btn{display:inline-flex;align-items:center;gap:6px}.subtable-grid-wrap{width:100%;overflow:auto;border:1px solid #dce3e7}.subtable-grid{width:100%;min-width:680px;border-collapse:collapse}.subtable-grid th,.subtable-grid td{height:44px;padding:7px 9px;border-bottom:1px solid #e4e9ec;text-align:left;white-space:nowrap}.subtable-grid th{background:#f5f7f8;color:#64727c;font-size:12px}.subtable-grid tr:last-child td{border-bottom:0}.subtable-grid th:first-child,.subtable-grid td:first-child{width:50px}.subtable-grid th:last-child,.subtable-grid td:last-child{width:132px}.row-actions{display:flex;justify-content:flex-end;gap:2px}.row-actions button{display:grid;place-items:center;width:27px;height:27px;border:0;background:transparent;color:#65737d;cursor:pointer}.row-actions button:hover:not(:disabled){background:#e8f3f1;color:#087f73}.row-actions button:disabled{opacity:.35;cursor:not-allowed}.subtable-empty{display:grid;place-items:center;min-height:72px;border:1px dashed #cfd8dd;color:#75818a;background:#fafbfc}.subtable-mobile-list{display:none}.row-editor{display:grid;gap:16px}.row-editor-field{display:grid;gap:6px}.row-editor-field label{font-weight:650}.row-editor-field label span,.row-editor-field small{color:#c33b32}@media(max-width:720px){.subtable-grid-wrap{display:none}.subtable-mobile-list{display:grid;gap:10px}.subtable-card{border:1px solid #dce3e7;background:#fff}.subtable-card header{min-height:42px;display:flex;align-items:center;justify-content:space-between;padding:5px 9px;border-bottom:1px solid #e3e8eb;background:#f7f9fa}.subtable-card dl{margin:0;padding:3px 10px}.subtable-card dl>div{display:grid;grid-template-columns:100px minmax(0,1fr);gap:8px;padding:8px 0;border-bottom:1px solid #edf0f2}.subtable-card dl>div:last-child{border-bottom:0}.subtable-card dt{color:#6c7982}.subtable-card dd{margin:0;overflow-wrap:anywhere}}
</style>
