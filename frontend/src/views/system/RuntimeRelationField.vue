<script setup lang="ts">
import { ChevronDown, ChevronUp, ExternalLink, RefreshCw, Search, Trash2 } from 'lucide-vue-next'
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import { runtimeApi } from '@/services/config'
import type { RuntimeFieldCapability, RuntimeRecordSchema, RuntimeRelationItem, RuntimeRelationPage } from '@/types/config'
import { runtimeErrorMessage } from './runtimeRecordModel'

const props = defineProps<{
  systemId: string
  moduleCode: string
  targetModuleCode: string
  schemaVersionId: string
  field: RuntimeFieldCapability
  recordId?: string
  recordVersion?: number
  modelValue?: RuntimeRelationItem[]
  readonly?: boolean
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: RuntimeRelationItem[]]
  mutated: [version: number]
}>()

const items = ref<RuntimeRelationItem[]>([])
const page = ref<RuntimeRelationPage | null>(null)
const targetSchema = ref<RuntimeRecordSchema | null>(null)
const options = ref<RuntimeRelationItem[]>([])
const selectedOption = ref<string>()
const loading = ref(false)
const searching = ref(false)
const mutating = ref(false)
const searched = ref(false)
const error = ref('')
let searchTimer: ReturnType<typeof setTimeout> | undefined

const multiple = computed(() => props.field.schema.multiple === true)
const lastRequiredTarget = computed(() => props.field.schema.required === true && items.value.length <= 1)
const canAdd = computed(() => !props.readonly && !props.disabled
  && (props.recordId ? page.value?.capabilities.relationAdd.enabled === true : props.field.writable))
const canRemove = computed(() => !lastRequiredTarget.value && !props.readonly && !props.disabled
  && (props.recordId ? page.value?.capabilities.relationRemove.enabled === true : props.field.writable))
const canReorder = computed(() => multiple.value && !props.readonly && !props.disabled
  && (props.recordId ? page.value?.capabilities.relationReorder.enabled === true : props.field.writable))
const canCreateTarget = computed(() => canAdd.value && props.field.schema.allowCreate === true
  && targetSchema.value?.actions.includes('CREATE') === true)
const visibleOptions = computed(() => options.value.filter((option) => !items.value.some((item) => item.targetRecordId === option.targetRecordId)))

function publish(next: RuntimeRelationItem[]) {
  items.value = next.map((item, ordinal) => ({ ...item, ordinal }))
  emit('update:modelValue', items.value)
}

async function load() {
  if (!props.recordId) {
    publish([...(props.modelValue ?? [])])
    return
  }
  loading.value = true
  error.value = ''
  try {
    page.value = await runtimeApi.relations(props.systemId, props.moduleCode, props.recordId, props.field.fieldCode)
    publish(page.value.items)
  } catch (cause) {
    error.value = runtimeErrorMessage(cause, `${props.field.fieldName}加载失败，请重试。`)
  } finally {
    loading.value = false
  }
}

async function loadTargetSchema() {
  if (!props.targetModuleCode) return
  try { targetSchema.value = await runtimeApi.recordSchema(props.systemId, props.targetModuleCode) }
  catch { targetSchema.value = null }
}

async function runSearch(query: string) {
  if (!props.targetModuleCode) return
  searching.value = true
  searched.value = true
  error.value = ''
  try {
    const result = await runtimeApi.relationCandidates(
      props.systemId, props.moduleCode, props.field.fieldCode, query, 1, 20,
    )
    options.value = result.items
  } catch (cause) {
    options.value = []
    error.value = runtimeErrorMessage(cause, '可选记录搜索失败，请重试。')
  } finally {
    searching.value = false
  }
}

function search(query: string) {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => { void runSearch(query) }, 300)
}

async function mutate(add: RuntimeRelationItem[] = [], remove: string[] = [], order: string[] = []) {
  if (!props.recordId || props.recordVersion === undefined) return
  mutating.value = true
  error.value = ''
  try {
    const result = await runtimeApi.mutateRelation(props.systemId, props.moduleCode, props.recordId, props.field.fieldCode, {
      expectedVersion: props.recordVersion,
      add: add.map((item, ordinal) => ({ targetRecordId: item.targetRecordId, targetExpectedVersion: item.targetVersion, ordinal })),
      remove,
      order,
    })
    emit('mutated', result.version)
    await load()
  } catch (cause) {
    const message = runtimeErrorMessage(cause, `${props.field.fieldName}保存失败，已保留服务端原值。`)
    await load()
    error.value = message
  } finally {
    mutating.value = false
  }
}

async function addTarget(recordId: string) {
  const target = options.value.find((item) => item.targetRecordId === recordId)
  selectedOption.value = undefined
  if (!target || items.value.some((item) => item.targetRecordId === recordId)) return
  const removed = multiple.value ? [] : items.value.map((item) => item.targetRecordId)
  if (props.recordId) await mutate([target], removed)
  else publish(multiple.value ? [...items.value, target] : [target])
}

