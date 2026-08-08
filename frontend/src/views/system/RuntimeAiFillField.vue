<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { ApiRequestError } from '@/services/api'
import { aiFillApi } from '@/services/aiFill'
import type { AiFillMaterializationResult, AiFillProposal } from '@/types/aiFill'
import type { RuntimeFieldCapability, RuntimeFieldValue } from '@/types/config'

const props = defineProps<{
  systemId: string
  moduleCode: string
  recordId: string
  recordVersion: number
  field: RuntimeFieldCapability
  value?: RuntimeFieldValue
}>()

const emit = defineEmits<{
  materialized: [result: AiFillMaterializationResult]
}>()

const proposal = ref<AiFillProposal | null>(null)
const generating = ref(false)
const action = ref<'confirm' | 'reject' | ''>('')
const errorMessage = ref('')
const actionKeys = new Map<string, { confirm?: string; reject?: string }>()

const currentDisplayValue = computed(() => {
  if (props.value?.displayValue !== undefined && props.value.displayValue !== '') {
    return props.value.displayValue
  }
  const raw = props.value?.value
  if (raw && typeof raw === 'object') {
    const envelope = raw as Record<string, unknown>
    const materialized = envelope.result ?? envelope.value
    if (materialized !== undefined && materialized !== null && materialized !== '') return String(materialized)
  }
  return raw === undefined || raw === null || raw === '' ? '尚未生成' : String(raw)
})

const provenance = computed(() => {
  const raw = props.value?.value
  if (!raw || typeof raw !== 'object') return null
  const envelope = raw as Record<string, unknown>
  const source = envelope.provenance && typeof envelope.provenance === 'object'
    ? envelope.provenance as Record<string, unknown> : envelope
  const confidence = Number(source.confidence)
  return {
    materializationId: String(source.materializationId ?? ''),
    confidence: Number.isFinite(confidence) ? confidence : null,
    model: String(source.model ?? ''),
    promptVersion: String(source.promptVersion ?? ''),
    generatedAt: String(source.generatedAt ?? source.executedAt ?? ''),
  }
})

const minimumConfidence = computed(() => {
  const value = Number(props.field.schema.minConfidence ?? 0.8)
  return Number.isFinite(value) ? value : 0.8
})

function clientState(value: AiFillProposal) {
  if (value.state === 'PENDING' && new Date(value.expiresAt).valueOf() <= Date.now()) return 'EXPIRED'
  return value.state
}

function stateLabel(value: AiFillProposal) {
  return ({
    CLARIFICATION_REQUIRED: '需要补充信息', PENDING: '等待确认', EXECUTING: '正在物化',
    SUCCEEDED: '已物化', FAILED: '物化失败',
    REJECTED: '已拒绝', EXPIRED: '已过期',
  } as Record<string, string>)[clientState(value)] ?? clientState(value)
}

function stateColor(value: AiFillProposal) {
  const state = clientState(value)
  if (state === 'SUCCEEDED') return 'green'
  if (state === 'PENDING' || state === 'EXECUTING') return 'orange'
  if (state === 'REJECTED') return 'default'
  return 'red'
}

function percent(value: number | null | undefined) {
  if (value === null || value === undefined || !Number.isFinite(value)) return '未提供'
  return `${Math.round((value <= 1 ? value * 100 : value))}%`
}

function display(value: string | null) {
  if (value === null) return '—'
  return value === '' ? '（空值）' : value
}

function canConfirm(value: AiFillProposal) {
  return clientState(value) === 'PENDING'
    && !value.clarification
    && value.confidence >= minimumConfidence.value
    && !(value.overwrite && String(props.field.schema.overwriteMode ?? 'CONFIRM') === 'NEVER')
}

function canReject(value: AiFillProposal) {
  return clientState(value) === 'PENDING'
}

function codeMessage(code: string) {
  if (/PERMISSION|MEMBER|TENANT|CONTEXT/iu.test(code)) return '当前权限或成员上下文已变化，字段未物化。'
  if (/SOURCE.*(STALE|CHANGED|VERSION)/iu.test(code)) return '来源字段已变化，请重新生成提案。'
  if (/RECORD.*VERSION|CONFLICT|REPLAY/iu.test(code)) return '记录或提案版本已变化，字段未物化。'
  if (/SCHEMA/iu.test(code)) return '字段结构已变化，请刷新记录后重新生成。'
  if (/EXPIRED/iu.test(code)) return '提案已过期，字段未物化。'
  if (/OVERWRITE/iu.test(code)) return '当前字段不允许覆盖已有值。'
  if (/CONFIDENCE/iu.test(code)) return '生成结果低于最低置信度，不能物化。'
  if (/CLARIFICATION/iu.test(code)) return '生成结果需要补充信息，不能物化。'
  if (/PROVIDER|TIMEOUT|UNAVAILABLE/iu.test(code)) return 'AI 服务暂不可用，记录内容未变化。'
  return code ? `AI_FILL 执行失败（${code}），记录内容未变化。` : 'AI_FILL 执行失败，记录内容未变化。'
}

