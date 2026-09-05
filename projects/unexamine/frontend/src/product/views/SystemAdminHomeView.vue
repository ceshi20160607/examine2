<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SystemAdminShell from '../components/SystemAdminShell.vue'
import ProductPageHeader from '../components/ProductPageHeader.vue'
import { allowsPermission } from '../permissions'
import { systemContext } from '../session'
import AuditEventsView from './AuditEventsView.vue'
import DictionaryConfigurationView from './DictionaryConfigurationView.vue'
import FixedConfigurationsView from './FixedConfigurationsView.vue'
import FlowDesignerView from './FlowDesignerView.vue'
import FoundationControlsView from './FoundationControlsView.vue'
import ModuleConfigurationView from './ModuleConfigurationView.vue'
import SystemAuthorizationView from './SystemAuthorizationView.vue'
import SystemIdentityMappingView from './SystemIdentityMappingView.vue'
import SystemSettingsView from './SystemSettingsView.vue'
import TenantExtensionsView from './TenantExtensionsView.vue'
import ApplicationManagementView from './ApplicationManagementView.vue'
import AiConfigurationView from './AiConfigurationView.vue'
import DashboardConfigurationView from './DashboardConfigurationView.vue'
import PrintTemplateConfigurationView from './PrintTemplateConfigurationView.vue'
import MessageTemplateManagementView from './MessageTemplateManagementView.vue'

const route = useRoute()
const router = useRouter()
const templateSection = ref<'main' | 'tenant'>('main')
const systemInfoSection = ref<'settings' | 'identity'>('settings')
const parameterSection = ref<'settings' | 'dashboard' | 'print' | 'messages'>('settings')

interface SystemAdminNavigationItem {
  key: string
  label: string
  description: string
}

const canManageSystem = computed(() => allowsPermission(systemContext.value?.permissions, 'CONFIG', 'SYSTEM', 'MANAGE'))
const canManageModules = computed(() => allowsPermission(systemContext.value?.permissions, 'CONFIG', 'MODULE', 'MANAGE'))
const canAudit = computed(() => allowsPermission(systemContext.value?.permissions, 'AUDIT', 'EVENT', 'VIEW'))
const canDesignFlow = computed(() => allowsPermission(systemContext.value?.permissions, 'FLOW', 'SYSTEM', 'DESIGN')
  || allowsPermission(systemContext.value?.permissions, 'FLOW', 'SYSTEM', 'PUBLISH')
  || allowsPermission(systemContext.value?.permissions, 'FLOW', '*', 'DESIGN')
  || allowsPermission(systemContext.value?.permissions, 'FLOW', '*', 'PUBLISH'))
const canManageApplications = computed(() => allowsPermission(systemContext.value?.permissions, 'APPLICATION', 'SYSTEM', 'VIEW')
  || allowsPermission(systemContext.value?.permissions, 'APPLICATION', 'SYSTEM', 'MANAGE')
  || allowsPermission(systemContext.value?.permissions, 'APPLICATION', '*', 'VIEW')
  || allowsPermission(systemContext.value?.permissions, 'APPLICATION', '*', 'MANAGE'))
const canManageAi = computed(() => allowsPermission(systemContext.value?.permissions, 'AI', 'SYSTEM', 'VIEW')
  || allowsPermission(systemContext.value?.permissions, 'AI', 'SYSTEM', 'MANAGE')
  || allowsPermission(systemContext.value?.permissions, 'AI', 'SYSTEM', 'PUBLISH'))
const canManageMessages = computed(() => allowsPermission(systemContext.value?.permissions, 'MESSAGE', 'SYSTEM', 'MANAGE')
  || allowsPermission(systemContext.value?.permissions, 'MESSAGE', '*', 'MANAGE'))

const navigation = computed<SystemAdminNavigationItem[]>(() => [
  ...(canManageSystem.value ? [
    { key: 'system-info', label: '系统信息', description: '系统资料、空间与统一认证' },
    { key: 'organization', label: '组织架构', description: '部门、成员、岗位与上下级' },
    { key: 'roles', label: '角色权限', description: '菜单、动作、字段与数据范围' },
    { key: 'dictionary', label: '数据字典', description: '字典草稿、发布与版本' },
  ] : []),
  ...(canManageModules.value ? [{ key: 'templates', label: '模板配置', description: '模块信息、字段、动作、流程与应用' }] : []),
  ...(canManageSystem.value ? [{ key: 'parameters', label: '其他业务参数', description: '编号、首页、打印、消息与任务字段' }] : []),
  ...(canDesignFlow.value ? [{ key: 'flow', label: '流程配置', description: '设计、模拟、绑定与发布' }] : []),
  ...(canManageApplications.value ? [{ key: 'applications', label: '应用配置', description: '受控访问、授权与调用策略' }] : []),
  ...(canManageAi.value ? [{ key: 'ai', label: '智能助手配置', description: '模型授权、助手与使用范围' }] : []),
  ...(canAudit.value ? [{ key: 'audit', label: '审计日志', description: '当前系统不可删除的操作记录' }] : []),
  ...(canManageSystem.value ? [{ key: 'operations', label: '运维配置', description: '作业、配额、开关与健康状态' }] : []),
])

