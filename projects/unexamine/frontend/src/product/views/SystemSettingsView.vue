<script setup lang="ts">
import { CopyOutlined, ExclamationCircleOutlined, LinkOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import { tenantModeLabel, userFacingWorkspaceName } from '../presentation'
import { platformContext, platformTokens, setSystemSession, systemContext, systemTokens, type CurrentContext, type SessionTokens } from '../session'
import type { SystemAccessRequest, SystemDomain, SystemRoleOption, SystemSettings, SystemTenant, TenantModeMigration, TenantModeMigrationPreflight } from '../types'

interface EntryResult {
  systemId: number
  systemCode: string
  systemName: string
  tenantMode: string
  tenantId: number
  tenantName: string
  systemMemberId: number
  tenantMemberId: number
  roleIds: number[]
  permissions: CurrentContext['permissions']
  dataScopes: CurrentContext['dataScopes']
  contextRevision: string
  redrawScopes: string[]
  tenantSwitchContext: CurrentContext['tenantSwitchContext']
  tokens: SessionTokens
}

const emit = defineEmits<{ contextChanged: [] }>()
const settings = ref<SystemSettings | null>(null)
const tenants = ref<SystemTenant[]>([])
const domains = ref<SystemDomain[]>([])
const migrations = ref<TenantModeMigration[]>([])
const migrationPreflight = ref<TenantModeMigrationPreflight | null>(null)
const accessRequests = ref<SystemAccessRequest[]>([])
const accessRoles = ref<SystemRoleOption[]>([])
const loading = ref(false)
const saving = ref(false)
const switching = ref<number | null>(null)
const deciding = ref<number | null>(null)
const error = ref('')
const success = ref('')
const tenantDrawer = ref(false)
const domainDrawer = ref(false)
const migrationConfirm = ref(false)
const migrationComment = ref('')
const form = reactive({ name: '', tenantMode: 'SINGLE' as 'SINGLE' | 'MULTI' })
const tenantForm = reactive({ code: '', name: '' })
const domainForm = reactive({
  id: null as number | null,
  domainType: 'CUSTOM' as 'SUBDOMAIN' | 'CUSTOM',
  host: '',
  basePath: '/',
  tlsRequired: true,
  expectedVersion: null as number | null,
})
const roleSelections = reactive<Record<number, number[]>>({})
const decisionComments = reactive<Record<number, string>>({})
const migrationTarget = computed<'SINGLE' | 'MULTI'>(() => settings.value?.tenantMode === 'MULTI' ? 'SINGLE' : 'MULTI')

const impactLabels: Record<string, string> = {
  tenantCount: '工作空间总数',
  secondaryTenantCount: '其他工作空间',
  nonMainRecordCount: '其他工作空间业务数据',
  activeShareCount: '有效跨工作空间共享',
}

function showError(reason: unknown, fallback: string) {
  error.value = reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（追踪号：${reason.traceId}）` : ''}` : fallback
}

async function load() {
  if (!systemTokens.value?.accessToken) return
  loading.value = true
  error.value = ''
  try {
    const [systemSettings, systemTenants, systemDomains, migrationRows, pendingRequests, roles] = await Promise.all([
      api<SystemSettings>('/api/admin/system/settings', {}, systemTokens.value.accessToken),
      api<SystemTenant[]>('/api/admin/system/tenants', {}, systemTokens.value.accessToken),
      api<SystemDomain[]>('/api/admin/system/domains', {}, systemTokens.value.accessToken),
      api<TenantModeMigration[]>('/api/admin/system/tenant-mode-migrations', {}, systemTokens.value.accessToken),
      api<SystemAccessRequest[]>('/api/admin/system/access-requests?status=PENDING', {}, systemTokens.value.accessToken),
      api<SystemRoleOption[]>('/api/admin/system/access-request-roles', {}, systemTokens.value.accessToken),
    ])
    settings.value = systemSettings
    tenants.value = systemTenants
    domains.value = systemDomains
    migrations.value = migrationRows
    accessRequests.value = pendingRequests
    accessRoles.value = roles
    for (const request of pendingRequests) {
      roleSelections[request.id] ??= []
      decisionComments[request.id] ??= ''
    }
    form.name = systemSettings.name
    form.tenantMode = systemSettings.tenantMode
  } catch (reason) {
    showError(reason, '系统设置加载失败')
  } finally {
    loading.value = false
  }
}

async function runMigrationPreflight() {
  if (!systemTokens.value?.accessToken) return
  saving.value = true
  error.value = ''
  success.value = ''
  migrationConfirm.value = false
  try {
    migrationPreflight.value = await api<TenantModeMigrationPreflight>('/api/admin/system/tenant-mode-migrations/preflight', {
      method: 'POST', body: JSON.stringify({ toMode: migrationTarget.value }),
    }, systemTokens.value.accessToken)
    success.value = migrationPreflight.value.allowed
      ? '发布检查通过，请核对影响范围并二次确认。'
      : '发布检查已完成，必须先处理全部阻断项；当前模式未发生变化。'
  } catch (reason) {
    showError(reason, '租户模式发布检查失败')
  } finally {
    saving.value = false
  }
}

async function requestMigration() {
  if (!systemTokens.value?.accessToken || !migrationPreflight.value?.allowed || !migrationConfirm.value) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const requested = await api<TenantModeMigration>('/api/admin/system/tenant-mode-migrations', {
      method: 'POST', body: JSON.stringify({ toMode: migrationPreflight.value.toMode }),
    }, systemTokens.value.accessToken)
    success.value = `迁移申请 #${requested.id} 已提交，须由具备租户模式迁移权限的管理员审批后执行。`
    migrationPreflight.value = null
    migrationConfirm.value = false
    await load()
  } catch (reason) {
    showError(reason, '租户模式迁移申请失败')
  } finally {
    saving.value = false
  }
}

