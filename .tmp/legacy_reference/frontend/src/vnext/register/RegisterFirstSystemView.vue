<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { InvalidRegistrationContextError, useSessionStore } from '@/stores/session'
import type { RegisterFirstSystemInput } from '@/types/session'
import './register.css'

type RegistrationField = keyof RegisterFirstSystemInput
type RegistrationState =
  | 'idle'
  | 'validation_error'
  | 'submitting'
  | 'username_conflict'
  | 'system_code_conflict'
  | 'rollback_failure'

const router = useRouter()
const session = useSessionStore()
const form = reactive<RegisterFirstSystemInput>({
  username: '',
  displayName: '',
  password: '',
  systemName: '',
  systemCode: '',
})
const fieldErrors = reactive<Record<RegistrationField, string>>({
  username: '',
  displayName: '',
  password: '',
  systemName: '',
  systemCode: '',
})
const state = ref<RegistrationState>('idle')
const formError = ref('')
const isSubmitting = computed(() => state.value === 'submitting' || session.loading)

const invalidMessages: Record<RegistrationField, string> = {
  username: '用户名需以字母开头，使用 3 至 32 位字母、数字或下划线。',
  displayName: '显示名称长度应为 1 至 120 个字符。',
  password: '密码长度应为 10 至 200 个字符。',
  systemName: '系统名称长度应为 1 至 160 个字符。',
  systemCode: '系统编码需以小写字母开头，使用 3 至 32 位小写字母、数字或下划线。',
}

function clearErrors() {
  for (const field of Object.keys(fieldErrors) as RegistrationField[]) {
    fieldErrors[field] = ''
  }
  formError.value = ''
}

function clearFieldError(field: RegistrationField) {
  fieldErrors[field] = ''
  formError.value = ''
  if (state.value !== 'submitting') state.value = 'idle'
}

function normalizedInput(): RegisterFirstSystemInput {
  return {
    username: form.username.normalize('NFKC').trim(),
    displayName: form.displayName.trim(),
    password: form.password,
    systemName: form.systemName.trim(),
    systemCode: form.systemCode,
  }
}

function validate(input: RegisterFirstSystemInput) {
  if (!/^[A-Za-z][A-Za-z0-9_]{2,31}$/.test(input.username)) {
    fieldErrors.username = invalidMessages.username
  }
  if (input.displayName.length < 1 || input.displayName.length > 120) {
    fieldErrors.displayName = invalidMessages.displayName
  }
  if (input.password.length < 10 || input.password.length > 200) {
    fieldErrors.password = invalidMessages.password
  }
  if (input.systemName.length < 1 || input.systemName.length > 160) {
    fieldErrors.systemName = invalidMessages.systemName
  }
  if (!/^[a-z][a-z0-9_]{2,31}$/.test(input.systemCode)) {
    fieldErrors.systemCode = invalidMessages.systemCode
  }
  return !Object.values(fieldErrors).some(Boolean)
}

function registrationField(path: string | undefined): RegistrationField | undefined {
  const candidate = path
    ?.replace(/\[([^\]]+)]/g, '.$1')
    .replaceAll('/', '.')
    .split('.')
    .filter(Boolean)
    .at(-1)
  return candidate && candidate in fieldErrors ? candidate as RegistrationField : undefined
}

function invalidResponse(error: ApiRequestError) {
  let hasFieldError = false
  for (const item of error.errors) {
    const field = registrationField(item.path)
    if (!field) continue
    fieldErrors[field] = invalidMessages[field]
    hasFieldError = true
  }
  if (!hasFieldError) formError.value = '请检查填写内容后重试。'
  state.value = 'validation_error'
}

function handleFailure(error: unknown) {
  if (error instanceof ApiRequestError) {
    if (error.code === 'REGISTER_USERNAME_CONFLICT') {
      fieldErrors.username = '该用户名已被使用，请更换后重试。'
      state.value = 'username_conflict'
      return
    }
    if (error.code === 'REGISTER_SYSTEM_CODE_CONFLICT') {
      fieldErrors.systemCode = '该系统编码已被使用，请更换后重试。'
      state.value = 'system_code_conflict'
      return
    }
    if (error.code === 'REGISTER_INVALID') {
      invalidResponse(error)
      return
    }
    if (error.code === 'REGISTER_FAILED' || error.status === 500) {
      formError.value = '创建未完成，可重新输入密码后安全重试。'
      state.value = 'rollback_failure'
      return
    }
    if (error.status === 409) {
      formError.value = '用户名或系统编码已被使用，请检查后重试。'
      state.value = 'validation_error'
      return
    }
  }
  formError.value = error instanceof InvalidRegistrationContextError
    ? '暂时无法确认新系统上下文，请返回登录后重试。'
    : '创建服务暂时不可用，请稍后重试。'
  state.value = 'rollback_failure'
}

