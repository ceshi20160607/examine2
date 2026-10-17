<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { api, ApiError } from '../api'
import { allowsPermission } from '../permissions'
import { platformContext, platformTokens } from '../session'

interface Capability { key: string; label: string }
interface Role { id: number; name: string; description?: string; builtIn: boolean; status: string;
  version: number; grants: string[]; memberCount: number }
interface Member { id: number; displayName: string; username: string; status: string;
  version: number; roleIds: number[] }
interface Overview { capabilities: Capability[]; roles: Role[]; members: Member[] }

const token = computed(() => platformTokens.value?.accessToken || '')
const canManage = computed(() => allowsPermission(platformContext.value?.permissions,
  'PLATFORM', 'AUTHORIZATION', 'MANAGE'))
const overview = ref<Overview>({ capabilities: [], roles: [], members: [] })
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const roleOpen = ref(false)
const grantsOpen = ref(false)
const memberOpen = ref(false)
const editingRole = ref<Role>()
const editingMember = ref<Member>()
const roleForm = reactive({ name: '', description: '' })
const selectedGrants = ref<string[]>([])
const selectedRoleIds = ref<number[]>([])
const memberSearch = ref('')
const editableRoles = computed(() => overview.value.roles.filter(item => !item.builtIn && item.status === 'ACTIVE'))
const roleOptions = computed(() => editableRoles.value.map(item => ({ label: item.name, value: item.id })))
const visibleMembers = computed(() => overview.value.members.filter(item =>
  !memberSearch.value.trim() || `${item.displayName} ${item.username}`.toLowerCase()
    .includes(memberSearch.value.trim().toLowerCase())))
const rolesById = computed(() => new Map(overview.value.roles.map(item => [item.id, item.name])))

function explain(cause: unknown) {
  return cause instanceof ApiError ? `${cause.message}（${cause.code}）` : '平台角色操作失败，请刷新后重试'
}

async function load() {
  if (!token.value) return
  loading.value = true
  error.value = ''
  try { overview.value = await api<Overview>('/api/admin/platform/authorization', {}, token.value) }
  catch (cause) { error.value = explain(cause) }
  finally { loading.value = false }
}

function editRole(role?: Role) {
  editingRole.value = role
  Object.assign(roleForm, { name: role?.name || '', description: role?.description || '' })
  roleOpen.value = true
}

async function saveRole() {
  if (!canManage.value || !roleForm.name.trim()) return
  saving.value = true
  error.value = ''
  try {
    const role = editingRole.value
    await api(role ? `/api/admin/platform/authorization/roles/${role.id}`
      : '/api/admin/platform/authorization/roles', {
      method: role ? 'PUT' : 'POST',
      body: JSON.stringify({ name: roleForm.name.trim(), description: roleForm.description.trim(),
        ...(role ? { expectedVersion: role.version } : {}) }),
    }, token.value)
    roleOpen.value = false
    await load()
    message.success(role ? '角色资料已更新' : '平台角色已建立，请配置权限')
  } catch (cause) { error.value = explain(cause) }
  finally { saving.value = false }
}

function editGrants(role: Role) {
  editingRole.value = role
  selectedGrants.value = role.grants.filter(key => overview.value.capabilities.some(item => item.key === key))
  grantsOpen.value = true
}

function saveGrants() {
  const role = editingRole.value
  if (!role || !canManage.value) return
  const removed = role.grants.filter(key => overview.value.capabilities.some(item => item.key === key)
    && !selectedGrants.value.includes(key)).length
  Modal.confirm({
    title: `发布“${role.name}”的平台授权？`,
    content: `该角色关联 ${role.memberCount} 名成员。本次保留 ${selectedGrants.value.length} 项动作，撤回 ${removed} 项动作；成员下一次请求生效。`,
    okText: '确认发布', cancelText: '返回检查',
    onOk: async () => {
      saving.value = true
      error.value = ''
      try {
        await api(`/api/admin/platform/authorization/roles/${role.id}/grants`, {
          method: 'PUT', body: JSON.stringify({ grants: selectedGrants.value, expectedVersion: role.version }),
        }, token.value)
        grantsOpen.value = false
        await load()
        message.success('平台授权已更新')
      } catch (cause) { error.value = explain(cause); throw cause }
      finally { saving.value = false }
    },
  })
}

function editMember(member: Member) {
  editingMember.value = member
  selectedRoleIds.value = member.roleIds.filter(id => editableRoles.value.some(item => item.id === id))
  memberOpen.value = true
}

