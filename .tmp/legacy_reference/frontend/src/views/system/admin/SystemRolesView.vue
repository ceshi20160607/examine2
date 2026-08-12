<script setup lang="ts">
import { message } from 'ant-design-vue'
import { CheckCircle2, Plus, RefreshCw, Save, Search, Send, ShieldCheck } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { useAdminViewport } from '@/composables/useAdminViewport'
import { ApiRequestError } from '@/services/api'
import { systemAdminApi } from '@/services/admin'
import { useSessionStore } from '@/stores/session'
import type { DataScope, PermissionDefinition, PermissionEvaluation, Role, SystemMember } from '@/types/admin'

const { isMobile } = useAdminViewport()
const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const systemId = computed(() => String(route.params.systemId))
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const roles = ref<Role[]>([])
const permissions = ref<PermissionDefinition[]>([])
const dataScopes = ref<DataScope[]>([])
const selectedId = ref('')
const checkedPermissions = ref<string[]>([])
const deniedPermissions = ref<string[]>([])
const createOpen = ref(false)
const evaluationOpen = ref(false)
const principalLoading = ref(false)
const evaluationLoading = ref(false)
const evaluationError = ref('')
const members = ref<SystemMember[]>([])
const selectedMemberId = ref<string>()
const selectedTenantId = ref<string>()
const evaluation = ref<PermissionEvaluation>()
const rolePublished = ref(false)
const createForm = reactive({ code: '', name: '', description: '' })
const roleForm = reactive({ name: '', description: '', dataScopeId: undefined as string | undefined, version: '' })
const selectedRole = computed(() => roles.value.find((role) => role.id === selectedId.value))
const selectedMember = computed(() => members.value.find((member) => member.id === selectedMemberId.value))
const memberOptions = computed(() => members.value.map((member) => ({
  label: `${member.displayName}${member.username ? ` (${member.username})` : ''}`,
  value: member.id,
})))
const tenantOptions = computed(() => (selectedMember.value?.tenantIds ?? []).map((tenantId) => ({
  label: session.tenants.find((tenant) => tenant.id === tenantId)?.name ?? `租户 ${tenantId}`,
  value: tenantId,
})))
const dataScopeOptions = computed(() => dataScopes.value.map((scope) => ({ label: `${scope.name} (${scope.kind})`, value: scope.id })))
const permissionPrefix = computed(() => {
  const value = route.query.permissionPrefix
  return typeof value === 'string' ? value.trim() : Array.isArray(value) ? String(value[0] ?? '').trim() : ''
})
const permissionSearch = ref(permissionPrefix.value)
const filteredPermissions = computed(() => {
  const term = permissionSearch.value.trim().toLowerCase()
  if (!term) return permissions.value
  return permissions.value.filter((permission) => [permission.code, permission.name, permission.type]
    .some((value) => value.toLowerCase().includes(term)))
})
const permissionGroups = computed(() => {
  const groups = new Map<string, PermissionDefinition[]>()
  filteredPermissions.value.forEach((permission) => groups.set(permission.type, [...(groups.get(permission.type) ?? []), permission]))
  return [...groups.entries()]
})
const focusedPermissionCount = computed(() => permissionPrefix.value
  ? permissions.value.filter((permission) => permission.type === 'FIELD' && permission.code.startsWith(permissionPrefix.value)).length
  : 0)
const permissionTargetModule = computed(() => permissionPrefix.value.match(/^module\.([^.]+)\.field\.[^.]+\.$/)?.[1] ?? '')

function reportError(error: unknown) {
  errorMessage.value = error instanceof ApiRequestError ? error.message : '角色权限加载失败'
}

watch([selectedRole, permissions], ([role, definitions]) => {
  if (!role) return
  const editableCodes = new Set(definitions.map((permission) => permission.code))
  Object.assign(roleForm, { name: role.name, description: role.description ?? '', dataScopeId: role.dataScopeId, version: role.version })
  checkedPermissions.value = role.permissionCodes.filter((code) => editableCodes.has(code))
  deniedPermissions.value = (role.deniedPermissionCodes ?? []).filter((code) => editableCodes.has(code))
  rolePublished.value = false
}, { immediate: true })

