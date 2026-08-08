<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { Pencil, Plus, RefreshCw } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { useAdminViewport } from '@/composables/useAdminViewport'
import { ApiRequestError } from '@/services/api'
import { platformAdminApi } from '@/services/admin'
import type { Department, PlatformAccount } from '@/types/admin'

const { isMobile } = useAdminViewport()
const activeTab = ref('accounts')
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const accounts = ref<PlatformAccount[]>([])
const departments = ref<Department[]>([])
const accountTotal = ref(0)
const departmentTotal = ref(0)
const accountModal = ref(false)
const departmentModal = ref(false)
const accountForm = reactive({ id: '', displayName: '', status: 'ACTIVE' as PlatformAccount['status'], departmentIds: [] as string[], version: '' })
const departmentForm = reactive({ id: '', name: '', code: '', parentId: undefined as string | undefined, status: 'ACTIVE' as Department['status'], version: '' })

const accountColumns: TableColumnsType = [
  { title: '账号', key: 'account', width: 220 },
  { title: '姓名', dataIndex: 'displayName', width: 160 },
  { title: '部门', key: 'departments' },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 90 },
]
const departmentColumns: TableColumnsType = [
  { title: '部门', key: 'department', width: 240 },
  { title: '上级部门', key: 'parent', width: 180 },
  { title: '成员数', dataIndex: 'memberCount', width: 100 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 90 },
]
const departmentOptions = computed(() => departments.value.map((item) => ({ label: item.name, value: item.id })))

function reportError(error: unknown) {
  errorMessage.value = error instanceof ApiRequestError ? error.message : '组织数据加载失败'
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [accountResult, departmentResult] = await Promise.all([
      platformAdminApi.listAccounts({ page: 1, size: 100 }),
      platformAdminApi.listDepartments({ page: 1, size: 200 }),
    ])
    accounts.value = accountResult.items
    accountTotal.value = accountResult.total
    departments.value = departmentResult.items
    departmentTotal.value = departmentResult.total
  } catch (error) {
    reportError(error)
  } finally {
    loading.value = false
  }
}

function departmentName(id: string) {
  return departments.value.find((item) => item.id === id)?.name ?? id
}

function editAccount(account: PlatformAccount) {
  Object.assign(accountForm, { id: account.id, displayName: account.displayName, status: account.status, departmentIds: [...account.departmentIds], version: account.version })
  accountModal.value = true
}

