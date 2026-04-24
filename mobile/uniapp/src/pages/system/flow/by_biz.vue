<template>
  <Page title="按 biz 查询流程" subtitle="用于排查：按 bizType/bizId 查询实例与待办">
    <view class="u-card u-section">
      <uni-forms labelPosition="top">
        <uni-forms-item label="bizType">
          <uni-easyinput v-model="bizType" placeholder="例如 record" />
        </uni-forms-item>
        <uni-forms-item label="bizId">
          <uni-easyinput v-model="bizId" placeholder="例如 123 / order-001" />
        </uni-forms-item>
      </uni-forms>
      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="loadLatest">查询最新实例</uni-button>
        <uni-button :disabled="loading" @click="loadWithPending">带待办</uni-button>
        <uni-button :disabled="loading" @click="loadActionable">可办理待办</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">结果</view>
      <view style="margin-top: 12px; font-family: monospace; white-space: pre-wrap;">{{ pretty(result) }}</view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { byBiz, byBizActionable, byBizWithPending } from '@/api/flow'

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
    const r = await byBiz(bizType.value.trim(), bizId.value.trim())
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
    const r = await byBizWithPending(bizType.value.trim(), bizId.value.trim())
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
    const r = await byBizActionable(bizType.value.trim(), bizId.value.trim())
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

