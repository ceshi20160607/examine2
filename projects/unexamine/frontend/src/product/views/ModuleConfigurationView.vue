<script setup lang="ts">
import {
  AppstoreAddOutlined,
  CheckCircleOutlined,
  CloudUploadOutlined,
  DragOutlined,
  PlusOutlined,
  ReloadOutlined,
} from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api'
import { systemTokens } from '../session'
import type {
  ConfiguredField,
  ConfiguredAction,
  ConfiguredModule,
  ModuleDraft,
  ModuleOverview,
  PublicationCheck,
} from '../types'

const emit = defineEmits<{ published: [] }>()

const fieldTypes = [
  { type: 'TEXT', name: '单行文本', hint: '名称、编号等短文本' },
  { type: 'MULTILINE_TEXT', name: '多行文本', hint: '备注和较长说明' },
  { type: 'NUMBER', name: '数字', hint: '普通数值' },
  { type: 'MONEY', name: '金额', hint: '精确金额数值' },
  { type: 'DATE', name: '日期', hint: '年月日' },
  { type: 'DATETIME', name: '日期时间', hint: '日期和具体时间' },
  { type: 'SINGLE_SELECT', name: '单选', hint: '配置固定选项' },
  { type: 'STATUS', name: '状态', hint: '业务状态选项' },
  { type: 'BOOLEAN', name: '是否', hint: '是或否' },
  { type: 'MEMBER', name: '成员', hint: '关联当前租户成员' },
  { type: 'DEPARTMENT', name: '部门', hint: '关联当前租户部门' },
]

const loading = ref(false)
const saving = ref(false)
const overview = ref<ModuleOverview>({ groups: [], modules: [] })
const selectedModuleId = ref<number>()
const draft = ref<ModuleDraft>()
const error = ref('')
const draggingType = ref('')
const groupModal = ref(false)
const moduleModal = ref(false)
const fieldDrawer = ref(false)
const activeDesignerTab = ref('FIELDS')
const actionDrawer = ref(false)
const publishModal = ref(false)
const publication = ref<PublicationCheck>()
const editingField = ref<ConfiguredField>()
const editingAction = ref<ConfiguredAction>()
const groupForm = reactive({ code: '', name: '', sortOrder: 10 })
const moduleForm = reactive({ groupId: undefined as number | undefined, code: '', name: '' })
const fieldForm = reactive({
  code: '', name: '', fieldType: 'TEXT', required: false, sortOrder: 10,
  status: 'ACTIVE', optionsText: '', version: 0,
})
const actionForm = reactive({ location: 'ROW', confirmation: false, confirmationText: '', version: 0 })

const token = computed(() => systemTokens.value?.accessToken || '')
const selectedModule = computed(() => overview.value.modules.find((item) => item.id === selectedModuleId.value))
const groupedModules = computed(() => overview.value.groups.map((group) => ({
  ...group,
  modules: overview.value.modules.filter((module) => module.groupId === group.id),
})))
const selectedPage = computed(() => draft.value?.pages.find((page) => page.pageType === activeDesignerTab.value))
const selectedPageFieldCodes = computed(() => {
  if (!selectedPage.value) return [] as string[]
  try {
    const layout = JSON.parse(selectedPage.value.layoutJson) as { fieldCodes?: string[] }
    return layout.fieldCodes || draft.value?.fields.filter((field) => field.status === 'ACTIVE').map((field) => field.code) || []
  } catch { return [] }
})

async function loadOverview(preferredModuleId?: number) {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    overview.value = await api<ModuleOverview>('/api/admin/module-config', {}, token.value)
    const target = preferredModuleId ?? selectedModuleId.value ?? overview.value.modules[0]?.id
    if (target) await selectModule(target)
    else draft.value = undefined
  } catch (reason) {
    error.value = readable(reason)
  } finally {
    loading.value = false
  }
}

async function selectModule(moduleId: number) {
  selectedModuleId.value = moduleId
  error.value = ''
  try {
    draft.value = await api<ModuleDraft>(`/api/admin/module-config/modules/${moduleId}/draft`, {}, token.value)
  } catch (reason) {
    error.value = readable(reason)
  }
}

