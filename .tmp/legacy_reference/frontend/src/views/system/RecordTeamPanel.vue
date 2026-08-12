<script setup lang="ts">
import { ArrowRightLeft, Crown, Plus, RefreshCw, Trash2, UsersRound } from 'lucide-vue-next'
import { computed, ref, watch } from 'vue'

import MemberPicker from '@/components/runtime/MemberPicker.vue'
import { ApiRequestError } from '@/services/api'
import { recordTeamApi } from '@/services/collab'
import { useSessionStore } from '@/stores/session'
import type {
  AssignableRecordTeamRole,
  RecordTeam,
  RecordTeamMember,
} from '@/types/collab'

const props = defineProps<{
  systemId: string
  moduleCode: string
  recordId: string
}>()

const session = useSessionStore()
const team = ref<RecordTeam | null>(null)
const loading = ref(false)
const loaded = ref(false)
const missing = ref(false)
const mutation = ref('')
const error = ref('')
const addOpen = ref(false)
const addMemberId = ref('')
const addRole = ref<AssignableRecordTeamRole>('COLLABORATOR')
const transferOpen = ref(false)
const transferMemberId = ref('')
let loadGeneration = 0

const runtimePermission = computed(() => session.hasPermission('system.runtime.access'))
const viewPermission = computed(() => session.hasPermission(permission('view')))
const canView = computed(() => runtimePermission.value && viewPermission.value)
const canManage = computed(() =>
  runtimePermission.value && session.hasPermission(permission('update')))
const canTransfer = computed(() =>
  runtimePermission.value && session.hasPermission(permission('action.transfer')))
const readOnly = computed(() => canView.value && !canManage.value && !canTransfer.value)
const scopeKey = computed(() => [
  props.systemId,
  props.moduleCode,
  props.recordId,
  session.context?.tenantId ?? '',
  session.context?.permissionVersion ?? '',
].join(':'))

const roleOptions: Array<{ label: string; value: AssignableRecordTeamRole }> = [
  { label: '协作者', value: 'COLLABORATOR' },
  { label: '查看者', value: 'VIEWER' },
  { label: '关注者', value: 'FOLLOWER' },
]

function permission(suffix: string) {
  return `module.${props.moduleCode}.${suffix}`
}

function roleLabel(role: RecordTeamMember['role']) {
  return {
    OWNER: '所有者',
    COLLABORATOR: '协作者',
    VIEWER: '查看者',
    FOLLOWER: '关注者',
  }[role]
}

function roleColor(role: RecordTeamMember['role']) {
  if (role === 'OWNER') return 'gold'
  if (role === 'COLLABORATOR') return 'blue'
  if (role === 'VIEWER') return 'green'
  return 'default'
}

async function loadTeam() {
  const generation = ++loadGeneration
  team.value = null
  missing.value = false
  error.value = ''
  loaded.value = false
  if (!canView.value) {
    loading.value = false
    loaded.value = true
    return
  }
  loading.value = true
  try {
    const result = await recordTeamApi.get(props.systemId, props.moduleCode, props.recordId)
    if (generation !== loadGeneration) return
    team.value = result
  } catch (cause) {
    if (generation !== loadGeneration) return
    if (cause instanceof ApiRequestError && cause.code === 'RECORD_TEAM_NOT_FOUND') {
      missing.value = true
    } else {
      error.value = errorMessage(cause, '协作团队加载失败，请稍后重试。')
    }
  } finally {
    if (generation === loadGeneration) {
      loading.value = false
      loaded.value = true
    }
  }
}

async function initializeTeam() {
  mutation.value = 'initialize'
  error.value = ''
  try {
    const initialized = await recordTeamApi.initialize(
      props.systemId,
      props.moduleCode,
      props.recordId,
    )
    team.value = initialized.team
    missing.value = false
    loaded.value = true
  } catch (cause) {
    error.value = errorMessage(cause, '协作团队初始化失败，请稍后重试。')
  } finally {
    mutation.value = ''
  }
}

