<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import ProductMark from '../components/ProductMark.vue'
import { setPlatformSession, type CurrentContext, type SessionTokens } from '../session'

const route = useRoute()
const router = useRouter()
const error = ref('')

async function complete() {
  const provider = sessionStorage.getItem('unexamine.sso.provider')
  const code = typeof route.query.code === 'string' ? route.query.code : ''
  const state = typeof route.query.state === 'string' ? route.query.state : ''
  if (!provider || !code || !state) {
    error.value = '企业登录回调信息不完整，请返回登录页重新发起。'
    return
  }
  try {
    const tokens = await api<SessionTokens>(`/api/auth/sso/${provider}/complete`, {
      method: 'POST', body: JSON.stringify({ code, state }),
    })
    const context = await api<CurrentContext>('/api/auth/me', {}, tokens.accessToken)
    sessionStorage.removeItem('unexamine.sso.provider')
    setPlatformSession(tokens, context)
    await router.replace('/platform')
  } catch (reason) {
    error.value = reason instanceof ApiError
      ? `${reason.message}${reason.traceId ? `（请求编号：${reason.traceId}）` : ''}`
      : '企业身份源暂时无法完成登录'
  }
}

onMounted(complete)
</script>

<template>
  <main class="callback-page">
    <section class="callback-card">
      <ProductMark />
      <template v-if="error">
        <a-result status="error" title="企业登录未完成" :sub-title="error">
          <template #extra><a-button type="primary" @click="router.replace('/login')">返回登录页</a-button></template>
        </a-result>
      </template>
      <template v-else>
        <a-spin size="large" />
        <h1>正在确认企业身份</h1>
        <p>完成映射和安全检查后将进入平台。</p>
      </template>
    </section>
  </main>
</template>
