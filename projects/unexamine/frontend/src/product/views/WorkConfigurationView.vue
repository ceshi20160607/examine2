<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { ApiError, api } from '../api'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens, systemContext, systemTokens } from '../session'
import { isoDateOffset, parseFieldSettings, sourceLabel } from '../work-configuration'
import type { WorkCalendar, WorkConfiguration, WorkConfigurationField, WorkProject } from '../types'

const props = withDefaults(defineProps<{ context?: 'platform' | 'system'; mode?: 'configuration' | 'calendar' }>(), { context: 'system', mode: 'configuration' })
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const current = computed(() => props.context === 'platform' ? platformContext.value : systemContext.value)
const contextCode = computed(() => props.context === 'platform' ? 'PLATFORM' : 'SYSTEM')
const configuration = ref<WorkConfiguration>()
const calendar = ref<WorkCalendar>()
const projects = ref<WorkProject[]>([])
const loading = ref(false)
const busy = ref('')
const fieldOpen = ref(false)
const publishOpen = ref(false)
const rollbackOpen = ref(false)
const activeField = ref<WorkConfigurationField>()
const fieldForm = reactive({ targetType: 'TASK', fieldCode: '', fieldName: '', fieldType: 'TEXT', required: false, sortOrder: 10, settings: '{}' })
const publicationForm = reactive({ summary: '', targetVersion: 1, reason: '' })
const filters = reactive({ from: isoDateOffset(-7), to: isoDateOffset(7), projectId: undefined as number | undefined, accountId: undefined as number | undefined })

const allConfiguredFields = computed(() => {
  const rows = [...(configuration.value?.ownDrafts || []), ...(configuration.value?.inheritedPlatformDefaults || [])]
  return rows.sort((a, b) => a.targetType.localeCompare(b.targetType) || a.sortOrder - b.sortOrder)
})
function can(action: string) {
  return allowsPermission(current.value?.permissions, 'WORK', contextCode.value, action)
    || allowsPermission(current.value?.permissions, 'WORK', '*', action)
}
function describeError(error: unknown) {
  if (error instanceof ApiError) return `${error.message}${error.traceId ? `（追踪号 ${error.traceId}）` : ''}`
  return error instanceof Error ? error.message : '操作失败'
}
function targetLabel(target: string) {
  return ({ PROJECT: '项目', TASK: '任务', LOG: '日志', STATISTICS: '统计查询' } as Record<string, string>)[target] || target
}
function taskStatusLabel(status: string) {
  return ({ BACKLOG: '待规划', TODO: '待开始', IN_PROGRESS: '进行中', BLOCKED: '已阻塞', COMPLETED: '已完成', CANCELLED: '已取消' } as Record<string, string>)[status] || '处理中'
}
function projectStatusLabel(status: string) {
  return ({ ACTIVE: '进行中', COMPLETED: '已完成', ARCHIVED: '已归档' } as Record<string, string>)[status] || '处理中'
}
function accountDisplayName(accountId?: number) {
  if (!accountId) return '未指定'
  return accountId === current.value?.accountId ? current.value?.displayName || '我' : '其他成员'
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
    settings: JSON.stringify(field.settings || {}, null, 2),
  } : { targetType: 'TASK', fieldCode: '', fieldName: '', fieldType: 'TEXT', required: false, sortOrder: 10, settings: '{}' })
  fieldOpen.value = true
}

async function saveField() {
  if (!token.value || !fieldForm.fieldCode.trim() || !fieldForm.fieldName.trim()) return
  busy.value = 'field'
  try {
    const settings = parseFieldSettings(fieldForm.settings)
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
    if (filters.accountId) query.set('accountId', String(filters.accountId))
    calendar.value = await api<WorkCalendar>(`/api/work/calendar?${query}`, {}, token.value)
    if (can('VIEW')) projects.value = await api<WorkProject[]>('/api/work/projects', {}, token.value)
  } catch (error) { message.error(describeError(error)) } finally { loading.value = false }
}

onMounted(() => props.mode === 'configuration' ? loadConfiguration() : loadCalendar())
</script>

