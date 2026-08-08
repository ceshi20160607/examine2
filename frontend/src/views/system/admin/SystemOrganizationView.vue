<script setup lang="ts">
import type { TableColumnsType } from 'ant-design-vue'
import { message } from 'ant-design-vue'
import { Pencil, Plus, RefreshCw } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { useAdminViewport } from '@/composables/useAdminViewport'
import { systemAdminApi } from '@/services/admin'
import { useSessionStore } from '@/stores/session'
import type { Department, SystemMember } from '@/types/admin'

const { isMobile } = useAdminViewport()
const route = useRoute()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const canManageDepartments = computed(() => session.hasPermission('system.organization.manage'))
const canManageMembers = computed(() => session.hasPermission('system.member.manage'))
const canViewIdentity = computed(() => session.hasPermission('system.member.identity.view'))
const activeTab = ref(canManageDepartments.value ? 'departments' : 'members')
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const departments = ref<Department[]>([])
const members = ref<SystemMember[]>([])
const departmentTotal = ref(0)
const memberTotal = ref(0)
const departmentModal = ref(false)
const memberModal = ref(false)
const saveErrorMessage = ref('')
const departmentForm = reactive({
  id: '',
  name: '',
  code: '',
  parentId: undefined as string | undefined,
  status: 'ACTIVE' as Department['status'],
  leaderMemberId: null as string | null,
  originalLeaderMemberId: null as string | null,
  version: '',
})
const memberForm = reactive({
  id: '',
  displayName: '',
  status: 'ACTIVE' as SystemMember['status'],
  managerMemberId: null as string | null,
  originalManagerMemberId: null as string | null,
  primaryDepartmentId: undefined as string | undefined,
  departmentIds: [] as string[],
  tenantIds: [] as string[],
  roleIds: [] as string[],
  version: '',
})

const departmentColumns: TableColumnsType = [
  { title: '部门', key: 'department', width: 250 },
  { title: '上级部门', key: 'parent' },
  { title: '部门负责人', key: 'leader', width: 180 },
  { title: '成员数', dataIndex: 'memberCount', width: 100 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 90 },
]
const memberColumns = computed<TableColumnsType>(() => [
  { title: '成员', key: 'member', width: 220 },
  ...(canViewIdentity.value ? [{ title: '账号身份', key: 'identity', width: 200 }] : []),
  { title: '部门', key: 'departments' },
  { title: '直属主管', key: 'manager', width: 180 },
  { title: '租户', key: 'tenants', width: 160 },
  { title: '状态', dataIndex: 'status', width: 100 },
  { title: '操作', key: 'actions', width: 90 },
])
const departmentOptions = computed(() => departments.value.map((item) => ({ label: item.name, value: item.id })))
const activeMemberOptions = computed(() => members.value
  .filter(member => member.status === 'ACTIVE')
  .map(member => ({ label: `${member.displayName}（${member.id}）`, value: member.id })))
const departmentLeaderOptions = computed(() => members.value
  .filter(member => member.status === 'ACTIVE'
    && member.departmentIds.includes(departmentForm.id))
  .map(member => ({ label: `${member.displayName}（${member.id}）`, value: member.id })))
const managerOptions = computed(() => activeMemberOptions.value
  .filter(member => member.value !== memberForm.id))

function reportError(error: unknown, fallback = '组织成员数据加载失败') {
  errorMessage.value = error instanceof Error ? error.message : fallback
}

function departmentName(id: string) {
  return departments.value.find((item) => item.id === id)?.name ?? id
}

function memberName(id?: string | null) {
  if (!id) return '未设置'
  return members.value.find(item => item.id === id)?.displayName ?? id
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    if (canManageDepartments.value) {
      const result = await systemAdminApi.listDepartments(systemId.value, { page: 1, size: 200 })
      departments.value = result.items
      departmentTotal.value = result.total
    }
    if (canManageMembers.value) {
      const result = await systemAdminApi.listMembers(systemId.value, { page: 1, size: 200 })
      members.value = result.items
      memberTotal.value = result.total
    }
  } catch (error) {
    reportError(error)
  } finally {
    loading.value = false
  }
}

function createDepartment() {
  Object.assign(departmentForm, {
    id: '',
    name: '',
    code: '',
    parentId: undefined,
    status: 'ACTIVE',
    leaderMemberId: null,
    originalLeaderMemberId: null,
    version: '',
  })
  saveErrorMessage.value = ''
  departmentModal.value = true
}

function editDepartment(department: Department) {
  Object.assign(departmentForm, {
    ...department,
    leaderMemberId: department.leaderMemberId ?? null,
    originalLeaderMemberId: department.leaderMemberId ?? null,
  })
  saveErrorMessage.value = ''
  departmentModal.value = true
}

