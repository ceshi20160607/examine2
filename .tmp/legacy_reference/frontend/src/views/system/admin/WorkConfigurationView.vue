<script setup lang="ts">
import { Plus, RefreshCw, Rocket, RotateCcw, ShieldCheck, Trash2 } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { ApiRequestError } from '@/services/api'
import { configApi } from '@/services/config'
import { workApi } from '@/services/work'
import type { ConfigDictionary } from '@/types/config'
import type {
  WorkConfigurationField,
  WorkConfigurationSnapshot,
  WorkConfigurationVersion,
  WorkFieldType,
  WorkKanbanRole,
  WorkObjectType,
  WorkPublishCheck,
} from '@/types/work'

const route = useRoute()
const systemId = computed(() => String(route.params.systemId))
const activeTab = ref('design')
const objectType = ref<WorkObjectType>('PROJECT_TASK')
const versions = ref<WorkConfigurationVersion[]>([])
const dictionaries = ref<ConfigDictionary[]>([])
const selectedDraftId = ref('')
const checkResult = ref<WorkPublishCheck | null>(null)
const loading = ref(false)
const mutation = ref('')
const error = ref('')
const result = ref('')

const snapshot = reactive<WorkConfigurationSnapshot>({
  fields: { PROJECT_TASK: [], ORDINARY_TASK: [], DAILY_REPORT: [] },
})

const objectOptions: Array<{ value: WorkObjectType; label: string }> = [
  { value: 'PROJECT_TASK', label: '项目任务' },
  { value: 'ORDINARY_TASK', label: '普通任务' },
  { value: 'DAILY_REPORT', label: '日报' },
]
const fieldTypes: WorkFieldType[] = [
  'TEXT', 'NUMBER', 'BOOLEAN', 'DATE', 'DATETIME', 'SELECT', 'MULTI_SELECT',
]
const kanbanRoles: WorkKanbanRole[] = ['NONE', 'COLUMN', 'GROUP', 'NUMERIC']
const fields = computed(() => snapshot.fields[objectType.value])
const enabledDictionaries = computed(() => dictionaries.value.filter(item => item.status === 'ENABLED'))
const selectedDraft = computed(() => versions.value.find(item => item.id === selectedDraftId.value && item.status === 'DRAFT'))

function cloneSnapshot(value: WorkConfigurationSnapshot): WorkConfigurationSnapshot {
  return JSON.parse(JSON.stringify(value)) as WorkConfigurationSnapshot
}

function message(cause: unknown) {
  if (cause instanceof ApiRequestError) return `${cause.code}：${cause.message}`
  return cause instanceof Error ? cause.message : '工作配置请求失败'
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [history, dictionaryResult] = await Promise.all([
      workApi.workConfigurationHistory(systemId.value),
      configApi.dictionaries(systemId.value).catch(() => []),
    ])
    versions.value = history.items
    dictionaries.value = dictionaryResult
    const draft = versions.value.find(item => item.status === 'DRAFT')
    if (draft && !selectedDraftId.value) selectVersion(draft)
  } catch (cause) {
    error.value = message(cause)
  } finally {
    loading.value = false
  }
}

function selectVersion(version: WorkConfigurationVersion) {
  selectedDraftId.value = version.status === 'DRAFT' ? version.id : ''
  snapshot.fields = cloneSnapshot(version.snapshot).fields
  checkResult.value = null
  activeTab.value = 'design'
}

function newDesign() {
  selectedDraftId.value = ''
  snapshot.fields = { PROJECT_TASK: [], ORDINARY_TASK: [], DAILY_REPORT: [] }
  checkResult.value = null
  result.value = ''
}

function addField() {
  const index = fields.value.length + 1
  fields.value.push({
    code: `field_${index}`,
    name: `字段 ${index}`,
    type: 'TEXT',
    required: false,
    dictionaryCode: null,
    readPermission: null,
    editPermission: null,
    cardVisible: false,
    kanbanRole: 'NONE',
  })
  checkResult.value = null
}

function removeField(index: number) {
  fields.value.splice(index, 1)
  checkResult.value = null
}

function changeType(field: WorkConfigurationField) {
  if (!['SELECT', 'MULTI_SELECT'].includes(field.type)) field.dictionaryCode = null
  if (field.kanbanRole === 'COLUMN' && field.type !== 'SELECT') field.kanbanRole = 'NONE'
  if (field.kanbanRole === 'GROUP' && !['SELECT', 'MULTI_SELECT'].includes(field.type)) field.kanbanRole = 'NONE'
  if (field.kanbanRole === 'NUMERIC' && field.type !== 'NUMBER') field.kanbanRole = 'NONE'
}

