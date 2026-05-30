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
import { hasId, idToString } from '@/utils/id'
import { openTempDocument } from '@/utils/document'

const nameRef = ref('')
const fileIdRef = ref('')
const opening = ref(false)
const error = ref<string | null>(null)

const name = computed(() => nameRef.value)

onLoad((opts) => {
  nameRef.value = decodeURIComponent(String((opts as any)?.name || ''))
  fileIdRef.value = idToString((opts as any)?.fileId)
})

function copyUrl() {
  error.value = null
  if (!hasToken() || !hasId(fileIdRef.value)) return
  const url = buildUploadViewUrl(fileIdRef.value)
  uni.setClipboardData({ data: url })
}

async function openPreview() {
  error.value = null
  if (!ensureSystemContext()) return
  if (!hasToken() || !hasId(fileIdRef.value)) return
  const url = buildUploadViewUrl(fileIdRef.value)

  opening.value = true
  uni.showLoading({ title: '加载预览...' })
  try {
    const filePath = await downloadUploadToTemp(url)
    openTempDocument(filePath, () => {
      error.value = '无法直接预览，已复制预览链接'
      copyUrl()
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

