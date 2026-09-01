<script setup lang="ts">
import { PlusOutlined, ReloadOutlined, SafetyCertificateOutlined, TeamOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { api, ApiError } from '../api'
import { systemTokens } from '../session'
import type { AuthorizationDepartment, AuthorizationMember, AuthorizationRole, PermissionPreview, SystemAuthorizationOverview } from '../types'

const overview = ref<SystemAuthorizationOverview | null>(null)
const activeTab = ref('organization')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const departmentDrawer = ref(false)
const selectedRoleId = ref<number | null>(null)
const publishReason = ref('')
const preview = ref<PermissionPreview | null>(null)
const previewMemberId = ref<number | undefined>()
const previewResourceCode = ref<string | undefined>()
const previewActionCode = ref<string | undefined>()
const policyChannel = ref<'PAGE' | 'APPLICATION' | 'FILE'>('PAGE')
const departmentForm = reactive({ id: null as number | null, parentId: undefined as number | undefined, code: '', name: '', sortOrder: 0, expectedVersion: undefined as number | undefined })
const roleForm = reactive({ id: null as number | null, code: '', name: '', description: '', expectedVersion: undefined as number | undefined })
const permissionSelections = reactive<Record<string, { checked: boolean; dataScopeType: string }>>({})
const fieldSelections = reactive<Record<string, { readable: boolean; writable: boolean; maskStrategy: string }>>({})
const memberAssignments = reactive<Record<number, { departmentId?: number; roleIds: number[]; version: number }>>({})

const selectedRole = computed(() => overview.value?.roles.find(role => role.id === selectedRoleId.value) ?? null)
const editableRoles = computed(() => overview.value?.roles.filter(role => !role.builtIn) ?? [])
const affectedMenus = computed(() => {
  if (!overview.value) return []
  const selected = new Set(Object.entries(permissionSelections).filter(([, value]) => value.checked).map(([key]) => key.split(':').slice(0, 2).join(':')))
  return overview.value.resources.filter(resource => selected.has(`${resource.resourceType}:${resource.resourceCode}`)).map(resource => resource.name)
})
const dataScopeOptions = [
  { value: 'ALL', label: '全部数据' },
  { value: 'DEPARTMENT_AND_DESCENDANTS', label: '本部门及下级' },
  { value: 'DEPARTMENT', label: '本部门' },
  { value: 'SELF', label: '仅本人' },
]
const previewActionOptions = computed(() => overview.value?.resources
  .find(resource => resource.resourceCode === previewResourceCode.value)?.actions.map(value => ({ value, label: value })) ?? [])

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
      memberAssignments[member.tenantMemberId] = { departmentId: member.departmentId, roleIds: [...member.roleIds], version: member.version }
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
    departmentId: member.departmentId, roleIds: [...member.roleIds], version: member.version,
  }
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
      for (const channel of ['PAGE', 'APPLICATION', 'FILE'] as const) {
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
  if (!systemTokens.value?.accessToken || !departmentForm.code.trim() || !departmentForm.name.trim()) return
  saving.value = true
  error.value = ''
  try {
    await api('/api/admin/system/authorization/departments', {
      method: 'POST', body: JSON.stringify({ ...departmentForm, code: departmentForm.code.trim(), name: departmentForm.name.trim() }),
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

async function saveMember(member: AuthorizationMember) {
  if (!systemTokens.value?.accessToken) return
  saving.value = true
  error.value = ''
  try {
    const assignment = memberAssignment(member)
    await api(`/api/admin/system/authorization/members/${member.tenantMemberId}/assignment`, {
      method: 'POST', body: JSON.stringify({ departmentId: assignment.departmentId ?? null, roleIds: assignment.roleIds, expectedVersion: assignment.version }),
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
  if (!systemTokens.value?.accessToken || !roleForm.code.trim() || !roleForm.name.trim()) return
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
    (['PAGE', 'APPLICATION', 'FILE'] as const)
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
      method: 'POST', body: JSON.stringify({ ...roleForm, code: roleForm.code.trim(), name: roleForm.name.trim(), permissions, fieldPolicies }),
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
      <div><p class="eyebrow">系统后台</p><h1>组织架构与角色权限</h1><p>部门、成员、菜单动作、字段和数据范围全部限定在当前系统与当前租户；未发布草稿不进入权限上下文。</p></div>
      <div class="heading-actions"><a-tag color="blue">权限版本 v{{ overview?.permissionVersion ?? 0 }}</a-tag><a-button :loading="loading" @click="load()"><ReloadOutlined />刷新</a-button></div>
    </div>
    <a-alert v-if="error" type="error" show-icon :message="error" class="section-alert" />
    <a-alert v-if="success" type="success" show-icon :message="success" class="section-alert" closable @close="success = ''" />
    <a-tabs v-model:active-key="activeTab">
      <a-tab-pane key="organization"><template #tab><TeamOutlined />组织与成员</template></a-tab-pane>
      <a-tab-pane key="roles"><template #tab><SafetyCertificateOutlined />角色权限矩阵</template></a-tab-pane>
      <a-tab-pane key="preview"><template #tab><SafetyCertificateOutlined />用户最终权限预览</template></a-tab-pane>
    </a-tabs>
    <a-spin :spinning="loading">
      <div v-if="activeTab === 'organization'" class="authorization-org-grid">
        <section class="panel-card authorization-departments">
          <div class="panel-title"><strong>部门</strong><a-button type="primary" size="small" @click="openDepartment()"><PlusOutlined />新建部门</a-button></div>
          <a-empty v-if="!overview?.departments.length" description="尚未建立部门" />
          <button v-for="department in overview?.departments" :key="department.id" class="department-item" @click="openDepartment(department)">
            <span>{{ department.pathCode.split('/').length }}</span><div><strong>{{ department.name }}</strong><small>{{ department.pathCode }}</small></div><a-tag>{{ department.code }}</a-tag>
          </button>
        </section>
        <section class="panel-card authorization-members">
          <div class="panel-title"><strong>成员归属与角色</strong><span>{{ overview?.members.length ?? 0 }} 人</span></div>
          <article v-for="member in overview?.members" :key="member.tenantMemberId" class="authorization-member-row">
            <div><strong>{{ member.displayName }}</strong><small>{{ member.employeeNumber || `成员 #${member.systemMemberId}` }}<template v-if="member.tenantAdmin"> · 租户管理员</template></small></div>
            <a-select v-model:value="memberAssignment(member).departmentId" allow-clear placeholder="选择部门" :options="overview?.departments.map(item => ({ value: item.id, label: item.name }))" />
            <a-select v-model:value="memberAssignment(member).roleIds" mode="multiple" placeholder="选择角色" :options="overview?.roles.map(item => ({ value: item.id, label: `${item.name}${item.status === 'DRAFT' ? '（草稿）' : ''}` }))" />
            <a-button type="primary" :loading="saving" @click="saveMember(member)">保存</a-button>
          </article>
        </section>
      </div>
      <div v-else-if="activeTab === 'roles'" class="authorization-role-layout">
        <section class="panel-card authorization-role-list">
          <div class="panel-title"><strong>角色</strong><a-button type="primary" size="small" @click="selectRole(null)"><PlusOutlined />新建</a-button></div>
          <button v-for="role in overview?.roles" :key="role.id" :class="['authorization-role-item', { active: selectedRoleId === role.id }]" @click="selectRole(role.id)">
            <div><strong>{{ role.name }}</strong><small>{{ role.code }}</small></div><a-tag :color="role.status === 'ACTIVE' ? 'green' : 'processing'">{{ role.status }}</a-tag>
          </button>
        </section>
        <section class="authorization-role-editor">
          <div v-if="selectedRole?.builtIn" class="panel-card"><a-result status="info" title="内置角色只读" sub-title="内置管理员角色不可通过普通角色授权流程修改，避免误删最后一个管理入口。" /></div>
          <template v-else>
            <section class="panel-card role-basic-form">
              <div class="panel-title"><strong>{{ roleForm.id ? '编辑角色草稿' : '新建角色草稿' }}</strong><span>保存不立即生效</span></div>
              <a-form layout="vertical">
                <div class="form-grid"><a-form-item label="角色编码" required><a-input v-model:value="roleForm.code" /></a-form-item><a-form-item label="角色名称" required><a-input v-model:value="roleForm.name" /></a-form-item></div>
                <a-form-item label="说明"><a-textarea v-model:value="roleForm.description" :rows="2" /></a-form-item>
              </a-form>
            </section>
            <section class="panel-card permission-matrix">
              <div class="panel-title"><strong>菜单、动作与数据范围</strong><span>逐动作独立范围</span></div>
              <article v-for="resource in overview?.resources" :key="`${resource.resourceType}:${resource.resourceCode}`" class="permission-resource">
                <div class="permission-resource__name"><strong>{{ resource.name }}</strong><small>{{ resource.resourceType }}:{{ resource.resourceCode }}</small></div>
                <div class="permission-actions">
                  <div v-for="action in resource.actions" :key="action">
                    <a-checkbox v-model:checked="permissionSelection(resource.resourceType, resource.resourceCode, action).checked">{{ action }}</a-checkbox>
                    <a-select v-model:value="permissionSelection(resource.resourceType, resource.resourceCode, action).dataScopeType" size="small" :disabled="!permissionSelection(resource.resourceType, resource.resourceCode, action).checked" :options="dataScopeOptions" />
                  </div>
                </div>
                <div v-if="resource.fields.length" class="field-policy-grid">
                  <a-segmented v-model:value="policyChannel" :options="[{ label: '页面', value: 'PAGE' }, { label: '应用', value: 'APPLICATION' }, { label: '文件', value: 'FILE' }]" />
                  <div v-for="field in resource.fields" :key="`${field}:${policyChannel}`"><code>{{ field }}</code><a-checkbox v-model:checked="fieldSelection(resource.resourceCode, field).readable">可读</a-checkbox><a-checkbox v-model:checked="fieldSelection(resource.resourceCode, field).writable" @change="keepWritableFieldReadable(resource.resourceCode, field)">可写</a-checkbox><a-input v-model:value="fieldSelection(resource.resourceCode, field).maskStrategy" size="small" placeholder="FULL / PARTIAL / LAST4" /></div>
                </div>
              </article>
            </section>
            <section class="panel-card role-publish-panel">
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
          <div class="panel-title"><strong>按真实规则版本预览</strong><span>只读敏感操作 · 记录审计</span></div>
          <div class="preview-filter-row">
            <a-select v-model:value="previewMemberId" placeholder="选择目标用户" :options="overview?.members.map(member => ({ value: member.tenantMemberId, label: member.displayName }))" />
            <a-select v-model:value="previewResourceCode" allow-clear placeholder="全部菜单 / 模块" :options="overview?.resources.map(resource => ({ value: resource.resourceCode, label: resource.name }))" @change="previewActionCode = undefined" />
            <a-select v-model:value="previewActionCode" allow-clear placeholder="全部动作" :disabled="!previewResourceCode" :options="previewActionOptions" />
            <a-button type="primary" :disabled="!previewMemberId" :loading="loading" @click="runPreview">执行权限预览</a-button>
          </div>
          <a-alert type="info" show-icon message="菜单和动作、数据范围按角色并集；字段与渠道硬限制取交集；缺少上下文或明确授权时默认拒绝。" />
        </section>
        <template v-if="preview">
          <section class="preview-summary-grid">
            <div class="panel-card"><small>目标用户</small><strong>{{ preview.targetMember.displayName }}</strong><span>租户成员 #{{ preview.targetMember.tenantMemberId }}</span></div>
            <div class="panel-card"><small>规则版本</small><strong>v{{ preview.permissionVersion }}</strong><span>与后端真实判定一致</span></div>
            <div class="panel-card"><small>有效角色</small><strong>{{ preview.roles.length }}</strong><span>{{ preview.roles.map(role => role.roleName).join('、') || '明确无角色' }}</span></div>
            <div class="panel-card"><small>合并规则</small><strong>并集 + 交集</strong><span>缺失即拒绝</span></div>
          </section>
          <section class="panel-card role-contributions">
            <div class="panel-title"><strong>各角色贡献</strong><span>只列当前筛选范围</span></div>
            <article v-for="role in preview.roles" :key="role.roleId">
              <div><strong>{{ role.roleName }}</strong><small>{{ role.roleCode }} · #{{ role.roleId }}</small></div>
              <div class="contribution-tags"><a-tag v-for="permission in role.contributedPermissions" :key="`${permission.resourceType}:${permission.resourceCode}:${permission.actionCode}`">{{ permission.resourceCode }} / {{ permission.actionCode }} / {{ permission.dataScopeType }}</a-tag><span v-if="!role.contributedPermissions.length">当前筛选范围无贡献</span></div>
            </article>
          </section>
          <section class="panel-card preview-decisions">
            <div class="panel-title"><strong>动作最终判定</strong><span>逐动作独立数据范围与原因</span></div>
            <article v-for="decision in preview.actions" :key="`${decision.resourceType}:${decision.resourceCode}:${decision.actionCode}`">
              <div><strong>{{ decision.resourceName }} · {{ decision.actionCode }}</strong><small>{{ decision.resourceType }}:{{ decision.resourceCode }}</small></div>
              <a-tag :color="decision.allowed ? 'green' : 'error'">{{ decision.allowed ? '允许' : '拒绝' }}</a-tag>
              <div><span>{{ decision.dataScope?.mode ?? '无数据范围' }}</span><small>角色 {{ decision.contributingRoleIds.join('、') || '无' }}</small></div>
              <p>{{ decision.reason }}</p>
            </article>
          </section>
          <section v-if="preview.fields.length" class="panel-card preview-fields">
            <div class="panel-title"><strong>字段与渠道硬限制</strong><span>相关角色策略取交集</span></div>
            <article v-for="field in preview.fields" :key="`${field.resourceCode}:${field.fieldCode}:${field.channel}`">
              <code>{{ field.resourceCode }}.{{ field.fieldCode }}</code><a-tag>{{ field.channel }}</a-tag><a-tag :color="field.readable ? 'green' : 'error'">{{ field.readable ? '可读' : '不可读' }}</a-tag><a-tag :color="field.writable ? 'blue' : 'default'">{{ field.writable ? '可写' : '不可写' }}</a-tag><span>{{ field.maskStrategies.join('、') || '无脱敏策略' }}</span><small>{{ field.reason }}</small>
            </article>
          </section>
        </template>
        <a-empty v-else description="选择用户，可按模块与动作筛选后执行预览" />
      </div>
    </a-spin>
    <a-drawer v-model:open="departmentDrawer" :title="departmentForm.id ? '编辑部门' : '新建部门'" width="460">
      <a-form layout="vertical">
        <a-form-item label="上级部门"><a-select v-model:value="departmentForm.parentId" allow-clear :options="overview?.departments.filter(item => item.id !== departmentForm.id).map(item => ({ value: item.id, label: `${item.name}（${item.pathCode}）` }))" /></a-form-item>
        <a-form-item label="部门编码" required><a-input v-model:value="departmentForm.code" /></a-form-item>
        <a-form-item label="部门名称" required><a-input v-model:value="departmentForm.name" /></a-form-item>
        <a-form-item label="排序"><a-input-number v-model:value="departmentForm.sortOrder" :min="0" /></a-form-item>
        <a-alert type="info" show-icon message="服务端会校验组织环；父部门不能是当前部门或其任何下级。" />
      </a-form>
      <template #footer><div class="drawer-footer"><a-button @click="departmentDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveDepartment">保存</a-button></div></template>
    </a-drawer>
  </div>
</template>
