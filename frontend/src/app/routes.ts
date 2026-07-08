export type ShellArea =
  | 'auth'
  | 'no-member'
  | 'platform-workbench'
  | 'platform-admin'
  | 'system-runtime'
  | 'system-admin';

export interface AppRoute {
  path: string;
  label: string;
  area: ShellArea;
  taskOwner: string;
  enabled: boolean;
  disabledReason?: string;
}

export const appRoutes: AppRoute[] = [
  { path: '/login', label: '登录', area: 'auth', taskOwner: 'FE-010', enabled: true },
  { path: '/register-with-system', label: '注册并创建系统', area: 'auth', taskOwner: 'FE-010', enabled: true },
  { path: '/forgot-password', label: '找回密码', area: 'auth', taskOwner: 'FE-010', enabled: true },
  { path: '/no-member', label: '申请系统成员映射', area: 'no-member', taskOwner: 'FE-010', enabled: true },
  { path: '/platform', label: '平台工作台', area: 'platform-workbench', taskOwner: 'FE-010', enabled: true },
  { path: '/platform/flow', label: '平台流程', area: 'platform-workbench', taskOwner: 'FE-010', enabled: true },
  { path: '/platform/apps', label: '平台应用', area: 'platform-workbench', taskOwner: 'FE-010', enabled: true },
  { path: '/platform/work', label: '平台工作', area: 'platform-workbench', taskOwner: 'FE-030', enabled: true },
  { path: '/platform/ai', label: '平台 AI', area: 'platform-workbench', taskOwner: 'FE-010', enabled: true },
  { path: '/platform/todos', label: '平台待办', area: 'platform-workbench', taskOwner: 'FE-030', enabled: true },
  { path: '/platform/messages', label: '平台消息', area: 'platform-workbench', taskOwner: 'FE-030', enabled: true },
  { path: '/platform/profile', label: '登录人信息', area: 'platform-workbench', taskOwner: 'FE-010', enabled: true },
  { path: '/platform/admin', label: '平台后台', area: 'platform-admin', taskOwner: 'FE-020', enabled: true },
  { path: '/systems/:systemId/dashboard', label: '系统仪表盘', area: 'system-runtime', taskOwner: 'FE-010', enabled: true },
  { path: '/systems/:systemId/modules', label: '业务模块', area: 'system-runtime', taskOwner: 'FE-010', enabled: true },
  { path: '/systems/:systemId/todos', label: '系统待办', area: 'system-runtime', taskOwner: 'FE-030', enabled: true },
  { path: '/systems/:systemId/work', label: '工作管理', area: 'system-runtime', taskOwner: 'FE-030', enabled: true },
  { path: '/systems/:systemId/messages', label: '系统消息', area: 'system-runtime', taskOwner: 'FE-030', enabled: true },
  { path: '/systems/:systemId/profile', label: '系统个人信息', area: 'system-runtime', taskOwner: 'FE-010', enabled: true },
  { path: '/systems/:systemId/admin', label: '系统后台', area: 'system-admin', taskOwner: 'FE-020', enabled: true },
];

export function routesByArea(area: ShellArea): AppRoute[] {
  return appRoutes.filter((route) => route.area === area);
}

export function normalizeRoute(rawHash: string): string {
  const path = rawHash.replace(/^#/, '');
  return path.length > 0 ? path : '/login';
}
