<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { CheckCircleOutlined, PlusOutlined, ReloadOutlined, RobotOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import { api, ApiError } from '../api'
import { aiPreviewSummary, aiScopeLabel, aiToolTypeLabel, isWriteTool } from '../ai'
import { platformTokens, systemTokens } from '../session'
import type {
  AiAgent,
  AiAgentPreview,
  AiModel,
  AiPlatformOverview,
  AiSystemOverview,
  AiTool,
} from '../types'

const props = defineProps<{ context: 'platform' | 'system' }>()
const token = computed(() => props.context === 'platform' ? platformTokens.value?.accessToken : systemTokens.value?.accessToken)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const platform = ref<AiPlatformOverview>({ models: [], systems: [] })
const system = ref<AiSystemOverview>({ availableModels: [], modules: [], agents: [] })
const preview = ref<AiAgentPreview>()
const selectedAgentId = ref<number>()

const modelForm = reactive({
  id: undefined as number | undefined,
  code: '', name: '', provider: 'LOCAL', modelName: '', endpointUrl: 'http://localhost:11434/v1',
  credentialRef: '', capabilities: ['CHAT', 'TOOL_CALLING'], dailyTokenLimit: 100000,
  concurrencyLimit: 4, logMasking: true, dataResidency: 'LOCAL_ONLY', expectedVersion: undefined as number | undefined,
})
const grantForm = reactive({ modelId: undefined as number | undefined, systemId: undefined as number | undefined,
  dailyTokenLimit: 50000, concurrencyLimit: 2 })

interface ToolDraft {
  toolType: AiTool['toolType']
  resourceType: 'MODULE'
  resourceId: string
  actionCode: string
  fieldCodes: string[]
  requestedDataScope: 'CURRENT' | 'ALL'
  requiresConfirmation: boolean
}
interface AgentDraft {
  id?: number
  code: string
  name: string
  description: string
  modelGrantId?: number
  systemPrompt: string
  expectedVersion?: number
  contextPolicy: { allowedEntryContexts: string[]; allowExternalData: boolean; maskSensitiveData: boolean }
  confirmationPolicy: { writeActionsRequireConfirmation: boolean; batchActionsRequireConfirmation: boolean; showFieldLevelDiff: boolean }
  fallbackPolicy: { mode: string; userMessage: string }
  tools: ToolDraft[]
}

function blankTool(): ToolDraft {
  const module = system.value.modules[0]
  return {
    toolType: 'QUERY', resourceType: 'MODULE', resourceId: module?.code || '',
    actionCode: module?.actions[0] || 'LIST', fieldCodes: module?.fields.slice(0, 1) || [],
    requestedDataScope: 'CURRENT', requiresConfirmation: false,
  }
}

const agentForm = reactive<AgentDraft>({
  code: '', name: '', description: '', modelGrantId: undefined,
  systemPrompt: '你是当前系统的业务助理。只在已授权模块、动作、字段和数据范围内回答与生成操作草稿。',
  contextPolicy: { allowedEntryContexts: ['SYSTEM_ADMIN', 'MODULE_PAGE'], allowExternalData: false, maskSensitiveData: true },
  confirmationPolicy: { writeActionsRequireConfirmation: true, batchActionsRequireConfirmation: true, showFieldLevelDiff: true },
  fallbackPolicy: { mode: 'READ_ONLY', userMessage: 'AI 暂不可用，已切换为只读查询。' },
  tools: [],
})

const selectedModel = computed(() => platform.value.models.find(item => item.id === grantForm.modelId))
const selectedAgent = computed(() => system.value.agents.find(item => item.id === selectedAgentId.value))
const modelGrantedSystems = computed(() => selectedModel.value?.grants.filter(item => item.status === 'ACTIVE') || [])
const previewAlreadyPublished = computed(() => Boolean(preview.value && selectedAgent.value?.versions.some(
  item => item.current && item.draftRevision === preview.value?.draftRevision,
)))

function message(cause: unknown, fallback: string) {
  return cause instanceof ApiError ? `${cause.message}（${cause.code}${cause.traceId ? ` · ${cause.traceId}` : ''}）` : fallback
}

async function load() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    if (props.context === 'platform') {
      platform.value = await api<AiPlatformOverview>('/api/admin/platform/ai', {}, token.value)
      if (!grantForm.modelId) grantForm.modelId = platform.value.models[0]?.id
      if (!grantForm.systemId) grantForm.systemId = platform.value.systems[0]?.id
    } else {
      system.value = await api<AiSystemOverview>('/api/admin/system/ai', {}, token.value)
      if (!agentForm.modelGrantId) agentForm.modelGrantId = system.value.availableModels[0]?.activeGrantId
      if (!agentForm.tools.length && system.value.modules.length) agentForm.tools.push(blankTool())
      if (selectedAgentId.value) selectedAgentId.value = system.value.agents.some(item => item.id === selectedAgentId.value)
        ? selectedAgentId.value : undefined
      const firstAgent = system.value.agents[0]
      if (!selectedAgentId.value && firstAgent) editAgent(firstAgent)
    }
  } catch (cause) {
    error.value = message(cause, 'AI 配置加载失败')
  } finally {
    loading.value = false
  }
}

