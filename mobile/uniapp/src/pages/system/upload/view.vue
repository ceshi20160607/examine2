<template>
  <view style="padding: 16px">
    <uni-card title="文件预览">
      <view style="color:#666; word-break: break-all;">{{ name }}</view>
      <view style="margin-top: 12px; display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button type="primary" :disabled="opening" @click="openPreview">打开预览</uni-button>
        <uni-button :disabled="opening" @click="copyUrl">复制预览链接</uni-button>
      </view>
      <view style="margin-top: 12px; color:#666">
        说明：会先下载到临时文件，再用系统能力打开预览。
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext } from '@/utils/guard'
import { getBaseURL } from '@/config/env'

const nameRef = ref('')
const fileIdRef = ref<number>(0)
const opening = ref(false)

const name = computed(() => nameRef.value)

onLoad((opts) => {
  nameRef.value = decodeURIComponent(String((opts as any)?.name || ''))
  fileIdRef.value = Number((opts as any)?.fileId || 0) || 0
})

function getToken(): string | null {
  const t = uni.getStorageSync('token')
  return typeof t === 'string' && t.trim() ? t.trim() : null
}

function buildUrl(path: string): string {
  const base = getBaseURL().replace(/\/$/, '')
  return base + path
}

function copyUrl() {
  const token = getToken()
  if (!token || !fileIdRef.value) return
  const url = buildUrl(`/v1/system/uploads/${fileIdRef.value}/view`)
  uni.setClipboardData({ data: url })
}

async function openPreview() {
  if (!ensureSystemContext()) return
  const token = getToken()
  if (!token || !fileIdRef.value) return
  const url = buildUrl(`/v1/system/uploads/${fileIdRef.value}/view`)

  opening.value = true
  uni.showLoading({ title: '加载预览...' })
  try {
    const dl: any = await new Promise((resolve, reject) => {
      uni.downloadFile({
        url,
        header: { Authorization: `Bearer ${token}` },
        success: resolve,
        fail: reject
      })
    })
    const filePath = dl?.tempFilePath
    if (!filePath) throw new Error('预览下载失败')
    // 尝试用系统打开
    // @ts-ignore
    uni.openDocument?.({
      filePath,
      showMenu: true,
      fail: () => {
        uni.showToast({ title: '无法直接预览，已复制链接', icon: 'none' })
        copyUrl()
      }
    })
  } catch (e: any) {
    uni.showToast({ title: e?.message ?? '预览失败', icon: 'none' })
  } finally {
    uni.hideLoading()
    opening.value = false
  }
}

ensureSystemContext()
</script>