async function addMember() {
  const memberId = addMemberId.value.trim()
  if (!memberId) {
    error.value = '请输入成员 ID。'
    return
  }
  mutation.value = 'add'
  error.value = ''
  try {
    team.value = await recordTeamApi.addMember(
      props.systemId,
      props.moduleCode,
      props.recordId,
      { memberId, role: addRole.value },
    )
    addOpen.value = false
    addMemberId.value = ''
    addRole.value = 'COLLABORATOR'
  } catch (cause) {
    error.value = errorMessage(cause, '添加协作成员失败，请稍后重试。')
  } finally {
    mutation.value = ''
  }
}

async function changeRole(member: RecordTeamMember, value: unknown) {
  if (!isAssignableRole(value) || value === member.role) return
  mutation.value = `role:${member.memberId}`
  error.value = ''
  try {
    team.value = await recordTeamApi.changeRole(
      props.systemId,
      props.moduleCode,
      props.recordId,
      member.memberId,
      { role: value },
    )
  } catch (cause) {
    error.value = errorMessage(cause, '成员角色修改失败，请稍后重试。')
  } finally {
    mutation.value = ''
  }
}

async function removeMember(member: RecordTeamMember) {
  mutation.value = `remove:${member.memberId}`
  error.value = ''
  try {
    team.value = await recordTeamApi.removeMember(
      props.systemId,
      props.moduleCode,
      props.recordId,
      member.memberId,
    )
  } catch (cause) {
    error.value = errorMessage(cause, '移除协作成员失败，请稍后重试。')
  } finally {
    mutation.value = ''
  }
}

function openTransfer() {
  transferMemberId.value = ''
  transferOpen.value = true
}

async function transferOwnership() {
  const targetMemberId = transferMemberId.value.trim()
  if (!targetMemberId) {
    error.value = '请输入新所有者的成员 ID。'
    return
  }
  mutation.value = 'transfer'
  error.value = ''
  try {
    team.value = await recordTeamApi.transferOwnership(
      props.systemId,
      props.moduleCode,
      props.recordId,
      { targetMemberId },
    )
    transferOpen.value = false
    transferMemberId.value = ''
  } catch (cause) {
    error.value = errorMessage(cause, '所有权转移失败，请稍后重试。')
  } finally {
    mutation.value = ''
  }
}

function isAssignableRole(value: unknown): value is AssignableRecordTeamRole {
  return value === 'COLLABORATOR' || value === 'VIEWER' || value === 'FOLLOWER'
}

function errorMessage(cause: unknown, fallback: string) {
  if (cause instanceof ApiRequestError) return cause.message || cause.code
  return cause instanceof Error ? cause.message : fallback
}

watch(scopeKey, loadTeam, { immediate: true })
</script>

