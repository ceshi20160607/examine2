<template>
  <view style="padding: 16px">
    <uni-card title="我的系统">
      <view style="display: flex; gap: 8px">
        <uni-easyinput v-model="newSystemName" placeholder="输入系统名称" />
        <uni-button type="primary" :disabled="creating" @click="createSystem">创建</uni-button>
      </view>
    </uni-card>

    <uni-card title="系统列表" style="margin-top: 12px">
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
      <view v-else style="color: #666">暂无系统</view>

      <view style="margin-top: 12px">
        <uni-button :disabled="loading" @click="load">刷新</uni-button>
      </view>
    </uni-card>
  </view>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { listMySystems, createSystem as apiCreateSystem, enterSystem as apiEnterSystem } from '@/api/platform'
import { ensureLogin } from '@/utils/guard'

type PlatSystem = {
  id: number
  name?: string
  ownerPlatAccountId?: number
}

const systems = ref<PlatSystem[]>([])
const loading = ref(false)
const creating = ref(false)
const newSystemName = ref('')

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
  await apiEnterSystem(s.id)
  uni.showToast({ title: `已进入系统: ${s.name || s.id}`, icon: 'success' })
  // 下一步：进入 module 元数据
  uni.reLaunch({ url: '/pages/system/module/meta/apps' })
}

onMounted(() => {
  if (!ensureLogin()) return
  load()
})
</script>

