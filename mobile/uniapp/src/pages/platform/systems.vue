<template>
  <Page title="我的系统" subtitle="创建并进入系统后开始配置应用与流程">
    <view class="u-card u-section">
      <view class="create-row">
        <uni-easyinput v-model="newSystemName" placeholder="输入系统名称" class="system-name" />
        <label class="tenant-check">
          <checkbox :checked="newMultiTenant" @click="newMultiTenant = !newMultiTenant" />
          <text>多租户</text>
        </label>
        <ActionBar>
          <uni-button type="primary" :disabled="creating" @click="createSystem">创建</uni-button>
          <uni-button :disabled="loading" @click="load">刷新</uni-button>
        </ActionBar>
      </view>
      <ErrorBlock :text="error" />
    </view>

    <view class="u-card u-section">
      <view class="u-title">系统列表</view>
      <view class="u-subtitle">启用状态的系统可直接进入；停用后入口保留，便于重新启用。</view>
      <view class="system-list">
        <view v-if="systems.length">
          <view v-for="s in systems" :key="s.id" class="system-item">
            <view class="system-main" @click="enterSystem(s)">
              <view class="system-title">{{ s.name || ('系统 #' + s.id) }}</view>
              <view class="u-subtitle">
                id={{ s.id }} · {{ s.multiTenantEnabled === 1 ? '多租户' : '单租户' }} · {{ statusText(s.status) }}
              </view>
              <view v-if="s.ownerPlatAccountId" class="u-subtitle">owner={{ s.ownerPlatAccountId }}</view>
            </view>
            <ActionBar>
              <uni-button size="mini" :disabled="s.status === 1" @click="setStatus(s, 1)">启用</uni-button>
              <uni-button size="mini" :disabled="s.status === 2" @click="setStatus(s, 2)">停用</uni-button>
              <uni-button size="mini" type="warn" @click="removeSystem(s)">删除</uni-button>
            </ActionBar>
          </view>
        </view>
        <EmptyState v-else text="暂无系统，请先创建" />
      </view>
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  createSystem as apiCreateSystem,
  deleteSystem as apiDeleteSystem,
  enterSystem as apiEnterSystem,
  listMySystems,
  setSystemStatus,
  type PlatSystem
} from '@/api/platform'
import { ensureLogin } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import ActionBar from '@/ui/ActionBar.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { useSessionStore } from '@/stores/session'
import { idToString } from '@/utils/id'

const systems = ref<PlatSystem[]>([])
const loading = ref(false)
const creating = ref(false)
const newSystemName = ref('')
const newMultiTenant = ref(false)
const error = ref<string | null>(null)
const session = useSessionStore()

function statusText(status?: number) {
  if (status === 1) return '启用'
  if (status === 2) return '停用'
  return `status=${status ?? '-'}`
}

async function load() {
  loading.value = true
  error.value = null
  try {
    const r = await listMySystems()
    systems.value = r.data || []
  } catch (e: any) {
    error.value = e?.message ?? String(e)
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
  error.value = null
  try {
    await apiCreateSystem(name, newMultiTenant.value ? 1 : 0)
    newSystemName.value = ''
    newMultiTenant.value = false
    uni.showToast({ title: '已创建', icon: 'success' })
    await load()
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  } finally {
    creating.value = false
  }
}

async function enterSystem(s: PlatSystem) {
  if (!s?.id) return
  if (s.status !== 1) {
    uni.showToast({ title: '系统已停用，请先启用', icon: 'none' })
    return
  }
  error.value = null
  try {
    const r = await apiEnterSystem(s.id)
    if (r?.data) {
      session.setPayload(r.data as any)
    }
    if (s.multiTenantEnabled === 1) {
      uni.navigateTo({
        url: `/pages/platform/tenant-select?systemId=${encodeURIComponent(idToString(s.id))}&systemName=${encodeURIComponent(s.name || '')}`
      })
      return
    }
    uni.showToast({ title: `已进入系统: ${s.name || s.id}`, icon: 'success' })
    uni.switchTab({ url: '/pages/tabs/workbench' })
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  }
}

function setStatus(s: PlatSystem, status: 1 | 2) {
  if (!s?.id) return
  uni.showModal({
    title: `确认${status === 1 ? '启用' : '停用'}？`,
    content: s.name || idToString(s.id),
    success: async (m) => {
      if (!m.confirm) return
      try {
        await setSystemStatus(s.id, status)
        uni.showToast({ title: `已${status === 1 ? '启用' : '停用'}`, icon: 'success' })
        await load()
      } catch (e: any) {
        error.value = e?.message ?? String(e)
      }
    }
  })
}

function removeSystem(s: PlatSystem) {
  if (!s?.id) return
  uni.showModal({
    title: '确认删除？',
    content: `${s.name || s.id} 将被停用，不再可进入。`,
    success: async (m) => {
      if (!m.confirm) return
      try {
        await apiDeleteSystem(s.id)
        uni.showToast({ title: '已删除', icon: 'success' })
        await load()
      } catch (e: any) {
        error.value = e?.message ?? String(e)
      }
    }
  })
}

onMounted(() => {
  if (!ensureLogin()) return
  load()
})
</script>

<style scoped>
.create-row {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.system-name {
  flex: 1;
  min-width: 220px;
}
.tenant-check {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #64748b;
  font-size: 13px;
}
.system-list {
  margin-top: 12px;
}
.system-item {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  background: #fff;
}
.system-main {
  margin-bottom: 10px;
}
.system-title {
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 4px;
}
</style>
