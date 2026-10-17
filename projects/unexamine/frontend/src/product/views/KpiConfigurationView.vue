<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, api } from '../api'
import { dashboardMetricSummary, formatDashboardMetric } from '../dashboard'
import { defaultKpiPeriod, kpiStatusLabel } from '../kpi'
import { userFacingDateTime } from '../presentation'
import { systemContext } from '../session'
import PersonSelect from '../components/PersonSelect.vue'
import type { DashboardDataSource, KpiDefinition, KpiOverview, KpiPreview, ReportMetadata,
  SystemAuthorizationOverview, SystemPeopleDirectory } from '../types'

const props = defineProps<{ token?: string; dataSources: DashboardDataSource[] }>()
const emit = defineEmits<{ changed: [] }>()
const router = useRouter()
const overview = ref<KpiOverview>({ kpis: [] })
const directory = ref<SystemPeopleDirectory>({ departments: [], people: [], permissionVersion: 0 })
const authorization = ref<SystemAuthorizationOverview>()
const metadata = ref<ReportMetadata>()
const selectedId = ref<number>()
const preview = ref<KpiPreview>()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const configurationDirty = ref(false)
const periodYear = ref(new Date().getFullYear())
const periodUnit = ref(new Date().getMonth() + 1)
const form = reactive({
  code: '', name: '', dataSourceVersionId: undefined as number | undefined,
  targetValue: 1, targetOperator: 'GTE' as 'GTE' | 'LTE', periodType: 'MONTH' as 'MONTH' | 'QUARTER' | 'YEAR',
  responsibleType: 'PERSON' as 'PERSON' | 'DEPARTMENT' | 'ROLE', responsibleIds: [] as number[],
  visibilityModuleCode: '', drillModuleCode: '', visibilityTenantMemberIds: [] as number[], drillTenantMemberIds: [] as number[], reminderEnabled: true,
  reminderRecipientTenantMemberIds: [] as number[], reminderBelowPercent: 100, status: 'ACTIVE' as 'ACTIVE' | 'INACTIVE',
  expectedVersion: undefined as number | undefined, periodKey: defaultKpiPeriod('MONTH'),
})

const sourceOptions = computed(() => props.dataSources
  .filter(source => source.sourceType === 'MODULE_REPORT')
  .flatMap(source => source.versions.map(version => ({
    value: version.id,
    label: `${source.name} · 发布版本 ${version.versionNumber}`,
    source,
  }))))
const selectedSource = computed(() => sourceOptions.value.find(item => item.value === form.dataSourceVersionId)?.source)
const selectedKpi = computed(() => overview.value.kpis.find(item => item.id === selectedId.value))
const latestResult = computed(() => selectedKpi.value?.latestResult)
const resultUsesCurrentConfiguration = computed(() => !latestResult.value || !selectedKpi.value
  || Number(latestResult.value.explanation.kpiVersion) === selectedKpi.value.version)
const previewReady = computed(() => !configurationDirty.value && preview.value?.periodKey === form.periodKey
  && preview.value.kpiVersion === form.expectedVersion)
const sourceTimeField = computed(() => {
  const time = selectedSource.value?.definition.timeField
  if (!time || typeof time !== 'object' || Array.isArray(time)) return ''
  const field = time as Record<string, unknown>
  const modules = selectedSource.value?.definition.modules
  const alias = String(field.alias || '')
  const module = Array.isArray(modules) ? modules.find(item => item && typeof item === 'object'
    && (item as Record<string, unknown>).alias === alias) as Record<string, unknown> | undefined : undefined
  return metadata.value?.modules.find(item => item.moduleCode === module?.moduleCode)
    ?.fields.find(item => item.code === field.fieldCode)?.name || '已配置时间字段'
})

function causeMessage(cause: unknown) {
  return cause instanceof ApiError ? `${cause.code}：${cause.message}` : '操作失败，请稍后重试'
}

function permission(moduleCode: string, members: number[]): Record<string, unknown> {
  if (members.length) return { tenantMemberIds: members }
  return moduleCode.trim()
    ? { resourceType: 'MODULE', resourceCode: moduleCode.trim(), actionCode: 'LIST' }
    : {}
}

