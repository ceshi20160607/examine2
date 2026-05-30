<template>
  <AdminLayout>
    <h2>RBAC · app {{ appId }}</h2>
    <div class="toolbar">
      <button type="button" @click="loadAll">刷新</button>
    </div>
    <p v-if="error" class="error">{{ error }}</p>

    <h3>新增 / 编辑角色</h3>
    <div class="role-form">
      <input v-model="roleForm.roleCode" placeholder="roleCode" />
      <input v-model="roleForm.roleName" placeholder="roleName" />
      <select v-model.number="roleForm.dataScope">
        <option v-for="o in dataScopeOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
      <select v-model.number="roleForm.status">
        <option :value="1">启用</option>
        <option :value="2">停用</option>
      </select>
      <button type="button" @click="saveRole">保存角色</button>
      <button type="button" class="secondary" @click="resetRoleForm">清空</button>
    </div>

    <h3>角色列表</h3>
    <table v-if="roles.length" class="table">
      <thead><tr><th>ID</th><th>编码</th><th>名称</th><th>数据权限</th><th>状态</th><th>操作</th></tr></thead>
      <tbody>
        <tr v-for="r in roles" :key="r.id">
          <td>{{ r.id }}</td>
          <td>{{ r.roleCode }}</td>
          <td>{{ r.roleName }}</td>
          <td>{{ dataScopeLabel(r.dataScope) }}</td>
          <td>{{ r.status === 2 ? '停用' : '启用' }}</td>
          <td>
            <button type="button" class="link" @click="editRole(r)">编辑</button>
            ·
            <router-link :to="{ name: 'role-menus', params: { appId, roleId: idToString(r.id) } }">菜单</router-link>
            ·
            <router-link :to="{ name: 'role-pages', params: { appId, roleId: idToString(r.id) } }">页面</router-link>
          </td>
        </tr>
      </tbody>
    </table>
    <p v-else class="muted">暂无角色</p>

    <h3>权限验证（URI）</h3>
    <div class="toolbar">
      <input v-model="permPreviewUri" placeholder="/v1/system/records/query" class="wide" />
      <button type="button" @click="runPermPreview">验证</button>
    </div>
    <pre v-if="permPreviewText" class="pre">{{ permPreviewText }}</pre>

    <h3>成员分配</h3>
    <div class="toolbar">
      <input v-model="accountKw" placeholder="搜索账号(≥2字)" @keyup.enter="searchAccount" />
      <button type="button" @click="searchAccount">搜索</button>
      <button type="button" @click="assignMember">分配角色</button>
    </div>
    <p class="muted member-row">
      memberPlatId: <input v-model="memberPlatId" />
      role:
      <select v-model="memberRoleId">
        <option value="">选择角色</option>
        <option v-for="r in roles" :key="r.id" :value="idToString(r.id)">
          {{ r.roleName || r.roleCode }} (#{{ r.id }})
        </option>
      </select>
    </p>
    <ul v-if="accountHits.length" class="hits">
      <li v-for="a in accountHits" :key="a.platId"><button type="button" class="secondary" @click="pickAccount(a)">{{ a.text }}</button></li>
    </ul>
    <table v-if="members.length" class="table">
      <thead><tr><th>ID</th><th>platId</th><th>roleId</th><th>deptId</th></tr></thead>
      <tbody>
        <tr v-for="m in members" :key="m.id" @click="fillMember(m)" style="cursor:pointer">
          <td>{{ m.id }}</td>
          <td>{{ m.platId }}</td>
          <td>{{ m.roleId }}</td>
          <td>{{ m.deptId }}</td>
        </tr>
      </tbody>
    </table>
  </AdminLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import AdminLayout from '../layouts/AdminLayout.vue'
import {
  assignRbacMemberRole,
  listRbacMembers,
  listRbacRoles,
  permPreview,
  searchRbacAccounts,
  upsertRbacRole
} from '../api/module'
import { idToString } from '../utils/id.js'
import { notify } from '../utils/notify.js'

const route = useRoute()
const appId = computed(() => String(route.params.appId || ''))
const roles = ref([])
const members = ref([])
const accountKw = ref('')
const accountHits = ref([])
const memberPlatId = ref('')
const memberRoleId = ref('')
const error = ref('')
const permPreviewUri = ref('/v1/system/records/query')
const permPreviewText = ref('')

const dataScopeOptions = [
  { value: 1, label: '1 仅本人' },
  { value: 2, label: '2 本人及下属' },
  { value: 3, label: '3 本部门' },
  { value: 4, label: '4 本部门及下级' },
  { value: 5, label: '5 全部' }
]

const roleForm = reactive({
  id: null,
  roleCode: '',
  roleName: '',
  dataScope: 1,
  status: 1
})

function dataScopeLabel(v) {
  return dataScopeOptions.find((o) => o.value === (v ?? 1))?.label ?? String(v ?? 1)
}

function resetRoleForm() {
  roleForm.id = null
  roleForm.roleCode = ''
  roleForm.roleName = ''
  roleForm.dataScope = 1
  roleForm.status = 1
}

function editRole(r) {
  roleForm.id = r.id ?? null
  roleForm.roleCode = r.roleCode || ''
  roleForm.roleName = r.roleName || ''
  roleForm.dataScope = Number(r.dataScope) || 1
  roleForm.status = Number(r.status) || 1
}

async function loadRoles() {
  const r = await listRbacRoles(appId.value)
  roles.value = r.data || []
}

async function loadMembers() {
  const r = await listRbacMembers(appId.value)
  members.value = r.data || []
}

async function loadAll() {
  error.value = ''
  try {
    await Promise.all([loadRoles(), loadMembers()])
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function saveRole() {
  if (!roleForm.roleCode.trim() || !roleForm.roleName.trim()) {
    error.value = '请填写 roleCode 与 roleName'
    return
  }
  error.value = ''
  try {
    await upsertRbacRole(appId.value, {
      id: roleForm.id,
      roleCode: roleForm.roleCode.trim(),
      roleName: roleForm.roleName.trim(),
      dataScope: Number(roleForm.dataScope) || 1,
      status: Number(roleForm.status) || 1
    })
    notify.success('角色已保存')
    resetRoleForm()
    await loadRoles()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function runPermPreview() {
  const uri = permPreviewUri.value.trim()
  if (!uri) return
  error.value = ''
  try {
    const r = await permPreview(uri)
    permPreviewText.value = JSON.stringify(r.data ?? r, null, 2)
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

async function searchAccount() {
  const kw = accountKw.value.trim()
  if (kw.length < 2) {
    error.value = '至少输入 2 个字符'
    return
  }
  error.value = ''
  try {
    const r = await searchRbacAccounts(kw)
    accountHits.value = r.data || []
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

function pickAccount(a) {
  memberPlatId.value = idToString(a.platId ?? a.value)
}

function fillMember(m) {
  if (m.platId) memberPlatId.value = idToString(m.platId)
  if (m.roleId) memberRoleId.value = idToString(m.roleId)
}

async function assignMember() {
  const pid = memberPlatId.value.trim()
  const rid = memberRoleId.value.trim()
  if (!pid || !rid) {
    error.value = '请输入 memberPlatId 与 roleId'
    return
  }
  error.value = ''
  try {
    await assignRbacMemberRole({ appId: appId.value, memberPlatId: pid, roleId: rid })
    notify.success('成员角色已保存')
    memberPlatId.value = ''
    memberRoleId.value = ''
    await loadMembers()
  } catch (e) {
    error.value = e?.message || String(e)
  }
}

onMounted(loadAll)
</script>

<style scoped>
.hits { list-style: none; padding: 0; display: flex; flex-wrap: wrap; gap: 0.35rem; }
input, select { padding: 0.35rem 0.5rem; border: 1px solid #d1d5db; border-radius: 6px; }
h3 { margin-top: 1.25rem; }
.role-form { display: flex; flex-wrap: wrap; gap: 0.5rem; align-items: center; margin-bottom: 0.75rem; }
.member-row {
  display: flex;
  align-items: center;
  gap: 0.45rem;
  flex-wrap: wrap;
}
.member-row input {
  width: 150px;
}
.wide { flex: 1; min-width: 220px; }
.pre { background: #f8fafc; border: 1px solid #e5e7eb; padding: 0.75rem; border-radius: 6px; font-size: 0.85rem; overflow: auto; }
.link { background: none; border: none; color: #1677ff; cursor: pointer; padding: 0; }
</style>
