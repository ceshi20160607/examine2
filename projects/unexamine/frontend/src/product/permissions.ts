import type { CurrentContext } from './session'

type Permission = CurrentContext['permissions'][number]

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

export function buildPlatformNavigation(permissions: Permission[] | undefined) {
  return [
    { code: 'home', label: '首页', path: '/platform/dashboard' },
    { code: 'systems', label: '系统', path: '/platform' },
    ...(hasResourceAccess(permissions, 'FLOW') ? [{ code: 'flow', label: '流程', path: '/platform/flows' }] : []),
    ...(hasResourceAccess(permissions, 'APPLICATION') ? [{ code: 'applications', label: '应用', path: '/platform/applications' }] : []),
    { code: 'tasks', label: '任务', path: '/platform/tasks' },
    ...(hasResourceAccess(permissions, 'AI') ? [{ code: 'assistant', label: '智能助手', path: '/platform/ai' }] : []),
  ]
}

export function hasPlatformManagementAccess(permissions: Permission[] | undefined) {
  return allowsPermission(permissions, 'PLATFORM', 'IDENTITY_PROVIDER', 'MANAGE')
    || allowsPermission(permissions, 'PLATFORM', 'CONFIGURATION', 'MANAGE')
    || allowsPermission(permissions, 'FLOW', 'PLATFORM', 'DESIGN')
    || allowsPermission(permissions, 'FLOW', 'PLATFORM', 'PUBLISH')
    || allowsPermission(permissions, 'PLATFORM', 'AI', 'MANAGE')
}