async function saveAccount() {
  saving.value = true
  try {
    await platformAdminApi.updateAccount(accountForm.id, { displayName: accountForm.displayName, status: accountForm.status, departmentIds: accountForm.departmentIds, version: accountForm.version })
    accountModal.value = false
    message.success('账号组织信息已保存')
    await load()
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

function createDepartment() {
  Object.assign(departmentForm, { id: '', name: '', code: '', parentId: undefined, status: 'ACTIVE', version: '' })
  departmentModal.value = true
}

function editDepartment(department: Department) {
  Object.assign(departmentForm, department)
  departmentModal.value = true
}

async function saveDepartment() {
  if (!departmentForm.name.trim() || (!departmentForm.id && !departmentForm.code.trim())) return
  saving.value = true
  try {
    if (departmentForm.id) {
      await platformAdminApi.updateDepartment(departmentForm.id, { name: departmentForm.name, parentId: departmentForm.parentId, status: departmentForm.status, version: departmentForm.version })
    } else {
      await platformAdminApi.createDepartment({ name: departmentForm.name, code: departmentForm.code, parentId: departmentForm.parentId })
    }
    departmentModal.value = false
    message.success('部门已保存')
    await load()
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
    <AdminPageHeader title="组织账号" description="维护平台账号与部门归属，平台角色在角色管理中发布。">
      <template v-if="!isMobile" #actions><a-button :loading="loading" @click="load"><RefreshCw :size="16" />刷新</a-button></template>
    </AdminPageHeader>
    <a-alert v-if="errorMessage" class="admin-alert" type="error" show-icon :message="errorMessage" />
    <AdminMobileNotice v-if="isMobile" />
    <a-tabs v-model:active-key="activeTab" class="admin-tabs">
      <a-tab-pane key="accounts" :tab="`平台账号 ${accountTotal}`">
        <div v-if="isMobile" class="mobile-record-list">
          <article v-for="account in accounts" :key="account.id" class="mobile-record"><div class="mobile-record-title"><strong>{{ account.displayName }}</strong><a-tag>{{ account.status }}</a-tag></div><dl><dt>账号</dt><dd>{{ account.username }}</dd><dt>部门</dt><dd>{{ account.departmentIds.map(departmentName).join('、') || '未分配' }}</dd></dl></article>
          <a-empty v-if="!loading && !accounts.length" description="暂无账号" />
        </div>
        <div v-else class="admin-table-region">
          <a-table :columns="accountColumns" :data-source="accounts" :loading="loading" :pagination="false" row-key="id">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'account'"><strong>{{ record.username }}</strong><span class="cell-secondary">{{ record.email || record.mobile || record.id }}</span></template>
              <template v-else-if="column.key === 'departments'">{{ record.departmentIds.map(departmentName).join('、') || '未分配' }}</template>
              <template v-else-if="column.dataIndex === 'status'"><a-tag :color="record.status === 'ACTIVE' ? 'green' : 'default'">{{ record.status }}</a-tag></template>
              <template v-else-if="column.key === 'actions'"><a-button type="link" size="small" @click="editAccount(record as PlatformAccount)"><Pencil :size="14" />编辑</a-button></template>
            </template>
          </a-table>
        </div>
      </a-tab-pane>
      <a-tab-pane key="departments" :tab="`部门 ${departmentTotal}`">
        <div v-if="!isMobile" class="tab-toolbar"><a-button type="primary" @click="createDepartment"><Plus :size="16" />新增部门</a-button></div>
        <div v-if="isMobile" class="mobile-record-list">
          <article v-for="department in departments" :key="department.id" class="mobile-record"><div class="mobile-record-title"><strong>{{ department.name }}</strong><a-tag>{{ department.status }}</a-tag></div><dl><dt>编码</dt><dd>{{ department.code }}</dd><dt>成员</dt><dd>{{ department.memberCount }}</dd></dl></article>
          <a-empty v-if="!loading && !departments.length" description="暂无部门" />
        </div>
        <div v-else class="admin-table-region">
          <a-table :columns="departmentColumns" :data-source="departments" :loading="loading" :pagination="false" row-key="id">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'department'"><strong>{{ record.name }}</strong><span class="cell-secondary">{{ record.code }}</span></template>
              <template v-else-if="column.key === 'parent'">{{ record.parentId ? departmentName(record.parentId) : '根部门' }}</template>
              <template v-else-if="column.dataIndex === 'status'"><a-tag :color="record.status === 'ACTIVE' ? 'green' : 'default'">{{ record.status }}</a-tag></template>
              <template v-else-if="column.key === 'actions'"><a-button type="link" size="small" @click="editDepartment(record as Department)"><Pencil :size="14" />编辑</a-button></template>
            </template>
          </a-table>
        </div>
      </a-tab-pane>
    </a-tabs>

    <a-modal v-model:open="accountModal" title="编辑平台账号" :confirm-loading="saving" @ok="saveAccount">
      <a-form layout="vertical"><a-form-item label="姓名"><a-input v-model:value="accountForm.displayName" /></a-form-item><a-form-item label="状态"><a-select v-model:value="accountForm.status"><a-select-option value="ACTIVE">启用</a-select-option><a-select-option value="DISABLED">停用</a-select-option><a-select-option value="LOCKED">锁定</a-select-option></a-select></a-form-item><a-form-item label="所属部门"><a-select v-model:value="accountForm.departmentIds" mode="multiple" :options="departmentOptions" /></a-form-item></a-form>
    </a-modal>
    <a-modal v-model:open="departmentModal" :title="departmentForm.id ? '编辑部门' : '新增部门'" :confirm-loading="saving" @ok="saveDepartment">
      <a-form layout="vertical"><a-form-item label="部门名称" required><a-input v-model:value="departmentForm.name" /></a-form-item><a-form-item v-if="!departmentForm.id" label="部门编码" required><a-input v-model:value="departmentForm.code" /></a-form-item><a-form-item label="上级部门"><a-select v-model:value="departmentForm.parentId" allow-clear :options="departmentOptions.filter((item) => item.value !== departmentForm.id)" /></a-form-item><a-form-item v-if="departmentForm.id" label="状态"><a-select v-model:value="departmentForm.status"><a-select-option value="ACTIVE">启用</a-select-option><a-select-option value="DISABLED">停用</a-select-option></a-select></a-form-item></a-form>
    </a-modal>
  </section>
</template>
