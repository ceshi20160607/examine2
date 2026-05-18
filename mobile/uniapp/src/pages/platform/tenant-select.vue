<template>
  <Page :title="`选择租户`" :subtitle="systemName ? `系统：${systemName}` : ''">
    <view class="u-card u-section">
      <view class="u-subtitle">该系统已开启多租户，请选择要进入的租户</view>
      <view style="margin-top: 12px">
        <uni-list v-if="tenants.length">
          <uni-list-item
            v-for="t in tenants"
            :key="t.id"
            :title="t.tenantName || ('租户 #' + t.id)"
            clickable
            @click="pick(t)"
          />
        </uni-list>
        <EmptyState v-else text="暂无可用租户" />
      </view>
      <ErrorBlock :text="error" />
    </view>
  </Page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { listTenants, selectTenant, type PlatTenant } from '@/api/platform'
import { ensureLogin } from '@/utils/guard'
import Page from '@/ui/Page.vue'
import EmptyState from '@/ui/EmptyState.vue'
import ErrorBlock from '@/ui/ErrorBlock.vue'
import { useSessionStore } from '@/stores/session'

const systemId = ref(0)
const systemName = ref('')
const tenants = ref<PlatTenant[]>([])
const error = ref<string | null>(null)
const session = useSessionStore()

onLoad((opts) => {
  systemId.value = Number((opts as any)?.systemId || 0) || 0
  systemName.value = decodeURIComponent(String((opts as any)?.systemName || ''))
})

async function load() {
  if (!systemId.value) return
  error.value = null
  try {
    const r = await listTenants(systemId.value)
    tenants.value = r.data || []
    if (tenants.value.length === 1) {
      await pick(tenants.value[0])
    }
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  }
}

async function pick(t: PlatTenant) {
  if (!t?.id) return
  try {
    const r = await selectTenant(t.id)
    if (r?.data) session.setPayload(r.data as any)
    uni.showToast({ title: '已进入租户', icon: 'success' })
    uni.switchTab({ url: '/pages/tabs/workbench' })
  } catch (e: any) {
    error.value = e?.message ?? String(e)
  }
}

onMounted(() => {
  if (!ensureLogin()) return
  load()
})
</script>
