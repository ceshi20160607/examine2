<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { ApiError, api } from '../api'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { isoDateOffset, sourceLabel } from '../work-configuration'
import { versionLabel } from '../presentation'
import type { SystemPeopleDirectory, WorkCalendar, WorkConfiguration, WorkConfigurationField, WorkProject } from '../types'
import PersonSelect from '../components/PersonSelect.vue'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system'; mode?: 'configuration' | 'calendar' }>(), { context: 'system', mode: 'configuration' })
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const configuration = ref<WorkConfiguration>()
const calendar = ref<WorkCalendar>()
const projects = ref<WorkProject[]>([])
const directory = ref<SystemPeopleDirectory>({ departments: [], people: [], permissionVersion: 0 })
const loading = ref(false)
const busy = ref('')
const pageError = ref('')
const fieldOpen = ref(false)
const publishOpen = ref(false)
const rollbackOpen = ref(false)
const activeField = ref<WorkConfigurationField>()
const fieldForm = reactive({
  targetType: 'TASK', fieldCode: '', fieldName: '', fieldType: 'TEXT', required: false, sortOrder: 10,
  dictionaryOptions: [] as string[], statisticsGroupBy: [] as string[], statisticsMetrics: [] as string[],
})
const publicationForm = reactive({ summary: '', targetVersion: 1, reason: '' })
const filters = reactive({ from: isoDateOffset(-7), to: isoDateOffset(7), projectId: undefined as number | undefined, tenantMemberId: undefined as number | undefined })
const fieldTypeOptions = [
  { value: 'TEXT', label: '单行文本' }, { value: 'TEXTAREA', label: '多行文本' },
  { value: 'NUMBER', label: '数字' }, { value: 'DATE', label: '日期' },
  { value: 'DATETIME', label: '日期时间' }, { value: 'BOOLEAN', label: '是 / 否' },
  { value: 'DICTIONARY', label: '固定选项' }, { value: 'REFERENCE', label: '业务引用' },
  { value: 'QUERY', label: '统计查询' },
]
const statisticsGroupOptions = [
  { value: 'DATE', label: '日期' }, { value: 'ACCOUNT', label: '人员' },
  { value: 'PROJECT', label: '项目' }, { value: 'STATUS', label: '状态' },
  { value: 'PRIORITY', label: '优先级' },
]
const statisticsMetricOptions = [
  { value: 'TASK_COUNT', label: '任务数' }, { value: 'COMPLETED_COUNT', label: '完成任务数' },
  { value: 'OVERDUE_COUNT', label: '逾期任务数' }, { value: 'RISK_COUNT', label: '风险任务数' },
  { value: 'LOG_COUNT', label: '日志数' }, { value: 'LOG_MINUTES', label: '工作时长（分钟）' },
]

