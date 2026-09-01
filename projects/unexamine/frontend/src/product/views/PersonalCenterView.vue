<script setup lang="ts">
import { KeyOutlined, ReloadOutlined, SafetyCertificateOutlined, SaveOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import PlatformShell from '../components/PlatformShell.vue'
import {
  clearSession,
  clearSystemSession,
  platformContext,
  platformTokens,
  setPlatformSession,
} from '../session'
import type { AccountProfile, AccountSession } from '../types'

const router = useRouter()
const profile = ref<AccountProfile | null>(null)
const loading = ref(false)
const saving = ref(false)
const changingPassword = ref(false)
const revokingSessionId = ref<number | null>(null)
const error = ref('')
const success = ref('')
const form = reactive({ displayName: '', email: '', mobile: '', locale: 'zh-CN', timezone: 'Asia/Shanghai' })
const password = reactive({ currentPassword: '', newPassword: '', confirmation: '' })
const activeSessions = computed(() => profile.value?.sessions.filter((session) => !session.revoked) ?? [])

function showError(reason: unknown, fallback: string) {
  error.value = reason instanceof ApiError
    ? `${reason.message}${reason.traceId ? `（追踪号：${reason.traceId}）` : ''}`
    : fallback
}

function synchronizeForm(value: AccountProfile) {
  form.displayName = value.displayName
  form.email = value.email ?? ''
  form.mobile = value.mobile ?? ''
  form.locale = value.locale
  form.timezone = value.timezone
}

async function requireToken() {
  const token = platformTokens.value?.accessToken
  if (!token) {
    clearSession()
    await router.replace('/login')
    return null
  }
  return token
}

async function loadProfile() {
  const token = await requireToken()
  if (!token) return
  loading.value = true
  error.value = ''
  try {
    const result = await api<AccountProfile>('/api/account/profile', {}, token)
    profile.value = result
    synchronizeForm(result)
  } catch (reason) {
    showError(reason, '个人资料加载失败')
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  const token = await requireToken()
  if (!token || !profile.value) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const result = await api<AccountProfile>('/api/account/profile', {
      method: 'PUT',
      body: JSON.stringify({ ...form, version: profile.value.version }),
    }, token)
    profile.value = result
    synchronizeForm(result)
    if (platformTokens.value && platformContext.value) {
      setPlatformSession(platformTokens.value, { ...platformContext.value, displayName: result.displayName })
    }
    success.value = '个人资料已保存，并已读取数据库中的最新结果。'
  } catch (reason) {
    showError(reason, '个人资料保存失败')
  } finally {
    saving.value = false
  }
}

async function changePassword() {
  const token = await requireToken()
  if (!token) return
  error.value = ''
  success.value = ''
  if (password.newPassword !== password.confirmation) {
    error.value = '两次输入的新密码不一致'
    return
  }
  changingPassword.value = true
  try {
    const result = await api<AccountProfile>('/api/account/password', {
      method: 'POST',
      body: JSON.stringify({ currentPassword: password.currentPassword, newPassword: password.newPassword }),
    }, token)
    profile.value = result
    password.currentPassword = ''
    password.newPassword = ''
    password.confirmation = ''
    clearSystemSession()
    success.value = '密码已修改，其他设备和系统会话已退出。'
  } catch (reason) {
    showError(reason, '密码修改失败')
  } finally {
    changingPassword.value = false
  }
}

async function revokeSession(session: AccountSession) {
  const token = await requireToken()
  if (!token) return
  revokingSessionId.value = session.id
  error.value = ''
  success.value = ''
  try {
    await api<void>(`/api/account/sessions/${session.id}/revoke`, { method: 'POST' }, token)
    success.value = '会话已退出。'
    await loadProfile()
  } catch (reason) {
    showError(reason, '会话退出失败')
  } finally {
    revokingSessionId.value = null
  }
}

function formatDate(value?: string) {
  return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value)) : '—'
}

