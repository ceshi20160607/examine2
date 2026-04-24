import { defineStore } from 'pinia'
import type { SessionPayload } from '@/api/platform'
import { clearSessionPayload, getSessionPayload, setSessionPayload } from '@/store/context'

const TOKEN_KEY = 'token'

export type AppEnv = 'dev' | 'test' | 'prod'

export const useSessionStore = defineStore('session', {
  state: () => ({
    token: (typeof uni !== 'undefined' ? (uni.getStorageSync(TOKEN_KEY) as any) : '') as string,
    payload: getSessionPayload() as SessionPayload | null,
    env: ((typeof uni !== 'undefined' ? uni.getStorageSync('env') : 'dev') as any) as AppEnv
  }),
  getters: {
    hasToken: (s) => typeof s.token === 'string' && !!s.token.trim(),
    hasSystem: (s) => !!(s.payload && (s.payload as any).systemId)
  },
  actions: {
    setToken(token: string | null) {
      const t = typeof token === 'string' ? token.trim() : ''
      this.token = t
      if (t) uni.setStorageSync(TOKEN_KEY, t)
      else uni.removeStorageSync(TOKEN_KEY)
    },
    setEnv(env: AppEnv) {
      this.env = env
      uni.setStorageSync('env', env)
    },
    setPayload(p: SessionPayload | null) {
      this.payload = p
      if (p) setSessionPayload(p)
      else clearSessionPayload()
    },
    logoutAndReLaunch() {
      this.setToken(null)
      this.setPayload(null)
      uni.reLaunch({ url: '/pages/auth/login' })
    }
  }
})

