<script setup lang="ts">
import { Building2, KeyRound, UserRound, WandSparkles } from 'lucide-vue-next'
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { useSessionStore } from '@/stores/session'
import './authPage.css'

const router = useRouter()
const session = useSessionStore()
const form = reactive({ username: '', displayName: '', password: '', systemName: '', systemCode: '' })
const errorMessage = ref('')
const requestId = ref('')

async function submit() {
  errorMessage.value = ''
  requestId.value = ''
  try {
    const systemId = await session.register(form)
    if (!systemId) throw new Error('Registration response did not include first system')
    await session.switchSystem(systemId)
    await router.replace(`/systems/${systemId}/workbench`)
  } catch (error) {
    if (error instanceof ApiRequestError) {
      errorMessage.value = error.message
      requestId.value = error.requestId
      return
    }
    errorMessage.value = '系统初始化失败，请根据提示重试'
  }
}
</script>

<template>
  <div class="auth-panel wide">
    <div class="panel-heading"><h1>创建账号和首个系统</h1><p>系统、默认租户和管理员身份会一次完成初始化</p></div>
    <a-alert v-if="errorMessage" type="error" show-icon class="form-alert" role="alert" aria-live="assertive">
      <template #message>{{ errorMessage }}</template>
      <template v-if="requestId" #description>请求编号：{{ requestId }}</template>
    </a-alert>
    <a-form :model="form" layout="vertical" :aria-busy="session.loading" @finish="submit">
      <div class="form-grid">
        <a-form-item label="用户名" name="username" :rules="[{ required: true, min: 3, message: '请输入至少 3 个字符' }]">
          <a-input v-model:value="form.username" aria-label="用户名" autocomplete="username" size="large" :disabled="session.loading"><template #prefix><UserRound :size="17" /></template></a-input>
        </a-form-item>
        <a-form-item label="姓名" name="displayName" :rules="[{ required: true, message: '请输入姓名' }]">
          <a-input v-model:value="form.displayName" aria-label="姓名" size="large" :disabled="session.loading" />
        </a-form-item>
        <a-form-item label="密码" name="password" :rules="[{ required: true, min: 10, message: '密码至少 10 位' }]">
          <a-input-password v-model:value="form.password" aria-label="密码" autocomplete="new-password" size="large" :disabled="session.loading"><template #prefix><KeyRound :size="17" /></template></a-input-password>
        </a-form-item>
        <a-form-item label="系统名称" name="systemName" :rules="[{ required: true, message: '请输入系统名称' }]">
          <a-input v-model:value="form.systemName" aria-label="系统名称" size="large" :disabled="session.loading"><template #prefix><Building2 :size="17" /></template></a-input>
        </a-form-item>
        <a-form-item class="span-2" label="系统编码" name="systemCode" :rules="[{ required: true, pattern: /^[a-z][a-z0-9_]{2,31}$/, message: '使用小写字母、数字和下划线，以字母开头' }]">
          <a-input v-model:value="form.systemCode" aria-label="系统编码" :maxlength="32" size="large" :disabled="session.loading" />
        </a-form-item>
      </div>
      <a-button type="primary" html-type="submit" size="large" block aria-label="创建账号并进入系统" :loading="session.loading" :disabled="session.loading"><WandSparkles :size="17" />创建并进入系统</a-button>
    </a-form>
    <div class="auth-switch">已有账号？<RouterLink to="/auth/login">返回登录</RouterLink></div>
  </div>
</template>
