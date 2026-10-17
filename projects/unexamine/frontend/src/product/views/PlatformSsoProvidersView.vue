<script setup lang="ts">
import { CheckCircleOutlined, CloudServerOutlined, PlusOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import PlatformAdminShell from '../components/PlatformAdminShell.vue'
import { api, ApiError } from '../api'
import { clearSession, platformTokens } from '../session'
import { versionLabel } from '../presentation'
import type { SsoProviderAdmin, SsoProviderTestReport, SsoProviderVersion } from '../types'

type Protocol = 'OIDC' | 'SAML2' | 'OAUTH2' | 'LDAP' | 'AD' | 'WECHAT' | 'DINGTALK'

const router = useRouter()
const providers = ref<SsoProviderAdmin[]>([])
const selectedProviderId = ref<number | null>(null)
const selectedVersionId = ref<number | null>(null)
const loading = ref(false)
const saving = ref(false)
const testingVersionId = ref<number | null>(null)
const publishingVersionId = ref<number | null>(null)
const disablingProviderId = ref<number | null>(null)
const editorOpen = ref(false)
const creatingVersion = ref(false)
const error = ref('')
const success = ref('')

const protocols: Array<{ value: Protocol; label: string }> = [
  { value: 'OIDC', label: 'OIDC' },
  { value: 'SAML2', label: 'SAML 2.0' },
  { value: 'OAUTH2', label: 'OAuth 2.0' },
  { value: 'LDAP', label: 'LDAP' },
  { value: 'AD', label: 'Active Directory' },
  { value: 'WECHAT', label: '企业微信' },
  { value: 'DINGTALK', label: '钉钉' },
]

const form = reactive({
  code: '', name: '', protocol: 'OIDC' as Protocol, issuer: '', clientId: '', clientSecretRef: '',
  authorizationEndpoint: '', tokenEndpoint: '', userinfoEndpoint: '', jwksUri: '', redirectUri: '', scopes: '',
  ssoEndpoint: '', acsUri: '', certificateRef: '', directoryEndpoint: '', bindDn: '', bindSecretRef: '', searchBase: '', loginAttribute: 'uid',
  healthEndpoint: '', allowedDomains: '', callbackUris: '', externalUserId: 'sub', email: 'email', emailVerified: 'email_verified',
  jitEnabled: false, usernameClaim: 'preferred_username', mfaRequired: false, mfaClaim: 'amr',
})

const selectedProvider = computed(() => providers.value.find((provider) => provider.id === selectedProviderId.value) ?? null)
const selectedVersion = computed(() => selectedProvider.value?.versions.find((version) => version.id === selectedVersionId.value)
  ?? selectedProvider.value?.versions[0] ?? null)
const interactive = computed(() => ['OIDC', 'OAUTH2', 'WECHAT', 'DINGTALK'].includes(form.protocol))
const directory = computed(() => ['LDAP', 'AD'].includes(form.protocol))
watch(() => form.protocol, protocol => {
  if (protocol === 'LDAP' || protocol === 'AD') {
    if (form.externalUserId === 'sub') form.externalUserId = 'uid'
    if (form.email === 'email') form.email = 'mail'
  }
})

function lines(value: string) {
  return value.split(/[\n,]/).map((item) => item.trim()).filter(Boolean)
}

function protocolLabel(value?: string) {
  return protocols.find(item => item.value === value)?.label || '企业身份协议'
}

function generatedCode(name: string) {
  const latin = name.trim().toLowerCase().replace(/[^a-z0-9_-]+/g, '_').replace(/^_+|_+$/g, '')
  return `identity_${latin || Date.now().toString(36)}`.slice(0, 96)
}

function checkLabel(code: string) {
  return ({ ENDPOINT: '连接地址', CONNECTIVITY: '网络连接', CREDENTIAL: '凭证可用性',
    CALLBACK: '回调地址', DOMAIN: '登录域名', ATTRIBUTE_MAPPING: '用户属性映射',
    VERIFIED_EMAIL_MAPPING: '已验证邮箱映射', VERIFIED_EMAIL_MAPPING_INVALID: '已验证邮箱映射', MFA: '多因素验证' } as Record<string, string>)[code] || '配置完整性'
}

function stringConfig(version: SsoProviderVersion, key: string) {
  const value = version.protocolConfig[key]
  return typeof value === 'string' ? value : ''
}

function resetForm() {
  Object.assign(form, {
    code: '', name: '', protocol: 'OIDC', issuer: '', clientId: '', clientSecretRef: '',
    authorizationEndpoint: '', tokenEndpoint: '', userinfoEndpoint: '', jwksUri: '', redirectUri: '', scopes: '',
    ssoEndpoint: '', acsUri: '', certificateRef: '', directoryEndpoint: '', bindDn: '', bindSecretRef: '', searchBase: '', loginAttribute: 'uid',
    healthEndpoint: '', allowedDomains: '', callbackUris: '', externalUserId: 'sub', email: 'email', emailVerified: 'email_verified',
    jitEnabled: false, usernameClaim: 'preferred_username', mfaRequired: false, mfaClaim: 'amr',
  })
}

function openCreate() {
  resetForm()
  creatingVersion.value = false
  editorOpen.value = true
  error.value = ''
}

function openNewVersion() {
  if (!selectedProvider.value || !selectedVersion.value) return
  resetForm()
  const version = selectedVersion.value
  Object.assign(form, {
    code: selectedProvider.value.code,
    name: selectedProvider.value.name,
    protocol: version.protocol as Protocol,
    issuer: version.issuer,
    clientId: version.clientId,
    clientSecretRef: version.clientSecretRef ?? '',
    authorizationEndpoint: stringConfig(version, 'authorizationEndpoint'),
    tokenEndpoint: stringConfig(version, 'tokenEndpoint'),
    userinfoEndpoint: stringConfig(version, 'userinfoEndpoint'),
    jwksUri: stringConfig(version, 'jwksUri'),
    redirectUri: stringConfig(version, 'redirectUri'),
    scopes: Array.isArray(version.protocolConfig.scopes) ? version.protocolConfig.scopes.join(' ') : '',
    ssoEndpoint: stringConfig(version, 'ssoEndpoint'),
    acsUri: stringConfig(version, 'acsUri'),
    certificateRef: stringConfig(version, 'certificateRef'),
    directoryEndpoint: stringConfig(version, 'directoryEndpoint'),
    bindDn: stringConfig(version, 'bindDn'),
    bindSecretRef: stringConfig(version, 'bindSecretRef'),
    searchBase: stringConfig(version, 'searchBase'),
    loginAttribute: stringConfig(version, 'loginAttribute') || 'uid',
    healthEndpoint: stringConfig(version, 'healthEndpoint'),
    allowedDomains: version.allowedDomains.join('\n'),
    callbackUris: version.callbackUris.join('\n'),
    externalUserId: String(version.attributeMapping.externalUserId ?? 'sub'),
    email: String(version.attributeMapping.email ?? 'email'),
    emailVerified: String(version.attributeMapping.emailVerified ?? 'email_verified'),
    jitEnabled: version.jitPolicy.enabled === true,
    usernameClaim: String(version.jitPolicy.username ?? 'preferred_username'),
    mfaRequired: version.mfaPolicy.required === true,
    mfaClaim: String(version.mfaPolicy.claim ?? 'amr'),
  })
  creatingVersion.value = true
  editorOpen.value = true
  error.value = ''
}

function protocolConfig() {
  const config: Record<string, unknown> = {}
  if (interactive.value) {
    Object.assign(config, {
      authorizationEndpoint: form.authorizationEndpoint.trim(), tokenEndpoint: form.tokenEndpoint.trim(),
      userinfoEndpoint: form.userinfoEndpoint.trim(), redirectUri: form.redirectUri.trim(),
    })
    if (form.protocol === 'OIDC') config.jwksUri = form.jwksUri.trim()
    if (form.scopes.trim()) config.scopes = form.scopes.trim().split(/[\s,]+/).filter(Boolean)
  } else if (form.protocol === 'SAML2') {
    Object.assign(config, { ssoEndpoint: form.ssoEndpoint.trim(), acsUri: form.acsUri.trim(), certificateRef: form.certificateRef.trim() })
  } else {
    Object.assign(config, { directoryEndpoint: form.directoryEndpoint.trim(), bindDn: form.bindDn.trim(),
      bindSecretRef: form.bindSecretRef.trim(), searchBase: form.searchBase.trim(), loginAttribute: form.loginAttribute.trim() })
  }
  if (form.healthEndpoint.trim()) config.healthEndpoint = form.healthEndpoint.trim()
  return config
}

function payload() {
  return {
    protocol: form.protocol,
    issuer: form.issuer.trim(),
    clientId: form.clientId.trim(),
    clientSecretRef: interactive.value ? form.clientSecretRef.trim() || null : null,
    protocolConfig: protocolConfig(),
    allowedDomains: lines(form.allowedDomains),
    attributeMapping: { externalUserId: form.externalUserId.trim(), email: form.email.trim(), emailVerified: form.emailVerified.trim() },
    jitPolicy: { enabled: form.jitEnabled, username: form.usernameClaim.trim() },
    mfaPolicy: { required: form.mfaRequired, claim: form.mfaClaim.trim() },
    callbackUris: directory.value ? [] : lines(form.callbackUris),
  }
}

async function loadProviders(preferProviderId?: number, preferVersionId?: number) {
  if (!platformTokens.value?.accessToken) {
    clearSession()
    await router.replace('/login')
    return
  }
  loading.value = true
  try {
    providers.value = await api<SsoProviderAdmin[]>('/api/admin/platform/sso-providers', {}, platformTokens.value.accessToken)
    const provider = providers.value.find((item) => item.id === (preferProviderId ?? selectedProviderId.value)) ?? providers.value[0]
    selectedProviderId.value = provider?.id ?? null
    const version = provider?.versions.find((item) => item.id === (preferVersionId ?? selectedVersionId.value)) ?? provider?.versions[0]
    selectedVersionId.value = version?.id ?? null
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '身份源列表加载失败'
  } finally {
    loading.value = false
  }
}

function selectProvider(provider: SsoProviderAdmin) {
  selectedProviderId.value = provider.id
  selectedVersionId.value = provider.versions[0]?.id ?? null
  error.value = ''
}

async function saveDraft() {
  if (!platformTokens.value?.accessToken) return
  if (!creatingVersion.value && !form.name.trim()) return
  if (!creatingVersion.value && !form.code) form.code = generatedCode(form.name)
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    if (creatingVersion.value && selectedProvider.value) {
      const version = await api<SsoProviderVersion>(`/api/admin/platform/sso-providers/${selectedProvider.value.id}/versions`, {
        method: 'POST', body: JSON.stringify(payload()),
      }, platformTokens.value.accessToken)
      editorOpen.value = false
      await loadProviders(selectedProvider.value.id, version.id)
      success.value = `${versionLabel(version.versionNumber)}已保存为新草稿。`
    } else {
      const provider = await api<SsoProviderAdmin>('/api/admin/platform/sso-providers', {
        method: 'POST', body: JSON.stringify({ code: form.code.trim(), name: form.name.trim(), ...payload() }),
      }, platformTokens.value.accessToken)
      editorOpen.value = false
      await loadProviders(provider.id, provider.versions[0]?.id)
      success.value = `身份源“${provider.name}”草稿已创建。`
    }
  } catch (reason) {
    error.value = reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（requestId: ${reason.traceId}）` : ''}` : '身份源草稿保存失败'
  } finally {
    saving.value = false
  }
}

