<script setup lang="ts">
import { CheckCircle2, Play, RefreshCw, ScanSearch, ShieldCheck } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { ApiRequestError } from '@/services/api'
import { systemIdentitySyncApi } from '@/services/systemIdentitySync'
import { useSessionStore } from '@/stores/session'
import type {
  ExternalIdentityDepartment,
  ExternalIdentityEmployee,
  IdentitySyncPolicy,
  IdentitySyncSnapshot,
  IdentitySyncTask,
  InheritedIdentityProvider,
} from '@/types/systemIdentitySync'

const route = useRoute()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const providers = ref<InheritedIdentityProvider[]>([])
const policies = ref<IdentitySyncPolicy[]>([])
const selectedPolicyId = ref('')
const snapshots = ref<IdentitySyncSnapshot[]>([])
const tasks = ref<IdentitySyncTask[]>([])
const selectedSnapshot = ref<IdentitySyncSnapshot | null>(null)
const activeTab = ref('policy')
const loading = ref(false)
const mutation = ref('')
const error = ref('')
const notice = ref('')
const approveUnmatched = ref(false)

const form = reactive({
  tenantId: '', providerId: '', domains: '', jitSystemMember: true,
  unmatchedAction: 'REQUIRE_REVIEW' as 'REQUIRE_REVIEW' | 'SKIP',
  scheduleEnabled: false, scheduleIntervalMinutes: 60 as number | null, expectedVersion: null as number | null,
})
const sourceVersion = ref(`directory-${new Date().toISOString().slice(0, 16)}`)
const departmentsJson = ref(JSON.stringify([
  { externalId: 'HQ', name: '集团总部' },
  { externalId: 'OPS', name: '运营中心', parentExternalId: 'HQ' },
], null, 2))
const employeesJson = ref(JSON.stringify([
  { externalUserId: 'ext-user-001', email: 'user@example.com', displayName: '示例员工', employeeNo: 'E001', departmentExternalId: 'OPS' },
], null, 2))

const selectedPolicy = computed(() => policies.value.find(item => item.id === selectedPolicyId.value) ?? null)
const currentTenantId = computed(() => String(session.context?.tenantId ?? session.tenants[0]?.id ?? ''))
const summary = computed(() => ({
  providers: providers.value.length,
  active: policies.value.filter(item => item.status === 'ACTIVE').length,
  unmatched: selectedSnapshot.value?.unmatchedCount ?? 0,
  failed: tasks.value.reduce((total, item) => total + item.failures.length, 0),
}))

const itemColumns = [
  { title: '类型', dataIndex: 'kind', width: 110 },
  { title: '外部标识 / 名称', key: 'identity', width: 240 },
  { title: '邮箱 / 外部部门', key: 'source', width: 240 },
  { title: '目标成员 / 部门', key: 'target', width: 220 },
  { title: '建议动作', dataIndex: 'proposedAction', width: 120 },
  { title: '问题', dataIndex: 'issueCode', width: 220 },
]
const taskColumns = [
  { title: '任务', dataIndex: 'id', width: 190 },
  { title: '触发', dataIndex: 'trigger', width: 100 },
  { title: '状态', dataIndex: 'status', width: 150 },
  { title: '进度', dataIndex: 'progressPercent', width: 100 },
  { title: '尝试', key: 'attempt', width: 100 },
  { title: '失败项', key: 'failures', width: 320 },
  { title: '创建时间', dataIndex: 'createdAt', width: 180 },
]

function problem(cause: unknown) {
  return cause instanceof ApiRequestError ? `${cause.code}：${cause.message}`
    : cause instanceof Error ? cause.message : '统一认证同步服务暂时不可用'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (!form.tenantId) form.tenantId = currentTenantId.value
    const [providerResult, policyResult] = await Promise.all([
      systemIdentitySyncApi.providers(systemId.value),
      systemIdentitySyncApi.policies(systemId.value, form.tenantId || undefined),
    ])
    providers.value = providerResult
    policies.value = policyResult
    if (!form.providerId && providers.value[0]) form.providerId = providers.value[0].id
    if (!selectedPolicyId.value && policies.value[0]) selectedPolicyId.value = policies.value[0].id
    if (selectedPolicyId.value) await loadPolicyDetail(selectedPolicyId.value)
  } catch (cause) { error.value = problem(cause) }
  finally { loading.value = false }
}

