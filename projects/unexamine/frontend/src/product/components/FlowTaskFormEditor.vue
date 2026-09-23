<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ApiError, api } from '../api'
import { fieldTypes, isSharedRuntimeField } from '../field-capabilities'
import type { FlowTaskFormContext, RuntimeDictionary, RuntimeField, SystemPeopleDirectory } from '../types'
import DepartmentSelect from './DepartmentSelect.vue'
import PersonSelect from './PersonSelect.vue'
import RuntimeFieldInput from './RuntimeFieldInput.vue'
import SchemaFormLayout from './SchemaFormLayout.vue'
import SchemaRecordDetail from './SchemaRecordDetail.vue'

const props = defineProps<{ taskId: number; instanceId: number; token?: string; identity: string; disabled?: boolean }>()
const emit = defineEmits<{ ready: [value: boolean]; denied: [] }>()
type CascadeOption = { value: number; label: string; children?: CascadeOption[] }
type ReferenceOption = { value: number; label: string; description: string; disabled?: boolean }
const context = ref<FlowTaskFormContext>()
const loading = ref(false)
const ready = ref(false)
const error = ref('')
const conflict = ref('')
const recoveryNotice = ref('')
const values = reactive<Record<string, unknown>>({})
const initial = new Map<string, unknown>()
const baseline = new Map<string, unknown>()
const dirty = reactive(new Set<string>())
type RecoveryField = { id: number; code: string; fieldType: string; value: unknown }
const recoveryDraft = new Map<string, RecoveryField>()
const organizations = reactive<Record<string, SystemPeopleDirectory>>({})
const dictionaries = reactive<Record<string, Array<{ value: string; label: string }>>>({})
const cascades = reactive<Record<string, CascadeOption[]>>({})
const references = reactive<Record<string, ReferenceOption[]>>({})
const referenceLoading = reactive<Record<string, boolean>>({})
const referenceErrors = reactive<Record<string, string>>({})
const referenceRequests = new Map<string, number>()
const referenceTimers = new Map<string, ReturnType<typeof setTimeout>>()
let active = true
let generation = 0
function editableFields(source: FlowTaskFormContext | undefined) {
  return (source?.form?.configuration.configuration.fields || [])
  .filter(field => field.status === 'ACTIVE' && field.access?.writable === true
    && !field.fieldType.startsWith('SYSTEM_') && !fieldConfig(field).readOnly)
  .sort((left, right) => left.sortOrder - right.sortOrder)
}
const fields = computed(() => editableFields(context.value))
// 只读字段复用自身的受保护描述；不进入控件值、选项目录和提交补丁。
const readOnlyFields = computed(() => (context.value?.form?.configuration.configuration.fields || [])
  .filter(field => field.status === 'ACTIVE' && field.access?.readable === true && field.access?.writable !== true)
  .sort((left, right) => left.sortOrder - right.sortOrder))
// 核对区只取刷新后安全投影，不展示密码或掩码字段的明文比较。
const reviewFields = computed(() => recoveryNotice.value ? fields.value.filter(field => dirty.has(field.code) && !protectedField(field)) : [])

