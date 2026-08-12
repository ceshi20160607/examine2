<script setup lang="ts">
import { BellRing, Blocks, Bot, Building2, ChevronLeft, ClipboardList, Database, FileText, Gauge, KeyRound, LayoutDashboard, LogOut, Rocket, ScrollText, Settings2, ShieldCheck, UsersRound } from 'lucide-vue-next'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useSessionStore } from '@/stores/session'

const session = useSessionStore()
const route = useRoute()
const router = useRouter()
const systemId = computed(() => String(route.params.systemId))
const navigation = computed(() => [
  { label: '上线引导', path: 'onboarding', allPermissions: ['system.admin.access', 'module.config.manage', 'system.member.manage'], icon: Rocket },
  { label: '工作配置', path: 'work-configuration', permission: 'work.config.manage', icon: ClipboardList },
  { label: '模块配置', path: 'configuration', allPermissions: ['system.admin.access', 'module.config.manage'], icon: Blocks },
  { label: '数据源', path: 'data-sources', allPermissions: ['system.admin.access', 'module.config.manage'], icon: Database },
  { label: '仪表盘', path: 'dashboards', allPermissions: ['system.admin.access', 'module.config.manage'], icon: LayoutDashboard },
  { label: 'KPI 管理', path: 'kpis', allPermissions: ['system.admin.access', 'module.config.manage'], icon: Gauge },
  { label: '报表工作室', path: 'reports', allPermissions: ['system.admin.access', 'module.config.manage'], icon: FileText },
  { label: '消息模板', path: 'message-templates', permission: 'event.template.manage', icon: BellRing },
  { label: 'Agent 配置', path: 'agent', permission: 'ai.policy.manage', icon: Bot },
  { label: '系统设置', path: 'settings', permission: 'system.settings.manage', icon: Settings2 },
  { label: '租户管理', path: 'tenants', permission: 'system.tenant.manage', icon: Building2 },
  { label: '组织成员', path: 'organization', anyPermissions: ['system.organization.manage', 'system.member.manage'], icon: UsersRound },
  { label: '统一认证同步', path: 'identity-sync', permission: 'system.organization.manage', icon: KeyRound },
  { label: '角色权限', path: 'roles', permission: 'system.role.manage', icon: ShieldCheck },
  { label: '开放应用', path: 'openapi-applications', permission: 'openapi.application.manage', icon: KeyRound },
  { label: '访问申请', path: 'access-requests', permission: 'system.access.review', icon: KeyRound },
  { label: '日志与体检', path: 'audit-logs', permission: 'system.audit.view', icon: ScrollText },
].filter((item) => item.allPermissions
  ? item.allPermissions.every(session.hasPermission)
  : item.permission
    ? session.hasPermission(item.permission)
    : item.anyPermissions?.some(session.hasPermission)))

async function logout() {
  await session.logout()
  await router.replace('/auth/login')
}
</script>

<template>
  <div class="admin-shell system-admin-shell">
    <a class="skip-link" href="#system-admin-main">跳到主要内容</a>
    <header class="admin-topbar">
      <RouterLink class="admin-brand" :to="`/systems/${systemId}/admin`" aria-label="系统后台首页"><Settings2 :size="20" /><strong>系统后台</strong></RouterLink>
      <span class="admin-context">{{ session.context?.systemName }}</span>
      <RouterLink class="admin-top-command" :to="`/systems/${systemId}/workbench`"><ChevronLeft :size="16" />返回系统</RouterLink>
      <button class="admin-icon-command" type="button" title="退出登录" aria-label="退出登录" @click="logout"><LogOut :size="17" /></button>
    </header>
    <div class="admin-frame">
      <aside class="admin-sidebar">
        <nav aria-label="系统后台导航">
          <RouterLink v-for="item in navigation" :key="item.path" :to="`/systems/${systemId}/admin/${item.path}`">
            <component :is="item.icon" :size="17" /><span>{{ item.label }}</span>
          </RouterLink>
        </nav>
        <div class="admin-sidebar-foot">租户：{{ session.context?.tenantName || session.context?.tenantId }}</div>
      </aside>
      <main id="system-admin-main" class="admin-main" tabindex="-1"><RouterView /></main>
    </div>
  </div>
</template>