async function decideMigration(migration: TenantModeMigration, approved: boolean) {
  if (!systemTokens.value?.accessToken) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const result = await api<TenantModeMigration>(`/api/admin/system/tenant-mode-migrations/${migration.id}/decision`, {
      method: 'POST',
      body: JSON.stringify({ approved, comment: migrationComment.value.trim() || null, expectedVersion: migration.version }),
    }, systemTokens.value.accessToken)
    success.value = result.status === 'COMPLETED'
      ? `迁移作业 #${result.jobId} 已完成，系统现在按${result.toMode === 'MULTI' ? '多租户' : '单租户'}模式运行。`
      : result.status === 'REJECTED' ? '迁移申请已拒绝，系统继续按原模式运行。' : `迁移结果：${result.status}，系统继续按原模式运行。`
    migrationComment.value = ''
    await load()
  } catch (reason) {
    await load()
    showError(reason, '租户模式迁移审批或执行失败')
  } finally {
    saving.value = false
  }
}

function parseSnapshot(value?: string): Record<string, unknown> {
  if (!value) return {}
  try { return JSON.parse(value) as Record<string, unknown> } catch { return {} }
}

async function decideAccess(request: SystemAccessRequest, decision: 'APPROVE' | 'REJECT') {
  if (!systemTokens.value?.accessToken) return
  if (decision === 'APPROVE' && !(roleSelections[request.id]?.length)) {
    error.value = '批准申请时至少选择一个初始角色'
    return
  }
  if (decision === 'REJECT' && !decisionComments[request.id]?.trim()) {
    error.value = '拒绝申请时必须填写原因'
    return
  }
  deciding.value = request.id
  error.value = ''
  success.value = ''
  try {
    await api<SystemAccessRequest>(`/api/admin/system/access-requests/${request.id}/decision`, {
      method: 'POST',
      body: JSON.stringify({
        decision,
        roleIds: decision === 'APPROVE' ? roleSelections[request.id] : [],
        comment: decisionComments[request.id]?.trim() || null,
        version: request.version,
      }),
    }, systemTokens.value.accessToken)
    success.value = decision === 'APPROVE'
      ? '申请已批准，申请人现在可以进入当前系统。'
      : '申请已拒绝，原因已反馈给申请人。'
    await load()
  } catch (reason) {
    showError(reason, '访问申请处理失败')
  } finally {
    deciding.value = null
  }
}

