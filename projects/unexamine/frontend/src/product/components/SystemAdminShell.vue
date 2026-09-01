<script setup lang="ts">
import { ArrowLeftOutlined, HomeOutlined, UserOutlined } from '@ant-design/icons-vue'
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import ProductMark from './ProductMark.vue'
import { userFacingWorkspaceName } from '../presentation'
import { isHeavyConfigurationSection } from '../responsive'
import { systemContext } from '../session'

interface SystemAdminNavigationItem {
  key: string
  label: string
  description: string
}

interface SystemAdminNavigationGroup {
  key: string
  label: string
  description: string
  items: SystemAdminNavigationItem[]
}

const props = defineProps<{
  activeKey: string
  groups: SystemAdminNavigationGroup[]
}>()

const emit = defineEmits<{ navigate: [key: string] }>()
const router = useRouter()
const showDesktopGuidance = computed(() => isHeavyConfigurationSection(props.activeKey))
const activeGroup = computed(() => props.groups.find(group => group.items.some(item => item.key === props.activeKey)) || props.groups[0])

function openGroup(group: SystemAdminNavigationGroup) {
  const target = group.items.find(item => item.key === props.activeKey) || group.items[0]
  if (target) emit('navigate', target.key)
}
</script>

<template>
  <div class="system-admin-shell">
    <header class="topbar topbar--system-admin">
      <ProductMark />
      <div class="system-admin-context">
        <span class="system-switch__mark">{{ systemContext?.systemName?.slice(0, 1) || '系' }}</span>
        <span><strong>{{ systemContext?.systemName || '当前系统' }}</strong><small>{{ userFacingWorkspaceName(systemContext?.tenantName) }} · 系统管理</small></span>
      </div>
      <div class="topbar__actions">
        <a-button type="text" class="topbar-action" @click="router.push(`/systems/${systemContext?.systemId}`)"><ArrowLeftOutlined /><span>返回系统</span></a-button>
        <a-button type="text" class="topbar-action" @click="router.push('/platform')"><HomeOutlined /><span>平台首页</span></a-button>
        <a-button type="text" class="user-trigger" @click="router.push('/profile')"><a-avatar size="small"><UserOutlined /></a-avatar><span class="topbar-user">{{ systemContext?.displayName }}</span></a-button>
      </div>
    </header>
    <div class="system-admin-frame">
      <aside class="system-admin-nav" aria-label="系统后台导航">
        <div class="system-admin-nav__heading"><strong>系统管理</strong><small>按工作域完成配置与治理</small></div>
        <button v-for="group in groups" :key="group.key" :class="['system-admin-nav__item', { active: activeGroup?.key === group.key }]" type="button" @click="openGroup(group)">
          <span>{{ group.label.slice(0, 1) }}</span><strong>{{ group.label }}</strong><small>{{ group.description }}</small>
        </button>
      </aside>
      <main class="system-admin-workspace">
        <nav v-if="activeGroup" class="system-admin-subnav" :aria-label="`${activeGroup.label}二级导航`">
          <div><strong>{{ activeGroup.label }}</strong><small>{{ activeGroup.description }}</small></div>
          <button v-for="item in activeGroup.items" :key="item.key" type="button" :class="{ active: activeKey === item.key }" @click="emit('navigate', item.key)">
            {{ item.label }}
          </button>
        </nav>
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
