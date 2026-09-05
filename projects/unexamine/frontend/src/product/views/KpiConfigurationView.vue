<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ApiError, api } from '../api'
import { defaultKpiPeriod, kpiStatusLabel } from '../kpi'
import PersonSelect from '../components/PersonSelect.vue'
import type { DashboardDataSource, KpiDefinition, KpiOverview, KpiPreview, ReportMetadata,
  SystemAuthorizationOverview, SystemPeopleDirectory } from '../types'

const props = defineProps<{ token?: string; dataSources: DashboardDataSource[] }>()
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
    label: `${source.name} · v${version.versionNumber} · ${version.definitionHash.slice(0, 8)}`,
    source,
  }))))

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
}

async function save() {
  if (!props.token || !form.dataSourceVersionId) { error.value = '请选择一个已发布的结构化报表版本。'; return }
  const responsibleIds = [...new Set(form.responsibleIds)]
  if (!responsibleIds.length) { error.value = '请选择至少一个责任人、责任部门或责任角色。'; return }
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
    await load()
  } catch (cause) { error.value = causeMessage(cause) } finally { saving.value = false }
}

async function execute(mode: 'preview' | 'calculate') {
  if (!props.token || !selectedId.value) return
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
        : `计算完成：${kpiStatusLabel(result?.status || '')}。`
      await load()
    }
  } catch (cause) { error.value = causeMessage(cause) } finally { loading.value = false }
}

