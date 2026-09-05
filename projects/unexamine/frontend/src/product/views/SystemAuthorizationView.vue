<script setup lang="ts">
import { PlusOutlined, ReloadOutlined, SafetyCertificateOutlined, TeamOutlined, UserAddOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api'
import { systemTokens } from '../session'
import PersonSelect from '../components/PersonSelect.vue'
import DepartmentSelect from '../components/DepartmentSelect.vue'
import type { AuthorizationDepartment, AuthorizationMember, AuthorizationRole, PermissionPreview, SystemAuthorizationOverview, SystemDirectoryDepartment, SystemDirectoryPerson } from '../types'

const overview = ref<SystemAuthorizationOverview | null>(null)
const props = withDefaults(defineProps<{ section?: 'all' | 'organization' | 'roles' }>(), { section: 'all' })
const activeTab = ref(props.section === 'roles' ? 'roles' : 'organization')
const roleStep = ref<'basic' | 'permissions' | 'publish'>('basic')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const departmentDrawer = ref(false)
const memberModal = ref(false)
const selectedRoleId = ref<number | null>(null)
const publishReason = ref('')
const preview = ref<PermissionPreview | null>(null)
const previewMemberId = ref<number | undefined>()
const previewResourceCode = ref<string | undefined>()
const previewActionCode = ref<string | undefined>()
const policyChannel = ref<'PAGE' | 'APPLICATION' | 'FILE' | 'FLOW'>('PAGE')
const departmentForm = reactive({ id: null as number | null, parentId: undefined as number | undefined, code: '', name: '', sortOrder: 0, expectedVersion: undefined as number | undefined })
const roleForm = reactive({ id: null as number | null, code: '', name: '', description: '', expectedVersion: undefined as number | undefined })
const memberForm = reactive({ account: '', employeeNumber: '', departmentId: undefined as number | undefined, managerTenantMemberId: undefined as number | undefined, positionTitle: '', roleIds: [] as number[] })
const permissionSelections = reactive<Record<string, { checked: boolean; dataScopeType: string }>>({})
const fieldSelections = reactive<Record<string, { readable: boolean; writable: boolean; maskStrategy: string }>>({})
const memberAssignments = reactive<Record<number, { departmentId?: number; managerTenantMemberId?: number; positionTitle: string; roleIds: number[]; version: number }>>({})

const selectedRole = computed(() => overview.value?.roles.find(role => role.id === selectedRoleId.value) ?? null)
const editableRoles = computed(() => overview.value?.roles.filter(role => !role.builtIn) ?? [])
const directoryDepartments = computed<SystemDirectoryDepartment[]>(() => (overview.value?.departments ?? []).map(item => ({
  id: item.id, parentId: item.parentId, name: item.name, fullName: departmentFullName(item.id),
  memberCount: overview.value?.members.filter(member => member.departmentId === item.id).length ?? 0,
})))
const directoryPeople = computed<SystemDirectoryPerson[]>(() => (overview.value?.members ?? []).map(member => ({
  tenantMemberId: member.tenantMemberId, systemMemberId: member.systemMemberId, accountId: member.accountId,
  displayName: member.displayName, employeeNumber: member.employeeNumber, departmentId: member.departmentId,
  departmentName: member.departmentName, managerTenantMemberId: member.managerTenantMemberId,
  managerName: member.managerName, positionTitle: member.positionTitle, roleNames: member.roleNames,
  tenantAdmin: member.tenantAdmin,
})))
const affectedMenus = computed(() => {
  if (!overview.value) return []
  const selected = new Set(Object.entries(permissionSelections).filter(([, value]) => value.checked).map(([key]) => key.split(':').slice(0, 2).join(':')))
  return overview.value.resources.filter(resource => selected.has(`${resource.resourceType}:${resource.resourceCode}`)).map(resource => resource.name)
})
const dataScopeOptions = [
  { value: 'ALL', label: '全部数据' },
  { value: 'DEPARTMENT_AND_DESCENDANTS', label: '本部门及下级' },
  { value: 'DEPARTMENT', label: '本部门' },
  { value: 'SELF_AND_SUBORDINATES', label: '本人及下属' },
  { value: 'SELF', label: '仅本人' },
]
const previewActionOptions = computed(() => {
  const resource = overview.value?.resources.find(item => item.resourceCode === previewResourceCode.value)
  return resource?.actions.map(value => ({ value, label: actionLabel(value, resource) })) ?? []
})
const pageTitle = computed(() => props.section === 'organization' ? '组织架构' : props.section === 'roles' ? '角色权限' : '组织与权限')
const pageDescription = computed(() => props.section === 'organization'
  ? '维护部门、成员、岗位和上下级关系。'
  : props.section === 'roles' ? '维护角色的菜单、动作、字段和数据范围，并在发布前预览结果。' : '先维护团队关系，再用角色控制成员能看什么、能做什么。')

function showError(reason: unknown, fallback: string) {
  error.value = reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（追踪号：${reason.traceId}）` : ''}` : fallback
}

async function load(preferredRoleId?: number) {
  if (!systemTokens.value?.accessToken) return
  loading.value = true
  error.value = ''
  try {
    overview.value = await api<SystemAuthorizationOverview>('/api/admin/system/authorization', {}, systemTokens.value.accessToken)
    for (const member of overview.value.members) {
      memberAssignments[member.tenantMemberId] = {
        departmentId: member.departmentId, managerTenantMemberId: member.managerTenantMemberId,
        positionTitle: member.positionTitle || '', roleIds: [...member.roleIds], version: member.version,
      }
    }
    const roleId = preferredRoleId ?? selectedRoleId.value ?? editableRoles.value[0]?.id ?? null
    selectRole(roleId)
  } catch (reason) {
    showError(reason, '组织与权限加载失败')
  } finally {
    loading.value = false
  }
}

function permissionKey(resourceType: string, resourceCode: string, action: string) {
  return `${resourceType}:${resourceCode}:${action}`
}

function fieldKey(resourceCode: string, fieldCode: string, channel = policyChannel.value) {
  return `${resourceCode}:${fieldCode}:${channel}`
}

function memberAssignment(member: AuthorizationMember) {
  return memberAssignments[member.tenantMemberId] ??= {
    departmentId: member.departmentId, managerTenantMemberId: member.managerTenantMemberId,
    positionTitle: member.positionTitle || '', roleIds: [...member.roleIds], version: member.version,
  }
}

function departmentFullName(departmentId?: number) {
  const names: string[] = []
  let current = overview.value?.departments.find(item => item.id === departmentId)
  while (current) {
    names.unshift(current.name)
    current = overview.value?.departments.find(item => item.id === current?.parentId)
  }
  return names.join(' / ')
}

function departmentDepth(department: AuthorizationDepartment) {
  let depth = 0
  let current = overview.value?.departments.find(item => item.id === department.parentId)
  while (current) {
    depth += 1
    current = overview.value?.departments.find(item => item.id === current?.parentId)
  }
  return depth
}

function actionLabel(code: string, resource?: { actionNames: Record<string, string> }) {
  return resource?.actionNames[code] || '自定义操作'
}

function scopeLabel(code?: string) {
  return dataScopeOptions.find(item => item.value === code)?.label || '无数据范围'
}

function channelLabel(code: string) {
  return ({ PAGE: '页面', APPLICATION: '应用', FILE: '文件', FLOW: '流程' } as Record<string, string>)[code] || code
}

function permissionSelection(resourceType: string, resourceCode: string, action: string) {
  const key = permissionKey(resourceType, resourceCode, action)
  return permissionSelections[key] ??= { checked: false, dataScopeType: 'SELF' }
}

function fieldSelection(resourceCode: string, fieldCode: string, channel = policyChannel.value) {
  const key = fieldKey(resourceCode, fieldCode, channel)
  return fieldSelections[key] ??= { readable: false, writable: false, maskStrategy: '' }
}

function keepWritableFieldReadable(resourceCode: string, fieldCode: string, channel = policyChannel.value) {
  const selection = fieldSelection(resourceCode, fieldCode, channel)
  if (selection.writable) selection.readable = true
}

function selectRole(roleId: number | null) {
  roleStep.value = 'basic'
  selectedRoleId.value = roleId
  Object.keys(permissionSelections).forEach(key => delete permissionSelections[key])
  Object.keys(fieldSelections).forEach(key => delete fieldSelections[key])
  const role = overview.value?.roles.find(item => item.id === roleId)
  roleForm.id = role?.id ?? null
  roleForm.code = role?.code ?? ''
  roleForm.name = role?.name ?? ''
  roleForm.description = role?.description ?? ''
  roleForm.expectedVersion = role?.version
  for (const resource of overview.value?.resources ?? []) {
    for (const action of resource.actions) {
      const saved = role?.permissions.find(item => item.resourceType === resource.resourceType && item.resourceCode === resource.resourceCode && item.actionCode === action)
      permissionSelections[permissionKey(resource.resourceType, resource.resourceCode, action)] = { checked: Boolean(saved), dataScopeType: saved?.dataScopeType ?? 'SELF' }
    }
    for (const field of resource.fields) {
      for (const channel of ['PAGE', 'APPLICATION', 'FILE', 'FLOW'] as const) {
        const saved = role?.fieldPolicies.find(item => item.resourceCode === resource.resourceCode && item.fieldCode === field && item.channel === channel)
        fieldSelections[fieldKey(resource.resourceCode, field, channel)] = { readable: saved?.readable ?? false, writable: saved?.writable ?? false, maskStrategy: saved?.maskStrategy ?? '' }
      }
    }
  }
  publishReason.value = ''
}

function openDepartment(department?: AuthorizationDepartment) {
  departmentForm.id = department?.id ?? null
  departmentForm.parentId = department?.parentId
  departmentForm.code = department?.code ?? ''
  departmentForm.name = department?.name ?? ''
  departmentForm.sortOrder = department?.sortOrder ?? 0
  departmentForm.expectedVersion = department?.version
  departmentDrawer.value = true
}

async function saveDepartment() {
  if (!systemTokens.value?.accessToken || !departmentForm.name.trim()) return
  saving.value = true
  error.value = ''
  try {
    await api('/api/admin/system/authorization/departments', {
      method: 'POST', body: JSON.stringify({ ...departmentForm, code: departmentForm.code || null, name: departmentForm.name.trim() }),
    }, systemTokens.value.accessToken)
    success.value = departmentForm.id ? '部门关系已更新。' : '部门已创建。'
    departmentDrawer.value = false
    await load()
  } catch (reason) {
    showError(reason, '部门保存失败')
  } finally {
    saving.value = false
  }
}

function openMember() {
  Object.assign(memberForm, { account: '', employeeNumber: '', departmentId: overview.value?.departments[0]?.id,
    managerTenantMemberId: undefined, positionTitle: '', roleIds: [] })
  memberModal.value = true
}

async function addMember() {
  if (!systemTokens.value?.accessToken || !memberForm.account.trim()) return
  saving.value = true
  error.value = ''
  try {
    const saved = await api<AuthorizationMember>('/api/admin/system/authorization/members', {
      method: 'POST', body: JSON.stringify({ ...memberForm, account: memberForm.account.trim(),
        employeeNumber: memberForm.employeeNumber.trim() || null, positionTitle: memberForm.positionTitle.trim() || null }),
    }, systemTokens.value.accessToken)
    memberModal.value = false
    success.value = `${saved.displayName}已加入当前系统，可以使用统一人员选择器分配业务。`
    await load()
  } catch (reason) {
    showError(reason, '成员开通失败')
  } finally {
    saving.value = false
  }
}

async function saveMember(member: AuthorizationMember) {
  if (!systemTokens.value?.accessToken) return
  saving.value = true
  error.value = ''
  try {
    const assignment = memberAssignment(member)
    await api(`/api/admin/system/authorization/members/${member.tenantMemberId}/assignment`, {
      method: 'POST', body: JSON.stringify({ departmentId: assignment.departmentId ?? null,
        managerTenantMemberId: assignment.managerTenantMemberId ?? null,
        positionTitle: assignment.positionTitle.trim() || null,
        roleIds: assignment.roleIds, expectedVersion: assignment.version }),
    }, systemTokens.value.accessToken)
    success.value = `${member.displayName}的部门和角色已保存；草稿角色须发布后才进入权限上下文。`
    await load()
  } catch (reason) {
    showError(reason, '成员授权保存失败')
  } finally {
    saving.value = false
  }
}

async function saveRole() {
  if (!systemTokens.value?.accessToken || !roleForm.name.trim()) return
  const permissions = (overview.value?.resources ?? []).flatMap(resource => resource.actions
    .filter(action => permissionSelections[permissionKey(resource.resourceType, resource.resourceCode, action)]?.checked)
    .map(action => ({
      resourceType: resource.resourceType, resourceCode: resource.resourceCode, actionCode: action,
      dataScopeType: permissionSelection(resource.resourceType, resource.resourceCode, action).dataScopeType,
      dataScopeJson: null,
    })))
  if (!permissions.length) {
    error.value = '角色至少选择一个菜单或动作权限'
    return
  }
  const fieldPolicies = (overview.value?.resources ?? []).flatMap(resource => resource.fields.flatMap(field =>
    (['PAGE', 'APPLICATION', 'FILE', 'FLOW'] as const)
      .filter(channel => fieldSelections[fieldKey(resource.resourceCode, field, channel)]?.readable || fieldSelections[fieldKey(resource.resourceCode, field, channel)]?.writable)
      .map(channel => ({
        resourceCode: resource.resourceCode, fieldCode: field, channel,
        ...fieldSelections[fieldKey(resource.resourceCode, field, channel)],
        maskStrategy: fieldSelection(resource.resourceCode, field, channel).maskStrategy || null,
      }))))
  saving.value = true
  error.value = ''
  try {
    const saved = await api<AuthorizationRole>('/api/admin/system/authorization/roles', {
      method: 'POST', body: JSON.stringify({ ...roleForm, code: roleForm.code || null, name: roleForm.name.trim(), permissions, fieldPolicies }),
    }, systemTokens.value.accessToken)
    success.value = `角色“${saved.name}”已保存为草稿，尚未影响目标用户。`
    await load(saved.id)
  } catch (reason) {
    showError(reason, '角色草稿保存失败')
  } finally {
    saving.value = false
  }
}

async function publishRole() {
  const role = selectedRole.value
  if (!systemTokens.value?.accessToken || !role || !publishReason.value.trim()) return
  saving.value = true
  error.value = ''
  try {
    await api(`/api/admin/system/authorization/roles/${role.id}/publish`, {
      method: 'POST', body: JSON.stringify({ reason: publishReason.value.trim(), expectedVersion: role.version }),
    }, systemTokens.value.accessToken)
    success.value = `授权版本已发布；目标用户下一次刷新会话时只获得新版本内的菜单、动作和数据范围。`
    await load(role.id)
  } catch (reason) {
    showError(reason, '授权版本发布失败')
  } finally {
    saving.value = false
  }
}

async function runPreview() {
  if (!systemTokens.value?.accessToken || !overview.value || !previewMemberId.value) return
  loading.value = true
  error.value = ''
  success.value = ''
  try {
    preview.value = await api<PermissionPreview>('/api/admin/system/authorization/preview', {
      method: 'POST', body: JSON.stringify({
        tenantMemberId: previewMemberId.value,
        resourceCode: previewResourceCode.value || null,
        actionCode: previewActionCode.value || null,
        expectedPermissionVersion: overview.value.permissionVersion,
      }),
    }, systemTokens.value.accessToken)
    success.value = `已按授权版本 v${preview.value.permissionVersion}完成敏感预览；本次预览只写审计，不修改任何授权。`
  } catch (reason) {
    preview.value = null
    showError(reason, '用户最终权限预览失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <div class="page-heading">
      <div><p class="eyebrow">系统管理</p><h1>{{ pageTitle }}</h1><p>{{ pageDescription }}</p></div>
      <div class="heading-actions"><a-button :loading="loading" @click="load()"><ReloadOutlined />刷新</a-button></div>
    </div>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-alert v-if="success" type="success" show-icon :message="success" class="section-alert" closable @close="success = ''" />
    <a-tabs v-if="section !== 'organization'" v-model:active-key="activeTab">
      <a-tab-pane v-if="section === 'all'" key="organization"><template #tab><TeamOutlined />组织目录</template></a-tab-pane>
      <a-tab-pane key="roles"><template #tab><SafetyCertificateOutlined />角色与数据权限</template></a-tab-pane>
      <a-tab-pane key="preview"><template #tab><SafetyCertificateOutlined />最终权限预览</template></a-tab-pane>
    </a-tabs>
    <a-spin :spinning="loading">
      <div v-if="activeTab === 'organization'" class="authorization-org-grid">
        <section class="panel-card authorization-departments">
          <div class="panel-title"><div><strong>部门</strong><small>按真实汇报关系组织团队</small></div><a-button type="primary" size="small" @click="openDepartment()"><PlusOutlined />新建部门</a-button></div>
          <a-empty v-if="!overview?.departments.length" description="尚未建立部门" />
          <button v-for="department in overview?.departments" :key="department.id" class="department-item" :style="{ paddingLeft: `${14 + departmentDepth(department) * 18}px` }" @click="openDepartment(department)">
            <span>{{ department.name.slice(0, 1) }}</span><div><strong>{{ department.name }}</strong><small>{{ departmentFullName(department.id) }}</small></div><a-tag>{{ overview?.members.filter(member => member.departmentId === department.id).length ?? 0 }} 人</a-tag>
          </button>
        </section>
        <section class="panel-card authorization-members">
          <div class="panel-title"><div><strong>成员目录</strong><small>部门、岗位、直属上级和角色</small></div><div><span>{{ overview?.members.length ?? 0 }} 人</span><a-button type="primary" size="small" @click="openMember"><UserAddOutlined />添加成员</a-button></div></div>
          <article v-for="member in overview?.members" :key="member.tenantMemberId" class="authorization-member-row">
            <div class="authorization-member-identity"><span>{{ member.displayName.slice(0, 1) }}</span><div><strong>{{ member.displayName }}</strong><small>{{ [member.positionTitle, member.departmentName, member.employeeNumber].filter(Boolean).join(' · ') || '尚未完善组织信息' }}<template v-if="member.tenantAdmin"> · 系统管理员</template></small></div></div>
            <DepartmentSelect v-model="memberAssignment(member).departmentId" :departments="directoryDepartments" />
            <a-input v-model:value="memberAssignment(member).positionTitle" placeholder="岗位，例如：销售顾问" />
            <PersonSelect v-model="memberAssignment(member).managerTenantMemberId" value-key="tenantMemberId" :people="directoryPeople" :excluded-values="[member.tenantMemberId]" placeholder="选择直属上级" />
            <a-select v-model:value="memberAssignment(member).roleIds" mode="multiple" placeholder="选择角色" :options="overview?.roles.map(item => ({ value: item.id, label: `${item.name}${item.status === 'DRAFT' ? '（未发布）' : ''}` }))" />
            <a-button type="primary" :loading="saving" @click="saveMember(member)">保存</a-button>
          </article>
        </section>
      </div>
      <div v-else-if="activeTab === 'roles'" class="authorization-role-layout">
        <section class="panel-card authorization-role-list">
          <div class="panel-title"><strong>角色</strong><a-button type="primary" size="small" @click="selectRole(null)"><PlusOutlined />新建</a-button></div>
          <button v-for="role in overview?.roles" :key="role.id" :class="['authorization-role-item', { active: selectedRoleId === role.id }]" @click="selectRole(role.id)">
            <div><strong>{{ role.name }}</strong><small>{{ role.description || (role.builtIn ? '系统内置角色' : '自定义业务角色') }}</small></div><a-tag :color="role.status === 'ACTIVE' ? 'green' : 'processing'">{{ role.status === 'ACTIVE' ? '已发布' : '草稿' }}</a-tag>
          </button>
        </section>
        <section class="authorization-role-editor">
          <div v-if="selectedRole?.builtIn" class="panel-card"><a-result status="info" title="内置角色只读" sub-title="内置管理员角色不可通过普通角色授权流程修改，避免误删最后一个管理入口。" /></div>
          <template v-else>
            <nav class="role-task-nav" aria-label="角色配置步骤">
              <button type="button" :class="{ active: roleStep === 'basic' }" @click="roleStep = 'basic'"><strong>1. 基本信息</strong><small>角色名称和说明</small></button>
              <button type="button" :class="{ active: roleStep === 'permissions' }" @click="roleStep = 'permissions'"><strong>2. 权限范围</strong><small>菜单、动作、字段和数据</small></button>
              <button type="button" :class="{ active: roleStep === 'publish' }" @click="roleStep = 'publish'"><strong>3. 保存与发布</strong><small>核对影响并使配置生效</small></button>
            </nav>
            <section v-if="roleStep === 'basic'" class="panel-card role-basic-form">
              <div class="panel-title"><strong>{{ roleForm.id ? '编辑角色草稿' : '新建角色草稿' }}</strong><span>保存不立即生效</span></div>
              <a-form layout="vertical">
                <a-form-item label="角色名称" required><a-input v-model:value="roleForm.name" placeholder="例如：销售顾问、销售经理、客户只读" /></a-form-item>
                <a-form-item label="说明"><a-textarea v-model:value="roleForm.description" :rows="2" /></a-form-item>
              </a-form>
            </section>
            <section v-else-if="roleStep === 'permissions'" class="panel-card permission-matrix">
              <div class="panel-title"><strong>菜单、动作与数据范围</strong><span>逐动作独立范围</span></div>
              <article v-for="resource in overview?.resources" :key="`${resource.resourceType}:${resource.resourceCode}`" class="permission-resource">
                <div class="permission-resource__name"><strong>{{ resource.name }}</strong><small>页面、操作和数据范围保持同一授权</small></div>
                <div class="permission-actions">
                  <div v-for="action in resource.actions" :key="action">
                    <a-checkbox v-model:checked="permissionSelection(resource.resourceType, resource.resourceCode, action).checked">{{ actionLabel(action, resource) }}</a-checkbox>
                    <a-select v-model:value="permissionSelection(resource.resourceType, resource.resourceCode, action).dataScopeType" size="small" :disabled="!permissionSelection(resource.resourceType, resource.resourceCode, action).checked" :options="dataScopeOptions" />
                  </div>
                </div>
                <div v-if="resource.fields.length" class="field-policy-grid">
                  <a-segmented v-model:value="policyChannel" :options="[{ label: '页面', value: 'PAGE' }, { label: '应用', value: 'APPLICATION' }, { label: '文件', value: 'FILE' }, { label: '流程', value: 'FLOW' }]" />
                  <div v-for="field in resource.fields" :key="`${field}:${policyChannel}`"><strong>{{ resource.fieldNames[field] || '业务字段' }}</strong><a-checkbox v-model:checked="fieldSelection(resource.resourceCode, field).readable">可读</a-checkbox><a-checkbox v-model:checked="fieldSelection(resource.resourceCode, field).writable" @change="keepWritableFieldReadable(resource.resourceCode, field)">可写</a-checkbox><a-select v-model:value="fieldSelection(resource.resourceCode, field).maskStrategy" size="small" allow-clear placeholder="不脱敏" :options="[{ value: 'FULL', label: '完全隐藏' }, { value: 'PARTIAL', label: '部分隐藏' }, { value: 'LAST4', label: '仅显示末四位' }]" /></div>
                </div>
              </article>
            </section>
            <section v-else class="panel-card role-publish-panel">
              <div><strong>受影响菜单预览</strong><p>{{ affectedMenus.join('、') || '尚未选择任何菜单或模块动作' }}</p></div>
              <a-button :loading="saving" @click="saveRole">保存草稿</a-button>
              <a-input v-model:value="publishReason" placeholder="发布原因（必填）" />
              <a-popconfirm title="发布后目标用户刷新会话即应用新权限，确认发布？" ok-text="确认发布" cancel-text="取消" @confirm="publishRole"><a-button type="primary" danger :disabled="!selectedRole || selectedRole.status !== 'DRAFT' || !publishReason.trim()" :loading="saving">发布授权版本</a-button></a-popconfirm>
            </section>
          </template>
        </section>
      </div>
      <div v-else class="authorization-preview">
        <section class="panel-card preview-filters">
          <div class="panel-title"><strong>以成员身份预览最终结果</strong><span>只读操作 · 自动记录审计</span></div>
          <div class="preview-filter-row">
            <PersonSelect v-model="previewMemberId" value-key="tenantMemberId" :people="directoryPeople" placeholder="搜索目标成员" />
            <a-select v-model:value="previewResourceCode" allow-clear placeholder="全部菜单 / 模块" :options="overview?.resources.map(resource => ({ value: resource.resourceCode, label: resource.name }))" @change="previewActionCode = undefined" />
            <a-select v-model:value="previewActionCode" allow-clear placeholder="全部动作" :disabled="!previewResourceCode" :options="previewActionOptions" />
            <a-button type="primary" :disabled="!previewMemberId" :loading="loading" @click="runPreview">执行权限预览</a-button>
          </div>
          <a-alert type="info" show-icon message="菜单和动作、数据范围按角色并集；字段与渠道硬限制取交集；缺少上下文或明确授权时默认拒绝。" />
        </section>
        <template v-if="preview">
          <section class="preview-summary-grid">
            <div class="panel-card"><small>目标成员</small><strong>{{ preview.targetMember.displayName }}</strong><span>{{ [preview.targetMember.positionTitle, preview.targetMember.departmentName].filter(Boolean).join(' · ') || '系统成员' }}</span></div>
            <div class="panel-card"><small>规则状态</small><strong>已生效</strong><span>与实际权限判定一致</span></div>
            <div class="panel-card"><small>有效角色</small><strong>{{ preview.roles.length }}</strong><span>{{ preview.roles.map(role => role.roleName).join('、') || '明确无角色' }}</span></div>
            <div class="panel-card"><small>合并规则</small><strong>并集 + 交集</strong><span>缺失即拒绝</span></div>
          </section>
          <section class="panel-card role-contributions">
            <div class="panel-title"><strong>各角色贡献</strong><span>只列当前筛选范围</span></div>
            <article v-for="role in preview.roles" :key="role.roleId">
              <div><strong>{{ role.roleName }}</strong><small>有效角色贡献</small></div>
              <div class="contribution-tags"><a-tag v-for="permission in role.contributedPermissions" :key="`${permission.resourceType}:${permission.resourceCode}:${permission.actionCode}`">{{ overview?.resources.find(item => item.resourceCode === permission.resourceCode)?.name || '业务资源' }} / {{ actionLabel(permission.actionCode, overview?.resources.find(item => item.resourceCode === permission.resourceCode)) }} / {{ scopeLabel(permission.dataScopeType) }}</a-tag><span v-if="!role.contributedPermissions.length">当前筛选范围无贡献</span></div>
            </article>
          </section>
          <section class="panel-card preview-decisions">
            <div class="panel-title"><strong>动作最终判定</strong><span>逐动作独立数据范围与原因</span></div>
            <article v-for="decision in preview.actions" :key="`${decision.resourceType}:${decision.resourceCode}:${decision.actionCode}`">
              <div><strong>{{ decision.resourceName }} · {{ actionLabel(decision.actionCode, overview?.resources.find(item => item.resourceCode === decision.resourceCode)) }}</strong><small>最终动作判定</small></div>
              <a-tag :color="decision.allowed ? 'green' : 'error'">{{ decision.allowed ? '允许' : '拒绝' }}</a-tag>
              <div><span>{{ decision.dataScope?.terms.map(term => scopeLabel(term.type)).join('、') || '无数据范围' }}</span><small>{{ decision.contributingRoleIds.length ? `${decision.contributingRoleIds.length} 个角色共同贡献` : '没有角色授予' }}</small></div>
              <p>{{ decision.reason }}</p>
            </article>
          </section>
          <section v-if="preview.fields.length" class="panel-card preview-fields">
            <div class="panel-title"><strong>字段与渠道硬限制</strong><span>相关角色策略取交集</span></div>
            <article v-for="field in preview.fields" :key="`${field.resourceCode}:${field.fieldCode}:${field.channel}`">
              <strong>{{ overview?.resources.find(item => item.resourceCode === field.resourceCode)?.fieldNames[field.fieldCode] || '业务字段' }}</strong><a-tag>{{ channelLabel(field.channel) }}</a-tag><a-tag :color="field.readable ? 'green' : 'error'">{{ field.readable ? '可读' : '不可读' }}</a-tag><a-tag :color="field.writable ? 'blue' : 'default'">{{ field.writable ? '可写' : '不可写' }}</a-tag><span>{{ field.maskStrategies.map(item => ({ FULL: '完全隐藏', PARTIAL: '部分隐藏', LAST4: '仅显示末四位' }[item] || item)).join('、') || '不脱敏' }}</span><small>{{ field.reason }}</small>
            </article>
          </section>
        </template>
        <a-empty v-else description="选择用户，可按模块与动作筛选后执行预览" />
      </div>
    </a-spin>
    <a-drawer v-model:open="departmentDrawer" :title="departmentForm.id ? '编辑部门' : '新建部门'" width="460">
      <a-form layout="vertical">
        <a-form-item label="上级部门"><DepartmentSelect v-model="departmentForm.parentId" :departments="directoryDepartments" :excluded-values="departmentForm.id ? [departmentForm.id] : []" placeholder="不选择则作为一级部门" /></a-form-item>
        <a-form-item label="部门名称" required><a-input v-model:value="departmentForm.name" placeholder="例如：销售部、客户成功部" /></a-form-item>
        <a-form-item label="排序"><a-input-number v-model:value="departmentForm.sortOrder" :min="0" /></a-form-item>
        <a-alert type="info" show-icon message="服务端会校验组织环；父部门不能是当前部门或其任何下级。" />
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="departmentDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveDepartment">保存</a-button></div></template>
    </a-drawer>
    <a-modal v-model:open="memberModal" title="添加系统成员" ok-text="确认添加" :confirm-loading="saving" @ok="addMember">
      <a-form layout="vertical">
        <a-alert type="info" show-icon message="输入已注册账号的用户名、邮箱或手机号；内部成员关系由系统自动建立。" class="section-alert" />
        <a-form-item label="账号" required><a-input v-model:value="memberForm.account" placeholder="用户名、邮箱或手机号" /></a-form-item>
        <div class="form-grid"><a-form-item label="员工编号"><a-input v-model:value="memberForm.employeeNumber" placeholder="可选" /></a-form-item><a-form-item label="岗位"><a-input v-model:value="memberForm.positionTitle" placeholder="例如：销售顾问" /></a-form-item></div>
        <a-form-item label="所属部门"><DepartmentSelect v-model="memberForm.departmentId" :departments="directoryDepartments" /></a-form-item>
        <a-form-item label="直属上级"><PersonSelect v-model="memberForm.managerTenantMemberId" value-key="tenantMemberId" :people="directoryPeople" /></a-form-item>
        <a-form-item label="角色"><a-select v-model:value="memberForm.roleIds" mode="multiple" placeholder="可先不分配，成员默认无业务权限" :options="overview?.roles.map(item => ({ value: item.id, label: item.name }))" /></a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