async function loadPolicyDetail(policyId: string) {
  if (!policyId) { snapshots.value = []; tasks.value = []; selectedSnapshot.value = null; return }
  const [snapshotResult, taskResult] = await Promise.all([
    systemIdentitySyncApi.snapshots(systemId.value, policyId),
    systemIdentitySyncApi.tasks(systemId.value, policyId),
  ])
  snapshots.value = snapshotResult
  tasks.value = taskResult
  selectedSnapshot.value = snapshots.value[0] ?? null
}

function editPolicy(policy: IdentitySyncPolicy) {
  selectedPolicyId.value = policy.id
  Object.assign(form, {
    tenantId: policy.tenantId, providerId: policy.providerId,
    domains: policy.allowedDomains.join('\n'), jitSystemMember: policy.jitSystemMember,
    unmatchedAction: policy.unmatchedAction, scheduleEnabled: policy.scheduleEnabled,
    scheduleIntervalMinutes: policy.scheduleIntervalMinutes, expectedVersion: policy.version,
  })
  activeTab.value = 'policy'
}

function newPolicy() {
  selectedPolicyId.value = ''
  Object.assign(form, {
    tenantId: currentTenantId.value, providerId: providers.value[0]?.id ?? '', domains: '',
    jitSystemMember: true, unmatchedAction: 'REQUIRE_REVIEW', scheduleEnabled: false,
    scheduleIntervalMinutes: 60, expectedVersion: null,
  })
  snapshots.value = []
  tasks.value = []
  selectedSnapshot.value = null
}

async function savePolicy() {
  if (!form.tenantId || !form.providerId) { error.value = '请选择租户和已发布身份源'; return }
  mutation.value = 'policy'; error.value = ''; notice.value = ''
  try {
    const saved = await systemIdentitySyncApi.savePolicy(systemId.value, {
      tenantId: form.tenantId, providerId: form.providerId,
      allowedDomains: form.domains.split(/[,\n]/).map(item => item.trim()).filter(Boolean),
      jitSystemMember: form.jitSystemMember, unmatchedAction: form.unmatchedAction,
      scheduleEnabled: form.scheduleEnabled,
      scheduleIntervalMinutes: form.scheduleEnabled ? form.scheduleIntervalMinutes : null,
      expectedVersion: form.expectedVersion,
    })
    selectedPolicyId.value = saved.id
    notice.value = '继承策略草稿已保存；完成差异预检和人工确认后才会生效。'
    await load()
    editPolicy(policies.value.find(item => item.id === saved.id) ?? saved)
    activeTab.value = 'preflight'
  } catch (cause) { error.value = problem(cause) }
  finally { mutation.value = '' }
}

function parseArray<T>(value: string, label: string): T[] {
  const parsed = JSON.parse(value) as unknown
  if (!Array.isArray(parsed)) throw new Error(`${label}必须是 JSON 数组`)
  return parsed as T[]
}

async function preflight() {
  if (!selectedPolicy.value) { error.value = '请先保存并选择继承策略'; return }
  mutation.value = 'preflight'; error.value = ''; notice.value = ''
  try {
    selectedSnapshot.value = await systemIdentitySyncApi.preflight(systemId.value, selectedPolicy.value.id, {
      sourceVersion: sourceVersion.value.trim(), expectedPolicyVersion: selectedPolicy.value.version,
      departments: parseArray<ExternalIdentityDepartment>(departmentsJson.value, '部门快照'),
      employees: parseArray<ExternalIdentityEmployee>(employeesJson.value, '员工快照'),
    })
    approveUnmatched.value = false
    notice.value = `预检完成：匹配 ${selectedSnapshot.value.matchedCount}，新建 ${selectedSnapshot.value.createCount}，未匹配 ${selectedSnapshot.value.unmatchedCount}。`
    await loadPolicyDetail(selectedPolicy.value.id)
    selectedSnapshot.value = snapshots.value.find(item => item.sourceVersion === sourceVersion.value.trim()) ?? snapshots.value[0] ?? null
    activeTab.value = 'diff'
  } catch (cause) { error.value = problem(cause) }
  finally { mutation.value = '' }
}

