<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, send, type Account, type System } from '../product/api'

const router = useRouter()
const account = ref<Account | null>(null)
const systems = ref<System[]>([])
const loading = ref(true)
const saving = ref(false)
const error = ref('')
const authMode = ref<'login' | 'register'>('login')
const username = ref('')
const password = ref('')
const displayName = ref('')
const showCreate = ref(false)
const systemCode = ref('')
const systemName = ref('')
const systemDescription = ref('')
async function loadSystems() {
  systems.value = await api<System[]>('/systems')
}

async function start() {
  try {
    account.value = await api<Account>('/auth/me')
  } catch {
    account.value = null
    systems.value = []
  }
  if (account.value) {
    try { await loadSystems() }
    catch (cause) { error.value = cause instanceof Error ? cause.message : '无法读取系统' }
  }
  // 页面读取失败不代表会话失效；保留账号让用户返回自己的系统。
  loading.value = false
}

async function authenticate() {
  error.value = ''
  saving.value = true
  try {
    account.value = authMode.value === 'register'
      ? await send<Account>('/auth/register', { username: username.value.trim(), password: password.value, displayName: displayName.value.trim() })
      : await send<Account>('/auth/login', { username: username.value.trim(), password: password.value })
    password.value = ''
    await router.push('/')
    await loadSystems()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '请稍后重试'
  } finally {
    saving.value = false
  }
}

async function createSystem() {
  error.value = ''
  saving.value = true
  try {
    const created = await send<System>('/systems', {
      code: systemCode.value.trim(), name: systemName.value.trim(), description: systemDescription.value.trim(),
    })
    showCreate.value = false
    systemCode.value = ''
    systemName.value = ''
    systemDescription.value = ''
    await loadSystems()
    await router.push(`/systems/${created.id}`)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '创建失败'
  } finally {
    saving.value = false
  }
}

async function logout() {
  await send<void>('/auth/logout')
  account.value = null
  authMode.value = 'login'
  systems.value = []
  await router.push('/')
}

onMounted(start)
</script>

<template>
  <div class="shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">U</span><div><strong>Unexamine</strong><small>业务系统工作台</small></div></div>
      <div class="side-label">工作空间</div>
      <RouterLink class="side-link" to="/"><span class="side-icon">▦</span> 我的系统</RouterLink>
      <div class="sidebar-bottom">从系统开始，逐步建立模块和业务流程。</div>
    </aside>

    <main class="main-area">
      <header class="topbar"><span>系统工作台 <span class="topbar-slash">/</span> 我的系统</span><div class="top-actions"><span v-if="account" class="account-badge">{{ account.displayName }}</span><button v-if="account" class="text-button" @click="logout">退出登录</button></div></header>

      <div v-if="loading" class="content"><p>正在读取工作空间…</p></div>

      <div v-else-if="!account" class="auth-layout">
        <section class="intro-panel"><div class="eyebrow">LOW CODE PLATFORM</div><h1>把业务想法，<br><em>变成可用的系统。</em></h1><p>从创建系统开始，逐步配置模块、数据与审批。所有内容都在你的工作空间中管理。</p><div class="intro-steps"><span>01 创建系统</span><span>02 配置模块</span><span>03 邀请成员</span></div></section>
        <section class="auth-card"><div class="eyebrow">欢迎使用</div><h2>{{ authMode === 'login' ? '登录工作空间' : '创建平台账号' }}</h2><p class="muted">{{ authMode === 'login' ? '输入账号，继续管理你的业务系统。' : '注册后即可建立自己的第一个系统。' }}</p><form @submit.prevent="authenticate"><label>用户名<input v-model="username" autocomplete="username" minlength="3" maxlength="40" required placeholder="例如 admin01" /></label><label v-if="authMode === 'register'">显示名称<input v-model="displayName" autocomplete="name" maxlength="120" required placeholder="你的姓名或团队名称" /></label><label>密码<input v-model="password" type="password" :autocomplete="authMode === 'login' ? 'current-password' : 'new-password'" minlength="8" required placeholder="至少 8 位" /></label><p v-if="error" class="form-error">{{ error }}</p><button class="primary-button wide" :disabled="saving">{{ saving ? '处理中…' : authMode === 'login' ? '登录' : '注册并进入' }} <span>→</span></button></form><button class="mode-button" @click="authMode = authMode === 'login' ? 'register' : 'login'; error = ''">{{ authMode === 'login' ? '还没有账号？立即注册' : '已有账号？返回登录' }}</button></section>
      </div>

      <div v-else class="content">
        <p v-if="error" class="form-error">{{ error }}</p>
        <div class="page-head"><div class="page-kicker">WORKSPACE / SYSTEMS</div><h1>我的系统</h1><p>管理你创建或获准进入的业务系统。进入系统后配置模块组与业务模块。</p></div>
        <div class="section-head"><div><h2>系统列表</h2><p>共 {{ systems.length }} 个可进入的系统</p></div><button class="primary-button" @click="showCreate = true; error = ''">＋ 创建系统</button></div><div v-if="systems.length" class="system-grid"><button v-for="item in systems" :key="item.id" class="system-card" @click="router.push(`/systems/${item.id}`)"><span class="card-icon">{{ item.name.slice(0, 1) }}</span><strong>{{ item.name }}</strong><span class="card-code">{{ item.code }}</span><span class="card-bottom">进入系统 <span>→</span></span></button></div><div v-else class="empty-state"><div class="empty-shape">◇</div><h3>还没有系统</h3><p>创建第一个系统，开始组织你的业务模块。</p><button class="secondary-button" @click="showCreate = true">创建第一个系统</button></div>
      </div>
    </main>

    <div v-if="showCreate" class="modal-backdrop" @click.self="showCreate = false"><div class="modal"><button class="modal-close" aria-label="关闭" @click="showCreate = false">×</button><div class="page-kicker">NEW SYSTEM</div><h2>创建业务系统</h2><p class="muted">系统创建后会自动建立主租户，并将你加入系统。</p><form @submit.prevent="createSystem"><label>系统名称<input v-model="systemName" maxlength="160" required placeholder="例如：采购管理" /></label><label>系统编码<input v-model="systemCode" pattern="[a-z][a-z0-9_]{2,39}" maxlength="40" required placeholder="例如：purchase" /><small>使用小写字母、数字和下划线，创建后作为稳定标识。</small></label><label>用途说明<textarea v-model="systemDescription" maxlength="500" rows="3" placeholder="这个系统解决什么业务问题？"></textarea></label><p v-if="error" class="form-error">{{ error }}</p><div class="modal-actions"><button type="button" class="secondary-button" @click="showCreate = false">取消</button><button class="primary-button" :disabled="saving">{{ saving ? '创建中…' : '创建并进入' }}</button></div></form></div></div>
  </div>
</template>