function saveMember() {
  const member = editingMember.value
  if (!member || !canManage.value) return
  Modal.confirm({
    title: `更新 ${member.displayName} 的平台角色？`,
    content: `将分配 ${selectedRoleIds.value.length} 个普通平台角色，下一次请求生效。不会授予系统或租户权限。`,
    okText: '确认更新', cancelText: '返回检查',
    onOk: async () => {
      saving.value = true
      error.value = ''
      try {
        await api(`/api/admin/platform/authorization/members/${member.id}/roles`, {
          method: 'PUT', body: JSON.stringify({ roleIds: selectedRoleIds.value, expectedVersion: member.version }),
        }, token.value)
        memberOpen.value = false
        await load()
        message.success('成员平台角色已更新')
      } catch (cause) { error.value = explain(cause); throw cause }
      finally { saving.value = false }
    },
  })
}

onMounted(load)
</script>

<template>
  <div class="platform-authorization-page">
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-alert type="info" show-icon message="平台角色只管理平台能力"
      description="系统与租户的业务权限请在对应系统后台维护。内置超级管理员角色受到保护。" class="section-alert" />
    <section class="panel-card">
      <div class="panel-title"><div><strong>平台角色</strong><small>配置成员可进入的平台后台及可执行动作</small></div>
        <a-space><a-button :loading="loading" @click="load">刷新</a-button><a-button v-if="canManage" type="primary" @click="editRole()">新建角色</a-button></a-space></div>
      <a-table :data-source="overview.roles" :loading="loading" row-key="id" :pagination="{ pageSize: 10 }">
        <a-table-column title="角色"><template #default="{ record }"><strong>{{ record.name }}</strong><br><small>{{ record.description || (record.builtIn ? '受保护的内置角色' : '未填写用途') }}</small></template></a-table-column>
        <a-table-column title="成员数" data-index="memberCount" />
        <a-table-column title="已授权动作"><template #default="{ record }">{{ record.builtIn ? '全部平台能力' : record.grants.length }}</template></a-table-column>
        <a-table-column v-if="canManage" title="操作"><template #default="{ record }">
          <a-space v-if="!record.builtIn"><a-button type="link" @click="editRole(record)">编辑资料</a-button>
            <a-button type="link" @click="editGrants(record)">配置权限</a-button></a-space>
          <span v-else>内置角色不可修改</span>
        </template></a-table-column>
      </a-table>
    </section>
    <section class="panel-card">
      <div class="panel-title"><div><strong>平台成员授权</strong><small>一个成员可有多个平台角色，权限取并集</small></div>
        <a-input v-model:value="memberSearch" allow-clear placeholder="搜索姓名或账号" style="max-width:240px" /></div>
      <a-table :data-source="visibleMembers" :loading="loading" row-key="id" :pagination="{ pageSize: 10 }">
        <a-table-column title="成员"><template #default="{ record }"><strong>{{ record.displayName }}</strong><br><small>{{ record.username }}</small></template></a-table-column>
        <a-table-column title="平台角色"><template #default="{ record }">{{ record.roleIds.map((id: number) => rolesById.get(id)).filter(Boolean).join('、') || '未分配角色' }}</template></a-table-column>
        <a-table-column title="状态"><template #default="{ record }">{{ record.status === 'ACTIVE' ? '有效' : '停用' }}</template></a-table-column>
        <a-table-column v-if="canManage" title="操作"><template #default="{ record }"><a-button type="link" @click="editMember(record)">调整角色</a-button></template></a-table-column>
      </a-table>
    </section>
    <a-modal v-model:open="roleOpen" :title="editingRole ? '编辑平台角色' : '新建平台角色'" :confirm-loading="saving" @ok="saveRole">
      <a-form layout="vertical"><a-form-item label="角色名称" required><a-input v-model:value="roleForm.name" :maxlength="200" placeholder="例如：平台审计员" /></a-form-item>
        <a-form-item label="用途说明"><a-textarea v-model:value="roleForm.description" :maxlength="1000" :rows="3" /></a-form-item></a-form>
    </a-modal>
    <a-modal v-model:open="grantsOpen" :title="`配置 ${editingRole?.name || ''} 的权限`" :confirm-loading="saving" ok-text="检查并发布" @ok="saveGrants">
      <p>勾选允许的动作。取消勾选会撤回该动作；成员下一次请求生效。</p>
      <a-checkbox-group v-model:value="selectedGrants" style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
        <a-checkbox v-for="item in overview.capabilities" :key="item.key" :value="item.key">{{ item.label }}</a-checkbox>
      </a-checkbox-group>
    </a-modal>
    <a-modal v-model:open="memberOpen" :title="`调整 ${editingMember?.displayName || ''} 的平台角色`" :confirm-loading="saving" ok-text="检查并保存" @ok="saveMember">
      <a-form layout="vertical"><a-form-item label="普通平台角色"><a-select v-model:value="selectedRoleIds" mode="multiple" :options="roleOptions" placeholder="选择角色" /></a-form-item></a-form>
      <a-alert v-if="editingMember?.roleIds.some(id => !editableRoles.some(role => role.id === id))" type="info" show-icon
        message="该成员的内置角色不在此处变更" />
    </a-modal>
  </div>
</template>