async function saveSettings() {
  if (!systemTokens.value?.accessToken || !form.name.trim()) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const result = await api<SystemSettings>('/api/admin/system/settings', {
      method: 'PUT', body: JSON.stringify({ name: form.name.trim(), tenantMode: settings.value?.tenantMode ?? form.tenantMode }),
    }, systemTokens.value.accessToken)
    settings.value = result
    if (systemContext.value && systemTokens.value) {
      setSystemSession(systemTokens.value, { ...systemContext.value, systemName: result.name })
    }
    success.value = result.tenantMode === 'MULTI' ? '系统设置已保存，可以创建并切换租户。' : '系统设置已保存。'
    await load()
  } catch (reason) {
    showError(reason, '系统设置保存失败')
  } finally {
    saving.value = false
  }
}

async function createTenant() {
  if (!systemTokens.value?.accessToken || !tenantForm.code.trim() || !tenantForm.name.trim()) return
  saving.value = true
  error.value = ''
  try {
    await api<SystemTenant>('/api/admin/system/tenants', {
      method: 'POST', body: JSON.stringify({ code: tenantForm.code.trim(), name: tenantForm.name.trim() }),
    }, systemTokens.value.accessToken)
    tenantDrawer.value = false
    tenantForm.code = ''
    tenantForm.name = ''
    success.value = '租户已创建，创建人已成为该租户管理员。'
    await load()
  } catch (reason) {
    showError(reason, '租户创建失败')
  } finally {
    saving.value = false
  }
}

async function changeTenantStatus(tenant: SystemTenant) {
  if (!systemTokens.value?.accessToken) return
  saving.value = true
  error.value = ''
  try {
    await api<SystemTenant>(`/api/admin/system/tenants/${tenant.id}/status`, {
      method: 'PUT', body: JSON.stringify({ status: tenant.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' }),
    }, systemTokens.value.accessToken)
    success.value = tenant.status === 'ACTIVE' ? '租户已停用，已有租户会话已失效。' : '租户已启用。'
    await load()
  } catch (reason) {
    showError(reason, '租户状态修改失败')
  } finally {
    saving.value = false
  }
}

async function switchTenant(tenant: SystemTenant) {
  if (!platformTokens.value?.accessToken || !platformContext.value || !settings.value) return
  switching.value = tenant.id
  error.value = ''
  try {
    const entry = await api<EntryResult>(`/api/systems/${settings.value.systemId}/tenants/${tenant.id}/enter`, {
      method: 'POST',
      body: JSON.stringify({
        previousSystemId: systemContext.value?.systemId ?? null,
        previousTenantId: systemContext.value?.tenantId ?? null,
      }),
    }, platformTokens.value.accessToken)
    setSystemSession(entry.tokens, {
      accountId: platformContext.value.accountId,
      platformId: platformContext.value.platformId,
      systemId: entry.systemId,
      tenantId: entry.tenantId,
      memberId: entry.systemMemberId,
      tenantMemberId: entry.tenantMemberId,
      username: platformContext.value.username,
      displayName: platformContext.value.displayName,
      mfaLevel: platformContext.value.mfaLevel,
      systemName: entry.systemName,
      tenantName: entry.tenantName,
      roleIds: entry.roleIds,
      permissions: entry.permissions,
      dataScopes: entry.dataScopes,
      contextRevision: entry.contextRevision,
      redrawScopes: entry.redrawScopes,
      tenantSwitchContext: entry.tenantSwitchContext,
    })
    success.value = `已切换到${entry.tenantName}`
    emit('contextChanged')
    await load()
  } catch (reason) {
    showError(reason, '租户切换失败')
  } finally {
    switching.value = null
  }
}

function openDomain(domain?: SystemDomain) {
  domainForm.id = domain?.id ?? null
  domainForm.domainType = domain?.domainType ?? 'CUSTOM'
  domainForm.host = domain?.host ?? ''
  domainForm.basePath = domain?.basePath ?? '/'
  domainForm.tlsRequired = domain?.tlsRequired ?? true
  domainForm.expectedVersion = domain?.version ?? null
  error.value = ''
  domainDrawer.value = true
}

async function saveDomain() {
  if (!systemTokens.value?.accessToken || !domainForm.host.trim() || !domainForm.basePath.trim()) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const saved = await api<SystemDomain>('/api/admin/system/domains', {
      method: 'POST',
      body: JSON.stringify({
        id: domainForm.id,
        domainType: domainForm.domainType,
        host: domainForm.host.trim(),
        basePath: domainForm.basePath.trim(),
        tlsRequired: domainForm.tlsRequired,
        expectedVersion: domainForm.expectedVersion,
      }),
    }, systemTokens.value.accessToken)
    domainDrawer.value = false
    success.value = `访问地址已保存为待验证状态，请在目标地址放置证明 ${saved.verificationToken}`
    await load()
  } catch (reason) {
    showError(reason, '访问地址保存失败')
  } finally {
    saving.value = false
  }
}

async function verifyDomain(domain: SystemDomain) {
  if (!systemTokens.value?.accessToken) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const verified = await api<SystemDomain>(`/api/admin/system/domains/${domain.id}/verify`, { method: 'POST' }, systemTokens.value.accessToken)
    success.value = `所有权验证通过：${verified.verificationUrl}`
    await load()
  } catch (reason) {
    await load()
    showError(reason, '访问地址验证失败')
  } finally {
    saving.value = false
  }
}