function requestError(error: unknown) {
  if (error instanceof ApiRequestError) return codeMessage(error.code) || error.message
  return error instanceof Error ? codeMessage(error.message) : codeMessage('')
}

async function refreshProposal(value: AiFillProposal) {
  const latest = await aiFillApi.proposal(
    props.systemId, props.moduleCode, props.recordId, props.field.fieldCode, value.id,
  )
  proposal.value = latest
  return latest
}

async function generate() {
  if (generating.value || action.value || proposal.value && canReject(proposal.value)) return
  generating.value = true
  errorMessage.value = ''
  try {
    proposal.value = await aiFillApi.createProposal(
      props.systemId, props.moduleCode, props.recordId, props.field.fieldCode,
      { expectedRecordVersion: props.recordVersion }, crypto.randomUUID(),
    )
  } catch (error) {
    errorMessage.value = requestError(error)
  } finally {
    generating.value = false
  }
}

async function confirm() {
  const current = proposal.value
  if (!current || !canConfirm(current) || action.value) return
  action.value = 'confirm'
  errorMessage.value = ''
  const keys = actionKeys.get(current.id) ?? {}
  keys.confirm ??= crypto.randomUUID()
  actionKeys.set(current.id, keys)
  try {
    const confirmed = await aiFillApi.confirm(
      props.systemId, props.moduleCode, props.recordId, props.field.fieldCode,
      current.id, current.version, keys.confirm,
    )
    proposal.value = confirmed
    if (confirmed.result) emit('materialized', confirmed.result)
  } catch (error) {
    errorMessage.value = requestError(error)
    try { await refreshProposal(current) } catch { /* retain the safe proposal already shown */ }
  } finally {
    action.value = ''
  }
}

async function reject() {
  const current = proposal.value
  if (!current || !canReject(current) || action.value) return
  action.value = 'reject'
  errorMessage.value = ''
  const keys = actionKeys.get(current.id) ?? {}
  keys.reject ??= crypto.randomUUID()
  actionKeys.set(current.id, keys)
  try {
    proposal.value = await aiFillApi.reject(
      props.systemId, props.moduleCode, props.recordId, props.field.fieldCode,
      current.id, current.version, keys.reject,
    )
  } catch (error) {
    errorMessage.value = requestError(error)
    try { await refreshProposal(current) } catch { /* retain the safe proposal already shown */ }
  } finally {
    action.value = ''
  }
}

watch(() => [props.moduleCode, props.recordId, props.field.fieldCode], () => {
  proposal.value = null
  errorMessage.value = ''
  action.value = ''
  actionKeys.clear()
})
</script>

