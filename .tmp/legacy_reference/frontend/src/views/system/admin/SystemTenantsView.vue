<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { Archive, Pencil, Plus, Power, RefreshCw, Settings2 } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import TenantLifecycleDrawer from '@/components/admin/TenantLifecycleDrawer.vue'
import { useAdminViewport } from '@/composables/useAdminViewport'
import { ApiRequestError } from '@/services/api'
import { systemAdminApi } from '@/services/admin'
import { useSessionStore } from '@/stores/session'
import type { Tenant } from '@/types/admin'

const { isMobile } = useAdminViewport()
const route = useRoute()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const tenants = ref<Tenant[]>([])
const total = ref(0)
const editorOpen = ref(false)
const commandOpen = ref(false)
const lifecycleOpen = ref(false)
const lifecycleTenant = ref<Tenant | null>(null)
const command = ref<'activate' | 'disable' | 'archive' | 'restore'>('disable')
const form = reactive({ id: '', code: '', name: '', version: '' })
const commandForm = reactive({ id: '', name: '', reason: '', version: '' })
const columns: TableColumnsType = [
  { title: '租户', key: 'tenant', width: 250 },
  { title: '成员数', dataIndex: 'memberCount', width: 100 },
  { title: '默认租户', dataIndex: 'isDefault', width: 110 },
  { title: '状态', dataIndex: 'status', width: 110 },
  { title: '操作', key: 'actions', width: 330 },
]

function reportError(error: unknown) {
  errorMessage.value = error instanceof ApiRequestError ? error.message : '租户数据加载失败'
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const result = await systemAdminApi.listTenants(systemId.value, { page: 1, size: 100 })
    tenants.value = result.items
    total.value = result.total
  } catch (error) {
    reportError(error)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: '', code: '', name: '', version: '' })
  editorOpen.value = true
}

function openEdit(tenant: Tenant) {
  Object.assign(form, tenant)
  editorOpen.value = true
}

async function save() {
  if (!form.name.trim() || (!form.id && !form.code.trim())) return
  saving.value = true
  try {
    if (form.id) await systemAdminApi.updateTenant(systemId.value, form.id, { name: form.name, version: form.version })
    else await systemAdminApi.createTenant(systemId.value, { code: form.code, name: form.name })
    editorOpen.value = false
    message.success('租户已保存')
    await load()
    await session.loadTenants()
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

function openCommand(tenant: Tenant, next: typeof command.value) {
  command.value = next
  Object.assign(commandForm, { id: tenant.id, name: tenant.name, reason: '', version: tenant.version })
  commandOpen.value = true
}

function openLifecycle(tenant: Tenant) {
  lifecycleTenant.value = tenant
  lifecycleOpen.value = true
}

async function runCommand() {
  if (!commandForm.reason.trim()) return
  saving.value = true
  try {
    await systemAdminApi.commandTenant(systemId.value, commandForm.id, command.value, { reason: commandForm.reason, version: commandForm.version, impactConfirmed: true })
    commandOpen.value = false
    message.success('租户状态已更新')
    await load()
    await session.loadTenants()
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <AdminPageHeader title="租户管理" :description="`当前系统共 ${total} 个租户，默认租户不可直接停用或归档。`">
      <template v-if="!isMobile" #actions><a-button :loading="loading" @click="load"><RefreshCw :size="16" />刷新</a-button><a-button type="primary" @click="openCreate"><Plus :size="16" />新增租户</a-button></template>
    </AdminPageHeader>
    <a-alert v-if="errorMessage" class="admin-alert" type="error" show-icon :message="errorMessage" />
    <template v-if="isMobile">
      <AdminMobileNotice />
      <div class="mobile-record-list"><article v-for="tenant in tenants" :key="tenant.id" class="mobile-record"><div class="mobile-record-title"><strong>{{ tenant.name }}</strong><a-tag :color="tenant.status === 'ACTIVE' ? 'green' : 'default'">{{ tenant.status }}</a-tag></div><dl><dt>编码</dt><dd>{{ tenant.code }}</dd><dt>成员</dt><dd>{{ tenant.memberCount }}</dd><dt>默认</dt><dd>{{ tenant.isDefault ? '是' : '否' }}</dd></dl></article></div>
      <a-empty v-if="!loading && !tenants.length" description="暂无租户" />
    </template>
    <div v-else class="admin-table-region">
      <a-table :columns="columns" :data-source="tenants" :loading="loading" :pagination="false" row-key="id">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'tenant'"><strong>{{ record.name }}</strong><span class="cell-secondary">{{ record.code }}</span></template>
          <template v-else-if="column.dataIndex === 'isDefault'">{{ record.isDefault ? '是' : '否' }}</template>
          <template v-else-if="column.dataIndex === 'status'"><a-tag :color="record.status === 'ACTIVE' ? 'green' : 'default'">{{ record.status }}</a-tag></template>
          <template v-else-if="column.key === 'actions'"><div class="table-actions"><a-button type="link" size="small" @click="openLifecycle(record as Tenant)"><Settings2 :size="14" />控制面</a-button><a-button type="link" size="small" @click="openEdit(record as Tenant)"><Pencil :size="14" />编辑</a-button><a-button v-if="record.status === 'ACTIVE' && !record.isDefault" type="link" danger size="small" @click="openCommand(record as Tenant, 'disable')"><Power :size="14" />停用</a-button><a-button v-if="record.status === 'DISABLED'" type="link" size="small" @click="openCommand(record as Tenant, 'activate')"><Power :size="14" />启用</a-button><a-button v-if="record.status === 'DISABLED' && !record.isDefault" type="link" size="small" @click="openCommand(record as Tenant, 'archive')"><Archive :size="14" />归档</a-button><a-button v-if="record.status === 'ARCHIVED'" type="link" size="small" @click="openCommand(record as Tenant, 'restore')"><Archive :size="14" />恢复</a-button></div></template>
        </template>
      </a-table>
    </div>
    <a-modal v-model:open="editorOpen" :title="form.id ? '编辑租户' : '新增租户'" :confirm-loading="saving" @ok="save"><a-form layout="vertical"><a-form-item label="租户名称" required><a-input v-model:value="form.name" /></a-form-item><a-form-item v-if="!form.id" label="租户编码" required><a-input v-model:value="form.code" /></a-form-item></a-form></a-modal>
    <a-modal v-model:open="commandOpen" title="确认租户状态变更" :confirm-loading="saving" @ok="runCommand"><p>对象：{{ commandForm.name }}</p><a-form layout="vertical"><a-form-item label="变更原因" required><a-textarea v-model:value="commandForm.reason" :rows="3" /></a-form-item></a-form></a-modal>
    <TenantLifecycleDrawer v-model:open="lifecycleOpen" :system-id="systemId" :tenant="lifecycleTenant" @reload="load" />
  </section>
</template>
