import type { RouteRecordRaw } from 'vue-router'
import './product.css'
import AuthView from './views/AuthView.vue'
import PlatformHomeView from './views/PlatformHomeView.vue'
import SystemHomeView from './views/SystemHomeView.vue'

export const productRoutes: RouteRecordRaw[] = [
  { path: '/', redirect: '/login' },
  { path: '/login', name: 'login', component: AuthView, props: { initialMode: 'login' } },
  { path: '/register', name: 'register', component: AuthView, props: { initialMode: 'register' } },
  { path: '/platform', name: 'platform', component: PlatformHomeView },
  { path: '/systems/:systemId', name: 'system-home', component: SystemHomeView },
  { path: '/:pathMatch(.*)*', redirect: '/login' },
]
