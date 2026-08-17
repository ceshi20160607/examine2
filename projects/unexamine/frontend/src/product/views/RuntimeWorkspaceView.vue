<script setup lang="ts">
import { EditOutlined, EyeOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { message, Modal } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api'
import { systemContext, systemTokens } from '../session'
import type {
  RuntimeField,
  RuntimeModuleCatalogItem,
  RuntimeModuleConfiguration,
  RuntimeRecord,
  RuntimeRecordList,
} from '../types'

const props = defineProps<{ refreshKey?: number }>()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const modules = ref<RuntimeModuleCatalogItem[]>([])
const activeCode = ref('')
const configuration = ref<RuntimeModuleConfiguration>()
const records = ref<RuntimeRecord[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const detailOpen = ref(false)
const formOpen = ref(false)
const detail = ref<RuntimeRecord>()
const editing = ref<RuntimeRecord>()
const form = reactive({
  title: '', recordNumber: '', status: 'ACTIVE', ownerMemberId: undefined as number | undefined,
  departmentId: undefined as number | undefined, participantText: '', fields: {} as Record<string, unknown>,
})

const token = computed(() => systemTokens.value?.accessToken || '')
const fields = computed(() => configuration.value?.configuration.fields.filter((field) => field.status === 'ACTIVE') || [])
const fieldsForPage = (pageType: string) => {
  const page = configuration.value?.configuration.pages.find((item) => item.pageType === pageType)
  if (!page) return fields.value
  try {
    const layout = JSON.parse(page.layoutJson) as { fieldCodes?: string[] }
    if (!layout.fieldCodes) return fields.value
    const byCode = new Map(fields.value.map((field) => [field.code, field]))
    return layout.fieldCodes.map((code) => byCode.get(code)).filter((field): field is RuntimeField => Boolean(field))
  } catch { return fields.value }
}
const listFields = computed(() => fieldsForPage('LIST'))
const formFields = computed(() => fieldsForPage('FORM'))
const detailFields = computed(() => fieldsForPage('DETAIL'))
const actions = computed(() => configuration.value?.configuration.actions || [])
const activeModule = computed(() => modules.value.find((item) => item.moduleCode === activeCode.value))
const groupedModules = computed(() => {
  const groups = new Map<number, { id: number; name: string; modules: RuntimeModuleCatalogItem[] }>()
  modules.value.forEach((item) => {
    if (!groups.has(item.groupId)) groups.set(item.groupId, { id: item.groupId, name: item.groupName, modules: [] })
    groups.get(item.groupId)?.modules.push(item)
  })
  return [...groups.values()]
})

function hasAction(code: string) {
  return actions.value.some((action) => action.code === code)
}

function actionConfiguration(code: string) {
  const action = actions.value.find((item) => item.code === code)
  try { return JSON.parse(action?.configJson || '{}') as { confirmation?: boolean; confirmationText?: string } }
  catch { return {} }
}

function runAction(code: string, callback: () => void) {
  const config = actionConfiguration(code)
  if (!config.confirmation) return callback()
  Modal.confirm({ title: config.confirmationText || '确认执行此操作？', onOk: callback })
}

async function loadModules() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    modules.value = await api<RuntimeModuleCatalogItem[]>('/api/runtime/modules', {}, token.value)
    const code = modules.value.some((item) => item.moduleCode === activeCode.value)
      ? activeCode.value : modules.value[0]?.moduleCode
    if (code) await selectModule(code)
    else {
      activeCode.value = ''
      configuration.value = undefined
      records.value = []
    }
  } catch (reason) {
    error.value = readable(reason)
  } finally {
    loading.value = false
  }
}

async function selectModule(code: string) {
  activeCode.value = code
  page.value = 1
  await Promise.all([loadConfiguration(), loadRecords()])
}

async function loadConfiguration() {
  configuration.value = await api<RuntimeModuleConfiguration>(
    `/api/runtime/modules/${activeCode.value}/configuration`, {}, token.value)
}

async function loadRecords() {
  loading.value = true
  error.value = ''
  try {
    const result = await api<RuntimeRecordList>(
      `/api/runtime/modules/${activeCode.value}/records?page=${page.value}&pageSize=${pageSize.value}`, {}, token.value)
    records.value = result.records
    total.value = result.total
  } catch (reason) {
    error.value = readable(reason)
  } finally {
    loading.value = false
  }
}

async function openDetail(record: RuntimeRecord) {
  try {
    detail.value = await api<RuntimeRecord>(`/api/runtime/modules/${activeCode.value}/records/${record.id}`, {}, token.value)
    detailOpen.value = true
  } catch (reason) {
    message.error(readable(reason))
  }
}

function openCreate() {
  editing.value = undefined
  Object.assign(form, {
    title: '', recordNumber: '', status: 'ACTIVE', ownerMemberId: systemContext.value?.memberId || undefined,
    departmentId: undefined, participantText: '', fields: {},
  })
  fields.value.forEach((field) => { form.fields[field.code] = field.fieldType === 'BOOLEAN' ? false : undefined })
  formOpen.value = true
}