<template>
  <div v-if="mode === 'configuration'" class="work-configuration-page">
    <div class="page-heading compact-heading"><div><p class="eyebrow">任务 · 工作配置</p><h1>字段、规则与统计口径</h1><p>草稿与发布版本分离；系统预览明确标注平台默认或系统覆盖，不复制任何任务、日志业务明细。</p></div><a-button v-if="can('CONFIGURE')" type="primary" @click="openField()">新建配置</a-button></div>
    <a-alert v-if="!can('VIEW_CONFIG')" type="warning" show-icon message="当前工作范围没有工作配置查看权限" />
    <template v-else>
      <section class="configuration-source-legend"><span><i class="source-dot platform"></i>平台默认</span><span><i class="source-dot system"></i>系统覆盖</span><small>系统覆盖只影响当前系统与租户；未覆盖项继续继承平台发布版本。</small></section>
      <a-spin :spinning="loading"><div class="work-config-grid">
        <article v-for="field in allConfiguredFields" :key="`${field.source}:${field.targetType}:${field.fieldCode}`" class="panel-card work-config-card" :data-source="field.source">
          <div class="panel-title"><span><a-tag>{{ targetLabel(field.targetType) }}</a-tag><strong>{{ field.fieldName }}</strong></span><a-tag :color="field.source === 'PLATFORM_DEFAULT' ? 'purple' : 'blue'">{{ sourceLabel(field.source) }}</a-tag></div>
          <code>{{ field.fieldCode }}</code><p>{{ field.fieldType }} · {{ field.required ? '必填' : '选填' }} · 排序 {{ field.sortOrder }}</p>
          <div class="config-version-line"><span>草稿修订 {{ field.rowVersion }}</span><strong>当前发布 V{{ field.currentPublicationVersion || '—' }}</strong><span>历史 {{ field.publicationVersions.length }} 版</span></div>
          <pre>{{ JSON.stringify(field.settings, null, 2) }}</pre>
          <div v-if="field.source !== 'PLATFORM_DEFAULT' || context === 'platform'" class="card-actions"><a-button v-if="can('CONFIGURE')" size="small" @click="openField(field)">编辑草稿</a-button><a-button v-if="can('PUBLISH_CONFIG')" size="small" type="primary" @click="openPublish(field)">发布</a-button><a-button v-if="can('PUBLISH_CONFIG') && field.publicationVersions.length" size="small" @click="openRollback(field)">回滚为新版本</a-button></div>
        </article>
        <a-empty v-if="!loading && !allConfiguredFields.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="暂无工作配置，可先创建任务、日志字段或统计查询定义" />
      </div></a-spin>
      <section v-if="configuration?.effectivePublished.length" class="panel-card effective-preview"><div class="panel-title"><strong>当前生效预览</strong><a-tag color="green">{{ configuration.effectivePublished.length }} 项</a-tag></div><a-table :pagination="false" size="small" :data-source="configuration.effectivePublished" :row-key="(row: WorkConfigurationField) => `${row.targetType}:${row.fieldCode}`" :columns="[{ title:'对象', dataIndex:'targetType' },{ title:'字段', dataIndex:'fieldName' },{ title:'编码', dataIndex:'fieldCode' },{ title:'来源', dataIndex:'source' },{ title:'发布版', dataIndex:'currentPublicationVersion' }]" /></section>
    </template>
  </div>

  <div v-else class="work-calendar-page">
    <div class="page-heading compact-heading"><div><p class="eyebrow">任务 · 日历统计</p><h1>授权范围内的工作日历</h1><p>任务、日志和项目里程碑按当前平台或系统、工作空间及数据权限实时汇总；无明细权限时只显示汇总结果。</p></div></div>
    <a-alert v-if="!can('VIEW_CALENDAR')" type="warning" show-icon message="当前工作范围没有工作日历查看权限" />
    <template v-else>
      <section class="panel-card calendar-filters"><div class="form-grid"><a-form-item label="日期范围"><a-range-picker :value="[filters.from, filters.to]" value-format="YYYY-MM-DD" @change="(value: [string,string]) => { filters.from=value?.[0] || filters.from; filters.to=value?.[1] || filters.to }" /></a-form-item><a-form-item label="项目"><a-select v-model:value="filters.projectId" allow-clear placeholder="全部可见项目" :options="projects.map(project => ({ value: project.id, label: project.name }))" /></a-form-item><a-form-item label="人员账号"><a-input-number v-model:value="filters.accountId" :min="1" allow-clear placeholder="全部授权人员" /></a-form-item><a-form-item label=" "><a-button type="primary" :loading="loading" @click="loadCalendar">查询统计</a-button></a-form-item></div></section>
      <template v-if="calendar"><a-alert v-if="!calendar.detailAvailable" type="info" show-icon message="当前账号只有汇总权限" description="任务和日志计数按数据范围计算；明细数组已由后端清空，无法从页面反推出敏感记录。" />
        <section class="calendar-summary"><article><small>到期任务</small><strong>{{ calendar.summary.tasksDue }}</strong></article><article><small>完成任务</small><strong>{{ calendar.summary.tasksCompleted }}</strong></article><article><small>逾期 / 风险</small><strong>{{ calendar.summary.overdueTasks }} / {{ calendar.summary.riskTasks }}</strong></article><article><small>工作日志</small><strong>{{ calendar.summary.logCount }}</strong><span>{{ calendar.summary.logMinutes }} 分钟</span></article><article><small>项目里程碑</small><strong>{{ calendar.summary.projectMilestones }}</strong></article></section>
        <section class="calendar-days"><article v-for="day in calendar.days" :key="day.date" class="panel-card calendar-day" :class="{ active: day.tasksDue || day.logCount || day.projectMilestones }"><header><strong>{{ day.date }}</strong><span><a-tag v-if="day.tasksDue" color="blue">任务 {{ day.tasksDue }}</a-tag><a-tag v-if="day.logCount" color="green">日志 {{ day.logCount }}</a-tag><a-tag v-if="day.projectMilestones" color="purple">里程碑 {{ day.projectMilestones }}</a-tag></span></header><template v-if="day.detailAvailable"><button v-for="task in day.tasks" :key="`task:${task.id}`" class="calendar-detail-row"><span>任务</span><strong>{{ task.title }}</strong><small>{{ taskStatusLabel(task.status) }} · 负责人：{{ accountDisplayName(task.ownerAccountId) }}</small></button><button v-for="log in day.logs" :key="`log:${log.id}`" class="calendar-detail-row"><span>日志</span><strong>{{ log.title }}</strong><small>{{ log.durationMinutes }} 分钟 · 作者：{{ accountDisplayName(log.authorAccountId) }}</small></button><button v-for="milestone in day.milestones" :key="`project:${milestone.id}`" class="calendar-detail-row"><span>项目</span><strong>{{ milestone.name }}</strong><small>{{ projectStatusLabel(milestone.status) }}</small></button></template><p v-else-if="day.tasksDue || day.logCount || day.projectMilestones" class="calendar-hidden-detail">明细因权限隐藏</p><small v-else>当日无授权范围内事项</small></article></section>
      </template>
    </template>
  </div>

  <a-modal v-model:open="fieldOpen" :title="activeField ? '编辑工作配置草稿' : '新建工作配置草稿'" width="700px" :confirm-loading="busy === 'field'" @ok="saveField"><a-form layout="vertical"><div class="form-grid"><a-form-item label="对象" required><a-select v-model:value="fieldForm.targetType" :disabled="!!activeField" :options="['PROJECT','TASK','LOG','STATISTICS'].map(value => ({ value, label: targetLabel(value) }))" /></a-form-item><a-form-item label="稳定编码" required><a-input v-model:value="fieldForm.fieldCode" :disabled="!!activeField" placeholder="如 acceptance_note" /></a-form-item></div><div class="form-grid"><a-form-item label="显示名称" required><a-input v-model:value="fieldForm.fieldName" /></a-form-item><a-form-item label="字段类型" required><a-select v-model:value="fieldForm.fieldType" :options="['TEXT','TEXTAREA','NUMBER','DATE','DATETIME','BOOLEAN','DICTIONARY','REFERENCE','QUERY'].map(value => ({ value }))" /></a-form-item></div><div class="form-grid"><a-form-item label="必填"><a-switch v-model:checked="fieldForm.required" /></a-form-item><a-form-item label="排序"><a-input-number v-model:value="fieldForm.sortOrder" :min="0" /></a-form-item></div><a-form-item label="设置（JSON 对象）"><a-textarea v-model:value="fieldForm.settings" :rows="6" /><small v-if="fieldForm.targetType === 'STATISTICS'">统计查询须使用 QUERY，settings 定义 groupBy 与 metrics 白名单，不保存业务明细。</small></a-form-item></a-form></a-modal>
  <a-modal v-model:open="publishOpen" title="发布工作配置版本" :confirm-loading="busy === 'publish'" @ok="publishField"><a-alert type="warning" show-icon message="发布会改变当前生效预览，但不会改写历史版本" style="margin-bottom:16px" /><a-form layout="vertical"><a-form-item label="变更说明" required><a-textarea v-model:value="publicationForm.summary" /></a-form-item></a-form></a-modal>
  <a-modal v-model:open="rollbackOpen" title="回滚并发布新版本" :confirm-loading="busy === 'rollback'" @ok="rollbackField"><a-form layout="vertical"><a-form-item label="目标历史版本" required><a-select v-model:value="publicationForm.targetVersion" :options="activeField?.publicationVersions.map(version => ({ value: version.version, label: `V${version.version} · ${version.changeSummary}` }))" /></a-form-item><a-form-item label="回滚原因" required><a-textarea v-model:value="publicationForm.reason" /></a-form-item></a-form></a-modal>
</template>
