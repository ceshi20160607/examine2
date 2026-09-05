<script setup lang="ts">
import { ArrowLeftOutlined, UserOutlined } from '@ant-design/icons-vue'
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import ProductMark from './ProductMark.vue'
import WorkspaceContext from './WorkspaceContext.vue'
import { userFacingWorkspaceName } from '../presentation'
import { isHeavyConfigurationSection } from '../responsive'
import { systemContext } from '../session'

interface SystemAdminNavigationItem {
  key: string
  label: string
  description: string
}

const props = defineProps<{
  activeKey: string
  items: SystemAdminNavigationItem[]
}>()

const emit = defineEmits<{ navigate: [key: string] }>()
const router = useRouter()
const showDesktopGuidance = computed(() => isHeavyConfigurationSection(props.activeKey))
</script>

<template>
  <div class="system-admin-shell">
    <header class="topbar topbar--system-admin">
      <ProductMark />
      <WorkspaceContext scope="system-admin" :name="systemContext?.systemName || '当前系统'" :subtitle="`${userFacingWorkspaceName(systemContext?.tenantName)} · 仅当前系统`" />
      <div class="topbar__actions">
        <a-button type="text" class="topbar-action" @click="router.push(`/systems/${systemContext?.systemId}`)"><ArrowLeftOutlined /><span>返回系统</span></a-button>
        <a-button type="text" class="user-trigger" @click="router.push('/profile')"><a-avatar size="small"><UserOutlined /></a-avatar><span class="topbar-user">{{ systemContext?.displayName }}</span></a-button>
      </div>
    </header>
    <div class="system-admin-frame">
      <aside class="system-admin-nav" aria-label="系统后台导航">
        <div class="system-admin-nav__heading"><strong>系统管理</strong><small>配置只作用于当前系统和工作空间</small></div>
        <button v-for="item in items" :key="item.key" :class="['system-admin-nav__item', { active: activeKey === item.key }]" type="button" @click="emit('navigate', item.key)">
          <span>{{ item.label.slice(0, 1) }}</span><strong>{{ item.label }}</strong><small>{{ item.description }}</small>
        </button>
      </aside>
      <main class="system-admin-workspace">
        <section v-if="showDesktopGuidance" class="mobile-desktop-guidance" role="status" aria-live="polite">
          <strong>重配置请切换桌面端完成</strong>
          <span>移动端不承载字段、页面、流程、复杂权限和设计器操作；请返回运行态查看同一已发布配置，或使用桌面端继续编辑。两端使用同一账号与权限。</span>
        </section>
        <div :class="['system-admin-content', { 'system-admin-content--desktop-only': showDesktopGuidance }]">
          <slot />
        </div>
      </main>
    </div>
  </div>
</template>
