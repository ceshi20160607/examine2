<script setup lang="ts">
import { Bot, CheckCircle2, Plus, RefreshCw, Rocket, Save } from 'lucide-vue-next'
import { onMounted, reactive, ref } from 'vue'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { ApiRequestError } from '@/services/api'
import { platformAiAdminApi } from '@/services/platformAi'
import type {
  CreatePlatformAiProviderInput,
  PlatformAiCapability,
  PlatformAiOperation,
  PlatformAiPolicy,
  PlatformAiPolicyCheck,
  PlatformAiProvider,
  SavePlatformAiPolicyInput,
  UpdatePlatformAiProviderInput,
} from '@/types/platformAi'
import { useAdminViewport } from '@/composables/useAdminViewport'

const { isMobile } = useAdminViewport()

const providers = ref<PlatformAiProvider[]>([])
const selectedProvider = ref<PlatformAiProvider | null>(null)
const policy = ref<PlatformAiPolicy | null>(null)
const capability = ref<PlatformAiCapability | null>(null)
const checkResult = ref<PlatformAiPolicyCheck | null>(null)
const loading = ref(false)
const mutation = ref('')
const errorMessage = ref('')
const successMessage = ref('')

const providerForm = reactive({
  id: '', code: '', name: '', baseUrl: '', model: '', secretRef: '',
  timeoutSeconds: 30, enabled: true, expectedVersion: 0,
})

const policyForm = reactive({
  providerId: '',
  allowedOperations: [] as PlatformAiOperation[],
  maxSystems: 50,
  dailyRequestQuota: 1000,
  dailyTokenQuota: 1000000,
  maxConcurrency: 4,
  strictRedaction: true,
  dataResidency: 'PLATFORM_METADATA_ONLY' as const,
  promptVersion: 'platform-v1',
  enabled: false,
})

function requestError(error: unknown, fallback: string) {
  return error instanceof ApiRequestError
    ? error.message || error.code
    : error instanceof Error ? error.message : fallback
}

function applyProvider(value: PlatformAiProvider) {
  selectedProvider.value = value
  Object.assign(providerForm, {
    id: value.id, code: value.code, name: value.name, baseUrl: value.baseUrl,
    model: value.model, secretRef: value.secretRef, timeoutSeconds: value.timeoutSeconds,
    enabled: value.enabled, expectedVersion: value.version,
  })
}

function newProvider() {
  selectedProvider.value = null
  Object.assign(providerForm, {
    id: '', code: '', name: '', baseUrl: '', model: '', secretRef: '',
    timeoutSeconds: 30, enabled: true, expectedVersion: 0,
  })
  errorMessage.value = ''
  successMessage.value = ''
}

function applyPolicy(value: PlatformAiPolicy) {
  policy.value = value
  Object.assign(policyForm, {
    providerId: value.providerId ?? '',
    allowedOperations: [...value.allowedOperations],
    maxSystems: value.maxSystems,
    dailyRequestQuota: value.dailyRequestQuota,
    dailyTokenQuota: value.dailyTokenQuota,
    maxConcurrency: value.maxConcurrency,
    strictRedaction: value.strictRedaction,
    dataResidency: value.dataResidency,
    promptVersion: value.promptVersion,
    enabled: value.enabled,
  })
  checkResult.value = null
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [providerItems, policyValue, capabilityValue] = await Promise.all([
      platformAiAdminApi.providers(), platformAiAdminApi.policy(), platformAiAdminApi.capability(),
    ])
    providers.value = providerItems
    capability.value = capabilityValue
    applyPolicy(policyValue)
    const current = selectedProvider.value
      ? providerItems.find(item => item.id === selectedProvider.value?.id)
      : providerItems[0]
    if (current) applyProvider(current)
    else newProvider()
  } catch (error) {
    errorMessage.value = requestError(error, '平台 Agent 配置加载失败')
  } finally {
    loading.value = false
  }
}

