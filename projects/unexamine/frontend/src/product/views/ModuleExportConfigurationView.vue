<script setup lang="ts">
import { message } from 'ant-design-vue'
import { computed, ref, watch } from 'vue'
import { api, ApiError } from '../api'
import { systemTokens } from '../session'
import type { ExportFieldView, ModuleOverview } from '../types'

interface ConfigDraft {
  id: number; name: string; selectedFields: string[]; relationDisplay: 'LABEL' | 'ID'
  draftRevision: number; version: number; status: string
}
interface ConfigPublished {
  versionId: number; versionNumber: number; selectedFields: string[]; relationDisplay: 'LABEL' | 'ID'
}
interface ConfigOverview {
  availableFields: ExportFieldView[]; draft: ConfigDraft | null; published: ConfigPublished | null
}
interface AiOptions {
  agents: Array<{ id: number; name: string }>; moduleId: number; availableFields: ExportFieldView[]
}
interface AiCandidate {
  pendingWriteId: number; status: string; outcome: string; message: string
  name: string; selectedFields: string[]; relationDisplay: 'LABEL' | 'ID'; confidence: number
  configVersion: number | null; businessWritten: boolean; version: number
}
const token = computed(() => systemTokens.value?.accessToken || '')
const overview = ref<ModuleOverview>()
const selectedModuleId = ref<number>()
const selectedModule = computed(() => overview.value?.modules.find(item => item.id === selectedModuleId.value))
const config = ref<ConfigOverview>()
const name = ref('')
const fields = ref<string[]>([])
const addCode = ref<string>()
const relationDisplay = ref<'LABEL' | 'ID'>('LABEL')
const dirty = ref(false)
const loading = ref(false)
const saving = ref(false)
const publishing = ref(false)
const error = ref('')
const aiOptions = ref<AiOptions>()
const aiAgentId = ref<number>()
const aiInstruction = ref('')
const aiCandidate = ref<AiCandidate>()
const aiName = ref('')
const aiFields = ref<string[]>([])
const aiRelationDisplay = ref<'LABEL' | 'ID'>('LABEL')
const aiConfirmed = ref(false)
const aiReviewed = ref(false)
const aiBusy = ref(false)
const aiError = ref('')
const fieldNames = computed(() => new Map(config.value?.availableFields.map(field => [field.code, field.name]) || []))
const availableCodes = computed(() => new Set(config.value?.availableFields.map(field => field.code) || []))
const aiCanSave = computed(() => Boolean(aiCandidate.value?.status === 'PENDING_CONFIRMATION'
  && !aiCandidate.value.businessWritten && aiConfirmed.value
  && (aiCandidate.value.confidence >= 0.7 || aiReviewed.value)
  && aiName.value.trim() && aiFields.value.length
  && aiFields.value.every(code => aiOptions.value?.availableFields.some(field => field.code === code))
  && new Set(aiFields.value).size === aiFields.value.length))
const configPath = computed(() => '/api/runtime/modules/' + selectedModule.value?.code + '/exports/config')

