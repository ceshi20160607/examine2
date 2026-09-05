<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { CheckCircleOutlined, PlusOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import { api, ApiError } from '../api'
import { productDateTime, productStatus, versionLabel } from '../presentation'
import { systemTokens } from '../session'
import type {
  IdentityMappingJob,
  IdentityMappingLog,
  IdentityMappingPreflightReport,
  SystemDepartmentOption,
  SystemIdentityMappingConfiguration,
  SystemIdentityProviderOption,
} from '../types'

interface MappingRow { externalDepartment: string; departmentId?: number }
interface SampleRow {
  externalUserId: string
  externalDepartment: string
  email: string
  mobile: string
  employeeNo: string
  displayName: string
  mfaLevel: string
  device: string
  requestId: string
}

const providers = ref<SystemIdentityProviderOption[]>([])
const departments = ref<SystemDepartmentOption[]>([])
const configuration = ref<SystemIdentityMappingConfiguration | null>(null)
const preflight = ref<IdentityMappingPreflightReport | null>(null)
const jobs = ref<IdentityMappingJob[]>([])
const logs = ref<IdentityMappingLog[]>([])
const mappings = ref<MappingRow[]>([{ externalDepartment: '', departmentId: undefined }])
const samples = ref<SampleRow[]>([blankSample()])
const loading = ref(false)
const saving = ref(false)
const preflighting = ref(false)
const confirming = ref(false)
const error = ref('')
const success = ref('')
const form = reactive({
  providerId: undefined as number | undefined,
  tenantDomain: '',
  jitPolicy: 'ACCESS_REQUEST' as 'ACCESS_REQUEST' | 'CREATE_MEMBER' | 'CREATE_ACCOUNT_AND_MEMBER',
})
const filters = reactive({ identityProvider: '', externalUserId: '', mfaLevel: '', device: '', requestId: '', traceId: '', failureReason: '' })

const token = computed(() => systemTokens.value?.accessToken || '')
const selectedProvider = computed(() => providers.value.find(item => item.providerId === form.providerId))
const canSave = computed(() => !!selectedProvider.value && !!form.tenantDomain.trim()
  && mappings.value.some(item => item.externalDepartment.trim() && item.departmentId))
const canPreflight = computed(() => !!configuration.value && samples.value.some(item => item.externalUserId.trim()
  && item.externalDepartment.trim() && item.displayName.trim()))

function blankSample(): SampleRow {
  return { externalUserId: '', externalDepartment: '', email: '', mobile: '', employeeNo: '', displayName: '', mfaLevel: 'MFA', device: '', requestId: `ui-${Date.now()}` }
}

function explain(cause: unknown, fallback: string) {
  return cause instanceof ApiError ? `${cause.message}（${cause.code}${cause.traceId ? `，traceId: ${cause.traceId}` : ''}）` : fallback
}

function applyConfiguration(value: SystemIdentityMappingConfiguration | null) {
  configuration.value = value
  preflight.value = value?.latestPreflight || null
  if (!value) return
  form.providerId = value.providerId
  form.tenantDomain = value.tenantDomain
  form.jitPolicy = value.jitPolicy
  mappings.value = Object.entries(value.departmentMappings).map(([externalDepartment, departmentId]) => ({ externalDepartment, departmentId }))
}

async function load() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    const [providerList, departmentList, current, jobList] = await Promise.all([
      api<SystemIdentityProviderOption[]>('/api/admin/system/identity-mapping/providers', {}, token.value),
      api<SystemDepartmentOption[]>('/api/admin/system/identity-mapping/departments', {}, token.value),
      api<SystemIdentityMappingConfiguration | null>('/api/admin/system/identity-mapping/configuration', {}, token.value),
      api<IdentityMappingJob[]>('/api/admin/system/identity-mapping/jobs', {}, token.value),
    ])
    providers.value = providerList
    departments.value = departmentList
    jobs.value = jobList
    applyConfiguration(current)
    if (!current) form.providerId = providerList[0]?.providerId
    await loadLogs()
  } catch (cause) {
    error.value = explain(cause, '统一认证配置加载失败')
  } finally {
    loading.value = false
  }
}

async function rereadConfiguration() {
  const current = await api<SystemIdentityMappingConfiguration | null>('/api/admin/system/identity-mapping/configuration', {}, token.value)
  applyConfiguration(current)
}

