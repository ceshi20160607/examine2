import { createRouter, createWebHistory } from 'vue-router'
import { getToken } from '../api/http'
import { hasSystemContext } from '../store/session'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  { path: '/register', name: 'register', component: () => import('../views/RegisterView.vue'), meta: { public: true } },
  { path: '/systems', name: 'systems', component: () => import('../views/SystemsView.vue') },
  { path: '/platform/inbox', name: 'platform-inbox', component: () => import('../views/PlatformInboxView.vue') },
  { path: '/platform/open-apps', name: 'open-apps', component: () => import('../views/OpenAppsView.vue') },
  { path: '/upload', name: 'upload', component: () => import('../views/UploadView.vue') },
  { path: '/apps', name: 'apps', component: () => import('../views/AppsView.vue') },
  { path: '/apps/:appId', name: 'app-hub', component: () => import('../views/AppHubView.vue') },
  { path: '/apps/:appId/models', name: 'models', component: () => import('../views/ModelsView.vue') },
  { path: '/apps/:appId/models/:modelId/fields', name: 'fields', component: () => import('../views/FieldsView.vue') },
  { path: '/apps/:appId/dicts', name: 'dicts', component: () => import('../views/DictsView.vue') },
  { path: '/apps/:appId/dicts/:dictId/items', name: 'dict-items', component: () => import('../views/DictItemsView.vue') },
  { path: '/apps/:appId/depts', name: 'depts', component: () => import('../views/DeptView.vue') },
  { path: '/apps/:appId/relations', name: 'relations', component: () => import('../views/RelationsView.vue') },
  { path: '/apps/:appId/pages', name: 'pages', component: () => import('../views/PagesView.vue') },
  { path: '/apps/:appId/pages/:pageId/edit', name: 'page-edit', component: () => import('../views/PageEditView.vue') },
  { path: '/apps/:appId/list-views', name: 'list-views', component: () => import('../views/ListViewsView.vue') },
  { path: '/apps/:appId/exports', name: 'exports', component: () => import('../views/ExportsView.vue') },
  { path: '/apps/:appId/flow-bindings', name: 'flow-bindings', component: () => import('../views/FlowBindingsView.vue') },
  { path: '/apps/:appId/rbac', name: 'rbac', component: () => import('../views/RbacView.vue') },
  { path: '/apps/:appId/rbac/roles/:roleId/menus', name: 'role-menus', component: () => import('../views/RoleMenusView.vue') },
  { path: '/apps/:appId/rbac/roles/:roleId/pages', name: 'role-pages', component: () => import('../views/RolePagesView.vue') },
  { path: '/apps/:appId/menus', name: 'menus', component: () => import('../views/RuntimeMenusView.vue') },
  { path: '/records', name: 'records', component: () => import('../views/RecordsListView.vue') },
  { path: '/records/form', name: 'record-form', component: () => import('../views/RecordFormView.vue') },
  { path: '/records/detail', name: 'record-detail', component: () => import('../views/RecordDetailView.vue') },
  { path: '/flow/inbox', name: 'flow-inbox', component: () => import('../views/FlowInboxView.vue') },
  { path: '/flow/task', name: 'flow-task', component: () => import('../views/FlowTaskView.vue') },
  { path: '/flow/start', name: 'flow-start', component: () => import('../views/FlowStartView.vue') },
  { path: '/flow/instances', name: 'flow-instances', component: () => import('../views/FlowInstancesView.vue') },
  { path: '/flow/temps', name: 'flow-temps', component: () => import('../views/FlowTempsView.vue') },
  { path: '/flow/temps/:tempId', name: 'flow-temp-detail', component: () => import('../views/FlowTempDetailView.vue') },
  {
    path: '/flow/temps/:tempId/versions/:tempVerId/designer',
    name: 'flow-graph-designer',
    component: () => import('../views/FlowGraphDesignerView.vue')
  },
  { path: '/flow/instances/:instanceId', name: 'flow-instance-detail', component: () => import('../views/FlowInstanceDetailView.vue') },
  { path: '/export-jobs', name: 'export-jobs', component: () => import('../views/ExportJobsView.vue') },
  { path: '/platform/open-apps/:id', name: 'open-app-detail', component: () => import('../views/OpenAppDetailView.vue') },
  { path: '/', redirect: '/systems' }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!getToken()) return { path: '/login', query: { redirect: to.fullPath } }
  if (to.name !== 'systems' && to.name !== 'login' && !hasSystemContext()) {
    return { path: '/systems' }
  }
  return true
})

export default router
