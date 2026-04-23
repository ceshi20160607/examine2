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
          clickable
          @click="openActions(f)"
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

type UploadRow = { id: number; originalName?: string; fileSize?: number; contentType?: string }
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
    rows.value = json.data?.records || json.data?.rows || []
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

function buildUrl(path: string): string {
  const base = getBaseURL().replace(/\/$/, '')
  return base + path
}

function openActions(f: UploadRow) {
  if (!f?.id) return
  uni.showActionSheet({
    itemList: ['预览(view)', '下载(download)', '删除(软删)'],
    success: (res) => {
      if (res.tapIndex === 0) {
        const u = buildUrl(`/v1/system/uploads/${f.id}/view`)
        uni.navigateTo({ url: `/pages/system/upload/view?fileId=${f.id}&name=${encodeURIComponent(f.originalName || '')}` })
        return
      }
      if (res.tapIndex === 1) {
        downloadFile(f.id, f.originalName || `file-${f.id}`)
        return
      }
      if (res.tapIndex === 2) {
        deleteFile(f.id)
      }
    }
  })
}

async function downloadFile(fileId: number, filename: string) {
  if (!ensureSystemContext()) return
  const token = getToken()
  if (!token) return
  const url = buildUrl(`/v1/system/uploads/${fileId}/download`)
  uni.showLoading({ title: '下载中...' })
  try {
    const dl: any = await new Promise((resolve, reject) => {
      uni.downloadFile({
        url,
        header: { Authorization: `Bearer ${token}` },
        success: resolve,
        fail: reject
      })
    })
    const tempFilePath = dl?.tempFilePath
    if (!tempFilePath) throw new Error('下载失败')
    // App/部分平台支持保存到本地；H5 会直接下载或给 temp path
    uni.saveFile({
      tempFilePath,
      success: () => {
        uni.showToast({ title: '已保存', icon: 'success' })
      },
      fail: () => {
        uni.showToast({ title: '已下载（临时文件）', icon: 'none' })
      }
    })
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    uni.hideLoading()
  }
}

async function deleteFile(fileId: number) {
  if (!ensureSystemContext()) return
  const token = getToken()
  if (!token) return
  loading.value = true
  try {
    const r: any = await new Promise((resolve, reject) => {
      uni.request({
        url: getBaseURL().replace(/\/$/, '') + `/v1/system/uploads/${fileId}/delete`,
        method: 'POST',
        data: {},
        header: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
        success: resolve,
        fail: reject
      })
    })
    const json = r?.data
    if (!json || json.code !== 0) throw new Error(json?.message || '删除失败')
    uni.showToast({ title: '已删除', icon: 'success' })
    await loadPage()
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