async function createGroup() {
  saving.value = true
  try {
    await api('/api/admin/module-config/groups', {
      method: 'POST', body: JSON.stringify(groupForm),
    }, token.value)
    groupModal.value = false
    Object.assign(groupForm, { code: '', name: '', sortOrder: 10 })
    message.success('模块组已创建')
    await loadOverview()
  } catch (reason) {
    message.error(readable(reason))
  } finally {
    saving.value = false
  }
}

async function createModule() {
  if (!moduleForm.groupId) return message.warning('请选择模块组')
  saving.value = true
  try {
    const result = await api<ModuleDraft>('/api/admin/module-config/modules', {
      method: 'POST', body: JSON.stringify(moduleForm),
    }, token.value)
    moduleModal.value = false
    Object.assign(moduleForm, { groupId: undefined, code: '', name: '' })
    message.success('模块草稿已创建')
    await loadOverview(result.module.id)
  } catch (reason) {
    message.error(readable(reason))
  } finally {
    saving.value = false
  }
}

function beginField(type: string) {
  if (!selectedModuleId.value) {
    message.warning('请先创建并选择一个模块')
    return
  }
  const meta = fieldTypes.find((item) => item.type === type)
  editingField.value = undefined
  Object.assign(fieldForm, {
    code: '', name: meta?.name || '', fieldType: type, required: false,
    sortOrder: (draft.value?.fields.length || 0) * 10 + 10,
    status: 'ACTIVE', optionsText: ['SINGLE_SELECT', 'STATUS'].includes(type) ? 'active|启用\ninactive|停用' : '', version: 0,
  })
  fieldDrawer.value = true
}

function editField(field: ConfiguredField) {
  editingField.value = field
  let optionsText = ''
  try {
    const config = JSON.parse(field.configJson) as { options?: Array<{ value?: string; code?: string; label?: string }> }
    optionsText = config.options?.map((option) => `${option.value || option.code}|${option.label || option.value || option.code}`).join('\n') || ''
  } catch { /* invalid drafts are reported by publication check */ }
  Object.assign(fieldForm, {
    code: field.code, name: field.name, fieldType: field.fieldType, required: field.required,
    sortOrder: field.sortOrder, status: field.status, optionsText, version: field.version,
  })
  fieldDrawer.value = true
}

function fieldConfig() {
  if (!['SINGLE_SELECT', 'STATUS'].includes(fieldForm.fieldType)) return {}
  return {
    options: fieldForm.optionsText.split('\n').map((line) => line.trim()).filter(Boolean).map((line) => {
      const [value, label] = line.split('|').map((item) => item?.trim())
      return { value, label: label || value, status: 'ACTIVE' }
    }),
  }
}

async function saveField() {
  if (!selectedModuleId.value) return
  saving.value = true
  try {
    if (editingField.value) {
      await api(`/api/admin/module-config/modules/${selectedModuleId.value}/fields/${editingField.value.id}`, {
        method: 'PUT', body: JSON.stringify({
          name: fieldForm.name, required: fieldForm.required, sortOrder: fieldForm.sortOrder,
          status: fieldForm.status, config: fieldConfig(), version: fieldForm.version,
        }),
      }, token.value)
    } else {
      await api(`/api/admin/module-config/modules/${selectedModuleId.value}/fields`, {
        method: 'POST', body: JSON.stringify({
          code: fieldForm.code, name: fieldForm.name, fieldType: fieldForm.fieldType,
          required: fieldForm.required, sortOrder: fieldForm.sortOrder, config: fieldConfig(),
        }),
      }, token.value)
    }
    fieldDrawer.value = false
    message.success(editingField.value ? '字段配置已更新' : '字段已加入草稿')
    await loadOverview(selectedModuleId.value)
  } catch (reason) {
    message.error(readable(reason))
  } finally {
    saving.value = false
  }
}

async function togglePageField(code: string, checked: boolean) {
  if (!selectedModuleId.value || !selectedPage.value) return
  const next = checked
    ? [...new Set([...selectedPageFieldCodes.value, code])]
    : selectedPageFieldCodes.value.filter((item) => item !== code)
  saving.value = true
  try {
    await api(`/api/admin/module-config/modules/${selectedModuleId.value}/pages/${selectedPage.value.pageType}`, {
      method: 'PUT', body: JSON.stringify({ layout: { fieldCodes: next }, version: selectedPage.value.version }),
    }, token.value)
    message.success(`${selectedPage.value.name}已更新`)
    await loadOverview(selectedModuleId.value)
  } catch (reason) {
    message.error(readable(reason))
  } finally { saving.value = false }
}