function permissionCode(policy: Record<string, unknown>) {
  return String(policy.resourceType === 'MODULE' ? policy.resourceCode || '' : '')
}

function policyMembers(policy: Record<string, unknown>) {
  return Array.isArray(policy.tenantMemberIds) ? policy.tenantMemberIds.map(Number).filter(Number.isFinite) : []
}

function generatedCode(name: string) {
  const latin = name.trim().toLowerCase().replace(/[^a-z0-9_-]+/g, '_').replace(/^_+|_+$/g, '')
  return `kpi_${latin || Date.now().toString(36)}`.slice(0, 96)
}

function setResponsibleId(value: unknown) {
  const id = Number(value)
  form.responsibleIds = Number.isInteger(id) && id > 0 ? [id] : []
}

function syncPeriodControls(key: string) {
  const match = key.match(/^(\d{4})(?:-(?:Q([1-4])|(0[1-9]|1[0-2])))?$/)
  periodYear.value = match ? Number(match[1]) : new Date().getFullYear()
  periodUnit.value = match?.[2] ? Number(match[2]) : match?.[3] ? Number(match[3]) : 1
}

function updatePeriodKey() {
  const year = Math.max(1900, Math.min(9999, Math.trunc(Number(periodYear.value) || new Date().getFullYear())))
  periodYear.value = year
  form.periodKey = form.periodType === 'YEAR' ? String(year)
    : form.periodType === 'QUARTER' ? `${year}-Q${periodUnit.value}`
      : `${year}-${String(periodUnit.value).padStart(2, '0')}`
  preview.value = undefined
}

function changePeriodType(value: 'MONTH' | 'QUARTER' | 'YEAR') {
  if (form.periodType === value) return
  form.periodType = value
  form.periodKey = defaultKpiPeriod(value)
  syncPeriodControls(form.periodKey)
}

function responsibleName(kpi: KpiDefinition) {
  const id = kpi.responsibleIds[0]
  if (!id) return '未指定责任对象'
  if (kpi.responsibleType === 'PERSON') return directory.value.people.find(item => item.tenantMemberId === id)?.displayName || '当前责任成员'
  if (kpi.responsibleType === 'DEPARTMENT') return directory.value.departments.find(item => item.id === id)?.fullName || '当前责任部门'
  return authorization.value?.roles.find(item => item.id === id)?.name || '当前责任角色'
}

function reminderName(id: number) {
  return directory.value.people.find(item => item.tenantMemberId === id)?.displayName || '当前成员'
}

function reminderStatus(status: string) {
  return ({ SENT: '已发送', ACKNOWLEDGED: '已确认', RESOLVED: '已解决' } as Record<string, string>)[status] || '处理中'
}

function recordSampleCount(explanation: Record<string, unknown>) {
  return Array.isArray(explanation.sourceRecordIds) ? explanation.sourceRecordIds.length : 0
}

function openKpiWorkspace() {
  if (!selectedId.value || !systemContext.value?.systemId) return
  void router.push(`/systems/${systemContext.value.systemId}?workspace=kpi&kpiId=${selectedId.value}`)
}

async function load() {
  if (!props.token) return
  loading.value = true; error.value = ''
  try {
    const [kpis, people, auth, reportMetadata] = await Promise.all([
      api<KpiOverview>('/api/analytics/admin/kpis', {}, props.token),
      api<SystemPeopleDirectory>('/api/system-directory', {}, props.token),
      api<SystemAuthorizationOverview>('/api/admin/system/authorization', {}, props.token),
      api<ReportMetadata>('/api/analytics/admin/report-metadata', {}, props.token),
    ])
    overview.value = kpis
    directory.value = people
    authorization.value = auth
    metadata.value = reportMetadata
    if (selectedId.value) {
      const selected = overview.value.kpis.find(item => item.id === selectedId.value)
      if (selected) fill(selected)
    }
  } catch (cause) { error.value = causeMessage(cause) } finally { loading.value = false }
}

