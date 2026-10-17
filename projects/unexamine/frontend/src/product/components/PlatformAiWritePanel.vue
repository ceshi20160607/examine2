<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import { platformTokens } from '../session'

interface AgentOption { id: number; name: string; capabilities: string[] }
interface MemberOption { memberId: number; accountId: number; name: string }
interface TemplateOption { code: string; name: string; requiredVariables: string[] }
interface WriteOptions { currentAccountId: number; currentMemberId: number | null; members: MemberOption[]; templates: TemplateOption[] }
interface Candidate {
  pendingWriteId: number; targetType: string; status: string; outcome: string
  message: string; errorCode: string | null; fields: Record<string, unknown>
  confidence: number; businessWritten: boolean; version: number
  resultId: number | null; resultPath: string | null
}

const props = defineProps<{ agents: AgentOption[] }>()
const router = useRouter()
const labels: Record<string, string> = {
  PLATFORM_TASK: '平台任务', PLATFORM_MESSAGE: '平台消息', PLATFORM_LOG: '平台工作日志',
}
const targetType = ref('PLATFORM_TASK')
const agentId = ref<number>()
const inputText = ref('')
const candidate = ref<Candidate | null>(null)
const options = ref<WriteOptions>({ currentAccountId: 0, currentMemberId: null, members: [], templates: [] })
const title = ref('')
const description = ref('')
const priority = ref('NORMAL')
const ownerAccountId = ref<number>()
const dueTime = ref('')
const today = () => {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
const workDate = ref(today())
const completedText = ref('')
const nextPlanText = ref('')
const templateCode = ref<string>()
const recipientMemberIds = ref<number[]>([])
const messageVariables = reactive<Record<string, string>>({})
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const confirmed = ref(false)
const reviewedLowConfidence = ref(false)

const eligibleAgents = computed(() => props.agents.filter(item => item.capabilities.includes(targetType.value)))
const selectedTemplate = computed(() => options.value.templates.find(item => item.code === templateCode.value))
watch([targetType, () => props.agents], () => {
  if (!eligibleAgents.value.some(item => item.id === agentId.value)) agentId.value = eligibleAgents.value[0]?.id
  candidate.value = null
}, { immediate: true })

async function loadOptions() {
  if (!platformTokens.value?.accessToken) return
  try {
    options.value = await api<WriteOptions>('/api/platform/ai/write-candidates/options', {}, platformTokens.value.accessToken)
    ownerAccountId.value = options.value.currentAccountId
    recipientMemberIds.value = options.value.currentMemberId ? [options.value.currentMemberId] : []
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '平台写入选项加载失败'
  }
}

function fillFromCandidate(item: Candidate) {
  const fields = item.fields || {}
  title.value = typeof fields.title === 'string' ? fields.title : ''
  description.value = typeof fields.description === 'string' ? fields.description : ''
  priority.value = typeof fields.priority === 'string' ? fields.priority : 'NORMAL'
  ownerAccountId.value = typeof fields.ownerAccountId === 'number' ? fields.ownerAccountId : options.value.currentAccountId
  dueTime.value = typeof fields.dueTime === 'string' ? fields.dueTime.slice(0, 16) : ''
  workDate.value = typeof fields.workDate === 'string' ? fields.workDate : today()
  completedText.value = typeof fields.completedText === 'string' ? fields.completedText : ''
  nextPlanText.value = typeof fields.nextPlanText === 'string' ? fields.nextPlanText : ''
  templateCode.value = typeof fields.templateCode === 'string' ? fields.templateCode : options.value.templates[0]?.code
  recipientMemberIds.value = Array.isArray(fields.recipientMemberIds)
    ? fields.recipientMemberIds.filter((value): value is number => typeof value === 'number')
    : options.value.currentMemberId ? [options.value.currentMemberId] : []
  Object.keys(messageVariables).forEach(key => delete messageVariables[key])
  if (fields.variables && typeof fields.variables === 'object' && !Array.isArray(fields.variables)) {
    Object.entries(fields.variables as Record<string, unknown>).forEach(([key, value]) => {
      if (typeof value === 'string') messageVariables[key] = value
    })
  }
}

async function recognize() {
  if (!platformTokens.value?.accessToken || !agentId.value || !inputText.value.trim()) return
  loading.value = true
  error.value = ''
  candidate.value = null
  confirmed.value = false
  reviewedLowConfidence.value = false
  try {
    const item = await api<Candidate>('/api/platform/ai/write-candidates', {
      method: 'POST', body: JSON.stringify({ agentId: agentId.value,
        targetType: targetType.value, inputText: inputText.value.trim() }),
    }, platformTokens.value.accessToken)
    candidate.value = item
    fillFromCandidate(item)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '识别失败'
  } finally {
    loading.value = false
  }
}

function confirmedFields(): Record<string, unknown> {
  if (targetType.value === 'PLATFORM_TASK') {
    return { title: title.value.trim(), description: description.value.trim(), priority: priority.value,
      ownerAccountId: ownerAccountId.value, ...(dueTime.value ? { dueTime: dueTime.value } : {}) }
  }
  if (targetType.value === 'PLATFORM_LOG') {
    return { workDate: workDate.value, title: title.value.trim(),
      completedText: completedText.value.trim(), nextPlanText: nextPlanText.value.trim() }
  }
  const variables: Record<string, string> = {}
  for (const name of selectedTemplate.value?.requiredVariables || []) variables[name] = (messageVariables[name] || '').trim()
  return { templateCode: templateCode.value, recipientMemberIds: recipientMemberIds.value, variables }
}

const canConfirm = computed(() => {
  if (!candidate.value || candidate.value.businessWritten || !confirmed.value) return false
  if (candidate.value.confidence < 0.7 && !reviewedLowConfidence.value) return false
  if (targetType.value === 'PLATFORM_TASK') return !!title.value.trim() && !!ownerAccountId.value
  if (targetType.value === 'PLATFORM_LOG') return !!workDate.value && !!title.value.trim()
  return !!selectedTemplate.value && recipientMemberIds.value.length > 0
    && (selectedTemplate.value?.requiredVariables || []).every(name => !!messageVariables[name]?.trim())
})

async function save() {
  if (!platformTokens.value?.accessToken || !candidate.value || !canConfirm.value) return
  saving.value = true
  error.value = ''
  try {
    candidate.value = await api<Candidate>(`/api/platform/ai/write-candidates/${candidate.value.pendingWriteId}/confirm`, {
      method: 'POST', body: JSON.stringify({ expectedVersion: candidate.value.version,
        confirmed: true, fields: confirmedFields(),
        reviewedLowConfidence: reviewedLowConfidence.value }),
    }, platformTokens.value.accessToken)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '确认保存失败'
  } finally {
    saving.value = false
  }
}

onMounted(loadOptions)
</script>

<template>
  <section class="panel-card platform-ai-write">
    <h2>对话录入平台数据</h2>
    <p>Agent 先识别待办、消息或工作日志；检查候选并明确确认后才保存。</p>
    <a-alert v-if="error" type="error" show-icon :message="error" class="write-alert" />
    <div class="write-row">
      <a-select v-model:value="targetType" aria-label="写入类型" :options="Object.entries(labels).map(([value, label]) => ({ value, label }))" />
      <a-select v-model:value="agentId" aria-label="选择平台 Agent" :options="eligibleAgents.map(item => ({ value: item.id, label: item.name }))"
        placeholder="选择已授权 Agent" />
    </div>
    <a-textarea v-model:value="inputText" aria-label="描述待录入内容" :rows="3" :maxlength="12000"
      placeholder="描述要创建的平台任务、消息或工作日志" />
    <div class="write-actions"><a-button :loading="loading" :disabled="!agentId || !inputText.trim()" @click="recognize">识别候选</a-button></div>

    <div v-if="candidate" class="write-candidate">
      <a-alert :type="candidate.outcome === 'READY' ? 'info' : 'warning'" show-icon :message="candidate.message" />
      <p class="write-hint">识别置信度 {{ Math.round(candidate.confidence * 100) }}%。请逐项核对，缺失内容可直接补充。</p>
      <template v-if="targetType === 'PLATFORM_TASK'">
        <label>任务标题 <a-input v-model:value="title" :maxlength="500" /></label>
        <label>任务描述 <a-textarea v-model:value="description" :rows="3" :maxlength="20000" /></label>
        <div class="write-row">
          <label>优先级 <a-select v-model:value="priority" :options="['LOW', 'NORMAL', 'HIGH', 'URGENT'].map(value => ({ value, label: value }))" /></label>
          <label>负责人 <a-select v-model:value="ownerAccountId" :options="options.members.map(item => ({ value: item.accountId, label: item.name }))" /></label>
        </div>
        <label>截止时间 <input v-model="dueTime" type="datetime-local" class="write-native-input" /></label>
      </template>
      <template v-else-if="targetType === 'PLATFORM_MESSAGE'">
        <label>已发布模板 <a-select v-model:value="templateCode" :options="options.templates.map(item => ({ value: item.code, label: item.name }))" /></label>
        <label>接收人 <a-select v-model:value="recipientMemberIds" mode="multiple" :options="options.members.map(item => ({ value: item.memberId, label: item.name }))" /></label>
        <label v-for="name in selectedTemplate?.requiredVariables || []" :key="name">{{ name }}
          <a-input v-model:value="messageVariables[name]" :maxlength="2000" />
        </label>
        <a-alert v-if="!options.templates.length" type="warning" show-icon message="当前平台没有可用的已发布消息模板，请先发布模板。" />
      </template>
      <template v-else>
        <label>工作日期 <input v-model="workDate" type="date" class="write-native-input" /></label>
        <label>日志标题 <a-input v-model:value="title" :maxlength="500" /></label>
        <label>完成内容 <a-textarea v-model:value="completedText" :rows="3" :maxlength="20000" /></label>
        <label>下一步计划 <a-textarea v-model:value="nextPlanText" :rows="2" :maxlength="20000" /></label>
      </template>
      <template v-if="!candidate.businessWritten">
        <a-checkbox v-if="candidate.confidence < 0.7" v-model:checked="reviewedLowConfidence">识别置信度较低，我已重新核对并选择字段值</a-checkbox>
        <a-checkbox v-model:checked="confirmed">我已核对字段，确认创建这条{{ labels[targetType] }}</a-checkbox>
        <div class="write-actions"><a-button type="primary" :loading="saving" :disabled="!canConfirm" @click="save">确认保存</a-button></div>
      </template>
      <div v-else class="write-success">
        <a-alert type="success" show-icon :message="`已创建 ${labels[targetType]} #${candidate.resultId}`" />
        <a-button v-if="candidate.resultPath" type="link" @click="router.push(candidate.resultPath)">打开结果</a-button>
      </div>
    </div>
  </section>
</template>

<style scoped>
.platform-ai-write { display: grid; gap: 12px; }
.platform-ai-write h2, .platform-ai-write p { margin: 0; }
.write-row { display: flex; gap: 12px; }
.write-row > * { flex: 1; min-width: 0; }
.write-actions { display: flex; justify-content: flex-end; }
.write-candidate { display: grid; gap: 12px; border-top: 1px solid #e5e7eb; padding-top: 16px; }
.write-candidate label { display: grid; gap: 6px; }
.write-native-input { width: 100%; min-height: 32px; padding: 4px 11px; border: 1px solid #d9d9d9; border-radius: 6px; }
.write-hint { color: #6b7280; }
.write-success { display: flex; align-items: center; justify-content: space-between; }
</style>
