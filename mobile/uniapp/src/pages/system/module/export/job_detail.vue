<template>
  <view style="padding: 16px">
    <uni-card :title="`Export Job #${jobId}`">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button type="primary" :disabled="loading" @click="reload">刷新</uni-button>
        <uni-button :disabled="!downloadUrl || loading" @click="download">下载结果</uni-button>
        <uni-button :disabled="!viewUrl || loading" @click="preview">预览结果</uni-button>
      </view>
      <view v-if="error" style="margin-top: 12px; color:#d00">{{ error }}</view>
    </uni-card>

    <uni-card title="job" style="margin-top: 12px">
      <view style="font-family: monospace; white-space: pre-wrap;">{{ pretty(job) }}</view>
    </uni-card>

    <uni-card title="file" style="margin-top: 12px" v-if="file">
      <view style="font-family: monospace; white-space: pre-wrap;">{{ pretty(file) }}</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { httpGet } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'
import { getBaseURL } from '@/config/env'

const jobId = ref<number>(0)
const loading = ref(false)
const error = ref<string | null>(null)

const job = ref<any>(null)
const file = ref<any>(null)
const viewUrl = ref<string>('')
const downloadUrl = ref<string>('')

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

function getToken(): string | null {
  const t = uni.getStorageSync('token')
  return typeof t === 'string' && t.trim() ? t.trim() : null
}

function buildUrl(path: string) {
  return getBaseURL().replace(/\/$/, '') + path
}

async function reload() {
  if (!jobId.value) return
  loading.value = true
  error.value = null
  try {
    const r = await httpGet<any>(`/v1/system/module/export-jobs/${jobId.value}`)
    const d = r.data || {}
    job.value = d.job
    file.value = d.file || null
    viewUrl.value = d.viewUrl ? buildUrl(String(d.viewUrl)) : ''
    downloadUrl.value = d.downloadUrl ? buildUrl(String(d.downloadUrl)) : ''
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

async function download() {
  const token = getToken()
  if (!token || !downloadUrl.value) return
  uni.showLoading({ title: '下载中...' })
  try {
    const dl: any = await new Promise((resolve, reject) => {
      uni.downloadFile({
        url: downloadUrl.value,
        header: { Authorization: `Bearer ${token}` },
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
  const token = getToken()
  if (!token || !viewUrl.value) return
  uni.showLoading({ title: '加载预览...' })
  try {
    const dl: any = await new Promise((resolve, reject) => {
      uni.downloadFile({
        url: viewUrl.value,
        header: { Authorization: `Bearer ${token}` },
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
</script>

