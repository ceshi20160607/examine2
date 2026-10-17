<script setup lang="ts">
import { PlusOutlined, ReloadOutlined, SafetyCertificateOutlined, TeamOutlined, UserAddOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { Modal } from 'ant-design-vue'
import { api, ApiError } from '../api'
import { systemTokens } from '../session'
import PersonSelect from '../components/PersonSelect.vue'
import DepartmentSelect from '../components/DepartmentSelect.vue'
import type { AuthorizationDepartment, AuthorizationMember, AuthorizationRole, PermissionPreview, SystemAuthorizationOverview, SystemDirectoryDepartment, SystemPeopleDirectory } from '../types'

// 部门停用前由服务端统计全部业务引用，并声明哪些引用可以转移。
interface DepartmentImpact {
  departmentId: number
  departmentName: string
  references: { key: string; label: string; count: number; transferable: boolean }[]
  canDeactivate: boolean
  canTransfer: boolean
}

// 邀请只在创建时返回一次令牌；列表只展示接受状态和目标范围。
interface MemberInvitation {
  id: number
  targetAccount?: string
  departmentName: string
  roleIds: number[]
  status: string
  expireTime: string
  version: number
}

const overview = ref<SystemAuthorizationOverview | null>(null)
const memberDirectory = ref<SystemPeopleDirectory>({ departments: [], people: [], permissionVersion: 0 })
const props = withDefaults(defineProps<{ section?: 'all' | 'organization' | 'roles' }>(), { section: 'all' })
const activeTab = ref(props.section === 'roles' ? 'roles' : 'organization')
const roleStep = ref<'basic' | 'permissions' | 'publish'>('basic')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')
const departmentDrawer = ref(false)
const departmentImpact = ref<DepartmentImpact | null>(null)
const departmentImpactLoading = ref(false)
const departmentDeactivating = ref(false)
const departmentActivating = ref(false)
const departmentActionError = ref('')
const replacementDepartmentId = ref<number | undefined>()
const draggedDepartmentId = ref<number>()
const departmentDrop = ref<{ targetId: number; placement: 'INSIDE' | 'BEFORE' | 'AFTER' }>()
const movingDepartment = ref(false)
const memberModal = ref(false)
const invitationModal = ref(false)
const invitationSaving = ref(false)
const invitationError = ref('')
const invitationLink = ref('')
const invitations = ref<MemberInvitation[]>([])
const invitationForm = reactive({ targetAccount: '', departmentId: undefined as number | undefined,
  roleIds: [] as number[], employeeNumber: '', positionTitle: '' })
const memberSearch = ref('')
const memberDepartmentFilterId = ref<number>()
const selectedRoleId = ref<number | null>(null)
const permissionSearch = ref('')
const expandedResources = ref<string[]>([])
const copyRoleModal = ref(false)
const copyTargetRoleId = ref<number | 'NEW'>('NEW')
const copyRoleName = ref('')
const publishReason = ref('')
const preview = ref<PermissionPreview | null>(null)
const previewMemberId = ref<number | undefined>()
const previewResourceCode = ref<string | undefined>()
const previewActionCode = ref<string | undefined>()
const policyChannel = ref<'PAGE' | 'APPLICATION' | 'FILE' | 'FLOW'>('PAGE')
const departmentForm = reactive({ id: null as number | null, parentId: undefined as number | undefined, code: '', name: '', sortOrder: 0, expectedVersion: undefined as number | undefined })
const roleForm = reactive({ id: null as number | null, code: '', name: '', description: '', expectedVersion: undefined as number | undefined })
const memberForm = reactive({ account: '', employeeNumber: '', departmentId: undefined as number | undefined, managerTenantMemberId: undefined as number | undefined, positionTitle: '', roleIds: [] as number[] })
interface PermissionSelection {
  checked: boolean
  dataScopeType: string
  dataScopeJson?: string
  memberIds: number[]
  departmentIds: number[]
}
const permissionSelections = reactive<Record<string, PermissionSelection>>({})
const fieldSelections = reactive<Record<string, { readable: boolean; writable: boolean; maskStrategy: string }>>({})
const memberAssignments = reactive<Record<number, { departmentId?: number; managerTenantMemberId?: number; positionTitle: string; roleIds: number[]; version: number }>>({})

const selectedRole = computed(() => overview.value?.roles.find(role => role.id === selectedRoleId.value) ?? null)
const editableRoles = computed(() => overview.value?.roles.filter(role => !role.builtIn) ?? [])
const visibleResources = computed(() => {
  const keyword = permissionSearch.value.trim().toLocaleLowerCase()
  return (overview.value?.resources ?? []).filter(resource => !keyword || [resource.name,
    ...resource.actions.map(action => actionLabel(action, resource)), ...Object.values(resource.fieldNames)]
    .some(value => value.toLocaleLowerCase().includes(keyword)))
})
const selectedDepartment = computed(() => overview.value?.departments.find(item => item.id === departmentForm.id) ?? null)
/** 根据父部门和排序号展示真实树顺序，移动后不依赖部门编码的字典序。 */
const orderedDepartments = computed(() => {
  const departments = overview.value?.departments ?? []
  const result: AuthorizationDepartment[] = []
  const visited = new Set<number>()
  function append(parentId: number | null) {
    departments.filter(item => item.parentId === parentId)
      .sort((left, right) => (left.sortOrder ?? 0) - (right.sortOrder ?? 0) || left.id - right.id)
      .forEach(item => {
        if (visited.has(item.id)) return
        visited.add(item.id)
        result.push(item)
        append(item.id)
      })
  }
  append(null)
  departments.filter(item => !visited.has(item.id)).forEach(item => result.push(item))
  return result
})
const directoryDepartments = computed<SystemDirectoryDepartment[]>(() => (overview.value?.departments ?? []).filter(item => item.status === 'ACTIVE').map(item => ({
  id: item.id, parentId: item.parentId, name: item.name, fullName: departmentFullName(item.id),
  memberCount: overview.value?.members.filter(member => member.departmentId === item.id).length ?? 0,
})))
const departmentImpactReferences = computed(() => departmentImpact.value?.references.filter(item => item.count > 0) ?? [])
const replacementDepartments = computed(() => (overview.value?.departments ?? [])
  .filter(item => item.status === 'ACTIVE' && item.id !== departmentForm.id && !departmentIsDescendant(item.id, departmentForm.id))
  .map(item => ({ value: item.id, label: departmentFullName(item.id) })))
// 管理列表可展示停用成员；选择直属上级和权限预览只使用真实可进入系统的有效成员目录。
const directoryPeople = computed(() => memberDirectory.value.people)
/** 目录使用当前系统真实成员；部门筛选包含下级，搜索匹配业务上可辨认的姓名和工号。 */
const filteredMembers = computed(() => {
  const keyword = memberSearch.value.trim().toLocaleLowerCase()
  return (overview.value?.members ?? []).filter(member => {
    const departmentMatches = memberDepartmentFilterId.value === undefined
      || member.departmentId === memberDepartmentFilterId.value
      || (member.departmentId !== undefined && departmentIsDescendant(member.departmentId, memberDepartmentFilterId.value))
    const searchMatches = !keyword || [member.displayName, member.employeeNumber, member.positionTitle,
      member.departmentName, ...member.roleNames].some(value => value?.toLocaleLowerCase().includes(keyword))
    return departmentMatches && searchMatches
  })
})
const affectedMenus = computed(() => {
  if (!overview.value) return []
  const selected = new Set(Object.entries(permissionSelections).filter(([, value]) => value.checked).map(([key]) => key.split(':').slice(0, 2).join(':')))
  return overview.value.resources.filter(resource => selected.has(`${resource.resourceType}:${resource.resourceCode}`)).map(resource => resource.name)
})
/** 发布影响按已保存草稿和当前成员绑定计算，不将编辑中的未保存选择误报为生效权限。 */
const rolePublishImpact = computed(() => {
  const role = selectedRole.value
  if (!role) return ''
  const members = (overview.value?.members ?? []).filter(member => member.roleIds.includes(role.id) && member.status === 'ACTIVE')
  const names = members.slice(0, 5).map(member => member.displayName).join('、')
  const resources = overview.value?.resources ?? []
  const actions = role.permissions.map(permission => {
    const resource = resources.find(item => item.resourceType === permission.resourceType && item.resourceCode === permission.resourceCode)
    return `${resource?.name || permission.resourceCode}·${actionLabel(permission.actionCode, resource)}`
  })
  const fields = role.fieldPolicies.map(policy => {
    const resource = resources.find(item => item.resourceCode === policy.resourceCode)
    const access = policy.writable ? '可读可写' : policy.readable ? '仅可读' : '不可读写'
    return `${resource?.name || policy.resourceCode}·${resource?.fieldNames[policy.fieldCode] || policy.fieldCode}（${channelLabel(policy.channel)}${access}）`
  })
  const scopes = [...new Set(role.permissions.map(permission => dataScopeOptions.find(option => option.value === permission.dataScopeType)?.label || permission.dataScopeType))]
  return `角色“${role.name}”当前关联 ${members.length} 名有效成员${names ? `（${names}${members.length > 5 ? '等' : ''}）` : ''}。本次发布将以草稿全量替换该角色的旧授权，未列出的旧动作和字段策略会被收回。发布后的 ${actions.length} 项菜单/动作权限：${actions.slice(0, 8).join('、') || '无'}${actions.length > 8 ? `等（共 ${actions.length} 项）` : ''}；${role.fieldPolicies.length} 项字段策略：${fields.slice(0, 8).join('、') || '无'}${fields.length > 8 ? `等（共 ${fields.length} 项）` : ''}；数据范围：${scopes.join('、') || '无'}。目标成员下一次请求立即按新版本判断可见记录、可用动作和字段读写；原有业务记录不改变。取消发布时成员继续使用旧授权版本。`
})
const dataScopeOptions = [
  { value: 'ALL', label: '全部数据' },
  { value: 'DEPARTMENT_AND_DESCENDANTS', label: '本部门及下级' },
  { value: 'DEPARTMENT', label: '本部门' },
  { value: 'SELF_AND_SUBORDINATES', label: '本人及下属' },
  { value: 'SELF', label: '仅本人' },
  { value: 'SPECIFIED_MEMBERS', label: '指定成员负责的数据' },
  { value: 'SPECIFIED_DEPARTMENTS', label: '指定部门的数据' },
  { value: 'CUSTOM', label: '已有自定义范围', disabled: true },
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
    const [authorization, directory] = await Promise.all([
      api<SystemAuthorizationOverview>('/api/admin/system/authorization', {}, systemTokens.value.accessToken),
      api<SystemPeopleDirectory>('/api/system-directory', {}, systemTokens.value.accessToken),
    ])
    overview.value = authorization
    memberDirectory.value = directory
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

async function loadInvitations() {
  if (!systemTokens.value?.accessToken) return
  invitations.value = await api<MemberInvitation[]>('/api/admin/system/authorization/invitations', {}, systemTokens.value.accessToken)
}

async function openInvitation() {
  invitationError.value = ''
  invitationLink.value = ''
  Object.assign(invitationForm, { targetAccount: '', departmentId: directoryDepartments.value[0]?.id,
    roleIds: [], employeeNumber: '', positionTitle: '' })
  invitationModal.value = true
  try { await loadInvitations() } catch (reason) { invitationError.value = reason instanceof ApiError ? reason.message : '邀请列表加载失败' }
}

async function createInvitation() {
  if (!systemTokens.value?.accessToken || !invitationForm.departmentId) return
  invitationSaving.value = true
  invitationError.value = ''
  try {
    const result = await api<{ token: string }>('/api/admin/system/authorization/invitations', {
      method: 'POST', body: JSON.stringify({
        targetAccount: invitationForm.targetAccount.trim() || null,
        departmentId: invitationForm.departmentId, roleIds: invitationForm.roleIds,
        employeeNumber: invitationForm.employeeNumber.trim() || null,
        positionTitle: invitationForm.positionTitle.trim() || null,
      }),
    }, systemTokens.value.accessToken)
    invitationLink.value = `${window.location.origin}/invitations/${result.token}`
    await loadInvitations()
  } catch (reason) {
    invitationError.value = reason instanceof ApiError ? reason.message : '邀请创建失败'
  } finally { invitationSaving.value = false }
}

async function revokeInvitation(invitation: MemberInvitation) {
  if (!systemTokens.value?.accessToken) return
  invitationError.value = ''
  try {
    await api(`/api/admin/system/authorization/invitations/${invitation.id}/revoke`, {
      method: 'POST', body: JSON.stringify({ expectedVersion: invitation.version }),
    }, systemTokens.value.accessToken)
    await loadInvitations()
  } catch (reason) { invitationError.value = reason instanceof ApiError ? reason.message : '撤回邀请失败' }
}

async function copyInvitationLink() {
  if (!invitationLink.value) return
  try { await navigator.clipboard.writeText(invitationLink.value); success.value = '邀请链接已复制' }
  catch { invitationError.value = '复制失败，请从下方文本框手动复制邀请链接' }
}

function permissionKey(resourceType: string, resourceCode: string, action: string) {
  return `${resourceType}:${resourceCode}:${action}`
}

function fieldKey(resourceCode: string, fieldCode: string, channel: string = policyChannel.value) {
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

function departmentIsDescendant(candidateId: number, ancestorId: number | null) {
  if (ancestorId === null) return false
  let current = overview.value?.departments.find(item => item.id === candidateId)
  const visited = new Set<number>()
  while (current?.parentId && !visited.has(current.id)) {
    if (current.parentId === ancestorId) return true
    visited.add(current.id)
    current = overview.value?.departments.find(item => item.id === current?.parentId)
  }
  return false
}

function canMoveDepartment(source: AuthorizationDepartment | undefined, target: AuthorizationDepartment,
  placement: 'INSIDE' | 'BEFORE' | 'AFTER') {
  return !!source && source.parentId !== null && source.status === 'ACTIVE' && target.status === 'ACTIVE'
    && source.id !== target.id && !departmentIsDescendant(target.id, source.id)
    && (placement === 'INSIDE' || target.parentId !== null)
}

function startDepartmentDrag(event: DragEvent, department: AuthorizationDepartment) {
  if (department.parentId === null || department.status !== 'ACTIVE' || movingDepartment.value) {
    event.preventDefault()
    return
  }
  draggedDepartmentId.value = department.id
  event.dataTransfer?.setData('text/plain', String(department.id))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'move'
}

function markDepartmentDrop(event: DragEvent, target: AuthorizationDepartment) {
  const source = overview.value?.departments.find(item => item.id === draggedDepartmentId.value)
  const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const relativeY = (event.clientY - bounds.top) / bounds.height
  const placement = relativeY < 0.25 ? 'BEFORE' : relativeY > 0.75 ? 'AFTER' : 'INSIDE'
  if (!canMoveDepartment(source, target, placement)) {
    departmentDrop.value = undefined
    return
  }
  departmentDrop.value = { targetId: target.id, placement }
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
}

async function moveDepartment(target: AuthorizationDepartment) {
  const source = overview.value?.departments.find(item => item.id === draggedDepartmentId.value)
  const drop = departmentDrop.value
  draggedDepartmentId.value = undefined
  departmentDrop.value = undefined
  if (!systemTokens.value?.accessToken || !source || !drop || drop.targetId !== target.id
    || !canMoveDepartment(source, target, drop.placement)) return
  movingDepartment.value = true
  error.value = ''
  try {
    await api(`/api/admin/system/authorization/departments/${source.id}/move`, {
      method: 'POST', body: JSON.stringify({ targetDepartmentId: target.id,
        placement: drop.placement, expectedVersion: source.version }),
    }, systemTokens.value.accessToken)
    success.value = `部门“${source.name}”的位置已更新。`
    await load()
  } catch (reason) {
    showError(reason, '部门移动失败')
  } finally {
    movingDepartment.value = false
  }
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
  return permissionSelections[key] ??= { checked: false, dataScopeType: 'SELF', memberIds: [], departmentIds: [] }
}

function resourceKey(resourceType: string, resourceCode: string) {
  return `${resourceType}:${resourceCode}`
}

function selectedActionCount(resource: { resourceType: string; resourceCode: string; actions: string[] }) {
  return resource.actions.filter(action => permissionSelection(resource.resourceType, resource.resourceCode, action).checked).length
}

function toggleResource(resourceType: string, resourceCode: string) {
  const key = resourceKey(resourceType, resourceCode)
  expandedResources.value = expandedResources.value.includes(key)
    ? expandedResources.value.filter(item => item !== key) : [...expandedResources.value, key]
}

function savedScopeIds(json: string | undefined, key: 'memberIds' | 'departmentIds'): number[] {
  try {
    const values = JSON.parse(json || '{}')?.[key]
    return Array.isArray(values) ? values.filter((value): value is number => Number.isSafeInteger(value) && value > 0) : []
  } catch { return [] }
}

function scopeCondition(selection: PermissionSelection): string | null {
  if (selection.dataScopeType === 'SPECIFIED_MEMBERS') return JSON.stringify({ memberIds: selection.memberIds })
  if (selection.dataScopeType === 'SPECIFIED_DEPARTMENTS') return JSON.stringify({ departmentIds: selection.departmentIds })
  // 旧自定义条件尚无结构化编辑器；保存角色其他内容时保留原条件。
  return selection.dataScopeType === 'CUSTOM' ? selection.dataScopeJson || null : null
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
  permissionSearch.value = ''
  expandedResources.value = []
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
      permissionSelections[permissionKey(resource.resourceType, resource.resourceCode, action)] = {
        checked: Boolean(saved), dataScopeType: saved?.dataScopeType ?? 'SELF', dataScopeJson: saved?.dataScopeJson,
        memberIds: savedScopeIds(saved?.dataScopeJson, 'memberIds'),
        departmentIds: savedScopeIds(saved?.dataScopeJson, 'departmentIds'),
      }
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

/** 复制只是填入目标角色草稿，管理员检查后沿用现有保存和发布流程。 */
function prepareRoleCopy() {
  if (!selectedRole.value || selectedRole.value.builtIn) return
  copyTargetRoleId.value = 'NEW'
  copyRoleName.value = `${selectedRole.value.name}副本`
  copyRoleModal.value = true
}

function applyRoleCopy() {
  const source = selectedRole.value
  if (!source || source.builtIn) return
  const target = copyTargetRoleId.value === 'NEW' ? null
    : overview.value?.roles.find(role => role.id === copyTargetRoleId.value && !role.builtIn)
  if (copyTargetRoleId.value !== 'NEW' && !target) return
  if (!target && !copyRoleName.value.trim()) {
    error.value = '请填写新角色名称'
    return
  }
  selectRole(target?.id ?? null)
  if (!target) {
    roleForm.name = copyRoleName.value.trim()
    roleForm.description = source.description || ''
  }
  for (const saved of source.permissions) {
    const key = permissionKey(saved.resourceType, saved.resourceCode, saved.actionCode)
    if (!permissionSelections[key]) continue
    permissionSelections[key] = {
      checked: true, dataScopeType: saved.dataScopeType, dataScopeJson: saved.dataScopeJson,
      memberIds: savedScopeIds(saved.dataScopeJson, 'memberIds'),
      departmentIds: savedScopeIds(saved.dataScopeJson, 'departmentIds'),
    }
  }
  for (const saved of source.fieldPolicies) {
    const key = fieldKey(saved.resourceCode, saved.fieldCode, saved.channel)
    if (!fieldSelections[key]) continue
    fieldSelections[key] = { readable: saved.readable, writable: saved.writable,
      maskStrategy: saved.maskStrategy || '' }
  }
  roleStep.value = 'permissions'
  copyRoleModal.value = false
  success.value = `已复制“${source.name}”的权限到${target ? `“${target.name}”` : '新角色'}草稿，请检查后保存并发布。`
}

function openDepartment(department?: AuthorizationDepartment) {
  departmentForm.id = department?.id ?? null
  departmentForm.parentId = department?.parentId
  departmentForm.code = department?.code ?? ''
  departmentForm.name = department?.name ?? ''
  departmentForm.sortOrder = department?.sortOrder ?? 0
  departmentForm.expectedVersion = department?.version
  departmentImpact.value = null
  replacementDepartmentId.value = undefined
  departmentActionError.value = ''
  departmentDrawer.value = true
}

async function inspectDepartmentImpact() {
  const departmentId = departmentForm.id
  if (!systemTokens.value?.accessToken || !departmentId) return
  departmentImpactLoading.value = true
  departmentImpact.value = null
  replacementDepartmentId.value = undefined
  departmentActionError.value = ''
  try {
    const result = await api<DepartmentImpact>(`/api/admin/system/authorization/departments/${departmentId}/impact`, {}, systemTokens.value.accessToken)
    if (departmentForm.id === departmentId) departmentImpact.value = result
  } catch (reason) {
    departmentActionError.value = reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（追踪号：${reason.traceId}）` : ''}` : '部门停用影响检查失败'
  } finally {
    departmentImpactLoading.value = false
  }
}

async function deactivateDepartment() {
  const departmentId = departmentForm.id
  const impact = departmentImpact.value
  if (!systemTokens.value?.accessToken || !departmentId || impact?.departmentId !== departmentId || departmentForm.expectedVersion === undefined) return
  if (!impact.canDeactivate && (!impact.canTransfer || !replacementDepartmentId.value)) return
  departmentDeactivating.value = true
  departmentActionError.value = ''
  error.value = ''
  try {
    await api<AuthorizationDepartment>(`/api/admin/system/authorization/departments/${departmentId}/deactivate`, {
      method: 'POST',
      body: JSON.stringify({ expectedVersion: departmentForm.expectedVersion, replacementDepartmentId: impact.canDeactivate ? null : replacementDepartmentId.value }),
    }, systemTokens.value.accessToken)
    success.value = impact.canDeactivate ? `部门“${impact.departmentName}”已停用。` : `部门“${impact.departmentName}”的引用已转移，并已停用。`
    departmentDrawer.value = false
    departmentImpact.value = null
    await load()
  } catch (reason) {
    departmentActionError.value = reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（追踪号：${reason.traceId}）` : ''}` : '部门停用失败'
  } finally {
    departmentDeactivating.value = false
  }
}

async function activateDepartment() {
  const departmentId = departmentForm.id
  if (!systemTokens.value?.accessToken || !departmentId || departmentForm.expectedVersion === undefined) return
  departmentActivating.value = true
  departmentActionError.value = ''
  try {
    await api<AuthorizationDepartment>(`/api/admin/system/authorization/departments/${departmentId}/activate`, {
      method: 'POST', body: JSON.stringify({ expectedVersion: departmentForm.expectedVersion }),
    }, systemTokens.value.accessToken)
    success.value = `部门“${departmentForm.name}”已启用。`
    departmentDrawer.value = false
    await load()
  } catch (reason) {
    departmentActionError.value = reason instanceof ApiError ? `${reason.message}${reason.traceId ? `（追踪号：${reason.traceId}）` : ''}` : '部门启用失败'
  } finally {
    departmentActivating.value = false
  }
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
  if (Object.values(permissionSelections).some(selection => selection.checked
    && ((selection.dataScopeType === 'SPECIFIED_MEMBERS' && !selection.memberIds.length)
      || (selection.dataScopeType === 'SPECIFIED_DEPARTMENTS' && !selection.departmentIds.length)))) {
    error.value = '指定成员或部门的数据范围至少选择一个有效对象'
    return
  }
  const permissions = (overview.value?.resources ?? []).flatMap(resource => resource.actions
    .filter(action => permissionSelections[permissionKey(resource.resourceType, resource.resourceCode, action)]?.checked)
    .map(action => ({
      resourceType: resource.resourceType, resourceCode: resource.resourceCode, actionCode: action,
      dataScopeType: permissionSelection(resource.resourceType, resource.resourceCode, action).dataScopeType,
      dataScopeJson: scopeCondition(permissionSelection(resource.resourceType, resource.resourceCode, action)),
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
    success.value = `角色“${saved.name}”已保存为草稿，现有成员仍使用已发布权限。`
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
    success.value = `授权版本已发布；目标用户下一次请求会使用新版本的菜单、动作、字段和数据范围。`
    await load(role.id)
  } catch (reason) {
    showError(reason, '授权版本发布失败')
  } finally {
    saving.value = false
  }
}

function confirmPublishRole() {
  if (!selectedRole.value?.draftPending || !publishReason.value.trim()) return
  Modal.confirm({
    title: `发布角色“${selectedRole.value.name}”的权限变更`,
    content: rolePublishImpact.value,
    okText: '确认发布权限', okType: 'danger', cancelText: '取消',
    onOk: () => publishRole(),
  })
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
          <div class="panel-title"><div><strong>部门</strong><small>拖到目标上方/中间/下方可调整同级顺序或设为下级</small></div><a-button type="primary" size="small" @click="openDepartment()"><PlusOutlined />新建部门</a-button></div>
          <a-empty v-if="!overview?.departments.length" description="尚未建立部门" />
          <button v-for="department in orderedDepartments" :key="department.id"
            :data-department-id="department.id"
            :class="['department-item', { 'department-item--dragging': draggedDepartmentId === department.id,
              'department-item--drop-before': departmentDrop?.targetId === department.id && departmentDrop.placement === 'BEFORE',
              'department-item--drop-inside': departmentDrop?.targetId === department.id && departmentDrop.placement === 'INSIDE',
              'department-item--drop-after': departmentDrop?.targetId === department.id && departmentDrop.placement === 'AFTER' }]"
            :style="{ paddingLeft: `${14 + departmentDepth(department) * 18}px` }"
            :draggable="department.parentId !== null && department.status === 'ACTIVE' && !movingDepartment"
            @dragstart="startDepartmentDrag($event, department)" @dragover.prevent="markDepartmentDrop($event, department)"
            @drop.prevent="moveDepartment(department)" @dragend="draggedDepartmentId = undefined; departmentDrop = undefined"
            @click="openDepartment(department)">
            <span>{{ department.name.slice(0, 1) }}</span><div><strong :title="department.name">{{ department.name }}</strong><small :title="departmentFullName(department.id)">{{ departmentFullName(department.id) }}</small></div><a-tag v-if="department.status !== 'ACTIVE'">已停用</a-tag><a-tag>{{ overview?.members.filter(member => member.departmentId === department.id).length ?? 0 }} 人</a-tag>
          </button>
        </section>
        <section class="panel-card authorization-members">
          <div class="panel-title"><div><strong>成员目录</strong><small>部门、岗位、直属上级和角色</small></div><div><span>{{ overview?.members.length ?? 0 }} 人</span><a-button size="small" @click="openInvitation">邀请成员</a-button><a-button type="primary" size="small" @click="openMember"><UserAddOutlined />添加成员</a-button></div></div>
          <div class="authorization-member-filters"><a-input v-model:value="memberSearch" allow-clear placeholder="搜索姓名、工号、岗位或角色" aria-label="搜索成员" /><DepartmentSelect v-model="memberDepartmentFilterId" :departments="directoryDepartments" placeholder="全部部门" /><span>匹配 {{ filteredMembers.length }} / {{ overview?.members.length ?? 0 }} 人</span></div>
          <a-empty v-if="!filteredMembers.length" :description="memberSearch || memberDepartmentFilterId ? '没有匹配的成员' : '尚无成员'" />
          <article v-for="member in filteredMembers" :key="member.tenantMemberId" class="authorization-member-row">
            <div class="authorization-member-identity"><span>{{ member.displayName.slice(0, 1) }}</span><div><strong>{{ member.displayName }}</strong><small>{{ [member.positionTitle, member.departmentName, member.employeeNumber].filter(Boolean).join(' · ') || '尚未完善组织信息' }}<template v-if="member.tenantAdmin"> · 系统管理员</template></small></div></div>
            <div class="authorization-member-field"><span>部门</span><DepartmentSelect v-model="memberAssignment(member).departmentId" :departments="directoryDepartments" :allow-clear="false" /></div>
            <div class="authorization-member-field"><span>岗位</span><a-input v-model:value="memberAssignment(member).positionTitle" placeholder="例如：销售顾问" /></div>
            <div class="authorization-member-field"><span>直属上级</span><PersonSelect v-model="memberAssignment(member).managerTenantMemberId" value-key="tenantMemberId" :people="directoryPeople" :excluded-values="[member.tenantMemberId]" show-department-filter placeholder="选择直属上级" /></div>
            <div class="authorization-member-field"><span>角色</span><a-select v-model:value="memberAssignment(member).roleIds" mode="multiple" placeholder="选择角色" :options="overview?.roles.map(item => ({ value: item.id, label: `${item.name}${item.status === 'DRAFT' ? '（未发布）' : ''}` }))" /></div>
            <a-button class="authorization-member-save" type="primary" :loading="saving" @click="saveMember(member)">保存</a-button>
          </article>
        </section>
      </div>
      <div v-else-if="activeTab === 'roles'" class="authorization-role-layout">
        <section class="panel-card authorization-role-list">
          <div class="panel-title"><strong>角色</strong><div class="authorization-role-tools"><a-button v-if="selectedRole && !selectedRole.builtIn" size="small" @click="prepareRoleCopy">复制权限</a-button><a-button type="primary" size="small" @click="selectRole(null)"><PlusOutlined />新建</a-button></div></div>
          <button v-for="role in overview?.roles" :key="role.id" :class="['authorization-role-item', { active: selectedRoleId === role.id }]" @click="selectRole(role.id)">
            <div><strong>{{ role.name }}</strong><small>{{ role.description || (role.builtIn ? '系统内置角色' : '自定义业务角色') }}</small></div><a-tag :color="role.status === 'ACTIVE' && !role.draftPending ? 'green' : 'processing'">{{ role.status === 'ACTIVE' ? (role.draftPending ? '已发布 · 待发布草稿' : '已发布') : '草稿' }}</a-tag>
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
              <div class="permission-resource-search"><a-input v-model:value="permissionSearch" allow-clear placeholder="搜索菜单、模块、动作或字段" /><span>匹配 {{ visibleResources.length }} 项资源</span></div>
              <a-empty v-if="!visibleResources.length" description="没有匹配的权限资源" />
              <article v-for="resource in visibleResources" :key="resourceKey(resource.resourceType, resource.resourceCode)" class="permission-resource">
                <button type="button" class="permission-resource__name" :aria-expanded="expandedResources.includes(resourceKey(resource.resourceType, resource.resourceCode))" @click="toggleResource(resource.resourceType, resource.resourceCode)">
                  <strong>{{ resource.name }}</strong><small>已选 {{ selectedActionCount(resource) }} / {{ resource.actions.length }} 项动作 · {{ resource.fields.length }} 个字段</small>
                  <span>{{ expandedResources.includes(resourceKey(resource.resourceType, resource.resourceCode)) ? '收起' : '展开配置' }}</span>
                </button>
                <div v-if="expandedResources.includes(resourceKey(resource.resourceType, resource.resourceCode))" class="permission-actions">
                  <div v-for="action in resource.actions" :key="action">
                    <a-checkbox v-model:checked="permissionSelection(resource.resourceType, resource.resourceCode, action).checked">{{ actionLabel(action, resource) }}</a-checkbox>
                    <a-select v-model:value="permissionSelection(resource.resourceType, resource.resourceCode, action).dataScopeType" size="small" :disabled="!permissionSelection(resource.resourceType, resource.resourceCode, action).checked" :options="resource.resourceType === 'MODULE' ? dataScopeOptions : dataScopeOptions.filter(item => !item.value.startsWith('SPECIFIED_'))" />
                    <PersonSelect v-if="permissionSelection(resource.resourceType, resource.resourceCode, action).checked && permissionSelection(resource.resourceType, resource.resourceCode, action).dataScopeType === 'SPECIFIED_MEMBERS'"
                      v-model="permissionSelection(resource.resourceType, resource.resourceCode, action).memberIds" :people="directoryPeople" value-key="systemMemberId" multiple show-department-filter class="permission-scope-target" placeholder="选择可见记录的负责人" />
                    <a-select v-if="permissionSelection(resource.resourceType, resource.resourceCode, action).checked && permissionSelection(resource.resourceType, resource.resourceCode, action).dataScopeType === 'SPECIFIED_DEPARTMENTS'"
                      v-model:value="permissionSelection(resource.resourceType, resource.resourceCode, action).departmentIds" mode="multiple" show-search option-filter-prop="label"
                      :options="memberDirectory.departments.map(item => ({ value: item.id, label: item.fullName }))" class="permission-scope-target" placeholder="选择可见记录的部门" />
                  </div>
                </div>
                <div v-if="expandedResources.includes(resourceKey(resource.resourceType, resource.resourceCode)) && resource.fields.length" class="field-policy-grid">
                  <a-segmented v-model:value="policyChannel" :options="[{ label: '页面', value: 'PAGE' }, { label: '应用', value: 'APPLICATION' }, { label: '文件', value: 'FILE' }, { label: '流程', value: 'FLOW' }]" />
                  <div v-for="field in resource.fields" :key="`${field}:${policyChannel}`"><strong>{{ resource.fieldNames[field] || '业务字段' }}</strong><a-checkbox v-model:checked="fieldSelection(resource.resourceCode, field).readable">可读</a-checkbox><a-checkbox v-model:checked="fieldSelection(resource.resourceCode, field).writable" @change="keepWritableFieldReadable(resource.resourceCode, field)">可写</a-checkbox><a-select v-model:value="fieldSelection(resource.resourceCode, field).maskStrategy" size="small" allow-clear placeholder="不脱敏" :options="[{ value: 'FULL', label: '完全隐藏' }, { value: 'PARTIAL', label: '部分隐藏' }, { value: 'LAST4', label: '仅显示末四位' }]" /></div>
                </div>
              </article>
            </section>
            <section v-else class="panel-card role-publish-panel">
              <div><strong>受影响菜单预览</strong><p>{{ affectedMenus.join('、') || '尚未选择任何菜单或模块动作' }}</p></div>
              <a-button :loading="saving" @click="saveRole">保存草稿</a-button>
              <a-alert v-if="selectedRole?.draftPending" type="warning" show-icon message="发布后立即影响成员权限" :description="rolePublishImpact" />
              <a-input v-model:value="publishReason" placeholder="发布原因（必填）" />
              <a-button type="primary" danger :disabled="!selectedRole?.draftPending || !publishReason.trim()" :loading="saving" @click="confirmPublishRole">发布授权版本</a-button>
            </section>
          </template>
        </section>
      </div>
      <div v-else class="authorization-preview">
        <section class="panel-card preview-filters">
          <div class="panel-title"><strong>以成员身份预览最终结果</strong><span>只读操作 · 自动记录审计</span></div>
          <div class="preview-filter-row">
            <PersonSelect v-model="previewMemberId" value-key="tenantMemberId" :people="directoryPeople" show-department-filter placeholder="搜索目标成员" />
            <a-select v-model:value="previewResourceCode" allow-clear placeholder="全部菜单 / 模块" :options="overview?.resources.map(resource => ({ value: resource.resourceCode, label: resource.name }))" @change="previewActionCode = undefined" />
            <a-select v-model:value="previewActionCode" allow-clear placeholder="全部动作" :disabled="!previewResourceCode" :options="previewActionOptions" />
            <a-button type="primary" :disabled="!previewMemberId" :loading="loading" @click="runPreview">执行权限预览</a-button>
          </div>
          <a-alert type="info" show-icon :message="preview?.systemOwner ? '系统当前所属人按归属隐式拥有全部管理、动作、字段和数据权限。' : '菜单和动作、数据范围按角色并集；字段与渠道硬限制取交集；缺少上下文或明确授权时默认拒绝。'" />
        </section>
        <template v-if="preview">
          <section class="preview-summary-grid">
            <div class="panel-card"><small>目标成员</small><strong>{{ preview.targetMember.displayName }}</strong><span>{{ [preview.targetMember.positionTitle, preview.targetMember.departmentName].filter(Boolean).join(' · ') || '系统成员' }}</span></div>
            <div class="panel-card"><small>规则状态</small><strong>已生效</strong><span>与实际权限判定一致</span></div>
            <div class="panel-card"><small>有效角色</small><strong>{{ preview.systemOwner ? '系统所属人' : preview.roles.length }}</strong><span>{{ preview.systemOwner ? '隐式最高权限，不经过角色配置' : (preview.roles.map(role => role.roleName).join('、') || '明确无角色') }}</span></div>
            <div class="panel-card"><small>判定来源</small><strong>{{ preview.systemOwner ? '系统归属' : '并集 + 交集' }}</strong><span>{{ preview.systemOwner ? '隐式授权' : '缺失即拒绝' }}</span></div>
          </section>
          <section class="panel-card role-contributions">
            <div class="panel-title"><strong>运行态可见业务菜单</strong><span>依据目标成员已生效权限和当前发布目录</span></div>
            <article v-for="menu in preview.visibleMenus" :key="menu.moduleId">
              <div><strong>{{ menu.groupName }} / {{ menu.menuName }}</strong><small>{{ menu.moduleName }}</small></div>
              <a-tag v-if="menu.configurationMode === 'SHARED_RECORDS'">共享记录入口</a-tag>
            </article>
            <p v-if="!preview.visibleMenus.length">当前没有可见的已发布业务模块菜单。</p>
          </section>
          <section class="panel-card role-contributions">
            <div class="panel-title"><strong>各角色贡献</strong><span>只列当前筛选范围</span></div>
            <p v-if="preview.systemOwner">当前系统所属人按归属直接拥有全部权限，无需配置角色。</p>
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
              <div><span>{{ decision.dataScope?.terms.map(term => scopeLabel(term.type)).join('、') || '无数据范围' }}</span><small>{{ preview.systemOwner ? '系统归属直接授权' : (decision.contributingRoleIds.length ? `${decision.contributingRoleIds.length} 个角色共同贡献` : '没有角色授予') }}</small></div>
              <p>{{ decision.reason }}</p>
            </article>
          </section>
          <section v-if="preview.fields.length" class="panel-card preview-fields">
            <div class="panel-title"><strong>字段访问结果</strong><span>{{ preview.systemOwner ? '系统归属直接授权' : '相关角色策略取交集' }}</span></div>
            <article v-for="field in preview.fields" :key="`${field.resourceCode}:${field.actionCode}:${field.fieldCode}:${field.channel}`">
              <strong>{{ overview?.resources.find(item => item.resourceCode === field.resourceCode)?.fieldNames[field.fieldCode] || '业务字段' }}</strong><a-tag>{{ actionLabel(field.actionCode, overview?.resources.find(item => item.resourceCode === field.resourceCode)) }}</a-tag><a-tag>{{ channelLabel(field.channel) }}</a-tag><a-tag :color="field.readable ? 'green' : 'error'">{{ field.readable ? '可读' : '不可读' }}</a-tag><a-tag :color="field.writable ? 'blue' : 'default'">{{ field.writable ? '可写' : '不可写' }}</a-tag><span>{{ field.maskStrategies.map(item => ({ FULL: '完全隐藏', PARTIAL: '部分隐藏', LAST4: '仅显示末四位' }[item] || item)).join('、') || '不脱敏' }}</span><small>{{ field.reason }}</small>
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
      <a-alert v-if="departmentActionError" type="error" show-icon :message="departmentActionError" class="section-alert" />
      <template v-if="selectedDepartment?.status === 'ACTIVE'">
        <a-divider>停用部门</a-divider>
        <a-button :loading="departmentImpactLoading" :disabled="saving || departmentDeactivating" @click="inspectDepartmentImpact">检查停用影响</a-button>
        <template v-if="departmentImpact?.departmentId === departmentForm.id">
          <a-descriptions v-if="departmentImpactReferences.length" title="当前引用" size="small" bordered :column="1" style="margin-top: 16px">
            <a-descriptions-item v-for="reference in departmentImpactReferences" :key="reference.key" :label="reference.label">
              {{ reference.count }} 项 <a-tag :color="reference.transferable ? 'blue' : 'error'">{{ reference.transferable ? '可转移' : '不可转移' }}</a-tag>
            </a-descriptions-item>
          </a-descriptions>
          <a-alert v-if="departmentImpact.canDeactivate" type="success" show-icon message="当前没有阻止停用的引用，可以直接停用。" class="section-alert" />
          <template v-else-if="departmentImpact.canTransfer">
            <a-alert type="warning" show-icon message="该部门仍有引用。请选择接收部门，服务端会在停用前重新检查并转移。" class="section-alert" />
            <a-select v-model:value="replacementDepartmentId" style="width: 100%" show-search option-filter-prop="label" placeholder="选择同一系统中的启用部门" :options="replacementDepartments" />
            <a-alert v-if="!replacementDepartments.length" type="warning" show-icon message="没有可接收引用的启用部门，请先建立或启用其他部门。" class="section-alert" />
          </template>
          <a-alert v-else type="warning" show-icon message="存在不可转移的引用，需先处理这些引用后才能停用。" class="section-alert" />
          <a-popconfirm title="服务端将再次检查引用和版本，确认停用该部门？" ok-text="确认停用" cancel-text="取消" @confirm="deactivateDepartment">
            <a-button danger :loading="departmentDeactivating" :disabled="saving || (!departmentImpact.canDeactivate && (!departmentImpact.canTransfer || !replacementDepartmentId))" style="margin-top: 16px">{{ departmentImpact.canDeactivate ? '停用部门' : departmentImpact.canTransfer ? '转移引用并停用' : '请先处理不可转移引用' }}</a-button>
          </a-popconfirm>
        </template>
      </template>
      <template v-else-if="selectedDepartment?.status === 'DISABLED'">
        <a-divider>恢复部门</a-divider>
        <a-alert type="info" show-icon message="启用后可重新选择该部门；此前转移的数据不会自动转回。" class="section-alert" />
        <a-popconfirm title="确认重新启用该部门？" ok-text="确认启用" cancel-text="取消" @confirm="activateDepartment">
          <a-button :loading="departmentActivating" :disabled="saving">启用部门</a-button>
        </a-popconfirm>
      </template>
      <template #footer><div class="drawer-footer"><a-button @click="departmentDrawer = false">取消</a-button><a-button type="primary" :loading="saving" @click="saveDepartment">保存</a-button></div></template>
    </a-drawer>
    <a-modal v-model:open="copyRoleModal" title="复制角色权限" ok-text="填入目标草稿" @ok="applyRoleCopy">
      <a-form layout="vertical">
        <a-alert type="info" show-icon message="复制后请核对权限并保存草稿；发布前不会改变目标角色的生效权限。" class="section-alert" />
        <a-form-item label="复制到" required><a-select v-model:value="copyTargetRoleId" :options="[{ value: 'NEW', label: '新建角色' }, ...editableRoles.filter(role => role.id !== selectedRoleId).map(role => ({ value: role.id, label: role.name }))]" /></a-form-item>
        <a-form-item v-if="copyTargetRoleId === 'NEW'" label="新角色名称" required><a-input v-model:value="copyRoleName" placeholder="输入新角色名称" /></a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="memberModal" title="添加系统成员" ok-text="确认添加" :confirm-loading="saving" @ok="addMember">
      <a-form layout="vertical">
        <a-alert type="info" show-icon message="新员工先在登录页选择“加入已有系统”注册平台账号，无需创建系统；再输入其用户名、邮箱或手机号完成开通。" class="section-alert" />
        <a-form-item label="账号" required><a-input v-model:value="memberForm.account" placeholder="用户名、邮箱或手机号" /></a-form-item>
        <div class="form-grid"><a-form-item label="员工编号"><a-input v-model:value="memberForm.employeeNumber" placeholder="可选" /></a-form-item><a-form-item label="岗位"><a-input v-model:value="memberForm.positionTitle" placeholder="例如：销售顾问" /></a-form-item></div>
        <a-form-item label="所属部门"><DepartmentSelect v-model="memberForm.departmentId" :departments="directoryDepartments" :allow-clear="false" /></a-form-item>
        <a-form-item label="直属上级"><PersonSelect v-model="memberForm.managerTenantMemberId" value-key="tenantMemberId" :people="directoryPeople" show-department-filter /></a-form-item>
        <a-form-item label="角色"><a-select v-model:value="memberForm.roleIds" mode="multiple" placeholder="可先不分配，成员默认无业务权限" :options="overview?.roles.map(item => ({ value: item.id, label: item.name }))" /></a-form-item>
      </a-form>
    </a-modal>
    <a-modal v-model:open="invitationModal" title="邀请成员加入当前系统" :footer="null" width="640px">
      <a-alert v-if="invitationError" type="error" show-icon :message="invitationError" class="section-alert" />
      <a-alert type="info" show-icon message="邀请在接受后才开通成员身份；链接有效期 7 天，只能使用一次。指定账号可限制接收人；留空时持链接的登录账号可接受。" class="section-alert" />
      <a-form layout="vertical">
        <a-form-item label="指定平台账号（可选）"><a-input v-model:value="invitationForm.targetAccount" placeholder="用户名、邮箱或手机号；留空允许新用户注册后接受" /></a-form-item>
        <div class="form-grid"><a-form-item label="所属部门" required><DepartmentSelect v-model="invitationForm.departmentId" :departments="directoryDepartments" :allow-clear="false" /></a-form-item><a-form-item label="岗位"><a-input v-model:value="invitationForm.positionTitle" placeholder="可选" /></a-form-item></div>
        <div class="form-grid"><a-form-item label="工号"><a-input v-model:value="invitationForm.employeeNumber" placeholder="可选" /></a-form-item><a-form-item label="角色"><a-select v-model:value="invitationForm.roleIds" mode="multiple" placeholder="可暂不授权" :options="overview?.roles.filter(item => item.status === 'ACTIVE').map(item => ({ value: item.id, label: item.name }))" /></a-form-item></div>
        <a-button type="primary" :loading="invitationSaving" :disabled="!invitationForm.departmentId" @click="createInvitation">生成邀请链接</a-button>
      </a-form>
      <div v-if="invitationLink" class="invitation-link"><strong>请立即复制并发送给受邀人</strong><a-input :value="invitationLink" readonly /><a-button @click="copyInvitationLink">复制链接</a-button></div>
      <div class="invitation-history"><strong>最近邀请</strong><a-empty v-if="!invitations.length" description="尚无邀请" />
        <div v-for="invitation in invitations.slice(0, 10)" :key="invitation.id" class="invitation-history-row">
          <div><strong>{{ invitation.targetAccount || '持链接者' }}</strong><small>{{ invitation.departmentName }} · {{ invitation.status === 'PENDING' ? '待接受' : invitation.status === 'ACCEPTED' ? '已接受' : invitation.status === 'REVOKED' ? '已撤回' : '已过期' }} · 截止 {{ invitation.expireTime }}</small></div>
          <a-popconfirm v-if="invitation.status === 'PENDING'" title="撤回这条邀请？" @confirm="revokeInvitation(invitation)"><a-button size="small" danger>撤回</a-button></a-popconfirm>
        </div>
      </div>
    </a-modal>
  </div>
</template>
