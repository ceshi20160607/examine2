<script setup lang="ts">
import { LinkOutlined, ReloadOutlined, RobotOutlined, SafetyCertificateOutlined, SendOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import { aiOutcomePresentation, aiScopeSummary } from '../ai-context'
import { productDateTime, userFacingRecordNumber } from '../presentation'
import { systemContext, systemTokens } from '../session'
import type { AiQueryConversationDetail, AiQueryOverview, AiQueryResult, AiQuerySource, PersonalDashboardOverview, RuntimeModuleCatalogItem, RuntimeModuleConfiguration } from '../types'

const props = withDefaults(defineProps<{
  entryType?: 'SYSTEM_AI' | 'RIGHT_ASSISTANT' | 'MODULE_PAGE' | 'RECORD_DETAIL' | 'WORK_PAGE'
  initialModuleCode?: string
}>(), { entryType: 'SYSTEM_AI', initialModuleCode: '' })

const router = useRouter()
const loading = ref(false)
const asking = ref(false)
const error = ref('')
const sourceError = ref('')
const overview = ref<AiQueryOverview>({ agents: [], conversations: [] })
const dataSources = ref<PersonalDashboardOverview['dataSources']>([])
const modules = ref<RuntimeModuleCatalogItem[]>([])
const moduleConfiguration = ref<RuntimeModuleConfiguration>()
const detail = ref<AiQueryConversationDetail>()
const result = ref<AiQueryResult>()
const continueConversation = ref(true)
const form = reactive({
  mode: 'list' as 'list' | 'report',
  agentId: undefined as number | undefined,
  moduleCode: props.initialModuleCode,
  dataSourceId: undefined as number | undefined,
  question: '',
  fieldCodes: [] as string[],
  tenantScope: 'ALL' as 'ALL' | 'OWN' | 'SHARED',
})

const selectedAgent = computed(() => overview.value.agents.find((agent) => agent.agentId === form.agentId))
const outcome = computed(() => aiOutcomePresentation(result.value?.outcome))
const fieldOptions = computed(() => moduleConfiguration.value?.configuration.fields
  .filter((field) => field.access?.readable)
  .map((field) => ({ value: field.code, label: field.name })) || [])
const reportSources = computed(() => dataSources.value.filter((source) => source.sourceType === 'MODULE_REPORT'))
const reportSourceName = computed(() => reportSources.value.find((source) => source.id === result.value?.report?.dataSourceId)?.name
  || `数据源 #${result.value?.report?.dataSourceId || '—'}`)

async function loadOverview() {
  if (!systemTokens.value?.accessToken) return
  loading.value = true
  error.value = ''
  sourceError.value = ''
  const [query, catalog, moduleCatalog] = await Promise.allSettled([
    api<AiQueryOverview>('/api/ai', {}, systemTokens.value.accessToken),
    api<PersonalDashboardOverview>('/api/analytics/personal', {}, systemTokens.value.accessToken),
    api<RuntimeModuleCatalogItem[]>('/api/runtime/modules', {}, systemTokens.value.accessToken),
  ])
  if (query.status === 'fulfilled') {
    overview.value = query.value
    if (!overview.value.agents.some((agent) => agent.agentId === form.agentId)) {
      form.agentId = overview.value.agents[0]?.agentId
    }
    chooseModule()
  } else {
    error.value = readable(query.reason)
  }
  if (catalog.status === 'fulfilled') dataSources.value = catalog.value.dataSources
  else sourceError.value = readable(catalog.reason)
  if (moduleCatalog.status === 'fulfilled') modules.value = moduleCatalog.value
  loading.value = false
}

function chooseModule() {
  const modules = selectedAgent.value?.moduleCodes || []
  if (!modules.includes(form.moduleCode)) form.moduleCode = modules[0] || ''
}

watch(() => form.agentId, chooseModule)
watch(() => form.moduleCode, async (code) => {
  form.fieldCodes = []
  moduleConfiguration.value = undefined
  if (!code || !systemTokens.value?.accessToken) return
  try {
    const loaded = await api<RuntimeModuleConfiguration>(`/api/runtime/modules/${code}/configuration`, {}, systemTokens.value.accessToken)
    if (form.moduleCode === code) moduleConfiguration.value = loaded
  } catch {
    // 查询本身仍由服务端按 Agent 和成员权限裁剪；配置读取失败时保留默认字段范围。
  }
}, { immediate: true })

function moduleName(code: string) {
  return modules.value.find((item) => item.moduleCode === code)?.moduleName || code
}

function scopeFieldName(code: string) {
  return result.value?.sources.find((source) => source.fieldPresentation?.[code])?.fieldPresentation?.[code]?.name
    || moduleConfiguration.value?.configuration.fields.find((field) => field.code === code)?.name || code
}

function sourceFields(source: AiQuerySource) {
  return Object.entries(source.fields).map(([code, value]) => ({
    code,
    name: source.fieldPresentation?.[code]?.name || scopeFieldName(code),
    value: source.fieldPresentation?.[code]?.displayValue || displayMetric(value),
  }))
}

function displayMetric(value: unknown): string {
  if (value === null || value === undefined || value === '') return '—'
  if (typeof value === 'number') return value.toLocaleString('zh-CN')
  if (typeof value === 'string') return value
  if (typeof value === 'boolean') return value ? '是' : '否'
  return JSON.stringify(value)
}

function returnToModule() {
  const moduleCode = result.value?.scope.moduleCode || form.moduleCode
  void router.push({ path: `/systems/${systemContext.value?.systemId}`, query: { workspace: 'runtime', module: moduleCode } })
}

async function ask() {
  if (!systemTokens.value?.accessToken || !form.agentId || !form.moduleCode || !form.question.trim()
    || (form.mode === 'report' && !form.dataSourceId)) return
  asking.value = true
  error.value = ''
  try {
    result.value = await api<AiQueryResult>('/api/ai/queries', {
      method: 'POST',
      body: JSON.stringify({
        agentId: form.agentId,
        // 统计只提交已发布来源 ID，计算口径和授权由服务端复核。
        dataSourceId: form.mode === 'report' ? form.dataSourceId : undefined,
        conversationId: continueConversation.value ? detail.value?.conversation.id ?? null : null,
        question: form.question.trim(),
        requestedFieldCodes: form.mode === 'report' ? [] : form.fieldCodes,
        requestedTenantId: systemContext.value?.tenantId,
        entryContext: {
          entryType: props.entryType,
          moduleCode: form.moduleCode,
          sourcePath: `/systems/${systemContext.value?.systemId}`,
          lifecycleState: 'ACTIVE',
          tenantScope: form.mode === 'report' ? 'ALL' : form.tenantScope,
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
  result.value = persisted?.structuredContent?.conversationId ? persisted.structuredContent as unknown as AiQueryResult : undefined
}

async function refreshHistory() {
  if (!systemTokens.value?.accessToken) return
  overview.value = await api<AiQueryOverview>('/api/ai', {}, systemTokens.value.accessToken)
}

async function openConversation(id: number) {
  error.value = ''
  try {
    await loadConversation(id)
    form.mode = result.value?.report ? 'report' : 'list'
    form.dataSourceId = result.value?.report?.dataSourceId
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
            <a-form-item label="查询方式"><a-segmented v-model:value="form.mode" :options="[{ value: 'list', label: '普通列表' }, { value: 'report', label: '已发布统计趋势' }]" /></a-form-item>
            <div class="form-grid">
              <a-form-item label="智能助手" required><a-select v-model:value="form.agentId" :options="overview.agents.map((agent) => ({ value: agent.agentId, label: agent.agentName }))" placeholder="暂无可用智能助手" /></a-form-item>
              <a-form-item label="业务模块" required><a-select v-model:value="form.moduleCode" :options="(selectedAgent?.moduleCodes || []).map((code) => ({ value: code, label: moduleName(code) }))" /></a-form-item>
            </div>
            <template v-if="form.mode === 'report'">
              <a-alert v-if="sourceError" type="warning" show-icon :message="`已发布统计来源读取失败：${sourceError}`" />
              <a-form-item label="已发布统计来源" required>
                <a-select v-model:value="form.dataSourceId" :options="reportSources.map((source) => ({ value: source.id, label: source.name }))" placeholder="选择可用的业务统计或趋势来源" />
              </a-form-item>
              <a-alert type="info" show-icon message="统计口径取自已发布来源，结果仍按当前成员权限过滤；来源所属模块和智能助手授权必须匹配。" />
            </template>
            <div v-if="form.mode === 'list'" class="form-grid">
              <a-form-item label="限定回答字段（可选）"><a-select v-model:value="form.fieldCodes" mode="multiple" :options="fieldOptions" placeholder="留空使用当前可见字段" /></a-form-item>
              <a-form-item label="数据范围"><a-select v-model:value="form.tenantScope" :options="[{ value: 'ALL', label: '当前工作空间及获权共享' }, { value: 'OWN', label: '仅当前工作空间' }, { value: 'SHARED', label: '仅共享给我' }]" /></a-form-item>
            </div>
            <a-form-item label="向当前页面提问" required><a-textarea v-model:value="form.question" :rows="3" :maxlength="2000" show-count :placeholder="form.mode === 'report' ? '例如：按月看当前模块的业务趋势。' : '例如：列出我负责的业务记录。'" /></a-form-item>
            <div class="ai-query-actions"><a-checkbox v-model:checked="continueConversation">继续当前会话</a-checkbox><span class="spacer" /><a-button type="primary" :loading="asking" :disabled="!form.agentId || !form.moduleCode || !form.question.trim() || (form.mode === 'report' && !form.dataSourceId)" @click="ask"><SendOutlined />发送问题</a-button></div>
          </div>

          <div v-if="result" class="panel-card ai-query-answer">
            <div class="panel-title"><strong><RobotOutlined />回答</strong><a-tag :color="outcome.color">{{ outcome.label }}</a-tag></div>
            <a-alert :type="outcome.alert" show-icon :message="result.answer" :description="result.errorCode ? (result.retryable ? '本次未完成，可以稍后重试。' : '本次未生成可用回答。') : '回答和依据已保存。'" />
            <section v-if="result.report" class="ai-source-list">
              <div class="panel-title"><strong>业务统计结果 · {{ reportSourceName }}</strong><span>服务端按当前权限计算</span></div>
              <a-statistic title="统计值" :value="displayMetric(result.report.value)" />
              <a-descriptions bordered size="small" :column="1">
                <a-descriptions-item label="统计口径">{{ result.report.metricDefinition }}</a-descriptions-item>
                <a-descriptions-item label="发布版本">V{{ result.report.dataSourceVersionNumber ?? '—' }} · 版本 ID {{ result.report.dataSourceVersionId ?? '—' }}</a-descriptions-item>
              </a-descriptions>
              <div v-if="result.report.groups.length" class="ai-source-list">
                <div class="panel-title"><strong>分组与时间桶</strong><span>{{ result.report.groups.length }} 组</span></div>
                <a-table :data-source="result.report.groups" :pagination="false" row-key="label" size="small" bordered>
                  <a-table-column title="分组 / 时间" data-index="label" key="label" />
                  <a-table-column title="数值" key="value"><template #default="{ record: group }">{{ displayMetric(group.value) }}</template></a-table-column>
                  <a-table-column title="记录数" data-index="rowCount" key="rowCount" />
                </a-table>
              </div>
              <a-button type="link" @click="returnToModule"><LinkOutlined />返回业务模块复核</a-button>
            </section>
            <a-descriptions class="ai-scope-card" bordered size="small" :column="1">
              <a-descriptions-item label="上下文与权限范围">{{ aiScopeSummary(result.scope, moduleName(result.scope.moduleCode)) }}</a-descriptions-item>
              <a-descriptions-item label="字段范围">{{ result.scope.fieldCodes.map(scopeFieldName).join('、') || '无动态字段' }}</a-descriptions-item>
              <a-descriptions-item v-if="!result.report" label="指标口径">{{ result.metricDefinition || '本次未执行业务查询，未形成指标口径。' }}</a-descriptions-item>
              <a-descriptions-item label="保存时间">{{ productDateTime(result.persistedAt) }}</a-descriptions-item>
            </a-descriptions>
            <div v-if="result.sources.length" class="ai-source-list">
              <div class="panel-title"><strong>获权来源</strong><span>点击回到普通业务页复核</span></div>
              <button v-for="source in result.sources" :key="source.recordId" class="ai-source-row" @click="router.push(source.path)">
                <span><strong>{{ source.title }}</strong><small>{{ userFacingRecordNumber(source.recordNumber) }} · {{ Object.keys(source.fields).length }} 个可见字段</small>
                  <small v-for="field in sourceFields(source)" :key="field.code">{{ field.name }}：{{ field.value }}</small></span><LinkOutlined />
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