async function saveDraft() {
  if (!canSave.value || !selectedProvider.value) return
  const departmentMappings = Object.fromEntries(mappings.value
    .filter(item => item.externalDepartment.trim() && item.departmentId)
    .map(item => [item.externalDepartment.trim(), item.departmentId]))
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    await api<SystemIdentityMappingConfiguration>('/api/admin/system/identity-mapping/configuration', {
      method: 'POST', body: JSON.stringify({
        providerId: selectedProvider.value.providerId,
        providerVersionId: selectedProvider.value.publishedVersionId,
        tenantDomain: form.tenantDomain.trim(),
        departmentMappings,
        matchOrder: ['EXTERNAL_USER_ID', 'EMAIL', 'MOBILE', 'EMPLOYEE_NO'],
        jitPolicy: form.jitPolicy,
        expectedVersion: configuration.value?.version,
      }),
    }, token.value)
    await rereadConfiguration()
    success.value = '映射草稿已保存，下一步可运行登录样本预检。'
  } catch (cause) {
    error.value = explain(cause, '身份映射草稿保存失败')
  } finally {
    saving.value = false
  }
}

async function runPreflight() {
  if (!canPreflight.value) return
  preflighting.value = true
  error.value = ''
  success.value = ''
  try {
    const validSamples = samples.value.filter(item => item.externalUserId.trim() && item.externalDepartment.trim() && item.displayName.trim())
    preflight.value = await api<IdentityMappingPreflightReport>('/api/admin/system/identity-mapping/preflight', {
      method: 'POST', body: JSON.stringify({ samples: validSamples }),
    }, token.value)
    await rereadConfiguration()
    success.value = `预检完成：${preflight.value.items.length} 项，冲突和缺失项不会自动建立系统上下文。`
  } catch (cause) {
    error.value = explain(cause, '身份映射预检失败')
  } finally {
    preflighting.value = false
  }
}

async function confirm() {
  if (!preflight.value) return
  confirming.value = true
  error.value = ''
  success.value = ''
  try {
    const job = await api<IdentityMappingJob>('/api/admin/system/identity-mapping/confirm', {
      method: 'POST', body: JSON.stringify({ preflightId: preflight.value.preflightId, confirmed: true }),
    }, token.value)
    jobs.value = [job, ...jobs.value.filter(item => item.id !== job.id)]
    await Promise.all([rereadConfiguration(), loadLogs()])
    success.value = `同步已完成：处理 ${job.progressCurrent}/${job.progressTotal} 项；未匹配成员的项目已转为访问申请。`
  } catch (cause) {
    error.value = explain(cause, '身份映射确认失败')
  } finally {
    confirming.value = false
  }
}

async function loadLogs() {
  if (!token.value) return
  const query = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => value.trim() && query.set(key, value.trim()))
  logs.value = await api<IdentityMappingLog[]>(`/api/admin/system/identity-mapping/logs?${query}`, {}, token.value)
}

function resultColor(value: string) {
  if (value === 'READY' || value === 'SUCCESS') return 'green'
  if (value === 'ACCESS_REQUEST' || value === 'ACCESS_REQUEST_PENDING') return 'blue'
  if (value === 'CONFLICT') return 'red'
  return 'orange'
}

function resultLabel(value: string) {
  return ({ READY: '可同步', SUCCESS: '成功', ACCESS_REQUEST: '转访问申请', ACCESS_REQUEST_PENDING: '等待访问审批',
    MANUAL_REVIEW: '需要人工检查', CONFLICT: '存在冲突' } as Record<string, string>)[value] || productStatus(value).label
}

function actionText(value: string) {
  return ({ BIND_MEMBER: '绑定已有成员', CREATE_MEMBER: '创建成员', CREATE_ACCOUNT_AND_MEMBER: '创建账号和成员',
    CREATE_ACCESS_REQUEST: '创建访问申请', SKIP: '不处理' } as Record<string, string>)[value] || '按映射策略处理'
}

function protocolLabel(value: string) {
  return ({ OIDC: '开放身份登录', SAML: '企业联合登录', LDAP: '企业目录' } as Record<string, string>)[value] || '企业身份源'
}

onMounted(load)
</script>

