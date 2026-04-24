<template>
  <Page title="上传文件" subtitle="支持上传、预览、下载与删除">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="uploading" @click="chooseAndUpload">选择并上传</uni-button>
        <uni-button :disabled="loading" @click="loadPage">刷新列表</uni-button>
      </ActionBar>

      <view v-if="lastFileId" class="u-subtitle">lastFileId: {{ lastFileId }}</view>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">文件列表（page）</view>
      <view style="margin-top: 12px">
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
        <EmptyState v-else text="暂无文件" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { buildApiUrl, buildAuthHeaders, httpGet, httpPost } from '@/api/http'
import { ensureSystemContext, hasToken } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'

const uploading = ref(false)
const loading = ref(false)
const error = ref<string | null>(null)
const lastFileId = ref<number | null>(null)

type UploadRow = { id: number; originalName?: string; fileSize?: number; contentType?: string }
const rows = ref<UploadRow[]>([])

async function chooseAndUpload() {
  error.value = null
  if (!ensureSystemContext()) return
  if (!hasToken()) return

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
        url: buildApiUrl('/v1/system/uploads'),
        filePath,
        name: 'file',
        header: buildAuthHeaders(),
        success: resolve,
        fail: reject
      })
    })

    const raw = uploadRes?.data
    let json: any
    if (typeof raw === 'string') {
      try {
        json = JSON.parse(raw)
      } catch {
        throw new Error('上传响应不是 JSON（请检查网关/地址配置）')
      }
    } else {
      json = raw
    }
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
    const r = await httpGet<any>('/v1/system/uploads/page?page=1&size=20')
    const d: any = r.data || {}
    rows.value = d.records || d.rows || []
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    loading.value = false
  }
}

function buildUrl(path: string): string {
  return buildApiUrl(path)
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
  if (!hasToken()) return
  const url = buildUrl(`/v1/system/uploads/${fileId}/download`)
  uni.showLoading({ title: '下载中...' })
  try {
    const dl: any = await new Promise((resolve, reject) => {
      uni.downloadFile({
        url,
        header: buildAuthHeaders(),
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
  if (!hasToken()) return
  loading.value = true
  try {
    await httpPost(`/v1/system/uploads/${fileId}/delete`, {})
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