async function submit() {
  if (isSubmitting.value) return
  clearErrors()
  const input = normalizedInput()
  if (!validate(input)) {
    state.value = 'validation_error'
    form.password = ''
    return
  }

  state.value = 'submitting'
  const idempotencyKey = crypto.randomUUID()
  try {
    const systemId = await session.register(input, idempotencyKey)
    await router.replace({ name: 'system-onboarding', params: { systemId } })
  } catch (error) {
    handleFailure(error)
  } finally {
    form.password = ''
    if (state.value === 'submitting') state.value = 'idle'
  }
}
</script>

<template>
  <main class="vnext-register">
    <section class="vnext-register__panel" aria-labelledby="register-title" :data-state="state">
      <header class="vnext-register__header">
        <p class="vnext-register__eyebrow">统一管理平台</p>
        <h1 id="register-title">创建账号和首个系统</h1>
        <p>一次完成账号和系统初始化，创建后直接进入配置引导。</p>
      </header>

      <form class="vnext-register__form" :aria-busy="isSubmitting" novalidate @submit.prevent="submit">
        <div class="vnext-register__fields">
          <label>
            <span>用户名</span>
            <input
              v-model="form.username"
              name="username"
              autocomplete="username"
              maxlength="32"
              :disabled="isSubmitting"
              :aria-invalid="Boolean(fieldErrors.username)"
              aria-describedby="username-error"
              @input="clearFieldError('username')"
            >
            <small v-if="fieldErrors.username" id="username-error" data-error-for="username" role="alert">{{ fieldErrors.username }}</small>
          </label>

          <label>
            <span>显示名称</span>
            <input
              v-model="form.displayName"
              name="displayName"
              autocomplete="name"
              maxlength="120"
              :disabled="isSubmitting"
              :aria-invalid="Boolean(fieldErrors.displayName)"
              aria-describedby="displayName-error"
              @input="clearFieldError('displayName')"
            >
            <small v-if="fieldErrors.displayName" id="displayName-error" data-error-for="displayName" role="alert">{{ fieldErrors.displayName }}</small>
          </label>

          <label>
            <span>密码</span>
            <input
              v-model="form.password"
              name="password"
              type="password"
              autocomplete="new-password"
              maxlength="200"
              :disabled="isSubmitting"
              :aria-invalid="Boolean(fieldErrors.password)"
              aria-describedby="password-error"
              @input="clearFieldError('password')"
            >
            <small v-if="fieldErrors.password" id="password-error" data-error-for="password" role="alert">{{ fieldErrors.password }}</small>
          </label>

          <label>
            <span>系统名称</span>
            <input
              v-model="form.systemName"
              name="systemName"
              maxlength="160"
              :disabled="isSubmitting"
              :aria-invalid="Boolean(fieldErrors.systemName)"
              aria-describedby="systemName-error"
              @input="clearFieldError('systemName')"
            >
            <small v-if="fieldErrors.systemName" id="systemName-error" data-error-for="systemName" role="alert">{{ fieldErrors.systemName }}</small>
          </label>

          <label class="vnext-register__wide-field">
            <span>系统编码</span>
            <input
              v-model="form.systemCode"
              name="systemCode"
              maxlength="32"
              spellcheck="false"
              :disabled="isSubmitting"
              :aria-invalid="Boolean(fieldErrors.systemCode)"
              aria-describedby="systemCode-error"
              @input="clearFieldError('systemCode')"
            >
            <small v-if="fieldErrors.systemCode" id="systemCode-error" data-error-for="systemCode" role="alert">{{ fieldErrors.systemCode }}</small>
          </label>
        </div>

        <p v-if="formError" class="vnext-register__error" role="alert">{{ formError }}</p>
        <button type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? '正在创建…' : '创建账号并创建系统' }}
        </button>
      </form>
      <p class="vnext-register__secondary">已有账号？<a href="/login">返回登录</a></p>
    </section>
  </main>
</template>
