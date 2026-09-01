<script setup lang="ts">
import { CheckCircleOutlined, CloseCircleOutlined, RobotOutlined, SafetyCertificateOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import { aiWriteCanConfirm, normalizedAiWriteFields } from '../ai-write'
import { systemContext, systemTokens } from '../session'
import type { AiWriteCandidate, AiWriteOverview, AiWriteResult } from '../types'
import ProductPageHeader from '../components/ProductPageHeader.vue'

const props = defineProps<{ initialModuleCode?: string }>()
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const recognizing = ref(false)
const confirming = ref(false)
const error = ref('')
const overview = ref<AiWriteOverview>({ agents: [] })
const candidate = ref<AiWriteCandidate>()
const result = ref<AiWriteResult>()
const explicitlyConfirmed = ref(false)
const source = reactive({
  agentId: undefined as number | undefined,
  moduleCode: props.initialModuleCode || '',
  sourceType: 'TEXT' as 'TEXT' | 'FILE',
  sourceReference: '',
  inputText: '',
})
const form = reactive({
  title: '', recordNumber: '', status: 'ACTIVE', ownerMemberId: undefined as number | undefined,
  departmentId: undefined as number | undefined, participantText: '', fields: {} as Record<string, unknown>,
})

const selectedAgent = computed(() => overview.value.agents.find((item) => item.agentId === source.agentId))
const canConfirm = computed(() => aiWriteCanConfirm(candidate.value, explicitlyConfirmed.value, form.title))
const candidateAlertType = computed(() => candidate.value?.outcome === 'READY' || candidate.value?.outcome === 'SUCCEEDED'
  ? 'success' : candidate.value?.outcome === 'DEGRADED' || candidate.value?.outcome === 'CANCELLED' ? 'warning' : 'error')

async function loadOverview() {
  if (!systemTokens.value?.accessToken) return
  loading.value = true
  error.value = ''
  try {
    overview.value = await api<AiWriteOverview>('/api/ai/writes', {}, systemTokens.value.accessToken)
    if (!overview.value.agents.some((item) => item.agentId === source.agentId)) {
      source.agentId = overview.value.agents[0]?.agentId
    }
    chooseModule()
  } catch (reason) { error.value = readable(reason) }
  finally { loading.value = false }
}

function chooseModule() {
  const modules = selectedAgent.value?.moduleCodes || []
  if (!modules.includes(source.moduleCode)) source.moduleCode = modules[0] || ''
}

watch(() => source.agentId, chooseModule)

async function recognize() {
  if (!systemTokens.value?.accessToken || !source.agentId || !source.moduleCode || !source.inputText.trim()) return
  recognizing.value = true
  error.value = ''
  result.value = undefined
  explicitlyConfirmed.value = false
  try {
    candidate.value = await api<AiWriteCandidate>('/api/ai/writes/recognize', {
      method: 'POST', body: JSON.stringify({
        agentId: source.agentId, moduleCode: source.moduleCode, inputText: source.inputText.trim(),
        sourceType: source.sourceType, sourceReference: source.sourceReference.trim() || null,
        requestedTenantId: systemContext.value?.tenantId, entryType: 'MODULE_PAGE',
      }),
    }, systemTokens.value.accessToken)
    applyCandidate()
    await router.replace({ query: { ...route.query, pendingWriteId: String(candidate.value.pendingWriteId) } })
  } catch (reason) { error.value = readable(reason) }
  finally { recognizing.value = false }
}

function applyCandidate() {
  if (!candidate.value) return
  form.title = candidate.value.proposedTitle
  form.recordNumber = candidate.value.proposedRecordNumber || ''
  form.status = candidate.value.proposedStatus || 'ACTIVE'
  form.ownerMemberId = undefined
  form.departmentId = undefined
  form.participantText = ''
  Object.keys(form.fields).forEach((key) => delete form.fields[key])
  candidate.value.fields.forEach((field) => { if (field.value !== undefined && field.value !== null) form.fields[field.code] = field.value })
}

function participantIds() {
  return [...new Set(form.participantText.split(',').map((item) => Number(item.trim()))
    .filter((item) => Number.isInteger(item) && item > 0))]
}

function normalizedFields() {
  return normalizedAiWriteFields(form.fields)
}

async function confirmWrite() {
  if (!systemTokens.value?.accessToken || !candidate.value || !canConfirm.value) return
  confirming.value = true
  error.value = ''
  try {
    result.value = await api<AiWriteResult>(`/api/ai/writes/${candidate.value.pendingWriteId}/confirm`, {
      method: 'POST', body: JSON.stringify({
        expectedVersion: candidate.value.version, confirmed: true, title: form.title.trim(),
        recordNumber: form.recordNumber.trim() || null, status: form.status,
        ownerMemberId: form.ownerMemberId || null, departmentId: form.departmentId || null,
        participantMemberIds: participantIds(), fields: normalizedFields(),
      }),
    }, systemTokens.value.accessToken)
    if (!result.value.businessWritten) await reread()
    else await reread()
  } catch (reason) { error.value = readable(reason) }
  finally { confirming.value = false }
}

async function cancelWrite() {
  if (!systemTokens.value?.accessToken || !candidate.value) return
  error.value = ''
  result.value = await api<AiWriteResult>(`/api/ai/writes/${candidate.value.pendingWriteId}/cancel`, {
    method: 'POST', body: JSON.stringify({ expectedVersion: candidate.value.version }),
  }, systemTokens.value.accessToken)
  await reread()
}

async function reread() {
  if (!systemTokens.value?.accessToken || !candidate.value) return
  candidate.value = await api<AiWriteCandidate>(`/api/ai/writes/${candidate.value.pendingWriteId}`, {}, systemTokens.value.accessToken)
}

async function restoreCandidate() {
  if (!systemTokens.value?.accessToken) return
  const rawId = Array.isArray(route.query.pendingWriteId) ? route.query.pendingWriteId[0] : route.query.pendingWriteId
  const pendingWriteId = Number(rawId)
  if (!Number.isInteger(pendingWriteId) || pendingWriteId <= 0) return
  try {
    candidate.value = await api<AiWriteCandidate>(`/api/ai/writes/${pendingWriteId}`, {}, systemTokens.value.accessToken)
    applyCandidate()
    restoreResult()
  } catch (reason) { error.value = readable(reason) }
}

function restoreResult() {
  if (!candidate.value) return
  if (candidate.value.status === 'CONFIRMED' && candidate.value.recordId && candidate.value.recordPath) {
    result.value = {
      pendingWriteId: candidate.value.pendingWriteId, status: candidate.value.status,
      outcome: candidate.value.outcome, message: candidate.value.message,
      recordId: candidate.value.recordId, recordPath: candidate.value.recordPath,
      record: {}, businessWritten: true, version: candidate.value.version,
      confirmedAt: candidate.value.confirmedAt,
    }
  } else if (candidate.value.status === 'CANCELLED' || candidate.value.status === 'VALIDATION_FAILED') {
    result.value = {
      pendingWriteId: candidate.value.pendingWriteId, status: candidate.value.status,
      outcome: candidate.value.outcome, errorCode: candidate.value.errorCode,
      message: candidate.value.message, record: {}, businessWritten: false, version: candidate.value.version,
    }
  }
}

async function openRecord() {
  if (!result.value?.recordPath) return
  await router.push(result.value.recordPath)
}

function confidence(value: number) { return `${Math.round(value * 100)}%` }
function readable(reason: unknown) { return reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（${reason.traceId}）` : ''}` : 'AI 写入请求失败' }

onMounted(async () => {
  await loadOverview()
  await restoreCandidate()
})
</script>

<template>
  <div class="ai-write-workspace">
    <ProductPageHeader kicker="智能助手" title="从内容创建业务记录" description="先识别内容并生成可编辑草稿，由你核对后再写入业务模块。">
      <template #actions><a-button v-if="candidate" @click="reread">刷新识别结果</a-button></template>
    </ProductPageHeader>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-spin :spinning="loading">
      <section class="panel-card ai-write-source">
        <div class="ai-query-boundary"><SafetyCertificateOutlined /><span><strong>写入前由你确认</strong><small>助手只会使用当前账号已有权限，不能绕过字段和业务规则</small></span></div>
        <div class="form-grid">
          <a-form-item label="写入助手" required><a-select v-model:value="source.agentId" :options="overview.agents.map((item) => ({ value: item.agentId, label: item.agentName }))" /></a-form-item>
          <a-form-item label="目标模块" required><a-select v-model:value="source.moduleCode" :options="(selectedAgent?.moduleCodes || []).map((code) => ({ value: code, label: code }))" /></a-form-item>
        </div>
        <div class="form-grid">
          <a-form-item label="输入类型"><a-select v-model:value="source.sourceType" :options="[{ value: 'TEXT', label: '文本' }, { value: 'FILE', label: '受控文件提取文本' }]" /></a-form-item>
          <a-form-item label="输入引用"><a-input v-model:value="source.sourceReference" placeholder="文本可留空；文件填写受控引用" /></a-form-item>
        </div>
        <a-form-item label="待识别内容" required><a-textarea v-model:value="source.inputText" :rows="4" :maxlength="12000" show-count placeholder="粘贴要整理成业务记录的文字内容" /></a-form-item>
        <div class="ai-query-actions"><span class="spacer" /><a-button type="primary" :loading="recognizing" :disabled="!source.agentId || !source.moduleCode || !source.inputText.trim()" @click="recognize"><RobotOutlined />生成可确认草稿</a-button></div>
      </section>

      <section v-if="candidate" class="panel-card ai-write-confirm">
        <div class="panel-title"><strong>核对业务内容</strong><span>{{ candidate.businessWritten ? '已经写入' : '尚未写入' }}</span></div>
        <a-alert :type="candidateAlertType" show-icon :message="candidate.message" :description="candidate.businessWritten ? '业务记录已经保存，可以打开查看。' : '请检查下面的识别结果，确认前不会写入业务数据。'" />
        <div class="ai-write-meta"><span>目标模块：{{ candidate.moduleCode }}</span><span>识别结果可编辑</span><span>每次写入均需人工确认</span></div>
        <div class="form-grid">
          <a-form-item label="标题" required><a-input v-model:value="form.title" /></a-form-item>
          <a-form-item label="记录编号"><a-input v-model:value="form.recordNumber" /></a-form-item>
        </div>
        <a-form-item label="状态"><a-select v-model:value="form.status" :options="[{value:'ACTIVE',label:'启用'},{value:'DRAFT',label:'草稿'}]" /></a-form-item>
        <div class="ai-write-fields">
          <div v-for="field in candidate.fields" :key="field.code" class="ai-write-field">
            <div><strong>{{ field.label }}</strong><a-tag :color="field.recognized ? 'green' : 'default'">识别可信度 {{ confidence(field.confidence) }}</a-tag></div>
            <a-input v-model:value="form.fields[field.code]" :disabled="!field.writable" :placeholder="field.note" />
            <small>{{ field.required ? '必填 · ' : '' }}{{ field.note }}</small>
          </div>
        </div>
        <a-alert v-if="candidate.unknownSegments.length" type="warning" show-icon message="未识别内容" :description="candidate.unknownSegments.join('；')" />
        <div class="ai-write-confirm-bar">
          <a-checkbox v-model:checked="explicitlyConfirmed" :disabled="candidate.status === 'REFUSED' || candidate.businessWritten">我已核对目标模块、字段映射和最终值，确认执行真实写入</a-checkbox>
          <span class="spacer" />
          <a-button danger :disabled="candidate.businessWritten || candidate.status === 'REFUSED'" @click="cancelWrite"><CloseCircleOutlined />取消且不写入</a-button>
          <a-button type="primary" :loading="confirming" :disabled="!canConfirm" @click="confirmWrite"><CheckCircleOutlined />确认写入真实业务</a-button>
        </div>
      </section>

      <section v-if="result" class="panel-card ai-write-result">
        <a-result :status="result.businessWritten ? 'success' : 'error'" :title="result.message" :sub-title="result.businessWritten ? '业务记录已保存并重新读取。' : '没有写入业务数据，可以修改内容后重试。'">
          <template #extra><a-button v-if="result.recordPath" type="primary" @click="openRecord">打开目标模块记录</a-button></template>
        </a-result>
      </section>
    </a-spin>
  </div>
</template>
