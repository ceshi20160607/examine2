<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Empty, message } from 'ant-design-vue'
import { api, ApiError } from '../api'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens } from '../session'

interface Department {
  id: number
  parentId?: number
  name: string
  pathCode: string
  sortOrder: number
  status: string
  version: number
}
interface Member {
  id: number
  accountId: number
  displayName: string
  username: string
  departmentId?: number
  title?: string
  status: string
  version: number
}
interface Overview { departments: Department[]; members: Member[] }

const token = computed(() => platformTokens.value?.accessToken || '')
const canManage = computed(() => allowsPermission(platformContext.value?.permissions,
  'PLATFORM', 'ORGANIZATION', 'MANAGE'))
const overview = ref<Overview>({ departments: [], members: [] })
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const departmentOpen = ref(false)
const memberOpen = ref(false)
const memberSearch = ref('')
const editingDepartment = ref<Department>()
const editingMember = ref<Member>()
const departmentForm = reactive({ name: '', parentId: undefined as number | undefined, sortOrder: 0 })
const memberForm = reactive({ departmentId: undefined as number | undefined, title: '' })
const activeDepartments = computed(() => overview.value.departments.filter(item => item.status === 'ACTIVE'))
const departmentOptions = computed(() => activeDepartments.value.map(item => ({
  value: item.id,
  label: `${'　'.repeat(item.pathCode.split('/').length - 1)}${item.name}`,
})))
const departmentNames = computed(() => new Map(overview.value.departments.map(item => [item.id, item.name])))
const visibleMembers = computed(() => overview.value.members.filter(item =>
  !memberSearch.value.trim() || `${item.displayName} ${item.username}`.toLowerCase()
    .includes(memberSearch.value.trim().toLowerCase())))

function explain(cause: unknown) {
  return cause instanceof ApiError ? `${cause.message}（${cause.code}）` : '平台组织操作失败，请刷新后重试'
}

async function load() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try { overview.value = await api<Overview>('/api/admin/platform/organization', {}, token.value) }
  catch (cause) { error.value = explain(cause) }
  finally { loading.value = false }
}

function createDepartment() {
  editingDepartment.value = undefined
  Object.assign(departmentForm, { name: '', parentId: undefined, sortOrder: 0 })
  departmentOpen.value = true
}

function renameDepartment(item: Department) {
  editingDepartment.value = item
  Object.assign(departmentForm, { name: item.name, parentId: item.parentId, sortOrder: item.sortOrder })
  departmentOpen.value = true
}

async function saveDepartment() {
  if (!canManage.value || !departmentForm.name.trim()) return
  saving.value = true
  error.value = ''
  try {
    const current = editingDepartment.value
    await api(current ? `/api/admin/platform/organization/departments/${current.id}`
      : '/api/admin/platform/organization/departments', {
      method: current ? 'PUT' : 'POST',
      body: JSON.stringify(current
        ? { name: departmentForm.name.trim(), sortOrder: departmentForm.sortOrder,
          expectedVersion: current.version }
        : { name: departmentForm.name.trim(), parentId: departmentForm.parentId,
          sortOrder: departmentForm.sortOrder }),
    }, token.value)
    departmentOpen.value = false
    await load()
    message.success(current ? '部门名称已更新' : '平台部门已建立')
  } catch (cause) { error.value = explain(cause) }
  finally { saving.value = false }
}

function editMember(item: Member) {
  editingMember.value = item
  Object.assign(memberForm, { departmentId: item.departmentId, title: item.title || '' })
  memberOpen.value = true
}

async function saveMember() {
  if (!canManage.value || !editingMember.value) return
  saving.value = true
  error.value = ''
  try {
    await api(`/api/admin/platform/organization/members/${editingMember.value.id}/assignment`, {
      method: 'PUT', body: JSON.stringify({ departmentId: memberForm.departmentId,
        title: memberForm.title.trim(), expectedVersion: editingMember.value.version }),
    }, token.value)
    memberOpen.value = false
    await load()
    message.success('成员部门与职务已更新，平台角色保持原样')
  } catch (cause) { error.value = explain(cause) }
  finally { saving.value = false }
}

