<script setup lang="ts">
import { message } from 'ant-design-vue'
import { CheckCircle2, Plus, RefreshCw, Search, Send, Save, ShieldCheck } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref, watch } from 'vue'

import AdminMobileNotice from '@/components/admin/AdminMobileNotice.vue'
import AdminPageHeader from '@/components/admin/AdminPageHeader.vue'
import { useAdminViewport } from '@/composables/useAdminViewport'
import { ApiRequestError } from '@/services/api'
import { platformAdminApi } from '@/services/admin'
import type { PermissionDefinition, PermissionEvaluation, PlatformAccount, Role } from '@/types/admin'

const { isMobile } = useAdminViewport()
const loading = ref(false)
const saving = ref(false)
const errorMessage = ref('')
const roles = ref<Role[]>([])
const permissions = ref<PermissionDefinition[]>([])
const selectedId = ref('')
const checkedPermissions = ref<string[]>([])
const deniedPermissions = ref<string[]>([])
const createOpen = ref(false)
const evaluationOpen = ref(false)
const principalLoading = ref(false)
const evaluationLoading = ref(false)
const evaluationError = ref('')
const accounts = ref<PlatformAccount[]>([])
const selectedAccountId = ref<string>()
const evaluation = ref<PermissionEvaluation>()
const createForm = reactive({ code: '', name: '', description: '' })
const roleForm = reactive({ name: '', description: '', version: '' })
const selectedRole = computed(() => roles.value.find((role) => role.id === selectedId.value))
const accountOptions = computed(() => accounts.value.map((account) => ({
  label: `${account.displayName} (${account.username})`,
  value: account.id,
})))
const permissionGroups = computed(() => {
  const groups = new Map<string, PermissionDefinition[]>()
  permissions.value.forEach((permission) => groups.set(permission.type, [...(groups.get(permission.type) ?? []), permission]))
  return [...groups.entries()]
})

function reportError(error: unknown) {
  errorMessage.value = error instanceof ApiRequestError ? error.message : '角色权限加载失败'
}

function selectRole(role: Role) {
  selectedId.value = role.id
}

watch(selectedRole, (role) => {
  if (!role) return
  Object.assign(roleForm, { name: role.name, description: role.description ?? '', version: role.version })
  checkedPermissions.value = [...role.permissionCodes]
  deniedPermissions.value = [...(role.deniedPermissionCodes ?? [])]
}, { immediate: true })

watch(checkedPermissions, (values) => {
  const filtered = deniedPermissions.value.filter((code) => !values.includes(code))
  if (filtered.length !== deniedPermissions.value.length) deniedPermissions.value = filtered
}, { deep: true })

watch(deniedPermissions, (values) => {
  const filtered = checkedPermissions.value.filter((code) => !values.includes(code))
  if (filtered.length !== checkedPermissions.value.length) checkedPermissions.value = filtered
}, { deep: true })

watch(selectedAccountId, () => {
  evaluation.value = undefined
  evaluationError.value = ''
})

async function openPermissionEvaluation() {
  evaluationOpen.value = true
  if (accounts.value.length || principalLoading.value) return
  principalLoading.value = true
  evaluationError.value = ''
  try {
    const result = await platformAdminApi.listAccounts({ page: 1, size: 200 })
    accounts.value = result.items
  } catch (error) {
    evaluationError.value = error instanceof ApiRequestError ? error.message : '账号列表加载失败'
  } finally {
    principalLoading.value = false
  }
}