<template>
  <section class="record-team-panel" aria-label="记录协作团队">
    <header class="team-heading">
      <div>
        <h3><UsersRound :size="19" /> 协作团队</h3>
        <p>为当前记录分配协作者、查看者和关注者。</p>
      </div>
      <div v-if="canView" class="team-heading-actions">
        <a-button
          :loading="loading"
          :disabled="Boolean(mutation)"
          aria-label="刷新协作团队"
          @click="loadTeam"
        >
          <RefreshCw :size="15" />刷新
        </a-button>
        <a-button
          v-if="team && canManage"
          type="primary"
          :disabled="Boolean(mutation)"
          @click="addOpen = true"
        >
          <Plus :size="15" />添加成员
        </a-button>
        <a-button
          v-if="team && canTransfer"
          :disabled="Boolean(mutation)"
          @click="openTransfer"
        >
          <ArrowRightLeft :size="15" />转移所有权
        </a-button>
      </div>
    </header>

    <a-alert
      v-if="!canView"
      type="warning"
      show-icon
      message="无权查看协作团队"
      :description="`需要 system.runtime.access 和 ${permission('view')} 权限。`"
    />
    <template v-else>
      <a-alert
        v-if="readOnly"
        type="info"
        show-icon
        message="当前为只读模式"
        description="你可以查看团队，但没有管理成员或转移所有权的权限。"
      />
      <a-alert
        v-if="error"
        type="error"
        show-icon
        closable
        :message="error"
        @close="error = ''"
      />

      <a-spin :spinning="loading">
        <div v-if="missing && loaded" class="team-empty-state">
          <a-empty description="当前记录尚未初始化协作团队" />
          <a-button
            v-if="canManage"
            type="primary"
            :loading="mutation === 'initialize'"
            :disabled="Boolean(mutation) && mutation !== 'initialize'"
            @click="initializeTeam"
          >
            初始化协作团队
          </a-button>
          <p v-else>需要 {{ permission('update') }} 权限才能初始化。</p>
        </div>
        <a-empty
          v-else-if="loaded && !team"
          description="当前没有可显示的协作团队"
        />
        <a-empty
          v-else-if="team && team.members.length === 0"
          description="协作团队暂时没有成员"
        />
        <div v-else-if="team" class="team-list">
          <article
            v-for="member in team.members"
            :key="member.memberId"
            class="team-member"
          >
            <div class="member-identity">
              <Crown v-if="member.role === 'OWNER'" :size="17" />
              <span>
                <strong>成员 {{ member.memberId }}</strong>
                <small v-if="member.memberId === session.context?.memberId">当前成员</small>
              </span>
            </div>
            <div class="member-role">
              <a-tag :color="roleColor(member.role)">{{ roleLabel(member.role) }}</a-tag>
              <a-select
                v-if="canManage && member.role !== 'OWNER'"
                :value="member.role"
                :options="roleOptions"
                :loading="mutation === `role:${member.memberId}`"
                :disabled="Boolean(mutation)"
                aria-label="协作角色"
                @change="changeRole(member, $event)"
              />
            </div>
            <a-popconfirm
              v-if="canManage && member.role !== 'OWNER'"
              title="确认将该成员移出协作团队？"
              ok-text="移除"
              cancel-text="取消"
              @confirm="removeMember(member)"
            >
              <a-button
                danger
                :loading="mutation === `remove:${member.memberId}`"
                :disabled="Boolean(mutation)"
                :aria-label="`移除成员 ${member.memberId}`"
              >
                <Trash2 :size="15" />移除
              </a-button>
            </a-popconfirm>
          </article>
        </div>
      </a-spin>
    </template>

    <a-modal
      v-model:open="addOpen"
      title="添加协作成员"
      :confirm-loading="mutation === 'add'"
      ok-text="添加"
      cancel-text="取消"
      @ok="addMember"
    >
      <a-form layout="vertical">
        <a-form-item label="成员" required>
          <MemberPicker
            v-model:value="addMemberId"
            :system-id="systemId"
            placeholder="搜索并选择协作成员"
          />
        </a-form-item>
        <a-form-item label="协作角色" required>
          <a-select v-model:value="addRole" :options="roleOptions" />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="transferOpen"
      title="转移记录所有权"
      :confirm-loading="mutation === 'transfer'"
      ok-text="确认转移"
      cancel-text="取消"
      @ok="transferOwnership"
    >
      <a-alert
        class="transfer-warning"
        type="warning"
        show-icon
        message="转移后，当前所有者将变为协作者。"
      />
      <a-form layout="vertical">
        <a-form-item label="新所有者" required>
          <MemberPicker
            v-model:value="transferMemberId"
            :system-id="systemId"
            placeholder="搜索并选择新所有者"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </section>
</template>

<style scoped>
.record-team-panel {
  display: grid;
  gap: 14px;
  margin-top: 22px;
  padding-top: 20px;
  border-top: 1px solid #e2e8f0;
}

.team-heading,
.team-heading-actions,
.team-member,
.member-identity,
.member-role,
.team-heading h3 {
  display: flex;
  align-items: center;
}

.team-heading {
  justify-content: space-between;
  gap: 16px;
}

.team-heading h3 {
  gap: 8px;
  margin: 0;
}

.team-heading p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.team-heading-actions,
.member-role {
  gap: 8px;
}

.team-list {
  display: grid;
  gap: 8px;
}

.team-member {
  min-height: 58px;
  justify-content: space-between;
  gap: 14px;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  background: #fff;
}

.member-identity {
  min-width: 0;
  gap: 9px;
}

.member-identity > span {
  display: grid;
  min-width: 0;
}

.member-identity strong,
.member-identity small {
  overflow-wrap: anywhere;
}

.member-identity small,
.team-empty-state p {
  color: #64748b;
}

.member-role {
  margin-left: auto;
}

.member-role .ant-select {
  width: 120px;
}

.team-empty-state {
  display: grid;
  justify-items: center;
  gap: 10px;
  padding-bottom: 18px;
  text-align: center;
}

.transfer-warning {
  margin-bottom: 16px;
}
</style>
