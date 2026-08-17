<script setup lang="ts">
import { ApartmentOutlined, ArrowLeftOutlined, AuditOutlined, BellOutlined, CheckSquareOutlined, DatabaseOutlined, SettingOutlined, UserOutlined } from '@ant-design/icons-vue'
import { useRouter } from 'vue-router'
import ProductMark from './ProductMark.vue'
import { systemContext } from '../session'

withDefaults(defineProps<{
  systemName?: string
  tenantName?: string
  activeKey?: string
  showRuntime?: boolean
  showConfig?: boolean
  showAudit?: boolean
  showSystemSettings?: boolean
}>(), {
  showRuntime: false,
  showConfig: false,
  showAudit: false,
  showSystemSettings: false,
})
const emit = defineEmits<{ navigate: [key: string] }>()
const router = useRouter()
</script>

<template>
  <div class="system-shell">
    <header class="topbar topbar--system">
      <ProductMark />
      <button class="system-switch" type="button" @click="router.push('/platform')">
        <span class="system-switch__mark">{{ systemName?.slice(0, 1) || '系' }}</span>
        <span><strong>{{ systemName || '当前系统' }}</strong><small>{{ tenantName || '默认主租户' }}</small></span>
        <ArrowLeftOutlined class="system-switch__back" />
      </button>
      <div class="topbar__actions">
        <a-button type="text" class="topbar-action"><CheckSquareOutlined /><span>待办</span></a-button>
        <a-button type="text" class="topbar-action"><BellOutlined /><span>消息</span></a-button>
        <a-avatar size="small"><UserOutlined /></a-avatar><span class="topbar-user">{{ systemContext?.displayName }}</span>
      </div>
    </header>
    <div class="system-frame">
      <aside class="system-nav">
        <button :class="['system-nav__item', { active: activeKey === 'home' }]" @click="emit('navigate', 'home')"><span>首</span>系统首页</button>
        <p v-if="showRuntime" class="nav-section-title">业务运行</p>
        <button v-if="showRuntime" :class="['system-nav__item', { active: activeKey === 'runtime' }]" @click="emit('navigate', 'runtime')"><DatabaseOutlined />业务模块</button>
        <p v-if="showConfig || showAudit || showSystemSettings" class="nav-section-title">后台配置</p>
        <button v-if="showSystemSettings" :class="['system-nav__item', { active: activeKey === 'settings' }]" @click="emit('navigate', 'settings')"><ApartmentOutlined />系统与租户</button>
        <button v-if="showConfig" :class="['system-nav__item', { active: activeKey === 'config' }]" @click="emit('navigate', 'config')"><SettingOutlined />模块配置</button>
        <button v-if="showAudit" :class="['system-nav__item', { active: activeKey === 'audit' }]" @click="emit('navigate', 'audit')"><AuditOutlined />操作审计</button>
      </aside>
      <main class="system-workspace"><slot /></main>
    </div>
  </div>
</template>
