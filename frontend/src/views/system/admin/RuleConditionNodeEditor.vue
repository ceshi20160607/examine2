<script setup lang="ts">
import { Plus, Trash2, Workflow } from 'lucide-vue-next'
import { computed } from 'vue'

import type { ConfigField } from '@/types/config'
import {
  createCondition,
  createConditionGroup,
  numericFieldTypes,
  operatorsForField,
  type RuleNodeForm,
} from './configStudioModel'

const props = defineProps<{
  node: RuleNodeForm
  fields: ConfigField[]
  depth: number
  removable?: boolean
}>()

const emit = defineEmits<{ remove: [] }>()

const groupNode = computed(() => props.node.kind === 'group' ? props.node : null)
const conditionNode = computed(() => props.node.kind === 'condition' ? props.node : null)
const conditionFieldType = computed(() => props.fields.find((field) => field.id === conditionNode.value?.fieldId)?.type)
const conditionOperators = computed(() => operatorsForField(conditionFieldType.value))
const noValue = computed(() => ['EMPTY', 'NOT_EMPTY'].includes(conditionNode.value?.operator ?? ''))
const canAddGroup = computed(() => props.depth < 4)

function normalizeOperator() {
  if (conditionNode.value && !conditionOperators.value.includes(conditionNode.value.operator)) {
    conditionNode.value.operator = 'EQ'
  }
}

function addCondition() {
  if (!groupNode.value || groupNode.value.children.length >= 50) return
  groupNode.value.children.push(createCondition(props.fields[0]?.id ?? ''))
}

function addGroup() {
  if (!groupNode.value || !canAddGroup.value || groupNode.value.children.length >= 50) return
  groupNode.value.children.push(createConditionGroup(props.fields[0]?.id ?? ''))
}

function removeChild(index: number) {
  if (groupNode.value && groupNode.value.children.length > 1) groupNode.value.children.splice(index, 1)
}
</script>

<template>
  <div :class="['condition-node', `depth-${depth}`, { group: groupNode }]">
    <template v-if="groupNode">
      <div class="condition-group-head">
        <a-segmented v-model:value="groupNode.join" :options="['AND', 'OR']" size="small" />
        <span class="condition-group-actions">
          <a-button size="small" title="添加条件" @click="addCondition"><Plus :size="14" /></a-button>
          <a-button v-if="canAddGroup" size="small" title="添加条件组" @click="addGroup"><Workflow :size="14" /></a-button>
          <a-button v-if="removable" size="small" danger title="删除条件组" @click="emit('remove')"><Trash2 :size="14" /></a-button>
        </span>
      </div>
      <div class="condition-group-children">
        <RuleConditionNodeEditor
          v-for="(child, index) in groupNode.children"
          :key="child.key"
          :node="child"
          :fields="fields"
          :depth="depth + 1"
          :removable="groupNode.children.length > 1"
          @remove="removeChild(Number(index))"
        />
      </div>
    </template>
    <template v-else-if="conditionNode">
      <div class="condition-leaf">
        <a-select v-model:value="conditionNode.fieldId" aria-label="条件字段" @change="normalizeOperator">
          <a-select-option v-for="field in fields" :key="field.id" :value="field.id">{{ field.name }}</a-select-option>
        </a-select>
        <a-select v-model:value="conditionNode.operator" aria-label="条件运算符">
          <a-select-option v-for="operator in conditionOperators" :key="operator" :value="operator">{{ operator }}</a-select-option>
        </a-select>
        <a-select v-if="!noValue && conditionFieldType === 'SWITCH'" v-model:value="conditionNode.value" aria-label="条件值">
          <a-select-option :value="'true'">是</a-select-option>
          <a-select-option :value="'false'">否</a-select-option>
        </a-select>
        <a-input-number
          v-else-if="!noValue && numericFieldTypes.has(conditionFieldType ?? '')"
          v-model:value="conditionNode.value"
          aria-label="条件值"
          style="width:100%"
        />
        <a-input
          v-else-if="!noValue"
          v-model:value="conditionNode.value"
          aria-label="条件值"
          :placeholder="['IN', 'NOT_IN'].includes(conditionNode.operator) ? '多个值以逗号分隔' : '值'"
        />
        <a-input-number
          v-if="conditionNode.operator === 'BETWEEN' && numericFieldTypes.has(conditionFieldType ?? '')"
          v-model:value="conditionNode.valueEnd"
          aria-label="条件结束值"
          style="width:100%"
        />
        <a-input
          v-else-if="conditionNode.operator === 'BETWEEN'"
          v-model:value="conditionNode.valueEnd"
          aria-label="条件结束值"
          placeholder="结束值"
        />
        <a-button v-if="removable" danger title="删除条件" @click="emit('remove')"><Trash2 :size="14" /></a-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.condition-node{min-width:0}.condition-node.group{border-left:2px solid #b8c8ce;padding-left:10px}.condition-group-head{display:flex;align-items:center;justify-content:space-between;gap:10px;margin-bottom:8px}.condition-group-actions{display:flex;gap:6px}.condition-group-children{display:grid;gap:8px}.condition-leaf{display:grid;grid-template-columns:minmax(140px,1.25fr) minmax(110px,.8fr) minmax(130px,1fr) minmax(110px,1fr) 36px;gap:8px;align-items:center}.depth-4,.depth-5{border-left-color:#6a8b95}@media(max-width:760px){.condition-leaf{grid-template-columns:1fr}.condition-group-head{align-items:flex-start}.condition-group-actions{flex-wrap:wrap;justify-content:flex-end}}
</style>