<script setup lang="ts">
import { Bot, CheckCircle2, Plus, RefreshCw, Rocket, Save } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { ApiRequestError } from '@/services/api'
import { aiAdminApi } from '@/services/ai'
import type {
  AiCapability,
  AiOperation,
  AiPolicy,
  AiPolicyCheck,
  AiProvider,
  AiRedactionMode,
  CreateAiProviderInput,
  SaveAiPolicyInput,
  UpdateAiProviderInput,
} from '@/types/ai'
import { useAdminViewport } from '@/composables/useAdminViewport'

const { isMobile } = useAdminViewport()

const route = useRoute()
const systemId = computed(() => String(route.params.systemId))
const providers = ref<AiProvider[]>([])
const selectedProvider = ref<AiProvider | null>(null)
const policy = ref<AiPolicy | null>(null)
const capability = ref<AiCapability | null>(null)
const checkResult = ref<AiPolicyCheck | null>(null)
const loading = ref(false)
const mutation = ref('')
const errorMessage = ref('')
const successMessage = ref('')

const providerForm = reactive({
  id: '', code: '', name: '', baseUrl: '', model: '', secretRef: '',
  timeoutSeconds: 30, enabled: true, expectedVersion: 0,
})
const policyForm = reactive({
  providerId: '', moduleCodesText: '', outputFieldCodesText: '', writableFieldCodesText: '',
  fillFieldCodesText: '',
  allowedOperations: ['RECORD_QUERY'] as AiOperation[], confirmationMode: 'REQUIRED' as const,
  confirmationExpiresSeconds: 600, maxRows: 20, redactionMode: 'STRICT' as AiRedactionMode,
  promptVersion: 'v1', enabled: false,
})

function requestError(error: unknown, fallback: string) {
  return error instanceof ApiRequestError
    ? error.message || error.code
    : error instanceof Error ? error.message : fallback
}

function lines(value: string) {
  return [...new Set(value.split(/[\n,]/u).map(item => item.trim()).filter(Boolean))]
}