function readable(reason: unknown, fallback: string) {
  return reason instanceof ApiError ? reason.message : fallback
}
async function loadModules() {
  loading.value = true; error.value = ''
  try {
    overview.value = await api<ModuleOverview>('/api/admin/module-config', {}, token.value)
    if (!overview.value.modules.some(item => item.id === selectedModuleId.value)) {
      selectedModuleId.value = overview.value.modules[0]?.id
    }
  } catch (reason) { error.value = readable(reason, '模块列表加载失败') }
  finally { loading.value = false }
}
async function loadConfig() {
  if (!selectedModule.value) return
  loading.value = true; error.value = ''; config.value = undefined
  try {
    config.value = await api<ConfigOverview>(configPath.value, {}, token.value)
    name.value = config.value.draft?.name || selectedModule.value.name + '导出配置'
    fields.value = [...(config.value.draft?.selectedFields || config.value.published?.selectedFields || [])]
    relationDisplay.value = config.value.draft?.relationDisplay || config.value.published?.relationDisplay || 'LABEL'
    dirty.value = false
  } catch (reason) { error.value = readable(reason, '当前模块导出配置加载失败；请确认模块已发布导出动作') }
  finally { loading.value = false }
}
function moveField(code: string, direction: -1 | 1) {
  const index = fields.value.indexOf(code)
  const next = index + direction
  if (index < 0 || next < 0 || next >= fields.value.length) return
  const copy = [...fields.value]
  const current = copy[index]!
  copy[index] = copy[next]!
  copy[next] = current
  fields.value = copy
  dirty.value = true
}
function addField() {
  if (!addCode.value || !availableCodes.value.has(addCode.value) || fields.value.includes(addCode.value)) return
  fields.value = [...fields.value, addCode.value]
  addCode.value = undefined
  dirty.value = true
}
async function save() {
  if (!config.value || !name.value.trim() || !fields.value.length
    || fields.value.some(code => !availableCodes.value.has(code))) return
  saving.value = true; error.value = ''
  try {
    await api<ConfigDraft>(configPath.value, {
      method: 'PUT', body: JSON.stringify({
        name: name.value.trim(), selectedFields: fields.value,
        relationDisplay: relationDisplay.value, expectedVersion: config.value.draft?.version ?? null,
      }),
    }, token.value)
    await loadConfig()
    message.success('导出配置草稿已保存')
  } catch (reason) { error.value = readable(reason, '保存导出配置失败') }
  finally { saving.value = false }
}
async function publish() {
  if (!config.value?.draft || dirty.value) return
  publishing.value = true; error.value = ''
  try {
    await api<ConfigPublished>(configPath.value + '/publish', {
      method: 'POST', body: JSON.stringify({ expectedDraftRevision: config.value.draft.draftRevision }),
    }, token.value)
    await loadConfig()
    message.success('导出配置已发布，业务页面将按该版本初始化导出列')
  } catch (reason) { error.value = readable(reason, '发布导出配置失败') }
  finally { publishing.value = false }
}
async function loadAiOptions() {
  if (!selectedModuleId.value) return
  aiBusy.value = true; aiError.value = ''; aiOptions.value = undefined
  try {
    aiOptions.value = await api<AiOptions>(
      '/api/admin/system/ai/export-config-drafts/options?moduleId=' + selectedModuleId.value,
      {}, token.value)
    aiAgentId.value = aiOptions.value.agents[0]?.id
  } catch (reason) { aiError.value = readable(reason, 'AI 导出配置助手加载失败') }
  finally { aiBusy.value = false }
}
async function recognizeAi() {
  if (!selectedModuleId.value || !aiAgentId.value || !aiInstruction.value.trim()) return
  aiBusy.value = true; aiError.value = ''; aiCandidate.value = undefined; aiConfirmed.value = false
  try {
    aiCandidate.value = await api<AiCandidate>('/api/admin/system/ai/export-config-drafts/recognize', {
      method: 'POST', body: JSON.stringify({
        agentId: aiAgentId.value, moduleId: selectedModuleId.value, instruction: aiInstruction.value.trim(),
      }),
    }, token.value)
    aiName.value = aiCandidate.value.name
    aiFields.value = [...aiCandidate.value.selectedFields]
    aiRelationDisplay.value = aiCandidate.value.relationDisplay
  } catch (reason) { aiError.value = readable(reason, 'AI 字段建议生成失败') }
  finally { aiBusy.value = false }
}
async function confirmAi() {
  if (!aiCandidate.value || !aiCanSave.value) return
  aiBusy.value = true; aiError.value = ''
  try {
    aiCandidate.value = await api<AiCandidate>(
      '/api/admin/system/ai/export-config-drafts/' + aiCandidate.value.pendingWriteId + '/confirm', {
        method: 'POST', body: JSON.stringify({
          expectedVersion: aiCandidate.value.version, confirmed: true,
          name: aiName.value.trim(), selectedFields: aiFields.value,
          relationDisplay: aiRelationDisplay.value, expectedConfigVersion: aiCandidate.value.configVersion,
          reviewedLowConfidence: aiReviewed.value,
        }),
      }, token.value)
    if (aiCandidate.value.businessWritten) {
      await loadConfig()
      message.success('AI 建议已保存为草稿；仍需单独发布')
    }
  } catch (reason) { aiError.value = readable(reason, 'AI 导出配置保存失败') }
  finally { aiBusy.value = false }
}
watch(selectedModuleId, () => {
  aiOptions.value = undefined; aiCandidate.value = undefined
  void loadConfig()
})
void loadModules()
</script>

