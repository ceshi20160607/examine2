<template>
  <AdminLayout>
    <h2>开放应用 #{{ id }}</h2>
    <div class="toolbar">
      <button type="button" @click="load">刷新</button>
      <button type="button" @click="rotate">轮换密钥</button>
      <router-link class="btn secondary" to="/platform/open-apps">返回</router-link>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <section v-if="detail" class="card">
      <p><strong>编码</strong> {{ detail.client?.clientCode }}</p>
      <p><strong>名称</strong> {{ detail.client?.clientName }}</p>
      <p><strong>Access Key</strong> <code>{{ detail.activeAccessKey || '-' }}</code></p>
      <p class="muted">状态 {{ detail.client?.status === 1 ? '启用' : '停用' }}</p>
    </section>

    <section v-if="credential" class="card warn-box">
      <h3>新密钥（仅显示一次）</h3>
      <p><strong>AK</strong> <code>{{ credential.accessKey }}</code></p>
      <p><strong>SK</strong> <code>{{ credential.secret }}</code></p>
      <p class="muted">轮换后可用 HMAC 签名调用（无需在请求头传 SK）。详见下方说明。</p>
    </section>

    <section class="card">
      <h3>开放 API 签名调用（v1）</h3>
      <p class="muted">请求头：X-Access-Key、X-Timestamp、X-Signature、X-Acting-Plat-Id；平台级 client 另加 X-Target-System-Id。</p>
      <pre class="json-pre">{{ signDoc }}</pre>
    </section>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import { getOpenApp, rotateOpenAppSecret } from '../api/platformApp.js'

const route = useRoute()
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

async function rotate() {
  if (!confirm('确认轮换密钥？旧密钥将失效')) return
  try {
    const r = await rotateOpenAppSecret(id.value)
    credential.value = r.data
    await load()
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
code {
  font-size: 0.85rem;
  word-break: break-all;
}
.muted {
  color: #666;
  font-size: 0.88rem;
}
.error {
  color: #c00;
}
</style>
<style src="./admin-shared.css"></style>
