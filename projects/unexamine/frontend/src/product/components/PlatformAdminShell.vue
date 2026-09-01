<script setup lang="ts">
import { ApartmentOutlined, ArrowLeftOutlined, DashboardOutlined, HomeOutlined, RobotOutlined, SafetyCertificateOutlined, SettingOutlined, UserOutlined } from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { platformContext } from '../session'
import ProductMark from './ProductMark.vue'

const route = useRoute()
const router = useRouter()
const navigation = [
  { path: '/platform/admin/configurations', label: '平台配置', description: '平台信息与业务参数', icon: SettingOutlined },
  { path: '/platform/admin/identity-providers', label: '统一认证', description: '企业身份源与发布版本', icon: SafetyCertificateOutlined },
  { path: '/platform/admin/dashboards', label: '首页配置', description: '数据组件、预览与发布', icon: DashboardOutlined },
  { path: '/platform/admin/flows', label: '流程配置', description: '平台流程设计与发布', icon: ApartmentOutlined },
  { path: '/platform/admin/ai', label: '智能助手', description: '模型、额度与系统授权', icon: RobotOutlined },
]
</script>

<template>
  <div class="platform-admin-shell">
    <header class="topbar topbar--platform-admin">
      <ProductMark />
      <div class="platform-admin-context"><span class="system-switch__mark">管</span><span><strong>平台管理</strong><small>平台范围配置</small></span></div>
      <div class="topbar__actions">
        <a-button type="text" class="topbar-action" @click="router.push('/platform/dashboard')"><ArrowLeftOutlined /><span>返回工作区</span></a-button>
        <a-button type="text" class="topbar-action" @click="router.push('/platform')"><HomeOutlined /><span>系统目录</span></a-button>
        <a-button type="text" class="user-trigger" @click="router.push('/profile')"><a-avatar size="small"><UserOutlined /></a-avatar><span class="topbar-user">{{ platformContext?.displayName }}</span></a-button>
      </div>
    </header>
    <div class="platform-admin-frame">
      <aside class="platform-admin-nav" aria-label="平台管理导航">
        <div class="system-admin-nav__heading"><strong>平台管理</strong><small>配置作用于整个平台，不进入单个系统业务。</small></div>
        <button v-for="item in navigation" :key="item.path" type="button" :class="['platform-admin-nav__item', { active: route.path === item.path }]" @click="router.push(item.path)">
          <component :is="item.icon" /><span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
        </button>
      </aside>
      <main class="platform-admin-workspace"><slot /></main>
    </div>
  </div>
</template>
