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
      && matches(permission.actionCode, actionCode)) ?? false
}

export function hasRuntimeModuleAccess(permissions: Permission[] | undefined) {
  return permissions?.some((permission) =>
    matches(permission.resourceType, 'MODULE')
      && ['*', 'LIST', 'DETAIL', 'CREATE', 'UPDATE'].includes(permission.actionCode)) ?? false
}
