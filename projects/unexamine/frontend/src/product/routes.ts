import type { RouteRecordRaw } from 'vue-router'
import './product.css'
import './design-system.css'
import AuthView from './views/AuthView.vue'
import PlatformHomeView from './views/PlatformHomeView.vue'
import PersonalCenterView from './views/PersonalCenterView.vue'
import SsoCallbackView from './views/SsoCallbackView.vue'
import SystemHomeView from './views/SystemHomeView.vue'
import PlatformSsoProvidersView from './views/PlatformSsoProvidersView.vue'
import PlatformConfigurationsView from './views/PlatformConfigurationsView.vue'
import PlatformFeatureView from './views/PlatformFeatureView.vue'
import PlatformFlowView from './views/PlatformFlowView.vue'
import PlatformFlowAdminView from './views/PlatformFlowAdminView.vue'
import PlatformAiConfigurationView from './views/PlatformAiConfigurationView.vue'
import SystemAdminHomeView from './views/SystemAdminHomeView.vue'
import WorkHubView from './views/WorkHubView.vue'
import PlatformTodoView from './views/PlatformTodoView.vue'
import PlatformApplicationView from './views/PlatformApplicationView.vue'
import PlatformMessageView from './views/PlatformMessageView.vue'
import PlatformDashboardView from './views/PlatformDashboardView.vue'
import PlatformDashboardConfigurationView from './views/PlatformDashboardConfigurationView.vue'
import PlatformMessageConfigurationView from './views/PlatformMessageConfigurationView.vue'
import PlatformApplicationConfigurationView from './views/PlatformApplicationConfigurationView.vue'
import PlatformWorkConfigurationView from './views/PlatformWorkConfigurationView.vue'
import { platformContext, platformTokens, systemContext, systemTokens } from './session'
import {
  allowsPermission,
  hasPlatformAdminCapability,
  hasResourceAccess,
  hasWorkConfigurationAccess,
  hasWorkRuntimeAccess,
  type PlatformAdminCapability,
} from './permissions'

type RouteTarget = { path: string; fullPath?: string; params: Record<string, string | string[]> }

const platformNotice = (notice: string) => ({ path: '/platform/dashboard', query: { notice } })
const systemNotice = (systemId: string, notice: string) => ({ path: `/systems/${systemId}`, query: { notice } })

export function defaultEntryPath() {
  return platformTokens.value?.accessToken && platformContext.value?.accountId ? '/platform/dashboard' : '/login'
}

export function unavailableRoute(to: RouteTarget) {
  if (defaultEntryPath() === '/login') return { path: '/login' }
  const currentSystemId = systemContext.value?.systemId
  const systemRoot = currentSystemId ? `/systems/${currentSystemId}` : ''
  if (systemRoot && systemTokens.value?.accessToken && (to.path === systemRoot || to.path.startsWith(`${systemRoot}/`))) {
    return systemNotice(String(currentSystemId), 'route-unavailable')
  }
  return platformNotice('route-unavailable')
}

function requirePlatform() {
  return platformTokens.value?.accessToken && platformContext.value?.accountId ? true : { path: '/login' }
}

function requirePlatformResource(...resourceTypes: string[]) {
  return () => {
    const authenticated = requirePlatform()
    if (authenticated !== true) return authenticated
    return hasResourceAccess(platformContext.value?.permissions, ...resourceTypes) ? true : platformNotice('access-denied')
  }
}

function requirePlatformWorkRuntime() {
  const authenticated = requirePlatform()
  if (authenticated !== true) return authenticated
  return hasWorkRuntimeAccess(platformContext.value?.permissions, 'PLATFORM') ? true : platformNotice('access-denied')
}

function requirePlatformAdmin(capability: PlatformAdminCapability) {
  return () => {
    const authenticated = requirePlatform()
    if (authenticated !== true) return authenticated
    return hasPlatformAdminCapability(platformContext.value?.permissions, capability) ? true : platformNotice('access-denied')
  }
}

function requireSystem(to: RouteTarget) {
  if (!systemTokens.value?.accessToken || !systemContext.value?.systemId) return platformNotice('system-context-required')
  return String(systemContext.value.systemId) === String(to.params.systemId) ? true : platformNotice('system-context-changed')
}

