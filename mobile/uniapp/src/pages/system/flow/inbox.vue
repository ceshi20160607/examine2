<template>
  <Page title="待办 Inbox" subtitle="我的待办 + 抄送">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="load">刷新</uni-button>
        <uni-button :disabled="loading" @click="toggleCcUnread">
          {{ ccOnlyUnread ? '显示全部抄送' : '仅看未读抄送' }}
        </uni-button>
      </ActionBar>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">我的待办</view>
      <view style="margin-top: 12px">
        <uni-list v-if="pending.length">
          <uni-list-item
            v-for="t in pending"
            :key="idToString(t.taskId || t.id)"
            :title="taskTitle(t)"
            :note="taskNote(t)"
            clickable
            @click="goTask(t)"
          />
        </uni-list>
        <EmptyState v-else text="暂无待办" />
      </view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">我的抄送</view>
      <view style="margin-top: 12px">
        <uni-list v-if="cc.length">
          <uni-list-item
            v-for="t in cc"
            :key="idToString(t.taskId || t.id)"
            :title="taskTitle(t)"
            :note="`${t.readFlag === 1 ? '已读' : '未读'} · ${taskNote(t)}`"
            clickable
            @click="openCc(t)"
          />
        </uni-list>
        <EmptyState v-else text="暂无抄送" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ensureSystemContext } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import { inboxCc, inboxPending, readInboxCc, type FlowTask } from '@/api/flow'
import { hasId, idToString } from '@/utils/id'

const { loading, error, run } = usePageRequest()
const pending = ref<FlowTask[]>([])
const cc = ref<FlowTask[]>([])
const ccOnlyUnread = ref(false)

function taskTitle(t: FlowTask) {
  return t.title || t.instanceTitle || t.nodeName || `Task#${idToString(t.taskId || t.id)}`
}

function taskInstanceId(t: FlowTask) {
  return idToString(t.instanceId || t.recordId)
}

function taskNote(t: FlowTask) {
  const instanceId = taskInstanceId(t)
  const taskId = idToString(t.taskId || t.id)
  return `instanceId=${instanceId || '-'} taskId=${taskId || '-'}`
}

async function load() {
  await run(async () => {
    const onlyUnread = ccOnlyUnread.value ? 1 : undefined
    const [pendingRes, ccRes] = await Promise.all([inboxPending(50), inboxCc(50, onlyUnread)])
    pending.value = pendingRes.data || []
    cc.value = ccRes.data || []
  })
}

async function loadCc() {
  await run(async () => {
    const onlyUnread = ccOnlyUnread.value ? 1 : undefined
    const r = await inboxCc(50, onlyUnread)
    cc.value = r.data || []
  })
}

function goTask(t: FlowTask) {
  const taskId = idToString(t?.taskId || t?.id)
  const instanceId = taskInstanceId(t)
  if (!taskId || !instanceId) return
  uni.navigateTo({ url: `/pages/system/flow/task?instanceId=${encodeURIComponent(instanceId)}&taskId=${encodeURIComponent(taskId)}` })
}

async function openCc(t: FlowTask) {
  const taskId = idToString(t?.taskId || t?.id)
  if (t.readFlag !== 1 && hasId(taskId)) {
    await run(async () => {
      await readInboxCc(taskId)
      t.readFlag = 1
    })
  }
  const instanceId = taskInstanceId(t)
  if (hasId(instanceId)) {
    uni.navigateTo({ url: `/pages/system/flow/instance?id=${encodeURIComponent(instanceId)}` })
  }
}

async function toggleCcUnread() {
  ccOnlyUnread.value = !ccOnlyUnread.value
  await loadCc()
}

onMounted(() => {
  if (!ensureSystemContext()) return
  load()
})
</script>