watch(permissionPrefix, (prefix) => {
  permissionSearch.value = prefix
}, { immediate: true })

watch(checkedPermissions, (values) => {
  const filtered = deniedPermissions.value.filter((code) => !values.includes(code))
  if (filtered.length !== deniedPermissions.value.length) deniedPermissions.value = filtered
}, { deep: true })

watch(deniedPermissions, (values) => {
  const filtered = checkedPermissions.value.filter((code) => !values.includes(code))
  if (filtered.length !== checkedPermissions.value.length) checkedPermissions.value = filtered
}, { deep: true })

watch(selectedMemberId, () => {
  evaluation.value = undefined
  evaluationError.value = ''
  const tenantIds = selectedMember.value?.tenantIds ?? []
  const currentTenantId = session.context?.tenantId
  selectedTenantId.value = currentTenantId && tenantIds.includes(currentTenantId) ? currentTenantId : tenantIds[0]
})

watch(selectedTenantId, () => {
  evaluation.value = undefined
  evaluationError.value = ''
})

async function openPermissionEvaluation() {
  evaluationOpen.value = true
  if (members.value.length || principalLoading.value) return
  principalLoading.value = true
  evaluationError.value = ''
  try {
    const result = await systemAdminApi.listMembers(systemId.value, { page: 1, size: 200 })
    members.value = result.items
  } catch (error) {
    evaluationError.value = error instanceof ApiRequestError ? error.message : '成员列表加载失败'
  } finally {
    principalLoading.value = false
  }
}

async function evaluatePermissions() {
  if (!selectedMemberId.value || !selectedTenantId.value) return
  evaluationLoading.value = true
  evaluationError.value = ''
  evaluation.value = undefined
  try {
    evaluation.value = await systemAdminApi.getPermissionEvaluation(systemId.value, selectedMemberId.value, selectedTenantId.value)
  } catch (error) {
    evaluationError.value = error instanceof ApiRequestError ? error.message : '权限预览加载失败'
  } finally {
    evaluationLoading.value = false
  }
}

function sourceRoleLabel(roleId: string) {
  const role = evaluation.value?.sourceRoles.find((item) => item.id === roleId)
  return role ? `${role.name} (${role.code})` : roleId
}

function permissionFocused(permission: PermissionDefinition) {
  return permission.type === 'FIELD' && Boolean(permissionPrefix.value) && permission.code.startsWith(permissionPrefix.value)
}

function returnToConfiguration() {
  router.push({
    name: 'system-admin-configuration',
    params: { systemId: systemId.value },
    query: permissionPrefix.value ? { permissionPrefix: permissionPrefix.value } : undefined,
  })
}

function openRuntimeVerification() {
  router.push({
    name: 'system-workbench',
    params: { systemId: systemId.value },
    query: permissionTargetModule.value ? { module: permissionTargetModule.value } : undefined,
  })
}

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [roleResult, permissionResult, dataScopeResult] = await Promise.all([
      systemAdminApi.listRoles(systemId.value, { page: 1, size: 200 }),
      systemAdminApi.listPermissions(systemId.value, { page: 1, size: 500, status: 'ACTIVE' }),
      systemAdminApi.listDataScopes(systemId.value, { page: 1, size: 200 }),
    ])
    roles.value = roleResult.items
    permissions.value = permissionResult.items
    dataScopes.value = dataScopeResult.items
    if (!roles.value.some((role) => role.id === selectedId.value)) selectedId.value = roles.value[0]?.id ?? ''
  } catch (error) {
    reportError(error)
  } finally {
    loading.value = false
  }
}

function replaceRole(updated: Role) {
  const index = roles.value.findIndex((role) => role.id === updated.id)
  if (index >= 0) roles.value[index] = updated
}

