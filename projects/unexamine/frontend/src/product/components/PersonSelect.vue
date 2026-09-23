<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { PeopleDirectoryOption } from '../types'

const props = withDefaults(defineProps<{
  modelValue?: number | number[]
  people: PeopleDirectoryOption[]
  valueKey?: 'tenantMemberId' | 'systemMemberId' | 'accountId'
  multiple?: boolean
  disabled?: boolean
  allowClear?: boolean
  placeholder?: string
  excludedValues?: number[]
}>(), {
  valueKey: 'systemMemberId',
  multiple: false,
  disabled: false,
  allowClear: true,
  placeholder: '搜索并选择成员',
  excludedValues: () => [],
})

const emit = defineEmits<{ 'update:modelValue': [value: number | number[] | undefined]; change: [] }>()
// 选择后收起下拉层，滚动弹窗中的下一项配置不会被展开的人员列表遮住。
const open = ref(false)
watch(() => props.disabled, value => { if (value) open.value = false })

const options = computed(() => props.people
  .filter(person => {
    const value = person[props.valueKey]
    return typeof value === 'number' && !props.excludedValues.includes(value)
  })
  .map(person => ({
    value: person[props.valueKey],
    label: [person.displayName, person.positionTitle, person.departmentName].filter(Boolean).join(' · '),
    person,
  })))

function update(value: number | number[] | undefined) {
  emit('update:modelValue', value)
  emit('change')
}
</script>

<template>
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
    @select="open = false"
    @update:value="update"
  >
    <template #option="{ person }">
      <div class="person-option">
        <span class="person-option__avatar">{{ person.displayName.slice(0, 1) }}</span>
        <span><strong>{{ person.displayName }}</strong><small>{{ [person.positionTitle, person.departmentName, person.roleNames?.join('、')].filter(Boolean).join(' · ') || '有效成员' }}</small></span>
      </div>
    </template>
  </a-select>
</template>
