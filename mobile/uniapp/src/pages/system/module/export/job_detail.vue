<template>
  <Page :title="`Export Job #${jobId}`" subtitle="异步导出任务详情（自动轮询 pending/running）">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="reload">刷新</uni-button>
        <uni-button :disabled="!downloadUrl || loading" @click="download">下载结果</uni-button>
        <uni-button :disabled="!viewUrl || loading" @click="preview">预览结果</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">job</view>
      <view style="margin-top: 12px; font-family: monospace; white-space: pre-wrap;">{{ pretty(job) }}</view>
    </view>

    <view class="u-card u-section" v-if="file">
      <view class="u-title">file</view>
      <view style="margin-top: 12px; font-family: monospace; white-space: pre-wrap;">{{ pretty(file) }}</view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { buildApiUrl, buildAuthHeaders } from '@/api/http'
import { ensureSystemContext, hasToken } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { getExportJobDetail } from '@/api/module'

const jobId = ref<number>(0)
const loading = ref(false)
const error = ref<string | null>(null)

const job = ref<any>(null)
const file = ref<any>(null)
const viewUrl = ref<string>('')
const downloadUrl = ref<string>('')
const pollTimer = ref<any>(null)

onLoad((opts) => {
  jobId.value = Number((opts as any)?.jobId || 0) || 0
})

function pretty(v: any) {
  try {
    return JSON.stringify(v, null, 2)
  } catch {
    return String(v ?? '')
  }
}

function toAbsUrl(u: string) {
  const s = String(u || '').trim()
  if (!s) return ''
  if (s.startsWith('http://') || s.startsWith('https://')) return s
  if (s.startsWith('/')) return buildApiUrl(s)
  return buildApiUrl(`/${s}`)
}

async function reload() {
  if (!jobId.value) return
  loading.value = true
  error.value = null
  try {
    const r = await getExportJobDetail(jobId.value)
    const d = r.data || {}
    job.value = d.job
    file.value = d.file || null
    viewUrl.value = d.viewUrl ? toAbsUrl(String(d.viewUrl)) : ''
    downloadUrl.value = d.downloadUrl ? toAbsUrl(String(d.downloadUrl)) : ''
    afterJobLoaded()
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

function stopPoll() {
  if (pollTimer.value) {
    clearInterval(pollTimer.value)
    pollTimer.value = null
  }
}

function shouldPollStatus(status: any) {
  return status === 0 || status === 1
}

function afterJobLoaded() {
  const st = job.value?.status
  if (!shouldPollStatus(st)) {
    stopPoll()
    return
  }
  if (pollTimer.value) {
    return
  }
  pollTimer.value = setInterval(async () => {
    try {
      const r = await getExportJobDetail(jobId.value)
      const d = r.data || {}
      job.value = d.job
      file.value = d.file || null
      viewUrl.value = d.viewUrl ? toAbsUrl(String(d.viewUrl)) : ''
      downloadUrl.value = d.downloadUrl ? toAbsUrl(String(d.downloadUrl)) : ''
      if (!shouldPollStatus(job.value?.status)) {
        stopPoll()
      }
    } catch {
      // ignore transient errors during polling; user can 刷新
    }
  }, 2000)
}

async function download() {
  if (!hasToken() || !downloadUrl.value) return
  uni.showLoading({ title: '下载中...' })
  try {
    const dl: any = await new Promise((resolve, reject) => {
      uni.downloadFile({
        url: downloadUrl.value,
        header: buildAuthHeaders(),
        success: resolve,
        fail: reject
      })
    })
    const tempFilePath = dl?.tempFilePath
    if (!tempFilePath) throw new Error('下载失败')
    uni.saveFile({
      tempFilePath,
      success: () => uni.showToast({ title: '已保存', icon: 'success' }),
      fail: () => uni.showToast({ title: '已下载（临时文件）', icon: 'none' })
    })
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    uni.hideLoading()
  }
}

async function preview() {
  if (!hasToken() || !viewUrl.value) return
  uni.showLoading({ title: '加载预览...' })
  try {
    const dl: any = await new Promise((resolve, reject) => {
      uni.downloadFile({
        url: viewUrl.value,
        header: buildAuthHeaders(),
        success: resolve,
        fail: reject
      })
    })
    const filePath = dl?.tempFilePath
    if (!filePath) throw new Error('预览下载失败')
    // @ts-ignore
    uni.openDocument?.({
      filePath,
      showMenu: true,
      fail: () => {
        uni.showToast({ title: '无法直接预览', icon: 'none' })
      }
    })
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    uni.hideLoading()
  }
}

onMounted(() => {
  if (!ensureSystemContext()) return
  if (!jobId.value) {
    uni.showToast({ title: '缺少 jobId', icon: 'none' })
    return
  }
  reload()
})

onUnmounted(() => {
  stopPoll()
})
</script>

