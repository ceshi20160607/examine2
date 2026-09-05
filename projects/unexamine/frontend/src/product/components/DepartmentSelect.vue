<script setup lang="ts">
import { computed } from 'vue'
import type { SystemDirectoryDepartment } from '../types'

const props = withDefaults(defineProps<{
  modelValue?: number
  departments: SystemDirectoryDepartment[]
  allowClear?: boolean
  placeholder?: string
  excludedValues?: number[]
}>(), {
  allowClear: true,
  placeholder: '选择部门',
  excludedValues: () => [],
})

const emit = defineEmits<{ 'update:modelValue': [value: number | undefined]; change: [] }>()
const options = computed(() => props.departments
  .filter(item => !props.excludedValues.includes(item.id))
  .map(item => ({ value: item.id, label: item.fullName })))

function update(value: number | undefined) {
  emit('update:modelValue', value)
  emit('change')
}
</script>

<template>
  <a-select :value="modelValue" :allow-clear="allowClear" show-search option-filter-prop="label"
    :placeholder="placeholder" :options="options" @update:value="update" />
</template>
