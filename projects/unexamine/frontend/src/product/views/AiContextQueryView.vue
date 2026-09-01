<script setup lang="ts">
import { LinkOutlined, ReloadOutlined, RobotOutlined, SafetyCertificateOutlined, SendOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import { aiOutcomePresentation, aiScopeSummary } from '../ai-context'
import { productDateTime } from '../presentation'
import { systemContext, systemTokens } from '../session'
import type { AiQueryConversationDetail, AiQueryOverview, AiQueryResult } from '../types'

const props = withDefaults(defineProps<{
  entryType?: 'SYSTEM_AI' | 'RIGHT_ASSISTANT' | 'MODULE_PAGE' | 'RECORD_DETAIL' | 'WORK_PAGE'
  initialModuleCode?: string
}>(), { entryType: 'SYSTEM_AI', initialModuleCode: '' })

const router = useRouter()
const loading = ref(false)
const asking = ref(false)
const error = ref('')
const overview = ref<AiQueryOverview>({ agents: [], conversations: [] })
const detail = ref<AiQueryConversationDetail>()
const result = ref<AiQueryResult>()
const continueConversation = ref(true)
const form = reactive({
  agentId: undefined as number | undefined,
  moduleCode: props.initialModuleCode,
  question: '',
  fieldText: '',
  tenantScope: 'ALL' as 'ALL' | 'OWN' | 'SHARED',
})

const selectedAgent = computed(() => overview.value.agents.find((agent) => agent.agentId === form.agentId))
const outcome = computed(() => aiOutcomePresentation(result.value?.outcome))
const fields = computed(() => form.fieldText.split(',').map((value) => value.trim()).filter(Boolean))

async function loadOverview() {
  if (!systemTokens.value?.accessToken) return
  loading.value = true
  error.value = ''
  try {
    overview.value = await api<AiQueryOverview>('/api/ai', {}, systemTokens.value.accessToken)
    if (!overview.value.agents.some((agent) => agent.agentId === form.agentId)) {
      form.agentId = overview.value.agents[0]?.agentId
    }
    chooseModule()
  } catch (reason) {
    error.value = readable(reason)
  } finally {
    loading.value = false
  }
}

function chooseModule() {
  const modules = selectedAgent.value?.moduleCodes || []
  if (!modules.includes(form.moduleCode)) form.moduleCode = modules[0] || ''
}

watch(() => form.agentId, chooseModule)

async function ask() {
  if (!systemTokens.value?.accessToken || !form.agentId || !form.moduleCode || !form.question.trim()) return
  asking.value = true
  error.value = ''
  try {
    result.value = await api<AiQueryResult>('/api/ai/queries', {
      method: 'POST',
      body: JSON.stringify({
        agentId: form.agentId,
        conversationId: continueConversation.value ? detail.value?.conversation.id ?? null : null,
        question: form.question.trim(),
        requestedFieldCodes: fields.value,
        requestedTenantId: systemContext.value?.tenantId,
        entryContext: {
          entryType: props.entryType,
          moduleCode: form.moduleCode,
          sourcePath: `/systems/${systemContext.value?.systemId}`,
          lifecycleState: 'ACTIVE',
          tenantScope: form.tenantScope,
          search: '',
          filters: [],
          sortField: 'updatedAt',
          sortDirection: 'DESC',
          pageSize: 5,
        },
      }),
    }, systemTokens.value.accessToken)
    await Promise.all([loadConversation(result.value.conversationId), refreshHistory()])
  } catch (reason) {
    error.value = readable(reason)
  } finally {
    asking.value = false
  }
}

async function loadConversation(id: number) {
  if (!systemTokens.value?.accessToken) return
  detail.value = await api<AiQueryConversationDetail>(`/api/ai/conversations/${id}`, {}, systemTokens.value.accessToken)
  const persisted = [...detail.value.messages].reverse().find((message) => message.role === 'ASSISTANT')
  if (persisted?.structuredContent?.conversationId) result.value = persisted.structuredContent as unknown as AiQueryResult
}

async function refreshHistory() {
  if (!systemTokens.value?.accessToken) return
  overview.value = await api<AiQueryOverview>('/api/ai', {}, systemTokens.value.accessToken)
}

async function openConversation(id: number) {
  error.value = ''
  try {
    await loadConversation(id)
    const moduleCode = String(detail.value?.conversation.entryContext.moduleCode || '')
    if (moduleCode) form.moduleCode = moduleCode
    form.agentId = detail.value?.conversation.agentId
  } catch (reason) {
    error.value = readable(reason)
  }
}

function newConversation() {
  detail.value = undefined
  result.value = undefined
  form.question = ''
}

function readable(reason: unknown) {
  return reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（${reason.traceId}）` : ''}` : '智能查询失败'
}

onMounted(loadOverview)
</script>

<template>
  <div class="ai-query-workspace">
    <div class="page-heading system-heading">
      <div><p class="eyebrow">智能助手 · 基于当前权限</p><h1>业务智能助手</h1><p>回答与业务页面使用相同的系统、工作空间、字段和数据权限；每次结果都保留可复核的业务来源。</p></div>
      <div class="heading-actions"><a-button @click="newConversation">新会话</a-button><a-button :loading="loading" @click="loadOverview"><ReloadOutlined />重新读取</a-button></div>
    </div>

    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-spin :spinning="loading">
      <div class="ai-query-layout">
        <aside class="ai-query-history panel-card">
          <div class="panel-title"><strong>持久化会话</strong><span>{{ overview.conversations.length }} 条</span></div>
          <a-empty v-if="!overview.conversations.length" description="还没有查询记录" />
          <button v-for="conversation in overview.conversations" :key="conversation.id"
            :class="['ai-history-row', { active: detail?.conversation.id === conversation.id }]"
            @click="openConversation(conversation.id)">
            <strong>{{ conversation.title }}</strong><small>{{ conversation.agentName || '智能助手' }} · {{ productDateTime(conversation.updatedAt) }}</small>
          </button>
        </aside>

        <section class="ai-query-main">
          <div class="panel-card ai-query-compose">
            <div class="ai-query-boundary"><SafetyCertificateOutlined /><span><strong>回答遵循当前权限</strong><small>智能助手不能扩大当前成员的字段权限或数据范围</small></span></div>
            <div class="form-grid">
              <a-form-item label="智能助手" required><a-select v-model:value="form.agentId" :options="overview.agents.map((agent) => ({ value: agent.agentId, label: agent.agentName }))" placeholder="暂无可用智能助手" /></a-form-item>
              <a-form-item label="业务模块" required><a-select v-model:value="form.moduleCode" :options="(selectedAgent?.moduleCodes || []).map((code) => ({ value: code, label: code }))" /></a-form-item>
            </div>
            <div class="form-grid">
              <a-form-item label="限定回答字段（可选）"><a-input v-model:value="form.fieldText" placeholder="留空使用当前可见字段；多个字段编码用逗号分隔" /></a-form-item>
              <a-form-item label="数据范围"><a-select v-model:value="form.tenantScope" :options="[{ value: 'ALL', label: '当前工作空间及获权共享' }, { value: 'OWN', label: '仅当前工作空间' }, { value: 'SHARED', label: '仅共享给我' }]" /></a-form-item>
            </div>
            <a-form-item label="向当前页面提问" required><a-textarea v-model:value="form.question" :rows="3" :maxlength="2000" show-count placeholder="例如：当前页面有多少条客户记录？" /></a-form-item>
            <div class="ai-query-actions"><a-checkbox v-model:checked="continueConversation">继续当前会话</a-checkbox><span class="spacer" /><a-button type="primary" :loading="asking" :disabled="!form.agentId || !form.moduleCode || !form.question.trim()" @click="ask"><SendOutlined />发送问题</a-button></div>
          </div>

          <div v-if="result" class="panel-card ai-query-answer">
            <div class="panel-title"><strong><RobotOutlined />回答</strong><a-tag :color="outcome.color">{{ outcome.label }}</a-tag></div>
            <a-alert :type="outcome.alert" show-icon :message="result.answer" :description="result.errorCode ? (result.retryable ? '本次未完成，可以稍后重试。' : '本次未生成可用回答。') : '回答和依据已保存。'" />
            <a-descriptions class="ai-scope-card" bordered size="small" :column="1">
              <a-descriptions-item label="上下文与权限范围">{{ aiScopeSummary(result.scope) }}</a-descriptions-item>
              <a-descriptions-item label="字段范围">{{ result.scope.fieldCodes.join('、') || '无动态字段' }}</a-descriptions-item>
              <a-descriptions-item label="指标口径">{{ result.metricDefinition || '本次未执行业务查询，未形成指标口径。' }}</a-descriptions-item>
              <a-descriptions-item label="保存时间">{{ productDateTime(result.persistedAt) }}</a-descriptions-item>
            </a-descriptions>
            <div v-if="result.sources.length" class="ai-source-list">
              <div class="panel-title"><strong>获权来源</strong><span>点击回到普通业务页复核</span></div>
              <button v-for="source in result.sources" :key="source.recordId" class="ai-source-row" @click="router.push(source.path)">
                <span><strong>{{ source.title }}</strong><small>{{ source.recordNumber || '业务记录' }} · {{ Object.keys(source.fields).length }} 个可见字段</small></span><LinkOutlined />
              </button>
            </div>
          </div>

          <div v-if="detail" class="panel-card ai-query-transcript">
            <div class="panel-title"><strong>会话记录</strong><span>{{ detail.conversation.agentName || '智能助手' }}</span></div>
            <article v-for="message in detail.messages" :key="message.id" :class="['ai-message', `ai-message--${message.role.toLowerCase()}`]">
              <a-tag>{{ message.role === 'USER' ? '用户' : '智能助手' }}</a-tag><p>{{ message.content }}</p><small>{{ productDateTime(message.createdAt) }}<template v-if="message.errorCode"> · 本条未完成</template></small>
            </article>
          </div>
        </section>
      </div>
    </a-spin>
  </div>
</template>
