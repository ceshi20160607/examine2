<script setup lang="ts">
import { ExclamationCircleOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons-vue'
import { onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api'
import { platformContext, platformTokens, setSystemSession, systemContext, systemTokens, type CurrentContext, type SessionTokens } from '../session'
import type { SystemSettings, SystemTenant } from '../types'

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

const emit = defineEmits<{ contextChanged: [] }>()
const settings = ref<SystemSettings | null>(null)
const tenants = ref<SystemTenant[]>([])
const loading = ref(false)
const saving = ref(false)
const switching = ref<number | null>(null)
const error = ref('')
const success = ref('')
const tenantDrawer = ref(false)
const form = reactive({ name: '', tenantMode: 'SINGLE' as 'SINGLE' | 'MULTI' })
const tenantForm = reactive({ code: '', name: '' })

function showError(reason: unknown, fallback: string) {
  error.value = reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（追踪号：${reason.traceId}）` : ''}` : fallback
}

async function load() {
  if (!systemTokens.value?.accessToken) return
  loading.value = true
  error.value = ''
  try {
    const [systemSettings, systemTenants] = await Promise.all([
      api<SystemSettings>('/api/admin/system/settings', {}, systemTokens.value.accessToken),
      api<SystemTenant[]>('/api/admin/system/tenants', {}, systemTokens.value.accessToken),
    ])
    settings.value = systemSettings
    tenants.value = systemTenants
    form.name = systemSettings.name
    form.tenantMode = systemSettings.tenantMode
  } catch (reason) {
    showError(reason, '系统设置加载失败')
  } finally {
    loading.value = false
  }
}

async function saveSettings() {
  if (!systemTokens.value?.accessToken || !form.name.trim()) return
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    const result = await api<SystemSettings>('/api/admin/system/settings', {
      method: 'PUT', body: JSON.stringify({ name: form.name.trim(), tenantMode: form.tenantMode }),
    }, systemTokens.value.accessToken)
    settings.value = result
    if (systemContext.value && systemTokens.value) {
      setSystemSession(systemTokens.value, { ...systemContext.value, systemName: result.name })
    }
    success.value = result.tenantMode === 'MULTI' ? '系统设置已保存，可以创建并切换租户。' : '系统设置已保存。'
    await load()
  } catch (reason) {
    showError(reason, '系统设置保存失败')
  } finally {
    saving.value = false
  }
}

async function createTenant() {
  if (!systemTokens.value?.accessToken || !tenantForm.code.trim() || !tenantForm.name.trim()) return
  saving.value = true
  error.value = ''
  try {
    await api<SystemTenant>('/api/admin/system/tenants', {
      method: 'POST', body: JSON.stringify({ code: tenantForm.code.trim(), name: tenantForm.name.trim() }),
    }, systemTokens.value.accessToken)
    tenantDrawer.value = false
    tenantForm.code = ''
    tenantForm.name = ''
    success.value = '租户已创建，创建人已成为该租户管理员。'
    await load()
  } catch (reason) {
    showError(reason, '租户创建失败')
  } finally {
    saving.value = false
  }
}

async function changeTenantStatus(tenant: SystemTenant) {
  if (!systemTokens.value?.accessToken) return
  saving.value = true
  error.value = ''
  try {
    await api<SystemTenant>(`/api/admin/system/tenants/${tenant.id}/status`, {
      method: 'PUT', body: JSON.stringify({ status: tenant.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE' }),
    }, systemTokens.value.accessToken)
    success.value = tenant.status === 'ACTIVE' ? '租户已停用，已有租户会话已失效。' : '租户已启用。'
    await load()
  } catch (reason) {
    showError(reason, '租户状态修改失败')
  } finally {
    saving.value = false
  }
}

async function switchTenant(tenant: SystemTenant) {
  if (!platformTokens.value?.accessToken || !platformContext.value || !settings.value) return
  switching.value = tenant.id
  error.value = ''
  try {
    const entry = await api<EntryResult>(`/api/systems/${settings.value.systemId}/tenants/${tenant.id}/enter`, { method: 'POST' }, platformTokens.value.accessToken)
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
    success.value = `已切换到${entry.tenantName}`
    emit('contextChanged')
    await load()
  } catch (reason) {
    showError(reason, '租户切换失败')
  } finally {
    switching.value = null
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-heading">
      <div><p class="eyebrow">后台配置</p><h1>系统与租户</h1><p>维护系统基础信息；多租户模式下，业务数据和成员权限按租户隔离。</p></div>
      <a-button :loading="loading" @click="load"><ReloadOutlined />刷新</a-button>
    </div>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-alert v-if="success" type="success" show-icon :message="success" class="section-alert" closable @close="success = ''" />
    <a-spin :spinning="loading">
      <div class="settings-grid">
        <section class="panel-card settings-card">
          <div class="panel-title"><strong>系统基础信息</strong><span>{{ settings?.code }}</span></div>
          <a-form layout="vertical" class="settings-form">
            <a-form-item label="系统名称" required><a-input v-model:value="form.name" :maxlength="200" /></a-form-item>
            <a-form-item label="租户模式" required>
              <a-radio-group v-model:value="form.tenantMode">
                <a-radio value="SINGLE">单租户</a-radio><a-radio value="MULTI">多租户</a-radio>
              </a-radio-group>
            </a-form-item>
            <a-alert v-if="form.tenantMode !== settings?.tenantMode" type="warning" show-icon class="mode-warning">
              <template #icon><ExclamationCircleOutlined /></template>
              <template #message>{{ form.tenantMode === 'MULTI' ? '启用后可创建其他租户，原默认租户继续作为主租户。' : '存在其他租户时不能退回单租户。' }}</template>
            </a-alert>
            <a-button type="primary" :loading="saving" @click="saveSettings">保存设置</a-button>
          </a-form>
        </section>
        <section class="panel-card tenant-card">
          <div class="panel-title"><strong>租户</strong><a-button v-if="settings?.tenantMode === 'MULTI'" type="primary" size="small" @click="tenantDrawer = true"><PlusOutlined />新建租户</a-button></div>
          <a-alert v-if="settings?.tenantMode === 'SINGLE'" type="info" show-icon message="当前为单租户模式，不显示租户切换入口。" class="tenant-hint" />
          <div v-for="tenant in tenants" :key="tenant.id" class="tenant-row">
            <div><strong>{{ tenant.name }}</strong><small>{{ tenant.code }}</small></div>
            <div class="tenant-row__state"><a-tag v-if="tenant.main" color="blue">主租户</a-tag><a-tag :color="tenant.status === 'ACTIVE' ? 'green' : 'default'">{{ tenant.status === 'ACTIVE' ? '启用' : '停用' }}</a-tag></div>
            <div class="tenant-row__actions">
              <a-button v-if="settings?.tenantMode === 'MULTI' && tenant.status === 'ACTIVE' && !tenant.current" size="small" :loading="switching === tenant.id" @click="switchTenant(tenant)">切换进入</a-button>
              <a-tag v-if="tenant.current" color="processing">当前</a-tag>
              <a-button v-if="!tenant.main" size="small" danger="" @click="changeTenantStatus(tenant)">{{ tenant.status === 'ACTIVE' ? '停用' : '启用' }}</a-button>
            </div>
          </div>
        </section>
      </div>
    </a-spin>
    <a-drawer v-model:open="tenantDrawer" title="新建租户" width="430">
      <a-form layout="vertical">
        <a-form-item label="租户名称" required><a-input v-model:value="tenantForm.name" placeholder="例如：华东分公司" /></a-form-item>
        <a-form-item label="租户编码" required extra="小写字母开头，可使用数字、下划线和短横线"><a-input v-model:value="tenantForm.code" placeholder="east_branch" /></a-form-item>
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="tenantDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="createTenant">创建租户</a-button></div></template>
    </a-drawer>
  </div>
</template>
