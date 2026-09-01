<script setup lang="ts">
import { ApartmentOutlined, DashboardOutlined, RobotOutlined, SafetyCertificateOutlined, SettingOutlined } from '@ant-design/icons-vue'
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
</script>

<template>
  <nav class="admin-subnav" aria-label="平台后台导航">
    <button :class="{ active: route.path === '/platform/admin/configurations' }" @click="router.push('/platform/admin/configurations')">
      <SettingOutlined /><span><strong>平台配置</strong><small>固定一级菜单与业务参数</small></span>
    </button>
    <button :class="{ active: route.path === '/platform/admin/identity-providers' }" @click="router.push('/platform/admin/identity-providers')">
      <SafetyCertificateOutlined /><span><strong>统一认证</strong><small>企业身份源与发布版本</small></span>
    </button>
    <button :class="{ active: route.path === '/platform/admin/dashboards' }" @click="router.push('/platform/admin/dashboards')">
      <DashboardOutlined /><span><strong>仪表盘</strong><small>数据源、组件预览与发布</small></span>
    </button>
    <button :class="{ active: route.path === '/platform/admin/flows' }" @click="router.push('/platform/admin/flows')">
      <ApartmentOutlined /><span><strong>流程配置</strong><small>平台流程设计、模拟与发布</small></span>
    </button>
    <button :class="{ active: route.path === '/platform/admin/ai' }" @click="router.push('/platform/admin/ai')">
      <RobotOutlined /><span><strong>AI 模型</strong><small>模型接入、额度与系统授权</small></span>
    </button>
  </nav>
</template>