function fieldConfig(field: RuntimeField): { readOnly?: boolean; maxDepth?: number; allowIntermediate?: boolean; saveMode?: string; options?: Array<{ code?: string; value?: string; label?: string; status?: string; disabled?: boolean }> } {
  try { return JSON.parse(field.configJson || '{}') }
  catch { return {} }
}
function supported(field: RuntimeField) {
  return isSharedRuntimeField(field.fieldType) || ['MEMBER', 'DEPARTMENT', 'CASCADE', 'REFERENCE'].includes(field.fieldType)
}
function protectedField(field: RuntimeField) {
  const mask = field.access?.maskStrategy
  return field.fieldType === 'SECRET' || field.access?.readable !== true || Boolean(mask && mask !== 'NONE')
}
function clone(value: unknown): unknown {
  return value === undefined ? undefined : JSON.parse(JSON.stringify(value))
}
function canonical(field: RuntimeField, value: unknown): unknown {
  if (value === undefined || value === '') return null
  if (field.fieldType === 'CASCADE' && Array.isArray(value) && fieldConfig(field).saveMode !== 'PATH') {
    return value.length ? value[value.length - 1] : null
  }
  return value
}
function same(left: unknown, right: unknown) { return JSON.stringify(left) === JSON.stringify(right) }
function requestContext() {
  return { generation, taskId: props.taskId, instanceId: props.instanceId, token: props.token, identity: props.identity }
}
function requestCurrent(request: ReturnType<typeof requestContext>) {
  return active && request.generation === generation && request.taskId === props.taskId
    && request.instanceId === props.instanceId && request.token === props.token && request.identity === props.identity
}
function setReady(value: boolean) { ready.value = value; emit('ready', value) }
function cancelReferenceRequests() {
  referenceTimers.forEach(timer => clearTimeout(timer))
  referenceTimers.clear()
  referenceRequests.clear()
}
function clearFormValues() {
  initial.clear()
  baseline.clear()
  dirty.clear()
  for (const cache of [values, organizations, dictionaries, cascades, references, referenceLoading, referenceErrors]) {
    Object.keys(cache).forEach(code => delete cache[code])
  }
}
function clear() {
  // 节点输入只在本次弹窗内存存在；关闭或换身份后，目录和恢复草稿一并失效。
  generation++
  cancelReferenceRequests()
  context.value = undefined
  clearFormValues()
  recoveryDraft.clear()
  conflict.value = ''
  recoveryNotice.value = ''
  loading.value = false
  error.value = ''
  setReady(false)
}
function endpoint(field: RuntimeField, taskId = props.taskId) {
  return `/api/flow-runtime/tasks/${taskId}/form/fields/${encodeURIComponent(field.code)}`
}
function describeError(reason: unknown) {
  return reason instanceof Error ? reason.message : '节点表单暂时无法加载，请重试。'
}
function accessDenied(reason: unknown) {
  return reason instanceof ApiError && [401, 403, 404].includes(reason.httpStatus || 0)
}

async function load(restoreDraft = false) {
  if (restoreDraft) {
    generation++
    cancelReferenceRequests()
    error.value = ''
    setReady(false)
  } else clear()
  if (!props.token) return
  const request = requestContext()
  loading.value = true
  try {
    const result = await api<FlowTaskFormContext>(`/api/flow-runtime/tasks/${request.taskId}/form`, {}, request.token)
    if (!requestCurrent(request)) return
    if (result.taskId !== request.taskId || result.instanceId !== request.instanceId) throw new Error('审批任务已变化，请关闭后刷新流程。')
    const writableFields = editableFields(result)
    const organizationChoices: Record<string, SystemPeopleDirectory> = {}
    const dictionaryChoices: Record<string, Array<{ value: string; label: string }>> = {}
    const cascadeChoices: Record<string, CascadeOption[]> = {}
    const referenceChoices: Record<string, ReferenceOption[]> = {}
    if (result.form) {
      await Promise.all(writableFields.filter(supported).map(async field => {
        const url = endpoint(field, request.taskId)
        if (['MEMBER', 'DEPARTMENT'].includes(field.fieldType)) {
          organizationChoices[field.code] = await api<SystemPeopleDirectory>(`${url}/organization-options`, {}, request.token)
        } else if (field.fieldType === 'CASCADE') {
          const maximumDepth = Math.max(1, Math.min(20, Number(fieldConfig(field).maxDepth) || 20))
          const loadLevel = async (parentId: number | undefined, depth: number): Promise<CascadeOption[]> => {
            if (!requestCurrent(request)) return []
            const query = parentId === undefined ? '' : `?parentId=${parentId}`
            const dictionary = await api<RuntimeDictionary>(`${url}/dictionary${query}`, {}, request.token)
            if (!requestCurrent(request)) return []
            return Promise.all(dictionary.items.map(async item => {
              const children = depth < maximumDepth ? await loadLevel(item.id, depth + 1) : []
              return { value: item.id, label: item.label, ...(children.length ? { children } : {}) }
            }))
          }
          cascadeChoices[field.code] = await loadLevel(undefined, 1)
        } else if (field.dictionaryId && ['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS'].includes(field.fieldType)) {
          const dictionary = await api<RuntimeDictionary>(`${url}/dictionary`, {}, request.token)
          dictionaryChoices[field.code] = dictionary.items.map(item => ({ value: item.code, label: item.label }))
        } else if (field.fieldType === 'REFERENCE') {
          referenceChoices[field.code] = await api<ReferenceOption[]>(`${url}/reference-options`, {}, request.token)
        }
      }))
    }
    if (!requestCurrent(request)) return
    // 表单和目录全部读取成功后再替换；可重试失败不抹掉尚未提交的恢复草稿。
    clearFormValues()
    context.value = result
    Object.assign(organizations, organizationChoices)
    Object.assign(dictionaries, dictionaryChoices)
    Object.assign(cascades, cascadeChoices)
    Object.assign(references, referenceChoices)
    if (result.form) {
      writableFields.filter(supported).forEach(field => {
        // 受保护值不能成为控件默认值或回传值；必填由服务端与保留的原意图合并检查。
        const value = protectedField(field) ? undefined : clone(result.form!.record.fields[field.code])
        initial.set(field.code, clone(value))
        baseline.set(field.code, clone(canonical(field, value)))
        values[field.code] = clone(value)
      })
    }
    if (restoreDraft) {
      let dropped = 0
      for (const draft of recoveryDraft.values()) {
        const field = writableFields.find(current => current.id === draft.id && current.code === draft.code
          && current.fieldType === draft.fieldType && supported(current))
        if (field) applyValue(field, draft.value)
        else dropped++
      }
      recoveryDraft.clear()
      conflict.value = ''
      recoveryNotice.value = '已重新读取最新内容并保留仍有权编辑的修改。请核对其他处理人的最新内容后再次提交；再次提交会采用本次修改。'
        + (dropped ? ' 部分原修改因字段或权限变化已从本次补丁移除。' : '')
    }
    setReady(true)
  } catch (reason) {
    if (!requestCurrent(request)) return
    if (accessDenied(reason)) {
      clear()
      error.value = describeError(reason)
      emit('denied')
    } else {
      if (!restoreDraft) clear()
      error.value = describeError(reason)
    }
  } finally { if (requestCurrent(request)) loading.value = false }
}

