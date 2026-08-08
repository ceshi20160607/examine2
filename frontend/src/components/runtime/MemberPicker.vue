<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

import { memberDirectoryApi } from '@/services/memberDirectory'
import { useSessionStore } from '@/stores/session'
import type { RuntimeMemberOption } from '@/types/memberDirectory'

const props = withDefaults(defineProps<{
  systemId: string
  value?: string
  placeholder?: string
  disabled?: boolean
}>(), {
  value: '',
  placeholder: '搜索当前租户成员',
  disabled: false,
})

const emit = defineEmits<{
  'update:value': [value: string]
  'select': [member: RuntimeMemberOption]
}>()

const session = useSessionStore()
const systemId = computed(() => props.systemId)
const tenantId = computed(() => session.context?.tenantId ?? '')
const options = ref<RuntimeMemberOption[]>([])
const loading = ref(false)
const error = ref('')
let requestGeneration = 0
let searchTimer: ReturnType<typeof setTimeout> | undefined

const selectOptions = computed(() => {
  const mapped = options.value.map((member) => ({
    value: member.memberId,
    label: `${member.displayName}（${member.memberCode}）`,
  }))
  if (props.value && !mapped.some((option) => option.value === props.value)) {
    mapped.unshift({ value: props.value, label: `成员 ${props.value}` })
  }
  return mapped
})

async function search(keyword = '') {
  const generation = ++requestGeneration
  if (!systemId.value || !tenantId.value || !session.hasPermission('system.runtime.access')) {
    options.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    const result = await memberDirectoryApi.list(systemId.value, keyword)
    if (generation === requestGeneration) options.value = result.items
  } catch (cause) {
    if (generation === requestGeneration) {
      options.value = []
      error.value = cause instanceof Error ? cause.message : '成员加载失败'
    }
  } finally {
    if (generation === requestGeneration) loading.value = false
  }
}

function scheduleSearch(keyword: string) {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => search(keyword), 250)
}

function update(value: unknown) {
  const memberId = typeof value === 'string' ? value : ''
  emit('update:value', memberId)
  const selected = options.value.find((member) => member.memberId === memberId)
  if (selected) emit('select', selected)
}

function resetScope() {
  requestGeneration += 1
  if (searchTimer) clearTimeout(searchTimer)
  options.value = []
  error.value = ''
  loading.value = false
  emit('update:value', '')
  search()
}

onMounted(() => search())
onBeforeUnmount(() => {
  requestGeneration += 1
  if (searchTimer) clearTimeout(searchTimer)
})
watch([systemId, tenantId], resetScope)
</script>

<template>
  <div class="member-picker">
    <a-select
      :value="value || undefined"
      :options="selectOptions"
      :loading="loading"
      :disabled="disabled"
      :placeholder="placeholder"
      show-search
      allow-clear
      :filter-option="false"
      :not-found-content="loading ? '搜索中…' : '没有匹配成员'"
      @search="scheduleSearch"
      @change="update"
      @clear="update('')"
    />
    <small v-if="error">{{ error }}</small>
  </div>
</template>

<style scoped>
.member-picker,
.member-picker :deep(.ant-select) {
  width: 100%;
}

.member-picker small {
  display: block;
  margin-top: 5px;
  color: #c2413b;
}
</style>