function resetModel() {
  Object.assign(modelForm, {
    id: undefined, code: '', name: '', provider: 'LOCAL', modelName: '', endpointUrl: 'http://localhost:11434/v1',
    credentialRef: '', capabilities: ['CHAT', 'TOOL_CALLING'], dailyTokenLimit: 100000,
    concurrencyLimit: 4, logMasking: true, dataResidency: 'LOCAL_ONLY', expectedVersion: undefined,
  })
}

function editModel(model: AiModel) {
  Object.assign(modelForm, {
    id: model.id, code: model.code, name: model.name, provider: model.provider, modelName: model.modelName,
    endpointUrl: model.endpointUrl || '', credentialRef: '', capabilities: [...model.capabilities],
    dailyTokenLimit: model.limitPolicy.dailyTokenLimit || 100000,
    concurrencyLimit: model.limitPolicy.concurrencyLimit || 4,
    logMasking: model.limitPolicy.logMasking !== false,
    dataResidency: model.limitPolicy.dataResidency || 'DOMESTIC_ONLY', expectedVersion: model.version,
  })
  error.value = ''
  success.value = '编辑模型时必须重新填写密钥引用，页面不会读回原引用或密钥值。'
}

async function saveModel() {
  if (!token.value) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const body = JSON.stringify({ ...modelForm, id: undefined })
    const saved = await api<AiModel>(modelForm.id ? `/api/admin/platform/ai/models/${modelForm.id}` : '/api/admin/platform/ai/models', {
      method: modelForm.id ? 'PUT' : 'POST', body,
    }, token.value)
    await load()
    const reread = platform.value.models.find(item => item.id === saved.id)
    if (!reread || reread.version !== saved.version) throw new Error('model reread mismatch')
    success.value = `模型 ${reread.name} 已保存并从平台持久层读回；凭证仅显示 ${reread.credentialReferenceType} 引用类型。`
    grantForm.modelId = reread.id
    resetModel()
  } catch (cause) {
    error.value = message(cause, '模型保存后未能读回')
  } finally {
    saving.value = false
  }
}

