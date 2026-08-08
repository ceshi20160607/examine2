<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { Eye, Pencil, Plus, Power, RefreshCw, Rocket, ShieldCheck } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { useAdminViewport } from '@/composables/useAdminViewport'
import { ApiRequestError } from '@/services/api'
import { enterpriseIdentityAdminApi } from '@/services/enterpriseIdentity'
import type {
  IdentityMfaPolicy,
  IdentityProtocol,
  IdentityProvider,
  IdentityProviderCommand,
  IdentityProviderStatus,
} from '@/types/enterpriseIdentity'

const { isMobile } = useAdminViewport()
const providers = ref<IdentityProvider[]>([])
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const editorOpen = ref(false)
const detailOpen = ref(false)
const selected = ref<IdentityProvider | null>(null)
const mappingText = ref('{}')
const domainsText = ref('')

function emptyForm(): IdentityProviderCommand & { id: string } {
  return {
    id: '', providerCode: '', name: '', protocol: 'OIDC', issuerUri: '', authorizationEndpoint: '',
    tokenEndpoint: '', jwksUri: '', directoryEndpoint: '', clientId: '', secretRef: '', secretVersion: 'v1',
    callbackUri: '', scopes: 'openid profile email', allowedDomains: [], attributeMapping: {},
    jitAccount: true, jitSystemMember: false, systemId: null, tenantId: null,
    mfaPolicy: 'OPTIONAL', expectedVersion: null,
  }
}

const form = reactive(emptyForm())
const isDirectory = computed(() => form.protocol === 'LDAP' || form.protocol === 'AD')
const columns: TableColumnsType = [
  { title: '身份源', key: 'provider', width: 230 },
  { title: '协议', dataIndex: 'protocol', width: 105 },
  { title: '作用域', key: 'scope', width: 180 },
  { title: 'MFA', dataIndex: 'mfaPolicy', width: 100 },
  { title: '预检', key: 'preflight', width: 135 },
  { title: '状态', dataIndex: 'status', width: 105 },
  { title: '操作', key: 'actions', width: 280, fixed: 'right' },
]
const protocols: IdentityProtocol[] = ['OIDC', 'OAUTH2', 'SAML2', 'LDAP', 'AD', 'WECOM', 'DINGTALK']
const statusText: Record<IdentityProviderStatus, string> = { DRAFT: '草稿', PUBLISHED: '已发布', DISABLED: '已停用' }
const mfaText: Record<IdentityMfaPolicy, string> = { DISABLED: '关闭', OPTIONAL: '可选', REQUIRED: '强制' }

function reportError(error: unknown) {
  errorMessage.value = error instanceof ApiRequestError ? error.message : '身份源管理服务暂时不可用'
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    providers.value = await enterpriseIdentityAdminApi.list()
  } catch (error) {
    reportError(error)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, emptyForm())
  mappingText.value = '{}'
  domainsText.value = ''
  editorOpen.value = true
}

function openEdit(provider: IdentityProvider) {
  Object.assign(form, {
    ...provider,
    id: provider.id,
    secretRef: '',
    expectedVersion: provider.version,
  })
  mappingText.value = JSON.stringify(provider.attributeMapping ?? {}, null, 2)
  domainsText.value = provider.allowedDomains.join('\n')
  editorOpen.value = true
}

function openDetail(provider: IdentityProvider) {
  selected.value = provider
  detailOpen.value = true
}

function nullable(value: string | null) {
  const normalized = value?.trim()
  return normalized ? normalized : null
}