const allConfiguredFields = computed(() => {
  const rows = [...(configuration.value?.ownDrafts || []), ...(configuration.value?.inheritedPlatformDefaults || [])]
  return rows.sort((a, b) => a.targetType.localeCompare(b.targetType) || a.sortOrder - b.sortOrder)
})
function can(action: string) {
  return allowsPermission(current.value?.permissions, 'WORK', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'WORK', '*', action)
}
function describeError(error: unknown) {
  const text = error instanceof ApiError ? `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
    : error instanceof Error ? error.message : '操作失败'
  pageError.value = text
  return text
}
function targetLabel(target: string) {
  return ({ PROJECT: '项目', TASK: '任务', LOG: '日志', STATISTICS: '统计查询' } as Record<string, string>)[target] || target
}
function fieldTypeLabel(type: string) {
  return fieldTypeOptions.find(item => item.value === type)?.label || type
}
function settingValues(settings: Record<string, unknown>, key: string) {
  const value = settings?.[key]
  if (!Array.isArray(value)) return []
  return value.map(item => {
    if (item && typeof item === 'object' && 'value' in item) return String((item as { value: unknown }).value)
    return String(item)
  })
}
function optionLabels(values: string[], options: Array<{ value: string; label: string }>) {
  return values.map(value => options.find(item => item.value === value)?.label || value)
}
function configurationRule(field: WorkConfigurationField) {
  if (field.targetType === 'STATISTICS') {
    const groups = optionLabels(settingValues(field.settings, 'groupBy'), statisticsGroupOptions)
    const metrics = optionLabels(settingValues(field.settings, 'metrics'), statisticsMetricOptions)
    return `按${groups.join('、') || '未设置维度'}分组 · 统计${metrics.join('、') || '未设置指标'}`
  }
  if (field.fieldType === 'DICTIONARY') {
    const options = settingValues(field.settings, 'options')
    return options.length ? `可选：${options.join('、')}` : '尚未设置固定选项'
  }
  return `按${fieldTypeLabel(field.fieldType)}格式校验${field.required ? '，提交时不能为空' : ''}`
}
function taskStatusLabel(status: string) {
  return ({ BACKLOG: '待规划', TODO: '待开始', IN_PROGRESS: '进行中', BLOCKED: '已阻塞', COMPLETED: '已完成', CANCELLED: '已取消' } as Record<string, string>)[status] || '处理中'
}
function projectStatusLabel(status: string) {
  return ({ ACTIVE: '进行中', COMPLETED: '已完成', ARCHIVED: '已归档' } as Record<string, string>)[status] || '处理中'
}
function generatedFieldCode(name: string) {
  const latin = name.trim().toLowerCase().replace(/[^a-z0-9_-]+/g, '_').replace(/^_+|_+$/g, '')
  return `work_${latin || Date.now().toString(36)}`.slice(0, 96)
}
async function loadConfiguration() {
  if (!token.value || !can('VIEW_CONFIG')) return
  loading.value = true
  try { configuration.value = await api<WorkConfiguration>('/api/work/configuration', {}, token.value) }
  catch (error) { message.error(describeError(error)) } finally { loading.value = false }
}

function openField(field?: WorkConfigurationField) {
  activeField.value = field
  Object.assign(fieldForm, field ? {
    targetType: field.targetType, fieldCode: field.fieldCode, fieldName: field.fieldName,
    fieldType: field.fieldType, required: field.required, sortOrder: field.sortOrder,
    dictionaryOptions: settingValues(field.settings, 'options'),
    statisticsGroupBy: settingValues(field.settings, 'groupBy'),
    statisticsMetrics: settingValues(field.settings, 'metrics'),
  } : {
    targetType: 'TASK', fieldCode: '', fieldName: '', fieldType: 'TEXT', required: false, sortOrder: 10,
    dictionaryOptions: [], statisticsGroupBy: [], statisticsMetrics: [],
  })
  fieldOpen.value = true
}

async function saveField() {
  if (!token.value || !fieldForm.fieldName.trim()) return
  if (!fieldForm.fieldCode) fieldForm.fieldCode = generatedFieldCode(fieldForm.fieldName)
  busy.value = 'field'
  try {
    if (fieldForm.targetType === 'STATISTICS'
      && (!fieldForm.statisticsGroupBy.length || !fieldForm.statisticsMetrics.length)) {
      throw new Error('统计查询至少选择一个分组维度和一个统计指标')
    }
    if (fieldForm.fieldType === 'DICTIONARY' && !fieldForm.dictionaryOptions.length) {
      throw new Error('固定选项字段至少添加一个可选项')
    }
    const settings = fieldForm.targetType === 'STATISTICS'
      ? { groupBy: fieldForm.statisticsGroupBy, metrics: fieldForm.statisticsMetrics }
      : fieldForm.fieldType === 'DICTIONARY' ? { options: fieldForm.dictionaryOptions } : {}
    await api(`/api/work/configuration/fields/${fieldForm.targetType}/${fieldForm.fieldCode}`, {
      method: 'PUT', body: JSON.stringify({ expectedVersion: activeField.value?.rowVersion,
        fieldName: fieldForm.fieldName, fieldType: fieldForm.fieldType, required: fieldForm.required,
        sortOrder: fieldForm.sortOrder, settings }),
    }, token.value)
    fieldOpen.value = false
    message.success('工作配置草稿已保存；发布前不会改变当前生效口径')
    await loadConfiguration()
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

function openPublish(field: WorkConfigurationField) {
  activeField.value = field
  publicationForm.summary = ''
  publishOpen.value = true
}
async function publishField() {
  if (!token.value || !activeField.value || !publicationForm.summary.trim()) return
  busy.value = 'publish'
  try {
    const field = activeField.value
    await api(`/api/work/configuration/fields/${field.targetType}/${field.fieldCode}/publish`, {
      method: 'POST', body: JSON.stringify({ expectedVersion: field.rowVersion, changeSummary: publicationForm.summary }),
    }, token.value)
    publishOpen.value = false
    message.success('新版本已发布，预览已按平台默认与系统覆盖重新计算')
    await loadConfiguration()
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

function openRollback(field: WorkConfigurationField) {
  activeField.value = field
  publicationForm.targetVersion = field.publicationVersions[0]?.version || 1
  publicationForm.reason = ''
  rollbackOpen.value = true
}
async function rollbackField() {
  if (!token.value || !activeField.value || !publicationForm.reason.trim()) return
  busy.value = 'rollback'
  try {
    const field = activeField.value
    await api(`/api/work/configuration/fields/${field.targetType}/${field.fieldCode}/rollback`, {
      method: 'POST', body: JSON.stringify({ expectedVersion: field.rowVersion,
        targetPublicationVersion: publicationForm.targetVersion, reason: publicationForm.reason }),
    }, token.value)
    rollbackOpen.value = false
    message.success('已基于历史快照发布新的回滚版本，旧版本仍完整保留')
    await loadConfiguration()
  } catch (error) { message.error(describeError(error)) } finally { busy.value = '' }
}

async function loadCalendar() {
  if (!token.value || !can('VIEW_CALENDAR')) return
  loading.value = true
  try {
    const query = new URLSearchParams({ from: filters.from, to: filters.to })
    if (filters.projectId) query.set('projectId', String(filters.projectId))
    if (filters.tenantMemberId) query.set('tenantMemberId', String(filters.tenantMemberId))
    calendar.value = await api<WorkCalendar>(`/api/work/calendar?${query}`, {}, token.value)
    if (can('VIEW')) projects.value = await api<WorkProject[]>('/api/work/projects', {}, token.value)
    if (props.context === 'system') directory.value = await api<SystemPeopleDirectory>('/api/system-directory', {}, token.value)
  } catch (error) { message.error(describeError(error)) } finally { loading.value = false }
}

onMounted(() => props.mode === 'configuration' ? loadConfiguration() : loadCalendar())
</script>

<template>
  <div v-if="mode === 'configuration'" class="work-configuration-page">
    <div class="page-heading compact-heading"><div><p class="eyebrow">任务 · 工作配置</p><h1>字段、规则与统计口径</h1><p>草稿与发布版本分离；系统预览明确标注平台默认或系统覆盖，不复制任何任务、日志业务明细。</p></div><a-button v-if="can('CONFIGURE')" type="primary" @click="openField()">新建配置</a-button></div>
    <a-alert v-if="pageError" type="error" show-icon closable :message="pageError" @close="pageError = ''" />
    <a-alert v-if="!can('VIEW_CONFIG')" type="warning" show-icon message="当前工作范围没有工作配置查看权限" />
    <template v-else>
      <section class="configuration-source-legend"><span><i class="source-dot platform"></i>平台默认</span><span><i class="source-dot system"></i>系统覆盖</span><small>系统覆盖只影响当前系统与工作空间；未覆盖项继续继承平台发布版本。</small></section>
      <a-spin :spinning="loading"><div class="work-config-grid">
        <article v-for="field in allConfiguredFields" :key="`${field.source}:${field.targetType}:${field.fieldCode}`" class="panel-card work-config-card" :data-source="field.source">
          <div class="panel-title"><span><a-tag>{{ targetLabel(field.targetType) }}</a-tag><strong>{{ field.fieldName }}</strong></span><a-tag :color="field.source === 'PLATFORM_DEFAULT' ? 'purple' : 'blue'">{{ sourceLabel(field.source) }}</a-tag></div>
          <p>{{ fieldTypeLabel(field.fieldType) }} · {{ field.required ? '必填' : '选填' }} · 显示顺序 {{ field.sortOrder }}</p>
          <div class="config-version-line"><span>配置草稿</span><strong>{{ versionLabel(field.currentPublicationVersion) }}</strong><span>历史 {{ field.publicationVersions.length }} 版</span></div>
          <p class="config-rule-summary">{{ configurationRule(field) }}</p>
          <div v-if="field.source !== 'PLATFORM_DEFAULT' || context === 'platform'" class="card-actions"><a-button v-if="can('CONFIGURE')" size="small" @click="openField(field)">编辑草稿</a-button><a-button v-if="can('PUBLISH_CONFIG')" size="small" type="primary" @click="openPublish(field)">发布</a-button><a-button v-if="can('PUBLISH_CONFIG') && field.publicationVersions.length" size="small" @click="openRollback(field)">回滚为新版本</a-button></div>
        </article>
        <a-empty v-if="!loading && !allConfiguredFields.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无工作配置，可先创建任务、日志字段或统计查询定义" />
      </div></a-spin>
      <section v-if="configuration?.effectivePublished.length" class="panel-card effective-preview"><div class="panel-title"><strong>当前生效预览</strong><a-tag color="green">{{ configuration.effectivePublished.length }} 项</a-tag></div><a-table :pagination="false" size="small" :data-source="configuration.effectivePublished" :row-key="(row: WorkConfigurationField) => `${row.targetType}:${row.fieldCode}`"><a-table-column title="对象"><template #default="{ record }">{{ targetLabel(record.targetType) }}</template></a-table-column><a-table-column title="字段" data-index="fieldName" /><a-table-column title="类型"><template #default="{ record }">{{ fieldTypeLabel(record.fieldType) }}</template></a-table-column><a-table-column title="来源"><template #default="{ record }">{{ sourceLabel(record.source) }}</template></a-table-column><a-table-column title="版本"><template #default="{ record }">{{ versionLabel(record.currentPublicationVersion) }}</template></a-table-column></a-table></section>
    </template>
  </div>

  <div v-else class="work-calendar-page">
    <div class="page-heading compact-heading"><div><p class="eyebrow">任务 · 日历统计</p><h1>授权范围内的工作日历</h1><p>任务、日志和项目里程碑按当前平台或系统、工作空间及数据权限实时汇总；无明细权限时只显示汇总结果。</p></div></div>
    <a-alert v-if="pageError" type="error" show-icon closable :message="pageError" @close="pageError = ''" />
    <a-alert v-if="!can('VIEW_CALENDAR')" type="warning" show-icon message="当前工作范围没有工作日历查看权限" />
    <template v-else>
      <section class="panel-card calendar-filters"><div class="form-grid"><a-form-item label="日期范围"><a-range-picker :value="[filters.from, filters.to]" value-format="YYYY-MM-DD" @change="(value: [string,string]) => { filters.from=value?.[0] || filters.from; filters.to=value?.[1] || filters.to }" /></a-form-item><a-form-item label="项目"><a-select v-model:value="filters.projectId" allow-clear placeholder="全部可见项目" :options="projects.map(project => ({ value: project.id, label: project.name }))" /></a-form-item><a-form-item v-if="context === 'system'" label="人员"><PersonSelect v-model="filters.tenantMemberId" :people="directory.people" value-key="tenantMemberId" placeholder="全部授权人员" /></a-form-item><a-form-item label=" "><a-button type="primary" :loading="loading" @click="loadCalendar">查询统计</a-button></a-form-item></div></section>
      <template v-if="calendar"><a-alert v-if="!calendar.detailAvailable" type="info" show-icon message="当前账号只有汇总权限" description="任务和日志计数按数据范围计算；明细数组已由后端清空，无法从页面反推出敏感记录。" />
        <section class="calendar-summary"><article><small>到期任务</small><strong>{{ calendar.summary.tasksDue }}</strong></article><article><small>完成任务</small><strong>{{ calendar.summary.tasksCompleted }}</strong></article><article><small>逾期 / 风险</small><strong>{{ calendar.summary.overdueTasks }} / {{ calendar.summary.riskTasks }}</strong></article><article><small>工作日志</small><strong>{{ calendar.summary.logCount }}</strong><span>{{ calendar.summary.logMinutes }} 分钟</span></article><article><small>项目里程碑</small><strong>{{ calendar.summary.projectMilestones }}</strong></article></section>
        <section class="calendar-days"><article v-for="day in calendar.days" :key="day.date" class="panel-card calendar-day" :class="{ active: day.tasksDue || day.logCount || day.projectMilestones }"><header><strong>{{ day.date }}</strong><span><a-tag v-if="day.tasksDue" color="blue">任务 {{ day.tasksDue }}</a-tag><a-tag v-if="day.logCount" color="green">日志 {{ day.logCount }}</a-tag><a-tag v-if="day.projectMilestones" color="purple">里程碑 {{ day.projectMilestones }}</a-tag></span></header><template v-if="day.detailAvailable"><button v-for="task in day.tasks" :key="`task:${task.id}`" class="calendar-detail-row"><span>任务</span><strong>{{ task.title }}</strong><small>{{ taskStatusLabel(task.status) }} · 负责人：{{ task.owner.displayName }}</small></button><button v-for="log in day.logs" :key="`log:${log.id}`" class="calendar-detail-row"><span>日志</span><strong>{{ log.title }}</strong><small>{{ log.durationMinutes }} 分钟 · 作者：{{ log.author.displayName }}</small></button><button v-for="milestone in day.milestones" :key="`project:${milestone.id}`" class="calendar-detail-row"><span>项目</span><strong>{{ milestone.name }}</strong><small>{{ projectStatusLabel(milestone.status) }}</small></button></template><p v-else-if="day.tasksDue || day.logCount || day.projectMilestones" class="calendar-hidden-detail">明细因权限隐藏</p><small v-else>当日无授权范围内事项</small></article></section>
      </template>
    </template>
  </div>

  <a-modal v-model:open="fieldOpen" :title="activeField ? '编辑工作配置草稿' : '新建工作配置草稿'" width="700px" :confirm-loading="busy === 'field'" @ok="saveField"><a-form layout="vertical"><div class="form-grid"><a-form-item label="对象" required><a-select v-model:value="fieldForm.targetType" :disabled="!!activeField" :options="['PROJECT','TASK','LOG','STATISTICS'].map(value => ({ value, label: targetLabel(value) }))" @change="fieldForm.fieldType = fieldForm.targetType === 'STATISTICS' ? 'QUERY' : fieldForm.fieldType === 'QUERY' ? 'TEXT' : fieldForm.fieldType" /></a-form-item><a-form-item label="显示名称" required><a-input v-model:value="fieldForm.fieldName" /></a-form-item></div><div class="form-grid"><a-form-item label="字段类型" required><a-select v-model:value="fieldForm.fieldType" :disabled="fieldForm.targetType === 'STATISTICS'" :options="fieldTypeOptions.filter(item => fieldForm.targetType === 'STATISTICS' ? item.value === 'QUERY' : item.value !== 'QUERY')" /></a-form-item><a-form-item label="显示顺序"><a-input-number v-model:value="fieldForm.sortOrder" :min="0" style="width:100%" /></a-form-item></div><a-form-item label="必填"><a-switch v-model:checked="fieldForm.required" :disabled="fieldForm.targetType === 'STATISTICS'" /></a-form-item><template v-if="fieldForm.targetType === 'STATISTICS'"><a-form-item label="分组维度" required><a-select v-model:value="fieldForm.statisticsGroupBy" mode="multiple" placeholder="选择统计结果按什么查看" :options="statisticsGroupOptions" /></a-form-item><a-form-item label="统计指标" required><a-select v-model:value="fieldForm.statisticsMetrics" mode="multiple" placeholder="选择需要统计的数据" :options="statisticsMetricOptions" /></a-form-item><a-alert type="info" show-icon message="这里只保存统计口径，不复制任务或日志明细" /></template><a-form-item v-else-if="fieldForm.fieldType === 'DICTIONARY'" label="固定选项" required><a-select v-model:value="fieldForm.dictionaryOptions" mode="tags" placeholder="输入一个选项后按回车，可继续添加" :open="false" /><small>业务填写时只能选择这里发布的选项。</small></a-form-item></a-form></a-modal>
  <a-modal v-model:open="publishOpen" title="发布工作配置版本" :confirm-loading="busy === 'publish'" @ok="publishField"><a-alert type="warning" show-icon message="发布会改变当前生效预览，但不会改写历史版本" style="margin-bottom:16px" /><a-form layout="vertical"><a-form-item label="变更说明" required><a-textarea v-model:value="publicationForm.summary" /></a-form-item></a-form></a-modal>
  <a-modal v-model:open="rollbackOpen" title="恢复并发布新版本" :confirm-loading="busy === 'rollback'" @ok="rollbackField"><a-form layout="vertical"><a-form-item label="目标历史版本" required><a-select v-model:value="publicationForm.targetVersion" :options="activeField?.publicationVersions.map(version => ({ value: version.version, label: `${versionLabel(version.version)} · ${version.changeSummary}` }))" /></a-form-item><a-form-item label="恢复原因" required><a-textarea v-model:value="publicationForm.reason" /></a-form-item></a-form></a-modal>
</template>
