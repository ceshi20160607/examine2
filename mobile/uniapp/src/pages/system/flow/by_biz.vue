<template>
  <view style="padding: 16px">
    <uni-card title="按 biz 查询流程">
      <uni-forms labelPosition="top">
        <uni-forms-item label="bizType">
          <uni-easyinput v-model="bizType" placeholder="例如 record" />
        </uni-forms-item>
        <uni-forms-item label="bizId">
          <uni-easyinput v-model="bizId" placeholder="例如 123 / order-001" />
        </uni-forms-item>
      </uni-forms>
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button type="primary" :disabled="loading" @click="loadLatest">查询最新实例</uni-button>
        <uni-button :disabled="loading" @click="loadWithPending">带待办</uni-button>
        <uni-button :disabled="loading" @click="loadActionable">可办理待办</uni-button>
      </view>
      <view v-if="error" style="margin-top: 12px; color:#d00">{{ error }}</view>
    </uni-card>

    <uni-card title="结果" style="margin-top: 12px">
      <view style="font-family: monospace; white-space: pre-wrap;">{{ pretty(result) }}</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'

const loading = ref(false)
const error = ref<string | null>(null)
const result = ref<any>(null)

const bizType = ref('record')
const bizId = ref('')

onLoad((opts) => {
  const bt = decodeURIComponent(String((opts as any)?.bizType || ''))
  const bi = decodeURIComponent(String((opts as any)?.bizId || ''))
  if (bt) bizType.value = bt
  if (bi) bizId.value = bi
})

function pretty(v: any) {
  try {
    return JSON.stringify(v, null, 2)
  } catch {
    return String(v ?? '')
  }
}

function requireBiz(): boolean {
  if (!bizType.value.trim() || !bizId.value.trim()) {
    error.value = 'bizType/bizId 不能为空'
    return false
  }
  return true
}

async function loadLatest() {
  if (!requireBiz()) return
  loading.value = true
  error.value = null
  try {
    const r = await httpGet<any>(`/v1/system/flow/instances/by-biz?bizType=${encodeURIComponent(bizType.value.trim())}&bizId=${encodeURIComponent(bizId.value.trim())}`)
    result.value = r.data
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

async function loadWithPending() {
  if (!requireBiz()) return
  loading.value = true
  error.value = null
  try {
    const r = await httpGet<any>(
      `/v1/system/flow/instances/by-biz/with-pending-tasks?bizType=${encodeURIComponent(bizType.value.trim())}&bizId=${encodeURIComponent(bizId.value.trim())}`
    )
    result.value = r.data
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

async function loadActionable() {
  if (!requireBiz()) return
  loading.value = true
  error.value = null
  try {
    const r = await httpGet<any>(
      `/v1/system/flow/instances/by-biz/actionable-tasks?bizType=${encodeURIComponent(bizType.value.trim())}&bizId=${encodeURIComponent(bizId.value.trim())}`
    )
    result.value = r.data
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  ensureSystemContext()
})
</script>

