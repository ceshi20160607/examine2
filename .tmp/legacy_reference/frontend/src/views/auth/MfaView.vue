<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiRequestError } from '@/services/api'
import { enterpriseAuthApi } from '@/services/enterpriseAuth'
import { useSessionStore } from '@/stores/session'
import './authPage.css'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const challenge = computed(() => typeof route.query.challenge === 'string' ? route.query.challenge : '')
const enrollmentRequired = computed(() => route.query.enroll === '1')
const form = reactive({ totpCode: '', recoveryCode: '', secretRef: '', secretVersion: '1' })
const loading = ref(false)
const error = ref('')
const recoveryCodes = ref<string[]>([])

async function submit() {
  error.value = ''
  if (!challenge.value) { error.value = 'MFA 挑战已失效，请重新登录'; return }
  loading.value = true
  try {
    const result = enrollmentRequired.value
      ? await enterpriseAuthApi.enrollMfa(challenge.value, form.secretRef, form.secretVersion, form.totpCode)
      : await enterpriseAuthApi.verifyMfa(challenge.value, form.totpCode || undefined, form.recoveryCode || undefined)
    recoveryCodes.value = result.enrollment?.recoveryCodes ?? []
    if (!result.session) throw new Error('missing session')
    session.applyAuth(result.session)
    if (!recoveryCodes.value.length) await router.replace(result.redirectUri || '/platform/workbench')
  } catch (cause) {
    error.value = cause instanceof ApiRequestError ? cause.message : 'MFA 验证失败'
  } finally {
    form.totpCode = ''
    form.recoveryCode = ''
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-panel mfa-panel">
    <h1>{{ enrollmentRequired ? '绑定多因素认证' : '多因素认证' }}</h1>
    <p v-if="enrollmentRequired">填写管理员提供的密钥引用并用验证器输入动态码；系统不会保存明文密钥。</p>
    <a-alert v-if="error" type="error" show-icon :message="error" role="alert" />
    <a-alert v-if="recoveryCodes.length" type="success" show-icon message="请立即保存恢复码，仅显示一次">
      <template #description><code v-for="code in recoveryCodes" :key="code" class="recovery-code">{{ code }}</code></template>
    </a-alert>
    <a-form v-else layout="vertical" :model="form" @finish="submit">
      <template v-if="enrollmentRequired">
        <a-form-item label="密钥引用" required><a-input v-model:value="form.secretRef" autocomplete="off" /></a-form-item>
        <a-form-item label="密钥版本" required><a-input v-model:value="form.secretVersion" autocomplete="off" /></a-form-item>
      </template>
      <a-form-item label="6 位动态码"><a-input v-model:value="form.totpCode" inputmode="numeric" autocomplete="one-time-code" maxlength="6" /></a-form-item>
      <a-form-item v-if="!enrollmentRequired" label="或使用恢复码"><a-input v-model:value="form.recoveryCode" autocomplete="one-time-code" /></a-form-item>
      <a-button type="primary" html-type="submit" block :loading="loading">验证并继续</a-button>
    </a-form>
    <a-button v-if="recoveryCodes.length" type="primary" block @click="router.replace('/platform/workbench')">我已保存，继续</a-button>
  </div>
</template>

<style scoped>.recovery-code{display:inline-block;margin:4px 10px 4px 0}</style>
