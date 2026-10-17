import type { RouteLocationNormalized, RouteRecordRaw } from 'vue-router'
import './product.css'
import './design-system.css'
import AuthView from './views/AuthView.vue'
import MemberInvitationView from './views/MemberInvitationView.vue'
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
import PlatformPersonalDashboardView from './views/PlatformPersonalDashboardView.vue'
import PlatformDashboardConfigurationView from './views/PlatformDashboardConfigurationView.vue'
import PlatformMessageConfigurationView from './views/PlatformMessageConfigurationView.vue'
import PlatformApplicationConfigurationView from './views/PlatformApplicationConfigurationView.vue'
import PlatformWorkConfigurationView from './views/PlatformWorkConfigurationView.vue'
import { clearSession, clearSystemSession, platformContext, platformTokens, setPlatformSession, setSystemSession,
  systemContext, systemTokens, type CurrentContext } from './session'
import { api, ApiError } from './api'
import {
  allowsPermission,
  hasModuleShareManagementAccess,
  hasPlatformAdminCapability,
  canOpenPlatformConfigurationCategory,
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

/** 平台导航和受限入口按当前授权判断，不能永久使用登录时存储的权限。 */
async function refreshPlatformAuthorization() {
  const authenticated = requirePlatform()
  if (authenticated !== true) return authenticated
  const accountId = platformContext.value?.accountId
  try {
    const context = await api<CurrentContext>('/api/auth/me', {}, platformTokens.value!.accessToken)
    // 账号切换期间旧响应不能覆盖新平台身份，也不清理其系统工作区。
    if (!platformTokens.value || platformContext.value?.accountId !== accountId) return false
    if (context.accountId !== accountId || context.systemId != null) return false
    setPlatformSession(platformTokens.value, context)
    return true
  } catch (error) {
    if (platformContext.value?.accountId !== accountId) return false
    if (error instanceof ApiError && (error.httpStatus === 401 || error.code === 'ACCOUNT_DISABLED')) clearSession()
    return requirePlatform() === true ? platformNotice('authorization-unavailable') : { path: '/login' }
  }
}

/** 首页网络失败时保留可重试的壳；业务与后台入口必须刷新成功才准入。 */
async function requirePlatformHome() {
  const refreshed = await refreshPlatformAuthorization()
  if (refreshed === false) return false
  return requirePlatform()
}

function requirePlatformResource(...resourceTypes: string[]) {
  return async () => {
    const authenticated = await refreshPlatformAuthorization()
    if (authenticated !== true) return authenticated
    return hasResourceAccess(platformContext.value?.permissions, ...resourceTypes) ? true : platformNotice('access-denied')
  }
}

async function requirePlatformWorkRuntime() {
  const authenticated = await refreshPlatformAuthorization()
  if (authenticated !== true) return authenticated
  return hasWorkRuntimeAccess(platformContext.value?.permissions, 'PLATFORM') ? true : platformNotice('access-denied')
}

function requirePlatformAdmin(capability: PlatformAdminCapability) {
  return async () => {
    const authenticated = await refreshPlatformAuthorization()
    if (authenticated !== true) return authenticated
    return hasPlatformAdminCapability(platformContext.value?.permissions, capability) ? true : platformNotice('access-denied')
  }
}

/** 配置页的栏目有各自授权；直接打开链接与页面内切换使用相同边界。 */
async function requirePlatformConfigurationCategory(to: RouteLocationNormalized) {
  const authenticated = await refreshPlatformAuthorization()
  if (authenticated !== true) return authenticated
  const category = String(to.query.category || 'PLATFORM_INFO')
  return canOpenPlatformConfigurationCategory(platformContext.value?.permissions, category)
    ? true : platformNotice('access-denied')
}

/** 发布域名映射到确切的系统与工作空间；域名本身不授予成员权限。 */
async function publishedDomainEntry(path: string) {
  try {
    const query = new URLSearchParams({ host: window.location.host, path,
      scheme: window.location.protocol.replace(':', '') })
    const entry = await api<{ systemId: number; tenantId: number }>(`/api/auth/access/domain?${query}`)
    return { path: `/access/id/${entry.systemId}/${entry.tenantId}` }
  } catch { return null }
}

let systemAuthorizationRevision = 0

/** 系统路由进入前重取成员授权，撤权后不继续展示缓存中的模块与后台入口。 */
async function requireSystem(to: RouteTarget) {
  if (!systemTokens.value?.accessToken || !systemContext.value?.systemId) return platformNotice('system-context-required')
  const previous = systemContext.value
  if (String(previous.systemId) !== String(to.params.systemId)) return platformNotice('system-context-changed')
  try {
    const fresh = await api<CurrentContext>('/api/auth/me', {}, systemTokens.value.accessToken)
    const current = systemContext.value
    if (!systemTokens.value || !current || current.accountId !== previous.accountId
      || current.systemId !== previous.systemId || current.tenantId !== previous.tenantId) return false
    if (fresh.accountId !== previous.accountId || fresh.systemId !== previous.systemId
      || fresh.tenantId !== previous.tenantId) return platformNotice('system-context-changed')
    const authorizationChanged = JSON.stringify([current.roleIds, current.permissions, current.dataScopes])
      !== JSON.stringify([fresh.roleIds, fresh.permissions, fresh.dataScopes])
    setSystemSession(systemTokens.value, { ...current, ...fresh,
      systemName: current.systemName, tenantName: current.tenantName,
      contextRevision: authorizationChanged ? `${Date.now()}-${++systemAuthorizationRevision}` : current.contextRevision })
    return true
  } catch (error) {
    if (error instanceof ApiError && error.code === 'ACCOUNT_DISABLED') clearSession()
    else if (error instanceof ApiError && (error.httpStatus === 401 || error.httpStatus === 403)) clearSystemSession()
    return platformNotice('authorization-unavailable')
  }
}

async function requireSystemAdmin(to: RouteTarget) {
  const validContext = await requireSystem(to)
  if (validContext !== true) return validContext
  const permissions = systemContext.value?.permissions
  const allowed = allowsPermission(permissions, 'CONFIG', 'SYSTEM', 'MANAGE')
    || allowsPermission(permissions, 'CONFIG', 'MODULE', 'MANAGE')
    || hasModuleShareManagementAccess(permissions)
    || allowsPermission(permissions, 'AUDIT', 'EVENT', 'VIEW')
    || allowsPermission(permissions, 'FLOW', 'SYSTEM', 'DESIGN')
    || allowsPermission(permissions, 'FLOW', 'SYSTEM', 'PUBLISH')
    || allowsPermission(permissions, 'FLOW', '*', 'DESIGN')
    || allowsPermission(permissions, 'FLOW', '*', 'PUBLISH')
    || allowsPermission(permissions, 'APPLICATION', 'SYSTEM', 'VIEW')
    || allowsPermission(permissions, 'APPLICATION', 'SYSTEM', 'MANAGE')
    || allowsPermission(permissions, 'APPLICATION', '*', 'VIEW')
    || allowsPermission(permissions, 'APPLICATION', '*', 'MANAGE')
    || allowsPermission(permissions, 'AI', 'SYSTEM', 'MANAGE')
    || allowsPermission(permissions, 'MESSAGE', 'SYSTEM', 'MANAGE')
    || allowsPermission(permissions, 'MESSAGE', '*', 'MANAGE')
    || hasWorkConfigurationAccess(permissions, 'SYSTEM')
  return allowed ? true : systemNotice(String(to.params.systemId || ''), 'access-denied')
}

export const productRoutes: RouteRecordRaw[] = [
  { path: '/', component: AuthView, props: { initialMode: 'login' },
    beforeEnter: async () => await publishedDomainEntry('/') || { path: defaultEntryPath() } },
  { path: '/login', name: 'login', component: AuthView, props: { initialMode: 'login' } },
  { path: '/access/id/:systemId/:tenantId', name: 'tenant-login-id', component: AuthView, props: { initialMode: 'login' } },
  { path: '/access/:systemCode/:tenantCode', name: 'tenant-login', component: AuthView, props: { initialMode: 'login' } },
  { path: '/register', name: 'register', component: AuthView, props: { initialMode: 'register' } },
  { path: '/invitations/:token', name: 'member-invitation', component: MemberInvitationView },
  { path: '/forgot-password', name: 'forgot-password', component: AuthView, props: { initialMode: 'forgot' } },
  { path: '/reset-password', name: 'reset-password', component: AuthView, props: { initialMode: 'reset' } },
  { path: '/sso/callback', name: 'sso-callback', component: SsoCallbackView },
  { path: '/platform', name: 'platform', component: PlatformHomeView, beforeEnter: requirePlatformHome },
  { path: '/platform/dashboard', name: 'platform-dashboard', component: PlatformDashboardView, beforeEnter: requirePlatformHome,
    meta: { title: '首页', capability: 'dashboard', description: '汇总当前账号有权访问的系统、待办、任务和消息。' } },
  { path: '/platform/my-dashboard', name: 'platform-personal-dashboard', component: PlatformPersonalDashboardView, beforeEnter: requirePlatformHome,
    meta: { title: '我的首页', capability: 'dashboard', description: '查看和设置当前账号的个人工作首页。' } },
  { path: '/platform/flows', name: 'platform-flows', component: PlatformFlowView, beforeEnter: requirePlatformResource('FLOW'),
    meta: { title: '流程', capability: 'flow', description: '查看权限范围内的系统流程和通过应用开放的外部流程。' } },
  { path: '/platform/applications', name: 'platform-applications', component: PlatformApplicationView, beforeEnter: requirePlatformResource('APPLICATION'),
    meta: { title: '应用', capability: 'application', description: '管理系统之间及平台内外访问的授权桥接。' } },
  { path: '/platform/tasks', name: 'platform-tasks', component: WorkHubView, props: { context: 'platform' }, beforeEnter: requirePlatformWorkRuntime,
    meta: { title: '任务', capability: 'task', description: '处理普通任务、项目任务和手工创建的每日日志。' } },
  { path: '/platform/ai', name: 'platform-ai', component: PlatformFeatureView, beforeEnter: requirePlatformHome,
    meta: { title: '智能助手', capability: 'ai', description: '查询当前账号可见的平台信息，并引导进入目标系统。' } },
  { path: '/platform/todos', name: 'platform-todos', component: PlatformTodoView, beforeEnter: requirePlatformResource('TODO'),
    meta: { title: '待办', capability: 'todo', description: '集中处理审批、今日需联系记录和其他待办信息。' } },
  { path: '/platform/messages', name: 'platform-messages', component: PlatformMessageView, beforeEnter: requirePlatformResource('MESSAGE'),
    meta: { title: '消息', capability: 'message', description: '查看通知消息及其处理记录。' } },
  { path: '/platform/admin/configurations', name: 'platform-configurations', component: PlatformConfigurationsView, beforeEnter: requirePlatformConfigurationCategory },
  { path: '/platform/admin/identity-providers', name: 'platform-identity-providers', component: PlatformSsoProvidersView, beforeEnter: requirePlatformAdmin('identity') },
  { path: '/platform/admin/flows', name: 'platform-flow-configurations', component: PlatformFlowAdminView, beforeEnter: requirePlatformAdmin('flow') },
  { path: '/platform/admin/applications', name: 'platform-application-configurations', component: PlatformApplicationConfigurationView, beforeEnter: requirePlatformAdmin('application') },
  { path: '/platform/admin/ai', name: 'platform-ai-configurations', component: PlatformAiConfigurationView, beforeEnter: requirePlatformAdmin('ai') },
  { path: '/platform/admin/dashboards', name: 'platform-dashboard-configurations', component: PlatformDashboardConfigurationView, beforeEnter: requirePlatformAdmin('configuration') },
  { path: '/platform/admin/messages', name: 'platform-message-configurations', component: PlatformMessageConfigurationView, beforeEnter: requirePlatformAdmin('message') },
  { path: '/platform/admin/work', name: 'platform-work-configurations', component: PlatformWorkConfigurationView, beforeEnter: requirePlatformAdmin('work') },
  { path: '/profile', name: 'profile', component: PersonalCenterView, beforeEnter: requirePlatformHome },
  { path: '/systems/:systemId', name: 'system-home', component: SystemHomeView, beforeEnter: requireSystem },
  { path: '/systems/:systemId/admin', name: 'system-admin', component: SystemAdminHomeView, beforeEnter: requireSystemAdmin },
  { path: '/:pathMatch(.*)*', component: AuthView, props: { initialMode: 'login' },
    beforeEnter: async to => await publishedDomainEntry(to.path) || unavailableRoute(to as RouteTarget) },
]
