import { computed, ref } from 'vue'

export interface SessionTokens {
  accessToken: string
  refreshToken: string
  accessExpiresAt: string
  refreshExpiresAt: string
}

export interface CurrentContext {
  accountId: number
  systemId: number | null
  tenantId: number | null
  memberId: number | null
  username: string
  displayName: string
  systemName?: string
  tenantName?: string
  roleIds: number[]
  permissions: Array<{ resourceType: string; resourceCode: string; actionCode: string }>
  dataScopes?: Record<string, { mode: string; terms: Array<{ type: string; roleIds: number[] }> }>
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

export function setPlatformSession(tokens: SessionTokens, context?: CurrentContext) {
  platformTokens.value = tokens
  persist(PLATFORM_TOKENS_KEY, tokens)
  if (context) {
    platformContext.value = context
    persist(PLATFORM_CONTEXT_KEY, context)
  }
}

export function setSystemSession(tokens: SessionTokens, context: CurrentContext) {
  systemTokens.value = tokens
  systemContext.value = context
  persist(SYSTEM_TOKENS_KEY, tokens)
  persist(SYSTEM_CONTEXT_KEY, context)
}

export function clearSession() {
  platformTokens.value = null
  systemTokens.value = null
  platformContext.value = null
  systemContext.value = null
  ;[PLATFORM_TOKENS_KEY, SYSTEM_TOKENS_KEY, PLATFORM_CONTEXT_KEY, SYSTEM_CONTEXT_KEY]
    .forEach((key) => sessionStorage.removeItem(key))
}