async function saveGrant() {
  if (!token.value || !grantForm.modelId || !grantForm.systemId) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const existing = selectedModel.value?.grants.find(item => item.systemId === grantForm.systemId)
    await api(`/api/admin/platform/ai/models/${grantForm.modelId}/grants/${grantForm.systemId}`, {
      method: 'PUT', body: JSON.stringify({ dailyTokenLimit: grantForm.dailyTokenLimit,
        concurrencyLimit: grantForm.concurrencyLimit, expectedVersion: existing?.version }),
    }, token.value)
    await load()
    const reread = platform.value.models.find(item => item.id === grantForm.modelId)?.grants
      .find(item => item.systemId === grantForm.systemId && item.status === 'ACTIVE')
    if (!reread) throw new Error('grant reread mismatch')
    success.value = `模型授权已写入并读回：${reread.systemName} · 日配额 ${reread.usageLimit.dailyTokenLimit}。`
  } catch (cause) {
    error.value = message(cause, '模型授权保存后未能读回')
  } finally {
    saving.value = false
  }
}

function resetAgent() {
  Object.assign(agentForm, {
    id: undefined, code: '', name: '', description: '', expectedVersion: undefined,
    modelGrantId: system.value.availableModels[0]?.activeGrantId,
    systemPrompt: '你是当前系统的业务助理。只在已授权模块、动作、字段和数据范围内回答与生成操作草稿。',
    contextPolicy: { allowedEntryContexts: ['SYSTEM_ADMIN', 'MODULE_PAGE'], allowExternalData: false, maskSensitiveData: true },
    confirmationPolicy: { writeActionsRequireConfirmation: true, batchActionsRequireConfirmation: true, showFieldLevelDiff: true },
    fallbackPolicy: { mode: 'READ_ONLY', userMessage: 'AI 暂不可用，已切换为只读查询。' },
    tools: system.value.modules.length ? [blankTool()] : [],
  })
  selectedAgentId.value = undefined
  preview.value = undefined
}

function editAgent(agent: AiAgent) {
  selectedAgentId.value = agent.id
  Object.assign(agentForm, {
    id: agent.id, code: agent.code, name: agent.name, description: agent.description || '',
    modelGrantId: agent.modelGrantId, systemPrompt: agent.systemPrompt, expectedVersion: agent.version,
    contextPolicy: { ...agent.contextPolicy }, confirmationPolicy: { ...agent.confirmationPolicy },
    fallbackPolicy: { ...agent.fallbackPolicy }, tools: agent.tools.map(tool => ({
      toolType: tool.toolType, resourceType: 'MODULE', resourceId: tool.resourceId, actionCode: tool.actionCode,
      fieldCodes: [...tool.fieldCodes], requestedDataScope: tool.requestedDataScope,
      requiresConfirmation: tool.requiresConfirmation,
    })),
  })
  preview.value = undefined
}

function moduleFor(code: string) {
  return system.value.modules.find(item => item.code === code)
}

function resetToolBinding(tool: ToolDraft) {
  const module = moduleFor(tool.resourceId)
  tool.actionCode = module?.actions[0] || ''
  tool.fieldCodes = module?.fields.slice(0, 1) || []
  if (isWriteTool(tool)) tool.requiresConfirmation = true
}

function toolTypeChanged(tool: ToolDraft) {
  if (isWriteTool(tool)) tool.requiresConfirmation = true
}

async function saveAgent() {
  if (!token.value || !agentForm.modelGrantId || !agentForm.tools.length) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const saved = await api<AiAgent>(agentForm.id ? `/api/admin/system/ai/agents/${agentForm.id}/draft` : '/api/admin/system/ai/agents', {
      method: agentForm.id ? 'PUT' : 'POST', body: JSON.stringify(agentForm),
    }, token.value)
    selectedAgentId.value = saved.id
    await load()
    const reread = system.value.agents.find(item => item.id === saved.id)
    if (!reread || reread.draftRevision !== saved.draftRevision) throw new Error('agent reread mismatch')
    editAgent(reread)
    success.value = `智能助手草稿已保存；下一步请执行权限预览。`
  } catch (cause) {
    error.value = message(cause, '智能助手草稿保存后未能读回')
  } finally {
    saving.value = false
  }
}

