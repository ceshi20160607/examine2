<template>
  <AdminLayout>
    <h2>页面编辑 #{{ pageId }}</h2>
    <p v-if="error" class="error">{{ error }}</p>
    <template v-if="page">
      <div class="toolbar">
        <button type="button" @click="savePage">保存页面</button>
        <button type="button" class="secondary" @click="load">刷新</button>
      </div>
      <p><label>pageCode <input v-model="form.pageCode" /></label></p>
      <p><label>pageName <input v-model="form.pageName" /></label></p>
      <p><label>pageType <input v-model="form.pageType" /></label></p>
      <p><label>routePath <input v-model="form.routePath" /></label></p>
      <section class="runtime-config">
        <h3>运行配置</h3>
        <label>
          绑定模型
          <select v-model="runtimeModelId" @change="onRuntimeModelChange">
            <option value="">请选择模型</option>
            <option v-for="m in models" :key="m.id" :value="String(m.id)">
              {{ m.modelName || m.modelCode }} (#{{ m.id }})
            </option>
          </select>
        </label>
        <label>
          列表视图
          <select v-model="runtimeListViewId" :disabled="!runtimeModelId">
            <option value="">自动列</option>
            <option v-for="v in listViews" :key="v.id" :value="String(v.id)">
              {{ v.viewName || v.viewCode }} (#{{ v.id }})
            </option>
          </select>
        </label>
        <label>
          筛选模板
          <select v-model="runtimeFilterTplId" :disabled="!runtimeModelId">
            <option value="">自动匹配</option>
            <option v-for="t in filterTpls" :key="t.id" :value="String(t.id)">
              {{ t.tplName || t.tplCode }} (#{{ t.id }})
            </option>
          </select>
        </label>
        <button type="button" class="secondary" @click="applyRuntimeConfig">写入 configJson</button>
        <router-link
          v-if="runtimeModelId"
          class="btn secondary"
          :to="{ path: form.pageType === 'form' ? '/records/form' : '/records', query: { appId, modelId: runtimeModelId, pageId } }"
        >预览运行页</router-link>
      </section>
      <p>configJson</p>
      <textarea v-model="form.configJson" class="json-area" rows="6" />
      <p>formFieldsJson</p>
      <textarea v-model="form.formFieldsJson" class="json-area" rows="4" />
    </template>

    <h3>区块</h3>
    <div class="toolbar">
      <button type="button" @click="addBlock">添加区块</button>
    </div>
    <table v-if="blocks.length" class="table">
      <thead><tr><th>ID</th><th>类型</th><th>排序</th><th>config</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="b in blocks" :key="b.id">
          <td>{{ b.id }}</td>
          <td>{{ b.blockType }}</td>
          <td>{{ b.sortNo }}</td>
          <td class="mono">{{ truncate(b.configJson) }}</td>
          <td><button type="button" class="secondary" @click="removeBlock(b)">删除</button></td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无区块</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { listModelsByApp } from '../api/meta.js'
import { listFilterTpls, listViewsByModel } from '../api/module.js'
import { deletePageBlocks, getPageDetail, upsertPage, upsertPageBlock } from '../api/pages'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const pageId = computed(() => String(route.params.pageId || ''))
const page = ref(null)
const blocks = ref([])
const models = ref([])
const listViews = ref([])
const filterTpls = ref([])
const runtimeModelId = ref('')
const runtimeListViewId = ref('')
const runtimeFilterTplId = ref('')
const error = ref('')
const form = reactive({
  pageCode: '',
  pageName: '',
  pageType: 'list',
  routePath: '',
  configJson: '',
  formFieldsJson: ''
})

function truncate(s, n = 80) {
  const t = String(s || '')
  return t.length > n ? t.slice(0, n) + '…' : t
}

function prettyJson(raw) {
  if (!raw) return ''
  if (typeof raw !== 'string') return JSON.stringify(raw, null, 2)
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function parseJsonText(text, fieldName) {
  const trimmed = String(text || '').trim()
  if (!trimmed) return null
  try {
    return JSON.parse(trimmed)
  } catch {
    throw new Error(`${fieldName} 不是合法 JSON`)
  }
}

function syncRuntimeControls() {
  let cfg = {}
  try {
    cfg = parseJsonText(form.configJson, 'configJson') || {}
  } catch {
    cfg = {}
  }
  runtimeModelId.value = cfg.modelId ? String(cfg.modelId) : ''
  runtimeListViewId.value = cfg.listViewId ? String(cfg.listViewId) : ''
  runtimeFilterTplId.value = cfg.filterTplId ? String(cfg.filterTplId) : ''
}

async function loadModels() {
  try {
    const r = await listModelsByApp(appId.value)
    models.value = r.data || []
  } catch {
    models.value = []
  }
}

async function loadListViews() {
  if (!runtimeModelId.value) {
    listViews.value = []
    return
  }
  try {
    const r = await listViewsByModel(runtimeModelId.value)
    listViews.value = r.data || []
  } catch {
    listViews.value = []
  }
}

async function loadFilterTplsForRuntime() {
  if (!runtimeModelId.value) {
    filterTpls.value = []
    return
  }
  try {
    const r = await listFilterTpls(runtimeModelId.value)
    filterTpls.value = (r.data || []).filter((tpl) => tpl.status !== 2)
    if (runtimeFilterTplId.value && !filterTpls.value.some((tpl) => String(tpl.id) === runtimeFilterTplId.value)) {
      runtimeFilterTplId.value = ''
    }
  } catch {
    filterTpls.value = []
  }
}

async function onRuntimeModelChange() {
  runtimeListViewId.value = ''
  runtimeFilterTplId.value = ''
  await loadListViews()
  await loadFilterTplsForRuntime()
}

function defaultRouteForType(type) {
  return String(type || '').toLowerCase() === 'form' ? '/records/form' : '/records'
}

function applyRuntimeConfig() {
  error.value = ''
  try {
    const cfg = parseJsonText(form.configJson, 'configJson') || {}
    if (runtimeModelId.value) cfg.modelId = runtimeModelId.value
    else delete cfg.modelId
    if (runtimeListViewId.value) cfg.listViewId = runtimeListViewId.value
    else delete cfg.listViewId
    if (runtimeFilterTplId.value) cfg.filterTplId = runtimeFilterTplId.value
    else delete cfg.filterTplId
    form.configJson = JSON.stringify(cfg, null, 2)
    if (!form.routePath) form.routePath = defaultRouteForType(form.pageType)
    notify.success('运行配置已写入')
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function load() {
  error.value = ''
  try {
    const r = await getPageDetail(pageId.value)
    page.value = r.data?.page || r.data
    blocks.value = r.data?.blocks || []
    const p = page.value
    if (p) {
      form.pageCode = p.pageCode || ''
      form.pageName = p.pageName || ''
      form.pageType = p.pageType || 'list'
      form.routePath = p.routePath || ''
      form.configJson = prettyJson(p.configJson || p.configJsonStr)
      form.formFieldsJson = prettyJson(p.formFieldsJson || p.formFieldsJsonStr)
      syncRuntimeControls()
      await loadListViews()
      await loadFilterTplsForRuntime()
    }
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function savePage() {
  error.value = ''
  try {
    if (form.configJson.trim()) parseJsonText(form.configJson, 'configJson')
    if (form.formFieldsJson.trim()) parseJsonText(form.formFieldsJson, 'formFieldsJson')
    await upsertPage({
      id: pageId.value,
      appId: appId.value,
      pageCode: form.pageCode,
      pageName: form.pageName,
      pageType: form.pageType,
      routePath: form.routePath || null,
      configJson: form.configJson.trim() || null,
      formFieldsJson: form.formFieldsJson.trim() || null
    })
    notify.success('页面已保存')
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addBlock() {
  const blockType = await promptText('区块类型', { defaultValue: 'form', message: 'form / table / chart / text / custom' })
  const sortNo = Number((await promptText('排序值', { defaultValue: '0' })) || '0')
  const configJson = await promptText('区块配置 JSON', { defaultValue: '{}', multiline: true })
  if (!blockType) return
  error.value = ''
  try {
    await upsertPageBlock({
      appId: appId.value,
      pageId: pageId.value,
      blockType,
      sortNo,
      configJson: configJson || null
    })
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function removeBlock(b) {
  if (!(await confirmDialog(`删除区块 #${b.id}?`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deletePageBlocks([b.id])
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(async () => {
  await loadModels()
  await load()
})
</script>

<style scoped>
input,
select {
  padding: 0.35rem 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  min-width: 200px;
}
.mono { font-family: ui-monospace, monospace; font-size: 0.8rem; }
h3 { margin-top: 1.5rem; }
.runtime-config {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  align-items: end;
  padding: 0.75rem;
  margin: 1rem 0;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #fff;
}
.runtime-config h3 {
  width: 100%;
  margin: 0;
}
.runtime-config label {
  display: grid;
  gap: 0.3rem;
  font-size: 0.9rem;
}
</style>