function normalizedSnapshot() {
  const value = cloneSnapshot(snapshot)
  for (const field of Object.values(value.fields).flat()) {
    field.code = field.code.trim()
    field.name = field.name.trim()
    field.dictionaryCode = field.dictionaryCode?.trim() || null
    field.readPermission = field.readPermission?.trim() || null
    field.editPermission = field.editPermission?.trim() || null
  }
  return value
}

function validateDesign(value: WorkConfigurationSnapshot) {
  for (const [type, items] of Object.entries(value.fields)) {
    const codes = new Set<string>()
    const kanbanRoles = new Set<WorkKanbanRole>()
    for (const field of items) {
      if (!/^[a-z][a-z0-9_.-]{0,63}$/.test(field.code) || !field.name) return `${type} 存在无效字段编码或名称`
      if (codes.has(field.code)) return `${type} 存在重复字段：${field.code}`
      codes.add(field.code)
      if (['SELECT', 'MULTI_SELECT'].includes(field.type) && !field.dictionaryCode) return `${field.name} 必须绑定字典`
      if (type === 'DAILY_REPORT' && field.kanbanRole !== 'NONE') return '日报字段不能参与 Kanban'
      if (field.kanbanRole !== 'NONE' && kanbanRoles.has(field.kanbanRole)) return `${type} 的 ${field.kanbanRole} Kanban 角色只能配置一个字段`
      kanbanRoles.add(field.kanbanRole)
    }
  }
  return ''
}

