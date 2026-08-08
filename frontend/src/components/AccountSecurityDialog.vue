<script setup lang="ts">
import { LockKeyhole, ShieldCheck } from 'lucide-vue-next'
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { useSessionStore } from '@/stores/session'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ 'update:open': [value: boolean] }>()

const router = useRouter()
const session = useSessionStore()
const form = reactive({
  currentPassword: '',
  newPassword: '',
  repeatPassword: '',
})
const loading = ref(false)
const errorMessage = ref('')
const requestId = ref('')

function clearPasswords() {
  form.currentPassword = ''
  form.newPassword = ''
  form.repeatPassword = ''
}

function resetDialog() {
  clearPasswords()
  errorMessage.value = ''
  requestId.value = ''
  loading.value = false
}

function validate() {
  if (!form.currentPassword.trim() || form.currentPassword.length > 200) return '请输入当前密码'
  if (!form.newPassword.trim() || form.newPassword.length < 10 || form.newPassword.length > 200) return '新密码长度须为 10 至 200 个字符'
  if (form.newPassword === form.currentPassword) return '新密码不能与当前密码相同'
  if (form.repeatPassword !== form.newPassword) return '两次输入的新密码不一致'
  return ''
}

function close() {
  if (loading.value) return
  resetDialog()
  emit('update:open', false)
}

async function submit() {
  if (loading.value) return
  errorMessage.value = validate()
  requestId.value = ''
  if (errorMessage.value) return

  loading.value = true
  try {
    await session.changePassword({
      currentPassword: form.currentPassword,
      newPassword: form.newPassword,
    })
    clearPasswords()
    emit('update:open', false)
    await router.replace({ path: '/auth/login', query: { notice: 'password-changed' } })
  } catch (error) {
    clearPasswords()
    if (error instanceof ApiRequestError) {
      errorMessage.value = error.message
      requestId.value = error.requestId
    } else {
      errorMessage.value = '暂时无法更新密码，请稍后重试'
    }
  } finally {
    loading.value = false
  }
}

watch(() => props.open, resetDialog)
onBeforeUnmount(clearPasswords)
</script>

<template>
  <a-modal
    :open="open"
    title="账户安全"
    :footer="null"
    :mask-closable="!loading"
    :keyboard="!loading"
    destroy-on-close
    @cancel="close"
  >
    <RouterLink class="account-center-link" to="/account" @click="emit('update:open', false)">管理个人资料、MFA 与登录会话</RouterLink>
    <a-form class="account-security-dialog" :model="form" layout="vertical" @finish="submit">
      <p class="muted"><ShieldCheck :size="16" /> 更新密码后，所有已登录会话都会退出。</p>
      <a-alert v-if="errorMessage" id="account-security-error" class="account-security-error" type="error" show-icon>
        <template #message>{{ errorMessage }}</template>
        <template v-if="requestId" #description>请求编号：{{ requestId }}</template>
      </a-alert>
      <a-form-item label="当前密码" name="currentPassword" html-for="account-security-current-password">
        <a-input-password
          id="account-security-current-password"
          v-model:value="form.currentPassword"
          class="account-security-current-password"
          autocomplete="current-password"
          aria-label="当前密码"
          :aria-describedby="errorMessage ? 'account-security-error' : undefined"
          :aria-invalid="Boolean(errorMessage)"
          :maxlength="200"
          autofocus
        >
          <template #prefix><LockKeyhole :size="16" /></template>
        </a-input-password>
      </a-form-item>
      <a-form-item label="新密码" name="newPassword" html-for="account-security-new-password">
        <a-input-password
          id="account-security-new-password"
          v-model:value="form.newPassword"
          class="account-security-new-password"
          autocomplete="new-password"
          aria-label="新密码"
          :aria-describedby="errorMessage ? 'account-security-error' : undefined"
          :aria-invalid="Boolean(errorMessage)"
          :maxlength="200"
        />
      </a-form-item>
      <a-form-item label="再次输入新密码" name="repeatPassword" html-for="account-security-repeat-password">
        <a-input-password
          id="account-security-repeat-password"
          v-model:value="form.repeatPassword"
          class="account-security-repeat-password"
          autocomplete="new-password"
          aria-label="再次输入新密码"
          :aria-describedby="errorMessage ? 'account-security-error' : undefined"
          :aria-invalid="Boolean(errorMessage)"
          :maxlength="200"
        />
      </a-form-item>
      <div class="form-actions">
        <a-button :disabled="loading" @click="close">取消</a-button>
        <a-button
          class="account-security-submit"
          type="primary"
          html-type="submit"
          :loading="loading"
          :disabled="loading"
        >
          更新密码
        </a-button>
      </div>
    </a-form>
  </a-modal>
</template>