async function removeTarget(item: RuntimeRelationItem) {
  if (props.recordId) await mutate([], [item.targetRecordId])
  else publish(items.value.filter((candidate) => candidate.targetRecordId !== item.targetRecordId))
}

async function move(index: number, offset: number) {
  const next = [...items.value]
  const target = index + offset
  if (target < 0 || target >= next.length) return
  const [item] = next.splice(index, 1)
  next.splice(target, 0, item!)
  if (props.recordId) await mutate([], [], next.map((candidate) => candidate.targetRecordId))
  else publish(next)
}

function createTarget() {
  const path = `/systems/${encodeURIComponent(props.systemId)}/workbench?module=${encodeURIComponent(props.targetModuleCode)}&mode=create`
  window.open(path, '_blank', 'noopener,noreferrer')
}

watch(() => [props.recordId, props.field.fieldCode], () => { void load() }, { immediate: true })
watch(() => props.modelValue, (value) => { if (!props.recordId) items.value = [...(value ?? [])] }, { deep: true })
watch(() => props.targetModuleCode, () => { void loadTargetSchema() }, { immediate: true })
onBeforeUnmount(() => { if (searchTimer) clearTimeout(searchTimer) })
</script>

<template>
  <section class="relation-field" :aria-label="field.fieldName">
    <a-alert v-if="!targetModuleCode" type="warning" show-icon message="目标模块当前不可用" />
    <a-alert v-else-if="error" type="error" show-icon :message="error">
      <template #action><a-button size="small" @click="load"><RefreshCw :size="14" />重试</a-button></template>
    </a-alert>
    <a-spin :spinning="loading || mutating">
      <a-select
        v-if="canAdd && targetModuleCode"
        v-model:value="selectedOption"
        class="relation-search"
        show-search
        :filter-option="false"
        :options="visibleOptions.map((item) => ({ value: item.targetRecordId, label: item.title }))"
        :loading="searching"
        :placeholder="multiple ? '搜索并添加记录' : '搜索并选择记录'"
        aria-label="搜索关联记录"
        @search="search"
        @focus="!searched && runSearch('')"
        @select="addTarget"
      >
        <template #suffixIcon><Search :size="15" /></template>
        <template #notFoundContent><span>{{ searching ? '正在搜索' : searched ? '没有可选记录' : '输入名称搜索' }}</span></template>
      </a-select>
      <div v-if="items.length" class="relation-selected" aria-label="已选关联记录">
        <div v-for="(item, index) in items" :key="item.targetRecordId" class="relation-item">
          <span>{{ item.title || '记录当前不可见' }}</span>
          <div v-if="!readonly" class="relation-actions">
            <button v-if="canReorder" type="button" title="上移" :disabled="index === 0 || mutating" @click="move(index, -1)"><ChevronUp :size="15" /></button>
            <button v-if="canReorder" type="button" title="下移" :disabled="index === items.length - 1 || mutating" @click="move(index, 1)"><ChevronDown :size="15" /></button>
            <button v-if="canRemove" type="button" title="移除关联" :disabled="mutating" @click="removeTarget(item)"><Trash2 :size="15" /></button>
          </div>
        </div>
      </div>
      <div v-else-if="!loading" class="relation-empty">尚未关联记录</div>
      <a-button v-if="canCreateTarget && searched && !options.length" class="create-target" type="link" @click="createTarget"><ExternalLink :size="15" />创建并关联</a-button>
      <p v-if="recordId && page && !page.capabilities.relationAdd.enabled && page.capabilities.relationAdd.disabledReason !== 'NOT_APPLICABLE'" class="capability-note">当前权限不允许添加关联。</p>
    </a-spin>
  </section>
</template>

<style scoped>
.relation-field{display:grid;gap:9px;min-width:0}.relation-search{width:100%}.relation-selected{display:grid;border-top:1px solid #dfe5e8}.relation-item{display:grid;grid-template-columns:minmax(0,1fr) auto;align-items:center;gap:8px;min-height:42px;padding:6px 2px;border-bottom:1px solid #e5eaed}.relation-item>span{font-weight:600;overflow-wrap:anywhere}.relation-actions{display:flex;gap:3px}.relation-actions button{display:grid;place-items:center;width:28px;height:28px;border:0;background:transparent;color:#65737d;cursor:pointer}.relation-actions button:hover:not(:disabled){background:#e8f3f1;color:#087f73}.relation-actions button:disabled{opacity:.35;cursor:not-allowed}.relation-empty{min-height:42px;display:flex;align-items:center;padding:0 10px;border:1px dashed #cfd8dd;color:#75818a;background:#fafbfc}.create-target{justify-self:start;display:inline-flex;align-items:center;gap:6px;padding-inline:0}.capability-note{margin:0;color:#7a5b18;font-size:12px}.ant-alert .ant-btn{display:inline-flex;align-items:center;gap:5px}
</style>
