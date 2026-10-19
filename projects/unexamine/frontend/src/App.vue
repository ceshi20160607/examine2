<script setup lang="ts">
import { onMounted, ref } from 'vue'

type Account = {
  id: number
  account: string
  displayName: string
}

const account = ref<Account | null>(null)
const loginName = ref('')
const password = ref('')
const loading = ref(true)
const message = ref('')

onMounted(async () => {
  try {
    const response = await fetch('/api/platform/session')
    if (response.ok) account.value = await response.json() as Account
  } catch {
    message.value = '暂时无法连接后端，请稍后重试'
  } finally {
    loading.value = false
  }
})

async function login() {
  message.value = ''
  loading.value = true
  try {
    const response = await fetch('/api/platform/session', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ account: loginName.value, password: password.value }),
    })
    if (!response.ok) {
      message.value = response.status === 401 ? '账号或密码错误' : '登录失败，请稍后重试'
      return
    }
    account.value = await response.json() as Account
    password.value = ''
  } catch {
    message.value = '暂时无法连接后端，请稍后重试'
  } finally {
    loading.value = false
  }
}

async function logout() {
  loading.value = true
  message.value = ''
  try {
    const response = await fetch('/api/platform/session', { method: 'DELETE' })
    if (!response.ok) {
      message.value = '退出失败，请稍后重试'
      return
    }
    account.value = null
  } catch {
    message.value = '暂时无法连接后端，请稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="page">
    <header class="brand">
      <span class="brand-mark">U</span>
      <div>
        <strong>Unexamine</strong>
        <small>平台入口</small>
      </div>
    </header>

    <section class="panel" v-if="!account">
      <p class="eyebrow">平台账号</p>
      <h1>登录工作台</h1>
      <p class="intro">使用平台账号和密码登录。</p>
      <form @submit.prevent="login">
        <label for="loginName">账号</label>
        <input id="loginName" v-model="loginName" autocomplete="username" required />
        <label for="password">密码</label>
        <input id="password" v-model="password" type="password" autocomplete="current-password" required />
        <p class="message" role="alert" v-if="message">{{ message }}</p>
        <button type="submit" :disabled="loading">{{ loading ? '正在处理…' : '登录' }}</button>
      </form>
    </section>

    <section class="panel" v-else>
      <p class="eyebrow">已连接平台</p>
      <h1>你好，{{ account.displayName }}</h1>
      <p class="intro">当前登录账号：{{ account.account }}</p>
      <p class="status">账号已登录。</p>
      <p class="message" role="alert" v-if="message">{{ message }}</p>
      <button class="secondary" type="button" :disabled="loading" @click="logout">退出登录</button>
    </section>
  </main>
</template>
