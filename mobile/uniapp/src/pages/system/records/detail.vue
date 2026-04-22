<template>
  <view style="padding: 16px">
    <uni-card :title="`Record #${recordId}`">
      <view style="display:flex; gap: 8px;">
        <uni-button type="primary" :disabled="!recordId" @click="goEdit">编辑</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </view>
      <view style="margin-top: 12px; font-family: monospace; white-space: pre-wrap;">
        {{ pretty }}
      </view>
      <view v-if="error" style="margin-top: 12px; color:#d00">{{ error }}</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, onMounted, ref } from 'vue'
import { httpGet } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

const recordId = ref<number>(0)
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<any>(null)

onLoad((opts) => {
  recordId.value = Number((opts as any)?.recordId || 0) || 0
})

const pretty = computed(() => {
  try {
    return JSON.stringify(detail.value, null, 2)
  } catch {
    return String(detail.value ?? '')
  }
})

async function load() {
  if (!recordId.value) return
  loading.value = true
  error.value = null
  try {
    const r = await httpGet<any>(`/v1/system/records/${recordId.value}`)
    detail.value = r.data
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

function goEdit() {
  uni.navigateTo({ url: `/pages/system/records/form?recordId=${recordId.value}` })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>