async function confirmSnapshot() {
  if (!selectedPolicy.value || !selectedSnapshot.value) return
  mutation.value = 'confirm'; error.value = ''; notice.value = ''
  try {
    selectedSnapshot.value = await systemIdentitySyncApi.confirm(
      systemId.value, selectedPolicy.value.id, selectedSnapshot.value.id,
      selectedPolicy.value.version, selectedSnapshot.value.version, approveUnmatched.value,
    )
    notice.value = '差异已由当前系统管理员确认；策略现已生效，可创建同步任务。'
    await load()
    activeTab.value = 'diff'
  } catch (cause) { error.value = problem(cause) }
  finally { mutation.value = '' }
}

async function startSync() {
  if (!selectedPolicy.value || !selectedSnapshot.value) return
  mutation.value = 'start'; error.value = ''; notice.value = ''
  try {
    const task = await systemIdentitySyncApi.start(systemId.value, selectedPolicy.value.id,
      selectedSnapshot.value.id, selectedSnapshot.value.version)
    tasks.value = [task, ...tasks.value]
    notice.value = `同步任务 ${task.id} 已进入持久化队列。`
    activeTab.value = 'tasks'
    await loadPolicyDetail(selectedPolicy.value.id)
  } catch (cause) { error.value = problem(cause) }
  finally { mutation.value = '' }
}

function taskColor(status: string) {
  if (['SUCCEEDED', 'APPLIED'].includes(status)) return 'green'
  if (['FAILED', 'PARTIAL_FAILED'].includes(status)) return 'red'
  if (['RUNNING', 'QUEUED'].includes(status)) return 'blue'
  return 'default'
}

watch(selectedPolicyId, value => { if (value) void loadPolicyDetail(value) })
onMounted(load)
</script>

