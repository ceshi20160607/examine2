<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import { systemTokens } from '../session'

interface ModuleOption { moduleId: number; moduleCode: string; moduleName: string }
interface AgentOption { id: number; name: string }
interface DraftOptions { agents: AgentOption[]; moduleId: number; moduleCode: string; moduleName: string; draftRevision: number }
interface SuggestedStep { index: number; name: string; assigneeType: string; confidence: number }
interface Candidate {
  pendingWriteId: number; status: string; outcome: string; message: string; errorCode: string | null
  flowName: string; description: string; steps: SuggestedStep[]; confidence: number
  businessWritten: boolean; version: number; moduleId: number; moduleCode: string; agentId: number
  flowId: number | null; flowPath: string | null
}

const props = defineProps<{ modules: ModuleOption[] }>()
const emit = defineEmits<{ saved: [flowId: number] }>()
const token = computed(() => systemTokens.value?.accessToken || '')
const open = ref(false)
const moduleId = ref<number>()
const options = ref<DraftOptions>()
const agentId = ref<number>()
const instruction = ref('')
const candidate = ref<Candidate>()
const flowName = ref('')
const description = ref('')
const steps = ref<SuggestedStep[]>([])
const reviewed = ref<Record<number, boolean>>({})
const confirmed = ref(false)
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const assigneeTypes = [
  { value: 'INITIATOR_MANAGER', label: '发起人上级' },
  { value: 'RECORD_OWNER', label: '记录负责人' },
  { value: 'STARTER_SELECTED', label: '发起时选择' },
]
const canSave = computed(() => Boolean(candidate.value && !candidate.value.businessWritten
  && confirmed.value && flowName.value.trim() && steps.value.length
  && steps.value.every(step => step.name.trim()
    && assigneeTypes.some(type => type.value === step.assigneeType)
    && (step.confidence >= 0.7 && candidate.value!.confidence >= 0.7 || reviewed.value[step.index]))))

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof ApiError ? reason.message : fallback
}

async function loadOptions() {
  if (!moduleId.value || !token.value) return
  error.value = ''
  try {
    const result = await api<DraftOptions>(`/api/admin/system/ai/flow-drafts/options?moduleId=${moduleId.value}`, {}, token.value)
    if (result.moduleId !== moduleId.value) return
    options.value = result
    agentId.value = result.agents.some(agent => agent.id === agentId.value) ? agentId.value : result.agents[0]?.id
  } catch (reason) {
    options.value = undefined
    error.value = errorMessage(reason, '流程配置 Agent 加载失败')
  }
}

