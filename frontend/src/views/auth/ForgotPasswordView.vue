<script setup lang="ts">
import { ArrowLeft, Mail, Send } from 'lucide-vue-next'
import { reactive, ref } from 'vue'

import { ApiRequestError } from '@/services/api'
import { passwordRecoveryApi } from '@/services/passwordRecovery'
import './authPage.css'

const form = reactive({ account: '' })
const loading = ref(false)
const requested = ref(false)
const errorMessage = ref('')
const requestId = ref('')

async function submit() {
  if (loading.value) return
  errorMessage.value = ''
  requestId.value = ''
  loading.value = true
  try {
    await passwordRecoveryApi.request(form.account.trim())
    form.account = ''
    requested.value = true
  } catch (error) {
    if (error instanceof ApiRequestError) {
      errorMessage.value = error.message
      requestId.value = error.requestId
    } else {
      errorMessage.value = '暂时无法提交找回请求，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-panel password-recovery-panel">
    <ol class="auth-flow-steps" aria-label="密码恢复进度">
      <li class="active"><span>1</span>验证账号</li>
      <li><span>2</span>设置密码</li>
      <li><span>3</span>完成</li>
    </ol>
    <div class="panel-heading"><h1>找回密码</h1><p>输入账号、邮箱或手机号，我们会发送密码恢复信息。</p></div>
    <a-alert v-if="requested" class="password-recovery-requested form-alert" type="success" show-icon role="status" aria-live="polite">
      <template #message>如果账号有效，密码恢复信息已发送。</template>
      <template #description>请检查对应的接收渠道，并通过恢复链接继续。为保护账号安全，这里不会确认账号是否存在。</template>
    </a-alert>
    <a-alert v-if="errorMessage" class="password-recovery-error form-alert" type="error" show-icon role="alert" aria-live="assertive">
      <template #message>{{ errorMessage }}</template>
      <template v-if="requestId" #description>请求编号：{{ requestId }}</template>
    </a-alert>
    <a-form :model="form" layout="vertical" :aria-busy="loading" @finish="submit">
      <a-form-item label="账号" name="account" :rules="[{ required: true, message: '请输入账号、邮箱或手机号' }]">
        <a-input
          v-model:value="form.account"
          class="password-recovery-account"
          aria-label="账号"
          autocomplete="username"
          size="large"
          :maxlength="200"
          :disabled="loading"
          placeholder="用户名、邮箱或手机号"
        >
          <template #prefix><Mail :size="17" /></template>
        </a-input>
      </a-form-item>
      <a-button class="password-recovery-submit" type="primary" html-type="submit" size="large" block aria-label="发送密码恢复信息" :loading="loading" :disabled="loading">
        <Send :size="17" />发送恢复信息
      </a-button>
    </a-form>
    <div class="auth-switch"><RouterLink to="/auth/login"><ArrowLeft :size="15" />返回登录</RouterLink></div>
  </div>
</template>
