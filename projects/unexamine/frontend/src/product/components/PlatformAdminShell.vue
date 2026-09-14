<script setup lang="ts">
import { ArrowLeftOutlined, UserOutlined } from '@ant-design/icons-vue'
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { platformContext } from '../session'
import { hasPlatformAdminCapability, type PlatformAdminCapability } from '../permissions'
import { isHeavyConfigurationSection } from '../responsive'
import ProductMark from './ProductMark.vue'
import WorkspaceContext from './WorkspaceContext.vue'

const route = useRoute()
const router = useRouter()
interface PlatformAdminNavigationItem {
  key: string
  path: string
  category?: string
  capability: PlatformAdminCapability
  label: string
  description: string
}

const allNavigation: PlatformAdminNavigationItem[] = [
  { key: 'platform-info', path: '/platform/admin/configurations', category: 'PLATFORM_INFO', capability: 'configuration', label: '平台信息', description: '平台资料与全局规则' },
  { key: 'identity', path: '/platform/admin/identity-providers', capability: 'identity', label: '统一认证', description: '身份源、测试与发布' },
  { key: 'organization', path: '/platform/admin/configurations', category: 'ORGANIZATION', capability: 'configuration', label: '组织架构', description: '平台成员与组织关系' },
  { key: 'authorization', path: '/platform/admin/configurations', category: 'AUTHORIZATION', capability: 'configuration', label: '角色权限', description: '平台角色与访问范围' },
  { key: 'dictionary', path: '/platform/admin/configurations', category: 'DICTIONARY', capability: 'configuration', label: '数据字典', description: '平台公共基础数据' },
  { key: 'systems', path: '/platform/admin/configurations', category: 'SYSTEM', capability: 'configuration', label: '系统配置', description: '系统创建与全局策略' },
  { key: 'parameters', path: '/platform/admin/configurations', category: 'BUSINESS_PARAMETER', capability: 'configuration', label: '业务参数', description: '平台参数与首页配置' },
  { key: 'work', path: '/platform/admin/work', capability: 'work', label: '工作配置', description: '任务、日志字段与统计口径' },
  { key: 'flow', path: '/platform/admin/flows', capability: 'flow', label: '流程配置', description: '平台流程设计与发布' },
  { key: 'applications', path: '/platform/admin/applications', capability: 'application', label: '应用配置', description: '平台应用与访问授权' },
  { key: 'ai', path: '/platform/admin/ai', capability: 'ai', label: '智能助手配置', description: '模型、额度与系统授权' },
  { key: 'messages', path: '/platform/admin/messages', capability: 'message', label: '消息模板', description: '模板、渠道与投递诊断' },
  { key: 'audit', path: '/platform/admin/configurations', category: 'AUDIT', capability: 'configuration', label: '审计日志', description: '平台关键操作记录' },
  { key: 'operations', path: '/platform/admin/configurations', category: 'OPERATIONS', capability: 'configuration', label: '运维配置', description: '平台运行与健康策略' },
]
const navigation = computed(() => allNavigation.filter(item => hasPlatformAdminCapability(platformContext.value?.permissions, item.capability)))

function isActive(item: PlatformAdminNavigationItem) {
  if (item.category) {
    if (route.path === '/platform/admin/dashboards') return item.category === 'BUSINESS_PARAMETER'
    return route.path === item.path && String(route.query.category || 'PLATFORM_INFO') === item.category
  }
  return route.path === item.path
}

const activeKey = computed(() => navigation.value.find(item => isActive(item))?.key || navigation.value[0]?.key || '')
const showDesktopGuidance = computed(() => isHeavyConfigurationSection(activeKey.value)
  || route.path === '/platform/admin/identity-providers')

function navigate(item: PlatformAdminNavigationItem) {
  void router.push(item.category ? { path: item.path, query: { category: item.category } } : item.path)
}
</script>

<template>
  <div class="platform-admin-shell">
    <header class="topbar topbar--platform-admin">
      <ProductMark />
      <WorkspaceContext scope="platform-admin" name="平台管理" subtitle="配置作用于全部系统" />
      <div class="topbar__actions">
        <a-button type="text" class="topbar-action" @click="router.push('/platform/dashboard')"><ArrowLeftOutlined /><span>返回工作区</span></a-button>
        <a-button type="text" class="user-trigger" @click="router.push('/profile')"><a-avatar size="small"><UserOutlined /></a-avatar><span class="topbar-user">{{ platformContext?.displayName }}</span></a-button>
      </div>
    </header>
    <div class="platform-admin-frame">
      <aside class="platform-admin-nav" aria-label="平台管理导航">
        <div class="system-admin-nav__heading"><strong>平台管理</strong><small>配置作用于整个平台，不进入单个系统业务。</small></div>
        <button v-for="item in navigation" :key="item.key" type="button" :class="['platform-admin-nav__item', { active: isActive(item) }]" @click="navigate(item)">
          <span class="platform-admin-nav__mark">{{ item.label.slice(0, 1) }}</span><span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
        </button>
      </aside>
      <main class="platform-admin-workspace">
        <section v-if="showDesktopGuidance" class="mobile-desktop-guidance" role="status" aria-live="polite">
          <strong>重配置请切换桌面端完成</strong>
          <span>移动端不承载流程、复杂权限、身份接入和设计器操作；可在业务工作区查看已发布结果，或使用桌面端继续编辑。两端使用同一账号与权限。</span>
        </section>
        <div :class="{ 'platform-admin-content--desktop-only': showDesktopGuidance }"><slot /></div>
      </main>
    </div>
  </div>
</template>
