<template>
  <Page title="待办" :subtitle="subtitle">
    <view class="u-card u-section">
      <ActionBar>
        <uni-button type="primary" :disabled="loading" @click="load">刷新</uni-button>
        <uni-button @click="goSystemInbox">当前系统待办</uni-button>
        <uni-button @click="goStart">发起流程</uni-button>
      </ActionBar>
      <view style="margin-top: 12px">
        <ActionBar>
          <uni-button @click="goTemps">流程模板</uni-button>
          <uni-button @click="goTempManage">模板管理</uni-button>
          <uni-button @click="goInstances">实例列表</uni-button>
          <uni-button @click="goMyInstances">我的实例</uni-button>
        </ActionBar>
      </view>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">跨系统待办</view>
      <uni-list v-if="todos.length" style="margin-top: 12px">
        <uni-list-item
          v-for="t in todos"
          :key="`todo-${t.id}`"
          :title="taskTitle(t)"
          :note="taskNote(t)"
          clickable
          @click="openTask(t)"
        />
      </uni-list>
      <EmptyState v-else text="暂无待办" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">抄送</view>
      <ActionBar>
        <uni-button :disabled="loading" @click="toggleCcUnread">
          {{ ccOnlyUnread ? '显示全部抄送' : '仅看未读抄送' }}
        </uni-button>
      </ActionBar>
      <uni-list v-if="cc.length" style="margin-top: 12px">
        <uni-list-item
          v-for="t in cc"
          :key="`cc-${t.id}`"
          :title="taskTitle(t)"
          :note="`${t.readFlag === 1 ? '已读' : '未读'} · ${taskNote(t)}`"
          clickable
          @click="openCc(t)"
        />
      </uni-list>
      <EmptyState v-else text="暂无抄送" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">消息</view>
      <uni-list v-if="messages.length" style="margin-top: 12px">
        <uni-list-item
          v-for="m in messages"
          :key="`msg-${m.id}`"
          :title="m.title || m.content || ('消息#' + m.id)"
          :note="m.createdAt || m.createTime || ''"
        />
      </uni-list>
      <EmptyState v-else text="暂无消息" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { usePageRequest } from '@/composables/usePageRequest'
import {
  enterSystem,
  listPlatformCc,
  listPlatformMessages,
  listPlatformTodos,
  readPlatformCc,
  selectTenant,
  type PlatformFlowTask,
  type PlatformMessage
} from '@/api/platform'
import { ensureLogin, ensureSystemContext } from '@/utils/guard'
import { idToString, sameId } from '@/utils/id'

const { loading, error, run } = usePageRequest()
const messages = ref<PlatformMessage[]>([])
const todos = ref<PlatformFlowTask[]>([])
const cc = ref<PlatformFlowTask[]>([])
const ccOnlyUnread = ref(false)

const subtitle = computed(() => {
  const unreadCc = cc.value.filter((item) => item.readFlag !== 1).length
  return `待办 ${todos.value.length} 条 · 抄送 ${cc.value.length} 条 · 未读 ${unreadCc} 条`
})

function taskTitle(t: PlatformFlowTask) {
  return t.title || t.nodeName || `任务#${idToString(t.id)}`
}

function taskNote(t: PlatformFlowTask) {
  const systemId = idToString(t.systemId)
  const tenantId = idToString(t.tenantId || '0') || '0'
  const instanceId = idToString(t.instanceId || t.recordId)
  return `systemId=${systemId || '-'} tenantId=${tenantId} instanceId=${instanceId || '-'}`
}

async function load() {
  if (!ensureLogin()) return
  await run(async () => {
    const onlyUnread = ccOnlyUnread.value ? 1 : undefined
    const [mr, tr, cr] = await Promise.all([
      listPlatformMessages(50),
      listPlatformTodos(50),
      listPlatformCc(50, onlyUnread)
    ])
    messages.value = mr.data || []
    todos.value = tr.data || []
    cc.value = cr.data || []
  })
}

async function openTask(t: PlatformFlowTask) {
  const taskId = idToString(t?.id)
  const instanceId = idToString(t?.instanceId || t?.recordId)
  const systemId = idToString(t?.systemId)
  const tenantId = idToString(t?.tenantId || '0') || '0'
  if (!taskId || !instanceId) {
    uni.showToast({ title: '任务缺少必要标识', icon: 'none' })
    return
  }
  await run(async () => {
    if (systemId) {
      await enterSystem(systemId)
      if (tenantId && !sameId(tenantId, '0')) {
        await selectTenant(tenantId)
      }
    }
    uni.navigateTo({
      url: `/pages/system/flow/task?instanceId=${encodeURIComponent(instanceId)}&taskId=${encodeURIComponent(taskId)}`
    })
  })
}

async function openCc(t: PlatformFlowTask) {
  if (t.readFlag !== 1 && t.id) {
    await run(async () => {
      await readPlatformCc(t.id)
      t.readFlag = 1
    })
  }
  await openTask(t)
}

async function toggleCcUnread() {
  ccOnlyUnread.value = !ccOnlyUnread.value
  await load()
}

function goSystemInbox() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/inbox' })
}

function goTemps() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/temps' })
}

function goTempManage() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/temp_list' })
}

function goStart() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/start' })
}

function goInstances() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/instances' })
}

function goMyInstances() {
  if (!ensureSystemContext()) return
  uni.navigateTo({ url: '/pages/system/flow/my_instances' })
}

onMounted(load)
</script>
