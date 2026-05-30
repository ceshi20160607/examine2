<template>
  <AdminLayout>
    <h2>开放应用 #{{ id }}</h2>
    <div class="toolbar">
      <button type="button" @click="load">刷新</button>
      <button type="button" class="secondary" @click="edit" :disabled="!detail">编辑资料</button>
      <button type="button" class="secondary" @click="toggleStatus" :disabled="!detail">
        {{ detail?.client?.status === 1 ? '停用' : '启用' }}
      </button>
      <button type="button" @click="rotate">轮换密钥</button>
      <button type="button" class="danger" @click="remove">删除</button>
      <router-link class="btn secondary" to="/platform/open-apps">返回</router-link>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <section v-if="detail" class="card">
      <div class="detail-grid">
        <p><strong>编码</strong><span class="mono">{{ detail.client?.clientCode }}</span></p>
        <p><strong>名称</strong><span>{{ detail.client?.clientName }}</span></p>
        <p><strong>状态</strong><span>{{ detail.client?.status === 1 ? '启用' : '停用' }}</span></p>
        <p><strong>Access Key</strong><code>{{ detail.activeAccessKey || '-' }}</code></p>
        <p><strong>联系人</strong><span>{{ detail.client?.contactName || '-' }}</span></p>
        <p><strong>电话</strong><span>{{ detail.client?.contactMobile || '-' }}</span></p>
        <p><strong>邮箱</strong><span>{{ detail.client?.contactEmail || '-' }}</span></p>
        <p><strong>备注</strong><span>{{ detail.client?.remark || '-' }}</span></p>
      </div>
    </section>

    <section v-if="credential" class="card warn-box">
      <h3>新密钥仅显示一次</h3>
      <p><strong>AK</strong> <code>{{ credential.accessKey }}</code></p>
      <p><strong>SK</strong> <code>{{ credential.secret }}</code></p>
      <p class="muted">轮换后旧凭据会失效，请先更新调用方配置。</p>
    </section>

    <section class="card">
      <h3>开放 API 签名调用（v1）</h3>
      <p class="muted">请求头：X-Access-Key、X-Timestamp、X-Signature、X-Acting-Plat-Id；平台级 client 可另加 X-Target-System-Id。</p>
      <pre class="json-pre">{{ signDoc }}</pre>
    </section>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import {
  deleteOpenApp,
  getOpenApp,
  rotateOpenAppSecret,
  setOpenAppStatus,
  updateOpenApp
} from '../api/platformApp.js'
import { confirmDialog, promptText } from '../utils/dialog.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const router = useRouter()
const id = computed(() => String(route.params.id || ''))
const detail = ref(null)
const credential = ref(null)
const error = ref('')

const signDoc = `canonical = METHOD + "\\n" + pathWithQuery + "\\n" + timestamp + "\\n" + hex(SHA256(body))
signature = Base64(HMAC-SHA256(SK, canonical))

示例（bash）见 docs/api/curl-examples.md 中 open_sign()`

async function load() {
  error.value = ''
  try {
    const r = await getOpenApp(id.value)
    detail.value = r.data
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function edit() {
  const client = detail.value?.client
  if (!client) return
  const clientName = await promptText('开放应用名称', { defaultValue: client.clientName || '' })
  if (!clientName) return
  const contactName = await promptText('联系人', { defaultValue: client.contactName || '' })
  const contactMobile = await promptText('联系电话', { defaultValue: client.contactMobile || '' })
  const contactEmail = await promptText('联系邮箱', { defaultValue: client.contactEmail || '' })
  const remark = await promptText('备注', { defaultValue: client.remark || '', multiline: true })
  error.value = ''
  try {
    await updateOpenApp(id.value, { clientName, contactName, contactMobile, contactEmail, remark })
    notify.success('开放应用已更新')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function toggleStatus() {
  const client = detail.value?.client
  if (!client) return
  const next = Number(client.status) === 1 ? 2 : 1
  if (!(await confirmDialog(`确认${next === 1 ? '启用' : '停用'}该开放应用？`, {
    danger: next !== 1,
    confirmText: next === 1 ? '启用' : '停用'
  }))) return
  try {
    await setOpenAppStatus(id.value, next)
    notify.success(`应用已${next === 1 ? '启用' : '停用'}`)
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function rotate() {
  if (!(await confirmDialog('确认轮换密钥？旧密钥将失效。', { danger: true, confirmText: '轮换' }))) return
  try {
    const r = await rotateOpenAppSecret(id.value)
    credential.value = r.data
    notify.success('密钥已轮换，请妥善保存')
    await load()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function remove() {
  if (!(await confirmDialog('删除该开放应用？相关凭据也会停用。', { danger: true, confirmText: '删除' }))) return
  try {
    await deleteOpenApp(id.value)
    notify.success('开放应用已删除')
    router.push('/platform/open-apps')
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(load)
</script>

<style scoped>
.card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 1rem;
  margin-bottom: 1rem;
}
.detail-grid {
  display: grid;
  gap: 0.65rem;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
}
.detail-grid p {
  margin: 0;
  display: grid;
  gap: 0.2rem;
}
.warn-box {
  border-color: #fcd34d;
  background: #fffbeb;
}
.json-pre {
  background: #f9fafb;
  padding: 0.75rem;
  border-radius: 6px;
  font-size: 0.8rem;
  overflow: auto;
}
.mono,
code {
  font-family: ui-monospace, monospace;
  font-size: 0.85rem;
  word-break: break-all;
}
.muted {
  color: #666;
  font-size: 0.88rem;
}
</style>
