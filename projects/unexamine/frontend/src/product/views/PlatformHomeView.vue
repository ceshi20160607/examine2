<script setup lang="ts">
import { AppstoreOutlined, ArrowRightOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, ApiError } from '../api'
import PlatformShell from '../components/PlatformShell.vue'
import { clearSession, platformContext, platformTokens, setSystemSession, type CurrentContext, type SessionTokens } from '../session'

interface AccessibleSystem {
  systemId: number
  systemCode: string
  systemName: string
  tenantMode: string
  status: string
  defaultTenantId: number
  defaultTenantName: string
}

interface EntryResult {
  systemId: number
  systemCode: string
  systemName: string
  tenantMode: string
  tenantId: number
  tenantName: string
  systemMemberId: number
  roleIds: number[]
  permissions: CurrentContext['permissions']
  dataScopes: CurrentContext['dataScopes']
  tokens: SessionTokens
}

const router = useRouter()
const systems = ref<AccessibleSystem[]>([])
const loading = ref(false)
const entering = ref<number | null>(null)
const error = ref('')

async function loadSystems() {
  if (!platformTokens.value?.accessToken) {
    clearSession()
    await router.replace('/login')
    return
  }
  loading.value = true
  error.value = ''
  try {
    systems.value = await api<AccessibleSystem[]>('/api/systems', {}, platformTokens.value.accessToken)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '系统列表加载失败'
  } finally {
    loading.value = false
  }
}

async function enterSystem(system: AccessibleSystem) {
  if (!platformTokens.value?.accessToken || !platformContext.value) return
  entering.value = system.systemId
  try {
    const entry = await api<EntryResult>(`/api/systems/${system.systemId}/enter`, { method: 'POST' }, platformTokens.value.accessToken)
    setSystemSession(entry.tokens, {
      accountId: platformContext.value.accountId,
      systemId: entry.systemId,
      tenantId: entry.tenantId,
      memberId: entry.systemMemberId,
      username: platformContext.value.username,
      displayName: platformContext.value.displayName,
      systemName: entry.systemName,
      tenantName: entry.tenantName,
      roleIds: entry.roleIds,
      permissions: entry.permissions,
      dataScopes: entry.dataScopes,
    })
    await router.push(`/systems/${system.systemId}`)
  } catch (reason) {
    error.value = reason instanceof ApiError ? reason.message : '无法进入系统'
  } finally {
    entering.value = null
  }
}

onMounted(loadSystems)
</script>

<template>
  <PlatformShell>
    <div class="page-heading">
      <div><p class="eyebrow">平台工作区</p><h1>我的系统</h1><p>选择一个系统，进入它独立的成员、权限和业务数据范围。</p></div>
      <a-button :loading="loading" @click="loadSystems"><ReloadOutlined />刷新</a-button>
    </div>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-spin :spinning="loading">
      <div v-if="systems.length" class="system-list">
        <article v-for="system in systems" :key="system.systemId" class="system-row" @click="enterSystem(system)">
          <div class="system-row__mark"><AppstoreOutlined /></div>
          <div class="system-row__main"><h2>{{ system.systemName }}</h2><p>{{ system.systemCode }}</p></div>
          <div class="system-row__meta"><span>{{ system.defaultTenantName }}</span><a-tag color="green">有效</a-tag></div>
          <a-button type="primary" ghost :loading="entering === system.systemId" @click.stop="enterSystem(system)">进入<ArrowRightOutlined /></a-button>
        </article>
      </div>
      <a-empty v-else-if="!loading" description="当前账号还没有可进入的系统" />
    </a-spin>
  </PlatformShell>
</template>
