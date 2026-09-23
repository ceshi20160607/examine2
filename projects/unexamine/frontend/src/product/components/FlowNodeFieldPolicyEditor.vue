<script lang="ts">
import type { FlowNodeFormField } from '../types'

type PolicyKey = 'visible' | 'editable' | 'required' | 'hidden'
const policyKeys: PolicyKey[] = ['visible', 'editable', 'required', 'hidden']
const policyNames: Record<PolicyKey, string> = { visible: '查看', editable: '编辑', required: '必填', hidden: '隐藏' }
const owns = (policy: Record<string, unknown>, key: string) => Object.prototype.hasOwnProperty.call(policy, key)

/** 与服务端同一集合语义：缺省查看全部，隐藏优先，编辑和必填只收紧已有权限。 */
export function nodeFieldPolicyIssues(policy: Record<string, unknown> | undefined, fields: FlowNodeFormField[], ready: boolean, unavailable?: string): string[] {
  if (!policy || !policyKeys.some(key => owns(policy, key))) return []
  if (!ready) return [unavailable || '节点字段目录尚未就绪，请选择有权访问的业务对象并刷新。']
  const issues: string[] = []
  const codes = new Set(fields.map(field => field.code))
  let wellFormed = true
  for (const key of policyKeys) {
    if (!owns(policy, key)) continue
    const values = policy[key]
    if (!Array.isArray(values) || values.some(value => typeof value !== 'string' || !value.trim())) {
      issues.push(`${policyNames[key]}设置必须是有效字段列表，原设置仍保留，请主动重新配置。`)
      wellFormed = false
      continue
    }
    if (new Set(values).size !== values.length) issues.push(`${policyNames[key]}设置有重复引用，请主动移除。`)
    if (values.some(value => !codes.has(value))) issues.push(`${policyNames[key]}设置包含已失效或当前无权读取的旧字段，请主动移除或重新配置。`)
  }
  if (!wellFormed) return issues
  const list = (key: PolicyKey) => Array.isArray(policy[key]) ? policy[key] as string[] : []
  const readable = (code: string) => (!owns(policy, 'visible') || list('visible').includes(code)) && !list('hidden').includes(code)
  for (const code of list('editable')) {
    const field = fields.find(item => item.code === code)
    const name = field?.name || '已失效字段'
    if (!readable(code)) issues.push(`“${name}”配置编辑时必须可查看且不能隐藏。`)
    if (!field?.editable) issues.push(`“${name}”是系统、只读或已失效字段，不能配置编辑。`)
  }
  for (const code of list('required')) {
    if (!list('editable').includes(code)) issues.push(`“${fields.find(field => field.code === code)?.name || '已失效字段'}”配置必填时必须同时配置编辑。`)
  }
  return issues
}
</script>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ modelValue?: Record<string, unknown>; fields: FlowNodeFormField[]; ready: boolean; loading?: boolean; error?: string; disabled?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: Record<string, unknown>] }>()
const selectorKeys = policyKeys
const selectorNames = policyNames
const policy = computed(() => props.modelValue || {})
const allVisible = computed(() => !owns(policy.value, 'visible'))
const disabled = computed(() => props.disabled || !props.ready || props.loading)
const issues = computed(() => nodeFieldPolicyIssues(props.modelValue, props.fields, props.ready, props.error))
const codes = (key: PolicyKey): string[] => Array.isArray(policy.value[key])
  ? (policy.value[key] as unknown[]).filter((value): value is string => typeof value === 'string' && Boolean(value.trim())) : []
const readable = (code: string) => (allVisible.value || codes('visible').includes(code)) && !codes('hidden').includes(code)
const allowed = (key: PolicyKey, field: FlowNodeFormField) => key === 'editable' ? field.editable && readable(field.code)
  : key === 'required' ? field.editable && readable(field.code) && codes('editable').includes(field.code) : true

function options(key: PolicyKey) {
  const available = props.fields.map(field => ({ value: field.code, label: `${field.name}${!field.editable ? '（只读）' : ''}`, disabled: !allowed(key, field) }))
  const known = new Set(props.fields.map(field => field.code))
  // 旧引用有明确占位名称和主动清理入口，不能把技术编码当作新选项或偷偷删掉。
  const unavailable = [...new Set(codes(key).filter(code => !known.has(code)))]
    .map((code, index) => ({ value: code, label: `失效字段（旧引用 ${index + 1}）`, disabled: true }))
  return [...available, ...unavailable]
}

function update(key: PolicyKey, value: unknown) {
  if (disabled.value || !Array.isArray(value)) return
  emit('update:modelValue', { ...policy.value, [key]: [...value] })
}

function setAllVisible(all: unknown) {
  if (disabled.value || typeof all !== 'boolean') return
  const next = { ...policy.value }
  if (all) delete next.visible
  else next.visible = props.fields.map(field => field.code)
  emit('update:modelValue', next)
}

function needsRepair(key: PolicyKey) {
  if (!owns(policy.value, key)) return false
  const value = policy.value[key]
  if (!Array.isArray(value) || value.some(item => typeof item !== 'string' || !item.trim())) return true
  return new Set(value).size !== value.length || value.some(code => {
    const field = props.fields.find(item => item.code === code)
    return !field || !allowed(key, field)
  })
}

/** 仅在用户明确点击后清理当前一项，其他策略、未知扩展键和旧配置均原样保留。 */
function repair(key: PolicyKey) {
  if (disabled.value) return
  update(key, [...new Set(codes(key))].filter(code => {
    const field = props.fields.find(item => item.code === code)
    return field && allowed(key, field)
  }))
}
</script>

<template>
  <div class="node-field-policy-editor">
    <a-alert v-if="!ready" show-icon :type="loading ? 'info' : 'warning'" :message="loading ? '正在读取节点字段目录' : error || '请先在系统中选择有效业务对象并刷新节点字段目录。'" />
    <a-alert v-for="(issue, index) in issues" :key="index" type="warning" show-icon :message="issue" />
    <p>节点设置只限制已有业务权限；系统和只读字段不能编辑，隐藏字段不会展示。</p>
    <div class="node-field-policy-all"><a-switch :checked="allVisible" :disabled="disabled" @change="setAllVisible" /><span>查看全部当前有权字段</span></div>
    <a-form-item v-for="key in selectorKeys" :key="key" :label="`${selectorNames[key]}字段`" :extra="key === 'visible' ? allVisible ? '未限定查看字段；隐藏设置仍优先。' : '没有选中字段时，本节点不查看任何业务字段。' : key === 'required' ? '必填字段必须同时配置编辑。' : key === 'editable' ? '仅选择可查看且未隐藏的可编辑字段，不授予额外权限。' : '隐藏设置优先于查看设置。'">
      <a-select mode="multiple" :value="key === 'visible' && allVisible ? fields.map(field => field.code) : codes(key)"
        :options="options(key)" :disabled="disabled || key === 'visible' && allVisible" show-search option-filter-prop="label"
        style="width:100%" placeholder="按名称选择字段" @change="update(key, $event)" />
      <a-button v-if="ready && needsRepair(key)" type="link" danger :disabled="disabled" @click="repair(key)">移除本项无效引用</a-button>
    </a-form-item>
  </div>
</template>

<style scoped>
.node-field-policy-editor { display: grid; gap: 10px; min-width: 0; }
.node-field-policy-editor p { margin: 0; color: #64748b; font-size: 12px; }
.node-field-policy-all { display: flex; align-items: center; gap: 8px; }
.node-field-policy-editor :deep(.ant-form-item) { margin-bottom: 0; }
</style>
