import { createRouter, createWebHistory } from 'vue-router'

import { ApiRequestError } from '@/services/api'
import { useSessionStore } from '@/stores/session'
import { canAccess } from './access'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/auth',
      component: () => import('@/layouts/AuthLayout.vue'),
      meta: { public: true },
      children: [
        { path: 'login', name: 'login', meta: { guestOnly: true }, component: () => import('@/views/auth/LoginView.vue') },
        { path: 'register', name: 'register', meta: { guestOnly: true }, component: () => import('@/views/auth/RegisterView.vue') },
        { path: 'password/forgot', name: 'forgot-password', meta: { guestOnly: true }, component: () => import('@/views/auth/ForgotPasswordView.vue') },
        { path: 'password/reset', name: 'reset-password', meta: { guestOnly: true }, component: () => import('@/views/auth/ResetPasswordView.vue') },
        { path: 'password/reset/success', name: 'password-reset-success', meta: { guestOnly: true }, component: () => import('@/views/auth/PasswordResetSuccessView.vue') },
        { path: 'sso/callback', name: 'sso-callback', meta: { guestOnly: true }, component: () => import('@/views/auth/SsoCallbackView.vue') },
        { path: 'mfa', name: 'mfa', meta: { guestOnly: true }, component: () => import('@/views/auth/MfaView.vue') },
      ],
    },
    { path: '/account', name: 'account-center', component: () => import('@/views/account/AccountCenterView.vue') },
    {
      path: '/platform',
      component: () => import('@/layouts/PlatformLayout.vue'),
      meta: { context: 'PLATFORM' },
      children: [
        { path: '', redirect: '/platform/workbench' },
        { path: 'workbench', name: 'platform-workbench', component: () => import('@/views/platform/PlatformWorkbenchView.vue') },
        { path: 'dashboard', name: 'platform-dashboard', meta: { requiredPermissions: ['platform.dashboard.view'] }, component: () => import('@/views/platform/PlatformDashboardView.vue') },
        { path: 'flows', name: 'platform-flows', meta: { requiredPermissions: ['platform.flow.read'] }, component: () => import('@/views/platform/PlatformFlowView.vue') },
        { path: 'work', name: 'platform-work', meta: { requiredPermissions: ['platform.work.read'] }, component: () => import('@/views/platform/PlatformWorkView.vue') },
        { path: 'todos', name: 'platform-todos', meta: { requiredPermissions: ['platform.task.read'] }, component: () => import('@/views/platform/PlatformTodoView.vue') },
        { path: 'messages', name: 'platform-messages', meta: { requiredPermissions: ['platform.message.read'] }, component: () => import('@/views/platform/PlatformMessageView.vue') },
        { path: 'agent', name: 'platform-agent', meta: { requiredPermissions: ['platform.runtime.access', 'platform.ai.agent.use'] }, component: () => import('@/views/platform/PlatformAgentView.vue') },
      ],
    },
    {
      path: '/platform/admin',
      component: () => import('@/layouts/PlatformAdminLayout.vue'),
      meta: { context: 'PLATFORM', requiredShell: 'PLATFORM_ADMIN' },
      children: [
        { path: '', name: 'platform-admin-index', component: () => import('@/views/admin/AdminIndexView.vue'), props: { zone: 'platform' } },
        { path: 'systems', name: 'platform-admin-systems', meta: { requiredPermissions: ['platform.system.manage'] }, component: () => import('@/views/platform/admin/PlatformSystemsView.vue') },
        { path: 'organization', name: 'platform-admin-organization', meta: { requiredPermissions: ['platform.organization.manage'] }, component: () => import('@/views/platform/admin/PlatformOrganizationView.vue') },
        { path: 'identity-providers', name: 'platform-admin-identity-providers', meta: { requiredPermissions: ['platform.organization.manage'] }, component: () => import('@/views/platform/admin/PlatformIdentityProvidersView.vue') },
        { path: 'roles', name: 'platform-admin-roles', meta: { requiredPermissions: ['platform.role.manage'] }, component: () => import('@/views/platform/admin/PlatformRolesView.vue') },
        { path: 'agent', name: 'platform-admin-agent', meta: { requiredPermissions: ['platform.ai.policy.manage'] }, component: () => import('@/views/platform/admin/PlatformAiAgentConfigView.vue') },
        { path: 'audit-logs', name: 'platform-admin-audit-logs', meta: { requiredPermissions: ['platform.audit.view'] }, component: () => import('@/views/admin/UnifiedAuditLogsView.vue'), props: { zone: 'platform' } },
        { path: 'global-settings', name: 'platform-admin-global-settings', meta: { requiredPermissions: ['platform.settings.manage'] }, component: () => import('@/views/platform/admin/PlatformGlobalSettingsView.vue') },
        { path: 'openapi-applications', name: 'platform-admin-openapi-applications', meta: { requiredPermissions: ['platform.openapi.application.manage'] }, component: () => import('@/views/platform/admin/PlatformOpenApiApplicationsView.vue') },
        { path: 'flows', name: 'platform-admin-flows', meta: { requiredPermissions: ['platform.flow.manage'] }, component: () => import('@/views/platform/admin/PlatformFlowAdminView.vue') },
        { path: 'dashboards', name: 'platform-admin-dashboards', meta: { requiredPermissions: ['platform.dashboard.manage'] }, component: () => import('@/views/platform/admin/PlatformDashboardAdminView.vue') },
      ],
    },
    {
      path: '/systems/:systemId',
      component: () => import('@/layouts/SystemLayout.vue'),
      meta: { context: 'SYSTEM' },
      children: [
        { path: '', redirect: (to) => `/systems/${String(to.params.systemId)}/workbench` },
        { path: 'workbench', name: 'system-workbench', component: () => import('@/views/system/SystemWorkbenchView.vue') },
        { path: 'dashboard', name: 'system-dashboard', component: () => import('@/views/system/SystemDashboardView.vue') },
        { path: 'kpis', name: 'system-kpis', component: () => import('@/views/system/SystemKpisView.vue') },
        { path: 'reports/:reportCode?', name: 'system-reports', component: () => import('@/views/system/SystemReportsView.vue') },
        { path: 'operations', name: 'system-operations', component: () => import('@/views/system/OperationsDashboardView.vue') },
        { path: 'todos', name: 'system-todos', component: () => import('@/views/system/TodoActionCenterView.vue') },
        { path: 'tasks', name: 'system-work-tasks', meta: { anyPermissions: ['work.task.access', 'work.report.access', 'work.report.create', 'work.report.manage'] }, component: () => import('@/views/system/WorkTasksView.vue') },
        { path: 'messages', name: 'system-messages', meta: { requiredPermissions: ['event.message.access'] }, component: () => import('@/views/system/MessageInboxView.vue') },
        { path: 'files', name: 'system-files', meta: { anyPermissions: ['file.create', 'file.read', 'file.reference', 'file.manage'] }, component: () => import('@/views/system/FileAssetsView.vue') },
        { path: 'flows', name: 'system-flows', meta: { anyPermissions: ['flow.definition.manage', 'flow.instance.start', 'flow.instance.decide', 'flow.instance.read', 'flow.external-task.work'] }, component: () => import('@/views/system/FlowView.vue') },
        { path: 'agent', name: 'system-agent', meta: { requiredPermissions: ['ai.agent.use'] }, component: () => import('@/views/system/SystemAgentView.vue') },
      ],
    },
    {
      path: '/systems/:systemId/admin',
      component: () => import('@/layouts/SystemAdminLayout.vue'),
      meta: { context: 'SYSTEM', requiredShell: 'SYSTEM_ADMIN' },
      children: [
        { path: '', name: 'system-admin-index', component: () => import('@/views/admin/AdminIndexView.vue'), props: { zone: 'system' } },
        { path: 'onboarding', name: 'system-admin-onboarding', meta: { requiredPermissions: ['system.admin.access', 'module.config.manage', 'system.member.manage'] }, component: () => import('@/views/system/admin/SystemOnboardingView.vue') },
        { path: 'settings', name: 'system-admin-settings', meta: { requiredPermissions: ['system.settings.manage'] }, component: () => import('@/views/system/admin/SystemSettingsView.vue') },
        { path: 'tenants', name: 'system-admin-tenants', meta: { requiredPermissions: ['system.tenant.manage'] }, component: () => import('@/views/system/admin/SystemTenantsView.vue') },
        { path: 'organization', name: 'system-admin-organization', meta: { anyPermissions: ['system.organization.manage', 'system.member.manage'] }, component: () => import('@/views/system/admin/SystemOrganizationView.vue') },
        { path: 'identity-sync', name: 'system-admin-identity-sync', meta: { requiredPermissions: ['system.organization.manage'] }, component: () => import('@/views/system/admin/SystemIdentitySyncView.vue') },
        { path: 'roles', name: 'system-admin-roles', meta: { requiredPermissions: ['system.role.manage'] }, component: () => import('@/views/system/admin/SystemRolesView.vue') },
        { path: 'audit-logs', name: 'system-admin-audit-logs', meta: { requiredPermissions: ['system.audit.view'] }, component: () => import('@/views/admin/UnifiedAuditLogsView.vue'), props: { zone: 'system' } },
        { path: 'openapi-applications', name: 'system-admin-openapi-applications', meta: { requiredPermissions: ['openapi.application.manage'] }, component: () => import('@/views/system/admin/OpenApiApplicationsView.vue') },
        { path: 'access-requests', name: 'system-admin-access-requests', meta: { requiredPermissions: ['system.access.review'] }, component: () => import('@/views/system/admin/SystemAccessRequestsView.vue') },
        { path: 'message-templates', name: 'system-admin-message-templates', meta: { requiredPermissions: ['event.template.manage'] }, component: () => import('@/views/system/admin/MessageTemplatesView.vue') },
        { path: 'work-configuration', name: 'system-admin-work-configuration', meta: { requiredPermissions: ['work.config.manage'] }, component: () => import('@/views/system/admin/WorkConfigurationView.vue') },
        { path: 'configuration', name: 'system-admin-configuration', meta: { requiredPermissions: ['system.admin.access', 'module.config.manage'] }, component: () => import('@/views/system/admin/SystemConfigStudioView.vue') },
        { path: 'data-sources', name: 'system-admin-data-sources', meta: { requiredPermissions: ['system.admin.access', 'module.config.manage'] }, component: () => import('@/views/system/admin/DataSourcesView.vue') },
        { path: 'dashboards', name: 'system-admin-dashboards', meta: { requiredPermissions: ['system.admin.access', 'module.config.manage'] }, component: () => import('@/views/system/admin/DashboardsView.vue') },
        { path: 'kpis', name: 'system-admin-kpis', meta: { requiredPermissions: ['system.admin.access', 'module.config.manage'] }, component: () => import('@/views/system/admin/KpisView.vue') },
        { path: 'reports', name: 'system-admin-reports', meta: { requiredPermissions: ['system.admin.access', 'module.config.manage'] }, component: () => import('@/views/system/admin/ReportsView.vue') },
        { path: 'agent', name: 'system-admin-agent', meta: { requiredPermissions: ['ai.policy.manage'] }, component: () => import('@/views/system/admin/AiAgentConfigView.vue') },
      ],
    },
    {
      path: '/no-member/:systemId',
      name: 'no-member',
      component: () => import('@/views/access/NoMemberView.vue'),
    },
    { path: '/', redirect: '/platform/workbench' },
    { path: '/:pathMatch(.*)*', redirect: '/platform/workbench' },
  ],
})

