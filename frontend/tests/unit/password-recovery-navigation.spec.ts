import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'

import router from '@/router'

describe('password recovery navigation', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('registers one guest-only route for every independent recovery page', () => {
    const expected = [
      ['forgot-password', '/auth/password/forgot'],
      ['reset-password', '/auth/password/reset'],
      ['password-reset-success', '/auth/password/reset/success'],
    ] as const

    for (const [name, path] of expected) {
      const routes = router.getRoutes().filter(route => route.name === name)
      expect(routes).toHaveLength(1)
      expect(routes[0]?.path).toBe(path)
      expect(routes[0]?.meta.guestOnly).toBe(true)
      expect(router.resolve(path).meta.public).toBe(true)
      expect(router.resolve(path).meta.guestOnly).toBe(true)
    }
  })

  it('applies guest-only behavior consistently to login and registration', () => {
    expect(router.getRoutes().find(route => route.name === 'login')?.meta.guestOnly).toBe(true)
    expect(router.getRoutes().find(route => route.name === 'register')?.meta.guestOnly).toBe(true)
  })
})