onMounted(loadProfile)
</script>

<template>
  <PlatformShell>
    <div class="page-heading">
      <div><p class="eyebrow">账号与安全</p><h1>个人中心</h1><p>维护账号资料、密码和当前有效会话。</p></div>
      <a-button :loading="loading" @click="loadProfile"><ReloadOutlined />刷新</a-button>
    </div>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-alert v-if="success" type="success" show-icon :message="success" class="section-alert" closable @close="success = ''" />
    <a-spin :spinning="loading">
      <div v-if="profile" class="profile-layout">
        <section class="panel-card profile-card">
          <div class="panel-title"><strong>基本资料</strong><span>@{{ profile.username }}</span></div>
          <a-form layout="vertical" class="profile-form">
            <div class="form-grid">
              <a-form-item label="显示名称" required><a-input v-model:value="form.displayName" :maxlength="100" /></a-form-item>
              <a-form-item label="手机"><a-input v-model:value="form.mobile" :maxlength="40" /></a-form-item>
            </div>
            <a-form-item label="邮箱"><a-input v-model:value="form.email" type="email" :maxlength="255" /></a-form-item>
            <div class="form-grid">
              <a-form-item label="语言"><a-select v-model:value="form.locale" :options="[{ value: 'zh-CN', label: '简体中文' }, { value: 'en-US', label: 'English' }]" /></a-form-item>
              <a-form-item label="时区"><a-select v-model:value="form.timezone" :options="[{ value: 'Asia/Shanghai', label: 'Asia/Shanghai' }, { value: 'UTC', label: 'UTC' }]" /></a-form-item>
            </div>
            <a-button type="primary" :loading="saving" @click="saveProfile"><SaveOutlined />保存资料</a-button>
          </a-form>
        </section>

        <section class="panel-card profile-card">
          <div class="panel-title"><strong>修改密码</strong><span>定期更新密码有助于保护账号</span></div>
          <a-form layout="vertical" class="profile-form">
            <a-form-item label="当前密码" required><a-input-password v-model:value="password.currentPassword" autocomplete="current-password" /></a-form-item>
            <a-form-item label="新密码" required extra="至少 12 位，并包含大小写字母、数字和符号"><a-input-password v-model:value="password.newPassword" autocomplete="new-password" /></a-form-item>
            <a-form-item label="确认新密码" required><a-input-password v-model:value="password.confirmation" autocomplete="new-password" /></a-form-item>
            <a-button type="primary" :loading="changingPassword" @click="changePassword"><KeyOutlined />修改密码</a-button>
          </a-form>
        </section>

        <section class="panel-card profile-card profile-card--wide">
          <div class="panel-title"><strong>登录安全</strong><span>最近登录 {{ formatDate(profile.lastLoginAt) }}</span></div>
          <div class="security-summary">
            <SafetyCertificateOutlined />
            <div><strong>多因素认证</strong><span v-if="profile.mfaMethods.length">已配置 {{ profile.mfaMethods.length }} 种方式</span><span v-else>暂未配置认证方式</span></div>
          </div>
          <div class="session-list">
            <div v-for="session in activeSessions" :key="session.id" class="session-row">
              <div>
                <strong>{{ session.contextType === 'PLATFORM' ? '平台会话' : '系统工作区会话' }}</strong>
                <small>创建于 {{ formatDate(session.createdAt) }} · 有效至 {{ formatDate(session.refreshExpiresAt) }}</small>
              </div>
              <div class="session-row__actions">
                <a-tag v-if="session.current" color="processing">当前会话</a-tag>
                <a-tag v-else>{{ session.mfaLevel === 'MFA' ? '多因素认证' : '基础认证' }}</a-tag>
                <a-button v-if="!session.current" size="small" danger :loading="revokingSessionId === session.id" @click="revokeSession(session)">退出</a-button>
              </div>
            </div>
          </div>
        </section>
      </div>
    </a-spin>
  </PlatformShell>
</template>
