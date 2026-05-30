<template>
  <AdminLayout>
    <h2>应用</h2>
    <p class="muted">选择应用进入管理控制台。</p>
    <div class="toolbar">
      <button type="button" @click="addApp">新建应用</button>
      <button type="button" class="secondary" @click="load">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <ul v-if="apps.length" class="list">
      <li v-for="a in apps" :key="a.id">
        <div class="list__item">
          <router-link :to="`/apps/${a.id}`" class="list__link">
            <strong>{{ a.appName || a.appCode }}</strong>
            <span class="muted">{{ a.appCode }} · {{ a.status === 2 ? '停用' : '启用' }}</span>
          </router-link>
          <div class="actions">
            <button type="button" class="secondary" @click="editApp(a)">编辑</button>
            <button type="button" class="danger" @click="removeApp(a)">删除</button>
          </div>
        </div>
      </li>
    </ul>
    <p v-else-if="!loading" class="muted">暂无应用</p>
  </AdminLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { deleteApps, listApps, upsertApp } from '../api/meta'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const apps = ref([])
const loading = ref(false)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    const r = await listApps()
    apps.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    loading.value = false
  }
}

async function saveApp(existing = null) {
  const appCode = await promptText('应用编码', { defaultValue: existing?.appCode || '' })
  const appName = await promptText('应用名称', { defaultValue: existing?.appName || '' })
  const status = await promptText('状态', { defaultValue: String(existing?.status ?? 1), message: '1=启用，2=停用' })
  if (!appCode || !appName) return
  error.value = ''
  try {
    await upsertApp({
      id: existing?.id ?? null,
      appCode,
      appName,
      iconUrl: existing?.iconUrl ?? null,
      publishedFlag: existing?.publishedFlag ?? 0,
      remark: existing?.remark ?? null,
      status: String(status).trim() === '2' ? 2 : 1
    })
    notify.success('应用已保存')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function addApp() {
  await saveApp()
}

async function editApp(app) {
  await saveApp(app)
}

async function removeApp(app) {
  if (!app?.id || !(await confirmDialog(`删除应用 ${app.appCode || app.id}？`, { danger: true, confirmText: '删除' }))) return
  error.value = ''
  try {
    await deleteApps([app.id])
    notify.success('应用已删除')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style scoped>
.list {
  list-style: none;
  padding: 0;
  display: grid;
  gap: 0.65rem;
}
.list__item {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding: 0.9rem 1rem;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #fff;
  box-shadow: var(--shadow-sm);
  transition: border-color 0.16s ease, transform 0.16s ease, box-shadow 0.16s ease;
}
.list__item:hover {
  border-color: var(--color-primary);
  transform: translateY(-1px);
  box-shadow: 0 8px 20px rgba(31, 41, 51, 0.08);
}
.list__link {
  display: block;
  text-decoration: none;
  color: inherit;
  flex: 1;
  min-width: 0;
}
.list__link .muted {
  display: block;
  margin-top: 0.25rem;
}
.actions {
  display: flex;
  gap: 0.4rem;
  flex-wrap: wrap;
}
</style>