function editAction(action: ConfiguredAction) {
  editingAction.value = action
  let config: { confirmation?: boolean; confirmationText?: string } = {}
  try { config = JSON.parse(action.configJson) } catch { /* publication check reports invalid JSON */ }
  Object.assign(actionForm, {
    location: action.location,
    confirmation: Boolean(config.confirmation),
    confirmationText: config.confirmationText || '',
    version: action.version,
  })
  actionDrawer.value = true
}

async function saveAction() {
  if (!selectedModuleId.value || !editingAction.value) return
  saving.value = true
  try {
    await api(`/api/admin/module-config/modules/${selectedModuleId.value}/actions/${editingAction.value.code}`, {
      method: 'PUT', body: JSON.stringify({
        location: actionForm.location,
        config: { confirmation: actionForm.confirmation, confirmationText: actionForm.confirmationText },
        version: actionForm.version,
      }),
    }, token.value)
    actionDrawer.value = false
    message.success('动作配置已更新')
    await loadOverview(selectedModuleId.value)
  } catch (reason) {
    message.error(readable(reason))
  } finally { saving.value = false }
}

async function checkPublication() {
  if (!selectedModuleId.value) return
  saving.value = true
  try {
    publication.value = await api<PublicationCheck>(
      `/api/admin/module-config/modules/${selectedModuleId.value}/publication-check`, {}, token.value)
    publishModal.value = true
  } catch (reason) {
    message.error(readable(reason))
  } finally {
    saving.value = false
  }
}

async function publish() {
  if (!selectedModuleId.value || !publication.value?.valid) return
  saving.value = true
  try {
    await api(`/api/admin/module-config/modules/${selectedModuleId.value}/publish`, {
      method: 'POST', body: JSON.stringify({ expectedDraftRevision: publication.value.draftRevision }),
    }, token.value)
    publishModal.value = false
    message.success('发布成功，业务运行页已经使用这个版本')
    await loadOverview(selectedModuleId.value)
    emit('published')
  } catch (reason) {
    message.error(readable(reason))
  } finally {
    saving.value = false
  }
}

function onDrop() {
  if (draggingType.value) beginField(draggingType.value)
  draggingType.value = ''
}

function readable(reason: unknown) {
  if (reason instanceof ApiError) return reason.traceId ? `${reason.message}（追踪号：${reason.traceId}）` : reason.message
  return '请求失败，请稍后重试'
}

onMounted(() => loadOverview())
</script>

