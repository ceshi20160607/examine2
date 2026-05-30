<template>
  <AdminLayout>
    <h2>开放应用</h2>
    <div class="toolbar">
      <button type="button" @click="load">刷新</button>
      <button type="button" @click="create">创建应用</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <table v-if="rows.length" class="table">
      <thead>
        <tr>
          <th>ID</th>
          <th>名称</th>
          <th>ClientCode</th>
          <th>联系人</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="a in rows" :key="a.id">
          <td>{{ a.id }}</td>
          <td>{{ a.clientName }}</td>
          <td class="mono">{{ a.clientCode }}</td>
          <td>{{ a.contactName || '-' }}</td>
          <td>
            <span :class="['status', a.status === 1 ? 'ok' : 'off']">{{ statusLabel(a.status) }}</span>
          </td>
          <td class="actions">
            <router-link :to="`/platform/open-apps/${a.id}`">详情</router-link>
            <button type="button" class="secondary" @click="edit(a)">编辑</button>
            <button type="button" class="secondary" @click="toggleStatus(a)">{{ a.status === 1 ? '停用' : '启用' }}</button>
            <button type="button" class="secondary" @click="rotate(a)">轮换密钥</button>
            <button type="button" class="danger" @click="remove(a)">删除</button>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无开放应用</p>

    <section v-if="secretText" class="card warn-box">
      <h3>新凭据仅显示一次</h3>
      <pre class="pre">{{ secretText }}</pre>
    </section>
  </AdminLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import {
  createOpenApp,
  deleteOpenApp,
  listOpenApps,
  rotateOpenAppSecret,
  setOpenAppStatus,
  updateOpenApp
} from '../api/platformApp'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const rows = ref([])
const error = ref('')
const secretText = ref('')

function statusLabel(status) {
  return Number(status) === 1 ? '启用' : '停用'
}

function genClientCode(name) {
  const prefix = String(name || 'app')
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '_')
    .replace(/^_+|_+$/g, '')
    .slice(0, 24) || 'app'
  return `${prefix}_${Math.random().toString(36).slice(2, 10)}`
}

async function load() {
  error.value = ''
  try {
    const r = await listOpenApps()
    rows.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function readProfile(current = {}) {
  const clientName = await promptText('开放应用名称', { defaultValue: current.clientName || '' })
  if (!clientName) return null
  const contactName = await promptText('联系人', { defaultValue: current.contactName || '' })
  const contactMobile = await promptText('联系电话', { defaultValue: current.contactMobile || '' })
  const contactEmail = await promptText('联系邮箱', { defaultValue: current.contactEmail || '' })
  const remark = await promptText('备注', { defaultValue: current.remark || '', multiline: true })
  return { clientName, contactName, contactMobile, contactEmail, remark }
}

async function create() {
  const profile = await readProfile()
  if (!profile) return
  const inputCode = await promptText('开放应用编码', {
    defaultValue: '',
    message: '可选；留空会自动生成。创建后编码不可修改。'
  })
  error.value = ''
  secretText.value = ''
  try {
    const clientCode = inputCode || genClientCode(profile.clientName)
    const r = await createOpenApp({ ...profile, clientCode })
    secretText.value = JSON.stringify(r.data || null, null, 2)
    notify.success('开放应用已创建，请保存新凭据')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function edit(app) {
  const profile = await readProfile(app)
  if (!profile) return
  error.value = ''
  try {
    await updateOpenApp(app.id, profile)
    notify.success('开放应用已更新')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function toggleStatus(app) {
  const next = Number(app.status) === 1 ? 2 : 1
  if (!(await confirmDialog(`确认${next === 1 ? '启用' : '停用'}应用 #${app.id}？`, {
    danger: next !== 1,
    confirmText: next === 1 ? '启用' : '停用'
  }))) return
  error.value = ''
  try {
    await setOpenAppStatus(app.id, next)
    notify.success(`应用已${next === 1 ? '启用' : '停用'}`)
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function rotate(app) {
  if (!(await confirmDialog(`轮换应用 #${app.id} 的密钥？旧密钥会立即失效。`, {
    danger: true,
    confirmText: '轮换'
  }))) return
  error.value = ''
  secretText.value = ''
  try {
    const r = await rotateOpenAppSecret(app.id)
    secretText.value = JSON.stringify(r.data || null, null, 2)
    notify.success('密钥已轮换，请妥善保存')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove(app) {
  if (!(await confirmDialog(`删除开放应用 #${app.id}？相关凭据也会停用。`, {
    danger: true,
    confirmText: '删除'
  }))) return
  error.value = ''
  try {
    await deleteOpenApp(app.id)
    notify.success('开放应用已删除')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style scoped>
.mono { font-family: ui-monospace, monospace; font-size: 0.8rem; }
.actions { display: flex; flex-wrap: wrap; gap: 0.35rem; align-items: center; }
.status { display: inline-flex; align-items: center; min-width: 3rem; font-size: 0.82rem; font-weight: 700; }
.status.ok { color: #047857; }
.status.off { color: #b45309; }
.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 1rem;
  margin-top: 1rem;
}
.warn-box {
  border-color: #fcd34d;
  background: #fffbeb;
}
.pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 0.85rem;
}
</style>