async function previewAgent() {
  if (!token.value || !selectedAgentId.value) return
  loading.value = true
  error.value = ''
  try {
    preview.value = await api<AiAgentPreview>(`/api/admin/system/ai/agents/${selectedAgentId.value}/preview`, {}, token.value)
    success.value = aiPreviewSummary(preview.value)
  } catch (cause) {
    error.value = message(cause, '智能助手权限预览失败')
  } finally {
    loading.value = false
  }
}

async function publishAgent() {
  if (!token.value || !selectedAgentId.value || !preview.value?.valid) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const result = await api<{ versionId: number; versionNumber: number; draftRevision: number; snapshotHash: string }>(
      `/api/admin/system/ai/agents/${selectedAgentId.value}/publish`, {
        method: 'POST', body: JSON.stringify({ expectedDraftRevision: preview.value.draftRevision }),
      }, token.value)
    await load()
    const reread = system.value.agents.find(item => item.id === selectedAgentId.value)
    if (!reread?.versions.some(item => item.id === result.versionId && item.current)) throw new Error('publication reread mismatch')
    editAgent(reread)
    await previewAgent()
    success.value = `智能助手版本 v${result.versionNumber} 已发布并确认生效。`
  } catch (cause) {
    error.value = message(cause, '智能助手发布后未能读回')
  } finally {
    saving.value = false
  }
}

watch(() => props.context, load)
onMounted(load)
</script>

