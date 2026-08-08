import type { SessionContext } from '@/types/session'

export type Shell = SessionContext['shells'][number]

export interface RouteAccessPolicy {
  shell?: Shell
  permissions?: string[]
  anyPermissions?: string[]
}

export function hasShell(context: SessionContext | null, shell: Shell) {
  return context?.shells.includes(shell) ?? false
}

export function hasPermission(context: SessionContext | null, permission: string) {
  return context?.permissions.includes(permission) ?? false
}

export function canAccess(context: SessionContext | null, policy: RouteAccessPolicy) {
  if (policy.shell && !hasShell(context, policy.shell)) return false
  if (policy.permissions?.some((permission) => !hasPermission(context, permission))) return false
  if (policy.anyPermissions?.length && !policy.anyPermissions.some((permission) => hasPermission(context, permission))) return false
  return true
}
