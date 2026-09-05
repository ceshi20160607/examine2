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
  const token = platformTokens.value?.accessToken
  const platform = platformContext.value
  if (!token || !platform) throw new Error('平台会话已失效，请重新登录')

  const entry = await api<SystemEntryResult>(`/api/systems/${systemId}/enter`, {
    method: 'POST',
    body: JSON.stringify({
      previousSystemId: systemContext.value?.systemId ?? null,
      previousTenantId: systemContext.value?.tenantId ?? null,
    }),
  }, token)

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