async function recognize() {
  if (!token.value || !agentId.value || !moduleId.value || !instruction.value.trim()) return
  loading.value = true
  error.value = ''
  candidate.value = undefined
  steps.value = []
  reviewed.value = {}
  confirmed.value = false
  try {
    const result = await api<Candidate>('/api/admin/system/ai/flow-drafts/recognize', {
      method: 'POST', body: JSON.stringify({ agentId: agentId.value, moduleId: moduleId.value,
        instruction: instruction.value.trim() }),
    }, token.value)
    if (result.moduleId !== moduleId.value) return
    candidate.value = result
    flowName.value = result.flowName
    description.value = result.description
    steps.value = result.steps.map(step => ({ ...step }))
  } catch (reason) {
    error.value = errorMessage(reason, '流程建议生成失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!token.value || !candidate.value || !canSave.value) return
  saving.value = true
  error.value = ''
  try {
    const result = await api<Candidate>(`/api/admin/system/ai/flow-drafts/${candidate.value.pendingWriteId}/confirm`, {
      method: 'POST', body: JSON.stringify({ expectedVersion: candidate.value.version, confirmed: true,
        flowName: flowName.value.trim(), description: description.value.trim(),
        steps: steps.value.map(step => ({ name: step.name.trim(), assigneeType: step.assigneeType })),
        reviewedStepIndexes: candidate.value.steps.filter(step => reviewed.value[step.index]).map(step => step.index) }),
    }, token.value)
    candidate.value = result
    if (result.businessWritten && result.flowId) emit('saved', result.flowId)
  } catch (reason) {
    error.value = errorMessage(reason, '流程草稿保存失败')
  } finally {
    saving.value = false
  }
}

watch(() => props.modules, modules => {
  if (!modules.some(module => module.moduleId === moduleId.value)) moduleId.value = modules[0]?.moduleId
}, { immediate: true })
watch(moduleId, () => {
  options.value = undefined
  candidate.value = undefined
  steps.value = []
  reviewed.value = {}
  confirmed.value = false
  if (open.value) void loadOptions()
})
watch(open, value => { if (value) void loadOptions() })
</script>

<template>
  <section class="panel-card ai-flow-panel">
    <div class="ai-flow-heading">
      <div><strong>AI 辅助设计流程</strong><small>按业务描述建议审批步骤，核对后保存为流程草稿。</small></div>
      <a-button size="small" @click="open = !open">{{ open ? '收起' : '生成流程建议' }}</a-button>
    </div>
    <div v-if="open" class="ai-flow-body">
      <a-alert v-if="error" type="error" show-icon :message="error" />
      <a-alert v-if="!modules.length" type="info" show-icon message="请先发布一个可用的业务模块，再为其生成流程草稿。" />
      <template v-else>
        <label>业务模块
          <a-select v-model:value="moduleId" :options="modules.map(module => ({ value: module.moduleId, label: module.moduleName }))" />
        </label>
        <a-alert v-if="options && !options.agents.length" type="info" show-icon message="当前模块尚无已发布且授权的流程配置 Agent，请先在智能助手配置中发布。" />
        <template v-if="options?.agents.length">
          <label>配置 Agent
            <a-select v-model:value="agentId" :options="options.agents.map(agent => ({ value: agent.id, label: agent.name }))" />
          </label>
          <label>业务描述
            <a-textarea v-model:value="instruction" :rows="3" :maxlength="12000" placeholder="例如：客户提交申请后由直属上级审核，再由记录负责人复核" />
          </label>
          <div class="ai-flow-actions"><a-button :loading="loading" :disabled="!agentId || !instruction.trim()" @click="recognize">生成建议</a-button></div>
        </template>
      </template>
      <div v-if="candidate" class="ai-flow-candidate">
        <a-alert :type="candidate.outcome === 'READY' ? 'info' : 'warning'" show-icon :message="candidate.message" />
        <p>整体识别置信度 {{ Math.round(candidate.confidence * 100) }}%。请核对流程名称、审批人来源与顺序；保存后仍需在设计页检查并单独发布。</p>
        <a-alert v-if="candidate.confidence < 0.7 && !candidate.businessWritten" type="warning" show-icon message="识别置信度较低，请逐项核对审批步骤。" />
        <label>流程名称<a-input v-model:value="flowName" :maxlength="200" :disabled="candidate.businessWritten" /></label>
        <label>流程说明<a-textarea v-model:value="description" :rows="2" :maxlength="1000" :disabled="candidate.businessWritten" /></label>
        <div v-for="step in steps" :key="step.index" class="ai-flow-step">
          <strong>第 {{ step.index + 1 }} 步审批</strong>
          <label>步骤名称<a-input v-model:value="step.name" :maxlength="200" :disabled="candidate.businessWritten" /></label>
          <label>审批人来源<a-select v-model:value="step.assigneeType" :options="assigneeTypes" :disabled="candidate.businessWritten" /></label>
          <a-checkbox v-if="(step.confidence < 0.7 || candidate.confidence < 0.7) && !candidate.businessWritten"
            v-model:checked="reviewed[step.index]">此步骤置信度较低，我已核对并选择审批人来源</a-checkbox>
        </div>
        <template v-if="!candidate.businessWritten">
          <a-checkbox v-model:checked="confirmed">我已核对流程步骤，确认保存为草稿</a-checkbox>
          <div class="ai-flow-actions"><a-button type="primary" :loading="saving" :disabled="!canSave" @click="save">确认保存流程草稿</a-button></div>
        </template>
        <a-alert v-else type="success" show-icon :message="`流程草稿 #${candidate.flowId} 已保存，请在设计器检查并发布`" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.ai-flow-panel { margin: 14px 0; }
.ai-flow-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.ai-flow-heading > div, .ai-flow-body, .ai-flow-candidate, .ai-flow-body label { display: grid; gap: 8px; }
.ai-flow-heading small { color: #64748b; }
.ai-flow-body, .ai-flow-candidate { margin-top: 16px; }
.ai-flow-actions { display: flex; justify-content: flex-end; }
.ai-flow-candidate p { margin: 0; color: #475569; }
.ai-flow-step { display: grid; gap: 10px; padding: 12px; border: 1px solid #e2e8f0; border-radius: 8px; }
</style>