async function publishDomain(domain: SystemDomain) {
  if (!systemTokens.value?.accessToken) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const published = await api<SystemDomain>(`/api/admin/system/domains/${domain.id}/publish`, { method: 'POST' }, systemTokens.value.accessToken)
    success.value = `访问地址已发布：${published.tlsRequired ? 'https' : 'http'}://${published.host}${published.basePath}`
    await load()
  } catch (reason) {
    showError(reason, '访问地址发布失败')
  } finally {
    saving.value = false
  }
}

async function copyProof(value: string) {
  await navigator.clipboard.writeText(value)
  success.value = '验证证明已复制。'
}

onMounted(load)
</script>

<template>
  <div>
    <ProductPageHeader kicker="系统管理" title="系统与组织" description="维护系统基础信息、组织空间和访问地址。">
      <template #actions><a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button></template>
    </ProductPageHeader>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-alert v-if="success" type="success" show-icon :message="success" class="section-alert" closable @close="success = ''" />
    <a-spin :spinning="loading">
      <div class="settings-grid">
        <section class="panel-card settings-card">
          <div class="panel-title"><strong>系统基础信息</strong><span>基础设置</span></div>
          <a-form layout="vertical" class="settings-form">
            <a-form-item label="系统名称" required><a-input v-model:value="form.name" :maxlength="200" /></a-form-item>
            <a-form-item label="租户模式" required>
              <a-radio-group v-model:value="form.tenantMode" disabled>
                <a-radio value="SINGLE">单租户</a-radio><a-radio value="MULTI">多租户</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-alert type="info" show-icon message="租户模式变更必须通过独立的发布检查、审批和后台迁移任务，不能随基础信息直接修改。" class="mode-warning" />
            <a-alert v-if="form.tenantMode !== settings?.tenantMode" type="warning" show-icon class="mode-warning">
              <template #icon><ExclamationCircleOutlined /></template>
              <template #message>{{ form.tenantMode === 'MULTI' ? '启用后可创建其他租户，原默认租户继续作为主租户。' : '存在其他租户时不能退回单租户。' }}</template>
            </a-alert>
            <a-button type="primary" :loading="saving" @click="saveSettings">保存设置</a-button>
          </a-form>
        </section>
        <section class="panel-card tenant-card">
          <div class="panel-title"><strong>工作空间</strong><a-button v-if="settings?.tenantMode === 'MULTI'" type="primary" size="small" @click="tenantDrawer = true"><PlusOutlined />新建工作空间</a-button></div>
          <a-alert v-if="settings?.tenantMode === 'SINGLE'" type="info" show-icon message="当前只有一个工作空间，无需切换。" class="tenant-hint" />
          <div v-for="tenant in tenants" :key="tenant.id" class="tenant-row">
            <div><strong>{{ userFacingWorkspaceName(tenant.name) }}</strong><small>{{ tenant.current ? '当前使用的业务空间' : '可切换的业务空间' }}</small></div>
            <div class="tenant-row__state"><a-tag v-if="tenant.main" color="blue">主工作空间</a-tag><a-tag :color="tenant.status === 'ACTIVE' ? 'green' : 'default'">{{ tenant.status === 'ACTIVE' ? '启用' : '停用' }}</a-tag></div>
            <div class="tenant-row__actions">
              <a-button v-if="settings?.tenantMode === 'MULTI' && tenant.status === 'ACTIVE' && !tenant.current" size="small" :loading="switching === tenant.id" @click="switchTenant(tenant)">切换进入</a-button>
              <a-tag v-if="tenant.current" color="processing">当前</a-tag>
              <a-button v-if="!tenant.main" size="small" danger="" @click="changeTenantStatus(tenant)">{{ tenant.status === 'ACTIVE' ? '停用' : '启用' }}</a-button>
            </div>
          </div>
        </section>
      </div>
      <section class="panel-card migration-card">
        <div class="panel-title">
          <div><strong>租户模式发布</strong><small>当前：{{ settings?.tenantMode === 'MULTI' ? '多租户' : '单租户' }}</small></div>
          <a-button type="primary" :loading="saving" @click="runMigrationPreflight">发布检查：切换为{{ migrationTarget === 'MULTI' ? '多租户' : '单租户' }}</a-button>
        </div>
        <a-alert type="warning" show-icon message="这是高风险独立流程：先做影响检查，再提交审批；只有专门权限可以批准并执行，失败或阻断时保持原模式。" class="tenant-hint" />
        <div v-if="migrationPreflight" class="migration-preflight">
          <div class="migration-impact">
            <div v-for="(value, key) in migrationPreflight.impact" :key="key"><span>{{ impactLabels[key] ?? key }}</span><strong>{{ value }}</strong></div>
          </div>
          <a-alert v-if="migrationPreflight.allowed" type="success" show-icon message="发布检查通过，没有发现阻断项。" />
          <a-alert v-else type="error" show-icon message="发布检查未通过，系统仍保持原租户模式。">
            <template #description><ul><li v-for="blocker in migrationPreflight.blockers" :key="blocker">{{ blocker }}</li></ul></template>
          </a-alert>
          <ol class="migration-steps"><li v-for="step in migrationPreflight.migrationSteps" :key="step">{{ step }}</li></ol>
          <div v-if="migrationPreflight.allowed" class="migration-confirm">
            <a-checkbox v-model:checked="migrationConfirm">我已核对租户、业务数据、共享与缓存影响，确认提交迁移审批</a-checkbox>
            <a-button type="primary" danger :disabled="!migrationConfirm" :loading="saving" @click="requestMigration">提交迁移审批</a-button>
          </div>
        </div>
        <a-empty v-if="!migrations.length" description="尚无租户模式迁移记录" />
        <article v-for="migration in migrations" :key="migration.id" class="migration-row">
          <div><strong>#{{ migration.id }} · {{ tenantModeLabel(migration.fromMode) }} → {{ tenantModeLabel(migration.toMode) }}</strong><small>申请成员 {{ migration.requestedByMemberId }}<template v-if="migration.jobId"> · 作业 #{{ migration.jobId }}</template></small></div>
          <ProductStatusTag :status="migration.status" />
          <div class="migration-snapshot"><span v-for="(value, key) in (parseSnapshot(migration.impactSnapshotJson).impact as Record<string, unknown> ?? {})" :key="key">{{ impactLabels[key] ?? key }} {{ value }}</span></div>
          <div v-if="migration.status === 'PENDING_APPROVAL'" class="migration-decision">
            <a-input v-model:value="migrationComment" :maxlength="1000" placeholder="审批说明" />
            <a-button :loading="saving" @click="decideMigration(migration, false)">拒绝</a-button>
            <a-popconfirm title="确认立即执行租户模式迁移？" ok-text="确认执行" cancel-text="取消" @confirm="decideMigration(migration, true)"><a-button type="primary" danger :loading="saving">批准并执行</a-button></a-popconfirm>
          </div>
          <a-alert v-if="migration.status === 'BLOCKED' || migration.status === 'FAILED'" type="error" show-icon message="执行未完成，原租户模式继续可用。" />
        </article>
      </section>
      <section class="panel-card domain-card">
        <div class="panel-title"><strong>域名 / 访问地址</strong><a-button type="primary" size="small" @click="openDomain()"><PlusOutlined />添加地址</a-button></div>
        <a-alert type="info" show-icon message="自定义地址先保存挑战，再由服务端真实访问验证地址读取证明；验证失败不会启用。" class="tenant-hint" />
        <a-empty v-if="!domains.length" description="尚未配置访问地址" />
        <article v-for="domain in domains" :key="domain.id" class="domain-row">
          <div class="domain-row__address">
            <strong><LinkOutlined />{{ domain.tlsRequired ? 'https' : 'http' }}://{{ domain.host }}{{ domain.basePath }}</strong>
            <small>{{ domain.domainType === 'CUSTOM' ? '自定义域名' : '平台子域名' }} · v{{ domain.version }}</small>
          </div>
          <ProductStatusTag :status="domain.status" />
          <div class="domain-row__proof">
            <code>{{ domain.verificationUrl }}</code>
            <span>内容：<code>{{ domain.verificationToken }}</code></span>
          </div>
          <div class="tenant-row__actions">
            <a-button size="small" @click="copyProof(domain.verificationToken)"><CopyOutlined />复制证明</a-button>
            <a-button size="small" @click="openDomain(domain)">修改</a-button>
            <a-button v-if="domain.status !== 'PUBLISHED'" size="small" :loading="saving" @click="verifyDomain(domain)">验证</a-button>
            <a-button v-if="domain.status === 'VERIFIED'" type="primary" size="small" :loading="saving" @click="publishDomain(domain)">发布</a-button>
          </div>
        </article>
      </section>
      <section class="panel-card access-review-card">
        <div class="panel-title"><strong>待处理访问申请</strong><span>{{ accessRequests.length }} 项</span></div>
        <a-empty v-if="!accessRequests.length" description="当前租户没有待处理申请" />
        <article v-for="request in accessRequests" :key="request.id" class="access-review-row">
          <div class="access-review-row__identity">
            <strong>{{ request.accountDisplayName }}</strong>
            <small>{{ request.identityProvider }}<template v-if="request.externalUserId"> · {{ request.externalUserId }}</template></small>
            <p>{{ request.requestReason }}</p>
            <a-tag v-if="request.requestedRole">期望：{{ request.requestedRole }}</a-tag>
          </div>
          <a-select v-model:value="roleSelections[request.id]" mode="multiple" :options="accessRoles.map((role) => ({ value: role.id, label: role.name }))" placeholder="选择批准后的初始角色" />
          <a-input v-model:value="decisionComments[request.id]" :maxlength="1000" placeholder="处理说明；拒绝时必填" />
          <div class="access-review-row__actions">
            <a-button danger :loading="deciding === request.id" @click="decideAccess(request, 'REJECT')">拒绝</a-button>
            <a-button type="primary" :loading="deciding === request.id" @click="decideAccess(request, 'APPROVE')">批准</a-button>
          </div>
        </article>
      </section>
    </a-spin>
    <a-drawer v-model:open="tenantDrawer" title="新建工作空间" width="430">
      <a-form layout="vertical">
        <a-form-item label="工作空间名称" required><a-input v-model:value="tenantForm.name" placeholder="例如：华东分公司" /></a-form-item>
        <a-form-item label="工作空间编码" required extra="用于系统集成；小写字母开头，可使用数字、下划线和短横线"><a-input v-model:value="tenantForm.code" placeholder="east_branch" /></a-form-item>
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="tenantDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="createTenant">创建工作空间</a-button></div></template>
    </a-drawer>
    <a-drawer v-model:open="domainDrawer" title="配置域名 / 访问地址" width="520">
      <a-form layout="vertical">
        <a-form-item label="地址类型" required><a-radio-group v-model:value="domainForm.domainType"><a-radio value="SUBDOMAIN">平台子域名</a-radio><a-radio value="CUSTOM">自定义域名</a-radio></a-radio-group></a-form-item>
        <a-form-item label="主机" required extra="只填写域名或主机名，可带端口；不要填写协议和路径。"><a-input v-model:value="domainForm.host" placeholder="portal.example.com" /></a-form-item>
        <a-form-item label="基础路径" required><a-input v-model:value="domainForm.basePath" placeholder="/" /></a-form-item>
        <a-form-item label="传输安全"><a-switch v-model:checked="domainForm.tlsRequired" /><span class="switch-label">强制 HTTPS</span></a-form-item>
        <a-alert type="warning" show-icon message="每次修改都会撤销原验证状态并生成新的验证证明，发布前必须重新验证。" />
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="domainDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveDomain">保存并生成证明</a-button></div></template>
    </a-drawer>
  </div>
</template>
