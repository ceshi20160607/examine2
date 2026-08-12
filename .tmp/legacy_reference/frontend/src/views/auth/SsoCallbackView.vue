<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiRequestError } from '@/services/api'
import { enterpriseAuthApi } from '@/services/enterpriseAuth'
import { useSessionStore } from '@/stores/session'
import './authPage.css'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const error = ref('')

onMounted(async () => {
  const state = typeof route.query.state === 'string' ? route.query.state : ''
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  if (!state || !code) { error.value = '身份回调参数不完整，请重新登录'; return }
  try {
    const result = await enterpriseAuthApi.callback(state, code)
    if (result.status === 'MFA_REQUIRED') {
      await router.replace({ name: 'mfa', query: { challenge: result.challenge, enroll: result.enrollmentRequired ? '1' : '0' } })
      return
    }
    if (!result.session) throw new Error('missing session')
    session.applyAuth(result.session)
    await router.replace(result.redirectUri || '/platform/workbench')
  } catch (cause) {
    error.value = cause instanceof ApiRequestError ? cause.message : '企业身份登录暂时不可用'
  }
})
</script>

<template>
  <div class="auth-panel sso-callback" aria-live="polite">
    <h1>企业身份登录</h1>
    <a-alert v-if="error" type="error" show-icon :message="error" />
    <a-spin v-else tip="正在验证企业身份…" />
    <RouterLink v-if="error" to="/auth/login">返回登录</RouterLink>
  </div>
</template>
