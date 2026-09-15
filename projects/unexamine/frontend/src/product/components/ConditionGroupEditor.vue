<script setup lang="ts">
import ConditionValueInput from './ConditionValueInput.vue'
import { emptyCondition, isConditionGroup, newCondition, operatorsFor, initialConditionValue, type ConditionField, type ConditionGroup, type ConditionLeaf, type ConditionNode } from '../conditions'
const props = withDefaults(defineProps<{ modelValue: ConditionGroup; fields: ConditionField[]; depth?: number }>(), { depth: 0 })
const emit = defineEmits<{ 'update:modelValue': [value: ConditionGroup] }>()
function update(index: number, node: ConditionNode) { emit('update:modelValue', { ...props.modelValue, conditions: props.modelValue.conditions.map((child, i) => index === i ? node : child) }) }
function append(node: ConditionNode) { emit('update:modelValue', { ...props.modelValue, conditions: [...props.modelValue.conditions, node] }) }
function remove(index: number) { emit('update:modelValue', { ...props.modelValue, conditions: props.modelValue.conditions.filter((_, i) => i !== index) }) }
function fieldFor(code: string) { return props.fields.find(field => field.code === code) }
function changeField(index: number, code: string) { update(index, newCondition(fieldFor(code))) }
function changeOperator(index: number, node: ConditionLeaf, operator: string) {
  update(index, { field: node.field, operator, ...(['EMPTY', 'NOT_EMPTY'].includes(operator) ? {} : { value: operator === 'IN' ? [] : fieldFor(node.field)?.fieldType === 'MULTI_SELECT' && operator === 'CONTAINS' ? '' : initialConditionValue(fieldFor(node.field)) }) })
}
</script>
<template>
  <div class="condition-group-editor">
    <div class="condition-group-header">
      <a-select :value="modelValue.mode" aria-label="条件组关系" :options="[{value:'ALL',label:'全部条件满足'}, {value:'ANY',label:'任一条件满足'}]" @update:value="emit('update:modelValue', {...modelValue, mode:$event})" />
      <a-button size="small" :disabled="!fields.length" @click="append(newCondition(fields[0]))">添加条件</a-button>
      <a-button v-if="depth < 7" size="small" @click="append(emptyCondition())">添加条件组</a-button>
    </div>
    <p v-if="!modelValue.conditions.length" class="condition-empty">{{ modelValue.mode === 'ALL' ? '没有条件时始终满足。' : '请添加至少一个条件。' }}</p>
    <div v-for="(node, index) in modelValue.conditions" :key="index" class="condition-node">
      <template v-if="isConditionGroup(node)">
        <div class="condition-subgroup-label"><span>条件组</span><a-button type="link" size="small" @click="remove(index)">移除条件组</a-button></div>
        <ConditionGroupEditor :model-value="node" :fields="fields" :depth="depth + 1" @update:model-value="update(index, $event)" />
      </template>
      <template v-else>
        <div class="condition-leaf-controls">
          <a-select :value="fieldFor(node.field) ? node.field : undefined" aria-label="条件字段" placeholder="选择业务字段" :options="fields.map(field=>({value:field.code,label:field.name}))" @update:value="changeField(index,$event)" />
          <a-select :value="node.operator" aria-label="比较方式" :options="operatorsFor(fieldFor(node.field))" @update:value="changeOperator(index,node,$event)" />
          <a-button size="small" @click="remove(index)">移除条件</a-button>
        </div>
        <ConditionValueInput v-if="fieldFor(node.field) && !['EMPTY','NOT_EMPTY'].includes(node.operator)" :field="fieldFor(node.field)!" :model-value="node.value" :operator="node.operator" @update:model-value="update(index,{...node,value:$event})" />
      </template>
    </div>
  </div>
</template>
<style scoped>
.condition-group-editor { border: 1px solid #dbe3ee; padding: 12px; border-radius: 6px; display: grid; gap: 10px; }
.condition-group-header, .condition-leaf-controls, .condition-subgroup-label { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.condition-group-header .ant-select { min-width: 130px; }
.condition-leaf-controls .ant-select { flex: 1; min-width: 120px; }
.condition-node { display: grid; gap: 8px; padding-top: 8px; border-top: 1px solid #edf0f5; }
.condition-subgroup-label { justify-content: space-between; color: #64748b; }
.condition-empty { margin: 0; color: #64748b; font-size: 12px; }
</style>