function requireSystemAdmin(to: RouteTarget) {
  const validContext = requireSystem(to)
  if (validContext !== true) return validContext
  const permissions = systemContext.value?.permissions
  const allowed = allowsPermission(permissions, 'CONFIG', 'SYSTEM', 'MANAGE')
    || allowsPermission(permissions, 'CONFIG', 'MODULE', 'MANAGE')
    || allowsPermission(permissions, 'AUDIT', 'EVENT', 'VIEW')
    || allowsPermission(permissions, 'FLOW', 'SYSTEM', 'DESIGN')
    || allowsPermission(permissions, 'FLOW', 'SYSTEM', 'PUBLISH')
    || allowsPermission(permissions, 'FLOW', '*', 'DESIGN')
    || allowsPermission(permissions, 'FLOW', '*', 'PUBLISH')
    || allowsPermission(permissions, 'APPLICATION', 'SYSTEM', 'VIEW')
    || allowsPermission(permissions, 'APPLICATION', 'SYSTEM', 'MANAGE')
    || allowsPermission(permissions, 'APPLICATION', '*', 'VIEW')
    || allowsPermission(permissions, 'APPLICATION', '*', 'MANAGE')
    || allowsPermission(permissions, 'AI', 'SYSTEM', 'VIEW')
    || allowsPermission(permissions, 'AI', 'SYSTEM', 'MANAGE')
    || allowsPermission(permissions, 'AI', 'SYSTEM', 'PUBLISH')
    || allowsPermission(permissions, 'MESSAGE', 'SYSTEM', 'MANAGE')
    || allowsPermission(permissions, 'MESSAGE', '*', 'MANAGE')
    || hasWorkConfigurationAccess(permissions, 'SYSTEM')
  return allowed ? true : systemNotice(String(to.params.systemId || ''), 'access-denied')
}

export const productRoutes: RouteRecordRaw[] = [
  { path: '/', redirect: defaultEntryPath },
  { path: '/login', name: 'login', component: AuthView, props: { initialMode: 'login' } },
  { path: '/register', name: 'register', component: AuthView, props: { initialMode: 'register' } },
  { path: '/sso/callback', name: 'sso-callback', component: SsoCallbackView },
  { path: '/platform', name: 'platform', component: PlatformHomeView, beforeEnter: requirePlatform },
  { path: '/platform/dashboard', name: 'platform-dashboard', component: PlatformDashboardView, beforeEnter: requirePlatform,
    meta: { title: '首页', capability: 'dashboard', description: '汇总当前账号有权访问的系统、待办、任务和消息。' } },
  { path: '/platform/flows', name: 'platform-flows', component: PlatformFlowView, beforeEnter: requirePlatformResource('FLOW'),
    meta: { title: '流程', capability: 'flow', description: '查看权限范围内的系统流程和通过应用开放的外部流程。' } },
  { path: '/platform/applications', name: 'platform-applications', component: PlatformApplicationView, beforeEnter: requirePlatformResource('APPLICATION'),
    meta: { title: '应用', capability: 'application', description: '管理系统之间及平台内外访问的授权桥接。' } },
  { path: '/platform/tasks', name: 'platform-tasks', component: WorkHubView, props: { context: 'platform' }, beforeEnter: requirePlatformWorkRuntime,
    meta: { title: '任务', capability: 'task', description: '处理普通任务、项目任务和手工创建的每日日志。' } },
  { path: '/platform/ai', name: 'platform-ai', component: PlatformFeatureView, beforeEnter: requirePlatformResource('AI'),
    meta: { title: '智能助手', capability: 'ai', description: '通过已授权的智能能力连接并操作整个平台。' } },
  { path: '/platform/todos', name: 'platform-todos', component: PlatformTodoView, beforeEnter: requirePlatformResource('TODO'),
    meta: { title: '待办', capability: 'todo', description: '集中处理审批、今日需联系记录和其他待办信息。' } },
  { path: '/platform/messages', name: 'platform-messages', component: PlatformMessageView, beforeEnter: requirePlatformResource('MESSAGE'),
    meta: { title: '消息', capability: 'message', description: '查看通知消息及其处理记录。' } },
  { path: '/platform/admin/configurations', name: 'platform-configurations', component: PlatformConfigurationsView, beforeEnter: requirePlatformAdmin('configuration') },
  { path: '/platform/admin/identity-providers', name: 'platform-identity-providers', component: PlatformSsoProvidersView, beforeEnter: requirePlatformAdmin('identity') },
  { path: '/platform/admin/flows', name: 'platform-flow-configurations', component: PlatformFlowAdminView, beforeEnter: requirePlatformAdmin('flow') },
  { path: '/platform/admin/applications', name: 'platform-application-configurations', component: PlatformApplicationConfigurationView, beforeEnter: requirePlatformAdmin('application') },
  { path: '/platform/admin/ai', name: 'platform-ai-configurations', component: PlatformAiConfigurationView, beforeEnter: requirePlatformAdmin('ai') },
  { path: '/platform/admin/dashboards', name: 'platform-dashboard-configurations', component: PlatformDashboardConfigurationView, beforeEnter: requirePlatformAdmin('configuration') },
  { path: '/platform/admin/messages', name: 'platform-message-configurations', component: PlatformMessageConfigurationView, beforeEnter: requirePlatformAdmin('message') },
  { path: '/platform/admin/work', name: 'platform-work-configurations', component: PlatformWorkConfigurationView, beforeEnter: requirePlatformAdmin('work') },
  { path: '/profile', name: 'profile', component: PersonalCenterView, beforeEnter: requirePlatform },
  { path: '/systems/:systemId', name: 'system-home', component: SystemHomeView, beforeEnter: requireSystem },
  { path: '/systems/:systemId/admin', name: 'system-admin', component: SystemAdminHomeView, beforeEnter: requireSystemAdmin },
  { path: '/:pathMatch(.*)*', redirect: unavailableRoute },
]
