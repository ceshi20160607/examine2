<template>
  <view style="padding: 16px">
    <uni-card title="上传文件">
      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button type="primary" :disabled="uploading" @click="chooseAndUpload">选择并上传</uni-button>
        <uni-button :disabled="loading" @click="loadPage">刷新列表</uni-button>
      </view>

      <view v-if="lastFileId" style="margin-top: 12px">
        <view>lastFileId: {{ lastFileId }}</view>
      </view>
      <view v-if="error" style="margin-top: 12px; color:#d00">{{ error }}</view>
    </uni-card>

    <uni-card title="文件列表（page）" style="margin-top: 12px">
      <uni-list v-if="rows.length">
        <uni-list-item
          v-for="f in rows"
          :key="f.id"
          :title="f.originalName || ('File#' + f.id)"
          :note="`${f.id} / ${f.fileSize || 0} bytes`"
        />
      </uni-list>
      <view v-else style="color:#666">暂无文件</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { getBaseURL } from '@/config/env'
import { ensureSystemContext, hasToken } from '@/utils/guard'

const uploading = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)
const lastFileId = ref<number | null>(null)

type UploadRow = { id: number; originalName?: string; fileSize?: number }
const rows = ref<UploadRow[]>([])

function getToken(): string | null {
  const t = uni.getStorageSync('token')
  return typeof t === 'string' && t.trim() ? t.trim() : null
}

async function chooseAndUpload() {
  error.value = null
  if (!ensureSystemContext()) return
  const token = getToken()
  if (!token) return

  // H5/小程序/App 兼容：先用 chooseFile（H5）/chooseImage 等后续再增强
  // 这里用 chooseFile（新版本 uni 支持），若平台不支持会 fail 并提示
  uploading.value = true
  try {
    const chooseRes: any = await new Promise((resolve, reject) => {
      ;(uni as any).chooseFile({
        count: 1,
        success: resolve,
        fail: reject
      })
    })
    const filePath = chooseRes?.tempFilePaths?.[0]
    if (!filePath) {
      throw new Error('未选择文件')
    }

    const uploadRes: any = await new Promise((resolve, reject) => {
      uni.uploadFile({
        url: getBaseURL().replace(/\/$/, '') + '/v1/system/uploads',
        filePath,
        name: 'file',
        header: {
          Authorization: `Bearer ${token}`
        },
        success: resolve,
        fail: reject
      })
    })

    const json = typeof uploadRes?.data === 'string' ? JSON.parse(uploadRes.data) : uploadRes.data
    if (!json || json.code !== 0) {
      throw new Error(json?.message || '上传失败')
    }
    lastFileId.value = json.data?.fileId ?? null
    uni.showToast({ title: '上传成功', icon: 'success' })
    await loadPage()
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    uploading.value = false
  }
}

async function loadPage() {
  if (!ensureSystemContext()) return
  loading.value = true
  error.value = null
  try {
    const r: any = await new Promise((resolve, reject) => {
      const token = getToken()
      uni.request({
        url: getBaseURL().replace(/\/$/, '') + '/v1/system/uploads/page?page=1&size=20',
        method: 'GET',
        header: token ? { Authorization: `Bearer ${token}` } : {},
        success: resolve,
        fail: reject
      })
    })
    const json = r?.data
    if (!json || json.code !== 0) {
      throw new Error(json?.message || '加载失败')
    }
    rows.value = json.data?.rows || []
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (!hasToken()) {
    uni.reLaunch({ url: '/pages/auth/login' })
    return
  }
  loadPage()
})
</script>

