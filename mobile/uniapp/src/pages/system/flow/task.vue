<template>
  <view style="padding: 16px">
    <uni-card :title="`Flow Task（instanceId=${instanceId} taskId=${taskId}）`">
      <uni-forms labelPosition="top">
        <uni-forms-item label="commentText">
          <uni-easyinput v-model="commentText" placeholder="可选" />
        </uni-forms-item>
      </uni-forms>

      <view style="display:flex; gap: 8px; flex-wrap: wrap;">
        <uni-button type="primary" :disabled="acting" @click="approve">同意</uni-button>
        <uni-button type="warn" :disabled="acting" @click="reject">拒绝</uni-button>
        <uni-button :disabled="acting" @click="claim">领取</uni-button>
        <uni-button :disabled="acting" @click="unclaim">取消领取</uni-button>
      </view>

      <view style="display:flex; gap: 8px; flex-wrap: wrap; margin-top: 8px;">
        <uni-button :disabled="acting" @click="withdraw">撤回实例</uni-button>
        <uni-button :disabled="acting" @click="terminate">终止实例</uni-button>
      </view>

      <view v-if="error" style="margin-top: 12px; color:#d00">{{ error }}</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { ref } from 'vue'
import { httpPost } from '@/api/http'

const instanceId = ref<number>(0)
const taskId = ref<number>(0)
const commentText = ref<string>('')
const acting = ref(false)
const error = ref<string | null>(null)

onLoad((opts) => {
  instanceId.value = Number((opts as any)?.instanceId || 0) || 0
  taskId.value = Number((opts as any)?.taskId || 0) || 0
})

async function approve() {
  await act(`/v1/system/flow/instances/${instanceId.value}/tasks/${taskId.value}/approve`, { commentText: commentText.value || null })
}
async function reject() {
  await act(`/v1/system/flow/instances/${instanceId.value}/tasks/${taskId.value}/reject`, { commentText: commentText.value || null })
}
async function claim() {
  await act(`/v1/system/flow/instances/${instanceId.value}/tasks/${taskId.value}/claim`)
}
async function unclaim() {
  await act(`/v1/system/flow/instances/${instanceId.value}/tasks/${taskId.value}/unclaim`)
}
async function withdraw() {
  await act(`/v1/system/flow/instances/${instanceId.value}/withdraw`)
}
async function terminate() {
  await act(`/v1/system/flow/instances/${instanceId.value}/terminate`, { reason: commentText.value || null })
}

async function act(path: string, body?: any) {
  if (!instanceId.value) return
  if (path.includes('/tasks/') && !taskId.value) return
  acting.value = true
  error.value = null
  try {
    await httpPost<any>(path, body)
    uni.showToast({ title: '操作成功', icon: 'success' })
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    acting.value = false
  }
}
</script>

