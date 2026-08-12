<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'
import './onboarding.css'

type OnboardingState = 'loading' | 'ready' | 'admin_settings_only' | 'permission_denied' | 'error'

const route = useRoute()
const router = useRouter()
const session = useSessionStore()
const actionError = ref('')

const routeSystemId = computed(() => String(route.params.systemId ?? ''))
const currentSystem = computed(() => session.context?.systemName || '当前系统')
const currentAccount = computed(() => session.context?.account.displayName || session.context?.account.username || '当前账号')
const currentRole = computed(() => {
  const system = session.systems.find((item) => item.id === session.context?.systemId)
  return system?.roleNames.length ? system.roleNames.join('、') : '身份信息暂不可用'
})
function hasCompleteSnapshot() {
  const context = session.context
  return Boolean(
    context
    && Array.isArray(context.roleIds)
    && context.roleIds.length > 0
    && context.dataScope?.id
    && context.dataScope.code
    && context.dataScope.kind
    && ['NONE', 'ADMIN_SETTINGS_ONLY'].includes(context.restrictedMode),
  )
}
const onboardingState = computed<OnboardingState>(() => {
  if (session.bootstrapping || session.systemsState === 'loading') return 'loading'
  if (session.systemsState === 'error') return 'error'
  const context = session.context
  if (
    context?.type !== 'SYSTEM'
    || context.systemId !== routeSystemId.value
    || !context.shells.includes('SYSTEM_ADMIN')
    || !hasCompleteSnapshot()
  ) {
    return 'permission_denied'
  }
  if (context.restrictedMode === 'ADMIN_SETTINGS_ONLY') {
    return context.permissions.length === 1
      && context.permissions[0] === 'system.admin.access'
      && context.shells.length === 1
      ? 'admin_settings_only'
      : 'permission_denied'
  }
  return context.permissions.includes('system.runtime.access')
    && context.permissions.includes('system.admin.access')
    && context.shells.length === 2
    && context.shells.includes('SYSTEM_RUNTIME')
    ? 'ready'
    : 'permission_denied'
})

async function retryContext() {
  actionError.value = ''
  await session.reloadContext()
  if (!session.authenticated) await router.replace({ name: 'login' })
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
</script>

<template>
  <div class="vnext-onboarding" :data-state="onboardingState">
    <aside class="vnext-onboarding__sidebar">
      <div class="vnext-onboarding__brand">统一管理平台</div>
      <nav aria-label="系统导航">
        <a class="vnext-onboarding__nav-item is-active" :href="`/systems/${routeSystemId}/admin/onboarding`" aria-current="page">配置引导</a>
      </nav>
    </aside>

    <div class="vnext-onboarding__body">
      <header class="vnext-onboarding__topbar">
        <span><small>当前系统</small>{{ currentSystem }}</span>
        <div class="vnext-onboarding__account">
          <span><small>当前账号</small>{{ currentAccount }}</span>
          <button type="button" :disabled="session.loading" @click="logout">
            {{ session.loading ? '正在退出…' : '退出登录' }}
          </button>
        </div>
      </header>

      <main class="vnext-onboarding__content">
        <section v-if="onboardingState === 'loading'" class="vnext-onboarding__state" role="status">
          <strong>正在恢复系统上下文</strong>
          <p>请稍候。</p>
        </section>

        <section v-else-if="onboardingState === 'error'" class="vnext-onboarding__state is-error" role="alert">
          <strong>系统上下文暂时无法加载</strong>
          <p>请检查网络后重新加载。</p>
          <button type="button" @click="retryContext">重新加载</button>
        </section>

        <section v-else-if="onboardingState === 'permission_denied'" class="vnext-onboarding__state is-denied" role="alert">
          <strong>无法打开配置引导</strong>
          <p>当前账号没有此系统的管理权限。</p>
        </section>

        <section v-else-if="onboardingState === 'admin_settings_only'" class="vnext-onboarding__state" role="status">
          <strong>系统已停用，仅可进行管理设置</strong>
          <p>当前仅开放管理设置；当前账号仍可确认管理上下文，系统运行能力未开放。</p>
        </section>

        <section v-else class="vnext-onboarding__welcome" aria-labelledby="onboarding-title">
          <p class="vnext-onboarding__eyebrow">系统上下文已就绪</p>
          <h1 id="onboarding-title">欢迎进入 {{ currentSystem }}</h1>
          <p>系统创建完成，当前账号与系统管理权限已确认。</p>
          <dl>
            <div><dt>当前系统</dt><dd>{{ currentSystem }}</dd></div>
            <div><dt>当前账号</dt><dd>{{ currentAccount }}</dd></div>
            <div><dt>当前身份</dt><dd>{{ currentRole }}</dd></div>
          </dl>
        </section>

        <p v-if="actionError" class="vnext-onboarding__action-error" role="alert">{{ actionError }}</p>
      </main>
    </div>
  </div>
</template>
