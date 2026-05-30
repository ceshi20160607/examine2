<template>
  <Page title="我的" subtitle="环境配置 / 会话信息">
    <view class="u-card">
      <view style="color: var(--u-text-muted)">account: {{ accountText }}</view>
      <view style="color: var(--u-text-muted)">env: {{ env }}</view>
      <view style="color: var(--u-text-muted)">baseURL: {{ baseURL }}</view>
      <view v-if="session.hasSystem" style="color: var(--u-text-muted)">
        systemId={{ session.payload?.systemId }} tenantId={{ session.payload?.tenantId }}
      </view>

      <view style="margin-top: 12px">
        <view class="u-title" style="margin-bottom: 8px">切换环境</view>
        <ActionBar>
          <uni-button @click="setEnv('dev')">dev</uni-button>
          <uni-button @click="setEnv('test')">test</uni-button>
          <uni-button @click="setEnv('prod')">prod</uni-button>
        </ActionBar>
      </view>

      <view style="margin-top: 12px">
        <view class="u-title" style="margin-bottom: 8px">会话</view>
        <ActionBar>
          <uni-button :disabled="refreshing" @click="refreshToken">刷新 token</uni-button>
          <uni-button :disabled="loadingMe" @click="loadMe">刷新 me</uni-button>
        </ActionBar>
      </view>

      <view style="margin-top: 12px">
        <view class="u-title" style="margin-bottom: 8px">平台</view>
        <ActionBar>
          <uni-button @click="goOpenApps">OpenAPI 应用</uni-button>
          <uni-button @click="goSystems">我的系统</uni-button>
        </ActionBar>
      </view>

      <view style="margin-top: 12px">
        <uni-button type="warn" @click="logout">退出登录</uni-button>
      </view>

      <ErrorBlock :text="error" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getBaseURL, type AppEnv } from '@/config/env'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { ensureLogin } from '@/utils/guard'
import { me as getMe, refresh as refreshAuthToken, logout as doLogout } from '@/api/platformAuth'
import type { PlatAccountMe } from '@/api/platformAuth'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const env = computed(() => session.env)
const baseURL = computed(() => {
  env.value
  return getBaseURL()
})

const me = ref<PlatAccountMe | null>(null)
const loadingMe = ref(false)
const refreshing = ref(false)
const error = ref<string | null>(null)

const accountText = computed(() => {
  if (!me.value) return '-'
  return `${me.value.username || '-'} (#${me.value.id || '-'})`
})

function setEnv(e: AppEnv) {
  session.setEnv(e)
  uni.showToast({ title: `env=${e}`, icon: 'none' })
}

async function loadMe() {
  loadingMe.value = true
  error.value = null
  try {
    const r = await getMe()
    me.value = r.data
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loadingMe.value = false
  }
}

async function refreshToken() {
  refreshing.value = true
  error.value = null
  try {
    const r = await refreshAuthToken()
    if (r.data?.token) {
      session.setToken(r.data.token)
      uni.showToast({ title: 'token 已刷新', icon: 'success' })
    }
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    refreshing.value = false
  }
}

function goOpenApps() {
  if (!ensureLogin()) return
  uni.navigateTo({ url: '/pages/platform/open_apps' })
}

function goSystems() {
  if (!ensureLogin()) return
  uni.navigateTo({ url: '/pages/platform/systems' })
}

async function logout() {
  try {
    await doLogout()
  } catch {
    /* ignore */
  }
  session.logoutAndReLaunch()
}

onMounted(loadMe)
</script>
