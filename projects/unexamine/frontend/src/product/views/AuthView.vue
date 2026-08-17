<script setup lang="ts">
import { message } from 'ant-design-vue'
import { computed, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import ProductMark from '../components/ProductMark.vue'
import { setPlatformSession, setSystemSession, type CurrentContext, type SessionTokens } from '../session'

const props = defineProps<{ initialMode: 'login' | 'register' }>()
const router = useRouter()
const mode = ref(props.initialMode)
const loading = ref(false)
const error = ref('')
const login = reactive({ username: '', password: '' })
const register = reactive({
  username: '', password: '', displayName: '', email: '', systemName: '', systemCode: '',
})
const title = computed(() => mode.value === 'login' ? '欢迎回来' : '创建你的第一个系统')
const subtitle = computed(() => mode.value === 'login' ? '登录并继续你的工作' : '注册平台账号，同时创建默认主租户')

watch(() => props.initialMode, (value) => {
  mode.value = value
  error.value = ''
})

async function loadContext(token: string) {
  return api<CurrentContext>('/api/auth/me', {}, token)
}

async function submitLogin() {
  loading.value = true
  error.value = ''
  try {
    const tokens = await api<SessionTokens>('/api/auth/login', {
      method: 'POST', body: JSON.stringify(login),
    })
    const context = await loadContext(tokens.accessToken)
    setPlatformSession(tokens, context)
    await router.replace('/platform')
  } catch (reason) {
    error.value = readableError(reason)
  } finally {
    loading.value = false
  }
}

async function submitRegister() {
  loading.value = true
  error.value = ''
  try {
    const result = await api<{
      systemId: number
      tenantId: number
      accountId: number
      systemName: string
      tenantName: string
      tokens: SessionTokens
    }>('/api/auth/register', { method: 'POST', body: JSON.stringify(register) })
    const context = await loadContext(result.tokens.accessToken)
    setPlatformSession(result.tokens, context)
    setSystemSession(result.tokens, { ...context, systemName: result.systemName, tenantName: result.tenantName })
    message.success('系统已创建，正在进入配置引导')
    await router.replace(`/systems/${result.systemId}`)
  } catch (reason) {
    error.value = readableError(reason)
  } finally {
    loading.value = false
  }
}

function readableError(reason: unknown) {
  if (reason instanceof ApiError) {
    return reason.traceId ? `${reason.message}（追踪号：${reason.traceId}）` : reason.message
  }
  return '无法连接服务，请稍后重试'
}

function switchMode(next: 'login' | 'register') {
  error.value = ''
  void router.push(next === 'login' ? '/login' : '/register')
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-story">
      <ProductMark />
      <div class="auth-story__copy">
        <p class="eyebrow">可配置业务系统平台</p>
        <h1>把复杂的管理流程，<br>变成团队真正会用的系统。</h1>
        <p>模块、字段、权限和业务数据统一在一个清晰的工作区中。</p>
      </div>
      <div class="auth-story__status">
        <span class="status-dot" /> 安全会话·系统与租户隔离·操作可追溯
      </div>
    </section>

    <section class="auth-panel">
      <div class="auth-mobile-brand"><ProductMark /></div>
      <div class="auth-card">
        <div class="auth-card__heading">
          <p class="eyebrow">{{ mode === 'login' ? '账号登录' : '开始使用' }}</p>
          <h2>{{ title }}</h2>
          <p>{{ subtitle }}</p>
        </div>

        <a-alert v-if="error" type="error" show-icon :message="error" class="auth-error" />

        <a-form v-if="mode === 'login'" :model="login" layout="vertical" @finish="submitLogin">
          <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]">
            <a-input v-model:value="login.username" size="large" autocomplete="username" placeholder="请输入用户名" />
          </a-form-item>
          <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
            <a-input-password v-model:value="login.password" size="large" autocomplete="current-password" placeholder="请输入密码" />
          </a-form-item>
          <a-button type="primary" size="large" html-type="submit" block :loading="loading">登录</a-button>
          <p class="auth-switch">还没有账号？<a @click="switchMode('register')">创建系统</a></p>
        </a-form>

        <a-form v-else :model="register" layout="vertical" @finish="submitRegister">
          <div class="form-grid">
            <a-form-item label="显示名称" name="displayName" :rules="[{ required: true, message: '请输入显示名称' }]"><a-input v-model:value="register.displayName" size="large" /></a-form-item>
            <a-form-item label="用户名" name="username" :rules="[{ required: true, message: '请输入用户名' }]"><a-input v-model:value="register.username" size="large" autocomplete="username" /></a-form-item>
          </div>
          <a-form-item label="邮箱" name="email" :rules="[{ type: 'email', message: '请输入正确的邮箱格式' }]"><a-input v-model:value="register.email" size="large" type="email" /></a-form-item>
          <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }, { min: 8, message: '密码至少 8 位' }]"><a-input-password v-model:value="register.password" size="large" autocomplete="new-password" /></a-form-item>
          <div class="form-grid">
            <a-form-item label="系统名称" name="systemName" :rules="[{ required: true, message: '请输入系统名称' }]"><a-input v-model:value="register.systemName" size="large" /></a-form-item>
            <a-form-item label="系统编码" name="systemCode" :rules="[{ required: true, message: '请输入系统编码' }, { pattern: /^[A-Za-z][A-Za-z0-9_-]{1,49}$/, message: '英文开头，至少 2 位' }]" extra="英文开头，可使用数字、- 和 _">
              <a-input v-model:value="register.systemCode" size="large" />
            </a-form-item>
          </div>
          <a-button type="primary" size="large" html-type="submit" block :loading="loading">创建并进入系统</a-button>
          <p class="auth-switch">已有账号？<a @click="switchMode('login')">直接登录</a></p>
        </a-form>
      </div>
    </section>
  </main>
</template>