async function saveDraft() {
  const role = selectedRole.value
  if (!role) return
  saving.value = true
  rolePublished.value = false
  try {
    const allowed = [...new Set(checkedPermissions.value)]
    const allowedSet = new Set(allowed)
    const denied = [...new Set(deniedPermissions.value)].filter((code) => !allowedSet.has(code))
    replaceRole(await systemAdminApi.saveRoleDraft(systemId.value, role.id, {
      name: roleForm.name,
      description: roleForm.description,
      dataScopeId: roleForm.dataScopeId,
      permissionCodes: allowed,
      deniedPermissionCodes: denied,
      version: roleForm.version,
    }))
    message.success('角色草稿已保存')
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

async function checkDraft() {
  const role = selectedRole.value
  if (!role) return
  saving.value = true
  rolePublished.value = false
  try {
    replaceRole(await systemAdminApi.checkRoleDraft(systemId.value, role.id, role.version))
    message.success('草稿检查通过')
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

async function publishDraft() {
  const role = selectedRole.value
  if (!role) return
  saving.value = true
  try {
    replaceRole(await systemAdminApi.publishRoleDraft(systemId.value, role.id, role.version))
    await session.refreshContext()
    rolePublished.value = true
    message.success('系统角色已发布')
  } catch (error) {
    reportError(error)
  } finally {
    saving.value = false
  }
}

async function createRole() {
  if (!createForm.code.trim() || !createForm.name.trim()) return
  saving.value = true
  try {
    const role = await systemAdminApi.createRole(systemId.value, createForm)
    roles.value.unshift(role)
    selectedId.value = role.id
    createOpen.value = false
    Object.assign(createForm, { code: '', name: '', description: '' })
    message.success('系统角色已创建')
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
    <AdminPageHeader title="角色权限" description="配置菜单、动作、字段与数据范围，检查通过后统一发布。">
      <template v-if="!isMobile" #actions><a-button @click="openPermissionEvaluation"><ShieldCheck :size="16" />权限预览</a-button><a-button :loading="loading" @click="load"><RefreshCw :size="16" />刷新</a-button><a-button type="primary" @click="createOpen = true"><Plus :size="16" />新增角色</a-button></template>
    </AdminPageHeader>
    <a-alert v-if="errorMessage" class="admin-alert" type="error" show-icon :message="errorMessage" />
    <template v-if="isMobile">
      <AdminMobileNotice />
      <div class="mobile-record-list"><article v-for="role in roles" :key="role.id" class="mobile-record"><div class="mobile-record-title"><strong>{{ role.name }}</strong><a-tag>{{ role.draftStatus }}</a-tag></div><dl><dt>编码</dt><dd>{{ role.code }}</dd><dt>允许</dt><dd>{{ role.permissionCodes.length }} 项</dd><dt>拒绝</dt><dd>{{ role.deniedPermissionCodes?.length ?? 0 }} 项</dd><dt>数据范围</dt><dd>{{ dataScopes.find((scope) => scope.id === role.dataScopeId)?.name || '未配置' }}</dd></dl></article></div>
      <a-empty v-if="!loading && !roles.length" description="暂无角色" />
    </template>
    <div v-else class="role-workspace">
      <aside class="role-list" aria-label="系统角色列表"><button v-for="role in roles" :key="role.id" type="button" :class="{ active: role.id === selectedId }" @click="selectedId = role.id"><span><strong>{{ role.name }}</strong><small>{{ role.code }}</small></span><a-tag>{{ role.draftStatus }}</a-tag></button><a-empty v-if="!loading && !roles.length" description="暂无角色" /></aside>
      <div v-if="selectedRole" class="role-editor">
        <div class="role-editor-heading"><div><h2>{{ selectedRole.name }}</h2><span>发布版本 {{ selectedRole.publishedVersion || '未发布' }}</span></div><div class="role-editor-actions"><a-button class="role-draft-save" aria-label="保存角色草稿" :loading="saving" @click="saveDraft"><Save :size="15" />保存草稿</a-button><a-button class="role-draft-check" aria-label="检查角色草稿" :loading="saving" @click="checkDraft"><CheckCircle2 :size="15" />检查</a-button><a-button class="role-draft-publish" aria-label="发布角色草稿" type="primary" :disabled="selectedRole.draftStatus !== 'CHECKED'" :loading="saving" @click="publishDraft"><Send :size="15" />发布</a-button></div></div>
        <div class="role-form-row"><label>角色名称<a-input v-model:value="roleForm.name" /></label><label>数据范围<a-select v-model:value="roleForm.dataScopeId" allow-clear :options="dataScopeOptions" /></label><label class="role-description-field">说明<a-input v-model:value="roleForm.description" /></label></div>
        <div class="role-permission-toolbar">
          <a-input v-model:value="permissionSearch" class="role-permission-search" aria-label="权限搜索" allow-clear placeholder="按名称、编码或类型搜索权限" />
          <span>{{ filteredPermissions.length }} / {{ permissions.length }} 项 ACTIVE 权限</span>
        </div>
        <a-alert v-if="permissionPrefix" class="role-permission-focus-notice" :type="focusedPermissionCount ? 'info' : 'warning'" show-icon :message="focusedPermissionCount ? `已定位 ${focusedPermissionCount} 项字段权限` : '未找到对应的 ACTIVE 字段权限'" :description="focusedPermissionCount ? '完成允许或拒绝选择后，依次保存草稿、检查并发布角色。' : '请确认字段已经以 STAGED 模式检查并发布配置，然后刷新本页。'" />
        <div class="permission-matrix"><section v-for="[type, items] in permissionGroups" :key="type"><h3>{{ type }}</h3><div class="permission-effect-columns"><div><strong>允许</strong><a-checkbox-group v-model:value="checkedPermissions"><div class="permission-options"><div v-for="permission in items" :key="permission.code" class="role-permission-row" :class="{ focused: permissionFocused(permission) }" :data-permission-code="permission.code" :data-focused="permissionFocused(permission)"><a-checkbox class="role-permission-allow" :data-permission-code="permission.code" :aria-label="`允许 ${permission.code}`" :value="permission.code"><span>{{ permission.name }}</span><small>{{ permission.code }}</small></a-checkbox></div></div></a-checkbox-group></div><div><strong>拒绝</strong><a-checkbox-group v-model:value="deniedPermissions"><div class="permission-options"><div v-for="permission in items" :key="permission.code" class="role-permission-row" :class="{ focused: permissionFocused(permission) }" :data-permission-code="permission.code" :data-focused="permissionFocused(permission)"><a-checkbox class="role-permission-deny" :data-permission-code="permission.code" :aria-label="`拒绝 ${permission.code}`" :value="permission.code"><span>{{ permission.name }}</span><small>{{ permission.code }}</small></a-checkbox></div></div></a-checkbox-group></div></div></section><a-empty v-if="!filteredPermissions.length" description="没有匹配的 ACTIVE 权限" /></div>
        <div v-if="rolePublished" class="role-publish-next-steps">
          <span>角色已发布且当前会话已刷新。返回配置将 STAGED 改为 ENFORCED，发布后再进入运行工作台验证。</span>
          <a-button class="role-return-configuration" aria-label="返回字段配置" @click="returnToConfiguration">返回字段配置</a-button>
          <a-button class="role-open-runtime" aria-label="前往业务工作台验证" type="primary" @click="openRuntimeVerification">运行验证</a-button>
        </div>
      </div>
      <div v-else class="admin-empty-state"><p>选择一个角色开始配置。</p></div>
    </div>
    <a-modal v-model:open="createOpen" title="新增系统角色" :confirm-loading="saving" @ok="createRole"><a-form layout="vertical"><a-form-item label="角色名称" required><a-input v-model:value="createForm.name" /></a-form-item><a-form-item label="角色编码" required><a-input v-model:value="createForm.code" /></a-form-item><a-form-item label="说明"><a-textarea v-model:value="createForm.description" :rows="3" /></a-form-item></a-form></a-modal>
    <a-drawer v-model:open="evaluationOpen" title="系统成员权限预览" :width="720">
      <div class="permission-evaluation-toolbar system-evaluation-toolbar">
        <a-select v-model:value="selectedMemberId" class="permission-principal-select" show-search option-filter-prop="label" :loading="principalLoading" :options="memberOptions" placeholder="选择成员" not-found-content="暂无可预览成员" />
        <a-select v-model:value="selectedTenantId" class="permission-tenant-select" :disabled="!selectedMemberId || !tenantOptions.length" :options="tenantOptions" placeholder="授权租户" />
        <a-button type="primary" :disabled="!selectedMemberId || !selectedTenantId" :loading="evaluationLoading" @click="evaluatePermissions"><Search :size="15" />查看权限</a-button>
      </div>
      <a-alert v-if="evaluationError" class="permission-evaluation-alert" type="error" show-icon :message="evaluationError" />
      <a-alert v-else-if="selectedMemberId && !tenantOptions.length" class="permission-evaluation-alert" type="warning" show-icon message="该成员尚未分配可用租户" />
      <a-spin :spinning="principalLoading || evaluationLoading">
        <a-empty v-if="!principalLoading && !members.length" description="暂无可预览成员" />
        <a-empty v-else-if="!evaluation && !evaluationLoading" description="选择成员和授权租户后查看已发布权限" />
        <div v-else-if="evaluation" class="permission-evaluation">
          <a-descriptions :column="2" bordered size="small">
            <a-descriptions-item label="成员">{{ memberOptions.find((item) => item.value === evaluation?.principalId)?.label || evaluation.principalId }}</a-descriptions-item>
            <a-descriptions-item label="授权租户">{{ tenantOptions.find((item) => item.value === evaluation?.tenantId)?.label || evaluation.tenantId }}</a-descriptions-item>
            <a-descriptions-item label="权限版本" :span="2">{{ evaluation.permissionVersion || '无' }}</a-descriptions-item>
          </a-descriptions>
          <section>
            <h3>来源角色</h3>
            <div v-if="evaluation.sourceRoles.length" class="permission-evaluation-rows">
              <div v-for="role in evaluation.sourceRoles" :key="role.id" class="permission-evaluation-row"><span><strong>{{ role.name }}</strong><small>{{ role.code }}</small></span><a-tag>版本 {{ role.publishedVersion }}</a-tag></div>
            </div>
            <a-empty v-else :image="null" description="没有已发布来源角色" />
          </section>
          <section>
            <h3>数据范围</h3>
            <div v-if="evaluation.dataScopes.length" class="permission-evaluation-rows">
              <div v-for="scope in evaluation.dataScopes" :key="`${scope.roleId}:${scope.id}`" class="permission-evaluation-row"><span><strong>{{ scope.code }}</strong><small>{{ sourceRoleLabel(scope.roleId) }}</small></span><a-tag color="blue">{{ scope.kind }}</a-tag></div>
            </div>
            <a-empty v-else :image="null" description="没有数据范围" />
          </section>
          <section>
            <h3>权限决策</h3>
            <div v-if="evaluation.decisions.length" class="permission-decision-list">
              <div v-for="decision in evaluation.decisions" :key="decision.permissionCode" class="permission-decision-row"><code>{{ decision.permissionCode }}</code><a-tag :color="decision.result === 'ALLOW' ? 'green' : 'red'">{{ decision.result }}</a-tag><span>{{ decision.reason || '无补充说明' }}</span><small>{{ decision.sourceRoleIds.map(sourceRoleLabel).join('、') || '无来源角色' }}</small></div>
            </div>
            <a-empty v-else :image="null" description="没有权限决策" />
          </section>
        </div>
      </a-spin>
    </a-drawer>
  </section>
</template>
