import { computed, ref } from 'vue'

export interface SessionTokens {
  accessToken: string
  refreshToken: string
  accessExpireTime: string
  refreshExpireTime: string
}

export interface CurrentContext {
  accountId: number
  platformId: number
  systemId: number | null
  tenantId: number | null
  memberId: number | null
  tenantMemberId: number | null
  username: string
  displayName: string
  mfaLevel: string
  systemName?: string
  tenantName?: string
  roleIds: number[]
  permissions: Array<{ resourceType: string; resourceCode: string; actionCode: string }>
  dataScopes?: Record<string, { mode: string; terms: Array<{ type: string; roleIds: number[] }> }>
  contextRevision?: string
  redrawScopes?: string[]
  tenantSwitchContext?: {
    tenantId: number
    tenantRoleIds: number[]
    tenantDataScope: Record<string, { mode: string; terms: Array<{ type: string; roleIds: number[] }> }>
    tenantSwitchable: boolean
    disabledReason?: string
  }
}

const PLATFORM_TOKENS_KEY = 'unexamine.platform.tokens'
const SYSTEM_TOKENS_KEY = 'unexamine.system.tokens'
const PLATFORM_CONTEXT_KEY = 'unexamine.platform.context'
const SYSTEM_CONTEXT_KEY = 'unexamine.system.context'

function read<T>(key: string): T | null {
  try {
    const value = sessionStorage.getItem(key)
    return value ? (JSON.parse(value) as T) : null
  } catch {
    return null
  }
}

function persist<T>(key: string, value: T | null) {
  if (value === null) sessionStorage.removeItem(key)
  else sessionStorage.setItem(key, JSON.stringify(value))
}

export const platformTokens = ref<SessionTokens | null>(read(PLATFORM_TOKENS_KEY))
export const systemTokens = ref<SessionTokens | null>(read(SYSTEM_TOKENS_KEY))
export const platformContext = ref<CurrentContext | null>(read(PLATFORM_CONTEXT_KEY))
export const systemContext = ref<CurrentContext | null>(read(SYSTEM_CONTEXT_KEY))
export const isAuthenticated = computed(() => Boolean(platformTokens.value?.accessToken))

export type SessionScope = 'platform' | 'system'

export function setPlatformSession(tokens: SessionTokens, context?: CurrentContext) {
  platformTokens.value = tokens
  persist(PLATFORM_TOKENS_KEY, tokens)
  if (context) {
    platformContext.value = context
    persist(PLATFORM_CONTEXT_KEY, context)
  }
}

export function setSystemSession(tokens: SessionTokens, context: CurrentContext) {
  // 切换系统或成员授权变更后，旧权限下保存的业务缓存不能继续复用。
  const changed = systemContext.value?.systemId !== context.systemId || systemContext.value?.tenantId !== context.tenantId
    || systemContext.value?.contextRevision !== context.contextRevision
  if (changed) {
    Object.keys(sessionStorage).filter((key) => key.startsWith('unexamine.system.cache.'))
      .forEach((key) => sessionStorage.removeItem(key))
  }
  systemTokens.value = tokens
  systemContext.value = context
  persist(SYSTEM_TOKENS_KEY, tokens)
  persist(SYSTEM_CONTEXT_KEY, context)
}

export function sessionScopeForAccessToken(accessToken: string): SessionScope | null {
  if (platformTokens.value?.accessToken === accessToken) return 'platform'
  if (systemTokens.value?.accessToken === accessToken) return 'system'
  return null
}

export function tokensForScope(scope: SessionScope) {
  return scope === 'platform' ? platformTokens.value : systemTokens.value
}

export function replaceSessionTokens(scope: SessionScope, tokens: SessionTokens) {
  if (scope === 'platform') {
    platformTokens.value = tokens
    persist(PLATFORM_TOKENS_KEY, tokens)
    return
  }
  systemTokens.value = tokens
  persist(SYSTEM_TOKENS_KEY, tokens)
}

export function clearSessionScope(scope: SessionScope) {
  if (scope === 'platform') clearSession()
  else clearSystemSession()
}

export function clearSession() {
  platformTokens.value = null
  systemTokens.value = null
  platformContext.value = null
  systemContext.value = null
  ;[PLATFORM_TOKENS_KEY, SYSTEM_TOKENS_KEY, PLATFORM_CONTEXT_KEY, SYSTEM_CONTEXT_KEY]
    .forEach((key) => sessionStorage.removeItem(key))
}

export function clearSystemSession() {
  systemTokens.value = null
  systemContext.value = null
  sessionStorage.removeItem(SYSTEM_TOKENS_KEY)
  sessionStorage.removeItem(SYSTEM_CONTEXT_KEY)
}