function reset() {
  selectedId.value = undefined; preview.value = undefined
  Object.assign(form, {
    code: '', name: '', dataSourceVersionId: undefined, targetValue: 1, targetOperator: 'GTE',
    periodType: 'MONTH', responsibleType: 'PERSON', responsibleIds: [], visibilityModuleCode: '',
    drillModuleCode: '', visibilityTenantMemberIds: [], drillTenantMemberIds: [], reminderEnabled: true, reminderRecipientTenantMemberIds: [],
    reminderBelowPercent: 100, status: 'ACTIVE', expectedVersion: undefined,
    periodKey: defaultKpiPeriod('MONTH'),
  })
  syncPeriodControls(form.periodKey)
  configurationDirty.value = false
}

function fill(kpi: KpiDefinition) {
  selectedId.value = kpi.id; preview.value = undefined
  Object.assign(form, {
    code: kpi.code, name: kpi.name, dataSourceVersionId: kpi.dataSourceVersionId,
    targetValue: Number(kpi.targetValue), targetOperator: kpi.targetOperator, periodType: kpi.periodType,
    responsibleType: kpi.responsibleType, responsibleIds: [...kpi.responsibleIds],
    visibilityModuleCode: permissionCode(kpi.visibilityPermission),
    drillModuleCode: permissionCode(kpi.drillPermission),
    visibilityTenantMemberIds: policyMembers(kpi.visibilityPermission), drillTenantMemberIds: policyMembers(kpi.drillPermission),
    reminderEnabled: kpi.reminderEnabled,
    reminderRecipientTenantMemberIds: [...kpi.reminderRecipientTenantMemberIds],
    reminderBelowPercent: Number(kpi.reminderBelowPercent), status: kpi.status,
    expectedVersion: kpi.version, periodKey: kpi.latestResult?.periodKey || defaultKpiPeriod(kpi.periodType),
  })
  syncPeriodControls(form.periodKey)
  configurationDirty.value = false
}

async function save() {
  if (!props.token || !form.dataSourceVersionId) { error.value = '请选择一个已发布的结构化报表版本。'; return }
  const responsibleIds = [...new Set(form.responsibleIds)]
  if (responsibleIds.length !== 1) { error.value = '请选择一个责任成员、部门或角色。'; return }
  const recipients = [...new Set(form.reminderRecipientTenantMemberIds)]
  if (form.reminderEnabled && !recipients.length) { error.value = '启用提醒后至少选择一个工作空间成员。'; return }
  saving.value = true; error.value = ''; success.value = ''
  try {
    if (!form.name.trim()) { error.value = '请填写 KPI 名称。'; return }
    form.code ||= generatedCode(form.name)
    const body = {
      code: form.code, name: form.name, dataSourceVersionId: form.dataSourceVersionId,
      targetValue: form.targetValue, targetOperator: form.targetOperator, periodType: form.periodType,
      responsibleType: form.responsibleType, responsibleIds,
      visibilityPermission: permission(form.visibilityModuleCode, form.visibilityTenantMemberIds),
      drillPermission: permission(form.drillModuleCode, form.drillTenantMemberIds),
      reminderEnabled: form.reminderEnabled, reminderRecipientTenantMemberIds: recipients,
      reminderBelowPercent: form.reminderBelowPercent, status: form.status, expectedVersion: form.expectedVersion,
    }
    const path = selectedId.value ? `/api/analytics/admin/kpis/${selectedId.value}` : '/api/analytics/admin/kpis'
    const saved = await api<KpiDefinition>(path, {
      method: selectedId.value ? 'PUT' : 'POST', body: JSON.stringify(body),
    }, props.token)
    selectedId.value = saved.id
    success.value = `KPI“${saved.name}”配置版本 v${saved.version} 已保存。`
    preview.value = undefined
    await load()
    emit('changed')
  } catch (cause) { error.value = causeMessage(cause) } finally { saving.value = false }
}

