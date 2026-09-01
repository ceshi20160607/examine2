<script setup lang="ts">
import { AppstoreOutlined, ArrowRightOutlined, PlusOutlined, ReloadOutlined, SendOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import ProductStatusTag from '../components/ProductStatusTag.vue'
import PlatformShell from '../components/PlatformShell.vue'
import { isVerificationArtifactName, userFacingWorkspaceName } from '../presentation'
import { clearSession, platformContext, platformTokens, setSystemSession, systemContext, type CurrentContext, type SessionTokens } from '../session'
import type { SystemAccessRequest, SystemDirectoryItem } from '../types'

interface AccessibleSystem {
  systemId: number
  systemCode: string
  systemName: string
  tenantMode: string
  status: string
  defaultTenantId: number
  defaultTenantName: string
}

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

const router = useRouter()
const systems = ref<AccessibleSystem[]>([])
const directory = ref<SystemDirectoryItem[]>([])
const accessRequests = ref<SystemAccessRequest[]>([])
const loading = ref(false)
const entering = ref<number | null>(null)
const submitting = ref(false)
const creating = ref(false)
const createOpen = ref(false)
const requestTarget = ref<SystemDirectoryItem | null>(null)
const error = ref('')
const success = ref('')
const requestForm = reactive({ reason: '', requestedRole: '' })
const createForm = reactive({ name: '', code: '', tenantMode: 'SINGLE' })
const visibleSystems = computed(() => systems.value.filter((system) => !isVerificationArtifactName(system.systemName)))
const requestableSystems = computed(() => directory.value.filter((system) => !system.accessible && !isVerificationArtifactName(system.systemName)))
const canCreateSystem = computed(() => platformContext.value?.permissions.some((permission) =>
  (permission.resourceType === '*' || permission.resourceType === 'PLATFORM')
  && (permission.resourceCode === '*' || permission.resourceCode === 'SYSTEM')
  && (permission.actionCode === '*' || permission.actionCode === 'CREATE')) ?? false)

async function loadSystems() {
  if (!platformTokens.value?.accessToken) {
    clearSession()
    await router.replace('/login')
    return
  }
  loading.value = true
  error.value = ''
  try {
    const [accessible, allSystems, mine] = await Promise.all([
      api<AccessibleSystem[]>('/api/systems', {}, platformTokens.value.accessToken),
      api<SystemDirectoryItem[]>('/api/systems/directory', {}, platformTokens.value.accessToken),
      api<SystemAccessRequest[]>('/api/systems/access-requests/mine', {}, platformTokens.value.accessToken),
    ])
    systems.value = accessible
    directory.value = allSystems
    accessRequests.value = mine
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '系统列表加载失败'
  } finally {
    loading.value = false
  }
}

function openAccessRequest(system: SystemDirectoryItem) {
  requestTarget.value = system
  requestForm.reason = ''
  requestForm.requestedRole = ''
  error.value = ''
}

function openCreateSystem() {
  createForm.name = ''
  createForm.code = ''
  createForm.tenantMode = 'SINGLE'
  error.value = ''
  createOpen.value = true
}

async function createSystem() {
  if (!platformTokens.value?.accessToken || !createForm.name.trim() || !createForm.code.trim()) return
  creating.value = true
  error.value = ''
  success.value = ''
  try {
    const created = await api<AccessibleSystem>('/api/systems', {
      method: 'POST',
      body: JSON.stringify({
        name: createForm.name.trim(),
        code: createForm.code.trim(),
        tenantMode: createForm.tenantMode,
      }),
    }, platformTokens.value.accessToken)
    createOpen.value = false
    await loadSystems()
    success.value = `系统“${created.systemName}”已创建，首个工作空间为“${userFacingWorkspaceName(created.defaultTenantName)}”。你现在可以进入系统继续配置。`
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '系统创建失败'
  } finally {
    creating.value = false
  }
}

async function submitAccessRequest() {
  if (!platformTokens.value?.accessToken || !requestTarget.value || !requestForm.reason.trim()) return
  submitting.value = true
  error.value = ''
  success.value = ''
  try {
    const result = await api<SystemAccessRequest>(`/api/systems/${requestTarget.value.systemId}/access-requests`, {
      method: 'POST',
      body: JSON.stringify({
        tenantId: requestTarget.value.defaultTenantId,
        reason: requestForm.reason.trim(),
        requestedRole: requestForm.requestedRole.trim() || null,
      }),
    }, platformTokens.value.accessToken)
    requestTarget.value = null
    success.value = result.status === 'PENDING' ? '访问申请已提交，系统管理员处理后可进入。' : '访问申请已更新。'
    await loadSystems()
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '访问申请提交失败'
  } finally {
    submitting.value = false
  }
}

function latestRequest(systemId: number) {
  return accessRequests.value.find((request) => request.systemId === systemId)
}

async function enterSystem(system: AccessibleSystem) {
  if (!platformTokens.value?.accessToken || !platformContext.value) return
  entering.value = system.systemId
  try {
    const entry = await api<EntryResult>(`/api/systems/${system.systemId}/enter`, {
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
    await router.push(`/systems/${system.systemId}`)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '无法进入系统'
  } finally {
    entering.value = null
  }
}

onMounted(loadSystems)
</script>

<template>
  <PlatformShell>
    <ProductPageHeader kicker="平台工作区" title="我的系统" description="选择要处理工作的系统，进入对应的业务空间。">
      <template #actions>
        <a-button :loading="loading" @click="loadSystems"><ReloadOutlined />刷新</a-button>
        <a-button v-if="canCreateSystem" type="primary" @click="openCreateSystem"><PlusOutlined />新建系统</a-button>
      </template>
    </ProductPageHeader>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-alert v-if="success" type="success" show-icon :message="success" class="section-alert" closable @close="success = ''" />
    <a-spin :spinning="loading">
      <div v-if="visibleSystems.length" class="system-list">
        <article v-for="system in visibleSystems" :key="system.systemId" class="system-row" @click="enterSystem(system)">
          <div class="system-row__mark"><AppstoreOutlined /></div>
          <div class="system-row__main"><h2>{{ system.systemName }}</h2><p>独立业务工作区</p></div>
          <div class="system-row__meta"><span>{{ userFacingWorkspaceName(system.defaultTenantName) }}</span><ProductStatusTag :status="system.status" /></div>
          <a-button type="primary" ghost :loading="entering === system.systemId" @click.stop="enterSystem(system)">进入<ArrowRightOutlined /></a-button>
        </article>
      </div>
      <a-empty v-else-if="!loading" description="当前账号还没有可进入的系统" />

      <section v-if="requestableSystems.length" class="access-directory panel-card">
        <div class="panel-title"><strong>申请访问其他系统</strong><span>批准前不会获得任何业务权限</span></div>
        <article v-for="system in requestableSystems" :key="system.systemId" class="access-directory__row">
          <div><strong>{{ system.systemName }}</strong><small>{{ userFacingWorkspaceName(system.defaultTenantName) }}</small></div>
          <div class="access-directory__state">
            <ProductStatusTag v-if="system.accessRequestStatus" :status="system.accessRequestStatus" />
            <a-tag v-else>未申请</a-tag>
            <span v-if="latestRequest(system.systemId)?.decisionComment">{{ latestRequest(system.systemId)?.decisionComment }}</span>
          </div>
          <a-button :disabled="system.accessRequestStatus === 'PENDING'" @click="openAccessRequest(system)">
            <SendOutlined />{{ system.accessRequestStatus === 'REJECTED' ? '重新申请' : '申请访问' }}
          </a-button>
        </article>
      </section>
    </a-spin>

    <a-modal :open="Boolean(requestTarget)" title="申请系统访问" :confirm-loading="submitting" ok-text="提交申请" @ok="submitAccessRequest" @cancel="requestTarget = null">
      <a-alert type="info" show-icon :message="`目标：${requestTarget?.systemName ?? ''} / ${userFacingWorkspaceName(requestTarget?.defaultTenantName)}`" class="section-alert" />
      <a-form layout="vertical">
        <a-form-item label="申请理由" required><a-textarea v-model:value="requestForm.reason" :maxlength="1000" :rows="4" show-count /></a-form-item>
        <a-form-item label="期望角色"><a-input v-model:value="requestForm.requestedRole" :maxlength="100" placeholder="例如：销售成员（最终角色由管理员决定）" /></a-form-item>
      </a-form>
    </a-modal>

    <a-modal :open="createOpen" title="新建系统" :confirm-loading="creating" ok-text="创建系统" @ok="createSystem" @cancel="createOpen = false">
      <a-alert type="info" show-icon message="创建后会同时生成首个工作空间，并授予你该系统的管理权限。" class="section-alert" />
      <a-form layout="vertical">
        <a-form-item label="系统名称" required><a-input v-model:value="createForm.name" :maxlength="200" placeholder="例如：客户经营系统" /></a-form-item>
        <a-form-item label="系统编码" required extra="2–100 位，以字母开头，可使用小写字母、数字、短横线和下划线。">
          <a-input v-model:value="createForm.code" :maxlength="100" placeholder="例如：customer_ops" />
        </a-form-item>
        <a-form-item label="租户模式" required>
          <a-radio-group v-model:value="createForm.tenantMode">
            <a-radio-button value="SINGLE">单组织</a-radio-button>
            <a-radio-button value="MULTI">多组织</a-radio-button>
          </a-radio-group>
        </a-form-item>
      </a-form>
    </a-modal>
  </PlatformShell>
</template>
