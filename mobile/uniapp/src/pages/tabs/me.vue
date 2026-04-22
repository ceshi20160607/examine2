<template>
  <view style="padding: 16px">
    <uni-card title="我的">
      <view style="color:#666">env: {{ env }}</view>
      <view style="color:#666">baseURL: {{ baseURL }}</view>
      <view style="margin-top: 12px; display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button @click="setEnv('dev')">dev</uni-button>
        <uni-button @click="setEnv('test')">test</uni-button>
        <uni-button @click="setEnv('prod')">prod</uni-button>
      </view>
      <view style="margin-top: 12px">
        <uni-button type="warn" @click="logout">退出登录</uni-button>
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { getBaseURL, getEnv, type AppEnv } from '@/config/env'

const envRef = ref<AppEnv>(getEnv())
const env = computed(() => envRef.value)
const baseURL = computed(() => getBaseURL())

function setEnv(e: AppEnv) {
  uni.setStorageSync('env', e)
  envRef.value = e
  uni.showToast({ title: `env=${e}`, icon: 'none' })
}

function logout() {
  uni.removeStorageSync('token')
  uni.reLaunch({ url: '/pages/auth/login' })
}
</script>