function buildCommand(): IdentityProviderCommand | null {
  errorMessage.value = ''
  if (!form.providerCode.trim() || !form.name.trim() || !form.callbackUri.trim()) {
    errorMessage.value = '身份源编码、名称和回调地址不能为空'
    return null
  }
  if (!/^https:\/\//i.test(form.callbackUri) && !/^http:\/\/127\.0\.0\.1(?::\d+)?\//i.test(form.callbackUri)) {
    errorMessage.value = '回调地址必须使用 HTTPS'
    return null
  }
  if (!/^(env|file):\/\/.+/.test(form.secretRef)) {
    errorMessage.value = '凭据必须填写 env:// 或 file:// SecretRef；编辑时也必须重新确认凭据引用'
    return null
  }
  if ((form.systemId && !form.tenantId) || (!form.systemId && form.tenantId)) {
    errorMessage.value = '系统与租户作用域必须同时填写'
    return null
  }
  if (form.jitSystemMember && (!form.jitAccount || !form.systemId)) {
    errorMessage.value = '创建系统成员要求开启 JIT 账号并指定系统租户'
    return null
  }
  let attributeMapping: Record<string, string>
  try {
    const parsed = JSON.parse(mappingText.value || '{}') as unknown
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object'
      || Object.values(parsed).some(value => typeof value !== 'string')) throw new Error('invalid')
    attributeMapping = parsed as Record<string, string>
  } catch {
    errorMessage.value = '属性映射必须是字符串键值 JSON 对象'
    return null
  }
  return {
    providerCode: form.providerCode.trim(), name: form.name.trim(), protocol: form.protocol,
    issuerUri: nullable(form.issuerUri), authorizationEndpoint: nullable(form.authorizationEndpoint),
    tokenEndpoint: nullable(form.tokenEndpoint), jwksUri: nullable(form.jwksUri),
    directoryEndpoint: nullable(form.directoryEndpoint), clientId: nullable(form.clientId),
    secretRef: form.secretRef.trim(), secretVersion: form.secretVersion.trim(),
    callbackUri: form.callbackUri.trim(), scopes: form.scopes.trim(),
    allowedDomains: domainsText.value.split(/[\n,]/).map(value => value.trim().toLowerCase()).filter(Boolean),
    attributeMapping, jitAccount: form.jitAccount, jitSystemMember: form.jitSystemMember,
    systemId: nullable(form.systemId), tenantId: nullable(form.tenantId),
    mfaPolicy: form.mfaPolicy, expectedVersion: form.id ? form.expectedVersion : null,
  }
}

