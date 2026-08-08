<script setup lang="ts">
import { Blocks, Bot, BriefcaseBusiness, CheckSquare2, ChevronDown, LayoutDashboard, LogOut, Mail, Settings, ShieldCheck, UserRound, Workflow } from 'lucide-vue-next'
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import AccountSecurityDialog from '@/components/AccountSecurityDialog.vue'
import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const router = useRouter()
const accountSecurityOpen = ref(false)

async function logout() {
  await session.logout()
  await router.replace('/auth/login')
}
</script>

<template>
  <div class="app-shell">
    <a class="skip-link" href="#platform-main">跳到主要内容</a>
    <header class="topbar">
      <RouterLink class="topbar-brand" to="/platform/workbench" aria-label="examine2 平台首页"><Blocks :size="22" /><strong>examine2</strong></RouterLink>
      <nav class="topnav" aria-label="平台主导航">
        <RouterLink v-if="session.hasPermission('platform.dashboard.view')" to="/platform/dashboard"><LayoutDashboard :size="16" />仪表盘</RouterLink>
        <RouterLink v-if="session.hasPermission('platform.flow.read')" to="/platform/flows"><Workflow :size="16" />Flow</RouterLink>
        <RouterLink to="/platform/workbench">工作台</RouterLink>
        <RouterLink v-if="session.hasPermission('platform.work.read')" to="/platform/work"><BriefcaseBusiness :size="16" />工作</RouterLink>
        <RouterLink v-if="session.hasPermission('platform.task.read')" to="/platform/todos"><CheckSquare2 :size="16" />待办</RouterLink>
        <RouterLink v-if="session.hasPermission('platform.message.read')" to="/platform/messages"><Mail :size="16" />消息</RouterLink>
        <RouterLink v-if="session.hasPermission('platform.runtime.access') && session.hasPermission('platform.ai.agent.use')" to="/platform/agent"><Bot :size="16" />Agent</RouterLink>
      </nav>
      <div class="topbar-tools">
        <span class="context-chip">平台</span>
        <a-dropdown :trigger="['click']" placement="bottomRight">
          <button class="icon-command" type="button" title="个人菜单" aria-label="打开个人菜单">
            <UserRound :size="18" /><span>{{ session.context?.account.displayName }}</span><ChevronDown :size="14" />
          </button>
          <template #overlay>
            <a-menu>
              <a-menu-item v-if="session.hasShell('PLATFORM_ADMIN')" @click="router.push('/platform/admin')"><ShieldCheck :size="15" /> 平台后台</a-menu-item>
              <a-menu-item class="account-security-menu-trigger" @click="accountSecurityOpen = true"><Settings :size="15" /> 个人设置</a-menu-item>
              <a-menu-divider />
              <a-menu-item @click="logout"><LogOut :size="15" /> 退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </div>
    </header>
    <main id="platform-main" class="page-surface" tabindex="-1"><RouterView /></main>
    <AccountSecurityDialog v-model:open="accountSecurityOpen" />
  </div>
</template>
