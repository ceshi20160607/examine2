<script setup lang="ts">
import { CheckCircleOutlined, CloseCircleOutlined, LinkOutlined, RobotOutlined, SafetyCertificateOutlined, SendOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import { aiOutcomePresentation, aiScopeSummary } from '../ai-context'
import { aiWriteCanConfirm, normalizedAiWriteFields } from '../ai-write'
import { productDateTime, userFacingRecordNumber } from '../presentation'
import { systemContext, systemTokens } from '../session'
import type {
  AiQueryOverview,
  AiQueryResult,
  AiWriteCandidate,
  AiWriteOverview,
  AiWriteResult,
  RuntimeField,
  RuntimeModuleCatalogItem,
  RuntimeRecord,
} from '../types'

const props = defineProps<{
  moduleCode: string
  moduleName: string
  record: RuntimeRecord
  visibleFields: RuntimeField[]
  modules: RuntimeModuleCatalogItem[]
}>()
const emit = defineEmits<{
  businessWritten: [value: { moduleCode: string; recordId: number; recordPath?: string }]
}>()

const router = useRouter()
const loading = ref(false)
const asking = ref(false)
const recognizing = ref(false)
const confirming = ref(false)
const error = ref('')
const active = ref<'query' | 'write'>('query')
const queryOverview = ref<AiQueryOverview>({ agents: [], conversations: [] })
const writeOverview = ref<AiWriteOverview>({ agents: [] })
const queryAgentId = ref<number>()
const writeAgentId = ref<number>()
const targetModuleCode = ref(props.moduleCode)
const question = ref('')
const conversationId = ref<number>()
const queryResult = ref<AiQueryResult>()
const writeText = ref('')
const candidate = ref<AiWriteCandidate>()
const writeResult = ref<AiWriteResult>()
const explicitlyConfirmed = ref(false)
const writeForm = reactive({ title: '', recordNumber: '', status: 'ACTIVE', fields: {} as Record<string, unknown> })

const queryAgents = computed(() => queryOverview.value.agents.filter((agent) => agent.moduleCodes.includes(props.moduleCode)))
const visibleModuleCodes = computed(() => new Set(props.modules.map((module) => module.moduleCode)))
const writeAgents = computed(() => writeOverview.value.agents.filter((agent) =>
  agent.moduleCodes.some((moduleCode) => visibleModuleCodes.value.has(moduleCode)),
))
const selectedWriteAgent = computed(() => writeAgents.value.find((agent) => agent.agentId === writeAgentId.value))
const targetModules = computed(() => (selectedWriteAgent.value?.moduleCodes || [])
  .map((code) => props.modules.find((module) => module.moduleCode === code))
  .filter((module): module is RuntimeModuleCatalogItem => Boolean(module))
  .map((module) => ({ value: module.moduleCode, label: module.moduleName })))
const resultPresentation = computed(() => aiOutcomePresentation(queryResult.value?.outcome))
const canConfirm = computed(() => aiWriteCanConfirm(candidate.value, explicitlyConfirmed.value, writeForm.title))
const sourcePath = computed(() => `/systems/${systemContext.value?.systemId}?workspace=runtime&module=${props.moduleCode}&recordId=${props.record.id}`)
const recordState = computed(() => props.record.deleted ? 'DELETED' : props.record.archived ? 'ARCHIVED' : 'ACTIVE')
const emptyMessage = computed(() => {
  if (loading.value) return '正在读取当前页面可用的助手'
  if (!queryAgents.value.length && !writeAgents.value.length) return '管理员尚未为当前业务发布可用助手'
  return ''
})

function readable(reason: unknown, fallback: string) {
  return reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（${reason.traceId}）` : ''}` : fallback
}

async function load() {
  if (!systemTokens.value?.accessToken) return
  loading.value = true
  error.value = ''
  const [query, write] = await Promise.allSettled([
    api<AiQueryOverview>('/api/ai', {}, systemTokens.value.accessToken),
    api<AiWriteOverview>('/api/ai/writes', {}, systemTokens.value.accessToken),
  ])
  if (query.status === 'fulfilled') queryOverview.value = query.value
  if (write.status === 'fulfilled') writeOverview.value = write.value
  if (query.status === 'rejected' && write.status === 'rejected') {
    error.value = readable(query.reason, '当前页面无法使用智能助手')
  }
  chooseAgents()
  loading.value = false
}

function chooseAgents() {
  if (!queryAgents.value.some((agent) => agent.agentId === queryAgentId.value)) queryAgentId.value = queryAgents.value[0]?.agentId
  if (!writeAgents.value.some((agent) => agent.agentId === writeAgentId.value)) {
    writeAgentId.value = writeAgents.value.find((agent) => agent.moduleCodes.includes(props.moduleCode))?.agentId
      || writeAgents.value[0]?.agentId
  }
  chooseTargetModule()
}

function chooseTargetModule() {
  const moduleCodes = targetModules.value.map((module) => module.value)
  if (!moduleCodes.includes(targetModuleCode.value)) {
    targetModuleCode.value = moduleCodes.includes(props.moduleCode) ? props.moduleCode : moduleCodes[0] || ''
  }
}

async function ask(value?: string) {
  if (value) question.value = value
  if (!systemTokens.value?.accessToken || !queryAgentId.value || !question.value.trim()) return
  asking.value = true
  error.value = ''
  try {
    queryResult.value = await api<AiQueryResult>('/api/ai/queries', {
      method: 'POST',
      body: JSON.stringify({
        agentId: queryAgentId.value,
        conversationId: conversationId.value || null,
        question: question.value.trim(),
        requestedFieldCodes: props.visibleFields.map((field) => field.code),
        requestedTenantId: systemContext.value?.tenantId,
        entryContext: {
          entryType: 'RECORD_DETAIL',
          moduleCode: props.moduleCode,
          recordId: props.record.id,
          sourcePath: sourcePath.value,
          lifecycleState: recordState.value,
          tenantScope: 'ALL',
          search: '',
          filters: [],
          sortField: 'updatedAt',
          sortDirection: 'DESC',
          pageSize: 5,
        },
      }),
    }, systemTokens.value.accessToken)
    conversationId.value = queryResult.value.conversationId
    question.value = ''
  } catch (reason) {
    error.value = readable(reason, '当前记录智能查询失败')
  } finally {
    asking.value = false
  }
}

async function recognize() {
  if (!systemTokens.value?.accessToken || !writeAgentId.value || !targetModuleCode.value || !writeText.value.trim()) return
  recognizing.value = true
  error.value = ''
  candidate.value = undefined
  writeResult.value = undefined
  explicitlyConfirmed.value = false
  try {
    candidate.value = await api<AiWriteCandidate>('/api/ai/writes/recognize', {
      method: 'POST',
      body: JSON.stringify({
        agentId: writeAgentId.value,
        moduleCode: targetModuleCode.value,
        inputText: writeText.value.trim(),
        sourceType: 'TEXT',
        sourceReference: sourcePath.value,
        requestedTenantId: systemContext.value?.tenantId,
        entryType: 'RECORD_DETAIL',
      }),
    }, systemTokens.value.accessToken)
    applyCandidate()
  } catch (reason) {
    error.value = readable(reason, '没有生成可确认的业务草稿')
  } finally {
    recognizing.value = false
  }
}

function applyCandidate() {
  if (!candidate.value) return
  writeForm.title = candidate.value.proposedTitle || ''
  writeForm.recordNumber = candidate.value.proposedRecordNumber || ''
  writeForm.status = candidate.value.proposedStatus || 'ACTIVE'
  Object.keys(writeForm.fields).forEach((key) => delete writeForm.fields[key])
  candidate.value.fields.forEach((field) => {
    if (field.value !== undefined && field.value !== null) writeForm.fields[field.code] = field.value
  })
}

async function confirmWrite() {
  if (!systemTokens.value?.accessToken || !candidate.value || !canConfirm.value) return
  confirming.value = true
  error.value = ''
  try {
    writeResult.value = await api<AiWriteResult>(`/api/ai/writes/${candidate.value.pendingWriteId}/confirm`, {
      method: 'POST',
      body: JSON.stringify({
        expectedVersion: candidate.value.version,
        confirmed: true,
        title: writeForm.title.trim(),
        recordNumber: writeForm.recordNumber.trim() || null,
        status: writeForm.status,
        ownerMemberId: null,
        departmentId: null,
        participantMemberIds: [],
        fields: normalizedAiWriteFields(writeForm.fields),
      }),
    }, systemTokens.value.accessToken)
    candidate.value = await api<AiWriteCandidate>(`/api/ai/writes/${candidate.value.pendingWriteId}`, {}, systemTokens.value.accessToken)
    if (writeResult.value.businessWritten && writeResult.value.recordId) {
      emit('businessWritten', { moduleCode: targetModuleCode.value, recordId: writeResult.value.recordId, recordPath: writeResult.value.recordPath })
    }
  } catch (reason) {
    error.value = readable(reason, '确认写入失败，业务数据未改变')
  } finally {
    confirming.value = false
  }
}

async function cancelWrite() {
  if (!systemTokens.value?.accessToken || !candidate.value) return
  error.value = ''
  try {
    writeResult.value = await api<AiWriteResult>(`/api/ai/writes/${candidate.value.pendingWriteId}/cancel`, {
      method: 'POST', body: JSON.stringify({ expectedVersion: candidate.value.version }),
    }, systemTokens.value.accessToken)
    candidate.value = await api<AiWriteCandidate>(`/api/ai/writes/${candidate.value.pendingWriteId}`, {}, systemTokens.value.accessToken)
  } catch (reason) {
    error.value = readable(reason, '取消候选失败')
  }
}

function inputType(fieldType: string) {
  if (['NUMBER', 'MONEY'].includes(fieldType)) return 'number'
  return 'text'
}

function resetRecordContext() {
  queryResult.value = undefined
  conversationId.value = undefined
  question.value = ''
  candidate.value = undefined
  writeResult.value = undefined
  writeText.value = ''
  explicitlyConfirmed.value = false
  targetModuleCode.value = props.moduleCode
  chooseAgents()
}

watch(() => writeAgentId.value, chooseTargetModule)
watch(() => [props.moduleCode, props.record.id], resetRecordContext)
onMounted(load)
</script>

<template>
  <aside class="context-assistant" aria-label="当前业务记录智能助手">
    <header class="context-assistant__header">
      <span class="context-assistant__mark"><RobotOutlined /></span>
      <span><strong>当前记录助手</strong><small>只读取你在此页有权查看的内容</small></span>
    </header>

    <a-alert v-if="error" type="error" show-icon :message="error" closable @close="error = ''" />
    <a-spin :spinning="loading">
      <a-empty v-if="emptyMessage" :image="undefined" :description="emptyMessage">
        <a-button size="small" @click="load">重新读取</a-button>
      </a-empty>
      <template v-else>
        <a-segmented v-model:value="active" block :options="[{ value: 'query', label: '询问当前记录' }, { value: 'write', label: '整理并创建记录' }]" />

        <section v-if="active === 'query'" class="context-assistant__body">
          <div class="context-boundary"><SafetyCertificateOutlined /><span><strong>{{ moduleName }} · {{ record.title }}</strong><small>当前记录、当前系统、当前租户与字段权限已自动带入</small></span></div>
          <a-select v-if="queryAgents.length > 1" v-model:value="queryAgentId" :options="queryAgents.map((agent) => ({ value: agent.agentId, label: agent.agentName }))" />
          <div class="context-assistant__suggestions">
            <button type="button" @click="ask('总结当前记录的关键信息')">总结关键信息</button>
            <button type="button" @click="ask('结合当前记录给出下一步建议')">下一步建议</button>
            <button type="button" @click="ask('检查当前记录中需要关注的风险和缺失信息')">风险与缺失</button>
          </div>
          <a-textarea v-model:value="question" :rows="3" :maxlength="2000" placeholder="直接询问当前记录，无需填写模块、字段或筛选条件" @keydown.ctrl.enter.prevent="ask()" />
          <a-button type="primary" block :loading="asking" :disabled="!queryAgentId || !question.trim()" @click="ask()"><SendOutlined />发送</a-button>

          <div v-if="queryResult" class="context-assistant__result">
            <div class="context-assistant__result-title"><strong>回答</strong><a-tag :color="resultPresentation.color">{{ resultPresentation.label }}</a-tag></div>
            <a-alert :type="resultPresentation.alert" show-icon :message="queryResult.answer"
              :description="queryResult.errorCode ? (queryResult.retryable ? '本次没有生成业务结果，可以稍后重试；当前页面仍可正常使用。' : '请求未通过权限或能力边界，没有返回业务数据。') : undefined" />
            <details v-if="queryResult.scope"><summary>查看查询口径与来源</summary><p>{{ aiScopeSummary(queryResult.scope) }}</p><p>{{ queryResult.metricDefinition || '本次为当前记录详情读取。' }}</p><small>保存于 {{ productDateTime(queryResult.persistedAt) }}</small></details>
            <button v-for="source in queryResult.sources" :key="source.recordId" type="button" class="context-source" @click="router.push(source.path)">
              <span><strong>{{ source.title }}</strong><small>{{ userFacingRecordNumber(source.recordNumber) }} · {{ Object.keys(source.fields).length }} 个有权字段</small></span><LinkOutlined />
            </button>
          </div>
        </section>

        <section v-else class="context-assistant__body">
          <div class="context-boundary"><SafetyCertificateOutlined /><span><strong>确认前不会写入</strong><small>内容来源已关联到“{{ record.title }}”，最终写入仍经过模块权限和业务校验</small></span></div>
          <a-select v-if="writeAgents.length > 1" v-model:value="writeAgentId" :options="writeAgents.map((agent) => ({ value: agent.agentId, label: agent.agentName }))" placeholder="选择写入助手" />
          <a-form-item label="创建到"><a-select v-model:value="targetModuleCode" :options="targetModules" /></a-form-item>
          <a-textarea v-model:value="writeText" :rows="4" :maxlength="12000" placeholder="例如：今天电话沟通，客户确认下周一提供合同盖章件，由我继续跟进。" />
          <a-button type="primary" block :loading="recognizing" :disabled="!writeAgentId || !targetModuleCode || !writeText.trim()" @click="recognize"><RobotOutlined />生成字段预览</a-button>

          <div v-if="candidate" class="context-write-preview">
            <a-alert :type="candidate.outcome === 'READY' ? 'success' : candidate.outcome === 'DEGRADED' ? 'warning' : 'error'" show-icon :message="candidate.message"
              :description="candidate.businessWritten ? '业务记录已经持久化。' : '下面内容仍是候选草稿，没有写入业务数据。'" />
            <a-form-item label="标题" required><a-input v-model:value="writeForm.title" /></a-form-item>
            <a-form-item v-for="field in candidate.fields" :key="field.code" :label="field.label" :required="field.required">
              <a-switch v-if="field.fieldType === 'BOOLEAN'" v-model:checked="writeForm.fields[field.code]" :disabled="!field.writable" />
              <a-input v-else-if="field.fieldType === 'REFERENCE' && Number(writeForm.fields[field.code]) === record.id" :value="record.title" disabled />
              <a-input-number v-else-if="inputType(field.fieldType) === 'number'" v-model:value="writeForm.fields[field.code]" :disabled="!field.writable" style="width:100%" />
              <a-date-picker v-else-if="field.fieldType === 'DATE'" v-model:value="writeForm.fields[field.code]" value-format="YYYY-MM-DD" :disabled="!field.writable" style="width:100%" />
              <a-date-picker v-else-if="field.fieldType === 'DATETIME'" v-model:value="writeForm.fields[field.code]" show-time value-format="YYYY-MM-DDTHH:mm:ss" :disabled="!field.writable" style="width:100%" />
              <a-textarea v-else-if="field.fieldType === 'MULTILINE_TEXT'" v-model:value="writeForm.fields[field.code]" :disabled="!field.writable" :rows="3" />
              <a-input v-else v-model:value="writeForm.fields[field.code]" :disabled="!field.writable" />
              <small>{{ field.recognized ? `识别可信度 ${Math.round(field.confidence * 100)}%` : '需要人工补充' }} · {{ field.note }}</small>
            </a-form-item>
            <a-alert v-if="candidate.unknownSegments.length" type="warning" show-icon message="仍有未识别内容" :description="candidate.unknownSegments.join('；')" />
            <a-checkbox v-model:checked="explicitlyConfirmed" :disabled="candidate.businessWritten || ['REFUSED','CANCELLED'].includes(candidate.status)">我已核对目标模块和每个字段，确认执行真实写入</a-checkbox>
            <div class="context-write-actions">
              <a-button danger :disabled="candidate.businessWritten || ['REFUSED','CANCELLED'].includes(candidate.status)" @click="cancelWrite"><CloseCircleOutlined />取消</a-button>
              <a-button type="primary" :loading="confirming" :disabled="!canConfirm" @click="confirmWrite"><CheckCircleOutlined />确认写入</a-button>
            </div>
          </div>

          <a-result v-if="writeResult" :status="writeResult.businessWritten ? 'success' : 'info'" :title="writeResult.message">
            <template #extra><a-button v-if="writeResult.recordPath" type="primary" @click="router.push(writeResult.recordPath)">打开新记录</a-button></template>
          </a-result>
        </section>
      </template>
    </a-spin>
  </aside>
</template>
