<script setup lang="ts">
import { CopyOutlined, ExclamationCircleOutlined, LinkOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import ProductPage from '../components/ProductPage.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import { userFacingWorkspaceName } from '../presentation'
import { clearSystemSession, platformContext, platformTokens, setSystemSession, systemContext, systemTokens, type CurrentContext, type SessionTokens } from '../session'
import type { SystemAccessRequest, SystemDomain, SystemRoleOption, SystemSettings, SystemTenant } from '../types'

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
const router = useRouter()
interface OwnerCandidate { accountId: number; username: string; displayName: string }
const settings = ref<SystemSettings | null>(null)
const ownerDrawer = ref(false)
const ownerQuery = ref('')
const ownerCandidates = ref<OwnerCandidate[]>([])
const selectedOwnerId = ref<number | null>(null)
const ownerSearching = ref(false)
const ownerTransferring = ref(false)
const tenants = ref<SystemTenant[]>([])
const domains = ref<SystemDomain[]>([])
const accessRequests = ref<SystemAccessRequest[]>([])
const accessRoles = ref<SystemRoleOption[]>([])
const loading = ref(false)
const saving = ref(false)
const switching = ref<number | null>(null)
const deciding = ref<number | null>(null)
const error = ref('')
const success = ref('')
const tenantDrawer = ref(false)
const loginPresentationDrawer = ref(false)
const loginPresentationTenant = ref<SystemTenant | null>(null)
const archiveCandidate = ref<SystemTenant | null>(null)
const domainDrawer = ref(false)
const form = reactive({ name: '', tenantMode: 'SINGLE' as 'SINGLE' | 'MULTI' })
const tenantForm = reactive({ code: '', name: '' })
const loginPresentationForm = reactive({ loginTitle: '', loginSubtitle: '' })
const domainForm = reactive({
  id: null as number | null,
  tenantId: undefined as number | undefined,
  domainType: 'CUSTOM' as 'SUBDOMAIN' | 'CUSTOM',
  host: '',
  basePath: '/',
  tlsRequired: true,
  expectedVersion: null as number | null,
})
const roleSelections = reactive<Record<number, number[]>>({})
const decisionComments = reactive<Record<number, string>>({})
type SettingsTask = 'basic' | 'workspaces' | 'domains' | 'access'

const activeTask = ref<SettingsTask>('basic')
const settingsTasks = computed(() => [
  { key: 'basic' as const, label: '基础信息', description: '系统名称与多租户开关', count: null },
  { key: 'workspaces' as const, label: '工作空间', description: '空间管理', count: tenants.value.length },
  { key: 'domains' as const, label: '访问地址', description: '域名验证与发布', count: domains.value.length },
  { key: 'access' as const, label: '访问审批', description: '处理待加入系统的成员', count: accessRequests.value.length },
])
const activeTaskDefinition = computed(() => settingsTasks.value.find(item => item.key === activeTask.value) ?? {
  key: 'basic' as const,
  label: '基础信息',
  description: '系统名称与多租户开关',
  count: null,
})

function showError(reason: unknown, fallback: string) {
  error.value = reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（追踪号：${reason.traceId}）` : ''}` : fallback
}

async function load() {
  if (!systemTokens.value?.accessToken) return
  loading.value = true
  error.value = ''
  try {
    const [systemSettings, systemTenants, systemDomains, pendingRequests, roles] = await Promise.all([
      api<SystemSettings>('/api/admin/system/settings', {}, systemTokens.value.accessToken),
      api<SystemTenant[]>('/api/admin/system/tenants', {}, systemTokens.value.accessToken),
      api<SystemDomain[]>('/api/admin/system/domains', {}, systemTokens.value.accessToken),
      api<SystemAccessRequest[]>('/api/admin/system/access-requests?status=PENDING', {}, systemTokens.value.accessToken),
      api<SystemRoleOption[]>('/api/admin/system/access-request-roles', {}, systemTokens.value.accessToken),
    ])
    settings.value = systemSettings
    tenants.value = systemTenants
    domains.value = systemDomains
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
      method: 'PUT', body: JSON.stringify({ name: form.name.trim(), tenantMode: form.tenantMode }),
    }, systemTokens.value.accessToken)
    settings.value = result
    if (systemContext.value && systemTokens.value) {
      setSystemSession(systemTokens.value, { ...systemContext.value, systemName: result.name,
        tenantSwitchContext: systemContext.value.tenantSwitchContext ? {
          ...systemContext.value.tenantSwitchContext,
          tenantSwitchable: result.tenantMode === 'MULTI',
          disabledReason: result.tenantMode === 'MULTI' ? undefined : '单租户系统只使用根租户',
        } : undefined })
    }
    success.value = result.tenantMode === 'MULTI'
      ? '多租户已开启；原租户和共享授权按各自原状态恢复生效。'
      : '多租户已关闭；非主租户及共享暂停访问，原配置和数据保留。'
    await load()
  } catch (reason) {
    showError(reason, '系统设置保存失败')
  } finally {
    saving.value = false
  }
}

