<template>
  <div class="auth">
    <h1>注册</h1>
    <form @submit.prevent="submit">
      <label>用户名<input v-model="username" required /></label>
      <label>密码<input v-model="password" type="password" required /></label>
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
.auth {
  max-width: 360px;
  margin: 4rem auto;
  padding: 2rem;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}
label {
  display: block;
  margin-bottom: 1rem;
}
input {
  width: 100%;
  margin-top: 0.35rem;
  padding: 0.5rem;
  box-sizing: border-box;
}
button {
  width: 100%;
  padding: 0.6rem;
  background: #1677ff;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
}
.error {
  color: #c00;
}
</style>