<template>
  <section class="admin-page identity-sync-page">
    <AdminPageHeader title="统一认证同步" description="继承平台已发布身份源，按租户限制域名；外部组织快照必须经过预检和人工确认才会写入系统成员。">
      <template #actions>
        <a-button :loading="loading" @click="load"><RefreshCw :size="15" />刷新</a-button>
        <a-button type="primary" @click="newPolicy">新建继承策略</a-button>
      </template>
    </AdminPageHeader>

    <a-alert v-if="error" type="error" show-icon closable :message="error" @close="error = ''" />
    <a-alert v-if="notice" type="success" show-icon closable :message="notice" @close="notice = ''" />

    <div class="identity-summary" aria-label="统一认证同步摘要">
      <article><span>可继承身份源</span><strong>{{ summary.providers }}</strong><small>仅平台已发布版本</small></article>
      <article><span>已生效策略</span><strong>{{ summary.active }}</strong><small>系统 / 租户隔离</small></article>
      <article :class="{ warn: summary.unmatched }"><span>当前未匹配</span><strong>{{ summary.unmatched }}</strong><small>需人工确认后跳过</small></article>
      <article :class="{ danger: summary.failed }"><span>任务失败项</span><strong>{{ summary.failed }}</strong><small>保留原映射并可追踪</small></article>
    </div>

    <div class="identity-workspace">
      <aside class="policy-list">
        <header><strong>继承策略</strong><span>{{ policies.length }}</span></header>
        <button v-for="policy in policies" :key="policy.id" type="button"
          :class="{ active: policy.id === selectedPolicyId }" @click="editPolicy(policy)">
          <span><strong>{{ policy.providerName }}</strong><small>{{ policy.providerCode }}</small></span>
          <a-tag :color="policy.status === 'ACTIVE' ? 'green' : 'orange'">{{ policy.status }}</a-tag>
          <small>租户 {{ policy.tenantId }} · v{{ policy.version }}</small>
        </button>
        <a-empty v-if="!policies.length && !loading" description="尚未配置继承策略" />
      </aside>

      <main class="identity-content">
        <a-tabs v-model:active-key="activeTab">
          <a-tab-pane key="policy" tab="1. 继承范围">
            <div class="form-grid">
              <label><span>租户</span><a-select v-model:value="form.tenantId" :options="session.tenants.map(item => ({ value: item.id, label: item.name }))" /></label>
              <label><span>平台已发布身份源</span><a-select v-model:value="form.providerId" :options="providers.map(item => ({ value: item.id, label: `${item.name} / ${item.protocol}` }))" /></label>
              <label class="wide"><span>租户域名限制（每行一个，且必须已验证）</span><a-textarea v-model:value="form.domains" :rows="3" placeholder="example.com" /></label>
              <label><span>未匹配处理</span><a-select v-model:value="form.unmatchedAction" :options="[{ value: 'REQUIRE_REVIEW', label: '必须人工复核' }, { value: 'SKIP', label: '预检后跳过' }]" /></label>
              <label class="switch"><span>允许确认后创建系统成员</span><a-switch v-model:checked="form.jitSystemMember" /></label>
              <label class="switch"><span>启用计划同步</span><a-switch v-model:checked="form.scheduleEnabled" /></label>
              <label v-if="form.scheduleEnabled"><span>同步间隔（分钟）</span><a-input-number v-model:value="form.scheduleIntervalMinutes" :min="15" :max="10080" /></label>
            </div>
            <div class="pane-actions"><a-button class="identity-policy-save" type="primary" :loading="mutation === 'policy'" @click="savePolicy"><ShieldCheck :size="15" />保存策略草稿</a-button></div>
          </a-tab-pane>

          <a-tab-pane key="preflight" tab="2. 外部快照预检" :disabled="!selectedPolicyId">
            <div class="form-grid">
              <label class="wide"><span>外部源版本 / 变更游标</span><a-input v-model:value="sourceVersion" /></label>
              <label class="wide"><span>部门快照 JSON</span><a-textarea v-model:value="departmentsJson" class="code-input" :rows="8" /></label>
              <label class="wide"><span>员工与 systemMember 映射快照 JSON</span><a-textarea v-model:value="employeesJson" class="code-input" :rows="9" /></label>
            </div>
            <div class="pane-actions"><a-button class="identity-preflight-run" type="primary" :loading="mutation === 'preflight'" @click="preflight"><ScanSearch :size="15" />生成差异预检</a-button></div>
          </a-tab-pane>

          <a-tab-pane key="diff" tab="3. 差异确认" :disabled="!selectedSnapshot">
            <template v-if="selectedSnapshot">
              <div class="snapshot-head">
                <div><strong>{{ selectedSnapshot.sourceVersion }}</strong><span>快照 {{ selectedSnapshot.id }} · v{{ selectedSnapshot.version }}</span></div>
                <a-tag :color="selectedSnapshot.unmatchedCount ? 'orange' : 'green'">{{ selectedSnapshot.status }}</a-tag>
              </div>
              <a-table :columns="itemColumns" :data-source="selectedSnapshot.items" row-key="id" :pagination="{ pageSize: 20 }" :scroll="{ x: 1160 }">
                <template #bodyCell="{ column, record }">
                  <template v-if="column.key === 'identity'"><strong>{{ record.displayName }}</strong><span class="cell-secondary">{{ record.externalId }}</span></template>
                  <template v-else-if="column.key === 'source'"><span>{{ record.email || '—' }}</span><span class="cell-secondary">{{ record.departmentExternalId || record.parentExternalId || '—' }}</span></template>
                  <template v-else-if="column.key === 'target'"><span>成员 {{ record.targetMemberId || '待创建' }}</span><span class="cell-secondary">部门 {{ record.targetDepartmentId || '待创建 / 映射' }}</span></template>
                  <template v-else-if="column.dataIndex === 'proposedAction'"><a-tag :color="record.proposedAction === 'UNMATCHED' ? 'orange' : record.proposedAction === 'CREATE' ? 'blue' : 'green'">{{ record.proposedAction }}</a-tag></template>
                  <template v-else-if="column.dataIndex === 'issueCode'"><span :class="{ 'issue-text': record.issueCode }">{{ record.issueCode || '—' }}</span></template>
                </template>
              </a-table>
              <div class="confirm-bar">
                <a-checkbox v-if="selectedSnapshot.unmatchedCount" v-model:checked="approveUnmatched">我已复核未匹配项，同意本次跳过且不自动授权</a-checkbox>
                <span v-else><CheckCircle2 :size="15" />全部项目均可安全应用</span>
                <a-button v-if="selectedSnapshot.status === 'DRAFT'" class="identity-confirm" type="primary" :disabled="selectedSnapshot.unmatchedCount > 0 && !approveUnmatched" :loading="mutation === 'confirm'" @click="confirmSnapshot">人工确认差异</a-button>
                <a-button v-else-if="selectedSnapshot.status === 'CONFIRMED'" class="identity-start" type="primary" :loading="mutation === 'start'" @click="startSync"><Play :size="15" />创建手动同步任务</a-button>
              </div>
            </template>
          </a-tab-pane>

          <a-tab-pane key="tasks" tab="4. 任务与失败项" :disabled="!selectedPolicyId">
            <div class="pane-actions top"><a-button @click="selectedPolicyId && loadPolicyDetail(selectedPolicyId)"><RefreshCw :size="15" />刷新任务</a-button></div>
            <a-table :columns="taskColumns" :data-source="tasks" row-key="id" :pagination="false" :scroll="{ x: 1080 }">
              <template #bodyCell="{ column, record }">
                <template v-if="column.dataIndex === 'status'"><a-tag :color="taskColor(record.status)">{{ record.status }}</a-tag></template>
                <template v-else-if="column.key === 'attempt'">{{ record.attemptCount }} / {{ record.maxAttempts }}</template>
                <template v-else-if="column.key === 'failures'">
                  <div v-if="record.failures.length" class="failure-list"><span v-for="failure in record.failures" :key="failure.itemId"><strong>{{ failure.externalId }}</strong> {{ failure.failureCode }}</span></div>
                  <span v-else>—</span>
                </template>
              </template>
            </a-table>
          </a-tab-pane>
        </a-tabs>
      </main>
    </div>
  </section>
