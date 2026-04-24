<template>
  <Page title="文件预览" subtitle="先下载到临时文件，再调用系统预览能力">
    <view class="u-card">
      <view style="color: var(--u-text-muted); word-break: break-all;">{{ name }}</view>
      <ActionBar>
        <uni-button type="primary" :disabled="opening" @click="openPreview">打开预览</uni-button>
        <uni-button :disabled="opening" @click="copyUrl">复制预览链接</uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { ensureSystemContext, hasToken } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { buildUploadViewUrl, downloadUploadToTemp } from '@/api/upload'

const nameRef = ref('')
const fileIdRef = ref<number>(0)
const opening = ref(false)
const error = ref<string | null>(null)

const name = computed(() => nameRef.value)

onLoad((opts) => {
  nameRef.value = decodeURIComponent(String((opts as any)?.name || ''))
  fileIdRef.value = Number((opts as any)?.fileId || 0) || 0
})

function copyUrl() {
  error.value = null
  if (!hasToken() || !fileIdRef.value) return
  const url = buildUploadViewUrl(fileIdRef.value)
  uni.setClipboardData({ data: url })
}

async function openPreview() {
  error.value = null
  if (!ensureSystemContext()) return
  if (!hasToken() || !fileIdRef.value) return
  const url = buildUploadViewUrl(fileIdRef.value)

  opening.value = true
  uni.showLoading({ title: '加载预览...' })
  try {
    const filePath = await downloadUploadToTemp(url)
    // 尝试用系统打开
    // @ts-ignore
    uni.openDocument?.({
      filePath,
      showMenu: true,
      fail: () => {
        error.value = '无法直接预览，已复制预览链接'
        copyUrl()
      }
    })
  } catch (e: any) {
    error.value = e?.message ?? '预览失败'
  } finally {
    uni.hideLoading()
    opening.value = false
  }
}

onMounted(() => {
  ensureSystemContext()
})
</script>