function applyProvider(provider: AiProvider) {
  selectedProvider.value = provider
  Object.assign(providerForm, {
    id: provider.id, code: provider.code, name: provider.name, baseUrl: provider.baseUrl,
    model: provider.model, secretRef: provider.secretRef, timeoutSeconds: provider.timeoutSeconds,
    enabled: provider.enabled, expectedVersion: provider.version,
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

function applyPolicy(value: AiPolicy) {
  policy.value = value
  Object.assign(policyForm, {
    providerId: value.providerId ?? '',
    moduleCodesText: value.moduleCodes.join('\n'),
    outputFieldCodesText: Object.entries(value.outboundFields)
      .flatMap(([moduleCode, fieldCodes]) => fieldCodes.map(fieldCode => `${moduleCode}.${fieldCode}`))
      .join('\n'),
    writableFieldCodesText: Object.entries(value.writableFields)
      .flatMap(([moduleCode, fieldCodes]) => fieldCodes.map(fieldCode => `${moduleCode}.${fieldCode}`))
      .join('\n'),
    fillFieldCodesText: Object.entries(value.fillFields)
      .flatMap(([moduleCode, fieldCodes]) => fieldCodes.map(fieldCode => `${moduleCode}.${fieldCode}`))
      .join('\n'),
    allowedOperations: [...value.allowedOperations],
    confirmationMode: value.confirmationMode,
    confirmationExpiresSeconds: value.confirmationExpiresSeconds,
    maxRows: value.maxRows,
    redactionMode: value.redactionMode,
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
      aiAdminApi.providers(systemId.value),
      aiAdminApi.policy(systemId.value),
      aiAdminApi.capability(systemId.value),
    ])
    providers.value = providerItems
    capability.value = capabilityValue
    applyPolicy(policyValue)
    if (selectedProvider.value) {
      const current = providerItems.find(item => item.id === selectedProvider.value?.id)
      if (current) applyProvider(current)
      else newProvider()
    } else if (providerItems[0]) applyProvider(providerItems[0])
  } catch (error) {
    errorMessage.value = requestError(error, 'Agent 配置加载失败')
  } finally {
    loading.value = false
  }
}

async function saveProvider() {
  errorMessage.value = ''
  successMessage.value = ''
  const input: CreateAiProviderInput = {
    code: providerForm.code.trim(),
    name: providerForm.name.trim(),
    baseUrl: providerForm.baseUrl.trim(),
    model: providerForm.model.trim(),
    secretRef: providerForm.secretRef.trim(),
    timeoutSeconds: Number(providerForm.timeoutSeconds),
    enabled: providerForm.enabled,
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
      ? await aiAdminApi.updateProvider(systemId.value, selectedProvider.value.id, {
          ...input,
          expectedVersion: providerForm.expectedVersion,
        } satisfies UpdateAiProviderInput)
      : await aiAdminApi.createProvider(systemId.value, input)
    const index = providers.value.findIndex(item => item.id === saved.id)
    providers.value = index < 0
      ? [saved, ...providers.value]
      : providers.value.map(item => item.id === saved.id ? saved : item)
    applyProvider(saved)
    successMessage.value = 'Provider 配置已保存。'
  } catch (error) {
    errorMessage.value = requestError(error, 'Provider 保存失败')
  } finally {
    mutation.value = ''
  }
}

function policyInput(): SaveAiPolicyInput | null {
  function groupedFields(source: string, label: string) {
    const grouped: Record<string, string[]> = {}
    for (const value of lines(source)) {
      const separator = value.indexOf('.')
      if (separator < 1 || separator === value.length - 1) {
        errorMessage.value = `${label} ${value} 必须使用 moduleCode.fieldCode 格式。`
        return null
      }
      const moduleCode = value.slice(0, separator)
      const fieldCode = value.slice(separator + 1)
      grouped[moduleCode] = [...new Set([...(grouped[moduleCode] ?? []), fieldCode])]
    }
    return grouped
  }
  const outboundFields = groupedFields(policyForm.outputFieldCodesText, '输出字段')
  if (!outboundFields) return null
  const writableFields = groupedFields(policyForm.writableFieldCodesText, '可写字段')
  if (!writableFields) return null
  const fillFields = groupedFields(policyForm.fillFieldCodesText, 'AI_FILL 字段')
  if (!fillFields) return null
  const moduleCodes = lines(policyForm.moduleCodesText)
  for (const moduleCode of [...Object.keys(outboundFields), ...Object.keys(writableFields), ...Object.keys(fillFields)]) {
    if (!moduleCodes.includes(moduleCode)) {
      errorMessage.value = `字段所属模块 ${moduleCode} 不在允许模块范围内。`
      return null
    }
  }
  const allowedOperations = [...new Set(policyForm.allowedOperations)]
  const hasWriteOperation = allowedOperations.some(
    operation => operation === 'RECORD_CREATE' || operation === 'RECORD_UPDATE',
  )
  const hasFillOperation = allowedOperations.includes('AI_FILL')
  if (!allowedOperations.length) {
    errorMessage.value = '请至少选择一个允许操作。'
    return null
  }
  if (hasWriteOperation && !Object.keys(writableFields).length) {
    errorMessage.value = '启用创建或更新时，必须配置至少一个可写字段。'
    return null
  }
  if (!hasWriteOperation && Object.keys(writableFields).length) {
    errorMessage.value = '未启用创建或更新时，请清空可写字段范围。'
    return null
  }
  if (hasFillOperation !== Boolean(Object.keys(fillFields).length)) {
    errorMessage.value = hasFillOperation
      ? '启用 AI_FILL 时，必须配置至少一个允许智能填充的字段。'
      : '未启用 AI_FILL 时，请清空智能填充字段范围。'
    return null
  }
  if (moduleCodes.some(moduleCode => !(outboundFields[moduleCode]?.length))) {
    errorMessage.value = '每个允许模块都必须配置至少一个查询输出字段。'
    return null
  }
  const confirmationExpiresSeconds = Number(policyForm.confirmationExpiresSeconds)
  if (!Number.isInteger(confirmationExpiresSeconds)
      || confirmationExpiresSeconds < 60 || confirmationExpiresSeconds > 3600) {
    errorMessage.value = '确认有效期必须是 60 到 3600 秒的整数。'
    return null
  }
  /* Confirmation is deliberately not configurable to an automatic mode. */
  if (policyForm.confirmationMode !== 'REQUIRED') {
    errorMessage.value = '记录写入必须显式确认。'
    return null
  }
  const input: SaveAiPolicyInput = {
    expectedVersion: policy.value?.draftVersion ?? 0,
    providerId: policyForm.providerId,
    moduleCodes,
    outboundFields,
    allowedOperations,
    writableFields,
    fillFields,
    confirmationMode: policyForm.confirmationMode,
    confirmationExpiresSeconds,
    maxRows: Number(policyForm.maxRows),
    redactionMode: policyForm.redactionMode,
    promptVersion: policyForm.promptVersion.trim(),
    enabled: policyForm.enabled,
  }
  if (!input.providerId || !input.moduleCodes.length || !Object.keys(input.outboundFields).length || !input.promptVersion) {
    errorMessage.value = 'Provider、允许模块、输出字段和 Prompt 版本均不能为空。'
    return null
  }
  if (!Number.isInteger(input.maxRows) || input.maxRows < 1 || input.maxRows > 50) {
    errorMessage.value = '最大返回行数必须是 1 到 50 的整数。'
    return null
  }
  return input
}

async function savePolicy() {
  errorMessage.value = ''
  successMessage.value = ''
  const input = policyInput()
  if (!input) return
  mutation.value = 'policy'
  try {
    applyPolicy(await aiAdminApi.savePolicy(systemId.value, input))
    successMessage.value = 'Agent 策略草稿已保存。'
  } catch (error) {
    errorMessage.value = requestError(error, 'Agent 策略保存失败')
  } finally {
    mutation.value = ''
  }
}

async function checkPolicy() {
  errorMessage.value = ''
  successMessage.value = ''
  mutation.value = 'check'
  try {
    checkResult.value = await aiAdminApi.checkPolicy(systemId.value)
    if (checkResult.value.status === 'PASSED') successMessage.value = '策略检查通过，可以发布。'
  } catch (error) {
    errorMessage.value = requestError(error, 'Agent 策略检查失败')
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
    applyPolicy(await aiAdminApi.publishPolicy(systemId.value, policy.value.draftVersion))
    capability.value = await aiAdminApi.capability(systemId.value)
    successMessage.value = 'Agent 策略已发布。'
  } catch (error) {
    errorMessage.value = requestError(error, 'Agent 策略发布失败')
  } finally {
    mutation.value = ''
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page ai-agent-config-page">
    <AdminPageHeader title="Agent 配置" description="配置 SecretRef Provider，并发布查询、确认写入、工作与 Flow/报表/打印草稿生成的显式授权策略。">
      <template v-if="!isMobile" #actions>
        <a-button aria-label="刷新 Agent 配置" :loading="loading" :disabled="loading" @click="load"><RefreshCw :size="16" />刷新</a-button>
      </template>
    </AdminPageHeader>

    <a-alert
      class="ai-secret-notice"
      type="info"
      show-icon
      message="仅保存 SecretRef"
      description="前端和业务数据库不接收明文 Provider 密钥；SecretRef 仅指向外部密钥提供方。"
    />
    <a-alert v-if="errorMessage" class="ai-admin-error" type="error" show-icon role="alert" aria-live="assertive" :message="errorMessage" />
    <a-alert v-if="successMessage" class="ai-admin-success" type="success" show-icon role="status" aria-live="polite" :message="successMessage" />

    <div v-if="isMobile" class="ai-mobile-gate">
      <AdminMobileNotice />
      <a-empty description="Provider、SecretRef 与操作策略需要桌面配置工作区，请在宽度大于 720px 的窗口中管理。" />
    </div>
    <a-spin v-else :spinning="loading" :aria-busy="loading">
      <section class="ai-capability-card" :class="{ unavailable: !capability?.available }">
        <div><Bot :size="20" /><strong>运行能力</strong></div>
        <a-tag :color="capability?.available ? 'green' : 'orange'">
          {{ capability?.available ? 'AVAILABLE' : 'UNAVAILABLE' }}
        </a-tag>
        <span>{{ capability?.available ? `策略版本 ${capability.policyVersion}` : capability?.reason || '尚未配置' }}</span>
      </section>

      <div class="ai-admin-layout">
        <section class="ai-provider-panel">
          <header><h2>Provider / 模型</h2><a-button class="ai-provider-new" @click="newProvider"><Plus :size="15" />新增</a-button></header>
          <div class="ai-provider-list">
            <button
              v-for="provider in providers"
              :key="provider.id"
              type="button"
              :class="{ active: selectedProvider?.id === provider.id }"
              @click="applyProvider(provider)"
            >
              <span><strong>{{ provider.name }}</strong><small>{{ provider.model }}</small></span>
              <a-tag :color="provider.enabled ? 'green' : 'default'">{{ provider.enabled ? '启用' : '停用' }}</a-tag>
            </button>
          </div>
          <a-empty v-if="!providers.length" description="尚未配置 Provider" />
        </section>

        <section class="ai-provider-editor">
          <h2>{{ selectedProvider ? '编辑 Provider' : '新增 Provider' }}</h2>
          <div class="ai-form-grid">
            <label v-if="selectedProvider">Provider ID<input v-model="providerForm.id" class="ai-provider-id" disabled /></label>
            <div v-else class="ai-generated-id"><span>Provider ID</span><strong>保存时由服务端生成</strong></div>
            <label>稳定编码<input v-model="providerForm.code" class="ai-provider-code" :disabled="Boolean(selectedProvider)" /></label>
            <label>名称<input v-model="providerForm.name" class="ai-provider-name" /></label>
            <label>Base URL<input v-model="providerForm.baseUrl" class="ai-provider-base-url" placeholder="https://provider.example/v1" /></label>
            <label>模型<input v-model="providerForm.model" class="ai-provider-model" /></label>
            <label>SecretRef<input v-model="providerForm.secretRef" class="ai-provider-secret-ref" placeholder="vault://ai/provider/v1" /></label>
            <label>超时（秒）<input v-model.number="providerForm.timeoutSeconds" class="ai-provider-timeout" type="number" min="1" max="30" /></label>
            <label class="ai-checkbox"><input v-model="providerForm.enabled" type="checkbox" />启用 Provider</label>
          </div>
          <div class="ai-editor-actions">
            <span>版本 {{ providerForm.expectedVersion }}</span>
            <a-button class="ai-provider-save" type="primary" :loading="mutation === 'provider'" @click="saveProvider"><Save :size="15" />保存 Provider</a-button>
          </div>
        </section>
      </div>

      <section class="ai-policy-editor">
        <header><div><h2>Agent 操作策略</h2><p>记录与工作上下文查询保持只读；记录、配置、工作、Flow、报表与打印模板草稿独立授权，任何物化都必须显式确认。</p></div><a-tag>草稿 v{{ policy?.draftVersion ?? 0 }}</a-tag></header>
        <div class="ai-policy-grid">
          <label>Provider
            <select v-model="policyForm.providerId" class="ai-policy-provider">
              <option value="">请选择</option><option v-for="provider in providers" :key="provider.id" :value="provider.id">{{ provider.name }} · {{ provider.model }}</option>
            </select>
          </label>
          <label>最大返回行数<input v-model.number="policyForm.maxRows" class="ai-policy-max-rows" type="number" min="1" max="50" /></label>
          <label>脱敏模式<input class="ai-policy-redaction" value="STRICT（严格脱敏）" disabled /></label>
          <label>Prompt 版本<input v-model="policyForm.promptVersion" class="ai-policy-prompt-version" /></label>
          <fieldset class="ai-policy-operations">
            <legend>允许操作（独立授权）</legend>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="RECORD_QUERY" />RECORD_QUERY</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="RECORD_CREATE" />RECORD_CREATE</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="RECORD_UPDATE" />RECORD_UPDATE</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="AI_FILL" />AI_FILL</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="CONFIG_FIELD_DRAFT" />CONFIG_FIELD_DRAFT（仅配置草稿）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="CONFIG_SELECTION_FIELD_DRAFT" />CONFIG_SELECTION_FIELD_DRAFT（选择字段草稿）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="CONFIG_PAGE_LAYOUT_DRAFT" />CONFIG_PAGE_LAYOUT_DRAFT（页面布局草稿）</label>
            <label><input v-model="policyForm.allowedOperations" class="ai-policy-config-filter-scenario-draft" type="checkbox" value="CONFIG_FILTER_SCENARIO_DRAFT" />CONFIG_FILTER_SCENARIO_DRAFT（共享筛选方案草稿）</label>
            <label><input v-model="policyForm.allowedOperations" class="ai-policy-config-field-permission-stage-draft" type="checkbox" value="CONFIG_FIELD_PERMISSION_STAGE_DRAFT" />CONFIG_FIELD_PERMISSION_STAGE_DRAFT（字段权限 STAGED 草稿）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="RECORD_CONTEXT_SUMMARY" />RECORD_CONTEXT_SUMMARY（只读）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="RECORD_COMMENT_QUERY" />RECORD_COMMENT_QUERY（记录评论，只读）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="RECORD_HISTORY_QUERY" />RECORD_HISTORY_QUERY（记录历史，只读）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="RECORD_FILE_QUERY" />RECORD_FILE_QUERY（附件元数据，只读）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="WORK_TASK_QUERY" />WORK_TASK_QUERY（只读）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="WORK_DAILY_REPORT_QUERY" />WORK_DAILY_REPORT_QUERY（只读）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="WORK_PROJECT_METRICS_QUERY" />WORK_PROJECT_METRICS_QUERY（项目进度与指标，只读）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="RUNTIME_STATISTICS_QUERY" />RUNTIME_STATISTICS_QUERY（已发布数据源统计与趋势，只读）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="RUNTIME_REPORT_QUERY" />RUNTIME_REPORT_QUERY（已发布报表，只读）</label>
            <label><input v-model="policyForm.allowedOperations" class="ai-policy-flow-instance-history-query" type="checkbox" value="FLOW_INSTANCE_HISTORY_QUERY" />FLOW_INSTANCE_HISTORY_QUERY（审批实例历史，只读）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="TODO_QUERY" />TODO_QUERY（当前成员待办，只读）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="MESSAGE_QUERY" />MESSAGE_QUERY（当前成员消息，只读）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="WORK_TASK_DRAFT" />WORK_TASK_DRAFT（确认后创建）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="WORK_DAILY_REPORT_DRAFT" />WORK_DAILY_REPORT_DRAFT（确认后创建草稿）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="FLOW_DEFINITION_DRAFT" />FLOW_DEFINITION_DRAFT（仅创建未发布定义）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="CONFIG_REPORT_DRAFT" />CONFIG_REPORT_DRAFT（仅创建未发布报表）</label>
            <label><input v-model="policyForm.allowedOperations" type="checkbox" value="CONFIG_PRINT_TEMPLATE_DRAFT" />CONFIG_PRINT_TEMPLATE_DRAFT（仅创建 DISABLED 模板）</label>
          </fieldset>
          <label>写入确认模式<input class="ai-policy-confirmation-mode" value="REQUIRED（必须显式确认）" disabled /></label>
          <label>确认有效期（秒，60–3600）<input v-model.number="policyForm.confirmationExpiresSeconds" class="ai-policy-confirmation-expiry" type="number" min="60" max="3600" /></label>
          <label>允许模块编码（每行一个）<textarea v-model="policyForm.moduleCodesText" class="ai-policy-modules" rows="5" /></label>
          <label>查询输出字段（moduleCode.fieldCode，每行一个）<textarea v-model="policyForm.outputFieldCodesText" class="ai-policy-fields" rows="5" /></label>
          <label>创建/更新可写字段（moduleCode.fieldCode，每行一个）<textarea v-model="policyForm.writableFieldCodesText" class="ai-policy-writable-fields" rows="5" /></label>
          <label>允许智能填充字段（moduleCode.fieldCode，每行一个）<textarea v-model="policyForm.fillFieldCodesText" class="ai-policy-fill-fields" rows="5" /></label>
        </div>
        <label class="ai-checkbox"><input v-model="policyForm.enabled" class="ai-policy-enabled" type="checkbox" />发布后启用 Agent</label>
        <div class="ai-policy-actions">
          <span>已发布：{{ policy?.activeVersionId || '无' }}</span>
          <a-button class="ai-policy-save" :loading="mutation === 'policy'" @click="savePolicy"><Save :size="15" />保存草稿</a-button>
          <a-button class="ai-policy-check" :loading="mutation === 'check'" @click="checkPolicy"><CheckCircle2 :size="15" />检查</a-button>
          <a-button class="ai-policy-publish" type="primary" :loading="mutation === 'publish'" :disabled="checkResult?.status !== 'PASSED'" @click="publishPolicy"><Rocket :size="15" />发布</a-button>
        </div>
        <div v-if="checkResult" class="ai-check-result">
          <strong>检查结果：{{ checkResult.status }}</strong>
          <ul v-if="checkResult.issues.length"><li v-for="issue in checkResult.issues" :key="`${issue.code}:${issue.path}`">{{ issue.code }} · {{ issue.message }}<span v-if="issue.path">（{{ issue.path }}）</span></li></ul>
          <p v-else>没有阻断问题。</p>
        </div>
      </section>
    </a-spin>
  </section>
</template>

<style scoped>
.ai-secret-notice,.ai-admin-error,.ai-admin-success{margin-bottom:12px}.ai-mobile-gate{min-width:0;padding:4px 0 28px}.ai-mobile-gate .ant-empty{padding:30px 12px;border:1px solid #dfe6e9;border-radius:8px;background:#fff}.ai-capability-card{display:flex;align-items:center;gap:10px;margin-bottom:14px;padding:12px 14px;border:1px solid #b8ddd6;border-radius:8px;background:#f1faf8}.ai-capability-card.unavailable{border-color:#ead6a7;background:#fff9ec}.ai-capability-card>div{display:flex;align-items:center;gap:7px}.ai-capability-card>span:last-child{color:#65747b;font-size:12px}.ai-admin-layout{display:grid;grid-template-columns:260px minmax(0,1fr);min-width:0;gap:14px}.ai-provider-panel,.ai-provider-editor,.ai-policy-editor{min-width:0;padding:16px;border:1px solid #dfe6e9;border-radius:9px;background:#fff}.ai-provider-panel header,.ai-policy-editor>header,.ai-editor-actions,.ai-policy-actions{display:flex;align-items:center;justify-content:space-between;gap:10px}.ai-provider-panel h2,.ai-provider-editor h2,.ai-policy-editor h2,.ai-policy-editor p{margin:0}.ai-provider-list{display:grid;gap:7px;margin-top:12px}.ai-provider-list>button{display:flex;align-items:center;justify-content:space-between;gap:8px;padding:10px;border:1px solid #dce5e8;border-radius:7px;background:#fff;text-align:left}.ai-provider-list>button.active{border-color:#278f82;background:#eef8f6}.ai-provider-list>button>span{display:grid;gap:3px}.ai-provider-list small,.ai-editor-actions>span,.ai-policy-editor p,.ai-policy-actions>span{color:#6b7980;font-size:12px}.ai-form-grid,.ai-policy-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:11px;margin-top:13px}.ai-form-grid label,.ai-policy-grid label{display:grid;min-width:0;gap:5px;color:#526169;font-size:12px}.ai-form-grid input,.ai-policy-grid input,.ai-policy-grid select,.ai-policy-grid textarea{width:100%;box-sizing:border-box;padding:7px 9px;border:1px solid #cbd5d9;border-radius:6px;background:#fff}.ai-checkbox{display:flex!important;align-items:center;gap:6px}.ai-checkbox input{width:auto}.ai-editor-actions,.ai-policy-actions{justify-content:flex-end;margin-top:14px}.ai-policy-editor{margin-top:14px}.ai-policy-grid label:nth-last-child(-n+2){grid-column:span 1}.ai-check-result{margin-top:12px;padding:12px;border-radius:7px;background:#f5f8f8}.ai-check-result p,.ai-check-result ul{margin:7px 0 0}.ai-check-result li{margin:4px 0}
.ai-generated-id{display:grid;gap:5px;color:#526169;font-size:12px}.ai-generated-id strong{padding:8px 0;color:#68777e}
.ai-policy-operations{display:flex;align-items:center;gap:14px;margin:0;padding:9px;border:1px solid #d7dfe2;border-radius:6px}.ai-policy-operations legend{padding:0 4px;color:#526169;font-size:12px}.ai-policy-operations label{display:flex;align-items:center;gap:5px}.ai-policy-operations input{width:auto}
@media(max-width:1100px){.ai-admin-layout{grid-template-columns:220px minmax(0,1fr)}.ai-policy-operations{align-items:flex-start;flex-direction:column}.ai-policy-actions{flex-wrap:wrap}}
</style>