function openEdit(record: RuntimeRecord) {
  editing.value = record
  Object.assign(form, {
    title: record.title,
    recordNumber: record.recordNumber || '',
    status: record.status,
    ownerMemberId: record.ownerMemberId,
    departmentId: record.departmentId,
    participantText: record.participantMemberIds.join(','),
    fields: { ...record.fields },
  })
  formOpen.value = true
  detailOpen.value = false
}

function options(field: RuntimeField) {
  try {
    const config = JSON.parse(field.configJson) as { options?: Array<{ value?: string; code?: string; label?: string; status?: string; disabled?: boolean }> }
    return (config.options || []).filter((option) => option.status !== 'DISABLED' && !option.disabled)
      .map((option) => ({ value: option.value || option.code, label: option.label || option.value || option.code }))
  } catch { return [] }
}

function participantIds() {
  return [...new Set(form.participantText.split(',').map((item) => Number(item.trim())).filter((item) => Number.isInteger(item) && item > 0))]
}

function normalizedFields() {
  return Object.fromEntries(Object.entries(form.fields).filter(([, value]) => value !== undefined && value !== null && value !== ''))
}

async function saveRecord() {
  saving.value = true
  try {
    const body = {
      title: form.title,
      recordNumber: form.recordNumber || null,
      status: form.status,
      ownerMemberId: form.ownerMemberId || null,
      departmentId: form.departmentId || null,
      participantMemberIds: participantIds(),
      fields: normalizedFields(),
      ...(editing.value ? { version: editing.value.version } : {}),
    }
    const path = editing.value
      ? `/api/runtime/modules/${activeCode.value}/records/${editing.value.id}`
      : `/api/runtime/modules/${activeCode.value}/records`
    await api(path, { method: editing.value ? 'PUT' : 'POST', body: JSON.stringify(body) }, token.value)
    formOpen.value = false
    message.success(editing.value ? '业务数据已更新' : '业务数据已创建')
    await loadRecords()
  } catch (reason) {
    message.error(readable(reason))
  } finally {
    saving.value = false
  }
}

function displayValue(field: RuntimeField, value: unknown) {
  if (value === undefined || value === null || value === '') return '—'
  if (field.fieldType === 'BOOLEAN') return value ? '是' : '否'
  if (['SINGLE_SELECT', 'STATUS'].includes(field.fieldType)) return options(field).find((item) => item.value === value)?.label || String(value)
  return String(value)
}

function actionVisibleForRecord(record: RuntimeRecord) {
  const scope = systemContext.value?.dataScopes?.[`MODULE:${activeCode.value}:UPDATE`]
  if (!scope || scope.terms.some((term) => term.type === 'ALL')) return true
  return scope.terms.some((term) => term.type === 'SELF' && record.ownerMemberId === systemContext.value?.memberId)
}

function readable(reason: unknown) {
  if (reason instanceof ApiError) return reason.traceId ? `${reason.message}（追踪号：${reason.traceId}）` : reason.message
  return '请求失败，请稍后重试'
}

onMounted(loadModules)
defineExpose({ refresh: loadModules })
</script>