router.beforeEach(async (to) => {
  const session = useSessionStore()
  await session.bootstrap()

  if (to.meta.public) {
    if (session.authenticated && to.meta.guestOnly) {
      return session.isSystem && session.context?.systemId
        ? `/systems/${session.context.systemId}/workbench`
        : '/platform/workbench'
    }
    return true
  }
  if (!session.authenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.meta.context === 'PLATFORM' && !session.isPlatform) {
    await session.switchPlatform()
  }
  if (to.meta.context === 'SYSTEM') {
    const systemId = String(to.params.systemId)
    if (!session.isSystem || session.context?.systemId !== systemId) {
      try {
        await session.switchSystem(systemId)
      } catch (error) {
        if (error instanceof ApiRequestError && [403, 404].includes(error.status)) {
          return { name: 'no-member', params: { systemId } }
        }
        throw error
      }
    }
  }
  const policy = {
    shell: to.meta.requiredShell as Parameters<typeof canAccess>[1]['shell'],
    permissions: to.meta.requiredPermissions as string[] | undefined,
    anyPermissions: to.meta.anyPermissions as string[] | undefined,
  }
  if (!canAccess(session.context, policy)) {
    if (to.meta.requiredShell === 'PLATFORM_ADMIN' && session.hasShell('PLATFORM_ADMIN')) return { name: 'platform-admin-index' }
    if (to.meta.requiredShell === 'SYSTEM_ADMIN' && session.hasShell('SYSTEM_ADMIN')) {
      return { name: 'system-admin-index', params: { systemId: String(to.params.systemId) } }
    }
    return session.isSystem && session.context?.systemId
      ? `/systems/${session.context.systemId}/workbench`
      : '/platform/workbench'
  }
  return true
})

export default router