async function saveProvider() {
  errorMessage.value = ''
  successMessage.value = ''
  const input: CreatePlatformAiProviderInput = {
    code: providerForm.code.trim(), name: providerForm.name.trim(), baseUrl: providerForm.baseUrl.trim(),
    model: providerForm.model.trim(), secretRef: providerForm.secretRef.trim(),
    timeoutSeconds: Number(providerForm.timeoutSeconds), enabled: providerForm.enabled,
  }
  if (!input.code || !input.name || !input.baseUrl || !input.model || !input.secretRef) {
    errorMessage.value = 'Provider 编码、名称、Base URL、模型和 SecretRef 均为必填项。'
    return
  }
  if (!/^[A-Za-z][A-Za-z0-9+.-]{1,31}:\/\/\S+$/u.test(input.secretRef)) {
    errorMessage.value = 'SecretRef 必须使用 scheme://reference 格式，不能填写明文密钥。'
    return
  }
  if (!Number.isInteger(input.timeoutSeconds) || input.timeoutSeconds < 1 || input.timeoutSeconds > 30) {
    errorMessage.value = '超时时间必须是 1 到 30 秒的整数。'
    return
  }
  mutation.value = 'provider'
  try {
    const saved = selectedProvider.value
      ? await platformAiAdminApi.updateProvider(selectedProvider.value.id, {
          ...input, expectedVersion: providerForm.expectedVersion,
        } satisfies UpdatePlatformAiProviderInput)
      : await platformAiAdminApi.createProvider(input)
    const index = providers.value.findIndex(item => item.id === saved.id)
    providers.value = index < 0
      ? [saved, ...providers.value]
      : providers.value.map(item => item.id === saved.id ? saved : item)
    applyProvider(saved)
    successMessage.value = '平台 Provider 配置已保存。'
  } catch (error) {
    errorMessage.value = requestError(error, '平台 Provider 保存失败')
  } finally {
    mutation.value = ''
  }
}

function boundedInteger(value: number, minimum: number, maximum: number, label: string) {
  if (Number.isInteger(value) && value >= minimum && value <= maximum) return true
  errorMessage.value = `${label}必须是 ${minimum} 到 ${maximum} 的整数。`
  return false
}

function policyInput(): SavePlatformAiPolicyInput | null {
  const allowedOperations = [...new Set(policyForm.allowedOperations)]
  const requiredOperations: PlatformAiOperation[] = ['AUTHORIZED_SYSTEMS_QUERY', 'SYSTEM_SWITCH_GUIDANCE']
  if (!policyForm.providerId || !policyForm.promptVersion.trim()
      || requiredOperations.some(operation => !allowedOperations.includes(operation))) {
    errorMessage.value = 'Provider、Prompt 版本、授权系统查询和系统切换引导均不能为空。'
    return null
  }
  if (!policyForm.strictRedaction || policyForm.dataResidency !== 'PLATFORM_METADATA_ONLY') {
    errorMessage.value = '平台 Agent 必须保持严格脱敏和仅平台元数据驻留。'
    return null
  }
  if (!boundedInteger(Number(policyForm.maxSystems), 1, 100, '最大系统数')) return null
  if (!boundedInteger(Number(policyForm.dailyRequestQuota), 1, 10000, '每日请求配额')) return null
  if (!boundedInteger(Number(policyForm.dailyTokenQuota), 10000, 10000000, '每日 Token 配额')) return null
  if (!boundedInteger(Number(policyForm.maxConcurrency), 1, 16, '最大并发数')) return null
  return {
    expectedVersion: policy.value?.draftVersion ?? 0,
    providerId: policyForm.providerId,
    allowedOperations,
    maxSystems: Number(policyForm.maxSystems),
    dailyRequestQuota: Number(policyForm.dailyRequestQuota),
    dailyTokenQuota: Number(policyForm.dailyTokenQuota),
    maxConcurrency: Number(policyForm.maxConcurrency),
    strictRedaction: true,
    dataResidency: 'PLATFORM_METADATA_ONLY',
    promptVersion: policyForm.promptVersion.trim(),
    enabled: policyForm.enabled,
  }
}

async function savePolicy() {
  errorMessage.value = ''
  successMessage.value = ''
  const input = policyInput()
  if (!input) return
  mutation.value = 'policy'
  try {
    applyPolicy(await platformAiAdminApi.savePolicy(input))
    successMessage.value = '平台 Agent 策略草稿已保存。'
  } catch (error) {
    errorMessage.value = requestError(error, '平台 Agent 策略保存失败')
  } finally {
    mutation.value = ''
  }
}