async function testVersion(version: SsoProviderVersion) {
  if (!platformTokens.value?.accessToken || !selectedProvider.value) return
  testingVersionId.value = version.id
  error.value = ''
  success.value = ''
  try {
    const report = await api<SsoProviderTestReport>(`/api/admin/platform/sso-providers/${selectedProvider.value.id}/versions/${version.id}/test`, { method: 'POST' }, platformTokens.value.accessToken)
    await loadProviders(selectedProvider.value.id, version.id)
    success.value = report.status === 'PASSED' ? `${versionLabel(version.versionNumber)}预检全部通过，可以发布。` : `${versionLabel(version.versionNumber)}预检未通过，请按报告修正后新建版本。`
  } catch (reason) {
    error.value = reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（requestId: ${reason.traceId}）` : ''}` : '身份源预检失败'
  } finally {
    testingVersionId.value = null
  }
}

async function publishVersion(version: SsoProviderVersion) {
  if (!platformTokens.value?.accessToken || !selectedProvider.value) return
  publishingVersionId.value = version.id
  error.value = ''
  success.value = ''
  try {
    const provider = await api<SsoProviderAdmin>(`/api/admin/platform/sso-providers/${selectedProvider.value.id}/versions/${version.id}/publish`, { method: 'POST' }, platformTokens.value.accessToken)
    await loadProviders(provider.id, version.id)
    success.value = `身份源“${provider.name}”${versionLabel(version.versionNumber)}已发布，并已进入登录页。`
  } catch (reason) {
    error.value = reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（requestId: ${reason.traceId}）` : ''}` : '身份源发布失败'
  } finally {
    publishingVersionId.value = null
  }
}

