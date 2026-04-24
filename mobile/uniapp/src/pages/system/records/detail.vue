<template>
  <Page :title="`Record #${recordId}`" subtitle="查看/复制/删除">
    <view class="u-card">
      <ActionBar>
        <uni-button type="primary" :disabled="!recordId" @click="goEdit">编辑</uni-button>
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
        <uni-button type="warn" :disabled="!recordId || loading" @click="doDelete">删除</uni-button>
        <uni-button :disabled="!detail" @click="copyJson">复制 JSON</uni-button>
        <uni-button @click="toggleRaw">{{ showRaw ? '结构化' : '原始 JSON' }}</uni-button>
      </ActionBar>

      <view v-if="showRaw" style="margin-top: 12px; font-family: monospace; white-space: pre-wrap;">
        {{ pretty }}
      </view>
      <view v-else style="margin-top: 12px">
        <uni-list v-if="dataEntries.length">
          <uni-list-item
            v-for="e in dataEntries"
            :key="e.k"
            :title="e.k"
            :note="e.v"
            clickable
            @click="copyText(`${e.k}=${e.v}`)"
          />
        </uni-list>
        <EmptyState v-else text="无字段数据" />
      </view>
      <ErrorBlock :text="error" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { computed, onMounted, ref } from 'vue'
import { httpGet, httpRequest } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'

const recordId = ref<number>(0)
const loading = ref(false)
const error = ref<string | null>(null)
const detail = ref<any>(null)
const showRaw = ref(false)

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

const dataEntries = computed(() => {
  const data = detail.value?.data
  if (!data || typeof data !== 'object') return []
  return Object.keys(data)
    .slice()
    .sort()
    .map((k) => ({ k, v: stringifyValue(data[k]) }))
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

function toggleRaw() {
  showRaw.value = !showRaw.value
}

function copyText(s: string) {
  uni.setClipboardData({ data: s })
}

function copyJson() {
  copyText(pretty.value)
}

function stringifyValue(v: any): string {
  if (v == null) return ''
  if (Array.isArray(v)) return v.slice(0, 20).join(',')
  if (typeof v === 'object') {
    try {
      return JSON.stringify(v)
    } catch {
      return String(v)
    }
  }
  return String(v)
}

function doDelete() {
  if (!recordId.value) return
  uni.showModal({
    title: '确认删除？',
    content: `将删除记录 #${recordId.value}`,
    success: async (m) => {
      if (!m.confirm) return
      try {
        await httpRequest('DELETE', `/v1/system/records/${recordId.value}`)
        uni.showToast({ title: '已删除', icon: 'success' })
        uni.navigateBack()
      } catch (e: any) {
        error.value = e?.message ?? String(e)
      }
    }
  })
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>

