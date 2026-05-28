<template>
  <div class="auth-page">
    <form class="auth" @submit.prevent="submit">
      <h1>注册账号</h1>
      <p class="muted">创建平台账号后继续登录。</p>
      <label>
        <span>用户名</span>
        <input v-model="username" required autocomplete="username" />
      </label>
      <label>
        <span>密码</span>
        <input v-model="password" type="password" required autocomplete="new-password" />
      </label>
      <button type="submit">注册</button>
      <p v-if="error" class="error">{{ error }}</p>
      <router-link to="/login">已有账号？登录</router-link>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { register } from '../api/platformAuth.js'

const router = useRouter()
const username = ref('')
const password = ref('')
const error = ref('')

async function submit() {
  error.value = ''
  try {
    await register(username.value.trim(), password.value)
    alert('注册成功，请登录')
    router.push('/login')
  } catch (e) {
    error.value = e?.message || String(e)
  }
}
</script>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  place-items: center;
  padding: 2rem;
  background:
    linear-gradient(145deg, rgba(22, 115, 111, 0.12), rgba(240, 184, 75, 0.12)),
    var(--color-bg);
}
.auth {
  max-width: 360px;
  width: 100%;
  padding: 2rem;
  background: #fff;
  border-radius: 8px;
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-md);
}
h1 {
  margin: 0 0 0.35rem;
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
  color: #43524b;
  font-size: 0.85rem;
  font-weight: 700;
}
label span {
  display: block;
  margin-bottom: 0.35rem;
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
  min-height: 42px;
  padding: 0.65rem;
  background: var(--color-primary);
  color: #fff;
  border: 1px solid var(--color-primary);
  border-radius: 7px;
  cursor: pointer;
  font-weight: 800;
}
.error {
  color: var(--color-danger);
  background: var(--color-danger-soft);
  border: 1px solid #ffd3cf;
  border-radius: 7px;
  padding: 0.55rem 0.7rem;
  font-size: 0.9rem;
}
</style>