</template>

<style scoped>
.identity-sync-page{display:grid;gap:14px}.identity-summary{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.identity-summary article{display:grid;gap:4px;padding:15px 17px;border:1px solid #dce5e8;border-radius:10px;background:#fff}.identity-summary span,.identity-summary small{color:#6c7c83}.identity-summary strong{font-size:26px;color:#17373b}.identity-summary article.warn{border-color:#ead49f;background:#fffaf0}.identity-summary article.danger{border-color:#efc7c7;background:#fff7f7}.identity-workspace{display:grid;grid-template-columns:260px minmax(0,1fr);min-height:630px;border:1px solid #dce5e8;border-radius:11px;background:#fff;overflow:hidden}.policy-list{padding:14px;border-right:1px solid #e2e9eb;background:#f8fafb}.policy-list header{display:flex;align-items:center;justify-content:space-between;margin-bottom:10px}.policy-list>button{display:grid;width:100%;gap:6px;margin-bottom:7px;padding:11px;border:1px solid transparent;border-radius:8px;background:transparent;text-align:left}.policy-list>button>span:first-child{display:grid}.policy-list>button>.ant-tag{position:absolute;justify-self:end}.policy-list>button small{color:#718087}.policy-list>button.active{border-color:#9dd5cc;background:#eaf7f4}.identity-content{min-width:0;padding:6px 18px 18px}.form-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:14px;padding:8px 0}.form-grid label{display:grid;gap:6px;color:#40545a}.form-grid label.wide{grid-column:1/-1}.form-grid label.switch{display:flex;align-items:center;justify-content:space-between;padding:10px;border:1px solid #e2e8ea;border-radius:7px}.code-input :deep(textarea){font-family:ui-monospace,SFMono-Regular,Consolas,monospace}.pane-actions{display:flex;justify-content:flex-end;margin-top:14px}.pane-actions.top{margin:0 0 12px}.snapshot-head,.confirm-bar{display:flex;align-items:center;justify-content:space-between;gap:12px;margin-bottom:12px}.snapshot-head>div{display:grid}.snapshot-head span{color:#718087}.cell-secondary{display:block;color:#74848a;font-size:12px}.issue-text{color:#b54a3e}.confirm-bar{margin:14px 0 0;padding:13px;border:1px solid #dce7e5;border-radius:8px;background:#f7fbfa}.confirm-bar>span{display:flex;align-items:center;gap:6px;color:#28776c}.failure-list{display:grid;gap:3px;color:#a43d34;font-size:12px}.failure-list strong{margin-right:5px}@media(max-width:980px){.identity-summary{grid-template-columns:repeat(2,1fr)}.identity-workspace{grid-template-columns:1fr}.policy-list{border-right:0;border-bottom:1px solid #e2e9eb}.form-grid{grid-template-columns:1fr}.form-grid label.wide{grid-column:auto}}@media(max-width:620px){.identity-summary{grid-template-columns:1fr}.identity-content{padding:4px 10px 12px}.confirm-bar{align-items:stretch;flex-direction:column}}
</style>