<template>
  <div class="config-page">
    <div class="page-heading">
      <div><p class="eyebrow">后台配置</p><h1>模块配置</h1><p>先编辑草稿，再检查并发布；未发布内容不会进入业务运行页。</p></div>
      <div class="heading-actions">
        <a-button :loading="loading" @click="loadOverview()"><ReloadOutlined />刷新</a-button>
        <a-button type="primary" :disabled="!selectedModuleId" :loading="saving" @click="checkPublication"><CloudUploadOutlined />检查并发布</a-button>
      </div>
    </div>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />

    <div class="config-layout">
      <aside class="config-tree panel-card">
        <div class="panel-title"><strong>模块结构</strong><a-button type="text" size="small" @click="groupModal = true"><PlusOutlined />分组</a-button></div>
        <a-skeleton v-if="loading && !overview.groups.length" active :paragraph="{ rows: 5 }" />
        <template v-else>
          <section v-for="group in groupedModules" :key="group.id" class="module-group">
            <div class="module-group__name"><span>{{ group.name }}</span><small>{{ group.modules.length }}</small></div>
            <button v-for="module in group.modules" :key="module.id" type="button"
              :class="['module-tree-item', { active: selectedModuleId === module.id }]" @click="selectModule(module.id)">
              <AppstoreAddOutlined /><span>{{ module.name }}</span><a-badge :status="module.status === 'ACTIVE' ? 'success' : 'default'" />
            </button>
          </section>
          <a-empty v-if="!overview.groups.length" image="simple" description="还没有模块组" />
          <a-button block class="tree-create" :disabled="!overview.groups.length" @click="moduleModal = true"><PlusOutlined />新建模块</a-button>
        </template>
      </aside>

      <main class="designer panel-card">
        <template v-if="draft">
          <div class="designer-heading">
            <div><span class="draft-state">{{ draft.published ? '已有发布版本' : '未发布' }}</span><h2>{{ draft.module.name }}</h2><p>{{ draft.module.code }} · 草稿修订 {{ draft.module.draftRevision }}</p></div>
            <a-tag :color="draft.published ? 'green' : 'default'">{{ draft.published ? '运行中' : '仅草稿' }}</a-tag>
          </div>
          <div class="designer-tabs">
            <button :class="{ active: activeDesignerTab === 'FIELDS' }" @click="activeDesignerTab = 'FIELDS'">字段设计</button>
            <button v-for="page in draft.pages" :key="page.id" :class="{ active: activeDesignerTab === page.pageType }" @click="activeDesignerTab = page.pageType">{{ page.name }}</button>
            <button :class="{ active: activeDesignerTab === 'ACTIONS' }" @click="activeDesignerTab = 'ACTIONS'">动作配置</button>
          </div>
          <div v-if="activeDesignerTab === 'FIELDS'" class="field-canvas" @dragover.prevent @drop="onDrop">
            <div class="canvas-hint"><DragOutlined /> 从右侧拖入字段，也可以直接点击字段类型</div>
            <button v-for="field in draft.fields" :key="field.id" type="button" class="configured-field" @click="editField(field)">
              <DragOutlined class="field-handle" />
              <span><strong>{{ field.name }}</strong><small>{{ field.code }}</small></span>
              <a-tag>{{ fieldTypes.find((item) => item.type === field.fieldType)?.name || field.fieldType }}</a-tag>
              <em v-if="field.required">必填</em><em v-if="field.status !== 'ACTIVE'" class="muted">停用</em>
            </button>
            <button v-if="!draft.fields.length" type="button" class="empty-drop" @click="beginField('TEXT')">
              <PlusOutlined /><strong>添加第一个字段</strong><span>模块至少需要一个有效字段才能发布</span>
            </button>
          </div>
          <div v-else-if="selectedPage" class="page-config-canvas">
            <div class="canvas-hint">勾选在{{ selectedPage.name }}中显示的字段；保存后需重新发布才影响业务运行页。</div>
            <label v-for="field in draft.fields.filter((item) => item.status === 'ACTIVE')" :key="field.id" class="page-field-row">
              <a-checkbox :checked="selectedPageFieldCodes.includes(field.code)" :disabled="saving" @change="togglePageField(field.code, $event.target.checked)" />
              <span><strong>{{ field.name }}</strong><small>{{ field.code }}</small></span>
              <a-tag>{{ fieldTypes.find((item) => item.type === field.fieldType)?.name }}</a-tag>
            </label>
          </div>
          <div v-else class="page-config-canvas">
            <button v-for="action in draft.actions" :key="action.id" type="button" class="action-config-row" @click="editAction(action)">
              <span><strong>{{ action.name }}</strong><small>{{ action.code }}</small></span>
              <a-tag>{{ action.location }}</a-tag><span>配置</span>
            </button>
          </div>
          <section class="default-config">
            <div><strong>页面</strong><span v-for="page in draft.pages" :key="page.id">{{ page.name }}</span></div>
            <div><strong>动作</strong><span v-for="action in draft.actions" :key="action.id">{{ action.name }}</span></div>
          </section>
        </template>
        <a-empty v-else image="simple" description="创建并选择模块后，在这里设计字段" />
      </main>

      <aside class="field-palette panel-card">
        <div class="panel-title"><strong>字段类型</strong><span>拖拽添加</span></div>
        <button v-for="field in fieldTypes" :key="field.type" type="button" class="palette-field" draggable="true"
          @dragstart="draggingType = field.type" @dragend="draggingType = ''" @click="beginField(field.type)">
          <span>{{ field.name.slice(0, 1) }}</span><div><strong>{{ field.name }}</strong><small>{{ field.hint }}</small></div><DragOutlined />
        </button>
      </aside>
    </div>

    <a-modal v-model:open="groupModal" title="新建模块组" :confirm-loading="saving" @ok="createGroup">
      <a-form layout="vertical"><a-form-item label="名称" required><a-input v-model:value="groupForm.name" placeholder="例如：销售管理" /></a-form-item>
        <a-form-item label="编码" required><a-input v-model:value="groupForm.code" placeholder="例如：sales" /></a-form-item>
        <a-form-item label="排序"><a-input-number v-model:value="groupForm.sortOrder" :min="0" /></a-form-item></a-form>
    </a-modal>
    <a-modal v-model:open="moduleModal" title="新建业务模块" :confirm-loading="saving" @ok="createModule">
      <a-form layout="vertical"><a-form-item label="所属模块组" required><a-select v-model:value="moduleForm.groupId" :options="overview.groups.map((item) => ({ value: item.id, label: item.name }))" /></a-form-item>
        <a-form-item label="模块名称" required><a-input v-model:value="moduleForm.name" placeholder="例如：客户" /></a-form-item>
        <a-form-item label="模块编码" required extra="发布后业务接口使用该编码"><a-input v-model:value="moduleForm.code" placeholder="例如：customer" /></a-form-item></a-form>
    </a-modal>
    <a-drawer v-model:open="fieldDrawer" :title="editingField ? '编辑字段' : '添加字段'" width="430">
      <a-form layout="vertical">
        <a-form-item label="字段类型"><a-input :value="fieldTypes.find((item) => item.type === fieldForm.fieldType)?.name" disabled /></a-form-item>
        <a-form-item label="字段名称" required><a-input v-model:value="fieldForm.name" /></a-form-item>
        <a-form-item label="字段编码" required extra="创建后不可修改"><a-input v-model:value="fieldForm.code" :disabled="Boolean(editingField)" /></a-form-item>
        <div class="form-grid"><a-form-item label="排序"><a-input-number v-model:value="fieldForm.sortOrder" :min="0" /></a-form-item>
          <a-form-item label="是否必填"><a-switch v-model:checked="fieldForm.required" /></a-form-item></div>
        <a-form-item v-if="editingField" label="状态"><a-radio-group v-model:value="fieldForm.status"><a-radio value="ACTIVE">启用</a-radio><a-radio value="DISABLED">停用</a-radio></a-radio-group></a-form-item>
        <a-form-item v-if="['SINGLE_SELECT', 'STATUS'].includes(fieldForm.fieldType)" label="选项" extra="每行一个，格式：值|显示名称">
          <a-textarea v-model:value="fieldForm.optionsText" :rows="7" />
        </a-form-item>
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="fieldDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveField">保存字段</a-button></div></template>
    </a-drawer>
    <a-drawer v-model:open="actionDrawer" :title="`配置动作：${editingAction?.name || ''}`" width="430">
      <a-form layout="vertical">
        <a-form-item label="出现位置"><a-select v-model:value="actionForm.location" :options="[
          { value: 'MODULE_ENTRY', label: '模块入口' }, { value: 'LIST_TOOLBAR', label: '列表工具栏' },
          { value: 'BATCH', label: '批量操作区' }, { value: 'ROW', label: '列表行' }, { value: 'DETAIL_HEADER', label: '详情头部' },
        ]" /></a-form-item>
        <a-form-item label="执行前二次确认"><a-switch v-model:checked="actionForm.confirmation" /></a-form-item>
        <a-form-item v-if="actionForm.confirmation" label="确认提示"><a-input v-model:value="actionForm.confirmationText" placeholder="确认执行此操作？" /></a-form-item>
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="actionDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveAction">保存动作</a-button></div></template>
    </a-drawer>
    <a-modal v-model:open="publishModal" title="发布检查" :ok-text="publication?.valid ? '确认发布' : '返回修改'"
      :confirm-loading="saving" @ok="publication?.valid ? publish() : (publishModal = false)">
      <a-result v-if="publication?.valid" status="success" title="配置检查通过" sub-title="发布会生成一个不可修改的版本，业务运行页随即使用它。"><template #icon><CheckCircleOutlined /></template></a-result>
      <template v-else><a-alert type="warning" show-icon message="当前配置还不能发布" description="请修正以下问题后重新检查。" />
        <ul class="publication-issues"><li v-for="issue in publication?.issues" :key="`${issue.path}-${issue.code}`"><strong>{{ issue.path }}</strong><span>{{ issue.message }}</span></li></ul></template>
    </a-modal>
  </div>
</template>
