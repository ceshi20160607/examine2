<script setup lang="ts">
import { ApartmentOutlined, BellOutlined, CheckSquareOutlined, DatabaseOutlined, DownOutlined, FolderOpenOutlined, HomeOutlined, ProjectOutlined, RobotOutlined, SettingOutlined, UserOutlined } from '@ant-design/icons-vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ProductMark from './ProductMark.vue'
import WorkspaceContext from './WorkspaceContext.vue'
import CommandCenter from './CommandCenter.vue'
import { userFacingWorkspaceName } from '../presentation'
import { systemContext } from '../session'
import type { CommandCenterItem } from '../types'

withDefaults(defineProps<{
  systemName?: string
  tenantName?: string
  activeKey?: string
  showRuntime?: boolean
  showFlow?: boolean
  showApplications?: boolean
  showTasks?: boolean
  showTodos?: boolean
  showMessages?: boolean
  showFiles?: boolean
  showAi?: boolean
  showAdmin?: boolean
  tenantSwitchable?: boolean
  tenantOptions?: Array<{ id: number; name: string; main: boolean }>
  tenantSwitching?: boolean
}>(), {
  showRuntime: false,
  showFlow: false,
  showApplications: false,
  showTasks: false,
  showTodos: false,
  showMessages: false,
  showFiles: false,
  showAi: false,
  showAdmin: false,
  tenantSwitchable: false,
  tenantOptions: () => [],
  tenantSwitching: false,
})
const emit = defineEmits<{ navigate: [key: string]; command: [command: CommandCenterItem]; switchTenant: [tenantId: number] }>()
const router = useRouter()
const route = useRoute()
const notice = computed(() => ({
  'access-denied': { message: '当前身份不能打开该工作区', description: '已返回系统首页；左侧只显示当前身份可以使用的功能。' },
  'route-unavailable': { message: '原地址不可用', description: '已返回当前系统首页，可从左侧重新进入业务模块。' },
}[String(route.query.notice || '')]))

function dismissNotice() {
  const query = { ...route.query }
  delete query.notice
  void router.replace({ path: route.path, query })
}
</script>

<template>
  <div class="system-shell">
    <header class="topbar topbar--system">
      <ProductMark />
      <button class="workspace-context-button" type="button" aria-label="切换系统" title="返回系统目录并切换系统" @click="router.push('/platform')">
        <WorkspaceContext scope="system" :name="systemName || '当前系统'" :subtitle="userFacingWorkspaceName(tenantName)" interactive />
      </button>
      <div class="topbar__actions">
        <a-dropdown v-if="tenantSwitchable" trigger="click" placement="bottomRight">
          <a-button type="text" class="topbar-action tenant-switch-trigger" :loading="tenantSwitching" aria-label="切换租户">
            <ApartmentOutlined /><span class="tenant-switch-label">{{ userFacingWorkspaceName(tenantName) }}</span><DownOutlined />
          </a-button>
          <template #overlay>
            <a-menu @click="emit('switchTenant', Number($event.key))">
              <a-menu-item v-for="tenant in tenantOptions" :key="String(tenant.id)" :disabled="tenant.id === systemContext?.tenantId || tenantSwitching">
                {{ userFacingWorkspaceName(tenant.name) }}{{ tenant.main ? ' · 主租户' : '' }}{{ tenant.id === systemContext?.tenantId ? ' · 当前' : '' }}
              </a-menu-item>
              <a-menu-item v-if="!tenantOptions.length" key="empty" disabled>当前没有可切换的租户</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
        <CommandCenter @execute="emit('command', $event)" />
        <a-button v-if="showTodos" type="text" class="topbar-action" @click="router.push('/platform/todos')"><CheckSquareOutlined /><span>全部待办</span></a-button>
        <a-button v-if="showMessages" type="text" class="topbar-action" @click="router.push('/platform/messages')"><BellOutlined /><span>全部消息</span></a-button>
        <a-button type="text" class="user-trigger" @click="router.push('/profile')"><a-avatar size="small"><UserOutlined /></a-avatar><span class="topbar-user">{{ systemContext?.displayName }}</span></a-button>
      </div>
    </header>
    <div class="system-frame">
      <aside class="system-nav">
        <div class="system-nav__main">
          <p class="workspace-nav-title">系统工作区</p>
          <button :class="['system-nav__item', { active: activeKey === 'home' }]" @click="emit('navigate', 'home')"><HomeOutlined />系统首页</button>
          <button :class="['system-nav__item', { active: activeKey === 'personal' }]" @click="emit('navigate', 'personal')"><UserOutlined />我的首页</button>
          <p v-if="showRuntime" class="nav-section-title">业务运行</p>
          <button v-if="showRuntime" :class="['system-nav__item', { active: activeKey === 'runtime' }]" @click="emit('navigate', 'runtime')"><DatabaseOutlined />业务模块</button>
          <button v-if="showFlow" :class="['system-nav__item', { active: activeKey === 'flow' }]" @click="emit('navigate', 'flow')"><ApartmentOutlined />流程</button>
          <button v-if="showApplications" :class="['system-nav__item', { active: activeKey === 'applications' }]" @click="emit('navigate', 'applications')"><CheckSquareOutlined />我的申请</button>
          <button v-if="showTasks" :class="['system-nav__item', { active: activeKey === 'tasks' }]" @click="emit('navigate', 'tasks')"><ProjectOutlined />任务</button>
          <p v-if="showTodos || showMessages || showFiles" class="nav-section-title">协作</p>
          <button v-if="showTodos" :class="['system-nav__item', { active: activeKey === 'todos' }]" @click="emit('navigate', 'todos')"><CheckSquareOutlined />系统待办</button>
          <button v-if="showMessages" :class="['system-nav__item', { active: activeKey === 'messages' }]" @click="emit('navigate', 'messages')"><BellOutlined />系统消息</button>
          <button v-if="showFiles" :class="['system-nav__item', { active: activeKey === 'files' }]" @click="emit('navigate', 'files')"><FolderOpenOutlined />文件</button>
          <button v-if="showAi" :class="['system-nav__item', { active: activeKey === 'ai' }]" @click="emit('navigate', 'ai')"><RobotOutlined />智能助手</button>
        </div>
        <button v-if="showAdmin && systemContext?.systemId" type="button" class="system-nav__manage" @click="router.push(`/systems/${systemContext.systemId}/admin`)"><SettingOutlined /><span><strong>系统管理</strong><small>配置当前系统</small></span></button>
      </aside>
      <main class="system-workspace">
        <a-alert v-if="notice" type="warning" show-icon closable :message="notice.message" :description="notice.description" class="workspace-notice" @close="dismissNotice" />
        <slot />
      </main>
    </div>
  </div>
</template>
