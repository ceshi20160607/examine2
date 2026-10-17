<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { PeopleDirectoryOption } from '../types'

const props = withDefaults(defineProps<{
  modelValue?: number | number[]
  people: PeopleDirectoryOption[]
  valueKey?: 'tenantMemberId' | 'systemMemberId' | 'accountId'
  multiple?: boolean
  showDepartmentFilter?: boolean
  disabled?: boolean
  allowClear?: boolean
  placeholder?: string
  excludedValues?: number[]
}>(), {
  valueKey: 'systemMemberId',
  multiple: false,
  showDepartmentFilter: false,
  disabled: false,
  allowClear: true,
  placeholder: '搜索并选择成员',
  excludedValues: () => [],
})

const emit = defineEmits<{ 'update:modelValue': [value: number | number[] | undefined]; change: [] }>()
// 单选后收起列表；多选保持展开，便于连续选择同部门的多个成员。
const open = ref(false)
const departmentFilterId = ref<number>()
watch(() => props.disabled, value => { if (value) open.value = false })

/** 人员候选来自当前已授权目录；按部门筛选只收窄候选，不丢失已选人员。 */
const departmentOptions = computed(() => {
  const departments = [...new Map(props.people.filter(person => typeof person.departmentId === 'number')
    .map(person => [person.departmentId as number, person.departmentName || `部门 ${person.departmentId}`])).entries()]
  return departments.map(([id, name]) => ({ value: id, label: departments.filter(item => item[1] === name).length > 1
    ? `${name} · 部门编号 ${id}` : name }))
})

const options = computed(() => {
  const selected = new Set(Array.isArray(props.modelValue) ? props.modelValue : props.modelValue === undefined ? [] : [props.modelValue])
  const people = props.people
    .filter(person => {
      const value = person[props.valueKey]
      return typeof value === 'number' && !props.excludedValues.includes(value)
        && (departmentFilterId.value === undefined || person.departmentId === departmentFilterId.value || selected.has(value))
    })
    .map(person => ({
      value: person[props.valueKey] as number,
      baseLabel: [person.displayName, person.positionTitle, person.departmentName].filter(Boolean).join(' · '),
      person,
    }))
  // 同名同部门成员优先用工号、角色区分；仍重名时展示编号，避免转交给错误的人。
  return people.map(item => {
    const peers = people.filter(other => other.baseLabel === item.baseLabel)
    const employeeNumber = (item.person as PeopleDirectoryOption & { employeeNumber?: string }).employeeNumber
    const employeeHint = employeeNumber ? `工号 ${employeeNumber}` : ''
    const roleHint = item.person.roleNames?.join('、') || ''
    const hint = [employeeHint, roleHint].find(candidate => candidate
      && peers.filter(other => (candidate === employeeHint
        ? `工号 ${(other.person as PeopleDirectoryOption & { employeeNumber?: string }).employeeNumber || ''}`
        : other.person.roleNames?.join('、') || '') === candidate).length === 1)
    const suffix = peers.length > 1 ? hint || `成员编号 ${item.value}` : ''
    return { value: item.value, label: [item.baseLabel, suffix].filter(Boolean).join(' · '), person: item.person, suffix }
  })
})

function update(value: number | number[] | undefined) {
  emit('update:modelValue', value)
  emit('change')
}

function afterSelect() {
  if (!props.multiple) open.value = false
}
</script>

<template>
  <div class="person-select">
  <a-select v-if="showDepartmentFilter && departmentOptions.length > 1" v-model:value="departmentFilterId" :disabled="disabled"
    :options="departmentOptions" allow-clear show-search option-filter-prop="label" placeholder="按部门筛选成员" />
  <a-select
    :value="modelValue"
    :mode="multiple ? 'multiple' : undefined"
    :disabled="disabled"
    :allow-clear="allowClear"
    show-search
    option-filter-prop="label"
    :placeholder="placeholder"
    :options="options"
    :open="open"
    @dropdown-visible-change="open = $event"
    @select="afterSelect"
    @update:value="update"
  >
    <template #option="{ person, suffix }">
      <div class="person-option">
        <span class="person-option__avatar">{{ person.displayName.slice(0, 1) }}</span>
        <span><strong>{{ person.displayName }}</strong><small>{{ [person.positionTitle, person.departmentName, person.roleNames?.join('、'), suffix].filter(Boolean).join(' · ') || '有效成员' }}</small></span>
      </div>
    </template>
  </a-select>
  </div>
</template>

<style scoped>
.person-select { display: grid; gap: 6px; width: 100%; }
</style>
