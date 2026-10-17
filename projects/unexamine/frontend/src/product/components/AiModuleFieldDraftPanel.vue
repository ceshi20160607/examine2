<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import { systemTokens } from '../session'

interface AgentOption { id: number; name: string }
interface DraftOptions { agents: AgentOption[]; moduleId: number; moduleCode: string; moduleName: string; draftRevision: number }
interface SuggestedField {
  code: string; name: string; fieldType: string; required: boolean; uniqueValue: boolean
  searchable: boolean; config: Record<string, unknown>; confidence: number
}
interface Candidate {
  pendingWriteId: number; status: string; outcome: string; message: string; errorCode: string | null
  fields: SuggestedField[]; confidence: number; businessWritten: boolean; version: number
  resultFieldIds: number[]; moduleId: number; moduleCode: string; agentId: number
}

const props = defineProps<{ moduleId: number }>()
const emit = defineEmits<{ saved: [] }>()
const token = computed(() => systemTokens.value?.accessToken || '')
const open = ref(false)
const options = ref<DraftOptions>()
const agentId = ref<number>()
const instruction = ref('')
const candidate = ref<Candidate>()
const fields = ref<SuggestedField[]>([])
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const confirmed = ref(false)
const reviewed = ref<Record<number, boolean>>({})
const fieldTypes = [
  { value: 'TEXT', label: '单行文本' }, { value: 'MULTILINE_TEXT', label: '多行文本' },
  { value: 'NUMBER', label: '数字' }, { value: 'DATE', label: '日期' },
  { value: 'DATETIME', label: '日期时间' }, { value: 'BOOLEAN', label: '是否' },
  { value: 'EMAIL', label: '邮箱' }, { value: 'PHONE', label: '电话' },
]
const canSave = computed(() => Boolean(candidate.value && !candidate.value.businessWritten && confirmed.value
  && fields.value.length > 0 && fields.value.every(field => field.name.trim()
    && /^[A-Za-z][A-Za-z0-9_]{1,49}$/.test(field.code.trim())
    && fieldTypes.some(type => type.value === field.fieldType))
  && new Set(fields.value.map(field => field.code.trim().toLowerCase())).size === fields.value.length
  && fields.value.every((field, index) => field.confidence >= 0.7 && candidate.value!.confidence >= 0.7
    || reviewed.value[index])))

function errorMessage(reason: unknown, fallback: string) {
  return reason instanceof ApiError ? reason.message : fallback
}

async function loadOptions() {
  if (!token.value) return
  error.value = ''
  try {
    const result = await api<DraftOptions>(`/api/admin/system/ai/module-field-drafts/options?moduleId=${props.moduleId}`, {}, token.value)
    if (result.moduleId !== props.moduleId) return
    options.value = result
    agentId.value = result.agents.some(agent => agent.id === agentId.value) ? agentId.value : result.agents[0]?.id
  } catch (reason) {
    options.value = undefined
    error.value = errorMessage(reason, '配置 Agent 加载失败')
  }
}

async function recognize() {
  if (!token.value || !agentId.value || !instruction.value.trim()) return
  loading.value = true
  error.value = ''
  candidate.value = undefined
  fields.value = []
  confirmed.value = false
  reviewed.value = {}
  try {
    const result = await api<Candidate>('/api/admin/system/ai/module-field-drafts/recognize', {
      method: 'POST', body: JSON.stringify({ agentId: agentId.value, moduleId: props.moduleId,
        instruction: instruction.value.trim() }),
    }, token.value)
    if (result.moduleId !== props.moduleId) return
    candidate.value = result
    fields.value = result.fields.map(field => ({ ...field, config: { ...field.config } }))
  } catch (reason) {
    error.value = errorMessage(reason, '字段建议生成失败')
  } finally {
    loading.value = false
  }
}

async function save() {
  if (!token.value || !candidate.value || !canSave.value) return
  saving.value = true
  error.value = ''
  try {
    const result = await api<Candidate>(`/api/admin/system/ai/module-field-drafts/${candidate.value.pendingWriteId}/confirm`, {
      method: 'POST', body: JSON.stringify({ expectedVersion: candidate.value.version, confirmed: true,
        reviewedFieldCodes: candidate.value.fields.filter((_, index) => reviewed.value[index]).map(field => field.code),
        fields: fields.value.map(field => ({ code: field.code.trim(), name: field.name.trim(),
          fieldType: field.fieldType, required: field.required, uniqueValue: field.uniqueValue,
          searchable: field.searchable, config: field.config || {} })) }),
    }, token.value)
    candidate.value = result
    if (result.businessWritten) emit('saved')
  } catch (reason) {
    error.value = errorMessage(reason, '字段保存失败')
  } finally {
    saving.value = false
  }
}

