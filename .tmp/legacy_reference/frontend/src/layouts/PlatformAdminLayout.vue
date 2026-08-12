<script setup lang="ts">
import { Blocks, Bot, Building2, ChevronLeft, KeyRound, LayoutDashboard, LogOut, ScrollText, Settings2, ShieldCheck, UsersRound, Workflow } from 'lucide-vue-next'
import { computed } from 'vue'
import { useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const router = useRouter()
const navigation = computed(() => [
  { label: '系统治理', to: '/platform/admin/systems', permission: 'platform.system.manage', icon: Building2 },
  { label: '组织账号', to: '/platform/admin/organization', permission: 'platform.organization.manage', icon: UsersRound },
  { label: '企业身份源', to: '/platform/admin/identity-providers', permission: 'platform.organization.manage', icon: KeyRound },
  { label: '平台角色', to: '/platform/admin/roles', permission: 'platform.role.manage', icon: ShieldCheck },
  { label: 'Agent 配置', to: '/platform/admin/agent', permission: 'platform.ai.policy.manage', icon: Bot },
  { label: '开放应用', to: '/platform/admin/openapi-applications', permission: 'platform.openapi.application.manage', icon: KeyRound },
  { label: '平台 Flow', to: '/platform/admin/flows', permission: 'platform.flow.manage', icon: Workflow },
  { label: '仪表盘管理', to: '/platform/admin/dashboards', permission: 'platform.dashboard.manage', icon: LayoutDashboard },
  { label: '日志与体检', to: '/platform/admin/audit-logs', permission: 'platform.audit.view', icon: ScrollText },
  { label: '平台全局策略', to: '/platform/admin/global-settings', permission: 'platform.settings.manage', icon: Settings2 },
].filter((item) => session.hasPermission(item.permission)))

async function logout() {
  await session.logout()
  await router.replace('/auth/login')
}
</script>

<template>
  <div class="admin-shell">
    <a class="skip-link" href="#platform-admin-main">跳到主要内容</a>
    <header class="admin-topbar">
      <RouterLink class="admin-brand" to="/platform/admin" aria-label="平台后台首页"><Blocks :size="20" /><strong>平台后台</strong></RouterLink>
      <span class="admin-context">平台治理域</span>
      <RouterLink class="admin-top-command" to="/platform/workbench"><ChevronLeft :size="16" />返回工作台</RouterLink>
      <button class="admin-icon-command" type="button" title="退出登录" aria-label="退出登录" @click="logout"><LogOut :size="17" /></button>
    </header>
    <div class="admin-frame">
      <aside class="admin-sidebar">
        <nav aria-label="平台后台导航">
          <RouterLink v-for="item in navigation" :key="item.to" :to="item.to">
            <component :is="item.icon" :size="17" /><span>{{ item.label }}</span>
          </RouterLink>
        </nav>
        <div class="admin-sidebar-foot">权限快照 {{ session.context?.permissionVersion }}</div>
      </aside>
      <main id="platform-admin-main" class="admin-main" tabindex="-1"><RouterView /></main>
    </div>
  </div>
</template>