function change(field: RuntimeField, value: unknown) {
  if (!ready.value || props.disabled || !supported(field)
    || !fields.value.some(current => current.id === field.id && current.code === field.code && current.fieldType === field.fieldType)) return
  applyValue(field, value)
}
function applyValue(field: RuntimeField, value: unknown) {
  values[field.code] = clone(value)
  // 密码留空表示保留，显式 null 才是清空；其他类型的清空统一为 null。
  if (field.fieldType === 'SECRET' && (value === undefined || value === '')) dirty.delete(field.code)
  else if (!protectedField(field) && same(canonical(field, value), baseline.get(field.code))) dirty.delete(field.code)
  else dirty.add(field.code)
}
function cancelChange(field: RuntimeField) {
  if (!ready.value || props.disabled) return
  values[field.code] = clone(initial.get(field.code))
  dirty.delete(field.code)
}
function submission(): Record<string, unknown> | undefined {
  if (!ready.value || loading.value) return undefined
  // 只提交真实修改，不发送完整表单，也不从显示文本或流程变量补值。
  return Object.fromEntries(fields.value.filter(field => supported(field) && dirty.has(field.code))
    .map(field => [field.code, clone(canonical(field, values[field.code]))]))
}
function formRevision(): number | undefined {
  return ready.value && !loading.value ? context.value?.formRevision ?? undefined : undefined
}
function markConflict(text: string) {
  // 草稿只记录用户刚输入的修改，密码不读取原文、不比较其他处理人的明文。
  recoveryDraft.clear()
  fields.value.filter(field => supported(field) && dirty.has(field.code)).forEach(field => {
    recoveryDraft.set(field.code, { id: field.id, code: field.code, fieldType: field.fieldType, value: clone(values[field.code]) })
  })
  generation++
  cancelReferenceRequests()
  conflict.value = text || '其他处理人已提交新的候选内容，请重新读取并核对。'
  recoveryNotice.value = ''
  error.value = ''
  loading.value = false
  setReady(false)
}
function reloadKeepingChanges() { return load(true) }
function selectionChoices(field: RuntimeField) {
  if (!['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS'].includes(field.fieldType)) return undefined
  const choices = field.dictionaryId ? [...(dictionaries[field.code] || [])]
    : (fieldConfig(field).options || []).filter(option => option.status !== 'DISABLED' && !option.disabled)
      .flatMap(option => { const value = option.value || option.code; return value ? [{ value, label: option.label || value }] : [] })
  const selected = Array.isArray(values[field.code]) ? values[field.code] as unknown[] : [values[field.code]]
  return [...choices, ...selected.filter(value => typeof value === 'string' && !choices.some(option => option.value === value))
    .map(value => ({ value: value as string, label: '当前选项不可用', disabled: true }))]
}
function organizationValue(field: RuntimeField): number | number[] | undefined {
  const value = values[field.code]
  const choices = organizations[field.code]
  const exists = (id: unknown) => typeof id === 'number' && (field.fieldType === 'MEMBER'
    ? choices?.people.some(person => person.systemMemberId === id) : choices?.departments.some(department => department.id === id))
  if (Array.isArray(value)) return value.every(exists) ? value as number[] : undefined
  return exists(value) ? value as number : undefined
}
function cascadePath(options: CascadeOption[], id: number, prefix: number[] = []): number[] | undefined {
  for (const option of options) {
    const path = [...prefix, option.value]
    if (option.value === id) return path
    const child = cascadePath(option.children || [], id, path)
    if (child) return child
  }
  return undefined
}
function cascadeValue(field: RuntimeField): number[] | undefined {
  const value = values[field.code]
  if (typeof value === 'number') return cascadePath(cascades[field.code] || [], value)
  if (!Array.isArray(value) || !value.length) return undefined
  let choices = cascades[field.code] || []
  for (const id of value) {
    const option = choices.find(item => item.value === id)
    if (!option) return undefined
    choices = option.children || []
  }
  return value as number[]
}
function referenceChoices(field: RuntimeField): ReferenceOption[] {
  const choices = references[field.code] || []
  const value = values[field.code]
  return typeof value === 'number' && !choices.some(option => option.value === value)
    ? [...choices, { value, label: '当前关联不可访问', description: '', disabled: true }] : choices
}
function unavailableCurrent(field: RuntimeField) {
  const value = values[field.code]
  if (value === undefined || value === null || value === '') return false
  return ['MEMBER', 'DEPARTMENT'].includes(field.fieldType) ? organizationValue(field) === undefined
    : field.fieldType === 'CASCADE' ? cascadeValue(field) === undefined : false
}
function searchReferences(field: RuntimeField, search: string) {
  if (!ready.value || !supported(field) || props.disabled) return
  const previous = referenceTimers.get(field.code)
  if (previous) clearTimeout(previous)
  const request = requestContext()
  const sequence = (referenceRequests.get(field.code) || 0) + 1
  referenceRequests.set(field.code, sequence)
  const isCurrent = () => requestCurrent(request) && ready.value && referenceRequests.get(field.code) === sequence
  referenceLoading[field.code] = true
  referenceErrors[field.code] = ''
  references[field.code] = []
  referenceTimers.set(field.code, setTimeout(() => {
    referenceTimers.delete(field.code)
    if (!isCurrent()) return
    const query = search.trim() ? `?${new URLSearchParams({ search: search.trim() })}` : ''
    void api<ReferenceOption[]>(`${endpoint(field, request.taskId)}/reference-options${query}`, {}, request.token)
      .then(choices => { if (isCurrent()) references[field.code] = choices })
      .catch(reason => {
        if (!isCurrent()) return
        if (accessDenied(reason)) { clear(); emit('denied') }
        else referenceErrors[field.code] = describeError(reason)
      }).finally(() => { if (isCurrent()) referenceLoading[field.code] = false })
  }, 500))
}