async function searchOwnerCandidates() {
  if (!systemTokens.value?.accessToken || ownerQuery.value.trim().length < 2) return
  ownerSearching.value = true
  selectedOwnerId.value = null
  error.value = ''
  try {
    ownerCandidates.value = await api<OwnerCandidate[]>(
      `/api/admin/system/settings/owner-candidates?query=${encodeURIComponent(ownerQuery.value.trim())}`,
      {}, systemTokens.value.accessToken)
  } catch (reason) {
    showError(reason, '受让人查询失败')
  } finally {
    ownerSearching.value = false
  }
}

async function transferOwner() {
  if (!systemTokens.value?.accessToken || !settings.value || selectedOwnerId.value === null) return
  ownerTransferring.value = true
  error.value = ''
  try {
    await api<SystemSettings>('/api/admin/system/settings/owner', {
      method: 'PUT',
      body: JSON.stringify({ accountId: selectedOwnerId.value, expectedVersion: settings.value.version }),
    }, systemTokens.value.accessToken)
    clearSystemSession()
    await router.push('/platform')
  } catch (reason) {
    showError(reason, '系统所属人转让失败')
  } finally {
    ownerTransferring.value = false
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
    success.value = '工作空间已创建，创建人已成为该空间管理员。'
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
      method: 'PUT', body: JSON.stringify({ status: tenant.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE',
        expectedVersion: tenant.version }),
    }, systemTokens.value.accessToken)
    success.value = tenant.status === 'ACTIVE' ? '工作空间已停用，已有会话已失效。'
      : tenant.status === 'ARCHIVED' ? '工作空间已恢复，原业务数据仍归属该空间。' : '工作空间已启用。'
    await load()
  } catch (reason) {
    showError(reason, '租户状态修改失败')
  } finally {
    saving.value = false
  }
}

