<template>
  <Page :title="`Flow Task（instanceId=${instanceId} taskId=${taskId}）`" subtitle="同意/拒绝/领取/撤回/终止">
    <view class="u-card">
      <uni-forms labelPosition="top">
        <uni-forms-item label="commentText">
          <uni-easyinput v-model="commentText" placeholder="可选" />
        </uni-forms-item>
      </uni-forms>

      <ActionBar>
        <uni-button type="primary" :disabled="acting" @click="approve">同意</uni-button>
        <uni-button type="warn" :disabled="acting" @click="reject">拒绝</uni-button>
        <uni-button :disabled="acting" @click="claim">领取</uni-button>
        <uni-button :disabled="acting" @click="unclaim">取消领取</uni-button>
      </ActionBar>

      <view style="margin-top: 8px">
        <ActionBar>
        <uni-button :disabled="acting" @click="withdraw">撤回实例</uni-button>
        <uni-button :disabled="acting" @click="terminate">终止实例</uni-button>
        </ActionBar>
      </view>

      <view
        v-if="actionResultText"
        style="margin-top: 12px; font-family: monospace; white-space: pre-wrap; color:#333"
      >
        {{ actionResultText }}
      </view>
      <ErrorBlock :text="error" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onLoad } from '@dcloudio/uni-app'
import { onMounted, ref } from 'vue'
import { httpPost } from '@/api/http'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'

const instanceId = ref<number>(0)
const taskId = ref<number>(0)
const commentText = ref<string>('')
const acting = ref(false)
const error = ref<string | null>(null)
const actionResultText = ref<string>('')

onLoad((opts) => {
  instanceId.value = Number((opts as any)?.instanceId || 0) || 0
  taskId.value = Number((opts as any)?.taskId || 0) || 0
})

onMounted(() => {
  ensureSystemContext()
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
  actionResultText.value = ''
  try {
    const r = await httpPost<any>(path, body)
    uni.showToast({ title: '操作成功', icon: 'success' })
    try {
      actionResultText.value = JSON.stringify(r?.data ?? null, null, 2)
    } catch {
      actionResultText.value = String(r?.data ?? '')
    }

    const d = r?.data || {}
    const nextTaskId = Number(d.nextTaskId || 0) || 0
    const nextNodeName = String(d.nextNodeName || '').trim()
    const items: string[] = []
    if (nextTaskId) {
      items.push(`打开下一任务${nextNodeName ? `：${nextNodeName}` : ''}`)
    }
    items.push('返回待办箱')
    items.push('留在当前页')
    uni.showActionSheet({
      itemList: items,
      success: (res) => {
        const pick = items[res.tapIndex]
        if (pick?.startsWith('打开下一任务') && nextTaskId) {
          uni.redirectTo({ url: `/pages/system/flow/task?instanceId=${instanceId.value}&taskId=${nextTaskId}` })
          return
        }
        if (pick === '返回待办箱') {
          uni.redirectTo({ url: '/pages/system/flow/inbox' })
        }
      }
    })
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    acting.value = false
  }
}
</script>