async function evaluatePermissions() {
  if (!selectedAccountId.value) return
  evaluationLoading.value = true
  evaluationError.value = ''
  evaluation.value = undefined
  try {
    evaluation.value = await platformAdminApi.getPermissionEvaluation(selectedAccountId.value)
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

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const [roleResult, permissionResult] = await Promise.all([
      platformAdminApi.listRoles({ page: 1, size: 200 }),
      platformAdminApi.listPermissions({ page: 1, size: 500 }),
    ])
    roles.value = roleResult.items
    permissions.value = permissionResult.items
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
  try {
    const allowed = [...new Set(checkedPermissions.value)]
    const allowedSet = new Set(allowed)
    const denied = [...new Set(deniedPermissions.value)].filter((code) => !allowedSet.has(code))
    const updated = await platformAdminApi.saveRoleDraft(role.id, { name: roleForm.name, description: roleForm.description, permissionCodes: allowed, deniedPermissionCodes: denied, version: roleForm.version })
    replaceRole(updated)
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
  try {
    replaceRole(await platformAdminApi.checkRoleDraft(role.id, role.version))
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
    replaceRole(await platformAdminApi.publishRoleDraft(role.id, role.version))
    message.success('角色权限已发布')
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
    const role = await platformAdminApi.createRole(createForm)
    roles.value.unshift(role)
    selectedId.value = role.id
    createOpen.value = false
    Object.assign(createForm, { code: '', name: '', description: '' })
    message.success('平台角色已创建')
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
    <AdminPageHeader title="平台角色" description="角色权限先保存草稿，通过检查后发布到运行快照。">
      <template v-if="!isMobile" #actions><a-button @click="openPermissionEvaluation"><ShieldCheck :size="16" />权限预览</a-button><a-button :loading="loading" @click="load"><RefreshCw :size="16" />刷新</a-button><a-button type="primary" @click="createOpen = true"><Plus :size="16" />新增角色</a-button></template>
    </AdminPageHeader>
    <a-alert v-if="errorMessage" class="admin-alert" type="error" show-icon :message="errorMessage" />
    <template v-if="isMobile">
      <AdminMobileNotice />
      <div class="mobile-record-list"><article v-for="role in roles" :key="role.id" class="mobile-record"><div class="mobile-record-title"><strong>{{ role.name }}</strong><a-tag>{{ role.draftStatus }}</a-tag></div><dl><dt>编码</dt><dd>{{ role.code }}</dd><dt>允许</dt><dd>{{ role.permissionCodes.length }} 项</dd><dt>拒绝</dt><dd>{{ role.deniedPermissionCodes?.length ?? 0 }} 项</dd><dt>成员</dt><dd>{{ role.memberCount }}</dd></dl></article></div>
      <a-empty v-if="!loading && !roles.length" description="暂无角色" />
    </template>
    <div v-else class="role-workspace">
      <aside class="role-list" aria-label="平台角色列表">
        <button v-for="role in roles" :key="role.id" type="button" :class="{ active: role.id === selectedId }" @click="selectRole(role)"><span><strong>{{ role.name }}</strong><small>{{ role.code }}</small></span><a-tag>{{ role.draftStatus }}</a-tag></button>
        <a-empty v-if="!loading && !roles.length" description="暂无角色" />
      </aside>
      <div v-if="selectedRole" class="role-editor">
        <div class="role-editor-heading"><div><h2>{{ selectedRole.name }}</h2><span>发布版本 {{ selectedRole.publishedVersion || '未发布' }}</span></div><div class="role-editor-actions"><a-button :loading="saving" @click="saveDraft"><Save :size="15" />保存草稿</a-button><a-button :loading="saving" @click="checkDraft"><CheckCircle2 :size="15" />检查</a-button><a-button type="primary" :disabled="selectedRole.draftStatus !== 'CHECKED'" :loading="saving" @click="publishDraft"><Send :size="15" />发布</a-button></div></div>
        <div class="role-form-row"><label>角色名称<a-input v-model:value="roleForm.name" /></label><label>说明<a-input v-model:value="roleForm.description" /></label></div>
        <div class="permission-matrix">
          <section v-for="[type, items] in permissionGroups" :key="type"><h3>{{ type }}</h3><div class="permission-effect-columns"><div><strong>允许</strong><a-checkbox-group v-model:value="checkedPermissions"><div class="permission-options"><a-checkbox v-for="permission in items" :key="permission.code" :value="permission.code"><span>{{ permission.name }}</span><small>{{ permission.code }}</small></a-checkbox></div></a-checkbox-group></div><div><strong>拒绝</strong><a-checkbox-group v-model:value="deniedPermissions"><div class="permission-options"><a-checkbox v-for="permission in items" :key="permission.code" :value="permission.code"><span>{{ permission.name }}</span><small>{{ permission.code }}</small></a-checkbox></div></a-checkbox-group></div></div></section>
        </div>
      </div>
      <div v-else class="admin-empty-state"><p>选择一个角色开始配置。</p></div>
    </div>
    <a-modal v-model:open="createOpen" title="新增平台角色" :confirm-loading="saving" @ok="createRole"><a-form layout="vertical"><a-form-item label="角色名称" required><a-input v-model:value="createForm.name" /></a-form-item><a-form-item label="角色编码" required><a-input v-model:value="createForm.code" /></a-form-item><a-form-item label="说明"><a-textarea v-model:value="createForm.description" :rows="3" /></a-form-item></a-form></a-modal>
    <a-drawer v-model:open="evaluationOpen" title="平台账号权限预览" :width="720">
      <div class="permission-evaluation-toolbar">
        <a-select v-model:value="selectedAccountId" class="permission-principal-select" show-search option-filter-prop="label" :loading="principalLoading" :options="accountOptions" placeholder="选择账号" not-found-content="暂无可预览账号" />
        <a-button type="primary" :disabled="!selectedAccountId" :loading="evaluationLoading" @click="evaluatePermissions"><Search :size="15" />查看权限</a-button>
      </div>
      <a-alert v-if="evaluationError" class="permission-evaluation-alert" type="error" show-icon :message="evaluationError" />
      <a-spin :spinning="principalLoading || evaluationLoading">
        <a-empty v-if="!principalLoading && !accounts.length" description="暂无可预览账号" />
        <a-empty v-else-if="!evaluation && !evaluationLoading" description="选择账号后查看其已发布权限" />
        <div v-else-if="evaluation" class="permission-evaluation">
          <a-descriptions :column="2" bordered size="small">
            <a-descriptions-item label="账号">{{ accountOptions.find((item) => item.value === evaluation?.principalId)?.label || evaluation.principalId }}</a-descriptions-item>
            <a-descriptions-item label="权限版本">{{ evaluation.permissionVersion || '无' }}</a-descriptions-item>
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