watch(() => form.periodType, value => { form.periodKey = defaultKpiPeriod(value) })

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
          <span><strong>{{ item.name }}</strong><small>{{ item.periodType }} · 配置 v{{ item.version }}</small></span>
          <a-tag :color="item.latestResult?.stale ? 'orange' : item.latestResult?.status === 'ACHIEVED' ? 'green' : 'red'">{{ kpiStatusLabel(item.latestResult?.status || '') }}</a-tag>
        </button>
        <a-empty v-if="!overview.kpis.length" :image="false" description="尚未配置 KPI" />
      </aside>

      <div class="kpi-builder">
        <section class="panel-card dashboard-config-form">
          <div class="panel-title"><strong>{{ selectedId ? '编辑 KPI 配置' : '新建 KPI' }}</strong><span>实际值只读取不可变报表发布版本</span></div>
          <a-form layout="vertical">
            <div class="form-grid"><a-form-item label="KPI 名称" required><a-input v-model:value="form.name" placeholder="月度有效客户" /></a-form-item><a-form-item label="统计口径" required><a-select v-model:value="form.dataSourceVersionId" show-search :options="sourceOptions" placeholder="选择已发布业务报表" /></a-form-item></div>
            <div class="form-grid form-grid--three"><a-form-item label="目标值"><a-input-number v-model:value="form.targetValue" :min="0" /></a-form-item><a-form-item label="达标规则"><a-select v-model:value="form.targetOperator" :options="[{ value: 'GTE', label: '实际值 ≥ 目标' }, { value: 'LTE', label: '实际值 ≤ 目标' }]" /></a-form-item><a-form-item label="周期"><a-select v-model:value="form.periodType" :options="[{ value: 'MONTH', label: '月' }, { value: 'QUARTER', label: '季度' }, { value: 'YEAR', label: '年' }]" /></a-form-item></div>
            <div class="form-grid form-grid--three"><a-form-item label="责任归属"><a-select :value="form.responsibleType" :options="[{ value: 'PERSON', label: '具体成员' }, { value: 'DEPARTMENT', label: '部门' }, { value: 'ROLE', label: '角色' }]" @change="changeResponsibleType" /></a-form-item><a-form-item label="选择责任对象" required><PersonSelect v-if="form.responsibleType === 'PERSON'" v-model="form.responsibleIds" multiple value-key="tenantMemberId" :people="directory.people" /><a-select v-else-if="form.responsibleType === 'DEPARTMENT'" v-model:value="form.responsibleIds" mode="multiple" show-search option-filter-prop="label" :options="directory.departments.map(item => ({ value: item.id, label: item.fullName }))" /><a-select v-else v-model:value="form.responsibleIds" mode="multiple" show-search option-filter-prop="label" :options="authorization?.roles.filter(item => item.status === 'ACTIVE').map(item => ({ value: item.id, label: item.name }))" /></a-form-item><a-form-item label="状态"><a-select v-model:value="form.status" :options="[{ value: 'ACTIVE', label: '启用' }, { value: 'INACTIVE', label: '停用' }]" /></a-form-item></div>
            <a-divider>查看与下钻范围</a-divider>
            <div class="form-grid"><a-form-item label="仅这些成员可在看板看到（留空按模块权限）"><PersonSelect v-model="form.visibilityTenantMemberIds" multiple value-key="tenantMemberId" :people="directory.people" placeholder="留空：使用模块权限" /></a-form-item><a-form-item label="仅这些成员可打开明细（留空按模块权限）"><PersonSelect v-model="form.drillTenantMemberIds" multiple value-key="tenantMemberId" :people="directory.people" placeholder="留空：使用模块权限" /></a-form-item></div>
            <div class="form-grid"><a-form-item label="看板查看所需模块"><a-select v-model:value="form.visibilityModuleCode" allow-clear show-search option-filter-prop="label" :disabled="form.visibilityTenantMemberIds.length > 0" :options="metadata?.modules.map(item => ({ value: item.moduleCode, label: item.moduleName }))" placeholder="留空：当前系统成员可见" /></a-form-item><a-form-item label="明细下钻所需模块"><a-select v-model:value="form.drillModuleCode" allow-clear show-search option-filter-prop="label" :disabled="form.drillTenantMemberIds.length > 0" :options="metadata?.modules.map(item => ({ value: item.moduleCode, label: item.moduleName }))" placeholder="选择对应业务模块" /></a-form-item></div>
            <a-divider>未达标处理</a-divider>
            <div class="form-grid form-grid--three"><a-form-item label="发送提醒"><a-switch v-model:checked="form.reminderEnabled" /></a-form-item><a-form-item label="提醒给谁"><PersonSelect v-model="form.reminderRecipientTenantMemberIds" multiple value-key="tenantMemberId" :people="directory.people" :disabled="!form.reminderEnabled" placeholder="选择接收成员" /></a-form-item><a-form-item label="低于达成率（%）"><a-input-number v-model:value="form.reminderBelowPercent" :disabled="!form.reminderEnabled" :min="0" /></a-form-item></div>
            <a-collapse ghost class="dashboard-advanced"><a-collapse-panel key="advanced" header="高级标识"><a-form-item label="稳定标识"><a-input v-model:value="form.code" placeholder="留空自动生成" /></a-form-item></a-collapse-panel></a-collapse>
            <div class="form-actions"><a-button type="primary" :loading="saving" @click="save">保存配置版本</a-button><a-input v-model:value="form.periodKey" class="kpi-period-input" placeholder="2026-08" /><a-button :disabled="!selectedId" :loading="loading" @click="execute('preview')">预览目标与数据源</a-button><a-button :disabled="!selectedId" :loading="loading" danger @click="execute('calculate')">计算并闭环提醒</a-button></div>
          </a-form>
        </section>

        <section v-if="preview" class="panel-card kpi-preview">
          <div class="panel-title"><strong>计算预览 · {{ preview.periodKey }}</strong><a-tag :color="preview.status === 'ACHIEVED' ? 'green' : 'red'">{{ kpiStatusLabel(preview.status) }}</a-tag></div>
          <div class="kpi-preview__metrics"><span><small>实际</small><strong>{{ preview.actualValue }}</strong></span><span><small>目标</small><strong>{{ preview.targetValue }}</strong></span><span><small>达成率</small><strong>{{ Number(preview.achievementRate).toFixed(2) }}%</strong></span></div>
          <p>{{ preview.targetExplanation }}</p><p>{{ preview.sourceExplanation }}</p>
          <small>源版本 #{{ preview.sourceResult.dataSourceVersionId }} · {{ String(preview.sourceResult.definitionHash || '').slice(0, 12) }} · {{ preview.calculatedAt }}</small>
        </section>

        <section v-if="selectedId && overview.kpis.find(item => item.id === selectedId)?.latestResult" class="panel-card kpi-result-explanation">
          <div class="panel-title"><strong>最近成功/保留快照</strong><a-tag :color="overview.kpis.find(item => item.id === selectedId)?.latestResult?.stale ? 'orange' : 'blue'">{{ overview.kpis.find(item => item.id === selectedId)?.latestResult?.periodKey }}</a-tag></div>
          <pre>{{ JSON.stringify(overview.kpis.find(item => item.id === selectedId)?.latestResult?.explanation, null, 2) }}</pre>
        </section>
      </div>
    </div>
  </section>
</template>