<template>
  <div class="page-heading">
    <div><p class="eyebrow">系统后台 · 统一认证</p><h1>平台身份源继承与成员映射</h1><p>系统只继承平台已发布版本；预检先列出部门、账号、成员和冲突，人工确认后才写入绑定或访问申请。</p></div>
    <a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button>
  </div>
  <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" closable @close="error = ''" />
  <a-alert v-if="success" type="success" show-icon :message="success" class="section-alert" closable @close="success = ''" />
  <a-alert v-if="!providers.length" type="warning" show-icon message="平台尚未发布可继承的身份源，请先由平台管理员完成预检和发布。" class="section-alert" />
  <a-alert v-if="!departments.length" type="warning" show-icon message="当前工作空间尚无有效部门，请先在组织架构中建立部门。" class="section-alert" />

  <div class="system-identity-grid">
    <section class="panel-card identity-mapping-config">
      <div class="panel-title"><strong>1. 身份源与部门映射</strong><span v-if="configuration">草稿已保存</span></div>
      <a-form layout="vertical">
        <a-form-item label="平台已发布身份源" required>
          <a-select v-model:value="form.providerId" placeholder="选择身份源">
            <a-select-option v-for="provider in providers" :key="provider.providerId" :value="provider.providerId">{{ provider.name }} · {{ protocolLabel(provider.protocol) }} · {{ versionLabel(provider.publishedVersionNumber) }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-alert v-if="selectedProvider" type="info" show-icon :message="`允许域名：${selectedProvider.allowedDomains.join('、') || '未限制'}`" class="mode-warning" />
        <a-form-item label="工作空间登录域名" required><a-input v-model:value="form.tenantDomain" placeholder="company.example.com" /></a-form-item>
        <a-form-item label="首次登录处理方式" required>
          <a-radio-group v-model:value="form.jitPolicy">
            <a-radio value="ACCESS_REQUEST">无成员时转访问申请</a-radio>
            <a-radio value="CREATE_MEMBER">匹配账号后创建成员</a-radio>
            <a-radio value="CREATE_ACCOUNT_AND_MEMBER">创建账号及成员</a-radio>
          </a-radio-group>
        </a-form-item>
        <div class="mapping-heading"><strong>外部部门 → 系统部门</strong><a-button size="small" type="link" @click="mappings.push({ externalDepartment: '', departmentId: undefined })"><PlusOutlined />添加</a-button></div>
        <div v-for="(mapping, index) in mappings" :key="index" class="department-mapping-row">
          <a-input v-model:value="mapping.externalDepartment" placeholder="外部部门编码" />
          <a-select v-model:value="mapping.departmentId" placeholder="系统部门">
            <a-select-option v-for="department in departments" :key="department.id" :value="department.id">{{ department.name }}</a-select-option>
          </a-select>
          <a-button danger type="text" :disabled="mappings.length === 1" @click="mappings.splice(index, 1)">移除</a-button>
        </div>
        <a-button type="primary" :loading="saving" :disabled="!canSave" @click="saveDraft"><SafetyCertificateOutlined />保存草稿并读回</a-button>
      </a-form>
    </section>

    <section class="panel-card identity-samples">
      <div class="panel-title"><strong>2. 登录样本预检</strong><a-button size="small" type="link" @click="samples.push(blankSample())"><PlusOutlined />添加样本</a-button></div>
      <div class="sample-scroll">
        <div v-for="(sample, index) in samples" :key="index" class="identity-sample-row">
          <a-input v-model:value="sample.externalUserId" placeholder="外部用户标识" />
          <a-input v-model:value="sample.externalDepartment" placeholder="外部部门" />
          <a-input v-model:value="sample.email" placeholder="邮箱" />
          <a-input v-model:value="sample.mobile" placeholder="手机" />
          <a-input v-model:value="sample.employeeNo" placeholder="工号" />
          <a-input v-model:value="sample.displayName" placeholder="显示名" />
          <a-input v-model:value="sample.mfaLevel" placeholder="登录验证级别" />
          <a-input v-model:value="sample.device" placeholder="设备" />
          <a-button danger type="text" :disabled="samples.length === 1" @click="samples.splice(index, 1)">移除</a-button>
        </div>
      </div>
      <div class="identity-step-actions"><a-button type="primary" :loading="preflighting" :disabled="!canPreflight" @click="runPreflight">运行预检</a-button></div>
    </section>
  </div>

  <section v-if="preflight" class="panel-card preflight-panel">
    <div class="panel-title"><strong>3. 预检报告</strong><span>{{ productDateTime(preflight.createdAt) }}</span></div>
    <div class="preflight-summary"><a-tag v-for="(count, outcome) in preflight.summary" :key="outcome" :color="resultColor(outcome)">{{ resultLabel(outcome) }} {{ count }}</a-tag></div>
    <a-table :data-source="preflight.items" :pagination="false" row-key="rowNumber" size="small" :scroll="{ x: 1100 }">
      <a-table-column title="外部身份" data-index="externalUserId" fixed="left" :width="150" />
      <a-table-column title="外部部门" data-index="externalDepartment" :width="120" />
      <a-table-column title="目标部门" data-index="departmentName" :width="120" />
      <a-table-column title="姓名" data-index="displayName" :width="110" />
      <a-table-column title="匹配情况" :width="110"><template #default="{ record }">{{ record.systemMemberId ? '已有成员' : record.accountId ? '已有账号' : '未匹配' }}</template></a-table-column>
      <a-table-column title="结果" :width="130"><template #default="{ record }"><a-tag :color="resultColor(record.outcome)">{{ resultLabel(record.outcome) }}</a-tag></template></a-table-column>
      <a-table-column title="确认动作" :width="190"><template #default="{ record }">{{ actionText(record.plannedAction) }}</template></a-table-column>
      <a-table-column title="原因"><template #default="{ record }">{{ record.reasons.join('；') || '—' }}</template></a-table-column>
    </a-table>
    <div class="identity-step-actions"><a-button type="primary" :loading="confirming" @click="confirm"><CheckCircleOutlined />人工确认并同步</a-button></div>
  </section>

  <section class="panel-card identity-jobs">
    <div class="panel-title"><strong>同步任务</strong><span>{{ jobs.length }} 条</span></div>
    <a-empty v-if="!jobs.length" description="尚无同步任务" class="fixed-config-empty" />
    <div v-for="job in jobs" v-else :key="job.id" class="identity-job-row">
      <span><strong>{{ productStatus(job.status).label }}</strong><small>{{ productDateTime(job.createdAt) }} · 已处理 {{ job.progressCurrent }}/{{ job.progressTotal }}</small></span>
      <a-tag :color="job.status === 'SUCCEEDED' ? 'green' : 'orange'">访问申请 {{ job.summary.accessRequests ?? 0 }}</a-tag>
    </div>
  </section>

  <section class="panel-card identity-logs">
    <div class="panel-title"><strong>登录映射日志</strong><a-button size="small" @click="loadLogs"><ReloadOutlined />查询</a-button></div>
    <div class="identity-log-filters">
      <a-input v-model:value="filters.identityProvider" placeholder="身份源" />
      <a-input v-model:value="filters.externalUserId" placeholder="外部用户标识" />
      <a-input v-model:value="filters.mfaLevel" placeholder="验证级别" />
      <a-input v-model:value="filters.device" placeholder="设备" />
      <a-input v-model:value="filters.requestId" placeholder="请求追踪号" />
      <a-input v-model:value="filters.traceId" placeholder="链路追踪号" />
      <a-input v-model:value="filters.failureReason" placeholder="失败原因" />
    </div>
    <a-table :data-source="logs" row-key="id" size="small" :pagination="{ pageSize: 10 }" :scroll="{ x: 1050 }">
      <a-table-column title="时间" :width="180"><template #default="{ record }">{{ productDateTime(record.occurredAt) }}</template></a-table-column>
      <a-table-column title="身份源" data-index="identityProvider" :width="130" />
      <a-table-column title="外部用户" data-index="externalUserId" :width="140" />
      <a-table-column title="MFA / 设备" :width="170"><template #default="{ record }">{{ record.mfaLevel }} · {{ record.device }}</template></a-table-column>
      <a-table-column title="追踪信息" :width="250"><template #default="{ record }"><code>{{ record.requestId }}</code><br /><code>{{ record.traceId }}</code></template></a-table-column>
      <a-table-column title="结果" :width="170"><template #default="{ record }"><a-tag :color="resultColor(record.resultCode)">{{ resultLabel(record.resultCode) }}</a-tag></template></a-table-column>
      <a-table-column title="成员匹配" :width="110"><template #default="{ record }">{{ record.systemMemberId ? '已匹配' : '未匹配' }}</template></a-table-column>
      <a-table-column title="失败原因" data-index="failureReason" />
    </a-table>
  </section>
</template>