async function checkPolicy() {
  errorMessage.value = ''
  successMessage.value = ''
  mutation.value = 'check'
  try {
    checkResult.value = await platformAiAdminApi.checkPolicy()
    if (checkResult.value.status === 'PASSED') successMessage.value = '策略检查通过，可以发布。'
  } catch (error) {
    errorMessage.value = requestError(error, '平台 Agent 策略检查失败')
  } finally {
    mutation.value = ''
  }
}

async function publishPolicy() {
  if (!policy.value) return
  errorMessage.value = ''
  successMessage.value = ''
  mutation.value = 'publish'
  try {
    applyPolicy(await platformAiAdminApi.publishPolicy(policy.value.draftVersion))
    capability.value = await platformAiAdminApi.capability()
    successMessage.value = '平台 Agent 策略已发布。'
  } catch (error) {
    errorMessage.value = requestError(error, '平台 Agent 策略发布失败')
  } finally {
    mutation.value = ''
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page platform-ai-config-page">
    <AdminPageHeader title="平台 Agent 配置" description="配置平台独立 Provider、平台只读查询和需人工确认的个人任务草稿；不授予任何系统业务能力。">
      <template v-if="!isMobile" #actions><a-button aria-label="刷新平台 Agent 配置" :loading="loading" :disabled="loading" @click="load"><RefreshCw :size="16" />刷新</a-button></template>
    </AdminPageHeader>

    <a-alert class="platform-ai-boundary" type="info" show-icon message="平台上下文安全边界" description="只保存 SecretRef；系统能力仅限授权目录和切换引导。个人平台任务必须先生成草稿并显式确认，不能携带模块、字段或业务记录。" />
    <a-alert v-if="errorMessage" class="platform-ai-admin-error" type="error" show-icon role="alert" aria-live="assertive" :message="errorMessage" />
    <a-alert v-if="successMessage" class="platform-ai-admin-success" type="success" show-icon role="status" aria-live="polite" :message="successMessage" />

    <div v-if="isMobile" class="platform-ai-mobile-gate">
      <AdminMobileNotice />
      <a-empty description="平台 Provider、SecretRef 与授权策略需要桌面配置工作区，请在宽度大于 720px 的窗口中管理。" />
    </div>
    <a-spin v-else :spinning="loading" :aria-busy="loading">
      <section class="platform-ai-capability" :class="{ unavailable: !capability?.available }">
        <div><Bot :size="20" /><strong>平台运行能力</strong></div>
        <a-tag :color="capability?.available ? 'green' : 'orange'">{{ capability?.available ? 'AVAILABLE' : 'UNAVAILABLE' }}</a-tag>
        <span>{{ capability?.available ? `策略版本 ${capability.policyVersion}` : capability?.reason || '尚未配置' }}</span>
      </section>

      <div class="platform-ai-admin-layout">
        <section class="platform-ai-provider-list-panel">
          <header><h2>Provider / 模型</h2><a-button class="platform-ai-provider-new" @click="newProvider"><Plus :size="15" />新增</a-button></header>
          <div class="platform-ai-provider-list">
            <button v-for="provider in providers" :key="provider.id" type="button" :class="{ active: selectedProvider?.id === provider.id }" @click="applyProvider(provider)">
              <span><strong>{{ provider.name }}</strong><small>{{ provider.model }}</small></span>
              <a-tag :color="provider.enabled ? 'green' : 'default'">{{ provider.enabled ? '启用' : '停用' }}</a-tag>
            </button>
          </div>
          <a-empty v-if="!providers.length" description="尚未配置平台 Provider" />
        </section>

        <section class="platform-ai-provider-editor">
          <h2>{{ selectedProvider ? '编辑 Provider' : '新增 Provider' }}</h2>
          <div class="platform-ai-form-grid">
            <label v-if="selectedProvider">Provider ID<input v-model="providerForm.id" class="platform-ai-provider-id" disabled /></label>
            <div v-else class="platform-ai-generated-id"><span>Provider ID</span><strong>保存时由服务端生成</strong></div>
            <label>稳定编码<input v-model="providerForm.code" class="platform-ai-provider-code" :disabled="Boolean(selectedProvider)" /></label>
            <label>名称<input v-model="providerForm.name" class="platform-ai-provider-name" /></label>
            <label>Base URL<input v-model="providerForm.baseUrl" class="platform-ai-provider-base-url" placeholder="https://provider.example/v1" /></label>
            <label>模型<input v-model="providerForm.model" class="platform-ai-provider-model" /></label>
            <label>SecretRef<input v-model="providerForm.secretRef" class="platform-ai-provider-secret-ref" placeholder="vault://ai/platform/v1" /></label>
            <label>超时（秒）<input v-model.number="providerForm.timeoutSeconds" class="platform-ai-provider-timeout" type="number" min="1" max="30" /></label>
            <label class="platform-ai-checkbox"><input v-model="providerForm.enabled" type="checkbox" />启用 Provider</label>
          </div>
          <div class="platform-ai-editor-actions"><span>版本 {{ providerForm.expectedVersion }}</span><a-button class="platform-ai-provider-save" type="primary" :loading="mutation === 'provider'" @click="saveProvider"><Save :size="15" />保存 Provider</a-button></div>
        </section>
      </div>

      <section class="platform-ai-policy-editor">
        <header><div><h2>平台 Agent 策略</h2><p>授权查询与切换引导保持只读；个人平台任务只生成自分配草稿，必须由成员显式确认后写入。</p></div><a-tag>{{ policy?.status || 'DRAFT' }} · v{{ policy?.draftVersion ?? 0 }}</a-tag></header>
        <div class="platform-ai-policy-grid">
          <label>Provider<select v-model="policyForm.providerId" class="platform-ai-policy-provider"><option value="">请选择</option><option v-for="provider in providers" :key="provider.id" :value="provider.id">{{ provider.name }} · {{ provider.model }}</option></select></label>
          <label>Prompt 版本<input v-model="policyForm.promptVersion" class="platform-ai-policy-prompt-version" /></label>
          <fieldset class="platform-ai-policy-operations"><legend>允许操作</legend><label><input v-model="policyForm.allowedOperations" type="checkbox" value="AUTHORIZED_SYSTEMS_QUERY" />AUTHORIZED_SYSTEMS_QUERY</label><label><input v-model="policyForm.allowedOperations" type="checkbox" value="SYSTEM_SWITCH_GUIDANCE" />SYSTEM_SWITCH_GUIDANCE</label><label><input v-model="policyForm.allowedOperations" type="checkbox" value="PLATFORM_OPERATIONS_QUERY" />PLATFORM_OPERATIONS_QUERY（只读）</label><label><input v-model="policyForm.allowedOperations" type="checkbox" value="PLATFORM_TASK_DRAFT" />PLATFORM_TASK_DRAFT（确认后创建）</label></fieldset>
          <label>最大系统数（1–100）<input v-model.number="policyForm.maxSystems" class="platform-ai-policy-max-systems" type="number" min="1" max="100" /></label>
          <label>每日请求配额（1–10000）<input v-model.number="policyForm.dailyRequestQuota" class="platform-ai-policy-request-quota" type="number" min="1" max="10000" /></label>
          <label>每日 Token 配额（10000–10000000）<input v-model.number="policyForm.dailyTokenQuota" class="platform-ai-policy-token-quota" type="number" min="10000" max="10000000" /></label>
          <label>最大并发数（1–16）<input v-model.number="policyForm.maxConcurrency" class="platform-ai-policy-concurrency" type="number" min="1" max="16" /></label>
          <label>脱敏策略<input class="platform-ai-policy-redaction" value="STRICT（不可关闭）" disabled /></label>
          <label>数据驻留<input class="platform-ai-policy-residency" value="PLATFORM_METADATA_ONLY" disabled /></label>
        </div>
        <label class="platform-ai-checkbox"><input v-model="policyForm.enabled" class="platform-ai-policy-enabled" type="checkbox" />发布后启用平台 Agent</label>
        <div class="platform-ai-policy-actions">
          <span>已发布：{{ policy?.activeVersionId || '无' }}</span>
          <a-button class="platform-ai-policy-save" :loading="mutation === 'policy'" @click="savePolicy"><Save :size="15" />保存草稿</a-button>
          <a-button class="platform-ai-policy-check" :loading="mutation === 'check'" @click="checkPolicy"><CheckCircle2 :size="15" />检查</a-button>
          <a-button class="platform-ai-policy-publish" type="primary" :loading="mutation === 'publish'" :disabled="checkResult?.status !== 'PASSED'" @click="publishPolicy"><Rocket :size="15" />发布</a-button>
        </div>
        <div v-if="checkResult" class="platform-ai-check-result"><strong>检查结果：{{ checkResult.status }}</strong><ul v-if="checkResult.issues.length"><li v-for="issue in checkResult.issues" :key="`${issue.code}:${issue.path}`">{{ issue.code }} · {{ issue.message }}<span v-if="issue.path">（{{ issue.path }}）</span></li></ul><p v-else>没有阻断问题。</p></div>
      </section>
    </a-spin>
  </section>
</template>

<style scoped>
.platform-ai-boundary,.platform-ai-admin-error,.platform-ai-admin-success{margin-bottom:12px}.platform-ai-mobile-gate{min-width:0;padding:4px 0 28px}.platform-ai-mobile-gate .ant-empty{padding:30px 12px;border:1px solid #dfe6e9;border-radius:8px;background:#fff}.platform-ai-capability{display:flex;align-items:center;gap:10px;margin-bottom:14px;padding:12px 14px;border:1px solid #b8ddd6;border-radius:8px;background:#f1faf8}.platform-ai-capability.unavailable{border-color:#ead6a7;background:#fff9ec}.platform-ai-capability>div{display:flex;align-items:center;gap:7px}.platform-ai-capability>span:last-child{color:#65747b;font-size:12px}.platform-ai-admin-layout{display:grid;grid-template-columns:260px minmax(0,1fr);min-width:0;gap:14px}.platform-ai-provider-list-panel,.platform-ai-provider-editor,.platform-ai-policy-editor{min-width:0;padding:16px;border:1px solid #dfe6e9;border-radius:9px;background:#fff}.platform-ai-provider-list-panel header,.platform-ai-policy-editor>header,.platform-ai-editor-actions,.platform-ai-policy-actions{display:flex;align-items:center;justify-content:space-between;gap:10px}.platform-ai-provider-list-panel h2,.platform-ai-provider-editor h2,.platform-ai-policy-editor h2,.platform-ai-policy-editor p{margin:0}.platform-ai-provider-list{display:grid;gap:7px;margin-top:12px}.platform-ai-provider-list>button{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:10px;border:1px solid #dce5e8;border-radius:7px;background:#fff;text-align:left}.platform-ai-provider-list>button.active{border-color:#278f82;background:#eef8f6}.platform-ai-provider-list>button>span{display:grid;gap:3px}.platform-ai-provider-list small,.platform-ai-editor-actions>span,.platform-ai-policy-editor p,.platform-ai-policy-actions>span{color:#6b7980;font-size:12px}.platform-ai-form-grid,.platform-ai-policy-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:11px;margin-top:13px}.platform-ai-form-grid label,.platform-ai-policy-grid label{display:grid;min-width:0;gap:5px;color:#526169;font-size:12px}.platform-ai-form-grid input,.platform-ai-policy-grid input,.platform-ai-policy-grid select{width:100%;box-sizing:border-box;padding:7px 9px;border:1px solid #cbd5d9;border-radius:6px;background:#fff}.platform-ai-checkbox{display:flex!important;align-items:center;gap:6px}.platform-ai-checkbox input{width:auto}.platform-ai-editor-actions,.platform-ai-policy-actions{justify-content:flex-end;margin-top:14px}.platform-ai-policy-editor{margin-top:14px}.platform-ai-policy-operations{display:flex;align-items:center;gap:14px;margin:0;padding:9px;border:1px solid #d7dfe2;border-radius:6px}.platform-ai-policy-operations legend{padding:0 4px;color:#526169;font-size:12px}.platform-ai-policy-operations label{display:flex;align-items:center;gap:5px}.platform-ai-policy-operations input{width:auto}.platform-ai-generated-id{display:grid;gap:5px;color:#526169;font-size:12px}.platform-ai-generated-id strong{padding:8px 0;color:#68777e}.platform-ai-check-result{margin-top:12px;padding:12px;border-radius:7px;background:#f5f8f8}.platform-ai-check-result p,.platform-ai-check-result ul{margin:7px 0 0}@media(max-width:1100px){.platform-ai-admin-layout{grid-template-columns:220px minmax(0,1fr)}.platform-ai-policy-operations{align-items:flex-start;flex-direction:column}.platform-ai-policy-actions{flex-wrap:wrap}}
</style>