onMounted(load)
</script>

<template>
  <div class="platform-organization-page">
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-alert type="info" show-icon message="平台组织独立于各系统和租户" description="调整平台成员的部门不会授予系统访问，也不会修改其平台角色。" class="section-alert" />
    <section class="panel-card">
      <div class="panel-title"><div><strong>平台部门</strong><small>以名称和上级部门建立组织层级</small></div>
        <a-space><a-button :loading="loading" @click="load">刷新</a-button><a-button v-if="canManage" type="primary" @click="createDepartment">新建部门</a-button></a-space>
      </div>
      <a-table :data-source="overview.departments" :loading="loading" row-key="id" :pagination="false">
        <a-table-column title="部门"><template #default="{ record }"><span>{{ '　'.repeat(record.pathCode.split('/').length - 1) }}{{ record.name }}</span></template></a-table-column>
        <a-table-column title="上级部门"><template #default="{ record }">{{ record.parentId ? departmentNames.get(record.parentId) : '平台直属' }}</template></a-table-column>
        <a-table-column title="状态"><template #default="{ record }">{{ record.status === 'ACTIVE' ? '启用' : '停用' }}</template></a-table-column>
        <a-table-column v-if="canManage" title="操作"><template #default="{ record }"><a-button type="link" @click="renameDepartment(record)">修改名称</a-button></template></a-table-column>
      </a-table>
      <a-empty v-if="!loading && !overview.departments.length" :image="Empty.PRESENTED_IMAGE_SIMPLE" description="还没有平台部门" />
    </section>
    <section class="panel-card">
      <div class="panel-title"><div><strong>平台成员</strong><small>按账号维护部门与职务</small></div><a-input v-model:value="memberSearch" allow-clear placeholder="搜索平台成员" style="max-width:240px" /></div>
      <a-table :data-source="visibleMembers" :loading="loading" row-key="id" :pagination="{ pageSize: 10 }">
        <a-table-column title="成员"><template #default="{ record }"><strong>{{ record.displayName }}</strong><br><small>{{ record.username }}</small></template></a-table-column>
        <a-table-column title="部门"><template #default="{ record }">{{ record.departmentId ? departmentNames.get(record.departmentId) : '未分配' }}</template></a-table-column>
        <a-table-column title="职务" data-index="title" />
        <a-table-column title="状态"><template #default="{ record }">{{ record.status === 'ACTIVE' ? '有效' : '停用' }}</template></a-table-column>
        <a-table-column v-if="canManage" title="操作"><template #default="{ record }"><a-button type="link" @click="editMember(record)">调整归属</a-button></template></a-table-column>
      </a-table>
    </section>
    <a-modal v-model:open="departmentOpen" :title="editingDepartment ? '修改平台部门' : '新建平台部门'" :confirm-loading="saving" @ok="saveDepartment">
      <a-form layout="vertical">
        <a-form-item label="部门名称" required><a-input v-model:value="departmentForm.name" :maxlength="200" placeholder="例如：客户服务部" /></a-form-item>
        <a-form-item v-if="!editingDepartment" label="上级部门"><a-select v-model:value="departmentForm.parentId" allow-clear :options="departmentOptions" placeholder="留空表示平台直属" /></a-form-item>
        <a-form-item label="排序"><a-input-number v-model:value="departmentForm.sortOrder" style="width:100%" /></a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="memberOpen" :title="`调整 ${editingMember?.displayName || ''} 的平台归属`" :confirm-loading="saving" @ok="saveMember">
      <a-form layout="vertical">
        <a-form-item label="部门"><a-select v-model:value="memberForm.departmentId" allow-clear :options="departmentOptions" placeholder="未分配部门" /></a-form-item>
        <a-form-item label="职务"><a-input v-model:value="memberForm.title" :maxlength="100" placeholder="可选" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
