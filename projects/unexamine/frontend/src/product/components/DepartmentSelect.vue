<script setup lang="ts">
import { computed } from 'vue'
import type { SystemDirectoryDepartment } from '../types'

const props = withDefaults(defineProps<{
  modelValue?: number
  departments: SystemDirectoryDepartment[]
  disabled?: boolean
  allowClear?: boolean
  placeholder?: string
  excludedValues?: number[]
}>(), {
  allowClear: true,
  disabled: false,
  placeholder: '选择部门',
  excludedValues: () => [],
})

const emit = defineEmits<{ 'update:modelValue': [value: number | undefined]; change: [] }>()
const options = computed(() => {
  const departments = props.departments.filter(item => !props.excludedValues.includes(item.id))
  // 完整路径仍重名时才补充编号，确保转移目标可辨认。
  return departments.map(item => ({
    value: item.id,
    label: departments.some(other => other.id !== item.id && other.fullName === item.fullName)
      ? `${item.fullName} · 部门编号 ${item.id}` : item.fullName,
  }))
})

function update(value: number | undefined) {
  emit('update:modelValue', value)
  emit('change')
}
</script>

<template>
  <a-select :value="modelValue" :disabled="disabled" :allow-clear="allowClear" show-search option-filter-prop="label"
    :placeholder="placeholder" :options="options" @update:value="update" />
</template>
