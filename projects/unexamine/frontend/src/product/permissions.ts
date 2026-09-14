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
  }
}

export function platformAdminEntryPath(permissions: Permission[] | undefined) {
  if (hasPlatformAdminCapability(permissions, 'configuration')) return '/platform/admin/configurations'
  if (hasPlatformAdminCapability(permissions, 'identity')) return '/platform/admin/identity-providers'
  if (hasPlatformAdminCapability(permissions, 'flow')) return '/platform/admin/flows'
  if (hasPlatformAdminCapability(permissions, 'application')) return '/platform/admin/applications'
  if (hasPlatformAdminCapability(permissions, 'ai')) return '/platform/admin/ai'
  if (hasPlatformAdminCapability(permissions, 'message')) return '/platform/admin/messages'
  if (hasPlatformAdminCapability(permissions, 'work')) return '/platform/admin/work'
  return null
}

export function buildPlatformNavigation(permissions: Permission[] | undefined) {
  return [
    { code: 'home', label: '首页', path: '/platform/dashboard' },
    { code: 'systems', label: '系统', path: '/platform' },
    ...(hasResourceAccess(permissions, 'FLOW') ? [{ code: 'flow', label: '流程', path: '/platform/flows' }] : []),
    ...(hasResourceAccess(permissions, 'APPLICATION') ? [{ code: 'applications', label: '应用', path: '/platform/applications' }] : []),
    ...(hasWorkRuntimeAccess(permissions, 'PLATFORM') ? [{ code: 'tasks', label: '任务', path: '/platform/tasks' }] : []),
    ...(hasResourceAccess(permissions, 'AI') ? [{ code: 'assistant', label: '智能助手', path: '/platform/ai' }] : []),
  ]
}

export function hasPlatformManagementAccess(permissions: Permission[] | undefined) {
  return platformAdminEntryPath(permissions) !== null
}
