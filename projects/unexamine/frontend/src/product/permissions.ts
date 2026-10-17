import type { CurrentContext } from './session'

type Permission = CurrentContext['permissions'][number]

export type PlatformAdminCapability =
  | 'configuration'
  | 'identity'
  | 'flow'
  | 'application'
  | 'ai'
  | 'message'
  | 'work'
  | 'audit'
  | 'organization'
  | 'authorization'

function matches(granted: string, required: string) {
  return granted === '*' || granted === required
}

export function allowsPermission(
  permissions: Permission[] | undefined,
  resourceType: string,
  resourceCode: string,
  actionCode: string,
) {
  return permissions?.some((permission) =>
    matches(permission.resourceType, resourceType)
      && matches(permission.resourceCode, resourceCode)
      && matches(permission.actionCode, actionCode)
      || permission.resourceType === 'PLATFORM'
        && permission.resourceCode === '*'
        && permission.actionCode === '*') ?? false
}

export function hasRuntimeModuleAccess(permissions: Permission[] | undefined) {
  return permissions?.some((permission) =>
    matches(permission.resourceType, 'MODULE')
      && ['*', 'LIST', 'DETAIL', 'CREATE', 'UPDATE'].includes(permission.actionCode)) ?? false
}

/** 模块 SHARE 授权可进入系统后台维护跨工作空间范围，不要求同时具备模块搭建权限。 */
export function hasModuleShareManagementAccess(permissions: Permission[] | undefined) {
  return permissions?.some(permission =>
    matches(permission.resourceType, 'MODULE') && matches(permission.actionCode, 'SHARE')
      || permission.resourceType === 'PLATFORM' && permission.resourceCode === '*' && permission.actionCode === '*') ?? false
}

export function hasResourceAccess(permissions: Permission[] | undefined, ...resourceTypes: string[]) {
  return permissions?.some((permission) =>
    permission.resourceType === '*'
      || resourceTypes.includes(permission.resourceType)
      || permission.resourceType === 'PLATFORM' && permission.resourceCode === '*' && permission.actionCode === '*') ?? false
}

function allowsAnyAction(
  permissions: Permission[] | undefined,
  resourceType: string,
  resourceCode: string,
  actions: string[],
) {
  return actions.some(action => allowsPermission(permissions, resourceType, resourceCode, action))
    || actions.some(action => allowsPermission(permissions, resourceType, '*', action))
}

export function hasWorkRuntimeAccess(permissions: Permission[] | undefined, context: 'PLATFORM' | 'SYSTEM') {
  return allowsAnyAction(permissions, 'WORK', context, ['VIEW', 'CREATE', 'UPDATE', 'VIEW_CALENDAR'])
}

export function hasWorkConfigurationAccess(permissions: Permission[] | undefined, context: 'PLATFORM' | 'SYSTEM') {
  return allowsAnyAction(permissions, 'WORK', context, ['VIEW_CONFIG', 'CONFIGURE', 'PUBLISH_CONFIG'])
}

export function hasPlatformAdminCapability(
  permissions: Permission[] | undefined,
  capability: PlatformAdminCapability,
) {
  switch (capability) {
    case 'configuration':
      return allowsPermission(permissions, 'PLATFORM', 'CONFIGURATION', 'MANAGE')
    case 'identity':
      return allowsPermission(permissions, 'PLATFORM', 'IDENTITY_PROVIDER', 'MANAGE')
    case 'flow':
      return allowsPermission(permissions, 'FLOW', 'PLATFORM', 'DESIGN')
        || allowsPermission(permissions, 'FLOW', 'PLATFORM', 'PUBLISH')
        || allowsPermission(permissions, 'FLOW', '*', 'DESIGN')
        || allowsPermission(permissions, 'FLOW', '*', 'PUBLISH')
    case 'application':
      return allowsAnyAction(permissions, 'APPLICATION', 'PLATFORM', ['VIEW', 'MANAGE'])
    case 'ai':
      return allowsPermission(permissions, 'PLATFORM', 'AI', 'MANAGE')
    case 'message':
      return allowsAnyAction(permissions, 'MESSAGE', 'PLATFORM', ['MANAGE'])
    case 'work':
      return hasWorkConfigurationAccess(permissions, 'PLATFORM')
    case 'audit':
      return allowsPermission(permissions, 'PLATFORM', 'AUDIT', 'VIEW')
    case 'organization':
      return allowsPermission(permissions, 'PLATFORM', 'ORGANIZATION', 'VIEW')
    case 'authorization':
      return allowsPermission(permissions, 'PLATFORM', 'AUTHORIZATION', 'VIEW')
  }
}

/** 同一路由承载多个平台后台栏目，入口权限必须按当前栏目而不是统一按配置权限判断。 */
export function canOpenPlatformConfigurationCategory(
  permissions: Permission[] | undefined, category: string,
) {
  const capability: PlatformAdminCapability | undefined = {
    PLATFORM_INFO: 'configuration',
    ORGANIZATION: 'organization',
    AUTHORIZATION: 'authorization',
    DICTIONARY: 'configuration',
    SYSTEM: 'configuration',
    BUSINESS_PARAMETER: 'configuration',
    AUDIT: 'audit',
    OPERATIONS: 'configuration',
  }[category] as PlatformAdminCapability | undefined
  return capability ? hasPlatformAdminCapability(permissions, capability) : false
}

export function platformAdminEntryPath(permissions: Permission[] | undefined) {
  if (hasPlatformAdminCapability(permissions, 'configuration')) return '/platform/admin/configurations'
  if (hasPlatformAdminCapability(permissions, 'identity')) return '/platform/admin/identity-providers'
  if (hasPlatformAdminCapability(permissions, 'flow')) return '/platform/admin/flows'
  if (hasPlatformAdminCapability(permissions, 'application')) return '/platform/admin/applications'
  if (hasPlatformAdminCapability(permissions, 'ai')) return '/platform/admin/ai'
  if (hasPlatformAdminCapability(permissions, 'message')) return '/platform/admin/messages'
  if (hasPlatformAdminCapability(permissions, 'work')) return '/platform/admin/work'
  if (hasPlatformAdminCapability(permissions, 'audit')) return '/platform/admin/configurations?category=AUDIT'
  if (hasPlatformAdminCapability(permissions, 'organization')) return '/platform/admin/configurations?category=ORGANIZATION'
  if (hasPlatformAdminCapability(permissions, 'authorization')) return '/platform/admin/configurations?category=AUTHORIZATION'
  return null
}

export function buildPlatformNavigation(permissions: Permission[] | undefined) {
  return [
    { code: 'home', label: '首页', path: '/platform/dashboard' },
    { code: 'systems', label: '系统', path: '/platform' },
    ...(hasResourceAccess(permissions, 'FLOW') ? [{ code: 'flow', label: '流程', path: '/platform/flows' }] : []),
    ...(hasResourceAccess(permissions, 'APPLICATION') ? [{ code: 'applications', label: '应用', path: '/platform/applications' }] : []),
    ...(hasWorkRuntimeAccess(permissions, 'PLATFORM') ? [{ code: 'tasks', label: '任务', path: '/platform/tasks' }] : []),
    { code: 'assistant', label: '智能助手', path: '/platform/ai' },
  ]
}

export function hasPlatformManagementAccess(permissions: Permission[] | undefined) {
  return platformAdminEntryPath(permissions) !== null
}
