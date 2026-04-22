<template>
  <view style="padding: 16px">
    <uni-card title="Backend Health">
      <view>baseURL: {{ baseURL }}</view>
      <view>status: {{ status }}</view>
      <view>latencyMs: {{ latencyMs }}</view>
      <view>requestId: {{ requestId }}</view>
      <view v-if="error" style="color: #d00">{{ error }}</view>
      <view style="margin-top: 12px">
        <uni-button type="primary" @click="ping">Ping</uni-button>
      </view>
    </uni-card>

    <view style="margin-top: 12px">
      <uni-button @click="goLogin">Go Login</uni-button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { getBaseURL } from '@/config/env'
import { httpGet } from '@/api/http'

const baseURL = getBaseURL()
const status = ref<'idle' | 'loading' | 'ok' | 'failed'>('idle')
const latencyMs = ref<number | null>(null)
const requestId = ref<string | null>(null)
const error = ref<string | null>(null)

async function ping() {
  status.value = 'loading'
  error.value = null
  requestId.value = null
  const t0 = Date.now()
  try {
    // 后端 PingController: GET /ping
    const resp = await httpGet<any>('/ping')
    latencyMs.value = Date.now() - t0
    requestId.value = resp.requestId ?? null
    status.value = 'ok'
  } catch (e: any) {
    latencyMs.value = Date.now() - t0
    status.value = 'failed'
    error.value = e?.message ?? String(e)
  }
}

function goLogin() {
  uni.navigateTo({ url: '/pages/auth/login' })
}
</script>

