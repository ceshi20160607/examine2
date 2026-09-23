<script setup lang="ts">
import { computed } from 'vue'
import { productDateTime, userFacingWorkspaceName } from '../presentation'
import { richPlainText, structuredSummary } from '../structured-content'
import type { RuntimeBusinessView, RuntimeReadField, RuntimeRecord } from '../types'
import SchemaRecordDetail from './SchemaRecordDetail.vue'

const props = defineProps<{ view?: RuntimeBusinessView | null }>()
const sections = computed(() => [
  { key: 'proposed', name: '原提交内容', record: props.view?.proposed,
    description: '这是发起动作时提交的内容，尚不代表已经正式生效。',
    unavailable: '当前没有可展示的原提交内容，可能未获业务字段权限。' },
  { key: 'current', name: '当前正式数据', record: props.view?.current,
    description: '这是当前正式业务记录，与原提交内容分别按当前权限展示。',
    unavailable: '当前没有可展示的正式数据，可能未获业务详情权限，或正式记录尚未生成。' },
  { key: 'submitted', name: '节点提交内容', record: props.view?.submitted,
    description: '这是已完成节点提交的修改，尚不代表正式生效，与原提交和正式数据分别按当前权限展示。',
    unavailable: '当前没有可展示的节点提交内容，可能尚无节点修改，或未获业务字段权限。' },
].map(section => ({ ...section, fields: readFields(section.record) })))

/** 只消费已授权来源描述，不从原始值或另一份投影补出隐藏字段和配置。 */
function readFields(record?: RuntimeRecord | null): RuntimeReadField[] {
  return Object.entries(record?.fieldPresentation || {})
    .map(([code, field]) => ({ code, ...field }))
    .sort((left, right) => left.sortOrder - right.sortOrder || left.code.localeCompare(right.code))
}

/** 每份投影各用自身的可信标签；展示文本优先，不能按原值恢复脱敏名称。 */
function readText(record: RuntimeRecord | null | undefined, field: RuntimeReadField, value: unknown): string {
  if (typeof field.displayValue === 'string') return field.displayValue
  if (value === undefined || value === null || value === '') return '—'
  if (value === '***') return '••••（已脱敏）'
  if (value === '[已散列]') return '不可回显（已散列）'
  if (field.fieldType === 'SECRET') return '••••（已脱敏）'
  if (field.fieldType === 'BOOLEAN') return value === true ? '是' : value === false ? '否' : '暂不可展示'
  if (['DATETIME', 'SYSTEM_CREATED_AT', 'SYSTEM_UPDATED_AT'].includes(field.fieldType)) return productDateTime(String(value))
  const labels = ['MEMBER', 'SYSTEM_CREATED_BY', 'SYSTEM_UPDATED_BY'].includes(field.fieldType)
    ? record?.organizationLabels?.members : field.fieldType === 'DEPARTMENT' ? record?.organizationLabels?.departments : undefined
  if (['MEMBER', 'DEPARTMENT', 'SYSTEM_CREATED_BY', 'SYSTEM_UPDATED_BY'].includes(field.fieldType)) {
    return (Array.isArray(value) ? value : [value]).map(item => labels?.[String(item)] || '名称暂不可用').join('、') || '—'
  }
  if (['SINGLE_SELECT', 'MULTI_SELECT', 'STATUS', 'CASCADE', 'REFERENCE'].includes(field.fieldType)) return '名称暂不可用'
  if (field.fieldType === 'RICH_TEXT') return richPlainText(String(value)) || '—'
  if (field.fieldType === 'JSON') return structuredSummary(value)
  return String(value)
}
</script>

<template>
  <section class="runtime-business-view">
    <h3>业务内容</h3>
    <a-alert v-if="view?.message" show-icon type="info" :message="view.message" />
    <template v-if="view">
      <section v-for="section in sections" :key="section.key" class="business-projection">
        <h4>{{ section.name }}</h4>
        <p>{{ section.description }}</p>
        <template v-if="section.record">
          <a-descriptions :column="1" bordered size="small" class="business-record-meta">
            <a-descriptions-item label="标题">{{ section.record.title }}</a-descriptions-item>
            <a-descriptions-item label="所属工作空间">{{ userFacingWorkspaceName(section.record.dataTenantName) }}</a-descriptions-item>
          </a-descriptions>
          <SchemaRecordDetail :fields="[]" :values="section.record.fields" :presentation="section.record.fieldPresentation"
            :read-fields="section.fields" :read-text="(field, value) => readText(section.record, field, value)" />
          <a-empty v-if="!section.fields.length" description="没有可展示的业务字段" />
        </template>
        <a-alert v-else show-icon type="info" :message="section.unavailable" />
      </section>
    </template>
    <a-alert v-else show-icon type="info" message="仅展示流程信息" description="当前没有可展示的业务内容。" />
  </section>
</template>

<style scoped>
.runtime-business-view { display: grid; gap: 16px; min-width: 0; }
h3 { margin: 0; font-size: 15px; }
h4 { margin: 0; font-size: 14px; }
p { margin: 6px 0 14px; color: #64748b; }
.business-projection { min-width: 0; }
.business-record-meta { margin-bottom: 16px; }
</style>
