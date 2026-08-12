<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { useSessionStore } from '@/stores/session'
import './login.css'

const router = useRouter()
const session = useSessionStore()

const account = ref('')
const password = ref('')
const errorMessage = ref('')
const canSubmit = computed(() => account.value.trim().length > 0 && password.value.length > 0 && !session.loading)
const visibleError = computed(() => errorMessage.value || session.bootstrapError)

function loginError(error: unknown) {
  if (!(error instanceof ApiRequestError)) return '登录服务暂时不可用，请稍后重试。'
  if (error.code === 'AUTH_INVALID_CREDENTIALS') {
    return '账号或密码不正确，请重新输入。'
  }
  if (error.code === 'AUTH_ACCOUNT_DISABLED') {
    return '当前账号已停用，请联系管理员。'
  }
  if (error.code === 'AUTH_RATE_LIMITED' || error.status === 429) {
    return '尝试次数过多，请稍后再试。'
  }
  if (error.status === 401) {
    return '账号或密码不正确，请重新输入。'
  }
  return '登录服务暂时不可用，请稍后重试。'
}

async function submit() {
  if (!canSubmit.value) return
  errorMessage.value = ''
  try {
    await session.login({ account: account.value.trim(), password: password.value })
    await router.replace({ name: 'platform-systems' })
  } catch (error) {
    errorMessage.value = loginError(error)
  } finally {
    password.value = ''
  }
}
</script>

<template>
  <main class="vnext-login">
    <section class="vnext-login__panel" aria-labelledby="login-title">
      <header class="vnext-login__header">
        <p class="vnext-login__eyebrow">统一管理平台</p>
        <h1 id="login-title">登录</h1>
        <p>使用你的工作账号进入系统。</p>
      </header>

      <form class="vnext-login__form" :aria-busy="session.loading" @submit.prevent="submit">
        <label for="account">账号</label>
        <input
          id="account"
          v-model="account"
          name="account"
          autocomplete="username"
          placeholder="请输入账号"
          :disabled="session.loading"
          required
          @input="errorMessage = ''"
        >

        <label for="password">密码</label>
        <input
          id="password"
          v-model="password"
          name="password"
          type="password"
          autocomplete="current-password"
          placeholder="请输入密码"
          :disabled="session.loading"
          required
          @input="errorMessage = ''"
        >

        <p v-if="visibleError" class="vnext-login__error" role="alert">{{ visibleError }}</p>
        <button type="submit" :disabled="!canSubmit">
          {{ session.loading ? '正在登录…' : '登录' }}
        </button>
      </form>
      <p class="vnext-login__secondary">还没有账号？<a href="/register">注册</a></p>
    </section>
  </main>
</template>