async function save() {
  const command = buildCommand()
  if (!command) return
  saving.value = true
  try {
    if (form.id) await enterpriseIdentityAdminApi.update(form.id, command)
    else await enterpriseIdentityAdminApi.create(command)
    editorOpen.value = false
    message.success(form.id ? '身份源草稿已更新，请重新预检' : '身份源草稿已创建')
    await load()
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

async function preflight(provider: IdentityProvider) {
  saving.value = true
  errorMessage.value = ''
  try {
    const result = await enterpriseIdentityAdminApi.preflight(provider.id, provider.version)
    if (result.successful) message.success(`预检通过：${result.checks.join('、')}`)
    else message.error(`预检失败：${result.failureCode ?? '未知错误'}`)
    await load()
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

async function publish(provider: IdentityProvider) {
  saving.value = true
  errorMessage.value = ''
  try {
    await enterpriseIdentityAdminApi.publish(provider.id, provider.version)
    message.success('身份源已发布')
    await load()
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

async function disable(provider: IdentityProvider) {
  saving.value = true
  errorMessage.value = ''
  try {
    await enterpriseIdentityAdminApi.disable(provider.id, provider.version)
    message.success('身份源已停用')
    await load()
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page identity-provider-page">
    <AdminPageHeader title="企业身份源" description="配置企业登录协议、JIT 映射、SecretRef 凭据和 MFA 发布策略。">
      <template v-if="!isMobile" #actions>
        <a-button :loading="loading" @click="load"><RefreshCw :size="16" />刷新</a-button>
        <a-button type="primary" @click="openCreate"><Plus :size="16" />创建身份源</a-button>
      </template>
    </AdminPageHeader>
    <a-alert v-if="errorMessage" class="admin-alert identity-provider-error" type="error" show-icon :message="errorMessage" />

    <template v-if="isMobile">
      <AdminMobileNotice />
      <div v-if="providers.length" class="mobile-record-list">
        <article v-for="provider in providers" :key="provider.id" class="mobile-record">
          <div class="mobile-record-title"><strong>{{ provider.name }}</strong><a-tag>{{ statusText[provider.status] }}</a-tag></div>
          <dl><dt>协议</dt><dd>{{ provider.protocol }}</dd><dt>MFA</dt><dd>{{ mfaText[provider.mfaPolicy] }}</dd></dl>
        </article>
      </div>
      <a-empty v-else-if="!loading" description="暂无身份源" />
      <a-spin v-else />
    </template>

    <div v-else class="admin-table-region">
      <a-table :columns="columns" :data-source="providers" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 1160 }">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'provider'"><strong>{{ record.name }}</strong><span class="cell-secondary">{{ record.providerCode }}</span></template>
          <template v-else-if="column.key === 'scope'">{{ record.systemId ? `${record.systemId} / ${record.tenantId}` : '平台全局' }}</template>
          <template v-else-if="column.dataIndex === 'mfaPolicy'">{{ mfaText[record.mfaPolicy as IdentityMfaPolicy] }}</template>
          <template v-else-if="column.key === 'preflight'">
            <a-tag :color="record.preflightStatus === 'PASSED' ? 'green' : record.preflightStatus === 'FAILED' ? 'red' : 'default'">{{ record.preflightStatus }}</a-tag>
          </template>
          <template v-else-if="column.dataIndex === 'status'"><a-tag :color="record.status === 'PUBLISHED' ? 'green' : 'default'">{{ statusText[record.status as IdentityProviderStatus] }}</a-tag></template>
          <template v-else-if="column.key === 'actions'">
            <div class="table-actions">
              <a-button type="link" size="small" @click="openDetail(record as IdentityProvider)"><Eye :size="14" />详情</a-button>
              <a-button type="link" size="small" @click="openEdit(record as IdentityProvider)"><Pencil :size="14" />编辑</a-button>
              <a-button v-if="record.status === 'DRAFT'" type="link" size="small" :loading="saving" @click="preflight(record as IdentityProvider)"><ShieldCheck :size="14" />预检</a-button>
              <a-button v-if="record.status === 'DRAFT' && record.preflightStatus === 'PASSED' && record.preflightVersion === record.version" type="link" size="small" :loading="saving" @click="publish(record as IdentityProvider)"><Rocket :size="14" />发布</a-button>
              <a-button v-if="record.status === 'PUBLISHED'" type="link" size="small" danger :loading="saving" @click="disable(record as IdentityProvider)"><Power :size="14" />停用</a-button>
            </div>
          </template>
        </template>
      </a-table>
    </div>

    <a-modal v-model:open="editorOpen" :title="form.id ? '编辑身份源草稿' : '创建身份源'" width="820px">
      <a-form :model="form" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12"><a-form-item label="名称" required><a-input v-model:value="form.name" :maxlength="160" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="编码" required><a-input v-model:value="form.providerCode" :disabled="Boolean(form.id)" :maxlength="64" /></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="协议" required><a-select v-model:value="form.protocol"><a-select-option v-for="protocol in protocols" :key="protocol" :value="protocol">{{ protocol }}</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="MFA 策略" required><a-select v-model:value="form.mfaPolicy"><a-select-option value="DISABLED">关闭</a-select-option><a-select-option value="OPTIONAL">可选</a-select-option><a-select-option value="REQUIRED">强制</a-select-option></a-select></a-form-item></a-col>
          <a-col :span="8"><a-form-item label="凭据版本" required><a-input v-model:value="form.secretVersion" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="SecretRef" required><a-input v-model:value="form.secretRef" class="identity-provider-secret-ref" placeholder="env://IDENTITY_SECRET 或 file:///run/secrets/identity" /></a-form-item></a-col>
          <a-col :span="24"><a-form-item label="回调地址" required><a-input v-model:value="form.callbackUri" placeholder="https://example.com/auth/callback" /></a-form-item></a-col>
          <a-col v-if="isDirectory" :span="24"><a-form-item label="LDAPS 目录地址"><a-input v-model:value="form.directoryEndpoint" placeholder="ldaps://directory.example.com:636/baseDn" /></a-form-item></a-col>
          <template v-else>
            <a-col :span="12"><a-form-item label="Issuer"><a-input v-model:value="form.issuerUri" /></a-form-item></a-col>
            <a-col :span="12"><a-form-item label="Client ID"><a-input v-model:value="form.clientId" /></a-form-item></a-col>
            <a-col :span="12"><a-form-item label="Authorization Endpoint"><a-input v-model:value="form.authorizationEndpoint" /></a-form-item></a-col>
            <a-col :span="12"><a-form-item label="Token Endpoint"><a-input v-model:value="form.tokenEndpoint" /></a-form-item></a-col>
            <a-col :span="12"><a-form-item label="JWKS URI"><a-input v-model:value="form.jwksUri" /></a-form-item></a-col>
            <a-col :span="12"><a-form-item label="Scopes"><a-input v-model:value="form.scopes" /></a-form-item></a-col>
          </template>
          <a-col :span="12"><a-form-item label="系统 ID"><a-input v-model:value="form.systemId" placeholder="留空表示平台全局" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="租户 ID"><a-input v-model:value="form.tenantId" placeholder="与系统 ID 同时填写" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="允许邮箱域名"><a-textarea v-model:value="domainsText" :rows="3" placeholder="example.com，每行一个" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="属性映射 JSON"><a-textarea v-model:value="mappingText" :rows="3" placeholder='{"email":"mail"}' /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="JIT 账号"><a-switch v-model:checked="form.jitAccount" /></a-form-item></a-col>
          <a-col :span="12"><a-form-item label="JIT 系统成员"><a-switch v-model:checked="form.jitSystemMember" /></a-form-item></a-col>
        </a-row>
      </a-form>
      <template #footer><a-button @click="editorOpen = false">取消</a-button><a-button type="primary" :loading="saving" class="identity-provider-save" @click="save">保存草稿</a-button></template>
    </a-modal>

    <a-drawer v-model:open="detailOpen" title="身份源详情" width="620">
      <a-tabs v-if="selected">
        <a-tab-pane key="configuration" tab="配置">
          <a-descriptions bordered :column="1" size="small">
            <a-descriptions-item label="名称 / 编码">{{ selected.name }} / {{ selected.providerCode }}</a-descriptions-item>
            <a-descriptions-item label="协议 / MFA">{{ selected.protocol }} / {{ mfaText[selected.mfaPolicy] }}</a-descriptions-item>
            <a-descriptions-item label="凭据">{{ selected.secretRefMasked }}（{{ selected.secretVersion }}）</a-descriptions-item>
            <a-descriptions-item label="回调">{{ selected.callbackUri }}</a-descriptions-item>
            <a-descriptions-item label="JIT">账号 {{ selected.jitAccount ? '开启' : '关闭' }}；系统成员 {{ selected.jitSystemMember ? '开启' : '关闭' }}</a-descriptions-item>
          </a-descriptions>
        </a-tab-pane>
        <a-tab-pane key="lifecycle" tab="生命周期">
          <a-descriptions bordered :column="1" size="small">
            <a-descriptions-item label="状态">{{ statusText[selected.status] }}</a-descriptions-item>
            <a-descriptions-item label="版本">{{ selected.version }}</a-descriptions-item>
            <a-descriptions-item label="预检">{{ selected.preflightStatus }} {{ selected.preflightFailureCode ?? '' }}</a-descriptions-item>
            <a-descriptions-item label="预检时间">{{ selected.preflightAt ?? '尚未执行' }}</a-descriptions-item>
            <a-descriptions-item label="发布时间">{{ selected.publishedAt ?? '尚未发布' }}</a-descriptions-item>
          </a-descriptions>
        </a-tab-pane>
      </a-tabs>
    </a-drawer>
  </section>
</template>

<style scoped>
.identity-provider-page :deep(.ant-table-cell) { vertical-align: top; }
</style>
