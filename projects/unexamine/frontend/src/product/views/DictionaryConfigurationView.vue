<script setup lang="ts">
import { ApartmentOutlined, CloudUploadOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { Empty, message } from 'ant-design-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api'
import { systemTokens } from '../session'
import type {
  ConfiguredDictionaryItem,
  DictionaryDraft,
  DictionaryPublicationCheck,
  DictionaryVersion,
  RuntimeDictionary,
} from '../types'

const token = computed(() => systemTokens.value?.accessToken || '')
const dictionaries = ref<DictionaryDraft[]>([])
const selectedId = ref<number>()
const selected = computed(() => dictionaries.value.find(item => item.dictionary.id === selectedId.value))
const versions = ref<DictionaryVersion[]>([])
const preview = ref<RuntimeDictionary>()
const previewParentId = ref<number>()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const dictionaryModal = ref(false)
const itemDrawer = ref(false)
const publicationModal = ref(false)
const publication = ref<DictionaryPublicationCheck>()
const editingItem = ref<ConfiguredDictionaryItem>()
const dictionaryForm = reactive({ code: '', name: '', hierarchical: true })
const itemForm = reactive({ parentId: undefined as number | undefined, code: '', label: '', color: '', sortOrder: 10, status: 'ACTIVE', version: 0 })

const itemOptions = computed(() => selected.value?.items
  .filter(item => item.id !== editingItem.value?.id && !item.pathCode.startsWith(`${editingItem.value?.pathCode || '#'},`))
  .map(item => ({ value: item.id, label: `${'　'.repeat(Math.max(item.pathCode.split(',').length - 1, 0))}${item.label}（${item.code}）` })) || [])

async function load(preferred?: number) {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try {
    dictionaries.value = await api<DictionaryDraft[]>('/api/admin/dictionaries', {}, token.value)
    const target = preferred ?? selectedId.value ?? dictionaries.value[0]?.dictionary.id
    if (target) await select(target)
  } catch (cause) { error.value = explain(cause) } finally { loading.value = false }
}

async function select(id: number) {
  selectedId.value = id
  preview.value = undefined
  previewParentId.value = undefined
  try {
    const [draft, history] = await Promise.all([
      api<DictionaryDraft>(`/api/admin/dictionaries/${id}`, {}, token.value),
      api<DictionaryVersion[]>(`/api/admin/dictionaries/${id}/versions`, {}, token.value),
    ])
    dictionaries.value = dictionaries.value.map(item => item.dictionary.id === id ? draft : item)
    versions.value = history
    if (draft.currentVersionId) await loadPreview()
  } catch (cause) { error.value = explain(cause) }
}

async function createDictionary() {
  saving.value = true
  try {
    const created = await api<DictionaryDraft>('/api/admin/dictionaries', {
      method: 'POST', body: JSON.stringify(dictionaryForm),
    }, token.value)
    dictionaryModal.value = false
    Object.assign(dictionaryForm, { code: '', name: '', hierarchical: true })
    message.success('字典草稿已创建；未发布前运行端不可见')
    await load(created.dictionary.id)
  } catch (cause) { message.error(explain(cause)) } finally { saving.value = false }
}

function beginItem(parentId?: number) {
  editingItem.value = undefined
  Object.assign(itemForm, { parentId, code: '', label: '', color: '', sortOrder: (selected.value?.items.length || 0) * 10 + 10, status: 'ACTIVE', version: 0 })
  itemDrawer.value = true
}

function editItem(item: ConfiguredDictionaryItem) {
  editingItem.value = item
  Object.assign(itemForm, { parentId: item.parentId, code: item.code, label: item.label, color: item.color || '', sortOrder: item.sortOrder, status: item.status, version: item.version })
  itemDrawer.value = true
}

async function saveItem() {
  if (!selected.value) return
  saving.value = true
  try {
    const base = `/api/admin/dictionaries/${selected.value.dictionary.id}/items`
    if (editingItem.value) {
      await api(`${base}/${editingItem.value.id}`, { method: 'PUT', body: JSON.stringify({
        parentId: itemForm.parentId, label: itemForm.label, color: itemForm.color || null,
        sortOrder: itemForm.sortOrder, status: itemForm.status, version: itemForm.version,
      }) }, token.value)
    } else {
      await api(base, { method: 'POST', body: JSON.stringify({
        parentId: itemForm.parentId, code: itemForm.code, label: itemForm.label,
        color: itemForm.color || null, sortOrder: itemForm.sortOrder,
      }) }, token.value)
    }
    itemDrawer.value = false
    message.success(editingItem.value ? '字典项草稿已更新' : '字典项已加入草稿')
    await load(selected.value.dictionary.id)
  } catch (cause) { message.error(explain(cause)) } finally { saving.value = false }
}

async function checkPublication() {
  if (!selected.value) return
  saving.value = true
  try {
    publication.value = await api<DictionaryPublicationCheck>(`/api/admin/dictionaries/${selected.value.dictionary.id}/publication-check`, {}, token.value)
    publicationModal.value = true
  } catch (cause) { message.error(explain(cause)) } finally { saving.value = false }
}

async function publish() {
  if (!selected.value || !publication.value?.valid) return
  saving.value = true
  try {
    const result = await api<{ versionNumber: number }>(`/api/admin/dictionaries/${selected.value.dictionary.id}/publish`, {
      method: 'POST', body: JSON.stringify({ expectedDraftRevision: publication.value.draftRevision }),
    }, token.value)
    publicationModal.value = false
    message.success(`字典 v${result.versionNumber} 已发布，运行预览只读取该不可变版本`)
    await load(selected.value.dictionary.id)
  } catch (cause) { message.error(explain(cause)) } finally { saving.value = false }
}

async function loadPreview(parentId?: number) {
  if (!selected.value?.currentVersionId) return
  previewParentId.value = parentId
  const query = parentId ? `?parentId=${parentId}` : ''
  try {
    preview.value = await api<RuntimeDictionary>(`/api/runtime/dictionaries/${selected.value.dictionary.code}${query}`, {}, token.value)
  } catch (cause) { message.error(explain(cause)) }
}

function explain(cause: unknown) {
  return cause instanceof ApiError ? `${cause.message}（${cause.code}${cause.traceId ? `，追踪号 ${cause.traceId}` : ''}）` : '字典操作失败'
}

onMounted(load)
</script>

<template>
  <section class="dictionary-config panel-card">
    <div class="page-heading compact-heading">
      <div><p class="eyebrow">系统后台 · 数据字典</p><h2>版本化字典</h2><p>编辑草稿、检查后发布；字段和运行页只读取当前不可变发布版本。</p></div>
      <div class="heading-actions"><a-button :loading="loading" @click="load()"><ReloadOutlined />刷新</a-button><a-button type="primary" @click="dictionaryModal = true"><PlusOutlined />新建字典</a-button></div>
    </div>
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <div class="dictionary-layout">
      <aside class="dictionary-list">
        <button v-for="entry in dictionaries" :key="entry.dictionary.id" :class="{ active: selectedId === entry.dictionary.id }" @click="select(entry.dictionary.id)">
          <ApartmentOutlined /><span><strong>{{ entry.dictionary.name }}</strong><small>{{ entry.dictionary.code }} · 草稿修订 {{ entry.dictionary.version }}</small></span>
          <a-tag :color="entry.currentVersionId ? 'green' : 'default'">{{ entry.currentVersionId ? `v${entry.currentVersionNumber}` : '未发布' }}</a-tag>
        </button>
        <a-empty v-if="!dictionaries.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="还没有数据字典" />
      </aside>
      <main v-if="selected" class="dictionary-editor">
        <div class="panel-title"><span><strong>{{ selected.dictionary.name }}</strong><small>{{ selected.dictionary.hierarchical ? '树形/级联字典' : '普通列表字典' }}</small></span>
          <span><a-button @click="beginItem()"><PlusOutlined />添加顶级项</a-button><a-button type="primary" :loading="saving" @click="checkPublication"><CloudUploadOutlined />检查并发布</a-button></span></div>
        <div class="dictionary-item-list">
          <button v-for="item in selected.items" :key="item.id" :style="{ paddingLeft: `${18 + (item.pathCode.split(',').length - 1) * 24}px` }" @click="editItem(item)">
            <span class="dictionary-color" :style="{ background: item.color || '#dbe4f0' }" /><span><strong>{{ item.label }}</strong><small>{{ item.pathCode }}</small></span>
            <a-tag :color="item.status === 'ACTIVE' ? 'blue' : 'default'">{{ item.status === 'ACTIVE' ? '可选' : '仅历史可解释' }}</a-tag>
            <a-button v-if="selected.dictionary.hierarchical" type="link" size="small" @click.stop="beginItem(item.id)">添加子项</a-button>
          </button>
          <a-empty v-if="!selected.items.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="添加至少一个启用项后才能发布" />
        </div>
        <section class="dictionary-preview">
          <div class="panel-title"><strong>运行态逐级预览</strong><span v-if="preview">发布 v{{ preview.versionNumber }} · 指针 v{{ preview.publicationVersion }}</span></div>
          <div v-if="preview" class="preview-options">
            <a-button v-if="previewParentId" size="small" @click="loadPreview()">返回顶级</a-button>
            <button v-for="item in preview.items" :key="item.id" @click="selected.dictionary.hierarchical && loadPreview(item.id)">
              <span class="dictionary-color" :style="{ background: item.color || '#dbe4f0' }" />{{ item.label }}<small>{{ item.code }}</small>
            </button>
            <a-empty v-if="!preview.items.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="当前层级没有可选项" />
          </div>
          <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="发布后可从运行接口预览，草稿修改不会改变此处" />
        </section>
        <section class="dictionary-versions">
          <div class="panel-title"><strong>发布历史</strong><span>{{ versions.length }} 个不可变版本</span></div>
          <div v-for="version in versions" :key="version.versionId"><span><strong>v{{ version.versionNumber }}</strong><small>草稿修订 {{ version.draftRevision }} · {{ version.publishedAt }}</small></span><a-tag :color="version.current ? 'green' : 'default'">{{ version.current ? '当前运行版本' : '历史快照' }}</a-tag></div>
        </section>
      </main>
      <a-empty v-else :image="Empty.PRESENTED_IMAGE_SIMPLE" description="选择或新建一个字典" class="dictionary-editor" />
    </div>
  </section>

  <a-modal v-model:open="dictionaryModal" title="新建数据字典" :confirm-loading="saving" @ok="createDictionary">
    <a-form layout="vertical"><a-form-item label="名称" required><a-input v-model:value="dictionaryForm.name" placeholder="例如：行政区划" /></a-form-item>
      <a-form-item label="稳定编码" required><a-input v-model:value="dictionaryForm.code" placeholder="例如：region" /></a-form-item>
      <a-form-item label="类型"><a-radio-group v-model:value="dictionaryForm.hierarchical"><a-radio :value="false">普通列表</a-radio><a-radio :value="true">树形/级联</a-radio></a-radio-group></a-form-item></a-form>
  </a-modal>
  <a-drawer v-model:open="itemDrawer" :title="editingItem ? '编辑字典项草稿' : '添加字典项'" width="430">
    <a-form layout="vertical"><a-form-item v-if="selected?.dictionary.hierarchical" label="父级"><a-select v-model:value="itemForm.parentId" allow-clear :options="itemOptions" /></a-form-item>
      <a-form-item label="显示名称" required><a-input v-model:value="itemForm.label" /></a-form-item>
      <a-form-item label="稳定编码" required extra="创建后不可修改，历史值按编码解释"><a-input v-model:value="itemForm.code" :disabled="Boolean(editingItem)" /></a-form-item>
      <div class="form-grid"><a-form-item label="颜色"><a-input v-model:value="itemForm.color" placeholder="#1677ff" /></a-form-item><a-form-item label="排序"><a-input-number v-model:value="itemForm.sortOrder" :min="0" /></a-form-item></div>
      <a-form-item v-if="editingItem" label="状态"><a-radio-group v-model:value="itemForm.status"><a-radio value="ACTIVE">启用</a-radio><a-radio value="DISABLED">停用</a-radio></a-radio-group></a-form-item></a-form>
    <template #footer><div class="drawer-footer"><a-button @click="itemDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveItem">保存草稿</a-button></div></template>
  </a-drawer>
  <a-modal v-model:open="publicationModal" title="字典发布检查" :confirm-loading="saving" :ok-text="publication?.valid ? '确认发布' : '返回修改'" @ok="publication?.valid ? publish() : (publicationModal = false)">
    <a-result v-if="publication?.valid" status="success" title="检查通过" sub-title="将生成不可修改的发布快照，运行端随即切换到新版本。" />
    <a-result v-else status="error" title="存在发布冲突"><template #subTitle><ul class="publication-issues"><li v-for="issue in publication?.issues" :key="`${issue.path}-${issue.code}`"><strong>{{ issue.code }}</strong>：{{ issue.message }}</li></ul></template></a-result>
  </a-modal>
</template>