async function disableProvider() {
  if (!platformTokens.value?.accessToken || !selectedProvider.value) return
  const providerId = selectedProvider.value.id
  disablingProviderId.value = providerId
  error.value = ''
  success.value = ''
  try {
    await api<SsoProviderAdmin>(`/api/admin/platform/sso-providers/${providerId}/disable`,
      { method: 'POST' }, platformTokens.value.accessToken)
    await loadProviders(providerId)
    success.value = '身份源已停用，登录入口已撤下；历史版本与审计保留。'
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '身份源停用失败'
  } finally {
    disablingProviderId.value = null
  }
}

function reportChecks(version: SsoProviderVersion) {
  return version.testReport?.checks ?? []
}

onMounted(() => loadProviders())
</script>

<template>
  <PlatformAdminShell>
    <div class="page-heading">
      <div><p class="eyebrow">平台后台 · 统一认证</p><h1>企业身份源</h1><p>配置协议草稿，完成真实连接预检后发布；登录运行态只读取当前发布版本。</p></div>
      <div class="heading-actions"><a-button :loading="loading" @click="loadProviders()"><ReloadOutlined />刷新</a-button><a-button type="primary" @click="openCreate"><PlusOutlined />新建身份源</a-button></div>
    </div>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-alert v-if="success" type="success" show-icon :message="success" class="section-alert" closable @close="success = ''" />

    <a-spin :spinning="loading">
      <div class="identity-layout">
        <section class="panel-card identity-providers">
          <div class="panel-title"><strong>身份源</strong><span>{{ providers.length }} 个</span></div>
          <button v-for="provider in providers" :key="provider.id" type="button" :class="['identity-provider-item', { active: provider.id === selectedProviderId }]" @click="selectProvider(provider)">
            <span><CloudServerOutlined /></span><div><strong>{{ provider.name }}</strong><small>{{ protocolLabel(provider.versions[0]?.protocol) }} · {{ provider.code }}</small></div><a-tag :color="provider.requiresRevalidation ? 'orange' : provider.status === 'PUBLISHED' ? 'green' : 'default'">{{ provider.requiresRevalidation ? '需重新验证' : provider.status === 'PUBLISHED' ? `已发布 · ${versionLabel(provider.publishedVersionNumber)}` : provider.status === 'DISABLED' ? '已停用' : '草稿' }}</a-tag>
          </button>
          <a-empty v-if="!providers.length && !loading" description="还没有企业身份源" />
        </section>

        <section v-if="selectedProvider" class="identity-detail">
          <div class="panel-card identity-summary">
            <div><p class="eyebrow">企业身份源</p><h2>{{ selectedProvider.name }}</h2><p>发布版本不可修改；配置变更通过新草稿版本完成。</p></div>
            <div class="heading-actions">
              <a-popconfirm v-if="selectedProvider.status === 'PUBLISHED'" title="停用后将立即撤下该身份源的登录入口，确定停用？" ok-text="停用" cancel-text="取消" @confirm="disableProvider">
                <a-button danger :loading="disablingProviderId === selectedProvider.id">停用身份源</a-button>
              </a-popconfirm>
              <a-button @click="openNewVersion"><PlusOutlined />新建版本</a-button>
            </div>
          </div>
          <a-alert v-if="selectedProvider.requiresRevalidation" type="warning" show-icon class="section-alert"
            message="当前发布版本尚未经过协议安全规则复核，登录入口已暂停。请新建版本、完成预检并发布。" />
          <a-alert v-if="selectedProvider.status === 'DISABLED'" type="info" show-icon class="section-alert"
            message="身份源已停用，登录入口已撤下；可新建版本并通过预检后重新发布。" />
          <div class="panel-card version-tabs">
            <button v-for="version in selectedProvider.versions" :key="version.id" :class="{ active: version.id === selectedVersion?.id }" @click="selectedVersionId = version.id">
              <strong>{{ versionLabel(version.versionNumber) }}</strong><span>{{ protocolLabel(version.protocol) }}</span><a-tag :color="version.status === 'PUBLISHED' ? 'green' : version.testStatus === 'FAILED' ? 'red' : version.testStatus === 'PASSED' ? 'blue' : 'default'">{{ version.status === 'PUBLISHED' ? '已发布' : version.testStatus === 'PASSED' ? '预检通过' : version.testStatus === 'FAILED' ? '预检失败' : '待预检' }}</a-tag>
            </button>
          </div>
          <div v-if="selectedVersion" class="panel-card version-detail">
            <div class="panel-title"><strong>{{ versionLabel(selectedVersion.versionNumber) }}配置</strong><span>{{ selectedVersion.status === 'PUBLISHED' ? selectedProvider.status === 'DISABLED' ? '已停用' : selectedProvider.requiresRevalidation ? '需重新验证' : '登录页当前使用' : '待发布草稿' }}</span></div>
            <div class="identity-metadata">
              <div><small>协议</small><strong>{{ protocolLabel(selectedVersion.protocol) }}</strong></div><div><small>身份发行方</small><strong>{{ selectedVersion.issuer }}</strong></div>
              <div><small>接入应用</small><strong>{{ selectedVersion.clientId }}</strong></div><div><small>安全凭证</small><strong>{{ selectedVersion.clientSecretRef || selectedVersion.protocolConfig.certificateRef || selectedVersion.protocolConfig.bindSecretRef || '不需要' }}</strong></div>
              <div><small>允许域名</small><strong>{{ selectedVersion.allowedDomains.join('、') || '未限制' }}</strong></div><div><small>回调地址</small><strong>{{ selectedVersion.callbackUris.join('、') || '目录协议无需回调' }}</strong></div>
            </div>
            <div class="version-actions">
              <a-button v-if="selectedVersion.status === 'DRAFT'" :loading="testingVersionId === selectedVersion.id" @click="testVersion(selectedVersion)"><SafetyCertificateOutlined />执行预检</a-button>
              <a-button v-if="selectedVersion.status === 'DRAFT'" type="primary" :disabled="selectedVersion.testStatus !== 'PASSED'" :loading="publishingVersionId === selectedVersion.id" @click="publishVersion(selectedVersion)"><CheckCircleOutlined />发布该版本</a-button>
              <a-tag v-else :color="selectedProvider.status === 'PUBLISHED' && !selectedProvider.requiresRevalidation ? 'green' : 'default'"><CheckCircleOutlined /> {{ selectedProvider.status === 'DISABLED' ? '身份源已停用' : selectedProvider.requiresRevalidation ? '需重新验证' : '已进入登录运行态' }}</a-tag>
            </div>
            <div v-if="selectedVersion.testReport" class="test-report">
              <div class="test-report__heading"><div><strong>预检报告</strong><small>{{ reportChecks(selectedVersion).length }} 项检查</small></div><a-tag :color="selectedVersion.testReport.status === 'PASSED' ? 'green' : 'red'">{{ selectedVersion.testReport.status === 'PASSED' ? '全部通过' : '存在未通过项' }}</a-tag></div>
              <div class="test-checks"><div v-for="check in reportChecks(selectedVersion)" :key="check.code" :class="['test-check', check.status.toLowerCase()]"><CheckCircleOutlined v-if="check.status === 'PASSED'" /><SafetyCertificateOutlined v-else /><div><strong>{{ checkLabel(check.code) }}</strong><span>{{ check.message }}</span></div></div></div>
            </div>
          </div>
        </section>
        <section v-else class="panel-card identity-empty"><SafetyCertificateOutlined /><h2>配置企业统一认证</h2><p>新建身份源后，草稿不会出现在登录页；只有预检全部通过并发布的版本才会开放登录。</p><a-button type="primary" @click="openCreate">新建身份源</a-button></section>
      </div>
    </a-spin>

    <a-modal :open="editorOpen" :title="creatingVersion ? `新建 ${selectedProvider?.name ?? ''} 的版本` : '新建企业身份源'" width="760px" :confirm-loading="saving" ok-text="保存不可变草稿" @ok="saveDraft" @cancel="editorOpen = false">
      <a-alert type="info" show-icon message="页面和接口只保存密钥引用（SecretRef），不会保存或回显密钥明文。" class="section-alert" />
      <a-form layout="vertical">
        <a-form-item v-if="!creatingVersion" label="身份源名称" required><a-input v-model:value="form.name" :maxlength="200" placeholder="例如：集团统一身份" /></a-form-item>
        <div class="form-grid"><a-form-item label="协议" required><a-select v-model:value="form.protocol" :options="protocols" /></a-form-item><a-form-item :label="form.protocol === 'SAML2' ? 'IdP Issuer' : 'Issuer / 目录标识'" required><a-input v-model:value="form.issuer" placeholder="https://id.example.com" /></a-form-item></div>
        <div class="form-grid"><a-form-item :label="form.protocol === 'SAML2' ? 'SP Entity ID' : 'Client ID / 应用标识'" required><a-input v-model:value="form.clientId" /></a-form-item><a-form-item v-if="interactive" label="Client SecretRef" required><a-input v-model:value="form.clientSecretRef" placeholder="property:app.identity.client-secret" /></a-form-item></div>
        <template v-if="interactive"><div class="form-grid"><a-form-item label="授权端点" required><a-input v-model:value="form.authorizationEndpoint" /></a-form-item><a-form-item label="令牌端点" required><a-input v-model:value="form.tokenEndpoint" /></a-form-item></div><div class="form-grid"><a-form-item label="用户信息端点" required><a-input v-model:value="form.userinfoEndpoint" /></a-form-item><a-form-item v-if="form.protocol === 'OIDC'" label="JWKS 地址" required><a-input v-model:value="form.jwksUri" /></a-form-item></div><a-form-item label="协议回调地址" required><a-input v-model:value="form.redirectUri" /></a-form-item><a-form-item label="授权范围" extra="空白时 OIDC 使用 openid profile email，OAuth2 使用 profile email；按身份源要求填写。"><a-input v-model:value="form.scopes" placeholder="profile email" /></a-form-item></template>
        <template v-else-if="form.protocol === 'SAML2'"><div class="form-grid"><a-form-item label="IdP SSO 端点" required><a-input v-model:value="form.ssoEndpoint" /></a-form-item><a-form-item label="ACS 回调地址" required extra="使用当前站点 /api/auth/sso/{身份源编码}/saml/acs；在 IdP 中登记同一地址。"><a-input v-model:value="form.acsUri" /></a-form-item></div><a-form-item label="IdP 签名证书 SecretRef" required extra="引用可解析的 X.509 PEM 证书；运行态只接受该证书签署的完整 Response。"><a-input v-model:value="form.certificateRef" placeholder="property:app.identity.saml-certificate" /></a-form-item></template>
        <template v-else><div class="form-grid"><a-form-item label="目录端点" required extra="LDAP 使用 LDAPS 和 JVM 信任库验证目录证书。"><a-input v-model:value="form.directoryEndpoint" placeholder="ldaps://directory.example.com:636" /></a-form-item><a-form-item label="服务账号 Bind DN" required><a-input v-model:value="form.bindDn" /></a-form-item></div><div class="form-grid"><a-form-item label="搜索基准 DN" required><a-input v-model:value="form.searchBase" placeholder="ou=people,dc=example,dc=com" /></a-form-item><a-form-item label="登录名属性" required><a-input v-model:value="form.loginAttribute" placeholder="uid" /></a-form-item></div><a-form-item label="服务账号密码 SecretRef" required extra="预检会实际连接目录并验证服务账号；用户密码仅在登录时用于 Bind。"><a-input v-model:value="form.bindSecretRef" placeholder="property:app.identity.bind-password" /></a-form-item></template>
        <a-form-item v-if="!directory" label="真实探活地址（可选）" extra="填写后预检会发起真实连接；留空时只验证协议端点完整性。"><a-input v-model:value="form.healthEndpoint" /></a-form-item>
        <div class="form-grid"><a-form-item label="允许登录域名" required><a-textarea v-model:value="form.allowedDomains" :rows="3" placeholder="example.com&#10;subsidiary.example.com" /></a-form-item><a-form-item v-if="!directory" label="允许回调地址" required><a-textarea v-model:value="form.callbackUris" :rows="3" placeholder="每行一个；必须包含协议回调地址" /></a-form-item></div>
        <div class="form-grid"><a-form-item label="外部用户标识属性" required><a-input v-model:value="form.externalUserId" :placeholder="directory ? 'uid' : 'sub'" /></a-form-item><a-form-item label="邮箱属性"><a-input v-model:value="form.email" :placeholder="directory ? 'mail' : 'email'" /></a-form-item></div>
        <a-form-item v-if="interactive || form.protocol === 'SAML2'" label="邮箱已验证属性" extra="启用域名白名单时，签名身份信息必须返回 true 才允许登录。"><a-input v-model:value="form.emailVerified" placeholder="email_verified" /></a-form-item>
        <div class="form-grid"><a-form-item label="JIT 自动建号" extra="当前版本尚未验收；开启后预检会拒绝发布。"><a-switch v-model:checked="form.jitEnabled" /></a-form-item><a-form-item label="JIT 用户名属性"><a-input v-model:value="form.usernameClaim" /></a-form-item></div>
        <div class="form-grid"><a-form-item label="要求 MFA"><a-switch v-model:checked="form.mfaRequired" /></a-form-item><a-form-item label="MFA 声明属性"><a-input v-model:value="form.mfaClaim" /></a-form-item></div>
      </a-form>
    </a-modal>
  </PlatformAdminShell>
</template>
