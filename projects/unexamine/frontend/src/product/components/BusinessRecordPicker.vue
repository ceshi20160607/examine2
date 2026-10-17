<script setup lang="ts">
import { message } from 'ant-design-vue'
import { computed, onMounted, ref, watch } from 'vue'
import { ApiError, api } from '../api'
import type { RuntimeModuleCatalogItem, RuntimeRecordList } from '../types'

export interface BusinessRecordValue { businessType?: string; businessId?: string; businessTitle?: string }
const props = defineProps<{ modelValue?: BusinessRecordValue; token: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: BusinessRecordValue] }>()
const modules = ref<RuntimeModuleCatalogItem[]>([])
const records = ref<RuntimeRecordList['records']>([])
const moduleCode = ref('')
const recordId = ref<number>()
const loading = ref(false)
let clearingRecordForModuleChange = false
const moduleOptions = computed(() => modules.value.filter(item => item.configurationMode !== 'SHARED_RECORDS')
  .map(item => ({ value: item.moduleCode, label: `${item.groupName} · ${item.moduleName}` })))
const recordOptions = computed(() => {
  const options = records.value.map(item => ({ value: item.id, label: item.title }))
  const existingId = Number(props.modelValue?.businessId)
  if (existingId && !options.some(item => item.value === existingId) && props.modelValue?.businessTitle) {
    options.unshift({ value: existingId, label: props.modelValue.businessTitle })
  }
  return options
})

function readable(reason: unknown) { return reason instanceof ApiError ? reason.message : '业务记录加载失败' }
async function loadModules() {
  if (!props.token) return
  try { modules.value = await api<RuntimeModuleCatalogItem[]>('/api/runtime/modules', {}, props.token) }
  catch (reason) { message.warning(readable(reason)) }
}
async function loadRecords(code: string) {
  records.value = []
  if (!code || !props.token) return
  loading.value = true
  try {
    const result = await api<RuntimeRecordList>(`/api/runtime/modules/${encodeURIComponent(code)}/records?page=1&pageSize=100&tenantScope=OWN`, {}, props.token)
    records.value = result.records
  } catch (reason) { message.warning(readable(reason)) }
  finally { loading.value = false }
}
function selectModule(code?: string) {
  moduleCode.value = code || ''
  recordId.value = undefined
  // 切换模块只清除旧记录；父表单回写空关联时不能把刚选的模块也清掉。
  clearingRecordForModuleChange = true
  emit('update:modelValue', {})
  void loadRecords(moduleCode.value)
}
function selectRecord(id?: number) {
  recordId.value = id
  if (!moduleCode.value || !id) return emit('update:modelValue', {})
  const record = records.value.find(item => item.id === id)
  emit('update:modelValue', { businessType: `MODULE:${moduleCode.value}`, businessId: String(id), businessTitle: record?.title })
}
watch(() => props.modelValue, (value) => {
  const nextCode = value?.businessType?.replace(/^MODULE:/, '') || ''
  const nextId = value?.businessId ? Number(value.businessId) : undefined
  if (clearingRecordForModuleChange && !nextCode && !nextId) {
    clearingRecordForModuleChange = false
    return
  }
  clearingRecordForModuleChange = false
  if (nextCode !== moduleCode.value) { moduleCode.value = nextCode; void loadRecords(nextCode) }
  recordId.value = nextId
}, { immediate: true })
onMounted(loadModules)
</script>

<template>
  <div class="business-record-picker">
    <a-select :value="moduleCode || undefined" allow-clear show-search option-filter-prop="label" placeholder="选择业务模块" :options="moduleOptions" @update:value="selectModule" />
    <a-select :value="recordId" allow-clear show-search option-filter-prop="label" placeholder="选择业务记录" :loading="loading" :disabled="!moduleCode" :options="recordOptions" @update:value="selectRecord" />
  </div>
</template>

<style scoped>.business-record-picker{display:grid;grid-template-columns:1fr 1.4fr;gap:8px}@media(max-width:640px){.business-record-picker{grid-template-columns:1fr}}</style>
