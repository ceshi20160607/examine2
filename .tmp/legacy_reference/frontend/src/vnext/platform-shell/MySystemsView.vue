<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { InvalidSystemSwitchContextError, useSessionStore } from '@/stores/session'
import type { SystemSummary } from '@/types/session'
import './platform-shell.css'

type SystemEntryState = 'ready' | 'switching' | 'system_disabled' | 'membership_denied' | 'error'

const session = useSessionStore()
const router = useRouter()
const actionError = ref('')
const entryState = ref<SystemEntryState>('ready')
const entryMessage = ref('')
const switchingSystemId = ref<string | null>(null)

const accountName = computed(() => session.context?.account.displayName || session.context?.account.username || '当前账号')
const viewState = computed(() => {
  if (session.systemsState === 'loading') return 'loading'
  if (session.systemsState === 'error') return 'error'
  if (session.systemsState === 'empty_create_first_system_guidance') return 'empty_create_first_system_guidance'
  return entryState.value
})

const statusText: Record<SystemSummary['status'], string> = {
  INITIALIZING: '初始化中',
  INIT_FAILED: '初始化失败',
  ACTIVE: '正常',
  DISABLED: '已停用',
  ARCHIVED: '已归档',
}

function availabilityText(system: SystemSummary) {
  if (system.memberStatus === 'DISABLED') return '成员已停用'
  if (system.memberStatus === 'PENDING') return '成员待激活'
  return statusText[system.status]
}

function roleText(system: SystemSummary) {
  return system.roleNames?.length ? system.roleNames.join('、') : '成员'
}

function recentEntryText(value: string | null | undefined) {
  if (!value) return '暂无记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '时间未知'
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}

async function loadAuthorizedSystems() {
  entryState.value = 'ready'
  entryMessage.value = ''
  await session.loadSystems()
}

function switchFailure(error: unknown) {
  if (error instanceof ApiRequestError) {
    if (['SYSTEM_NOT_FOUND', 'SYSTEM_MEMBER_REQUIRED', 'SYSTEM_MEMBER_DISABLED'].includes(error.code)) {
      entryState.value = 'membership_denied'
      entryMessage.value = '无法进入该系统，请确认当前账号的成员权限。'
      return
    }
    if (error.code === 'SYSTEM_DISABLED') {
      entryState.value = 'system_disabled'
      entryMessage.value = '该系统当前已停用，当前账号不能进入。'
      return
    }
    if (error.code === 'SYSTEM_NO_TENANT') {
      entryState.value = 'error'
      entryMessage.value = '系统入口暂时不可用，请联系管理员检查默认上下文。'
      return
    }
    if (error.status === 403 || error.status === 404) {
      entryState.value = 'membership_denied'
      entryMessage.value = '无法进入该系统，请确认当前账号的成员权限。'
      return
    }
  }
  entryState.value = 'error'
  entryMessage.value = error instanceof InvalidSystemSwitchContextError
    ? '系统返回的上下文不完整，未进入该系统。'
    : '暂时无法进入该系统，请稍后重试。'
}

async function enterSystem(system: SystemSummary) {
  if (entryState.value === 'switching') return
  entryState.value = 'switching'
  entryMessage.value = ''
  switchingSystemId.value = system.id
  try {
    const systemId = await session.switchSystem(system.id)
    await router.replace({ name: 'system-onboarding', params: { systemId } })
  } catch (error) {
    switchFailure(error)
  } finally {
    switchingSystemId.value = null
    if (entryState.value === 'switching') entryState.value = 'ready'
  }
}

async function logout() {
  actionError.value = ''
  try {
    await session.logout()
  } catch {
    actionError.value = '退出请求未完成，本地登录状态已清除。'
  } finally {
    await router.replace({ name: 'login' })
  }
}

onMounted(() => {
  if (session.isPlatform) void loadAuthorizedSystems()
})
</script>

<template>
  <div class="vnext-shell">
    <aside class="vnext-shell__sidebar">
      <div class="vnext-shell__brand">统一管理平台</div>
      <nav aria-label="平台导航">
        <a class="vnext-shell__nav-item is-active" href="/platform/systems" aria-current="page">我的系统</a>
      </nav>
    </aside>

    <div class="vnext-shell__body">
      <header class="vnext-shell__topbar">
        <strong>平台工作台</strong>
        <div class="vnext-shell__account">
          <span data-testid="current-account"><small>当前账号</small>{{ accountName }}</span>
          <button type="button" :disabled="session.loading" @click="logout">
            {{ session.loading ? '正在退出…' : '退出登录' }}
          </button>
        </div>
      </header>

      <main class="vnext-shell__content">
        <header class="vnext-page-header">
          <div>
            <h1>我的系统</h1>
            <p>系统列表根据当前账号的有效成员权限实时获取。</p>
          </div>
        </header>

        <section class="vnext-system-list" aria-labelledby="systems-heading" :data-state="viewState">
          <h2 id="systems-heading" class="sr-only">系统列表</h2>
          <div v-if="session.systemsState === 'loading'" class="vnext-state" role="status">
            正在恢复账号与系统信息…
          </div>
          <div v-else-if="session.systemsState === 'error'" class="vnext-state vnext-state--error" role="alert">
            <strong>系统信息暂时无法加载</strong>
            <span>请检查网络后重试。</span>
            <button type="button" @click="loadAuthorizedSystems">重新加载</button>
          </div>
          <div v-else-if="session.systemsState === 'empty_create_first_system_guidance'" class="vnext-state">
            <strong>暂无可访问的系统</strong>
            <span>请联系管理员确认首个系统或为当前账号开通成员权限。</span>
          </div>
          <div v-else class="vnext-system-list__scroll">
            <p v-if="entryState === 'switching'" class="vnext-system-list__feedback" role="status">
              正在进入所选系统…
            </p>
            <p
              v-else-if="entryMessage"
              class="vnext-system-list__feedback is-error"
              :data-entry-state="entryState"
              role="alert"
            >
              {{ entryMessage }}
            </p>
            <table>
              <thead>
                <tr>
                  <th scope="col">系统</th>
                  <th scope="col">状态</th>
                  <th scope="col">角色</th>
                  <th scope="col">最近进入</th>
                  <th scope="col">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="system in session.systems" :key="system.id">
                  <td>
                    <strong>{{ system.name }}</strong>
                    <span>{{ system.code }}</span>
                  </td>
                  <td><span class="vnext-status" :data-status="system.status">{{ availabilityText(system) }}</span></td>
                  <td>{{ roleText(system) }}</td>
                  <td>{{ recentEntryText(system.recentEnteredAt) }}</td>
                  <td>
                    <button
                      class="vnext-system-list__enter"
                      type="button"
                      :disabled="entryState === 'switching'"
                      :aria-label="`进入系统 ${system.name}`"
                      @click="enterSystem(system)"
                    >
                      {{ switchingSystemId === system.id ? '正在进入…' : '进入系统' }}
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
        <p v-if="actionError" class="vnext-shell__action-error" role="alert">{{ actionError }}</p>
      </main>
    </div>
  </div>
</template>
