import {
  createRouter,
  createWebHistory,
  type RouterHistory,
  type RouteRecordRaw,
} from 'vue-router'

import { useSessionStore } from '@/stores/session'

function authenticatedEntry(session: ReturnType<typeof useSessionStore>) {
  if (session.context?.type === 'SYSTEM' && session.context.systemId) {
    return {
      name: 'system-onboarding',
      params: { systemId: session.context.systemId },
    }
  }
  return { name: 'platform-systems' }
}

export const p1Routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    meta: { public: true, guestOnly: true },
    component: () => import('@/vnext/auth/LoginView.vue'),
  },
  {
    path: '/register',
    name: 'register',
    meta: { public: true, guestOnly: true },
    component: () => import('@/vnext/register/RegisterFirstSystemView.vue'),
  },
  {
    path: '/platform/systems',
    name: 'platform-systems',
    component: () => import('@/vnext/platform-shell/MySystemsView.vue'),
  },
  {
    path: '/systems/:systemId/admin/onboarding',
    name: 'system-onboarding',
    component: () => import('@/vnext/onboarding/SystemOnboardingView.vue'),
  },
  {
    path: '/',
    name: 'root',
    component: () => import('@/vnext/auth/LoginView.vue'),
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'fallback',
    component: () => import('@/vnext/auth/LoginView.vue'),
  },
]

export function createP1Router(history: RouterHistory = createWebHistory()) {
  const router = createRouter({ history, routes: p1Routes })

  router.beforeEach(async (to) => {
    const session = useSessionStore()
    await session.bootstrap()

    if (to.name === 'root' || to.name === 'fallback') {
      return session.authenticated ? authenticatedEntry(session) : { name: 'login' }
    }

    if (to.meta.public) {
      return session.authenticated && to.meta.guestOnly
        ? authenticatedEntry(session)
        : true
    }
    if (!session.authenticated) {
      return { name: 'login' }
    }
    if (
      to.name === 'platform-systems'
      && session.context?.type === 'SYSTEM'
      && session.context.systemId
    ) {
      return authenticatedEntry(session)
    }
    return true
  })

  return router
}

export default createP1Router()