watch(() => [props.taskId, props.instanceId, props.token, props.identity], () => {
  clear()
  const request = requestContext()
  // 同一轮切换可能连续更新任务和身份；立即清空，仅最终代次读取一次目录。
  void Promise.resolve().then(() => { if (requestCurrent(request)) void load() })
}, { immediate: true, flush: 'sync' })
onBeforeUnmount(() => { active = false; clear() })
defineExpose({ submission, formRevision, markConflict, clear })
</script>

<template>
  <section class="flow-task-form-editor">
    <a-spin :spinning="loading">
      <a-alert v-if="error" type="error" show-icon :message="error" />
      <a-button v-if="error && !conflict" size="small" :disabled="disabled" @click="load()">重新读取节点表单</a-button>
      <template v-if="conflict">
        <a-alert type="warning" show-icon :message="conflict" description="本次未提交的修改仍保留。重新读取后请核对最新内容，再明确提交。" />
        <a-button size="small" :disabled="disabled || loading" @click="reloadKeepingChanges">重新读取并保留我的修改</a-button>
      </template>
      <a-alert v-if="recoveryNotice" type="info" show-icon :message="recoveryNotice" />
      <a-alert v-if="context?.message" type="info" show-icon :message="context.message" />
      <a-alert v-else-if="ready && context && !context.form" type="info" show-icon message="本任务没有可编辑的节点表单，提交不会修改业务字段。" />
      <template v-if="(ready || conflict) && context?.form">
        <template v-if="reviewFields.length">
          <p>以下是其他处理人提交后的最新候选内容；下方输入保留你的修改，请核对后再提交。</p>
          <SchemaRecordDetail :fields="reviewFields" :values="context.form.record.fields" :presentation="context.form.record.fieldPresentation" />
        </template>
        <template v-if="readOnlyFields.length">
          <p>以下内容仅供核对，提交不会修改这些字段。</p>
          <SchemaRecordDetail :fields="readOnlyFields" :values="context.form.record.fields" :presentation="context.form.record.fieldPresentation" />
        </template>
        <a-alert v-if="fields.length" type="info" show-icon message="本节点业务修改" description="只提交你实际修改的字段；未修改的内容保持原意图。节点提交仍须经过后续流程和最终业务权限校验才正式生效。" />
        <SchemaFormLayout v-if="fields.length" :fields="fields">
          <template #field="{ field: configuredField }">
            <a-form-item :label="configuredField.name" :required="configuredField.required">
              <fieldset :disabled="disabled || !ready" :inert="disabled || !ready" class="task-field-input">
                <template v-if="!supported(configuredField)">
                  <a-alert type="warning" show-icon :message="`${fieldTypes.find(type => type.type === configuredField.fieldType)?.name || '此类型字段'}暂不能在审批框编辑，原值保持。`" />
                </template>
                <RuntimeFieldInput v-else-if="isSharedRuntimeField(configuredField.fieldType)" :field="configuredField"
                  :model-value="values[configuredField.code]" :preserve-existing="true" :options="selectionChoices(configuredField)"
                  @update:model-value="change(configuredField, $event)" />
                <PersonSelect v-else-if="configuredField.fieldType === 'MEMBER'" :model-value="organizationValue(configuredField)"
                  :people="organizations[configuredField.code]?.people || []" value-key="systemMemberId"
                  :disabled="disabled" @update:model-value="change(configuredField, $event)" />
                <DepartmentSelect v-else-if="configuredField.fieldType === 'DEPARTMENT'" :model-value="organizationValue(configuredField) as number | undefined"
                  :departments="organizations[configuredField.code]?.departments || []" :disabled="disabled" @update:model-value="change(configuredField, $event)" />
                <a-cascader v-else-if="configuredField.fieldType === 'CASCADE'" :value="cascadeValue(configuredField)" :options="cascades[configuredField.code] || []"
                  :disabled="disabled" :change-on-select="Boolean(fieldConfig(configuredField).allowIntermediate)" allow-clear show-search placeholder="选择层级项"
                  @update:value="change(configuredField, $event)" />
                <a-select v-else-if="configuredField.fieldType === 'REFERENCE'" :value="values[configuredField.code] as number | undefined"
                  :options="referenceChoices(configuredField)" :disabled="disabled" :loading="referenceLoading[configuredField.code]" show-search :filter-option="false"
                  allow-clear placeholder="搜索并选择关联记录" @search="searchReferences(configuredField, $event)" @update:value="change(configuredField, $event)" />
              </fieldset>
              <small v-if="supported(configuredField) && protectedField(configuredField)">受保护内容不预填，未修改时保持原值。</small>
              <small v-if="unavailableCurrent(configuredField)">当前选项不可访问，未修改时保持原值。</small>
              <small v-if="referenceErrors[configuredField.code]">{{ referenceErrors[configuredField.code] }}</small>
              <a-button v-if="supported(configuredField) && protectedField(configuredField) && configuredField.fieldType !== 'SECRET' && !configuredField.required"
                type="link" size="small" :disabled="disabled || !ready" @click="change(configuredField, null)">清空原值</a-button>
              <template v-if="dirty.has(configuredField.code)"><a-tag color="blue">{{ values[configuredField.code] === null ? '将清空' : '已修改' }}</a-tag><a-button type="link" size="small" :disabled="disabled || !ready" @click="cancelChange(configuredField)">取消修改</a-button></template>
            </a-form-item>
          </template>
        </SchemaFormLayout>
        <p v-if="!fields.length && !context.message">当前没有可编辑字段；提交仍须通过节点必填与业务权限校验。</p>
      </template>
    </a-spin>
  </section>
</template>

<style scoped>
.flow-task-form-editor { margin: 16px 0; }
.flow-task-form-editor :deep(.ant-alert) { margin-bottom: 12px; }
.task-field-input { min-width: 0; margin: 0; padding: 0; border: 0; }
small { display: block; margin-top: 5px; color: #64748b; }
</style>