<template>
  <section class="runtime-ai-fill-field">
    <header class="ai-fill-current">
      <div><strong>{{ currentDisplayValue }}</strong><small>AI_FILL · {{ field.schema.resultSchema || 'SCALAR' }}</small></div>
      <a-button class="ai-fill-generate" size="small" :loading="generating" :disabled="Boolean(action) || Boolean(proposal && canReject(proposal))" @click="generate">
        {{ proposal ? '重新生成' : currentDisplayValue === '尚未生成' ? '生成建议' : '生成覆盖建议' }}
      </a-button>
    </header>

    <dl v-if="provenance && (provenance.materializationId || provenance.confidence !== null)" class="ai-fill-provenance">
      <div v-if="provenance.materializationId"><dt>物化记录</dt><dd>{{ provenance.materializationId }}</dd></div>
      <div v-if="provenance.confidence !== null"><dt>置信度</dt><dd>{{ percent(provenance.confidence) }}</dd></div>
      <div v-if="provenance.model"><dt>模型</dt><dd>{{ provenance.model }}</dd></div>
      <div v-if="provenance.promptVersion"><dt>Prompt</dt><dd>{{ provenance.promptVersion }}</dd></div>
      <div v-if="provenance.generatedAt"><dt>生成时间</dt><dd>{{ provenance.generatedAt }}</dd></div>
    </dl>

    <section v-if="proposal" class="ai-fill-proposal" :class="clientState(proposal).toLowerCase()">
      <header><div><strong>智能填充提案</strong><small>{{ proposal.moduleCode }} · {{ proposal.fieldName }} ({{ proposal.fieldCode }})</small></div><a-tag :color="stateColor(proposal)">{{ stateLabel(proposal) }}</a-tag></header>
      <div v-if="proposal.overwrite" class="ai-fill-overwrite-warning"><strong>覆盖提醒</strong><span>确认后将覆盖当前已有值；未确认不会修改记录。</span></div>
      <dl class="ai-fill-preview">
        <div><dt>变更前</dt><dd>{{ display(proposal.beforeDisplayValue) }}</dd></div>
        <div><dt>建议值</dt><dd>{{ display(proposal.afterDisplayValue) }}</dd></div>
        <div><dt>结果类型</dt><dd>{{ proposal.resultSchema }}</dd></div>
        <div><dt>置信度</dt><dd>{{ percent(proposal.confidence) }}（最低 {{ percent(minimumConfidence) }}）</dd></div>
        <div><dt>有效期至</dt><dd>{{ proposal.expiresAt }}</dd></div>
      </dl>
      <div class="ai-fill-sources">
        <strong>本次使用的安全来源快照</strong>
        <table><thead><tr><th>来源字段</th><th>显示值</th></tr></thead><tbody><tr v-for="source in proposal.sources" :key="source.fieldCode"><th>{{ source.fieldName }}<small>{{ source.fieldCode }}</small></th><td>{{ source.masked ? '已脱敏' : display(source.displayValue) }}</td></tr></tbody></table>
      </div>
      <div v-if="proposal.clarification" class="ai-fill-clarification"><strong>需要补充信息，当前提案不可确认</strong><span>{{ proposal.clarification }}</span></div>
      <div v-if="canReject(proposal)" class="ai-fill-actions">
        <span>只有显式确认后才会写入该字段。</span>
        <a-button class="ai-fill-reject" size="small" :loading="action === 'reject'" :disabled="Boolean(action)" @click="reject">拒绝</a-button>
        <a-button class="ai-fill-confirm" size="small" type="primary" :loading="action === 'confirm'" :disabled="Boolean(action) || !canConfirm(proposal)" @click="confirm">确认物化</a-button>
      </div>
      <section v-if="proposal.result" class="ai-fill-result">
        <strong>业务模块真实回读</strong>
        <dl><div><dt>物化 ID</dt><dd>{{ proposal.result.materializationId }}</dd></div><div><dt>记录版本</dt><dd>{{ proposal.result.recordVersion }}</dd></div><div><dt>字段</dt><dd>{{ proposal.result.fieldCode }}</dd></div><div><dt>最终值</dt><dd>{{ proposal.result.displayValue }}</dd></div><div><dt>置信度</dt><dd>{{ percent(proposal.result.confidence) }}</dd></div></dl>
      </section>
      <a-alert v-if="proposal.errorCode && !errorMessage" class="ai-fill-error" type="error" show-icon :message="codeMessage(proposal.errorCode)" />
    </section>
    <a-alert v-if="errorMessage" class="ai-fill-error" type="error" show-icon :message="errorMessage" />
  </section>
</template>

<style scoped>
.runtime-ai-fill-field{display:grid;gap:9px;min-width:0}.ai-fill-current,.ai-fill-proposal>header,.ai-fill-actions{display:flex;align-items:center;justify-content:space-between;gap:10px}.ai-fill-current>div,.ai-fill-proposal>header>div{display:grid;gap:2px}.ai-fill-current small,.ai-fill-proposal header small{color:#6d7b82;font-size:11px}.ai-fill-provenance,.ai-fill-preview,.ai-fill-result dl{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:6px;margin:0}.ai-fill-provenance>div,.ai-fill-preview>div,.ai-fill-result dl>div{display:grid;gap:2px;padding:6px;border-radius:5px;background:#f6f8f9}.runtime-ai-fill-field dt{color:#718087;font-size:11px}.runtime-ai-fill-field dd{margin:0;overflow-wrap:anywhere}.ai-fill-proposal{display:grid;gap:9px;padding:10px;border:1px solid #dfc47f;border-radius:7px;background:#fffaf0}.ai-fill-proposal.succeeded{border-color:#91cabb;background:#effaf7}.ai-fill-proposal.failed,.ai-fill-proposal.expired{border-color:#dfa6a6;background:#fff5f5}.ai-fill-overwrite-warning,.ai-fill-clarification{display:grid;gap:3px;padding:8px;border-radius:5px;background:#fff0cd}.ai-fill-sources{display:grid;gap:5px}.ai-fill-sources table{width:100%;border-collapse:collapse;background:#fff}.ai-fill-sources th,.ai-fill-sources td{padding:6px;border:1px solid #dce3e6;text-align:left}.ai-fill-sources th small{display:block;color:#718087;font-weight:400}.ai-fill-actions>span{margin-right:auto;color:#65747b;font-size:11px}.ai-fill-result{display:grid;gap:7px;padding:9px;border:1px solid #91cabb;border-radius:6px;background:#eaf8f4}.ai-fill-error{margin-top:2px}
</style>
