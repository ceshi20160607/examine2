<script setup lang="ts">
import { computed } from 'vue'
import RuntimeFieldInput from './RuntimeFieldInput.vue'
import { initialConditionValue, type ConditionField } from '../conditions'
const props = defineProps<{ field: ConditionField; modelValue?: unknown; operator?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: unknown] }>()
const inputField = computed(() => ({ ...props.field, required: false, fieldType: props.field.fieldType === 'MULTI_SELECT' && props.operator === 'CONTAINS' ? 'SINGLE_SELECT' : props.field.fieldType }))
const values = computed(() => Array.isArray(props.modelValue) ? props.modelValue : [])
function change(index: number, value: unknown) { emit('update:modelValue', values.value.map((item, i) => i === index ? value : item)) }
</script>
<template>
  <div v-if="operator === 'IN'" class="condition-value-list">
    <div v-for="(value, index) in values" :key="index" class="condition-value-item">
      <RuntimeFieldInput :field="inputField" :model-value="value" @update:model-value="change(index, $event)" />
      <a-button size="small" @click="emit('update:modelValue', values.filter((_, i) => i !== index))">移除</a-button>
    </div>
    <a-button size="small" @click="emit('update:modelValue', [...values, initialConditionValue(field)])">添加比较值</a-button>
  </div>
  <a-select v-else-if="field.fieldType === 'BOOLEAN'" :value="modelValue as boolean" :options="[{value:true,label:'是'}, {value:false,label:'否'}]" allow-clear placeholder="未填写" style="width:100%" @update:value="emit('update:modelValue',$event)" />
  <RuntimeFieldInput v-else :field="inputField" :model-value="modelValue" @update:model-value="emit('update:modelValue', $event)" />
</template>
<style scoped>
.condition-value-list { display: grid; gap: 6px; }
.condition-value-item { display: flex; gap: 6px; }
.condition-value-item > :first-child { flex: 1; min-width: 0; }
</style>
