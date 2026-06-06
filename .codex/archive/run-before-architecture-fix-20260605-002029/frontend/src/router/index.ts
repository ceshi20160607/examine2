import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import AppShell from '../layouts/AppShell.vue';
import LoginView from '../views/LoginView.vue';
import DashboardView from '../views/DashboardView.vue';
import PlatformView from '../views/PlatformView.vue';
import AppConfigView from '../views/AppConfigView.vue';
import RecordsView from '../views/RecordsView.vue';
import WorkflowView from '../views/WorkflowView.vue';
import FilesView from '../views/FilesView.vue';
import OpenApiView from '../views/OpenApiView.vue';
import OpsView from '../views/OpsView.vue';

const routes: RouteRecordRaw[] = [
  { path: '/login', name: 'login', component: LoginView },
  {
    path: '/',
    component: AppShell,
    meta: { requiresAuth: true },
    children: [
      { path: '', name: 'dashboard', component: DashboardView },
      { path: 'platform', name: 'platform', component: PlatformView },
      { path: 'config', name: 'config', component: AppConfigView },
      { path: 'records', name: 'records', component: RecordsView },
      { path: 'workflow', name: 'workflow', component: WorkflowView },
      { path: 'files', name: 'files', component: FilesView },
      { path: 'openapi', name: 'openapi', component: OpenApiView },
      { path: 'ops', name: 'ops', component: OpsView }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
];

const router = createRouter({
  history: createWebHistory(),
  routes
});

router.beforeEach((to) => {
  const auth = useAuthStore();
  if (to.meta.requiresAuth && !auth.isAuthenticated) {
    return { path: '/login', query: { redirect: to.fullPath } };
  }
  if (to.path === '/login' && auth.isAuthenticated) {
    return '/';
  }
  return true;
});

export default router;
