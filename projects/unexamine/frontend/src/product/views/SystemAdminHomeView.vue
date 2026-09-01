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

const route = useRoute()
const router = useRouter()
const templateSection = ref<'main' | 'tenant'>('main')

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

const navigationGroups = computed<SystemAdminNavigationGroup[]>(() => [
  {
    key: 'foundation', label: '基础与组织', description: '系统、成员、权限与基础数据', items: [
      ...(canManageSystem.value ? [
        { key: 'system-info', label: '系统信息', description: '系统、工作空间、域名与访问' },
        { key: 'organization', label: '组织与权限', description: '部门、成员、角色与数据范围' },
        { key: 'identity', label: '统一认证', description: '身份源继承与成员映射' },
        { key: 'dictionary', label: '数据字典', description: '字典草稿、发布与版本' },
        { key: 'parameters', label: '业务参数', description: '系统内其他业务参数' },
      ] : []),
    ],
  },
  {
    key: 'modeling', label: '业务建模', description: '模块、流程、页面与经营视图', items: [
      ...(canManageModules.value ? [{ key: 'templates', label: '业务模块', description: '模块组、模块、字段、页面与动作' }] : []),
      ...(canDesignFlow.value ? [{ key: 'flow', label: '业务流程', description: '设计、模拟、业务绑定与发布' }] : []),
      ...(canManageSystem.value ? [
        { key: 'dashboard', label: '仪表盘', description: '数据源、组件布局、预览与发布' },
        { key: 'print', label: '打印模板', description: '字段布局、分页预览、版本与发布' },
      ] : []),
    ],
  },
  {
    key: 'connections', label: '连接与智能', description: '受控访问与智能能力', items: [
      ...(canManageApplications.value ? [{ key: 'applications', label: '应用授权', description: '受控访问、授权与调用策略' }] : []),
      ...(canManageAi.value ? [{ key: 'ai', label: '智能助手', description: '模型授权、助手与使用范围' }] : []),
    ],
  },
  {
    key: 'governance', label: '运行治理', description: '审计、作业、健康与恢复', items: [
      ...(canAudit.value ? [{ key: 'audit', label: '审计日志', description: '当前系统不可删除的操作记录' }] : []),
      ...(canManageSystem.value ? [{ key: 'operations', label: '运维中心', description: '作业、配额、开关与健康状态' }] : []),
    ],
  },
].filter(group => group.items.length))

const navigation = computed(() => navigationGroups.value.flatMap(group => group.items))

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
  <SystemAdminShell :active-key="active" :groups="navigationGroups" @navigate="navigate">
    <a-alert v-if="!navigation.length" type="warning" show-icon message="当前系统上下文没有后台配置权限" description="请返回系统运行态，或联系系统管理员授予明确的配置权限。" />
    <SystemSettingsView v-else-if="active === 'system-info'" @context-changed="contextChanged" />
    <SystemAuthorizationView v-else-if="active === 'organization'" />
    <SystemIdentityMappingView v-else-if="active === 'identity'" />
    <DashboardConfigurationView v-else-if="active === 'dashboard'" context="system" />
    <PrintTemplateConfigurationView v-else-if="active === 'print'" />
    <template v-else-if="active === 'dictionary'">
      <ProductPageHeader kicker="系统管理" title="数据字典" description="统一维护系统内可复用的选项和基础数据。" />
      <DictionaryConfigurationView />
    </template>
    <template v-else-if="active === 'templates'">
      <ProductPageHeader kicker="业务建模" title="业务模块" description="在一条配置链中完成模块、字段、页面、动作和发布。">
        <template #actions><a-segmented v-model:value="templateSection" :options="[{ value: 'main', label: '系统模板' }, { value: 'tenant', label: '工作空间扩展' }]" /></template>
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
      <ProductPageHeader kicker="系统管理" title="业务参数" description="维护只在当前系统内生效的业务参数。" />
      <FixedConfigurationsView context="system" initial-category="BUSINESS_PARAMETER" />
    </template>
  </SystemAdminShell>
</template>