async function saveDepartment() {
  if (!departmentForm.name.trim() || (!departmentForm.id && !departmentForm.code.trim())) return
  saving.value = true
  saveErrorMessage.value = ''
  try {
    if (departmentForm.id) {
      let saved = await systemAdminApi.updateDepartment(systemId.value, departmentForm.id, {
        name: departmentForm.name,
        parentId: departmentForm.parentId,
        status: departmentForm.status,
        version: departmentForm.version,
      })
      departmentForm.version = saved.version
      const leaderMemberId = departmentForm.leaderMemberId ?? null
      if (leaderMemberId !== departmentForm.originalLeaderMemberId) {
        saved = await systemAdminApi.updateDepartmentLeader(systemId.value, departmentForm.id, {
          leaderMemberId,
          version: saved.version,
        })
        departmentForm.version = saved.version
        departmentForm.originalLeaderMemberId = saved.leaderMemberId ?? null
      }
    } else {
      await systemAdminApi.createDepartment(systemId.value, {
        name: departmentForm.name,
        code: departmentForm.code,
        parentId: departmentForm.parentId,
      })
    }
    departmentModal.value = false
    message.success('部门已保存')
    await load()
  } catch (error) {
    saveErrorMessage.value = error instanceof Error ? error.message : '部门保存失败'
    reportError(error, '部门保存失败')
  } finally {
    saving.value = false
  }
}

function editMember(member: SystemMember) {
  Object.assign(memberForm, {
    id: member.id,
    displayName: member.displayName,
    status: member.status,
    managerMemberId: member.managerMemberId ?? null,
    originalManagerMemberId: member.managerMemberId ?? null,
    primaryDepartmentId: member.primaryDepartmentId,
    departmentIds: [...member.departmentIds],
    tenantIds: [...member.tenantIds],
    roleIds: [...member.roleIds],
    version: member.version,
  })
  saveErrorMessage.value = ''
  memberModal.value = true
}