watch(() => props.moduleId, () => {
  candidate.value = undefined
  fields.value = []
  options.value = undefined
  confirmed.value = false
  reviewed.value = {}
  if (open.value) void loadOptions()
})
watch(open, value => { if (value) void loadOptions() })
</script>

<template>
  <section class="ai-field-panel">
    <div class="ai-field-heading">
      <div><strong>AI 辅助添加字段</strong><small>描述业务字段，核对建议后保存到模块草稿。</small></div>
      <a-button size="small" @click="open = !open">{{ open ? '收起' : '生成字段建议' }}</a-button>
    </div>
    <div v-if="open" class="ai-field-body">
      <a-alert v-if="error" type="error" show-icon :message="error" />
      <a-alert v-if="options && !options.agents.length" type="info" show-icon message="当前模块尚无已发布且授权的配置 Agent，请先在 AI 配置中发布。" />
      <template v-if="options?.agents.length">
        <label>配置 Agent
          <a-select v-model:value="agentId" :options="options.agents.map(agent => ({ value: agent.id, label: agent.name }))" />
        </label>
        <label>业务描述
          <a-textarea v-model:value="instruction" :rows="3" :maxlength="12000" placeholder="例如：为客户档案增加联系人姓名、联系电话和下次跟进日期" />
        </label>
        <div class="ai-field-actions"><a-button :loading="loading" :disabled="!agentId || !instruction.trim()" @click="recognize">生成建议</a-button></div>
      </template>
      <div v-if="candidate" class="ai-field-candidate">
        <a-alert :type="candidate.outcome === 'READY' ? 'info' : 'warning'" show-icon :message="candidate.message" />
        <p>整体识别置信度 {{ Math.round(candidate.confidence * 100) }}%。请核对字段名称、标识和类型；保存后仍需单独发布模块。</p>
        <a-alert v-if="candidate.confidence < 0.7 && !candidate.businessWritten" type="warning" show-icon message="识别置信度较低，请逐项核对后再确认。" />
        <div v-for="(field, index) in fields" :key="index" class="ai-field-row">
          <label>字段名称<a-input v-model:value="field.name" :maxlength="100" :disabled="candidate.businessWritten" /></label>
          <label>字段标识<a-input v-model:value="field.code" :maxlength="50" :disabled="candidate.businessWritten" /></label>
          <label>字段类型<a-select v-model:value="field.fieldType" :options="fieldTypes" :disabled="candidate.businessWritten" /></label>
          <div class="ai-field-flags">
            <a-checkbox v-model:checked="field.required" :disabled="candidate.businessWritten">必填</a-checkbox>
            <a-checkbox v-model:checked="field.uniqueValue" :disabled="candidate.businessWritten">唯一</a-checkbox>
            <a-checkbox v-model:checked="field.searchable" :disabled="candidate.businessWritten">可搜索</a-checkbox>
            <a-checkbox v-if="(field.confidence < 0.7 || candidate.confidence < 0.7) && !candidate.businessWritten"
              v-model:checked="reviewed[index]">此字段置信度较低，我已核对并选择字段值</a-checkbox>
          </div>
        </div>
        <template v-if="!candidate.businessWritten">
          <a-checkbox v-model:checked="confirmed">我已核对上述字段，确认保存到当前模块草稿</a-checkbox>
          <div class="ai-field-actions"><a-button type="primary" :loading="saving" :disabled="!canSave" @click="save">确认保存字段</a-button></div>
        </template>
        <a-alert v-else type="success" show-icon :message="`已保存 ${candidate.resultFieldIds.length} 个字段到模块草稿`" />
      </div>
    </div>
  </section>
</template>

<style scoped>
.ai-field-panel { margin-bottom: 18px; padding: 14px 16px; border: 1px solid #dbeafe; border-radius: 10px; background: #f8fbff; }
.ai-field-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.ai-field-heading > div, .ai-field-body, .ai-field-candidate, .ai-field-body label, .ai-field-row label { display: grid; gap: 8px; }
.ai-field-heading small { color: #64748b; }
.ai-field-body, .ai-field-candidate { margin-top: 14px; }
.ai-field-actions { display: flex; justify-content: flex-end; }
.ai-field-candidate p { margin: 0; color: #475569; }
.ai-field-row { display: grid; grid-template-columns: 1fr 1fr 1fr; align-items: end; gap: 10px; padding: 12px; border: 1px solid #e2e8f0; border-radius: 8px; background: white; }
.ai-field-flags { display: flex; flex-wrap: wrap; grid-column: 1 / -1; gap: 12px; }
@media (max-width: 800px) { .ai-field-row { grid-template-columns: 1fr; } }
</style>
