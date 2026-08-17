<script setup lang="ts">
import { DatabaseOutlined, SettingOutlined } from '@ant-design/icons-vue'
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import SystemShell from '../components/SystemShell.vue'
import { clearSession, systemContext, systemTokens } from '../session'
import ModuleConfigurationView from './ModuleConfigurationView.vue'
import RuntimeWorkspaceView from './RuntimeWorkspaceView.vue'
import AuditEventsView from './AuditEventsView.vue'
import SystemSettingsView from './SystemSettingsView.vue'
import { allowsPermission, hasRuntimeModuleAccess } from '../permissions'

const router = useRouter()
const active = ref('home')
const runtimeRefreshKey = ref(0)
const showRuntime = computed(() => hasRuntimeModuleAccess(systemContext.value?.permissions))
const showConfig = computed(() => allowsPermission(systemContext.value?.permissions, 'CONFIG', 'MODULE', 'MANAGE'))
const showAudit = computed(() => allowsPermission(systemContext.value?.permissions, 'AUDIT', 'EVENT', 'VIEW'))
const showSystemSettings = computed(() => allowsPermission(systemContext.value?.permissions, 'CONFIG', 'SYSTEM', 'MANAGE'))

function modulePublished() {
  runtimeRefreshKey.value += 1
}

function contextChanged() {
  active.value = 'home'
  runtimeRefreshKey.value += 1
}

if (!systemTokens.value?.accessToken || !systemContext.value?.systemId) {
  clearSession()
  void router.replace('/login')
}
</script>

<template>
  <SystemShell
    :system-name="systemContext?.systemName"
    :tenant-name="systemContext?.tenantName"
    :active-key="active"
    :show-runtime="showRuntime"
    :show-config="showConfig"
    :show-audit="showAudit"
    :show-system-settings="showSystemSettings"
    @navigate="active = $event"
  >
    <template v-if="active === 'home'">
      <div class="page-heading system-heading">
        <div><p class="eyebrow">系统工作区</p><h1>下午好，{{ systemContext?.displayName }}</h1><p>当前已进入系统成员上下文，所有操作按当前租户和合并后权限执行。</p></div>
      </div>
      <div class="welcome-grid">
        <button v-if="showSystemSettings" class="welcome-action" @click="active = 'settings'"><span><SettingOutlined /></span><strong>系统与租户</strong><small>维护系统信息、启用多租户并切换当前租户</small></button>
        <button v-if="showConfig" class="welcome-action" @click="active = 'config'"><span><SettingOutlined /></span><strong>配置业务模块</strong><small>创建模块组、字段和发布版本</small></button>
        <button v-if="showRuntime" class="welcome-action" @click="active = 'runtime'"><span><DatabaseOutlined /></span><strong>进入业务运行页</strong><small>查看已发布模块和权限范围内数据</small></button>
      </div>
      <section v-if="showConfig" class="onboarding-panel">
        <div><p class="eyebrow">首次配置</p><h2>从模块组开始搭建</h2><p>创建模块组 → 创建模块 → 添加字段 → 发布，发布前的草稿不会进入运行页。</p></div>
        <a-button type="primary" @click="active = 'config'">开始配置</a-button>
      </section>
    </template>
    <ModuleConfigurationView v-else-if="active === 'config'" @published="modulePublished" />
    <RuntimeWorkspaceView v-else-if="active === 'runtime'" :key="runtimeRefreshKey" :refresh-key="runtimeRefreshKey" />
    <AuditEventsView v-else-if="active === 'audit' && showAudit" />
    <SystemSettingsView v-else-if="active === 'settings' && showSystemSettings" @context-changed="contextChanged" />
  </SystemShell>
</template>
