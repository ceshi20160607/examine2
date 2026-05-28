<template>
  <div class="login-page">
    <section class="login-copy" aria-label="平台介绍">
      <p class="eyebrow">examine2</p>
      <h1>低代码审批与业务数据管理</h1>
      <p>进入平台后可切换系统、维护应用模型、配置流程并查看待办。</p>
    </section>
    <div class="card">
      <h2>登录</h2>
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
  display: grid;
  grid-template-columns: minmax(280px, 460px) minmax(320px, 390px);
  gap: 3rem;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  background:
    linear-gradient(145deg, rgba(22, 115, 111, 0.12), rgba(240, 184, 75, 0.12)),
    var(--color-bg);
}
.login-copy {
  max-width: 460px;
}
.login-copy .eyebrow {
  margin-bottom: 0.6rem;
  color: var(--color-primary);
  font-weight: 800;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.login-copy h1 {
  margin: 0 0 1rem;
  font-size: clamp(2rem, 5vw, 3.5rem);
  line-height: 1.05;
}
.login-copy p:last-child {
  color: var(--color-muted);
  font-size: 1rem;
  max-width: 34rem;
}
.card {
  background: #fff;
  padding: 2rem;
  border-radius: 8px;
  width: 100%;
  max-width: 390px;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-md);
}
h2 {
  margin: 0 0 0.25rem;
  font-size: 1.45rem;
}
.muted {
  color: var(--color-muted);
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
  color: #43524b;
  font-weight: 700;
}
input {
  width: 100%;
  min-height: 40px;
  padding: 0.55rem 0.7rem;
  border: 1px solid var(--color-border-strong);
  border-radius: 7px;
  box-sizing: border-box;
}
button {
  width: 100%;
  margin-top: 0.5rem;
  min-height: 42px;
  padding: 0.65rem;
  background: var(--color-primary);
  color: #fff;
  border: 1px solid var(--color-primary);
  border-radius: 7px;
  cursor: pointer;
  font-weight: 800;
}
button:disabled {
  opacity: 0.6;
}
.error {
  color: var(--color-danger);
  background: var(--color-danger-soft);
  border: 1px solid #ffd3cf;
  border-radius: 7px;
  padding: 0.55rem 0.7rem;
  font-size: 0.9rem;
}

@media (max-width: 820px) {
  .login-page {
    grid-template-columns: 1fr;
    gap: 1.5rem;
    align-content: center;
  }
  .login-copy {
    max-width: 390px;
  }
}
</style>
