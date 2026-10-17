<script setup lang="ts">
import { ApartmentOutlined, AppstoreOutlined, BellOutlined, CheckSquareOutlined, DashboardOutlined, LogoutOutlined, ProjectOutlined, RobotOutlined, SettingOutlined, UserOutlined } from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import ProductMark from './ProductMark.vue'
import WorkspaceContext from './WorkspaceContext.vue'
import { api } from '../api'
import { clearSession, platformContext, platformTokens } from '../session'
import { computed } from 'vue'
import { buildPlatformNavigation, hasResourceAccess, platformAdminEntryPath } from '../permissions'
import CommandCenter from './CommandCenter.vue'
import type { CommandCenterItem } from '../types'

const router = useRouter()
const route = useRoute()
const navigation = computed(() => buildPlatformNavigation(platformContext.value?.permissions))
const showTodos = computed(() => hasResourceAccess(platformContext.value?.permissions, 'TODO'))
const showMessages = computed(() => hasResourceAccess(platformContext.value?.permissions, 'MESSAGE'))
const managementPath = computed(() => platformAdminEntryPath(platformContext.value?.permissions))
const notice = computed(() => ({
  'access-denied': { message: '当前账号不能打开该页面', description: '已返回平台首页；导航只显示当前账号可以使用的功能。' },
  'route-unavailable': { message: '原地址不可用', description: '已返回平台首页，可从左侧导航重新进入。' },
  'system-context-required': { message: '请先选择系统', description: '进入系统业务前，需要先从“系统”选择可用系统和工作空间。' },
  'system-context-changed': { message: '系统上下文已变化', description: '原地址不属于当前系统，请重新选择要进入的系统。' },
}[String(route.query.notice || '')]))
const navigationIcons = {
  home: DashboardOutlined,
  systems: AppstoreOutlined,
  flow: ApartmentOutlined,
  applications: AppstoreOutlined,
  tasks: ProjectOutlined,
  assistant: RobotOutlined,
}
async function logout() {
  try {
    if (platformTokens.value?.accessToken) {
      await api('/api/auth/logout', { method: 'POST' }, platformTokens.value.accessToken)
    }
  } finally {
    clearSession()
    await router.replace('/login')
  }
}

function dismissNotice() {
  const query = { ...route.query }
  delete query.notice
  void router.replace({ path: route.path, query })
}

function executeCommand(command: CommandCenterItem) {
  const routes: Partial<Record<CommandCenterItem['target'], string>> = {
    PLATFORM_SYSTEMS: '/platform', PLATFORM_DASHBOARD: '/platform/dashboard', PLATFORM_FLOWS: '/platform/flows',
    PLATFORM_APPLICATIONS: '/platform/applications', PLATFORM_TASKS: '/platform/tasks', PLATFORM_AI: '/platform/ai',
    PLATFORM_TODOS: '/platform/todos', PLATFORM_MESSAGES: '/platform/messages', PLATFORM_PROFILE: '/profile',
    PLATFORM_ADMIN: managementPath.value || undefined,
  }
  const path = routes[command.target]
  if (path) void router.push(path)
}
</script>

<template>
  <div class="platform-shell">
    <header class="topbar topbar--platform">
      <ProductMark />
      <WorkspaceContext scope="platform" name="全部工作" subtitle="跨系统汇总与协作" />
      <div class="topbar__actions">
        <CommandCenter context="platform" @execute="executeCommand" />
        <a-button v-if="showTodos" type="text" class="topbar-action" @click="router.push('/platform/todos')"><CheckSquareOutlined /><span>待办</span></a-button>
        <a-button v-if="showMessages" type="text" class="topbar-action" @click="router.push('/platform/messages')"><BellOutlined /><span>消息</span></a-button>
        <a-dropdown>
          <a-button type="text" class="user-trigger"><a-avatar size="small"><UserOutlined /></a-avatar>{{ platformContext?.displayName || platformContext?.username }}</a-button>
          <template #overlay>
            <a-menu>
              <a-menu-item @click="router.push('/profile')"><UserOutlined /> 个人中心</a-menu-item>
              <a-menu-divider />
              <a-menu-item @click="logout"><LogoutOutlined /> 退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </header>
    <div class="platform-frame">
      <aside class="side-nav">
        <div>
          <p class="workspace-nav-title">平台工作区</p>
          <nav>
            <button
              v-for="item in navigation"
              :key="item.label"
              type="button"
              class="side-nav__item"
              :class="{ 'side-nav__item--active': item.path === '/platform' ? route.path === item.path : route.path === item.path || !!(item.path && route.path.startsWith(`${item.path}/`)) }"
              @click="router.push(item.path)"
            >
              <component :is="navigationIcons[item.code as keyof typeof navigationIcons]" class="side-nav__icon" /><span>{{ item.label }}</span>
            </button>
            <button type="button" class="side-nav__item" :class="{ 'side-nav__item--active': route.path === '/platform/my-dashboard' }" @click="router.push('/platform/my-dashboard')"><UserOutlined class="side-nav__icon" /><span>我的首页</span></button>
          </nav>
        </div>
        <div class="side-nav__footer">
          <button v-if="managementPath" type="button" class="side-nav__manage" @click="router.push(managementPath)"><SettingOutlined /><span><strong>平台管理</strong><small>身份、配置与运行管理</small></span></button>
          <div class="side-nav__scope"><strong>平台范围</strong><span>汇总获权系统与个人工作，不混入单个系统业务。</span></div>
        </div>
      </aside>
      <main class="workspace">
        <a-alert v-if="notice" type="warning" show-icon closable :message="notice.message" :description="notice.description" class="workspace-notice" @close="dismissNotice" />
        <slot />
      </main>
    </div>
  </div>
</template>