<template>
  <div class="runtime-page" :data-refresh-key="props.refreshKey">
    <div class="page-heading">
      <div><p class="eyebrow">业务运行</p><h1>{{ activeModule?.moduleName || '业务模块' }}</h1><p>页面来自当前发布版本，数据只读取当前租户和权限范围。</p></div>
      <div class="heading-actions"><a-button :loading="loading" @click="loadModules"><ReloadOutlined />刷新</a-button>
        <a-button v-if="hasAction('CREATE')" type="primary" @click="runAction('CREATE', openCreate)"><PlusOutlined />新建{{ activeModule?.moduleName }}</a-button></div>
    </div>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <div class="runtime-layout">
      <aside class="runtime-modules panel-card">
        <div class="panel-title"><strong>业务模块</strong><span>{{ modules.length }}</span></div>
        <section v-for="group in groupedModules" :key="group.id" class="runtime-group"><p>{{ group.name }}</p>
          <button v-for="module in group.modules" :key="module.moduleId" type="button"
            :class="['module-tree-item', { active: activeCode === module.moduleCode }]" @click="selectModule(module.moduleCode)">
            <span>{{ module.moduleName.slice(0, 1) }}</span><strong>{{ module.moduleName }}</strong>
          </button></section>
        <a-empty v-if="!modules.length && !loading" image="simple" description="没有可见的已发布模块" />
      </aside>
      <main class="record-panel panel-card">
        <template v-if="activeCode">
          <div class="record-summary"><span>共 {{ total }} 条</span><a-tag color="blue">发布版本 v{{ configuration?.versionNumber }}</a-tag></div>
          <a-table :data-source="records" :loading="loading" row-key="id" :pagination="false" :scroll="{ x: 800 }">
            <a-table-column title="标题" data-index="title" :width="210"><template #default="{ record }"><a class="record-link" @click="openDetail(record)">{{ record.title }}</a><small>{{ record.recordNumber || `#${record.id}` }}</small></template></a-table-column>
            <a-table-column v-for="field in listFields.slice(0, 5)" :key="field.code" :title="field.name" :width="150"><template #default="{ record }">{{ displayValue(field, record.fields[field.code]) }}</template></a-table-column>
            <a-table-column title="状态" data-index="status" :width="100"><template #default="{ text }"><a-tag>{{ text }}</a-tag></template></a-table-column>
            <a-table-column title="更新时间" data-index="updatedAt" :width="170" />
            <a-table-column title="操作" fixed="right" :width="130"><template #default="{ record }"><a-button v-if="hasAction('DETAIL')" type="link" size="small" @click="runAction('DETAIL', () => openDetail(record))"><EyeOutlined />详情</a-button><a-button v-if="hasAction('UPDATE') && actionVisibleForRecord(record)" type="link" size="small" @click="runAction('UPDATE', () => openEdit(record))"><EditOutlined />编辑</a-button></template></a-table-column>
          </a-table>
          <div class="record-pagination"><a-pagination v-model:current="page" v-model:page-size="pageSize" :total="total" show-size-changer @change="loadRecords" /></div>
        </template>
        <a-empty v-else description="发布模块后，业务页面会在这里出现" />
      </main>
    </div>

    <a-drawer v-model:open="detailOpen" :title="detail?.title" width="520" class="detail-drawer">
      <template v-if="detail"><div class="detail-meta"><span>{{ detail.recordNumber || `#${detail.id}` }}</span><a-tag>{{ detail.status }}</a-tag></div>
        <a-descriptions :column="1" bordered size="small"><a-descriptions-item v-for="field in detailFields" :key="field.code" :label="field.name">{{ displayValue(field, detail.fields[field.code]) }}</a-descriptions-item>
          <a-descriptions-item label="负责人">{{ detail.ownerMemberId || '—' }}</a-descriptions-item><a-descriptions-item label="相关人">{{ detail.participantMemberIds.join('、') || '—' }}</a-descriptions-item><a-descriptions-item label="更新时间">{{ detail.updatedAt }}</a-descriptions-item></a-descriptions>
        <div class="detail-tabs"><strong>更多信息</strong><span>第一期暂无转化关系；转化功能进入后会在这里增加“转化”标签页。</span></div></template>
      <template #footer><div class="drawer-footer"><a-button @click="detailOpen = false">关闭</a-button><a-button v-if="detail && hasAction('UPDATE') && actionVisibleForRecord(detail)" type="primary" @click="openEdit(detail)">编辑</a-button></div></template>
    </a-drawer>
    <a-drawer v-model:open="formOpen" :title="editing ? `编辑${activeModule?.moduleName}` : `新建${activeModule?.moduleName}`" width="560">
      <a-form layout="vertical"><div class="form-grid"><a-form-item label="标题" required><a-input v-model:value="form.title" /></a-form-item><a-form-item label="业务编号"><a-input v-model:value="form.recordNumber" /></a-form-item></div>
        <div class="form-grid"><a-form-item label="负责人 ID"><a-input-number v-model:value="form.ownerMemberId" :min="1" style="width:100%" /></a-form-item><a-form-item label="部门 ID"><a-input-number v-model:value="form.departmentId" :min="1" style="width:100%" /></a-form-item></div>
        <a-form-item label="相关人 ID" extra="多个成员 ID 用英文逗号分隔"><a-input v-model:value="form.participantText" /></a-form-item>
        <a-divider>模块字段</a-divider>
        <a-form-item v-for="field in formFields" :key="field.code" :label="field.name" :required="field.required">
          <a-textarea v-if="field.fieldType === 'MULTILINE_TEXT'" v-model:value="form.fields[field.code]" :rows="4" />
          <a-input-number v-else-if="['NUMBER', 'MONEY'].includes(field.fieldType)" v-model:value="form.fields[field.code]" style="width:100%" />
          <a-date-picker v-else-if="field.fieldType === 'DATE'" v-model:value="form.fields[field.code]" value-format="YYYY-MM-DD" style="width:100%" />
          <a-date-picker v-else-if="field.fieldType === 'DATETIME'" v-model:value="form.fields[field.code]" show-time value-format="YYYY-MM-DDTHH:mm:ss" style="width:100%" />
          <a-select v-else-if="['SINGLE_SELECT', 'STATUS'].includes(field.fieldType)" v-model:value="form.fields[field.code]" :options="options(field)" allow-clear />
          <a-switch v-else-if="field.fieldType === 'BOOLEAN'" v-model:checked="form.fields[field.code]" />
          <a-input-number v-else-if="['MEMBER', 'DEPARTMENT'].includes(field.fieldType)" v-model:value="form.fields[field.code]" :min="1" style="width:100%" />
          <a-input v-else v-model:value="form.fields[field.code]" />
        </a-form-item>
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="formOpen = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveRecord">保存</a-button></div></template>
    </a-drawer>
  </div>
</template>
