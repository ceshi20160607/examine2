<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiRequestError } from '@/services/api'
import { accountApi, enterpriseAuthApi } from '@/services/enterpriseAuth'
import type { AccountProfile, OwnedSession } from '@/types/enterpriseAuth'
import { useSessionStore } from '@/stores/session'

const router = useRouter()
const sessionStore = useSessionStore()
const activeTab = ref('profile')
const loading = ref(false)
const error = ref('')
const success = ref('')
const profile = reactive<AccountProfile>({ id: '', username: '', displayName: '', version: 0 })
const sessions = ref<OwnedSession[]>([])
const mfa = reactive({ secretRef: '', secretVersion: '1', totpCode: '' })
const recoveryCodes = ref<string[]>([])
const currentSession = computed(() => sessions.value.find(item => item.current))

function report(cause: unknown) {
  error.value = cause instanceof ApiRequestError ? cause.message : '操作失败，请稍后重试'
}

async function loadProfile() {
  try { Object.assign(profile, await accountApi.profile()) } catch (cause) { report(cause) }
}
async function loadSessions() {
  try { sessions.value = await accountApi.sessions() } catch (cause) { report(cause) }
}
async function saveProfile() {
  loading.value = true; error.value = ''; success.value = ''
  try {
    Object.assign(profile, await accountApi.updateProfile({
      displayName: profile.displayName, email: profile.email, phone: profile.phone,
      locale: profile.locale, timeZone: profile.timeZone, version: profile.version,
    }))
    if (sessionStore.context) sessionStore.context.account.displayName = profile.displayName
    success.value = '个人资料已更新'
  } catch (cause) { report(cause) } finally { loading.value = false }
}
async function enrollMfa() {
  loading.value = true; error.value = ''; recoveryCodes.value = []
  try {
    recoveryCodes.value = (await enterpriseAuthApi.enrollAuthenticated(mfa.secretRef, mfa.secretVersion, mfa.totpCode)).recoveryCodes
    mfa.totpCode = ''
  } catch (cause) { report(cause) } finally { mfa.totpCode = ''; loading.value = false }
}
async function revoke(item: OwnedSession) {
  loading.value = true; error.value = ''
  try {
    await accountApi.revokeSession(item.id)
    if (item.current) {
      sessionStore.$reset(); sessionStore.initialized = true
      await router.replace('/auth/login')
    } else await loadSessions()
  } catch (cause) { report(cause) } finally { loading.value = false }
}
async function revokeOthers() {
  loading.value = true; error.value = ''
  try { await accountApi.revokeOthers(); await loadSessions(); success.value = '其他会话已退出' }
  catch (cause) { report(cause) } finally { loading.value = false }
}

onMounted(async () => { await Promise.all([loadProfile(), loadSessions()]) })
</script>

<template>
  <main class="account-center page-shell">
    <header class="page-header"><div><h1>个人中心</h1><p>维护个人资料、多因素认证和登录会话。</p></div></header>
    <a-alert v-if="error" type="error" show-icon :message="error" closable @close="error = ''" />
    <a-alert v-if="success" type="success" show-icon :message="success" closable @close="success = ''" />
    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="profile" tab="个人资料">
        <a-card class="settings-card" title="基本信息">
          <a-form layout="vertical" :model="profile" @finish="saveProfile">
            <a-row :gutter="16"><a-col :xs="24" :md="12"><a-form-item label="用户名" html-for="account-username"><a-input id="account-username" :value="profile.username" disabled /></a-form-item></a-col>
              <a-col :xs="24" :md="12"><a-form-item label="显示名称" html-for="account-display-name" required><a-input id="account-display-name" v-model:value="profile.displayName" maxlength="80" /></a-form-item></a-col></a-row>
            <a-row :gutter="16"><a-col :xs="24" :md="12"><a-form-item label="邮箱" html-for="account-email"><a-input id="account-email" v-model:value="profile.email" type="email" /></a-form-item></a-col>
              <a-col :xs="24" :md="12"><a-form-item label="手机号" html-for="account-phone"><a-input id="account-phone" v-model:value="profile.phone" /></a-form-item></a-col></a-row>
            <a-row :gutter="16"><a-col :xs="24" :md="12"><a-form-item label="语言" html-for="account-locale"><a-input id="account-locale" v-model:value="profile.locale" placeholder="zh-CN" /></a-form-item></a-col>
              <a-col :xs="24" :md="12"><a-form-item label="时区" html-for="account-time-zone"><a-input id="account-time-zone" v-model:value="profile.timeZone" placeholder="Asia/Shanghai" /></a-form-item></a-col></a-row>
            <a-button type="primary" html-type="submit" :loading="loading">保存资料</a-button>
          </a-form>
        </a-card>
      </a-tab-pane>
      <a-tab-pane key="mfa" tab="多因素认证">
        <a-card class="settings-card" title="绑定验证器">
          <p class="muted">使用安全存储中的 SecretRef，平台只保存引用，不接收明文密钥。</p>
          <a-form layout="vertical" :model="mfa" @finish="enrollMfa">
            <a-form-item label="密钥引用" html-for="account-mfa-secret-ref" required><a-input id="account-mfa-secret-ref" v-model:value="mfa.secretRef" autocomplete="off" /></a-form-item>
            <a-form-item label="密钥版本" html-for="account-mfa-secret-version" required><a-input id="account-mfa-secret-version" v-model:value="mfa.secretVersion" /></a-form-item>
            <a-form-item label="动态验证码" html-for="account-mfa-code" required><a-input id="account-mfa-code" v-model:value="mfa.totpCode" inputmode="numeric" maxlength="6" autocomplete="one-time-code" /></a-form-item>
            <a-button type="primary" html-type="submit" :loading="loading">验证并绑定</a-button>
          </a-form>
          <a-alert v-if="recoveryCodes.length" class="recovery-codes" type="warning" show-icon message="恢复码仅显示一次，请立即离线保存">
            <template #description><code v-for="code in recoveryCodes" :key="code">{{ code }}</code></template>
          </a-alert>
        </a-card>
      </a-tab-pane>
      <a-tab-pane key="sessions" tab="登录会话">
        <a-card class="settings-card" title="最近会话">
          <template #extra><a-button :disabled="!currentSession || loading" @click="revokeOthers">退出其他会话</a-button></template>
          <a-list :data-source="sessions" :loading="loading">
            <template #renderItem="{ item }"><a-list-item>
              <template #actions><a-button danger :disabled="item.status !== 'ACTIVE'" @click="revoke(item)">{{ item.current ? '退出当前会话' : '撤销' }}</a-button></template>
              <a-list-item-meta :title="`${item.contextType === 'PLATFORM' ? '平台' : '系统'}会话${item.current ? '（当前）' : ''}`" :description="`登录：${item.issuedAt} · 最近活动：${item.lastSeenAt || '-'} · ${item.status}`" />
            </a-list-item></template>
          </a-list>
        </a-card>
      </a-tab-pane>
    </a-tabs>
  </main>
</template>

<style scoped>
.account-center{max-width:1080px;margin:0 auto;padding:24px}.page-header{margin-bottom:16px}.page-header h1{margin:0}.settings-card{max-width:900px}.recovery-codes{margin-top:16px}.recovery-codes code{display:inline-block;margin:4px 12px 4px 0}
</style>
