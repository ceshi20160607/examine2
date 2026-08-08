<script setup lang="ts">
import { LockKeyhole, LogIn, UserRound } from 'lucide-vue-next'
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { enterpriseAuthApi } from '@/services/enterpriseAuth'
import { useSessionStore } from '@/stores/session'
import './authPage.css'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const form = reactive({ account: '', password: '' })
const enterprise = reactive({ providerCode: '', username: '', password: '' })
const enterpriseLoading = ref(false)
const enterpriseOpen = ref(false)
const errorMessage = ref('')
const requestId = ref('')
const successMessage = computed(() => {
  if (route.query.notice === 'password-changed') return '密码已更新，所有会话已退出，请使用新密码重新登录。'
  if (route.query.notice === 'password-reset') return '密码已重置，请使用新密码登录。'
  return ''
})

async function submit() {
  errorMessage.value = ''
  requestId.value = ''
  try {
    await session.login(form)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/platform/workbench'
    await router.replace(redirect)
  } catch (error) {
    if (error instanceof ApiRequestError) {
      errorMessage.value = error.message
      requestId.value = error.requestId
      return
    }
    errorMessage.value = '暂时无法连接服务，请稍后重试'
  }
}

async function completeEnterprise(result: Awaited<ReturnType<typeof enterpriseAuthApi.directory>>) {
  if (result.status === 'MFA_REQUIRED') {
    await router.replace({ name: 'mfa', query: { challenge: result.challenge, enroll: result.enrollmentRequired ? '1' : '0' } })
    return
  }
  if (!result.session) throw new Error('missing session')
  session.applyAuth(result.session)
  await router.replace(result.redirectUri || (typeof route.query.redirect === 'string' ? route.query.redirect : '/platform/workbench'))
}

async function startSso() {
  errorMessage.value = ''
  enterpriseLoading.value = true
  try {
    const callback = `${window.location.origin}/auth/sso/callback`
    const result = await enterpriseAuthApi.start(enterprise.providerCode, callback)
    window.location.assign(result.authorizationUrl)
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '企业单点登录暂时不可用'
  } finally {
    enterpriseLoading.value = false
  }
}

async function directoryLogin() {
  errorMessage.value = ''
  enterpriseLoading.value = true
  try {
    await completeEnterprise(await enterpriseAuthApi.directory(enterprise.providerCode, enterprise.username, enterprise.password))
  } catch (error) {
    errorMessage.value = error instanceof ApiRequestError ? error.message : '企业目录登录暂时不可用'
  } finally {
    enterprise.password = ''
    enterpriseLoading.value = false
  }
}
</script>

<template>
  <div class="auth-panel">
    <div class="panel-heading"><h1>登录</h1><p>进入你的平台和业务系统</p></div>
    <a-alert v-if="successMessage" class="password-change-success password-success form-alert" type="success" show-icon role="status" aria-live="polite">
      <template #message>{{ successMessage }}</template>
    </a-alert>
    <a-alert v-if="errorMessage" type="error" show-icon class="form-alert" role="alert" aria-live="assertive">
      <template #message>{{ errorMessage }}</template>
      <template v-if="requestId" #description>请求编号：{{ requestId }}</template>
    </a-alert>
    <a-form :model="form" layout="vertical" :aria-busy="session.loading" @finish="submit">
      <a-form-item label="账号" name="account" :rules="[{ required: true, message: '请输入账号' }]">
        <a-input v-model:value="form.account" aria-label="账号" autocomplete="username" size="large" placeholder="用户名、邮箱或手机号" :disabled="session.loading">
          <template #prefix><UserRound :size="17" /></template>
        </a-input>
      </a-form-item>
      <a-form-item label="密码" name="password" :rules="[{ required: true, message: '请输入密码' }]">
        <a-input-password v-model:value="form.password" aria-label="密码" autocomplete="current-password" size="large" :disabled="session.loading">
          <template #prefix><LockKeyhole :size="17" /></template>
        </a-input-password>
      </a-form-item>
      <div class="login-recovery-link"><RouterLink to="/auth/password/forgot">忘记密码？</RouterLink></div>
      <a-button type="primary" html-type="submit" size="large" block aria-label="登录" :loading="session.loading" :disabled="session.loading"><LogIn :size="17" />登录</a-button>
    </a-form>
    <a-divider>企业身份</a-divider>
    <a-button class="enterprise-login-trigger" block @click="enterpriseOpen = !enterpriseOpen">使用 SSO 或企业目录登录</a-button>
    <a-form v-if="enterpriseOpen" class="enterprise-login" :model="enterprise" layout="vertical" @finish="directoryLogin">
          <a-form-item label="身份源代码" required><a-input v-model:value="enterprise.providerCode" autocomplete="organization" /></a-form-item>
          <a-button class="sso-login" block :loading="enterpriseLoading" :disabled="!enterprise.providerCode" @click="startSso">跳转企业单点登录</a-button>
          <a-divider>或目录账号</a-divider>
          <a-form-item label="目录用户名"><a-input v-model:value="enterprise.username" autocomplete="username" /></a-form-item>
          <a-form-item label="目录密码"><a-input-password v-model:value="enterprise.password" autocomplete="current-password" /></a-form-item>
          <a-button block html-type="submit" :loading="enterpriseLoading" :disabled="!enterprise.providerCode || !enterprise.username || !enterprise.password">企业目录登录</a-button>
    </a-form>
    <div class="auth-switch">还没有账号？<RouterLink to="/auth/register">创建账号和系统</RouterLink></div>
  </div>
</template>