async function saveDraft() {
  const value = normalizedSnapshot()
  const invalid = validateDesign(value)
  if (invalid) { error.value = invalid; return }
  mutation.value = 'draft'
  error.value = ''
  result.value = ''
  try {
    const created = await workApi.createWorkConfigurationDraft(systemId.value, value)
    versions.value = [created, ...versions.value]
    selectVersion(created)
    result.value = `草稿修订 ${created.revision} 已保存`
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function checkDraft() {
  if (!selectedDraft.value) return
  mutation.value = 'check'
  error.value = ''
  result.value = ''
  try {
    checkResult.value = await workApi.checkWorkConfiguration(
      systemId.value, selectedDraft.value.id, selectedDraft.value.version,
    )
    result.value = `修订 ${checkResult.value.revision} 发布检查通过`
  } catch (cause) {
    checkResult.value = null
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function publishDraft() {
  if (!selectedDraft.value || !checkResult.value?.ready) return
  mutation.value = 'publish'
  error.value = ''
  try {
    const published = await workApi.publishWorkConfiguration(
      systemId.value, selectedDraft.value.id, selectedDraft.value.version,
    )
    selectedDraftId.value = ''
    checkResult.value = null
    result.value = `修订 ${published.revision} 已发布`
    await load()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

async function rollback(version: WorkConfigurationVersion) {
  if (version.status === 'DRAFT' || !window.confirm(`确认回滚到修订 ${version.revision}？系统会创建新的发布修订。`)) return
  mutation.value = `rollback:${version.revision}`
  error.value = ''
  try {
    const rolled = await workApi.rollbackWorkConfiguration(systemId.value, version.revision)
    result.value = `已回滚并发布为修订 ${rolled.revision}`
    await load()
  } catch (cause) {
    error.value = message(cause)
  } finally {
    mutation.value = ''
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page work-configuration-page">
    <AdminPageHeader title="工作配置" description="配置项目任务、普通任务和日报字段；发布后由工作运行态直接执行。">
      <template #actions>
        <a-button :loading="loading" @click="load"><RefreshCw :size="15" />刷新</a-button>
        <a-button @click="newDesign"><Plus :size="15" />新设计</a-button>
      </template>
    </AdminPageHeader>

    <a-alert v-if="error" type="error" show-icon closable :message="error" @close="error = ''" />
    <a-alert v-if="result" type="success" show-icon closable :message="result" @close="result = ''" />

    <a-tabs v-model:active-key="activeTab" class="work-config-tabs">
      <a-tab-pane key="design" tab="字段与看板">
        <div class="work-config-toolbar">
          <a-segmented v-model:value="objectType" :options="objectOptions" />
          <span>{{ selectedDraft ? `正在编辑草稿修订 ${selectedDraft.revision}` : '新设计将保存为独立草稿修订' }}</span>
          <a-button type="primary" @click="addField"><Plus :size="15" />添加字段</a-button>
        </div>

        <div class="work-field-table-wrap">
          <table class="work-field-table">
            <thead><tr><th>字段</th><th>类型 / 字典</th><th>权限</th><th>卡片 / Kanban</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="(field, index) in fields" :key="`${objectType}:${index}`">
                <td><a-input v-model:value="field.name" aria-label="字段名称" placeholder="显示名称" /><a-input v-model:value="field.code" aria-label="字段编码" placeholder="field_code" /><a-checkbox v-model:checked="field.required">必填</a-checkbox></td>
                <td>
                  <a-select v-model:value="field.type" aria-label="字段类型" @change="changeType(field)"><a-select-option v-for="value in fieldTypes" :key="value" :value="value">{{ value }}</a-select-option></a-select>
                  <a-select v-if="['SELECT','MULTI_SELECT'].includes(field.type)" v-model:value="field.dictionaryCode" aria-label="绑定字典" allow-clear show-search placeholder="选择字典"><a-select-option v-for="dictionary in enabledDictionaries" :key="dictionary.code" :value="dictionary.code">{{ dictionary.name }} · {{ dictionary.code }}</a-select-option></a-select>
                </td>
                <td><a-input v-model:value="field.readPermission" aria-label="查看权限" allow-clear placeholder="查看权限（可空）" /><a-input v-model:value="field.editPermission" aria-label="编辑权限" allow-clear placeholder="编辑权限（可空）" /></td>
                <td><a-checkbox v-model:checked="field.cardVisible">卡片显示</a-checkbox><a-select v-model:value="field.kanbanRole" aria-label="Kanban 角色" :disabled="objectType === 'DAILY_REPORT'"><a-select-option v-for="role in kanbanRoles" :key="role" :value="role">{{ role }}</a-select-option></a-select></td>
                <td><a-button danger aria-label="删除字段" @click="removeField(index)"><Trash2 :size="15" /></a-button></td>
              </tr>
            </tbody>
          </table>
          <a-empty v-if="!fields.length" description="当前对象尚未配置字段" />
        </div>

        <footer class="work-config-actions">
          <a-tag v-if="checkResult?.ready" color="success"><ShieldCheck :size="14" />发布检查通过</a-tag>
          <a-button class="work-config-save" :loading="mutation === 'draft'" :disabled="Boolean(mutation)" @click="saveDraft">保存新草稿</a-button>
          <a-button class="work-config-check" :loading="mutation === 'check'" :disabled="!selectedDraft || Boolean(mutation)" @click="checkDraft"><ShieldCheck :size="15" />发布检查</a-button>
          <a-button class="work-config-publish" type="primary" :loading="mutation === 'publish'" :disabled="!selectedDraft || !checkResult?.ready || Boolean(mutation)" @click="publishDraft"><Rocket :size="15" />发布</a-button>
        </footer>
      </a-tab-pane>

      <a-tab-pane key="history" tab="版本历史">
        <a-table :data-source="versions" :pagination="false" row-key="id" size="middle">
          <a-table-column title="修订" data-index="revision" />
          <a-table-column title="状态"><template #default="{ record }"><a-tag :color="record.status === 'PUBLISHED' ? 'green' : record.status === 'DRAFT' ? 'blue' : 'default'">{{ record.status }}</a-tag></template></a-table-column>
          <a-table-column title="发布时间"><template #default="{ record }">{{ record.publishedAt || '未发布' }}</template></a-table-column>
          <a-table-column title="回滚来源"><template #default="{ record }">{{ record.rollbackFromRevision || '—' }}</template></a-table-column>
          <a-table-column title="操作"><template #default="{ record }"><a-button v-if="record.status === 'DRAFT'" size="small" @click="selectVersion(record)">打开草稿</a-button><a-button v-else size="small" :loading="mutation === `rollback:${record.revision}`" @click="rollback(record)"><RotateCcw :size="14" />回滚到此修订</a-button></template></a-table-column>
        </a-table>
      </a-tab-pane>
    </a-tabs>
  </section>
</template>

<style scoped>
.work-configuration-page{display:grid;gap:14px;min-width:0}.work-config-toolbar,.work-config-actions{display:flex;align-items:center;gap:12px}.work-config-toolbar{justify-content:space-between;margin-bottom:14px}.work-config-toolbar>span{flex:1;color:#6b7685}.work-field-table-wrap{overflow:auto;border:1px solid #e0e6ea}.work-field-table{width:100%;min-width:980px;border-collapse:collapse}.work-field-table th,.work-field-table td{padding:10px;border-bottom:1px solid #e7ebee;text-align:left;vertical-align:top}.work-field-table th{background:#f7f9fa;color:#66737d;font-size:12px}.work-field-table td{display:table-cell}.work-field-table td>div,.work-field-table td>.ant-input,.work-field-table td>.ant-select{display:block;width:100%;margin-bottom:8px}.work-field-table td:first-child{min-width:190px}.work-field-table td:nth-child(2){min-width:210px}.work-field-table td:nth-child(3){min-width:220px}.work-config-actions{justify-content:flex-end;padding-top:14px}.work-config-actions .ant-tag{margin-right:auto;display:inline-flex;align-items:center;gap:5px}
</style>
