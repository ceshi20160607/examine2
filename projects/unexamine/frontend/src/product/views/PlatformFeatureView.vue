<script setup lang="ts">
import { AppstoreOutlined, ArrowRightOutlined, RobotOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import PlatformShell from '../components/PlatformShell.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import PlatformAiWritePanel from '../components/PlatformAiWritePanel.vue'
import { hasPlatformAdminCapability } from '../permissions'
import { platformContext, platformTokens } from '../session'

interface PlatformAgent {
  id: number
  name: string
  capabilities: string[]
}

interface PlatformAnswer {
  conversationId: number
  executionId: number
  outcome: string
  answer: string
  errorCode: string | null
  resourceType: string
  sources: Array<{ id: number; name: string; status: string }>
}

const labels: Record<string, string> = {
  PLATFORM_SYSTEM: '可进入的系统', PLATFORM_TASK: '平台待办',
  PLATFORM_MESSAGE: '平台消息', PLATFORM_LOG: '本人平台日志',
  PLATFORM_FLOW: '平台流程', PLATFORM_HEALTH: '平台健康', MODEL_QUOTA: '模型额度',
}
const router = useRouter()
const agents = ref<PlatformAgent[]>([])
const agentId = ref<number | undefined>()
const resourceType = ref<string | undefined>()
const question = ref('')
const answer = ref<PlatformAnswer | null>(null)
const loading = ref(false)
const submitting = ref(false)
const error = ref('')
const canConfigure = computed(() => hasPlatformAdminCapability(platformContext.value?.permissions, 'ai'))
const selectedAgent = computed(() => agents.value.find(item => item.id === agentId.value))
const resources = computed(() => selectedAgent.value?.capabilities.map(value => ({
  value, label: labels[value] || value,
})) || [])
const defaultResource = () => resources.value.find(item => item.value === 'PLATFORM_SYSTEM')?.value || resources.value[0]?.value

async function load() {
  if (!platformTokens.value?.accessToken) return
  loading.value = true
  error.value = ''
  try {
    agents.value = await api<PlatformAgent[]>('/api/platform/ai/agents', {}, platformTokens.value.accessToken)
    if (!agents.value.some(item => item.id === agentId.value)) agentId.value = agents.value[0]?.id
    if (!resources.value.some(item => item.value === resourceType.value)) resourceType.value = defaultResource()
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '平台 Agent 加载失败'
  } finally {
    loading.value = false
  }
}

async function ask() {
  if (!platformTokens.value?.accessToken || !agentId.value || !resourceType.value || !question.value.trim()) return
  submitting.value = true
  answer.value = null
  error.value = ''
  try {
    answer.value = await api<PlatformAnswer>('/api/platform/ai/queries', {
      method: 'POST',
      body: JSON.stringify({ agentId: agentId.value, resourceType: resourceType.value,
        question: question.value.trim() }),
    }, platformTokens.value.accessToken)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '平台查询失败'
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<template>
  <PlatformShell>
    <ProductPageHeader kicker="平台工作区" title="智能助手" description="查询当前平台范围的授权系统、待办、消息、流程和运行信息。">
      <template #actions><a-button @click="router.push('/platform')"><AppstoreOutlined />选择系统</a-button></template>
    </ProductPageHeader>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-spin :spinning="loading">
      <section v-if="agents.length" class="panel-card">
        <h2><RobotOutlined /> 平台 Agent 对话</h2>
        <p>选择已发布 Agent 和查询范围，回答只使用当前账号可访问的平台来源。</p>
        <div class="platform-ai-query-controls">
          <a-select v-model:value="agentId" aria-label="选择平台 Agent" :options="agents.map(item => ({ value: item.id, label: item.name }))"
            @change="resourceType = defaultResource()" />
          <a-select v-model:value="resourceType" aria-label="选择平台查询范围" :options="resources" />
        </div>
        <a-textarea v-model:value="question" aria-label="平台问题" :rows="3" :maxlength="4000"
          placeholder="例如：我可以进入哪些系统？" @keydown.ctrl.enter="ask" />
        <div class="platform-ai-query-actions">
          <span>查询系统业务记录时，请先切换到目标系统。</span>
          <a-button type="primary" :loading="submitting" :disabled="!question.trim() || !resourceType" @click="ask">发送</a-button>
        </div>
      </section>
      <section v-else class="panel-card platform-ai-entry">
        <div class="platform-ai-entry__icon"><RobotOutlined /></div>
        <div><h2>暂无已发布的平台 Agent</h2><p>管理员发布平台 Agent 后，这里即可查询当前账号有权访问的平台信息。</p></div>
        <a-button v-if="canConfigure" type="link" @click="router.push('/platform/admin/ai')">前往配置 <ArrowRightOutlined /></a-button>
      </section>
    </a-spin>
    <section v-if="answer" class="panel-card platform-ai-answer">
      <h2>回答 <a-tag :color="answer.outcome === 'SUCCEEDED' ? 'green' : 'orange'">{{ answer.outcome === 'SUCCEEDED' ? '已完成' : '模型不可用' }}</a-tag></h2>
      <p>{{ answer.answer }}</p>
      <div v-if="answer.sources.length" class="platform-ai-answer__sources">
        <strong>本次来源：{{ labels[answer.resourceType] || answer.resourceType }}</strong>
        <ul><li v-for="source in answer.sources.slice(0, 8)" :key="source.id">{{ source.name }} · {{ source.status }}</li></ul>
        <span v-if="answer.sources.length > 8">共 {{ answer.sources.length }} 项，仅显示前 8 项。</span>
      </div>
    </section>
    <PlatformAiWritePanel v-if="agents.some(item => item.capabilities.some(capability =>
      ['PLATFORM_TASK', 'PLATFORM_MESSAGE', 'PLATFORM_LOG'].includes(capability)))" :agents="agents" />
    <section class="panel-card platform-ai-entry">
      <div class="platform-ai-entry__icon"><AppstoreOutlined /></div>
      <div><h2>处理系统业务</h2><p>进入有权限的目标系统后，使用系统 Agent 查询或确认保存模块业务数据。</p></div>
      <a-button type="link" @click="router.push('/platform')">选择系统 <ArrowRightOutlined /></a-button>
    </section>
  </PlatformShell>
</template>
