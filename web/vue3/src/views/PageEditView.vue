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
import { deletePageBlocks, getPageDetail, upsertPage, upsertPageBlock } from '../api/pages'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const pageId = computed(() => String(route.params.pageId || ''))
const page = ref(null)
const blocks = ref([])
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
      form.configJson = p.configJson ? JSON.stringify(p.configJson, null, 2) : (p.configJsonStr || '')
      form.formFieldsJson = p.formFieldsJson ? JSON.stringify(p.formFieldsJson, null, 2) : (p.formFieldsJsonStr || '')
    }
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function savePage() {
  error.value = ''
  try {
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
    alert('页面已保存')
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addBlock() {
  const blockType = prompt('blockType (form/table/chart/text/custom)', 'form')
  const sortNo = Number(prompt('sortNo', '0') || '0')
  const configJson = prompt('configJson', '{}')
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
  if (!confirm(`删除区块 #${b.id}?`)) return
  error.value = ''
  try {
    await deletePageBlocks([b.id])
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style src="./admin-shared.css"></style>
<style scoped>
input { padding: 0.35rem 0.5rem; border: 1px solid #d1d5db; border-radius: 6px; min-width: 200px; }
.mono { font-family: ui-monospace, monospace; font-size: 0.8rem; }
h3 { margin-top: 1.5rem; }
</style>
