<template>
  <Page title="我的系统" subtitle="创建并进入系统后开始配置应用与流程">
    <view class="u-card u-section">
      <view style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
        <uni-easyinput v-model="newSystemName" placeholder="输入系统名称" style="flex: 1; min-width: 220px" />
        <ActionBar>
          <uni-button type="primary" :disabled="creating" @click="createSystem">创建</uni-button>
          <uni-button :disabled="loading" @click="load">刷新</uni-button>
        </ActionBar>
      </view>
    </view>

    <view class="u-card u-section">
      <view class="u-title">系统列表</view>
      <view class="u-subtitle">点击进入系统</view>
      <view style="margin-top: 12px">
        <uni-list v-if="systems.length">
          <uni-list-item
            v-for="s in systems"
            :key="s.id"
            :title="s.name || ('系统 #' + s.id)"
            :note="s.ownerPlatAccountId ? ('owner=' + s.ownerPlatAccountId) : ''"
            clickable
            @click="enterSystem(s)"
          />
        </uni-list>
        <view v-else style="color: var(--u-text-muted)">暂无系统</view>
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listMySystems, createSystem as apiCreateSystem, enterSystem as apiEnterSystem } from '@/api/platform'
import { ensureLogin } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import { useSessionStore } from '@/stores/session'

type PlatSystem = {
  id: number
  name?: string
  ownerPlatAccountId?: number
}

const systems = ref<PlatSystem[]>([])
const loading = ref(false)
const creating = ref(false)
const newSystemName = ref('')
const session = useSessionStore()

async function load() {
  loading.value = true
  try {
    const r = await listMySystems()
    systems.value = r.data || []
  } finally {
    loading.value = false
  }
}

async function createSystem() {
  const name = newSystemName.value.trim()
  if (!name) {
    uni.showToast({ title: '请输入系统名称', icon: 'none' })
    return
  }
  creating.value = true
  try {
    await apiCreateSystem(name, 0)
    newSystemName.value = ''
    await load()
  } finally {
    creating.value = false
  }
}

async function enterSystem(s: PlatSystem) {
  if (!s?.id) return
  const r = await apiEnterSystem(s.id)
  if (r?.data) {
    session.setPayload(r.data as any)
  }
  uni.showToast({ title: `已进入系统: ${s.name || s.id}`, icon: 'success' })
  // 下一步：进入 module 元数据
  uni.reLaunch({ url: '/pages/system/module/meta/apps' })
}

onMounted(() => {
  if (!ensureLogin()) return
  load()
})
</script>