async function execute(mode: 'preview' | 'calculate') {
  if (!props.token || !selectedId.value) return
  if (configurationDirty.value) { error.value = '配置已有未保存的改动，请先保存，再预览或计算。'; return }
  if (mode === 'calculate' && !previewReady.value) {
    error.value = '请先预览当前周期和配置版本，确认计算口径后再计算并发送提醒。'
    return
  }
  loading.value = true; error.value = ''; success.value = ''
  try {
    if (mode === 'preview') {
      preview.value = await api<KpiPreview>(`/api/analytics/admin/kpis/${selectedId.value}/preview?periodKey=${encodeURIComponent(form.periodKey)}`, {}, props.token)
      success.value = '预览完成：目标、数据源版本、计算时点和授权记录均已解释。'
    } else {
      const result = await api<KpiDefinition['latestResult']>(`/api/analytics/admin/kpis/${selectedId.value}/calculate`, {
        method: 'POST', body: JSON.stringify({ periodKey: form.periodKey }),
      }, props.token)
      success.value = result?.stale ? '源暂不可用，已保留上次成功快照且未发送虚假达标提醒。'
        : `计算完成：${kpiStatusLabel(result?.status || '')}，已发送 ${result?.reminders.filter(item => item.status === 'SENT').length || 0} 条责任提醒。`
      await load()
      emit('changed')
    }
  } catch (cause) { error.value = causeMessage(cause) } finally { loading.value = false }
}

watch(() => ({ code: form.code, name: form.name, dataSourceVersionId: form.dataSourceVersionId,
  targetValue: form.targetValue, targetOperator: form.targetOperator, periodType: form.periodType,
  responsibleType: form.responsibleType, responsibleIds: form.responsibleIds,
  visibilityModuleCode: form.visibilityModuleCode, drillModuleCode: form.drillModuleCode,
  visibilityTenantMemberIds: form.visibilityTenantMemberIds, drillTenantMemberIds: form.drillTenantMemberIds,
  reminderEnabled: form.reminderEnabled, reminderRecipientTenantMemberIds: form.reminderRecipientTenantMemberIds,
  reminderBelowPercent: form.reminderBelowPercent, status: form.status,
}), () => { configurationDirty.value = true; preview.value = undefined }, { deep: true, flush: 'sync' })

function changeResponsibleType(value: 'PERSON' | 'DEPARTMENT' | 'ROLE') {
  if (value === form.responsibleType) return
  form.responsibleType = value
  form.responsibleIds = []
}
onMounted(load)
</script>

