<template>
  <AdminLayout>
    <h2>开放应用</h2>
    <div class="toolbar">
      <button type="button" @click="load">刷新</button>
      <button type="button" @click="create">创建应用</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>
    <table v-if="rows.length" class="table">
      <thead><tr><th>ID</th><th>名称</th><th>AppKey</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="a in rows" :key="a.id">
          <td>{{ a.id }}</td>
          <td>{{ a.appName }}</td>
          <td class="mono">{{ a.appKey }}</td>
          <td>{{ a.status }}</td>
          <td>
            <router-link :to="`/platform/open-apps/${a.id}`">详情</router-link>
            <button type="button" class="secondary" @click="rotate(a)">轮换密钥</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无开放应用</p>
    <pre v-if="secretText" class="pre">{{ secretText }}</pre>
  </AdminLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import { createOpenApp, listOpenApps, rotateOpenAppSecret } from '../api/platformApp'

const rows = ref([])
const error = ref('')
const secretText = ref('')

async function load() {
  error.value = ''
  try {
    const r = await listOpenApps()
    rows.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function create() {
  const appName = prompt('appName')
  const appKey = prompt('appKey (可选)', '')
  if (!appName) return
  error.value = ''
  secretText.value = ''
  try {
    const r = await createOpenApp({ appName, appKey: appKey || undefined })
    secretText.value = JSON.stringify(r.data || null, null, 2)
    load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function rotate(a) {
  if (!confirm(`轮换应用 #${a.id} 的密钥?`)) return
  error.value = ''
  secretText.value = ''
  try {
    const r = await rotateOpenAppSecret(a.id)
    secretText.value = JSON.stringify(r.data || null, null, 2)
    alert('密钥已轮换，请妥善保存')
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style src="./admin-shared.css"></style>
<style scoped>
.mono { font-family: ui-monospace, monospace; font-size: 0.8rem; }
.pre { margin-top: 1rem; background: #fff; border: 1px solid #e5e7eb; padding: 0.75rem; border-radius: 6px; font-size: 0.85rem; }
</style>