async function saveMember() {
  saving.value = true
  saveErrorMessage.value = ''
  try {
    let saved = await systemAdminApi.updateMember(systemId.value, memberForm.id, {
      status: memberForm.status,
      primaryDepartmentId: memberForm.primaryDepartmentId,
      departmentIds: memberForm.departmentIds,
      tenantIds: memberForm.tenantIds,
      roleIds: memberForm.roleIds,
      version: memberForm.version,
    })
    memberForm.version = saved.version
    const managerMemberId = memberForm.managerMemberId ?? null
    if (managerMemberId !== memberForm.originalManagerMemberId) {
      saved = await systemAdminApi.updateMemberManager(systemId.value, memberForm.id, {
        managerMemberId,
        version: saved.version,
      })
      memberForm.version = saved.version
      memberForm.originalManagerMemberId = saved.managerMemberId ?? null
    }
    memberModal.value = false
    message.success('成员授权已保存')
    await load()
  } catch (error) {
    saveErrorMessage.value = error instanceof Error ? error.message : '成员保存失败'
    reportError(error, '成员保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="admin-page">
    <AdminPageHeader title="组织成员" description="部门结构与成员、租户、角色绑定分别按权限维护。">
      <template v-if="!isMobile" #actions><a-button :loading="loading" @click="load"><RefreshCw :size="16" />刷新</a-button></template>
    </AdminPageHeader>
    <a-alert v-if="errorMessage" class="admin-alert" type="error" show-icon :message="errorMessage" />
    <AdminMobileNotice v-if="isMobile" />
    <a-tabs v-model:active-key="activeTab" class="admin-tabs">
      <a-tab-pane v-if="canManageDepartments" key="departments" :tab="`部门 ${departmentTotal}`">
        <div v-if="!isMobile" class="tab-toolbar"><a-button type="primary" @click="createDepartment"><Plus :size="16" />新增部门</a-button></div>
        <div v-if="isMobile" class="mobile-record-list"><article v-for="department in departments" :key="department.id" class="mobile-record"><div class="mobile-record-title"><strong>{{ department.name }}</strong><a-tag>{{ department.status }}</a-tag></div><dl><dt>编码</dt><dd>{{ department.code }}</dd><dt>负责人</dt><dd>{{ memberName(department.leaderMemberId) }}</dd><dt>成员</dt><dd>{{ department.memberCount }}</dd></dl></article></div>
        <div v-else class="admin-table-region"><a-table :columns="departmentColumns" :data-source="departments" :loading="loading" :pagination="false" row-key="id"><template #bodyCell="{ column, record }"><template v-if="column.key === 'department'"><strong>{{ record.name }}</strong><span class="cell-secondary">{{ record.code }}</span></template><template v-else-if="column.key === 'parent'">{{ record.parentId ? departmentName(record.parentId) : '根部门' }}</template><template v-else-if="column.key === 'leader'">{{ memberName(record.leaderMemberId) }}</template><template v-else-if="column.dataIndex === 'status'"><a-tag :color="record.status === 'ACTIVE' ? 'green' : 'default'">{{ record.status }}</a-tag></template><template v-else-if="column.key === 'actions'"><a-button type="link" size="small" @click="editDepartment(record as Department)"><Pencil :size="14" />编辑</a-button></template></template></a-table></div>
      </a-tab-pane>
      <a-tab-pane v-if="canManageMembers" key="members" :tab="`成员 ${memberTotal}`">
        <div v-if="isMobile" class="mobile-record-list"><article v-for="member in members" :key="member.id" class="mobile-record"><div class="mobile-record-title"><strong>{{ member.displayName }}</strong><a-tag>{{ member.status }}</a-tag></div><dl><dt>部门</dt><dd>{{ member.departmentIds.map(departmentName).join('、') || '未分配' }}</dd><dt>直属主管</dt><dd>{{ memberName(member.managerMemberId) }}</dd><dt>租户数</dt><dd>{{ member.tenantIds.length }}</dd><dt>角色数</dt><dd>{{ member.roleIds.length }}</dd></dl></article></div>
        <div v-else class="admin-table-region"><a-table :columns="memberColumns" :data-source="members" :loading="loading" :pagination="false" row-key="id" :scroll="{ x: 1040 }"><template #bodyCell="{ column, record }"><template v-if="column.key === 'member'"><strong>{{ record.displayName }}</strong><span class="cell-secondary">{{ record.id }}</span></template><template v-else-if="column.key === 'identity'">{{ record.username || record.email || record.accountId }}</template><template v-else-if="column.key === 'departments'">{{ record.departmentIds.map(departmentName).join('、') || '未分配' }}</template><template v-else-if="column.key === 'manager'">{{ memberName(record.managerMemberId) }}</template><template v-else-if="column.key === 'tenants'">{{ record.tenantIds.length }} 个租户</template><template v-else-if="column.dataIndex === 'status'"><a-tag :color="record.status === 'ACTIVE' ? 'green' : 'default'">{{ record.status }}</a-tag></template><template v-else-if="column.key === 'actions'"><a-button type="link" size="small" @click="editMember(record as SystemMember)"><Pencil :size="14" />编辑</a-button></template></template></a-table></div>
      </a-tab-pane>
    </a-tabs>
    <a-modal v-model:open="departmentModal" :title="departmentForm.id ? '编辑部门' : '新增部门'" :confirm-loading="saving" @ok="saveDepartment">
      <a-alert v-if="saveErrorMessage" class="admin-alert" type="error" show-icon :message="saveErrorMessage" />
      <a-form layout="vertical"><a-form-item label="部门名称" required><a-input v-model:value="departmentForm.name" /></a-form-item><a-form-item v-if="!departmentForm.id" label="部门编码" required><a-input v-model:value="departmentForm.code" /></a-form-item><a-form-item label="上级部门"><a-select v-model:value="departmentForm.parentId" allow-clear :options="departmentOptions.filter((item) => item.value !== departmentForm.id)" /></a-form-item><a-form-item v-if="departmentForm.id" label="部门负责人"><a-select v-model:value="departmentForm.leaderMemberId" class="department-leader-select" allow-clear show-search :options="departmentLeaderOptions" placeholder="选择该部门的启用成员" /></a-form-item><a-form-item v-if="departmentForm.id" label="状态"><a-select v-model:value="departmentForm.status"><a-select-option value="ACTIVE">启用</a-select-option><a-select-option value="DISABLED">停用</a-select-option></a-select></a-form-item></a-form>
    </a-modal>
    <a-modal v-model:open="memberModal" title="编辑成员授权" :confirm-loading="saving" @ok="saveMember">
      <a-alert v-if="saveErrorMessage" class="admin-alert" type="error" show-icon :message="saveErrorMessage" />
      <a-form layout="vertical"><a-form-item label="成员"><a-input :value="memberForm.displayName" disabled /></a-form-item><a-form-item label="直属主管"><a-select v-model:value="memberForm.managerMemberId" class="member-manager-select" allow-clear show-search :options="managerOptions" placeholder="选择启用成员" /></a-form-item><a-form-item label="状态"><a-select v-model:value="memberForm.status"><a-select-option value="ACTIVE">启用</a-select-option><a-select-option value="DISABLED">停用</a-select-option><a-select-option value="PENDING">待处理</a-select-option></a-select></a-form-item><a-form-item v-if="canManageDepartments" label="主部门"><a-select v-model:value="memberForm.primaryDepartmentId" allow-clear :options="departmentOptions" /></a-form-item><a-form-item v-if="canManageDepartments" label="全部部门"><a-select v-model:value="memberForm.departmentIds" mode="multiple" :options="departmentOptions" /></a-form-item><a-form-item label="授权租户 ID"><a-select v-model:value="memberForm.tenantIds" mode="tags" /></a-form-item><a-form-item label="角色 ID"><a-select v-model:value="memberForm.roleIds" mode="tags" /></a-form-item></a-form>
    </a-modal>
  </section>
</template>