const requestedSection = computed(() => String(route.query.section || ''))
const active = ref('')

function syncActive() {
  const requested = requestedSection.value
  active.value = navigation.value.some(item => item.key === requested) ? requested : navigation.value[0]?.key || ''
}

async function navigate(key: string) {
  active.value = key
  await router.replace({ query: { ...route.query, section: key } })
}

function contextChanged() {
  void router.replace(`/systems/${systemContext.value?.systemId}/admin?section=system-info`)
}

watch([requestedSection, navigation], syncActive, { immediate: true })
</script>

<template>
  <SystemAdminShell :active-key="active" :items="navigation" @navigate="navigate">
    <a-alert v-if="!navigation.length" type="warning" show-icon message="当前系统上下文没有后台配置权限" description="请返回系统运行态，或联系系统管理员授予明确的配置权限。" />
    <template v-else-if="active === 'system-info'">
      <nav class="admin-subnav admin-subnav--text" aria-label="系统信息任务">
        <button :class="{ active: systemInfoSection === 'settings' }" type="button" @click="systemInfoSection = 'settings'"><span><strong>系统资料</strong><small>基础信息、空间、域名与访问</small></span></button>
        <button :class="{ active: systemInfoSection === 'identity' }" type="button" @click="systemInfoSection = 'identity'"><span><strong>统一认证</strong><small>身份源继承与成员映射</small></span></button>
      </nav>
      <SystemSettingsView v-if="systemInfoSection === 'settings'" @context-changed="contextChanged" />
      <SystemIdentityMappingView v-else />
    </template>
    <SystemAuthorizationView v-else-if="active === 'organization'" section="organization" />
    <SystemAuthorizationView v-else-if="active === 'roles'" section="roles" />
    <template v-else-if="active === 'dictionary'">
      <ProductPageHeader kicker="系统管理" title="数据字典" description="统一维护系统内可复用的选项和基础数据。" />
      <DictionaryConfigurationView />
    </template>
    <template v-else-if="active === 'templates'">
      <ProductPageHeader kicker="系统管理" title="业务模块" description="从业务名称开始，按字段、页面和功能逐步搭建并发布。">
        <template #actions><a-segmented v-model:value="templateSection" :options="[{ value: 'main', label: '模块搭建' }, { value: 'tenant', label: '组织扩展' }]" /></template>
      </ProductPageHeader>
      <ModuleConfigurationView v-if="templateSection === 'main'" />
      <TenantExtensionsView v-else />
    </template>
    <FlowDesignerView v-else-if="active === 'flow'" context="system" />
    <ApplicationManagementView v-else-if="active === 'applications'" context="system" />
    <template v-else-if="active === 'ai'">
      <ProductPageHeader kicker="系统管理" title="智能助手" description="配置可用模型、智能助手及其使用范围。" />
      <AiConfigurationView context="system" />
    </template>
    <AuditEventsView v-else-if="active === 'audit'" />
    <template v-else-if="active === 'operations'">
      <ProductPageHeader kicker="系统管理" title="运维与健康" description="检查依赖和运行状态，并管理作业、配额、开关与缓存。" />
      <FoundationControlsView />
    </template>
    <template v-else-if="active === 'parameters'">
      <ProductPageHeader kicker="系统管理" title="其他业务参数" description="维护只在当前系统内生效的编号、首页、打印、消息和任务参数。" />
      <nav class="admin-subnav admin-subnav--text" aria-label="系统业务参数任务">
        <button :class="{ active: parameterSection === 'settings' }" type="button" @click="parameterSection = 'settings'"><span><strong>参数配置</strong><small>编号、任务字段与其他参数</small></span></button>
        <button :class="{ active: parameterSection === 'dashboard' }" type="button" @click="parameterSection = 'dashboard'"><span><strong>首页配置</strong><small>数据源、组件布局与发布</small></span></button>
        <button :class="{ active: parameterSection === 'print' }" type="button" @click="parameterSection = 'print'"><span><strong>打印模板</strong><small>字段布局、预览与发布</small></span></button>
        <button v-if="canManageMessages" :class="{ active: parameterSection === 'messages' }" type="button" @click="parameterSection = 'messages'"><span><strong>消息模板</strong><small>模板、渠道与投递诊断</small></span></button>
      </nav>
      <FixedConfigurationsView v-if="parameterSection === 'settings'" context="system" initial-category="BUSINESS_PARAMETER" single-category />
      <DashboardConfigurationView v-else-if="parameterSection === 'dashboard'" context="system" />
      <PrintTemplateConfigurationView v-else-if="parameterSection === 'print'" />
      <MessageTemplateManagementView v-else context="system" />
    </template>
  </SystemAdminShell>
</template>