<template>
  <div class="ai-config" :class="{ 'is-loading': loading }">
    <a-alert v-if="error" type="error" show-icon :message="error" closable @close="error = ''" />
    <a-alert v-if="success" type="success" show-icon :message="success" closable @close="success = ''" />

    <template v-if="context === 'platform'">
      <section class="ai-boundary-card">
        <div><SafetyCertificateOutlined /><strong>平台只管理模型接入与系统授权</strong></div>
        <p>密钥通过安全引用保存，页面不回显引用内容或密钥值；智能助手的模块、字段和数据范围由系统后台统一控制。</p>
      </section>
      <div class="ai-config-grid ai-config-grid--platform">
        <section class="panel-card">
          <div class="panel-title"><strong>模型目录</strong><a-button type="text" @click="load"><ReloadOutlined /></a-button></div>
          <div v-if="platform.models.length" class="ai-model-list">
            <button v-for="model in platform.models" :key="model.id" @click="editModel(model); grantForm.modelId = model.id">
              <span class="ai-model-logo"><RobotOutlined /></span>
              <span><strong>{{ model.name }}</strong><small>{{ model.provider }} · {{ model.modelName }}</small></span>
              <a-tag :color="model.credentialAvailable ? 'green' : 'red'">{{ model.credentialAvailable ? '凭证可用' : '凭证失效' }}</a-tag>
            </button>
          </div>
          <a-empty v-else description="尚未配置模型" />
        </section>

        <section class="panel-card ai-editor">
          <div class="panel-title"><strong>{{ modelForm.id ? '编辑模型' : '接入模型' }}</strong><a-button v-if="modelForm.id" type="link" @click="resetModel">新建</a-button></div>
          <a-form layout="vertical">
            <div class="form-grid form-grid--2">
              <a-form-item label="模型编码" required><a-input v-model:value="modelForm.code" placeholder="local_qwen" /></a-form-item>
              <a-form-item label="显示名称" required><a-input v-model:value="modelForm.name" placeholder="本地业务模型" /></a-form-item>
              <a-form-item label="提供方" required><a-input v-model:value="modelForm.provider" placeholder="LOCAL / OPENAI" /></a-form-item>
              <a-form-item label="模型名称" required><a-input v-model:value="modelForm.modelName" placeholder="qwen3" /></a-form-item>
            </div>
            <a-form-item label="接口地址" extra="外部模型必须使用 HTTPS，本地模型可使用本机地址"><a-input v-model:value="modelForm.endpointUrl" /></a-form-item>
            <a-form-item label="密钥引用" required extra="仅支持 env:变量名 或 property:配置键；保存后不会回显"><a-input-password v-model:value="modelForm.credentialRef" placeholder="property:app.ai.model-key" autocomplete="new-password" /></a-form-item>
            <a-form-item label="能力"><a-checkbox-group v-model:value="modelForm.capabilities" :options="['CHAT', 'TOOL_CALLING', 'VISION', 'EMBEDDING']" /></a-form-item>
            <div class="form-grid form-grid--2">
              <a-form-item label="每日 Token 上限"><a-input-number v-model:value="modelForm.dailyTokenLimit" :min="1" style="width:100%" /></a-form-item>
              <a-form-item label="并发上限"><a-input-number v-model:value="modelForm.concurrencyLimit" :min="1" :max="100" style="width:100%" /></a-form-item>
              <a-form-item label="数据驻留"><a-select v-model:value="modelForm.dataResidency" :options="[
                { value: 'LOCAL_ONLY', label: '仅本地' }, { value: 'DOMESTIC_ONLY', label: '境内' }, { value: 'ALLOW_EXTERNAL', label: '允许外部' }]" /></a-form-item>
              <a-form-item label="日志安全"><a-checkbox v-model:checked="modelForm.logMasking">强制敏感信息遮蔽</a-checkbox></a-form-item>
            </div>
            <a-button type="primary" :loading="saving" :disabled="!modelForm.code || !modelForm.name || !modelForm.credentialRef" @click="saveModel">保存并持久化读回</a-button>
          </a-form>
        </section>
      </div>

      <section class="panel-card ai-grant-panel">
        <div class="panel-title"><strong>系统模型授权</strong><span>未授权模型不会出现在系统后台</span></div>
        <div class="form-grid form-grid--4">
          <a-form-item label="模型"><a-select v-model:value="grantForm.modelId" :options="platform.models.map(item => ({ value: item.id, label: item.name }))" /></a-form-item>
          <a-form-item label="目标系统"><a-select v-model:value="grantForm.systemId" :options="platform.systems.map(item => ({ value: item.id, label: item.name }))" /></a-form-item>
          <a-form-item label="系统日配额"><a-input-number v-model:value="grantForm.dailyTokenLimit" :min="1" style="width:100%" /></a-form-item>
          <a-form-item label="系统并发"><a-input-number v-model:value="grantForm.concurrencyLimit" :min="1" :max="100" style="width:100%" /></a-form-item>
        </div>
        <div class="ai-inline-action"><a-button type="primary" :loading="saving" :disabled="!grantForm.modelId || !grantForm.systemId" @click="saveGrant">授权并读回</a-button>
          <span v-if="selectedModel">平台上限：{{ selectedModel.limitPolicy.dailyTokenLimit }} Token / {{ selectedModel.limitPolicy.concurrencyLimit }} 并发</span></div>
        <div v-if="modelGrantedSystems.length" class="ai-grant-tags"><a-tag v-for="grant in modelGrantedSystems" :key="grant.id" color="blue">{{ grant.systemName }} · {{ grant.usageLimit.dailyTokenLimit }}/日</a-tag></div>
      </section>
    </template>

    <template v-else>
      <section class="ai-boundary-card">
        <div><SafetyCertificateOutlined /><strong>智能助手只能使用当前系统已授权模型和已发布模块</strong></div>
        <p>发布预览会取当前成员的动作、字段和数据范围交集；写入动作必须人工确认，越权范围会直接阻断发布。</p>
      </section>
      <a-alert v-if="!system.availableModels.length" type="warning" show-icon message="当前系统没有可用模型授权" description="请先由平台管理员在“平台后台 → AI 模型”完成模型授权。" />
      <a-alert v-else-if="!system.modules.length" type="warning" show-icon message="没有可绑定的发布态模块" description="请先发布模块，并确保当前管理员拥有明确的模块动作权限。" />
      <div class="ai-config-grid ai-config-grid--system">
        <section class="panel-card">
          <div class="panel-title"><strong>智能助手</strong><a-button type="text" @click="resetAgent"><PlusOutlined />新建</a-button></div>
          <div v-if="system.agents.length" class="ai-agent-list">
            <button v-for="agent in system.agents" :key="agent.id" :class="{ active: selectedAgentId === agent.id }" @click="editAgent(agent)">
              <span><strong>{{ agent.name }}</strong><small>{{ agent.code }} · 草稿 r{{ agent.draftRevision }}</small></span>
              <a-tag :color="agent.status === 'PUBLISHED' ? 'green' : 'gold'">{{ agent.status === 'PUBLISHED' ? '已发布' : '草稿' }}</a-tag>
            </button>
          </div>
          <a-empty v-else description="尚未配置智能助手" />
          <div v-if="selectedAgent?.versions.length" class="ai-version-list">
            <strong>不可变版本</strong>
            <span v-for="version in selectedAgent.versions" :key="version.id">v{{ version.versionNumber }} · r{{ version.draftRevision }} <a-tag v-if="version.current" color="green">当前</a-tag></span>
          </div>
        </section>

        <section class="panel-card ai-editor">
          <div class="panel-title"><strong>{{ agentForm.id ? '智能助手草稿' : '新建智能助手' }}</strong><span>先保存，再预览，再发布</span></div>
          <a-form layout="vertical">
            <div class="form-grid form-grid--2">
              <a-form-item label="编码" required><a-input v-model:value="agentForm.code" placeholder="sales_assistant" /></a-form-item>
              <a-form-item label="名称" required><a-input v-model:value="agentForm.name" placeholder="销售助理" /></a-form-item>
            </div>
            <a-form-item label="授权模型" required><a-select v-model:value="agentForm.modelGrantId" :options="system.availableModels.map(item => ({ value: item.activeGrantId, label: `${item.name} · ${item.provider}` }))" placeholder="仅展示平台已授权模型" /></a-form-item>
            <a-form-item label="系统提示词" required><a-textarea v-model:value="agentForm.systemPrompt" :rows="4" /></a-form-item>
            <div class="ai-policy-row">
              <a-checkbox v-model:checked="agentForm.contextPolicy.maskSensitiveData">敏感字段脱敏</a-checkbox>
              <a-checkbox v-model:checked="agentForm.contextPolicy.allowExternalData">允许携带外部数据</a-checkbox>
              <a-checkbox v-model:checked="agentForm.confirmationPolicy.writeActionsRequireConfirmation">写动作人工确认</a-checkbox>
              <a-checkbox v-model:checked="agentForm.confirmationPolicy.showFieldLevelDiff">展示字段差异</a-checkbox>
            </div>
            <a-form-item label="不可用时降级"><a-select v-model:value="agentForm.fallbackPolicy.mode" :options="[
              { value: 'READ_ONLY', label: '只读查询' }, { value: 'TEMPLATE_QUERY', label: '模板查询' }, { value: 'DISABLED', label: '停用' }]" /></a-form-item>

            <div class="panel-title ai-tool-title"><strong>工具权限</strong><a-button size="small" @click="agentForm.tools.push(blankTool())"><PlusOutlined />添加工具</a-button></div>
            <div v-for="(tool, index) in agentForm.tools" :key="index" class="ai-tool-editor">
              <div class="form-grid form-grid--4">
                <a-form-item label="类型"><a-select v-model:value="tool.toolType" :options="['QUERY','WRITE','FLOW_DRAFT','REPORT','ERROR_EXPLAIN'].map(value => ({ value, label: aiToolTypeLabel(value) }))" @change="toolTypeChanged(tool)" /></a-form-item>
                <a-form-item label="模块"><a-select v-model:value="tool.resourceId" :options="system.modules.map(item => ({ value: item.code, label: item.name }))" @change="resetToolBinding(tool)" /></a-form-item>
                <a-form-item label="动作"><a-select v-model:value="tool.actionCode" :options="(moduleFor(tool.resourceId)?.actions || []).map(value => ({ value, label: value }))" @change="toolTypeChanged(tool)" /></a-form-item>
                <a-form-item label="数据范围"><a-select v-model:value="tool.requestedDataScope" :options="[
                  { value: 'CURRENT', label: '当前成员范围' }, { value: 'ALL', label: '全部数据' }]" /></a-form-item>
              </div>
              <a-form-item label="字段"><a-select v-model:value="tool.fieldCodes" mode="multiple" :options="(moduleFor(tool.resourceId)?.fields || []).map(value => ({ value, label: value }))" /></a-form-item>
              <div class="ai-tool-footer"><a-checkbox v-model:checked="tool.requiresConfirmation" :disabled="isWriteTool(tool)">执行前人工确认</a-checkbox><span>{{ aiScopeLabel(tool.requestedDataScope) }}</span><a-button danger type="link" @click="agentForm.tools.splice(index, 1)">移除</a-button></div>
            </div>
            <a-button type="primary" :loading="saving" :disabled="!agentForm.code || !agentForm.name || !agentForm.modelGrantId || !agentForm.tools.length" @click="saveAgent">保存草稿并读回</a-button>
            <a-button class="ai-secondary-action" :disabled="!selectedAgentId" @click="previewAgent">执行权限预览</a-button>
          </a-form>
        </section>
      </div>

      <section v-if="preview" class="panel-card ai-preview">
        <div class="panel-title"><strong>最终发布预览</strong><a-tag :color="preview.valid ? 'green' : 'red'">{{ aiPreviewSummary(preview) }}</a-tag></div>
        <a-descriptions bordered size="small" :column="3">
          <a-descriptions-item label="最终模型">{{ preview.finalModel?.name || '不可用' }}</a-descriptions-item>
          <a-descriptions-item label="草稿修订">r{{ preview.draftRevision }}</a-descriptions-item>
          <a-descriptions-item label="角色快照">{{ (preview.permissionSnapshot.roleIds as number[] || []).length }} 个角色</a-descriptions-item>
        </a-descriptions>
        <a-alert v-if="preview.issues.length" type="error" show-icon message="发布被阻断">
          <template #description><ul><li v-for="item in preview.issues" :key="`${item.code}-${item.toolIndex}`">{{ item.code }}：{{ item.message }}</li></ul></template>
        </a-alert>
        <div class="ai-preview-tools">
          <article v-for="tool in preview.tools" :key="tool.toolId" :class="{ invalid: !tool.valid }">
            <header><strong>{{ aiToolTypeLabel(tool.toolType) }} · {{ tool.resourceId }} / {{ tool.actionCode }}</strong><a-tag :color="tool.valid ? 'green' : 'red'">{{ tool.valid ? '范围有效' : '范围越权' }}</a-tag></header>
            <div v-for="(decision, field) in tool.fieldAuthorization" :key="field" class="ai-field-decision">
              <code>{{ field }}</code><span>{{ decision.readable ? '可读' : '隐藏' }} / {{ decision.writable ? '可写' : '只读' }}</span><a-tag v-if="decision.maskStrategy">{{ decision.maskStrategy }} 脱敏</a-tag>
            </div>
            <footer>{{ tool.requiresConfirmation ? '需要人工确认' : '只读直达' }} · 数据范围由服务器按权限快照收敛</footer>
          </article>
        </div>
        <a-button type="primary" :loading="saving" :disabled="!preview.valid || previewAlreadyPublished" @click="publishAgent">
          <CheckCircleOutlined />{{ previewAlreadyPublished ? '当前修订已发布' : '发布不可变策略版本' }}
        </a-button>
      </section>
    </template>
  </div>
</template>
