<template>
  <div class="login-page">
    <div class="card">
      <h1>登录</h1>
      <p class="muted">examine2 平台管理台</p>
      <form @submit.prevent="submit">
        <label>
          <span>用户名</span>
          <input v-model="username" autocomplete="username" aria-label="用户名" />
        </label>
        <label>
          <span>密码</span>
          <input v-model="password" type="password" autocomplete="current-password" aria-label="密码" />
        </label>
        <p v-if="error" class="error">{{ error }}</p>
        <button type="submit" data-testid="login-submit" :disabled="loading">{{ loading ? '登录中…' : '登录' }}</button>
        <p class="muted" style="margin-top: 0.75rem">
          <router-link to="/register">注册账号</router-link>
        </p>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { login } from '../api/platformAuth'
import { setToken } from '../api/http'

const router = useRouter()
const route = useRoute()
const username = ref('')
const password = ref('')
const error = ref('')
const loading = ref(false)

async function submit() {
  error.value = ''
  if (!username.value.trim() || !password.value) {
    error.value = '请输入用户名和密码'
    return
  }
  loading.value = true
  try {
    const r = await login(username.value.trim(), password.value)
    setToken(r.data.token)
    const redirect = route.query.redirect || '/systems'
    router.replace(String(redirect))
  } catch (e) {
    error.value = e?.message || String(e)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f3f4f6;
}
.card {
  background: #fff;
  padding: 2rem;
  border-radius: 8px;
  width: 100%;
  max-width: 360px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}
h1 {
  margin: 0 0 0.25rem;
  font-size: 1.5rem;
}
.muted {
  color: #666;
  font-size: 0.9rem;
  margin-bottom: 1.5rem;
}
label {
  display: block;
  margin-bottom: 1rem;
}
label span {
  display: block;
  font-size: 0.85rem;
  margin-bottom: 0.35rem;
  color: #444;
}
input {
  width: 100%;
  padding: 0.5rem 0.65rem;
  border: 1px solid #d1d5db;
  border-radius: 6px;
  box-sizing: border-box;
}
button {
  width: 100%;
  margin-top: 0.5rem;
  padding: 0.6rem;
  background: #1677ff;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
}
.error {
  color: #c00;
  font-size: 0.9rem;
}
</style>