async function archiveTenant() {
  const tenant = archiveCandidate.value
  if (!tenant || !systemTokens.value?.accessToken) return
  saving.value = true
  error.value = ''
  try {
    await api<SystemTenant>(`/api/admin/system/tenants/${tenant.id}/status`, {
      method: 'PUT', body: JSON.stringify({ status: 'ARCHIVED', expectedVersion: tenant.version }),
    }, systemTokens.value.accessToken)
    archiveCandidate.value = null
    success.value = '工作空间已归档；业务数据和配置保留，原会话已失效。'
    await load()
  } catch (reason) {
    showError(reason, '工作空间归档失败')
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
  domainForm.tenantId = domain?.tenantId ?? tenants.value.find(tenant => tenant.main && tenant.status === 'ACTIVE')?.id
  domainForm.domainType = domain?.domainType ?? 'CUSTOM'
  domainForm.host = domain?.host ?? ''
  domainForm.basePath = domain?.basePath ?? '/'
  domainForm.tlsRequired = domain?.tlsRequired ?? true
  domainForm.expectedVersion = domain?.version ?? null
  error.value = ''
  domainDrawer.value = true
}

async function saveDomain() {
  if (!systemTokens.value?.accessToken || !domainForm.tenantId || !domainForm.host.trim() || !domainForm.basePath.trim()) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const saved = await api<SystemDomain>('/api/admin/system/domains', {
      method: 'POST',
      body: JSON.stringify({
        id: domainForm.id,
        tenantId: domainForm.tenantId,
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

function openLoginPresentation(tenant: SystemTenant) {
  loginPresentationTenant.value = tenant
  loginPresentationForm.loginTitle = tenant.loginTitle || ''
  loginPresentationForm.loginSubtitle = tenant.loginSubtitle || ''
  loginPresentationDrawer.value = true
}

async function saveLoginPresentation() {
  const tenant = loginPresentationTenant.value
  if (!tenant || !systemTokens.value?.accessToken) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    await api<SystemTenant>(`/api/admin/system/tenants/${tenant.id}/login-presentation`, {
      method: 'PUT', body: JSON.stringify({ loginTitle: loginPresentationForm.loginTitle.trim() || null,
        loginSubtitle: loginPresentationForm.loginSubtitle.trim() || null, expectedVersion: tenant.version }),
    }, systemTokens.value.accessToken)
    loginPresentationDrawer.value = false
    loginPresentationTenant.value = null
    success.value = '工作空间登录展示已保存，专属登录页将显示最新文案。'
    await load()
  } catch (reason) {
    showError(reason, '登录展示保存失败')
  } finally { saving.value = false }
}

async function copyDomainAccess(domain: SystemDomain) {
  if (domain.status !== 'PUBLISHED') return
  await navigator.clipboard.writeText(`${domain.tlsRequired ? 'https' : 'http'}://${domain.host}${domain.basePath}`)
  success.value = `“${domain.tenantName || '当前工作空间'}”的已发布访问地址已复制。`
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

async function copyTenantAccess(tenant: SystemTenant) {
  if (!settings.value) return
  const path = `/access/id/${settings.value.systemId}/${tenant.id}`
  await navigator.clipboard.writeText(new URL(path, window.location.origin).toString())
  success.value = `“${tenant.name}”的专属登录地址已复制。`
}

onMounted(load)
</script>

<template>
  <ProductPage density="configuration">
    <ProductPageHeader density="configuration" kicker="系统管理 / 系统信息" title="系统信息" description="一次只处理一类系统配置；先选择任务，再完成保存、检查或发布。">
      <template #actions><a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button></template>
    </ProductPageHeader>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-alert v-if="success" type="success" show-icon :message="success" class="section-alert" closable @close="success = ''" />
    <a-spin :spinning="loading">
      <nav class="settings-task-nav" aria-label="系统信息配置任务">
        <button v-for="task in settingsTasks" :key="task.key" type="button" :class="{ active: activeTask === task.key }" :aria-pressed="activeTask === task.key" @click="activeTask = task.key">
          <span><strong>{{ task.label }}</strong><small>{{ task.description }}</small></span>
          <a-badge v-if="task.count !== null" :count="task.count" show-zero :overflow-count="99" />
        </button>
      </nav>
      <div class="settings-task-heading">
        <div><small>当前任务</small><strong>{{ activeTaskDefinition.label }}</strong></div>
        <p>{{ activeTaskDefinition.description }}</p>
      </div>

      <section v-if="activeTask === 'basic'" class="panel-card settings-card">
          <div class="panel-title"><strong>系统基础信息</strong><span>基础设置</span></div>
          <a-form layout="vertical" class="settings-form">
            <a-form-item label="系统名称" required><a-input v-model:value="form.name" :maxlength="200" /></a-form-item>
            <a-form-item label="多租户开关" required>
              <a-radio-group v-model:value="form.tenantMode">
                <a-radio value="SINGLE">关闭，仅主租户</a-radio><a-radio value="MULTI">开启，可选其他租户</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-alert v-if="form.tenantMode !== settings?.tenantMode" type="warning" show-icon class="mode-warning">
              <template #icon><ExclamationCircleOutlined /></template>
              <template #message>{{ form.tenantMode === 'MULTI' ? '开启后恢复原有有效租户及共享授权的访问，也可新建租户。' : '关闭后只显示主租户；其他租户会话失效，共享暂停生效，原状态和数据保留。' }}</template>
            </a-alert>
            <a-button type="primary" :loading="saving" @click="saveSettings">保存设置</a-button>
          </a-form>
          <div v-if="settings?.ownerAccountId === systemContext?.accountId" class="owner-transfer">
            <strong>系统所属人</strong>
            <p>你拥有此系统的全部配置和数据查看权限。转让后这项隐式权限立即移交，当前系统会话结束。</p>
            <a-button @click="ownerDrawer = true">转让系统所属人</a-button>
          </div>
          <a-modal v-model:open="ownerDrawer" title="转让系统所属人" ok-text="确认转让" :ok-button-props="{ disabled: selectedOwnerId === null }" :confirm-loading="ownerTransferring" @ok="transferOwner">
            <p>选择平台有效账号。转让后，对方成为系统当前所属人并可管理全部配置；你将失去所属人的隐式权限。</p>
            <a-input-search v-model:value="ownerQuery" placeholder="输入用户名或姓名，至少 2 个字" :loading="ownerSearching" @search="searchOwnerCandidates" />
            <a-radio-group v-model:value="selectedOwnerId" class="owner-candidates">
              <a-radio v-for="candidate in ownerCandidates" :key="candidate.accountId" :value="candidate.accountId">
                {{ candidate.displayName || candidate.username }}（{{ candidate.username }}）
              </a-radio>
            </a-radio-group>
          </a-modal>
      </section>

      <template v-else-if="activeTask === 'workspaces'">
        <section class="panel-card tenant-card">
          <div class="panel-title"><strong>工作空间</strong><a-button v-if="settings?.tenantMode === 'MULTI'" size="small" @click="tenantDrawer = true"><PlusOutlined />新建工作空间</a-button></div>
          <a-alert v-if="settings?.tenantMode === 'SINGLE'" type="info" show-icon message="当前只有一个工作空间，无需切换。" class="tenant-hint" />
          <div v-for="tenant in tenants" :key="tenant.id" class="tenant-row">
            <div><strong>{{ userFacingWorkspaceName(tenant.name) }}</strong><small>{{ tenant.current ? '当前使用的业务空间' : '可切换的业务空间' }} · 登录页：{{ tenant.loginTitle || `登录${tenant.name}` }}</small></div>
            <div class="tenant-row__state"><a-tag v-if="tenant.main" color="blue">主工作空间</a-tag><a-tag :color="tenant.status === 'ACTIVE' ? 'green' : 'default'">{{ tenant.status === 'ACTIVE' ? '启用' : tenant.status === 'ARCHIVED' ? '已归档' : '停用' }}</a-tag></div>
            <div class="tenant-row__actions">
              <a-button v-if="tenant.status === 'ACTIVE'" size="small" @click="copyTenantAccess(tenant)"><CopyOutlined />复制登录地址</a-button>
              <a-button size="small" @click="openLoginPresentation(tenant)">登录展示</a-button>
              <a-button v-if="settings?.tenantMode === 'MULTI' && tenant.status === 'ACTIVE' && !tenant.current" size="small" :loading="switching === tenant.id" @click="switchTenant(tenant)">切换进入</a-button>
              <a-tag v-if="tenant.current" color="processing">当前</a-tag>
              <a-button v-if="!tenant.main && tenant.status === 'DISABLED'" size="small" danger @click="archiveCandidate = tenant">归档</a-button>
              <a-button v-if="!tenant.main" size="small" :danger="tenant.status === 'ACTIVE'" @click="changeTenantStatus(tenant)">{{ tenant.status === 'ACTIVE' ? '停用' : tenant.status === 'ARCHIVED' ? '恢复' : '启用' }}</a-button>
            </div>
          </div>
        </section>
        <a-modal :open="Boolean(archiveCandidate)" title="归档工作空间" ok-text="确认归档" :confirm-loading="saving" @ok="archiveTenant" @cancel="archiveCandidate = null">
          <p>归档后成员无法进入此工作空间，现有会话立即失效。业务记录、模块配置与成员关系保留在原空间，后续可恢复。</p>
          <p>请先确认该空间已停用，并已处理需要继续访问的共享和应用调用。</p>
        </a-modal>
      </template>

      <section v-else-if="activeTask === 'domains'" class="panel-card domain-card">
        <div class="panel-title"><strong>域名 / 访问地址</strong><a-button size="small" @click="openDomain()"><PlusOutlined />添加地址</a-button></div>
        <a-alert type="info" show-icon message="自定义地址先保存挑战，再由服务端真实访问验证地址读取证明；验证失败不会启用。" class="tenant-hint" />
        <a-empty v-if="!domains.length" description="尚未配置访问地址" />
        <article v-for="domain in domains" :key="domain.id" class="domain-row">
          <div class="domain-row__address">
            <strong><LinkOutlined />{{ domain.tlsRequired ? 'https' : 'http' }}://{{ domain.host }}{{ domain.basePath }}</strong>
            <small>{{ domain.tenantName || '未绑定工作空间' }} · {{ domain.domainType === 'CUSTOM' ? '自定义域名' : '平台子域名' }}</small>
          </div>
          <ProductStatusTag :status="domain.status" />
          <div class="domain-row__proof">
            <code>{{ domain.verificationUrl }}</code>
            <span>内容：<code>{{ domain.verificationToken }}</code></span>
          </div>
          <div class="tenant-row__actions">
            <a-button size="small" @click="copyProof(domain.verificationToken)"><CopyOutlined />复制证明</a-button>
            <a-button v-if="domain.status === 'PUBLISHED'" size="small" @click="copyDomainAccess(domain)"><CopyOutlined />复制访问地址</a-button>
            <a-button size="small" @click="openDomain(domain)">修改</a-button>
            <a-button v-if="domain.status !== 'PUBLISHED'" size="small" :loading="saving" @click="verifyDomain(domain)">验证</a-button>
            <a-button v-if="domain.status === 'VERIFIED'" size="small" :loading="saving" @click="publishDomain(domain)">发布</a-button>
          </div>
        </article>
      </section>

      <section v-else class="panel-card access-review-card">
        <div class="panel-title"><strong>待处理访问申请</strong><span>{{ accessRequests.length }} 项</span></div>
        <a-empty v-if="!accessRequests.length" description="当前工作空间没有待处理申请" />
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
            <a-button :loading="deciding === request.id" @click="decideAccess(request, 'APPROVE')">批准</a-button>
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
        <a-form-item label="所属工作空间" required><a-select v-model:value="domainForm.tenantId" show-search option-filter-prop="label"
          :options="tenants.filter(tenant => tenant.status === 'ACTIVE').map(tenant => ({ value: tenant.id, label: userFacingWorkspaceName(tenant.name) }))" /></a-form-item>
        <a-form-item label="地址类型" required><a-radio-group v-model:value="domainForm.domainType"><a-radio value="SUBDOMAIN">平台子域名</a-radio><a-radio value="CUSTOM">自定义域名</a-radio></a-radio-group></a-form-item>
        <a-form-item label="主机" required extra="只填写域名或主机名，可带端口；不要填写协议和路径。"><a-input v-model:value="domainForm.host" placeholder="portal.example.com" /></a-form-item>
        <a-form-item label="基础路径" required><a-input v-model:value="domainForm.basePath" placeholder="/" /></a-form-item>
        <a-form-item label="传输安全"><a-switch v-model:checked="domainForm.tlsRequired" /><span class="switch-label">强制 HTTPS</span></a-form-item>
        <a-alert type="warning" show-icon message="每次修改都会撤销原验证状态并生成新的验证证明，发布前必须重新验证。" />
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="domainDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveDomain">保存并生成证明</a-button></div></template>
    </a-drawer>
    <a-drawer v-model:open="loginPresentationDrawer" :title="`${loginPresentationTenant?.name || '工作空间'} · 登录展示`" width="520">
      <a-form layout="vertical">
        <a-form-item label="登录标题" extra="留空时显示“登录工作空间名称”。"><a-input v-model:value="loginPresentationForm.loginTitle" :maxlength="200" /></a-form-item>
        <a-form-item label="登录说明" extra="留空时显示系统默认说明。"><a-textarea v-model:value="loginPresentationForm.loginSubtitle" :maxlength="500" :rows="3" /></a-form-item>
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="loginPresentationDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveLoginPresentation">保存登录展示</a-button></div></template>
    </a-drawer>
  </ProductPage>
</template>

<style scoped>
.owner-transfer { margin-top: 24px; padding-top: 20px; border-top: 1px solid var(--border-color, #edf0f2); }
.owner-transfer p { margin: 8px 0 12px; color: #667085; }
.owner-candidates { display: grid; gap: 10px; margin-top: 16px; }
</style>
