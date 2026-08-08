import { defineStore } from 'pinia'

import { ApiRequestError, apiRequest } from '@/services/api'
import { contextAccessApi } from '@/services/access'
import type { AuthResult, SessionContext, SystemSummary, TenantSummary } from '@/types/session'

interface LoginInput {
  account: string
  password: string
}

interface RegisterInput {
  username: string
  displayName: string
  password: string
  systemName: string
  systemCode: string
}

interface ChangePasswordInput {
  currentPassword: string
  newPassword: string
}

export const useSessionStore = defineStore('session', {
  state: () => ({
    context: null as SessionContext | null,
    systems: [] as SystemSummary[],
    tenants: [] as TenantSummary[],
    initialized: false,
    loading: false,
  }),
  getters: {
    authenticated: (state) => state.context !== null,
    isPlatform: (state) => state.context?.type === 'PLATFORM',
    isSystem: (state) => state.context?.type === 'SYSTEM',
    hasShell: (state) => (shell: SessionContext['shells'][number]) => state.context?.shells.includes(shell) ?? false,
    hasPermission: (state) => (permission: string) => state.context?.permissions.includes(permission) ?? false,
  },
  actions: {
    applyAuth(result: AuthResult) {
      const previousSystemId = this.context?.systemId
      this.context = result.context
      this.systems = result.systems
      if (result.context.type === 'PLATFORM') {
        this.tenants = []
      } else if (result.tenants) {
        this.tenants = result.tenants
      } else if (previousSystemId !== result.context.systemId) {
        this.tenants = []
      }
      this.initialized = true
    },
    async bootstrap() {
      if (this.initialized) return
      try {
        const result = await apiRequest<AuthResult>('/api/v1/me/context')
        this.applyAuth(result)
      } catch (error) {
        if (!(error instanceof ApiRequestError) || error.status !== 401) throw error
        this.context = null
        this.systems = []
        this.tenants = []
        this.initialized = true
      }
    },
    async login(input: LoginInput) {
      this.loading = true
      try {
        const result = await apiRequest<AuthResult>('/api/v1/auth/login', {
          method: 'POST',
          body: input,
        })
        this.applyAuth(result)
      } finally {
        this.loading = false
      }
    },
    async register(input: RegisterInput) {
      this.loading = true
      try {
        const result = await apiRequest<AuthResult>('/api/v1/auth/register', {
          method: 'POST',
          body: input,
          idempotencyKey: crypto.randomUUID(),
        })
        this.applyAuth(result)
        return result.firstSystemId
      } finally {
        this.loading = false
      }
    },
    async loadSystems() {
      this.systems = await apiRequest<SystemSummary[]>('/api/v1/context/systems')
    },
    async loadTenants() {
      if (!this.isSystem) {
        this.tenants = []
        return
      }
      this.tenants = await contextAccessApi.listTenants()
    },
    async switchSystem(systemId: string) {
      this.loading = true
      try {
        const result = await apiRequest<AuthResult>(`/api/v1/context/systems/${systemId}:switch`, {
          method: 'POST',
        })
        this.applyAuth(result)
      } finally {
        this.loading = false
      }
    },
    async switchPlatform() {
      this.loading = true
      try {
        const result = await apiRequest<AuthResult>('/api/v1/context/platform:switch', { method: 'POST' })
        this.applyAuth(result)
      } finally {
        this.loading = false
      }
    },
    async switchTenant(tenantId: string) {
      if (tenantId === this.context?.tenantId) return
      this.loading = true
      try {
        const result = await contextAccessApi.switchTenant(tenantId)
        this.applyAuth(result)
        if (!result.tenants) await this.loadTenants()
      } finally {
        this.loading = false
      }
    },
    async refreshContext() {
      const result = await apiRequest<AuthResult>('/api/v1/auth/refresh', { method: 'POST' })
      this.applyAuth(result)
    },
    async changePassword(input: ChangePasswordInput) {
      await apiRequest<void>('/api/v1/auth/password:change', {
        method: 'POST',
        body: input,
      })
      this.$reset()
      this.initialized = true
    },
    async logout() {
      try {
        await apiRequest<void>('/api/v1/auth/logout', { method: 'POST' })
      } finally {
        this.$reset()
        this.initialized = true
      }
    },
  },
})
