<script setup lang="ts">
import { CloudUploadOutlined, DeleteOutlined, PlusOutlined, ReloadOutlined, RollbackOutlined, SaveOutlined } from '@ant-design/icons-vue'
import { Empty, message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api'
import { systemTokens } from '../session'
import type { TenantExtensionApplicationGrant, TenantExtensionCheck, TenantExtensionField, TenantExtensionModule } from '../types'

const token = computed(() => systemTokens.value?.accessToken || '')
const modules = ref<TenantExtensionModule[]>([])
const selectedId = ref<number>()
const selected = computed(() => modules.value.find(item => item.moduleId === selectedId.value))
const extensionFields = ref<TenantExtensionField[]>([])
const formFieldCodes = ref<string[]>([])
const listFieldCodes = ref<string[]>([])
const selectedGrantIds = ref<number[]>([])
const loading = ref(false)
const saving = ref(false)
const unavailable = ref(false)
const error = ref('')
const fieldDrawer = ref(false)
const checkModal = ref(false)
const publication = ref<TenantExtensionCheck>()
const fieldForm = reactive({ code: '', name: '', fieldType: 'TEXT', required: false, sortOrder: 20, placeholder: '' })

const previewFields = computed(() => [
  ...(selected.value?.mergedPreview.fields || []).filter(field => field.source === 'MAIN'),
  ...extensionFields.value,
])
const fieldOptions = computed(() => previewFields.value.map(field => ({
  value: field.code,
  label: `${field.name}（${field.code} · ${field.source === 'MAIN' ? '主配置' : '本租户'}）`,
})))
const applicationGroups = computed(() => {
  const groups = new Map<number, { id: number; name: string; code: string; grants: TenantExtensionApplicationGrant[] }>()
  for (const grant of selected.value?.availableApplicationGrants || []) {
    const group = groups.get(grant.applicationId) || {
      id: grant.applicationId, name: grant.applicationName, code: grant.applicationCode, grants: [],
    }
    group.grants.push(grant)
    groups.set(grant.applicationId, group)
  }
  return [...groups.values()]
})

async function load(preferred?: number) {
  if (!token.value) return
  loading.value = true
  error.value = ''
  unavailable.value = false
  try {
    modules.value = await api<TenantExtensionModule[]>('/api/admin/tenant-extensions', {}, token.value)
    const target = preferred ?? selectedId.value ?? modules.value[0]?.moduleId
    if (target) await choose(target)
  } catch (cause) {
    if (cause instanceof ApiError && cause.code === 'TENANT_EXTENSION_NOT_AVAILABLE') unavailable.value = true
    else error.value = explain(cause)
  } finally { loading.value = false }
}

async function choose(moduleId: number) {
  selectedId.value = moduleId
  try {
    const detail = await api<TenantExtensionModule>(`/api/admin/tenant-extensions/modules/${moduleId}`, {}, token.value)
    modules.value = modules.value.map(item => item.moduleId === moduleId ? detail : item)
    hydrate(detail)
  } catch (cause) { message.error(explain(cause)) }
}

function hydrate(detail: TenantExtensionModule) {
  extensionFields.value = (detail.draft.fields || []).map(field => ({ ...field }))
  const allCodes = detail.mergedPreview.fields.map(field => field.code)
  const form = detail.draft.pages?.find(page => page.pageType === 'FORM')
  const list = detail.draft.pages?.find(page => page.pageType === 'LIST')
  formFieldCodes.value = form?.fieldCodes?.length ? [...form.fieldCodes] : [...allCodes]
  listFieldCodes.value = list?.fieldCodes?.length ? [...list.fieldCodes] : [...allCodes]
  selectedGrantIds.value = detail.draft.applicationBindings?.flatMap(binding => binding.grantIds) || []
}

function beginField() {
  Object.assign(fieldForm, {
    code: '', name: '', fieldType: 'TEXT', required: false,
    sortOrder: (extensionFields.value.length + previewFields.value.filter(field => field.source === 'MAIN').length + 1) * 10,
    placeholder: '',
  })
  fieldDrawer.value = true
}

function addField() {
  const code = fieldForm.code.trim().toLowerCase()
  if (!code || !fieldForm.name.trim()) return message.warning('请填写字段名称和稳定编码')
  if (previewFields.value.some(field => field.code === code) || extensionFields.value.some(field => field.code === code)) {
    return message.warning('字段编码不能与主配置或本租户扩展重复')
  }
  extensionFields.value.push({
    id: 0, code, name: fieldForm.name.trim(), fieldType: fieldForm.fieldType,
    required: fieldForm.required, sortOrder: fieldForm.sortOrder, status: 'ACTIVE',
    configJson: JSON.stringify({ placeholder: fieldForm.placeholder }), version: 0,
    source: 'TENANT', sourceTenantId: 0, mandatory: false,
  })
  formFieldCodes.value.push(code)
  listFieldCodes.value.push(code)
  fieldDrawer.value = false
}

function removeDraftField(code: string) {
  extensionFields.value = extensionFields.value.filter(field => field.code !== code)
  formFieldCodes.value = formFieldCodes.value.filter(item => item !== code)
  listFieldCodes.value = listFieldCodes.value.filter(item => item !== code)
}

function config(field: TenantExtensionField) {
  try { return JSON.parse(field.configJson || '{}') }
  catch { return {} }
}

function bindings() {
  return applicationGroups.value.map(group => ({
    applicationId: group.id,
    grantIds: group.grants.filter(grant => selectedGrantIds.value.includes(grant.grantId)).map(grant => grant.grantId),
  })).filter(binding => binding.grantIds.length)
}

async function saveDraft() {
  if (!selected.value) return
  saving.value = true
  try {
    const result = await api<TenantExtensionModule>(`/api/admin/tenant-extensions/modules/${selected.value.moduleId}`, {
      method: 'PUT', body: JSON.stringify({
        fields: extensionFields.value.map(field => ({
          fieldId: field.id || null, code: field.code, name: field.name, fieldType: field.fieldType,
          required: field.required, sortOrder: field.sortOrder, config: config(field),
        })),
        pages: [
          { pageType: 'FORM', fieldCodes: formFieldCodes.value },
          { pageType: 'LIST', fieldCodes: listFieldCodes.value },
        ],
        applicationBindings: bindings(), expectedVersion: selected.value.extension?.version || 0,
      }),
    }, token.value)
    modules.value = modules.value.map(item => item.moduleId === result.moduleId ? result : item)
    hydrate(result)
    message.success('租户扩展草稿已保存，运行态仍使用当前发布版本')
  } catch (cause) { message.error(explain(cause)) } finally { saving.value = false }
}

async function checkPublication() {
  if (!selected.value) return
  saving.value = true
  try {
    publication.value = await api<TenantExtensionCheck>(
      `/api/admin/tenant-extensions/modules/${selected.value.moduleId}/publication-check`, {}, token.value)
    checkModal.value = true
  } catch (cause) { message.error(explain(cause)) } finally { saving.value = false }
}

async function publish() {
  if (!selected.value || !publication.value?.valid) return
  saving.value = true
  try {
    const result = await api<TenantExtensionModule>(
      `/api/admin/tenant-extensions/modules/${selected.value.moduleId}/publish`, {
        method: 'POST', body: JSON.stringify({ expectedDraftRevision: publication.value.draftRevision }),
      }, token.value)
    modules.value = modules.value.map(item => item.moduleId === result.moduleId ? result : item)
    hydrate(result)
    checkModal.value = false
    message.success('租户扩展已发布，运行态已切换到合并配置')
  } catch (cause) { message.error(explain(cause)) } finally { saving.value = false }
}

async function removePublished() {
  if (!selected.value?.extension) return
  saving.value = true
  try {
    await api(`/api/admin/tenant-extensions/modules/${selected.value.moduleId}/remove`, {
      method: 'POST', body: JSON.stringify({ expectedVersion: selected.value.extension.version }),
    }, token.value)
    message.success('本租户扩展已移除，运行态已自动回退主配置；导航层级保持不变')
    await choose(selected.value.moduleId)
  } catch (cause) { message.error(explain(cause)) } finally { saving.value = false }
}

async function rollback(versionId: number) {
  if (!selected.value?.extension) return
  saving.value = true
  try {
    await api(`/api/admin/tenant-extensions/modules/${selected.value.moduleId}/rollback`, {
      method: 'POST', body: JSON.stringify({
        targetVersionId: versionId, expectedVersion: selected.value.extension.version,
      }),
    }, token.value)
    message.success('租户扩展发布指针已回滚')
    await choose(selected.value.moduleId)
  } catch (cause) { message.error(explain(cause)) } finally { saving.value = false }
}

function explain(cause: unknown) {
  return cause instanceof ApiError
    ? `${cause.message}（${cause.code}${cause.traceId ? `，追踪号 ${cause.traceId}` : ''}）`
    : '租户扩展操作失败'
}

onMounted(load)
</script>

<template>
  <section class="tenant-extension panel-card">
    <div class="page-heading compact-heading">
      <div><p class="eyebrow">系统后台 · 模板配置</p><h2>租户模板扩展与应用绑定</h2><p>主配置只读继承；本租户仅添加自己的字段、页面视图和已有应用授权绑定。</p></div>
      <a-button :loading="loading" @click="load()"><ReloadOutlined />刷新</a-button>
    </div>
    <a-alert v-if="unavailable" type="info" show-icon message="当前是主租户" description="主租户请在“模块配置”维护基础版本；进入非主租户后才能创建租户扩展。" />
    <a-alert v-else-if="error" type="error" show-icon :message="error" />
    <div v-else class="extension-layout">
      <aside class="extension-modules">
        <button v-for="item in modules" :key="item.moduleId" :class="{ active: selectedId === item.moduleId }" @click="choose(item.moduleId)">
          <span><strong>{{ item.moduleName }}</strong><small>{{ item.moduleCode }} · 主版本 v{{ item.baseVersionNumber }}</small></span>
          <a-tag :color="item.extension?.status === 'PUBLISHED' ? 'green' : item.extension?.status === 'DRAFT' ? 'orange' : 'default'">{{ item.extension?.status || '仅主配置' }}</a-tag>
        </button>
        <a-empty v-if="!modules.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="主租户还没有已发布模块" />
      </aside>
      <main v-if="selected" class="extension-editor">
        <div class="extension-summary">
          <span><strong>{{ selected.moduleName }}</strong><small>基础来源：{{ selected.baseTenantName }} · 不可变版本 v{{ selected.baseVersionNumber }}</small></span>
          <span><a-tag color="blue">MAIN 主配置</a-tag><a-tag color="purple">TENANT 本租户</a-tag></span>
        </div>
        <a-tabs>
          <a-tab-pane key="fields" tab="字段与来源">
            <div class="section-action"><p>主字段的编码、类型、安全语义和强制属性不可编辑。</p><a-button @click="beginField"><PlusOutlined />添加租户字段</a-button></div>
            <div class="source-list">
              <div v-for="field in previewFields" :key="`${field.source}-${field.code}`">
                <a-tag :color="field.source === 'MAIN' ? 'blue' : 'purple'">{{ field.source }}</a-tag>
                <span><strong>{{ field.name }}</strong><small>{{ field.code }} · {{ field.fieldType }}</small></span>
                <a-tag v-if="field.mandatory" color="red">基础强制项 · 只读</a-tag>
                <a-tag v-else-if="field.required" color="orange">本租户必填</a-tag>
                <a-button v-if="field.source === 'TENANT'" type="link" danger @click="removeDraftField(field.code)">移除草稿</a-button>
              </div>
            </div>
          </a-tab-pane>
          <a-tab-pane key="pages" tab="页面与视图">
            <a-alert type="info" show-icon message="主配置的导航、模块组和菜单层级不会被租户页面覆盖。" />
            <a-form layout="vertical" class="page-overrides">
              <a-form-item label="表单页面字段" extra="主配置强制字段必须保留"><a-select v-model:value="formFieldCodes" mode="multiple" :options="fieldOptions" /></a-form-item>
              <a-form-item label="列表视图字段"><a-select v-model:value="listFieldCodes" mode="multiple" :options="fieldOptions" /></a-form-item>
            </a-form>
          </a-tab-pane>
          <a-tab-pane key="applications" tab="应用绑定">
            <a-alert type="warning" show-icon message="这里只能绑定应用已经授予当前租户、当前模块的动作；绑定不会新建授权，也不能扩大字段范围。" />
            <div class="grant-groups">
              <section v-for="application in applicationGroups" :key="application.id">
                <strong>{{ application.name }}</strong><small>{{ application.code }}</small>
                <a-checkbox-group v-model:value="selectedGrantIds">
                  <a-checkbox v-for="grant in application.grants" :key="grant.grantId" :value="grant.grantId">{{ grant.actionCode }} · 字段 {{ grant.fieldCodes.join('、') || '无' }}</a-checkbox>
                </a-checkbox-group>
              </section>
              <a-empty v-if="!applicationGroups.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前租户没有可绑定的有效模块授权" />
            </div>
          </a-tab-pane>
          <a-tab-pane key="versions" tab="发布历史">
            <div class="version-list"><div v-for="version in selected.versions" :key="version.versionId"><span><strong>租户 v{{ version.versionNumber }}</strong><small>基于主配置版本 {{ version.baseModuleVersionId }} · {{ version.publishedAt }}</small></span><a-tag v-if="version.current" color="green">当前运行版本</a-tag><a-button v-else size="small" @click="rollback(version.versionId)"><RollbackOutlined />回滚</a-button></div><a-empty v-if="!selected.versions.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="尚未发布租户扩展" /></div>
          </a-tab-pane>
        </a-tabs>
        <div class="extension-footer">
          <span>草稿修订 {{ selected.extension?.draftRevision || 0 }} · 状态 {{ selected.extension?.status || '未创建' }}</span>
          <div><a-button v-if="selected.extension?.currentVersionId" danger :loading="saving" @click="removePublished"><DeleteOutlined />删除并回退主配置</a-button><a-button :loading="saving" @click="saveDraft"><SaveOutlined />保存草稿</a-button><a-button type="primary" :loading="saving" @click="checkPublication"><CloudUploadOutlined />检查并发布</a-button></div>
        </div>
      </main>
      <a-empty v-else class="extension-editor" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="选择一个主租户已发布模块" />
    </div>
  </section>

  <a-drawer v-model:open="fieldDrawer" title="添加本租户字段" width="430">
    <a-form layout="vertical"><a-form-item label="字段名称" required><a-input v-model:value="fieldForm.name" /></a-form-item><a-form-item label="稳定编码" required extra="不能与主配置或其他租户字段重复，创建后类型和编码不可修改"><a-input v-model:value="fieldForm.code" placeholder="例如：vip_level" /></a-form-item><a-form-item label="字段类型"><a-select v-model:value="fieldForm.fieldType" :options="['TEXT','LONG_TEXT','NUMBER','DATE','DATETIME','BOOLEAN'].map(value => ({ value, label: value }))" /></a-form-item><a-form-item label="占位提示"><a-input v-model:value="fieldForm.placeholder" /></a-form-item><div class="form-grid"><a-form-item label="排序"><a-input-number v-model:value="fieldForm.sortOrder" :min="0" /></a-form-item><a-form-item label="本租户必填"><a-switch v-model:checked="fieldForm.required" /></a-form-item></div></a-form>
    <template #footer><div class="drawer-footer"><a-button @click="fieldDrawer = false">取消</a-button><a-button type="primary" @click="addField">加入草稿</a-button></div></template>
  </a-drawer>
  <a-modal v-model:open="checkModal" title="租户扩展发布检查" :confirm-loading="saving" :ok-text="publication?.valid ? '确认发布' : '返回修改'" @ok="publication?.valid ? publish() : (checkModal = false)">
    <a-result v-if="publication?.valid" status="success" title="检查通过" sub-title="将生成不可变租户版本并原子切换运行指针；主导航不变。" />
    <a-result v-else status="error" title="发布被阻止"><template #subTitle><ul><li v-for="issue in publication?.issues" :key="`${issue.path}-${issue.code}`"><strong>{{ issue.code }}</strong>：{{ issue.message }}</li></ul></template></a-result>
  </a-modal>
</template>

<style scoped>
.extension-layout{display:grid;grid-template-columns:270px 1fr;min-height:590px;border-top:1px solid #edf0f5}.extension-modules{padding:16px;border-right:1px solid #edf0f5}.extension-modules>button{width:100%;display:flex;align-items:center;justify-content:space-between;text-align:left;border:0;background:transparent;padding:12px;border-radius:10px;margin-bottom:8px;cursor:pointer}.extension-modules>button.active{background:#eef5ff;color:#0958d9}.extension-modules span,.extension-summary>span,.source-list span,.version-list span{display:flex;flex-direction:column}.extension-modules small,.extension-summary small,.source-list small,.version-list small,.grant-groups small{color:#8792a5;margin-top:3px}.extension-editor{padding:18px;min-width:0}.extension-summary,.section-action,.extension-footer{display:flex;align-items:center;justify-content:space-between;gap:16px}.section-action p{color:#667085}.source-list>div,.version-list>div{display:flex;align-items:center;gap:12px;padding:12px;border-bottom:1px solid #edf0f5}.source-list span,.version-list span{flex:1}.page-overrides{padding:18px 4px}.grant-groups section{display:grid;grid-template-columns:180px 1fr;gap:4px 18px;padding:16px 0;border-bottom:1px solid #edf0f5}.grant-groups .ant-checkbox-group{grid-column:2;display:flex;flex-direction:column;gap:10px}.extension-footer{position:sticky;bottom:0;background:#fff;border-top:1px solid #e8edf4;padding:14px 0 2px;margin-top:16px}.extension-footer>div{display:flex;gap:8px}@media(max-width:900px){.extension-layout{grid-template-columns:1fr}.extension-modules{border-right:0;border-bottom:1px solid #edf0f5}.grant-groups section{grid-template-columns:1fr}.grant-groups .ant-checkbox-group{grid-column:1}}
</style>
