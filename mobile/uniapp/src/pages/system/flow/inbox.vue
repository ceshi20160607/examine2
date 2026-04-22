<template>
  <view style="padding: 16px">
    <uni-card title="待办 Inbox">
      <view style="display:flex; gap: 8px;">
        <uni-button type="primary" :disabled="loading" @click="loadPending">刷新待办</uni-button>
        <uni-button :disabled="loading" @click="loadCc">刷新抄送</uni-button>
      </view>
    </uni-card>

    <uni-card title="我的待办" style="margin-top: 12px">
      <uni-list v-if="pending.length">
        <uni-list-item
          v-for="t in pending"
          :key="t.id"
          :title="t.nodeName || ('Task#' + t.id)"
          :note="`instanceId=${t.recordId} taskId=${t.id}`"
          clickable
          @click="goTask(t)"
        />
      </uni-list>
      <view v-else style="color:#666">暂无待办</view>
    </uni-card>

    <uni-card title="我的抄送" style="margin-top: 12px">
      <uni-list v-if="cc.length">
        <uni-list-item
          v-for="t in cc"
          :key="t.id"
          :title="t.nodeName || ('CC#' + t.id)"
          :note="`instanceId=${t.recordId} taskId=${t.id}`"
        />
      </uni-list>
      <view v-else style="color:#666">暂无抄送</view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { httpGet } from '@/api/http'

type FlowTask = { id: number; recordId?: number; nodeName?: string }

const loading = ref(false)
const pending = ref<FlowTask[]>([])
const cc = ref<FlowTask[]>([])

async function loadPending() {
  loading.value = true
  try {
    const r = await httpGet<FlowTask[]>('/v1/system/flow/inbox/tasks/pending?limit=50')
    pending.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function loadCc() {
  loading.value = true
  try {
    const r = await httpGet<FlowTask[]>('/v1/system/flow/inbox/cc?limit=50')
    cc.value = r.data || []
  } finally {
    loading.value = false
  }
}

function goTask(t: FlowTask) {
  if (!t?.id || !t?.recordId) return
  uni.navigateTo({ url: `/pages/system/flow/task?instanceId=${t.recordId}&taskId=${t.id}` })
}
</script>

