<template>
  <Page title="我的" subtitle="环境配置 / 会话信息">
    <view class="u-card">
      <view style="color: var(--u-text-muted)">account: {{ accountText }}</view>
      <view style="color: var(--u-text-muted)">env: {{ env }}</view>
      <view style="color: var(--u-text-muted)">baseURL: {{ baseURL }}</view>

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
        <uni-button type="warn" @click="logout">退出登录</uni-button>
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getBaseURL, getEnv, type AppEnv } from '@/config/env'
import { httpGet, httpPost } from '@/api/http'
import { clearSessionPayload } from '@/store/context'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'

const envRef = ref<AppEnv>(getEnv())
const env = computed(() => envRef.value)
const baseURL = computed(() => getBaseURL())

const me = ref<{ id?: number; username?: string } | null>(null)
const loadingMe = ref(false)
const refreshing = ref(false)

const accountText = computed(() => {
  if (!me.value) return '-'
  return `${me.value.username || '-'} (#${me.value.id || '-'})`
})

function setEnv(e: AppEnv) {
  uni.setStorageSync('env', e)
  envRef.value = e
  uni.showToast({ title: `env=${e}`, icon: 'none' })
}

async function loadMe() {
  loadingMe.value = true
  try {
    const r = await httpGet<any>('/v1/platform/auth/me')
    me.value = r.data
  } finally {
    loadingMe.value = false
  }
}

async function refreshToken() {
  refreshing.value = true
  try {
    const r = await httpPost<{ token: string }>('/v1/platform/auth/refresh')
    if (r.data?.token) {
      uni.setStorageSync('token', r.data.token)
      uni.showToast({ title: 'token 已刷新', icon: 'success' })
    }
  } finally {
    refreshing.value = false
  }
}

async function logout() {
  try {
    await httpPost('/v1/platform/auth/logout')
  } catch {
    // ignore
  }
  uni.removeStorageSync('token')
  clearSessionPayload()
  uni.reLaunch({ url: '/pages/auth/login' })
}

onMounted(loadMe)
</script>

