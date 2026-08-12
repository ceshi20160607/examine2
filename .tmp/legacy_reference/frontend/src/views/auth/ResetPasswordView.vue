<script setup lang="ts">
import { AlertTriangle, KeyRound } from 'lucide-vue-next'
import { onBeforeUnmount, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { passwordRecoveryApi } from '@/services/passwordRecovery'
import './authPage.css'

const route = useRoute()
const router = useRouter()
const queryToken = typeof route.query.token === 'string' ? route.query.token.trim() : ''
const token = ref(queryToken)
const form = reactive({ newPassword: '', repeatPassword: '' })
const loading = ref(false)
const errorMessage = ref('')
const requestId = ref('')

if (Object.prototype.hasOwnProperty.call(route.query, 'token')) {
  const query = { ...route.query }
  delete query.token
  void router.replace({ path: route.path, query, hash: route.hash })
}

function clearPasswords() {
  form.newPassword = ''
  form.repeatPassword = ''
}

function validate() {
  if (form.newPassword.length < 10 || form.newPassword.length > 200) return '新密码长度须为 10 至 200 个字符'
  if (form.repeatPassword !== form.newPassword) return '两次输入的新密码不一致'
  return ''
}

async function submit() {
  if (loading.value || !token.value) return
  errorMessage.value = validate()
  requestId.value = ''
  if (errorMessage.value) return

  loading.value = true
  try {
    await passwordRecoveryApi.reset(token.value, form.newPassword)
    clearPasswords()
    token.value = ''
    await router.replace('/auth/password/reset/success')
  } catch (error) {
    clearPasswords()
    if (error instanceof ApiRequestError) {
      errorMessage.value = error.message
      requestId.value = error.requestId
    } else {
      errorMessage.value = '暂时无法重置密码，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

onBeforeUnmount(() => {
  clearPasswords()
  token.value = ''
})
</script>

<template>
  <div class="auth-panel password-recovery-panel">
    <ol class="auth-flow-steps" aria-label="密码恢复进度">
      <li><span>1</span>验证账号</li>
      <li class="active"><span>2</span>设置密码</li>
      <li><span>3</span>完成</li>
    </ol>
    <div class="panel-heading"><h1>设置新密码</h1><p>设置完成后，原有登录会话将全部退出。</p></div>
    <template v-if="token">
      <a-alert v-if="errorMessage" class="password-reset-error form-alert" type="error" show-icon role="alert" aria-live="assertive">
        <template #message>{{ errorMessage }}</template>
        <template v-if="requestId" #description>请求编号：{{ requestId }}</template>
      </a-alert>
      <a-form class="password-reset-form" :model="form" layout="vertical" :aria-busy="loading" @finish="submit">
        <a-form-item label="新密码" name="newPassword">
          <a-input-password
            v-model:value="form.newPassword"
            class="password-reset-new"
            aria-label="新密码"
            autocomplete="new-password"
            size="large"
            :maxlength="200"
            :disabled="loading"
            autofocus
          >
            <template #prefix><KeyRound :size="17" /></template>
          </a-input-password>
        </a-form-item>
        <a-form-item label="再次输入新密码" name="repeatPassword">
          <a-input-password
            v-model:value="form.repeatPassword"
            class="password-reset-repeat"
            aria-label="再次输入新密码"
            autocomplete="new-password"
            size="large"
            :maxlength="200"
            :disabled="loading"
          />
        </a-form-item>
        <a-button class="password-reset-submit" type="primary" html-type="submit" size="large" block aria-label="重置密码" :loading="loading" :disabled="loading">重置密码</a-button>
      </a-form>
    </template>
    <div v-else class="password-reset-invalid" role="alert">
      <AlertTriangle :size="34" />
      <strong>恢复链接无效或已从当前页面移除</strong>
      <p>请重新打开收到的恢复链接；如果链接已过期，请重新申请。</p>
      <RouterLink to="/auth/password/forgot">重新找回密码</RouterLink>
    </div>
    <div class="auth-switch"><RouterLink to="/auth/login">返回登录</RouterLink></div>
  </div>
</template>
