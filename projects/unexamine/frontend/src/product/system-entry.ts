import { api } from './api'
import {
  platformContext,
  platformTokens,
  setSystemSession,
  systemContext,
  type CurrentContext,
  type SessionTokens,
} from './session'

export interface SystemEntryResult {
  systemId: number
  systemCode: string
  systemName: string
  tenantMode: string
  tenantId: number
  tenantName: string
  systemMemberId: number
  tenantMemberId: number
  roleIds: number[]
  permissions: CurrentContext['permissions']
  dataScopes: CurrentContext['dataScopes']
  contextRevision: string
  redrawScopes: string[]
  tenantSwitchContext: CurrentContext['tenantSwitchContext']
  tokens: SessionTokens
}

export async function establishSystemSession(systemId: number) {
  return enterSystem(`/api/systems/${systemId}/enter`)
}

/** 专属入口按指定空间进入，不能退回该账号的默认空间。 */
export async function establishTenantSession(systemId: number, tenantId: number, tenantMode: string) {
  return enterSystem(tenantMode === 'SINGLE'
    ? `/api/systems/${systemId}/enter`
    : `/api/systems/${systemId}/tenants/${tenantId}/enter`, tenantId)
}

async function enterSystem(path: string, expectedTenantId?: number) {
  const token = platformTokens.value?.accessToken
  const platform = platformContext.value
  if (!token || !platform) throw new Error('平台会话已失效，请重新登录')

  const entry = await api<SystemEntryResult>(path, {
    method: 'POST',
    body: JSON.stringify({
      previousSystemId: systemContext.value?.systemId ?? null,
      previousTenantId: systemContext.value?.tenantId ?? null,
    }),
  }, token)

  if (expectedTenantId && entry.tenantId !== expectedTenantId) {
    throw new Error('专属入口与当前工作空间不一致，请重新打开登录地址')
  }

  setSystemSession(entry.tokens, {
    accountId: platform.accountId,
    platformId: platform.platformId,
    systemId: entry.systemId,
    tenantId: entry.tenantId,
    memberId: entry.systemMemberId,
    tenantMemberId: entry.tenantMemberId,
    username: platform.username,
    displayName: platform.displayName,
    mfaLevel: platform.mfaLevel,
    systemName: entry.systemName,
    tenantName: entry.tenantName,
    roleIds: entry.roleIds,
    permissions: entry.permissions,
    dataScopes: entry.dataScopes,
    contextRevision: entry.contextRevision,
    redrawScopes: entry.redrawScopes,
    tenantSwitchContext: entry.tenantSwitchContext,
  })
  return entry
}