<template>
  <section class="export-admin">
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <label>选择业务模块
      <a-select v-model:value="selectedModuleId" show-search option-filter-prop="label" style="width: 100%; max-width: 480px"
        :options="overview?.modules.map(item => ({ value: item.id, label: item.name + '（' + item.code + '）' })) || []" />
    </label>
    <a-spin :spinning="loading">
      <template v-if="config">
        <div class="export-admin-row"><strong>当前发布版本</strong>
          <a-tag v-if="config.published" color="green">第 {{ config.published.versionNumber }} 版 · {{ config.published.selectedFields.length }} 列</a-tag>
          <span v-else>尚未发布</span>
        </div>
        <p v-if="config.published">发布列：{{ config.published.selectedFields.map(code => fieldNames.get(code) || code).join('、') }}</p>
        <a-divider>配置草稿</a-divider>
        <label>配置名称<a-input v-model:value="name" :maxlength="200" @input="dirty = true" /></label>
        <label>关联字段显示<a-radio-group v-model:value="relationDisplay" @change="dirty = true"><a-radio value="LABEL">名称</a-radio><a-radio value="ID">标识</a-radio></a-radio-group></label>
        <strong>导出字段和列顺序</strong>
        <div class="export-admin-fields">
          <div v-for="(code, index) in fields" :key="code">
            <span>{{ index + 1 }}. {{ fieldNames.get(code) || code }}</span>
            <a-space><a-button size="small" :disabled="index === 0" @click="moveField(code, -1)">上移</a-button>
              <a-button size="small" :disabled="index === fields.length - 1" @click="moveField(code, 1)">下移</a-button>
              <a-button size="small" danger @click="fields = fields.filter(item => item !== code); dirty = true">移除</a-button></a-space>
          </div>
        </div>
        <div class="export-admin-row"><a-select v-model:value="addCode" show-search option-filter-prop="label" placeholder="添加字段" style="min-width: 260px"
          :options="config.availableFields.filter(field => !fields.includes(field.code)).map(field => ({ value: field.code, label: field.name }))" />
          <a-button :disabled="!addCode" @click="addField">添加字段</a-button></div>
        <div class="export-admin-row"><a-button :loading="saving" :disabled="!name.trim() || !fields.length || Boolean(config.draft && !dirty)" @click="save">保存草稿</a-button>
          <a-button type="primary" :loading="publishing" :disabled="!config.draft || config.draft.status === 'PUBLISHED' || dirty" @click="publish">发布导出配置</a-button>
          <span v-if="dirty">请先保存当前修改</span></div>
        <a-divider>AI 辅助配置</a-divider>
        <a-alert v-if="aiError" type="error" show-icon :message="aiError" />
        <a-button :loading="aiBusy" @click="loadAiOptions">加载可用助手</a-button>
        <template v-if="aiOptions">
          <a-alert v-if="!aiOptions.agents.length" type="info" show-icon message="当前模块没有可用的导出配置助手。" />
          <template v-else>
            <label>配置助手<a-select v-model:value="aiAgentId" :options="aiOptions.agents.map(agent => ({ value: agent.id, label: agent.name }))" /></label>
            <label>导出要求<a-textarea v-model:value="aiInstruction" :rows="3" :maxlength="10000" placeholder="例如：客户名称、联系人和电话" /></label>
            <a-button :loading="aiBusy" :disabled="!aiAgentId || !aiInstruction.trim()" @click="recognizeAi">生成字段建议</a-button>
          </template>
        </template>
        <template v-if="aiCandidate">
          <a-alert :type="aiCandidate.outcome === 'READY' ? 'info' : 'warning'" show-icon :message="aiCandidate.message" />
          <template v-if="aiCandidate.status === 'PENDING_CONFIRMATION' && !aiCandidate.businessWritten">
            <p>识别置信度 {{ Math.round(aiCandidate.confidence * 100) }}%。请逐项核对字段。</p>
            <label>配置名称<a-input v-model:value="aiName" /></label>
            <label>关联字段显示<a-radio-group v-model:value="aiRelationDisplay"><a-radio value="LABEL">名称</a-radio><a-radio value="ID">标识</a-radio></a-radio-group></label>
            <label>导出字段<a-select v-model:value="aiFields" mode="multiple" show-search option-filter-prop="label"
              :options="aiOptions?.availableFields.map(field => ({ value: field.code, label: field.name })) || []" /></label>
            <a-checkbox v-if="aiCandidate.confidence < 0.7" v-model:checked="aiReviewed">低置信度结果已逐项复核</a-checkbox>
            <a-checkbox v-model:checked="aiConfirmed">确认保存为当前模块草稿</a-checkbox>
            <a-button type="primary" :loading="aiBusy" :disabled="!aiCanSave" @click="confirmAi">保存建议草稿</a-button>
          </template>
        </template>
      </template>
    </a-spin>
  </section>
</template>

<style scoped>
.export-admin { display: grid; gap: 14px; max-width: 900px; }
.export-admin label { display: grid; gap: 6px; }
.export-admin-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.export-admin-fields { display: grid; gap: 6px; }
.export-admin-fields > div { display: flex; justify-content: space-between; align-items: center; gap: 12px; border: 1px solid #e2e8f0; border-radius: 6px; padding: 8px 10px; }
</style>
