<template>
  <AdminLayout>
    <h2>页面设计 · app {{ appId }}</h2>
    <div class="toolbar">
      <button type="button" @click="add">新建页面</button>
      <button type="button" class="secondary" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>类型</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="p in rows" :key="p.id">
          <td>{{ p.id }}</td>
          <td>{{ p.pageCode }}</td>
          <td>{{ p.pageName }}</td>
          <td>{{ p.pageType }}</td>
          <td>
            <router-link :to="`/apps/${appId}/pages/${p.id}/edit`">编辑</router-link>
            ·
            <button type="button" class="secondary" @click="remove(p)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无页面</p>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { listModelsByApp } from '../api/meta.js'
import { deletePages, listPagesByApp, upsertPage } from '../api/pages'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const rows = ref([])
const models = ref([])
const error = ref('')

async function load() {
  error.value = ''
  try {
    const r = await listPagesByApp(appId.value)
    rows.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function loadModels() {
  try {
    const r = await listModelsByApp(appId.value)
    models.value = r.data || []
  } catch {
    models.value = []
  }
}

function defaultRouteForType(type) {
  const normalized = String(type || '').toLowerCase()
  if (normalized === 'form') return '/records/form'
  if (normalized === 'detail') return '/records/detail'
  return '/records'
}

function modelOptionMessage() {
  if (!models.value.length) return '请输入模型 ID。页面运行态必须绑定模型，否则菜单无法打开。'
  return models.value
    .slice(0, 20)
    .map((m) => `${m.id} - ${m.modelName || m.modelCode || '未命名模型'}`)
    .join('\n')
}

async function add() {
  const pageCode = await promptText('页面编码')
  const pageName = await promptText('页面名称')
  const pageType = await promptText('页面类型', { defaultValue: 'list', message: 'list / form / detail' })
  if (!pageCode || !pageName || !pageType) return
  if (!models.value.length) await loadModels()
  const defaultModelId = models.value[0]?.id ? String(models.value[0].id) : ''
  const modelId = await promptText('绑定模型 ID', {
    defaultValue: defaultModelId,
    message: modelOptionMessage()
  })
  if (!modelId) return
  error.value = ''
  try {
    await upsertPage({
      appId: appId.value,
      pageCode,
      pageName,
      pageType,
      routePath: defaultRouteForType(pageType),
      configJson: JSON.stringify({ modelId: String(modelId).trim() }, null, 2)
    })
    notify.success('页面已创建')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove(p) {
  if (!(await confirmDialog(`删除页面 #${p.id}?`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deletePages([p.id])
    notify.success('页面已删除')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(async () => {
  await Promise.all([load(), loadModels()])
})
</script>