<template>
  <section class="kpi-configuration">
    <a-alert v-if="error" type="error" show-icon :message="error" closable @close="error = ''" />
    <a-alert v-if="success" type="success" show-icon :message="success" closable @close="success = ''" />
    <div class="dashboard-config-layout">
      <aside class="panel-card dashboard-config-list">
        <div class="panel-title"><strong>KPI 责任清单</strong><a-button size="small" @click="reset">新建</a-button></div>
        <button v-for="item in overview.kpis" :key="item.id" type="button" :class="{ active: selectedId === item.id }" @click="fill(item)">
          <span><strong>{{ item.name }}</strong><small>{{ item.periodType === 'MONTH' ? '月度' : item.periodType === 'QUARTER' ? '季度' : '年度' }} · {{ responsibleName(item) }}</small></span>
          <a-tag :color="item.latestResult?.stale ? 'orange' : item.latestResult?.status === 'ACHIEVED' ? 'green' : 'red'">{{ kpiStatusLabel(item.latestResult?.status || '') }}</a-tag>
        </button>
        <a-empty v-if="!overview.kpis.length" :image="false" description="尚未配置 KPI" />
      </aside>

      <div class="kpi-builder">
        <section class="panel-card dashboard-config-form">
          <div class="panel-title"><strong>{{ selectedId ? '编辑 KPI 配置' : '新建 KPI' }}</strong><span>实际值只读取不可变报表发布版本</span></div>
          <a-form layout="vertical">
            <div class="form-grid"><a-form-item label="KPI 名称" required><a-input v-model:value="form.name" placeholder="月度有效客户" /></a-form-item><a-form-item label="统计口径" required><a-select v-model:value="form.dataSourceVersionId" show-search :options="sourceOptions" placeholder="选择已发布业务报表" /></a-form-item></div>
            <a-alert v-if="form.dataSourceVersionId" type="info" show-icon :message="sourceTimeField ? `当前数据源草稿的时间字段：${sourceTimeField}` : '计算周期需要已发布数据源配置时间字段'" description="月、季度和年的实际值按所选发布版本的时间字段筛选；最终以服务端对该发布版本的校验和计算为准。责任归属不会自动改变来源统计范围。" />
            <div class="form-grid form-grid--three"><a-form-item label="目标值"><a-input-number v-model:value="form.targetValue" :min="0" /></a-form-item><a-form-item label="达标规则"><a-select v-model:value="form.targetOperator" :options="[{ value: 'GTE', label: '实际值 ≥ 目标' }, { value: 'LTE', label: '实际值 ≤ 目标' }]" /></a-form-item><a-form-item label="周期"><a-select :value="form.periodType" :options="[{ value: 'MONTH', label: '月' }, { value: 'QUARTER', label: '季度' }, { value: 'YEAR', label: '年' }]" @change="changePeriodType" /></a-form-item></div>
            <div class="form-grid form-grid--three"><a-form-item label="责任归属"><a-select :value="form.responsibleType" :options="[{ value: 'PERSON', label: '具体成员' }, { value: 'DEPARTMENT', label: '部门' }, { value: 'ROLE', label: '角色' }]" @change="changeResponsibleType" /></a-form-item><a-form-item label="选择一个责任对象" required><PersonSelect v-if="form.responsibleType === 'PERSON'" :model-value="form.responsibleIds[0]" value-key="tenantMemberId" :people="directory.people" @update:model-value="setResponsibleId" /><a-select v-else-if="form.responsibleType === 'DEPARTMENT'" :value="form.responsibleIds[0]" allow-clear show-search option-filter-prop="label" :options="directory.departments.map(item => ({ value: item.id, label: item.fullName }))" @change="setResponsibleId" /><a-select v-else :value="form.responsibleIds[0]" allow-clear show-search option-filter-prop="label" :options="authorization?.roles.filter(item => item.status === 'ACTIVE').map(item => ({ value: item.id, label: item.name }))" @change="setResponsibleId" /></a-form-item><a-form-item label="状态"><a-select v-model:value="form.status" :options="[{ value: 'ACTIVE', label: '启用' }, { value: 'INACTIVE', label: '停用' }]" /></a-form-item></div>
            <p class="kpi-assignment-note">责任对象用于归属、查看和提醒；来源统计范围仍由报表筛选条件及当前权限决定。</p>
            <a-divider>查看与下钻范围</a-divider>
            <div class="form-grid"><a-form-item label="仅这些成员可在看板看到（留空按模块权限）"><PersonSelect v-model="form.visibilityTenantMemberIds" multiple value-key="tenantMemberId" :people="directory.people" show-department-filter placeholder="留空：使用模块权限" /></a-form-item><a-form-item label="仅这些成员可打开明细（留空按模块权限）"><PersonSelect v-model="form.drillTenantMemberIds" multiple value-key="tenantMemberId" :people="directory.people" show-department-filter placeholder="留空：使用模块权限" /></a-form-item></div>
            <div class="form-grid"><a-form-item label="看板查看所需模块"><a-select v-model:value="form.visibilityModuleCode" allow-clear show-search option-filter-prop="label" :disabled="form.visibilityTenantMemberIds.length > 0" :options="metadata?.modules.map(item => ({ value: item.moduleCode, label: item.moduleName }))" placeholder="留空：当前系统成员可见" /></a-form-item><a-form-item label="明细下钻所需模块"><a-select v-model:value="form.drillModuleCode" allow-clear show-search option-filter-prop="label" :disabled="form.drillTenantMemberIds.length > 0" :options="metadata?.modules.map(item => ({ value: item.moduleCode, label: item.moduleName }))" placeholder="选择对应业务模块" /></a-form-item></div>
            <a-divider>未达标处理</a-divider>
            <div class="form-grid form-grid--three"><a-form-item label="发送提醒"><a-switch v-model:checked="form.reminderEnabled" /></a-form-item><a-form-item label="提醒给谁"><PersonSelect v-model="form.reminderRecipientTenantMemberIds" multiple value-key="tenantMemberId" :people="directory.people" :disabled="!form.reminderEnabled" placeholder="选择接收成员" /></a-form-item><a-form-item label="低于达成率（%）"><a-input-number v-model:value="form.reminderBelowPercent" :disabled="!form.reminderEnabled" :min="0" /></a-form-item></div>
            <a-collapse ghost class="dashboard-advanced"><a-collapse-panel key="advanced" header="高级标识"><a-form-item label="稳定标识"><a-input v-model:value="form.code" placeholder="留空自动生成" /></a-form-item></a-collapse-panel></a-collapse>
            <div class="form-actions"><a-button type="primary" :loading="saving" @click="save">保存配置版本</a-button><span>计算周期</span><a-input-number v-model:value="periodYear" :min="1900" :max="9999" :precision="0" @change="updatePeriodKey" /><a-select v-if="form.periodType !== 'YEAR'" v-model:value="periodUnit" style="width: 92px" :options="(form.periodType === 'QUARTER' ? [1, 2, 3, 4] : [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]).map(value => ({ value, label: form.periodType === 'QUARTER' ? `第 ${value} 季度` : `${value} 月` }))" @change="updatePeriodKey" /><a-button :disabled="!selectedId || configurationDirty" :loading="loading" @click="execute('preview')">预览目标与数据源</a-button><a-button :disabled="!selectedId || !previewReady" :loading="loading" danger @click="execute('calculate')">计算并闭环提醒</a-button></div>
          </a-form>
        </section>

        <section v-if="preview" class="panel-card kpi-preview">
          <div class="panel-title"><strong>计算预览 · {{ preview.periodKey }}</strong><a-tag :color="preview.status === 'ACHIEVED' ? 'green' : 'red'">{{ kpiStatusLabel(preview.status) }}</a-tag></div>
          <div class="kpi-preview__metrics"><span><small>实际</small><strong>{{ formatDashboardMetric(preview.actualValue) }}</strong></span><span><small>目标</small><strong>{{ form.targetOperator === 'GTE' ? '≥' : '≤' }} {{ formatDashboardMetric(preview.targetValue) }}</strong></span><span><small>达成率</small><strong>{{ Number(preview.achievementRate).toFixed(2) }}%</strong></span><span><small>状态</small><strong>{{ kpiStatusLabel(preview.status) }}</strong></span></div>
          <p>{{ selectedSource?.name || '已发布业务报表' }} · {{ dashboardMetricSummary(preview.sourceResult.metricDefinition) }}</p>
          <small>按当前权限统计 · 预览于 {{ userFacingDateTime(preview.calculatedAt) }}。预览不会保存计算结果或发送提醒。</small>
        </section>

        <section v-if="latestResult" class="panel-card kpi-result-explanation">
          <div class="panel-title"><strong>最近计算结果 · {{ latestResult.periodKey }}</strong><a-tag :color="latestResult.stale ? 'orange' : latestResult.status === 'ACHIEVED' ? 'green' : 'red'">{{ kpiStatusLabel(latestResult.status) }}</a-tag></div>
          <a-alert v-if="!resultUsesCurrentConfiguration" type="warning" show-icon message="配置已调整，当前结果仍来自旧配置" description="请重新预览并计算当前周期后，再把此 KPI 加入仪表盘。" />
          <div class="kpi-preview__metrics"><span><small>实际值</small><strong>{{ formatDashboardMetric(latestResult.actualValue) }}</strong></span><span><small>目标值</small><strong>{{ selectedKpi?.targetOperator === 'LTE' ? '≤' : '≥' }} {{ formatDashboardMetric(latestResult.targetValue) }}</strong></span><span><small>达成率</small><strong>{{ Number(latestResult.achievementRate).toFixed(2) }}%</strong></span><span><small>责任对象</small><strong>{{ selectedKpi ? responsibleName(selectedKpi) : '—' }}</strong></span></div>
          <p>{{ selectedSource?.name || '已发布业务报表' }} · {{ dashboardMetricSummary(String(latestResult.explanation.metricDefinition || '')) }}</p>
          <p>计算于 {{ userFacingDateTime(latestResult.calculatedAt) }} · 可下钻明细样例 {{ recordSampleCount(latestResult.explanation) }} 条</p>
          <a-alert v-if="latestResult.stale" type="warning" show-icon message="本次数据源不可用，显示上次成功结果" :description="String(latestResult.explanation.sourceErrorMessage || '')" />
          <div v-if="latestResult.reminders.length" class="kpi-reminder-summary"><strong>责任提醒</strong><span v-for="reminder in latestResult.reminders" :key="reminder.id">{{ reminderName(reminder.recipientTenantMemberId) }} · {{ reminderStatus(reminder.status) }}</span></div>
          <a-button type="link" @click="openKpiWorkspace">查看指标看板与授权明细</a-button>
        </section>
      </div>
    </div>
  </section>
</template>
